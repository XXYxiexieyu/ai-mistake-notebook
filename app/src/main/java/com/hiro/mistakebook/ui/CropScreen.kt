package com.hiro.mistakebook.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiro.mistakebook.data.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun CropDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(ImageStore(context).resolve(imagePath).absolutePath)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("框选题目", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "拖动四个角进行透视校正；只框一道题。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black)
                ) {
                    val source = bitmap
                    if (source != null) {
                        val widthPx = constraints.maxWidth.toFloat()
                        val heightPx = constraints.maxHeight.toFloat()
                        val imageAspect = source.width.toFloat() / source.height
                        val containerAspect = widthPx / heightPx
                        val drawWidth = if (containerAspect > imageAspect) heightPx * imageAspect else widthPx
                        val drawHeight = if (containerAspect > imageAspect) heightPx else widthPx / imageAspect
                        val left = (widthPx - drawWidth) / 2f
                        val top = (heightPx - drawHeight) / 2f
                        val displayRect = RectF(left, top, left + drawWidth, top + drawHeight)
                        val points = remember(imagePath) {
                            mutableStateListOf(
                                Offset(0.04f, 0.04f),
                                Offset(0.96f, 0.04f),
                                Offset(0.96f, 0.96f),
                                Offset(0.04f, 0.96f)
                            )
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(imagePath, displayRect) {
                                    detectDragGestures { change, dragAmount ->
                                        val screenPoints = points.map {
                                            Offset(
                                                displayRect.left + it.x * displayRect.width(),
                                                displayRect.top + it.y * displayRect.height()
                                            )
                                        }
                                        val nearest = screenPoints.indices.minByOrNull { index ->
                                            val distance = hypot(
                                                screenPoints[index].x - change.position.x,
                                                screenPoints[index].y - change.position.y
                                            )
                                            distance
                                        } ?: 0
                                        val current = points[nearest]
                                        points[nearest] = Offset(
                                            (current.x + dragAmount.x / displayRect.width()).coerceIn(0f, 1f),
                                            (current.y + dragAmount.y / displayRect.height()).coerceIn(0f, 1f)
                                        )
                                        change.consume()
                                    }
                                }
                        ) {
                            val image = source.asImageBitmap()
                            drawImage(
                                image = image,
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = androidx.compose.ui.unit.IntSize(source.width, source.height),
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    displayRect.left.toInt(),
                                    displayRect.top.toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    displayRect.width().toInt(),
                                    displayRect.height().toInt()
                                )
                            )
                            val screenPoints = points.map {
                                Offset(
                                    displayRect.left + it.x * displayRect.width(),
                                    displayRect.top + it.y * displayRect.height()
                                )
                            }
                            val path = Path().apply {
                                moveTo(screenPoints[0].x, screenPoints[0].y)
                                lineTo(screenPoints[1].x, screenPoints[1].y)
                                lineTo(screenPoints[2].x, screenPoints[2].y)
                                lineTo(screenPoints[3].x, screenPoints[3].y)
                                close()
                            }
                            drawPath(path, ComposeColor(0x5527C38A))
                            drawPath(path, ComposeColor(0xFF67E8B9), style = Stroke(width = 3.dp.toPx()))
                            screenPoints.forEach { point ->
                                drawCircle(ComposeColor.White, radius = 10.dp.toPx(), center = point)
                                drawCircle(ComposeColor(0xFF176B57), radius = 7.dp.toPx(), center = point)
                            }
                        }
                        Button(
                            onClick = {
                                val pxPoints = points.map {
                                    Offset(
                                        it.x * source.width,
                                        it.y * source.height
                                    )
                                }
                                onCropped(perspectiveCrop(source, pxPoints))
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("使用此框选")
                        }
                    } else {
                        Text("正在读取图片…", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

private fun perspectiveCrop(source: Bitmap, points: List<Offset>): Bitmap {
    val topWidth = hypot(points[1].x - points[0].x, points[1].y - points[0].y)
    val bottomWidth = hypot(points[2].x - points[3].x, points[2].y - points[3].y)
    val leftHeight = hypot(points[3].x - points[0].x, points[3].y - points[0].y)
    val rightHeight = hypot(points[2].x - points[1].x, points[2].y - points[1].y)
    val width = max(64, max(topWidth, bottomWidth).toInt())
    val height = max(64, max(leftHeight, rightHeight).toInt())
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.WHITE)
    val matrix = Matrix()
    val src = floatArrayOf(
        points[0].x, points[0].y,
        points[1].x, points[1].y,
        points[2].x, points[2].y,
        points[3].x, points[3].y
    )
    val dst = floatArrayOf(
        0f, 0f,
        width.toFloat(), 0f,
        width.toFloat(), height.toFloat(),
        0f, height.toFloat()
    )
    if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
        matrix.setRectToRect(
            RectF(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Matrix.ScaleToFit.FILL
        )
    }
    canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return output
}
