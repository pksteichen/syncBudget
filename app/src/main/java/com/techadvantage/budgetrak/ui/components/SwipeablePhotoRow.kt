package com.techadvantage.budgetrak.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import com.techadvantage.budgetrak.ui.theme.ScrollableDropdownContent
import com.techadvantage.budgetrak.ui.theme.AdAwareAlertDialog
import com.techadvantage.budgetrak.ui.theme.DialogDangerButton
import com.techadvantage.budgetrak.ui.theme.DialogSecondaryButton
import com.techadvantage.budgetrak.ui.theme.DialogStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.techadvantage.budgetrak.ui.strings.LocalStrings
import com.techadvantage.budgetrak.ui.theme.LocalAppToast
import com.techadvantage.budgetrak.data.sync.ReceiptManager
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

// All receipt-photo compression lives in ReceiptManager.processAndSavePhoto.
// This component used to duplicate that pipeline; now it just calls the service.
private val PHOTO_ROW_HEIGHT = 56.dp

/**
 * Wraps a transaction row with swipe-left-to-reveal-photos.
 *
 * Architecture: the photo panel sits behind the transaction row at the
 * same position. The transaction row slides left via offset, revealing
 * the photo panel underneath. This avoids wide-Row layout issues.
 */
@Composable
fun SwipeablePhotoRow(
    transactionId: Int,
    photos: List<Bitmap?>,
    receiptIds: List<String?> = emptyList(),  // parallel to photos, for rotation save
    onPhotosAdded: (List<String>) -> Unit,    // receiptIds produced by ReceiptManager

    onSwipeOpen: () -> Unit = {},
    onPhotoTap: ((Int) -> Unit)? = null,    // slot index (0-4) tapped
    onPhotoDelete: ((Int) -> Unit)? = null,  // slot index (0-4) to delete
    onPhotoRotated: (() -> Unit)? = null,    // called after rotation save to refresh thumbnails
    onReorder: ((List<String?>) -> Unit)? = null, // new full receiptIds list (length 5, nulls trailing) after drag-reorder
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val toastState = LocalAppToast.current

    var containerWidthPx by remember { mutableIntStateOf(0) }
    // Anchor for toasts triggered from within this row (gallery truncation,
    // pending-placeholder tap). Updated when the photo panel is laid out.
    var photoPanelWindowY by remember { mutableIntStateOf(0) }
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(enabled) {
        if (!enabled) offsetX.animateTo(0f, tween(1000))
    }

    var showCameraPicker by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    // Track the underlying temp file so we can delete it after the camera
    // callback (whether success or failure). Without this, `cacheDir`
    // accumulates `receipt_capture_<uuid>.jpg` files across every capture.
    var tempPhotoFile by remember { mutableStateOf<java.io.File?>(null) }
    var deleteConfirmSlot by remember { mutableIntStateOf(-1) }
    var fullScreenSlot by remember { mutableIntStateOf(-1) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = tempPhotoUri
        val capturedFile = tempPhotoFile
        // Clear refs immediately so a second capture can't interfere
        tempPhotoUri = null
        tempPhotoFile = null
        if (success && capturedUri != null) {
            scope.launch {
                val rid = withContext(Dispatchers.IO) {
                    try {
                        ReceiptManager.processAndSavePhoto(context, capturedUri)
                    } finally {
                        // Always delete the temp file — process-and-save already
                        // copied the bytes into filesDir/receipts/.
                        capturedFile?.delete()
                    }
                }
                if (rid != null) {
                    // Upload-queue enqueue happens at transaction-save time
                    // (MainViewModel.saveTransactions) — queuing here would leak
                    // orphans if the caller never attaches the receipt.
                    onPhotosAdded(listOf(rid))
                }
            }
        } else {
            // Capture failed / user cancelled — still delete the temp file.
            capturedFile?.delete()
        }
    }

    val remainingSlots = 5 - photos.count { it != null }

    // OpenMultipleDocuments (SAF) shows images + PDFs; SAF has no native max
    // so truncate to remaining slots and toast if the user over-picked.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val toProcess = uris.take(remainingSlots)
        if (uris.size > remainingSlots) {
            toastState.show(
                "Only $remainingSlots slot${if (remainingSlots == 1) "" else "s"} available — added first $remainingSlots",
                windowYPx = photoPanelWindowY
            )
        }
        scope.launch {
            val rids = withContext(Dispatchers.IO) {
                // Upload-queue enqueue happens at transaction-save time
                // (MainViewModel.saveTransactions); no queue writes here.
                toProcess.mapNotNull { uri -> ReceiptManager.processAndSavePhoto(context, uri) }
            }
            if (rids.isNotEmpty()) onPhotosAdded(rids)
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
                com.techadvantage.budgetrak.data.sync.ReceiptManager.loadFullImage(context, it)
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
                } else null,
                onRotated = onPhotoRotated
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
                .onGloballyPositioned { coords ->
                    photoPanelWindowY = coords.positionInWindow().y.toInt()
                }
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
                    ScrollableDropdownContent {
                        DropdownMenuItem(
                            text = { Text(S.settings.photoCamera) },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                            onClick = {
                                showCameraPicker = false
                                val file = File(context.cacheDir, "receipt_capture_${UUID.randomUUID()}.jpg")
                                tempPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                tempPhotoFile = file
                                cameraLauncher.launch(tempPhotoUri!!)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(S.settings.photoGallery) },
                            leadingIcon = { Icon(Icons.Filled.Collections, null) },
                            onClick = {
                                showCameraPicker = false
                                galleryLauncher.launch(arrayOf("image/*", "application/pdf"))
                            }
                        )
                    }
                }
            }

            // Photo frames — only show occupied slots + pending-download placeholders.
            // Supports long-press to highlight (blue outline) and long-press-then-drag
            // to reorder among occupied slots. Highlight does NOT persist after
            // release — it's purely a drag-in-progress visual.
            val frameSize = PHOTO_ROW_HEIGHT - 8.dp
            val thumbSpacing = 4.dp
            val strideDp = frameSize + thumbSpacing
            val strideDpFloat = with(density) { strideDp.toPx() }

            // Occupied slots in visual order — includes pending-download placeholders
            // (receiptId set but bytes not yet synced). The receiptId is the identity,
            // so reorder is well-defined even before the image arrives; the thumbnail
            // will land in whichever slot the receiptId ends up in when it syncs.
            val occupiedSlots = (0 until 5).filter {
                photos.getOrNull(it) != null || receiptIds.getOrNull(it) != null
            }

            var draggedSlot by remember { mutableIntStateOf(-1) }
            var dragOffsetXPx by remember { mutableStateOf(0f) }
            var dragDidMove by remember { mutableStateOf(false) }

            val draggedVisibleIdx = if (draggedSlot >= 0) occupiedSlots.indexOf(draggedSlot) else -1
            val proposedNewVisibleIdx = if (draggedVisibleIdx >= 0) {
                val shift = kotlin.math.round(dragOffsetXPx / strideDpFloat).toInt()
                val maxIdx = (occupiedSlots.size - 1).coerceAtLeast(0)
                (draggedVisibleIdx + shift).coerceIn(0, maxIdx)
            } else -1

            val occupiedSlotsState = rememberUpdatedState(occupiedSlots)
            val receiptIdsSnapshot = rememberUpdatedState(receiptIds)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(thumbSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 5) {
                    val thumb = photos.getOrNull(i)
                    val rid = receiptIds.getOrNull(i)
                    if (thumb == null && rid == null) continue
                    val visibleIdx = occupiedSlots.indexOf(i)
                    val isBeingDragged = draggedSlot == i
                    val isPending = thumb == null && rid != null

                    // Shift non-dragged items to make room; dragged item follows finger.
                    val shiftTargetPx: Float = when {
                        draggedVisibleIdx < 0 -> 0f
                        isBeingDragged -> dragOffsetXPx
                        draggedVisibleIdx < visibleIdx && proposedNewVisibleIdx >= visibleIdx -> -strideDpFloat
                        draggedVisibleIdx > visibleIdx && proposedNewVisibleIdx <= visibleIdx -> strideDpFloat
                        else -> 0f
                    }
                    val animatedShiftPx by animateIntAsState(
                        targetValue = shiftTargetPx.toInt(),
                        animationSpec = if (isBeingDragged) tween(0) else tween(150),
                        label = "rowThumbShift"
                    )
                    val renderShiftPx = if (isBeingDragged) shiftTargetPx.toInt() else animatedShiftPx

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(renderShiftPx, 0) }
                            .zIndex(if (isBeingDragged) 1f else 0f)
                            .size(frameSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPending) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .border(
                                width = if (isBeingDragged) 2.dp else 1.dp,
                                color = if (isBeingDragged) Color(0xFF2196F3)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            // Tap → full-screen viewer when the image is on disk, or a
                            // short toast explaining the wait when the pending bytes haven't
                            // arrived from the SYNC device that added it.
                            .clickable {
                                if (isPending) {
                                    toastState.show(
                                        S.settings.pendingPhotoTapped,
                                        windowYPx = photoPanelWindowY
                                    )
                                } else {
                                    if (onPhotoTap != null) onPhotoTap(i)
                                    else fullScreenSlot = i
                                }
                            }
                            .pointerInput(i) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedSlot = i
                                        dragOffsetXPx = 0f
                                        dragDidMove = false
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetXPx += dragAmount.x
                                        if (kotlin.math.abs(dragAmount.x) > 0.5f) dragDidMove = true
                                    },
                                    onDragEnd = {
                                        val curOccupied = occupiedSlotsState.value
                                        val curRids = receiptIdsSnapshot.value
                                        val curVisIdx = curOccupied.indexOf(i)
                                        val shiftCells = kotlin.math.round(dragOffsetXPx / strideDpFloat).toInt()
                                        val maxIdx = (curOccupied.size - 1).coerceAtLeast(0)
                                        val curProposed = (curVisIdx + shiftCells).coerceIn(0, maxIdx)
                                        if (dragDidMove && curVisIdx >= 0 && curProposed != curVisIdx && onReorder != null) {
                                            val newOrder = curOccupied.toMutableList().apply {
                                                val item = removeAt(curVisIdx)
                                                add(curProposed, item)
                                            }
                                            val newRids = newOrder.map { curRids.getOrNull(it) }
                                            val padded = List(5) { idx -> newRids.getOrNull(idx) }
                                            onReorder.invoke(padded)
                                        }
                                        // Highlight does NOT persist on the list-row variant;
                                        // just clear drag state regardless of outcome.
                                        draggedSlot = -1
                                        dragOffsetXPx = 0f
                                        dragDidMove = false
                                    },
                                    onDragCancel = {
                                        draggedSlot = -1
                                        dragOffsetXPx = 0f
                                        dragDidMove = false
                                    }
                                )
                            },
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
                            // Pending download placeholder
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
    onDelete: (() -> Unit)? = null,
    onRotated: (() -> Unit)? = null
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var rotationSteps by remember { mutableIntStateOf(0) } // 0, 1, 2, 3 = 0°, 90°, 180°, 270°

    // Tracks the most recent rotated copy so we can recycle it when the user
    // rotates again or dismisses the viewer. Without this, every rotation tap
    // leaks a full-size Bitmap; four taps on a 24 MB receipt = 96 MB in memory.
    val previousRotatedRef = remember { androidx.compose.runtime.mutableStateOf<Bitmap?>(null) }
    val displayBitmap = remember(bitmap, rotationSteps) {
        // Recycle the prior rotated bitmap (if any) before allocating a new one.
        previousRotatedRef.value?.let { prev ->
            if (prev !== bitmap && !prev.isRecycled) prev.recycle()
            previousRotatedRef.value = null
        }
        if (rotationSteps == 0) {
            bitmap
        } else {
            val matrix = android.graphics.Matrix()
            matrix.postRotate((rotationSteps * 90).toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            previousRotatedRef.value = rotated
            rotated
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            previousRotatedRef.value?.let { prev ->
                if (prev !== bitmap && !prev.isRecycled) prev.recycle()
            }
            previousRotatedRef.value = null
        }
    }

    val appToast = LocalAppToast.current
    // Rate-limit repeated save attempts so a disk-full / OOM retry storm can't
    // spin up multiple concurrent compression passes.
    var rotationSaveInFlight by remember { mutableStateOf(false) }

    val handleDismiss: () -> Unit = handleDismiss@{
        if (rotationSteps % 4 == 0 || receiptId == null) {
            onDismiss()
            return@handleDismiss
        }

        // Gate: if the local file is missing (pending-download placeholder, or
        // file was pruned between viewer open and rotation save), refuse and
        // show a toast. Saving rotation without a source file would leave a
        // stale or missing blob that peers would later invalidate as corrupt.
        if (!ReceiptManager.hasLocalFile(context, receiptId)) {
            appToast.show(S.settings.receiptRotationNoLocalFile)
            onDismiss()
            return@handleDismiss
        }
        if (rotationSaveInFlight) return@handleDismiss
        rotationSaveInFlight = true

        scope.launch {
            val saved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate((rotationSteps * 90).toFloat())
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    val ok = ReceiptManager.replaceReceipt(context, receiptId, rotated)
                    if (rotated !== bitmap) rotated.recycle()
                    ok
                } catch (e: Exception) {
                    android.util.Log.w("RotationSave", "Failed to save rotation for $receiptId: ${e.message}")
                    false
                }
            }
            rotationSaveInFlight = false
            if (saved) {
                onRotated?.invoke()
                onDismiss()
            } else {
                // Keep the viewer open so the user can retry or cancel by
                // tapping outside. Toast the reason.
                appToast.show(S.settings.receiptRotationSaveFailed)
            }
        }
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

// All receipt-photo compression lives in ReceiptManager — the duplicate
// pipeline that used to live here (processAndSaveImage, resizeBitmap,
// compressToTargetSize, loadReceiptThumbnail) was removed in the min-dim
// floor refactor. Rotation-save goes through ReceiptManager.replaceReceipt.

