package ir.html2apk.app.buildengine

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.IOException

/**
 * Signs an aligned (unsigned) APK with v1 + v2 + v3 signature schemes using apksig,
 * then verifies the result with the authoritative [ApkVerifier] — exactly the
 * v3.4.0 pipeline («امضای تولیدشده تأیید نشد: …» when verification fails).
 * Everything happens locally in temp files, fully streamed — no full-size in-memory
 * copies, no network, no external tool.
 */
object ApkSignUtil {

    /** Signs [input] and writes the result to [output]. Both are temp files owned by the engine. */
    fun sign(input: File, output: File, key: KeystoreManager.SigningKey) {
        require(input.isFile && input.length() > 0) {
            "فایل APK برای امضا خالی یا ناموجود است"
        }
        try {
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "HTML2APK",
                key.privateKey,
                listOf(key.certificate),
            ).build()

            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                // Match the lowest Android version that still supports modern WebView +
                // our template (builder minSdk is 26; 24 is safe for apksig).
                .setMinSdkVersion(24)
                .build()
            signer.sign()
        } catch (t: Throwable) {
            throw IOException(
                "امضای APK ناموفق بود: ${t.message ?: t.javaClass.simpleName}",
                t,
            )
        }

        if (!output.isFile || output.length() == 0L) {
            throw IOException("امضا خروجی تولید نکرد — فایل خروجی خالی است")
        }

        verify(output)
    }

    /**
     * Authoritative post-sign verification (the v3.4.0 «امضای تولیدشده تأیید نشد» step):
     * the signed APK must verify with v1 (legacy) and v2/v3 (modern Android).
     */
    fun verify(signedApk: File) {
        try {
            val result = ApkVerifier.Builder(signedApk)
                .setMinCheckedPlatformVersion(24)
                .build()
                .verify()
            if (!result.isVerified) {
                val issues = result.allErrors.take(3).joinToString("; ") { it.toString() }
                throw IOException("امضای تولیدشده تأیید نشد: $issues")
            }
        } catch (e: IOException) {
            throw e
        } catch (t: Throwable) {
            throw IOException(
                "امضای تولیدشده تأیید نشد: ${t.message ?: t.javaClass.simpleName}",
                t,
            )
        }
    }
}
