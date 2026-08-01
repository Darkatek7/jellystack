package dev.jellystack.design.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.jellyfin
import jellystack_mobile.design.generated.resources.seerr
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeerrSignInMethodSelector(
    useJellyfinLogin: Boolean,
    onUseJellyfinLoginChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val methods = listOf(true, false)
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        methods.forEachIndexed { index, jellyfinLogin ->
            SegmentedButton(
                selected = useJellyfinLogin == jellyfinLogin,
                onClick = { onUseJellyfinLoginChange(jellyfinLogin) },
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = methods.size),
                label = {
                    Text(
                        text =
                            stringResource(
                                if (jellyfinLogin) {
                                    Res.string.jellyfin
                                } else {
                                    Res.string.seerr
                                },
                            ),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}
