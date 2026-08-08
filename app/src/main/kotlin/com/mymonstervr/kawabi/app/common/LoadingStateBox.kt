package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.NightSession

/** Shared loading/error/empty branches for a grid/list screen; renders [content] once past all three. */
@Composable
fun LoadingStateBox(
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    emptyFontSize: TextUnit = 11.5.sp,
    content: @Composable () -> Unit,
) {
    when {
        isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        error != null -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
        isEmpty -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = emptyMessage, color = NightSession.TextDim, fontSize = emptyFontSize)
        }
        else -> content()
    }
}
