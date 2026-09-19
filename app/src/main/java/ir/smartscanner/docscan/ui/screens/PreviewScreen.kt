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

    // بیت‌مپ خام منبع: یا از تصویر فایل/حافظه یا ساخت نمونه
    val initialBitmap = remember(document?.id) {
        document?.bitmap
            ?: (document?.filePath?.let { DocStorageManager.loadSampledBitmap(it, 1600, 2200) })
            ?: DocFilterEngine.createSampleDocBitmap(docTitle)
    }

    // بیت‌مپ فعال جاری (قبل از فیلتر، پس از اعمال برش‌های پرسپکتیو)
    var currentRawBitmap by remember(initialBitmap) { mutableStateOf(initialBitmap) }

    // بیت‌مپ نهایی فیلتر شده (فتوکپی، سیاه و سفید، رنگی شفاف یا اصلی)
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // برگه‌های اضافی اضافه شده به سند برای تبدیل چند برگه به یک PDF واحد
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    // وضعیت‌های زوم دو انگشتی و جابه‌جایی تعاملی برای سند اسکن‌شده
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val allPages = remember(processedBitmap, currentRawBitmap, additionalPages) {
        val firstPage = processedBitmap ?: currentRawBitmap
        listOf(firstPage) + additionalPages
    }

    // انتخابی چند تصویر از گالری جهت افزودن برگه‌های جدید به PDF
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val loaded = uris.mapNotNull { uri ->
                DocStorageManager.loadBitmapFromUri(context, uri)
            }
            if (loaded.isNotEmpty()) {
                onAdditionalPagesChange(additionalPages + loaded)
                Toast.makeText(context, "${loaded.size} برگه جدید به سند اضافه شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // وضعیت فعال بودن حالت برش ۴ گوشه و پرسپکتیو
    var isCropModeOpen by remember { mutableStateOf(false) }

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
                                contentDescription = "اشتراک‌گذاری",
                                tint = PrimaryBlue
                            )
                        }

                        // دکمه ذخیره در حافظه محلی و ساخت PDF
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
                                    val pdfFile = DocStorageManager.createMultiPagePdf(
                                        context = context,
                                        pages = allPages,
                                        title = docTitle
                                    )
                                    Toast.makeText(
                                        context,
                                        "مدرک «$docTitle» و فایل PDF (${allPages.size} برگه) ذخیره شد",
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
                                        text = "${allPages.size} برگه انتخاب‌شده",
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
                                                pages = allPages,
                                                title = docTitle
                                            )
                                            Toast.makeText(
                                                context,
                                                "فایل PDF با ${allPages.size} برگه با موفقیت تولید شد",
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
                            itemsIndexed(allPages) { index, _ ->
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
                                                    val updated = additionalPages.toMutableList().also {
                                                        it.removeAt(index - 1)
                                                    }
                                                    onAdditionalPagesChange(updated)
                                                    if (selectedPageIndex >= allPages.size - 1) {
                                                        selectedPageIndex = 0
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
                    text = selectedFilter.titleFa,
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
                        val displayBitmap = if (selectedPageIndex in allPages.indices) allPages[selectedPageIndex] else (processedBitmap ?: currentRawBitmap)
                        Image(
                            bitmap = displayBitmap.asImageBitmap(),
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
