package app.jellystack.tv.watchnext

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import dev.jellystack.design.tv.TvWatchNextArtAspect
import dev.jellystack.design.tv.TvWatchNextKind
import dev.jellystack.design.tv.TvWatchNextMediaType

private typealias Columns = TvContract.WatchNextPrograms

/**
 * The real Watch Next table. The provider only exposes this app's own rows to it. The `browsable` column is
 * read but never written: only the system may change it, and writing it from an app is rejected.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class TvProviderWatchNextGateway(
    context: Context,
) : WatchNextGateway {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override fun readPublished(): List<PublishedProgram> {
        val projection =
            arrayOf(
                android.provider.BaseColumns._ID,
                Columns.COLUMN_INTERNAL_PROVIDER_ID,
                Columns.COLUMN_BROWSABLE,
            )
        val rows = ArrayList<PublishedProgram>()
        resolver.query(Columns.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                rows +=
                    PublishedProgram(
                        rowId = cursor.getLong(0),
                        internalId = cursor.getString(1),
                        browsable = cursor.getInt(2) != 0,
                    )
            }
        }
        return rows
    }

    override fun insert(entry: WatchNextEntry): Boolean = resolver.insert(Columns.CONTENT_URI, toValues(entry)) != null

    override fun update(
        rowId: Long,
        entry: WatchNextEntry,
    ): Boolean {
        val uri = TvContract.buildWatchNextProgramUri(rowId)
        return resolver.update(uri, toValues(entry), null, null) > 0
    }

    override fun delete(rowId: Long): Boolean {
        val uri = TvContract.buildWatchNextProgramUri(rowId)
        return resolver.delete(uri, null, null) > 0
    }

    private fun toValues(entry: WatchNextEntry): ContentValues =
        ContentValues().apply {
            put(Columns.COLUMN_TYPE, programType(entry.mediaType))
            put(Columns.COLUMN_WATCH_NEXT_TYPE, watchNextType(entry.kind))
            put(Columns.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, entry.lastEngagementMillis)
            put(Columns.COLUMN_TITLE, entry.title)
            put(Columns.COLUMN_INTERNAL_PROVIDER_ID, entry.internalId)
            put(Columns.COLUMN_INTENT_URI, intentUri(entry.deepLink).toString())
            entry.episodeTitle?.let { put(Columns.COLUMN_EPISODE_TITLE, it) }
            entry.seasonNumber?.let { put(Columns.COLUMN_SEASON_DISPLAY_NUMBER, it.toString()) }
            entry.episodeNumber?.let { put(Columns.COLUMN_EPISODE_DISPLAY_NUMBER, it.toString()) }
            entry.durationMs?.let {
                put(Columns.COLUMN_DURATION_MILLIS, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            entry.positionMs?.let {
                put(Columns.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            entry.artUri?.let { uri ->
                put(Columns.COLUMN_POSTER_ART_URI, uri)
                put(Columns.COLUMN_POSTER_ART_ASPECT_RATIO, posterAspect(entry.artAspect))
            }
        }

    private fun programType(mediaType: TvWatchNextMediaType): Int =
        when (mediaType) {
            TvWatchNextMediaType.MOVIE -> Columns.TYPE_MOVIE
            TvWatchNextMediaType.EPISODE -> Columns.TYPE_TV_EPISODE
        }

    private fun watchNextType(kind: TvWatchNextKind): Int =
        when (kind) {
            TvWatchNextKind.CONTINUE -> Columns.WATCH_NEXT_TYPE_CONTINUE
            TvWatchNextKind.NEXT -> Columns.WATCH_NEXT_TYPE_NEXT
        }

    private fun posterAspect(aspect: TvWatchNextArtAspect?): Int =
        when (aspect) {
            TvWatchNextArtAspect.PORTRAIT_2_3 -> Columns.ASPECT_RATIO_2_3
            else -> Columns.ASPECT_RATIO_16_9
        }

    /** An explicit-package VIEW intent; the launcher parses it back with the BROWSABLE category the filter expects. */
    private fun intentUri(deepLink: String): Uri {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(appContext.packageName)
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }
}
