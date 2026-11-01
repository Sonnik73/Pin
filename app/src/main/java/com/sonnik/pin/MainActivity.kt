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
        handleIntent(intent)
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Кнопки уведомлений открывают нужный экран. */
    private fun handleIntent(intent: android.content.Intent?) {
        intent?.getStringExtra(EXTRA_SCREEN)?.let { viewModel.pendingScreen.value = it }
        // «Открыть в Pin» / «Поделиться → Люди в Pin» для файла .vcf.
        val type = intent?.type.orEmpty()
        if (type.contains("vcard", ignoreCase = true) || type == "text/directory") {
            val uri = when (intent?.action) {
                android.content.Intent.ACTION_VIEW -> intent.data
                android.content.Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else -> null
            }
            uri?.let { viewModel.loadPeopleFile(it); setIntent(android.content.Intent()) }
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

/** Экстра Intent: какой экран открыть (`review`). */
const val EXTRA_SCREEN = "com.sonnik.pin.screen"

private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
