package com.techadvantage.budgetrak.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HSV color wheel picker: ring of hues, drag/tap the puck to choose hue+saturation.
 * Below: a value/brightness slider and a hex input field.
 *
 * Stateless — caller owns the [color] state and receives changes via [onColorChange].
 */
@Composable
fun ColorWheelPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    // State seeded once from the initial color. Re-keying on `color` would
    // recreate the underlying mutable state objects on every emit, leaving
    // HueSatWheel's pointerInput holding stale references to the previous
    // value/sat/hue — which made the brightness slider's last value snap
    // back to the originally-loaded one as soon as the wheel was touched.
    val initialHsv = remember { color.toHsv() }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(color.toHex()) }

    fun emit(h: Float, s: Float, v: Float) {
        val c = Color.hsv(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        hexInput = c.toHex()
        onColorChange(c)
    }

    Column(modifier = modifier) {
        // Hue/saturation wheel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            HueSatWheel(
                hue = hue,
                sat = sat,
                value = value,
                onChange = { h, s ->
                    hue = h; sat = s
                    emit(h, s, value)
                },
            )
        }

        // Brightness/Value slider — gradient from black to current hue/sat at full V.
        val fullColor = Color.hsv(hue, sat, 1f)
        Spacer(Modifier.height(8.dp))
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .background(
                        Brush.horizontalGradient(listOf(Color.Black, fullColor)),
                        RoundedCornerShape(4.dp),
                    ),
            )
            Slider(
                value = value,
                onValueChange = { v ->
                    value = v
                    emit(hue, sat, v)
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Hex input + swatch preview.
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    hexInput = raw
                    parseHex(raw)?.let { parsed ->
                        val newHsv = parsed.toHsv()
                        hue = newHsv[0]
                        sat = newHsv[1]
                        value = newHsv[2]
                        onColorChange(parsed)
                    }
                },
                label = { Text("Hex") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HueSatWheel(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (hue: Float, sat: Float) -> Unit,
) {
    val density = LocalDensity.current
    var sizePx by remember { mutableFloatStateOf(0f) }

    fun pickAt(offset: Offset) {
        val r = sizePx / 2f
        if (r <= 0f) return
        val dx = offset.x - r
        val dy = offset.y - r
        val dist = sqrt(dx * dx + dy * dy)
        val s = (dist / r).coerceIn(0f, 1f)
        var h = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
        onChange(h, s)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { pickAt(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pickAt(change.position) }
            },
    ) {
        sizePx = size.minDimension
        val r = sizePx / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Hue sweep (angular).
        val sweep = Brush.sweepGradient(
            colors = listOf(
                Color.hsv(0f, 1f, 1f),
                Color.hsv(60f, 1f, 1f),
                Color.hsv(120f, 1f, 1f),
                Color.hsv(180f, 1f, 1f),
                Color.hsv(240f, 1f, 1f),
                Color.hsv(300f, 1f, 1f),
                Color.hsv(360f, 1f, 1f),
            ),
            center = center,
        )
        drawCircle(brush = sweep, radius = r, center = center)
        // Radial white-to-transparent overlay → saturation falls off toward center.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
        // Value dim — overlay black with (1 - v) alpha.
        if (value < 1f) {
            drawCircle(
                color = Color.Black.copy(alpha = 1f - value),
                radius = r,
                center = center,
            )
        }

        // Puck.
        val puckAngle = Math.toRadians(hue.toDouble())
        val puckR = r * sat
        val puckCenter = Offset(
            center.x + (puckR * cos(puckAngle)).toFloat(),
            center.y + (puckR * sin(puckAngle)).toFloat(),
        )
        val puckSize = with(density) { 14.dp.toPx() }
        drawCircle(Color.White, radius = puckSize, center = puckCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        drawCircle(Color.Black, radius = puckSize, center = puckCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

/** Wrap the wheel in an AdAwareDialog with green header + Save/Cancel footer. */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onSave: (Color) -> Unit,
) {
    var current by remember { mutableStateOf(initial) }
    AdAwareDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                DialogHeader(title = title)
                Box(modifier = Modifier.padding(16.dp)) {
                    ColorWheelPicker(color = current, onColorChange = { current = it })
                }
                DialogFooter {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DialogSecondaryButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        DialogPrimaryButton(onClick = { onSave(current) }) { Text("Save") }
                    }
                }
            }
        }
    }
}

// ─────────── helpers ───────────

private fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    return hsv
}

private fun Color.toHex(): String =
    String.format("#%06X", this.toArgb() and 0x00FFFFFF)

private fun parseHex(s: String): Color? {
    val hex = s.trim().removePrefix("#")
    return try {
        when (hex.length) {
            6 -> Color(android.graphics.Color.parseColor("#FF$hex"))
            8 -> Color(android.graphics.Color.parseColor("#$hex"))
            else -> null
        }
    } catch (_: Exception) { null }
}
