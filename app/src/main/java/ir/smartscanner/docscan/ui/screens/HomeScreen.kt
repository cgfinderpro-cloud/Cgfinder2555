package ir.smartscanner.docscan.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.DocumentPage
import ir.smartscanner.docscan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<DocumentItem>,
    onOpenDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onAddPageToDocument: (docId: String, useCamera: Boolean) -> Unit = { _, _ -> },
    onDeletePageFromDocument: (docId: String, pageId: String) -> Unit = { _, _ -> }
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var documentToDelete by remember { mutableStateOf<DocumentItem?>(null) }
    var managingPagesDoc by remember { mutableStateOf<DocumentItem?>(null) }
    var pageToDelete by remember { mutableStateOf<Pair<String, DocumentPage>?>(null) }

    val filteredDocs = remember(documents, searchQuery) {
        if (searchQuery.isBlank()) {
            documents
        } else {
            documents.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("جستجو در مدارک...", color = TextTertiary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن جستجو",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLight)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "اسکنر هوشمند مدارک",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "مدیریت چند صفحه‌ای با شناسه یکتا و فیلتر فتوکپی",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLight),
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "جستجو",
                                tint = TextSecondary
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            // دو دکمه شناور بزرگ (FAB): یکی برای «دوربین» و دیگری برای «گالری» جهت ایجاد پرونده جدید
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // دکمه گالری
                ExtendedFloatingActionButton(
                    onClick = onLaunchGallery,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "گالری",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "سند از گالری",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // دکمه دوربین
                ExtendedFloatingActionButton(
                    onClick = onLaunchCamera,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "دوربین",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اسکن سند جدید",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = BackgroundLight
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مدارک اسکن‌شده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${filteredDocs.size} پرونده",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }

            if (filteredDocs.isEmpty()) {
                item {
                    EmptyDocumentsPlaceholder(
                        isSearching = searchQuery.isNotBlank(),
                        onLaunchCamera = onLaunchCamera,
                        onLaunchGallery = onLaunchGallery
                    )
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    DocumentCard(
                        doc = doc,
                        onClick = { onOpenDocument(doc.id) },
                        onDelete = { documentToDelete = doc },
                        onManagePages = { managingPagesDoc = doc },
                        onAddPage = { useCamera -> onAddPageToDocument(doc.id, useCamera) }
                    )
                }
            }
        }
    }

    // دیالوگ مدیریت صفحات سند زیر یک شناسه مدرک یکتا
    managingPagesDoc?.let { doc ->
        val currentDoc = documents.find { it.id == doc.id } ?: doc
        ManagePagesDialog(
            doc = currentDoc,
            onDismiss = { managingPagesDoc = null },
            onAddPage = { useCamera ->
                managingPagesDoc = null
                onAddPageToDocument(currentDoc.id, useCamera)
            },
            onDeletePage = { page ->
                pageToDelete = Pair(currentDoc.id, page)
            },
            onOpenDocument = {
                managingPagesDoc = null
                onOpenDocument(currentDoc.id)
            }
        )
    }

    // دیالوگ تأیید حذف صفحه خاص
    pageToDelete?.let { (docId, page) ->
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = {
                Text(
                    text = "حذف صفحه ${page.pageNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "آیا از حذف صفحه شماره ${page.pageNumber} از این سند اطمینان دارید؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePageFromDocument(docId, page.id)
                        pageToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف صفحه", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // دیالوگ تأیید حذف کل مدرک
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = {
                Text(
                    text = "حذف کامل پرونده",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "آیا از حذف پرونده «${doc.title}» و کلیه ${doc.pages.size.coerceAtLeast(doc.pageCount)} صفحه آن اطمینان دارید؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDocument(doc.id)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف پرونده", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun DocumentCard(
    doc: DocumentItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onManagePages: () -> Unit,
    onAddPage: (useCamera: Boolean) -> Unit
) {
    val totalPages = doc.pages.size.coerceAtLeast(doc.pageCount)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ردیف اصلی اطلاعات مدرک
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // عکس بندانگشتی شاخص مدرک
                Box(
                    modifier = Modifier
                        .size(64.dp, 82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariantLight)
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val coverBitmap = doc.primaryBitmap
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = doc.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // عنوان، تاریخ و برچسب‌ها
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )

                    Text(
                        text = "تاریخ: ${doc.datePersian}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // برچسب تعداد صفحات با نشانگر چندصفحه‌ای
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryBlueContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = OnPrimaryBlueContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "$totalPages صفحه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnPrimaryBlueContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // برچسب فیلتر
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceVariantLight
                        ) {
                            Text(
                                text = doc.filter.titleFa,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // دکمه حذف مدرک
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف مدرک",
                        tint = TextTertiary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // نوار افقی پیش‌نمایش صفحات زیر همین شناسه سند (Multi-page horizontal strip)
            if (doc.pages.isNotEmpty()) {
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), thickness = 0.8.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // نمایش بندانگشتی تک‌تک صفحات با برگه و شماره صفحه
                    doc.pages.forEach { page ->
                        Box(
                            modifier = Modifier
                                .size(46.dp, 60.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceVariantLight)
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (page.bitmap != null) {
                                Image(
                                    bitmap = page.bitmap.asImageBitmap(),
                                    contentDescription = "صفحه ${page.pageNumber}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // نشانگر شماره صفحه
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ص ${page.pageNumber}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // دکمه افزودن سریع صفحه جدید در نوار صفحات
                    OutlinedButton(
                        onClick = onManagePages,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(60.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PrimaryBlueContainer.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "افزودن برگه",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ردیف دکمه‌های عملیاتی پایین کارت: مدیریت صفحات و پیش‌نمایش
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // دکمه مدیریت صفحات چندگانه (تحت شناسه همین سند)
                Button(
                    onClick = onManagePages,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PostAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مدیریت صفحات ($totalPages برگه)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // دکمه مشاهده و اسکن پیش‌نمایش
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "مشاهده سند",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * دیالوگ مدیریت صفحات سند: افزودن برگه‌های جدید به شناسه سند موجود و مشاهده/حذف صفحات
 */
@Composable
fun ManagePagesDialog(
    doc: DocumentItem,
    onDismiss: () -> Unit,
    onAddPage: (useCamera: Boolean) -> Unit,
    onDeletePage: (DocumentPage) -> Unit,
    onOpenDocument: () -> Unit
) {
    val pages = doc.pages

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "مدیریت صفحات سند",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${doc.title} (شناسه: ${doc.id})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "می‌توانید صفحات جدیدی با دوربین یا گالری به همین پرونده اضافه کنید یا صفحات قبلی را بررسی و حذف نمایید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                // دو دکمه افزودن برگه به این سند
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAddPage(true) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ صفحه با دوربین",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { onAddPage(false) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ صفحه از گالری",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // لیست صفحات موجود در این سند
                Text(
                    text = "صفحات موجود در این پرونده (${pages.size} برگه):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (pages.isEmpty()) {
                    Text(
                        text = "هنوز صفحه‌ای ذخیره نشده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pages.forEach { page ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceVariantLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // تصویر بندانگشتی صفحه
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp, 48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (page.bitmap != null) {
                                            Image(
                                                bitmap = page.bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = TextTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "برگه شماره ${page.pageNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "شناسه برگه: ${page.id.takeLast(12)}",
                                            fontSize = 10.sp,
                                            color = TextTertiary
                                        )
                                    }

                                    // دکمه حذف این صفحه (در صورت وجود بیش از ۱ صفحه)
                                    if (pages.size > 1) {
                                        IconButton(
                                            onClick = { onDeletePage(page) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "حذف صفحه",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenDocument) {
                Text("مشاهده کل پرونده")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@Composable
fun EmptyDocumentsPlaceholder(
    isSearching: Boolean,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = SurfaceVariantLight,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        if (isSearching) {
            Text(
                text = "مدرکی با این عنوان یافت نشد",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        } else {
            Text(
                text = "هنوز مدرکی ذخیره نشده است",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "با لمس دکمه «اسکن سند جدید» عکس بگیرید و در صورت تمایل برگه‌های متعددی را به همین پرونده اضافه کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

