package com.sonnik.pin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Значение editorId для новой задачи. */
private const val NEW = ""

@Composable
fun AppRoot(viewModel: PinViewModel) {
    PinTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            // null — открыт список; "" — новая задача; иначе id редактируемой.
            var editorId by rememberSaveable { mutableStateOf<String?>(null) }
            val state = ui
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            val id = editorId
            if (id != null) {
                val entry = if (id == NEW) null else state.entries[id]
                key(id) {
                    EditorScreen(
                        entry = entry,
                        today = state.today,
                        onSave = { viewModel.save(entry?.id, it) },
                        onDelete = { entry?.let { viewModel.delete(it.id) } },
                        onToggleDone = { done -> entry?.let { viewModel.setDone(it.id, done) } },
                        onClose = { editorId = null },
                    )
                }
            } else {
                TodayScreen(
                    ui = state,
                    onOpen = { editorId = it },
                    onAdd = { editorId = NEW },
                    onToggle = viewModel::setDone,
                    onDayBoundary = viewModel::setDayBoundary,
                )
            }
        }
    }
}
