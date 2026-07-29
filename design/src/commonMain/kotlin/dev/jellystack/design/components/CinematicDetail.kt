package dev.jellystack.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jellystack.design.layout.LocalResponsiveProfile

internal object CinematicDetailColors {
    val background = Color(0xFF08090F)
    val surface = Color(0xFF171721)
    val surfaceHigh = Color(0xFF22212E)
    val primary = Color(0xFFC7B7FF)
    val primaryContainer = Color(0xFF57408F)
    val onSurface = Color(0xFFF5F1FF)
    val muted = Color(0xFFBEB8C9)
}

internal data class CinematicDetailTab<T>(
    val key: T,
    val label: String,
    val testTag: String,
)

@Composable
internal fun CinematicDetailTheme(content: @Composable () -> Unit) {
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val cinematicScheme =
        darkColorScheme(
            primary = CinematicDetailColors.primary,
            onPrimary = Color(0xFF25124E),
            primaryContainer = CinematicDetailColors.primaryContainer,
            onPrimaryContainer = Color.White,
            background = CinematicDetailColors.background,
            onBackground = CinematicDetailColors.onSurface,
            surface = CinematicDetailColors.surface,
            onSurface = CinematicDetailColors.onSurface,
            surfaceVariant = CinematicDetailColors.surfaceHigh,
            onSurfaceVariant = CinematicDetailColors.muted,
            outline = Color(0xFF777181),
        )
    MaterialTheme(
        colorScheme = cinematicScheme,
        typography = parentTypography,
        shapes = parentShapes,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides CinematicDetailColors.onSurface,
            content = content,
        )
    }
}

@Composable
internal fun cinematicHeroHeight(): Dp {
    val profile = LocalResponsiveProfile.current
    return when {
        profile.isShortHeight -> 280.dp
        profile.isExpanded -> 470.dp
        else -> 330.dp
    }
}

@Composable
internal fun CinematicHeroStage(
    heroHeight: Dp,
    artwork: @Composable () -> Unit,
    identity: @Composable () -> Unit,
    commandDeck: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    heroModifier: Modifier = Modifier,
    topStart: (@Composable () -> Unit)? = null,
    topEnd: (@Composable () -> Unit)? = null,
) {
    val profile = LocalResponsiveProfile.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(heroHeight + 76.dp),
    ) {
        Box(
            modifier =
                heroModifier
                    .fillMaxWidth()
                    .height(heroHeight),
        ) {
            artwork()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.05f),
                                0.48f to Color.Black.copy(alpha = 0.12f),
                                0.78f to CinematicDetailColors.background.copy(alpha = 0.62f),
                                1f to CinematicDetailColors.background,
                            ),
                        ),
            )
            topStart?.let { slot ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 16.dp),
                ) {
                    slot()
                }
            }
            topEnd?.let { slot ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 16.dp),
                ) {
                    slot()
                }
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = profile.horizontalContentPadding)
                        .padding(bottom = 66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                identity()
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = profile.horizontalContentPadding),
        ) {
            commandDeck()
        }
    }
}

@Composable
internal fun CinematicCommandDeckSurface(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        color = CinematicDetailColors.surfaceHigh,
        contentColor = CinematicDetailColors.onSurface,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 18.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

@Composable
internal fun <T> CinematicDetailTabs(
    tabs: List<CinematicDetailTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.key == selected
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable { onSelect(tab.key) }
                        .testTag(tab.testTag)
                        .semantics {
                            this.selected = isSelected
                            role = Role.Tab
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                androidx.compose.material3.Text(
                    text = tab.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color =
                        if (isSelected) {
                            CinematicDetailColors.onSurface
                        } else {
                            CinematicDetailColors.muted
                        },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (isSelected) {
                                    CinematicDetailColors.primary
                                } else {
                                    Color.Transparent
                                },
                            ),
                )
            }
        }
    }
}
