package com.ptut.expense.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.ptut.expense.design.PtutRadii
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.locale.LocalCopy
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyButton
import com.ptut.expense.ui.sky.SkyButtonKind
import com.ptut.expense.ui.sky.SkyDashedAddRow
import com.ptut.expense.ui.sky.SkyListScreen
import com.ptut.expense.ui.sky.SkySearchField
import com.ptut.expense.ui.sky.skyItem

/**
 * #141, after Sky p31: search, one row per person with their groups and one balance across them,
 * archived people faded at the end, then the dashed ADD PERSON row. Tapping a person who exists on
 * their own (not just a typed name) offers Archive, or Bring back.
 */
@Composable
fun PeopleView(
    uiState: PeopleUiState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onSetArchived: (personId: String, archived: Boolean) -> Unit,
    onAddPerson: (name: String, personId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = LocalCopy.current.shared
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    SkyListScreen(title = text.people, onBack = onBack, modifier = modifier) {
        item {
            SkySearchField(query = uiState.query, onSearch = onSearch, placeholder = text.person.search, modifier = Modifier.padding(horizontal = Spacing.sm))
        }
        items(uiState.rows, key = { it.key }) { row ->
            PeopleRow(row, onClick = row.personId?.let { id -> { picked = id } }, modifier = skyItem().padding(horizontal = Spacing.sm))
        }
        item {
            SkyDashedAddRow(label = text.addPerson, onClick = { adding = true }, modifier = Modifier.padding(horizontal = Spacing.sm))
        }
    }

    val row = uiState.rows.firstOrNull { it.personId != null && it.personId == picked }
    if (row != null) {
        SkyBottomSheet(onDismissRequest = { picked = null }) {
            Text(row.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                if (row.archived) text.person.bringBackBody(row.name) else text.person.archiveBody(row.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            SkyButton(
                if (row.archived) text.person.bringBack else text.person.archive(row.name),
                onClick = {
                    picked = null
                    row.personId?.let { onSetArchived(it, !row.archived) }
                },
                kind = SkyButtonKind.Outline,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            )
        }
    }

    if (adding) {
        NewPersonSheet(
            people = uiState.knownPeople,
            onAdd = { name, personId ->
                adding = false
                onAddPerson(name, personId)
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun PeopleRow(row: PeopleRowUiModel, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.tappable(PtutRadii.r22, Role.Button, onClick = onClick))
            .semantics(mergeDescendants = true) {}
            .heightIn(min = SkySizes.transactionRow),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // #201: only the initial fades; the words ("Archived · …") keep full contrast.
        MemberAvatar(label = row.initial, isMe = false, modifier = if (row.archived) Modifier.alpha(ARCHIVED_ALPHA) else Modifier)
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.word, style = MaterialTheme.typography.bodySmall, color = row.tone.ink())
            Text(row.amount, style = MaterialTheme.typography.titleMedium, color = row.tone.ink())
        }
    }
}

/** Faded, not hidden: an archived person still owes or is owed what their past splits say. */
private const val ARCHIVED_ALPHA = 0.6f
