package com.ptut.expense.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyGroupedCard
import com.ptut.expense.ui.sky.SkyIntro
import com.ptut.expense.ui.sky.SkyListScreen

/** Each row opens its full text under it, so nothing here is a link out of the app. */
@Composable
fun LicencesView(
    uiState: LicencesUiState,
    onToggle: (LicenceUiModel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.settings
    SkyListScreen(title = text.licences, onBack = onBack, modifier = modifier) {
        item { SkyIntro(text.licencesIntro) }
        item {
            SkyGroupedCard(
                rows = uiState.licences.map<LicenceUiModel, @Composable () -> Unit> { licence ->
                    { LicenceRow(licence, open = uiState.open == licence.name, text = uiState.text, onToggle = onToggle) }
                },
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
    }
}

@Composable
private fun LicenceRow(licence: LicenceUiModel, open: Boolean, text: String?, onToggle: (LicenceUiModel) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .tappable(PtutRadii.r12, Role.Button, onClick = { onToggle(licence) })
            .padding(Spacing.sm),
    ) {
        Text(licence.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "${licence.licence} · ${licence.covers}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s2),
        )
        if (open && text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
