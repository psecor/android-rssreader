package net.secorp.rssreader.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import net.secorp.rssreader.BuildConfig

/**
 * Wraps the Credential Manager + Sign in with Google flow. `serverClientId`
 * is the web OAuth client ID — the ID token's `aud` will match this value,
 * which is what the backend verifies against.
 */
@Singleton
class GoogleSignInClient @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val credentialManager = CredentialManager.create(appContext)

    /**
     * Launches the sign-in chooser bound to the supplied Activity context
     * (Credential Manager requires an Activity for the UI). Returns the raw
     * Google ID token on success.
     */
    suspend fun requestIdToken(activityContext: Context): String {
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty()) {
            "GOOGLE_WEB_CLIENT_ID is empty — set googleWebClientId in local.properties"
        }

        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = credentialManager.getCredential(
            context = activityContext,
            request = request,
        )
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Unexpected credential type: ${credential::class.java.name}")
    }
}
