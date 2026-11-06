package ir.html2apk.app

import ir.html2apk.app.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    private fun validate(input: String) = Validators.validatePackageName(input)

    @Test
    fun `valid two segment package`() {
        val v = validate("com.example.app")
        assertTrue(v.isValid)
        assertEquals("com.example.app", v.normalized)
        assertTrue(v.errorMessage == null)
    }

    @Test
    fun `valid package with digits and underscores`() {
        assertTrue(validate("ir.html2apk.my_app2").isValid)
        assertTrue(validate("com.company.productname").isValid)
    }

    @Test
    fun `normalizes upper case and whitespace`() {
        val v = validate("  Com.Example.App ")
        assertTrue(v.isValid)
        assertEquals("com.example.app", v.normalized)
    }

    @Test
    fun `empty input is invalid`() {
        val v = validate("")
        assertFalse(v.isValid)
        assertTrue(v.errorMessage != null)
    }

    @Test
    fun `blank spaces only is invalid`() {
        assertFalse(validate("   ").isValid)
    }

    @Test
    fun `single segment is invalid`() {
        val v = validate("myapp")
        assertFalse(v.isValid)
        assertTrue(v.errorMessage!!.contains("دو بخش"))
    }

    @Test
    fun `non-latin letters are invalid`() {
        assertFalse(validate("com.exämple.app").isValid)
        assertFalse(validate("com.مثال.app").isValid)
    }

    @Test
    fun `digits-only segment start is invalid`() {
        assertFalse(validate("com.2app.app").isValid)
    }

    @Test
    fun `illegal characters are invalid`() {
        assertFalse(validate("com.exa-mple.app").isValid)
        assertFalse(validate("com.exa mple.app").isValid)
        assertFalse(validate("com.example!app").isValid)
    }

    @Test
    fun `leading dot is invalid`() {
        assertFalse(validate(".com.example").isValid)
    }

    @Test
    fun `trailing dot is invalid`() {
        assertFalse(validate("com.example.").isValid)
    }

    @Test
    fun `double dot is invalid`() {
        assertFalse(validate("com..example").isValid)
    }

    @Test
    fun `segment starting with digit is invalid`() {
        val v = validate("com.1example.app")
        assertFalse(v.isValid)
    }

    @Test
    fun `java keyword segment is invalid`() {
        val v = validate("com.class.app")
        assertFalse(v.isValid)
        // v3.4.0 exact wording: «کلمه رزرو جاوا است»
        assertTrue(v.errorMessage!!.contains("رزرو"))
        assertTrue(v.errorMessage!!.contains("جاوا"))
    }

    @Test
    fun `android prefix is reserved`() {
        val v = validate("android.example.app")
        assertFalse(v.isValid)
    }

    @Test
    fun `java prefix is reserved`() {
        assertFalse(validate("java.example.app").isValid)
    }

    @Test
    fun `package name over 120 chars is invalid`() {
        val v = validate("com." + "a".repeat(120) + ".app")
        assertFalse(v.isValid)
        assertTrue(v.errorMessage!!.contains("۱۲۰"))
    }

    @Test
    fun `package segment over 40 chars is invalid`() {
        val v = validate("com." + "a".repeat(41) + ".app")
        assertFalse(v.isValid)
        assertTrue(v.errorMessage!!.contains("۴۰"))
    }

    @Test
    fun `androidx first segment is invalid`() {
        val v = validate("androidx.example.app")
        assertFalse(v.isValid)
        assertTrue(v.errorMessage!!.contains("android/java/androidx"))
    }

    // ---- app name: v3.4.0 rules ----

    @Test
    fun `app name below 2 chars is invalid`() {
        val v = Validators.validateAppName("a")
        assertFalse(v.isValid)
        assertTrue(v.errorMessage!!.contains("حداقل ۲"))
    }

    @Test
    fun `app name over 50 chars is invalid`() {
        assertFalse(Validators.validateAppName("x".repeat(51)).isValid)
    }

    @Test
    fun `app name with hostile chars is invalid`() {
        assertFalse(Validators.validateAppName("my/app").isValid)
        assertFalse(Validators.validateAppName("my:app").isValid)
        assertFalse(Validators.validateAppName("my|app").isValid)
    }

    @Test
    fun `normal app names pass`() {
        assertTrue(Validators.validateAppName("فروشگاه من").isValid)
        assertTrue(Validators.validateAppName("My-Shop_v2").isValid)
    }

    // ---- version name: v3.4.0 rules ----

    @Test
    fun `empty version name is invalid`() {
        assertFalse(Validators.validateVersionName("  ").isValid)
    }

    @Test
    fun `version name format is enforced`() {
        assertTrue(Validators.validateVersionName("1.0.0").isValid)
        assertTrue(Validators.validateVersionName("10.25.3").isValid)
        assertFalse(Validators.validateVersionName("1.0.0-beta").isValid)
        assertFalse(Validators.validateVersionName("۱.۰").isValid)
    }

    @Test
    fun `version name over 30 chars is invalid`() {
        assertFalse(Validators.validateVersionName("1.0.0.0.0.0.0.0.0.0.0.0").isValid)
    }

    // ---- version code: v3.4.0 rules ----

    @Test
    fun `version code must be positive integer`() {
        assertFalse(Validators.validateVersionCode("").isValid)
        assertFalse(Validators.validateVersionCode("abc").isValid)
        assertFalse(Validators.validateVersionCode("0").isValid)
        assertFalse(Validators.validateVersionCode("-5").isValid)
        assertFalse(Validators.validateVersionCode("2100000001").isValid)
        assertTrue(Validators.validateVersionCode("1").isValid)
        assertTrue(Validators.validateVersionCode("2100000000").isValid)
    }

    // ---- input file acceptance ----

    @Test
    fun `only zip html htm accepted`() {
        assertTrue(Validators.isAcceptedInputName("site.html"))
        assertTrue(Validators.isAcceptedInputName("site.htm"))
        assertTrue(Validators.isAcceptedInputName("bundle.ZIP"))
        assertFalse(Validators.isAcceptedInputName("photo.png"))
        assertFalse(Validators.isAcceptedInputName("document.pdf"))
        assertFalse(Validators.isAcceptedInputName(null))
    }

    // ---- deriveAppName: the app-name fallback when the user leaves the field empty ----

    @Test
    fun `extension is stripped from the input file name`() {
        assertEquals("my site", Validators.deriveAppName("my site.html"))
        assertEquals("my site", Validators.deriveAppName("my site.ZIP"))
    }

    @Test
    fun `filename-unsafe characters are replaced`() {
        assertEquals("my site 2024", Validators.deriveAppName("my site:2024?.html"))
    }

    @Test
    fun `name with no extension passes through`() {
        assertEquals("وب اپلیکیشن", Validators.deriveAppName("وب اپلیکیشن"))
    }

    @Test
    fun `extension-only name falls back to App`() {
        assertEquals("App", Validators.deriveAppName(".html"))
        assertEquals("App", Validators.deriveAppName(""))
    }
}
