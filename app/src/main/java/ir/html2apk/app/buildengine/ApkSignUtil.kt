package ir.html2apk.app.buildengine

import com.android.apksig.ApkSigner
import java.io.File

/**
 * Signs an aligned (unsigned) APK with v1 + v2 + v3 signature schemes using apksig.
 * Everything happens locally in temp files — no network, no external tool.
 */
object ApkSignUtil {

    fun sign(unsignedApk: ByteArray, key: KeystoreManager.SigningKey): ByteArray {
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "HTML2APK",
            key.privateKey,
            listOf(key.certificate),
        ).build()

        val input = File.createTempFile("html2apk-unsigned", ".apk")
        val output = File.createTempFile("html2apk-signed", ".apk")
        try {
            input.writeBytes(unsignedApk)

            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setMinSdkVersion(24)
                .build()
            signer.sign()

            return output.readBytes()
        } finally {
            input.delete()
            output.delete()
        }
    }
}
