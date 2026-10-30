package kz.raw.moyden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Remove
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.raw.moyden.data.Debt
import kz.raw.moyden.data.MONTHS
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.day
import kz.raw.moyden.data.ep
import kz.raw.moyden.data.fullDate
import kz.raw.moyden.data.money
import kz.raw.moyden.data.relativeDay
import kz.raw.moyden.data.today
import java.time.YearMonth

@Composable
fun MoneyScreen(nav: Nav) {
    val p = pal()
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = Pad)) {
        Spacer(Modifier.height(18.dp))
        Text("Деньги", style = MaterialTheme.typography.headlineLarge, color = p.text)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(p.cardAlt).padding(4.dp)) {
            listOf("Доходы и расходы", "Долги").forEachIndexed { i, t ->
                Box(Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                    .background(if (tab == i) p.card else p.cardAlt)
                    .clickable { tab = i }
                    .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(t, style = MaterialTheme.typography.titleSmall, color = if (tab == i) p.text else p.muted)
                }
            }
        }
        if (tab == 0) FinanceTab(nav) else DebtsTab(nav)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun FinanceTab(nav: Nav) {
    val p = pal()
    val data by Store.data.collectAsStateWithLifecycle()
    var monthEp by rememberSaveable { mutableStateOf(today().withDayOfMonth(1).ep()) }
    val ym = YearMonth.from(day(monthEp))
    val list = data.liveMoney.filter { YearMonth.from(day(it.date)) == ym }.sortedByDescending { it.date }
    val inc = list.filter { it.income }.sumOf { it.amount }
    val exp = list.filter { !it.income }.sumOf { it.amount }

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ monthEp = ym.minusMonths(1).atDay(1).ep() }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Назад", tint = p.text)
        }
        Text("${MONTHS[ym.monthValue - 1]} ${ym.year}", style = MaterialTheme.typography.titleLarge, color = p.text,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton({ monthEp = ym.plusMonths(1).atDay(1).ep() }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Вперёд", tint = p.text)
        }
    }
    Spacer(Modifier.height(8.dp))
    Card {
        Text("Осталось за месяц", style = MaterialTheme.typography.titleSmall, color = p.muted)
        Text(money(inc - exp), style = MaterialTheme.typography.headlineLarge, color = if (inc - exp < 0) p.bad else p.text)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Доходы", style = MaterialTheme.typography.labelLarge, color = p.muted)
                Text(money(inc), style = MaterialTheme.typography.titleLarge, color = p.good)
            }
            Column(Modifier.weight(1f)) {
                Text("Расходы", style = MaterialTheme.typography.labelLarge, color = p.muted)
                Text(money(exp), style = MaterialTheme.typography.titleLarge, color = p.bad)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftButton("Расход", { nav.go(Route.EditMoney(null, false)) }, Modifier.weight(1f), Icons.Outlined.Remove, p.bad)
        SoftButton("Доход", { nav.go(Route.EditMoney(null, true)) }, Modifier.weight(1f), Icons.Outlined.Add, p.good)
    }

    val byCat = list.filter { !it.income }.groupBy { it.category.ifBlank { "Другое" } }
        .mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    if (byCat.isNotEmpty()) {
        SectionTitle("На что потрачено")
        Card {
            byCat.forEachIndexed { i, (cat, sum) ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row {
                    Text(cat, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
                    Text(money(sum), style = MaterialTheme.typography.titleMedium, color = p.text)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small).background(p.cardAlt)) {
                    Box(Modifier.fillMaxWidth((sum / exp).toFloat().coerceIn(0.02f, 1f)).height(8.dp)
                        .clip(MaterialTheme.shapes.small).background(p.accent))
                }
            }
        }
    }

    SectionTitle("Записи")
    if (list.isEmpty()) EmptyNote("В этом месяце записей пока нет")
    list.groupBy { it.date }.forEach { (d, items) ->
        Text(relativeDay(day(d)), style = MaterialTheme.typography.titleSmall, color = p.muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        items.forEach { m ->
            Card(Modifier.padding(bottom = 8.dp), onClick = { nav.go(Route.EditMoney(m.id, m.income)) },
                padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (m.income) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        if (m.income) p.good else p.bad, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.category.ifBlank { if (m.income) "Доход" else "Расход" },
                            style = MaterialTheme.typography.titleMedium, color = p.text)
                        if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodyMedium, color = p.muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text((if (m.income) "+" else "−") + money(m.amount), style = MaterialTheme.typography.titleMedium,
                        color = if (m.income) p.good else p.text)
                }
            }
        }
    }
}

@Composable
private fun DebtsTab(nav: Nav) {
    val p = pal()
    val data by Store.data.collectAsStateWithLifecycle()
    val active = data.liveDebts.filter { !it.closed }
    val closed = data.liveDebts.filter { it.closed }.sortedByDescending { it.updatedAt }
    val owedToMe = active.filter { it.theyOweMe }.sumOf { it.left }
    val iOwe = active.filter { !it.theyOweMe }.sumOf { it.left }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.weight(1f)) {
            Text("Мне должны", style = MaterialTheme.typography.labelLarge, color = p.muted)
            Text(money(owedToMe), style = MaterialTheme.typography.titleLarge, color = p.good)
        }
        Card(Modifier.weight(1f)) {
            Text("Я должен", style = MaterialTheme.typography.labelLarge, color = p.muted)
            Text(money(iOwe), style = MaterialTheme.typography.titleLarge, color = p.bad)
        }
    }
    Spacer(Modifier.height(12.dp))
    SoftButton("Записать долг", { nav.go(Route.EditDebt(null)) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)

    SectionTitle("Активные")
    if (active.isEmpty()) EmptyNote("Долгов нет")
    active.sortedWith(compareBy({ it.due ?: Long.MAX_VALUE }, { it.person })).forEach { DebtRow(it, nav) }
    if (closed.isNotEmpty()) {
        SectionTitle("Закрытые")
        closed.forEach { DebtRow(it, nav) }
    }
}

@Composable
private fun DebtRow(x: Debt, nav: Nav) {
    val p = pal()
    Card(Modifier.padding(bottom = 10.dp), onClick = { nav.go(Route.EditDebt(x.id)) },
        color = if (x.closed) p.card.copy(alpha = 0.6f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Handshake, if (x.theyOweMe) p.good else p.bad)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(x.person, style = MaterialTheme.typography.titleMedium, color = p.text)
                val sub = buildList {
                    add(if (x.theyOweMe) "Должен мне" else "Я должен")
                    if (x.paid > 0 && !x.closed) add("вернули ${money(x.paid)} из ${money(x.amount)}")
                    if (x.closed) add("закрыт")
                    else x.due?.let { add("до ${fullDate(day(it))}") }
                }.joinToString(", ")
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
            Text(money(if (x.closed) x.amount else x.left), style = MaterialTheme.typography.titleMedium,
                color = if (x.closed) p.muted else if (x.theyOweMe) p.good else p.bad)
        }
    }
}

