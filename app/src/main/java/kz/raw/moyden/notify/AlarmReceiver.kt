package kz.raw.moyden.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kz.raw.moyden.data.Birthday
import kz.raw.moyden.data.Cloud
import kz.raw.moyden.data.Debt
import kz.raw.moyden.data.Entry
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.Weather
import kz.raw.moyden.data.ageOn
import kz.raw.moyden.data.day
import kz.raw.moyden.data.inDays
import kz.raw.moyden.data.isDone
import kz.raw.moyden.data.minuteText
import kz.raw.moyden.data.money
import kz.raw.moyden.data.nextDate
import kz.raw.moyden.data.occKey
import kz.raw.moyden.data.occurrencesOn
import kz.raw.moyden.data.today
import kz.raw.moyden.data.weatherText
import kz.raw.moyden.data.yearsWord
import java.time.temporal.ChronoUnit

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx); Settings.init(ctx); Cloud.init(ctx); Weather.init(ctx)
        val type = intent.getStringExtra(Notifs.EXTRA_TYPE)
        val id = intent.getStringExtra(Notifs.EXTRA_ID).orEmpty()
        when (type) {
            Scheduler.T_ENTRY -> {
                val dayEp = intent.getLongExtra("day", 0)
                val minute = intent.getIntExtra("minute", 0)
                (Store.get(id) as? Entry)?.let { fireEntry(ctx, it, dayEp, minute, occKey(dayEp, minute)) }
            }
            Scheduler.T_SNOOZE -> {
                val key = intent.getStringExtra(Notifs.EXTRA_KEY).orEmpty()
                val e = Store.get(id) as? Entry
                if (e != null && !e.deleted && !e.done.contains(key)) {
                    val dayEp = key.substringBefore(":").toLongOrNull() ?: today().toEpochDay()
                    val minute = key.substringAfter(":").toIntOrNull() ?: 0
                    fireEntry(ctx, e, dayEp, minute, key, snoozed = true)
                }
            }
            Scheduler.T_BDAY -> (Store.get(id) as? Birthday)?.let { fireBirthday(ctx, it, intent.getIntExtra("before", 0)) }
            Scheduler.T_DEBT -> (Store.get(id) as? Debt)?.let { fireDebt(ctx, it) }
            Scheduler.T_TEST -> {
                if (intent.getBooleanExtra("loud", false))
                    AlarmService.start(ctx, "Проверка сигнала", "Так будет звучать напоминание о лекарстве", "", "", true)
                else Notifs.simple(ctx, Notifs.CH_REMIND, "test", "Проверка уведомления", "Уведомления работают")
            }
            Scheduler.T_SUMMARY -> {
                val pending = goAsync()
                Thread {
                    try { summary(ctx) } finally {
                        Scheduler.rescheduleAll(ctx); pending.finish()
                    }
                }.start()
                return
            }
        }
        Scheduler.rescheduleAll(ctx)
    }

    private fun fireEntry(ctx: Context, e: Entry, dayEp: Long, minute: Int, key: String, snoozed: Boolean = false) {
        if (e.deleted || e.isDone(dayEp, minute)) return
        val p = Settings.p
        val enabled = when (e.kind) {
            Kind.TASK -> p.notifTasks; Kind.MEDICINE -> p.notifMeds; Kind.EVENT -> p.notifEvents
        }
        if (!enabled) return
        val time = minuteText(minute)
        val (title, text) = when (e.kind) {
            Kind.MEDICINE -> "Пора принять лекарство" to listOf(e.title, e.note).filter { it.isNotBlank() }.joinToString("\n")
            Kind.EVENT -> {
                val whenText = if (e.remindBefore > 0 && !snoozed) {
                    val d = day(dayEp)
                    val prefix = when (ChronoUnit.DAYS.between(today(), d)) {
                        0L -> "Сегодня"; 1L -> "Завтра"; else -> inDays(ChronoUnit.DAYS.between(today(), d))
                    }
                    "$prefix в $time"
                } else "Сейчас, $time"
                e.title to listOf(whenText, e.note).filter { it.isNotBlank() }.joinToString("\n")
            }
            Kind.TASK -> e.title to listOf("Напоминание на $time", e.note).filter { it.isNotBlank() }.joinToString("\n")
        }
        if (e.loud && p.loudEnabled) {
            AlarmService.start(ctx, title, text, e.id, key, e.kind == Kind.MEDICINE)
        } else {
            val ch = if (e.kind == Kind.MEDICINE) Notifs.CH_MEDS else Notifs.CH_REMIND
            Notifs.reminder(ctx, ch, title, text, e.id, key, p.snoozeMin)
        }
    }

    private fun fireBirthday(ctx: Context, b: Birthday, before: Int) {
        if (b.deleted || !Settings.p.notifBirthdays) return
        val date = b.nextDate(today())
        val age = b.ageOn(date)?.let { ", исполнится ${yearsWord(it)}" }.orEmpty()
        val title = when (before) {
            0 -> "Сегодня день рождения: ${b.name}"
            1 -> "Завтра день рождения: ${b.name}"
            else -> "Через $before дн. день рождения: ${b.name}"
        }
        val text = (if (before == 0) "Не забудьте поздравить$age" else "Пора подумать о подарке$age") +
            (if (b.note.isNotBlank()) "\n${b.note}" else "")
        Notifs.simple(ctx, Notifs.CH_BDAY, "bday" + b.id + before, title, text)
    }

    private fun fireDebt(ctx: Context, d: Debt) {
        if (d.deleted || d.closed || !Settings.p.notifDebts || d.due == null) return
        val n = ChronoUnit.DAYS.between(today(), day(d.due))
        val w = if (n <= 0) "Сегодня" else "Завтра"
        val text = if (d.theyOweMe) "$w ${d.person} должен вернуть вам ${money(d.left)}"
        else "$w нужно вернуть ${d.person}: ${money(d.left)}"
        Notifs.simple(ctx, Notifs.CH_MONEY, "debt" + d.id, "Срок долга", text)
    }

    private fun summary(ctx: Context) {
        val p = Settings.p
        if (!p.summaryOn) return
        runCatching { Cloud.syncBlocking() }
        val w = Weather.fetchBlocking()
        val d = Store.data.value
        val t = today()
        val occ = d.liveEntries.flatMap { it.occurrencesOn(t) }
        val meds = occ.count { it.entry.kind == Kind.MEDICINE }
        val other = occ.count { it.entry.kind != Kind.MEDICINE }
        val bdays = d.liveBirthdays.filter { it.nextDate(t) == t }
        val lines = mutableListOf<String>()
        if (w != null) {
            val todayF = w.days.firstOrNull { it.date == t }
            lines += "${w.city}: ${weatherText(w.code).lowercase()}, сейчас ${w.temp}°" +
                (todayF?.let { ", днём до ${it.max}°" } ?: "") +
                (todayF?.takeIf { it.rain >= 50 }?.let { ". Возьмите зонт" } ?: "")
        }
        if (other > 0) lines += "Дел и событий на сегодня: $other"
        if (meds > 0) lines += "Приёмов лекарств: $meds"
        if (bdays.isNotEmpty()) lines += "День рождения: " + bdays.joinToString(", ") { it.name }
        if (lines.isEmpty()) return
        val hello = when (t.dayOfWeek.value) { 6, 7 -> "Хороших выходных"; else -> "Доброе утро" } +
            (if (p.userName.isNotBlank()) ", ${p.userName}" else "")
        Notifs.simple(ctx, Notifs.CH_SUMMARY, "summary", hello, lines.joinToString("\n"))
    }
}
