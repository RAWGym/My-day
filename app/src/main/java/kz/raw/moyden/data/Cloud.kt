package kz.raw.moyden.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kz.raw.moyden.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class CloudState(
    val email: String = "",
    val loggedIn: Boolean = false,
    val syncing: Boolean = false,
    val lastSync: Long = 0,
    val error: String = "",
)

class CloudError(msg: String) : Exception(msg)

/** Account and sync through Supabase (auth + one table of items). */
object Cloud {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_KEY
    private lateinit var sp: SharedPreferences
    private val exec = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null
    private val _state = MutableStateFlow(CloudState())
    val state: StateFlow<CloudState> = _state

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        _state.value = CloudState(
            email = sp.getString("email", "")!!,
            loggedIn = sp.getString("refresh", "").orEmpty().isNotEmpty(),
            lastSync = sp.getLong("lastSync", 0),
        )
    }

    // ---------- HTTP ----------

    private class Resp(val code: Int, val body: String)

    private fun http(method: String, path: String, body: String? = null, token: String? = null,
                     headers: Map<String, String> = emptyMap()): Resp {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("apikey", key)
        c.setRequestProperty("Authorization", "Bearer ${token ?: key}")
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        return Resp(code, text)
    }

    private fun authError(r: Resp): String {
        val o = runCatching { JSONObject(r.body) }.getOrNull()
        val code = o?.optString("error_code").orEmpty()
        val msg = (o?.optString("msg")?.takeIf { it.isNotEmpty() }
            ?: o?.optString("error_description")?.takeIf { it.isNotEmpty() }
            ?: o?.optString("message")).orEmpty()
        return when {
            code == "invalid_credentials" || msg.contains("Invalid login", true) -> "Неверная почта или пароль"
            code == "user_already_exists" || msg.contains("already registered", true) -> "Такой аккаунт уже есть. Нажмите «Войти»"
            code == "weak_password" || msg.contains("Password should", true) -> "Пароль слишком простой: нужно не меньше 6 символов"
            code == "validation_failed" || msg.contains("invalid format", true) || msg.contains("is invalid", true) -> "Проверьте адрес почты"
            code == "email_not_confirmed" -> "Почта не подтверждена. Проверьте письмо"
            code.contains("rate_limit") || r.code == 429 -> "Слишком много попыток. Подождите немного"
            else -> "Ошибка входа (${r.code}). ${msg.take(80)}"
        }
    }

    private fun saveSession(o: JSONObject, email: String) {
        sp.edit()
            .putString("access", o.optString("access_token"))
            .putString("refresh", o.optString("refresh_token"))
            .putLong("expiresAt", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000)
            .putString("email", email)
            .apply()
    }

    // ---------- account ----------

    /** Blocking. Throws CloudError with a message in Russian. */
    fun signIn(email: String, password: String, create: Boolean) {
        val payload = JSONObject().put("email", email.trim()).put("password", password).toString()
        val r = try {
            if (create) http("POST", "/auth/v1/signup", payload)
            else http("POST", "/auth/v1/token?grant_type=password", payload)
        } catch (e: Exception) {
            throw CloudError("Нет связи с интернетом")
        }
        if (r.code !in 200..299) throw CloudError(authError(r))
        var o = JSONObject(r.body)
        if (!o.has("access_token")) {
            // sign-up without an immediate session: log in right away
            val r2 = http("POST", "/auth/v1/token?grant_type=password", payload)
            if (r2.code !in 200..299) throw CloudError(authError(r2))
            o = JSONObject(r2.body)
        }
        saveSession(o, email.trim())
        sp.edit().putString("lastPull", "").apply()
        // what is already on this phone goes to the account as well
        Store.markAllDirty()
        _state.value = _state.value.copy(email = email.trim(), loggedIn = true, error = "")
        syncNow()
    }

    fun signOut() {
        val t = sp.getString("access", null)
        exec.execute { runCatching { http("POST", "/auth/v1/logout", "{}", t) } }
        sp.edit().clear().apply()
        _state.value = CloudState()
    }

    private fun token(): String {
        val exp = sp.getLong("expiresAt", 0)
        val access = sp.getString("access", "").orEmpty()
        if (access.isNotEmpty() && exp - System.currentTimeMillis() > 60_000) return access
        val refresh = sp.getString("refresh", "").orEmpty()
        if (refresh.isEmpty()) throw CloudError("Войдите в аккаунт")
        val r = http("POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refresh).toString())
        if (r.code in 400..499) {
            sp.edit().remove("refresh").remove("access").apply()
            _state.value = _state.value.copy(loggedIn = false)
            throw CloudError("Сессия закончилась. Войдите снова")
        }
        if (r.code !in 200..299) throw CloudError("Сервер недоступен")
        val o = JSONObject(r.body)
        saveSession(o, sp.getString("email", "")!!)
        return o.getString("access_token")
    }

    // ---------- sync ----------

    /** Debounced sync after local changes. */
    fun requestSync(delaySec: Long = 3) {
        if (!::sp.isInitialized || !_state.value.loggedIn) return
        pending?.cancel(false)
        pending = exec.schedule({ runSync() }, delaySec, TimeUnit.SECONDS)
    }

    fun syncNow() = requestSync(0)

    /** Blocking variant for background receivers. */
    fun syncBlocking() {
        if (!::sp.isInitialized || !_state.value.loggedIn) return
        runCatching { exec.submit { runSync() }.get(40, TimeUnit.SECONDS) }
    }

    private fun runSync() {
        if (!_state.value.loggedIn) return
        _state.value = _state.value.copy(syncing = true)
        try {
            val t = token()
            push(t)
            pull(t)
            val now = System.currentTimeMillis()
            sp.edit().putLong("lastSync", now).apply()
            _state.value = _state.value.copy(syncing = false, lastSync = now, error = "")
        } catch (e: CloudError) {
            _state.value = _state.value.copy(syncing = false, error = e.message.orEmpty())
        } catch (e: Exception) {
            _state.value = _state.value.copy(syncing = false, error = "Нет связи с интернетом")
        }
    }

    private fun push(t: String) {
        val items = Store.dirtyItems()
        if (items.isEmpty()) return
        items.chunked(200).forEach { chunk ->
            val arr = JSONArray()
            chunk.forEach {
                arr.put(JSONObject().put("id", it.id).put("kind", it.tag).put("data", it.toJson())
                    .put("updated_at", it.updatedAt).put("deleted", it.deleted))
            }
            val r = http("POST", "/rest/v1/items?on_conflict=user_id,id", arr.toString(), t,
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
            if (r.code !in 200..299) throw CloudError("Не удалось сохранить в облако (${r.code})")
            Store.clearDirty(chunk)
        }
    }

    private fun pull(t: String) {
        var since = sp.getString("lastPull", "").orEmpty()
        while (true) {
            val filter = if (since.isEmpty()) "" else {
                val margin = OffsetDateTime.parse(since).minusSeconds(5).toString()
                "&server_ts=gt." + URLEncoder.encode(margin, "UTF-8")
            }
            val r = http("GET", "/rest/v1/items?select=kind,data,server_ts$filter&order=server_ts.asc&limit=500",
                null, t)
            if (r.code !in 200..299) throw CloudError("Не удалось загрузить из облака (${r.code})")
            val arr = JSONArray(r.body)
            val items = (0 until arr.length()).mapNotNull {
                val row = arr.getJSONObject(it)
                itemFromJson(row.optString("kind"), row.getJSONObject("data"))
            }
            if (items.isNotEmpty()) Store.mergeRemote(items)
            if (arr.length() > 0) {
                val last = arr.getJSONObject(arr.length() - 1).optString("server_ts")
                if (last == since) break
                since = last
                sp.edit().putString("lastPull", since).apply()
            }
            if (arr.length() < 500) break
        }
    }
}
