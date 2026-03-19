package com.syncbudget.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.syncbudget.app.ui.theme.AdAwareAlertDialog
import com.syncbudget.app.ui.theme.DialogDangerButton
import com.syncbudget.app.ui.theme.DialogSecondaryButton
import com.syncbudget.app.ui.theme.DialogStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.syncbudget.app.ui.strings.LocalStrings
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private const val MAX_IMAGE_DIMENSION = 1000
private const val THUMBNAIL_SIZE = 200
private const val TARGET_BYTES_PER_MEGAPIXEL = 250 * 1024  // 250KB per 1M pixels
private val PHOTO_ROW_HEIGHT = 56.dp

/**
 * Wraps a transaction row with swipe-left-to-reveal-photos.
 *
 * Architecture: the photo panel sits behind the transaction row at the
 * same position. The transaction row slides left via offset, revealing
 * the photo panel underneath. This avoids wide-Row layout issues.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeablePhotoRow(
    transactionId: Int,
    photos: List<Bitmap?>,
    receiptIds: List<String?> = emptyList(),  // parallel to photos, for rotation save
    onPhotosAdded: (List<File>) -> Unit,
    onSwipeOpen: () -> Unit = {},
    onPhotoTap: ((Int) -> Unit)? = null,    // slot index (0-4) tapped
    onPhotoDelete: ((Int) -> Unit)? = null,  // slot index (0-4) to delete
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var containerWidthPx by remember { mutableIntStateOf(0) }
    val offsetX = remember { Animatable(0f) }

    var showCameraPicker by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var deleteConfirmSlot by remember { mutableIntStateOf(-1) }
    var fullScreenSlot by remember { mutableIntStateOf(-1) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    processAndSaveImage(context, tempPhotoUri!!, transactionId)
                }
                if (saved != null) onPhotosAdded(listOf(saved))
            }
        }
    }

    val remainingSlots = 5 - photos.count { it != null }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxOf(remainingSlots, 2))
    ) { uris ->
        val toProcess = uris.take(remainingSlots)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                toProcess.mapNotNull { uri ->
                    processAndSaveImage(context, uri, transactionId)
                }
            }
            if (saved.isNotEmpty()) onPhotosAdded(saved)
        }
    }

    // Delete confirmation dialog
    if (deleteConfirmSlot >= 0) {
        AdAwareAlertDialog(
            onDismissRequest = { deleteConfirmSlot = -1 },
            title = { Text(S.settings.deletePhotoTitle) },
            style = DialogStyle.DANGER,
            text = { Text(S.settings.deletePhotoConfirm) },
            confirmButton = {
                DialogDangerButton(onClick = {
                    onPhotoDelete?.invoke(deleteConfirmSlot)
                    deleteConfirmSlot = -1
                }) { Text(S.common.delete) }
            },
            dismissButton = {
                DialogSecondaryButton(onClick = { deleteConfirmSlot = -1 }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // Full-screen photo viewer — load FULL-SIZE image from disk, not thumbnail
    if (fullScreenSlot >= 0) {
        val viewReceiptId = receiptIds.getOrNull(fullScreenSlot)
        val viewBitmap = remember(viewReceiptId) {
            viewReceiptId?.let {
                com.syncbudget.app.data.sync.ReceiptManager.loadFullImage(context, it)
            } ?: photos.getOrNull(fullScreenSlot) // fallback to thumbnail if no receiptId
        }
        if (viewBitmap != null) {
            FullScreenPhotoViewer(
                bitmap = viewBitmap,
                receiptId = viewReceiptId,
                onDismiss = { fullScreenSlot = -1 },
                onDelete = if (onPhotoDelete != null) {
                    {
                        val slot = fullScreenSlot
                        fullScreenSlot = -1
                        deleteConfirmSlot = slot
                    }
                } else null
            )
        } else {
            fullScreenSlot = -1
        }
    }

    val widthPx = containerWidthPx.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width }
    ) {
        // Layer 1 (back): Photo panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PHOTO_ROW_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value > -widthPx * 0.7f) {
                                    offsetX.animateTo(0f, tween(250))
                                } else {
                                    offsetX.animateTo(-widthPx, tween(250))
                                }
                            }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            val new = (offsetX.value + dragAmount).coerceIn(-widthPx, 0f)
                            offsetX.snapTo(new)
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera handle
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable { showCameraPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = S.settings.receiptPhotosSection,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                DropdownMenu(
                    expanded = showCameraPicker,
                    onDismissRequest = { showCameraPicker = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(S.settings.photoCamera) },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                        onClick = {
                            showCameraPicker = false
                            val file = File(context.cacheDir, "receipt_capture_${UUID.randomUUID()}.jpg")
                            tempPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            cameraLauncher.launch(tempPhotoUri!!)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(S.settings.photoGallery) },
                        leadingIcon = { Icon(Icons.Filled.Collections, null) },
                        onClick = {
                            showCameraPicker = false
                            galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
            }

            // 5 photo frames
            val frameSize = PHOTO_ROW_HEIGHT - 8.dp
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 5) {
                    val thumb = photos.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .size(frameSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (thumb != null) Color.Transparent
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                            .then(
                                if (thumb != null) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (onPhotoTap != null) onPhotoTap(i)
                                            else fullScreenSlot = i
                                        },
                                        onLongClick = {
                                            if (onPhotoDelete != null) deleteConfirmSlot = i
                                        }
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = "Receipt photo ${i + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Layer 2 (front): Transaction row — slides left to reveal photo panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -widthPx * 0.3f) {
                                    offsetX.animateTo(-widthPx, tween(250))
                                    onSwipeOpen()
                                } else {
                                    offsetX.animateTo(0f, tween(250))
                                }
                            }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            val new = (offsetX.value + dragAmount).coerceIn(-widthPx, 0f)
                            offsetX.snapTo(new)
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * Full-screen photo viewer with pinch-zoom, two-axis pan, and rotate.
 * On dismiss, if the image was rotated, saves the rotated version + regenerates thumbnail.
 */
@Composable
fun FullScreenPhotoViewer(
    bitmap: Bitmap,
    receiptId: String? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var rotationSteps by remember { mutableIntStateOf(0) } // 0, 1, 2, 3 = 0°, 90°, 180°, 270°

    val displayBitmap = remember(bitmap, rotationSteps) {
        if (rotationSteps == 0) bitmap
        else {
            val matrix = android.graphics.Matrix()
            matrix.postRotate((rotationSteps * 90).toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    val handleDismiss: () -> Unit = {
        if (rotationSteps % 4 != 0 && receiptId != null) {
            // Save rotated image and regenerate thumbnail
            scope.launch(Dispatchers.IO) {
                try {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate((rotationSteps * 90).toFloat())
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                    // Overwrite full-size file (100% to avoid cumulative compression loss)
                    val fullFile = com.syncbudget.app.data.sync.ReceiptManager.getReceiptFile(context, receiptId)
                    fullFile.outputStream().use { out ->
                        rotated.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }

                    // Regenerate thumbnail
                    val thumbSize = 200
                    val thumbScale = thumbSize.toFloat() / maxOf(rotated.width, rotated.height)
                    val thumb = Bitmap.createScaledBitmap(
                        rotated,
                        (rotated.width * thumbScale).toInt(),
                        (rotated.height * thumbScale).toInt(),
                        true
                    )
                    com.syncbudget.app.data.sync.ReceiptManager.getThumbFile(context, receiptId).outputStream().use { out ->
                        thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    }
                    if (thumb !== rotated) rotated.recycle()
                } catch (e: Exception) {
                    android.util.Log.w("PhotoViewer", "Failed to save rotation: ${e.message}")
                }
            }
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = "Full size receipt photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
            // Close button (top-end)
            IconButton(
                onClick = handleDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = S.common.close,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            // Rotate button (top-start)
            IconButton(
                onClick = {
                    rotationSteps = (rotationSteps + 1) % 4
                    // Reset zoom/pan on rotate
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.RotateRight,
                    contentDescription = "Rotate",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            // Delete button (bottom-end)
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = S.settings.deletePhotoTitle,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

private fun processAndSaveImage(
    context: android.content.Context,
    uri: Uri,
    transactionId: Int
): File? {
    return try {
        // Read original bytes to check dimensions without re-encoding
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
        val origWidth = opts.outWidth
        val origHeight = opts.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        val needsResize = origWidth > MAX_IMAGE_DIMENSION || origHeight > MAX_IMAGE_DIMENSION

        val receiptDir = File(context.filesDir, "receipts")
        receiptDir.mkdirs()
        val fileName = "receipt_${transactionId}_${UUID.randomUUID()}.jpg"
        val fullFile = File(receiptDir, fileName)

        val bitmapForThumb: Bitmap
        val pixelArea = origWidth.toLong() * origHeight
        val targetBytes = (pixelArea * TARGET_BYTES_PER_MEGAPIXEL / 1_000_000L).toInt()
        if (!needsResize && rawBytes.size <= targetBytes) {
            // Already within size and dimension limits — copy raw
            fullFile.writeBytes(rawBytes)
            bitmapForThumb = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return null
        } else {
            // Decode (and resize if needed), then compress to target size
            val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return null
            val bitmap = if (needsResize) resizeBitmap(original, MAX_IMAGE_DIMENSION) else original
            val area = bitmap.width.toLong() * bitmap.height
            val target = (area * TARGET_BYTES_PER_MEGAPIXEL / 1_000_000L).toInt()
            compressToTargetSize(bitmap, fullFile, target)
            if (bitmap !== original) original.recycle()
            bitmapForThumb = bitmap
        }

        // Generate thumbnail (70% is fine for small preview)
        val thumbDir = File(context.filesDir, "receipt_thumbs")
        thumbDir.mkdirs()
        val thumb = resizeBitmap(bitmapForThumb, THUMBNAIL_SIZE)
        File(thumbDir, fileName).outputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
        }
        if (thumb !== bitmapForThumb) bitmapForThumb.recycle()

        fullFile
    } catch (e: Exception) {
        android.util.Log.w("SwipeablePhotoRow", "Failed to process image: ${e.message}")
        null
    }
}

private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap

    val scale = maxDimension.toFloat() / maxOf(width, height)
    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

/**
 * Compress a bitmap to a target file size in bytes.
 * Iterates on the ORIGINAL bitmap (never re-compresses compressed data).
 * Starts at Q=92, uses log-linear interpolation from accumulated data points.
 * Stops as soon as a result falls within ±10% of target.
 */
private fun compressToTargetSize(bitmap: Bitmap, outFile: File, targetBytes: Int) {
    val minTarget = (targetBytes * 0.9).toInt()
    val maxTarget = (targetBytes * 1.1).toInt()
    // Data points: quality -> size
    val samples = mutableListOf<Pair<Int, Int>>()
    var bestBytes: ByteArray? = null
    var bestDistance = Int.MAX_VALUE

    fun tryQuality(q: Int): Boolean {
        if (samples.any { it.first == q }) return false
        val buf = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, q, buf)
        val size = buf.size()
        samples.add(q to size)
        val dist = kotlin.math.abs(size - targetBytes)
        if (dist < bestDistance) { bestDistance = dist; bestBytes = buf.toByteArray() }
        return size in minTarget..maxTarget
    }

    // Attempt 1: Q=92 (high quality)
    if (tryQuality(92)) { outFile.writeBytes(bestBytes!!); return }

    // Attempt 2: go opposite direction
    val firstSize = samples[0].second
    val secondQ = if (firstSize > targetBytes) 50 else 98
    if (tryQuality(secondQ)) { outFile.writeBytes(bestBytes!!); return }

    // Attempt 3+: log-linear interpolation from best two bracketing points
    for (round in 0 until 3) {
        // Find closest points above and below target
        val below = samples.filter { it.second <= targetBytes }.maxByOrNull { it.second }
        val above = samples.filter { it.second > targetBytes }.minByOrNull { it.second }

        val predictedQ = if (below != null && above != null) {
            val lnTarget = kotlin.math.ln(targetBytes.toDouble())
            val lnLo = kotlin.math.ln(below.second.toDouble())
            val lnHi = kotlin.math.ln(above.second.toDouble())
            val denom = lnHi - lnLo
            if (denom > 0.001) {
                (((lnTarget - lnLo) / denom) * (above.first - below.first) + below.first).toInt()
            } else (below.first + above.first) / 2
        } else {
            // All samples on one side — extrapolate linearly
            val s = samples.sortedBy { it.first }
            val ratio = targetBytes.toDouble() / s.last().second
            (s.last().first * ratio).toInt()
        }

        val clampedQ = predictedQ.coerceIn(20, 100)
        if (tryQuality(clampedQ)) { outFile.writeBytes(bestBytes!!); return }
    }

    // Use the best attempt we have
    outFile.writeBytes(bestBytes!!)
}

fun loadReceiptThumbnail(context: android.content.Context, fileName: String): Bitmap? {
    return try {
        val file = File(File(context.filesDir, "receipt_thumbs"), fileName)
        if (!file.exists()) return null
        BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Exception) { null }
}
