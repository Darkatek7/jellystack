package dev.jellystack.design.tv

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
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
