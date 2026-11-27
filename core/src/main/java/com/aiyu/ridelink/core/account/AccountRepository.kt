package com.aiyu.ridelink.core.account

import android.content.Context
import com.aiyu.ridelink.core.BuildConfig
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** The Google account the rider signed in with. Only used to back up their own data. */
data class Account(val uid: String, val name: String?, val email: String?, val photoUrl: String?)

sealed interface SignInResult {
    data object Done : SignInResult
    /** The rider closed the account picker: not an error. */
    data object Cancelled : SignInResult
    /** No Google account on the phone (or none allowed for this app). */
    data object NoAccount : SignInResult
    data class Failed(val detail: String?) : SignInResult
}

/**
 * Optional Google sign-in: Credential Manager's "Sign in with Google" gives an ID token, Firebase Auth turns it into a
 * session that survives restarts. Nothing else in the app needs an account.
 */
@Singleton
class AccountRepository @Inject constructor(@ApplicationContext private val context: Context) {
    /** False in a build without google-services.json or GOOGLE_WEB_CLIENT_ID (forks, CI without secrets): sign-in is hidden. */
    val available: Boolean = WEB_CLIENT_ID.isNotBlank() && FirebaseApp.getApps(context).isNotEmpty()

    private val auth by lazy { FirebaseAuth.getInstance() }

    val account: Flow<Account?> = if (!available) flowOf(null) else callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun current(): Account? = if (available) auth.currentUser?.toAccount() else null

    /**
     * [activity] must be an Activity: Credential Manager shows its account picker over it. Tries the bottom-sheet picker
     * first; with no Google account on the phone it falls back to the full dialog, which can add one.
     */
    suspend fun signIn(activity: Context): SignInResult {
        if (!available) return SignInResult.Failed("Firebase is not configured")
        val sheet = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val first = attempt(activity, sheet)
        return if (first == SignInResult.NoAccount) attempt(activity, GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()) else first
    }

    private suspend fun attempt(activity: Context, option: CredentialOption): SignInResult {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failed("Unexpected credential ${credential.type}")
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            SignInResult.Done
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.NoAccount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A wrong SHA-1 or a missing Android OAuth client lands here; logcat says which.
            android.util.Log.w("RideLink", "Google sign-in failed", e)
            SignInResult.Failed(e.message)
        }
    }

    /** Signs out of Firebase and forgets the picked account, so the next sign-in asks again. Local data stays. */
    suspend fun signOut() {
        if (!available) return
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Profile photo bytes at [px] square, null when offline or the account has none. */
    suspend fun photo(url: String, px: Int = 256): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(sized(url, px)).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
        }.getOrNull()
    }

    private fun FirebaseUser.toAccount() = Account(
        uid = uid,
        name = displayName?.takeIf { it.isNotBlank() } ?: providerData.firstNotNullOfOrNull { it.displayName?.takeIf(String::isNotBlank) },
        email = email ?: providerData.firstNotNullOfOrNull { it.email },
        photoUrl = (photoUrl ?: providerData.firstNotNullOfOrNull { it.photoUrl })?.toString(),
    )

    companion object {
        /** Web OAuth client of the Firebase project, from local.properties / the CI secret (core/build.gradle.kts). */
        private const val WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID

        private val SIZE_SUFFIX = Regex("=s\\d+(-c)?$")

        /** Google photo URLs end in `=s96-c`; ask for a sharper square instead. */
        fun sized(url: String, px: Int): String =
            if (SIZE_SUFFIX.containsMatchIn(url)) url.replace(SIZE_SUFFIX, "=s$px-c") else url
    }
}
