package dev.jellystack.design.tv

internal enum class TvPlayerPanel {
    NONE,
    MORE,
    AUDIO,
    SUBTITLES,
    QUALITY,
    SPEED,
    SYNCPLAY,
}

internal data class TvPlayerPanelNavigation(
    val current: TvPlayerPanel,
    val root: TvPlayerPanel,
    val restoreFocusTo: TvPlayerPanel? = null,
) {
    fun openMore(): TvPlayerPanelNavigation =
        copy(current = TvPlayerPanel.MORE, root = TvPlayerPanel.MORE, restoreFocusTo = null)

    fun openQuick(panel: TvPlayerPanel): TvPlayerPanelNavigation =
        copy(current = panel, root = TvPlayerPanel.NONE, restoreFocusTo = null)

    fun openFromMore(panel: TvPlayerPanel): TvPlayerPanelNavigation =
        copy(current = panel, root = TvPlayerPanel.MORE, restoreFocusTo = panel)

    fun back(): TvPlayerPanelNavigation =
        when {
            current == TvPlayerPanel.NONE -> this
            current != TvPlayerPanel.MORE && root == TvPlayerPanel.MORE -> copy(current = TvPlayerPanel.MORE)
            else -> closed()
        }

    companion object {
        fun closed(): TvPlayerPanelNavigation =
            TvPlayerPanelNavigation(current = TvPlayerPanel.NONE, root = TvPlayerPanel.NONE)
    }
}
