package com.ptut.expense.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FolderAccess
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.domain.folders.FolderChooser
import com.ptut.expense.domain.folders.FolderReading
import com.ptut.expense.domain.folders.WatchedFolder
import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.folders.slipsRead
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.locale.AppText
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
    private val catalog: FolderCatalog,
    private val folders: WatchedFolderRepository,
    /**
     * Read again on every [refresh] (#217): access granted in the system settings has to show when
     * the reader comes back, not after a restart.
     */
    private val access: () -> FolderAccess = { FolderAccess(action = null, explanation = "") },
    private val chooser: FolderChooser = FolderChooser {},
) : ViewModel() {

    private val available = MutableStateFlow<List<DeviceFolder>?>(null)

    private val offer = MutableStateFlow(access())

    /**
     * The ticks the reader has changed and not saved (#169), or null while there are none. The
     * rule for leaving: nothing is watched or dropped until SAVE, so backing out keeps the folders
     * exactly as they were. A tick is a choice, and a choice half-made is not applied.
     */
    private val draft = MutableStateFlow<Set<String>?>(null)

    val uiState: StateFlow<PhotoFoldersUiState> = combine(
        available,
        folders.folders,
        draft,
        offer,
    ) { offered, watched, ticks, platform -> state(offered, watched, ticks, platform) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = state(offered = null, watched = emptyList(), ticks = null, platform = offer.value),
        )

    init {
        refresh()
    }

    /** Called on start and whenever the screen comes back, so it follows the system settings. */
    fun refresh() = viewModelScope.launch {
        offer.value = access()
        available.value = catalog.available()
    }

    /**
     * Opens the platform's own way of reaching more pictures, then re-reads: whatever the reader
     * granted while they were away has to show up without them coming back a second time.
     */
    fun chooseMore() = viewModelScope.launch {
        chooser.chooseMore()
        offer.value = access()
        available.value = catalog.available()
    }

    /** A tick, held until SAVE. */
    fun onToggle(id: String) {
        val now = ticks()
        draft.value = if (id in now) now - id else now + id
    }

    /** Applies every tick at once: the folders newly ticked are watched, the ones unticked dropped. */
    fun save() = viewModelScope.launch {
        val chosen = draft.value ?: return@launch
        val rows = uiState.value.rows
        rows.filter { it.watched && it.id !in chosen }.forEach { folders.stopWatching(it.id) }
        rows.filter { !it.watched && it.id in chosen }.forEach { folders.watch(it.id, it.name, it.path) }
        draft.value = null
    }

    private fun ticks(): Set<String> = draft.value ?: uiState.value.rows.filter { it.watched }.map { it.id }.toSet()

    private fun state(
        offered: List<DeviceFolder>?,
        watched: List<WatchedFolder>,
        ticks: Set<String>?,
        platform: FolderAccess,
    ): PhotoFoldersUiState {
        val byId = watched.associateBy { it.id }
        val listed = offered.orEmpty().map { device ->
            byId[device.id].row(device.id, device.name, device.path, device.imageCount)
        }
        // A known folder the platform stopped offering keeps its row and its count.
        val gone = watched.filter { held -> offered.orEmpty().none { it.id == held.id } }
            .map { it.row(it.id, it.name, it.path, imageCount = null) }

        val rows = (listed + gone)
            .map { row -> if (ticks == null) row else row.copy(ticked = row.id in ticks) }
            .sortedWith(compareByDescending<FolderRowUiModel> { it.watched }.thenBy { it.name })
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

    private fun WatchedFolder?.row(id: String, name: String, path: String, imageCount: Int?) = FolderRowUiModel(
        id = id,
        name = this?.name ?: name,
        path = this?.path ?: path,
        reading = (this?.readingState ?: FolderReading.NeverChecked).phrase(this?.slipsRead ?: 0),
        // Green only while watched (#125): the summary counts watched folders, and the two must agree.
        badged = this?.watched == true && this.foundSlips,
        watched = this?.watched == true,
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
