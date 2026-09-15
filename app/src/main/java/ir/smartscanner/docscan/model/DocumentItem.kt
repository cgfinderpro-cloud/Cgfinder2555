package ir.smartscanner.docscan.model

import android.graphics.Bitmap

enum class ScanFilter(val titleFa: String, val descriptionFa: String) {
    PHOTOCOPY("فتوکپی", "کنتراست بالا مناسب متن و فرم‌ها"),
    BLACK_AND_WHITE("سیاه و سفید", "طیف خاکستری دقیق اسناد"),
    CLEAR_COLOR("رنگی شفاف", "رنگ‌های تقویت‌شده و حذف سایه"),
    ORIGINAL("اصلی", "تصویر خام ثبت‌شده")
}

/**
 * مدل داده‌ای اختصاصی برای هر صفحه از یک مدرک چندصفحه‌ای زیر یک شناسه سند یکتا
 */
data class DocumentPage(
    val id: String,
    val pageNumber: Int,
    val filePath: String? = null,
    val imageUri: String? = null,
    val bitmap: Bitmap? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * مدل اصلی سند شامل شناسه یکتا (Document ID) و مدیریت صفحات متعدد زیر همین شناسه
 */
data class DocumentItem(
    val id: String,
    val title: String,
    val datePersian: String,
    var filter: ScanFilter = ScanFilter.PHOTOCOPY,
    val pageCount: Int = 1,
    val pages: List<DocumentPage> = emptyList(),
    val imageResId: Int? = null,
    val imageUri: String? = null,
    val filePath: String? = null,
    val bitmap: Bitmap? = null
) {
    /**
     * تصویر شاخص مدرک (اولین صفحه موجود یا تصویر اصلی مدرک)
     */
    val primaryBitmap: Bitmap?
        get() = pages.firstOrNull()?.bitmap ?: bitmap

    /**
     * مسیر فایل تصویر شاخص مدرک
     */
    val primaryFilePath: String?
        get() = pages.firstOrNull()?.filePath ?: filePath
}
