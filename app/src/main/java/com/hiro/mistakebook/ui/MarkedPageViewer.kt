package com.hiro.mistakebook.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.ui.theme.Amber

data class PageWordMark(
    val box: List<Float>,
    val label: String,
    val color: Color = Amber
)

fun sourceImageAspectRatio(context: Context, path: String): Float {
    val file = ImageStore(context).resolve(path)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return if (options.outWidth > 0 && options.outHeight > 0) {
        options.outWidth.toFloat() / options.outHeight
    } else {
        0.707f
    }
}

@Composable
fun MarkedPageImage(
    path: String,
    marks: List<PageWordMark>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageAspect = remember(path) { sourceImageAspectRatio(context, path) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(14.dp))) {
        ImagePanel(path, Modifier.fillMaxSize(), ContentScale.Fit)
        val containerAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val drawWidth = if (containerAspect > imageAspect) maxHeight * imageAspect else maxWidth
        val drawHeight = if (containerAspect > imageAspect) maxHeight else maxWidth / imageAspect
        val imageLeft = (maxWidth - drawWidth) / 2
        val imageTop = (maxHeight - drawHeight) / 2

        marks.forEach { mark ->
            val box = mark.box
            if (box.size < 4) return@forEach
            val leftRatio = box[0].coerceIn(0f, 1000f) / 1000f
            val topRatio = box[1].coerceIn(0f, 1000f) / 1000f
            val rightRatio = box[2].coerceIn(0f, 1000f) / 1000f
            val bottomRatio = box[3].coerceIn(0f, 1000f) / 1000f
            if (rightRatio <= leftRatio || bottomRatio <= topRatio) return@forEach
            val markLeft = imageLeft + drawWidth * leftRatio
            val markTop = imageTop + drawHeight * topRatio
            val markWidth = (drawWidth * (rightRatio - leftRatio)).coerceAtLeast(18.dp)
            val markHeight = (drawHeight * (bottomRatio - topRatio)).coerceAtLeast(18.dp)
            Box(
                modifier = Modifier
                    .offset(x = markLeft, y = markTop)
                    .size(markWidth, markHeight)
                    .background(mark.color.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                    .border(2.dp, mark.color, RoundedCornerShape(3.dp))
            )
            if (mark.label.isNotBlank()) {
                Text(
                    mark.label,
                    modifier = Modifier
                        .offset(x = markLeft, y = if (markTop > 24.dp) markTop - 22.dp else 0.dp)
                        .background(mark.color, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MarkedPageFullscreenDialog(
    path: String,
    marks: List<PageWordMark>,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                }
        ) {
            MarkedPageImage(
                path = path,
                marks = marks,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Icon(Icons.Outlined.Close, "关闭图片", tint = Color.White)
                }
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier.background(
                        Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Text("重置缩放", color = Color.White)
                }
            }
            Text(
                "双指缩放 · 拖动查看拍摄篇目",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
