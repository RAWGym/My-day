package kz.raw.moyden.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.millis
import kz.raw.moyden.data.nextTrigger
import kz.raw.moyden.data.today
import java.time.LocalTime

/** Keeps exactly one pending alarm per item: its next reminder moment. */
object Scheduler {
    const val T_ENTRY = "entry"
    const val T_BDAY = "birthday"
    const val T_DEBT = "debt"
    const val T_SUMMARY = "summary"
    const val T_SNOOZE = "snooze"
    const val T_TEST = "test"

    private fun pi(ctx: Context, uri: String, extras: Intent.() -> Unit = {}, create: Boolean = true): PendingIntent? {
        val i = Intent(ctx, AlarmReceiver::class.java).setData(Uri.parse(uri)).apply(extras)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, 0, i, flags)
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun set(ctx: Context, at: Long, p: PendingIntent, loud: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            when {
                loud && canExact(ctx) ->
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifs.openApp(ctx, 7)), p)
                canExact(ctx) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    @Synchronized
    fun rescheduleAll(ctx: Context) {
        Store.init(ctx); Settings.init(ctx)
        val now = System.currentTimeMillis()
        val d = Store.data.value
        val p = Settings.p
        val wanted = mutableSetOf<String>()

        for (e in d.entries) {
            val uri = "moyden://$T_ENTRY/${e.id}"
            val t = if (e.deleted) null else e.nextTrigger(now)
            if (t != null) {
                wanted += uri
                set(ctx, t.atMillis, pi(ctx, uri, {
                    putExtra(Notifs.EXTRA_TYPE, T_ENTRY); putExtra(Notifs.EXTRA_ID, e.id)
                    putExtra("day", t.day); putExtra("minute", t.minute)
                })!!, e.loud && p.loudEnabled)
            }
        }
        for (b in d.birthdays) {
            val uri = "moyden://$T_BDAY/${b.id}"
            val t = b.nextTrigger(now) ?: continue
            wanted += uri
            set(ctx, t.atMillis, pi(ctx, uri, {
                putExtra(Notifs.EXTRA_TYPE, T_BDAY); putExtra(Notifs.EXTRA_ID, b.id)
                putExtra("before", t.daysBefore); putExtra("bday", t.date.toEpochDay())
            })!!, false)
        }
        for (x in d.debts) {
            val uri = "moyden://$T_DEBT/${x.id}"
            val at = x.nextTrigger(now) ?: continue
            wanted += uri
            set(ctx, at, pi(ctx, uri, {
                putExtra(Notifs.EXTRA_TYPE, T_DEBT); putExtra(Notifs.EXTRA_ID, x.id)
            })!!, false)
        }
        if (p.summaryOn) {
            val uri = "moyden://$T_SUMMARY"
            val t = LocalTime.of(p.summaryMinute / 60, p.summaryMinute % 60)
            var at = today().atTime(t).millis()
            if (at <= now) at = today().plusDays(1).atTime(t).millis()
            wanted += uri
            set(ctx, at, pi(ctx, uri, { putExtra(Notifs.EXTRA_TYPE, T_SUMMARY) })!!, false)
        }

        // cancel alarms that are no longer needed
        val sp = ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        val before = sp.getStringSet("uris", emptySet()) ?: emptySet()
        val am = ctx.getSystemService(AlarmManager::class.java)
        (before - wanted).forEach { uri -> pi(ctx, uri, create = false)?.let { am.cancel(it); it.cancel() } }
        sp.edit().putStringSet("uris", wanted).apply()
    }

    fun snooze(ctx: Context, entryId: String, key: String, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val uri = "moyden://$T_SNOOZE/$entryId/$key"
        set(ctx, at, pi(ctx, uri, {
            putExtra(Notifs.EXTRA_TYPE, T_SNOOZE); putExtra(Notifs.EXTRA_ID, entryId)
            putExtra(Notifs.EXTRA_KEY, key)
        })!!, true)
    }

    fun test(ctx: Context, loud: Boolean, seconds: Int = 5) {
        val at = System.currentTimeMillis() + seconds * 1000L
        set(ctx, at, pi(ctx, "moyden://$T_TEST/$loud", {
            putExtra(Notifs.EXTRA_TYPE, T_TEST); putExtra("loud", loud)
        })!!, loud)
    }
}
