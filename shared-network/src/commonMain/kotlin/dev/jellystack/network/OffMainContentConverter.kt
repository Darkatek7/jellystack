package dev.jellystack.network

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the JSON encode and decode of every request and response body on [dispatcher].
 *
 * Ktor performs content negotiation on the coroutine of whoever called `body()`/`setBody()`, which in the apps is
 * usually Main. Large Jellyfin pages and Seerr discover payloads would then be parsed on the UI thread. The
 * converter is stateless, so moving it does not touch any shared mutable state.
 */
internal class OffMainContentConverter(
    private val delegate: ContentConverter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ContentConverter {
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? = withContext(dispatcher) { delegate.serialize(contentType, charset, typeInfo, value) }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? = withContext(dispatcher) { delegate.deserialize(charset, typeInfo, content) }
}
