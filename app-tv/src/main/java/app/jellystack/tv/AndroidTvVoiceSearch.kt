package app.jellystack.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dev.jellystack.design.tv.TvVoiceSearchAvailability
import dev.jellystack.design.tv.TvVoiceSearchPort
import dev.jellystack.design.tv.TvVoiceSearchResult

internal class AndroidTvVoiceSearch private constructor(
    override val availability: TvVoiceSearchAvailability,
    private val launchRecognizer: ((TvVoiceSearchResult) -> Unit) -> Unit,
) : TvVoiceSearchPort {
    override fun launch(onResult: (TvVoiceSearchResult) -> Unit) {
        if (availability == TvVoiceSearchAvailability.AVAILABLE) launchRecognizer(onResult)
    }

    companion object {
        fun create(activity: AppCompatActivity): AndroidTvVoiceSearch {
            val intent = voiceRecognitionIntent()
            val available = intent.resolveActivity(activity.packageManager) != null
            var pendingResult: ((TvVoiceSearchResult) -> Unit)? = null
            val launcher =
                activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val callback = pendingResult
                    pendingResult = null
                    callback?.invoke(
                        mapVoiceSearchResult(
                            successful = result.resultCode == Activity.RESULT_OK,
                            candidates = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
                        ),
                    )
                }
            return AndroidTvVoiceSearch(
                availability =
                    if (available) TvVoiceSearchAvailability.AVAILABLE else TvVoiceSearchAvailability.UNAVAILABLE,
                launchRecognizer = { callback ->
                    pendingResult = callback
                    try {
                        launcher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        pendingResult = null
                        callback(TvVoiceSearchResult.Error("No system voice recognizer is available"))
                    }
                },
            )
        }

        internal fun forTest(
            recognizerAvailable: Boolean,
            launchAction: () -> Unit,
        ): AndroidTvVoiceSearch =
            AndroidTvVoiceSearch(
                availability =
                    if (recognizerAvailable) {
                        TvVoiceSearchAvailability.AVAILABLE
                    } else {
                        TvVoiceSearchAvailability.UNAVAILABLE
                    },
                launchRecognizer = { launchAction() },
            )
    }
}

internal fun mapVoiceSearchResult(
    successful: Boolean,
    candidates: List<String>?,
): TvVoiceSearchResult {
    if (!successful) return TvVoiceSearchResult.Cancelled
    val text = candidates.orEmpty().firstOrNull { it.isNotBlank() }?.trim()
    return text?.let(TvVoiceSearchResult::Success) ?: TvVoiceSearchResult.Error("No speech was recognized")
}

private fun voiceRecognitionIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search Jellystack")
    }
