package ir.smartscanner.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * مدل داده ۴ گوشه سند اسکن‌شده جهت تراز و برش پرسپکتیو
 * ترتیب استاندارد: بالا-چپ (Top-Left)، بالا-راست (Top-Right)، پایین-راست (Bottom-Right)، پایین-چپ (Bottom-Left)
 */
data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    fun toCornerPoints(): CornerPoints = CornerPoints(
        p0 = Offset(topLeft.x, topLeft.y),
        p1 = Offset(topRight.x, topRight.y),
        p2 = Offset(bottomRight.x, bottomRight.y),
        p3 = Offset(bottomLeft.x, bottomLeft.y)
    )

    fun toPointsArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    fun withCorner(index: Int, point: PointF): DocumentCorners {
        return when (index) {
            0 -> copy(topLeft = point)
            1 -> copy(topRight = point)
            2 -> copy(bottomRight = point)
            3 -> copy(bottomLeft = point)
            else -> this
        }
    }

    companion object {
        fun fromCornerPoints(cp: CornerPoints): DocumentCorners = DocumentCorners(
            topLeft = PointF(cp.p0.x, cp.p0.y),
            topRight = PointF(cp.p1.x, cp.p1.y),
            bottomRight = PointF(cp.p2.x, cp.p2.y),
            bottomLeft = PointF(cp.p3.x, cp.p3.y)
        )
    }
}

/**
 * تنظیمات بهینه‌سازی الگوریتم Canny Edge Detection
 */
data class CannyConfig(
    val sampleWidth: Int = 300,
    val lowThresholdRatio: Float = 0.12f,
    val highThresholdRatio: Float = 0.30f,
    val borderMarginRatio: Float = 0.035f
)

/**
 * موتور تشخیص هوشمند لبه‌ها با استفاده از الگوریتم سبک و سریع Canny Edge Detection و Convex Hull
 * کاملاً نیتیو بدون وابستگی‌های سنگین خارجی، با بهره‌گیری از اولیه‌های گرافیکی android.graphics
 */
object EdgeDetectionEngine {

    /**
     * اجرای کامل پایپ‌لاین تشخیص ۴ گوشه مدرک بر روی تصویر ورودی
     */
    fun detectCorners(
        bitmap: Bitmap,
        config: CannyConfig = CannyConfig()
    ): DocumentCorners {
        val origWidth = bitmap.width.toFloat()
        val origHeight = bitmap.height.toFloat()

        if (origWidth <= 10f || origHeight <= 10f) {
            return getDefaultCorners(origWidth, origHeight)
        }

        try {
            // ۱. مقیاس‌گذاری متناسب جهت پردازش سریع و روان روی دستگاه‌های موبایل
            val targetW = config.sampleWidth.coerceIn(160, 600)
            val aspectRatio = origHeight / origWidth
            val targetH = (targetW * aspectRatio).roundToInt().coerceIn(160, 800)

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

            // ۲. استخراج مقادیر روشنایی (Grayscale)
            val grayscale = extractGrayscale(scaledBitmap, targetW, targetH)
            scaledBitmap.recycle()

            // ۳. فیلتر هموارسازی گوسی دو مرحله‌ای جهت محو کردن کامل متون ریز و برجسته‌سازی مرز کاغذ
            val blurredPass1 = applySeparableGaussianBlur(grayscale, targetW, targetH)
            val blurred = applySeparableGaussianBlur(blurredPass1, targetW, targetH)

            // ۴. محاسبه شیب روشنایی با عملگر سوبل (Sobel Gradient Magnitudes & Angles)
            val (magnitudes, angles) = computeSobelGradients(blurred, targetW, targetH)

            // ۵. سرکوب غیر بیشینه‌ها (Non-Maximum Suppression - NMS) جهت نازک‌سازی خطوط لبه
            val nmsEdges = applyNonMaximumSuppression(magnitudes, angles, targetW, targetH)

            // ۶. آستانه‌گذاری دوگانه و اتصال هیسترزیس (Double Thresholding & Hysteresis Tracking)
            val edgeMask = applyHysteresis(nmsEdges, targetW, targetH, config)

            // ۷. استخراج هندسی ۴ گوشه سند با Convex Hull و نگاشت به ابعاد واقعی تصویر
            val detected = findDocumentQuadCorners(
                edgeMask = edgeMask,
                width = targetW,
                height = targetH,
                origWidth = origWidth,
                origHeight = origHeight,
                borderMarginRatio = config.borderMarginRatio
            )

            return detected ?: getDefaultCorners(origWidth, origHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            return getDefaultCorners(origWidth, origHeight)
        }
    }

    /**
     * خروجی تصویری لبه‌های Canny برای قابلیت‌های عیب‌یابی یا نمایش بصری در رابط کاربری
     */
    fun createCannyEdgeBitmap(
        bitmap: Bitmap,
        config: CannyConfig = CannyConfig()
    ): Bitmap {
        val targetW = config.sampleWidth
        val targetH = (targetW * (bitmap.height.toFloat() / bitmap.width.toFloat())).roundToInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val gray = extractGrayscale(scaled, targetW, targetH)
        scaled.recycle()

        val blurred = applySeparableGaussianBlur(gray, targetW, targetH)
        val (mag, angles) = computeSobelGradients(blurred, targetW, targetH)
        val nms = applyNonMaximumSuppression(mag, angles, targetW, targetH)
        val edgeMask = applyHysteresis(nms, targetW, targetH, config)

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(targetW * targetH)
        for (i in pixels.indices) {
            pixels[i] = if (edgeMask[i]) Color.WHITE else Color.BLACK
        }
        output.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        return output
    }

    /**
     * تولید ۴ گوشه پیش‌فرض با حاشیه استاندارد
     */
    fun getDefaultCorners(width: Float, height: Float, marginRatio: Float = 0.05f): DocumentCorners {
        val mx = width * marginRatio
        val my = height * marginRatio
        return DocumentCorners(
            topLeft = PointF(mx, my),
            topRight = PointF(width - mx, my),
            bottomRight = PointF(width - mx, height - my),
            bottomLeft = PointF(mx, height - my)
        )
    }

    // ============================================================================
    // پیاده‌سازی گام‌به‌گام الگوریتم Canny Edge Detection
    // ============================================================================

    private fun extractGrayscale(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return gray
    }

    /**
     * فیلتر گوسی ۱ بعدی به صورت افقی و عمودی [1, 4, 6, 4, 1] / 16
     * جهت حداکثر کارایی بدون اختصاص حافظه‌های سنگین ماتریسی
     */
    private fun applySeparableGaussianBlur(input: FloatArray, w: Int, h: Int): FloatArray {
        val kernel = floatArrayOf(1f / 16f, 4f / 16f, 6f / 16f, 4f / 16f, 1f / 16f)
        val kRadius = 2
        val temp = FloatArray(w * h)
        val result = FloatArray(w * h)

        // عبور افقی
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var sum = 0f
                for (k in -kRadius..kRadius) {
                    val px = (x + k).coerceIn(0, w - 1)
                    sum += input[rowOffset + px] * kernel[k + kRadius]
                }
                temp[rowOffset + x] = sum
            }
        }

        // عبور عمودی
        for (x in 0 until w) {
            for (y in 0 until h) {
                var sum = 0f
                for (k in -kRadius..kRadius) {
                    val py = (y + k).coerceIn(0, h - 1)
                    sum += temp[py * w + x] * kernel[k + kRadius]
                }
                result[y * w + x] = sum
            }
        }

        return result
    }

    /**
     * محاسبه گرادیان سوبل (Sobel Gradients)
     */
    private fun computeSobelGradients(
        img: FloatArray,
        w: Int,
        h: Int
    ): Pair<FloatArray, FloatArray> {
        val magnitudes = FloatArray(w * h)
        val angles = FloatArray(w * h)

        for (y in 1 until h - 1) {
            val rowPrev = (y - 1) * w
            val rowCurr = y * w
            val rowNext = (y + 1) * w

            for (x in 1 until w - 1) {
                // عملگر افقی سوبل Gx
                val gx = (img[rowPrev + (x + 1)] - img[rowPrev + (x - 1)]) +
                        2f * (img[rowCurr + (x + 1)] - img[rowCurr + (x - 1)]) +
                        (img[rowNext + (x + 1)] - img[rowNext + (x - 1)])

                // عملگر عمودی سوبل Gy
                val gy = (img[rowNext + (x - 1)] - img[rowPrev + (x - 1)]) +
                        2f * (img[rowNext + x] - img[rowPrev + x]) +
                        (img[rowNext + (x + 1)] - img[rowPrev + (x + 1)])

                val mag = sqrt(gx * gx + gy * gy)
                magnitudes[rowCurr + x] = mag

                var angle = (atan2(gy, gx) * 180f / Math.PI.toFloat())
                if (angle < 0f) angle += 180f
                angles[rowCurr + x] = angle
            }
        }

        return Pair(magnitudes, angles)
    }

    /**
     * سرکوب غیر بیشینه‌ها در جهت زاویه گرادیان
     */
    private fun applyNonMaximumSuppression(
        mag: FloatArray,
        angles: FloatArray,
        w: Int,
        h: Int
    ): FloatArray {
        val nms = FloatArray(w * h)

        for (y in 1 until h - 1) {
            val rowPrev = (y - 1) * w
            val rowCurr = y * w
            val rowNext = (y + 1) * w

            for (x in 1 until w - 1) {
                val idx = rowCurr + x
                val currentMag = mag[idx]
                if (currentMag < 1f) continue

                val angle = angles[idx]
                val q: Float
                val r: Float

                // کوانتایز زاویه به ۴ راستای 0، 45، 90، 135 درجه
                if ((angle >= 0f && angle < 22.5f) || (angle >= 157.5f && angle <= 180f)) {
                    // راستای 0 درجه (افقی): مقایسه با همسایه‌های چپ و راست
                    q = mag[rowCurr + (x + 1)]
                    r = mag[rowCurr + (x - 1)]
                } else if (angle >= 22.5f && angle < 67.5f) {
                    // راستای 45 درجه (مورب بالا-راست به پایین-چپ)
                    q = mag[rowPrev + (x + 1)]
                    r = mag[rowNext + (x - 1)]
                } else if (angle >= 67.5f && angle < 112.5f) {
                    // راستای 90 درجه (عمودی): مقایسه با همسایه‌های بالا و پایین
                    q = mag[rowPrev + x]
                    r = mag[rowNext + x]
                } else {
                    // راستای 135 درجه (مورب بالا-چپ به پایین-راست)
                    q = mag[rowPrev + (x - 1)]
                    r = mag[rowNext + (x + 1)]
                }

                if (currentMag >= q && currentMag >= r) {
                    nms[idx] = currentMag
                } else {
                    nms[idx] = 0f
                }
            }
        }

        return nms
    }

    /**
     * آستانه‌گذاری دوگانه تطبیقی و پیگیری لبه‌های متصل از طریق هیسترزیس
     */
    private fun applyHysteresis(
        nms: FloatArray,
        w: Int,
        h: Int,
        config: CannyConfig
    ): BooleanArray {
        var maxMag = 0f
        for (v in nms) {
            if (v > maxMag) maxMag = v
        }

        if (maxMag <= 0f) return BooleanArray(w * h)

        val highThreshold = maxMag * config.highThresholdRatio
        val lowThreshold = maxMag * config.lowThresholdRatio

        // وضعیت‌ها: 0 = بدون لبه، 1 = لبه ضعیف، 2 = لبه قوی
        val states = ByteArray(w * h)
        val queue = ArrayDeque<Int>()

        for (y in 1 until h - 1) {
            val offset = y * w
            for (x in 1 until w - 1) {
                val idx = offset + x
                val v = nms[idx]
                if (v >= highThreshold) {
                    states[idx] = 2
                    queue.add(idx)
                } else if (v >= lowThreshold) {
                    states[idx] = 1
                }
            }
        }

        // انتشار لبه‌های قوی به لبه‌های ضعیف همسایه (8-Connected Hysteresis)
        val dx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

        while (!queue.isEmpty()) {
            val currentIdx = queue.poll()
            val cx = currentIdx % w
            val cy = currentIdx / w

            for (i in 0 until 8) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]
                if (nx in 1 until w - 1 && ny in 1 until h - 1) {
                    val nIdx = ny * w + nx
                    if (states[nIdx].toInt() == 1) {
                        states[nIdx] = 2
                        queue.add(nIdx)
                    }
                }
            }
        }

        val result = BooleanArray(w * h)
        for (i in states.indices) {
            result[i] = (states[i].toInt() == 2)
        }
        return result
    }

    /**
     * استخراج موقعیت ۴ گوشه سند با استفاده از پوش محدب (Convex Hull) و بیشینه‌سازی اکسترمم‌های ۴ ربع
     * این متد متون ریز و نویزهای درون صفحه را نادیده گرفته و مرز اصلی کاغذ را پیدا می‌کند
     */
    private fun findDocumentQuadCorners(
        edgeMask: BooleanArray,
        width: Int,
        height: Int,
        origWidth: Float,
        origHeight: Float,
        borderMarginRatio: Float
    ): DocumentCorners? {
        val marginX = (width * borderMarginRatio).roundToInt().coerceAtLeast(2)
        val marginY = (height * borderMarginRatio).roundToInt().coerceAtLeast(2)

        val rawPoints = ArrayList<PointF>(width * 2)

        // ۱. فیلتر همسایگی و حذف نویزهای تک‌پیکسلی پراکنده
        for (y in marginY until (height - marginY)) {
            val offset = y * width
            for (x in marginX until (width - marginX)) {
                if (edgeMask[offset + x]) {
                    // بررسی اتصال به حداقل ۱ پیکسل لبه دیگر در همسایگی ۳x۳
                    var neighborCount = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val ny = y + dy
                            val nx = x + dx
                            if (nx in 0 until width && ny in 0 until height) {
                                if (edgeMask[ny * width + nx]) {
                                    neighborCount++
                                }
                            }
                        }
                    }
                    if (neighborCount >= 1) {
                        rawPoints.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        if (rawPoints.size < 30) {
            return null
        }

        // زیرنمونه‌گیری متوازن جهت بهینه‌سازی سرعت محاسبات Convex Hull
        val sampledPoints = if (rawPoints.size > 1000) {
            val step = (rawPoints.size / 500).coerceAtLeast(2)
            rawPoints.filterIndexed { index, _ -> index % step == 0 }
        } else {
            rawPoints
        }

        // ۲. محاسبه پوش محدب بیرونی (Convex Hull) جهت حذف قطعی متون و خطوط درون صفحه
        val hull = computeConvexHull(sampledPoints)
        if (hull.size < 4) {
            return null
        }

        // ۳. محاسبه مرکز ثقل (Centroid) پوش محدب
        var sumX = 0f
        var sumY = 0f
        for (p in hull) {
            sumX += p.x
            sumY += p.y
        }
        val cx = sumX / hull.size
        val cy = sumY / hull.size

        // ۴. استخراج ۴ گوشه واقعی سند از رئوس پوش محدب بر اساس بیشینه‌سازی فاصله در ۴ ربع
        var bestTL: PointF? = null
        var bestTR: PointF? = null
        var bestBR: PointF? = null
        var bestBL: PointF? = null

        var maxScoreTL = -Float.MAX_VALUE
        var maxScoreTR = -Float.MAX_VALUE
        var maxScoreBR = -Float.MAX_VALUE
        var maxScoreBL = -Float.MAX_VALUE

        val qSlackX = width * 0.15f
        val qSlackY = height * 0.15f

        for (p in hull) {
            val dx = p.x - cx
            val dy = p.y - cy

            val scoreTL = -dx - dy
            val scoreTR = dx - dy
            val scoreBR = dx + dy
            val scoreBL = -dx + dy

            if (p.x <= cx + qSlackX && p.y <= cy + qSlackY) {
                if (scoreTL > maxScoreTL) {
                    maxScoreTL = scoreTL
                    bestTL = p
                }
            }
            if (p.x >= cx - qSlackX && p.y <= cy + qSlackY) {
                if (scoreTR > maxScoreTR) {
                    maxScoreTR = scoreTR
                    bestTR = p
                }
            }
            if (p.x >= cx - qSlackX && p.y >= cy - qSlackY) {
                if (scoreBR > maxScoreBR) {
                    maxScoreBR = scoreBR
                    bestBR = p
                }
            }
            if (p.x <= cx + qSlackX && p.y >= cy - qSlackY) {
                if (scoreBL > maxScoreBL) {
                    maxScoreBL = scoreBL
                    bestBL = p
                }
            }
        }

        // استفاده از اکسترمم‌های قطعی پوش محدب در صورت خالی بودن هر یک از ربع‌ها
        val finalTL = bestTL ?: hull.minByOrNull { it.x + it.y } ?: return null
        val finalTR = bestTR ?: hull.maxByOrNull { it.x - it.y } ?: return null
        val finalBR = bestBR ?: hull.maxByOrNull { it.x + it.y } ?: return null
        val finalBL = bestBL ?: hull.minByOrNull { it.x - it.y } ?: return null

        val scaleX = origWidth / width.toFloat()
        val scaleY = origHeight / height.toFloat()

        val pTL = PointF(finalTL.x * scaleX, finalTL.y * scaleY)
        val pTR = PointF(finalTR.x * scaleX, finalTR.y * scaleY)
        val pBR = PointF(finalBR.x * scaleX, finalBR.y * scaleY)
        val pBL = PointF(finalBL.x * scaleX, finalBL.y * scaleY)

        val candidateCorners = DocumentCorners(pTL, pTR, pBR, pBL)

        // بررسی محدب بودن چندضلعی و داشتن مساحت کافی
        val manager = TransformationManager()
        if (!manager.isConvexQuad(candidateCorners)) {
            return null
        }

        // اعتبارسنجی حداقل ابعاد سند (حداقل ۲۲ درصد کادر تصویر)
        val topW = hypot(pTR.x - pTL.x, pTR.y - pTL.y)
        val leftH = hypot(pBL.x - pTL.x, pBL.y - pTL.y)
        if (topW < origWidth * 0.22f || leftH < origHeight * 0.22f) {
            return null
        }

        return candidateCorners
    }

    /**
     * پیاده‌سازی الگوریتم Andrew's Monotone Chain برای استخراج سریع Convex Hull با پیچیدگی O(N log N)
     */
    private fun computeConvexHull(points: List<PointF>): List<PointF> {
        val n = points.size
        if (n <= 4) return points

        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))

        fun crossProduct(o: PointF, a: PointF, b: PointF): Float {
            return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        }

        val lower = ArrayList<PointF>()
        for (p in sorted) {
            while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        val upper = ArrayList<PointF>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        if (lower.isNotEmpty()) lower.removeAt(lower.size - 1)
        if (upper.isNotEmpty()) upper.removeAt(upper.size - 1)
        return lower + upper
    }
}

/**
 * کلاس مدیریت تبدیلات هندسی و تصحیح پرسپکتیو اسناد با استفاده از Matrix.setPolyToPoly
 * پشتیبانی کامل از تنظیم دستی گوشه‌ها، چرخش و برش دقیق
 */
class TransformationManager(
    private val useAntiAlias: Boolean = true
) {

    /**
     * اعمال تصحیح پرسپکتیو با تبدیل چندضلعی ۴ گوشه به مستطیل صاف تراز شده
     */
    fun applyPerspectiveCorrection(
        source: Bitmap,
        corners: DocumentCorners
    ): Bitmap {
        val (dstWidth, dstHeight) = calculateOptimalDestinationDimensions(corners)
        val srcPoints = corners.toPointsArray()
        val dstPoints = floatArrayOf(
            0f, 0f,
            dstWidth.toFloat(), 0f,
            dstWidth.toFloat(), dstHeight.toFloat(),
            0f, dstHeight.toFloat()
        )

        return warpBitmap(source, srcPoints, dstPoints, dstWidth, dstHeight)
    }

    /**
     * همگام با مدل CornerPoints رابط کاربری
     */
    fun applyPerspectiveCorrection(
        source: Bitmap,
        corners: CornerPoints
    ): Bitmap {
        return applyPerspectiveCorrection(source, DocumentCorners.fromCornerPoints(corners))
    }

    /**
     * تولید ماتریس پرسپکتیو با Matrix.setPolyToPoly
     */
    fun computePerspectiveMatrix(
        srcCorners: DocumentCorners,
        dstWidth: Float,
        dstHeight: Float
    ): Matrix {
        val matrix = Matrix()
        val src = srcCorners.toPointsArray()
        val dst = floatArrayOf(
            0f, 0f,
            dstWidth, 0f,
            dstWidth, dstHeight,
            0f, dstHeight
        )
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return matrix
    }

    /**
     * محاسبه هوشمند ابعاد خروجی متناسب با فاصله اقلیدسی لبه‌ها جهت جلوگیری از تغییر مقیاس غیرطبیعی
     */
    fun calculateOptimalDestinationDimensions(corners: DocumentCorners): Pair<Int, Int> {
        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        val widthTop = hypot(tr.x - tl.x, tr.y - tl.y)
        val widthBottom = hypot(br.x - bl.x, br.y - bl.y)
        val targetWidth = max(widthTop, widthBottom).roundToInt().coerceIn(150, 4096)

        val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
        val heightRight = hypot(br.x - tr.x, br.y - tr.y)
        val targetHeight = max(heightLeft, heightRight).roundToInt().coerceIn(150, 4096)

        return Pair(targetWidth, targetHeight)
    }

    /**
     * تبدیل هندسی تصویر با استفاده از Matrix.setPolyToPoly بر روی Canvas
     */
    fun warpBitmap(
        source: Bitmap,
        srcPoints: FloatArray,
        dstPoints: FloatArray,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val matrix = Matrix()
        val mappedSuccess = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (mappedSuccess) {
            val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val flags = if (useAntiAlias) {
                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
            } else {
                Paint.FILTER_BITMAP_FLAG
            }
            val paint = Paint(flags)
            canvas.drawBitmap(source, matrix, paint)
            return output
        } else {
            // در صورت بروز تداخل در نقاط، برش مستطیلی با حداقل/حداکثر ابعاد انجام می‌گیرد
            val minX = min(min(srcPoints[0], srcPoints[2]), min(srcPoints[4], srcPoints[6])).toInt().coerceIn(0, source.width - 10)
            val minY = min(min(srcPoints[1], srcPoints[3]), min(srcPoints[5], srcPoints[7])).toInt().coerceIn(0, source.height - 10)
            val maxX = max(max(srcPoints[0], srcPoints[2]), max(srcPoints[4], srcPoints[6])).toInt().coerceIn(minX + 10, source.width)
            val maxY = max(max(srcPoints[1], srcPoints[3]), max(srcPoints[5], srcPoints[7])).toInt().coerceIn(minY + 10, source.height)

            return Bitmap.createBitmap(source, minX, minY, maxX - minX, maxY - minY)
        }
    }

    /**
     * تنظیم دستی یک گوشه توسط کاربر در هنگام درگ کردن نشانگرها (با DocumentCorners)
     */
    fun adjustCorner(
        corners: DocumentCorners,
        cornerIndex: Int,
        newPoint: PointF
    ): DocumentCorners {
        return corners.withCorner(cornerIndex, newPoint)
    }

    /**
     * تنظیم دستی یک گوشه توسط کاربر در هنگام درگ کردن نشانگرها (با CornerPoints در Jetpack Compose)
     */
    fun adjustCorner(
        corners: CornerPoints,
        cornerIndex: Int,
        newPoint: Offset
    ): CornerPoints {
        return corners.withPoint(cornerIndex, newPoint)
    }

    /**
     * انتقال یک نقطه از فضای مبدا به فضای مقصد با استفاده از ماتریس تبدیل
     */
    fun mapPoint(matrix: Matrix, point: PointF): PointF {
        val pts = floatArrayOf(point.x, point.y)
        matrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    /**
     * بررسی هندسی برای اطمینان از محدب بودن ۴ گوشه (Convex Quadrilateral)
     * با بررسی هم‌جهت بودن ضرب خارجی بردارهای متوالی
     */
    fun isConvexQuad(corners: DocumentCorners): Boolean {
        val p = arrayOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
        var sign = 0

        for (i in 0 until 4) {
            val p1 = p[i]
            val p2 = p[(i + 1) % 4]
            val p3 = p[(i + 2) % 4]

            val dx1 = p2.x - p1.x
            val dy1 = p2.y - p1.y
            val dx2 = p3.x - p2.x
            val dy2 = p3.y - p2.y

            val crossProduct = dx1 * dy2 - dy1 * dx2
            if (crossProduct != 0f) {
                val currentSign = if (crossProduct > 0) 1 else -1
                if (sign == 0) {
                    sign = currentSign
                } else if (sign != currentSign) {
                    return false
                }
            }
        }
        return sign != 0
    }

    /**
     * چرخش ۹۰ درجه تصویر جهت تراز اسناد افقی و عمودی
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
