package ir.smartscanner.docscan.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import ir.smartscanner.docscan.model.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * موتور پردازش تصویر اسناد کاملاً نیتیو اندروید بدون نیاز به کتابخانه‌های خارجی
 * با استفاده از ColorMatrix، Canvas و پردازش مستقیم پیکسل‌ها
 */
object DocFilterEngine {

    /**
     * اعمال فیلتر بر روی تصویر به صورت همگام (Synchronous)
     */
    fun applyFilterSync(source: Bitmap, filter: ScanFilter): Bitmap {
        return when (filter) {
            ScanFilter.PHOTOCOPY -> applyPhotocopy(source)
            ScanFilter.BLACK_AND_WHITE -> applyBlackAndWhite(source)
            ScanFilter.CLEAR_COLOR -> applyClearColor(source)
            ScanFilter.ORIGINAL -> source
        }
    }

    /**
     * اعمال فیلتر بر روی تصویر به صورت ناهمگام در دیسپچر Default
     */
    suspend fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap = withContext(Dispatchers.Default) {
        applyFilterSync(source, filter)
    }

    /**
     * فیلتر فتوکپی پیشرفته استودیویی (Studio-Grade Document Photocopy):
     * ۱. برآورد دقیق و پیوسته سطح روشنایی پس‌زمینه با Morphological Closing دو مرحله‌ای در مقیاس چندگانه
     *    (حذف کامل سایه‌های دست، تاشدگی کاغذ، بازتاب‌های نوری در کاغذهای روغنی و شفاف)
     * ۲. نرمال‌سازی بازتابی (Flatfield Illumination Correction) جهت دستیابی به سفیدی یکنواخت در کل سند
     * ۳. نگاشت تونال پیوسته سیگموئید (Smoothstep Sigmoid) برای حفظ نرمی لبه‌های حروف و جلوگیری از پیکسلی شدن
     * ۴. فیلتر Post-Sharpening غیرتخریبی با گیت نویز (Noise-Gated Unsharp Masking)
     * ۵. بازیابی پیوستگی خطوط باریک و نقطه‌های خط فارسی (Persian Ligature & Diacritic Preservation)
     * ۶. فیلتر لکه‌زدایی هوشمند (Despeckle) برای حذف ذرات و نویزهای پراکنده بدون آسیب به نقطه‌های حروف
     */
    fun applyPhotocopy(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val totalPixels = width * height
        val srcPixels = IntArray(totalPixels)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // ۱. استخراج ماتریس روشنایی (Luminance) به شکل آرایه فشرده
        val lum = FloatArray(totalPixels)
        for (i in 0 until totalPixels) {
            val c = srcPixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // فرمول ادراکی استاندارد روشنایی ITU-R BT.601
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // ۲. ساخت نقشه روشنایی پس‌زمینه با نمونه‌برداری شبکه‌ای بهینه و بسته‌شدن مورفولوژیک (Morphological Closing)
        // این روش به طور کامل متن‌ها، امضاها و مهرها را حذف کرده و فقط نور سطح کاغذ (حتی در کاغذهای روغنی و سایه‌ها) را برآورد می‌کند.
        val downScale = maxOf(4, minOf(16, maxOf(width, height) / 120))
        val gw = maxOf(8, (width + downScale - 1) / downScale)
        val gh = maxOf(8, (height + downScale - 1) / downScale)

        // مقادیر بیشینه در هر سلول برای تخمین سطح کاغذ
        val maxGrid = FloatArray(gw * gh)
        for (gy in 0 until gh) {
            val y0 = gy * downScale
            val y1 = minOf(y0 + downScale, height)
            for (gx in 0 until gw) {
                val x0 = gx * downScale
                val x1 = minOf(x0 + downScale, width)

                var maxVal = 0f
                val stepY = maxOf(1, (y1 - y0) / 4)
                val stepX = maxOf(1, (x1 - x0) / 4)

                for (y in y0 until y1 step stepY) {
                    val row = y * width
                    for (x in x0 until x1 step stepX) {
                        val v = lum[row + x]
                        if (v > maxVal) maxVal = v
                    }
                }
                maxGrid[gy * gw + gx] = maxVal.coerceIn(40f, 255f)
            }
        }

        // اتساع مورفولوژیک (Dilation) برای محو کردن تمام خطوط متن و هدینگ‌های ضخیم
        val dilated = FloatArray(gw * gh)
        val dilateRadius = 2
        for (gy in 0 until gh) {
            val minY = maxOf(0, gy - dilateRadius)
            val maxY = minOf(gh - 1, gy + dilateRadius)
            for (gx in 0 until gw) {
                val minX = maxOf(0, gx - dilateRadius)
                val maxX = minOf(gw - 1, gx + dilateRadius)

                var maxV = 0f
                for (y in minY..maxY) {
                    val r = y * gw
                    for (x in minX..maxX) {
                        val v = maxGrid[r + x]
                        if (v > maxV) maxV = v
                    }
                }
                dilated[gy * gw + gx] = maxV
            }
        }

        // فرسایش مورفولوژیک (Erosion) جهت بازگرداندن مقیاس سطحی به تراز مرجع
        val closed = FloatArray(gw * gh)
        for (gy in 0 until gh) {
            val minY = maxOf(0, gy - dilateRadius)
            val maxY = minOf(gh - 1, gy + dilateRadius)
            for (gx in 0 until gw) {
                val minX = maxOf(0, gx - dilateRadius)
                val maxX = minOf(gw - 1, gx + dilateRadius)

                var minV = 255f
                for (y in minY..maxY) {
                    val r = y * gw
                    for (x in minX..maxX) {
                        val v = dilated[r + x]
                        if (v < minV) minV = v
                    }
                }
                closed[gy * gw + gx] = minV
            }
        }

        // هموارسازی ملایم ۳×۳ برای اطمینان از پیوستگی کامل سطح روشنایی
        val bgSurface = FloatArray(gw * gh)
        for (gy in 0 until gh) {
            val minY = maxOf(0, gy - 1)
            val maxY = minOf(gh - 1, gy + 1)
            for (gx in 0 until gw) {
                val minX = maxOf(0, gx - 1)
                val maxX = minOf(gw - 1, gx + 1)

                var sum = 0f
                var count = 0
                for (y in minY..maxY) {
                    val r = y * gw
                    for (x in minX..maxX) {
                        sum += closed[r + x]
                        count++
                    }
                }
                bgSurface[gy * gw + gx] = sum / count
            }
        }

        // ۳. گذر اول: نرمال‌سازی روشنایی موضعی (Flatfield) و اعمال منحنی تونال پیوسته
        val toneBuffer = IntArray(totalPixels)

        for (y in 0 until height) {
            val fy = (y.toFloat() / downScale)
            val gy0 = fy.toInt().coerceIn(0, gh - 1)
            val gy1 = minOf(gy0 + 1, gh - 1)
            val wy = (fy - gy0).coerceIn(0f, 1f)
            val rowOffset = y * width

            for (x in 0 until width) {
                val fx = (x.toFloat() / downScale)
                val gx0 = fx.toInt().coerceIn(0, gw - 1)
                val gx1 = minOf(gx0 + 1, gw - 1)
                val wx = (fx - gx0).coerceIn(0f, 1f)

                // درونیابی دوخطی مقدار روشنایی پس‌زمینه در مختصات پیکسلی
                val top = bgSurface[gy0 * gw + gx0] * (1f - wx) + bgSurface[gy0 * gw + gx1] * wx
                val bottom = bgSurface[gy1 * gw + gx0] * (1f - wy) + bgSurface[gy1 * gw + gx1] * wy
                val bgLum = maxOf(35f, top * (1f - wy) + bottom * wy)

                val idx = rowOffset + x
                val pixelLum = lum[idx]

                // نسبت روشنایی واقعی به روشنایی پس‌زمینه کاغذ در همان ناحیه
                val ratio = (pixelLum / bgLum).coerceIn(0f, 1.25f)

                // نگاشت بهینه فتوکپی با حفظ سفیدی کامل کاغذ و تاریکی یکدست متون
                val tone = when {
                    ratio >= 0.84f -> 255 // کاغذ سفید خالص (حذف زردی، تیرگی پس‌زمینه و روغنی بودن)
                    ratio <= 0.44f -> {
                        // متون و خطوط پررنگ به مشکی عمیق تبدیل می‌شوند
                        val t = (ratio / 0.44f).coerceIn(0f, 1f)
                        (t * 16f).toInt().coerceIn(0, 20)
                    }
                    else -> {
                        // ناحیه خاکستری ملایم لبه حروف برای حفظ آنتی‌آلیاسینگ و خطوط نازک فارسی
                        val t = (ratio - 0.44f) / (0.84f - 0.44f)
                        val s = t * t * (3f - 2f * t) // تابع Smoothstep
                        (16f + s * 239f).toInt().coerceIn(0, 255)
                    }
                }
                toneBuffer[idx] = tone
            }
        }

        // ۴. پردازش Post-Sharpening غیرتخریبی (Non-Destructive Edge Sharpening) همراه با گیت نویز
        // و تقویت پیوستگی اتصالات حروف فارسی و فیلتر لکه‌زدایی
        val outPixels = IntArray(totalPixels)
        val noiseThreshold = 10 // آستانه گیت نویز برای جلوگیری از زبر شدن زمینه

        for (y in 0 until height) {
            val rowOffset = y * width
            val isBorderY = y == 0 || y == height - 1

            for (x in 0 until width) {
                val idx = rowOffset + x
                val center = toneBuffer[idx]

                if (isBorderY || x == 0 || x == width - 1) {
                    outPixels[idx] = if (center > 210) Color.WHITE else Color.rgb(center, center, center)
                    continue
                }

                val left = toneBuffer[idx - 1]
                val right = toneBuffer[idx + 1]
                val top = toneBuffer[idx - width]
                val bottom = toneBuffer[idx + width]

                // فیلتر لکه‌زدایی (Despeckle): پاکسازی ذرات ریز نویز یا گردوغبار اسکنر در میان زمینه سفید
                if (center in 1..220 && left > 240 && right > 240 && top > 240 && bottom > 240) {
                    outPixels[idx] = Color.WHITE
                    continue
                }

                // میانگین همسایگی مستقیم ۴-جهته
                val localMean = (left + right + top + bottom) / 4
                val diff = center - localMean

                var enhancedVal = center

                if (kotlin.math.abs(diff) > noiseThreshold) {
                    // وضوح‌بخشی هوشمند (Unsharp boost) تنها روی لبه‌های قطعی حروف
                    val boost = if (diff > 0) {
                        (diff - noiseThreshold) * 0.45f
                    } else {
                        (diff + noiseThreshold) * 0.55f
                    }
                    enhancedVal = (center + boost).toInt().coerceIn(0, 255)
                }

                // تقویت پیوستگی کلمات و حروف کشیده فارسی (مانند سرکش‌های ک، گ و دندانه‌ها)
                // اگر پیکسلی خاکستری بین دو نقطه تیره قرار گرفته باشد، اتصال آن پررنگ و محکم می‌ماند
                val isHorizontalStroke = (left < 60 && right < 60)
                val isVerticalStroke = (top < 60 && bottom < 60)
                if ((isHorizontalStroke || isVerticalStroke) && enhancedVal in 61..180) {
                    enhancedVal = (enhancedVal * 0.55f).toInt().coerceIn(10, 80)
                }

                // کلمپینگ نهایی برای تضمین پاکیزگی کنتراست
                if (enhancedVal >= 240) {
                    outPixels[idx] = Color.WHITE
                } else if (enhancedVal <= 30) {
                    outPixels[idx] = Color.rgb(enhancedVal / 2, enhancedVal / 2, enhancedVal / 2)
                } else {
                    outPixels[idx] = Color.rgb(enhancedVal, enhancedVal, enhancedVal)
                }
            }
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * فیلتر سیاه و سفید: تبدیل استاندارد به طیف خاکستری تمیز و خوانا
     */
    fun applyBlackAndWhite(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)

        // افزایش ملایم کنتراست برای خوانایی اسناد
        val contrast = 1.3f
        val brightness = 5f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * فیلتر رنگی شفاف: افزایش اشباع رنگ، حذف سایه کاغذ و وضوح بالای متن‌ها و مهرها
     */
    fun applyClearColor(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = ColorMatrix()
        // افزایش اشباع رنگ‌ها برای برجسته‌سازی مهرها و خودکار آبی/قرمز
        matrix.setSaturation(1.45f)

        // تنظیم بهینه کنتراست و روشنایی
        val contrast = 1.25f
        val brightness = 15f
        val adjustMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(adjustMatrix)

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * بارگذاری ایمن Bitmap از Uri گالری با مدیریت سایز حافظه (InSampleSize)
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // محدود کردن سایز به منظور جلوگیری از خطای OutOfMemory
            var sampleSize = 1
            val maxDimension = 1920
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * ساخت بیت‌مپ نمونه ساده و استاندارد برای پیش‌نمایش متنی فیلترها (بدون اطلاعات هویتی یا مهرهای اداری)
     */
    fun createSampleDocBitmap(title: String): Bitmap {
        val width = 900
        val height = 1250
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // پس‌زمینه کاغذ با ته رنگ طبیعی
        canvas.drawColor(Color.rgb(250, 248, 242))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // عنوان سند
        paint.color = Color.rgb(40, 50, 70)
        paint.textSize = 38f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, width / 2f, 130f, paint)

        // خط جداکننده
        paint.strokeWidth = 2.5f
        paint.color = Color.rgb(180, 190, 205)
        canvas.drawLine(80f, 175f, width - 80f, 175f, paint)

        // خطوط متن یادداشت و سند
        paint.isFakeBoldText = false
        paint.strokeWidth = 0f
        paint.color = Color.rgb(55, 65, 81)
        paint.textSize = 28f
        paint.textAlign = Paint.Align.RIGHT

        val sampleLines = listOf(
            "یادداشت و متن آزمایشی جهت بررسی کیفیت اسکن و تفکیک متن",
            "این متن برای ارزیابی عملکرد فیلتر فتوکپی، حذف سایه و شفاف‌سازی تنظیم شده است.",
            "قابلیت تفکیک خطوط نازک و حاشیه‌ها با الگوریتم پردازش تصویر محلی بررسی می‌شود.",
            "تنظیم کنتراست پویا به وضوح بهتر دست‌نوشته‌ها و تایپ در فایل نهایی کمک می‌کند.",
            "صفحات با ابعاد و وضوح استاندارد در خروجی PDF ذخیره می‌شوند."
        )

        var yPos = 260f
        for (line in sampleLines) {
            canvas.drawText(line, width - 80f, yPos, paint)
            yPos += 80f
        }

        return bitmap
    }

    /**
     * ذخیره مدرک پردازش‌شده در حافظه داخلی اختصاصی اپلیکیشن
     */
    suspend fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, title: String): File = withContext(Dispatchers.IO) {
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanTitle = title.replace("\\s+".toRegex(), "_")
        val file = File(docsDir, "DOC_${cleanTitle}_$timeStamp.jpg")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.flush()
        }
        file
    }

    /**
     * آماده‌سازی فایل موقت و ارسال Intent.ACTION_SEND جهت اشتراک‌گذاری در پیام‌رسان‌ها
     */
    suspend fun shareBitmap(context: Context, bitmap: Bitmap, title: String) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val file = File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "مدرک اسکن‌شده: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "اشتراک‌گذاری مدرک با:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
