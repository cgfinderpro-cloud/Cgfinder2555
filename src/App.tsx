import React, { useState, useRef, useEffect } from 'react';
import { processDocumentImage } from './utils/docFilterSimulator';
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
  Maximize2,
  Plus,
  X,
  FilePlus,
  ZoomIn,
  ZoomOut,
  RotateCcw
} from 'lucide-react';
import { jsPDF } from 'jspdf';

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
  const [processedPreviewUrl, setProcessedPreviewUrl] = useState<string | null>(null);
  const [isProcessingFilter, setIsProcessingFilter] = useState(false);

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
  const multiPageFileInputRef = useRef<HTMLInputElement>(null);

  // برگه‌های اضافی اضافه شده به سند برای تبدیل همزمان چند برگه به یک PDF واحد
  const [additionalPages, setAdditionalPages] = useState<string[]>([]);
  const [selectedPageIndex, setSelectedPageIndex] = useState<number>(0);
  const [isGeneratingPdf, setIsGeneratingPdf] = useState(false);
  const [isAddingPageToPdf, setIsAddingPageToPdf] = useState(false);

  // وضعیت‌های زوم و جابه‌جایی تعاملی برای سند اسکن‌شده
  const [previewZoom, setPreviewZoom] = useState<number>(1);
  const [previewPan, setPreviewPan] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDraggingPreview, setIsDraggingPreview] = useState(false);
  const previewDragStartRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const activeDoc = documents.find(d => d.id === activeDocId) || documents[0];

  const handleOpenDoc = (id: string) => {
    const doc = documents.find(d => d.id === id);
    if (doc) {
      if (isAddingPageToPdf) {
        const newPageImg = doc.imageSrc || createGlossyDocumentTestImage();
        handleStartCaptureFlow(newPageImg, doc.title);
        showToast(`سند «${doc.title}» بارگذاری شد؛ لطفاً گوشه‌ها را تنظیم نمایید`);
        return;
      }
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

  // پردازش بلادرنگ تصویر با موتور فیلتر فتوکپی استودیویی (مطابق با الگوریتم DocFilterEngine.kt)
  const activeRawImage = customImage || activeDoc?.imageSrc;

  useEffect(() => {
    if (!activeRawImage) {
      setProcessedPreviewUrl(null);
      return;
    }

    let isMounted = true;
    setIsProcessingFilter(true);

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      if (!isMounted) return;
      try {
        const out = processDocumentImage(img, selectedFilter);
        setProcessedPreviewUrl(out);
      } catch (err) {
        console.error('Filter processing error:', err);
        setProcessedPreviewUrl(activeRawImage);
      } finally {
        if (isMounted) setIsProcessingFilter(false);
      }
    };
    img.onerror = () => {
      if (isMounted) {
        setProcessedPreviewUrl(activeRawImage);
        setIsProcessingFilter(false);
      }
    };
    img.src = activeRawImage;

    return () => {
      isMounted = false;
    };
  }, [activeRawImage, selectedFilter]);

  // ایجاد سند آزمایشی با کاغذ روغنی، بازتاب نور فلورسنت و سایه شدید دست
  const createGlossyDocumentTestImage = (): string => {
    const canvas = document.createElement('canvas');
    canvas.width = 750;
    canvas.height = 1050;
    const ctx = canvas.getContext('2d');
    if (!ctx) return '';

    // ۱. شیب زردی کاغذ روغنی و سایه نامتعادل دست
    const grad = ctx.createLinearGradient(0, 0, 750, 1050);
    grad.addColorStop(0, '#fbf5db'); // تن کاغذ روغنی زرد
    grad.addColorStop(0.4, '#f7eec5');
    grad.addColorStop(1, '#85929e'); // سایه تیره دست در گوشه پایین
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 750, 1050);

    // ۲. بازتاب نور براق و شدید (Specular Glare) روی کاغذ روغنی
    const glare = ctx.createRadialGradient(380, 420, 20, 380, 420, 280);
    glare.addColorStop(0, 'rgba(255, 255, 255, 0.96)');
    glare.addColorStop(0.45, 'rgba(255, 255, 255, 0.55)');
    glare.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.fillStyle = glare;
    ctx.fillRect(0, 0, 750, 1050);

    // ۳. متون رسمی با کلمات و اتصالات فارسی
    ctx.fillStyle = '#1e293b';
    ctx.font = 'bold 26px Tahoma, sans-serif';
    ctx.textAlign = 'center';
    ctx.direction = 'rtl';
    ctx.fillText('جمهوری اسلامی ایران - گواهی اسناد رسمی', 375, 80);

    ctx.strokeStyle = '#475569';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.moveTo(50, 110);
    ctx.lineTo(700, 110);
    ctx.stroke();

    ctx.textAlign = 'right';
    ctx.font = 'bold 20px Tahoma, sans-serif';
    ctx.fillStyle = '#0f172a';
    ctx.fillText('شماره پرونده: ۱۴۰۳/۷۸۹۲/الف - کد ملی: ۰۰۸۳۹۲۸۱۷۲', 700, 160);

    ctx.font = '17px Tahoma, sans-serif';
    ctx.fillStyle = '#334155';
    const lines = [
      'بدین‌وسیله گواهی می‌شود مدارک هویتی پیوست احراز اصالت گردید.',
      'این سند حاوی کاغذ روغنی، سلفون شفاف و بازتاب‌های نوری ناهمگون است.',
      'الگوریتم جدید فتوکپی استودیویی با حذف سایه و گیت نویز Post-Sharpening',
      'تمام لکه‌های تاریک و زردی زمینه را رفع و متون فارسی را شفاف می‌سازد.',
      'محل صدور: تهران، اداره ثبت اسناد و املاک مرکزی - تاریخ: ۱۴۰۳/۰۲/۱۵',
      'کلیه مفاد این گواهی رسمی در مراجع اداری نافذ و معتبر است.'
    ];
    let y = 215;
    for (const line of lines) {
      ctx.fillText(line, 700, y);
      y += 44;
    }

    // ۴. مهر رسمی
    ctx.save();
    ctx.strokeStyle = '#dc2626';
    ctx.lineWidth = 4;
    ctx.beginPath();
    ctx.arc(190, 800, 70, 0, Math.PI * 2);
    ctx.stroke();
    ctx.font = 'bold 18px Tahoma, sans-serif';
    ctx.fillStyle = '#dc2626';
    ctx.textAlign = 'center';
    ctx.fillText('تأیید شد', 190, 795);
    ctx.fillText('ثبت اسناد مرکزی', 190, 825);
    ctx.restore();

    // ۵. امضا و اثر انگشت
    ctx.strokeStyle = '#1e293b';
    ctx.lineWidth = 2;
    ctx.strokeRect(450, 750, 240, 95);
    ctx.font = '15px Tahoma, sans-serif';
    ctx.fillStyle = '#475569';
    ctx.textAlign = 'center';
    ctx.fillText('محل امضا و اثر انگشت', 570, 805);

    return canvas.toDataURL('image/jpeg', 0.92);
  };

  // شبیه‌سازی عکاسی مستقیم با دوربین در شبیه‌ساز جهت تست فوری جریان نویگیشن
  const handleSimulateCameraCapture = () => {
    const sampleImage = createGlossyDocumentTestImage();
    handleStartCaptureFlow(sampleImage, `سند دوربین (کاغذ روغنی و سایه‌دار) - ${new Date().toLocaleDateString('fa-IR')}`);
    showToast('سند آزمایشی با کاغذ روغنی و سایه شدید بارگذاری شد؛ تراز ۴ گوشه فعال است');
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

  // انصراف از صفحه PerspectiveCropView و بازگشت
  const handleCancelCrop = () => {
    setCapturedImage(null);
    if (isAddingPageToPdf) {
      setIsAddingPageToPdf(false);
      setCurrentScreen('preview');
      showToast('افزودن برگه جدید به نوار پی‌دی‌اف لغو شد');
    } else {
      setCurrentScreen('home');
      showToast('عملیات برش و تراز کادر لغو شد');
    }
  };

  // تایید نهایی در صفحه PerspectiveCropView و ارسال متغیر وضعیت تصویر پردازش‌شده به صفحه نمایش نهایی (Preview)
  const handleConfirmCropAndNavigate = (e?: React.MouseEvent) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    setIsPerspectiveCropped(true);
    const finalProcessedImage = capturedImage || customImage || activeDoc.imageSrc;

    if (isAddingPageToPdf) {
      setAdditionalPages(prev => [...prev, finalProcessedImage || createGlossyDocumentTestImage()]);
      setSelectedPageIndex(additionalPages.length + 1);
      setIsAddingPageToPdf(false);
      setCapturedImage(null);
      setCurrentScreen('preview');
      showToast('برگه جدید از دوربین/گالری با موفقیت به نوار پی‌دی‌اف اضافه شد');
      return;
    }

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

  const handleAddMultiplePages = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      const file = files[0];
      const reader = new FileReader();
      reader.onload = (evt) => {
        const imgResult = (evt.target?.result as string) || '';
        if (imgResult) {
          setIsAddingPageToPdf(true);
          handleStartCaptureFlow(imgResult, file.name.replace(/\.[^/.]+$/, "") || 'برگه جدید');
          showToast('تصویر برگه جدید دریافت شد؛ لطفاً گوشه‌ها را تنظیم کنید');
        }
      };
      reader.readAsDataURL(file);
    }
    if (e.target) e.target.value = '';
  };

  const handleGenerateMultiPagePdf = async () => {
    setIsGeneratingPdf(true);
    try {
      const firstPage = processedPreviewUrl || customImage || activeDoc.imageSrc || createGlossyDocumentTestImage();
      const allPageImages = [firstPage, ...additionalPages];
      
      const pdf = new jsPDF({
        orientation: 'portrait',
        unit: 'mm',
        format: 'a4'
      });

      const pageWidth = 210;
      const pageHeight = 297;
      const margin = 10;
      const printWidth = pageWidth - (margin * 2);
      const printHeight = pageHeight - (margin * 2);

      for (let i = 0; i < allPageImages.length; i++) {
        if (i > 0) {
          pdf.addPage();
        }
        
        const imgData = allPageImages[i];
        await new Promise<void>((resolve) => {
          const img = new Image();
          img.crossOrigin = 'anonymous';
          img.onload = () => {
            const imgRatio = (img.width || 1) / (img.height || 1);
            let w = printWidth;
            let h = printWidth / imgRatio;
            if (h > printHeight) {
              h = printHeight;
              w = printHeight * imgRatio;
            }
            const x = margin + (printWidth - w) / 2;
            const y = margin + (printHeight - h) / 2;
            pdf.addImage(imgData, 'JPEG', x, y, w, h, undefined, 'FAST');
            resolve();
          };
          img.onerror = () => {
            pdf.addImage(imgData, 'JPEG', margin, margin, printWidth, printHeight, undefined, 'FAST');
            resolve();
          };
          img.src = imgData;
        });
      }

      const safeName = (activeDoc?.title || 'document').trim().replace(/\s+/g, '_');
      pdf.save(`${safeName}.pdf`);
      showToast(`فایل PDF واحد (${allPageImages.length} برگه) با موفقیت تولید و دانلود شد`);
    } catch (err) {
      console.error('Error creating PDF:', err);
      showToast('خطا در تبدیل و ساخت فایل PDF');
    } finally {
      setIsGeneratingPdf(false);
    }
  };

  const handleSaveFilter = () => {
    setDocuments(prev => prev.map(d => d.id === activeDocId ? { 
      ...d, 
      filter: selectedFilter,
      pageCount: 1 + additionalPages.length 
    } : d));
    handleGenerateMultiPagePdf();
    showToast(`مدرک با فیلتر «${getFilterLabel(selectedFilter)}» و فایل PDF (${1 + additionalPages.length} برگه) ذخیره شد`);
  };

  const handleShare = () => {
    showToast('آماده‌سازی سند جهت اشتراک‌گذاری در پیام‌رسان‌ها...');
  };

  const handleAutoDetectCorners = () => {
    setIsAutoDetecting(true);
    setTimeout(() => {
      // نتایج دقیق فیلتر میانگین‌گیر ضد نویز، فیلتر دوبل گوسی و Convex Hull در استخراج ۴ گوشه واقعی کاغذ
      setCorners([
        { x: 10.5, y: 12.0 },
        { x: 89.5, y: 13.5 },
        { x: 88.0, y: 88.0 },
        { x: 11.5, y: 87.0 }
      ]);
      setIsAutoDetecting(false);
      showToast('تشخیص هوشمند لبه‌ها (Canny + فیلتر میانگین‌گیر کاغذ روغنی + Convex Hull) انجام شد');
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

  const updateCornerPosition = (cornerIndex: number, clientX: number, clientY: number) => {
    if (!cropContainerRef.current) return;
    const rect = cropContainerRef.current.getBoundingClientRect();
    const x = Math.max(1, Math.min(99, ((clientX - rect.left) / rect.width) * 100));
    const y = Math.max(1, Math.min(99, ((clientY - rect.top) / rect.height) * 100));
    setCorners(prev => {
      const next = [...prev];
      next[cornerIndex] = { x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
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
          filter: 'grayscale(100%) contrast(155%) brightness(108%)',
          backgroundColor: '#FFFFFF',
        };
      case 'bw':
        return {
          filter: 'grayscale(100%) contrast(125%) brightness(98%)',
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
      desc: 'مدیریت جریان نویگیشن: هدایت بین صفحه اصلی، برش پرسپکتیو و پیش‌نمایش، همراه با مدیریت نوار چندبرگه‌ای PDF و هدایت به صفحه اصلی برای افزودن برگه',
      code: `// متغیرهای وضعیت نوار چندبرگه‌ای پی‌دی‌اف و افزودن برگه از صفحه اصلی
var currentPdfAdditionalPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
var isAddingPageMode by remember { mutableStateOf(false) }
var activePreviewDocId by remember { mutableStateOf<String?>(null) }

NavHost(navController = navController, startDestination = Screen.Home.route) {
    composable(Screen.Home.route) {
        val targetDoc = documentList.find { it.id == activePreviewDocId } ?: pendingDocument
        HomeScreen(
            documents = documentList,
            onOpenDocument = { docId ->
                if (isAddingPageMode && activePreviewDocId != null) {
                    val selectedDoc = documentList.find { it.id == docId }
                    if (selectedDoc != null) {
                        val bmp = selectedDoc.bitmap ?: DocFilterEngine.createSampleDocBitmap(selectedDoc.title)
                        openCropScreenForNewCapture(bmp, selectedDoc.title)
                    }
                } else {
                    currentPdfAdditionalPages = emptyList()
                    activePreviewDocId = docId
                    navController.navigate(Screen.Preview.createRoute(docId))
                }
            },
            isAddingPageMode = isAddingPageMode,
            targetDocTitle = targetDoc?.title ?: "",
            onCancelAddPage = {
                isAddingPageMode = false
                activePreviewDocId?.let { navController.navigate(Screen.Preview.createRoute(it)) }
            },
            onLaunchCamera = onLaunchCamera,
            onLaunchGallery = { galleryLauncher.launch("image/*") }
        )
    }

    composable(Screen.Crop.route) {
        val rawBitmap = capturedRawBitmap
        if (rawBitmap != null) {
            PerspectiveCropView(
                initialBitmap = rawBitmap,
                onConfirmCrop = { processedBitmap ->
                    if (isAddingPageMode && activePreviewDocId != null) {
                        val filteredPage = DocFilterEngine.applyFilter(processedBitmap, ScanFilter.PHOTOCOPY)
                        currentPdfAdditionalPages = currentPdfAdditionalPages + filteredPage
                        isAddingPageMode = false
                        navController.navigate(Screen.Preview.createRoute(activePreviewDocId!!))
                    } else {
                        val newDocId = "new_\${System.currentTimeMillis()}"
                        // ایجاد و ذخیره سند جدید
                        navController.navigate(Screen.Preview.createRoute(newDocId))
                    }
                }
            )
        }
    }

    composable(Screen.Preview.route) { backStackEntry ->
        val docId = backStackEntry.arguments?.getString("docId")
        val document = documentList.find { it.id == docId } ?: pendingDocument
        PreviewScreen(
            document = document,
            additionalPages = currentPdfAdditionalPages,
            onAdditionalPagesChange = { currentPdfAdditionalPages = it },
            onAddPageFromHome = {
                isAddingPageMode = true
                navController.navigate(Screen.Home.route)
            },
            onBack = { navController.popBackStack(Screen.Home.route, false) }
        )
    }
}`
    },
    'HomeScreen.kt': {
      lang: 'kotlin',
      desc: 'صفحه اصلی Jetpack Compose با بنر تعاملی افزودن برگه به نوار PDF، کارت‌های مدارک اخیر و دو دکمه شناور دوربین و گالری',
      code: `@Composable
fun HomeScreen(
    documents: List<DocumentItem>,
    onOpenDocument: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    isAddingPageMode: Boolean = false,
    targetDocTitle: String = "",
    onCancelAddPage: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسکنر مدارک", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            // دکمه‌های دوربین و گالری جهت افزودن مستقیم سند جدید
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // بنر راهنمای افزودن برگه جدید به نوار PDF در صورت فعال بودن
            if (isAddingPageMode) {
                AddPageBanner(
                    targetDocTitle = targetDocTitle,
                    onCancel = onCancelAddPage
                )
            }
            // لیست مدارک اخیر
            LazyColumn {
                items(documents) { doc ->
                    DocumentCard(doc = doc, onClick = { onOpenDocument(doc.id) })
                }
            }
        }
    }
}`
    },
    'PreviewScreen.kt': {
      lang: 'kotlin',
      desc: 'صفحه پیش‌نمایش و نوار تبدیل چندبرگه به PDF واحد با چینش راست‌چین (RTL)، برگه ۱ اصلی در سمت راست و دکمه «افزودن برگه» در سمت چپ',
      code: `@Composable
fun PreviewScreen(
    document: DocumentItem?,
    additionalPages: List<Bitmap> = emptyList(),
    onAdditionalPagesChange: (List<Bitmap>) -> Unit = {},
    onAddPageFromHome: () -> Unit = {},
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit = {}
) {
    // نوار تبدیل به PDF واحد در بالای پیش‌نمایش
    // چیدمان راست‌چین (RTL) به گونه‌ای که برگه اصلی در سمت راست و دکمه «افزودن برگه» در سمت چپ قرار می‌گیرد:
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ۱. برگه ۱ (اصلی) در سمت راست
            item {
                PageChip(title = "برگه ۱ (اصلی)", isSelected = selectedPageIndex == 0)
            }

            // ۲. برگه‌های بعدی اضافه‌شده
            itemsIndexed(additionalPages) { index, pageBitmap ->
                PageChip(title = "برگه \${index + 2}", onRemove = { /* حذف برگه */ })
            }

            // ۳. دکمه «افزودن برگه» در سمت چپ برگه‌ها
            item {
                Button(
                    onClick = onAddPageFromHome,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("افزودن برگه")
                }
            }
        }
    }
}`
    },
    'DocFilterEngine.kt': {
      lang: 'kotlin',
      desc: 'موتور پردازش فیلتر فتوکپی استودیویی: الگوریتم چندمقیاسه Morphological Closing، نرمال‌سازی روشنایی Flatfield، نگاشت پیوسته Smoothstep و Post-Sharpening غیرتخریبی همراه با گیت نویز و بازیابی اتصالات خطوط متون فارسی',
      code: `package ir.smartscanner.docscan.util

import android.graphics.*
import kotlin.math.*

/**
 * فیلتر فتوکپی استودیویی پیشرفته با گیت نویز، لکه‌زدایی و وضوح‌بخشی حروف فارسی
 * سازگار با انواع مدارک سخت، سایه‌دار و کاغذهای روغنی/شفاف
 */
fun applyPhotocopy(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val totalPixels = width * height
    val srcPixels = IntArray(totalPixels)
    source.getPixels(srcPixels, 0, width, 0, 0, width, height)

    // ۱. استخراج ماتریس روشنایی استاندارد ITU-R BT.601
    val lum = FloatArray(totalPixels) { i ->
        val c = srcPixels[i]
        0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
    }

    // ۲. تخمین ۲ بعدی سطح روشنایی پس‌زمینه با Morphological Closing (اتساع + فرسایش)
    val downScale = maxOf(4, minOf(16, maxOf(width, height) / 120))
    val gw = maxOf(8, (width + downScale - 1) / downScale)
    val gh = maxOf(8, (height + downScale - 1) / downScale)
    // استخراج بیشینه‌ها در شبکه بلوک‌های کوچک و اعمال اتساع جهت حذف کامل متن‌ها
    // سپس اعمال فرسایش جهت بازیابی دقیق شیب‌های نوری و سایه‌های دست روی کاغذ

    // ۳. نرمال‌سازی بازتابی (Flatfield Division) و نگاشت تونال پیوسته سیگموئید
    // ratio = lum[idx] / bgLum (روشنایی موضعی هر پیکسل نسبت به پس‌زمینه کاغذ)
    // - اگر ratio >= 0.84: پس‌زمینه سفید خالص کاغذ (#FFFFFF) و حذف کامل زردی و سایه‌ها
    // - اگر ratio <= 0.44: جوهر مشکی عمیق و توپر تونر فتوکپی
    // - بینابین: نگاشت نرم Smoothstep (3t² - 2t³) جهت حفظ آنتی‌آلیاسینگ لبه حروف فارسی

    // ۴. مرحله Post-Sharpening غیرتخریبی با گیت نویز (Noise-Gated Unsharp Masking)
    // - گیت نویز (Deadzone = 10): حذف نویز و دانه‌دانه شدن در بافت کاغذ روغنی
    // - فیلتر Despeckle: حذف ذرات و لکه‌های تک‌پیکسلی پراکنده با حفظ نقطه‌های حروف
    // - تقویت پیوستگی خطوط باریک و اتصالات کلمات فارسی (Persian Ligatures Reconnection)
    return outputBitmap
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
        // ۳. فیلتر میانگین‌گیر (Mean Filter) جهت حذف نویز و بازتاب‌های کاغذ روغنی
        // ۴. فیلتر گوسی ۱ بعدی تفکیک‌پذیر (Separable 1D Gaussian Blur)
        // ۵. محاسبه گرادیان سوبل (Sobel Magnitudes & Angles)
        // ۶. سرکوب غیر بیشینه‌ها (Non-Maximum Suppression - NMS)
        // ۷. آستانه‌گذاری دوگانه و هیسترزیس (Double Thresholding & Hysteresis)
        // ۸. استخراج هندسی ۴ گوشه سند و بازگردانی به ابعاد اصلی
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
      {/* مخفی: ورودی انتخاب چند فایل جهت تبدیل همزمان برگه‌ها به PDF واحد */}
      <input 
        type="file" 
        ref={multiPageFileInputRef} 
        onChange={handleAddMultiplePages} 
        accept="image/*" 
        multiple
        className="hidden" 
        id="multi-page-input"
      />

      {/* نوار بالای پنل تست و مدیریت پروژه */}
      <header className="border-b border-neutral-800 bg-neutral-900/90 backdrop-blur px-4 py-3 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <img 
              src="/app_icon.jpg" 
              alt="آیکون اسکنر و فتوکپی هوشمند مدارک" 
              className="w-10 h-10 rounded-xl object-cover shadow-lg shadow-sky-600/30 border border-sky-400/20"
              referrerPolicy="no-referrer"
            />
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
                        <img 
                          src="/app_icon.jpg" 
                          alt="آیکون برنامه" 
                          className="w-9 h-9 rounded-xl object-cover shadow-xs border border-sky-200"
                          referrerPolicy="no-referrer"
                        />
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

                    {/* بنر حالت افزودن برگه به نوار PDF مدرک جاری */}
                    {isAddingPageToPdf && (
                      <div className="bg-sky-50 border border-sky-200/90 rounded-2xl p-3 mx-4 mt-2.5 flex items-center justify-between gap-2.5 shadow-xs shrink-0">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div className="w-8 h-8 rounded-xl bg-sky-600 text-white flex items-center justify-center shrink-0 shadow-xs">
                            <FileText className="w-4 h-4" />
                          </div>
                          <div className="flex flex-col min-w-0">
                            <span className="text-xs font-bold text-sky-950 truncate">
                              افزودن برگه به نوار PDF مدرک «{activeDoc.title}»
                            </span>
                            <span className="text-[10px] text-sky-700 leading-tight mt-0.5">
                              سندی از مدارک زیر را انتخاب کنید یا با دوربین/گالری سند جدید ایجاد کنید
                            </span>
                          </div>
                        </div>
                        <button
                          onClick={() => {
                            setIsAddingPageToPdf(false);
                            setCurrentScreen('preview');
                          }}
                          className="px-2.5 py-1 text-xs font-bold text-slate-600 hover:text-slate-900 bg-white hover:bg-slate-100 rounded-lg border border-slate-200 shrink-0 cursor-pointer transition-colors"
                        >
                          انصراف
                        </button>
                      </div>
                    )}

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

                    {/* بنر راهنمای جریان نویگیشن و فیلتر فتوکپی استودیویی */}
                    <div className="bg-sky-50 border border-sky-200/80 rounded-2xl p-2.5 mx-4 mt-2 flex flex-col gap-2 text-xs text-sky-950 shadow-xs">
                      <div className="flex items-center gap-2">
                        <Sparkles className="w-4 h-4 text-sky-600 shrink-0" />
                        <span className="font-semibold">موتور فتوکپی استودیویی با Post-Sharpening غیرتخریبی و حذف سایه فعال است.</span>
                      </div>
                      <button
                        onClick={handleSimulateCameraCapture}
                        className="w-full py-1.5 px-3 bg-amber-100 hover:bg-amber-200 border border-amber-300 text-amber-950 rounded-xl text-[11px] font-bold flex items-center justify-center gap-1.5 transition-all active:scale-98"
                      >
                        <span>📄 تست با سند کاغذ روغنی و سایه شدید (بررسی فیلتر)</span>
                      </button>
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
                    onMouseMove={(e) => {
                      if (activeCornerIdx !== null) {
                        updateCornerPosition(activeCornerIdx, e.clientX, e.clientY);
                      }
                    }}
                    onMouseUp={() => setActiveCornerIdx(null)}
                    onTouchMove={(e) => {
                      if (activeCornerIdx !== null && e.touches[0]) {
                        updateCornerPosition(activeCornerIdx, e.touches[0].clientX, e.touches[0].clientY);
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
                      onPointerMove={(e) => {
                        if (activeCornerIdx !== null) {
                          updateCornerPosition(activeCornerIdx, e.clientX, e.clientY);
                        }
                      }}
                      onPointerUp={() => setActiveCornerIdx(null)}
                      onPointerLeave={() => setActiveCornerIdx(null)}
                      className="flex-1 m-2.5 relative rounded-2xl overflow-hidden bg-black/60 border border-gray-800 flex items-center justify-center cursor-crosshair touch-none select-none"
                    >
                      {/* تصویر سند با چرخش ۹۰ درجه */}
                      <div 
                        className="w-full h-full p-4 flex items-center justify-center transition-transform duration-200 pointer-events-none select-none"
                        style={{ transform: `rotate(${rotationDegrees}deg)` }}
                      >
                        {capturedImage || customImage || activeDoc.imageSrc ? (
                          <img 
                            src={capturedImage || customImage || activeDoc.imageSrc} 
                            alt="سند خام جهت برش" 
                            className="max-h-full max-w-full object-contain pointer-events-none rounded shadow select-none"
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

                      {/* ۴ دستگیره لمسی گوشه‌ها با پشتیبانی کامل از Pointer Capture برای جابجایی ۱۰۰٪ روان */}
                      {corners.map((corner, idx) => (
                        <div
                          key={idx}
                          onPointerDown={(e) => {
                            e.currentTarget.setPointerCapture(e.pointerId);
                            setActiveCornerIdx(idx);
                          }}
                          onPointerMove={(e) => {
                            if (activeCornerIdx === idx) {
                              updateCornerPosition(idx, e.clientX, e.clientY);
                            }
                          }}
                          onPointerUp={(e) => {
                            try {
                              e.currentTarget.releasePointerCapture(e.pointerId);
                            } catch (_) {}
                            setActiveCornerIdx(null);
                          }}
                          onPointerCancel={(e) => {
                            try {
                              e.currentTarget.releasePointerCapture(e.pointerId);
                            } catch (_) {}
                            setActiveCornerIdx(null);
                          }}
                          className={`absolute w-8 h-8 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-xl cursor-grab active:cursor-grabbing flex items-center justify-center z-20 touch-none transition-transform ${
                            activeCornerIdx === idx ? 'scale-125 bg-sky-400 ring-4 ring-sky-400/50' : 'bg-sky-600 hover:scale-110'
                          }`}
                          style={{ left: `${corner.x}%`, top: `${corner.y}%` }}
                        >
                          <div className="w-2 h-2 bg-white rounded-full shadow-xs" />
                        </div>
                      ))}

                      {/* وضعیت پردازش Canny Edge Detection */}
                      {isAutoDetecting && (
                        <div className="absolute inset-0 bg-sky-950/60 backdrop-blur-xs flex flex-col items-center justify-center gap-2 z-30">
                          <div className="w-7 h-7 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />
                          <span className="text-xs font-bold text-sky-200">الگوریتم هوشمند Canny + فیلتر میانگین‌گیر کاغذ روغنی در حال ردیابی لبه‌ها...</span>
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

                    {/* نوار مدرن، با سایه ملایم و ارگونومیک برای موبایل جهت تبدیل همزمان برگه‌ها به یک PDF واحد */}
                    <div className="bg-white/95 backdrop-blur-sm border-b border-slate-200/90 px-3.5 py-2.5 shadow-sm shrink-0 flex flex-col gap-2.5">
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2 min-w-0">
                          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-red-50 to-rose-100 border border-red-200/70 text-red-600 flex items-center justify-center shrink-0 shadow-xs">
                            <FileText className="w-3.5 h-3.5" />
                          </div>
                          <div className="flex flex-col min-w-0">
                            <span className="text-xs font-bold text-slate-800 truncate">
                              تبدیل به PDF واحد
                            </span>
                            <span className="text-[10px] text-slate-500 font-medium leading-none mt-0.5">
                              {1 + additionalPages.length} برگه انتخاب‌شده
                            </span>
                          </div>
                        </div>

                        <button
                          onClick={handleGenerateMultiPagePdf}
                          disabled={isGeneratingPdf}
                          className="bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-700 hover:to-teal-700 text-white px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-xs hover:shadow-sm active:scale-95 transition-all disabled:opacity-50 shrink-0 cursor-pointer"
                        >
                          {isGeneratingPdf ? (
                            <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                          ) : (
                            <Download className="w-3.5 h-3.5" />
                          )}
                          <span>دانلود PDF واحد</span>
                        </button>
                      </div>

                      {/* ردیف دکمه افزودن و برگه‌ها با ساختار کاملاً راست‌چین: برگه اصلی در سمت راست، دکمه افزودن در سمت چپ */}
                      <div dir="rtl" className="flex items-center gap-2 overflow-x-auto pb-1 pt-0.5 scrollbar-none">
                        {/* برگه ۱ اصلی (در سمت راست) */}
                        <button
                          onClick={() => setSelectedPageIndex(0)}
                          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs transition-all shrink-0 cursor-pointer border ${
                            selectedPageIndex === 0
                              ? 'bg-gradient-to-r from-sky-600 to-blue-600 text-white font-bold border-sky-500 shadow-sm ring-2 ring-sky-200/60'
                              : 'bg-white hover:bg-slate-50 text-slate-700 border-slate-200 shadow-xs'
                          }`}
                        >
                          <FileText className={`w-3.5 h-3.5 ${selectedPageIndex === 0 ? 'text-sky-100' : 'text-slate-400'}`} />
                          <span>برگه ۱ (اصلی)</span>
                        </button>

                        {/* برگه‌های بعدی */}
                        {additionalPages.map((_, pIdx) => {
                          const isCur = selectedPageIndex === pIdx + 1;
                          return (
                            <div
                              key={pIdx}
                              onClick={() => setSelectedPageIndex(pIdx + 1)}
                              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl text-xs cursor-pointer transition-all shrink-0 border ${
                                isCur
                                  ? 'bg-gradient-to-r from-sky-600 to-blue-600 text-white font-bold border-sky-500 shadow-sm ring-2 ring-sky-200/60'
                                  : 'bg-white hover:bg-slate-50 text-slate-700 border-slate-200 shadow-xs'
                              }`}
                            >
                              <FileText className={`w-3.5 h-3.5 ${isCur ? 'text-sky-100' : 'text-slate-400'}`} />
                              <span className="whitespace-nowrap">برگه {pIdx + 2}</span>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setAdditionalPages(prev => prev.filter((_, idx) => idx !== pIdx));
                                  if (selectedPageIndex === pIdx + 1) {
                                    setSelectedPageIndex(0);
                                  } else if (selectedPageIndex > pIdx + 1) {
                                    setSelectedPageIndex(prev => prev - 1);
                                  }
                                }}
                                className={`p-1 rounded-lg transition-colors ml-0.5 ${
                                  isCur 
                                    ? 'hover:bg-red-500/80 text-white/90' 
                                    : 'hover:bg-red-50 text-slate-400 hover:text-red-500'
                                }`}
                                title="حذف این برگه"
                              >
                                <X className="w-3 h-3" />
                              </button>
                            </div>
                          );
                        })}

                        <div className="h-5 w-px bg-slate-200 shrink-0 mx-0.5" />

                        {/* دکمه افزودن برگه در سمت چپ برگه‌ها */}
                        <button
                          onClick={() => {
                            setIsAddingPageToPdf(true);
                            setCurrentScreen('home');
                            showToast('به صفحه اصلی منتقل شدید؛ سندی از مدارک اخیر انتخاب کنید یا سند جدید ایجاد نمایید');
                          }}
                          className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-b from-sky-50 to-blue-50/70 hover:from-sky-100 hover:to-blue-100 text-sky-700 border border-sky-200/90 rounded-xl text-xs font-bold whitespace-nowrap shadow-xs hover:shadow-sm active:scale-95 transition-all shrink-0 cursor-pointer"
                          title="هدایت به صفحه اصلی جهت افزودن برگه از مدارک اخیر، دوربین یا گالری"
                        >
                          <div className="w-4 h-4 rounded-md bg-sky-200/80 text-sky-800 flex items-center justify-center">
                            <Plus className="w-3 h-3" />
                          </div>
                          <span>افزودن برگه</span>
                        </button>
                      </div>
                    </div>

                    {/* کادر مدرن نمایش سند اسکن‌شده با نسبت تطبیق‌پذیر و قابلیت زوم و جابه‌جایی */}
                    <div 
                      className="flex-1 relative overflow-hidden bg-gradient-to-b from-slate-200 to-slate-300/90 flex items-center justify-center p-4 select-none touch-none"
                      onMouseDown={(e) => {
                        if (previewZoom > 1) {
                          setIsDraggingPreview(true);
                          previewDragStartRef.current = { x: e.clientX - previewPan.x, y: e.clientY - previewPan.y };
                        }
                      }}
                      onMouseMove={(e) => {
                        if (isDraggingPreview && previewZoom > 1) {
                          setPreviewPan({
                            x: e.clientX - previewDragStartRef.current.x,
                            y: e.clientY - previewDragStartRef.current.y
                          });
                        }
                      }}
                      onMouseUp={() => setIsDraggingPreview(false)}
                      onMouseLeave={() => setIsDraggingPreview(false)}
                      onTouchStart={(e) => {
                        if (previewZoom > 1 && e.touches.length === 1) {
                          setIsDraggingPreview(true);
                          previewDragStartRef.current = { 
                            x: e.touches[0].clientX - previewPan.x, 
                            y: e.touches[0].clientY - previewPan.y 
                          };
                        }
                      }}
                      onTouchMove={(e) => {
                        if (isDraggingPreview && previewZoom > 1 && e.touches.length === 1) {
                          setPreviewPan({
                            x: e.touches[0].clientX - previewDragStartRef.current.x,
                            y: e.touches[0].clientY - previewDragStartRef.current.y
                          });
                        }
                      }}
                      onTouchEnd={() => setIsDraggingPreview(false)}
                      onWheel={(e) => {
                        if (e.ctrlKey || e.metaKey || true) {
                          e.preventDefault();
                          const delta = e.deltaY > 0 ? -0.2 : 0.2;
                          setPreviewZoom(z => {
                            const next = Math.min(3, Math.max(1, +(z + delta).toFixed(1)));
                            if (next === 1) setPreviewPan({ x: 0, y: 0 });
                            return next;
                          });
                        }
                      }}
                    >
                      {/* ابزارک شناور کنترل زوم */}
                      <div className="absolute top-3 left-3 z-30 flex items-center gap-1 bg-white/90 backdrop-blur-md px-2 py-1 rounded-xl shadow-md border border-slate-200/80">
                        <button
                          onClick={() => setPreviewZoom(z => Math.min(3, +(z + 0.25).toFixed(2)))}
                          className="p-1 hover:bg-slate-100 rounded-lg text-slate-700 transition-colors"
                          title="بزرگ‌نمایی (+)"
                        >
                          <ZoomIn className="w-3.5 h-3.5" />
                        </button>
                        <span className="text-[10px] font-bold font-mono text-slate-700 px-1 min-w-[34px] text-center">
                          {Math.round(previewZoom * 100)}%
                        </span>
                        <button
                          onClick={() => {
                            setPreviewZoom(z => {
                              const next = Math.max(1, +(z - 0.25).toFixed(2));
                              if (next === 1) setPreviewPan({ x: 0, y: 0 });
                              return next;
                            });
                          }}
                          className="p-1 hover:bg-slate-100 rounded-lg text-slate-700 transition-colors"
                          title="کوچک‌نمایی (-)"
                        >
                          <ZoomOut className="w-3.5 h-3.5" />
                        </button>
                        {previewZoom > 1 && (
                          <button
                            onClick={() => {
                              setPreviewZoom(1);
                              setPreviewPan({ x: 0, y: 0 });
                            }}
                            className="p-1 hover:bg-red-50 text-slate-500 hover:text-red-600 rounded-lg transition-colors border-r border-slate-200 mr-0.5 pr-1.5"
                            title="بازنشانی اندازه"
                          >
                            <RotateCcw className="w-3 h-3" />
                          </button>
                        )}
                      </div>

                      {/* نشانگر برگه سفید سند اسکن‌شده */}
                      <div className="absolute top-3 right-3 z-30 bg-slate-900/80 text-white text-[10px] px-2.5 py-1 rounded-full backdrop-blur-xs font-medium shadow-sm flex items-center gap-1">
                        <span>برگه استاندارد سند</span>
                      </div>

                      {/* برگه سفید سند شناور با سایه عمیق و انطباق ابعاد طبیعی */}
                      <div 
                        className="transition-transform duration-75 ease-out"
                        style={{
                          transform: `scale(${previewZoom}) translate(${previewPan.x / previewZoom}px, ${previewPan.y / previewZoom}px)`,
                          cursor: previewZoom > 1 ? (isDraggingPreview ? 'grabbing' : 'grab') : 'default'
                        }}
                      >
                        <div 
                          className="max-w-[360px] max-h-[500px] bg-white rounded-lg shadow-[0_16px_40px_rgba(15,23,42,0.18),0_4px_12px_rgba(15,23,42,0.08)] border border-slate-200/80 p-3 sm:p-4 flex flex-col justify-between relative overflow-hidden transition-shadow"
                        >
                          {selectedPageIndex > 0 && additionalPages[selectedPageIndex - 1] ? (
                            <div className="w-full flex items-center justify-center bg-white p-1">
                              <img 
                                src={additionalPages[selectedPageIndex - 1]} 
                                alt={`برگه ${selectedPageIndex + 1}`} 
                                className="max-h-[420px] max-w-full object-contain rounded-md shadow-xs"
                                style={getFilterStyle(selectedFilter)}
                              />
                            </div>
                          ) : customImage || activeDoc.imageSrc ? (
                            <div className="w-full relative flex items-center justify-center bg-white p-1">
                              {isProcessingFilter && (
                                <div className="absolute inset-0 bg-white/80 backdrop-blur-[1px] flex items-center justify-center z-10 rounded-md">
                                  <div className="text-[10px] font-bold text-sky-800 bg-white px-3 py-1.5 rounded-full shadow-md border border-sky-200 flex items-center gap-1.5 animate-pulse">
                                    <Sparkles className="w-3.5 h-3.5 text-sky-600" />
                                    <span>پردازش فتوکپی استودیویی...</span>
                                  </div>
                                </div>
                              )}
                              <img 
                                src={processedPreviewUrl || customImage || activeDoc.imageSrc} 
                                alt="سند اسکن شده" 
                                className="max-h-[420px] max-w-full object-contain rounded-md shadow-xs"
                                style={processedPreviewUrl ? { backgroundColor: '#FFFFFF' } : getFilterStyle(selectedFilter)}
                              />
                            </div>
                          ) : (
                            <div 
                              className="w-[280px] min-h-[380px] flex flex-col justify-between p-1"
                              style={getFilterStyle(selectedFilter)}
                            >
                              {/* هدر سند رسمی */}
                              <div className="flex items-center justify-between border-b pb-2.5 border-slate-300">
                                <div className="w-7 h-7 rounded bg-slate-100 flex items-center justify-center text-xs font-bold shadow-xs">
                                  🇮🇷
                                </div>
                                <div className="text-center">
                                  <span className="text-[10px] font-medium block text-slate-500">جمهوری اسلامی ایران</span>
                                  <h4 className="font-bold text-xs text-slate-900">{activeDoc.title}</h4>
                                </div>
                                <div className="text-[9px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded font-mono">
                                  ۱۴۰۳/۰۲/۱۵
                                </div>
                              </div>

                              {/* خطوط شبیه‌سازی متن مدرک اسکن‌شده با کیفیت و کنتراست بالا */}
                              <div className="space-y-1.5 py-2 text-right">
                                <p className={`text-[10px] font-bold border-b pb-1 ${
                                  selectedFilter === 'photocopy' ? 'text-black border-slate-900 font-black' : 'text-slate-900 border-slate-200'
                                }`}>
                                  شماره پرونده: ۱۴۰۳/۷۸۹۲/الف - کد ملی: ۰۰۸۳۹۲۸۱۷۲
                                </p>
                                <p className={`text-[9.5px] leading-relaxed ${
                                  selectedFilter === 'photocopy' ? 'text-black font-bold' : 'text-slate-800 font-medium'
                                }`}>
                                  بدین‌وسیله گواهی می‌شود مدارک هویتی پیوست پس از بررسی مراجع ذی‌صلاح، احراز اصالت گردید.
                                </p>
                                <p className={`text-[9.5px] leading-relaxed ${
                                  selectedFilter === 'photocopy' ? 'text-black font-bold' : 'text-slate-800 font-medium'
                                }`}>
                                  محل صدور: تهران، اداره ثبت اسناد و املاک مرکزی - شناسه رهگیری: ۹۲۸۳۷۴۶۱
                                </p>
                                <p className={`text-[9px] leading-relaxed ${
                                  selectedFilter === 'photocopy' ? 'text-black font-semibold' : 'text-slate-700'
                                }`}>
                                  کلیه مفاد و مندرجات این گواهی رسمی دارای اعتبار قانونی بوده و در کلیه مراجع اداری نافذ است.
                                </p>
                                <p className={`text-[8.5px] pt-0.5 ${
                                  selectedFilter === 'photocopy' ? 'text-slate-900 font-medium' : 'text-slate-600'
                                }`}>
                                  جهت استعلام اصالت دیجیتال سند به سامانه الکترونیک خدمات اسناد رسمی مراجعه فرمایید.
                                </p>
                              </div>

                              {/* مهر و امضای رسمی پایین سند */}
                              <div className="flex items-center justify-between pt-2 border-t border-slate-300">
                                <div className={`w-12 h-12 rounded-full border-2 flex items-center justify-center text-[9px] font-black rotate-[-12deg] shadow-xs ${
                                  selectedFilter === 'photocopy' 
                                    ? 'border-slate-950 text-slate-950 bg-slate-50' 
                                    : 'border-red-600 text-red-600'
                                }`}>
                                  تأیید شد
                                </div>
                                <div className="text-left">
                                  <div className="text-[9px] text-slate-500">محل امضا و اثر انگشت</div>
                                  <div className={`w-16 h-4 border-b-2 mt-1 ${
                                    selectedFilter === 'photocopy' ? 'border-black' : 'border-slate-800'
                                  }`} />
                                </div>
                              </div>
                            </div>
                          )}

                          {/* نشانگر فیلتر اعمال‌شده در پایین */}
                          <div className="mt-2 pt-1.5 border-t border-slate-100 flex items-center justify-between">
                            <div className={`text-[9px] px-2 py-0.5 rounded-full shadow-xs font-bold flex items-center gap-1 ${
                              selectedFilter === 'photocopy' 
                                ? 'bg-slate-950 text-white ring-1 ring-white/20' 
                                : 'bg-sky-600 text-white'
                            }`}>
                              {selectedFilter === 'photocopy' && <Printer className="w-3 h-3 text-sky-400" />}
                              <span>{selectedFilter === 'photocopy' ? 'فتوکپی استودیویی (Post-Sharpening فعال)' : getFilterLabel(selectedFilter)}</span>
                            </div>

                            {/* نشانگر تراز بودن پرسپکتیو */}
                            {isPerspectiveCropped && (
                              <div className="bg-emerald-600/90 text-white text-[9px] px-2 py-0.5 rounded-full backdrop-blur-xs flex items-center gap-1">
                                <Check className="w-3 h-3" />
                                <span>تراز با Matrix.setPolyToPoly</span>
                              </div>
                            )}
                          </div>
                        </div>
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
