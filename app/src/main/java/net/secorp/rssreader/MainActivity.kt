package net.secorp.rssreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import net.secorp.rssreader.auth.AuthState
import net.secorp.rssreader.ui.login.LoginScreen
import net.secorp.rssreader.ui.main.HomeScreen
import net.secorp.rssreader.ui.main.MainViewModel
import net.secorp.rssreader.ui.theme.RssReaderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

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
                        val auth by viewModel.authState.collectAsState()
                        when (val s = auth) {
                            AuthState.SignedOut, AuthState.Unknown -> LoginScreen()
                            is AuthState.SignedIn -> HomeScreen(
                                user = s.user,
                                onSignOut = viewModel::signOut,
                            )
                        }
                    }
                }
            }
        }
    }
}
