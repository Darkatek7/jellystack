package app.jellystack.tv.watchnext

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the cached Watch Next poster art, read-only, to the launcher. It is exported because the launcher
 * runs in another process; access is limited to files named by the unguessable hash from [WatchNextArtFiles],
 * which are artwork only. It has no query surface and refuses every write.
 */
class WatchNextArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read-only")
        val name = WatchNextArtFiles.nameFromPath(uri.pathSegments.orEmpty()) ?: throw FileNotFoundException()
        val directory = File(requireNotNull(context).cacheDir, WatchNextArtFiles.CACHE_DIRECTORY)
        val file = File(directory, name)
        if (!file.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()
}
