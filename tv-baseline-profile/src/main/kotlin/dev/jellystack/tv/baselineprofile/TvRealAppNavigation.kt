package dev.jellystack.tv.baselineprofile

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice

private const val FOCUS_MOVE_TIMEOUT_MS = 1_500L
private const val IDLE_POLL_MS = 100L
private const val KEY_PACING_MS = 120L
private const val HOME_ROWS_TO_TRAVERSE = 4
private const val CARDS_PER_ROW_TO_TRAVERSE = 4

/** Focus starts on the Home spotlight; Center opens the spotlight item's detail page. */
internal fun UiDevice.openSpotlightDetail() {
    pressDPadCenter()
    awaitLeftHome()
}

/** Opens the navigation rail with Left from the first column and activates the entry [railSteps] below Home. */
internal fun UiDevice.openRailEntry(railSteps: Int) {
    pressAndAwaitFocusMove(KeyEvent.KEYCODE_DPAD_LEFT)
    repeat(railSteps) { pressAndAwaitFocusMove(KeyEvent.KEYCODE_DPAD_DOWN) }
    pressDPadCenter()
    awaitLeftHome()
}

/** Walks down through the Home rows, moving right through some cards of each, then returns to the top. */
internal fun UiDevice.traverseHomeRows() {
    repeat(HOME_ROWS_TO_TRAVERSE) {
        pressAndAwaitFocusMove(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(CARDS_PER_ROW_TO_TRAVERSE) { pressAndAwaitFocusMove(KeyEvent.KEYCODE_DPAD_RIGHT) }
    }
    repeat(HOME_ROWS_TO_TRAVERSE) { pressAndAwaitFocusMove(KeyEvent.KEYCODE_DPAD_UP) }
}

/**
 * The same traversal without any UI queries between key presses, so a frame-timing measurement does not
 * include accessibility snapshots. Keys are paced like a person holding a remote.
 */
internal fun UiDevice.traverseHomeRowsWithoutQueries() {
    repeat(HOME_ROWS_TO_TRAVERSE) {
        pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        SystemClock.sleep(KEY_PACING_MS)
        repeat(CARDS_PER_ROW_TO_TRAVERSE) {
            pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            SystemClock.sleep(KEY_PACING_MS)
        }
    }
    repeat(HOME_ROWS_TO_TRAVERSE) {
        pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
        SystemClock.sleep(KEY_PACING_MS)
    }
    waitForIdle(SCREEN_CHANGE_TIMEOUT_MS)
}

/** Presses [keyCode] and waits (bounded) until the focus ring moves; at the end of a list it simply times out. */
private fun UiDevice.pressAndAwaitFocusMove(keyCode: Int) {
    val before = focusedBounds()
    pressKeyCode(keyCode)
    val deadline = SystemClock.uptimeMillis() + FOCUS_MOVE_TIMEOUT_MS
    do {
        waitForIdle(IDLE_POLL_MS)
    } while (focusedBounds() == before && SystemClock.uptimeMillis() < deadline)
}

private fun UiDevice.focusedBounds(): Rect? =
    try {
        findObject(By.pkg(TV_APP_PACKAGE).focused(true))?.visibleBounds
    } catch (_: StaleObjectException) {
        null
    }
