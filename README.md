# HTML2APK

ساخت اپلیکیشن اندروید از HTML/ZIP — **کاملاً آفلاین، روی خود دستگاه**

HTML2APK یک اپلیکیشن اندروید است که یک فایل HTML یا یک ZIP وب (شامل `index.html` و منابع آن) را به یک **APK امضاشده و قابل‌نصب** تبدیل می‌کند؛ بدون هیچ سرور، حساب کاربری یا اتصال اینترنتی. تمام مراحل — پچ مانیفست باینری، بازنویسی `resources.arsc`، zipalign و امضای v1/v2/v3 — روی خود گوشی انجام می‌شود.

## ✨ قابلیت‌ها

- ورودی: فایل `HTML` تکی یا `ZIP` وب (با `index.html` در ریشه یا یک پوشهٔ تکی)
- نام اپ، **نام پکیج با اعتبارسنجی زنده**، versionCode / versionName
- جهت صفحه: خودکار / عمودی / افقی
- آیکون اختلافی (اختیاری): برش خودکار مربعی + تولید در همهٔ تراکم‌ها (mdpi…xxxhdpi)
- بخش تاشو تنظیمات امضا (Keystore) و مانیفست (دسترسی INTERNET)
- پیشرفت ۵ مرحله‌ای با زمان هر مرحله + **ترمینال لاگ** به سبک خط فرمان
- دکمه‌های نصب، اشتراک‌گذاری و بازکردن پوشهٔ دانلودها پس از ساخت
- معماری MVVM با StateFlow و Coroutines؛ UI با Jetpack Compose + Material3، تم سبز یشمی تیره، RTL کامل، فونت Vazirmatn و JetBrains Mono برای بخش فنی

## 🔄 تغییرات نسخهٔ ۱.۰.۱ (رفع کرش هنگام ساخت)

در نسخهٔ ۱.۰.۰، با زدن دکمهٔ «ساخت APK» روی برخی دستگاه‌ها اپ کرش می‌کرد. علت و رفع:

- **ریشهٔ اصلی کرش — تخصیص ~۷۸۶ مگابایت حافظه (OutOfMemoryError):** در مونتاژ APK، ظرفیت اولیهٔ بافر خروجی با خطای محاسباتی (`sum shl 16` به‌جای `×16`) حدود **۷۸۶MB** رزرو می‌شد — روی هر دستگاهی بیشتر از سقف heap است. با زدن دکمهٔ ساخت، `OutOfMemoryError` (از نوع `Error`) پرتاب می‌شد که در `catch (e: Exception)` نمی‌افتد → **بسته‌شدن کامل اپ**.
- **باگ دوم — EOCD خراب (`cdsize=0`):** بازنویسی ZIP اندازهٔ Central Directory را در `finish()` از `bytesWritten` می‌گرفت که هنگام نوشتن CD به‌روزرسانی نمی‌شد؛ نتیجه: **هیچ APK خروجی‌ای از نظر apksig معتبر نبود** و امضا همیشه با `ZipFormatException` شکست می‌خورد.
- **خط لولهٔ کاملاً استریمی:** کپی ورودی با سقف ۲۰۰MB، استخراج ZIP، مونتاژ APK (`putEntryFile` دو-پاسه)، امضا (فایل به فایل) و ذخیره در MediaStore همگی استریم می‌شوند؛ مصرف حافظهٔ لحظه‌ای دیگر به حجم فایل ورودی وابسته نیست.
- **ضدکرش شدن مسیر ساخت:** در ViewModel اکنون `Throwable` (نه فقط `Exception`) گرفته می‌شود و بخش همگامِ شروع ساخت هم محافظت شده است. هر خطا — حتی OOM — به‌جای بستن اپ، به‌صورت پیام قابل‌خواندن + stack trace در ترمینال‌لاگ نمایش داده می‌شود.
- **حذف فایل خروجی نیمه‌کاره:** اگر نوشتن در MediaStore وسط راه fail شود، ردیف `IS_PENDING` حذف می‌شود تا فایل خراب در Downloads باقی نماند.
- **تست انتها-به-انتها جدید روی JVM** (`EngineEndToEndTest`) با فیکسچر واقعی template.apk: مونتاژ استریمی + امضا + تأیید با `ApkVerifier` (v1+v2+v3) + بررسی STORED/تراز ۴ بایتیِ `resources.arsc`.

## 🧱 معماری

```
HTML2APK/
├── app/                     ← اپ اصلی (Kotlin + Compose)
│   └── buildengine/         ← موتور ساخت آفلاین
│       ├── AXmlEditor.kt    ← پارسر/سریالایزر مانیفست باینری + پچر
│       ├── ApkAssembler.kt  ← مونتاژ APK + پچ resources.arsc
│       ├── ZipWriter.kt     ← بازنویسی ZIP با STORED + تراز ۴ بایتی (zipalign)
│       ├── ZipUtils.kt      ← استخراج امن (ضد Zip-Slip) + محدودیت حجم/تعداد
│       ├── KeystoreManager.kt ← ساخت/بارگذاری کلید RSA-2048 (BouncyCastle)
│       ├── ApkSignUtil.kt   ← امضای v1+v2+v3 (apksig)
│       ├── IconProcessor.kt ← برش مربعی + ریسایز برای mipmap ها
│       └── OutputStore.kt   ← ذخیره از طریق MediaStore + نصب/اشتراک
├── template/                ← APK پایه (پوستهٔ WebView تمام‌صفحه، جاوای خالص)
│   └── build_template.sh    ← ساخت template.apk و جاسازی در assets اپ
├── .github/workflows/       ← ساخت خودکار APK با GitHub Actions
└── gradle/libs.versions.toml ← version catalog
```

### چرخهٔ ساخت (۵ مرحله)

1. **آماده‌سازی** — کپی ورودی، استخراج ZIP با محافظ Zip-Slip، یافتن `index.html`
2. **پردازش منابع** — خواندن `template.apk` از assets، پردازش آیکون در مسیرهای دقیق mipmap قالب
3. **ساخت و بسته‌بندی** — پچ مانیفست باینری (package/version/label/orientation/INTERNET)، پچ نام پکیج در `resources.arsc`، بازنویسی ZIP با همهٔ ورودی‌های STORED و تراز ۴ بایتی
4. **امضا** — ساخت/بارگذاری keystore (PKCS12 در حافظهٔ داخلی) و امضا با apksig
5. **ذخیره** — انتشار از طریق MediaStore در پوشهٔ Download (اندروید ۱۰ به بالا) یا مسیر مخصوص اپ (اندروید ۸/۹)

## 🛠 ساخت پروژه

### با Android Studio (ساده‌ترین راه)

1. مخزن را کلون کنید: `git clone https://github.com/pubbgggamerss00-bot/HTML2APK.git`
2. با Android Studio (Giraffe یا جدیدتر، JDK 17) باز کنید
3. ابتدا `build_template.sh` را اجرا کنید تا `template.apk` ساخته شود (یا اجازه دهید CI انجام دهد):
   ```bash
   ./build_template.sh
   ```
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)`

### با خط فرمان

```bash
gradle wrapper --gradle-version 8.7        # فقط بار اول (اگر gradlew نبود)
./gradlew :template:assembleRelease
cp template/build/outputs/apk/release/template-release-unsigned.apk app/src/main/assets/template.apk
./gradlew :app:assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

### ساخت خودکار (GitHub Actions)

هر push روی مخزن، workflow ‏`.github/workflows/build-apk.yml` را اجرا می‌کند:

- ساخت `template.apk` → جاسازی در assets → اجرای تست‌های واحد → ساخت APK
- APK به‌صورت **Actions artifact** با نام `HTML2APK-debug-apk` آپلود می‌شود
- آخرین نسخهٔ منتشرشده در بخش **Releases** موجود است

## 🧪 تست‌ها

```bash
./gradlew :app:testDebugUnitTest
```

پوشش تست:

- اعتبارسنجی نام پکیج (فرمت، کلمات رزروشده، پیشوندهای ممنوع `android/java/...`)
- Zip-Slip و مسیرهای مطلق/ویندوزی، یافتن ریشهٔ `index.html`
- دور‌زنی کامل AXML (ساخت فیکسچر مستقل طبق مشخصات AOSP → پارس → پچ → بازپارس)
- تست فیکسچر **واقعی aapt2**: در CI مانیفست و `resources.arsc` واقعیِ قالب استخراج و تست می‌شوند (به‌صورت محلی، اگر فیکسچر نباشد این تست‌ها رد می‌شوند)

## 📱 APK نهایی

- آخرین APK ساخت‌شده در بخش [Releases](https://github.com/pubbgggamerss00-bot/HTML2APK/releases) قرار می‌گیرد (امضای debug برای نصب مستقیم روی دستگاه مناسب است؛ برای انتشار در استور، امضای release خودتان را جایگزین کنید).
- اپ‌های خروجیِ HTML2APK با کلید اختصاصی خودتان (v1+v2+v3) امضا می‌شوند و قابل نصب/ارتقا هستند.

## ⚠️ موارد تأییدنشده روی دستگاه واقعی

این پروژه در محیط بدون Android SDK نوشته شده است؛ کامپایل و تست‌های JVM در GitHub Actions انجام می‌شود، اما این موارد **هنوز روی دستگاه واقعی آزموده نشده‌اند**:

1. چرخهٔ کامل ساخت روی گوشی واقعی (انتخاب فایل تا نصب خروجی)
2. نصب APK خروجی روی نسخه‌های مختلف اندروید (خصوصاً ۱۱ به بالا به‌خاطر قید arsc/align)
3. بوت‌شدن اپ خروجی WebView و بارگذاری محتوای ZIP کاربر (subfolderها، فایل‌های نسبی)
4. امضای v3 روی دستگاه‌های قدیمی و نصبِ ارتقایی با همان کلید
5. رفتار FileProvider در نصب/اشتراک‌گذاری روی اکوسیستم‌های مختلف سازنده

اگر مشکلی دیدید، لطفاً Issue باز کنید.

## 🔐 نکات امنیتی

- توکن GitHub استفاده‌شده برای آپلود این مخزن **در گفتگو فاش شده است** — لطفاً در تنظیمات GitHub آن را باطل (Revoke) کنید و توکن جدید بسازید.
- گذرواژهٔ keystore به‌صورت پیش‌فرض مقدار ساده‌ای دارد؛ اگر امنیت بیشتر می‌خواهید، از بخش تنظیمات امضا گذرواژهٔ قوی وارد کنید.
- کلید در حافظهٔ داخلی اپ (`filesDir`) ذخیره می‌شود و از دستگاه خارج نمی‌شود.

## 📄 لایسنس

MIT
