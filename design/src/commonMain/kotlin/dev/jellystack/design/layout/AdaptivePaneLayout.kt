package dev.jellystack.design.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.jellystack.design.ShellTestTags
import dev.jellystack.design.shell.ShellPaneMode
import dev.jellystack.design.theme.JellystackLayoutTokens
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.select_item_prompt
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdaptivePaneLayout(
    paneMode: ShellPaneMode,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    primaryContent: @Composable (PaddingValues) -> Unit,
    secondaryContent: (@Composable (PaddingValues) -> Unit)? = null,
) {
    val profile = LocalResponsiveProfile.current
    when {
        !profile.isExpanded -> {
            val content = secondaryContent ?: primaryContent
            val paneTag =
                if (secondaryContent == null) {
                    ShellTestTags.PRIMARY_PANE
                } else {
                    ShellTestTags.SECONDARY_PANE
                }
            Box(
                modifier = modifier.fillMaxSize().testTag(paneTag),
            ) {
                content(contentPadding)
            }
        }

        paneMode == ShellPaneMode.Single -> {
            Box(
                modifier = modifier.fillMaxSize().testTag(ShellTestTags.PRIMARY_PANE),
            ) {
                primaryContent(contentPadding)
            }
        }

        else -> {
            Row(modifier = modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(PRIMARY_PANE_WEIGHT)
                            .testTag(ShellTestTags.PRIMARY_PANE),
                ) {
                    primaryContent(contentPadding)
                }

                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = JellystackLayoutTokens.paneGap,
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(SECONDARY_PANE_WEIGHT)
                            .testTag(ShellTestTags.SECONDARY_PANE),
                ) {
                    if (secondaryContent == null) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(contentPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.select_item_prompt),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        secondaryContent(contentPadding)
                    }
                }
            }
        }
    }
}

private const val PRIMARY_PANE_WEIGHT = 42f
private const val SECONDARY_PANE_WEIGHT = 58f
