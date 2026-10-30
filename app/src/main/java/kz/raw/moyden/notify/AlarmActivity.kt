package kz.raw.moyden.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.ui.LocalPalette
import kz.raw.moyden.ui.MoyDenTheme
import java.time.LocalTime

class AlarmActivity : ComponentActivity() {

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this); Store.init(this)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        ContextCompat.registerReceiver(this, closer, IntentFilter(ACTION_CLOSE), ContextCompat.RECEIVER_NOT_EXPORTED)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent); render(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closer) }
        super.onDestroy()
    }

    private fun render(i: Intent) {
        val title = i.getStringExtra(Notifs.EXTRA_TITLE).orEmpty()
        val text = i.getStringExtra(Notifs.EXTRA_TEXT).orEmpty()
        val id = i.getStringExtra(Notifs.EXTRA_ID).orEmpty()
        val key = i.getStringExtra(Notifs.EXTRA_KEY).orEmpty()
        val meds = i.getBooleanExtra(Notifs.EXTRA_MEDS, false)
        setContent {
            MoyDenTheme {
                AlarmScreen(title, text, meds, hasItem = id.isNotEmpty(), snooze = Settings.p.snoozeMin,
                    onDone = { ActionReceiver.handle(this, Notifs.ACT_DONE, id, key); finish() },
                    onSnooze = { ActionReceiver.handle(this, Notifs.ACT_SNOOZE, id, key); finish() },
                    onStop = { ActionReceiver.handle(this, Notifs.ACT_STOP, id, key); finish() })
            }
        }
    }

    companion object { const val ACTION_CLOSE = "kz.raw.moyden.ALARM_CLOSE" }
}

@Composable
private fun AlarmScreen(title: String, text: String, meds: Boolean, hasItem: Boolean, snooze: Int,
                        onDone: () -> Unit, onSnooze: () -> Unit, onStop: () -> Unit) {
    val pal = LocalPalette.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = LocalTime.now() } }
    Box(Modifier.fillMaxSize().background(pal.bg).padding(28.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                Text("%02d:%02d".format(now.hour, now.minute), style = MaterialTheme.typography.displayLarge,
                    color = pal.text)
                Spacer(Modifier.height(40.dp))
                Box(Modifier.size(112.dp).background(pal.accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(if (meds) Icons.Outlined.Medication else Icons.Outlined.NotificationsActive, null,
                        tint = pal.accent, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = pal.text, textAlign = TextAlign.Center)
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.titleLarge, color = pal.muted, textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (hasItem) {
                    Button(onDone, Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = pal.onAccent)) {
                        Text(if (meds) "Принял" else "Сделано", style = MaterialTheme.typography.headlineSmall)
                    }
                    OutlinedButton(onSnooze, Modifier.fillMaxWidth().height(64.dp)) {
                        Text("Напомнить через $snooze мин", style = MaterialTheme.typography.titleMedium, color = pal.text)
                    }
                } else {
                    Button(onStop, Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = pal.onAccent)) {
                        Text("Выключить", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
