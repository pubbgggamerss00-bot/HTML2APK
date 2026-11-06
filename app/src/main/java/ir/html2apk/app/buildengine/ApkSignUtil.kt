package ir.html2apk.app.buildengine

import com.android.apksig.ApkSigner
import java.io.File

/**
 * Signs an aligned (unsigned) APK with v1 + v2 + v3 signature schemes using apksig.
 * Everything happens locally in temp files, fully streamed — no full-size in-memory
 * copies, no network, no external tool.
 */
object ApkSignUtil {

    /** Signs [input] and writes the result to [output]. Both are temp files owned by the engine. */
    fun sign(input: File, output: File, key: KeystoreManager.SigningKey) {
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
            .setMinSdkVersion(24)
            .build()
        signer.sign()

        require(output.isFile && output.length() > 0) { "apksigner produced no output" }
    }
}
