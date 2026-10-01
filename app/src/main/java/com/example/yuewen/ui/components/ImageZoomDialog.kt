package com.example.yuewen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * 文章图片全屏预览：双指捏合缩放（1x~6x）、放大后单指拖动、双击在 1x / 2.5x 间切换、
 * 点空白处或右上角 ✕ 关闭。
 *
 * 为什么不用现成的 zoomable 库：正文里图片数量不定，为一个手势引入一整个依赖不划算；
 * detectTransformGestures + graphicsLayer 三个状态就够，行为也更可控。
 */
@Composable
fun ImageZoomDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                // 单击关闭 / 双击缩放。挂在父 Box 上，手势未被内层消费时才触发：
                // 单指拖动会被内层 transform 检测消费，不会误关。
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.5f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = url,
                contentDescription = "图片预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offsetX; translationY = offsetY
                    }
                    // 双指缩放 + 放大后的拖动。缩到 1x 时自动回正中。
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val new = (scale * zoom).coerceIn(1f, 6f)
                            scale = new
                            if (new > 1.01f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f; offsetY = 0f
                            }
                        }
                    }
            )

            Text(
                "单击关闭 · 双击放大/还原 · 双指缩放",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}
