package kz.raw.moyden.notify

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kz.raw.moyden.R
import kz.raw.moyden.data.Settings

/**
 * Loud reminder: plays the alarm sound in a loop and shows a full-screen screen
 * until the person presses "Принял" / "Отложить", or the time limit runs out.
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var current: Bundle? = null

    private data class Bundle(val title: String, val text: String, val id: String, val key: String, val meds: Boolean)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Settings.init(this)
        if (intent?.action == Notifs.ACT_STOP) { stopAll(); return START_NOT_STICKY }
        val b = Bundle(
            intent?.getStringExtra(Notifs.EXTRA_TITLE).orEmpty(),
            intent?.getStringExtra(Notifs.EXTRA_TEXT).orEmpty(),
            intent?.getStringExtra(Notifs.EXTRA_ID).orEmpty(),
            intent?.getStringExtra(Notifs.EXTRA_KEY).orEmpty(),
            intent?.getBooleanExtra(Notifs.EXTRA_MEDS, false) ?: false,
        )
        // a previous alarm still ringing becomes a normal notification
        current?.let { prev ->
            if (prev.id.isNotEmpty() && prev.id != b.id)
                Notifs.reminder(this, if (prev.meds) Notifs.CH_MEDS else Notifs.CH_REMIND,
                    prev.title, prev.text, prev.id, prev.key, Settings.p.snoozeMin)
        }
        current = b

        val notif = buildNotification(b)
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notif,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } catch (e: Exception) {
            // could not run in foreground: fall back to a normal loud notification
            Notifs.reminder(this, if (b.meds) Notifs.CH_MEDS else Notifs.CH_REMIND, b.title, b.text, b.id, b.key, Settings.p.snoozeMin)
            stopSelf(); return START_NOT_STICKY
        }
        acquireWake()
        startSound()
        try { startActivity(alarmIntent(b).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            // nobody answered: leave a reminder in the shade and go quiet
            current?.let {
                if (it.id.isNotEmpty())
                    Notifs.reminder(this, if (it.meds) Notifs.CH_MEDS else Notifs.CH_REMIND,
                        "Пропущено: " + it.title, it.text, it.id, it.key, Settings.p.snoozeMin)
            }
            stopAll()
        }, Settings.p.alarmDurationMin.coerceIn(1, 15) * 60_000L)
        return START_NOT_STICKY
    }

    private fun alarmIntent(b: Bundle) = Intent(this, AlarmActivity::class.java)
        .putExtra(Notifs.EXTRA_TITLE, b.title).putExtra(Notifs.EXTRA_TEXT, b.text)
        .putExtra(Notifs.EXTRA_ID, b.id).putExtra(Notifs.EXTRA_KEY, b.key)
        .putExtra(Notifs.EXTRA_MEDS, b.meds)

    private fun buildNotification(b: Bundle): android.app.Notification {
        val full = PendingIntent.getActivity(this, 11, alarmIntent(b),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun act(a: String, code: Int) = PendingIntent.getBroadcast(this, code,
            Intent(this, ActionReceiver::class.java).setAction(a)
                .putExtra(Notifs.EXTRA_ID, b.id).putExtra(Notifs.EXTRA_KEY, b.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifs.CH_ALARM)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(b.title)
            .setContentText(b.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(b.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (b.id.isNotEmpty()) {
            n.addAction(0, if (b.meds) "Принял" else "Сделано", act(Notifs.ACT_DONE, 12))
            n.addAction(0, "Через ${Settings.p.snoozeMin} мин", act(Notifs.ACT_SNOOZE, 13))
        } else {
            n.addAction(0, "Выключить", act(Notifs.ACT_STOP, 14))
        }
        return n.build()
    }

    private fun soundUri(): Uri {
        val chosen = Settings.p.alarmSound
        if (chosen.isNotEmpty()) return Uri.parse(chosen)
        return RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    private fun startSound() {
        stopSound()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun tryPlay(uri: Uri): Boolean = try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            player?.release(); player = null; false
        }
        if (!tryPlay(soundUri())) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { tryPlay(it) }
        }
        // make sure the alarm stream is audible
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max / 2)
                am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7).toInt().coerceAtLeast(1), 0)
        }
        if (Settings.p.vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= 31)
                getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 600, 700, 1200), 0))
        }
    }

    private fun stopSound() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel(); vibrator = null
    }

    private fun acquireWake() {
        if (wake?.isHeld == true) return
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "moyden:alarm").apply { acquire(16 * 60_000L) }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        wake?.takeIf { it.isHeld }?.release(); wake = null
        current = null
        sendBroadcast(Intent(AlarmActivity.ACTION_CLOSE).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        wake?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4242

        fun start(ctx: Context, title: String, text: String, id: String, key: String, meds: Boolean) {
            val i = Intent(ctx, AlarmService::class.java)
                .putExtra(Notifs.EXTRA_TITLE, title).putExtra(Notifs.EXTRA_TEXT, text)
                .putExtra(Notifs.EXTRA_ID, id).putExtra(Notifs.EXTRA_KEY, key)
                .putExtra(Notifs.EXTRA_MEDS, meds)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                Notifs.reminder(ctx, if (meds) Notifs.CH_MEDS else Notifs.CH_REMIND, title, text, id, key, Settings.p.snoozeMin)
            }
        }

        fun stop(ctx: Context) {
            // only reaches the service if it is running
            runCatching { ctx.startService(Intent(ctx, AlarmService::class.java).setAction(Notifs.ACT_STOP)) }
        }
    }
}
