package kz.raw.moyden.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kz.raw.moyden.R
import kz.raw.moyden.ui.MainActivity

object Notifs {
    const val CH_REMIND = "remind"
    const val CH_MEDS = "meds"
    const val CH_BDAY = "birthdays"
    const val CH_MONEY = "money"
    const val CH_SUMMARY = "summary"
    const val CH_ALARM = "alarm_v1"
    const val CH_UPDATES = "updates"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

        fun ch(id: String, name: String, desc: String, imp: Int, withSound: Boolean = true) =
            NotificationChannel(id, name, imp).apply {
                description = desc
                enableVibration(withSound)
                if (withSound) setSound(sound, attrs) else setSound(null, null)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

        nm.createNotificationChannels(listOf(
            ch(CH_REMIND, "Дела и события", "Напоминания о делах и событиях", NotificationManager.IMPORTANCE_HIGH),
            ch(CH_MEDS, "Лекарства", "Напоминания принять лекарство", NotificationManager.IMPORTANCE_HIGH),
            ch(CH_BDAY, "Дни рождения", "Кого поздравить", NotificationManager.IMPORTANCE_HIGH),
            ch(CH_MONEY, "Долги", "Сроки возврата долгов", NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_SUMMARY, "Утренняя сводка", "Погода и планы на день", NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_ALARM, "Громкий сигнал", "Звучит как будильник, пока не нажмёте «Принял»",
                NotificationManager.IMPORTANCE_HIGH, withSound = false).apply { setBypassDnd(true) },
            ch(CH_UPDATES, "Обновления", "Новая версия приложения", NotificationManager.IMPORTANCE_LOW, withSound = false),
        ))
    }

    fun allowed(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun nid(key: String): Int = key.hashCode() and 0x7fffffff

    fun openApp(ctx: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        ctx, code, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(ctx: Context, act: String, entryId: String, key: String, notifId: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(act)
            .putExtra(EXTRA_ID, entryId).putExtra(EXTRA_KEY, key).putExtra(EXTRA_NID, notifId)
        return PendingIntent.getBroadcast(ctx, (act + entryId + key).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun post(ctx: Context, id: Int, n: NotificationCompat.Builder) {
        if (!allowed(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(id, n.build()) } catch (_: SecurityException) {}
    }

    fun base(ctx: Context, channel: String, title: String, text: String, nid: Int) =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx, nid))
            .setAutoCancel(true)

    /** Regular reminder with "Done" and "Later" buttons. */
    fun reminder(ctx: Context, channel: String, title: String, text: String,
                 entryId: String, key: String, snoozeMin: Int) {
        val nid = nid(entryId + key)
        val doneLabel = if (channel == CH_MEDS) "Принял" else "Сделано"
        post(ctx, nid, base(ctx, channel, title, text, nid)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, doneLabel, action(ctx, ACT_DONE, entryId, key, nid))
            .addAction(0, "Через $snoozeMin мин", action(ctx, ACT_SNOOZE, entryId, key, nid)))
    }

    fun simple(ctx: Context, channel: String, key: String, title: String, text: String) {
        val nid = nid(key)
        post(ctx, nid, base(ctx, channel, title, text, nid))
    }

    fun cancel(ctx: Context, nid: Int) = NotificationManagerCompat.from(ctx).cancel(nid)

    const val EXTRA_ID = "id"
    const val EXTRA_KEY = "key"
    const val EXTRA_NID = "nid"
    const val EXTRA_TYPE = "type"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_MEDS = "meds"
    const val ACT_DONE = "kz.raw.moyden.DONE"
    const val ACT_SNOOZE = "kz.raw.moyden.SNOOZE"
    const val ACT_STOP = "kz.raw.moyden.STOP"
}
