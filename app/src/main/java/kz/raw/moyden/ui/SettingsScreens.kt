package kz.raw.moyden.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.raw.moyden.data.City
import kz.raw.moyden.data.Cloud
import kz.raw.moyden.data.CloudError
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.UpdateState
import kz.raw.moyden.data.Updater
import kz.raw.moyden.data.Weather
import kz.raw.moyden.data.minuteText
import kz.raw.moyden.notify.Notifs
import kz.raw.moyden.notify.Scheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Account ----------------

@Composable
fun AccountScreen(nav: Nav) {
    val p = pal()
    val cloud by Cloud.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(cloud.email) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var askOut by remember { mutableStateOf(false) }

    fun go(create: Boolean) {
        error = ""
        if (!email.contains("@")) { error = "Введите адрес почты"; return }
        if (pass.length < 6) { error = "Пароль должен быть не короче 6 символов"; return }
        busy = true
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try { Cloud.signIn(email, pass, create); null } catch (e: CloudError) { e.message } catch (e: Exception) { "Нет связи с интернетом" }
            }
            busy = false
            if (err != null) error = err else pass = ""
        }
    }

    Screen("Аккаунт", onBack = { nav.back() }) {
        if (cloud.loggedIn) {
            Spacer(Modifier.height(8.dp))
            Card {
                Text("Вы вошли как", style = MaterialTheme.typography.titleSmall, color = p.muted)
                Text(cloud.email, style = MaterialTheme.typography.titleLarge, color = p.text)
                Spacer(Modifier.height(10.dp))
                val last = if (cloud.lastSync > 0)
                    SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(cloud.lastSync)) else "ещё не было"
                Text(if (cloud.syncing) "Сохраняю в облако…" else "Последнее сохранение: $last",
                    style = MaterialTheme.typography.bodyLarge, color = p.muted)
                if (cloud.error.isNotEmpty() && !cloud.syncing)
                    Text(cloud.error, style = MaterialTheme.typography.bodyLarge, color = p.bad)
            }
            Spacer(Modifier.height(12.dp))
            Text("Все дела, лекарства, дни рождения и деньги сохраняются в облаке автоматически. " +
                "На новом телефоне установите приложение и войдите с этой же почтой и паролем, всё появится само.",
                style = MaterialTheme.typography.bodyLarge, color = p.muted)
            Spacer(Modifier.height(16.dp))
            BigButton("Синхронизировать сейчас", { Cloud.syncNow() }, icon = Icons.Outlined.Sync)
            Spacer(Modifier.height(10.dp))
            SoftButton("Выйти из аккаунта", { askOut = true }, Modifier.fillMaxWidth(), color = p.bad)
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Войдите, чтобы данные хранились в облаке и не потерялись при смене телефона. " +
                "Если аккаунта ещё нет, придумайте пароль и нажмите «Создать аккаунт».",
                style = MaterialTheme.typography.bodyLarge, color = p.muted)
            Spacer(Modifier.height(16.dp))
            Field(email, { email = it.trim() }, "Почта", email = true)
            Spacer(Modifier.height(10.dp))
            Field(pass, { pass = it }, "Пароль (не меньше 6 символов)", password = true)
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(error, style = MaterialTheme.typography.bodyLarge, color = p.bad)
            }
            Spacer(Modifier.height(18.dp))
            if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
            else {
                BigButton("Войти", { go(false) })
                Spacer(Modifier.height(10.dp))
                SoftButton("Создать аккаунт", { go(true) }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Text("Запишите почту и пароль в надёжное место: они понадобятся на новом телефоне.",
                style = MaterialTheme.typography.bodyMedium, color = p.muted)
        }
    }
    if (askOut) Confirm("Выйти из аккаунта?", "Записи останутся на этом телефоне, но перестанут сохраняться в облако.",
        "Выйти", { Cloud.signOut() }) { askOut = false }
}

// ---------------- Appearance ----------------

@Composable
fun AppearanceScreen(nav: Nav) {
    val p = pal()
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(prefs.userName) }
    Screen("Оформление", onBack = { Settings.update { it.copy(userName = name.trim()) }; nav.back() }) {
        Label("Как к вам обращаться")
        Field(name, { name = it; Settings.update { s -> s.copy(userName = it.trim()) } }, "Имя (необязательно)")

        Label("Цветовая тема")
        PALETTES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val sel = t.id == prefs.themeId
                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(t.bg)
                        .border(if (sel) 3.dp else 1.dp, if (sel) p.accent else p.muted.copy(alpha = 0.25f), MaterialTheme.shapes.medium)
                        .clickable { Settings.update { it.copy(themeId = t.id) } }.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(t.accent))
                            Box(Modifier.size(18.dp).clip(CircleShape).background(t.card))
                            Box(Modifier.size(18.dp).clip(CircleShape).background(t.med))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(t.name, style = MaterialTheme.typography.labelLarge, color = t.text, maxLines = 2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Label("Шрифт")
        FONTS.forEach { f ->
            val sel = f.id == prefs.fontId
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.medium)
                .background(if (sel) p.accent.copy(alpha = 0.14f) else p.card)
                .border(if (sel) 2.dp else 0.dp, if (sel) p.accent else p.card, MaterialTheme.shapes.medium)
                .clickable { Settings.update { it.copy(fontId = f.id) } }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = f.family), color = p.text)
                    Text("Купить хлеб, позвонить сестре", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = f.family),
                        color = p.muted)
                }
                if (sel) Icon(Icons.Outlined.CheckCircle, null, tint = p.accent)
            }
        }

        Label("Размер текста")
        PillGroup(TEXT_SIZES, { it == prefs.textScale }) { v -> Settings.update { it.copy(textScale = v) } }

        Label("Валюта")
        PillGroup(listOf("₸", "₽", "$", "€", "сом").map { it to it }, { it == prefs.currency }) { c ->
            Settings.update { it.copy(currency = c) }
        }
    }
}

// ---------------- Notifications ----------------

@Composable
fun NotificationsScreen(nav: Nav) {
    val p = pal()
    val ctx = LocalContext.current
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    var pickSummary by remember { mutableStateOf(false) }
    fun upd(f: (kz.raw.moyden.data.Prefs) -> kz.raw.moyden.data.Prefs) { Settings.update(f); Scheduler.rescheduleAll(ctx) }

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val name = uri?.let { runCatching { RingtoneManager.getRingtone(ctx, it)?.getTitle(ctx) }.getOrNull() }.orEmpty()
            upd { it.copy(alarmSound = uri?.toString().orEmpty(), alarmSoundName = name) }
        }
    }

    Screen("Уведомления и звук", onBack = { nav.back() }) {
        Label("Что присылать")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchRow("Лекарства", null, prefs.notifMeds, { v -> upd { it.copy(notifMeds = v) } })
            SwitchRow("Дела", null, prefs.notifTasks, { v -> upd { it.copy(notifTasks = v) } })
            SwitchRow("События", null, prefs.notifEvents, { v -> upd { it.copy(notifEvents = v) } })
            SwitchRow("Дни рождения", null, prefs.notifBirthdays, { v -> upd { it.copy(notifBirthdays = v) } })
            SwitchRow("Сроки долгов", null, prefs.notifDebts, { v -> upd { it.copy(notifDebts = v) } })
        }

        Label("Утренняя сводка")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchRow("Присылать по утрам", "Погода, дела и дни рождения на день", prefs.summaryOn,
                { v -> upd { it.copy(summaryOn = v) } })
            if (prefs.summaryOn) SettingRow(Icons.Outlined.Schedule, "Время", minuteText(prefs.summaryMinute), { pickSummary = true })
        }

        Label("Громкий сигнал")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchRow("Разрешить громкий сигнал", "Для записей, где он включён, например для таблеток", prefs.loudEnabled,
                { v -> upd { it.copy(loudEnabled = v) } })
            SettingRow(Icons.Outlined.LibraryMusic, "Мелодия",
                prefs.alarmSoundName.ifBlank { "Как у будильника" }, {
                    ringtone.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Мелодия сигнала")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            prefs.alarmSound.takeIf { it.isNotBlank() }?.let(Uri::parse)))
                })
            SwitchRow("Вибрация", null, prefs.vibrate, { v -> upd { it.copy(vibrate = v) } })
        }
        Label("Отложить на")
        PillGroup(listOf(5, 10, 15, 30, 60).map { it to "$it мин" }, { it == prefs.snoozeMin }) { v -> upd { it.copy(snoozeMin = v) } }
        Label("Сколько звучит сигнал")
        PillGroup(listOf(1, 3, 5, 10).map { it to "$it мин" }, { it == prefs.alarmDurationMin }) { v -> upd { it.copy(alarmDurationMin = v) } }

        Label("Проверка")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Уведомление", { Scheduler.test(ctx, false) }, Modifier.weight(1f), Icons.Outlined.Notifications)
            SoftButton("Сигнал", { Scheduler.test(ctx, true) }, Modifier.weight(1f), Icons.Outlined.Timer)
        }
        Text("Сработает через 5 секунд. Можно выключить экран и проверить.",
            style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 8.dp))
    }
    if (pickSummary) TimePick(prefs.summaryMinute, { v -> upd { it.copy(summaryMinute = v) } }) { pickSummary = false }
}

// ---------------- Health check ----------------

@Composable
fun HealthScreen(nav: Nav) {
    val p = pal()
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val notifOk = remember(tick) { Notifs.allowed(ctx) }
    val exactOk = remember(tick) { Scheduler.canExact(ctx) }
    val fullOk = remember(tick) {
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val batteryOk = remember(tick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun open(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Screen("Проверка напоминаний", onBack = { nav.back() }) {
        Spacer(Modifier.height(4.dp))
        Text("Чтобы напоминания приходили вовремя, даже когда телефон долго лежит, все пункты должны быть зелёными.",
            style = MaterialTheme.typography.bodyLarge, color = p.muted)
        Spacer(Modifier.height(14.dp))
        HealthRow(Icons.Outlined.Notifications, "Уведомления разрешены", notifOk) {
            if (Build.VERSION.SDK_INT >= 33 && !notifOk) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        HealthRow(Icons.Outlined.Schedule, "Точное время напоминаний", exactOk) {
            if (Build.VERSION.SDK_INT >= 31) open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
        HealthRow(Icons.Outlined.Fullscreen, "Сигнал на весь экран", fullOk) {
            if (Build.VERSION.SDK_INT >= 34) open(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
        }
        HealthRow(Icons.Outlined.BatteryAlert, "Без ограничений экономии батареи", batteryOk) {
            @Suppress("BatteryLife")
            open(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        }
        Spacer(Modifier.height(10.dp))
        Card {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = p.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Для Xiaomi, Redmi, Huawei, Honor, Oppo, Realme, Vivo", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text("В настройках приложения включите «Автозапуск», а в разделе батареи выберите «Нет ограничений». " +
                        "На Samsung уберите приложение из списка «Спящие приложения».",
                        style = MaterialTheme.typography.bodyMedium, color = p.muted)
                    TextButton({ open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) {
                        Text("Открыть настройки приложения", color = p.accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Тест уведомления", { Scheduler.test(ctx, false) }, Modifier.weight(1f))
            SoftButton("Тест сигнала", { Scheduler.test(ctx, true) }, Modifier.weight(1f))
        }
        Text("Сработает через 5 секунд", style = MaterialTheme.typography.bodyMedium, color = p.muted,
            modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun HealthRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, ok: Boolean, fix: () -> Unit) {
    val p = pal()
    Card(Modifier.padding(bottom = 10.dp), onClick = if (ok) null else fix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (ok) p.good else p.bad)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(if (ok) "Всё в порядке" else "Нажмите, чтобы исправить", style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) p.good else p.bad)
            }
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (ok) p.good else p.bad)
        }
    }
}

// ---------------- City ----------------

private val QUICK_CITIES = listOf(
    City("Алматы", "Казахстан", 43.2567, 76.9286), City("Астана", "Казахстан", 51.1694, 71.4491),
    City("Шымкент", "Казахстан", 42.3417, 69.5901), City("Караганда", "Казахстан", 49.8047, 73.1094),
    City("Актобе", "Казахстан", 50.2839, 57.1670), City("Тараз", "Казахстан", 42.9000, 71.3667),
    City("Павлодар", "Казахстан", 52.2873, 76.9674), City("Усть-Каменогорск", "Казахстан", 49.9480, 82.6280),
    City("Семей", "Казахстан", 50.4111, 80.2275), City("Костанай", "Казахстан", 53.2144, 63.6246),
    City("Кызылорда", "Казахстан", 44.8528, 65.5092), City("Атырау", "Казахстан", 47.1167, 51.8833),
    City("Актау", "Казахстан", 43.6481, 51.1722), City("Уральск", "Казахстан", 51.2333, 51.3667),
    City("Петропавловск", "Казахстан", 54.8667, 69.1500), City("Талдыкорган", "Казахстан", 45.0156, 78.3739),
    City("Туркестан", "Казахстан", 43.2973, 68.2517), City("Москва", "Россия", 55.7558, 37.6173),
)

@Composable
fun CityScreen(nav: Nav) {
    val p = pal()
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    fun choose(c: City) {
        Settings.update { it.copy(city = c.name, lat = c.lat, lon = c.lon) }
        Weather.refresh(force = true)
        nav.back()
    }

    Screen("Город для погоды", onBack = { nav.back() }) {
        Spacer(Modifier.height(4.dp))
        Text("Сейчас: ${prefs.city}", style = MaterialTheme.typography.titleMedium, color = p.text)
        Spacer(Modifier.height(12.dp))
        Field(q, { q = it }, "Найти город или село")
        Spacer(Modifier.height(10.dp))
        BigButton(if (busy) "Ищу…" else "Найти", enabled = q.isNotBlank() && !busy, icon = Icons.Outlined.Search, onClick = {
            busy = true; err = ""
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Weather.searchCities(q) }.getOrNull() }
                busy = false
                if (r == null) err = "Нет связи с интернетом" else { results = r; if (r.isEmpty()) err = "Ничего не нашлось" }
            }
        })
        if (err.isNotEmpty()) Text(err, color = p.bad, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
        val list = results ?: QUICK_CITIES
        SectionTitle(if (results == null) "Быстрый выбор" else "Результаты")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            list.forEach { c ->
                SettingRow(Icons.Outlined.LocationOn, c.name, c.region, { choose(c) },
                    trailing = if (c.name == prefs.city) ({ Icon(Icons.Outlined.CheckCircle, null, tint = p.accent) }) else null)
            }
        }
    }
}

// ---------------- Updates ----------------

@Composable
fun UpdatesScreen(nav: Nav) {
    val p = pal()
    val ctx = LocalContext.current
    val st by Updater.state.collectAsStateWithLifecycle()
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    Screen("Обновления", onBack = { nav.back() }) {
        Spacer(Modifier.height(8.dp))
        Card {
            Text("Установлена версия", style = MaterialTheme.typography.titleSmall, color = p.muted)
            Text(Updater.current, style = MaterialTheme.typography.headlineMedium, color = p.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val msg = when (val s = st) {
                UpdateState.Checking -> "Проверяю…"
                UpdateState.UpToDate -> "У вас последняя версия"
                is UpdateState.Available -> "Доступна версия ${s.release.version}"
                is UpdateState.Downloading -> "Загрузка: ${(s.progress * 100).toInt()}%"
                is UpdateState.Ready -> "Обновление скачано, можно устанавливать"
                is UpdateState.Failed -> s.message
                UpdateState.Idle -> "Приложение само проверяет обновления при запуске"
            }
            Text(msg, style = MaterialTheme.typography.bodyLarge, color = if (st is UpdateState.Failed) p.bad else p.muted)
            (st as? UpdateState.Downloading)?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth(), color = p.accent)
            }
        }
        Spacer(Modifier.height(14.dp))
        when (val s = st) {
            is UpdateState.Available -> BigButton("Скачать и обновить", { Updater.download(ctx, s.release) })
            is UpdateState.Ready -> BigButton("Установить", {
                if (Updater.canInstall(ctx)) Updater.install(ctx, s.file) else Updater.openInstallPermission(ctx)
            })
            is UpdateState.Downloading, UpdateState.Checking -> {}
            else -> BigButton("Проверить обновления", { Updater.check(ctx) })
        }
        Spacer(Modifier.height(10.dp))
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            SwitchRow("Проверять автоматически", "При запуске, не чаще раза в 6 часов", prefs.autoUpdate,
                { v -> Settings.update { it.copy(autoUpdate = v) } })
        }
        Spacer(Modifier.height(10.dp))
        Text("При первом обновлении телефон попросит разрешить установку из этого приложения. Это нужно сделать один раз. " +
            "Все записи при обновлении сохраняются.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}
