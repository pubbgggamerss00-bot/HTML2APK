package ir.html2apk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ir.html2apk.app.ui.FormScreen
import ir.html2apk.app.ui.ProgressScreen
import ir.html2apk.app.ui.ResultScreen
import ir.html2apk.app.ui.theme.HTML2APKTheme
import ir.html2apk.app.viewmodel.BuildViewModel
import ir.html2apk.app.viewmodel.Phase

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HTML2APKTheme {
                BuildAppRoot()
            }
        }
    }
}

@Composable
private fun BuildAppRoot() {
    val vm: BuildViewModel = viewModel()
    val state by vm.state.collectAsState()

    when (state.phase) {
        Phase.RUNNING -> ProgressScreen(state, vm)
        Phase.SUCCESS -> ResultScreen(state, vm)
        else -> FormScreen(state, vm)
    }
}
