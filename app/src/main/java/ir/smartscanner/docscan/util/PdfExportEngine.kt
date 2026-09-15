package ir.smartscanner.docscan.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.DocumentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * موتور تولید خروجی PDF چندصفحه‌ای با کیفیت بالا بر مبنای استاندارد کاغذ A4
 * کامپایل تمامی برگه‌های سند اسکن‌شده ذیل یک فایل PDF نهایی و امکان اشتراک‌گذاری یا چاپ مستقیم
 */
object PdfExportEngine {

    // ابعاد استاندارد برگه A4 در سیستم برداری PDF (بر حسب Point / 72 DPI)
    const val A4_WIDTH_POINTS = 595
    const val A4_HEIGHT_POINTS = 842
    private const val PAGE_MARGIN = 24f

    /**
     * تولید فایل PDF چندصفحه‌ای از روی صفحات انتخاب‌شده یک سند
     * @param context کانتکست اندروید
     * @param doc سند هدف شامل متادیتا و لیست صفحات
     * @param selectedPageIndices لیست شماره صفحات انتخابی (در صورت null، تمام صفحات اضافه می‌شوند)
     * @param onProgress گزارش درصد پیشرفت ساخت فایل
     */
    suspend fun generateMultiPagePdf(
        context: Context,
        doc: DocumentItem,
        selectedPageIndices: List<Int>? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val pagesToExport = if (selectedPageIndices != null) {
                doc.pages.filterIndexed { index, _ -> selectedPageIndices.contains(index) }
            } else {
                doc.pages
            }

            // اگر سندی صفحه نداشت اما تصویر اصلی دارد، یک صفحه از تصویر اصلی بساز
            val actualPages = if (pagesToExport.isEmpty() && doc.bitmap != null) {
                listOf(
                    DocumentPage(
                        id = "${doc.id}_default",
                        pageNumber = 1,
                        filePath = doc.filePath,
                        bitmap = doc.bitmap
                    )
                )
            } else {
                pagesToExport
            }

            if (actualPages.isEmpty()) return@withContext null

            val pdfDocument = PdfDocument()
            val totalPages = actualPages.size

            // قلم‌های ترسیم پس‌زمینه، متن شماره صفحه و خطوط
            val bgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            val textPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val titlePaint = Paint().apply {
                color = Color.LTGRAY
                textSize = 9f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }

            val bitmapPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            for ((index, page) in actualPages.withIndex()) {
                onProgress?.invoke(index + 1, totalPages)

                // دریافت بیت‌مپ صفحه با کیفیت مطلوب
                var pageBitmap: Bitmap? = page.bitmap
                if (pageBitmap == null && page.filePath != null && File(page.filePath).exists()) {
                    pageBitmap = DocStorageManager.loadSampledBitmap(page.filePath, 1600, 2200)
                }
                if (pageBitmap == null && index == 0) {
                    pageBitmap = doc.bitmap
                }

                if (pageBitmap == null) continue

                // مشخصات برگه A4
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_POINTS, A4_HEIGHT_POINTS, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // ۱. پس‌زمینه سفید استاندارد کاغذ
                canvas.drawRect(0f, 0f, A4_WIDTH_POINTS.toFloat(), A4_HEIGHT_POINTS.toFloat(), bgPaint)

                // ۲. محاسبه نسبت تصویر و قرارگیری در کادر A4 با حفظ نسبت ابعاد
                val usableWidth = A4_WIDTH_POINTS - (PAGE_MARGIN * 2)
                val usableHeight = A4_HEIGHT_POINTS - (PAGE_MARGIN * 2) - 30f // فضای خالی برای پاورقی

                val scaleX = usableWidth / pageBitmap.width.toFloat()
                val scaleY = usableHeight / pageBitmap.height.toFloat()
                val scale = minOf(scaleX, scaleY)

                val destWidth = pageBitmap.width * scale
                val destHeight = pageBitmap.height * scale

                val left = PAGE_MARGIN + (usableWidth - destWidth) / 2f
                val top = PAGE_MARGIN + (usableHeight - destHeight) / 2f
                val destRect = RectF(left, top, left + destWidth, top + destHeight)

                // ۳. ترسیم تصویر اسکن‌شده صفحه
                val srcRect = Rect(0, 0, pageBitmap.width, pageBitmap.height)
                canvas.drawBitmap(pageBitmap, srcRect, destRect, bitmapPaint)

                // ۴. پاورقی رسمی سند اسکن‌شده شامل شماره صفحه و عنوان
                val footerY = A4_HEIGHT_POINTS - 14f
                val pageNumberText = "صفحه ${index + 1} از $totalPages"
                canvas.drawText(pageNumberText, A4_WIDTH_POINTS / 2f, footerY, textPaint)

                // عنوان سند در گوشه سمت راست پاورقی
                val safeTitle = doc.title.take(30)
                canvas.drawText(safeTitle, A4_WIDTH_POINTS - PAGE_MARGIN, footerY, titlePaint)

                // اتمام برگه جاری
                pdfDocument.finishPage(pdfPage)
            }

            // ذخیره در پوشه کش یا دایرکتوری اسناد برنامه
            val pdfDir = File(context.cacheDir, "pdf_exports")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val cleanTitle = doc.title.replace("[^a-zA-Z0-9آ-ی_\s-]".toRegex(), "_").trim().ifEmpty { "document" }
            val pdfFile = File(pdfDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * اشتراک‌گذاری فایل PDF خروجی از طریق سیستم‌عامل اندروید (تلگرام، ایتا، واتساپ، پرینت و ...)
     */
    fun sharePdfFile(context: Context, file: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "سند اسکن‌شده: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "ارسال فایل PDF چندصفحه‌ای").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطا در اشتراک‌گذاری فایل PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
