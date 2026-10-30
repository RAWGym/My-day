package kz.raw.moyden.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kz.raw.moyden.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Updates come from GitHub releases of the public repo. */
object Updater {
    private val exec = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    val current: String get() = BuildConfig.VERSION_NAME

    fun isNewer(remote: String, local: String = current): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun check(ctx: Context, auto: Boolean = false) {
        val sp = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (auto) {
            if (System.currentTimeMillis() - sp.getLong("lastCheck", 0) < 6 * 3600_000L) return
            if (_state.value !is UpdateState.Idle && _state.value !is UpdateState.UpToDate) return
        }
        _state.value = UpdateState.Checking
        exec.execute {
            try {
                val c = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 15000; c.readTimeout = 15000
                if (c.responseCode == 404) { _state.value = UpdateState.UpToDate; return@execute }
                if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                c.disconnect()
                sp.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                val tag = o.optString("tag_name")
                val assets = o.optJSONArray("assets")
                var url = ""; var size = 0L
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url"); size = a.optLong("size")
                    }
                }
                val skipped = sp.getString("skipped", "")
                _state.value = if (url.isNotEmpty() && isNewer(tag) && !(auto && skipped == tag))
                    UpdateState.Available(Release(tag.removePrefix("v"), o.optString("body"), url, size))
                else UpdateState.UpToDate
            } catch (e: Exception) {
                _state.value = if (auto) UpdateState.Idle else UpdateState.Failed("Не удалось проверить обновления. Проверьте интернет")
            }
        }
    }

    fun skip(ctx: Context, r: Release) {
        ctx.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putString("skipped", "v" + r.version).apply()
        _state.value = UpdateState.Idle
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    fun download(ctx: Context, r: Release) {
        _state.value = UpdateState.Downloading(r, 0f)
        exec.execute {
            try {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "moy-den-${r.version}.apk")
                var url = URL(r.apkUrl)
                var c: HttpURLConnection
                var hops = 0
                while (true) {
                    c = url.openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 20000; c.readTimeout = 30000
                    val code = c.responseCode
                    if (code in 300..399 && hops < 5) {
                        url = URL(url, c.getHeaderField("Location")); c.disconnect(); hops++; continue
                    }
                    if (code !in 200..299) throw Exception("HTTP $code")
                    break
                }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L; var last = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            output.write(buf, 0, n); done += n
                            if (total > 0 && done - last > 200_000) {
                                last = done
                                _state.value = UpdateState.Downloading(r, done.toFloat() / total)
                            }
                        }
                    }
                }
                c.disconnect()
                _state.value = UpdateState.Ready(r, out)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Не удалось скачать обновление. Проверьте интернет")
            }
        }
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
