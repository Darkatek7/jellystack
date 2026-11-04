@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jellystack.core.server.JellyfinSignInMethod

private val QR_SIZE = 208.dp

/** Smaller, still scannable QR code once large text needs the height for the code and its explanation. */
private val QR_SIZE_LARGE_TEXT = 176.dp
private const val LARGE_TEXT_FONT_SCALE = 1.2f

/** Everything the sign-in card shows; the stateful [TvConnectionScreen] owns it, goldens pass fixed values. */
internal data class TvConnectionFormState(
    val method: JellyfinSignInMethod = JellyfinSignInMethod.QUICK_CONNECT,
    val displayName: String = "Jellyfin",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val editingName: Boolean = false,
    /** The Quick Connect code while waiting for approval. */
    val waitingCode: String? = null,
    /** Quick Connect is being started or finished and shows no code yet. */
    val busy: Boolean = false,
    val hint: String? = null,
    val error: String? = null,
    val canDismiss: Boolean = false,
)

internal class TvConnectionActions(
    val onMethodChange: (JellyfinSignInMethod) -> Unit,
    val onFieldChange: (TvConnectionField, String) -> Unit,
    val onEditName: () -> Unit,
    val onConnect: () -> Unit,
    /** Cancels a pending Quick Connect code, otherwise leaves the screen. */
    val onBack: () -> Unit,
)

internal class TvConnectionFocus {
    val quickMethod = FocusRequester()
    val passwordMethod = FocusRequester()
    val displayName = FocusRequester()
    val url = FocusRequester()
    val username = FocusRequester()
    val password = FocusRequester()
    val connect = FocusRequester()
    val waitingAction = FocusRequester()
}

internal enum class TvConnectionField { URL, DISPLAY_NAME, USERNAME, PASSWORD }

/** The text the user typed; [with] replaces one field. */
internal data class TvConnectionFields(
    val displayName: String = "Jellyfin",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
) {
    fun with(
        field: TvConnectionField,
        value: String,
    ): TvConnectionFields =
        when (field) {
            TvConnectionField.URL -> copy(baseUrl = value)
            TvConnectionField.DISPLAY_NAME -> copy(displayName = value)
            TvConnectionField.USERNAME -> copy(username = value)
            TvConnectionField.PASSWORD -> copy(password = value)
        }
}

/** The text fields from top to bottom: the address first, the name only after "Edit name", account fields last. */
internal fun connectionFieldOrder(
    method: JellyfinSignInMethod,
    editingName: Boolean,
): List<TvConnectionField> =
    buildList {
        add(TvConnectionField.URL)
        if (editingName) add(TvConnectionField.DISPLAY_NAME)
        if (method == JellyfinSignInMethod.PASSWORD) {
            add(TvConnectionField.USERNAME)
            add(TvConnectionField.PASSWORD)
        }
    }

@Composable
internal fun TvConnectionContent(
    state: TvConnectionFormState,
    actions: TvConnectionActions,
    focus: TvConnectionFocus,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val inProgress = state.waitingCode != null || state.busy
    val contentMode = connectionContentMode(inProgress)
    val layout = connectionFormLayout(state.method, state.waitingCode != null)
    Box(modifier.fillMaxSize().background(TvBackground), contentAlignment = Alignment.Center) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = 86.dp,
                    vertical = layout.screenVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // While a code is shown the card needs the whole width for the code and the QR code.
            if (!inProgress) {
                Column(Modifier.weight(0.7f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(TV_BRAND_JELLYSTACK, color = TvText, fontSize = 50.sp, fontWeight = FontWeight.Bold)
                    Text(strings.signIn.tvTagline, color = TvTextMuted, fontSize = 22.sp)
                }
            }
            Column(
                Modifier
                    .weight(1.3f)
                    .background(TvSurface, RoundedCornerShape(28.dp))
                    .padding(horizontal = 34.dp, vertical = layout.cardVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
            ) {
                Text(strings.signIn.connectJellyfin, color = TvText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                // The heading stays put; a long message and the form scroll beneath it instead of being clipped.
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
                ) {
                    state.hint?.let { TvStatusMessage(it, TvMessageKind.HINT) }
                    state.error?.let { TvStatusMessage(it, TvMessageKind.ERROR) }
                    if (contentMode.showEditableFields) {
                        TvConnectionForm(state, actions, focus, strings, layout)
                    }
                    when {
                        state.waitingCode != null -> TvQuickConnectCode(state.waitingCode, state.baseUrl, strings)
                        state.busy -> Text(strings.loading, color = TvTextMuted, fontSize = 18.sp)
                    }
                    TvConnectionButtons(state, actions, focus, strings, contentMode)
                }
            }
        }
    }
}

@Composable
private fun TvConnectionForm(
    state: TvConnectionFormState,
    actions: TvConnectionActions,
    focus: TvConnectionFocus,
    strings: TvStrings,
    layout: TvConnectionFormLayout,
) {
    val methodFocus =
        if (state.method == JellyfinSignInMethod.QUICK_CONNECT) focus.quickMethod else focus.passwordMethod
    val order = connectionFieldOrder(state.method, state.editingName)

    fun requesterOf(field: TvConnectionField) =
        when (field) {
            TvConnectionField.URL -> focus.url
            TvConnectionField.DISPLAY_NAME -> focus.displayName
            TvConnectionField.USERNAME -> focus.username
            TvConnectionField.PASSWORD -> focus.password
        }

    fun fieldModifier(field: TvConnectionField): Modifier {
        val index = order.indexOf(field)
        return Modifier
            .heightIn(min = layout.textFieldHeight)
            .focusRequester(requesterOf(field))
            .focusProperties {
                up = order.getOrNull(index - 1)?.let(::requesterOf) ?: methodFocus
                down = order.getOrNull(index + 1)?.let(::requesterOf) ?: focus.connect
            }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvActionButton(
            strings.signIn.quickConnect,
            { actions.onMethodChange(JellyfinSignInMethod.QUICK_CONNECT) },
            modifier =
                Modifier
                    .focusRequester(focus.quickMethod)
                    .focusProperties {
                        right = focus.passwordMethod
                        down = focus.url
                    },
            primary = state.method == JellyfinSignInMethod.QUICK_CONNECT,
            selected = state.method == JellyfinSignInMethod.QUICK_CONNECT,
        )
        TvActionButton(
            strings.signIn.password,
            { actions.onMethodChange(JellyfinSignInMethod.PASSWORD) },
            modifier =
                Modifier
                    .focusRequester(focus.passwordMethod)
                    .focusProperties {
                        left = focus.quickMethod
                        down = focus.url
                    },
            primary = state.method == JellyfinSignInMethod.PASSWORD,
            selected = state.method == JellyfinSignInMethod.PASSWORD,
        )
    }
    TvTextField(
        state.baseUrl,
        { actions.onFieldChange(TvConnectionField.URL, it) },
        strings.signIn.serverUrl,
        modifier = fieldModifier(TvConnectionField.URL),
    )
    if (state.editingName) {
        TvTextField(
            state.displayName,
            { actions.onFieldChange(TvConnectionField.DISPLAY_NAME, it) },
            strings.signIn.displayName,
            modifier = fieldModifier(TvConnectionField.DISPLAY_NAME),
        )
    }
    if (state.method == JellyfinSignInMethod.PASSWORD) {
        TvTextField(
            state.username,
            { actions.onFieldChange(TvConnectionField.USERNAME, it) },
            strings.signIn.username,
            modifier = fieldModifier(TvConnectionField.USERNAME),
        )
        TvTextField(
            state.password,
            { actions.onFieldChange(TvConnectionField.PASSWORD, it) },
            strings.signIn.password,
            password = true,
            modifier = fieldModifier(TvConnectionField.PASSWORD),
        )
    }
}

/** The code on its own in large type, with a QR code that opens the server's Quick Connect page on a phone. */
@Composable
private fun TvQuickConnectCode(
    code: String,
    baseUrl: String,
    strings: TvStrings,
) {
    val pageUrl = remember(baseUrl) { quickConnectPageUrl(baseUrl) }
    val qrSize = if (LocalDensity.current.fontScale > LARGE_TEXT_FONT_SCALE) QR_SIZE_LARGE_TEXT else QR_SIZE
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                code,
                color = TvPurple,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(strings.signIn.quickConnectHelp, color = TvTextMuted, fontSize = 17.sp, lineHeight = 24.sp)
            Text(strings.signIn.enterCode, color = TvText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (pageUrl != null) {
            Column(
                Modifier.width(qrSize),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvQrCode(pageUrl, qrSize, strings.messages.quickConnectScan)
                Text(
                    strings.messages.quickConnectScan,
                    color = TvTextMuted,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvConnectionButtons(
    state: TvConnectionFormState,
    actions: TvConnectionActions,
    focus: TvConnectionFocus,
    strings: TvStrings,
    contentMode: TvConnectionContentMode,
) {
    // Wraps so longer labels (German "Neuen Code erstellen" next to "Abbrechen") keep their full text.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (contentMode.showConnectAction) {
            TvActionButton(
                label = strings.signIn.connect,
                primary = true,
                modifier = Modifier.focusRequester(focus.connect),
                onClick = actions.onConnect,
            )
            // The name rarely matters: it stays behind this action until someone wants to change it.
            if (!state.editingName) TvActionButton(strings.messages.editName, actions.onEditName)
        }
        if (state.waitingCode != null) {
            TvActionButton(
                strings.signIn.newCode,
                actions.onConnect,
                modifier = Modifier.focusRequester(focus.waitingAction),
            )
        }
        if (contentMode.showWaitingInstructions) TvActionButton(strings.cancel, actions.onBack)
        if (state.canDismiss) TvActionButton(strings.back, actions.onBack)
    }
}

@Composable
internal fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Read-only until OK is pressed: this value-based text field opens the keyboard as soon as it becomes editable
    // while focused, so the remote can walk through the form without dismissing the keyboard at every field.
    var editing by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = !editing,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
        colors = tvOutlinedTextFieldColors(),
        modifier =
            modifier
                .fillMaxWidth()
                // A minimum, not a fixed height: TV text sizes need more room than the phone defaults.
                .onFocusChanged { if (!it.hasFocus) editing = false }
                // Compose only reacts to D-pad events from non-virtual devices inside a text field. Handling them here
                // works for every remote and keyboard; left/right still move the cursor.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (editing) keyboardController?.show() else editing = true
                                true
                            }
                            else -> false
                        }
                    }
                },
    )
}

internal data class TvConnectionFormLayout(
    val screenVerticalPadding: Dp,
    val cardVerticalPadding: Dp,
    val itemSpacing: Dp,
    val textFieldHeight: Dp,
)

internal fun connectionFormLayout(
    method: JellyfinSignInMethod,
    waiting: Boolean = false,
): TvConnectionFormLayout =
    if (waiting) {
        TvConnectionFormLayout(
            screenVerticalPadding = 16.dp,
            cardVerticalPadding = 18.dp,
            itemSpacing = 12.dp,
            textFieldHeight = 64.dp,
        )
    } else if (method == JellyfinSignInMethod.PASSWORD) {
        TvConnectionFormLayout(
            screenVerticalPadding = 28.dp,
            cardVerticalPadding = 24.dp,
            itemSpacing = 10.dp,
            textFieldHeight = 56.dp,
        )
    } else {
        TvConnectionFormLayout(
            screenVerticalPadding = 28.dp,
            cardVerticalPadding = 24.dp,
            itemSpacing = 14.dp,
            textFieldHeight = 64.dp,
        )
    }
