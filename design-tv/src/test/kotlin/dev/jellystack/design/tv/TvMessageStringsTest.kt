package dev.jellystack.design.tv

import dev.jellystack.core.preferences.AppLanguage
import kotlin.test.Test
import kotlin.test.assertNotEquals

class TvMessageStringsTest {
    @Test
    fun countsOfOneUseTheSingularInEveryLanguage() {
        listOf(AppLanguage.ENGLISH, AppLanguage.GERMAN).forEach { language ->
            val messages = TvStrings.current(language).messages

            assertNotEquals(messages.playingIn(2).replace("2", "1"), messages.playingIn(1), "$language countdown")
            assertNotEquals(
                messages.pinAttemptsRemaining(2).replace("2", "1"),
                messages.pinAttemptsRemaining(1),
                "$language PIN attempts",
            )
        }
    }
}
