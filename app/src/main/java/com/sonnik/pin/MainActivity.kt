package com.sonnik.pin

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        setContent { AppRoot(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        // Пока приложение было в фоне, могла пройти граница дня.
        viewModel.refresh()
    }
}
