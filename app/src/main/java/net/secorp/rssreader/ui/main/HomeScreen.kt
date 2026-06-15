package net.secorp.rssreader.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.secorp.rssreader.data.api.AuthUser

@Composable
fun HomeScreen(
    user: AuthUser?,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Signed in", style = MaterialTheme.typography.headlineMedium)
        if (user != null) {
            Text(user.email, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("(session restored from local token)", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onSignOut) {
            Text("Sign out")
        }
    }
}
