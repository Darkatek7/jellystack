@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
)

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jellystack.core.server.JellyfinConnectionInput
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.JellyfinQuickConnectError
import dev.jellystack.core.server.JellyfinQuickConnectInput
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.core.server.ServerConnectionCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun TvConnectionScreen(
    coordinator: ServerConnectionCoordinator,
    quickConnectCoordinator: JellyfinQuickConnectCoordinator,
    appVersion: String,
    strings: TvStrings,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    existingServerId: String? = null,
    initialDisplayName: String = "Jellyfin",
    initialBaseUrl: String = "",
) {
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(JellyfinSignInMethod.QUICK_CONNECT) }
    var displayName by remember(existingServerId, initialDisplayName) { mutableStateOf(initialDisplayName) }
    var baseUrl by remember(existingServerId, initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<JellyfinQuickConnectState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectJob by remember { mutableStateOf<Job?>(null) }
    val quickConnectInProgress =
        state is JellyfinQuickConnectState.Starting ||
            state is JellyfinQuickConnectState.Waiting ||
            state is JellyfinQuickConnectState.Registering
    val contentMode = connectionContentMode(quickConnectInProgress)
    val layout = connectionFormLayout(method)
    val quickMethodFocus = remember { FocusRequester() }
    val passwordMethodFocus = remember { FocusRequester() }
    val displayNameFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    val waitingActionFocus = remember { FocusRequester() }

    fun beginQuickConnect() {
        connectJob?.cancel()
        error = null
        connectJob =
            scope.launch {
                quickConnectCoordinator
                    .connect(
                        JellyfinQuickConnectInput(
                            name = displayName,
                            baseUrl = baseUrl,
                            serverId = existingServerId,
                            appVersion = appVersion,
                            deviceName = "Jellystack TV",
                        ),
                    ).collect { next ->
                        state = next
                        when (next) {
                            is JellyfinQuickConnectState.Connected -> onConnected()
                            is JellyfinQuickConnectState.Failed ->
                                error =
                                    when (next.error) {
                                        JellyfinQuickConnectError.DISABLED -> strings.quickConnectDisabled
                                        JellyfinQuickConnectError.EXPIRED -> strings.quickConnectExpired
                                        JellyfinQuickConnectError.TRANSPORT -> strings.serverUnreachable
                                        else -> strings.quickConnectFailed
                                    }
                            else -> Unit
                        }
                    }
            }
    }

    Box(modifier.fillMaxSize().background(TvBackground), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 86.dp, vertical = layout.screenVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Jellystack", color = TvText, fontSize = 50.sp, fontWeight = FontWeight.Bold)
                Text(strings.tvBeta, color = TvPurple, fontSize = 22.sp)
                Text(
                    strings.tvTagline,
                    color = TvTextMuted,
                    fontSize = 22.sp,
                )
            }
            Column(
                Modifier
                    .weight(1.15f)
                    .background(TvSurface, RoundedCornerShape(28.dp))
                    .padding(horizontal = 34.dp, vertical = layout.cardVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
            ) {
                Text(strings.connectJellyfin, color = TvText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (contentMode.showEditableFields) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvActionButton(
                            strings.quickConnect,
                            { method = JellyfinSignInMethod.QUICK_CONNECT },
                            modifier =
                                Modifier
                                    .focusRequester(quickMethodFocus)
                                    .focusProperties {
                                        right = passwordMethodFocus
                                        down = displayNameFocus
                                    },
                            primary = method == JellyfinSignInMethod.QUICK_CONNECT,
                            selected = method == JellyfinSignInMethod.QUICK_CONNECT,
                        )
                        TvActionButton(
                            strings.password,
                            { method = JellyfinSignInMethod.PASSWORD },
                            modifier =
                                Modifier
                                    .focusRequester(passwordMethodFocus)
                                    .focusProperties {
                                        left = quickMethodFocus
                                        down = displayNameFocus
                                    },
                            primary = method == JellyfinSignInMethod.PASSWORD,
                            selected = method == JellyfinSignInMethod.PASSWORD,
                        )
                    }
                    TvTextField(
                        displayName,
                        { displayName = it },
                        strings.displayName,
                        height = layout.textFieldHeight,
                        modifier =
                            Modifier
                                .focusRequester(displayNameFocus)
                                .focusProperties {
                                    up = if (method == JellyfinSignInMethod.QUICK_CONNECT) quickMethodFocus else passwordMethodFocus
                                    down = urlFocus
                                },
                    )
                    TvTextField(
                        baseUrl,
                        { baseUrl = it },
                        strings.serverUrl,
                        height = layout.textFieldHeight,
                        modifier =
                            Modifier
                                .focusRequester(urlFocus)
                                .focusProperties {
                                    up = displayNameFocus
                                    down = if (method == JellyfinSignInMethod.PASSWORD) usernameFocus else connectFocus
                                },
                    )
                    if (method == JellyfinSignInMethod.PASSWORD) {
                        TvTextField(
                            username,
                            { username = it },
                            strings.username,
                            height = layout.textFieldHeight,
                            modifier =
                                Modifier
                                    .focusRequester(usernameFocus)
                                    .focusProperties {
                                        up = urlFocus
                                        down = passwordFocus
                                    },
                        )
                        TvTextField(
                            password,
                            { password = it },
                            strings.password,
                            password = true,
                            height = layout.textFieldHeight,
                            modifier =
                                Modifier
                                    .focusRequester(passwordFocus)
                                    .focusProperties {
                                        up = usernameFocus
                                        down = connectFocus
                                    },
                        )
                    }
                }
                when (val quickState = state) {
                    is JellyfinQuickConnectState.Waiting -> {
                        Text(quickState.session.code, color = TvPurple, fontSize = 54.sp, fontWeight = FontWeight.Bold)
                        Text(strings.quickConnectHelp, color = TvTextMuted, fontSize = 17.sp)
                        Text(strings.enterCode, color = TvText, fontSize = 16.sp)
                    }
                    is JellyfinQuickConnectState.Starting,
                    is JellyfinQuickConnectState.Registering,
                    -> Text(strings.loading, color = TvTextMuted)
                    else -> Unit
                }
                error?.let {
                    Text(
                        it,
                        color =
                            androidx.compose.ui.graphics
                                .Color(0xFFFFA59E),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (contentMode.showConnectAction) {
                        TvActionButton(
                            label = strings.connect,
                            primary = true,
                            modifier = Modifier.focusRequester(connectFocus),
                            onClick = {
                                if (baseUrl.isBlank()) {
                                    error = strings.enterServerUrl
                                } else if (method == JellyfinSignInMethod.QUICK_CONNECT) {
                                    beginQuickConnect()
                                } else {
                                    connectJob?.cancel()
                                    connectJob =
                                        scope.launch {
                                            runCatching {
                                                coordinator.connectJellyfin(
                                                    JellyfinConnectionInput(
                                                        displayName,
                                                        baseUrl,
                                                        username,
                                                        password,
                                                        serverId = existingServerId,
                                                    ),
                                                )
                                            }.onSuccess { onConnected() }
                                                .onFailure { error = it.message ?: strings.jellyfinConnectionFailed }
                                            password = ""
                                        }
                                }
                            },
                        )
                    }
                    if (state is JellyfinQuickConnectState.Waiting) {
                        TvActionButton(
                            strings.newCode,
                            ::beginQuickConnect,
                            modifier = Modifier.focusRequester(waitingActionFocus),
                        )
                    }
                    if (contentMode.showWaitingInstructions) {
                        TvActionButton(
                            strings.cancel,
                            {
                                connectJob?.cancel()
                                connectJob = null
                                state = null
                                error = null
                            },
                        )
                    }
                    onDismiss?.let { dismiss -> TvActionButton(strings.back, dismiss) }
                }
            }
        }
    }

    LaunchedEffect(method) {
        if (method == JellyfinSignInMethod.PASSWORD) {
            connectJob?.cancel()
            state = null
        }
    }
    LaunchedEffect(contentMode, method) {
        if (contentMode.showWaitingInstructions) {
            waitingActionFocus.requestFocus()
        } else if (contentMode.showEditableFields) {
            quickMethodFocus.requestFocus()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            connectJob?.cancel()
            password = ""
        }
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    height: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
        colors = tvOutlinedTextFieldColors(),
        modifier = modifier.fillMaxWidth().height(height),
    )
}

internal data class TvConnectionFormLayout(
    val screenVerticalPadding: Dp,
    val cardVerticalPadding: Dp,
    val itemSpacing: Dp,
    val textFieldHeight: Dp,
)

internal fun connectionFormLayout(method: JellyfinSignInMethod): TvConnectionFormLayout =
    if (method == JellyfinSignInMethod.PASSWORD) {
        TvConnectionFormLayout(
            screenVerticalPadding = 28.dp,
            cardVerticalPadding = 24.dp,
            itemSpacing = 10.dp,
            textFieldHeight = 56.dp,
        )
    } else {
        TvConnectionFormLayout(
            screenVerticalPadding = 54.dp,
            cardVerticalPadding = 34.dp,
            itemSpacing = 14.dp,
            textFieldHeight = 64.dp,
        )
    }
