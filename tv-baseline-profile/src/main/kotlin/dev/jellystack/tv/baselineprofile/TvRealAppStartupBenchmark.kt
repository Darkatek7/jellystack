package dev.jellystack.tv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STARTUP_ITERATIONS = 8

/**
 * Cold start of the production [TV_APP_PACKAGE] app (not the synthetic fixture), measured with nothing
 * precompiled and with the app's baseline profile compiled in. This is the Fire TV situation: no Play cloud
 * profile, so the baseline profile packaged in the APK is all the app gets.
 *
 * timeToInitialDisplay ends at the first frame (the loading screen); Home content arrives after it.
 */
@RunWith(AndroidJUnit4::class)
class TvRealAppStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutCompilation() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun coldStart(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TV_APP_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startTvAppAtHome()
        }
}
