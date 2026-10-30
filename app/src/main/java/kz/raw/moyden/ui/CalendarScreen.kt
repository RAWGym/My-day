package kz.raw.moyden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.MONTHS
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.WD_SHORT
import kz.raw.moyden.data.ageOn
import kz.raw.moyden.data.dateIn
import kz.raw.moyden.data.ep
import kz.raw.moyden.data.occursOn
import kz.raw.moyden.data.today
import kz.raw.moyden.data.weekdayDate
import kz.raw.moyden.data.yearsWord
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(nav: Nav) {
    val p = pal()
    val data by Store.data.collectAsStateWithLifecycle()
    var monthEp by rememberSaveable { mutableStateOf(today().withDayOfMonth(1).ep()) }
    var selEp by rememberSaveable { mutableStateOf(today().ep()) }
    val month = YearMonth.from(LocalDate.ofEpochDay(monthEp))
    val sel = LocalDate.ofEpochDay(selEp)

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = Pad)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${MONTHS[month.monthValue - 1]} ${month.year}", style = MaterialTheme.typography.headlineMedium,
                color = p.text, modifier = Modifier.weight(1f))
            IconButton({ monthEp = month.minusMonths(1).atDay(1).ep() }, Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Назад", tint = p.text, modifier = Modifier.size(32.dp))
            }
            IconButton({ monthEp = month.plusMonths(1).atDay(1).ep() }, Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Вперёд", tint = p.text, modifier = Modifier.size(32.dp))
            }
        }
        if (month != YearMonth.from(today()) || sel != today()) {
            Text("Вернуться к сегодня", style = MaterialTheme.typography.labelLarge, color = p.accent,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable {
                    monthEp = today().withDayOfMonth(1).ep(); selEp = today().ep()
                }.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(10.dp))

        Card(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                WD_SHORT.forEachIndexed { i, w ->
                    Text(w, Modifier.weight(1f).padding(vertical = 6.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge, color = if (i >= 5) p.bad else p.muted)
                }
            }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp)) {
                            if (idx in 1..month.lengthOfMonth()) {
                                val d = month.atDay(idx)
                                val dots = buildList {
                                    val es = data.liveEntries.filter { it.occursOn(d) }
                                    if (es.any { it.kind == Kind.MEDICINE }) add(p.med)
                                    if (es.any { it.kind == Kind.TASK }) add(p.accent)
                                    if (es.any { it.kind == Kind.EVENT }) add(p.event)
                                    if (data.liveBirthdays.any { it.dateIn(d.year) == d }) add(p.bad)
                                    if (data.liveDebts.any { !it.closed && it.due == d.ep() }) add(p.good)
                                }
                                DayCell(d, d == sel, d == today(), dots) { selEp = d.ep() }
                            }
                        }
                    }
                }
            }
        }

        SectionTitle(weekdayDate(sel).replaceFirstChar { it.uppercase() }) {
            IconButton({ nav.go(Route.EditEntry(null, Kind.TASK, selEp)) }) {
                Icon(Icons.Outlined.Add, "Добавить на этот день", tint = p.accent, modifier = Modifier.size(30.dp))
            }
        }
        data.liveBirthdays.filter { it.dateIn(sel.year) == sel }.forEach { b ->
            Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditBirthday(b.id)) }, color = p.bad.copy(alpha = 0.1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(BirthdayIcon, p.bad)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("День рождения: ${b.name}", style = MaterialTheme.typography.titleMedium, color = p.text)
                        b.ageOn(sel)?.let { Text("Исполнится ${yearsWord(it)}", style = MaterialTheme.typography.bodyMedium, color = p.muted) }
                    }
                }
            }
        }
        data.liveDebts.filter { !it.closed && it.due == sel.ep() }.forEach { x ->
            Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditDebt(x.id)) }) {
                Text("Срок долга: ${x.person}", style = MaterialTheme.typography.titleMedium, color = p.text)
            }
        }
        DayAgenda(data, sel, nav, showUndated = false)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DayCell(d: LocalDate, selected: Boolean, isToday: Boolean, dots: List<Color>, onClick: () -> Unit) {
    val p = pal()
    Column(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
        .background(if (selected) p.accent else Color.Transparent)
        .then(if (isToday && !selected) Modifier.border(2.dp, p.accent, MaterialTheme.shapes.small) else Modifier)
        .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium,
            color = if (selected) p.onAccent else p.text)
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.take(4).forEach { c ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (selected) p.onAccent else c))
            }
        }
    }
}
