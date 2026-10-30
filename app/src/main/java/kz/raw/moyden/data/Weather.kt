package kz.raw.moyden.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class DayForecast(val date: LocalDate, val code: Int, val max: Int, val min: Int, val rain: Int)

data class WeatherInfo(
    val city: String,
    val temp: Int,
    val feels: Int,
    val code: Int,
    val wind: Int,
    val humidity: Int,
    val isDay: Boolean,
    val days: List<DayForecast>,
    val fetchedAt: Long,
)

data class City(val name: String, val region: String, val lat: Double, val lon: Double)

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2 -> Sky.PARTLY
    3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.STORM
    else -> Sky.CLOUDY
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Ясно"
    1 -> "Преимущественно ясно"
    2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    51, 53, 55 -> "Морось"
    56, 57 -> "Ледяная морось"
    61 -> "Небольшой дождь"
    63 -> "Дождь"
    65 -> "Сильный дождь"
    66, 67 -> "Ледяной дождь"
    71 -> "Небольшой снег"
    73 -> "Снег"
    75 -> "Сильный снег"
    77 -> "Снежная крупа"
    80, 81 -> "Ливень"
    82 -> "Сильный ливень"
    85, 86 -> "Снегопад"
    95 -> "Гроза"
    96, 99 -> "Гроза с градом"
    else -> "Облачно"
}

/** Weather from Open-Meteo: free, no key needed. */
object Weather {
    private lateinit var sp: SharedPreferences
    private val exec = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow<WeatherInfo?>(null)
    val state: StateFlow<WeatherInfo?> = _state
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)
        sp.getString("cache", null)?.let { c -> runCatching { _state.value = parse(JSONObject(c)) } }
    }

    /** Re-reads the cached forecast (used by tests and after restore). */
    internal fun reloadCache() {
        sp.getString("cache", null)?.let { c -> runCatching { _state.value = parse(JSONObject(c)) } }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 15000
        try {
            if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun parse(o: JSONObject): WeatherInfo {
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            DayForecast(
                LocalDate.parse(dates.getString(i)),
                d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i).roundToInt(),
                d.getJSONArray("temperature_2m_min").optDouble(i).roundToInt(),
                d.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0,
            )
        }
        return WeatherInfo(
            city = o.optString("_city"),
            temp = cur.optDouble("temperature_2m").roundToInt(),
            feels = cur.optDouble("apparent_temperature").roundToInt(),
            code = cur.optInt("weather_code"),
            wind = (cur.optDouble("wind_speed_10m") / 3.6).roundToInt(),
            humidity = cur.optInt("relative_humidity_2m"),
            isDay = cur.optInt("is_day", 1) == 1,
            days = days,
            fetchedAt = o.optLong("_at"),
        )
    }

    /** Blocking fetch, returns fresh info or the cached one. */
    fun fetchBlocking(): WeatherInfo? {
        val p = Settings.p
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7"
            val o = JSONObject(get(url)).put("_city", p.city).put("_at", System.currentTimeMillis())
            val w = parse(o)
            sp.edit().putString("cache", o.toString()).apply()
            _state.value = w
            w
        } catch (e: Exception) {
            _state.value
        }
    }

    fun refresh(force: Boolean = false) {
        val cur = _state.value
        val fresh = cur != null && cur.city == Settings.p.city &&
            System.currentTimeMillis() - cur.fetchedAt < 20 * 60_000
        if (fresh && !force) return
        if (_loading.value) return
        _loading.value = true
        exec.execute { try { fetchBlocking() } finally { _loading.value = false } }
    }

    fun searchCities(q: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=10&language=ru&format=json&name=" +
            URLEncoder.encode(q.trim(), "UTF-8")
        val o = JSONObject(get(url))
        val arr = o.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map {
            val r = arr.getJSONObject(it)
            City(
                r.optString("name"),
                listOf(r.optString("admin1"), r.optString("country")).filter { s -> s.isNotBlank() }.joinToString(", "),
                r.optDouble("latitude"), r.optDouble("longitude"),
            )
        }
    }
}
