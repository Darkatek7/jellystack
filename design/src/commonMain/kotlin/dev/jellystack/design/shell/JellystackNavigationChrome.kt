package dev.jellystack.design.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jellystack.design.ShellTestTags
import dev.jellystack.design.TestTags
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.theme.JellystackLayoutTokens
import dev.jellystack.players.cast.CastConnectionState
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.nav_admin
import jellystack_mobile.design.generated.resources.nav_discover
import jellystack_mobile.design.generated.resources.nav_home
import jellystack_mobile.design.generated.resources.nav_library
import jellystack_mobile.design.generated.resources.navigate_back
import jellystack_mobile.design.generated.resources.open_settings_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun JellystackTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    castState: CastConnectionState,
    renderCastButton: @Composable (CastConnectionState) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDescription = stringResource(Res.string.navigate_back)
    val settingsDescription = stringResource(Res.string.open_settings_description)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                .statusBarsPadding(),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showBack) {
                IconButton(
                    modifier =
                        Modifier
                            .sizeIn(
                                minWidth = JellystackLayoutTokens.minimumTouchTarget,
                                minHeight = JellystackLayoutTokens.minimumTouchTarget,
                            ).semantics {
                                role = Role.Button
                                contentDescription = backDescription
                            },
                    onClick = onBack,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            renderCastButton(castState)
            IconButton(
                modifier =
                    Modifier
                        .sizeIn(
                            minWidth = JellystackLayoutTokens.minimumTouchTarget,
                            minHeight = JellystackLayoutTokens.minimumTouchTarget,
                        ).testTag(ShellTestTags.OPEN_SETTINGS),
                onClick = onOpenSettings,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = settingsDescription,
                )
            }
        }
    }
}

@Composable
internal fun JellystackBottomDock(
    selected: PrimaryDestination,
    showLabels: Boolean,
    destinations: List<PrimaryDestination> = PrimaryDestination.entries.filterNot { it == PrimaryDestination.Admin },
    onSelect: (PrimaryDestination) -> Unit,
) {
    NavigationBar(
        modifier =
            Modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = JellystackLayoutTokens.screenPaddingCompact,
                    vertical = JellystackLayoutTokens.dockOuterSpacing,
                ).clip(RoundedCornerShape(JellystackLayoutTokens.dockRadius))
                .testTag(ShellTestTags.BOTTOM_DOCK),
    ) {
        destinations.forEach { destination ->
            val label = destinationLabel(destination)
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { PrimaryDestinationIcon(destination) },
                label = if (showLabels) ({ Text(label) }) else null,
                alwaysShowLabel = showLabels,
                modifier =
                    Modifier
                        .heightIn(min = JellystackLayoutTokens.minimumTouchTarget)
                        .testTag(destination.testTag)
                        .then(
                            if (showLabels) {
                                Modifier
                            } else {
                                Modifier.semantics { contentDescription = label }
                            },
                        ),
            )
        }
    }
}

@Composable
internal fun JellystackNavigationRail(
    selected: PrimaryDestination,
    destinations: List<PrimaryDestination> = PrimaryDestination.entries.filterNot { it == PrimaryDestination.Admin },
    onSelect: (PrimaryDestination) -> Unit,
) {
    NavigationRail(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(JellystackLayoutTokens.railWidth)
                .testTag(ShellTestTags.NAVIGATION_RAIL),
    ) {
        destinations.forEach { destination ->
            val label = destinationLabel(destination)
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { PrimaryDestinationIcon(destination) },
                label = { Text(label) },
                alwaysShowLabel = true,
                modifier =
                    Modifier
                        .heightIn(min = JellystackLayoutTokens.minimumTouchTarget)
                        .testTag(destination.testTag),
            )
        }
    }
}

@Composable
private fun destinationLabel(destination: PrimaryDestination): String =
    stringResource(
        when (destination) {
            PrimaryDestination.Home -> Res.string.nav_home
            PrimaryDestination.Library -> Res.string.nav_library
            PrimaryDestination.Discover -> Res.string.nav_discover
            PrimaryDestination.Admin -> Res.string.nav_admin
        },
    )

@Composable
private fun PrimaryDestinationIcon(destination: PrimaryDestination) {
    Icon(
        imageVector = destination.icon,
        contentDescription = null,
    )
}

private val PrimaryDestination.icon: ImageVector
    get() =
        when (this) {
            PrimaryDestination.Home -> Icons.Filled.Home
            PrimaryDestination.Library -> Icons.Filled.Folder
            PrimaryDestination.Discover -> Icons.Filled.Movie
            PrimaryDestination.Admin -> Icons.Filled.AdminPanelSettings
        }

private val PrimaryDestination.testTag: String
    get() =
        when (this) {
            PrimaryDestination.Home -> TestTags.PRIMARY_HOME
            PrimaryDestination.Library -> TestTags.PRIMARY_LIBRARY
            PrimaryDestination.Discover -> TestTags.PRIMARY_DISCOVER
            PrimaryDestination.Admin -> TestTags.PRIMARY_ADMIN
        }
