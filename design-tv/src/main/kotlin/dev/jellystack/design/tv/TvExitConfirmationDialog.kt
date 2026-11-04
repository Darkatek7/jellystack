@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

@Composable
internal fun TvExitConfirmationDialog(
    strings: TvStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocusRequester = rememberTvDialogInitialFocus()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(560.dp).background(TvSurfaceRaised, RoundedCornerShape(26.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.messages.exitAppTitle, color = TvText, fontSize = 28.sp)
            Text(strings.messages.exitAppMessage, color = TvTextMuted, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    label = strings.messages.exitApp,
                    onClick = onConfirm,
                    destructive = true,
                    modifier = Modifier.width(180.dp).testTag("tv-exit-confirm"),
                )
                TvActionButton(
                    label = strings.cancel,
                    onClick = onDismiss,
                    focusRequester = cancelFocusRequester,
                    modifier = Modifier.testTag("tv-exit-cancel"),
                )
            }
        }
    }
}
