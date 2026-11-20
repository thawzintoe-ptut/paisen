package com.ptut.expense.domain.folders

/**
 * One row of Photo folders. [known] is what the importer holds for it, or null for a folder never
 * watched; [imageCount] is null for a folder the platform no longer offers.
 */
data class ListedFolder(
    val id: String,
    val name: String,
    val path: String,
    val imageCount: Int?,
    val known: WatchedFolder?,
)

/**
 * FR-5: the catalogue merged with what is known. A folder the platform stopped offering keeps its row and count, since
 * a card pulled out still explains a total. Watched folders first, then by name.
 */
fun mergeFolderList(offered: List<DeviceFolder>?, known: List<WatchedFolder>): List<ListedFolder> {
    val byId = known.associateBy { it.id }
    val listed = offered.orEmpty().map { device ->
        val held = byId[device.id]
        ListedFolder(device.id, held?.name ?: device.name, held?.path ?: device.path, device.imageCount, held)
    }
    val gone = known.filter { held -> offered.orEmpty().none { it.id == held.id } }
        .map { ListedFolder(it.id, it.name, it.path, imageCount = null, known = it) }
    return (listed + gone).sortedWith(compareByDescending<ListedFolder> { it.known?.watched == true }.thenBy { it.name })
}
