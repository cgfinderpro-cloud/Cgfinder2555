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
     * فیلتر فتوکپی: افزایش شدید کنتراست و حذف سایه‌های خاکستری پس‌زمینه
     * پس‌زمینه کاغذ کاملاً سفید و خطوط و متن‌ها مشکی پررنگ می‌شوند (High-Contrast Binarization)
     */
    fun applyPhotocopy(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // ۱. ابتدا تبدیل به سیاه و سفید با کنتراست بهینه
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ماتریس تبدیل به سطح خاکستری با حذف سایه‌های تیره و روشن‌سازی زمینه
        val matrix = ColorMatrix().apply {
            // ماتریس خاکستری استاندارد Luminance
            setSaturation(0f)
        }

        // اعمال کنتراست شدید (High Contrast)
        val contrast = 2.4f
        val brightness = -40f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        // ۲. الگوریتم پاکسازی نهایی پیکسل‌ها برای فتوکپی تمیز
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 160 // آستانه تفکیک متن از سفیدی کاغذ
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            if (luminance >= threshold) {
                // تبدیل پس‌زمینه کاغذ به سفید مطلق
                pixels[i] = Color.WHITE
            } else {
                // تقویت خطوط متن به سیاه عمیق با حفظ نرمی لبه‌ها
                val factor = luminance.toFloat() / threshold.toFloat()
                val newGray = (factor * 35).toInt().coerceIn(0, 50)
                pixels[i] = Color.rgb(newGray, newGray, newGray)
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
