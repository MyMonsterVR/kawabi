package com.mymonstervr.kawabi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.SourceToggleDto
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit, viewModel: SourcesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = { Text("Sources", fontWeight = FontWeight.Bold, color = NightSession.Text) },
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
        Box(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
            when (val current = state) {
                is SourcesState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is SourcesState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(text = current.message, color = MaterialTheme.colorScheme.error)
                }
                is SourcesState.Success -> LazyColumn {
                    items(current.sources, key = { it.key }) { source ->
                        SourceRow(source, onToggle = { enabled -> viewModel.toggle(source.key, enabled) })
                        HorizontalDivider(color = NightSession.Hairline)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SourceRow(source: SourceToggleDto, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
            Text(text = source.key, fontSize = 10.sp, color = NightSession.TextDim, modifier = Modifier.padding(top = 1.dp))
        }
        Switch(
            checked = source.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightSession.OnAccent,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = NightSession.TextDim,
                uncheckedTrackColor = NightSession.Chip,
                uncheckedBorderColor = NightSession.Hairline,
            ),
        )
    }
}
