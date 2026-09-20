package ir.smartscanner.docscan.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch

// تعریف ساختار نگه‌داری اطلاعات هر برگه (بیت‌مپ خام، فیلتر و بیت‌مپ پردازش‌شده)
data class PageData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawBitmap: Bitmap,
    val filter: ScanFilter = ScanFilter.PHOTOCOPY,
    val processedBitmap: Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    document: DocumentItem?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit = {},
    additionalPages: List<Bitmap> = emptyList(),
    onAdditionalPagesChange: (List<Bitmap>) -> Unit = {},
    onAddPageFromHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // عنوان مدرک با امکان ویرایش
    var docTitle by remember {
        mutableStateOf(
            document?.title?.ifEmpty { "سند اسکن‌شده - ${DocStorageManager.getPersianDateNow()}" }
                ?: "سند اسکن‌شده - ${DocStorageManager.getPersianDateNow()}"
        )
    }
    var showRenameDialog by remember { mutableStateOf(false) }

    // بیت‌مپ خام منبع برگه اصلی: یا از تصویر فایل/حافظه یا ساخت نمونه
    val initialBitmap = remember(document?.id) {
        document?.bitmap
            ?: (document?.filePath?.let { DocStorageManager.loadSampledBitmap(it, 1600, 2200) })
            ?: DocFilterEngine.createSampleDocBitmap(docTitle)
    }

    // فهرست تمام برگه‌های سند شامل برگه اصلی و برگه‌های افزوده‌شده
    var pages by remember(initialBitmap) {
        mutableStateOf(
            listOf(
                PageData(
                    rawBitmap = initialBitmap,
                    filter = document?.filter ?: ScanFilter.PHOTOCOPY,
                    processedBitmap = null
                )
            )
        )
    }

    // ایندکس برگه انتخاب‌شده جاری در پیش‌نمایش
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    // وضعیت‌های زوم دو انگشتی و جابه‌جایی تعاملی برای سند اسکن‌شده
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // وضعیت فعال بودن حالت برش ۴ گوشه و پرسپکتیو
    var isCropModeOpen by remember { mutableStateOf(false) }

    // هماهنگ‌سازی برگه‌های اضافی جدید وارد شده از مسیرهای بیرونی (با اطمینان از فیلترگذاری کامل)
    LaunchedEffect(additionalPages) {
        val currentAdditionalCount = (pages.size - 1).coerceAtLeast(0)
        if (additionalPages.size > currentAdditionalCount) {
            val newItems = mutableListOf<PageData>()
            for (bmp in additionalPages.drop(currentAdditionalCount)) {
                val filtered = DocFilterEngine.applyFilter(bmp, ScanFilter.PHOTOCOPY)
                newItems.add(
                    PageData(
                        rawBitmap = bmp,
                        filter = ScanFilter.PHOTOCOPY,
                        processedBitmap = filtered
                    )
                )
            }
            pages = pages + newItems
            selectedPageIndex = pages.size - 1
        }
    }

    // پردازش اولیه برگه اول در بدو ورود در صورت نیاز
    LaunchedEffect(initialBitmap) {
        if (pages.isNotEmpty() && pages[0].processedBitmap == null) {
            isProcessing = true
            val firstFilter = pages[0].filter
            val filtered = DocFilterEngine.applyFilter(pages[0].rawBitmap, firstFilter)
            val updated = pages.toMutableList()
            updated[0] = updated[0].copy(processedBitmap = filtered)
            pages = updated
            isProcessing = false
        }
    }

    // برگه فعال و جاری در پیش‌نمایش
    val activeIndex = selectedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val activePage = pages.getOrElse(activeIndex) { pages[0] }
    val activeFilter = activePage.filter
    val activeDisplayBitmap = activePage.processedBitmap ?: activePage.rawBitmap
    val allPagesBitmaps = remember(pages) {
        pages.map { it.processedBitmap ?: it.rawBitmap }
    }

    // اعمال فیلتر دلخواه روی برگه انتخاب‌شده فعلی
    fun applyFilterToActive(filter: ScanFilter) {
        if (activePage.filter == filter && activePage.processedBitmap != null) return
        coroutineScope.launch {
            isProcessing = true
            val targetIdx = activeIndex
            val targetPage = pages.getOrElse(targetIdx) { pages[0] }
            val newFiltered = DocFilterEngine.applyFilter(targetPage.rawBitmap, filter)
            val updated = pages.toMutableList()
            updated[targetIdx] = targetPage.copy(filter = filter, processedBitmap = newFiltered)
            pages = updated
            onAdditionalPagesChange(updated.drop(1).map { it.processedBitmap ?: it.rawBitmap })
            isProcessing = false
        }
    }

    // در صورت باز بودن حالت برش، کامپوننت ۴ گوشه برای برگه انتخاب‌شده نمایش داده می‌شود
    if (isCropModeOpen) {
        PerspectiveCropView(
            initialBitmap = activePage.rawBitmap,
            onConfirmCrop = { cropped ->
                coroutineScope.launch {
                    isProcessing = true
                    val targetIdx = activeIndex
                    val targetPage = pages.getOrElse(targetIdx) { pages[0] }
                    val newFiltered = DocFilterEngine.applyFilter(cropped, targetPage.filter)
                    val updated = pages.toMutableList()
                    updated[targetIdx] = targetPage.copy(
                        rawBitmap = cropped,
                        processedBitmap = newFiltered
                    )
                    pages = updated
                    onAdditionalPagesChange(updated.drop(1).map { it.processedBitmap ?: it.rawBitmap })
                    isProcessing = false
                    isCropModeOpen = false
                    Toast.makeText(context, "کادر برگه ${targetIdx + 1} تنظیم شد و فیلتر ${targetPage.filter.titleFa} اعمال گردید", Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = {
                isCropModeOpen = false
            }
        )
    } else {
        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        // دکمه اشتراک‌گذاری
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    DocFilterEngine.shareBitmap(
                                        context = context,
                                        bitmap = activeDisplayBitmap,
                                        title = docTitle
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "اشتراک‌گذاری",
                                tint = PrimaryBlue
                            )
                        }

                        // دکمه ذخیره در حافظه محلی و ساخت PDF
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    DocStorageManager.saveDocument(
                                        context = context,
                                        title = docTitle,
                                        filter = pages[0].filter,
                                        bitmap = pages[0].processedBitmap ?: pages[0].rawBitmap
                                    )
                                    val pdfFile = DocStorageManager.createMultiPagePdf(
                                        context = context,
                                        pages = allPagesBitmaps,
                                        title = docTitle
                                    )
                                    Toast.makeText(
                                        context,
                                        "مدرک «$docTitle» و فایل PDF (${allPagesBitmaps.size} برگه) ذخیره شد",
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

                // نوار مدرن و ارگونومیک با سایه ملایم جهت تبدیل همزمان برگه‌ها به یک PDF واحد
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFAFAFC),
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(8.dp),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "تبدیل به PDF واحد",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${allPagesBitmaps.size} برگه انتخاب‌شده",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // دکمه ساخت و دانلود PDF واحد
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isGeneratingPdf = true
                                        try {
                                            val pdfFile = DocStorageManager.createMultiPagePdf(
                                                context = context,
                                                pages = allPagesBitmaps,
                                                title = docTitle
                                            )
                                            Toast.makeText(
                                                context,
                                                "فایل PDF با ${allPagesBitmaps.size} برگه با موفقیت تولید شد",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            DocStorageManager.sharePdfFile(context, pdfFile, docTitle)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "خطا در ساخت PDF", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isGeneratingPdf = false
                                        }
                                    }
                                },
                                enabled = !isGeneratingPdf,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF059669),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                if (isGeneratingPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "دانلود PDF واحد",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // ردیف اسکرول برگه‌ها و دکمه افزودن برگه با سایه و طراحی ارگونومیک (چیدمان راست‌چین: برگه اصلی در سمت راست و دکمه افزودن برگه در سمت چپ)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // لیست برگه‌ها (ابتدا برگه ۱ اصلی در سمت راست، سپس برگه‌های بعدی)
                            itemsIndexed(pages) { index, _ ->
                                val isSelected = selectedPageIndex == index
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) PrimaryBlueContainer else Color.White,
                                    shadowElevation = if (isSelected) 2.dp else 1.dp,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.clickable { selectedPageIndex = index }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = if (isSelected) PrimaryBlue else Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (index == 0) "برگه ۱ (اصلی)" else "برگه ${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) OnPrimaryBlueContainer else TextSecondary
                                        )
                                        if (index > 0) {
                                            IconButton(
                                                onClick = {
                                                    val updated = pages.toMutableList().also {
                                                        it.removeAt(index)
                                                    }
                                                    pages = updated
                                                    onAdditionalPagesChange(updated.drop(1).map { it.processedBitmap ?: it.rawBitmap })
                                                    if (selectedPageIndex >= pages.size) {
                                                        selectedPageIndex = (pages.size - 1).coerceAtLeast(0)
                                                    }
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "حذف برگه",
                                                    tint = Color(0xFFDC2626),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // دکمه افزودن برگه در سمت چپ برگه‌ها در چیدمان راست‌چین
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0F9FF),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.clickable { onAddPageFromHome() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFFBAE6FD),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "افزودن برگه",
                                                    tint = PrimaryBlue,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "افزودن برگه",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                            isSelected = activeFilter == ScanFilter.PHOTOCOPY,
                            modifier = Modifier.weight(1f),
                            onClick = { applyFilterToActive(ScanFilter.PHOTOCOPY) }
                        )

                        FilterButton(
                            title = ScanFilter.BLACK_AND_WHITE.titleFa,
                            icon = Icons.Default.Contrast,
                            isSelected = activeFilter == ScanFilter.BLACK_AND_WHITE,
                            modifier = Modifier.weight(1f),
                            onClick = { applyFilterToActive(ScanFilter.BLACK_AND_WHITE) }
                        )

                        FilterButton(
                            title = ScanFilter.CLEAR_COLOR.titleFa,
                            icon = Icons.Default.AutoFixHigh,
                            isSelected = activeFilter == ScanFilter.CLEAR_COLOR,
                            modifier = Modifier.weight(1f),
                            onClick = { applyFilterToActive(ScanFilter.CLEAR_COLOR) }
                        )

                        FilterButton(
                            title = ScanFilter.ORIGINAL.titleFa,
                            icon = Icons.Default.Image,
                            isSelected = activeFilter == ScanFilter.ORIGINAL,
                            modifier = Modifier.weight(1f),
                            onClick = { applyFilterToActive(ScanFilter.ORIGINAL) }
                        )
                    }

                    // توضیحات فیلتر و وضعیت در حال پردازش
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeFilter.descriptionFa,
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
        // کادر مدرن پیش‌نمایش تصویر با فاصله معقول از لبه‌ها، پس‌زمینه شیک، و زوم دو انگشتی
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE5E9F0)) // پس‌زمینه ملایم و چشم‌نواز میز کار
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 3.5f)
                        if (scale == 1f) {
                            offset = Offset.Zero
                        } else {
                            val maxOffset = (scale - 1f) * 400f
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                y = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // ابزارک شناور کنترل زوم در گوشه بالا-چپ
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(3.5f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "بزرگ‌نمایی",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "${(scale * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = {
                            scale = (scale - 0.25f).coerceAtLeast(1f)
                            if (scale == 1f) offset = Offset.Zero
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "کوچک‌نمایی",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (scale > 1f) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "اندازه اصلی",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // نشانگر وضعیت فیلتر در بالا-راست
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = activeFilter.titleFa,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            // برگه سفید شناور با سایه ملایم و نسبت تطبیق‌پذیر به تصویر افقی یا عمودی
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .wrapContentSize()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(8.dp),
                            spotColor = Color(0x330F172A),
                            ambientColor = Color(0x1A0F172A)
                        )
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = activeDisplayBitmap.asImageBitmap(),
                            contentDescription = docTitle,
                            modifier = Modifier
                                .wrapContentSize()
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )

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
