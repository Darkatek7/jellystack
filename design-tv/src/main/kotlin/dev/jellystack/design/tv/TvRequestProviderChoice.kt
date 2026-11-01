package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.RequestProviderType

@Composable
internal fun tvRequestProviderChoice(
    selected: RequestProviderType,
    enabled: Boolean,
    onSelected: (RequestProviderType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RequestProviderType.entries.forEach { provider ->
            TvActionButton(
                label = if (provider == RequestProviderType.MEDIARIUM) "Mediarium" else "Seerr",
                onClick = { onSelected(provider) },
                enabled = enabled,
                selected = provider == selected,
            )
        }
    }
}

@Composable
internal fun tvMediariumKeyField(
    value: String,
    enabled: Boolean,
    strings: TvStrings,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(strings.requestProvider.apiKey) },
        modifier = modifier,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        colors = tvOutlinedTextFieldColors(),
    )
}

internal fun requestConnectionNextFocus(
    provider: RequestProviderType,
    needsCredentials: Boolean,
    apiKey: FocusRequester,
    method: FocusRequester,
    connect: FocusRequester,
): FocusRequester =
    when {
        provider == RequestProviderType.MEDIARIUM -> apiKey
        needsCredentials -> method
        else -> connect
    }

internal fun requestConnectionInitialFocus(
    needsCredentials: Boolean,
    method: FocusRequester,
    url: FocusRequester,
): FocusRequester = if (needsCredentials) method else url
