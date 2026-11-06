@file:OptIn(ExperimentalMaterial3Api::class)

package ir.html2apk.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.html2apk.app.buildengine.IconProcessor
import ir.html2apk.app.model.FormState
import ir.html2apk.app.ui.theme.Emerald
import ir.html2apk.app.ui.theme.Emerald400
import ir.html2apk.app.ui.theme.EmeraldDeep
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.Phase
import ir.html2apk.app.viewmodel.UiState
import ir.html2apk.app.util.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tab 1 — «ساخت پکیج»: web bundle + app identity + adaptive icon.
 * Signing & manifest options live in the «تنظیمات SDK» tab (same as v3.4.0).
 */
@Composable
fun BuildScreen(state: UiState, vm: BuildViewModel) {
    val form = state.form
    val context = LocalContext.current
    val pkgValidation = remember(form.packageName) { Validators.validatePackageName(form.packageName) }
    val appNameValidation = remember(form.appName) { Validators.validateAppName(form.appName.trim().ifBlank { "Web App" }) }
    val versionNameValidation = remember(form.versionName) { Validators.validateVersionName(form.versionName) }
    val versionCodeValidation = remember(form.versionCode) { Validators.validateVersionCode(form.versionCode) }
    val fieldsValid = appNameValidation.isValid &&
        versionNameValidation.isValid &&
        versionCodeValidation.isValid
    val canBuild = form.inputUri != null &&
        pkgValidation.isValid &&
        fieldsValid &&
        state.phase != Phase.RUNNING

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::onFilePicked) }

    // The MIME list is deliberately wide: many providers (Telegram, downloads,
    // file managers) store .html/.zip files as application/octet-stream, and a
    // narrow filter makes the files invisible — the «انتخاب فایل» then appears
    // broken. Acceptance of the picked file is validated by extension in the VM
    // (v3.4.0 rule: «فقط ZIP یا HTML پذیرفته می‌شود»).

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
                text = "تبدیل کامل آفلاین بسته‌های وب (HTML/ZIP) به APK امضاشده و قابل نصب، مستقیماً روی گوشی",
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
                text = "۱. آرشیو فایل وب (Web Bundle)",
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
                            text = "تغییر فایل",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "text/html",
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            )
                        )
                    },
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
                text = "۲. مشخصات و متادیتای اپلیکیشن",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            H2ATextField(
                value = form.appName,
                onValueChange = { v -> vm.updateForm { it.copy(appName = v) } },
                label = "نام اپلیکیشن (Application Title)",
                placeholder = { Text("مثال: پرتال سازمانی یا فروشگاه") },
                isError = form.appName.isNotBlank() && !appNameValidation.isValid,
                supportingText = {
                    val inputName = form.inputName
                    when {
                        form.appName.isNotBlank() && !appNameValidation.isValid -> Text(
                            appNameValidation.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
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
                label = "شناسه یکتا (Package Name / ID)",
                placeholder = { Text("com.example.myapp") },
                supportingText = {
                    if (form.packageName.isBlank()) {
                        Text(
                            "قالب صحیح: com.vendor.appname (حروف کوچک، عدد و _) — دست‌کم دو بخش با نقطه",
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
                    label = "کد بیلد (Version Code)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = form.versionCode.isNotBlank() && !versionCodeValidation.isValid,
                    supportingText = {
                        if (form.versionCode.isNotBlank() && !versionCodeValidation.isValid) {
                            Text(versionCodeValidation.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                H2ATextField(
                    value = form.versionName,
                    onValueChange = { v -> vm.updateForm { it.copy(versionName = v) } },
                    label = "ورژن نمایشی (Name)",
                    isError = form.versionName.isNotBlank() && !versionNameValidation.isValid,
                    supportingText = {
                        if (form.versionName.isNotBlank() && !versionNameValidation.isValid) {
                            Text(versionNameValidation.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // v3.4.0 «نمایش در لانچر» toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "نمایش در لانچر",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "اگر خاموش شود، اپ ساخته‌شده آیکونی در صفحهٔ اصلی ندارد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.showInLauncher,
                    onCheckedChange = { v -> vm.updateForm { it.copy(showInLauncher = v) } },
                )
            }
        }

        // ---- adaptive icon ----
        // Two explicit sources: the device gallery (with the media-permission
        // request the user asked for) or any file via the system picker.
        SectionCard {
            Text(
                text = "۳. آیکون تطبیقی (Adaptive Icon)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            if (form.iconUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPreview(uri = form.iconUri)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "آیکون انتخابی — رندر خودکار در سایزهای mdpi تا xxxhdpi",
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
                    Text("انتخاب عکس از گالری")
                }
                OutlinedButton(
                    onClick = { iconFilePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("انتخاب از فایل")
                }
            }
        }

        // ---- build CTA — the live shimmering emerald pill ----
        ShimmerCtaButton(
            enabled = canBuild,
            label = if (state.phase == Phase.FAILED) "تلاش مجدد برای ساخت" else "ساخت APK امضاشده",
            onClick = vm::startBuild,
        )

        if (!canBuild && form.inputUri == null) {
            Text(
                text = "برای شروع، ابتدا فایل ورودی را انتخاب کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "تنظیمات امضا (Keystore)، مانیفست و دسترسی‌ها در تب «تنظیمات SDK» انجام می‌شود. " +
                "پایپ‌لاین و کنسول زندهٔ ساخت در تب «کامپایلر» دیده می‌شود.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The signature CTA of the live theme: emerald gradient pill with a light
 * sweep that travels across it forever while the button is enabled.
 */
@Composable
fun ShimmerCtaButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ctaSweep")
    val sweep by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweep",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
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
                    if (enabled) {
                        Modifier.background(
                            Brush.horizontalGradient(listOf(EmeraldDeep, Emerald, Emerald400, Emerald)),
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
            if (enabled) {
                // travelling light sweep
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val bandW = w * 0.35f
                    val startX = w * sweep - bandW
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            start = androidx.compose.ui.geometry.Offset(startX, 0f),
                            end = androidx.compose.ui.geometry.Offset(startX + bandW, size.height),
                        ),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
