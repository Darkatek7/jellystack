package dev.jellystack.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jellystack.core.server.JellyfinQuickConnectError
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.cancel
import jellystack_mobile.design.generated.resources.quick_connect
import jellystack_mobile.design.generated.resources.quick_connect_authentication_failed
import jellystack_mobile.design.generated.resources.quick_connect_code_label
import jellystack_mobile.design.generated.resources.quick_connect_creating
import jellystack_mobile.design.generated.resources.quick_connect_disabled
import jellystack_mobile.design.generated.resources.quick_connect_expired
import jellystack_mobile.design.generated.resources.quick_connect_instructions
import jellystack_mobile.design.generated.resources.quick_connect_invalid_response
import jellystack_mobile.design.generated.resources.quick_connect_new_code
import jellystack_mobile.design.generated.resources.quick_connect_registering
import jellystack_mobile.design.generated.resources.quick_connect_transport_error
import jellystack_mobile.design.generated.resources.quick_connect_use_password
import jellystack_mobile.design.generated.resources.quick_connect_waiting
import jellystack_mobile.design.generated.resources.username_and_password
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JellyfinSignInMethodSelector(
    selected: JellyfinSignInMethod,
    onSelected: (JellyfinSignInMethod) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val methods = JellyfinSignInMethod.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(56.dp)) {
        methods.forEachIndexed { index, method ->
            SegmentedButton(
                selected = selected == method,
                onClick = { onSelected(method) },
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = methods.size),
                label = {
                    Text(
                        when (method) {
                            JellyfinSignInMethod.QUICK_CONNECT -> stringResource(Res.string.quick_connect)
                            JellyfinSignInMethod.PASSWORD -> stringResource(Res.string.username_and_password)
                        },
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

@Composable
internal fun JellyfinQuickConnectStatus(
    state: JellyfinQuickConnectState,
    onUsePassword: () -> Unit,
    onNewCode: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            JellyfinQuickConnectState.Starting -> {
                ProgressStatus(stringResource(Res.string.quick_connect_creating))
            }
            is JellyfinQuickConnectState.Waiting -> {
                Text(
                    text = stringResource(Res.string.quick_connect_code_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { heading() },
                )
                SelectionContainer {
                    Text(
                        text = state.session.code,
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier.semantics {
                                liveRegion = LiveRegionMode.Assertive
                            },
                    )
                }
                Text(
                    text = stringResource(Res.string.quick_connect_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgressStatus(stringResource(Res.string.quick_connect_waiting))
            }
            JellyfinQuickConnectState.Registering -> {
                ProgressStatus(stringResource(Res.string.quick_connect_registering))
            }
            is JellyfinQuickConnectState.Failed -> {
                Text(
                    text = quickConnectErrorMessage(state.error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            is JellyfinQuickConnectState.Connected -> Unit
        }

        if (state !is JellyfinQuickConnectState.Connected) {
            OutlinedButton(
                onClick = onUsePassword,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(Res.string.quick_connect_use_password))
            }
            if (
                state is JellyfinQuickConnectState.Waiting ||
                state is JellyfinQuickConnectState.Failed
            ) {
                OutlinedButton(
                    onClick = onNewCode,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(Res.string.quick_connect_new_code))
                }
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(Res.string.cancel))
            }
        }
    }
}

@Composable
private fun ProgressStatus(label: String) {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun quickConnectErrorMessage(error: JellyfinQuickConnectError): String =
    stringResource(
        when (error) {
            JellyfinQuickConnectError.DISABLED -> Res.string.quick_connect_disabled
            JellyfinQuickConnectError.EXPIRED -> Res.string.quick_connect_expired
            JellyfinQuickConnectError.TRANSPORT -> Res.string.quick_connect_transport_error
            JellyfinQuickConnectError.INVALID_RESPONSE -> Res.string.quick_connect_invalid_response
            JellyfinQuickConnectError.AUTHENTICATION_FAILED -> Res.string.quick_connect_authentication_failed
        },
    )
