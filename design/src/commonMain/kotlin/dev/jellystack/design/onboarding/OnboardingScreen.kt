@file:Suppress("FunctionName")

package dev.jellystack.design.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.design.ServerFormState
import dev.jellystack.design.components.JellyfinQuickConnectStatus
import dev.jellystack.design.components.JellyfinSignInMethodSelector
import dev.jellystack.design.components.JellystackMark
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.app_title
import jellystack_mobile.design.generated.resources.back
import jellystack_mobile.design.generated.resources.connect_jellyfin
import jellystack_mobile.design.generated.resources.connect_seerr
import jellystack_mobile.design.generated.resources.continue_label
import jellystack_mobile.design.generated.resources.email
import jellystack_mobile.design.generated.resources.hide_password
import jellystack_mobile.design.generated.resources.insecure_http_confirm
import jellystack_mobile.design.generated.resources.insecure_http_warning
import jellystack_mobile.design.generated.resources.onboarding_connect_jellyfin_first
import jellystack_mobile.design.generated.resources.onboarding_jellyfin_body
import jellystack_mobile.design.generated.resources.onboarding_jellyfin_title
import jellystack_mobile.design.generated.resources.onboarding_ready_body
import jellystack_mobile.design.generated.resources.onboarding_ready_title
import jellystack_mobile.design.generated.resources.onboarding_required_error
import jellystack_mobile.design.generated.resources.onboarding_saving
import jellystack_mobile.design.generated.resources.onboarding_seerr_body
import jellystack_mobile.design.generated.resources.onboarding_seerr_title
import jellystack_mobile.design.generated.resources.onboarding_step
import jellystack_mobile.design.generated.resources.onboarding_url_error
import jellystack_mobile.design.generated.resources.onboarding_welcome_body
import jellystack_mobile.design.generated.resources.onboarding_welcome_title
import jellystack_mobile.design.generated.resources.password
import jellystack_mobile.design.generated.resources.quick_connect_description
import jellystack_mobile.design.generated.resources.server_name
import jellystack_mobile.design.generated.resources.server_url
import jellystack_mobile.design.generated.resources.server_url_missing_protocol
import jellystack_mobile.design.generated.resources.show_password
import jellystack_mobile.design.generated.resources.skip_for_now
import jellystack_mobile.design.generated.resources.start_exploring
import jellystack_mobile.design.generated.resources.use_jellyfin_account
import jellystack_mobile.design.generated.resources.use_seerr_account
import jellystack_mobile.design.generated.resources.username
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.password as passwordSemantics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember(state.step) { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            JellystackMark(Modifier.size(56.dp))
            Text(stringResource(Res.string.app_title), style = MaterialTheme.typography.headlineSmall)
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            Res.string.onboarding_step,
                            state.progress.current,
                            state.progress.total,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { state.progress.fraction },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                progressBarRangeInfo =
                                    ProgressBarRangeInfo(
                                        current = state.progress.fraction,
                                        range = 0f..1f,
                                        steps = (state.progress.total - 1).coerceAtLeast(0),
                                    )
                            },
                )
            }
            Card(modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OnboardingStageContent(
                        state = state,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        onAction = onAction,
                    )
                    state.serviceErrorDetail?.takeIf(String::isNotBlank)?.let { detail ->
                        Text(
                            text = detail,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        )
                    }
                    if (state.step == TutorialStep.Explore && !state.canStartExploring) {
                        Text(
                            text = stringResource(Res.string.onboarding_connect_jellyfin_first),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OnboardingActions(state, onAction)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingStageContent(
    state: OnboardingUiState,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    onAction: (OnboardingAction) -> Unit,
) {
    when (state.step) {
        TutorialStep.Welcome -> {
            Text(
                stringResource(Res.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TutorialStep.ConnectJellyfin -> {
            Text(
                stringResource(Res.string.onboarding_jellyfin_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.onboarding_jellyfin_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            JellyfinSignInMethodSelector(
                selected = state.form.jellyfinSignInMethod,
                onSelected = { onAction(OnboardingAction.SignInMethodChanged(it)) },
                enabled = !state.isSaving || state.quickConnectState != null,
            )
            if (state.quickConnectState != null) {
                JellyfinQuickConnectStatus(
                    state = state.quickConnectState,
                    onUsePassword = {
                        onAction(OnboardingAction.SignInMethodChanged(JellyfinSignInMethod.PASSWORD))
                    },
                    onNewCode = { onAction(OnboardingAction.RestartQuickConnect) },
                    onCancel = { onAction(OnboardingAction.CancelQuickConnect) },
                )
            } else {
                if (state.form.jellyfinSignInMethod == JellyfinSignInMethod.QUICK_CONNECT) {
                    Text(
                        stringResource(Res.string.quick_connect_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ServerFields(
                    state = state,
                    showUsername = state.form.jellyfinSignInMethod == JellyfinSignInMethod.PASSWORD,
                    showEmail = false,
                    showPassword = state.form.jellyfinSignInMethod == JellyfinSignInMethod.PASSWORD,
                    passwordVisible = passwordVisible,
                    onTogglePassword = onTogglePassword,
                    onChange = { onAction(OnboardingAction.FormChanged(it)) },
                )
            }
        }
        TutorialStep.ConnectJellyseerr -> {
            Text(
                stringResource(Res.string.onboarding_seerr_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.onboarding_seerr_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.seerrQuickConnectExplanation?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ServerFields(
                state = state,
                showUsername = state.manualSeerrCredentialsRequired && state.form.useJellyfinLogin,
                showEmail = state.manualSeerrCredentialsRequired && !state.form.useJellyfinLogin,
                showPassword = state.manualSeerrCredentialsRequired,
                passwordVisible = passwordVisible,
                onTogglePassword = onTogglePassword,
                onChange = { onAction(OnboardingAction.FormChanged(it)) },
            )
            AnimatedVisibility(state.manualSeerrCredentialsRequired) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.form.useJellyfinLogin,
                        onClick = {
                            onAction(
                                OnboardingAction.FormChanged(
                                    state.form.copy(useJellyfinLogin = true, email = ""),
                                ),
                            )
                        },
                        label = { Text(stringResource(Res.string.use_jellyfin_account)) },
                    )
                    FilterChip(
                        selected = !state.form.useJellyfinLogin,
                        onClick = {
                            onAction(
                                OnboardingAction.FormChanged(
                                    state.form.copy(useJellyfinLogin = false, username = ""),
                                ),
                            )
                        },
                        label = { Text(stringResource(Res.string.use_seerr_account)) },
                    )
                }
            }
        }
        TutorialStep.Explore -> {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(Res.string.onboarding_ready_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.onboarding_ready_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ServerFields(
    state: OnboardingUiState,
    showUsername: Boolean,
    showEmail: Boolean,
    showPassword: Boolean,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    onChange: (ServerFormState) -> Unit,
) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OnboardingTextField(
            value = form.name,
            onValueChange = { onChange(form.copy(name = it)) },
            label = stringResource(Res.string.server_name),
            error = state.fieldErrors[OnboardingField.Name],
        )
        OnboardingTextField(
            value = form.baseUrl,
            onValueChange = { onChange(form.copy(baseUrl = it, allowInsecureHttp = false)) },
            label = stringResource(Res.string.server_url),
            error = state.fieldErrors[OnboardingField.Url],
        )
        if (form.requiresInsecureHttpConfirmation) {
            Text(
                text = stringResource(Res.string.insecure_http_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            FilterChip(
                selected = form.allowInsecureHttp,
                onClick = { onChange(form.copy(allowInsecureHttp = !form.allowInsecureHttp)) },
                label = { Text(stringResource(Res.string.insecure_http_confirm)) },
            )
        }
        if (showUsername) {
            OnboardingTextField(
                value = form.username,
                onValueChange = { onChange(form.copy(username = it)) },
                label = stringResource(Res.string.username),
                error = state.fieldErrors[OnboardingField.Username],
            )
        }
        if (showEmail) {
            OnboardingTextField(
                value = form.email,
                onValueChange = { onChange(form.copy(email = it)) },
                label = stringResource(Res.string.email),
                error = state.fieldErrors[OnboardingField.Email],
            )
        }
        if (showPassword) {
            val error = state.fieldErrors[OnboardingField.Password]
            OutlinedTextField(
                value = form.password,
                onValueChange = { onChange(form.copy(password = it)) },
                label = { Text(stringResource(Res.string.password)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { FieldErrorText(it) } },
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector =
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription =
                                if (passwordVisible) {
                                    stringResource(Res.string.hide_password)
                                } else {
                                    stringResource(Res.string.show_password)
                                },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().semantics { passwordSemantics() },
            )
        }
    }
}

@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: OnboardingValidationError?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { FieldErrorText(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FieldErrorText(error: OnboardingValidationError) {
    Text(
        when (error) {
            OnboardingValidationError.Required -> stringResource(Res.string.onboarding_required_error)
            OnboardingValidationError.MissingProtocol ->
                stringResource(Res.string.server_url_missing_protocol)
            OnboardingValidationError.InvalidUrl -> stringResource(Res.string.onboarding_url_error)
            OnboardingValidationError.InsecureTransportNotConfirmed ->
                stringResource(Res.string.insecure_http_confirm)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingActions(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.step != TutorialStep.Welcome) {
                TextButton(
                    onClick = { onAction(OnboardingAction.Back) },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(Res.string.back))
                }
            }
            if (state.step == TutorialStep.ConnectJellyseerr) {
                TextButton(
                    onClick = { onAction(OnboardingAction.SkipSeerr) },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(Res.string.skip_for_now))
                }
            }
        }
        if (state.step != TutorialStep.ConnectJellyfin || state.quickConnectState == null) {
            Button(
                onClick = {
                    onAction(
                        if (state.step == TutorialStep.Explore) {
                            OnboardingAction.StartExploring
                        } else {
                            OnboardingAction.Continue
                        },
                    )
                },
                enabled =
                    !state.isSaving &&
                        (state.step != TutorialStep.Explore || state.canStartExploring),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.onboarding_saving))
                    }
                } else {
                    Text(
                        when (state.step) {
                            TutorialStep.Welcome -> stringResource(Res.string.continue_label)
                            TutorialStep.ConnectJellyfin -> stringResource(Res.string.connect_jellyfin)
                            TutorialStep.ConnectJellyseerr -> stringResource(Res.string.connect_seerr)
                            TutorialStep.Explore -> stringResource(Res.string.start_exploring)
                        },
                    )
                }
            }
        }
    }
}
