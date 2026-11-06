package ir.html2apk.app.model

import android.net.Uri

/** The five build stages shown in the compiler tab (titles mirror the v3.4.0 pipeline). */
enum class BuildStage(val index: Int, val title: String) {
    PREPARE(0, "استخراج فایل ورودی و قالب پایه (template.apk)"),
    RESOURCES(1, "پردازش منابع و آیکون تطبیقی"),
    ASSEMBLE(2, "بازنویسی باینری AXML، تزریق فایل‌های وب و Zipalign"),
    SIGN(3, "امضای دیجیتال v1+v2+v3"),
    SAVE(4, "ذخیرهٔ خروجی در Downloads/HTML2APK");

    companion object {
        val COUNT = entries.size
        fun byIndex(i: Int): BuildStage? = entries.firstOrNull { it.index == i }
    }
}

enum class Orientation(val label: String, val manifestValue: Int) {
    AUTO("خودکار", AXmlOrientations.UNSPECIFIED),
    PORTRAIT("عمودی", AXmlOrientations.PORTRAIT),
    LANDSCAPE("افقی", AXmlOrientations.LANDSCAPE),
}

/** Mirror of AXml orientation constants to keep the model android-free. */
object AXmlOrientations {
    const val UNSPECIFIED = -1
    const val PORTRAIT = 1
    const val LANDSCAPE = 0
}

enum class LogType { CMD, INFO, OK, ERROR }

data class LogLine(
    val timestamp: Long,
    val type: LogType,
    val text: String,
)

/** Fully-validated user input handed to the build engine. */
data class BuildRequest(
    val inputUri: Uri,
    val inputName: String,
    val isZip: Boolean,
    val appName: String,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val orientation: Orientation,
    val iconUri: Uri?,
    val keystorePassword: String,
    val keyAlias: String,
    val internetPermission: Boolean,
    val usesCleartextTraffic: Boolean = false,
)

/** Mutable form state kept by the ViewModel. */
data class FormState(
    val inputUri: Uri? = null,
    val inputName: String? = null,
    val inputSize: Long? = null,
    val appName: String = "",
    val packageName: String = "",
    val versionCode: String = "1",
    val versionName: String = "1.0",
    val orientation: Orientation = Orientation.AUTO,
    val iconUri: Uri? = null,
    val keystorePassword: String = "",
    val keyAlias: String = "html2apk",
    val internetPermission: Boolean = true,
    val usesCleartextTraffic: Boolean = false,
)
