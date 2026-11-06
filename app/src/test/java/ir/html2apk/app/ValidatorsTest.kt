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
    fun `uppercase characters are invalid`() {
        assertFalse(validate("Com.Example.App".trim().lowercase() + "A").isValid)
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
        assertTrue(v.errorMessage!!.contains("رزروشده"))
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
}
