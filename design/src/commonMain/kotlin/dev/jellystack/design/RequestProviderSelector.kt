package dev.jellystack.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.RequestProviderType
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.mediarium_api_key
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RequestProviderSelector(
    state: ServerFormState,
    isSaving: Boolean,
    onValueChange: (ServerFormState) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RequestProviderType.entries.forEach { provider ->
            FilterChip(
                selected = state.requestProvider == provider,
                enabled = !isSaving,
                onClick = {
                    if (state.requestProvider != provider) {
                        onValueChange(
                            state.copy(
                                serverId = null,
                                requestProvider = provider,
                                name = if (provider == RequestProviderType.MEDIARIUM) "Mediarium" else "Seerr",
                                apiKey = "",
                                password = "",
                                automaticSeerrLogin = true,
                            ),
                        )
                    }
                },
                label = { Text(if (provider == RequestProviderType.MEDIARIUM) "Mediarium" else "Seerr") },
            )
        }
    }
}

@Composable
internal fun MediariumKeyField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(stringResource(Res.string.mediarium_api_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
}
