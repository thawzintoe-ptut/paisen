package com.ptut.expense.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ptut.expense.design.Spacing
import com.ptut.expense.domain.play.PlayTip
import com.ptut.expense.play.PlayTipViewModel
import com.ptut.expense.ui.play.PlayTipCard
import com.ptut.expense.ui.play.TipPointer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The slot a screen shows above (or under) a feature: the tip while it is due, else null. */
@Composable
internal fun playTip(tip: PlayTip, pointer: TipPointer = TipPointer.Down): (@Composable () -> Unit)? {
    val viewModel = koinViewModel<PlayTipViewModel>(key = "tip-${tip.name}") { parametersOf(tip) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val model = state ?: return null
    return { PlayTipCard(model, onGotIt = viewModel::gotIt, onSkipAll = viewModel::skipAll, pointer = pointer, modifier = Modifier.padding(horizontal = Spacing.sm)) }
}
