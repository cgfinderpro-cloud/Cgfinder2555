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
     * الگوریتم سبک و سریع Canny Edge Detection جهت شناسایی ۴ گوشه سند
     */
    fun detectDocumentCorners(bitmap: Bitmap): CornerPoints {
        return try {
            val detected = EdgeDetectionEngine.detectCorners(bitmap)
            detected.toCornerPoints()
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultCorners(bitmap.width.toFloat(), bitmap.height.toFloat())
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

    val transformationManager = TransformationManager()

    /**
     * تنظیم دستی گوشه سند با استفاده از TransformationManager
     */
    fun adjustCorner(corners: CornerPoints, index: Int, newOffset: Offset): CornerPoints {
        return transformationManager.adjustCorner(corners, index, newOffset)
    }

    /**
     * برش پرسپکتیو با تبدیل هندسی نیتیو setPolyToPoly از طریق TransformationManager:
     * تصویر کج‌شده یا زاویه‌دار را به یک مستطیل کاملاً صاف و تراز تبدیل می‌کند
     */
    fun cropPerspective(source: Bitmap, corners: CornerPoints): Bitmap {
        return transformationManager.applyPerspectiveCorrection(source, corners)
    }

    /**
     * چرخش ۹۰ درجه تصویر جهت تراز سریع اسناد افقی/عمودی
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        return transformationManager.rotateBitmap(source, degrees)
    }
}
