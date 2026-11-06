package ir.html2apk.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.html2apk.app.ui.theme.Container
import ir.html2apk.app.ui.theme.OnSurfaceVariantNavy
import ir.html2apk.app.ui.theme.Primary
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.Phase
import ir.html2apk.app.viewmodel.UiState

/** The four tabs of the app — the exact structure the original v3.4.0 had. */
enum class AppTab(val label: String, val icon: ImageVector) {
    BUILD("ساخت پکیج", Icons.Filled.Build),
    COMPILER("کامپایلر", Icons.Filled.Terminal),
    OUTPUTS("خروجی‌ها", Icons.Filled.Inventory2),
    SDK("تنظیمات SDK", Icons.Filled.Settings),
}

@Composable
fun AppRoot(state: UiState, vm: BuildViewModel) {
    var tab by rememberSaveable { mutableStateOf(AppTab.BUILD) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // live theme background behind everything
        LiveAuroraBackground(modifier = Modifier.matchParentSize())

        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 })
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "tabContent",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { current ->
                when (current) {
                    AppTab.BUILD -> BuildScreen(state, vm)
                    AppTab.COMPILER -> CompilerScreen(state, vm)
                    AppTab.OUTPUTS -> OutputsScreen(state, vm)
                    AppTab.SDK -> SettingsScreen(state, vm)
                }
            }

            AppTabBar(
                current = tab,
                compilerActive = state.phase == Phase.RUNNING,
                onSelect = { tab = it },
            )
        }
    }

    // refresh outputs whenever the tab becomes visible or a build finishes
    LaunchedEffect(tab, state.phase) {
        if (tab == AppTab.OUTPUTS) vm.refreshOutputs()
    }
}

/**
 * Custom live bottom tab bar: animated scale/tint on selection, a breathing
 * emerald pill behind the active icon and a pulsing badge on the compiler tab
 * while a build is running.
 */
@Composable
private fun AppTabBar(
    current: AppTab,
    compilerActive: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    Surface(
        color = Container.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(66.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == current
                val tint by animateFloatAsState(if (selected) 1f else 0.55f, label = "tabAlpha")
                val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "tabScale")
                val fg = if (selected) Primary else OnSurfaceVariantNavy

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            BreathingGlow(
                                color = Primary,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .scale(scale)
                                .background(
                                    if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = fg,
                                modifier = Modifier.size(19.dp),
                            )
                            if (tab == AppTab.COMPILER && compilerActive) {
                                PulsingDot(
                                    color = MaterialTheme.colorScheme.secondary,
                                    size = 8.dp,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = fg,
                        modifier = Modifier
                            .padding(bottom = 7.dp)
                            .alpha(tint),
                    )
                }
            }
        }
    }
}
