package com.mymonstervr.kawabi.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

// Deliberately narrower than ResponsiveContainer/LocalKawabiScale's content-width caps
// (a login form reads better narrow at any screen size, this isn't the "text stretches
// too wide" problem those solve) -- kept as its own constant, deduped across both
// call sites below rather than routed through ResponsiveContainer, since this screen's
// vertically-centered Box layout would conflict with ResponsiveContainer's TopCenter.
private val LOGIN_FORM_MAX_WIDTH = 400.dp

@Composable
fun LoginScreen(onDone: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoggedIn) {
            LoggedInContent(onLogout = viewModel::logout, onDone = onDone)
        } else {
            LoginForm(viewModel, onContinueWithoutAccount = onDone)
        }
    }
}

@Composable
private fun LoggedInContent(onLogout: () -> Unit, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().widthIn(max = LOGIN_FORM_MAX_WIDTH)) {
        Text(text = "You're logged in.", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(NightSession.RadiusMd),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Done") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onLogout) {
            Text("Log out", color = NightSession.TextDim, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
        }
    }
}

@Composable
private fun LoginForm(viewModel: LoginViewModel, onContinueWithoutAccount: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().widthIn(max = LOGIN_FORM_MAX_WIDTH)) {
        Text(
            text = "Kawabi",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp * LocalKawabiScale.current.font,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Your library, everywhere",
            style = MaterialTheme.typography.bodySmall,
            color = NightSession.TextDim,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))

        LoginField(value = email, onValueChange = { email = it }, placeholder = "you@example.com", keyboardType = KeyboardType.Email)
        Spacer(modifier = Modifier.height(10.dp))
        LoginField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            keyboardType = KeyboardType.Password,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingText = if (passwordVisible) "Hide" else "Show",
            onTrailingClick = { passwordVisible = !passwordVisible },
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.login(email, password) },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(NightSession.RadiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = NightSession.OnAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp, color = NightSession.OnAccent)
            } else {
                Text("Log in", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onContinueWithoutAccount) {
            Text(
                text = "Continue without an account",
                color = NightSession.TextDim,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = NightSession.TextDim) },
        singleLine = true,
        shape = RoundedCornerShape(NightSession.RadiusMd),
        visualTransformation = visualTransformation,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (trailingText != null && onTrailingClick != null) {
            { TextButton(onClick = onTrailingClick) { Text(trailingText, color = NightSession.TextDim, style = MaterialTheme.typography.labelSmall) } }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = NightSession.Chip,
            unfocusedContainerColor = NightSession.Chip,
            disabledContainerColor = NightSession.Chip,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedTextColor = NightSession.Text,
            unfocusedTextColor = NightSession.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
