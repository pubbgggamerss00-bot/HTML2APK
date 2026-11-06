package ir.html2apk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.html2apk.app.model.BuildStage
import ir.html2apk.app.model.LogLine
import ir.html2apk.app.model.LogType
import ir.html2apk.app.ui.theme.ContainerLowest
import ir.html2apk.app.ui.theme.JetBrainsMono
import ir.html2apk.app.ui.theme.LogError
import ir.html2apk.app.ui.theme.LogStage
import ir.html2apk.app.ui.theme.LogStdout
import ir.html2apk.app.ui.theme.LogSuccess
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.Phase
import ir.html2apk.app.viewmodel.UiState
import kotlinx.coroutines.delay

/**
 * Tab 2 — «کامپایلر»: live status pill, the 5-step compile pipeline,
 * the streaming compiler console and the start/cancel controls.
 */
@Composable
fun CompilerScreen(state: UiState, vm: BuildViewModel) {
    val form = state.form
    val running = state.phase == Phase.RUNNING
    val doneCount = state.stageDurationsMs.count { it != null }
    val overall = doneCount / BuildStage.COUNT.toFloat()
    val canCompile = form.inputUri != null && !running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "کامپایلر",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            PulsingDot(color = statusColor(running, state.phase == Phase.FAILED, state.phase == Phase.SUCCESS))
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (state.phase) {
                    Phase.RUNNING -> "در حال ساخت…"
                    Phase.SUCCESS -> "ساخت موفق بود"
                    Phase.FAILED -> "ساخت ناموفق بود"
                    else -> "آمادهٔ ساخت"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- live status card ----
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "پیشرفت:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$doneCount/${BuildStage.COUNT}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = overall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // success result block
            if (state.phase == Phase.SUCCESS && state.output != null) {
                val out = state.output!!
                Spacer(Modifier.height(12.dp))
                ResultChip(out.displayName, out.sizeBytes)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.installOutput(out) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نصب مستقیم پکیج (Install APK)")
                    }
                    OutlinedButton(onClick = { vm.shareOutput(out) }) {
                        Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(18.dp))
                    }
                }
            }

            if (state.phase == Phase.FAILED && state.error != null) {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(message = state.error!!, onDismiss = vm::dismissError)
            }
        }

        // ---- pipeline ----
        SectionCard {
            Text(
                text = "پایپ‌لاین کامپایل و ساخت",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BuildStage.entries.forEach { stage ->
                    StageRow(state, stage)
                }
            }
        }

        // ---- compiler console ----
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "کنسول کامپایلر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.logs.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            CompilerConsole(state.logs, height = 230.dp)
        }

        // ---- start / cancel ----
        if (running) {
            OutlinedButton(
                onClick = vm::cancelBuild,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("لغو ساخت", fontWeight = FontWeight.Bold)
            }
        } else {
            ShimmerCtaButton(
                enabled = canCompile,
                label = if (state.phase == Phase.FAILED) "تلاش مجدد برای کامپایل" else "شروع کامپایل",
                onClick = vm::startBuild,
            )
            if (form.inputUri == null) {
                Text(
                    text = "ابتدا از تب «ساخت پکیج» فایل ورودی را انتخاب کنید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- engine info ----
        SectionCard {
            EngineInfoRow(
                icon = { Icon(Icons.Filled.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = "موتور کامپایل بومی",
                desc = "ویرایشگر باینری AXML، پچ resources.arsc، zipalign دستی و امضای v1/v2/v3 — روی خود دستگاه",
            )
            Spacer(Modifier.height(10.dp))
            EngineInfoRow(
                icon = { Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = "کامپایلر آفلاین و ایزوله",
                desc = "کامپایل کاملاً محلی است؛ هیچ داده‌ای به سرور ارسال نمی‌شود",
            )
            Spacer(Modifier.height(10.dp))
            EngineInfoRow(
                icon = { Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = "بدون مجوز حافظه (Scoped Storage)",
                desc = "فایل‌ها از طریق MediaStore در Downloads/HTML2APK ذخیره می‌شوند",
            )
        }
    }
}

@Composable
private fun EngineInfoRow(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultChip(name: String, sizeBytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "فایل APK امضا شده در Downloads/HTML2APK ذخیره شد",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$name • ${formatSize(sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StageRow(state: UiState, stage: BuildStage) {
    val duration = state.stageDurationsMs.getOrNull(stage.index)
    val isCurrent = state.currentStage == stage.index
    val isDone = duration != null

    Row(verticalAlignment = Alignment.CenterVertically) {
        // status bubble
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isDone -> {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                isCurrent -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stage.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent || isDone) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDuration(duration, state, isCurrent),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatDuration(duration: Long?, state: UiState, isCurrent: Boolean): String {
    if (duration != null) {
        val seconds = duration / 1000.0
        return if (seconds < 10) "%.1f ثانیه".format(seconds) else "${seconds.toInt()} ثانیه"
    }
    if (isCurrent && state.currentStageStartedAt > 0) {
        // ticking clock for the running stage
        var tick by remember(state.currentStage) { mutableIntStateOf(0) }
        LaunchedEffect(state.currentStage) {
            while (true) {
                delay(200)
                tick++
            }
        }
        val elapsed = (System.currentTimeMillis() - state.currentStageStartedAt) / 1000.0
        return if (elapsed < 10) "%.1f ثانیه".format(elapsed) else "${elapsed.toInt()} ثانیه"
    }
    return ""
}

/** The terminal-style streaming console (always LTR, JetBrains Mono). */
@Composable
fun CompilerConsole(logs: List<LogLine>, height: androidx.compose.ui.unit.Dp) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = ContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "$ ready — waiting for build…",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = LogStdout,
                        )
                    }
                }
                itemsIndexed(logs) { _, line ->
                    LogRow(line)
                }
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    // Log colors exactly like the original terminal: stage lines sky-blue,
    // success lines emerald, stdout slate, errors red.
    val lineColor = when (line.type) {
        LogType.CMD -> LogStage
        LogType.OK -> LogSuccess
        LogType.INFO -> LogStdout
        LogType.ERROR -> LogError
    }
    val prefix = when (line.type) {
        LogType.CMD -> "$ "
        LogType.OK -> "OK "
        LogType.INFO -> "-- "
        LogType.ERROR -> "ERR "
    }
    Row {
        Text(
            text = prefix,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = lineColor,
        )
        Text(
            text = line.text,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = lineColor,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
