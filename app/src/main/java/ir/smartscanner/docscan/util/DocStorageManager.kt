package ir.smartscanner.docscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.ScanFilter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/**
 * سیستم ذخیره‌سازی محلی کاملاً سبک و سریع با SharedPreferences و JSON نیتیو اندروید
 * ذخیره ایمن اسناد در context.filesDir/documents/ بدون نیاز به دیتابیس‌های سنگین
 */
object DocStorageManager {

    private const val PREFS_NAME = "smart_scanner_docs_prefs"
    private const val KEY_DOCUMENTS_JSON = "documents_list_json"
    private const val DOCS_DIR_NAME = "documents"

    /**
     * دریافت لیست مدارک ذخیره‌شده محلی همراه با کلیه صفحات متعلق به هر شناسه مدرک
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
                val filePath = obj.optString("filePath")

                val filter = try {
                    ScanFilter.valueOf(filterName)
                } catch (e: Exception) {
                    ScanFilter.PHOTOCOPY
                }

                // خواندن لیست صفحات ذخیره‌شده زیر این شناسه مدرک
                val pages = mutableListOf<ir.smartscanner.docscan.model.DocumentPage>()
                val pagesJsonArray = obj.optJSONArray("pages")
                if (pagesJsonArray != null && pagesJsonArray.length() > 0) {
                    for (p in 0 until pagesJsonArray.length()) {
                        val pageObj = pagesJsonArray.getJSONObject(p)
                        val pageId = pageObj.optString("id", "page_${p + 1}")
                        val pageNum = pageObj.optInt("pageNumber", p + 1)
                        val pagePath = pageObj.optString("filePath")
                        val pageTime = pageObj.optLong("timestamp", System.currentTimeMillis())

                        var pageBitmap: Bitmap? = null
                        if (pagePath.isNotEmpty() && File(pagePath).exists()) {
                            pageBitmap = loadSampledBitmap(pagePath, 400, 550)
                        }

                        pages.add(
                            ir.smartscanner.docscan.model.DocumentPage(
                                id = pageId,
                                pageNumber = pageNum,
                                filePath = pagePath,
                                bitmap = pageBitmap,
                                timestamp = pageTime
                            )
                        )
                    }
                } else if (filePath.isNotEmpty() && File(filePath).exists()) {
                    // سازگاری با اسناد تک‌صفحه‌ای قدیمی
                    val singleBitmap = loadSampledBitmap(filePath, 400, 550)
                    pages.add(
                        ir.smartscanner.docscan.model.DocumentPage(
                            id = "${id}_p1",
                            pageNumber = 1,
                            filePath = filePath,
                            bitmap = singleBitmap
                        )
                    )
                }

                // تصویر شاخص سند
                val primaryBitmap = pages.firstOrNull()?.bitmap
                    ?: if (filePath.isNotEmpty() && File(filePath).exists()) loadSampledBitmap(filePath, 400, 550) else null

                list.add(
                    DocumentItem(
                        id = id,
                        title = title,
                        datePersian = datePersian,
                        filter = filter,
                        pageCount = pages.size.coerceAtLeast(1),
                        pages = pages,
                        filePath = pages.firstOrNull()?.filePath ?: filePath,
                        bitmap = primaryBitmap
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
        val safeFileName = "SCAN_${docId}_p1_${System.currentTimeMillis()}.jpg"
        val targetFile = File(docsDir, safeFileName)

        try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val firstPage = ir.smartscanner.docscan.model.DocumentPage(
            id = "${docId}_page_1",
            pageNumber = 1,
            filePath = targetFile.absolutePath,
            bitmap = bitmap
        )

        val newItem = DocumentItem(
            id = docId,
            title = finalTitle,
            datePersian = datePersian,
            filter = filter,
            pageCount = 1,
            pages = listOf(firstPage),
            filePath = targetFile.absolutePath,
            bitmap = bitmap
        )

        // ذخیره در SharedPreferences با آرایه صفحات
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_DOCUMENTS_JSON, "[]")
        try {
            val oldArray = JSONArray(currentJson)
            val newArray = JSONArray()

            val pagesArray = JSONArray().apply {
                val pageObj = JSONObject().apply {
                    put("id", firstPage.id)
                    put("pageNumber", firstPage.pageNumber)
                    put("filePath", firstPage.filePath)
                    put("timestamp", firstPage.timestamp)
                }
                put(pageObj)
            }

            val newObj = JSONObject().apply {
                put("id", newItem.id)
                put("title", newItem.title)
                put("datePersian", newItem.datePersian)
                put("filter", newItem.filter.name)
                put("pageCount", 1)
                put("filePath", newItem.filePath)
                put("pages", pagesArray)
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
     * افزودن یک صفحه جدید به یک شناسه سند موجود در همان جلسه اسکن
     */
    fun addPageToDocument(
        context: Context,
        docId: String,
        bitmap: Bitmap
    ): DocumentItem? {
        val docsDir = File(context.filesDir, DOCS_DIR_NAME).apply { if (!exists()) mkdirs() }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_DOCUMENTS_JSON, "[]")

        try {
            val oldArray = JSONArray(currentJson)
            val newArray = JSONArray()
            var updatedItem: DocumentItem? = null

            for (i in 0 until oldArray.length()) {
                val obj = oldArray.getJSONObject(i)
                if (obj.optString("id") == docId) {
                    var pagesArray = obj.optJSONArray("pages")
                    if (pagesArray == null) {
                        pagesArray = JSONArray()
                        val oldFilePath = obj.optString("filePath")
                        if (oldFilePath.isNotEmpty()) {
                            pagesArray.put(JSONObject().apply {
                                put("id", "${docId}_p1")
                                put("pageNumber", 1)
                                put("filePath", oldFilePath)
                                put("timestamp", System.currentTimeMillis())
                            })
                        }
                    }

                    val nextNum = pagesArray.length() + 1
                    val newPageFileName = "SCAN_${docId}_p${nextNum}_${System.currentTimeMillis()}.jpg"
                    val pageFile = File(docsDir, newPageFileName)

                    FileOutputStream(pageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        out.flush()
                    }

                    val newPageObj = JSONObject().apply {
                        put("id", "${docId}_p${nextNum}_${System.currentTimeMillis()}")
                        put("pageNumber", nextNum)
                        put("filePath", pageFile.absolutePath)
                        put("timestamp", System.currentTimeMillis())
                    }
                    pagesArray.put(newPageObj)

                    obj.put("pages", pagesArray)
                    obj.put("pageCount", pagesArray.length())

                    // ساخت مدل به‌روزشده
                    val pagesList = mutableListOf<ir.smartscanner.docscan.model.DocumentPage>()
                    for (p in 0 until pagesArray.length()) {
                        val pObj = pagesArray.getJSONObject(p)
                        val pPath = pObj.optString("filePath")
                        val pBmp = if (File(pPath).exists()) loadSampledBitmap(pPath, 400, 550) else null
                        pagesList.add(
                            ir.smartscanner.docscan.model.DocumentPage(
                                id = pObj.optString("id"),
                                pageNumber = pObj.optInt("pageNumber", p + 1),
                                filePath = pPath,
                                bitmap = pBmp,
                                timestamp = pObj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }

                    val filterName = obj.optString("filter", ScanFilter.PHOTOCOPY.name)
                    val filter = try { ScanFilter.valueOf(filterName) } catch (e: Exception) { ScanFilter.PHOTOCOPY }

                    updatedItem = DocumentItem(
                        id = docId,
                        title = obj.optString("title"),
                        datePersian = obj.optString("datePersian"),
                        filter = filter,
                        pageCount = pagesList.size,
                        pages = pagesList,
                        filePath = pagesList.firstOrNull()?.filePath ?: obj.optString("filePath"),
                        bitmap = pagesList.firstOrNull()?.bitmap
                    )

                    newArray.put(obj)
                } else {
                    newArray.put(obj)
                }
            }

            prefs.edit().putString(KEY_DOCUMENTS_JSON, newArray.toString()).apply()
            return updatedItem
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * حذف یک صفحه خاص از یک سند چندصفحه‌ای زیر شناسه سند مشخص
     */
    fun deletePageFromDocument(
        context: Context,
        docId: String,
        pageId: String
    ): DocumentItem? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_DOCUMENTS_JSON, "[]")

        try {
            val oldArray = JSONArray(currentJson)
            val newArray = JSONArray()
            var updatedItem: DocumentItem? = null

            for (i in 0 until oldArray.length()) {
                val obj = oldArray.getJSONObject(i)
                if (obj.optString("id") == docId) {
                    val pagesArray = obj.optJSONArray("pages") ?: JSONArray()
                    val newPagesArray = JSONArray()
                    var renumbered = 1

                    for (p in 0 until pagesArray.length()) {
                        val pObj = pagesArray.getJSONObject(p)
                        if (pObj.optString("id") == pageId) {
                            val pPath = pObj.optString("filePath")
                            if (pPath.isNotEmpty()) {
                                File(pPath).delete()
                            }
                        } else {
                            pObj.put("pageNumber", renumbered++)
                            newPagesArray.put(pObj)
                        }
                    }

                    obj.put("pages", newPagesArray)
                    obj.put("pageCount", newPagesArray.length())
                    if (newPagesArray.length() > 0) {
                        obj.put("filePath", newPagesArray.getJSONObject(0).optString("filePath"))
                    }

                    newArray.put(obj)
                } else {
                    newArray.put(obj)
                }
            }

            prefs.edit().putString(KEY_DOCUMENTS_JSON, newArray.toString()).apply()
            return getAllDocuments(context).find { it.id == docId }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * حذف کامل یک مدرک و تمام صفحات آن از دیسک و SharedPreferences
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
                        if (file.exists()) file.delete()
                    }

                    // حذف تمام صفحات ذخیره‌شده
                    val pagesArray = obj.optJSONArray("pages")
                    if (pagesArray != null) {
                        for (p in 0 until pagesArray.length()) {
                            val pPath = pagesArray.getJSONObject(p).optString("filePath")
                            if (pPath.isNotEmpty()) {
                                val pFile = File(pPath)
                                if (pFile.exists()) pFile.delete()
                            }
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
}
