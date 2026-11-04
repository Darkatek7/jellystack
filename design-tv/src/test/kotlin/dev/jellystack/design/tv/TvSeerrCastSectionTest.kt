package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinPerson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TvSeerrCastSectionTest {
    private val person = JellyfinPerson("p-1", "Ada Example", null, "Person", null)

    @Test
    fun aKnownPersonIsReturnedForTheName() =
        runTest {
            assertEquals(person, resolveSeerrCastPerson("Ada Example") { person })
        }

    @Test
    fun aFailingLookupIsTreatedLikeNoMatch() =
        runTest {
            assertNull(resolveSeerrCastPerson("Ada Example") { error("server down") })
        }

    @Test
    fun cancellationIsNotSwallowed() =
        runTest {
            assertFailsWith<CancellationException> {
                resolveSeerrCastPerson("Ada Example") { throw CancellationException("gone") }
            }
        }
}
