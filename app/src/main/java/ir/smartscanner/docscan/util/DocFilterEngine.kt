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
     * اعمال فیلتر بر روی تصویر به صورت ناهمگام در دیسپچر IO
     */
    suspend fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            ScanFilter.PHOTOCOPY -> applyPhotocopy(source)
            ScanFilter.BLACK_AND_WHITE -> applyBlackAndWhite(source)
            ScanFilter.CLEAR_COLOR -> applyClearColor(source)
            ScanFilter.ORIGINAL -> source
        }
    }

    /**
     * فیلتر فتوکپی با کیفیت فوق‌العاده بالا (Studio-Grade Document Photocopy):
     * ۱. حذف ناهمگونی‌های نوری و سایه‌های دست/محیط با تصحیح سطح پس‌زمینه (Adaptive Flatfield Illumination)
     * ۲. حفظ لبه‌های نرم حروف و جلوگیری از پیکسلی یا شکسته شدن خطوط با نگاشت تونال پیوسته (Softstep Sigmoid)
     * ۳. تقویت وضوح متن و خوانایی خطوط فارسی با فیلتر شارپ هوشمند بدون افزایش نویز
     */
    fun applyPhotocopy(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // اندازه بلاک‌های تخمین نور پس‌زمینه کاغذ متناسب با رزولوشن سند
        val blockSize = (maxOf(width, height) / 24).coerceIn(24, 64)
        val gridX = (width + blockSize - 1) / blockSize
        val gridY = (height + blockSize - 1) / blockSize

        // محاسبه سطح روشنایی پس‌زمینه کاغذ در هر بلاک (چارک ۸۵ام روشنایی جهت حذف سایه‌ها)
        val bgRaw = FloatArray(gridX * gridY)
        val hist = IntArray(32)

        for (gy in 0 until gridY) {
            val y0 = gy * blockSize
            val y1 = minOf(y0 + blockSize, height)
            for (gx in 0 until gridX) {
                val x0 = gx * blockSize
                val x1 = minOf(x0 + blockSize, width)

                hist.fill(0)
                var sampledCount = 0

                val stepY = maxOf(1, (y1 - y0) / 8)
                val stepX = maxOf(1, (x1 - x0) / 8)

                for (y in y0 until y1 step stepY) {
                    val rowOffset = y * width
                    for (x in x0 until x1 step stepX) {
                        val c = pixels[rowOffset + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                        hist[(lum shr 3).coerceIn(0, 31)]++
                        sampledCount++
                    }
                }

                // یافتن تقریب صدک ۸۵ام روشنایی در بلاک
                val targetCount = (sampledCount * 0.85f).toInt()
                var accumulated = 0
                var estimatedBg = 210f
                for (b in 0..31) {
                    accumulated += hist[b]
                    if (accumulated >= targetCount) {
                        estimatedBg = (b * 8 + 4).toFloat()
                        break
                    }
                }

                // کف روشنایی برای جلوگیری از به اشتباه افتادن در کادرهای تیره یا حواشی
                bgRaw[gy * gridX + gx] = estimatedBg.coerceIn(145f, 255f)
            }
        }

        // هموارسازی ملایم نقشه پس‌زمینه جهت حذف مرزهای ناگهانی بین بلاک‌ها (3x3 Box Blur)
        val bgSmooth = FloatArray(gridX * gridY)
        for (gy in 0 until gridY) {
            for (gx in 0 until gridX) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) {
                    val ny = gy + dy
                    if (ny in 0 until gridY) {
                        for (dx in -1..1) {
                            val nx = gx + dx
                            if (nx in 0 until gridX) {
                                sum += bgRaw[ny * gridX + nx]
                                count++
                            }
                        }
                    }
                }
                bgSmooth[gy * gridX + gx] = sum / count
            }
        }

        // ۲. پردازش هر پیکسل با درونیابی دوخطی نور پس‌زمینه و نگاشت تونال پیوسته
        for (y in 0 until height) {
            val fy = (y.toFloat() / blockSize) - 0.5f
            val gy0 = fy.toInt().coerceIn(0, gridY - 1)
            val gy1 = (gy0 + 1).coerceIn(0, gridY - 1)
            val wy = (fy - gy0).coerceIn(0f, 1f)
            val rowOffset = y * width

            for (x in 0 until width) {
                val fx = (x.toFloat() / blockSize) - 0.5f
                val gx0 = fx.toInt().coerceIn(0, gridX - 1)
                val gx1 = (gx0 + 1).coerceIn(0, gridX - 1)
                val wx = (fx - gx0).coerceIn(0f, 1f)

                // درونیابی سطح روشنایی پس‌زمینه در مختصات جاری
                val top = bgSmooth[gy0 * gridX + gx0] * (1f - wx) + bgSmooth[gy0 * gridX + gx1] * wx
                val bottom = bgSmooth[gy1 * gridX + gx0] * (1f - wx) + bgSmooth[gy1 * gridX + gx1] * wx
                val bgLum = top * (1f - wy) + bottom * wy

                val idx = rowOffset + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lumCenter = 0.299f * r + 0.587f * g + 0.114f * b

                // اعمال ماسک افزایش وضوح متن (Sharpening) برای شفاف‌سازی لبه‌های حروف و خطوط ریز
                val sharpLum = if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                    val cL = pixels[idx - 1]
                    val cR = pixels[idx + 1]
                    val cT = pixels[idx - width]
                    val cB = pixels[idx + width]

                    val nLum = (
                        (0.299f * ((cL shr 16) and 0xFF) + 0.587f * ((cL shr 8) and 0xFF) + 0.114f * (cL and 0xFF)) +
                        (0.299f * ((cR shr 16) and 0xFF) + 0.587f * ((cR shr 8) and 0xFF) + 0.114f * (cR and 0xFF)) +
                        (0.299f * ((cT shr 16) and 0xFF) + 0.587f * ((cT shr 8) and 0xFF) + 0.114f * (cT and 0xFF)) +
                        (0.299f * ((cB shr 16) and 0xFF) + 0.587f * ((cB shr 8) and 0xFF) + 0.114f * (cB and 0xFF))
                    ) * 0.25f

                    (lumCenter + 0.35f * (lumCenter - nLum)).coerceIn(0f, 255f)
                } else {
                    lumCenter
                }

                // نسبت روشنایی پیکسل به روشنایی محلی کاغذ
                val ratio = (sharpLum / bgLum).coerceIn(0f, 1.2f)

                if (ratio >= 0.88f) {
                    // پس‌زمینه کاغذ: تبدیل به سفید تمیز، یکدست و براق بدون لکه‌های خاکستری
                    pixels[idx] = Color.WHITE
                } else if (ratio <= 0.42f) {
                    // مغز حروف و خطوط: مشکی عمیق، توپر و بدون بریدگی یا کم‌رنگ شدن
                    val darkVal = (ratio / 0.42f * 18f).toInt().coerceIn(0, 22)
                    pixels[idx] = Color.rgb(darkVal, darkVal, darkVal)
                } else {
                    // ناحیه شیب و لبه حروف (Anti-aliasing): نگاشت پیوسته منحنی جهت جلوگیری از دندانه‌موشی شدن حروف
                    val t = (ratio - 0.42f) / (0.88f - 0.42f)
                    val smooth = t * t * (3f - 2f * t)
                    val gray = (18f + smooth * 237f).toInt().coerceIn(0, 255)
                    pixels[idx] = Color.rgb(gray, gray, gray)
                }
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
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
     * ساخت بیت‌مپ واقعی برای اسناد نمونه اولیه با نوشته‌های فارسی و مهر رسمی
     */
    fun createSampleDocBitmap(title: String): Bitmap {
        val width = 900
        val height = 1250
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // پس‌زمینه کاغذ با ته رنگ طبیعی
        canvas.drawColor(Color.rgb(250, 248, 242))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // سربرگ
        paint.color = Color.rgb(40, 50, 70)
        paint.textSize = 34f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("جمهوری اسلامی ایران", width / 2f, 100f, paint)

        paint.textSize = 42f
        paint.isFakeBoldText = true
        canvas.drawText(title, width / 2f, 170f, paint)

        // خط جداکننده
        paint.strokeWidth = 3f
        paint.color = Color.rgb(180, 190, 205)
        canvas.drawLine(80f, 210f, width - 80f, 210f, paint)

        // خطوط متن سند
        paint.isFakeBoldText = false
        paint.strokeWidth = 0f
        paint.color = Color.rgb(55, 65, 81)
        paint.textSize = 28f
        paint.textAlign = Paint.Align.RIGHT

        val sampleLines = listOf(
            "شماره پرونده: ۱۴۰۳/۷۸۹۲/الف - کد ملی: ۰۰۸۳۹۲۸۱۷۲",
            "بدین‌وسیله گواهی می‌شود مدارک هویتی پیوست احراز اصالت گردید.",
            "محل صدور: تهران، اداره ثبت اسناد و املاک مرکزی",
            "کلیه مفاد و مندرجات این سند رسمی مورد تأیید است.",
            "تاریخ ثبت درخواست: ۱۴۰۳/۰۲/۲۲ - شماره رهگیری: ۹۲۸۳۷۴۶۱",
            "اعتبار این سند تا پایان سال جاری معتبر و دارای ارزش قانونی می‌باشد.",
            "جهت استعلام اصالت به سامانه الکترونیک اسناد مراجعه نمایید."
        )

        var yPos = 290f
        for (line in sampleLines) {
            canvas.drawText(line, width - 80f, yPos, paint)
            yPos += 75f
        }

        // مهر قرمز رسمی
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 38, 38)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(220f, 960f, 90f, stampPaint)

        stampPaint.style = Paint.Style.FILL
        stampPaint.textSize = 26f
        stampPaint.textAlign = Paint.Align.CENTER
        stampPaint.isFakeBoldText = true
        canvas.drawText("مهر تأیید رسمی", 220f, 950f, stampPaint)
        canvas.drawText("ثبت اسناد", 220f, 990f, stampPaint)

        // کادر امضا
        val signPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(width - 320f, 890f, width - 80f, 1030f, 16f, 16f, signPaint)
        signPaint.style = Paint.Style.FILL
        signPaint.textSize = 24f
        signPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("امضا و اثر انگشت", width - 200f, 965f, signPaint)

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
