package ir.smartscanner.docscan.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.ScanFilter
import ir.smartscanner.docscan.ui.components.PerspectiveCropView
import ir.smartscanner.docscan.ui.theme.*
import ir.smartscanner.docscan.util.DocFilterEngine
import ir.smartscanner.docscan.util.DocStorageManager
import ir.smartscanner.docscan.util.PdfExportEngine
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    document: DocumentItem?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // فیلتر انتخابی
    var selectedFilter by remember { mutableStateOf(document?.filter ?: ScanFilter.PHOTOCOPY) }

    // عنوان مدرک با امکان ویرایش
    var docTitle by remember {
        mutableStateOf(
            document?.title?.ifEmpty { "سند اسکن‌شده - ${DocStorageManager.getPersianDateNow()}" }
                ?: "سند اسکن‌شده - ${DocStorageManager.getPersianDateNow()}"
        )
    }
    var showRenameDialog by remember { mutableStateOf(false) }

    // مدیریت صفحات سند در صورت وجود چند صفحه
    val pages = document?.pages ?: emptyList()
    var currentPageIndex by remember(document?.id) { mutableStateOf(0) }

    // بیت‌مپ خام منبع: بر اساس صفحه انتخابی یا تصویر اصلی سند
    val initialBitmap = remember(document?.id, currentPageIndex) {
        if (pages.isNotEmpty() && currentPageIndex in pages.indices) {
            val page = pages[currentPageIndex]
            page.bitmap ?: (page.filePath?.let { DocStorageManager.loadSampledBitmap(it, 1600, 2200) })
        } else {
            document?.bitmap ?: (document?.filePath?.let { DocStorageManager.loadSampledBitmap(it, 1600, 2200) })
        } ?: DocFilterEngine.createSampleDocBitmap(docTitle)
    }

    // بیت‌مپ فعال جاری (قبل از فیلتر، پس از اعمال برش‌های پرسپکتیو)
    var currentRawBitmap by remember(initialBitmap) { mutableStateOf(initialBitmap) }

    // بیت‌مپ نهایی فیلتر شده (فتوکپی، سیاه و سفید، رنگی شفاف یا اصلی)
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // وضعیت فعال بودن حالت برش ۴ گوشه و پرسپکتیو
    var isCropModeOpen by remember { mutableStateOf(false) }

    // وضعیت دیالوگ ساخت و خروجی فایل PDF چندصفحه‌ای
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var pdfExportProgress by remember { mutableStateOf("") }

    // اعمال فیلتر هوشمند هر زمان تصویر پایه یا فیلتر تغییر کند
    LaunchedEffect(currentRawBitmap, selectedFilter) {
        isProcessing = true
        val filtered = DocFilterEngine.applyFilter(currentRawBitmap, selectedFilter)
        processedBitmap = filtered
        isProcessing = false
    }

    // در صورت باز بودن حالت برش، کامپوننت ۴ گوشه نمایش داده می‌شود
    if (isCropModeOpen) {
        PerspectiveCropView(
            initialBitmap = currentRawBitmap,
            onConfirmCrop = { cropped ->
                currentRawBitmap = cropped
                isCropModeOpen = false
            },
            onCancel = {
                isCropModeOpen = false
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { showRenameDialog = true }
                    ) {
                        Text(
                            text = docTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش نام",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // دکمه اختصاصی خروجی PDF چندصفحه‌ای
                    FilledTonalButton(
                        onClick = { showPdfExportDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFC62828)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "خروجی PDF",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // دکمه اشتراک‌گذاری تصویر تک‌برگه
                    IconButton(
                        onClick = {
                            val bmp = processedBitmap ?: currentRawBitmap
                            coroutineScope.launch {
                                DocFilterEngine.shareBitmap(
                                    context = context,
                                    bitmap = bmp,
                                    title = docTitle
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "اشتراک‌گذاری تصویر",
                            tint = PrimaryBlue
                        )
                    }

                    // دکمه ذخیره در حافظه محلی دائمی
                    Button(
                        onClick = {
                            val bmp = processedBitmap ?: currentRawBitmap
                            coroutineScope.launch {
                                DocStorageManager.saveDocument(
                                    context = context,
                                    title = docTitle,
                                    filter = selectedFilter,
                                    bitmap = bmp
                                )
                                Toast.makeText(
                                    context,
                                    "مدرک «$docTitle» با موفقیت ذخیره شد",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSaveSuccess()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.padding(start = 4.dp, end = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ذخیره",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceLight
                )
            )
        },
        bottomBar = {
            // نوار ابزار پایین با دکمه برش و ۴ حالت فیلتر
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceLight,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ردیف دکمه برش و تنظیم کادر
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حالت فیلتر و پردازش:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        // دکمه باز کردن ابزار برش ۴ گوشه
                        FilledTonalButton(
                            onClick = { isCropModeOpen = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = PrimaryBlueContainer,
                                contentColor = OnPrimaryBlueContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "برش و تنظیم کادر",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ردیف دکمه‌های ۴ فیلتر
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterButton(
                            title = ScanFilter.PHOTOCOPY.titleFa,
                            icon = Icons.Default.Print,
                            isSelected = selectedFilter == ScanFilter.PHOTOCOPY,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedFilter = ScanFilter.PHOTOCOPY }
                        )

                        FilterButton(
                            title = ScanFilter.BLACK_AND_WHITE.titleFa,
                            icon = Icons.Default.Contrast,
                            isSelected = selectedFilter == ScanFilter.BLACK_AND_WHITE,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedFilter = ScanFilter.BLACK_AND_WHITE }
                        )

                        FilterButton(
                            title = ScanFilter.CLEAR_COLOR.titleFa,
                            icon = Icons.Default.AutoFixHigh,
                            isSelected = selectedFilter == ScanFilter.CLEAR_COLOR,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedFilter = ScanFilter.CLEAR_COLOR }
                        )

                        FilterButton(
                            title = ScanFilter.ORIGINAL.titleFa,
                            icon = Icons.Default.Image,
                            isSelected = selectedFilter == ScanFilter.ORIGINAL,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedFilter = ScanFilter.ORIGINAL }
                        )
                    }

                    // توضیحات فیلتر و وضعیت در حال پردازش
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedFilter.descriptionFa,
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Normal
                        )

                        if (isProcessing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "در حال پردازش...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        // کادر پیش‌نمایش تصویر در وسط صفحه بر روی سطح ملایم خاکستری
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE2E8F0)) // سطح ملایم میز کار
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // برگه سفید سند اسکن‌شده تمیز (Clean White A4 Document Sheet)
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.98f)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(4.dp),
                        spotColor = Color.Black.copy(alpha = 0.25f)
                    )
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                color = Color.White
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val displayBitmap = processedBitmap ?: currentRawBitmap
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = docTitle,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Fit
                    )

                    // نشانگر فیلتر در گوشه بالا
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "فیلتر: ${selectedFilter.titleFa}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // نشانگر چندصفحه‌ای در گوشه بالا سمت چپ (در صورت وجود بیش از ۱ صفحه)
                    if (pages.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "صفحه ${currentPageIndex + 1} از ${pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // دکمه‌های جابجایی بین صفحات در پایین تصویر
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPageIndex > 0) currentPageIndex--
                                },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("‹", color = if (currentPageIndex > 0) Color.White else Color.Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "برگه ${currentPageIndex + 1} / ${pages.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    if (currentPageIndex < pages.size - 1) currentPageIndex++
                                },
                                enabled = currentPageIndex < pages.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("›", color = if (currentPageIndex < pages.size - 1) Color.White else Color.Gray, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // نشانگر در حال پردازش در صورت لودینگ
                    if (isProcessing) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Text(
                                    text = "در حال پردازش و پاکسازی صفحه...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // دیالوگ تغییر نام مدرک
    if (showRenameDialog) {
        var tempName by remember { mutableStateOf(docTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    text = "تغییر نام مدرک",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("نام مدرک") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            docTitle = tempName.trim()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("تأیید", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // دیالوگ هوشمند خروجی PDF چندصفحه‌ای
    if (showPdfExportDialog && document != null) {
        MultiPagePdfExportDialog(
            document = document,
            docTitle = docTitle,
            isExporting = isExportingPdf,
            progressText = pdfExportProgress,
            onDismiss = { if (!isExportingPdf) showPdfExportDialog = false },
            onConfirmExport = { selectedIndices ->
                isExportingPdf = true
                pdfExportProgress = "آماده‌سازی صفحات..."
                coroutineScope.launch {
                    val pdfFile = PdfExportEngine.generateMultiPagePdf(
                        context = context,
                        doc = document.copy(title = docTitle),
                        selectedPageIndices = selectedIndices,
                        onProgress = { cur, tot ->
                            pdfExportProgress = "در حال پردازش برگه $cur از $tot در قالب A4..."
                        }
                    )
                    isExportingPdf = false
                    showPdfExportDialog = false
                    if (pdfFile != null) {
                        Toast.makeText(context, "فایل PDF چندصفحه‌ای با موفقیت ایجاد شد", Toast.LENGTH_SHORT).show()
                        PdfExportEngine.sharePdfFile(context, pdfFile, docTitle)
                    } else {
                        Toast.makeText(context, "خطا در تولید فایل PDF", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun MultiPagePdfExportDialog(
    document: DocumentItem,
    docTitle: String,
    isExporting: Boolean,
    progressText: String,
    onDismiss: () -> Unit,
    onConfirmExport: (selectedIndices: List<Int>) -> Unit
) {
    val totalPages = document.pages.size.coerceAtLeast(1)
    val selectedIndices = remember {
        mutableStateListOf<Int>().apply {
            addAll(0 until totalPages)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "خروجی PDF چندصفحه‌ای",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "استاندارد کاغذ اداری A4",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "سند: $docTitle",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isExporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFC62828),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = progressText.ifEmpty { "در حال تجمیع برگه‌ها در فایل PDF..." },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "صفحات جهت افزودن به PDF (${selectedIndices.size} از $totalPages):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                if (selectedIndices.size == totalPages) {
                                    selectedIndices.clear()
                                } else {
                                    selectedIndices.clear()
                                    selectedIndices.addAll(0 until totalPages)
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIndices.size == totalPages) "لغو همه" else "انتخاب همه",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // لیست صفحات با امکان انتخاب
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 0 until totalPages) {
                            val isChecked = selectedIndices.contains(i)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) Color(0xFFF1F8E9) else Color(0xFFF5F5F5),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isChecked) Color(0xFF81C784) else Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            if (selectedIndices.size > 1) selectedIndices.remove(i)
                                        } else {
                                            selectedIndices.add(i)
                                            selectedIndices.sort()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "برگه شماره ${i + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (!selectedIndices.contains(i)) {
                                                    selectedIndices.add(i)
                                                    selectedIndices.sort()
                                                }
                                            } else {
                                                if (selectedIndices.size > 1) selectedIndices.remove(i)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "• هر صفحه با رزولوشن اصلی در قالب پرینت استاندارد A4 با مارجین متناسب جای‌گذاری خواهد شد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIndices.isNotEmpty() && !isExporting) {
                        onConfirmExport(selectedIndices.toList())
                    }
                },
                enabled = selectedIndices.isNotEmpty() && !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تولید و اشتراک‌گذاری PDF", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss) {
                    Text("انصراف")
                }
            }
        }
    )
}

@Composable
fun FilterButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlueContainer else SurfaceVariantLight,
        label = "filterBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else Color.Transparent,
        label = "filterBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlueDark else TextSecondary,
        label = "filterContent"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            fontSize = 11.sp
        )
    }
}
