package com.sonnik.pin

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonnik.pin.sync.FileSyncWorker
import com.sonnik.pin.ui.isDarkTheme
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sonnik.pin.ui.AppRoot
import com.sonnik.pin.ui.PinViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themes = (application as PinApp).themeSettings
        setContent {
            val mode by themes.mode.collectAsStateWithLifecycle()
            val dark = isDarkTheme(mode)
            // Значки строки состояния — под выбранную тему, а не под системную.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            AppRoot(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // Пока приложение было в фоне, могла пройти граница дня.
        viewModel.refresh()
        // Хранение файлом: подтянуть правки с другого телефона.
        if ((application as PinApp).fileSync.state.value.enabled) FileSyncWorker.requestSoon(this, delaySeconds = 0)
    }
}

private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
