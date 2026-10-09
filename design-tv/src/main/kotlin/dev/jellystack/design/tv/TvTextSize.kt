package dev.jellystack.design.tv

import androidx.compose.ui.unit.sp

/**
 * Type scale of the TV UI, designed for 10-foot viewing at the 960×540dp reference size.
 * New and touched code uses these sizes instead of inline `sp` values.
 */
internal object TvTextSize {
    /** Hero and detail titles without a logo. */
    val Display = 46.sp

    /** Screen titles such as Library, Search, and Settings. */
    val Headline = 38.sp

    /** Dialog and panel titles. */
    val Title = 30.sp

    /** Row and section headings. */
    val SectionTitle = 20.sp

    /** Hero metadata and overview text. */
    val BodyLarge = 19.sp

    /** Overlay titles on portrait cards and list rows. */
    val Subtitle = 18.sp

    /** Default body text and button labels. */
    val Body = 16.sp

    /** Titles in card metadata bands. */
    val CardTitle = 15.sp

    /** Secondary lines under overlay titles. */
    val BodySmall = 14.sp

    /** Labels of compact icon buttons. */
    val Label = 13.sp

    /** Card subtitles, badges, and other small metadata. */
    val Caption = 12.sp
}
