package ir.html2apk.app.util

/**
 * Validation rules — limits and error messages mirror the original v3.4.0 build.
 */
object Validators {

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while",
        "true", "false", "null",
    )

    // «بخش اول نمی‌تواند android/java/androidx باشد» — exactly the v3.4.0 rule set.
    private val RESERVED_FIRST_SEGMENTS = setOf("android", "java", "androidx")

    data class PackageValidation(
        val isValid: Boolean,
        val normalized: String,
        val errorMessage: String?,
    )

    data class FieldValidation(
        val isValid: Boolean,
        val errorMessage: String?,
    )

    fun validatePackageName(raw: String): PackageValidation {
        val normalized = raw.trim().lowercase()

        if (normalized.isEmpty()) {
            return PackageValidation(false, normalized, "شناسه پکیج نمی‌تواند خالی باشد")
        }
        // v3.4.0: «شناسه پکیج حداکثر ۱۲۰ کاراکتر است»
        if (normalized.length > 120) {
            return PackageValidation(false, normalized, "شناسه پکیج حداکثر ۱۲۰ کاراکتر است")
        }
        if (!Regex("^[a-z0-9_.]+$").matches(normalized)) {
            return PackageValidation(
                false, normalized,
                "فقط حروف کوچک مجاز است (حروف انگلیسی کوچک، عدد، نقطه و زیرخط)"
            )
        }
        val segments = normalized.split('.')
        if (segments.any { it.isEmpty() }) {
            return PackageValidation(
                false, normalized,
                "نقطهٔ تکراری یا نقطه در ابتدا/انتهای نام مجاز نیست"
            )
        }
        // v3.4.0: «حداقل دو بخش لازم است (مثلاً com.myapp)»
        if (segments.size < 2) {
            return PackageValidation(
                false, normalized,
                "حداقل دو بخش لازم است (مثلاً com.myapp)"
            )
        }
        for (seg in segments) {
            // v3.4.0: «هر بخش حداکثر ۴۰ کاراکتر»
            if (seg.length > 40) {
                return PackageValidation(false, normalized, "هر بخش حداکثر ۴۰ کاراکتر است")
            }
            if (!seg[0].isLetter() || seg[0] !in 'a'..'z') {
                return PackageValidation(
                    false, normalized,
                    "هر بخش باید با حرف انگلیسی کوچک شروع شود («$seg»)"
                )
            }
            // v3.4.0: «کلمه رزرو جاوا است»
            if (seg in JAVA_KEYWORDS) {
                return PackageValidation(false, normalized, "«$seg» کلمه رزرو جاوا است")
            }
        }
        if (segments.first() in RESERVED_FIRST_SEGMENTS) {
            return PackageValidation(
                false, normalized,
                "بخش اول نمی‌تواند android/java/androidx باشد"
            )
        }
        return PackageValidation(true, normalized, null)
    }

    /**
     * v3.4.0 app-name rules: 2..50 chars, no filename-hostile / control characters.
     */
    fun validateAppName(raw: String): FieldValidation {
        val name = raw.trim()
        if (name.length < 2) {
            return FieldValidation(false, "نام برنامه حداقل ۲ کاراکتر است")
        }
        if (name.length > 50) {
            return FieldValidation(false, "نام برنامه حداکثر ۵۰ کاراکتر است")
        }
        if (Regex("[\\p{Cntrl}\\\\/:*?\"<>|]").containsMatchIn(name)) {
            return FieldValidation(false, "نام برنامه شامل کاراکتر غیرمجاز است")
        }
        return FieldValidation(true, null)
    }

    /**
     * v3.4.0 version-name rules: «قالب نسخه: مثل 1.0.0 (حداکثر ۳۰ کاراکتر)».
     */
    fun validateVersionName(raw: String): FieldValidation {
        val name = raw.trim()
        if (name.isEmpty()) {
            return FieldValidation(false, "نام نسخه نمی‌تواند خالی باشد")
        }
        if (name.length > 30 || !Regex("^[0-9]+(\\.[0-9]+){0,3}$").matches(name)) {
            return FieldValidation(false, "قالب نسخه: مثل 1.0.0 (حداکثر ۳۰ کاراکتر)")
        }
        return FieldValidation(true, null)
    }

    /**
     * v3.4.0 version-code rules: positive integer up to 2,100,000,000.
     */
    fun validateVersionCode(raw: String): FieldValidation {
        val code = raw.trim()
        val value = code.toLongOrNull()
        if (value == null || value < 1 || value > 2_100_000_000L) {
            return FieldValidation(false, "کد بیلد عدد مثبت (تا ۲٬۱۰۰٬۰۰۰٬۰۰۰) است")
        }
        return FieldValidation(true, null)
    }

    /**
     * "my site.html" -> "my site" — extension stripped, filename-unsafe
     * characters removed. Only falls back to a fixed name when the input
     * file itself had no usable name (e.g. ".html").
     *
     * Used at build time when the user leaves the app-name field empty, and
     * in the form UI to preview exactly which name will be registered.
     */
    fun deriveAppName(inputName: String): String {
        val cleaned = inputName.substringBeforeLast('.')
            .replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), " ")
            .trim()
        return cleaned.ifBlank { "App" }
    }

    /** Only .zip / .html / .htm inputs are accepted (v3.4.0 rule). */
    fun isAcceptedInputName(name: String?): Boolean {
        val n = (name ?: "").lowercase()
        return n.endsWith(".zip") || n.endsWith(".html") || n.endsWith(".htm")
    }
}
