package kz.raw.moyden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.data.Cloud
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.Updater
import kz.raw.moyden.data.ageOn
import kz.raw.moyden.data.dayMonth
import kz.raw.moyden.data.inDays
import kz.raw.moyden.data.isFinished
import kz.raw.moyden.data.nextDate
import kz.raw.moyden.data.scheduleText
import kz.raw.moyden.data.today
import kz.raw.moyden.data.yearsWord
import java.time.temporal.ChronoUnit

@Composable
fun MoreScreen(nav: Nav) {
    val p = pal()
    val cloud by Cloud.state.collectAsStateWithLifecycle()
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    val data by Store.data.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = Pad)) {
        Spacer(Modifier.height(18.dp))
        Text("Ещё", style = MaterialTheme.typography.headlineLarge, color = p.text)
        Spacer(Modifier.height(14.dp))

        Card(onClick = { nav.go(Route.Account) },
            color = if (cloud.loggedIn) null else p.accent.copy(alpha = 0.13f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (cloud.loggedIn) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    if (cloud.loggedIn) p.good else p.accent, 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (cloud.loggedIn) "Аккаунт" else "Войти в аккаунт", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text(if (cloud.loggedIn) cloud.email else "Чтобы данные сохранялись и переносились на другой телефон",
                        style = MaterialTheme.typography.bodyMedium, color = p.muted)
                }
            }
        }

        SectionTitle("Мои записи")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            val tasks = data.liveEntries.count { it.kind != Kind.MEDICINE && !it.isFinished() }
            val meds = data.liveEntries.count { it.kind == Kind.MEDICINE && !it.isFinished() }
            SettingRow(kindIcon(Kind.TASK), "Все дела и события", "Активных: $tasks", { nav.go(Route.Entries(null)) })
            SettingRow(kindIcon(Kind.MEDICINE), "Лекарства", "Активных: $meds", { nav.go(Route.Entries(Kind.MEDICINE)) }, tint = p.med)
            SettingRow(BirthdayIcon, "Дни рождения", "Записано: ${data.liveBirthdays.size}", { nav.go(Route.Birthdays) }, tint = p.bad)
        }

        SectionTitle("Настройки")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            SettingRow(Icons.Outlined.Palette, "Оформление", "Тема, шрифт, размер текста", { nav.go(Route.Appearance) })
            SettingRow(Icons.Outlined.NotificationsActive, "Уведомления и звук", "Что присылать, сигнал, утренняя сводка",
                { nav.go(Route.Notifications) })
            SettingRow(Icons.Outlined.LocationOn, "Город для погоды", prefs.city, { nav.go(Route.City) })
            SettingRow(Icons.Outlined.HealthAndSafety, "Проверка напоминаний", "Убедиться, что всё будет приходить",
                { nav.go(Route.Health) })
            SettingRow(Icons.Outlined.SystemUpdate, "Обновления", "Версия ${Updater.current}", { nav.go(Route.Updates) })
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun EntriesScreen(nav: Nav, kind: Kind?) {
    val p = pal()
    val data by Store.data.collectAsStateWithLifecycle()
    var showDone by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(kind?.name ?: "ALL") }
    val list = data.liveEntries.filter {
        (filter == "ALL" && it.kind != Kind.MEDICINE || it.kind.name == filter) && it.isFinished() == showDone
    }.sortedWith(compareBy({ it.date ?: Long.MAX_VALUE }, { it.times.minOrNull() ?: 0 }))

    Screen(if (kind == Kind.MEDICINE) "Лекарства" else "Мои дела", onBack = { nav.back() },
        bottom = {
            BigButton(if (kind == Kind.MEDICINE) "Добавить лекарство" else "Добавить дело",
                { nav.go(Route.EditEntry(null, kind ?: Kind.TASK)) })
        }) {
        if (kind != Kind.MEDICINE) {
            Spacer(Modifier.height(4.dp))
            PillGroup(listOf("ALL" to "Все", "TASK" to "Дела", "EVENT" to "События"), { it == filter }) { filter = it }
        }
        Spacer(Modifier.height(10.dp))
        PillGroup(listOf(false to "Активные", true to "Завершённые"), { it == showDone }) { showDone = it }
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) EmptyNote(if (showDone) "Завершённых пока нет" else "Здесь пока пусто")
        list.forEach { e ->
            Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditEntry(e.id, e.kind)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(kindIcon(e.kind), kindColor(e.kind))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(e.scheduleText(), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                        if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodyMedium, color = p.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun BirthdaysScreen(nav: Nav) {
    val p = pal()
    val data by Store.data.collectAsStateWithLifecycle()
    val t = today()
    val list = data.liveBirthdays.map { it to it.nextDate(t) }.sortedBy { it.second }
    Screen("Дни рождения", onBack = { nav.back() },
        bottom = { BigButton("Добавить день рождения", { nav.go(Route.EditBirthday(null)) }) }) {
        Spacer(Modifier.height(4.dp))
        if (list.isEmpty()) EmptyNote("Добавьте дни рождения родных и друзей, и приложение напомнит заранее")
        list.forEach { (b, d) ->
            val n = ChronoUnit.DAYS.between(t, d)
            Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditBirthday(b.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(BirthdayIcon, p.bad)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(dayMonth(d) + (b.ageOn(d)?.let { ", исполнится ${yearsWord(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = p.muted)
                    }
                    Text(inDays(n), style = MaterialTheme.typography.labelLarge, color = if (n <= 1) p.bad else p.muted)
                }
            }
        }
    }
}
