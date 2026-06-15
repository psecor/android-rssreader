package net.secorp.rssreader.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val activityContext = LocalContext.current
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RSS Reader", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Sign in with your Google account to continue.",
            style = MaterialTheme.typography.bodyMedium,
        )
        when (val s = state) {
            LoginUiState.SigningIn -> CircularProgressIndicator()
            is LoginUiState.Error -> Text(
                "Sign-in failed: ${s.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Unit
        }
        Button(
            onClick = { viewModel.signIn(activityContext) },
            enabled = state !is LoginUiState.SigningIn,
        ) {
            Text("Sign in with Google")
        }
    }
}
