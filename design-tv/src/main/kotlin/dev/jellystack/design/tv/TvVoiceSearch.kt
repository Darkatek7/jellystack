package dev.jellystack.design.tv

enum class TvVoiceSearchAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

sealed interface TvVoiceSearchResult {
    data class Success(
        val text: String,
    ) : TvVoiceSearchResult

    data object Cancelled : TvVoiceSearchResult

    data class Error(
        val failure: TvVoiceSearchFailure = TvVoiceSearchFailure.FAILED,
    ) : TvVoiceSearchResult
}

/** Why voice search ended without a query; the UI words it in the app language. */
enum class TvVoiceSearchFailure {
    RECOGNIZER_UNAVAILABLE,
    NO_SPEECH,
    FAILED,
}

interface TvVoiceSearchPort {
    val availability: TvVoiceSearchAvailability

    fun launch(onResult: (TvVoiceSearchResult) -> Unit)
}

object UnsupportedTvVoiceSearch : TvVoiceSearchPort {
    override val availability: TvVoiceSearchAvailability = TvVoiceSearchAvailability.UNAVAILABLE

    override fun launch(onResult: (TvVoiceSearchResult) -> Unit) = Unit
}
