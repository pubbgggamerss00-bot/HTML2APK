package ir.html2apk.app.buildengine

import android.content.Context
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
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
 * First run: generates an RSA-2048 key pair + 30 year self-signed X.509 certificate
 * (BouncyCastle) and stores it as PKCS12 in the app's internal storage, so later
 * builds can *upgrade* previously generated apps instead of replacing them.
 */
object KeystoreManager {

    private const val KS_FILE_NAME = "html2apk-signing.keystore"
    private const val DEFAULT_ALIAS = "html2apk"
    private const val DEFAULT_PASSWORD = "html2apk"

    class SigningKey(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
        val alias: String,
        val sha256Fingerprint: String,
        val createdNew: Boolean,
    )

    private fun ksFile(context: Context): File = File(context.filesDir, KS_FILE_NAME)

    fun keystoreExists(context: Context): Boolean = ksFile(context).exists()

    fun getOrCreate(context: Context, password: String, alias: String): SigningKey {
        registerBouncyCastle()
        val pass = (password.ifBlank { DEFAULT_PASSWORD }).toCharArray()
        val file = ksFile(context)
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
        val notAfter = Date(now + 30L * 365 * 24 * 3600 * 1000) // 30 years
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

    private fun registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private fun fingerprintOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
