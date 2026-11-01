package com.sonnik.pin.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.pin.BuildConfigInfo
import com.sonnik.pin.engine.Changelog
import com.sonnik.pin.engine.ChangelogEntry
import java.util.Locale

/** Журнал на языке системы (русский или английский) из assets. */
fun loadChangelog(context: Context): List<ChangelogEntry> {
    val file = if (Locale.getDefault().language == "en") "changelog_en.md" else "changelog_ru.md"
    return runCatching { context.assets.open(file).bufferedReader().use { Changelog.parse(it.readText()) } }.getOrDefault(emptyList())
}

/** История версий: что менялось в каждом выпуске. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { loadChangelog(context) }
    val current = remember { BuildConfigInfo.versionName(context) }
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История версий") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.version }) { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text("Версия ${entry.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (entry.version == current) {
                                Text(
                                    "  · установлена",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        entry.items.forEach { item ->
                            Row {
                                Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                                Text(item, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
