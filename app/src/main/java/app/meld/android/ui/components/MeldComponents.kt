package app.meld.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meld.android.model.ConversionResult
import app.meld.android.ui.theme.MeldColors
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TactileCard(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = if (onClick != null) modifier.clip(shape).clickable { onClick() } else modifier,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
    ) {
        Box(content = content)
    }
}

@Composable
fun TactileCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MeldColors.PureWhite,
    iconTint: Color = MeldColors.InkBlack,
    size: Dp = 44.dp
) {
    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable { onClick() },
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TactileChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() },
        shape = CircleShape,
        color = if (selected) MeldColors.InkBlack else MeldColors.PureWhite,
        border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MeldColors.PureWhite else MeldColors.InkBlack,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun WorkingDocumentIllustration(
    extension: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(72.dp, 88.dp)) {
        val w = size.width
        val h = size.height
        val cornerFold = 16.dp.toPx()

        // Slight shadow offset card behind
        val path = Path().apply {
            moveTo(0f, 8.dp.toPx())
            lineTo(w - cornerFold, 4.dp.toPx())
            lineTo(w, cornerFold + 6.dp.toPx())
            lineTo(w - 4.dp.toPx(), h)
            lineTo(6.dp.toPx(), h - 4.dp.toPx())
            close()
        }

        drawPath(path = path, color = Color(0xFFFDF8F2))
        drawPath(
            path = path,
            color = MeldColors.InkBlack,
            style = Stroke(width = 2.dp.toPx())
        )

        // Header ink bar inside page
        drawRoundRect(
            color = MeldColors.InkBlack,
            topLeft = Offset(12.dp.toPx(), 18.dp.toPx()),
            size = Size(w * 0.45f, 7.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )

        // Three soft content lines
        val lineColor = Color(0xFFDFD8CE)
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(12.dp.toPx(), 34.dp.toPx()),
            size = Size(w * 0.68f, 5.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(12.dp.toPx(), 46.dp.toPx()),
            size = Size(w * 0.68f, 5.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(12.dp.toPx(), 58.dp.toPx()),
            size = Size(w * 0.50f, 5.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

@Composable
fun TactileRulerSlider(
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeMax = maxValue.coerceAtLeast(minValue + 10)
    val fraction = ((value - minValue).toFloat() / (safeMax - minValue).toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(minValue, safeMax) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val snapped = minValue + (frac * (safeMax - minValue)).roundToInt()
                    onValueChange(snapped)
                }
            }
            .pointerInput(minValue, safeMax) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val snapped = minValue + (frac * (safeMax - minValue)).roundToInt()
                    onValueChange(snapped)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickCount = 24
            val usableWidth = size.width - 16.dp.toPx()
            val startX = 8.dp.toPx()
            val bottomY = size.height - 6.dp.toPx()

            for (i in 0..tickCount) {
                val x = startX + (i.toFloat() / tickCount) * usableWidth
                val tickH = if (i % 4 == 0) 28.dp.toPx() else 16.dp.toPx()
                drawLine(
                    color = MeldColors.InkBlack,
                    start = Offset(x, bottomY - tickH),
                    end = Offset(x, bottomY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Orange tactile capsule needle indicator
            val needleX = startX + fraction * usableWidth
            val needleWidth = 8.dp.toPx()
            val needleHeight = 52.dp.toPx()
            drawRoundRect(
                color = MeldColors.CoralOrange,
                topLeft = Offset(needleX - needleWidth / 2f, bottomY - needleHeight),
                size = Size(needleWidth, needleHeight),
                cornerRadius = CornerRadius(needleWidth / 2f)
            )
            drawRoundRect(
                color = MeldColors.InkBlack,
                topLeft = Offset(needleX - needleWidth / 2f, bottomY - needleHeight),
                size = Size(needleWidth, needleHeight),
                cornerRadius = CornerRadius(needleWidth / 2f),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}

@Composable
fun BeforeAfterComparisonCard(
    nowLabel: String,
    afterLabel: String,
    afterRatio: Float
) {
    val animatedRatio by animateFloatAsState(
        targetValue = afterRatio.coerceIn(0.18f, 0.96f),
        label = "afterBarRatio"
    )

    TactileCard(
        backgroundColor = MeldColors.PureWhite,
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.TextSecondary,
                    modifier = Modifier.width(52.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(MeldColors.TrackGray)
                        .border(MeldColors.StrokeWidth, MeldColors.InkBlack, CircleShape),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = nowLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MeldColors.InkBlack,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "After",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeldColors.TextSecondary,
                    modifier = Modifier.width(52.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatio)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(MeldColors.MintSage)
                            .border(MeldColors.StrokeWidth, MeldColors.InkBlack, CircleShape),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = afterLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MeldColors.InkBlack,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultShowcaseCard(
    result: ConversionResult,
    modifier: Modifier = Modifier
) {
    val previewBitmap = remember(result.outputFile.absolutePath, result.outputSizeBytes) {
        val ext = result.outputFile.extension.lowercase()
        when {
            !result.outputFile.exists() -> null
            ext in setOf("png", "jpg", "jpeg", "webp") ->
                BitmapFactory.decodeFile(result.outputFile.absolutePath)
            ext == "pdf" ->
                renderFirstPdfPage(result.outputFile)
            else -> null
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        TactileCard(
            backgroundColor = MeldColors.SoftLavender,
            cornerRadius = 26.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(225.dp)
        ) {
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Tilted secondary background card for tactile depth
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight(0.88f)
                            .width(145.dp)
                            .offset(x = (-44).dp)
                            .rotate(-4f),
                        shape = RoundedCornerShape(14.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(2.dp, MeldColors.InkBlack)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MELD STUDIO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MeldColors.InkBlack
                            )
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MeldColors.WarmSurface)
                                )
                            }
                        }
                    }

                    // Primary tilted output preview sheet
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .width(155.dp)
                            .offset(x = 32.dp)
                            .rotate(3f),
                        shape = RoundedCornerShape(14.dp),
                        color = MeldColors.PureWhite,
                        border = BorderStroke(2.dp, MeldColors.InkBlack)
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Result preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }
            } else {
                // Text / HTML / DOCX / ZIP / WAV tactile card preview
                val previewText = remember(result.outputFile.absolutePath) {
                    val ext = result.outputFile.extension.lowercase()
                    if (ext in setOf("json", "md", "txt", "html") && result.outputFile.exists()) {
                        result.outputFile.readText().take(240)
                    } else {
                        "${result.tool.title}\nOutput: ${result.outputFile.name}"
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TactileCard(
                        backgroundColor = MeldColors.PureWhite,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = result.outputFile.extension.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MeldColors.CoralOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeldColors.InkBlack,
                                maxLines = 5
                            )
                        }
                    }
                }
            }
        }

        // Overlapping signature coral orange checkmark badge in top-right corner
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = (-10).dp)
                .size(52.dp),
            shape = CircleShape,
            color = MeldColors.CoralOrange,
            border = BorderStroke(MeldColors.StrokeWidth, MeldColors.InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MeldColors.InkBlack,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun renderFirstPdfPage(pdfFile: File): Bitmap? {
    return runCatching {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                } else null
            }
        }
    }.getOrNull()
}
