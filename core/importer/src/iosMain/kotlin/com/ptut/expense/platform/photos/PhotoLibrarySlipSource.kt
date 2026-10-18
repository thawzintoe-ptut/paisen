package com.ptut.expense.platform.photos

import com.ptut.expense.domain.folders.WatchedFolderRepository
import com.ptut.expense.domain.folders.watched
import com.ptut.expense.platform.toByteArray
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSSortDescriptor
import platform.Foundation.timeIntervalSince1970
import platform.Photos.PHAsset
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHFetchOptions
import platform.Photos.PHFetchResult
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsVersionCurrent
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * How many assets one run may read, across every album together. The Android scan holds the same
 * ceiling for the same reason: an album added later is read from its own beginning, and Screenshots
 * can hold thousands, so an uncapped first run never finishes and never leaves the floor higher.
 */
private const val BATCH_CAP = 200

private const val MILLIS_PER_SECOND = 1_000L

private const val OLDEST_FIRST = "creationDate"

/**
 * FR-2 and FR-5 on iOS, read through `PHPhotoLibrary`.
 *
 * It holds no state of its own beyond the floors it reports, and every decision it could make —
 * dedupe, parsing, what to do with an unreadable slip — belongs to
 * [com.ptut.expense.importer.SlipImporter] and runs
 * in the host suite. What is left here is the translation, which is the part a JVM test could not
 * have run anyway.
 *
 * [delete] does nothing, and that is the platform, not an oversight. Removing an asset this app did
 * not create raises a system confirmation the reader has to answer, and a scan on foreground return
 * has nobody to ask. Android lands in the same place through scoped storage, so the gallery photo
 * stays on both and the app says so (#219). NFR-4's promise holds where it can be kept: on the
 * copies this app makes and on anything it owns. A slip that survives is recognised by its hash on
 * the next scan and never read twice.
 *
 * [isAllowed] and not the whole of [PhotoLibraryAccess], because a scan only ever needs the answer.
 * Asking is the schedule's job — it is the one thing here with a moment to ask in.
 */
class PhotoLibrarySlipSource(
    private val isAllowed: () -> Boolean,
    private val folders: WatchedFolderRepository,
    private val clock: Clock = Clock.System,
) : SlipSource {

    /** Creation second per candidate, so a read that fails can hold its album behind that asset. */
    private var batch = emptyMap<String, Long>()

    /** Which album listed a candidate, so a held floor is held on the right one. */
    private var albumOf = emptyMap<String, String>()

    /** Listed-through per album, held until the next scan settles it. */
    private var pending = mutableMapOf<String, Long>()

    override suspend fun candidates(): List<SlipCandidate> = withContext(Dispatchers.Default) {
        if (!isAllowed()) throw PhotoAccessDenied()

        settlePreviousBatch()

        val watched = folders.folders.value.watched()
        if (watched.isEmpty()) return@withContext emptyList()

        val share = maxOf(1, BATCH_CAP / watched.size)
        val listedAt = clock.now().toEpochMilliseconds()

        val found = mutableListOf<SlipCandidate>()
        val createdAt = mutableMapOf<String, Long>()
        val owner = mutableMapOf<String, String>()

        for (album in watched) {
            val floor = folders.scannedThroughSeconds(album.id)
            val fresh = assetsIn(album.id).filter { it.createdAtSeconds > floor }.take(share)

            fresh.forEach {
                createdAt[it.id] = it.createdAtSeconds
                owner[it.id] = album.id
                found += SlipCandidate(it.id, it.createdAtSeconds * MILLIS_PER_SECOND, album.id, album.name)
            }
            fresh.maxOfOrNull { it.createdAtSeconds }?.let { pending[album.id] = it }

            // Stamped as looked-at now, at the floor it already had: that is what tells an album
            // holding nothing apart from one nothing has ever looked in.
            folders.recordScan(album.id, floor, listedAt)
        }

        batch = createdAt
        albumOf = owner
        found.sortedBy { it.capturedAtEpochMillis }
    }

    /** Nothing is written while a batch is in flight, so a run that died lists its batch again. */
    private suspend fun settlePreviousBatch() {
        if (pending.isEmpty()) return
        val settling = pending
        pending = mutableMapOf()
        val at = clock.now().toEpochMilliseconds()
        settling.forEach { (albumId, through) -> folders.recordScan(albumId, through, at) }
    }

    private fun holdBelow(id: String) {
        val albumId = albumOf[id] ?: return
        val seconds = batch[id] ?: return
        val listed = pending[albumId] ?: return
        pending[albumId] = minOf(listed, seconds - 1)
    }

    override suspend fun bytesOf(id: String): ByteArray? {
        val asset = assetAt(id) ?: return null
        val data = requestImageData(asset)

        if (data == null) {
            // Still in the album, just not readable this minute — an asset offloaded to iCloud is
            // the usual reason. Holding the floor turns that into a retry on the next scan
            // instead of a slip stepped over for good.
            holdBelow(id)
        }
        return data
    }

    /** See the class note: iOS will not let this app remove a picture it did not write, unasked. */
    override suspend fun delete(id: String) = Unit

    private fun assetsIn(albumId: String): List<Slip> {
        val album = PHAssetCollection
            .fetchAssetCollectionsWithLocalIdentifiers(listOf(albumId), null)
            .collections()
            .firstOrNull()
            // An album deleted in Photos, or one outside a limited grant. The row keeps its count
            // and simply reads nothing new, which is the truth about it.
            ?: return emptyList()

        val options = PHFetchOptions().apply {
            sortDescriptors = listOf(NSSortDescriptor.sortDescriptorWithKey(OLDEST_FIRST, ascending = true))
        }

        return PHAsset.fetchAssetsInAssetCollection(album, options).assets()
            .filter { it.mediaType == PHAssetMediaTypeImage }
            .map { Slip(id = it.localIdentifier, createdAtSeconds = it.createdAtSeconds()) }
            // Two albums can hold the same asset, and a run over both must not read it twice.
            .distinctBy { it.id }
            .sortedBy { it.createdAtSeconds }
    }

    private fun assetAt(id: String): PHAsset? =
        PHAsset.fetchAssetsWithLocalIdentifiers(listOf(id), null).assets().firstOrNull()

    /**
     * Suspends until Photos hands the bytes over, and cancels the request if the caller goes away.
     *
     * `networkAccessAllowed` stays false: NFR-1 says v1 makes no outbound request at all, and the
     * default here is one iCloud download away from breaking that promise.
     */
    private suspend fun requestImageData(asset: PHAsset): ByteArray? =
        suspendCancellableCoroutine { waiting ->
            val options = PHImageRequestOptions().apply {
                networkAccessAllowed = false
                deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
                version = PHImageRequestOptionsVersionCurrent
            }

            val request = PHImageManager.defaultManager()
                .requestImageDataAndOrientationForAsset(asset, options) { data, _, _, _ ->
                    waiting.resumeOnce(data?.toByteArray())
                }

            waiting.invokeOnCancellation { PHImageManager.defaultManager().cancelImageRequest(request) }
        }

    private data class Slip(val id: String, val createdAtSeconds: Long)
}

/**
 * Photos may call a handler more than once — a degraded image first, then the full one — and
 * resuming a continuation twice is a crash rather than a wasted call.
 */
private fun CancellableContinuation<ByteArray?>.resumeOnce(bytes: ByteArray?) {
    if (isActive) resume(bytes)
}

/**
 * A slip is a screenshot and a screenshot carries no EXIF date, which leaves the moment Photos
 * first saw the asset as the closest honest stand-in — the same call the Android scan makes.
 */
private fun PHAsset.createdAtSeconds(): Long =
    creationDate?.timeIntervalSince1970?.toLong() ?: 0L

private fun PHFetchResult.assets(): List<PHAsset> = itemsOfType()

private fun PHFetchResult.collections(): List<PHAssetCollection> = itemsOfType()

/**
 * `PHFetchResult` is lazy on purpose: an album of a thousand slips loads one asset at a time as
 * this walks it, which is why nothing here reads image data.
 */
private inline fun <reified T : Any> PHFetchResult.itemsOfType(): List<T> =
    (0uL until count()).mapNotNull { objectAtIndex(it) as? T }
