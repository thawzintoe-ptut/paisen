package com.ptut.expense

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ptut.expense.add.AddActions
import com.ptut.expense.add.AddRateActions
import com.ptut.expense.add.AddSheet
import com.ptut.expense.add.AddViewModel
import com.ptut.expense.add.AddVoiceActions
import com.ptut.expense.ui.sky.AskBeforeLeaving
import com.ptut.expense.ui.sky.SkyBottomSheet
import com.ptut.expense.ui.sky.SkyDiscardSheet

/** The Add sheet over whatever is on screen, while [open]; [onClose] is how it shuts itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSheetHost(addViewModel: AddViewModel, open: Boolean, onClose: () -> Unit) {
    val addState by addViewModel.uiState.collectAsStateWithLifecycle()

    // The sheet closes on the write finishing, not on the tap: a save that failed must leave
    // the amount where the person can see it.
    LaunchedEffect(addState.saved) {
        if (addState.saved) onClose()
    }

    if (open) {
        // #214: a changed draft asks before a swipe, a scrim tap, back or the iOS edge swipe drops it.
        var askDiscard by remember { mutableStateOf(false) }
        SkyBottomSheet(
            onDismissRequest = onClose,
            confirmDismiss = {
                if (addState.changed) askDiscard = true
                !addState.changed
            },
        ) {
            AskBeforeLeaving(changed = addState.changed, onAsk = { askDiscard = true })
            AddSheet(
                uiState = addState,
                actions = remember(addViewModel) {
                    AddActions(
                        onType = addViewModel::type,
                        onKind = addViewModel::setKind,
                        onCategory = addViewModel::choose,
                        onSubCategory = addViewModel::chooseSubCategory,
                        onTime = addViewModel::setTime,
                        onOpenMemo = addViewModel::openMemo,
                        onMemo = addViewModel::setMemo,
                        onCloseMemo = addViewModel::closeMemo,
                        onSave = addViewModel::save,
                        onQuickAdd = addViewModel::quickAdd,
                        onAskText = addViewModel::typeAsk,
                    )
                },
                voiceActions = remember(addViewModel) {
                    AddVoiceActions(
                        onOpenVoice = addViewModel::openVoice,
                        onCloseVoice = addViewModel::closeVoice,
                        onListen = addViewModel::listen,
                        onStopListening = addViewModel::stopListening,
                        onVoiceLanguage = addViewModel::chooseVoiceLanguage,
                        onDownloadVoice = addViewModel::downloadVoice,
                        onAgreeOnline = addViewModel::agreeOnline,
                        onDismissOnlineAsk = addViewModel::dismissOnlineAsk,
                    )
                },
                rateActions = remember(addViewModel) {
                    AddRateActions(
                        onCurrency = addViewModel::chooseCurrency,
                        onRate = addViewModel::typeRate,
                        onOpenRate = addViewModel::openRate,
                        onCloseRate = addViewModel::closeRate,
                    )
                },
            )
        }
        if (askDiscard) {
            SkyDiscardSheet(
                onKeep = { askDiscard = false },
                onDiscard = {
                    askDiscard = false
                    onClose()
                },
            )
        }
    }
}
