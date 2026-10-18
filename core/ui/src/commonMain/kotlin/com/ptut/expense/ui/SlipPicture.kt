package com.ptut.expense.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ptut.expense.design.PtutIcon
import com.ptut.expense.design.Sizes
import com.ptut.expense.design.Spacing
import com.ptut.expense.design.tappable
import com.ptut.expense.domain.slip.SlipPictures
import com.ptut.expense.locale.LocalCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Provided by the shell. Null in tests and previews, which then draw the plain placeholder. */
val LocalSlipPictures = staticCompositionLocalOf<SlipPictures?> { null }

private sealed interface PictureState {
    data object Loading : PictureState

    data class Shown(val image: ImageBitmap) : PictureState

    data object Gone : PictureState
}

/**
 * A queued slip's real picture (#118), decoded off the main thread and kept no larger than
 * [maxSide]. Shown while the phone has it: Android refuses the sweep's delete for K PLUS pictures.
 */
@Composable
fun SlipPicture(
    id: String,
    shape: Shape,
    maxSide: Dp,
    modifier: Modifier = Modifier,
    caption: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    onOpen: (() -> Unit)? = null,
) {
    val pictures = LocalSlipPictures.current
    val openLabel = LocalCopy.current.shell.openSlipPicture
    val maxPx = with(LocalDensity.current) { maxSide.roundToPx() }
    val state by produceState<PictureState>(PictureState.Loading, pictures, id, maxPx) {
        if (pictures == null) return@produceState
        value = pictures.load(id, maxPx)?.let(PictureState::Shown) ?: PictureState.Gone
    }

    when (val picture = state) {
        PictureState.Loading -> SlipPlaceholder(shape = shape, modifier = modifier, caption = caption)

        PictureState.Gone -> SlipPlaceholder(shape = shape, modifier = modifier, caption = LocalCopy.current.shell.pictureGone)

        is PictureState.Shown -> Column(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (onOpen == null) {
                        Modifier
                    } else {
                        Modifier
                            .semantics { contentDescription = openLabel }
                            .tappable(shape, Role.Button, onClick = onOpen)
                    },
                ),
        ) {
            Image(
                bitmap = picture.image,
                contentDescription = LocalCopy.current.shell.slipPicture,
                contentScale = contentScale,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            if (caption != null) SlipCaption(caption)
        }
    }
}

/** The slip at screen size, so a figure too small on the panel can be read against the evidence. */
@Composable
fun SlipPictureViewer(id: String, onClose: () -> Unit) {
    val closeLabel = LocalCopy.current.shell.closeSlipPicture
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding(),
        ) {
            SlipPicture(
                id = id,
                shape = RectangleShape,
                maxSide = maxOf(maxWidth, maxHeight),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(Sizes.minTouchTarget)
                    .semantics { contentDescription = closeLabel }
                    .tappable(CircleShape, Role.Button, onClick = onClose)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            ) {
                Icon(
                    imageVector = PtutIcon.Close.image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Sizes.settingsTrailing),
                )
            }
        }
    }
}

private suspend fun SlipPictures.load(id: String, maxSide: Int): ImageBitmap? = withContext(Dispatchers.Default) {
    val bytes = bytesOf(id) ?: return@withContext null
    runCatching { bytes.decodeToImageBitmap().fitWithin(maxSide) }.getOrNull()
}

/** Only the downscaled copy is kept, so a list of cards never holds a full-size slip. */
private fun ImageBitmap.fitWithin(maxSide: Int): ImageBitmap {
    val longer = maxOf(width, height)
    if (longer <= maxSide) return this
    val target = IntSize(width.scaledTo(maxSide, longer), height.scaledTo(maxSide, longer))
    val fitted = ImageBitmap(target.width, target.height)
    Canvas(fitted).drawImageRect(this, dstSize = target, paint = Paint().apply { filterQuality = FilterQuality.Medium })
    return fitted
}

/** Whole-pixel scaling: the source rules keep floats for money out of everything but Money.kt. */
private fun Int.scaledTo(maxSide: Int, longer: Int): Int = (toLong() * maxSide / longer).toInt().coerceAtLeast(1)
