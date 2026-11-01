package dev.jellystack.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.RequestProviderType
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerAddressValidation
import dev.jellystack.design.components.InsecureHttpWarning
import dev.jellystack.design.components.JellyfinQuickConnectStatus
import dev.jellystack.design.components.JellyfinSignInMethodSelector
import dev.jellystack.design.components.SeerrCompatibilityNotice
import dev.jellystack.design.components.SeerrSignInMethodSelector
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.base_url
import jellystack_mobile.design.generated.resources.cancel
import jellystack_mobile.design.generated.resources.connect_jellyfin
import jellystack_mobile.design.generated.resources.connect_mediarium
import jellystack_mobile.design.generated.resources.connect_seerr
import jellystack_mobile.design.generated.resources.connect_server
import jellystack_mobile.design.generated.resources.email
import jellystack_mobile.design.generated.resources.hide_password
import jellystack_mobile.design.generated.resources.onboarding_saving
import jellystack_mobile.design.generated.resources.onboarding_url_error
import jellystack_mobile.design.generated.resources.password
import jellystack_mobile.design.generated.resources.password_optional
import jellystack_mobile.design.generated.resources.quick_connect_description
import jellystack_mobile.design.generated.resources.request_provider
import jellystack_mobile.design.generated.resources.seerr_automatic_login
import jellystack_mobile.design.generated.resources.seerr_connect_jellyfin_first
import jellystack_mobile.design.generated.resources.server_name
import jellystack_mobile.design.generated.resources.server_url_missing_protocol
import jellystack_mobile.design.generated.resources.show_password
import jellystack_mobile.design.generated.resources.sign_in_with
import jellystack_mobile.design.generated.resources.use_different_account
import jellystack_mobile.design.generated.resources.username
import kotlinx.coroutines.cancel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.password as passwordSemantics

@Suppress("FunctionName", "UNUSED_PARAMETER")
@Composable
internal fun AddServerDialog(
    state: ServerFormState,
    isSaving: Boolean,
    quickConnectState: JellyfinQuickConnectState?,
    errorMessage: String?,
    seerrCompatibilityNotice: String?,
    availableJellyfinServers: List<ManagedServer>,
    onValueChange: (ServerFormState) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onRestartQuickConnect: () -> Unit,
) {
    var passwordVisible by remember(state.type) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isSaving || quickConnectState != null) onDismiss() },
        title = { Text(stringResource(Res.string.connect_server)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.type == ServerFormType.JELLYFIN,
                        onClick = {
                            if (!isSaving) {
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        type = ServerFormType.JELLYFIN,
                                        email = "",
                                        password = "",
                                        useJellyfinLogin = false,
                                        automaticSeerrLogin = false,
                                    ),
                                )
                                onClearError()
                            }
                        },
                        enabled = !isSaving,
                        label = { Text("Jellyfin") },
                    )
                    FilterChip(
                        selected = state.type == ServerFormType.SEERR,
                        onClick = {
                            if (!isSaving) {
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        type = ServerFormType.SEERR,
                                        username = "",
                                        password = "",
                                        useJellyfinLogin = true,
                                        automaticSeerrLogin = true,
                                    ),
                                )
                                onClearError()
                            }
                        },
                        enabled = !isSaving,
                        label = { Text(stringResource(Res.string.request_provider)) },
                    )
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = {
                        onValueChange(state.copy(name = it))
                        onClearError()
                    },
                    label = { Text(stringResource(Res.string.server_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = {
                        onValueChange(state.copy(baseUrl = it, allowInsecureHttp = false))
                        onClearError()
                    },
                    label = { Text(stringResource(Res.string.base_url)) },
                    placeholder = { Text("https://jellyfin.example.com") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError =
                        state.serverAddressValidation is ServerAddressValidation.MissingProtocol ||
                            state.serverAddressValidation is ServerAddressValidation.Invalid,
                    supportingText =
                        when (state.serverAddressValidation) {
                            ServerAddressValidation.MissingProtocol ->
                                {
                                    {
                                        Text(stringResource(Res.string.server_url_missing_protocol))
                                    }
                                }
                            ServerAddressValidation.Invalid ->
                                {
                                    {
                                        Text(stringResource(Res.string.onboarding_url_error))
                                    }
                                }
                            else -> null
                        },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                )
                if (state.requiresInsecureHttpConfirmation) {
                    InsecureHttpWarning(
                        confirmed = state.allowInsecureHttp,
                        onConfirmedChange = { confirmed ->
                            onValueChange(state.copy(allowInsecureHttp = confirmed))
                            onClearError()
                        },
                        enabled = !isSaving,
                    )
                }
                when (state.type) {
                    ServerFormType.JELLYFIN -> {
                        JellyfinSignInMethodSelector(
                            selected = state.jellyfinSignInMethod,
                            onSelected = { method ->
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        jellyfinSignInMethod = method,
                                        password = "",
                                    ),
                                )
                                onClearError()
                            },
                            enabled = !isSaving || quickConnectState != null,
                        )
                        if (quickConnectState != null) {
                            JellyfinQuickConnectStatus(
                                state = quickConnectState,
                                onUsePassword = {
                                    onValueChange(
                                        state.copy(
                                            jellyfinSignInMethod = JellyfinSignInMethod.PASSWORD,
                                        ),
                                    )
                                    onClearError()
                                },
                                onNewCode = onRestartQuickConnect,
                                onCancel = onDismiss,
                            )
                        } else if (state.jellyfinSignInMethod == JellyfinSignInMethod.QUICK_CONNECT) {
                            Text(
                                text = stringResource(Res.string.quick_connect_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = {
                                    onValueChange(state.copy(username = it))
                                    onClearError()
                                },
                                label = { Text(stringResource(Res.string.username)) },
                                placeholder = { Text("user") },
                                singleLine = true,
                                enabled = !isSaving,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = {
                                    onValueChange(state.copy(password = it))
                                    onClearError()
                                },
                                label = { Text(stringResource(Res.string.password_optional)) },
                                singleLine = true,
                                enabled = !isSaving,
                                visualTransformation =
                                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions =
                                    KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val icon =
                                        if (passwordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        }
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription =
                                                stringResource(
                                                    if (passwordVisible) {
                                                        Res.string.hide_password
                                                    } else {
                                                        Res.string.show_password
                                                    },
                                                ),
                                        )
                                    }
                                },
                                modifier = Modifier.semantics { passwordSemantics() },
                            )
                        }
                    }
                    ServerFormType.SEERR -> {
                        RequestProviderSelector(state, isSaving, onValueChange)
                        if (state.requestProvider == RequestProviderType.MEDIARIUM) {
                            MediariumKeyField(state.apiKey, !isSaving) { onValueChange(state.copy(apiKey = it)) }
                        } else if (state.automaticSeerrLogin) {
                            Text(
                                text =
                                    if (availableJellyfinServers.isNotEmpty()) {
                                        stringResource(Res.string.seerr_automatic_login)
                                    } else {
                                        stringResource(Res.string.seerr_connect_jellyfin_first)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    onValueChange(
                                        state.copy(
                                            automaticSeerrLogin = false,
                                            useJellyfinLogin = availableJellyfinServers.isNotEmpty(),
                                        ),
                                    )
                                    onClearError()
                                },
                                enabled = !isSaving,
                            ) {
                                Text(stringResource(Res.string.use_different_account))
                            }
                        } else {
                            seerrCompatibilityNotice?.let { notice ->
                                SeerrCompatibilityNotice(text = notice)
                            }
                            Text(stringResource(Res.string.sign_in_with), style = MaterialTheme.typography.labelLarge)
                            SeerrSignInMethodSelector(
                                useJellyfinLogin = state.useJellyfinLogin,
                                onUseJellyfinLoginChange = { useJellyfinLogin ->
                                    onValueChange(
                                        state.copy(
                                            useJellyfinLogin = useJellyfinLogin,
                                            username = if (useJellyfinLogin) state.username else "",
                                            email = if (useJellyfinLogin) "" else state.email,
                                        ),
                                    )
                                    onClearError()
                                },
                                enabled = !isSaving,
                            )
                            if (state.useJellyfinLogin) {
                                OutlinedTextField(
                                    value = state.username,
                                    onValueChange = {
                                        onValueChange(state.copy(username = it))
                                        onClearError()
                                    },
                                    label = { Text(stringResource(Res.string.username)) },
                                    placeholder = { Text("user") },
                                    singleLine = true,
                                    enabled = !isSaving,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                )
                            } else {
                                OutlinedTextField(
                                    value = state.email,
                                    onValueChange = {
                                        onValueChange(state.copy(email = it))
                                        onClearError()
                                    },
                                    label = { Text(stringResource(Res.string.email)) },
                                    placeholder = { Text("user@example.com") },
                                    singleLine = true,
                                    enabled = !isSaving,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Email),
                                )
                            }
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = {
                                    onValueChange(state.copy(password = it))
                                    onClearError()
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            if (state.useJellyfinLogin) {
                                                if (state.requiresSeerrPassword) {
                                                    Res.string.password
                                                } else {
                                                    Res.string.password_optional
                                                }
                                            } else {
                                                Res.string.password
                                            },
                                        ),
                                    )
                                },
                                singleLine = true,
                                enabled = !isSaving,
                                visualTransformation =
                                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions =
                                    KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription =
                                                stringResource(
                                                    if (passwordVisible) Res.string.hide_password else Res.string.show_password,
                                                ),
                                        )
                                    }
                                },
                                modifier = Modifier.semantics { passwordSemantics() },
                            )
                        }
                    }
                }
                if (errorMessage != null && quickConnectState == null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (quickConnectState == null) {
                Button(
                    onClick = onSubmit,
                    enabled = state.isValid && !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.onboarding_saving))
                    } else {
                        val label =
                            when (state.type) {
                                ServerFormType.JELLYFIN -> stringResource(Res.string.connect_jellyfin)
                                ServerFormType.SEERR ->
                                    stringResource(
                                        if (state.requestProvider ==
                                            RequestProviderType.MEDIARIUM
                                        ) {
                                            Res.string.connect_mediarium
                                        } else {
                                            Res.string.connect_seerr
                                        },
                                    )
                            }
                        Text(label)
                    }
                }
            }
        },
        dismissButton = {
            if (quickConnectState == null) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}
