package com.ptut.expense.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptut.expense.design.SkySizes
import com.ptut.expense.design.Spacing

/**
 * A pushed Sky screen with no artboard of its own (#172): the top bar over a list on the grey
 * page, ending clear of the pill's space. The screens built on it share one shape.
 */
@Composable
fun SkyListScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SkyTopBar(title = title, onBack = onBack, trailing = trailing)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            content()
            item { Box(Modifier.height(SkySizes.pillClearance)) }
        }
    }
}

/** The line under a Sky screen's title that says what the screen is for. */
@Composable
fun SkyIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.md),
    )
}

/** A small heading over a group of rows on a Sky screen. */
@Composable
fun SkyGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = Spacing.md).padding(top = Spacing.xs),
    )
}
