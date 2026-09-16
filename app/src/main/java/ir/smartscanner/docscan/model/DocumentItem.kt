package ir.smartscanner.docscan.model

enum class ScanFilter(val titleFa: String, val descriptionFa: String) {
    PHOTOCOPY("فتوکپی", "کنتراست بالا مناسب متن و فرم‌ها"),
    BLACK_AND_WHITE("سیاه و سفید", "طیف خاکستری دقیق اسناد"),
    CLEAR_COLOR("رنگی شفاف", "رنگ‌های تقویت‌شده و حذف سایه"),
    ORIGINAL("اصلی", "تصویر خام ثبت‌شده")
}

data class DocumentItem(
    val id: String,
    val title: String,
    val datePersian: String,
    var filter: ScanFilter = ScanFilter.PHOTOCOPY,
    val pageCount: Int = 1,
    val imageResId: Int? = null,
    val imageUri: String? = null,
    val filePath: String? = null,
    val bitmap: android.graphics.Bitmap? = null
)
