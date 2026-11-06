package ir.html2apk.app.buildengine

import com.android.apksig.ApkSigner
import com.android.apksig.util.DataSources
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Signs an aligned (unsigned) APK with v1 + v2 + v3 signature schemes using apksig.
 * Everything happens locally in memory — no network, no external tool.
 */
object ApkSignUtil {

    fun sign(unsignedApk: ByteArray, key: KeystoreManager.SigningKey): ByteArray {
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "HTML2APK",
            key.certificate,
            key.privateKey,
        ).build()

        val signer = ApkSigner.Builder(listOf(signerConfig))
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setMinSdkVersion(24)
            .build()

        val output = ByteArrayOutputStream(unsignedApk.size + 16 * 1024)
        signer.sign(DataSources.asDataSource(ByteBuffer.wrap(unsignedApk)), output)
        return output.toByteArray()
    }
}
