package dev.jellystack.design.tv

/**
 * What the app-level Back handler needs to know about Home: whether focus last sat at the top (the hero) or down in
 * the rows, and how to bring it back to the top. The Home screen keeps it current; the dispatcher only reads it.
 */
internal class TvHomeBackState {
    /** True while the hero (or nothing yet) holds focus; false after focus moved into the rows. */
    var atTop: Boolean = true

    private var focusTop: (() -> Unit)? = null

    fun bind(focusTop: () -> Unit) {
        this.focusTop = focusTop
    }

    fun unbind() {
        focusTop = null
        atTop = true
    }

    /** Scrolls Home back to the top and focuses the hero; false when Home is not on screen. */
    fun returnToTop(): Boolean {
        val action = focusTop ?: return false
        action()
        return true
    }
}
