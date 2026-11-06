package ir.html2apk.app.buildengine

import android.content.Context
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Creates / loads the signing keystore completely offline on the device.
 *
 * First run: generates an RSA-2048 key pair + 25 year self-signed X.509 certificate
 * (BouncyCastle) and stores it as PKCS12 in the app's internal storage, so later
 * builds can *upgrade* previously generated apps instead of replacing them
 * («ماندگاری ۲۵ سال • نگهداری در حافظه داخلی» — exactly the v3.4.0 behaviour).
 *
 * A user-provided keystore file (.p12 / .jks) can replace the auto-generated one —
 * the v3.4.0 «انتخاب فایل keystore» flow. When a custom keystore is stored, every
 * build is signed with it until it is removed.
 */
object KeystoreManager {

    private const val KS_FILE_NAME = "html2apk-signing.keystore"
    private const val CUSTOM_KS_FILE_NAME = "html2apk-custom-signing.keystore"
    private const val DEFAULT_ALIAS = "html2apk"
    private const val DEFAULT_PASSWORD = "html2apk"
    /** v3.4.0: «ماندگاری ۲۵ سال». */
    private const val VALIDITY_YEARS = 25L

    class SigningKey(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
        val alias: String,
        val sha256Fingerprint: String,
        val createdNew: Boolean,
    )

    /** Read-only keystore status for the SDK-settings screen. */
    data class KeystoreStatus(
        val exists: Boolean,
        val alias: String? = null,
        val sha256: String? = null,
        val algorithm: String? = null,
        val validUntil: String? = null,
        val error: String? = null,
        /** "custom" when a user-imported keystore will sign the builds, "auto" otherwise. */
        val mode: String? = null,
    )

    private fun ksFile(context: Context): File = File(context.filesDir, KS_FILE_NAME)
    private fun customKsFile(context: Context): File = File(context.filesDir, CUSTOM_KS_FILE_NAME)

    fun keystoreExists(context: Context): Boolean = ksFile(context).exists()

    fun customKeystoreExists(context: Context): Boolean = customKsFile(context).exists()

    /**
     * Reads the current signing identity without creating anything.
     * Used by the SDK-settings screen to show alias / algorithm / validity / fingerprint.
     * When a custom keystore file is stored it takes precedence (mode = "custom").
     */
    fun describe(context: Context, password: String, alias: String): KeystoreStatus {
        val custom = customKsFile(context)
        if (custom.exists() && custom.length() > 0) {
            val status = describeCustom(custom, password)
            return status.copy(mode = "custom")
        }
        val file = ksFile(context)
        if (!file.exists()) return KeystoreStatus(exists = false, mode = "auto")
        return try {
            val pass = (password.ifBlank { DEFAULT_PASSWORD }).toCharArray()
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, pass) }
            val resolved = (alias.ifBlank { DEFAULT_ALIAS }.lowercase())
                .takeIf { ks.containsAlias(it) }
                ?: ks.aliases().toList().firstOrNull()
                ?: return KeystoreStatus(exists = true, error = "keystore موجود است اما کلیدی داخلش نیست", mode = "auto")
            val cert = ks.getCertificate(resolved) as? X509Certificate
                ?: return KeystoreStatus(exists = true, error = "گواهی قابل خواندن نیست", mode = "auto")
            val df = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
            KeystoreStatus(
                exists = true,
                alias = resolved,
                sha256 = fingerprintOf(cert),
                algorithm = "${cert.publicKey.algorithm}-2048",
                validUntil = df.format(cert.notAfter),
                error = null,
                mode = "auto",
            )
        } catch (t: Throwable) {
            KeystoreStatus(
                exists = true,
                error = "خواندن keystore ناموفق بود — اگر رمز دیگری گذاشته‌اید همان را وارد کنید",
                mode = "auto",
            )
        }
    }

    /** Reads the user-imported keystore file (status only). */
    private fun describeCustom(file: File, password: String): KeystoreStatus {
        return try {
            val key = loadCustomKey(file, password)
            val df = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
            KeystoreStatus(
                exists = true,
                alias = key.alias,
                sha256 = key.sha256Fingerprint,
                algorithm = "${key.certificate.publicKey.algorithm}",
                validUntil = df.format(key.certificate.notAfter),
                error = null,
                mode = "custom",
            )
        } catch (t: Throwable) {
            KeystoreStatus(
                exists = true,
                error = "خواندن keystore سفارشی ناموفق بود — رمز keystore را بررسی کنید",
                mode = "custom",
            )
        }
    }

    /**
     * Imports a user keystore file (.p12 / .jks) — the v3.4.0 «انتخاب فایل keystore»
     * flow. The file is copied into internal storage and becomes the signing identity
     * for all subsequent builds until it is removed.
     */
    fun importCustom(context: Context, uri: android.net.Uri, password: String): SigningKey {
        val file = customKsFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("خطا در انتخاب keystore — فایل خوانده نشد")
        return try {
            loadCustomKey(file, password)
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    /** Deletes the imported custom keystore (signing falls back to the auto key). */
    fun clearCustom(context: Context) {
        customKsFile(context).delete()
    }

    /**
     * Loads the first private-key entry from a .p12/.jks file.
     * PKCS12 is tried first, then the legacy JKS format.
     */
    private fun loadCustomKey(file: File, password: String): SigningKey {
        val pass = (password.ifBlank { DEFAULT_PASSWORD }).toCharArray()
        val types = listOf("PKCS12", "JKS")
        var lastError: Throwable? = null
        for (type in types) {
            try {
                val ks = KeyStore.getInstance(type)
                file.inputStream().use { ks.load(it, pass) }
                val alias = ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) }
                    ?: throw IllegalStateException("هیچ کلید خصوصی‌ای در keystore پیدا نشد")
                val key = ks.getKey(alias, pass) as PrivateKey
                val cert = ks.getCertificate(alias) as? X509Certificate
                    ?: throw IllegalStateException("گواهی alias «$alias» قابل خواندن نیست")
                return SigningKey(key, cert, alias, fingerprintOf(cert), createdNew = false)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        val cause = lastError
        throw IllegalStateException(
            cause?.message ?: "فایل keystore معتبر نیست (.p12 / .jks با رمز درست)",
            cause,
        )
    }

    /**
     * Deletes the stored keystore and generates a completely fresh identity.
     * After this, previously built APKs can no longer be upgraded (signature
     * mismatch) — the UI must warn the user before calling this.
     */
    fun regenerate(context: Context, password: String, alias: String): SigningKey {
        ksFile(context).delete()
        return getOrCreate(context, password, alias)
    }

    /**
     * The signing identity actually used by a build: the imported custom keystore
     * file when present, otherwise the auto-generated self-signed key.
     */
    fun getSigningKey(context: Context, password: String, alias: String): SigningKey {
        val custom = customKsFile(context)
        if (custom.exists() && custom.length() > 0) {
            return loadCustomKey(custom, password)
        }
        return getOrCreate(context, password, alias)
    }

    fun getOrCreate(context: Context, password: String, alias: String): SigningKey =
        getOrCreate(context.filesDir, password, alias)

    /** Context-free variant (used by the JVM end-to-end test). */
    fun getOrCreate(ksDir: File, password: String, alias: String): SigningKey {
        registerBouncyCastle()
        val pass = (password.ifBlank { DEFAULT_PASSWORD }).toCharArray()
        val file = File(ksDir, KS_FILE_NAME)
        val requestedAlias = alias.ifBlank { DEFAULT_ALIAS }.lowercase()

        if (file.exists()) {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, pass) }
            val resolved = if (ks.containsAlias(requestedAlias)) {
                requestedAlias
            } else {
                ks.aliases().toList().firstOrNull()
                    ?: throw IllegalStateException("keystore موجود اما بدون کلید است")
            }
            val key = ks.getKey(resolved, pass) as PrivateKey
            val cert = ks.getCertificate(resolved) as X509Certificate
            return SigningKey(key, cert, resolved, fingerprintOf(cert), createdNew = false)
        }

        // ---- generate a fresh key pair + self-signed certificate ----
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 3600 * 1000)
        val notAfter = Date(now + VALIDITY_YEARS * 365 * 24 * 3600 * 1000) // «ماندگاری ۲۵ سال»
        val serial = BigInteger(64, SecureRandom())
        val dn = org.bouncycastle.asn1.x500.X500Name("CN=HTML2APK, O=HTML2APK, C=IR")

        val builder = JcaX509v3CertificateBuilder(dn, serial, notBefore, notAfter, dn, kp.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(kp.private)
        val cert = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(requestedAlias, kp.private, pass, arrayOf(cert))
        file.outputStream().use { ks.store(it, pass) }

        return SigningKey(kp.private, cert, requestedAlias, fingerprintOf(cert), createdNew = true)
    }

    /**
     * Register BouncyCastle robustly on Android.
     * Prefer insertProviderAt(1) so it is preferred over the platform provider
     * for the algorithms we need (avoids "algorithm not found" on some OEMs).
     * Synchronized to avoid concurrent registration races.
     * Silently ignore if already present under any priority.
     */
    @Synchronized
    private fun registerBouncyCastle() {
        val name = BouncyCastleProvider.PROVIDER_NAME
        if (Security.getProvider(name) != null) return
        try {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (t: Throwable) {
            // Some environments reject insertProviderAt; fall back to addProvider.
            try {
                Security.addProvider(BouncyCastleProvider())
            } catch (_: Throwable) {
                // Provider already present under a different path — continue.
            }
        }
    }

    private fun fingerprintOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
