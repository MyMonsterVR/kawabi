package com.mymonstervr.kawabi.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    viewModel: TvSettingsViewModel = koinViewModel(),
) {
    val email by viewModel.email.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = TvDimens.OverscanHorizontal, vertical = TvDimens.OverscanVertical),
    ) {
        Text("Settings", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("Signed in as", color = TvColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        Text(email ?: "...", color = TvColors.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = viewModel::signOut,
            modifier = Modifier.focusRequester(focusRequester),
            colors = ButtonDefaults.colors(
                containerColor = TvColors.SurfaceOverlay,
                contentColor = TvColors.Text,
                focusedContainerColor = TvColors.Accent,
                focusedContentColor = TvColors.OnAccent,
            ),
        ) { Text("Sign out of this TV") }
    }
}
