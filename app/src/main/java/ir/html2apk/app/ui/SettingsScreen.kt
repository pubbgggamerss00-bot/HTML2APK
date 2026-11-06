package ir.html2apk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.html2apk.app.model.Orientation
import ir.html2apk.app.ui.theme.EmeraldChipBg
import ir.html2apk.app.ui.theme.EmeraldChipText
import ir.html2apk.app.ui.theme.JetBrainsMono
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.UiState

/**
 * Tab 4 — «تنظیمات SDK»: signing identity, manifest & permission toggles and
 * the about card — the exact sections the v3.4.0 settings screen had.
 */
@Composable
fun SettingsScreen(state: UiState, vm: BuildViewModel) {
    val form = state.form
    val ks by vm.keystoreInfo.collectAsState()
    val context = LocalContext.current
    val appVersion = remember {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            "نسخهٔ ${pi.versionName} (کد بیلد ${pi.longVersionCode})"
        } catch (_: Exception) {
            ""
        }
    }

    // reflect the current signing identity (re-read when password/alias change)
    LaunchedEffect(form.keystorePassword, form.keyAlias) {
        vm.refreshKeystoreInfo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- header ----
        Column {
            Text(
                text = "تنظیمات SDK",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "تنظیمات کلید امضا، مانیفست و کش",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.error != null) {
            ErrorBanner(message = state.error!!, onDismiss = vm::dismissError)
        }

        // ---- self-signed keystore ----
        SectionCard {
            Text(
                text = "امضای خودکار (Self-Signed Keystore)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            InfoNote(
                "اولین ساخت: کلید RSA-2048 با اعتبار ۳۰ سال روی دستگاه ساخته و در حافظهٔ داخلی نگه‌داری می‌شود؛ " +
                    "ساخت‌های بعدی از همان کلید استفاده می‌کنند تا بتوانید اپ‌های قبلی را ارتقا دهید."
            )
            Spacer(Modifier.height(6.dp))

            H2ATextField(
                value = form.keystorePassword,
                onValueChange = { v -> vm.updateForm { it.copy(keystorePassword = v) } },
                label = "رمز Keystore (اختیاری — پیش‌فرض: html2apk)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            H2ATextField(
                value = form.keyAlias,
                onValueChange = { v -> vm.updateForm { it.copy(keyAlias = v) } },
                label = "نام کلید (Alias)",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            val ksError = ks.error
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyInfoLine(
                    label = "وضعیت",
                    value = if (ks.exists) "کلید ساخته شده و آماده است"
                    else "در اولین بیلد ساخته می‌شود",
                )
                if (ks.exists && ksError == null) {
                    KeyInfoLine(label = "نام کلید", value = ks.alias ?: "—")
                    KeyInfoLine(label = "الگوریتم", value = ks.algorithm ?: "—")
                    KeyInfoLine(label = "اعتبار گواهی", value = ks.validUntil?.let { "تا $it" } ?: "—")
                    KeyInfoLine(label = "SHA-256", value = ks.sha256 ?: "—", mono = true)
                }
                if (ksError != null) {
                    Text(
                        text = ksError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = vm::regenerateKeystore,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("بازتولید کلید (ریست امضا)")
            }
            Text(
                text = "هشدار: پس از ریست کلید، آپدیت روی APKهای قبلی ساخته‌شده ممکن نیست و باید اپ قبلی حذف شود.",
                style = MaterialTheme.typography.bodySmall,
                color = ir.html2apk.app.ui.theme.Warning,
            )
        }

        // ---- manifest & permissions ----
        SectionCard {
            Text(
                text = "کلید اختصاصی Keystore، دسترسی اینترنت و ترافیک HTTP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "مجوز شبکه و اینترنت (android.permission.INTERNET)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "ارتباط با APIها، وب‌سوکت و لود فونت‌های وب",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.internetPermission,
                    onCheckedChange = { v -> vm.updateForm { it.copy(internetPermission = v) } },
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "اجازه ترافیک HTTP (usesCleartextTraffic)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "برای لود منابع http:// در وب‌ویو (اندروید ۹+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.usesCleartextTraffic,
                    onCheckedChange = { v -> vm.updateForm { it.copy(usesCleartextTraffic = v) } },
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "جهت نمایش صفحه (Orientation)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Orientation.entries.forEach { orientation ->
                    val selected = form.orientation == orientation
                    FilterChip(
                        selected = selected,
                        onClick = { vm.updateForm { it.copy(orientation = orientation) } },
                        label = { Text(orientation.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = EmeraldChipBg,
                            selectedLabelColor = EmeraldChipText,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        // ---- about ----
        SectionCard {
            Text(
                text = "درباره HTML2APK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "تبدیل کامل آفلاین بسته‌های وب (HTML/ZIP) به APK امضاشده و قابل نصب، مستقیماً روی گوشی. " +
                    "موتور شامل ویرایشگر باینری AXML، پچ resources.arsc، zipalign دستی و امضای v1/v2/v3 است.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "کدهای HTML/CSS/JS مستقیماً در رم گوشی و بدون ارسال به سرور بیرونی به فرمت APK رسمی اندروید تبدیل می‌شوند. " +
                    "کامپایل کاملاً محلی است؛ هیچ داده‌ای به سرور ارسال نمی‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (appVersion.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun KeyInfoLine(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) JetBrainsMono else null,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
