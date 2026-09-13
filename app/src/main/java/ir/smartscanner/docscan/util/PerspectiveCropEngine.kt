package ir.smartscanner.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max

/**
 * مدل ۴ گوشه اصلی سند (به ترتیب: بالا-چپ، بالا-راست، پایین-راست، پایین-چپ)
 */
data class CornerPoints(
    val p0: Offset, // بالا - چپ (Top-Left)
    val p1: Offset, // بالا - راست (Top-Right)
    val p2: Offset, // پایین - راست (Bottom-Right)
    val p3: Offset  // پایین - چپ (Bottom-Left)
) {
    fun toList(): List<Offset> = listOf(p0, p1, p2, p3)

    fun withPoint(index: Int, newOffset: Offset): CornerPoints {
        return when (index) {
            0 -> copy(p0 = newOffset)
            1 -> copy(p1 = newOffset)
            2 -> copy(p2 = newOffset)
            3 -> copy(p3 = newOffset)
            else -> this
        }
    }
}

/**
 * موتور پردازش و تصحیح پرسپکتیو کاملاً نیتیو با استفاده از android.graphics.Matrix.setPolyToPoly
 */
object PerspectiveCropEngine {

    /**
     * تشخیص هوشمند لبه‌ها (Edge Detection):
     * الگوریتم سبک و سریع نیتیو کاتلین جهت شناسایی ۴ گوشه سند یا حاشیه بهینه
     */
    fun detectDocumentCorners(bitmap: Bitmap): CornerPoints {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        try {
            // برای سرعت بالا، نمونه‌برداری سبکی از ابعاد انجام می‌دهیم
            val sampleW = 120
            val sampleH = 160
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
            val pixels = IntArray(sampleW * sampleH)
            scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

            // محاسبه شدت روشنایی (Luminance) میانگین مرزهای خارجی
            var borderLuminanceSum = 0L
            var borderCount = 0
            for (x in 0 until sampleW) {
                borderLuminanceSum += getLuminance(pixels[x])
                borderLuminanceSum += getLuminance(pixels[(sampleH - 1) * sampleW + x])
                borderCount += 2
            }
            for (y in 0 until sampleH) {
                borderLuminanceSum += getLuminance(pixels[y * sampleW])
                borderLuminanceSum += getLuminance(pixels[y * sampleW + (sampleW - 1)])
                borderCount += 2
            }
            val avgBorderLum = (borderLuminanceSum / max(1, borderCount)).toInt()

            // اسکن از ۴ جهت برای پیدا کردن تغییر کنتراست سند نسبت به پس‌زمینه
            var topY = 0
            var bottomY = sampleH - 1
            var leftX = 0
            var rightX = sampleW - 1

            val thresholdDiff = 25

            // اسکن از بالا
            for (y in 0 until sampleH / 3) {
                var rowLumSum = 0L
                for (x in sampleW / 4 until (sampleW * 3) / 4) {
                    rowLumSum += getLuminance(pixels[y * sampleW + x])
                }
                val rowAvg = (rowLumSum / (sampleW / 2)).toInt()
                if (Math.abs(rowAvg - avgBorderLum) > thresholdDiff) {
                    topY = y
                    break
                }
            }

            // اسکن از پایین
            for (y in sampleH - 1 downTo (sampleH * 2) / 3) {
                var rowLumSum = 0L
                for (x in sampleW / 4 until (sampleW * 3) / 4) {
                    rowLumSum += getLuminance(pixels[y * sampleW + x])
                }
                val rowAvg = (rowLumSum / (sampleW / 2)).toInt()
                if (Math.abs(rowAvg - avgBorderLum) > thresholdDiff) {
                    bottomY = y
                    break
                }
            }

            // اسکن از چپ
            for (x in 0 until sampleW / 3) {
                var colLumSum = 0L
                for (y in sampleH / 4 until (sampleH * 3) / 4) {
                    colLumSum += getLuminance(pixels[y * sampleW + x])
                }
                val colAvg = (colLumSum / (sampleH / 2)).toInt()
                if (Math.abs(colAvg - avgBorderLum) > thresholdDiff) {
                    leftX = x
                    break
                }
            }

            // اسکن از راست
            for (x in sampleW - 1 downTo (sampleW * 2) / 3) {
                var colLumSum = 0L
                for (y in sampleH / 4 until (sampleH * 3) / 4) {
                    colLumSum += getLuminance(pixels[y * sampleW + x])
                }
                val colAvg = (colLumSum / (sampleH / 2)).toInt()
                if (Math.abs(colAvg - avgBorderLum) > thresholdDiff) {
                    rightX = x
                    break
                }
            }

            scaled.recycle()

            // تبدیل مقیاس به ابعاد واقعی تصویر
            val xRatio = width / sampleW
            val yRatio = height / sampleH

            val marginPercent = 0.05f
            val minX = (leftX * xRatio).coerceIn(width * marginPercent, width * 0.25f)
            val maxX = (rightX * xRatio).coerceIn(width * 0.75f, width * (1f - marginPercent))
            val minY = (topY * yRatio).coerceIn(height * marginPercent, height * 0.25f)
            val maxY = (bottomY * yRatio).coerceIn(height * 0.75f, height * (1f - marginPercent))

            return CornerPoints(
                p0 = Offset(minX, minY),
                p1 = Offset(maxX, minY),
                p2 = Offset(maxX, maxY),
                p3 = Offset(minX, maxY)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // پیش‌فرض امن: حاشیه ۵ درصدی استاندارد
            return getDefaultCorners(width, height)
        }
    }

    /**
     * محاسبه ۴ نقطه پیش‌فرض با حاشیه مناسب
     */
    fun getDefaultCorners(width: Float, height: Float, marginFactor: Float = 0.06f): CornerPoints {
        val mx = width * marginFactor
        val my = height * marginFactor
        return CornerPoints(
            p0 = Offset(mx, my),
            p1 = Offset(width - mx, my),
            p2 = Offset(width - mx, height - my),
            p3 = Offset(mx, height - my)
        )
    }

    /**
     * برش پرسپکتیو با تبدیل هندسی نیتیو setPolyToPoly:
     * تصویر کج‌شده یا زاویه‌دار را به یک مستطیل کاملاً صاف و تراز تبدیل می‌کند
     */
    fun cropPerspective(source: Bitmap, corners: CornerPoints): Bitmap {
        val p0 = corners.p0
        val p1 = corners.p1
        val p2 = corners.p2
        val p3 = corners.p3

        // ۱. محاسبه ابعاد هدف بر اساس فاصله اقلیدسی لبه‌ها
        val topWidth = hypot(p1.x - p0.x, p1.y - p0.y)
        val bottomWidth = hypot(p2.x - p3.x, p2.y - p3.y)
        val targetWidth = max(topWidth, bottomWidth).toInt().coerceIn(200, 4000)

        val leftHeight = hypot(p3.x - p0.x, p3.y - p0.y)
        val rightHeight = hypot(p2.x - p1.x, p2.y - p1.y)
        val targetHeight = max(leftHeight, rightHeight).toInt().coerceIn(200, 4000)

        // ۲. آرایه نقاط مبدا (۴ گوشه سند انتخاب شده توسط کاربر)
        val src = floatArrayOf(
            p0.x, p0.y, // بالا چپ
            p1.x, p1.y, // بالا راست
            p2.x, p2.y, // پایین راست
            p3.x, p3.y  // پایین چپ
        )

        // ۳. آرایه نقاط مقصد (مستطیل صاف و عمودی خروجی)
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)

        return if (success) {
            val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, matrix, paint)
            output
        } else {
            // در صورت وجود نقاط غیرمحدب یا هم‌پوشان، برش مستطیلی استاندارد انجام می‌شود
            val minX = minOf(p0.x, p1.x, p2.x, p3.x).toInt().coerceIn(0, source.width - 50)
            val minY = minOf(p0.y, p1.y, p2.y, p3.y).toInt().coerceIn(0, source.height - 50)
            val maxX = maxOf(p0.x, p1.x, p2.x, p3.x).toInt().coerceIn(minX + 50, source.width)
            val maxY = maxOf(p0.y, p1.y, p2.y, p3.y).toInt().coerceIn(minY + 50, source.height)
            Bitmap.createBitmap(source, minX, minY, maxX - minX, maxY - minY)
        }
    }

    /**
     * چرخش ۹۰ درجه تصویر جهت تراز سریع اسناد افقی/عمودی
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun getLuminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
