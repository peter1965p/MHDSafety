package com.paeffgen.mhdsafety.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException

sealed interface SignInResult {
    /** @param accountId Konto, dem die Daten gehören (mehrere Anmeldungen können ein Konto teilen) */
    data class Success(val email: String, val accountId: Long) : SignInResult
    data class Failed(val message: String) : SignInResult
}

data class AccountLogin(val id: Long, val email: String, val pending: Boolean)
data class AccountInfo(val accountId: Long, val email: String, val logins: List<AccountLogin>)
data class Invitation(val loginId: Long, val email: String, val code: String)

/**
 * Konto bei der eigenen MHD-API (ersetzt die Google-/Firebase-Anmeldung).
 *
 * Ein Konto besitzt den Bestand; mehrere Personen können sich mit eigener E-Mail und eigenem
 * Passwort am selben Konto anmelden und sehen dann dieselbe Liste.
 */
class CloudAccount(private val store: AuthStore, private val api: ApiClient) {
    private val _email = MutableStateFlow(store.email.takeIf { store.refreshToken != null })

    /** E-Mail der Anmeldung auf diesem Handy, oder null = nur lokal. */
    val email: StateFlow<String?> = _email.asStateFlow()

    val isSignedIn: Boolean get() = _email.value != null

    private val refreshLock = Mutex()

    suspend fun login(email: String, password: String): SignInResult =
        authCall("/auth/login", JSONObject().put("email", email.trim()).put("password", password))

    suspend fun register(email: String, password: String): SignInResult =
        authCall("/auth/register", JSONObject().put("email", email.trim()).put("password", password))

    suspend fun acceptInvite(email: String, code: String, password: String): SignInResult =
        authCall(
            "/auth/accept-invite",
            JSONObject().put("email", email.trim()).put("code", code.trim()).put("password", password),
        )

    /** @return Text für die Oberfläche */
    suspend fun requestPasswordReset(email: String): String = try {
        val r = api.call("POST", "/auth/password-reset/request", JSONObject().put("email", email.trim()))
        if (r.ok) "Anfrage gespeichert. Der Mailversand ist noch nicht eingerichtet – bitte beim Betreiber melden."
        else r.message()
    } catch (e: IOException) {
        offline(e)
    }

    private suspend fun authCall(path: String, body: JSONObject): SignInResult {
        val r = try {
            api.call("POST", path, body)
        } catch (e: IOException) {
            return SignInResult.Failed(offline(e))
        }
        if (!r.ok || r.body == null) return SignInResult.Failed(r.message())
        val email = takeTokens(r.body)
        return SignInResult.Success(email, r.body.getLong("accountId"))
    }

    private fun takeTokens(body: JSONObject): String {
        val email = body.getString("email")
        store.accessToken = body.getString("accessToken")
        store.accessExpiresAt = System.currentTimeMillis() + body.optLong("expiresIn", 900) * 1000 - 30_000
        store.refreshToken = body.getString("refreshToken")
        store.email = email
        _email.value = email
        return email
    }

    /** Meldet nur dieses Handy ab; die Daten bleiben im Konto auf dem Server. */
    suspend fun signOut() {
        store.refreshToken?.let { token ->
            runCatching { api.call("POST", "/auth/logout", JSONObject().put("refreshToken", token)) }
        }
        store.clearSession()
        _email.value = null
    }

    /** Gültiger Zugriffstoken; erneuert ihn bei Bedarf. null = Sitzung ist vorbei. */
    private suspend fun accessToken(forceRefresh: Boolean = false): String? = refreshLock.withLock {
        val current = store.accessToken
        if (!forceRefresh && current != null && System.currentTimeMillis() < store.accessExpiresAt) return current
        val refresh = store.refreshToken ?: return null
        val r = api.call("POST", "/auth/refresh", JSONObject().put("refreshToken", refresh))
        if (r.code == 401) {
            store.clearSession()
            _email.value = null
            return null
        }
        if (!r.ok || r.body == null) throw IOException(r.message())
        takeTokens(r.body)
        store.accessToken
    }

    /**
     * Angemeldeter Aufruf. Bei abgelaufenem Zugriffstoken wird einmal erneuert und wiederholt.
     * @throws IOException offline oder Server gestört
     * @throws SessionExpired wenn eine neue Anmeldung nötig ist
     */
    suspend fun authorized(method: String, path: String, body: JSONObject? = null): ApiResponse {
        val token = accessToken() ?: throw SessionExpired()
        val first = api.call(method, path, body, token)
        if (first.code != 401) return first
        val fresh = accessToken(forceRefresh = true) ?: throw SessionExpired()
        val second = api.call(method, path, body, fresh)
        if (second.code == 401) {
            store.clearSession()
            _email.value = null
            throw SessionExpired()
        }
        return second
    }

    suspend fun info(): AccountInfo? {
        val r = authorized("GET", "/account")
        if (!r.ok || r.body == null) return null
        val list = r.body.getJSONArray("logins")
        return AccountInfo(
            accountId = r.body.getLong("accountId"),
            email = r.body.getString("email"),
            logins = (0 until list.length()).map { i ->
                list.getJSONObject(i).let { AccountLogin(it.getLong("id"), it.getString("email"), it.getBoolean("pending")) }
            },
        )
    }

    /** Weitere Person zum Konto einladen. Liefert den Code, den man ihr weitergibt. */
    suspend fun invite(email: String): Result<Invitation> =
        invitationOf(authorized("POST", "/account/logins", JSONObject().put("email", email.trim())))

    suspend fun reinvite(loginId: Long): Result<Invitation> =
        invitationOf(authorized("POST", "/account/logins/$loginId/invite"))

    private fun invitationOf(r: ApiResponse): Result<Invitation> =
        if (r.ok && r.body != null) {
            Result.success(Invitation(r.body.getLong("loginId"), r.body.getString("email"), r.body.getString("code")))
        } else {
            Result.failure(IOException(r.message()))
        }

    suspend fun removeLogin(loginId: Long): Result<Unit> {
        val r = authorized("DELETE", "/account/logins/$loginId")
        return if (r.ok) Result.success(Unit) else Result.failure(IOException(r.message()))
    }

    private fun offline(e: IOException) =
        "Server nicht erreichbar (${store.apiUrl}).${e.message?.let { " $it" }.orEmpty()}"
}

class SessionExpired : Exception("Die Anmeldung ist abgelaufen. Bitte neu anmelden.")
