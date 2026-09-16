package ir.smartscanner.docscan.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.ScanFilter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * سیستم ذخیره‌سازی محلی کاملاً سبک و سریع با SharedPreferences و JSON نیتیو اندروید
 * ذخیره ایمن اسناد در context.filesDir/documents/ بدون نیاز به دیتابیس‌های سنگین
 */
object DocStorageManager {

    private const val PREFS_NAME = "smart_scanner_docs_prefs"
    private const val KEY_DOCUMENTS_JSON = "documents_list_json"
    private const val DOCS_DIR_NAME = "documents"

    /**
     * دریافت لیست مدارک ذخیره‌شده محلی
     */
    fun getAllDocuments(context: Context): List<DocumentItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_DOCUMENTS_JSON, null) ?: return emptyList()

        val list = mutableListOf<DocumentItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val datePersian = obj.optString("datePersian")
                val filterName = obj.optString("filter", ScanFilter.PHOTOCOPY.name)
                val pageCount = obj.optInt("pageCount", 1)
                val filePath = obj.optString("filePath")

                val filter = try {
                    ScanFilter.valueOf(filterName)
                } catch (e: Exception) {
                    ScanFilter.PHOTOCOPY
                }

                // بارگذاری تصویر از مسیر فایل (در صورت وجود)
                var bitmap: Bitmap? = null
                if (filePath.isNotEmpty()) {
                    val file = File(filePath)
                    if (file.exists()) {
                        bitmap = loadSampledBitmap(file.absolutePath, 400, 550)
                    }
                }

                list.add(
                    DocumentItem(
                        id = id,
                        title = title,
                        datePersian = datePersian,
                        filter = filter,
                        pageCount = pageCount,
                        filePath = filePath,
                        bitmap = bitmap
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * ذخیره مدرک جدید در فایل‌های داخلی و متادیتا در SharedPreferences
     */
    fun saveDocument(
        context: Context,
        title: String,
        filter: ScanFilter,
        bitmap: Bitmap
    ): DocumentItem {
        val docsDir = File(context.filesDir, DOCS_DIR_NAME).apply { if (!exists()) mkdirs() }
        val docId = "doc_${System.currentTimeMillis()}"
        val datePersian = getPersianDateNow()

        val finalTitle = title.trim().ifEmpty { "سند اسکن‌شده - $datePersian" }
        val safeFileName = "SCAN_${System.currentTimeMillis()}.jpg"
        val targetFile = File(docsDir, safeFileName)

        try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val newItem = DocumentItem(
            id = docId,
            title = finalTitle,
            datePersian = datePersian,
            filter = filter,
            pageCount = 1,
            filePath = targetFile.absolutePath,
            bitmap = bitmap
        )

        // افزودن به ابتدای لیست در SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_DOCUMENTS_JSON, "[]")
        try {
            val oldArray = JSONArray(currentJson)
            val newArray = JSONArray()

            val newObj = JSONObject().apply {
                put("id", newItem.id)
                put("title", newItem.title)
                put("datePersian", newItem.datePersian)
                put("filter", newItem.filter.name)
                put("pageCount", newItem.pageCount)
                put("filePath", newItem.filePath)
                put("timestamp", System.currentTimeMillis())
            }
            newArray.put(newObj)

            for (i in 0 until oldArray.length()) {
                val item = oldArray.getJSONObject(i)
                if (item.optString("id") != docId) {
                    newArray.put(item)
                }
            }

            prefs.edit().putString(KEY_DOCUMENTS_JSON, newArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newItem
    }

    /**
     * حذف کامل یک مدرک از دیسک و SharedPreferences
     */
    fun deleteDocument(context: Context, docId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_DOCUMENTS_JSON, null) ?: return false

        var deleted = false
        try {
            val oldArray = JSONArray(jsonString)
            val newArray = JSONArray()

            for (i in 0 until oldArray.length()) {
                val obj = oldArray.getJSONObject(i)
                if (obj.optString("id") == docId) {
                    val filePath = obj.optString("filePath")
                    if (filePath.isNotEmpty()) {
                        val file = File(filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    deleted = true
                } else {
                    newArray.put(obj)
                }
            }

            prefs.edit().putString(KEY_DOCUMENTS_JSON, newArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return deleted
    }

    /**
     * بارگذاری بهینه Bitmap از فایل با ابعاد نمونه‌برداری شده جهت جلوگیری از OutOfMemory
     */
    fun loadSampledBitmap(path: String, reqWidth: Int = 800, reqHeight: Int = 1100): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * محاسبه تاریخ دقیق شمسی (خورشیدی) جاری به صورت رشته‌ای فارسی: مثلاً ۲۲ شهریور ۱۴۰۵
     */
    fun getPersianDateNow(): String {
        val cal = Calendar.getInstance()
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        val jalali = gregorianToJalali(gYear, gMonth, gDay)
        val monthNames = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        val monthIndex = (jalali.month - 1).coerceIn(0, 11)
        val monthName = monthNames[monthIndex]

        return "${jalali.day} $monthName ${jalali.year}"
    }

    private data class JalaliDate(val year: Int, val month: Int, val day: Int)

    /**
     * الگوریتم خالص و دقیق کاتلین برای تبدیل تاریخ میلادی به شمسی
     */
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy2 = gy
        if (gy2 > 1600) {
            // standard modern era
        }

        val isLeapG = (gy2 % 4 == 0 && gy2 % 100 != 0) || (gy2 % 400 == 0)
        if (isLeapG) gDaysInMonth[2] = 29

        var gDayOfYear = 0
        for (i in 1 until gm) {
            gDayOfYear += gDaysInMonth[i]
        }
        gDayOfYear += gd

        val march21GDay = if (isLeapG) 80 else 79
        val jy: Int
        var jDayOfYear: Int

        if (gDayOfYear > march21GDay) {
            jDayOfYear = gDayOfYear - march21GDay
            jy = gy2 - 621
        } else {
            val prevYear = gy2 - 1
            val prevIsLeap = (prevYear % 4 == 0 && prevYear % 100 != 0) || (prevYear % 400 == 0)
            val prevYearDays = if (prevIsLeap) 366 else 365
            jDayOfYear = gDayOfYear + (prevYearDays - (if (prevIsLeap) 80 else 79))
            jy = gy2 - 622
        }

        var jm = 1
        while (jm <= 12 && jDayOfYear > jDaysInMonth[jm]) {
            jDayOfYear -= jDaysInMonth[jm]
            jm++
        }
        val jd = jDayOfYear

        return JalaliDate(jy, jm, jd)
    }

    /**
     * بارگذاری ایمن Bitmap از Uri انتخاب شده از گالری
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * تبدیل چند تصویر/برگه اسکن‌شده به یک فایل PDF استاندارد واحد A4
     * کاملاً آفلاین و نیتیو با android.graphics.pdf.PdfDocument بدون نیاز به کتابخانه‌های خارجی
     */
    fun createMultiPagePdf(
        context: Context,
        pages: List<Bitmap>,
        title: String
    ): File {
        val pdfDocument = PdfDocument()
        // ابعاد استاندارد برگه A4 در مقیاس 72DPI: ۵۹۵ در ۸۴۲ پوینت
        val a4Width = 595
        val a4Height = 842

        for (i in pages.indices) {
            val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, i + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // زمینه سفید کاغذ A4
            canvas.drawColor(Color.WHITE)

            val bmp = pages[i]
            val margin = 24f
            val maxW = a4Width - (margin * 2)
            val maxH = a4Height - (margin * 2)

            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val scale = minOf(maxW / bmpW, maxH / bmpH)

            val destW = bmpW * scale
            val destH = bmpH * scale
            val left = margin + (maxW - destW) / 2f
            val top = margin + (maxH - destH) / 2f

            val destRect = RectF(left, top, left + destW, top + destH)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }
            canvas.drawBitmap(bmp, null, destRect, paint)

            pdfDocument.finishPage(page)
        }

        val docsDir = File(context.filesDir, DOCS_DIR_NAME).apply { if (!exists()) mkdirs() }
        val cleanTitle = title.trim().replace("\\s+".toRegex(), "_").ifEmpty { "document" }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFile = File(docsDir, "PDF_${cleanTitle}_$timeStamp.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
            out.flush()
        }
        pdfDocument.close()
        return pdfFile
    }

    /**
     * اشتراک‌گذاری یا ذخیره فایل PDF از طریق Intent به پیام‌رسان‌ها و برنامه‌های نمایش پی‌دی‌اف
     */
    fun sharePdfFile(context: Context, pdfFile: File, title: String) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "فایل PDF مدرک اسکن‌شده: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "ارسال یا ذخیره PDF با:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
