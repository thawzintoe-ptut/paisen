package com.ptut.expense.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.PtutTheme
import com.ptut.expense.design.QrPaper
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.domain.format
import com.ptut.expense.domain.shared.PromptPay
import com.ptut.expense.domain.shared.SharedGroup
import com.ptut.expense.domain.shared.Transfer
import com.ptut.expense.locale.AppText
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.locale.SharedSettleCopy
import com.ptut.expense.sync.InviteLinks
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/** What a "Pay me" card says. Built on the phone and shared as a picture; nothing is uploaded. */
data class PayMeCard(
    val payerName: String,
    val groupName: String,
    val amount: String,
    val maskedId: String,
    /** The PromptPay QR for exactly this amount. It holds my ID in full, as every PromptPay QR does. */
    val qrPayload: String,
    val shareText: String,
)

/** Null unless [transfer] is owed to me and my PromptPay ID is saved: the QR is the card. */
fun payMeCard(group: SharedGroup, transfer: Transfer, text: SharedSettleCopy = AppText.copy.shared.settle): PayMeCard? {
    if (!group.isMe(transfer.toMemberId)) return null
    val myId = group.member(transfer.toMemberId)?.promptPayId ?: return null
    val qr = PromptPay.payload(myId, transfer.amount) ?: return null
    val payer = group.displayName(transfer.fromMemberId)
    val amount = transfer.amount.format()
    val link = group.inviteCode?.takeIf { group.inviteActive }?.let(InviteLinks::link)
    return PayMeCard(
        payerName = payer,
        groupName = group.name,
        amount = amount,
        maskedId = PromptPay.masked(myId),
        qrPayload = qr,
        shareText = listOfNotNull(text.payMeText(payer, amount, group.name), link).joinToString("\n"),
    )
}

/**
 * The picture that is shared. Always the light look on white paper, whatever the phone's theme: a
 * dark card hides the code from a bank app's camera.
 */
@Composable
fun PayMeCardPicture(card: PayMeCard, modifier: Modifier = Modifier) {
    val text = LocalCopy.current.shared.settle
    val qrDescription = LocalCopy.current.shared.payment.qrDescription
    PtutTheme(darkTheme = false) {
        Column(
            modifier = modifier.width(SkySizes.payMeCard).background(QrPaper).padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text.payMeFor(card.groupName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                card.amount,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(text.payMeFrom(card.payerName), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Image(
                painter = rememberQrCodePainter(card.qrPayload),
                contentDescription = null,
                modifier = Modifier.padding(top = Spacing.md).clip(PtutRadii.r12).size(SkySizes.inviteQr).semantics { contentDescription = qrDescription },
            )
            Text(
                text.payMeScan(card.maskedId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
