package dev.jellystack.core.downloads

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
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
import platform.CommonCrypto.CC_LONG
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSOperationQueue.Companion.mainQueue
import platform.Foundation.NSSearchPathDirectory.NSDocumentDirectory
import platform.Foundation.NSSearchPathDomainMask.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
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
import platform.Foundation.stringByDeletingLastPathComponent
import platform.darwin.NSObject
import kotlin.Exception

private const val DOWNLOADS_FOLDER = "offline/downloads"

class IosOfflineDownloadManager(
    private val mediaStore: OfflineMediaStore,
    private val queueStore: OfflineDownloadQueueStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : NSObject(),
    OfflineDownloadManager,
    NSURLSessionDownloadDelegateProtocol {
    private val fileManager = NSFileManager.defaultManager()
    private val downloadsRoot = ensureDownloadsRoot()
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, DownloadTask>()

    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()
    private val _offlineMedia = MutableStateFlow<List<OfflineMedia>>(emptyList())
    override val offlineMedia: StateFlow<List<OfflineMedia>> = _offlineMedia.asStateFlow()

    private val session: NSURLSession =
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate = this,
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
                            HTTPMethod = "GET"
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

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64,
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

    override fun URLSession(
        session: NSURLSession,
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

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        val mediaId = task.taskDescription ?: return
        if (didCompleteWithError == null) return
        scope.launch {
            mutex.withLock {
                val tracked = tasks[mediaId] ?: return@withLock
                if (tracked.isPaused && didCompleteWithError.code.toInt() == NSURLErrorCancelled) {
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
                        request.mimeType?.substringAfter('/', missingDelimiterValue = null)
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
        val base =
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
                ?: NSTemporaryDirectory()
        val path = base.appendingPathComponent(DOWNLOADS_FOLDER)
        ensureDirectory(path)
        return path
    }

    private fun ensureDirectory(path: String) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            errorPtr.value = null
            fileManager.createDirectoryAtPath(path, true, null, errorPtr.ptr)
            errorPtr.value?.let { throw IosDownloadException("Failed to create directory: ${it.localizedDescription}") }
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
        memScoped {
            if (fileManager.fileExistsAtPath(target)) {
                val removePtr = alloc<ObjCObjectVar<NSError?>>()
                removePtr.value = null
                fileManager.removeItemAtPath(target, removePtr.ptr)
                removePtr.value?.let { throw IosDownloadException("Failed to replace file: ${it.localizedDescription}") }
            }
            ensureParentDirectory(target)
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            errorPtr.value = null
            fileManager.moveItemAtPath(source, target, errorPtr.ptr)
            errorPtr.value?.let { throw IosDownloadException("Failed to move download: ${it.localizedDescription}") }
        }
    }

    private fun removeFile(path: String) {
        memScoped {
            if (!fileManager.fileExistsAtPath(path)) return@memScoped
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            errorPtr.value = null
            fileManager.removeItemAtPath(path, errorPtr.ptr)
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

    private fun fileSize(path: String): Long =
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            errorPtr.value = null
            val attributes = fileManager.attributesOfItemAtPath(path, errorPtr.ptr)
            errorPtr.value?.let { throw IosDownloadException("Failed to read file size: ${it.localizedDescription}") }
            val number = attributes?.get(NSFileSize) as? NSNumber ?: return@memScoped 0L
            number.longLongValue
        }

    private fun sha256(path: String): String {
        val data = NSData.dataWithContentsOfFile(path) ?: throw IosDownloadException("Unable to read file for checksum.")
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
        val pointer = data.bytes ?: throw IosDownloadException("Checksum read returned empty buffer.")
        CC_SHA256(pointer, data.length.convert<CC_LONG>(), digest.refTo(0))
        return digest.joinToString("") { "%02x".format(it.toInt()) }
    }

    private fun String.parentPath(): String = (this as NSString).stringByDeletingLastPathComponent
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
