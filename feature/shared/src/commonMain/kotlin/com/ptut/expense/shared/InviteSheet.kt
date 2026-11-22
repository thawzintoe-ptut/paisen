package com.ptut.expense.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.QrPaper
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.sync.sharedDataRegion
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyProgress
import com.ptut.expense.ui.sky.SkyTextAction
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * #190: share the link (mostly to LINE), show its QR for someone beside you, or copy the code. The
 * phone camera opens the link, so no scanner is built in. Link options holds the link itself and,
 * for the creator, renew and switch off.
 */
@Composable
fun InviteSheet(
    groupName: String,
    invite: InviteUiModel?,
    action: InviteActionState,
    isCreator: Boolean,
    onNewLink: () -> Unit,
    onSwitchOff: () -> Unit,
    onDismiss: () -> Unit,
    /** #218: makes the link again after it failed. */
    onTryAgain: () -> Unit = {},
    /** Set when the sheet opened by itself after Create: the link is made only on this tap. */
    onMakeLink: (() -> Unit)? = null,
) {
    val share = rememberShareText()
    val text = LocalCopy.current.shared
    val words = text.invite

    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var options by rememberSaveable { mutableStateOf(false) }
    SkyBottomSheet(onDismissRequest = onDismiss) {
        Text(text.invitePeople, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
        when {
            invite == null && onMakeLink != null && !action.working && action.error == null -> {
                Text(
                    words.makeLinkBody(groupName, sharedDataRegion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                SkyButton(words.makeLink, onClick = onMakeLink, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
                SkyTextAction(label = words.notNow, onClick = onDismiss, enabled = true, modifier = Modifier.padding(top = Spacing.xs))
            }

            invite == null && action.error != null -> {
                Text(action.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                SkyButton(LocalCopy.current.common.tryAgain, onClick = onTryAgain, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
            }

            invite == null -> Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SkyProgress()
                Text(words.making, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            !invite.active -> {
                Text(
                    words.switchedOff,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                if (isCreator) {
                    SkyButton(words.newLink, onClick = onNewLink, enabled = !action.working, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
                }
                action.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                }
            }

            else -> {
                Text(
                    words.howToJoin(groupName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = Spacing.md)
                        .fillMaxWidth()
                        .clip(PtutRadii.r22)
                        // White under the code in every theme, or a dark surface hides it from the camera.
                        .background(QrPaper)
                        .padding(Spacing.md),
                ) {
                    Image(
                        painter = rememberQrCodePainter(invite.link),
                        contentDescription = words.qr,
                        modifier = Modifier.size(SkySizes.inviteQr),
                    )
                }
                Text(
                    invite.code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
                Text(
                    invite.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                SkyButton(words.shareLink, onClick = { share(words.message(groupName, invite.link)) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                    SkyTextAction(label = words.copyCode, onClick = { clipboard.setText(AnnotatedString(invite.code)) }, enabled = true)
                    SkyTextAction(label = words.linkOptions, onClick = { options = !options }, enabled = true)
                }
                if (options) {
                    // The rarer controls, one step down: the link itself, and the creator's renew and switch off.
                    SkyTextAction(label = words.copyLink, onClick = { clipboard.setText(AnnotatedString(invite.link)) }, enabled = true)
                    if (isCreator) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SkyTextAction(label = words.newLink, onClick = onNewLink, enabled = !action.working)
                            SkyTextAction(label = words.switchOff, onClick = onSwitchOff, enabled = !action.working)
                        }
                    }
                }
                action.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
}
