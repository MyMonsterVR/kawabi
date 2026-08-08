package com.mymonstervr.kawabi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession

data class ChangelogEntry(val title: String, val changes: List<String>)

// CHANGELOG.md is hand-authored at the repo root (readable directly on GitHub) and copied
// into this module's assets at build time (app/build.gradle.kts's copyChangelogToAssets) --
// parsed here rather than shipped as structured data since a plain markdown file is the
// easiest thing to keep up to date by hand.
fun parseChangelog(text: String): List<ChangelogEntry> {
    val entries = mutableListOf<ChangelogEntry>()
    var currentTitle: String? = null
    var currentChanges = mutableListOf<String>()
    fun flush() {
        val title = currentTitle ?: return
        entries += ChangelogEntry(title, currentChanges.toList())
    }
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        when {
            line.startsWith("## ") -> {
                flush()
                currentTitle = line.removePrefix("## ").trim()
                currentChanges = mutableListOf()
            }
            trimmed.startsWith("- ") -> currentChanges += trimmed.removePrefix("- ").trim()
            // A wrapped continuation of the previous bullet (source markdown line-wraps
            // long bullets across several lines for readable diffs) -- append rather than
            // drop, or only the wrapped line's first sentence would ever show.
            trimmed.isNotEmpty() && currentChanges.isNotEmpty() ->
                currentChanges[currentChanges.lastIndex] = "${currentChanges.last()} $trimmed"
        }
    }
    flush()
    return entries
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = remember {
        runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }
            .map(::parseChangelog)
            .getOrDefault(emptyList())
    }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = { Text("Changelog", fontWeight = FontWeight.Bold, color = NightSession.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
            )
        },
    ) { padding ->
        com.mymonstervr.kawabi.app.common.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(NightSession.Background), contentAlignment = Alignment.Center) {
                    Text("No changelog yet", color = NightSession.TextDim)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize().background(NightSession.Background),
                ) {
                    items(entries) { entry -> ChangelogEntryCard(entry) }
                }
            }
        }
    }
}

@Composable
private fun ChangelogEntryCard(entry: ChangelogEntry) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp * scale.spacing)) {
        Text(text = entry.title, fontSize = 13.sp * scale.font, fontWeight = FontWeight.Bold, color = NightSession.Text)
        Column(modifier = Modifier.padding(top = 6.dp * scale.spacing)) {
            entry.changes.forEach { change ->
                Row(modifier = Modifier.padding(vertical = 3.dp * scale.spacing)) {
                    Text("-  ", fontSize = 11.5.sp * scale.font, color = NightSession.TextDim)
                    Text(change, fontSize = 11.5.sp * scale.font, color = NightSession.TextDim)
                }
            }
        }
    }
}
