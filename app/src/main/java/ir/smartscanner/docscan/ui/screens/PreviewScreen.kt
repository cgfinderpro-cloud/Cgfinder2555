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
import kotlinx.coroutines.launch

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

    // بیت‌مپ خام منبع: یا از تصویر فایل/حافظه یا ساخت نمونه
    val initialBitmap = remember(document?.id) {
        document?.bitmap
            ?: (document?.filePath?.let { DocStorageManager.loadSampledBitmap(it, 1600, 2200) })
            ?: DocFilterEngine.createSampleDocBitmap(docTitle)
    }

    // بیت‌مپ فعال جاری (قبل از فیلتر، پس از اعمال برش‌های پرسپکتیو)
    var currentRawBitmap by remember { mutableStateOf(initialBitmap) }

    // بیت‌مپ نهایی فیلتر شده (فتوکپی، سیاه و سفید، رنگی شفاف یا اصلی)
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

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
        // کادر پیش‌نمایش تصویر در وسط صفحه
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = Color.Black.copy(alpha = 0.2f)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val displayBitmap = processedBitmap ?: currentRawBitmap
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = docTitle,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )

                    // نشانگر گوشه بالا
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "فیلتر: ${selectedFilter.titleFa}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
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
