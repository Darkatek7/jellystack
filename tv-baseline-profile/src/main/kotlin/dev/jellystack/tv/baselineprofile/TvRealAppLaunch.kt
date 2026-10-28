package dev.jellystack.tv.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * Launching and screen detection for scenarios against the production TV app (MainActivity), as opposed to the
 * synthetic fixture used by TvBrowseBenchmark. They need a profile that is already signed in to a Jellyfin server
 * with a few libraries; the profile picker is passed through with the focused profile.
 */

internal const val TV_APP_PACKAGE = "app.jellystack.mobile"
internal const val SCREEN_CHANGE_TIMEOUT_MS = 10_000L

private const val TV_MAIN_ACTIVITY = "app.jellystack.tv.MainActivity"
private const val APP_READY_TIMEOUT_MS = 30_000L

// The media-libraries row is the first row of Home (its casing differs between servers); the picker title
// appears on a cold start with several profiles.
private val profilePickerMarker = Pattern.compile("Who's watching\\?|Wer schaut\\?")
private val homeMarker = Pattern.compile("(?i)my media|meine medien")
private val readyMarker = Pattern.compile("${profilePickerMarker.pattern()}|${homeMarker.pattern()}")

/** Cold-starts [TV_MAIN_ACTIVITY] through its leanback entry and returns once Home is on screen. */
internal fun MacrobenchmarkScope.startTvAppAtHome() {
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            component = ComponentName(TV_APP_PACKAGE, TV_MAIN_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        },
    )
    device.awaitHome()
}

/** Waits for Home, choosing the focused profile first when the "Who's watching?" gate is shown. */
internal fun UiDevice.awaitHome() {
    check(wait(Until.hasObject(appText(readyMarker)), APP_READY_TIMEOUT_MS) == true) {
        "Neither the profile picker nor Home appeared; sign in to a Jellyfin server on this device first."
    }
    if (hasObject(appText(profilePickerMarker))) {
        pressDPadCenter()
        check(wait(Until.hasObject(appText(homeMarker)), APP_READY_TIMEOUT_MS) == true) {
            "Home did not appear after choosing a profile."
        }
    }
}

/** Waits until Home has been replaced by another screen (a detail page, a library, ...). */
internal fun UiDevice.awaitLeftHome() {
    check(wait(Until.gone(appText(homeMarker)), SCREEN_CHANGE_TIMEOUT_MS) == true) {
        "Home was still visible after the navigation key press."
    }
    waitForIdle(SCREEN_CHANGE_TIMEOUT_MS)
}

/** Presses Back and waits for Home to be shown again. */
internal fun UiDevice.backToHome() {
    pressBack()
    check(wait(Until.hasObject(appText(homeMarker)), SCREEN_CHANGE_TIMEOUT_MS) == true) {
        "Home did not return after Back."
    }
    waitForIdle(SCREEN_CHANGE_TIMEOUT_MS)
}

// A BySelector is mutable, so every query builds its own.
private fun appText(text: Pattern) = By.pkg(TV_APP_PACKAGE).text(text)
