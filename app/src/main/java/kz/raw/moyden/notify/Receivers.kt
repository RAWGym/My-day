package kz.raw.moyden.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kz.raw.moyden.data.Cloud
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store

/** Buttons on notifications and on the alarm screen. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx); Settings.init(ctx); Cloud.init(ctx)
        val id = intent.getStringExtra(Notifs.EXTRA_ID).orEmpty()
        val key = intent.getStringExtra(Notifs.EXTRA_KEY).orEmpty()
        val nid = intent.getIntExtra(Notifs.EXTRA_NID, 0)
        handle(ctx, intent.action, id, key)
        if (nid != 0) Notifs.cancel(ctx, nid)
    }

    companion object {
        fun handle(ctx: Context, action: String?, id: String, key: String) {
            when (action) {
                Notifs.ACT_DONE -> if (id.isNotEmpty()) Store.toggleDone(id, key, true)
                Notifs.ACT_SNOOZE -> if (id.isNotEmpty()) Scheduler.snooze(ctx, id, key, Settings.p.snoozeMin)
            }
            AlarmService.stop(ctx)
            Scheduler.rescheduleAll(ctx)
        }
    }
}

/** Alarms are lost on reboot and on time changes, so we set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        runCatching { Scheduler.rescheduleAll(ctx) }
    }
}
