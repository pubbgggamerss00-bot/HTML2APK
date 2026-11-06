package ir.html2apk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ir.html2apk.app.ui.AppRoot
import ir.html2apk.app.ui.theme.HTML2APKTheme
import ir.html2apk.app.viewmodel.BuildViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BuildAppRoot()
        }
    }
}

@Composable
private fun BuildAppRoot() {
    val vm: BuildViewModel = viewModel()
    val state by vm.state.collectAsState()

    HTML2APKTheme {
        AppRoot(state, vm)
    }
}
