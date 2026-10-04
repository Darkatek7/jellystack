package dev.jellystack.design.tv

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.jellystack.players.AndroidPlayerEngine
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** TV focus tests need the same keyboard input mode as a D-pad controller. */
@Suppress("DEPRECATION")
internal fun useDpadInput() {
    InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
}

internal fun createTvComposeRule(): ComposeContentTestRule {
    val composeRule = createComposeRule()
    return object : ComposeContentTestRule by composeRule {
        override fun apply(
            base: Statement,
            description: Description,
        ): Statement =
            composeRule.apply(
                object : Statement() {
                    override fun evaluate() {
                        // The activity is already launched; configure input before test setup.
                        useDpadInput()
                        base.evaluate()
                    }
                },
                description,
            )
    }
}

/**
 * Player engines own an ExoPlayer, its threads, and a video surface. Release them together with the test
 * content so suites with many preview tests do not exhaust the emulator.
 */
@Composable
internal fun rememberTestPlayerEngine(context: Context): AndroidPlayerEngine {
    val engine = remember(context) { AndroidPlayerEngine(context) }
    DisposableEffect(engine) { onDispose { engine.release() } }
    return engine
}
