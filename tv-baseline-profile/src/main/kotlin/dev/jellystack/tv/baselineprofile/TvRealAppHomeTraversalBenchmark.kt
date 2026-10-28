package dev.jellystack.tv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TRAVERSAL_ITERATIONS = 10

/**
 * D-pad traversal of the production Home screen (rows and cards backed by the signed-in server). The launch
 * happens in the setup block, so only the key-driven focus movement is measured.
 */
@RunWith(AndroidJUnit4::class)
class TvRealAppHomeTraversalBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeTraversalWithoutCompilation() = traverseHome(CompilationMode.None())

    @Test
    fun homeTraversalWithBaselineProfile() = traverseHome(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun traverseHome(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TV_APP_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = null,
            iterations = TRAVERSAL_ITERATIONS,
            setupBlock = {
                pressHome()
                startTvAppAtHome()
            },
        ) {
            device.traverseHomeRowsWithoutQueries()
        }
}
