package dev.jellystack.players

import kotlin.math.roundToInt

/**
 * Frame rate with up to three decimals and no trailing zeros ("23.976 fps", "25 fps", "59.94 fps"),
 * so fractional broadcast rates stay distinguishable from their whole-number neighbours.
 */
fun formatFrameRate(framesPerSecond: Float): String? {
    if (!framesPerSecond.isFinite() || framesPerSecond <= 0f) return null
    val thousandths = (framesPerSecond * THOUSAND).roundToInt()
    val whole = thousandths / THOUSAND
    val fraction =
        (thousandths % THOUSAND)
            .toString()
            .padStart(FRACTION_DIGITS, '0')
            .trimEnd('0')
    return if (fraction.isEmpty()) "$whole fps" else "$whole.$fraction fps"
}

private const val THOUSAND = 1_000
private const val FRACTION_DIGITS = 3
