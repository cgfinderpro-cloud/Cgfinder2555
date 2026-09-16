package ir.smartscanner.docscan.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
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
import ir.smartscanner.docscan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<DocumentItem>,
    onOpenDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var documentToDelete by remember { mutableStateOf<DocumentItem?>(null) }

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
                                    text = "فتوکپی، برش پرسپکتیو و ذخیره آفلاین",
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
            // دو دکمه شناور بزرگ (FAB): یکی برای «دوربین» و دیگری برای «گالری»
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
                            text = "گالری",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
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
                            text = "دوربین",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "${filteredDocs.size} مدرک",
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
                        onDelete = { documentToDelete = doc }
                    )
                }
            }
        }
    }

    // دیالوگ تأیید حذف مدرک
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = {
                Text(
                    text = "حذف مدرک",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "آیا از حذف مدرک «${doc.title}» اطمینان دارید؟ تصویر این مدرک از حافظه دستگاه پاک خواهد شد.",
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
                    Text("حذف", fontWeight = FontWeight.Bold)
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // عکس بندانگشتی واقعی مدرک (Thumbnail)
            Box(
                modifier = Modifier
                    .size(68.dp, 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantLight)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (doc.bitmap != null) {
                    Image(
                        bitmap = doc.bitmap.asImageBitmap(),
                        contentDescription = doc.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // اطلاعات سند (عنوان، تاریخ، حالت فیلتر)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryBlueContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = doc.filter.titleFa,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnPrimaryBlueContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "${doc.pageCount} صفحه",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            // دکمه حذف
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف مدرک",
                    tint = TextTertiary.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
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
                text = "با لمس دکمه «دوربین» از مدارک عکس بگیرید یا از «گالری» سند وارد کنید و با فیلتر فتوکپی کیفیت آن را ارتقا دهید.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )
        }
    }
}
