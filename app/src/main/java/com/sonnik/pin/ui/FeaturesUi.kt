package com.sonnik.pin.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonnik.pin.engine.Feature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Какие функции выключены — на этом устройстве (SharedPreferences), в синхронизацию не попадает. */
class FeatureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("features", Context.MODE_PRIVATE)
    private val _disabled = MutableStateFlow(Feature.parseDisabled(prefs.getString(KEY, null)))
    val disabled: StateFlow<Set<Feature>> = _disabled.asStateFlow()

    fun isOn(feature: Feature): Boolean = feature !in _disabled.value

    fun set(feature: Feature, on: Boolean) {
        val next = if (on) _disabled.value - feature else _disabled.value + feature
        prefs.edit().putString(KEY, Feature.formatDisabled(next)).apply()
        _disabled.value = next
    }

    private companion object {
        const val KEY = "disabled"
    }
}

/** Выключенные функции для экранов; по умолчанию (в т. ч. на скриншотах) всё включено. */
val LocalDisabledFeatures = compositionLocalOf<Set<Feature>> { emptySet() }

/** Включена ли функция — для условий в разметке. */
@Composable
fun Feature.on(): Boolean = this !in LocalDisabledFeatures.current

/** Настройки → «Функции»: выключить ненужное. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(disabled: Set<Feature>, onSet: (Feature, Boolean) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Функции") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Выключенное просто прячется: задачи, привычки, люди и планы остаются в памяти и в файлах. Включите — всё вернётся.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(Feature.entries, key = { it.name }) { f ->
                val on = f !in disabled
                Row(
                    Modifier.fillMaxWidth().clickable { onSet(f, !on) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(f.title, style = MaterialTheme.typography.bodyLarge)
                        Text(f.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = on, onCheckedChange = { onSet(f, it) })
                }
            }
        }
    }
}
