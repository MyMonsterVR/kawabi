package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.BackScaffold
import com.mymonstervr.kawabi.app.theme.NightSession

/**
 * Placeholder for the Media3/ExoPlayer screen (PLAN-anime.md phase P4). Shows the episode
 * key it was handed so the navigation path is verifiable end to end before the player
 * itself exists.
 */
@Composable
fun PlayerScreen(episodeKey: String, onBack: () -> Unit) {
    BackScaffold(title = "Player", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Playback is coming next", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NightSession.Text)
            Text(
                text = episodeKey,
                fontSize = 11.sp,
                color = NightSession.TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "This episode's streams will open here.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
