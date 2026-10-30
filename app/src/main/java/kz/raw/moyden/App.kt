package kz.raw.moyden

import android.app.Application
import kz.raw.moyden.data.Cloud
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.Weather
import kz.raw.moyden.notify.Notifs
import kz.raw.moyden.notify.Scheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Store.init(this)
        Cloud.init(this)
        Weather.init(this)
        Notifs.createChannels(this)
        Store.onChanged = {
            Scheduler.rescheduleAll(this)
            Cloud.requestSync()
        }
        Scheduler.rescheduleAll(this)
    }
}
