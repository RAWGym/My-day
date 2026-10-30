package kz.raw.moyden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.data.AppData
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.Occurrence
import kz.raw.moyden.data.Repeat
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Sky
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.WD_SHORT
import kz.raw.moyden.data.Weather
import kz.raw.moyden.data.WeatherInfo
import kz.raw.moyden.data.ageOn
import kz.raw.moyden.data.day
import kz.raw.moyden.data.ep
import kz.raw.moyden.data.inDays
import kz.raw.moyden.data.isDone
import kz.raw.moyden.data.isFinished
import kz.raw.moyden.data.minuteText
import kz.raw.moyden.data.money
import kz.raw.moyden.data.nextDate
import kz.raw.moyden.data.occKey
import kz.raw.moyden.data.occurrencesOn
import kz.raw.moyden.data.relativeDay
import kz.raw.moyden.data.skyOf
import kz.raw.moyden.data.today
import kz.raw.moyden.data.weatherText
import kz.raw.moyden.data.weekdayDate
import kz.raw.moyden.data.yearsWord
import kz.raw.moyden.notify.Notifs
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

fun skyIcon(code: Int, isDay: Boolean = true): ImageVector = when (skyOf(code)) {
    Sky.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    Sky.PARTLY -> Icons.Outlined.FilterDrama
    Sky.CLOUDY -> Icons.Outlined.Cloud
    Sky.FOG -> Icons.Outlined.Dehaze
    Sky.DRIZZLE -> Icons.Outlined.WaterDrop
    Sky.RAIN -> Icons.Outlined.Umbrella
    Sky.SNOW -> Icons.Outlined.AcUnit
    Sky.STORM -> Icons.Outlined.FlashOn
}

@Composable
fun TodayScreen(nav: Nav) {
    val p = pal()
    val ctx = LocalContext.current
    val data by Store.data.collectAsStateWithLifecycle()
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    val t = today()
    val hour = LocalTime.now().hour
    val hello = when (hour) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Доброй ночи" }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = Pad)) {
        Spacer(Modifier.height(18.dp))
        Text(weekdayDate(t).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = p.muted)
        Text(hello + if (prefs.userName.isNotBlank()) ", ${prefs.userName}" else "",
            style = MaterialTheme.typography.headlineLarge, color = p.text)

        if (!Notifs.allowed(ctx)) {
            Spacer(Modifier.height(14.dp))
            Card(onClick = { nav.go(Route.Health) }, color = p.bad.copy(alpha = 0.12f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsOff, null, tint = p.bad)
                    Spacer(Modifier.width(12.dp))
                    Text("Уведомления выключены, напоминания не придут. Нажмите, чтобы включить",
                        style = MaterialTheme.typography.bodyLarge, color = p.text)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        WeatherCard(onCity = { nav.go(Route.City) })

        val cloud by kz.raw.moyden.data.Cloud.state.collectAsStateWithLifecycle()
        if (!cloud.loggedIn) {
            Spacer(Modifier.height(12.dp))
            Card(onClick = { nav.go(Route.Account) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Cloud, p.accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Войдите в аккаунт", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text("Чтобы записи не потерялись и были на новом телефоне",
                            style = MaterialTheme.typography.bodyMedium, color = p.muted)
                    }
                }
            }
        }

        DayAgenda(data, t, nav, showUndated = true)

        // birthdays soon
        val soon = data.liveBirthdays.map { it to it.nextDate(t) }
            .filter { ChronoUnit.DAYS.between(t, it.second) <= 30 }.sortedBy { it.second }
        if (soon.isNotEmpty()) {
            SectionTitle("Дни рождения")
            soon.forEach { (b, d) ->
                val n = ChronoUnit.DAYS.between(t, d)
                Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditBirthday(b.id)) },
                    color = if (n == 0L) p.bad.copy(alpha = 0.12f) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(BirthdayIcon, p.bad)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium, color = p.text)
                            val age = b.ageOn(d)?.let { ", исполнится ${yearsWord(it)}" }.orEmpty()
                            Text(inDays(n).replaceFirstChar { it.uppercase() } + age,
                                style = MaterialTheme.typography.bodyMedium, color = if (n == 0L) p.bad else p.muted)
                        }
                    }
                }
            }
        }

        // debts with a close due date
        val debts = data.liveDebts.filter { !it.closed && it.due != null && it.due - t.ep() <= 7 }.sortedBy { it.due }
        if (debts.isNotEmpty()) {
            SectionTitle("Долги")
            debts.forEach { x ->
                val n = x.due!! - t.ep()
                Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditDebt(x.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Outlined.Handshake, if (x.theyOweMe) p.good else p.bad)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(x.person, style = MaterialTheme.typography.titleMedium, color = p.text)
                            Text((if (x.theyOweMe) "Должен вам" else "Вы должны") + ": " + money(x.left),
                                style = MaterialTheme.typography.bodyMedium, color = p.muted)
                        }
                        Text(if (n < 0) "Просрочен" else inDays(n), style = MaterialTheme.typography.labelLarge,
                            color = if (n <= 0) p.bad else p.muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun WeatherCard(onCity: () -> Unit) {
    val p = pal()
    val w by Weather.state.collectAsStateWithLifecycle()
    val loading by Weather.loading.collectAsStateWithLifecycle()
    val prefs by Settings.prefs.collectAsStateWithLifecycle()
    val info: WeatherInfo? = w?.takeIf { it.city == prefs.city }
    Card(onClick = { Weather.refresh(force = true) }, color = p.accent.copy(alpha = if (p.dark) 0.18f else 0.13f)) {
        if (info == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WbSunny, null, tint = p.accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Text(if (loading) "Загружаю погоду…" else "Погода появится, когда будет интернет. Нажмите, чтобы обновить",
                    style = MaterialTheme.typography.bodyLarge, color = p.text)
            }
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.city, style = MaterialTheme.typography.titleSmall, color = p.muted,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onCity).padding(vertical = 2.dp))
                Text("${if (info.temp > 0) "+" else ""}${info.temp}°", style = MaterialTheme.typography.displayMedium, color = p.text)
                Text(weatherText(info.code), style = MaterialTheme.typography.titleMedium, color = p.text)
                Text("Ощущается как ${info.feels}°, ветер ${info.wind} м/с", style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
            Icon(skyIcon(info.code, info.isDay), null, tint = p.accent, modifier = Modifier.size(72.dp))
        }
        val today = info.days.firstOrNull()
        if (today != null && today.rain >= 50) {
            Spacer(Modifier.height(8.dp))
            Text("Сегодня вероятен дождь (${today.rain}%), возьмите зонт",
                style = MaterialTheme.typography.bodyMedium, color = p.text)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            info.days.take(7).forEachIndexed { i, d ->
                Column(Modifier.widthIn(min = 58.dp).clip(MaterialTheme.shapes.medium).background(p.card.copy(alpha = 0.7f))
                    .padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i == 0) "Сег" else WD_SHORT[d.date.dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelMedium, color = p.muted)
                    Icon(skyIcon(d.code), null, tint = p.accent, modifier = Modifier.size(24.dp).padding(vertical = 2.dp))
                    Text("${d.max}°", style = MaterialTheme.typography.labelLarge, color = p.text)
                    Text("${d.min}°", style = MaterialTheme.typography.labelMedium, color = p.muted)
                }
            }
        }
    }
}

/** Medicines, tasks and events of one day with tick boxes. */
@Composable
fun DayAgenda(data: AppData, d: LocalDate, nav: Nav, showUndated: Boolean) {
    val p = pal()
    val occ = data.liveEntries.flatMap { it.occurrencesOn(d) }.sortedBy { it.minute ?: 24 * 60 }
    val meds = occ.filter { it.entry.kind == Kind.MEDICINE }
    val rest = occ.filter { it.entry.kind != Kind.MEDICINE }
    val isToday = d == today()

    if (meds.isNotEmpty()) {
        SectionTitle("Лекарства") {
            Text("${meds.count { it.done }} из ${meds.size}", style = MaterialTheme.typography.titleSmall, color = p.muted)
        }
        meds.forEach { OccRow(it, nav, isToday) }
    }

    SectionTitle(if (isToday) "Дела на сегодня" else "Дела и события") {
        if (rest.isNotEmpty())
            Text("${rest.count { it.done }} из ${rest.size}", style = MaterialTheme.typography.titleSmall, color = p.muted)
    }
    if (rest.isEmpty()) EmptyNote(if (isToday) "На сегодня ничего не запланировано. Нажмите «+», чтобы добавить дело." else "На этот день ничего нет")
    rest.forEach { OccRow(it, nav, isToday) }

    if (showUndated) {
        val overdue = data.liveEntries.filter {
            it.kind != Kind.MEDICINE && it.repeat == Repeat.NONE && it.date != null && it.date < d.ep() && !it.isFinished()
        }
        val undated = data.liveEntries.filter { it.date == null && !it.isDone(null, null) }
        if (overdue.isNotEmpty()) {
            SectionTitle("Не сделано раньше")
            overdue.sortedBy { it.date }.forEach { e ->
                val m = e.times.firstOrNull { !e.isDone(e.date, it) }
                OccRow(Occurrence(e, e.date!!, m), nav, false, subtitle = relativeDay(day(e.date)))
            }
        }
        if (undated.isNotEmpty()) {
            SectionTitle("Когда-нибудь")
            undated.forEach { OccRow(Occurrence(it, 0, null).copy(day = -1), nav, false) }
        }
    }
}

@Composable
fun OccRow(o: Occurrence, nav: Nav, isToday: Boolean, subtitle: String? = null) {
    val p = pal()
    val e = o.entry
    val dayKey: Long? = if (o.day < 0) null else o.day
    val done = e.isDone(dayKey, o.minute)
    val color = kindColor(e.kind)
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val late = isToday && !done && o.minute != null && o.minute < nowMin
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(MaterialTheme.shapes.large).background(p.card)
        .clickable { nav.go(Route.EditEntry(e.id, e.kind)) }.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(64.dp)) {
            Text(o.minute?.let { minuteText(it) } ?: "—", style = MaterialTheme.typography.titleLarge,
                color = if (late) p.bad else p.text)
        }
        IconBadge(kindIcon(e.kind), color, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, color = if (done) p.muted else p.text,
                textDecoration = if (done) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = subtitle ?: e.note.takeIf { it.isNotBlank() }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.muted, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        CheckCircle(done, color, onClick = { Store.toggleDone(e.id, occKey(dayKey, o.minute)) })
    }
}
