package net.secorp.rssreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import net.secorp.rssreader.ui.HealthCheckViewModel
import net.secorp.rssreader.ui.theme.RssReaderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RssReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        HealthScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthScreen(viewModel: HealthCheckViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "RSS Reader",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "API base: ${BuildConfig.API_BASE_URL}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Health: ${state.label}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HealthScreenPreview() {
    RssReaderTheme {
        Text("RSS Reader")
    }
}
