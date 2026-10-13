package dev.jellystack.design.tv

/** How Search leaves text editing after the query field lost focus. */
internal enum class TvSearchEditExit {
    /** Editing ended in place; Search moves focus to the source chips. */
    FOCUS_SOURCES,

    /** Left opened the navigation rail; focus stays in the rail. */
    KEEP_FOCUS,
}

/**
 * Ends text editing once the query field loses focus.
 *
 * Fire OS closes its keyboard on Back without delivering the key to the app and drops the field's focus, so the
 * screen's own Back handling never runs. Any focus loss while editing therefore ends editing; a pending Left
 * towards the rail keeps focus where the rail put it.
 */
internal class TvSearchQueryFocusTracker {
    private var focused = false
    private var leavingForRail = false

    fun onLeavingForRail() {
        leavingForRail = true
    }

    fun onFocusChanged(
        focused: Boolean,
        mode: TvSearchMode,
    ): TvSearchEditExit? {
        val lostFocus = this.focused && !focused
        this.focused = focused
        val leftForRail = leavingForRail
        if (focused || lostFocus) leavingForRail = false
        if (!lostFocus || mode != TvSearchMode.EDIT) return null
        return if (leftForRail) TvSearchEditExit.KEEP_FOCUS else TvSearchEditExit.FOCUS_SOURCES
    }
}
