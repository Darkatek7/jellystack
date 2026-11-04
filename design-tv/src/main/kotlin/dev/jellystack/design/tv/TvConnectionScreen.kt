@file:Suppress("FunctionName", "FunctionNaming", "LongMethod", "LongParameterList")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.jellystack.core.server.JellyfinConnectionInput
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.JellyfinQuickConnectError
import dev.jellystack.core.server.JellyfinQuickConnectInput
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerConnectionCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun TvConnectionScreen(
    coordinator: ServerConnectionCoordinator,
    quickConnectCoordinator: JellyfinQuickConnectCoordinator,
    appVersion: String,
    strings: TvStrings,
    onConnected: (ManagedServer) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    existingServerId: String? = null,
    refreshExistingAccount: Boolean = false,
    initialDisplayName: String = "Jellyfin",
    initialBaseUrl: String = "",
    notice: String? = null,
) {
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(JellyfinSignInMethod.QUICK_CONNECT) }
    var fields by remember(existingServerId, initialDisplayName, initialBaseUrl) {
        mutableStateOf(TvConnectionFields(displayName = initialDisplayName, baseUrl = initialBaseUrl))
    }
    val baseUrl = fields.baseUrl
    var editingName by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<JellyfinQuickConnectState?>(null) }
    // A notice informs the viewer and is no failure, so it keeps the hint styling until the next attempt.
    var hint by remember(notice) { mutableStateOf(notice) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectJob by remember { mutableStateOf<Job?>(null) }
    val quickState = state
    val waitingCode = (quickState as? JellyfinQuickConnectState.Waiting)?.session?.code
    val busy = quickState.isStartingOrRegistering()
    val contentMode = connectionContentMode(waitingCode != null || busy)
    val focus = remember { TvConnectionFocus() }

    fun beginQuickConnect() {
        connectJob?.cancel()
        error = null
        hint = null
        connectJob =
            scope.launch {
                quickConnectCoordinator
                    .connect(
                        JellyfinQuickConnectInput(
                            name = fields.displayName,
                            baseUrl = baseUrl,
                            serverId = existingServerId,
                            appVersion = appVersion,
                            deviceName = "Jellystack TV",
                            refreshExistingAccount = refreshExistingAccount,
                        ),
                    ).collect { next ->
                        state = next
                        when (next) {
                            is JellyfinQuickConnectState.Connected -> onConnected(next.server)
                            is JellyfinQuickConnectState.Failed -> error = quickConnectErrorMessage(next.error, strings)
                            else -> Unit
                        }
                    }
            }
    }

    fun beginPasswordSignIn() {
        connectJob?.cancel()
        error = null
        hint = null
        connectJob =
            scope.launch {
                val connected =
                    coordinator.signInWithPassword(
                        JellyfinConnectionInput(
                            fields.displayName,
                            baseUrl,
                            fields.username,
                            fields.password,
                            serverId = existingServerId,
                            refreshExistingAccount = refreshExistingAccount,
                        ),
                    ) { message -> error = message ?: strings.signIn.jellyfinConnectionFailed }
                fields = fields.copy(password = "")
                connected?.let(onConnected)
            }
    }

    val actions =
        TvConnectionActions(
            onMethodChange = { method = it },
            onFieldChange = { field, value -> fields = fields.with(field, value) },
            onEditName = { editingName = true },
            onConnect = {
                when {
                    baseUrl.isBlank() -> error = strings.signIn.enterServerUrl
                    method == JellyfinSignInMethod.QUICK_CONNECT -> beginQuickConnect()
                    else -> beginPasswordSignIn()
                }
            },
            onBack = {
                if (waitingCode != null || busy) {
                    connectJob?.cancel()
                    connectJob = null
                    state = null
                    error = null
                } else {
                    onDismiss?.invoke()
                }
            },
        )
    TvConnectionContent(
        state =
            TvConnectionFormState(
                method = method,
                displayName = fields.displayName,
                baseUrl = baseUrl,
                username = fields.username,
                password = fields.password,
                editingName = editingName,
                waitingCode = waitingCode,
                busy = busy,
                hint = hint,
                error = error,
                canDismiss = onDismiss != null,
            ),
        actions = actions,
        focus = focus,
        strings = strings,
        modifier = modifier,
    )

    LaunchedEffect(method) {
        if (method == JellyfinSignInMethod.PASSWORD) {
            connectJob?.cancel()
            state = null
        }
    }
    TvConnectionInitialFocus(contentMode, method, baseUrl.isNotBlank(), editingName, focus)
    DisposableEffect(Unit) {
        onDispose {
            connectJob?.cancel()
        }
    }
}

/** Moves focus to the one useful control whenever the card changes between form, waiting and name editing. */
@Composable
private fun TvConnectionInitialFocus(
    contentMode: TvConnectionContentMode,
    method: JellyfinSignInMethod,
    hasAddress: Boolean,
    editingName: Boolean,
    focus: TvConnectionFocus,
) {
    LaunchedEffect(editingName) { if (editingName) focus.displayName.requestFocus() }
    LaunchedEffect(contentMode, method) {
        when {
            contentMode.showWaitingInstructions -> focus.waitingAction.requestFocus()
            !contentMode.showEditableFields -> Unit
            // Quick Connect needs nothing but the address: a known address goes straight to the one useful action.
            // A new server starts on the address field, which is read-only until OK, so the keyboard stays closed.
            hasAddress && method == JellyfinSignInMethod.QUICK_CONNECT -> focus.connect.requestFocus()
            else -> focus.url.requestFocus()
        }
    }
}

private fun JellyfinQuickConnectState?.isStartingOrRegistering(): Boolean =
    this is JellyfinQuickConnectState.Starting || this is JellyfinQuickConnectState.Registering

private fun quickConnectErrorMessage(
    error: JellyfinQuickConnectError,
    strings: TvStrings,
): String =
    when (error) {
        JellyfinQuickConnectError.DISABLED -> strings.signIn.quickConnectDisabled
        JellyfinQuickConnectError.EXPIRED -> strings.signIn.quickConnectExpired
        JellyfinQuickConnectError.TRANSPORT -> strings.signIn.serverUnreachable
        else -> strings.signIn.quickConnectFailed
    }

/** Signs in with a password; reports the server's message (or null) through [onFailure] and returns null on failure. */
private suspend fun ServerConnectionCoordinator.signInWithPassword(
    input: JellyfinConnectionInput,
    onFailure: (String?) -> Unit,
): ManagedServer? =
    try {
        connectJellyfin(input)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: IllegalStateException) {
        onFailure(failure.message)
        null
    } catch (failure: IllegalArgumentException) {
        onFailure(failure.message)
        null
    } catch (_: Throwable) {
        onFailure(null)
        null
    }
