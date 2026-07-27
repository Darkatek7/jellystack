@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.jellystack.core.downloads

import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSOperationQueue.Companion.mainQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSession.Companion.sessionWithConfiguration
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionConfiguration.Companion.defaultSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionDownloadTaskResumeData
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.Exception

private const val DOWNLOADS_FOLDER = "offline/downloads"

class IosOfflineDownloadManager(
    private val mediaStore: OfflineMediaStore,
    private val queueStore: OfflineDownloadQueueStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : OfflineDownloadManager {
    private val fileManager = NSFileManager.defaultManager()
    private val downloadsRoot = ensureDownloadsRoot()
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, DownloadTask>()
    private val sessionDelegate =
        IosDownloadSessionDelegate(
            onProgress = ::handleProgress,
            onFinished = ::handleFinished,
            onError = ::handleError,
        )

    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()
    private val _offlineMedia = MutableStateFlow<List<OfflineMedia>>(emptyList())
    override val offlineMedia: StateFlow<List<OfflineMedia>> = _offlineMedia.asStateFlow()

    private val session: NSURLSession =
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate = sessionDelegate,
            delegateQueue = NSOperationQueue.mainQueue(),
        )

    init {
        scope.launch {
            restoreCompleted()
            restoreQueue()
        }
    }

    override fun enqueue(request: DownloadRequest) {
        scope.launch {
            enqueueInternal(request, persist = true)
        }
    }

    override fun pause(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[mediaId] ?: return@withLock
                task.isPaused = true
                task.downloadTask?.cancelByProducingResumeData { data ->
                    scope.launch {
                        mutex.withLock {
                            task.downloadTask = null
                            task.resumeData = data
                            val current = _statuses.value[mediaId]
                            val pausedStatus =
                                when (current) {
                                    is DownloadStatus.InProgress ->
                                        DownloadStatus.Paused(
                                            mediaId = mediaId,
                                            bytesDownloaded = current.bytesDownloaded,
                                            totalBytes = current.totalBytes,
                                        )
                                    else -> DownloadStatus.Paused(mediaId, bytesDownloaded = 0, totalBytes = null)
                                }
                            updateStatus(mediaId, pausedStatus)
                        }
                    }
                }
            }
        }
    }

    override fun resume(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[mediaId] ?: return@withLock
                if (!task.isPaused) return@withLock
                task.isPaused = false
                startDownload(task)
            }
        }
    }

    override fun remove(mediaId: String) {
        scope.launch {
            val tracked =
                mutex.withLock {
                    tasks.remove(mediaId)
                }
            tracked?.downloadTask?.cancel()
            tracked?.targetPath?.let { removeFile(it) }
            mutex.withLock {
                mediaStore.remove(mediaId)
                queueStore.remove(mediaId)
                refreshOfflineMedia()
                _statuses.value = _statuses.value - mediaId
            }
        }
    }

    fun release() {
        session.invalidateAndCancel()
        scope.cancel()
    }

    private suspend fun enqueueInternal(
        request: DownloadRequest,
        persist: Boolean,
    ) {
        mutex.withLock {
            if (tasks.containsKey(request.mediaId)) {
                return@withLock
            }
            val target = targetPathFor(request)
            val tracked =
                DownloadTask(
                    request = request,
                    targetPath = target,
                )
            tasks[request.mediaId] = tracked
            if (persist) {
                queueStore.put(request)
            }
            updateStatus(request.mediaId, DownloadStatus.Queued(request.mediaId))
            startDownload(tracked)
        }
    }

    private fun startDownload(task: DownloadTask) {
        val downloadTask =
            task.resumeData
                ?.let { session.downloadTaskWithResumeData(it) }
                ?: run {
                    val url = NSURL.URLWithString(task.request.downloadUrl) ?: throw IosDownloadException("Invalid URL")
                    val request =
                        NSMutableURLRequest.requestWithURL(url).apply {
                            setHTTPMethod("GET")
                            task.request.headers.forEach { (key, value) ->
                                setValue(value, forHTTPHeaderField = key)
                            }
                        }
                    session.downloadTaskWithRequest(request)
                }
        task.resumeData = null
        task.downloadTask = downloadTask
        downloadTask.taskDescription = task.request.mediaId
        downloadTask.resume()
    }

    private suspend fun restoreCompleted() {
        mediaStore.list().forEach { media ->
            if (fileExists(media.filePath)) {
                updateStatus(
                    media.mediaId,
                    DownloadStatus.Completed(
                        mediaId = media.mediaId,
                        filePath = media.filePath,
                        bytesDownloaded = fileSize(media.filePath),
                    ),
                )
            } else {
                mediaStore.remove(media.mediaId)
            }
        }
        refreshOfflineMedia()
    }

    private suspend fun restoreQueue() {
        queueStore
            .all()
            .forEach { request ->
                enqueueInternal(request, persist = false)
            }
    }

    private fun handleProgress(
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val mediaId = downloadTask.taskDescription ?: return
        scope.launch {
            updateStatus(
                mediaId,
                DownloadStatus.InProgress(
                    mediaId = mediaId,
                    bytesDownloaded = totalBytesWritten,
                    totalBytes = totalBytesExpectedToWrite.takeIf { it > 0 },
                ),
            )
        }
    }

    private fun handleFinished(
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        val mediaId = downloadTask.taskDescription ?: return
        scope.launch {
            mutex.withLock {
                val tracked = tasks[mediaId] ?: return@withLock
                val tempPath = didFinishDownloadingToURL.path ?: return@withLock
                moveFile(tempPath, tracked.targetPath)
                val checksum = validateDownload(tracked.request, tracked.targetPath)
                val size = fileSize(tracked.targetPath)
                mediaStore.write(
                    OfflineMedia(
                        mediaId = mediaId,
                        filePath = tracked.targetPath,
                        mimeType = tracked.request.mimeType,
                        checksumSha256 = checksum,
                        sizeBytes = size,
                        kind = tracked.request.kind,
                        language = tracked.request.language,
                        relativePath = tracked.request.relativePath,
                        metadata = tracked.request.metadata,
                    ),
                )
                queueStore.remove(mediaId)
                tasks.remove(mediaId)
                refreshOfflineMedia()
                updateStatus(
                    mediaId,
                    DownloadStatus.Completed(
                        mediaId = mediaId,
                        filePath = tracked.targetPath,
                        bytesDownloaded = size,
                    ),
                )
            }
        }
    }

    private fun handleError(
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        val mediaId = task.taskDescription ?: return
        if (didCompleteWithError == null) return
        scope.launch {
            mutex.withLock {
                val tracked = tasks[mediaId] ?: return@withLock
                if (tracked.isPaused && didCompleteWithError.code == NSURLErrorCancelled) {
                    val resumeData = didCompleteWithError.userInfo?.get(NSURLSessionDownloadTaskResumeData) as? NSData
                    tracked.resumeData = resumeData
                    tracked.downloadTask = null
                    return@withLock
                }
                tasks.remove(mediaId)
                removeFile(tracked.targetPath)
                mediaStore.remove(mediaId)
                refreshOfflineMedia()
                updateStatus(
                    mediaId,
                    DownloadStatus.Failed(
                        mediaId = mediaId,
                        cause = didCompleteWithError.asException(),
                    ),
                )
            }
        }
    }

    private suspend fun updateStatus(
        mediaId: String,
        status: DownloadStatus,
    ) {
        _statuses.emit(_statuses.value + (mediaId to status))
    }

    private suspend fun refreshOfflineMedia() {
        _offlineMedia.emit(mediaStore.list())
    }

    private fun targetPathFor(request: DownloadRequest): String {
        val sanitizedId = request.mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val relative =
            request.relativePath
                ?: run {
                    val extension =
                        request.mimeType
                            ?.substringAfter('/', "")
                            ?.takeIf { it.isNotBlank() }
                            ?: when (request.kind) {
                                OfflineMediaKind.SUBTITLE -> "vtt"
                                else -> "bin"
                            }
                    "$sanitizedId.$extension"
                }
        val path = downloadsRoot.appendingPathComponent(relative)
        ensureParentDirectory(path)
        return path
    }

    private fun ensureDownloadsRoot(): String {
        val base = NSHomeDirectory().trimEnd('/') + "/Documents"
        val path = base.appendingPathComponent(DOWNLOADS_FOLDER)
        ensureDirectory(path)
        return path
    }

    private fun ensureDirectory(path: String) {
        val created =
            fileManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        if (!created && !fileManager.fileExistsAtPath(path)) {
            throw IosDownloadException("Failed to create directory.")
        }
    }

    private fun ensureParentDirectory(path: String) {
        val parent = path.parentPath()
        if (parent.isNotEmpty()) {
            ensureDirectory(parent)
        }
    }

    private fun moveFile(
        source: String,
        target: String,
    ) {
        if (fileManager.fileExistsAtPath(target)) {
            val removed = fileManager.removeItemAtPath(target, error = null)
            if (!removed) {
                throw IosDownloadException("Failed to replace file.")
            }
        }
        ensureParentDirectory(target)
        val moved = fileManager.moveItemAtPath(source, toPath = target, error = null)
        if (!moved) {
            throw IosDownloadException("Failed to move download.")
        }
    }

    private fun removeFile(path: String) {
        if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, error = null)
        }
    }

    private fun validateDownload(
        request: DownloadRequest,
        path: String,
    ): String? {
        val size = fileSize(path)
        request.expectedSizeBytes?.let { expected ->
            if (expected != size) {
                throw IosDownloadException("Downloaded size mismatch. Expected=$expected, actual=$size")
            }
        }
        val expectedChecksum = request.checksumSha256 ?: return null
        val actualChecksum = sha256(path)
        if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
            throw IosDownloadException("Checksum mismatch for $path")
        }
        return actualChecksum
    }

    private fun fileExists(path: String): Boolean = fileManager.fileExistsAtPath(path)

    private fun fileSize(path: String): Long {
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
        val number = attributes?.get("NSFileSize") as? NSNumber ?: return 0L
        return number.longLongValue
    }

    private fun sha256(path: String): String {
        val data = NSData.dataWithContentsOfFile(path) ?: throw IosDownloadException("Unable to read file for checksum.")
        val pointer = data.bytes ?: throw IosDownloadException("Checksum read returned empty buffer.")
        return sha256Hex(pointer.readBytes(data.length.toInt()))
    }

    private fun String.appendingPathComponent(component: String): String = trimEnd('/') + "/" + component.trimStart('/')

    private fun String.parentPath(): String = substringBeforeLast('/', "")
}

private class DownloadTask(
    val request: DownloadRequest,
    val targetPath: String,
    var downloadTask: NSURLSessionDownloadTask? = null,
    var resumeData: NSData? = null,
    var isPaused: Boolean = false,
)

private class IosDownloadException(
    message: String,
) : Exception(message)

private fun NSError.asException(): Exception = IosDownloadException("$domain (${code.toInt()}): ${localizedDescription ?: "Unknown error"}")

private class IosDownloadSessionDelegate(
    private val onProgress: (NSURLSessionDownloadTask, Long, Long, Long) -> Unit,
    private val onFinished: (NSURLSessionDownloadTask, NSURL) -> Unit,
    private val onError: (NSURLSessionTask, NSError?) -> Unit,
) : NSObject(),
    NSURLSessionDownloadDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        onProgress(downloadTask, didWriteData, totalBytesWritten, totalBytesExpectedToWrite)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        onFinished(downloadTask, didFinishDownloadingToURL)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        onError(task, didCompleteWithError)
    }
}
