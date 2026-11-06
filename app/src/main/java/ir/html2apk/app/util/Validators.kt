package ir.html2apk.app.util

/**
 * Live package-name validation (same rules aapt2 enforces):
 *  - lowercase latin letters, digits, underscores and dots only
 *  - at least two segments, each starting with a letter
 *  - no java keyword / reserved segment
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

    private val RESERVED_PREFIXES = setOf("android", "java", "javax", "kotlin", "com.android")

    data class PackageValidation(
        val isValid: Boolean,
        val normalized: String,
        val errorMessage: String?,
    )

    fun validatePackageName(raw: String): PackageValidation {
        val normalized = raw.trim().lowercase()

        if (normalized.isEmpty()) {
            return PackageValidation(false, normalized, "نام پکیج نمی‌تواند خالی باشد")
        }
        if (normalized.length > 200) {
            return PackageValidation(false, normalized, "نام پکیج بیش از حد طولانی است")
        }
        if (!Regex("^[a-z0-9_.]+$").matches(normalized)) {
            return PackageValidation(
                false, normalized,
                "فقط حروف کوچک انگلیسی، عدد، نقطه و زیرخط مجاز است"
            )
        }
        val segments = normalized.split('.')
        if (segments.any { it.isEmpty() }) {
            return PackageValidation(
                false, normalized,
                "نقطهٔ تکراری یا نقطه در ابتدا/انتهای نام مجاز نیست"
            )
        }
        if (segments.size < 2) {
            return PackageValidation(
                false, normalized,
                "حداقل دو بخش با نقطه لازم است؛ مثال: com.example.app"
            )
        }
        for (seg in segments) {
            if (!seg[0].isLetter() || seg[0] !in 'a'..'z') {
                return PackageValidation(
                    false, normalized,
                    "هر بخش باید با حرف انگلیسی کوچک شروع شود («$seg»)"
                )
            }
            if (seg in JAVA_KEYWORDS) {
                return PackageValidation(
                    false, normalized,
                    "«$seg» کلمهٔ رزروشدهٔ جاواست و مجاز نیست"
                )
            }
        }
        if (segments.first() in RESERVED_PREFIXES) {
            return PackageValidation(
                false, normalized,
                "نام پکیج نمی‌تواند با «${segments.first()}» شروع شود"
            )
        }
        return PackageValidation(true, normalized, null)
    }
}
