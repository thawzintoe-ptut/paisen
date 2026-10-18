package com.ptut.expense.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.QrPaper
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.ptutType
import com.ptut.expense.domain.shared.PaymentMethod
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyChip
import com.ptut.expense.ui.sky.SkyDateTimeRow
import com.ptut.expense.ui.sky.SkyHairline
import com.ptut.expense.ui.sky.SkyKeyValueRow
import com.ptut.expense.ui.sky.SkyTopBar
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.launch

/** #139 (p31). PromptPay and Bank transfer are labels: the footer says plainly that no money moves. */
@Composable
fun RecordPaymentView(
    uiState: RecordPaymentUiState,
    onClose: () -> Unit,
    onAmount: (String) -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onTime: (Long) -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current
    val words = text.shared.payment
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer)) {
        SkyTopBar(title = words.title, onBack = onClose, backIcon = PtutIcon.Close, backLabel = text.common.close)
        val state = uiState as? RecordPaymentUiState.Ready ?: return@Column GroupGone(status = text.shared.expense.goneStatus)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Person(name = state.fromName, label = state.fromLabel, isMe = state.fromIsMe)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                    Text(words.paidCaps, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SkyHairline(Modifier.width(SkySizes.payAvatar))
                }
                Person(name = state.toName, label = state.toLabel, isMe = state.toIsMe)
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("฿", style = MaterialTheme.ptutType.heroSymbol, color = MaterialTheme.colorScheme.onSurface)
                Box {
                    BasicTextField(
                        value = state.form.amountText,
                        onValueChange = onAmount,
                        singleLine = true,
                        textStyle = MaterialTheme.ptutType.hero.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.width(SkySizes.amountField).semantics { contentDescription = words.amountPaid },
                    )
                }
            }
            Text(
                state.fullBalanceLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
            Text(words.howPaid, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
            FlowRow(
                modifier = Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PaymentMethod.entries.forEach { method ->
                    SkyChip(label = method.label(), selected = method == state.form.method, onClick = { onMethod(method) })
                }
            }
            SkyDateTimeRow(epochMillis = state.form.occurredAtEpochMillis, onPick = onTime, label = words.date, modifier = Modifier.padding(top = Spacing.sm))
            SkyKeyValueRow(label = text.shared.group, value = state.groupName)
            SkyKeyValueRow(label = words.afterThis, value = state.afterThis)
            // #199: scan in any Thai bank app; it fills in the payee and the amount.
            state.promptPayQr?.let { qr ->
                val qrPainter = rememberQrCodePainter(qr)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).clip(PtutRadii.r22).background(MaterialTheme.colorScheme.surface).padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // White under the code in every theme, or a dark surface hides it from the camera.
                    Image(painter = qrPainter, contentDescription = null, modifier = Modifier.clip(PtutRadii.r12).background(QrPaper).padding(Spacing.sm).size(SkySizes.inviteQr).semantics { contentDescription = words.qrDescription })
                    state.promptPayLine?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
                    }
                    Text(words.scanHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OpenBankApp(qrPainter)
                }
            }
            Text(
                words.onlyRecords,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        ) {
            SkyButton(text.common.cancel, onClick = onClose, kind = SkyButtonKind.Outline, modifier = Modifier.weight(1f))
            SkyButton(state.buttonLabel, onClick = onRecord, enabled = state.canSave, modifier = Modifier.weight(1.5f))
        }
    }
}

@Composable
private fun Person(name: String, label: String, isMe: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MemberAvatar(label = label, isMe = isMe, size = SkySizes.payAvatar)
        Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/**
 * #199: saves the QR to Photos, then opens the bank app the person picks (Android) or the share
 * sheet (iOS). In the bank app: Scan, then choose the QR from photos.
 */
@Composable
private fun OpenBankApp(qr: Painter) {
    val text = LocalCopy.current.shared.payment
    val banks = rememberBankApps()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var line by rememberSaveable { mutableStateOf<String?>(null) }
    var listing by rememberSaveable { mutableStateOf(false) }
    SkyButton(
        text.openMyBank,
        onClick = {
            scope.launch {
                val png = qrPng(qr, density)
                line = if (banks.savePng(png, "PaiSen-PromptPay")) text.qrSaved else text.takeScreenshot
                if (banks.canList) listing = true else banks.sharePng(png)
            }
        },
        kind = SkyButtonKind.Outline,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )
    line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.xs)) }
    if (listing) {
        val installed = remember { banks.installed() }
        SkyBottomSheet(onDismissRequest = { listing = false }) {
            Text(text.openYourBank, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            if (installed.isEmpty()) {
                Text(
                    text.noBankApp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            installed.forEach { app ->
                SkyButton(
                    app.name,
                    onClick = {
                        listing = false
                        banks.open(app)
                    },
                    kind = SkyButtonKind.Outline,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s12),
                )
            }
        }
    }
}
