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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
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
import ir.html2apk.app.ui.theme.JetBrainsMono
import ir.html2apk.app.ui.theme.ContainerLowest
import ir.html2apk.app.ui.theme.LogError
import ir.html2apk.app.ui.theme.LogStage
import ir.html2apk.app.ui.theme.LogStdout
import ir.html2apk.app.ui.theme.LogSuccess
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.UiState
import kotlinx.coroutines.delay

@Composable
fun ProgressScreen(state: UiState, vm: BuildViewModel) {
    val doneCount = state.stageDurationsMs.count { it != null }
    val overall = doneCount / BuildStage.COUNT.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- header ----
        Text(
            text = "در حال ساخت…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = overall,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$doneCount/${BuildStage.COUNT}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- stage list ----
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BuildStage.entries.forEach { stage ->
                StageRow(state, stage)
            }
        }

        // ---- terminal log (always LTR, monospace) ----
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ContainerLowest,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(state.logs.size) {
                if (state.logs.isNotEmpty()) {
                    listState.animateScrollToItem(state.logs.size - 1)
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
                    itemsIndexed(state.logs) { _, line ->
                        LogRow(line)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = vm::cancelBuild,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("لغو ساخت")
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
                    androidx.compose.material3.CircularProgressIndicator(
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

private fun formatElapsed(elapsed: Long?): String? = null // (kept simple: durations are formatted inline)
