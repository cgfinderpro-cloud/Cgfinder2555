package ir.smartscanner.docscan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.smartscanner.docscan.model.DocumentItem
import ir.smartscanner.docscan.model.ScanFilter
import ir.smartscanner.docscan.ui.components.PerspectiveCropView
import ir.smartscanner.docscan.ui.navigation.Screen
import ir.smartscanner.docscan.ui.screens.HomeScreen
import ir.smartscanner.docscan.ui.screens.PreviewScreen
import ir.smartscanner.docscan.ui.theme.SmartScannerTheme
import ir.smartscanner.docscan.util.DocFilterEngine
import ir.smartscanner.docscan.util.DocStorageManager
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // پشتیبانی کامل از زبان فارسی و راست‌چین (RTL)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SmartScannerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        SmartScannerApp()
                    }
                }
            }
        }
    }
}

@Composable
fun SmartScannerApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // ۱. لیست اسناد واقعی خوانده‌شده از حافظه محلی
    var documentList by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }

    // سند موقت ایجادشده از دوربین یا گالری که هنوز ذخیره نشده
    var pendingDocument by remember { mutableStateOf<DocumentItem?>(null) }

    // متغیر کمکی برای آدرس موقت تصویر دوربین
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // بارگذاری اسناد واقعی ذخیره‌شده در شروع و پس از هر تغییر
    fun refreshDocuments() {
        documentList = DocStorageManager.getAllDocuments(context)
    }

    LaunchedEffect(Unit) {
        refreshDocuments()
    }

    // متغیرهای موقت برای تصویری که تازه از دوربین یا گالری گرفته شده (قبل از برش و پرسپکتیو)
    var capturedRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedDocTitle by remember { mutableStateOf<String>("") }

    // متغیرهای وضعیت نوار چندبرگه‌ای پی‌دی‌اف و هدایت به صفحه اصلی
    var currentPdfAdditionalPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isAddingPageMode by remember { mutableStateOf(false) }
    var activePreviewDocId by remember { mutableStateOf<String?>(null) }

    // هدایت بلافاصله به صفحه تنظیم کادر و برش پرسپکتیو (PerspectiveCropView) به جای پیش‌نمایش ساده
    fun openCropScreenForNewCapture(bitmap: Bitmap, title: String) {
        capturedRawBitmap = bitmap
        capturedDocTitle = title
        navController.navigate(Screen.Crop.route)
    }

    // ۲. لانچر عکس‌برداری کیفیت بالا با TakePicture
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val bitmap = DocFilterEngine.loadBitmapFromUri(context, tempCameraUri!!)
            if (bitmap != null) {
                openCropScreenForNewCapture(bitmap, "سند دوربین - ${DocStorageManager.getPersianDateNow()}")
            } else {
                Toast.makeText(context, "خطا در بارگذاری تصویر دوربین", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // لانچر پشتیبان برای پیش‌نمایش مستقیم دوربین در صورت عدم دسترسی به FileProvider
    val previewCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            openCropScreenForNewCapture(bitmap, "سند دوربین - ${DocStorageManager.getPersianDateNow()}")
        }
    }

    // تابع ایمن راه‌اندازی دوربین
    val launchCameraDirectly = {
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
            val photoFile = File(cameraDir, "camera_doc_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = photoUri
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            // پشتیبان در صورت خطای ساخت فایل موقت
            try {
                previewCameraLauncher.launch(null)
            } catch (ex: Exception) {
                Toast.makeText(context, "خطا در باز کردن دوربین: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ۳. لانچر درخواست مجوز دسترسی به دوربین (حل قطعی مشکل کرش برنامه)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraDirectly()
        } else {
            Toast.makeText(
                context,
                "جهت عکاسی از اسناد، دسترسی به دوربین الزامی است",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // بررسی و راه‌اندازی دوربین با درخواست مجوز در صورت نیاز
    val onLaunchCamera = {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            launchCameraDirectly()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ۴. لانچر گالری دستگاه
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val loadedBitmap = DocFilterEngine.loadBitmapFromUri(context, uri)
            if (loadedBitmap != null) {
                openCropScreenForNewCapture(loadedBitmap, "سند گالری - ${DocStorageManager.getPersianDateNow()}")
            } else {
                Toast.makeText(context, "خطا در خواندن تصویر از گالری", Toast.LENGTH_SHORT).show()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // ۱. صفحه اصلی (Home)
        composable(Screen.Home.route) {
            val targetDoc = documentList.find { it.id == activePreviewDocId } ?: pendingDocument
            HomeScreen(
                documents = documentList,
                onOpenDocument = { docId ->
                    if (isAddingPageMode && activePreviewDocId != null) {
                        val selectedDoc = documentList.find { it.id == docId }
                        if (selectedDoc != null) {
                            // از آنجا که سند از قبل ویرایش، کادربندی و ذخیره شده، مستقیماً به نوار بالای صفحه افزوده شده
                            // و کاربر بدون معطلی و بدون نیاز به برش مجدد به صفحه پیش‌نمایش هدایت می‌شود.
                            val bmp = selectedDoc.bitmap
                                ?: (selectedDoc.filePath?.let { DocStorageManager.loadSampledBitmap(it, 1600, 2200) })
                                ?: DocFilterEngine.createSampleDocBitmap(selectedDoc.title)
                            val readyBitmap = DocFilterEngine.applyFilterSync(bmp, selectedDoc.filter)
                            currentPdfAdditionalPages = currentPdfAdditionalPages + readyBitmap
                            isAddingPageMode = false
                            Toast.makeText(context, "سند «${selectedDoc.title}» مستقیماً به نوار برگه‌ها افزوده شد", Toast.LENGTH_SHORT).show()
                            navController.navigate(Screen.Preview.createRoute(activePreviewDocId!!)) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        }
                    } else {
                        currentPdfAdditionalPages = emptyList()
                        activePreviewDocId = docId
                        navController.navigate(Screen.Preview.createRoute(docId))
                    }
                },
                onDeleteDocument = { docId ->
                    DocStorageManager.deleteDocument(context, docId)
                    refreshDocuments()
                    Toast.makeText(context, "مدرک با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                },
                onLaunchCamera = onLaunchCamera,
                onLaunchGallery = {
                    galleryLauncher.launch("image/*")
                },
                isAddingPageMode = isAddingPageMode,
                targetDocTitle = targetDoc?.title ?: "",
                onCancelAddPage = {
                    isAddingPageMode = false
                    if (activePreviewDocId != null) {
                        navController.navigate(Screen.Preview.createRoute(activePreviewDocId!!)) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                }
            )
        }

        // ۲. صفحه برش و تنظیم پرسپکتیو (PerspectiveCropView) - بلافاصله پس از عکس‌برداری یا انتخاب گالری
        composable(Screen.Crop.route) {
            val rawBitmap = capturedRawBitmap
            if (rawBitmap != null) {
                PerspectiveCropView(
                    initialBitmap = rawBitmap,
                    onConfirmCrop = { processedBitmap ->
                        if (isAddingPageMode && activePreviewDocId != null) {
                            // اعمال فیلتر هوشمند فتوکپی استودیویی بر روی برگه جدید برش‌خورده به جای ارسال خام
                            val filteredBitmap = DocFilterEngine.applyFilterSync(processedBitmap, ScanFilter.PHOTOCOPY)
                            currentPdfAdditionalPages = currentPdfAdditionalPages + filteredBitmap
                            isAddingPageMode = false
                            capturedRawBitmap = null
                            Toast.makeText(context, "برگه جدید تنظیم کادر شد و با فیلتر فتوکپی استودیویی به سند اضافه گردید", Toast.LENGTH_SHORT).show()
                            navController.navigate(Screen.Preview.createRoute(activePreviewDocId!!)) {
                                popUpTo(Screen.Crop.route) { inclusive = true }
                            }
                        } else {
                            // پس از تأیید نهایی، متغیر وضعیت تصویر پردازش‌شده به صفحه نمایش نهایی ارسال می‌شود
                            val newDocId = "new_${System.currentTimeMillis()}"
                            val newDoc = DocumentItem(
                                id = newDocId,
                                title = capturedDocTitle.ifEmpty { "سند اسکن‌شده - ${DocStorageManager.getPersianDateNow()}" },
                                datePersian = DocStorageManager.getPersianDateNow(),
                                filter = ScanFilter.PHOTOCOPY,
                                pageCount = 1,
                                bitmap = processedBitmap
                            )
                            pendingDocument = newDoc
                            currentPdfAdditionalPages = emptyList()
                            activePreviewDocId = newDocId
                            // هدایت مطمئن به صفحه پیش‌نمایش و خارج کردن صفحه برش از BackStack
                            navController.navigate(Screen.Preview.createRoute(newDocId)) {
                                popUpTo(Screen.Crop.route) { inclusive = true }
                            }
                        }
                    },
                    onCancel = {
                        capturedRawBitmap = null
                        if (isAddingPageMode && activePreviewDocId != null) {
                            navController.navigate(Screen.Preview.createRoute(activePreviewDocId!!)) {
                                popUpTo(Screen.Crop.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            } else if (pendingDocument == null) {
                LaunchedEffect(Unit) {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            }
        }

        // ۳. صفحه نمایش نهایی و فیلترها (Preview Screen)
        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument("docId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")
            val document = if (docId != null && pendingDocument?.id == docId) {
                pendingDocument
            } else {
                documentList.find { it.id == docId }
            }

            if (docId != null) {
                activePreviewDocId = docId
            }

            PreviewScreen(
                document = document,
                additionalPages = currentPdfAdditionalPages,
                onAdditionalPagesChange = { currentPdfAdditionalPages = it },
                onAddPageFromHome = {
                    isAddingPageMode = true
                    navController.navigate(Screen.Home.route)
                },
                onBack = {
                    pendingDocument = null
                    capturedRawBitmap = null
                    currentPdfAdditionalPages = emptyList()
                    activePreviewDocId = null
                    isAddingPageMode = false
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onSaveSuccess = {
                    refreshDocuments()
                    pendingDocument = null
                    capturedRawBitmap = null
                    currentPdfAdditionalPages = emptyList()
                    activePreviewDocId = null
                    isAddingPageMode = false
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}
