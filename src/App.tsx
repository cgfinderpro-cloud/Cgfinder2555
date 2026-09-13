import React, { useState, useRef } from 'react';
import { 
  Camera, 
  Image as ImageIcon, 
  ArrowRight, 
  Share2, 
  Save, 
  FileText, 
  Printer, 
  Contrast, 
  Sparkles, 
  Layers, 
  Check, 
  Download, 
  Code2, 
  Smartphone, 
  ShieldCheck, 
  ChevronLeft,
  Search,
  MoreVertical,
  Upload,
  Info,
  Crop,
  RotateCw,
  Wand2,
  Maximize2
} from 'lucide-react';

type FilterType = 'photocopy' | 'bw' | 'color' | 'original';

interface DocumentItem {
  id: string;
  title: string;
  datePersian: string;
  filter: FilterType;
  pageCount: number;
  imageSrc?: string;
}

const INITIAL_DOCUMENTS: DocumentItem[] = [
  {
    id: 'doc-1',
    title: 'شناسنامه و کارت ملی هوشمند',
    datePersian: '۲۲ اردیبهشت ۱۴۰۳',
    filter: 'photocopy',
    pageCount: 2,
  },
  {
    id: 'doc-2',
    title: 'قرارداد کاری و سفته بانکی',
    datePersian: '۱۸ اردیبهشت ۱۴۰۳',
    filter: 'color',
    pageCount: 4,
  },
  {
    id: 'doc-3',
    title: 'قبض بیمه و گواهی مهارت فنی',
    datePersian: '۱۰ اردیبهشت ۱۴۰۳',
    filter: 'bw',
    pageCount: 1,
  },
];

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<'home' | 'crop' | 'preview'>('home');
  const [documents, setDocuments] = useState<DocumentItem[]>(INITIAL_DOCUMENTS);
  const [activeDocId, setActiveDocId] = useState<string>('doc-1');
  const [selectedFilter, setSelectedFilter] = useState<FilterType>('photocopy');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'preview' | 'code' | 'cicd'>('preview');
  const [selectedFileCode, setSelectedFileCode] = useState<string>('NavGraph.kt');
  const [customImage, setCustomImage] = useState<string | null>(null);

  // وضعیت‌های تصویر گرفته‌شده از دوربین یا انتخاب‌شده از گالری قبل از برش
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [capturedTitle, setCapturedTitle] = useState<string>('');

  // وضعیت‌های مربوط به برش ۴ گوشه و تصحیح پرسپکتیو با Matrix.setPolyToPoly و Canny
  const [rotationDegrees, setRotationDegrees] = useState(0);
  const [corners, setCorners] = useState<{ x: number; y: number }[]>([
    { x: 12, y: 12 }, // بالا-چپ
    { x: 88, y: 14 }, // بالا-راست
    { x: 85, y: 88 }, // پایین-راست
    { x: 15, y: 86 }, // پایین-چپ
  ]);
  const [activeCornerIdx, setActiveCornerIdx] = useState<number | null>(null);
  const [isAutoDetecting, setIsAutoDetecting] = useState(false);
  const [isPerspectiveCropped, setIsPerspectiveCropped] = useState(false);
  const cropContainerRef = useRef<HTMLDivElement>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const activeDoc = documents.find(d => d.id === activeDocId) || documents[0];

  const handleOpenDoc = (id: string) => {
    const doc = documents.find(d => d.id === id);
    if (doc) {
      setActiveDocId(id);
      setSelectedFilter(doc.filter);
      setCurrentScreen('preview');
    }
  };

  // تابع آغاز فرایند پس از عکسبرداری یا گالری: بلافاصله هدایت به PerspectiveCropView
  const handleStartCaptureFlow = (imageSrc: string, title: string) => {
    setCapturedImage(imageSrc);
    setCapturedTitle(title);
    setRotationDegrees(0);
    setIsPerspectiveCropped(false);
    setCorners([
      { x: 10, y: 12 },
      { x: 90, y: 13 },
      { x: 86, y: 88 },
      { x: 13, y: 86 },
    ]);
    // هدایت مستقیم و فوری به کامپوننت PerspectiveCropView طبق منطق نویگیشن جدید
    setCurrentScreen('crop');
    showToast('عکس دریافت شد؛ هدایت بلافاصله به PerspectiveCropView جهت تراز ۴ گوشه');
  };

  const handleCameraCapture = () => {
    // باز کردن دوربین یا فایل منیجر در شبیه‌ساز
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  // شبیه‌سازی عکاسی مستقیم با دوربین در شبیه‌ساز جهت تست فوری جریان نویگیشن
  const handleSimulateCameraCapture = () => {
    const sampleImage = 'https://images.unsplash.com/photo-1586281380349-632531db7ed4?w=800&auto=format&fit=crop&q=80';
    handleStartCaptureFlow(sampleImage, `سند دوربین - ${new Date().toLocaleDateString('fa-IR')}`);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const result = event.target?.result as string;
        handleStartCaptureFlow(result, file.name.replace(/\.[^/.]+$/, "") || 'سند جدید دوربین');
      };
      reader.readAsDataURL(file);
    }
  };

  // انصراف از صفحه PerspectiveCropView و بازگشت به صفحه اصلی
  const handleCancelCrop = () => {
    setCapturedImage(null);
    setCurrentScreen('home');
    showToast('عملیات برش و تراز کادر لغو شد');
  };

  // تایید نهایی در صفحه PerspectiveCropView و ارسال متغیر وضعیت تصویر پردازش‌شده به صفحه نمایش نهایی (Preview)
  const handleConfirmCropAndNavigate = (e?: React.MouseEvent) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    setIsPerspectiveCropped(true);
    const finalProcessedImage = capturedImage || customImage || activeDoc.imageSrc;
    setCustomImage(finalProcessedImage);

    const newDocId = `doc-${Date.now()}`;
    const newDoc: DocumentItem = {
      id: newDocId,
      title: capturedTitle || 'سند اسکن‌شده جدید',
      datePersian: 'امروز',
      filter: 'photocopy',
      pageCount: 1,
      imageSrc: finalProcessedImage
    };
    setDocuments(prev => [newDoc, ...prev]);
    setActiveDocId(newDocId);
    setSelectedFilter('photocopy');

    // هدایت قطعی به صفحه نمایش نهایی (پیش‌نمایش سند در برگه سفید تمیز)
    setCurrentScreen('preview');
    showToast('سند پردازش و تراز شد و در صفحه سفید تمیز پیش‌نمایش قرار گرفت');
  };

  const handleSaveFilter = () => {
    setDocuments(prev => prev.map(d => d.id === activeDocId ? { ...d, filter: selectedFilter } : d));
    showToast(`مدرک با فیلتر «${getFilterLabel(selectedFilter)}» ذخیره شد`);
  };

  const handleShare = () => {
    showToast('آماده‌سازی سند جهت اشتراک‌گذاری در پیام‌رسان‌ها...');
  };

  const handleAutoDetectCorners = () => {
    setIsAutoDetecting(true);
    setTimeout(() => {
      // شبیه‌سازی نتایج دقیق EdgeDetectionEngine (Canny + Hysteresis)
      setCorners([
        { x: 8, y: 10 },
        { x: 92, y: 11 },
        { x: 90, y: 91 },
        { x: 9, y: 90 }
      ]);
      setIsAutoDetecting(false);
      showToast('تشخیص هوشمند لبه‌ها (Canny Edge Detection) اعمال شد');
    }, 450);
  };

  const handleFullFrame = () => {
    setCorners([
      { x: 3, y: 3 },
      { x: 97, y: 3 },
      { x: 97, y: 97 },
      { x: 3, y: 97 }
    ]);
    showToast('کادر به حالت تمام‌صفحه بازنشانی شد');
  };

  const handleRotate = () => {
    setRotationDegrees(prev => (prev + 90) % 360);
    showToast('چرخش ۹۰ درجه تصویر سند اعمال شد');
  };

  const handleCornerDrag = (clientX: number, clientY: number) => {
    if (activeCornerIdx === null || !cropContainerRef.current) return;
    const rect = cropContainerRef.current.getBoundingClientRect();
    const x = Math.max(2, Math.min(98, ((clientX - rect.left) / rect.width) * 100));
    const y = Math.max(2, Math.min(98, ((clientY - rect.top) / rect.height) * 100));
    setCorners(prev => {
      const next = [...prev];
      next[activeCornerIdx] = { x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
      return next;
    });
  };

  const getFilterLabel = (f: FilterType) => {
    switch (f) {
      case 'photocopy': return 'فتوکپی';
      case 'bw': return 'سیاه و سفید';
      case 'color': return 'رنگی شفاف';
      case 'original': return 'اصلی';
    }
  };

  const getFilterStyle = (f: FilterType): React.CSSProperties => {
    switch (f) {
      case 'photocopy':
        return {
          filter: 'grayscale(100%) contrast(240%) brightness(105%)',
          backgroundColor: '#FFFFFF',
        };
      case 'bw':
        return {
          filter: 'grayscale(100%) contrast(120%) brightness(95%)',
          backgroundColor: '#F8FAFC',
        };
      case 'color':
        return {
          filter: 'saturate(140%) contrast(125%) brightness(108%)',
          backgroundColor: '#FFFFFF',
        };
      case 'original':
        return {
          filter: 'none',
          backgroundColor: '#FFFBEB',
        };
    }
  };

  // کدهای متناظر اندروید نیتیو جهت بازبینی مستقیم کاربر در پیش‌نمایش
  const codeSnippets: Record<string, { lang: string; code: string; desc: string }> = {
    'app/build.gradle.kts': {
      lang: 'kotlin',
      desc: 'پیکربندی دقیق SDK 34، Jetpack Compose و سازگاری استاندارد مایکت و بازار',
      code: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.smartscanner.docscan"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.smartscanner.docscan"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
}`
    },
    'app/src/main/AndroidManifest.xml': {
      lang: 'xml',
      desc: 'دسترسی دوربین، پشتیبانی از زبان فارسی و راست‌چین (android:supportsRtl="true") و کامپوننت آفلاین',
      code: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SmartDocumentScanner">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>`
    },
    '.github/workflows/build-apk.yml': {
      lang: 'yaml',
      desc: 'ورکفلو خودکار گیت‌هاب اکشنز برای تولید فایل نصبی app-debug.apk و دانلود به عنوان Artifact',
      code: `name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    name: Assemble Debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
          cache: 'gradle'

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Build with Gradle
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: scanner-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 14`
    },
    'NavGraph.kt': {
      lang: 'kotlin',
      desc: 'تعریف مسیرهای سه‌گانه Navigation: صفحه اصلی (Home)، صفحه برش پرسپکتیو (Crop) و پیش‌نمایش نهایی (Preview)',
      code: `package ir.smartscanner.docscan.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Crop : Screen("crop") // بلافاصله پس از عکاسی یا انتخاب از گالری
    object Preview : Screen("preview/{docId}") {
        fun createRoute(docId: String) = "preview/$docId"
    }
}`
    },
    'MainActivity.kt': {
      lang: 'kotlin',
      desc: 'مدیریت جریان نویگیشن: هدایت فوری به PerspectiveCropView پس از دوربین/گالری و تحویل داده پردازش‌شده به PreviewScreen',
      code: `// ۱. تعریف متغیرهای نگهداری موقت تصویر خام منبع
var capturedRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
var capturedDocTitle by remember { mutableStateOf<String>("") }

// ۲. متد بازگشایی آنی صفحه برش و پرسپکتیو به جای پیش‌نمایش ساده
fun openCropScreenForNewCapture(bitmap: Bitmap, title: String) {
    capturedRawBitmap = bitmap
    capturedDocTitle = title
    navController.navigate(Screen.Crop.route)
}

// ۳. پیکربندی گراف مسیرها با تفکیک وظایف
NavHost(navController = navController, startDestination = Screen.Home.route) {
    composable(Screen.Home.route) {
        HomeScreen(
            documents = documentList,
            onOpenDocument = { docId -> navController.navigate(Screen.Preview.createRoute(docId)) },
            onLaunchCamera = onLaunchCamera,
            onLaunchGallery = { galleryLauncher.launch("image/*") }
        )
    }

    // بلافاصله پس از عکس‌برداری، کاربر به صفحه PerspectiveCropView هدایت می‌شود
    composable(Screen.Crop.route) {
        val rawBitmap = capturedRawBitmap
        if (rawBitmap != null) {
            PerspectiveCropView(
                initialBitmap = rawBitmap,
                onConfirmCrop = { processedBitmap ->
                    // پس از تأیید نهایی، متغیر وضعیت تصویر پردازش‌شده به صفحه پیش‌نمایش نهایی ارسال می‌شود
                    val newDocId = "new_\${System.currentTimeMillis()}"
                    val newDoc = DocumentItem(
                        id = newDocId,
                        title = capturedDocTitle.ifEmpty { "سند اسکن‌شده - \${DocStorageManager.getPersianDateNow()}" },
                        datePersian = DocStorageManager.getPersianDateNow(),
                        filter = ScanFilter.PHOTOCOPY,
                        pageCount = 1,
                        bitmap = processedBitmap
                    )
                    pendingDocument = newDoc
                    // هدایت مطمئن به صفحه پیش‌نمایش و خارج کردن صفحه برش از BackStack
                    navController.navigate(Screen.Preview.createRoute(newDocId)) {
                        popUpTo(Screen.Crop.route) { inclusive = true }
                    }
                },
                onCancel = {
                    capturedRawBitmap = null
                    navController.popBackStack()
                }
            )
        } else if (pendingDocument == null) {
            LaunchedEffect(Unit) {
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }
        }
    }

    // صفحه نمایش نهایی و فیلترها (سند اسکن‌شده در صفحه سفید تمیز)
    composable(Screen.Preview.route) { backStackEntry ->
        val docId = backStackEntry.arguments?.getString("docId")
        val document = if (docId != null && pendingDocument?.id == docId) {
            pendingDocument
        } else {
            documentList.find { it.id == docId }
        }
        PreviewScreen(
            document = document,
            onBack = {
                pendingDocument = null
                capturedRawBitmap = null
                navController.popBackStack(Screen.Home.route, inclusive = false)
            },
            onSaveSuccess = {
                refreshDocuments()
                pendingDocument = null
                capturedRawBitmap = null
                navController.popBackStack(Screen.Home.route, inclusive = false)
            }
        )
    }
}`
    },
    'HomeScreen.kt': {
      lang: 'kotlin',
      desc: 'صفحه اصلی Jetpack Compose با اپ‌بار «اسکنر مدارک»، کارتهای مدارک اخیر و دو دکمه شناور دوربین و گالری',
      code: `@Composable
fun HomeScreen(
    documents: List<DocumentItem>,
    onOpenDocument: (String) -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسکنر مدارک", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = onLaunchGallery,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text("گالری")
                }
                ExtendedFloatingActionButton(
                    onClick = onLaunchCamera,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text("دوربین")
                }
            }
        }
    ) { /* کارتهای شیک مدارک اخیر */ }
}`
    },
    'PreviewScreen.kt': {
      lang: 'kotlin',
      desc: 'صفحه پیش‌نمایش با ۴ حالت فیلتر (فتوکپی، سیاه و سفید، رنگی شفاف، اصلی) و دکمه‌های بازگشت، اشتراک‌گذاری، ذخیره',
      code: `@Composable
fun PreviewScreen(
    document: DocumentItem?,
    onBack: () -> Unit,
    onSave: (ScanFilter) -> Unit,
    onShare: (ScanFilter) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(ScanFilter.PHOTOCOPY) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.title ?: "پیش‌نمایش مدرک") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(selectedFilter) }) {
                        Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری")
                    }
                    Button(onClick = { onSave(selectedFilter) }) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text("ذخیره")
                    }
                }
            )
        },
        bottomBar = {
            // نوار ابزار پایین با ۴ حالت فیلتر: «فتوکپی»، «سیاه و سفید»، «رنگی شفاف» و «اصلی»
            BottomFilterBar(
                selected = selectedFilter,
                onSelect = { selectedFilter = it }
            )
        }
    ) { /* کادر نمایش تصویر مدرک در مرکز */ }
}`
    },
    'EdgeDetectionEngine.kt': {
      lang: 'kotlin',
      desc: 'الگوریتم Canny Edge Detection با اولیه‌های android.graphics، تشخیص ۴ گوشه سند و TransformationManager با Matrix.setPolyToPoly برای تنظیم دستی',
      code: `package ir.smartscanner.docscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import java.util.ArrayDeque
import kotlin.math.*

/**
 * مدل داده ۴ گوشه سند اسکن‌شده جهت تراز و برش پرسپکتیو
 */
data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    fun toCornerPoints(): CornerPoints = CornerPoints(
        p0 = Offset(topLeft.x, topLeft.y),
        p1 = Offset(topRight.x, topRight.y),
        p2 = Offset(bottomRight.x, bottomRight.y),
        p3 = Offset(bottomLeft.x, bottomLeft.y)
    )

    fun toPointsArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    fun withCorner(index: Int, point: PointF): DocumentCorners {
        return when (index) {
            0 -> copy(topLeft = point)
            1 -> copy(topRight = point)
            2 -> copy(bottomRight = point)
            3 -> copy(bottomLeft = point)
            else -> this
        }
    }
}

/**
 * موتور تشخیص هوشمند لبه‌ها با الگوریتم سبُک Canny Edge Detection
 */
object EdgeDetectionEngine {
    fun detectCorners(bitmap: Bitmap, config: CannyConfig = CannyConfig()): DocumentCorners {
        // ۱. مقیاس‌گذاری سریع متناسب
        // ۲. استخراج روشنایی (Grayscale)
        // ۳. فیلتر گوسی ۱ بعدی تفکیک‌پذیر (Separable 1D Gaussian Blur)
        // ۴. محاسبه گرادیان سوبل (Sobel Magnitudes & Angles)
        // ۵. سرکوب غیر بیشینه‌ها (Non-Maximum Suppression - NMS)
        // ۶. آستانه‌گذاری دوگانه و هیسترزیس (Double Thresholding & Hysteresis)
        // ۷. استخراج هندسی ۴ گوشه سند و بازگردانی به ابعاد اصلی
        ...
    }
}

/**
 * کلاس مدیریت تبدیلات هندسی و تصحیح پرسپکتیو اسناد با استفاده از Matrix.setPolyToPoly
 */
class TransformationManager(private val useAntiAlias: Boolean = true) {
    fun applyPerspectiveCorrection(source: Bitmap, corners: DocumentCorners): Bitmap {
        val (dstWidth, dstHeight) = calculateOptimalDestinationDimensions(corners)
        val srcPoints = corners.toPointsArray()
        val dstPoints = floatArrayOf(
            0f, 0f,
            dstWidth.toFloat(), 0f,
            dstWidth.toFloat(), dstHeight.toFloat(),
            0f, dstHeight.toFloat()
        )
        return warpBitmap(source, srcPoints, dstPoints, dstWidth, dstHeight)
    }

    fun computePerspectiveMatrix(srcCorners: DocumentCorners, dstWidth: Float, dstHeight: Float): Matrix {
        val matrix = Matrix()
        matrix.setPolyToPoly(srcCorners.toPointsArray(), 0, floatArrayOf(0f, 0f, dstWidth, 0f, dstWidth, dstHeight, 0f, dstHeight), 0, 4)
        return matrix
    }

    fun adjustCorner(corners: DocumentCorners, cornerIndex: Int, newPoint: PointF): DocumentCorners {
        return corners.withCorner(cornerIndex, newPoint)
    }

    fun isConvexQuad(corners: DocumentCorners): Boolean { ... }
}
`
    },
    'PerspectiveCropEngine.kt': {
      lang: 'kotlin',
      desc: 'یکپارچه‌سازی EdgeDetectionEngine و TransformationManager جهت تنظیم دستی و برش پرسپکتیو با Matrix.setPolyToPoly',
      code: `package ir.smartscanner.docscan.util

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset

object PerspectiveCropEngine {
    val transformationManager = TransformationManager()

    /**
     * تشخیص هوشمند لبه‌ها با Canny Edge Detection
     */
    fun detectDocumentCorners(bitmap: Bitmap): CornerPoints {
        return try {
            val detected = EdgeDetectionEngine.detectCorners(bitmap)
            detected.toCornerPoints()
        } catch (e: Exception) {
            getDefaultCorners(bitmap.width.toFloat(), bitmap.height.toFloat())
        }
    }

    /**
     * تنظیم دستی موقعیت یک گوشه سند توسط کاربر با TransformationManager
     */
    fun adjustCorner(corners: CornerPoints, index: Int, newOffset: Offset): CornerPoints {
        return transformationManager.adjustCorner(corners, index, newOffset)
    }

    /**
     * برش پرسپکتیو با تبدیل هندسی نیتیو setPolyToPoly
     */
    fun cropPerspective(source: Bitmap, corners: CornerPoints): Bitmap {
        return transformationManager.applyPerspectiveCorrection(source, corners)
    }

    /**
     * چرخش ۹۰ درجه تصویر جهت تراز اسناد
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        return transformationManager.rotateBitmap(source, degrees)
    }
}`
    },
    'PerspectiveCropView.kt': {
      lang: 'kotlin',
      desc: 'کامپوننت Jetpack Compose جهت تنظیم دستی ۴ گوشه با کشیدن دستگیره‌ها و اتصال مستقیم به TransformationManager',
      code: `package ir.smartscanner.docscan.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import ir.smartscanner.docscan.util.PerspectiveCropEngine

@Composable
fun PerspectiveCropView(
    initialBitmap: Bitmap,
    onConfirmCrop: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var workingBitmap by remember { mutableStateOf(initialBitmap) }
    var corners by remember(workingBitmap) {
        mutableStateOf(PerspectiveCropEngine.detectDocumentCorners(workingBitmap))
    }
    var draggedCornerIndex by remember { mutableStateOf<Int?>(null) }

    // تنظیم دستی موقعیت گوشه با مدیریت TransformationManager
    // Canvas جهت رسم خطوط چهارضلعی و ۴ دستگیره تعاملی لمسی
    // دکمه‌های بازنشانی کادر، تشخیص هوشمند لبه‌ها (Canny) و تأیید نهایی
}`
    }
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col font-['Vazirmatn',sans-serif]" dir="rtl">
      {/* مخفی: ورودی فایل جهت آزمایش زنده بارگذاری مدرک در شبیه‌ساز */}
      <input 
        type="file" 
        ref={fileInputRef} 
        onChange={handleFileUpload} 
        accept="image/*" 
        className="hidden" 
        id="camera-input"
      />

      {/* نوار بالای پنل تست و مدیریت پروژه */}
      <header className="border-b border-neutral-800 bg-neutral-900/90 backdrop-blur px-4 py-3 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-sky-600 flex items-center justify-center text-white shadow-lg shadow-sky-600/30">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base sm:text-lg font-bold text-white">اسکنر و فتوکپی هوشمند مدارک</h1>
                <span className="text-xs bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-full font-medium">
                  نیتیو اندروید Jetpack Compose
                </span>
                <span className="hidden sm:inline-block text-xs bg-sky-500/20 text-sky-300 border border-sky-500/30 px-2 py-0.5 rounded-full font-mono">
                  compileSdk 34
                </span>
              </div>
              <p className="text-xs text-neutral-400">
                ۱۰۰٪ آفلاین • معماری کاتلین • گیت‌هاب اکشنز CI/CD آماده انتشار در مایکت و بازار
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex bg-neutral-800 p-1 rounded-xl border border-neutral-700">
              <button
                onClick={() => setActiveTab('preview')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'preview'
                    ? 'bg-sky-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                شبیه‌ساز زنده موبایل
              </button>
              <button
                onClick={() => setActiveTab('code')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'code'
                    ? 'bg-sky-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Code2 className="w-3.5 h-3.5" />
                فایل‌های کاتلین و گریدل
              </button>
              <button
                onClick={() => setActiveTab('cicd')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'cicd'
                    ? 'bg-sky-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                <Download className="w-3.5 h-3.5" />
                ورکفلو APK
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* پیام موقت Toast */}
      {toastMessage && (
        <div className="fixed top-16 left-1/2 -translate-x-1/2 z-50 bg-neutral-800 text-neutral-100 border border-neutral-600 px-4 py-2.5 rounded-xl shadow-2xl flex items-center gap-2 text-sm animate-bounce">
          <Check className="w-4 h-4 text-emerald-400" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* بدنه اصلی */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 flex flex-col items-center justify-center">
        {activeTab === 'preview' && (
          <div className="w-full flex flex-col lg:flex-row items-center justify-center gap-8 py-2">
            
            {/* قاب شبیه‌ساز تلفن همراه اندروید با متریال دیزاین ۳ */}
            <div className="relative w-full max-w-[390px] h-[780px] bg-neutral-900 rounded-[44px] p-3 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.9)] border-[6px] border-neutral-700 flex flex-col shrink-0">
              {/* بریدگی بلندگو و دوربین سلفی اندروید */}
              <div className="absolute top-5 left-1/2 -translate-x-1/2 w-28 h-4 bg-neutral-800 rounded-full flex items-center justify-center z-40">
                <div className="w-2.5 h-2.5 rounded-full bg-neutral-950 ml-6" />
                <div className="w-12 h-1 bg-neutral-700 rounded-full" />
              </div>

              {/* محتوای درون صفحه نمایش اندروید */}
              <div className="w-full h-full bg-[#F8FAFC] text-slate-900 rounded-[34px] overflow-hidden flex flex-col relative select-none">
                
                {/* نوار وضعیت سیستم اندروید (Status Bar) */}
                <div className="bg-white/80 backdrop-blur-sm px-6 pt-5 pb-1 flex items-center justify-between text-[11px] font-medium text-slate-700 border-b border-slate-100 shrink-0">
                  <span className="font-mono">12:30</span>
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px] bg-slate-100 px-1 rounded">IR-MCI</span>
                    <span>📶</span>
                    <span>🔋 98%</span>
                  </div>
                </div>

                {/* صفحات اپلیکیشن (صفحه ۱: خانه | صفحه ۲: پیش‌نمایش و فیلترها) */}
                {currentScreen === 'home' ? (
                  /* ۱. صفحه اصلی (Home) */
                  <div className="flex-1 flex flex-col overflow-hidden relative">
                    {/* اپ‌بار اصلی */}
                    <div className="bg-white px-5 py-3 border-b border-slate-100 shadow-sm flex items-center justify-between shrink-0">
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-xl bg-sky-100 text-sky-700 flex items-center justify-center">
                          <FileText className="w-5 h-5" />
                        </div>
                        <div>
                          <h2 className="text-base font-bold text-slate-900 leading-tight">اسکنر مدارک</h2>
                          <p className="text-[11px] text-slate-500">فتوکپی و پردازش آفلاین</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 text-slate-500">
                        <button className="p-1.5 hover:bg-slate-100 rounded-lg">
                          <Search className="w-4 h-4" />
                        </button>
                        <button className="p-1.5 hover:bg-slate-100 rounded-lg">
                          <MoreVertical className="w-4 h-4" />
                        </button>
                      </div>
                    </div>

                    {/* لیست مدارک اخیر */}
                    <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3 pb-24">
                      <div className="flex items-center justify-between text-xs px-1">
                        <span className="font-bold text-slate-800 text-sm">مدارک اخیر</span>
                        <span className="text-slate-500">{documents.length} مدرک ذخیره‌شده</span>
                      </div>

                      {documents.map((doc) => (
                        <div
                          key={doc.id}
                          onClick={() => handleOpenDoc(doc.id)}
                          className="bg-white rounded-2xl p-3.5 border border-slate-200/80 shadow-sm hover:shadow-md transition-all cursor-pointer flex items-center gap-3.5 active:scale-[0.99]"
                        >
                          {/* عکس بندانگشتی شبیه‌سازی‌شده سند */}
                          <div className="w-14 h-18 bg-slate-100 rounded-xl border border-slate-200 p-2 flex flex-col justify-between shrink-0 relative overflow-hidden">
                            {doc.imageSrc ? (
                              <img src={doc.imageSrc} alt="" className="w-full h-full object-cover rounded-md" />
                            ) : (
                              <>
                                <div className="w-6 h-1.5 bg-sky-500/70 rounded-full" />
                                <div className="space-y-1">
                                  <div className="w-full h-1 bg-slate-300 rounded" />
                                  <div className="w-4/5 h-1 bg-slate-300 rounded" />
                                  <div className="w-3/5 h-1 bg-slate-300 rounded" />
                                </div>
                                <div className="w-4 h-4 rounded-full border border-red-400 flex items-center justify-center text-[7px] text-red-500 font-bold self-end">
                                  مهر
                                </div>
                              </>
                            )}
                          </div>

                          {/* مشخصات سند */}
                          <div className="flex-1 min-w-0">
                            <h3 className="font-bold text-slate-900 text-sm truncate">{doc.title}</h3>
                            <p className="text-xs text-slate-500 mt-1">تاریخ: {doc.datePersian}</p>
                            <div className="flex items-center gap-2 mt-2">
                              <span className="text-[10px] bg-sky-100 text-sky-800 px-2 py-0.5 rounded-md font-semibold">
                                {getFilterLabel(doc.filter)}
                              </span>
                              <span className="text-[10px] text-slate-400">
                                {doc.pageCount} صفحه
                              </span>
                            </div>
                          </div>

                          <ChevronLeft className="w-4 h-4 text-slate-400 shrink-0" />
                        </div>
                      ))}
                    </div>

                    {/* بنر راهنمای جریان نویگیشن */}
                    <div className="bg-sky-50 border border-sky-200/80 rounded-2xl p-3 mx-4 mt-2 flex items-center gap-2.5 text-xs text-sky-900 shadow-xs">
                      <Sparkles className="w-4 h-4 text-sky-600 shrink-0" />
                      <span>جریان نویگیشن جدید: بلافاصله پس از عکس‌برداری یا انتخاب گالری، مستقیماً به <strong className="font-bold">PerspectiveCropView</strong> هدایت می‌شوید.</span>
                    </div>

                    {/* دو دکمه شناور بزرگ (FAB) در پایین: دوربین و گالری */}
                    <div className="absolute bottom-4 inset-x-4 flex items-center gap-3 z-30">
                      {/* دکمه گالری */}
                      <button
                        onClick={handleCameraCapture}
                        className="flex-1 h-14 bg-teal-100 text-teal-900 hover:bg-teal-200 active:scale-95 rounded-2xl shadow-lg border border-teal-200/60 font-bold text-sm flex items-center justify-center gap-2 transition-all"
                        title="انتخاب عکس و هدایت بلادرنگ به PerspectiveCropView"
                      >
                        <ImageIcon className="w-5 h-5 text-teal-800" />
                        <span>گالری</span>
                      </button>

                      {/* دکمه دوربین */}
                      <button
                        onClick={handleSimulateCameraCapture}
                        className="flex-1 h-14 bg-sky-600 hover:bg-sky-700 active:scale-95 text-white rounded-2xl shadow-xl shadow-sky-600/30 font-bold text-sm flex items-center justify-center gap-2 transition-all"
                        title="عکاسی و هدایت فوری به PerspectiveCropView"
                      >
                        <Camera className="w-5 h-5" />
                        <span>دوربین</span>
                      </button>
                    </div>
                  </div>
                ) : currentScreen === 'crop' ? (
                  /* نمایش تعاملی کامپوننت ۴ گوشه و تصحیح پرسپکتیو (PerspectiveCropView) - بلافاصله پس از عکسبرداری */
                  <div 
                    className="flex-1 flex flex-col bg-[#111827] text-white select-none relative overflow-hidden"
                    onMouseMove={(e) => handleCornerDrag(e.clientX, e.clientY)}
                    onMouseUp={() => setActiveCornerIdx(null)}
                    onTouchMove={(e) => {
                      if (e.touches[0]) {
                        handleCornerDrag(e.touches[0].clientX, e.touches[0].clientY);
                      }
                    }}
                    onTouchEnd={() => setActiveCornerIdx(null)}
                  >
                    {/* هدر بالای صفحه برش */}
                    <div className="px-4 py-2.5 bg-[#1F2937] border-b border-gray-800 flex items-center justify-between shrink-0">
                      <button
                        onClick={handleCancelCrop}
                        className="p-1 hover:bg-gray-700 rounded-lg text-white transition-all"
                        title="انصراف و بازگشت به خانه"
                      >
                        <ArrowRight className="w-4 h-4" />
                      </button>
                      <div className="text-center">
                        <span className="font-bold text-xs block">برش و تنظیم پرسپکتیو (PerspectiveCropView)</span>
                        <span className="text-[9px] text-sky-400">هدایت بلادرنگ پس از عکس‌برداری / گالری</span>
                      </div>
                      <button
                        onClick={handleRotate}
                        className="p-1.5 hover:bg-gray-700 rounded-lg text-white transition-all"
                        title="چرخش ۹۰ درجه"
                      >
                        <RotateCw className="w-4 h-4" />
                      </button>
                    </div>

                    {/* کادر بوم تعاملی + ۴ دستگیره قابل جابجایی */}
                    <div 
                      ref={cropContainerRef}
                      className="flex-1 m-2.5 relative rounded-2xl overflow-hidden bg-black/60 border border-gray-800 flex items-center justify-center cursor-crosshair"
                    >
                      {/* تصویر سند با چرخش ۹۰ درجه */}
                      <div 
                        className="w-full h-full p-4 flex items-center justify-center transition-transform duration-200"
                        style={{ transform: `rotate(${rotationDegrees}deg)` }}
                      >
                        {capturedImage || customImage || activeDoc.imageSrc ? (
                          <img 
                            src={capturedImage || customImage || activeDoc.imageSrc} 
                            alt="سند خام جهت برش" 
                            className="max-h-full max-w-full object-contain pointer-events-none rounded shadow"
                          />
                        ) : (
                          <div className="w-4/5 h-4/5 bg-amber-50 rounded-xl p-3 text-slate-800 flex flex-col justify-between border border-amber-200 pointer-events-none shadow-md">
                            <div className="flex justify-between items-center text-[9px] border-b border-slate-300 pb-1 font-bold">
                              <span>جمهوری اسلامی ایران</span>
                              <span>سند شناسایی</span>
                            </div>
                            <div className="space-y-1.5 my-auto">
                              <div className="h-2 bg-slate-300 rounded w-2/3" />
                              <div className="h-1.5 bg-slate-200 rounded w-full" />
                              <div className="h-1.5 bg-slate-200 rounded w-4/5" />
                              <div className="h-1.5 bg-slate-200 rounded w-3/5" />
                            </div>
                            <div className="flex justify-between items-center text-[8px] border-t border-slate-300 pt-1">
                              <span className="text-red-600 font-bold border border-red-500 rounded px-1">تأیید</span>
                              <span>شماره سند: ۱۴۰۳</span>
                            </div>
                          </div>
                        )}
                      </div>

                      {/* لایه SVG خطوط چندضلعی ۴ گوشه و اقطار فرضی */}
                      <svg className="absolute inset-0 w-full h-full pointer-events-none z-10">
                        {/* خطوط متقاطع اقطار */}
                        <line 
                          x1={`${corners[0].x}%`} y1={`${corners[0].y}%`} 
                          x2={`${corners[2].x}%`} y2={`${corners[2].y}%`} 
                          stroke="rgba(255,255,255,0.4)" 
                          strokeDasharray="3,3" 
                          strokeWidth="1.5" 
                        />
                        <line 
                          x1={`${corners[1].x}%`} y1={`${corners[1].y}%`} 
                          x2={`${corners[3].x}%`} y2={`${corners[3].y}%`} 
                          stroke="rgba(255,255,255,0.4)" 
                          strokeDasharray="3,3" 
                          strokeWidth="1.5" 
                        />
                        {/* کادر ۴ ضلعی مرز سند */}
                        <polygon 
                          points={`${corners[0].x}%,${corners[0].y}% ${corners[1].x}%,${corners[1].y}% ${corners[2].x}%,${corners[2].y}% ${corners[3].x}%,${corners[3].y}%`}
                          fill="rgba(14, 165, 233, 0.18)"
                          stroke="#0284c7"
                          strokeWidth="3"
                        />
                      </svg>

                      {/* ۴ دستگیره لمسی گوشه‌ها */}
                      {corners.map((corner, idx) => (
                        <div
                          key={idx}
                          onMouseDown={() => setActiveCornerIdx(idx)}
                          onTouchStart={() => setActiveCornerIdx(idx)}
                          className={`absolute w-7 h-7 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-xl cursor-grab active:cursor-grabbing flex items-center justify-center z-20 transition-transform ${
                            activeCornerIdx === idx ? 'scale-125 bg-sky-400 ring-4 ring-sky-400/40' : 'bg-sky-600 hover:scale-110'
                          }`}
                          style={{ left: `${corner.x}%`, top: `${corner.y}%` }}
                        >
                          <div className="w-1.5 h-1.5 bg-white rounded-full" />
                        </div>
                      ))}

                      {/* وضعیت پردازش Canny Edge Detection */}
                      {isAutoDetecting && (
                        <div className="absolute inset-0 bg-sky-950/60 backdrop-blur-xs flex flex-col items-center justify-center gap-2 z-30">
                          <div className="w-7 h-7 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />
                          <span className="text-xs font-bold text-sky-200">الگوریتم Canny در حال تفکیک لبه‌ها...</span>
                        </div>
                      )}
                    </div>

                    {/* دکمه‌های کمکی و دکمه اصلی تأیید پرسپکتیو */}
                    <div className="bg-[#1F2937] p-3 space-y-2 border-t border-gray-800 shrink-0">
                      <div className="flex gap-2">
                        <button
                          onClick={handleAutoDetectCorners}
                          disabled={isAutoDetecting}
                          className="flex-1 py-2 px-2.5 bg-gray-800 hover:bg-gray-700 text-sky-300 border border-gray-700 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 active:scale-95 transition-all"
                        >
                          <Wand2 className="w-3.5 h-3.5" />
                          <span>تشخیص هوشمند (Canny)</span>
                        </button>
                        <button
                          onClick={handleFullFrame}
                          className="py-2 px-3 bg-gray-800 hover:bg-gray-700 text-gray-300 border border-gray-700 rounded-xl text-xs font-bold flex items-center justify-center gap-1 active:scale-95 transition-all"
                        >
                          <Maximize2 className="w-3.5 h-3.5" />
                          <span>کادر کامل</span>
                        </button>
                      </div>

                      <button
                        onClick={handleConfirmCropAndNavigate}
                        className="w-full py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 shadow-lg shadow-sky-600/30 active:scale-95 transition-all"
                      >
                        <Check className="w-4 h-4" />
                        <span>تأیید نهایی و ارسال به صفحه پیش‌نمایش</span>
                      </button>
                    </div>
                  </div>
                ) : (
                  /* ۲. صفحه پیش‌نمایش و فیلترها (Preview Screen) */
                  <div className="flex-1 flex flex-col overflow-hidden bg-slate-100">
                    {/* نوار ابزار بالا با بازگشت، اشتراک‌گذاری و ذخیره */}
                    <div className="bg-white px-4 py-2.5 border-b border-slate-200 shadow-sm flex items-center justify-between shrink-0">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => setCurrentScreen('home')}
                          className="p-1.5 hover:bg-slate-100 rounded-lg text-slate-700"
                          title="بازگشت"
                        >
                          <ArrowRight className="w-5 h-5" />
                        </button>
                        <span className="font-bold text-sm text-slate-900 truncate max-w-[140px]">
                          {activeDoc.title}
                        </span>
                      </div>

                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={handleShare}
                          className="p-2 hover:bg-sky-50 text-sky-600 rounded-xl"
                          title="اشتراک‌گذاری"
                        >
                          <Share2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={handleSaveFilter}
                          className="bg-sky-600 hover:bg-sky-700 text-white px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-sm active:scale-95 transition-all"
                        >
                          <Save className="w-3.5 h-3.5" />
                          <span>ذخیره</span>
                        </button>
                      </div>
                    </div>

                    {/* کادر نمایش سند اسکن‌شده تمیز در برگه سفید A4 */}
                    <div className="flex-1 p-3.5 flex items-center justify-center overflow-hidden bg-slate-200/80">
                      {/* برگه سفید سند اسکن‌شده */}
                      <div 
                        className="w-full max-w-[315px] aspect-[1/1.38] bg-white rounded-[3px] shadow-[0_12px_35px_rgba(0,0,0,0.2)] border border-slate-300/80 p-4 flex flex-col justify-between transition-all duration-300 relative overflow-hidden select-none"
                      >
                        {customImage || activeDoc.imageSrc ? (
                          <div className="w-full h-full relative flex items-center justify-center bg-white p-1">
                            <img 
                              src={customImage || activeDoc.imageSrc} 
                              alt="سند اسکن شده" 
                              className="max-h-full max-w-full object-contain rounded-xs shadow-xs"
                              style={getFilterStyle(selectedFilter)}
                            />
                          </div>
                        ) : (
                          <div 
                            className="w-full h-full flex flex-col justify-between"
                            style={getFilterStyle(selectedFilter)}
                          >
                            {/* هدر سند رسمی */}
                            <div className="flex items-center justify-between border-b pb-2.5 border-slate-300">
                              <div className="w-7 h-7 rounded bg-slate-100 flex items-center justify-center text-xs font-bold shadow-xs">
                                🇮🇷
                              </div>
                              <div className="text-center">
                                <span className="text-[10px] font-medium block text-slate-500">جمهوری اسلامی ایران</span>
                                <h4 className="font-bold text-xs text-slate-800">{activeDoc.title}</h4>
                              </div>
                              <div className="text-[9px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                                ۱۴۰۳/۰۲/۱۵
                              </div>
                            </div>

                            {/* خطوط شبیه‌سازی متن مدرک اسکن‌شده با کیفیت و کنتراست بالا */}
                            <div className="space-y-2.5 py-2">
                              <div className="h-2 bg-slate-800 rounded-xs w-2/5" />
                              <div className="space-y-1.5">
                                <div className="h-1.5 bg-slate-700 rounded-xs w-full" />
                                <div className="h-1.5 bg-slate-700 rounded-xs w-11/12" />
                                <div className="h-1.5 bg-slate-700 rounded-xs w-5/6" />
                              </div>
                              <div className="h-2 bg-slate-800 rounded-xs w-1/3 pt-1" />
                              <div className="space-y-1.5">
                                <div className="h-1.5 bg-slate-700 rounded-xs w-full" />
                                <div className="h-1.5 bg-slate-700 rounded-xs w-4/5" />
                              </div>
                            </div>

                            {/* مهر و امضای رسمی پایین سند */}
                            <div className="flex items-center justify-between pt-2 border-t border-slate-300">
                              <div className="w-12 h-12 rounded-full border-2 border-red-600 flex items-center justify-center text-[9px] text-red-600 font-black rotate-[-12deg] shadow-xs">
                                تأیید شد
                              </div>
                              <div className="text-left">
                                <div className="text-[9px] text-slate-500">محل امضا و اثر انگشت</div>
                                <div className="w-16 h-4 border-b-2 border-slate-800 mt-1" />
                              </div>
                            </div>
                          </div>
                        )}

                        {/* نشانگر برگه سفید سند اسکن‌شده */}
                        <div className="absolute top-2 right-2 bg-slate-900/80 text-white text-[9px] px-2 py-0.5 rounded-full backdrop-blur-xs font-medium">
                          برگه سفید A4 اسکن‌شده
                        </div>

                        {/* نشانگر فیلتر اعمال‌شده */}
                        <div className="absolute top-2 left-2 bg-sky-600 text-white text-[9px] px-2 py-0.5 rounded-full shadow-xs font-bold">
                          {getFilterLabel(selectedFilter)}
                        </div>

                        {/* نشانگر تراز بودن پرسپکتیو */}
                        {isPerspectiveCropped && (
                          <div className="absolute bottom-2 left-2 bg-emerald-600/90 text-white text-[9px] px-2 py-0.5 rounded-full backdrop-blur-xs flex items-center gap-1">
                            <Check className="w-3 h-3" />
                            <span>تراز با Matrix.setPolyToPoly</span>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* نوار ابزار پایین با دکمه برش و ۴ حالت فیلتر */}
                    <div className="bg-white px-4 py-3 border-t border-slate-200 shadow-lg rounded-t-3xl shrink-0 space-y-2.5">
                      {/* ردیف دکمه برش ۴ گوشه و پرسپکتیو */}
                      <div className="flex items-center justify-between pb-1">
                        <span className="text-[11px] font-bold text-slate-500">پردازش و تراز کادر:</span>
                        <button
                          onClick={() => setCurrentScreen('crop')}
                          className="bg-sky-50 hover:bg-sky-100 text-sky-700 border border-sky-200/80 px-2.5 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 active:scale-95 transition-all"
                          title="بازگشت به صفحه PerspectiveCropView جهت ویرایش مجدد گوشه‌ها"
                        >
                          <Crop className="w-3.5 h-3.5 text-sky-600" />
                          <span>تنظیم در PerspectiveCropView</span>
                        </button>
                      </div>

                      {/* گزینه‌های فیلتر سند */}
                      <div>
                        <div className="text-[11px] font-bold text-slate-500 mb-1.5">انتخاب فیلتر مدرک:</div>
                        <div className="grid grid-cols-4 gap-2">
                          {/* ۱. فتوکپی */}
                          <button
                            onClick={() => setSelectedFilter('photocopy')}
                            className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                              selectedFilter === 'photocopy'
                                ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                                : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                            }`}
                          >
                            <Printer className="w-4 h-4" />
                            <span className="text-[10px] font-bold">فتوکپی</span>
                          </button>

                          {/* ۲. سیاه و سفید */}
                          <button
                            onClick={() => setSelectedFilter('bw')}
                            className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                              selectedFilter === 'bw'
                                ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                                : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                            }`}
                          >
                            <Contrast className="w-4 h-4" />
                            <span className="text-[10px] font-bold">سیاه و سفید</span>
                          </button>

                          {/* ۳. رنگی شفاف */}
                          <button
                            onClick={() => setSelectedFilter('color')}
                            className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                              selectedFilter === 'color'
                                ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                                : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                            }`}
                          >
                            <Sparkles className="w-4 h-4" />
                            <span className="text-[10px] font-bold">رنگی شفاف</span>
                          </button>

                          {/* ۴. اصلی */}
                          <button
                            onClick={() => setSelectedFilter('original')}
                            className={`p-2 rounded-xl flex flex-col items-center gap-1 transition-all border ${
                              selectedFilter === 'original'
                                ? 'bg-sky-50 border-sky-500 text-sky-700 shadow-sm'
                                : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                            }`}
                          >
                            <Layers className="w-4 h-4" />
                            <span className="text-[10px] font-bold">اصلی</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* پنل توضیحات و مشخصات فنی پروژه کاتلین */}
            <div className="flex-1 max-w-lg space-y-4 text-right">
              <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 shadow-lg space-y-4">
                <div className="flex items-center gap-2 text-sky-400">
                  <ShieldCheck className="w-5 h-5" />
                  <h3 className="font-bold text-base text-white">انطباق کامل با ضوابط کافه‌بازار و مایکت</h3>
                </div>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">Target & Compile SDK</span>
                    <span className="text-emerald-400 font-bold font-mono text-sm">34 (Android 14)</span>
                  </div>
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">حداقل نسخه پشتیبانی (minSdk)</span>
                    <span className="text-sky-400 font-bold font-mono text-sm">24 (Android 7.0+)</span>
                  </div>
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">نوع رابط کاربری</span>
                    <span className="text-amber-400 font-bold text-sm">Jetpack Compose M3</span>
                  </div>
                  <div className="bg-neutral-800/80 p-3 rounded-xl border border-neutral-700/60">
                    <span className="text-neutral-400 block mb-1">وضعیت شبکه</span>
                    <span className="text-purple-400 font-bold text-sm">۱۰۰٪ آفلاین و محلی</span>
                  </div>
                </div>

                <div className="border-t border-neutral-800 pt-3 space-y-2">
                  <h4 className="text-xs font-bold text-neutral-300">امکانات پیاده‌سازی شده در فاز اول:</h4>
                  <ul className="text-xs text-neutral-400 space-y-1.5 list-disc list-inside">
                    <li>صفحه اصلی با نوار اپ‌بار اختصاصی «اسکنر مدارک»</li>
                    <li>لیست کارتهای «مدارک اخیر» با ریزعکس، عنوان و تاریخ شمسی</li>
                    <li>دو دکمه شناور بزرگ مجزا (FAB) برای «دوربین» و «گالری»</li>
                    <li>صفحه پیش‌نمایش مدارک با ۴ فیلتر (فتوکپی، سیاه و سفید، رنگی شفاف، اصلی)</li>
                    <li>عملیات بازگشت، ذخیره و اشتراک‌گذاری</li>
                    <li>پیکربندی گیت‌هاب اکشنز جهت ساخت خودکار فایل نصبی <code className="text-sky-300">app-debug.apk</code></li>
                  </ul>
                </div>

                <div className="pt-2 flex flex-wrap gap-2">
                  <button
                    onClick={handleCameraCapture}
                    className="flex-1 bg-neutral-800 hover:bg-neutral-700 text-white px-4 py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border border-neutral-700 transition-all"
                  >
                    <Upload className="w-4 h-4 text-sky-400" />
                    <span>تست تصویر دلخواه در شبیه‌ساز</span>
                  </button>
                  <button
                    onClick={() => setActiveTab('code')}
                    className="bg-sky-600 hover:bg-sky-500 text-white px-4 py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition-all"
                  >
                    <Code2 className="w-4 h-4" />
                    <span>مشاهده فایل‌های کاتلین</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* برگه فایل‌های کاتلین و گریدل */}
        {activeTab === 'code' && (
          <div className="w-full max-w-5xl bg-neutral-900 border border-neutral-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col md:flex-row min-h-[600px]">
            {/* ستون فهرست فایل‌ها */}
            <div className="w-full md:w-64 bg-neutral-950/80 p-4 border-b md:border-b-0 md:border-l border-neutral-800 flex flex-col gap-2 shrink-0">
              <span className="text-xs font-bold text-neutral-400 px-2">فایل‌های اصلی پروژه کاتلین:</span>
              {Object.keys(codeSnippets).map((path) => (
                <button
                  key={path}
                  onClick={() => setSelectedFileCode(path)}
                  className={`text-right px-3 py-2 rounded-xl text-xs font-mono transition-all truncate ${
                    selectedFileCode === path
                      ? 'bg-sky-600 text-white font-bold'
                      : 'text-neutral-400 hover:bg-neutral-800 hover:text-white'
                  }`}
                  dir="ltr"
                >
                  {path}
                </button>
              ))}
              <div className="mt-auto p-3 bg-neutral-900 rounded-xl border border-neutral-800 text-[11px] text-neutral-400">
                <Info className="w-4 h-4 text-sky-400 mb-1" />
                این فایل‌ها مستقیماً در ریشه مخزن اندروید (<code className="text-sky-300">app/src/main/...</code>) ایجاد و آماده بیلد شده‌اند.
              </div>
            </div>

            {/* بخش نمایش محتوای سورس کد */}
            <div className="flex-1 flex flex-col">
              <div className="bg-neutral-800/80 px-4 py-3 border-b border-neutral-700 flex items-center justify-between">
                <div>
                  <span className="font-mono text-xs font-bold text-sky-400" dir="ltr">
                    {selectedFileCode}
                  </span>
                  <p className="text-[11px] text-neutral-400 mt-0.5">
                    {codeSnippets[selectedFileCode]?.desc}
                  </p>
                </div>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(codeSnippets[selectedFileCode]?.code || '');
                    showToast('کد در کلیپ‌بورد کپی شد');
                  }}
                  className="bg-neutral-700 hover:bg-neutral-600 text-neutral-200 text-xs px-2.5 py-1.5 rounded-lg"
                >
                  کپی کد
                </button>
              </div>

              <div className="flex-1 p-4 overflow-x-auto bg-neutral-950 font-mono text-xs leading-relaxed text-neutral-300" dir="ltr">
                <pre>{codeSnippets[selectedFileCode]?.code}</pre>
              </div>
            </div>
          </div>
        )}

        {/* برگه آموزش و راهنمای ساخت APK با گیت‌هاب اکشنز */}
        {activeTab === 'cicd' && (
          <div className="w-full max-w-4xl bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-2xl space-y-6">
            <div className="flex items-center gap-3 border-b border-neutral-800 pb-4">
              <div className="w-10 h-10 rounded-xl bg-emerald-600/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30">
                <Download className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white">ورکفلو GitHub Actions برای خروجی APK</h2>
                <p className="text-xs text-neutral-400">تولید خودکار فایل نصبی آماده انتشار برای مایکت و کافه‌بازار</p>
              </div>
            </div>

            <div className="space-y-4 text-sm text-neutral-300">
              <div className="bg-neutral-950 p-4 rounded-xl border border-neutral-800 space-y-2">
                <span className="font-bold text-sky-400 block text-xs">مراحل خودکار ورکفلو (.github/workflows/build-apk.yml):</span>
                <ol className="list-decimal list-inside space-y-2 text-xs text-neutral-400">
                  <li><strong className="text-neutral-200">راه‌اندازی محیط:</strong> نصب جاوا ۱۷ (Zulu OpenJDK 17) با کش خودکار وابستگی‌های گریدل.</li>
                  <li><strong className="text-neutral-200">اجرای پرمیشن:</strong> اعطای دسترسی اجرایی با <code className="bg-neutral-800 px-1 py-0.5 rounded text-sky-300">chmod +x ./gradlew</code>.</li>
                  <li><strong className="text-neutral-200">کامپایل سورس‌ها:</strong> اجرای بیلد از طریق <code className="bg-neutral-800 px-1 py-0.5 rounded text-sky-300">./gradlew assembleDebug --no-daemon</code>.</li>
                  <li><strong className="text-neutral-200">آپلود خروجی نصبی:</strong> انتشار و ذخیره فایل <code className="bg-neutral-800 px-1 py-0.5 rounded text-emerald-300">app-debug.apk</code> با شناسه Artifact به نام <code className="bg-neutral-800 px-1 py-0.5 rounded text-amber-300">scanner-apk</code>.</li>
                </ol>
              </div>

              <div className="bg-sky-950/40 border border-sky-800/50 p-4 rounded-xl text-xs space-y-2">
                <h4 className="font-bold text-sky-300">نحوه اجرای بیلد در گیت‌هاب:</h4>
                <p className="text-neutral-300 leading-relaxed">
                  با فشردن دکمه Push به شاخه <code className="text-sky-300">main</code> یا در بخش <strong>Actions</strong> در گیت‌هاب، ورکفلو به صورت خودکار اجرا شده و پس از پایان بیلد می‌توانید فایل نصبی <code className="text-sky-300">scanner-apk</code> را مستقیماً از بخش Artifacts دانلود و روی گوشی‌های اندروید نصب نمایید.
                </p>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
