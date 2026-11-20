package com.ptut.expense.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.SUBSCRIPTION_STOP_MILLIS
import com.ptut.expense.domain.folders.ApplyWatchedFoldersUseCase
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.domain.folders.FolderReading
import com.ptut.expense.domain.folders.GetFolderAccessUseCase
import com.ptut.expense.domain.folders.ListedFolder
import com.ptut.expense.domain.folders.ObserveWatchedFoldersUseCase
import com.ptut.expense.domain.folders.RefreshFolderCatalogUseCase
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.mergeFolderList
import com.ptut.expense.domain.folders.slipsRead
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.domain.runSuspendCatching
import com.ptut.expense.locale.AppText
import com.ptut.expense.ui.ToastMessage
import com.ptut.expense.ui.ToastMessages
import com.ptut.expense.ui.ToastTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * FR-2 and FR-5: every folder on the phone, and which of them the importer reads.
 *
 * The list is the catalogue merged with what is watched, never one or the other. A watched folder
 * the catalogue no longer offers still has to appear — a card pulled out, an album deleted in
 * Photos — because it is still holding a count and still the reason a total looks the way it does.
 */
class PhotoFoldersViewModel(
    private val refreshCatalog: RefreshFolderCatalogUseCase,
    observeFolders: ObserveWatchedFoldersUseCase,
    private val applyWatchedFolders: ApplyWatchedFoldersUseCase,
    /** Read again on every [refresh] (#217). */
    private val folderAccess: GetFolderAccessUseCase = GetFolderAccessUseCase(),
    private val chooser: FolderChooser = FolderChooser {},
    /** Where a failed read or save is said. Null in a graph that binds none. */
    private val toasts: ToastMessages? = null,
) : ViewModel() {

    private val available = MutableStateFlow<List<DeviceFolder>?>(null)

    private val offer = MutableStateFlow(folderAccess())

    /**
     * The ticks the reader has changed and not saved (#169), or null while there are none. The
     * rule for leaving: nothing is watched or dropped until SAVE, so backing out keeps the folders
     * exactly as they were. A tick is a choice, and a choice half-made is not applied.
     */
    private val draft = MutableStateFlow<Set<String>?>(null)

    val uiState: StateFlow<PhotoFoldersUiState> = combine(
        available,
        observeFolders(),
        draft,
        offer,
    ) { offered, watched, ticks, platform -> state(offered, watched, ticks, platform) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
            initialValue = state(offered = null, watched = emptyList(), ticks = null, platform = offer.value),
        )

    init {
        refresh()
    }

    /** Called on start and whenever the screen comes back, so it follows the system settings. */
    fun refresh() = viewModelScope.launch { reread() }

    /**
     * Opens the platform's own way of reaching more pictures, then re-reads: whatever the reader
     * granted while they were away has to show up without them coming back a second time.
     */
    fun chooseMore() = viewModelScope.launch {
        runSuspendCatching { chooser.chooseMore() }.onFailure { say(AppText.copy.settings.didNotWork) }
        reread()
    }

    private suspend fun reread() {
        runSuspendCatching {
            offer.value = folderAccess()
            available.value = refreshCatalog()
        }.onFailure { say(AppText.copy.folders.notRead) }
    }

    /** A tick, held until SAVE. */
    fun onToggle(id: String) {
        val now = ticks()
        draft.value = if (id in now) now - id else now + id
    }

    /**
     * Applies every tick at once: the folders newly ticked are watched, the ones unticked dropped.
     * One folder failing does not stop the rest; the ticks stay so SAVE can retry the ones left.
     */
    fun save() = viewModelScope.launch {
        val chosen = draft.value ?: return@launch
        val rows = uiState.value.rows
        val stop = rows.filter { it.watched && it.id !in chosen }.map { it.id }
        val watch = rows.filter { !it.watched && it.id in chosen }.map { DeviceFolder(it.id, it.name, it.path) }
        if (applyWatchedFolders(stop, watch)) draft.value = null else say(AppText.copy.folders.notSaved)
    }

    private suspend fun say(text: String) {
        toasts?.show(ToastMessage(text, tone = ToastTone.Error))
    }

    private fun ticks(): Set<String> = draft.value ?: uiState.value.rows.filter { it.watched }.map { it.id }.toSet()

    private fun state(
        offered: List<DeviceFolder>?,
        watched: List<WatchedFolder>,
        ticks: Set<String>?,
        platform: FolderAccess,
    ): PhotoFoldersUiState {
        val rows = mergeFolderList(offered, watched).map { listed ->
            val row = listed.row()
            if (ticks == null) row else row.copy(ticked = row.id in ticks)
        }
        val watchedIds = watched.watched().map { it.id }.toSet()
        return PhotoFoldersUiState(
            summary = summary(watched),
            rows = rows,
            // Null means the catalogue has not answered yet, which is not the same as it answering
            // with nothing. Only the second one is a permission problem worth a screen saying so.
            accessGranted = offered == null || offered.isNotEmpty() || watched.isNotEmpty(),
            chooseMore = platform.action,
            chooseMoreExplanation = platform.explanation,
            ticked = rows.count { it.ticked },
            canSave = ticks != null && ticks != watchedIds,
        )
    }

    private fun ListedFolder.row() = FolderRowUiModel(
        id = id,
        name = name,
        path = path,
        reading = (known?.readingState ?: FolderReading.NeverChecked).phrase(known?.slipsRead ?: 0),
        // Green only while watched (#125): the summary counts watched folders, and the two must agree.
        badged = known?.let { it.watched && it.foundSlips } == true,
        watched = known?.watched == true,
        images = imageCount?.let { AppText.copy.folders.images(it) },
    )

    /**
     * The slip figure is the sum over the badged rows, which is what makes the badge and the chip
     * one claim rather than two that can drift.
     */
    private fun summary(known: List<WatchedFolder>): String {
        val on = known.watched()
        return AppText.copy.folders.summary(on.size, on.slipsRead())
    }
}
