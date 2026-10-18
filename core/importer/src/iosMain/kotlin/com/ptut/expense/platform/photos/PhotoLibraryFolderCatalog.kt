package com.ptut.expense.platform.photos

import com.ptut.expense.domain.folders.DeviceFolder
import com.ptut.expense.domain.folders.FolderCatalog
import com.ptut.expense.locale.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSPredicate
import platform.Photos.PHAsset
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionSubtypeAny
import platform.Photos.PHAssetCollectionTypeAlbum
import platform.Photos.PHAssetCollectionTypeSmartAlbum
import platform.Photos.PHFetchOptions
import platform.Photos.PHFetchResult

/**
 * The albums a reader can point the importer at.
 *
 * Both collection types are fetched, and the smart one is not optional: Screenshots is a smart
 * album, and slips genuinely land there. Asking only for `PHAssetCollectionTypeAlbum` — which is
 * what the album scan did while the folder was hard-coded — leaves the one album most worth
 * offering impossible to offer.
 *
 * An album has no filesystem path, so [DeviceFolder.path] carries the kind of album it is. That is
 * the honest answer to a row that wants to print where the pictures live: on iOS, nowhere a person
 * can name.
 */
class PhotoLibraryFolderCatalog(
    private val isAllowed: () -> Boolean = PhotoLibraryAccess()::isGranted,
) : FolderCatalog {

    override suspend fun available(): List<DeviceFolder> = withContext(Dispatchers.Default) {
        if (!isAllowed()) return@withContext emptyList()

        val mine = albums(PHAssetCollectionTypeAlbum, AppText.copy.folders.albumYouMade)
        val system = albums(PHAssetCollectionTypeSmartAlbum, AppText.copy.folders.albumIosKeeps)

        (mine + system)
            // Under limited access the same album can come back from both fetches.
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    private fun albums(type: platform.Photos.PHAssetCollectionType, kind: String): List<DeviceFolder> =
        PHAssetCollection.fetchAssetCollectionsWithType(type, PHAssetCollectionSubtypeAny, null)
            .collections()
            .mapNotNull { album ->
                val name = album.localizedTitle ?: return@mapNotNull null
                // Images only, as Android counts them: a smart album also holds videos (#169).
                val images = PHAsset.fetchAssetsInAssetCollection(album, imagesOnly()).count().toInt()
                DeviceFolder(id = album.localIdentifier, name = name, path = kind, imageCount = images)
            }

    private fun imagesOnly() = PHFetchOptions().apply {
        predicate = NSPredicate.predicateWithFormat("mediaType == $IMAGE_MEDIA_TYPE")
    }

    private fun PHFetchResult.collections(): List<PHAssetCollection> =
        (0uL until count()).mapNotNull { objectAtIndex(it) as? PHAssetCollection }
}

/** `PHAssetMediaTypeImage`, written into the predicate's format rather than passed as a vararg. */
private const val IMAGE_MEDIA_TYPE = 1
