package dev.jellystack.tv.benchmark

import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

private const val TARGET_PACKAGE = "app.jellystack.mobile"
private const val BENCHMARK_ACTIVITY = "app.jellystack.tv.TvBenchmarkActivity"
private const val ACTION_COUNT = 100
private const val P95_FRAME_BUDGET_NANOS = 33_300_000L

@RunWith(AndroidJUnit4::class)
class TvBrowseBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun cinematicTraversalFrames() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = StartupMode.WARM,
            iterations = 3,
            setupBlock = { pressHome() },
        ) {
            startFixture()
            traverse(device)
            assertTraversalContract(device)
        }

    @Test
    fun cinematicTraversalP95AndFocusContract() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$BENCHMARK_ACTIVITY")
        assertNotNull(
            device.wait(
                androidx.test.uiautomator.Until
                    .findObject(By.res("cinematic-benchmark-root")),
                10_000,
            ),
        )
        assertTrue("Cinematic card did not receive initial focus", device.waitForFocusedCard())

        device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE reset")
        traverse(device)
        device.waitForIdle()

        val frames = parseFrameDurations(device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE framestats"))
        assertTrue("Expected rendered frames after D-pad traversal", frames.isNotEmpty())
        val p95 = percentile95(frames)
        if (!device.isEmulator()) {
            assertTrue("p95 frame time ${p95 / 1_000_000.0}ms exceeded 33.3ms", p95 <= P95_FRAME_BUDGET_NANOS)
        }
        assertTraversalContract(device)
    }
}

private fun MacrobenchmarkScope.startFixture() {
    startActivityAndWait(
        Intent().apply {
            component = ComponentName(TARGET_PACKAGE, BENCHMARK_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        },
    )
    device.wait(
        androidx.test.uiautomator.Until
            .findObject(By.res("cinematic-benchmark-root")),
        10_000,
    )
    check(device.waitForFocusedCard()) { "Cinematic card did not receive initial focus" }
}

private fun traverse(device: UiDevice) {
    val keyCodes =
        List(ACTION_COUNT) { index ->
            if ((index / 10) % 2 == 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        }
    device.executeShellCommand("input dpad keyevent ${keyCodes.joinToString(" ")}")
    device.waitForIdle()
}

private fun assertTraversalContract(device: UiDevice) {
    val root = device.findObject(By.res("cinematic-benchmark-root"))
    assertNotNull("Cinematic surface became blank", root)
    assertNotNull("Backdrop layer disappeared", device.findObject(By.res("cinematic-backdrop")))
    assertEquals("activations=0;queuedBackdrops=0", root.contentDescription)
    val focusedCards = root.findObjects(By.focused(true)).count { it.resourceName?.contains("cinematic-card-") == true }
    assertEquals("Exactly one cinematic card must retain focus", 1, focusedCards)
}

private fun UiDevice.isEmulator(): Boolean = executeShellCommand("getprop ro.kernel.qemu").trim() == "1"

private fun UiDevice.waitForFocusedCard(): Boolean {
    wait(
        androidx.test.uiautomator.Until
            .hasObject(By.focused(true)),
        10_000,
    )
    return findObjects(By.focused(true)).any { it.resourceName?.contains("cinematic-card-") == true }
}

private fun parseFrameDurations(output: String): List<Long> {
    var headers: List<String>? = null
    return buildList {
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("Flags,IntendedVsync,")) {
                headers = line.split(',')
            } else if (headers != null && line.firstOrNull()?.isDigit() == true) {
                val values = line.split(',')
                val intendedIndex = headers.orEmpty().indexOf("IntendedVsync")
                val completedIndex = headers.orEmpty().indexOf("FrameCompleted")
                if (intendedIndex >= 0 && completedIndex >= 0 && values.size > completedIndex) {
                    val intended = values[intendedIndex].toLongOrNull() ?: return@forEach
                    val completed = values[completedIndex].toLongOrNull() ?: return@forEach
                    (completed - intended).takeIf { it in 1..999_999_999 }?.let(::add)
                }
            }
        }
    }
}

private fun percentile95(values: List<Long>): Long {
    val sorted = values.sorted()
    val index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}
