package app.jellystack.tv

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationEventOwnerTest {
    @Test
    fun mainActivityProvidesNavigationEventDispatcherOwner() {
        val ownerType = Class.forName("androidx.navigationevent.NavigationEventDispatcherOwner")
        val viewTreeOwnerType = Class.forName("androidx.navigationevent.ViewTreeNavigationEventDispatcherOwner")
        val findViewTreeOwner = viewTreeOwnerType.getMethod("get", android.view.View::class.java)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(
                    "MainActivity must provide NavigationEventDispatcherOwner for Navigation 3",
                    ownerType.isInstance(activity),
                )
                assertSame(
                    "MainActivity must install itself as the view-tree navigation event owner",
                    activity,
                    findViewTreeOwner.invoke(null, activity.window.decorView),
                )
            }
        }
    }
}
