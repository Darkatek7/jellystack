package dev.jellystack.core.downloads

import android.content.Context
import android.os.Build
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AndroidOfflineDownloadManager(
    private val context: Context,
    private val mediaStore: OfflineMediaStore,
    private val queueStore: OfflineDownloadQueueStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val networkAllowed: StateFlow<Boolean> = MutableStateFlow(true),
) : OfflineDownloadManager {
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, DownloadTask>()
    private val downloadsRoot = File(context.filesDir, "offline/downloads")

    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()
    private val _offlineMedia = MutableStateFlow<List<OfflineMedia>>(emptyList())
    override val offlineMedia: StateFlow<List<OfflineMedia>> = _offlineMedia.asStateFlow()

    init {
        scope.launch {
            restoreCompleted()
            restoreQueue()
        }
        scope.launch {
            networkAllowed.collect { allowed ->
                if (!allowed) return@collect
                val waiting =
                    mutex.withLock {
                        tasks.values.filter { task -> task.waitingForNetwork && task.job?.isActive != true }
                    }
                waiting.forEach { task ->
                    task.waitingForNetwork = false
                    startDownload(task)
                }
            }
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
                task.pausedByUser = true
            }
        }
    }

    override fun resume(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[mediaId] ?: return@withLock
                if (!task.pausedByUser) return@withLock
                task.pausedByUser = false
                startDownload(task)
            }
        }
    }

    override fun remove(mediaId: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks.remove(mediaId)
                task?.job?.cancel()
                task?.targetFile?.delete()
                mediaStore.remove(mediaId)
                queueStore.remove(mediaId)
                refreshOfflineMedia()
                _statuses.emit(_statuses.value - mediaId)
            }
        }
    }

    override fun clearAll() {
        scope.launch {
            val currentTasks = mutex.withLock { tasks.values.toList().also { tasks.clear() } }
            currentTasks.forEach { task -> task.job?.cancel() }
            queueStore.clear()
            mediaStore.list().forEach { media -> mediaStore.remove(media.mediaId) }
            downloadsRoot.deleteRecursively()
            downloadsRoot.mkdirs()
            _statuses.emit(emptyMap())
            refreshOfflineMedia()
        }
    }

    fun release() {
        scope.cancel()
    }

    private fun startDownload(task: DownloadTask) {
        if (task.job?.isActive == true || task.pausedByUser) return
        if (!networkAllowed.value) {
            task.waitingForNetwork = true
            scope.launch {
                updateStatus(
                    task.request.mediaId,
                    DownloadStatus.WaitingForNetwork(
                        mediaId = task.request.mediaId,
                        bytesDownloaded = task.targetFile.length(),
                        totalBytes = task.request.expectedSizeBytes,
                    ),
                )
            }
            return
        }
        val existingBytes = task.targetFile.length()
        task.job =
            scope.launch {
                try {
                    val result =
                        download(task.request, task.targetFile, existingBytes) { progress ->
                            updateStatus(task.request.mediaId, progress)
                        }
                    when (result) {
                        is DownloadCompletion.Completed -> {
                            val completedMedia =
                                OfflineMedia(
                                    mediaId = task.request.mediaId,
                                    filePath = task.targetFile.absolutePath,
                                    mimeType = task.request.mimeType,
                                    checksumSha256 = result.checksum,
                                    sizeBytes = task.targetFile.length(),
                                    kind = task.request.kind,
                                    language = task.request.language,
                                    relativePath = task.request.relativePath,
                                    metadata = task.request.metadata,
                                )
                            mediaStore.write(completedMedia)
                            queueStore.remove(task.request.mediaId)
                            refreshOfflineMedia()
                            updateStatus(
                                task.request.mediaId,
                                DownloadStatus.Completed(
                                    mediaId = task.request.mediaId,
                                    filePath = task.targetFile.absolutePath,
                                    bytesDownloaded = task.targetFile.length(),
                                ),
                            )
                        }
                        is DownloadCompletion.Paused -> {
                            updateStatus(
                                task.request.mediaId,
                                DownloadStatus.Paused(
                                    mediaId = task.request.mediaId,
                                    bytesDownloaded = result.bytesDownloaded,
                                    totalBytes = result.totalBytes,
                                ),
                            )
                        }
                        is DownloadCompletion.WaitingForNetwork -> {
                            task.waitingForNetwork = true
                            updateStatus(
                                task.request.mediaId,
                                DownloadStatus.WaitingForNetwork(
                                    mediaId = task.request.mediaId,
                                    bytesDownloaded = result.bytesDownloaded,
                                    totalBytes = result.totalBytes,
                                ),
                            )
                        }
                    }
                } catch (throwable: Throwable) {
                    task.targetFile.delete()
                    mediaStore.remove(task.request.mediaId)
                    refreshOfflineMedia()
                    updateStatus(
                        task.request.mediaId,
                        DownloadStatus.Failed(task.request.mediaId, throwable),
                    )
                } finally {
                    mutex.withLock {
                        task.job = null
                        if (!task.pausedByUser && !task.waitingForNetwork) {
                            tasks.remove(task.request.mediaId)
                        }
                    }
                }
            }
    }

    private suspend fun download(
        request: DownloadRequest,
        target: File,
        resumeOffset: Long,
        onProgress: suspend (DownloadStatus.InProgress) -> Unit,
    ): DownloadCompletion =
        withContext(ioDispatcher) {
            val connection =
                (URL(request.downloadUrl).openConnection() as HttpURLConnection).apply {
                    request.headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }
                    if (resumeOffset > 0) {
                        setRequestProperty("Range", "bytes=$resumeOffset-")
                    }
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }

            val declaredLength =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    connection.contentLength.toLong()
                }
            val totalBytes =
                connection.headerFields["Content-Range"]
                    ?.firstOrNull()
                    ?.substringAfter("/")
                    ?.toLongOrNull()
                    ?: declaredLength.takeIf { it >= 0 }?.let { it + resumeOffset }

            val digest =
                if (request.checksumSha256 != null) {
                    MessageDigest.getInstance("SHA-256")
                } else {
                    null
                }

            val raf = RandomAccessFile(target, "rw")
            if (resumeOffset > 0) {
                raf.seek(resumeOffset)
            }

            var downloaded = resumeOffset
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            connection.inputStream.use { input ->
                while (true) {
                    if (!networkAllowed.value) {
                        raf.close()
                        connection.disconnect()
                        return@withContext DownloadCompletion.WaitingForNetwork(downloaded, totalBytes)
                    }
                    if (!isActive) {
                        raf.close()
                        connection.disconnect()
                        return@withContext DownloadCompletion.Paused(downloaded, totalBytes)
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    digest?.update(buffer, 0, read)
                    downloaded += read
                    onProgress(
                        DownloadStatus.InProgress(
                            mediaId = request.mediaId,
                            bytesDownloaded = downloaded,
                            totalBytes = totalBytes,
                        ),
                    )
                    val task = mutex.withLock { tasks[request.mediaId] }
                    if (task?.pausedByUser == true) {
                        raf.close()
                        connection.disconnect()
                        return@withContext DownloadCompletion.Paused(downloaded, totalBytes)
                    }
                }
            }

            raf.fd.sync()
            raf.close()
            connection.disconnect()

            val finalSize = target.length()
            request.expectedSizeBytes?.let { expected ->
                if (expected != finalSize) {
                    throw IllegalStateException(
                        "Downloaded size mismatch. Expected=$expected, actual=$finalSize",
                    )
                }
            }

            val checksum =
                if (digest != null) {
                    val computed = digest.digest().joinToString("") { "%02x".format(it) }
                    val normalizedExpected = request.checksumSha256!!.lowercase()
                    if (computed != normalizedExpected) {
                        throw IllegalStateException("Checksum mismatch")
                    }
                    computed
                } else {
                    null
                }

            DownloadCompletion.Completed(checksum)
        }

    private fun targetFileFor(request: DownloadRequest): File {
        downloadsRoot.mkdirs()
        val target =
            request.relativePath
                ?.let { relative ->
                    val file = File(downloadsRoot, relative)
                    file.parentFile?.mkdirs()
                    file
                }
                ?: run {
                    val extension =
                        request.mimeType?.let { mime ->
                            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                        } ?: when (request.kind) {
                            OfflineMediaKind.SUBTITLE -> "vtt"
                            else -> "bin"
                        }
                    val safeId = request.mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(downloadsRoot, "$safeId.$extension")
                }
        target.parentFile?.mkdirs()
        return target
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

    private suspend fun enqueueInternal(
        request: DownloadRequest,
        persist: Boolean,
    ) {
        mutex.withLock {
            if (tasks.containsKey(request.mediaId)) {
                return@withLock
            }
            val targetFile = targetFileFor(request)
            val task =
                DownloadTask(
                    request = request,
                    targetFile = targetFile,
                )
            tasks[request.mediaId] = task
            if (persist) {
                queueStore.put(request)
            }
            updateStatus(request.mediaId, DownloadStatus.Queued(request.mediaId))
            startDownload(task)
        }
    }

    private suspend fun restoreCompleted() {
        mediaStore.list().forEach { media ->
            val file = File(media.filePath)
            if (file.exists()) {
                updateStatus(
                    media.mediaId,
                    DownloadStatus.Completed(
                        mediaId = media.mediaId,
                        filePath = media.filePath,
                        bytesDownloaded = file.length(),
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

    private data class DownloadTask(
        val request: DownloadRequest,
        val targetFile: File,
        var job: Job? = null,
        var pausedByUser: Boolean = false,
        var waitingForNetwork: Boolean = false,
    )

    private sealed class DownloadCompletion {
        data class Completed(
            val checksum: String?,
        ) : DownloadCompletion()

        data class Paused(
            val bytesDownloaded: Long,
            val totalBytes: Long?,
        ) : DownloadCompletion()

        data class WaitingForNetwork(
            val bytesDownloaded: Long,
            val totalBytes: Long?,
        ) : DownloadCompletion()
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
