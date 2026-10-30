package kz.raw.moyden.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Prefs(
    val themeId: String = "raw",
    val fontId: String = "nunito",
    val textScale: Float = 1.15f,
    val userName: String = "",
    val currency: String = "₸",
    // notifications
    val notifTasks: Boolean = true,
    val notifMeds: Boolean = true,
    val notifEvents: Boolean = true,
    val notifBirthdays: Boolean = true,
    val notifDebts: Boolean = true,
    val loudEnabled: Boolean = true,
    val summaryOn: Boolean = true,
    val summaryMinute: Int = 8 * 60,
    val alarmSound: String = "",
    val alarmSoundName: String = "",
    val vibrate: Boolean = true,
    val snoozeMin: Int = 10,
    val alarmDurationMin: Int = 3,
    // weather
    val city: String = "Алматы",
    val lat: Double = 43.2567,
    val lon: Double = 76.9286,
    // updates
    val autoUpdate: Boolean = true,
)

object Settings {
    private lateinit var sp: SharedPreferences
    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> = _prefs
    val p: Prefs get() = _prefs.value

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _prefs.value = read()
    }

    fun raw(): SharedPreferences = sp

    private fun read(): Prefs {
        val d = Prefs()
        return Prefs(
            themeId = sp.getString("themeId", d.themeId)!!,
            fontId = sp.getString("fontId", d.fontId)!!,
            textScale = sp.getFloat("textScale", d.textScale),
            userName = sp.getString("userName", d.userName)!!,
            currency = sp.getString("currency", d.currency)!!,
            notifTasks = sp.getBoolean("notifTasks", d.notifTasks),
            notifMeds = sp.getBoolean("notifMeds", d.notifMeds),
            notifEvents = sp.getBoolean("notifEvents", d.notifEvents),
            notifBirthdays = sp.getBoolean("notifBirthdays", d.notifBirthdays),
            notifDebts = sp.getBoolean("notifDebts", d.notifDebts),
            loudEnabled = sp.getBoolean("loudEnabled", d.loudEnabled),
            summaryOn = sp.getBoolean("summaryOn", d.summaryOn),
            summaryMinute = sp.getInt("summaryMinute", d.summaryMinute),
            alarmSound = sp.getString("alarmSound", d.alarmSound)!!,
            alarmSoundName = sp.getString("alarmSoundName", d.alarmSoundName)!!,
            vibrate = sp.getBoolean("vibrate", d.vibrate),
            snoozeMin = sp.getInt("snoozeMin", d.snoozeMin),
            alarmDurationMin = sp.getInt("alarmDurationMin", d.alarmDurationMin),
            city = sp.getString("city", d.city)!!,
            lat = sp.getFloat("lat", d.lat.toFloat()).toDouble(),
            lon = sp.getFloat("lon", d.lon.toFloat()).toDouble(),
            autoUpdate = sp.getBoolean("autoUpdate", d.autoUpdate),
        )
    }

    fun update(f: (Prefs) -> Prefs) {
        val n = f(_prefs.value)
        _prefs.value = n
        sp.edit()
            .putString("themeId", n.themeId).putString("fontId", n.fontId)
            .putFloat("textScale", n.textScale).putString("userName", n.userName)
            .putString("currency", n.currency)
            .putBoolean("notifTasks", n.notifTasks).putBoolean("notifMeds", n.notifMeds)
            .putBoolean("notifEvents", n.notifEvents).putBoolean("notifBirthdays", n.notifBirthdays)
            .putBoolean("notifDebts", n.notifDebts).putBoolean("loudEnabled", n.loudEnabled)
            .putBoolean("summaryOn", n.summaryOn).putInt("summaryMinute", n.summaryMinute)
            .putString("alarmSound", n.alarmSound).putString("alarmSoundName", n.alarmSoundName)
            .putBoolean("vibrate", n.vibrate).putInt("snoozeMin", n.snoozeMin)
            .putInt("alarmDurationMin", n.alarmDurationMin)
            .putString("city", n.city).putFloat("lat", n.lat.toFloat()).putFloat("lon", n.lon.toFloat())
            .putBoolean("autoUpdate", n.autoUpdate)
            .apply()
    }
}
