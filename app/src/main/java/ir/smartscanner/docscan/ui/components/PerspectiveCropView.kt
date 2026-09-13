package ir.smartscanner.docscan.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.smartscanner.docscan.ui.theme.PrimaryBlue
import ir.smartscanner.docscan.ui.theme.PrimaryBlueDark
import ir.smartscanner.docscan.util.CornerPoints
import ir.smartscanner.docscan.util.PerspectiveCropEngine
import kotlin.math.hypot

@Composable
fun PerspectiveCropView(
    initialBitmap: Bitmap,
    onConfirmCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var workingBitmap by remember { mutableStateOf(initialBitmap) }

    // نقاط ۴ گوشه روی ابعاد واقعی Bitmap (به ترتیب: 0=بالا چپ، 1=بالا راست، 2=پایین راست، 3=پایین چپ)
    var corners by remember(workingBitmap) {
        mutableStateOf(PerspectiveCropEngine.detectDocumentCorners(workingBitmap))
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandleIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current.density

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827)) // زمینه تیره برای تمرکز بر سند
    ) {
        // نوار بالای صفحه برش
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "انصراف",
                    tint = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "برش و تنظیم پرسپکتیو",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "۴ گوشه سند را جهت تراز کادر جابجا کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = {
                    // چرخش ۹۰ درجه تصویر
                    val rotated = PerspectiveCropEngine.rotateBitmap(workingBitmap, 90f)
                    workingBitmap = rotated
                    corners = PerspectiveCropEngine.detectDocumentCorners(rotated)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "چرخش ۹۰ درجه",
                    tint = Color.White
                )
            }
        }

        // محفظه تعاملی نمایش تصویر + کادر ۴ گوشه قابل لمس
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            val cW = containerSize.width.toFloat()
            val cH = containerSize.height.toFloat()

            if (cW > 0 && cH > 0) {
                val bW = workingBitmap.width.toFloat()
                val bH = workingBitmap.height.toFloat()

                val imageAspect = bW / bH
                val containerAspect = cW / cH

                val displayedW: Float
                val displayedH: Float
                val offsetX: Float
                val offsetY: Float

                if (imageAspect > containerAspect) {
                    displayedW = cW
                    displayedH = cW / imageAspect
                    offsetX = 0f
                    offsetY = (cH - displayedH) / 2f
                } else {
                    displayedH = cH
                    displayedW = cH * imageAspect
                    offsetX = (cW - displayedW) / 2f
                    offsetY = 0f
                }

                val scaleX = displayedW / bW
                val scaleY = displayedH / bH

                fun toScreen(bmpOffset: Offset): Offset {
                    return Offset(
                        x = bmpOffset.x * scaleX + offsetX,
                        y = bmpOffset.y * scaleY + offsetY
                    )
                }

                fun toBitmap(screenOffset: Offset): Offset {
                    val bx = ((screenOffset.x - offsetX) / scaleX).coerceIn(0f, bW)
                    val by = ((screenOffset.y - offsetY) / scaleY).coerceIn(0f, bH)
                    return Offset(bx, by)
                }

                val s0 = toScreen(corners.p0)
                val s1 = toScreen(corners.p1)
                val s2 = toScreen(corners.p2)
                val s3 = toScreen(corners.p3)
                val screenPoints = listOf(s0, s1, s2, s3)

                // ۱. تصویر پیش‌نمایش
                Image(
                    bitmap = workingBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )

                // ۲. لایه خطوط و های‌لایت کادر پرسپکتیو
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // ترسیم چندضلعی کادر
                    val polygonPath = Path().apply {
                        moveTo(s0.x, s0.y)
                        lineTo(s1.x, s1.y)
                        lineTo(s2.x, s2.y)
                        lineTo(s3.x, s3.y)
                        close()
                    }

                    // سایه پرسپکتیو درون سند
                    drawPath(
                        path = polygonPath,
                        color = PrimaryBlue.copy(alpha = 0.22f)
                    )

                    // خطوط حاشیه کادر سند
                    drawPath(
                        path = polygonPath,
                        color = PrimaryBlue,
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // خطوط فرضی متقاطع کم‌رنگ
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = s0,
                        end = s2,
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = s1,
                        end = s3,
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // ۳. لایه لمسی و رهگیری کشیدن دستگیره‌های ۴ گوشه
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(corners, displayedW, displayedH) {
                            detectDragGestures(
                                onDragStart = { startPos ->
                                    val touchThreshold = 55.dp.toPx()
                                    // یافتن نزدیک‌ترین دستگیره به انگشت کاربر
                                    var closestIndex: Int? = null
                                    var minDistance = Float.MAX_VALUE

                                    screenPoints.forEachIndexed { index, pt ->
                                        val dist = hypot(pt.x - startPos.x, pt.y - startPos.y)
                                        if (dist < touchThreshold && dist < minDistance) {
                                            minDistance = dist
                                            closestIndex = index
                                        }
                                    }
                                    activeHandleIndex = closestIndex
                                },
                                onDragEnd = {
                                    activeHandleIndex = null
                                },
                                onDragCancel = {
                                    activeHandleIndex = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val index = activeHandleIndex ?: return@detectDragGestures
                                    val currentScreenPt = screenPoints[index]
                                    val newScreenPt = currentScreenPt + dragAmount
                                    val newBmpPt = toBitmap(newScreenPt)
                                    corners = corners.withPoint(index, newBmpPt)
                                }
                            )
                        }
                ) {
                    // ۴. المان‌های بصری دستگیره‌ها در محل ۴ گوشه
                    screenPoints.forEachIndexed { index, pt ->
                        val isActive = activeHandleIndex == index
                        val handleRadius = if (isActive) 18.dp else 14.dp

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = (pt.x / density - handleRadius.value).dp,
                                    y = (pt.y / density - handleRadius.value).dp
                                )
                                .size(handleRadius * 2)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(if (isActive) PrimaryBlueDark else PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }

        // نوار ابزارهای کمکی و دکمه‌های پایینی
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1F2937),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // دکمه‌های تشخیص خودکار و تمام‌صفحه
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // تشخیص هوشمند خودکار لبه‌ها
                            corners = PerspectiveCropEngine.detectDocumentCorners(workingBitmap)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تشخیص هوشمند",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            // کادر کامل با حاشیه ۳ درصد
                            corners = PerspectiveCropEngine.getDefaultCorners(
                                workingBitmap.width.toFloat(),
                                workingBitmap.height.toFloat(),
                                marginFactor = 0.03f
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "کادر کامل",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // دکمه اصلی تأیید برش و تصحیح پرسپکتیو
                Button(
                    onClick = {
                        val cropped = PerspectiveCropEngine.cropPerspective(workingBitmap, corners)
                        onConfirmCrop(cropped)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تأیید برش و تراز پرسپکتیو",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
