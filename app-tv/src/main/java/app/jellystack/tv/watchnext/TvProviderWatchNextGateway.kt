package app.jellystack.tv.watchnext

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import dev.jellystack.design.tv.TvWatchNextArtAspect
import dev.jellystack.design.tv.TvWatchNextKind
import dev.jellystack.design.tv.TvWatchNextMediaType

/**
 * The real Watch Next table. The provider only exposes this app's own rows to it. The `browsable` column is
 * read but never written: only the system may change it, and writing it from an app is rejected.
 */
internal class TvProviderWatchNextGateway(
    context: Context,
) : WatchNextGateway {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override fun readPublished(): List<PublishedProgram> {
        val projection =
            arrayOf(
                android.provider.BaseColumns._ID,
                TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE,
            )
        val rows = ArrayList<PublishedProgram>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, projection, null, null, null)?.use { cursor ->
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

    override fun insert(entry: WatchNextEntry): Boolean =
        resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, toProgram(entry).toContentValues()) != null

    override fun update(
        rowId: Long,
        entry: WatchNextEntry,
    ): Boolean {
        val uri = TvContractCompat.buildWatchNextProgramUri(rowId)
        return resolver.update(uri, toProgram(entry).toContentValues(), null, null) > 0
    }

    override fun delete(rowId: Long): Boolean {
        val uri = TvContractCompat.buildWatchNextProgramUri(rowId)
        return resolver.delete(uri, null, null) > 0
    }

    private fun toProgram(entry: WatchNextEntry): WatchNextProgram {
        val builder = WatchNextProgram.Builder()
        builder
            .setType(
                when (entry.mediaType) {
                    TvWatchNextMediaType.MOVIE -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                    TvWatchNextMediaType.EPISODE -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                },
            ).setWatchNextType(
                when (entry.kind) {
                    TvWatchNextKind.CONTINUE -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                    TvWatchNextKind.NEXT -> TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                },
            ).setLastEngagementTimeUtcMillis(entry.lastEngagementMillis)
            .setTitle(entry.title)
            .setInternalProviderId(entry.internalId)
            .setIntentUri(intentUri(entry.deepLink))
        entry.episodeTitle?.let(builder::setEpisodeTitle)
        entry.seasonNumber?.let(builder::setSeasonNumber)
        entry.episodeNumber?.let(builder::setEpisodeNumber)
        entry.durationMs?.let { builder.setDurationMillis(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        entry.positionMs?.let { builder.setLastPlaybackPositionMillis(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        entry.artUri?.let { uri ->
            builder.setPosterArtUri(Uri.parse(uri))
            builder.setPosterArtAspectRatio(
                when (entry.artAspect) {
                    TvWatchNextArtAspect.PORTRAIT_2_3 -> TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
                    else -> TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
                },
            )
        }
        return builder.build()
    }

    /** An explicit-package VIEW intent; the launcher parses it back with the BROWSABLE category the filter expects. */
    private fun intentUri(deepLink: String): Uri {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(appContext.packageName)
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }
}
