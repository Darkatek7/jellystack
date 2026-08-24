package app.jellystack.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.jellystack.design.tv.JellystackTvPerformanceFixture

class TvBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JellystackTvPerformanceFixture() }
    }
}
