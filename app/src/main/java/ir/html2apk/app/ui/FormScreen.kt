@file:OptIn(ExperimentalMaterial3Api::class)

package ir.html2apk.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.html2apk.app.ui.theme.Emerald
import ir.html2apk.app.ui.theme.Emerald400
import ir.html2apk.app.ui.theme.EmeraldChipBg
import ir.html2apk.app.ui.theme.EmeraldChipText
import ir.html2apk.app.buildengine.IconProcessor
import ir.html2apk.app.model.FormState
import ir.html2apk.app.model.Orientation
import ir.html2apk.app.util.Validators
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.Phase
import ir.html2apk.app.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FormScreen(state: UiState, vm: BuildViewModel) {
    val form = state.form
    val context = LocalContext.current
    val pkgValidation = remember(form.packageName) { Validators.validatePackageName(form.packageName) }
    val canBuild = form.inputUri != null &&
        pkgValidation.isValid &&
        state.phase != Phase.RUNNING

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::onFilePicked) }

    // ---- launcher-icon picking: two explicit sources, gallery OR file ----
    // "از گالری" opens the device's own gallery/photos app (ACTION_PICK);
    // "از فایل" opens the system document picker (SAF, needs no permission).
    // Before the gallery path runs, media read permission is requested —
    // the requested flow the user asked for when picking an icon image.
    val iconPermissions = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> result.data?.data?.let(vm::onIconPicked) }

    val iconFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::onIconPicked) }

    fun openGallery() {
        galleryPicker.launch(
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        )
    }

    val iconPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (!grants.values.all { it }) {
            // Permission denied — the system gallery picker itself still works
            // (it grants read access to the picked image), so the flow is never
            // blocked; the user is told what happened and what to do instead.
            vm.showError(
                "دسترسی خواندن تصاویر داده نشد — گالری همچنان کار می‌کند، یا آیکون را با گزینهٔ «از فایل» انتخاب کنید"
            )
        }
        openGallery()
    }

    fun launchGallerySelection() {
        val granted = iconPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            openGallery()
        } else {
            iconPermissionLauncher.launch(iconPermissions)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- header ----
        Column {
            Text(
                text = "HTML2APK",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "ساخت اپلیکیشن اندروید از HTML/ZIP — کاملاً آفلاین، روی خود دستگاه",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- error banner ----
        if (state.error != null) {
            ErrorBanner(message = state.error!!, onDismiss = vm::dismissError)
        }

        // ---- input file ----
        SectionCard {
            Text(
                text = "۱. فایل ورودی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            if (form.inputUri != null) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                filePicker.launch(arrayOf("text/html", "application/zip"))
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = form.inputName ?: "index.html",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = formSizeLabel(form),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "تغییر",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Button(
                    onClick = { filePicker.launch(arrayOf("text/html", "application/zip")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("انتخاب فایل HTML یا ZIP")
                }
                Text(
                    text = "فایل HTML تکی، یا ZIP شامل index.html و منابع آن (css/js/تصاویر)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- app identity ----
        SectionCard {
            Text(
                text = "۲. مشخصات اپلیکیشن",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            H2ATextField(
                value = form.appName,
                onValueChange = { v -> vm.updateForm { it.copy(appName = v) } },
                label = "نام اپلیکیشن",
                placeholder = { Text("همان چیزی که بنویسید، همان می‌شود") },
                supportingText = {
                    val inputName = form.inputName
                    when {
                        form.appName.isNotBlank() -> Text(
                            "نام اپ: «${form.appName.trim()}» — دقیقاً همین نام ثبت می‌شود",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        inputName != null -> Text(
                            "اگر خالی بماند، نام فایل یعنی «${Validators.deriveAppName(inputName)}» به‌عنوان نام اپ ثبت می‌شود",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Text(
                            "اگر خالی بماند، نام فایل ورودی به‌عنوان نام اپ ثبت می‌شود",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            H2ATextField(
                value = form.packageName,
                onValueChange = { v -> vm.updateForm { it.copy(packageName = v) } },
                label = "نام پکیج (Package Name)",
                placeholder = { Text("com.example.myapp") },
                supportingText = {
                    if (form.packageName.isBlank()) {
                        Text(
                            "مثال: com.example.myapp — باید دست‌کم دو بخش با نقطه داشته باشد",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (pkgValidation.isValid) {
                        Text(
                            "نام پکیج معتبر است ✓",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(pkgValidation.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    }
                },
                isError = form.packageName.isNotBlank() && !pkgValidation.isValid,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                H2ATextField(
                    value = form.versionCode,
                    onValueChange = { v -> vm.updateForm { it.copy(versionCode = v.filter { c -> c.isDigit() }) } },
                    label = "کد نسخه",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                H2ATextField(
                    value = form.versionName,
                    onValueChange = { v -> vm.updateForm { it.copy(versionName = v) } },
                    label = "نام نسخه",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- orientation ----
        SectionCard {
            Text(
                text = "۳. جهت صفحه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
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

        // ---- icon ----
        // Two explicit sources: the device gallery (with the media-permission
        // request the user asked for) or any file via the system picker.
        SectionCard {
            Text(
                text = "۴. آیکون (اختیاری)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            if (form.iconUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPreview(uri = form.iconUri)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "آیکون انتخابی (خودکار به مربع بریده و در همهٔ تراکم‌ها جایگزین می‌شود)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = vm::clearIcon) {
                        Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("حذف")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = ::launchGallerySelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("از گالری")
                }
                OutlinedButton(
                    onClick = { iconFilePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("از فایل")
                }
            }
            Text(
                text = "تصویر آیکون را می‌توانید مستقیم از گالری دستگاه یا از فایل‌های گوشی انتخاب کنید",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- signing ----
        CollapsibleSection(
            title = "تنظیمات امضا (Keystore)",
            subtitle = "کلید به‌صورت خودکار روی دستگاه ساخته و ذخیره می‌شود",
        ) {
            Spacer(Modifier.height(10.dp))
            InfoNote(
                "اولین ساخت: کلید RSA-2048 با اعتبار ۳۰ سال ساخته می‌شود. ساخت‌های بعدی از " +
                    "همان کلید استفاده می‌کنند تا بتوانید نسخه‌های قبلی اپ را ارتقا دهید."
            )
            H2ATextField(
                value = form.keystorePassword,
                onValueChange = { v -> vm.updateForm { it.copy(keystorePassword = v) } },
                label = "گذرواژهٔ Keystore (اختیاری)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            H2ATextField(
                value = form.keyAlias,
                onValueChange = { v -> vm.updateForm { it.copy(keyAlias = v) } },
                label = "نام کلید (Alias)",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- manifest options ----
        CollapsibleSection(
            title = "تنظیمات مانیفست",
            subtitle = "دسترسی‌ها و سایر پارامترهای اپ خروجی",
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "دسترسی اینترنت (INTERNET)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "برای اپ‌های آفلاین می‌توانید خاموش کنید تا هیچ دسترسی شبکه‌ای نباشد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.internetPermission,
                    onCheckedChange = { v -> vm.updateForm { it.copy(internetPermission = v) } },
                )
            }
        }

        // ---- build button ----
        // The original signature CTA: emerald gradient pill with a soft glow.
        Button(
            onClick = vm::startBuild,
            enabled = canBuild,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (canBuild) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(Emerald, Emerald400, Emerald),
                                ),
                                RoundedCornerShape(16.dp),
                            )
                        } else {
                            Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.phase == Phase.FAILED)
                        "تلاش مجدد برای ساخت"
                    else "ساخت APK امضاشده",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (!canBuild && form.inputUri == null) {
            Text(
                text = "برای شروع، ابتدا فایل ورودی را انتخاب کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "تمام پردازش‌ها (پچ مانیفست، zipalign، امضا) بدون هیچ سروری، روی همین دستگاه انجام می‌شود.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formSizeLabel(form: FormState): String {
    val size = form.inputSize ?: return "اندازه نامشخص"
    return when {
        size >= 1024 * 1024 -> "%.1f MB".format(size / 1024f / 1024f)
        size >= 1024 -> "%.1f KB".format(size / 1024f)
        else -> "$size B"
    }
}

@Composable
private fun IconPreview(uri: android.net.Uri) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            IconProcessor.loadPreview(context, uri, 256)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "پیش‌نمایش آیکون",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
        )
    } else {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}
