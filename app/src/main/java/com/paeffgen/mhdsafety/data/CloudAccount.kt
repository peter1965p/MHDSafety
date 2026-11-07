package com.paeffgen.mhdsafety.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.paeffgen.mhdsafety.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface SignInResult {
    data class Success(val email: String) : SignInResult
    data class NotAllowed(val email: String) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Google-Anmeldung für die gemeinsame Liste.
 *
 * Rein darf nur, wessen E-Mail-Adresse in Firestore unter `allowed/<email>` steht.
 * Diese Liste pflegt man selbst in der Firebase-Konsole; die Sicherheitsregeln
 * (firestore.rules) setzen das auch auf dem Server durch.
 */
class CloudAccount(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val credentials = CredentialManager.create(context)

    private val _email = MutableStateFlow(auth.currentUser?.email)

    /** E-Mail des angemeldeten Kontos, oder null = nur lokal auf dem Handy. */
    val email: StateFlow<String?> = _email.asStateFlow()

    init {
        auth.addAuthStateListener { _email.value = it.currentUser?.email }
    }

    val isSignedIn: Boolean get() = auth.currentUser != null

    /** @param activityContext muss eine Activity sein, damit Android den Konto-Dialog zeigen kann. */
    suspend fun signIn(activityContext: Context): SignInResult {
        val idToken = try {
            val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
            val response = credentials.getCredential(
                activityContext,
                GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
            GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        } catch (_: GetCredentialCancellationException) {
            return SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            return SignInResult.Failed("Google-Anmeldung fehlgeschlagen: ${e.message ?: e.type}")
        }

        val user = try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
        } catch (e: Exception) {
            return SignInResult.Failed("Anmeldung bei Firebase fehlgeschlagen: ${e.message}")
        } ?: return SignInResult.Failed("Anmeldung fehlgeschlagen.")

        val email = user.email ?: run {
            signOut()
            return SignInResult.Failed("Das Google-Konto hat keine E-Mail-Adresse.")
        }

        val allowed = try {
            firestore.collection("allowed").document(email).get(Source.SERVER).await().exists()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                false
            } else {
                signOut()
                return SignInResult.Failed("Keine Verbindung zur Datenbank: ${e.message}")
            }
        }
        if (!allowed) {
            signOut()
            return SignInResult.NotAllowed(email)
        }
        return SignInResult.Success(email)
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { credentials.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
