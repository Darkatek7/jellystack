package dev.jellystack.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class OffMainContentConverterTest {
    @Test
    fun responseBodiesAreDecodedOffTheCallingThreadAndStillArriveCorrectly() =
        runTest {
            val callingThread = Thread.currentThread()
            val engine =
                MockEngine {
                    respond("\"payload\"", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine))
            try {
                val probe = client.get("https://media.example").body<DecodeProbe>()

                assertEquals("payload", probe.value)
                assertNotSame(callingThread, probe.decodedOn)
                assertEquals(callingThread, Thread.currentThread())
            } finally {
                client.close()
            }
        }

    @Serializable(with = DecodeProbeSerializer::class)
    class DecodeProbe(
        val value: String,
        val decodedOn: Thread,
    )

    object DecodeProbeSerializer : KSerializer<DecodeProbe> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DecodeProbe", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): DecodeProbe = DecodeProbe(decoder.decodeString(), Thread.currentThread())

        override fun serialize(
            encoder: Encoder,
            value: DecodeProbe,
        ) = encoder.encodeString(value.value)
    }
}
