package kz.raw.moyden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.raw.moyden.data.Birthday
import kz.raw.moyden.data.Debt
import kz.raw.moyden.data.EXPENSE_CATEGORIES
import kz.raw.moyden.data.Entry
import kz.raw.moyden.data.INCOME_CATEGORIES
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.MONTHS_GEN
import kz.raw.moyden.data.Money
import kz.raw.moyden.data.Payment
import kz.raw.moyden.data.Repeat
import kz.raw.moyden.data.Settings
import kz.raw.moyden.data.Store
import kz.raw.moyden.data.WD_SHORT
import kz.raw.moyden.data.beforeText
import kz.raw.moyden.data.day
import kz.raw.moyden.data.ep
import kz.raw.moyden.data.fullDate
import kz.raw.moyden.data.minuteText
import kz.raw.moyden.data.money
import kz.raw.moyden.data.today
import java.time.LocalDate
import java.time.LocalTime

private fun parseAmount(s: String): Double? = s.replace(" ", "").replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
private fun amountText(v: Double): String = if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
private fun DeleteAction(what: String, onDelete: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    IconButton({ ask = true }, Modifier.size(52.dp)) {
        Icon(Icons.Outlined.DeleteOutline, "Удалить", tint = pal().bad, modifier = Modifier.size(28.dp))
    }
    if (ask) Confirm("Удалить $what?", "Это действие нельзя отменить.", "Удалить", onDelete) { ask = false }
}

// ---------------- Task / medicine / event ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditor(nav: Nav, r: Route.EditEntry) {
    val p = pal()
    val existing = r.id?.let { Store.get(it) as? Entry }
    var e by remember {
        mutableStateOf(existing ?: run {
            val d = r.date ?: today().ep()
            val nextHour = ((LocalTime.now().hour + 1) % 24) * 60
            when (r.kind) {
                Kind.TASK -> Entry(kind = Kind.TASK, date = d)
                Kind.MEDICINE -> Entry(kind = Kind.MEDICINE, date = d, times = listOf(8 * 60), repeat = Repeat.DAILY, loud = true)
                Kind.EVENT -> Entry(kind = Kind.EVENT, date = d, times = listOf(nextHour), remindBefore = 60)
            }
        })
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var editTime by remember { mutableStateOf<Int?>(null) } // index, -1 = new
    val isMed = e.kind == Kind.MEDICINE

    val title = when {
        existing != null -> e.kind.title
        isMed -> "Новое лекарство"
        e.kind == Kind.EVENT -> "Новое событие"
        else -> "Новое дело"
    }

    Screen(title, onBack = { nav.back() },
        actions = { if (existing != null) DeleteAction(e.kind.title.lowercase()) { Store.delete(e.id); nav.back() } },
        bottom = {
            BigButton("Сохранить", enabled = e.title.isNotBlank(), onClick = {
                var x = e.copy(title = e.title.trim(), note = e.note.trim())
                if (x.repeat != Repeat.NONE && x.date == null) x = x.copy(date = today().ep())
                if (x.date == null) x = x.copy(times = emptyList(), repeat = Repeat.NONE)
                Store.save(x); nav.back()
            })
        }) {
        if (existing == null) {
            Spacer(Modifier.height(4.dp))
            PillGroup(Kind.entries.map { it to it.title }, { it == e.kind }) { k ->
                e = when (k) {
                    Kind.MEDICINE -> e.copy(kind = k, loud = true, repeat = if (e.repeat == Repeat.NONE) Repeat.DAILY else e.repeat,
                        times = e.times.ifEmpty { listOf(8 * 60) }, date = e.date ?: today().ep(), remindBefore = 0)
                    Kind.EVENT -> e.copy(kind = k, loud = false, remindBefore = 60, date = e.date ?: today().ep(),
                        times = e.times.ifEmpty { listOf(((LocalTime.now().hour + 1) % 24) * 60) })
                    Kind.TASK -> e.copy(kind = k, loud = false, remindBefore = 0)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Field(e.title, { e = e.copy(title = it) }, when (e.kind) {
            Kind.MEDICINE -> "Название лекарства"; Kind.EVENT -> "Что за событие"; Kind.TASK -> "Что нужно сделать"
        })
        Spacer(Modifier.height(10.dp))
        Field(e.note, { e = e.copy(note = it) },
            if (isMed) "Дозировка, например: 1 таблетка после еды" else "Заметка (необязательно)",
            singleLine = false, minLines = 2)

        Label("Когда")
        PickerRow(Icons.Outlined.CalendarToday, if (e.repeat == Repeat.NONE) "Дата" else "Начиная с",
            e.date?.let { fullDate(day(it)) } ?: "Без даты", onClick = { pickDate = true },
            onClear = if (e.kind == Kind.TASK && e.date != null && e.repeat == Repeat.NONE) ({ e = e.copy(date = null, times = emptyList()) }) else null)

        if (e.date != null) {
            Label(if (isMed) "Время приёма" else "Время")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.times.sorted().forEachIndexed { i, m ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(p.card).clickable { editTime = e.times.indexOf(m) }
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(minuteText(m), style = MaterialTheme.typography.titleLarge, color = p.text)
                        IconButton({ e = e.copy(times = e.times - m) }, Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, "Убрать", tint = p.muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (isMed || e.times.isEmpty()) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.15f)).clickable { editTime = -1 }
                        .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Add, null, tint = p.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (e.times.isEmpty()) "Указать время" else "Ещё время", style = MaterialTheme.typography.titleSmall, color = p.accent)
                    }
                }
            }
            if (e.times.isEmpty()) Text("Без времени напоминание не придёт, дело просто будет в списке на день",
                style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 6.dp))

            Label("Повтор")
            PillGroup(Repeat.entries.map { it to it.title }, { it == e.repeat }) { e = e.copy(repeat = it) }
            if (e.repeat == Repeat.WEEKLY) {
                Spacer(Modifier.height(12.dp))
                val sel = e.weekdays.ifEmpty { setOf(day(e.date!!).dayOfWeek.value) }
                PillGroup((1..7).map { it to WD_SHORT[it - 1] }, { it in sel }) { d ->
                    val n = if (d in sel) sel - d else sel + d
                    if (n.isNotEmpty()) e = e.copy(weekdays = n)
                }
            }
            if (e.repeat == Repeat.INTERVAL) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Каждые", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.width(12.dp))
                    SoftButton("−", { e = e.copy(interval = (e.interval - 1).coerceAtLeast(2)) })
                    Text("${e.interval}", style = MaterialTheme.typography.headlineSmall, color = p.text,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    SoftButton("+", { e = e.copy(interval = (e.interval + 1).coerceAtMost(60)) })
                    Spacer(Modifier.width(12.dp))
                    Text("дня", style = MaterialTheme.typography.titleMedium, color = p.text)
                }
            }
            if (e.repeat != Repeat.NONE) {
                Spacer(Modifier.height(12.dp))
                PickerRow(Icons.Outlined.EventBusy, if (isMed) "Курс до" else "Повторять до",
                    e.endDate?.let { fullDate(day(it)) } ?: "Без окончания", onClick = { pickEnd = true },
                    onClear = if (e.endDate != null) ({ e = e.copy(endDate = null) }) else null)
            }

            if (e.times.isNotEmpty()) {
                Label("Напоминание")
                Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchRow("Напомнить", null, e.remind, { e = e.copy(remind = it) })
                    if (e.remind) {
                        SwitchRow("Громкий сигнал, как будильник",
                            "Звучит, пока не нажмёте «${if (isMed) "Принял" else "Сделано"}»", e.loud, { e = e.copy(loud = it) })
                    }
                }
                if (e.remind && e.kind == Kind.EVENT) {
                    Spacer(Modifier.height(12.dp))
                    PillGroup(listOf(0, 15, 30, 60, 120, 1440).map { it to beforeText(it) }, { it == e.remindBefore }) {
                        e = e.copy(remindBefore = it)
                    }
                }
            }
        }
    }

    if (pickDate) DatePick(e.date?.let(::day), { e = e.copy(date = it.ep()) }) { pickDate = false }
    if (pickEnd) DatePick(e.endDate?.let(::day) ?: today().plusDays(14), { e = e.copy(endDate = it.ep()) }) { pickEnd = false }
    editTime?.let { idx ->
        val init = if (idx >= 0) e.times[idx] else (e.times.maxOrNull()?.plus(4 * 60)?.takeIf { it < 24 * 60 } ?: (LocalTime.now().hour + 1) % 24 * 60)
        TimePick(init, { m ->
            val list = e.times.toMutableList()
            if (idx >= 0) list[idx] = m else list += m
            e = e.copy(times = list.distinct())
        }) { editTime = null }
    }
}

// ---------------- Birthday ----------------

@Composable
fun BirthdayEditor(nav: Nav, id: String?) {
    val p = pal()
    val existing = id?.let { Store.get(it) as? Birthday }
    var b by remember { mutableStateOf(existing ?: Birthday(day = today().dayOfMonth, month = today().monthValue)) }
    var pick by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Screen(if (existing != null) "День рождения" else "Новый день рождения", onBack = { nav.back() },
        actions = { if (existing != null) DeleteAction("день рождения") { Store.delete(b.id); nav.back() } },
        bottom = { BigButton("Сохранить", enabled = b.name.isNotBlank(), onClick = { Store.save(b.copy(name = b.name.trim())); nav.back() }) }) {
        Spacer(Modifier.height(8.dp))
        Field(b.name, { b = b.copy(name = it) }, "Кого поздравить")
        Label("Дата рождения")
        PickerRow(Icons.Outlined.CalendarToday, "Дата",
            "${b.day} ${MONTHS_GEN[b.month - 1]}" + (b.year?.let { " $it" } ?: ""), onClick = { pick = true })
        Spacer(Modifier.height(4.dp))
        SwitchRow("Год известен", "Тогда будет видно, сколько исполнится", b.year != null, {
            b = b.copy(year = if (it) (b.year ?: (today().year - 30)) else null)
        })
        Label("Когда напомнить")
        PillGroup(listOf(0 to "В тот же день", 1 to "За день", 3 to "За 3 дня", 7 to "За неделю"), { it in b.remindDays }) {
            b = b.copy(remindDays = if (it in b.remindDays) b.remindDays - it else b.remindDays + it)
        }
        Spacer(Modifier.height(12.dp))
        PickerRow(Icons.Outlined.Schedule, "Во сколько напомнить", minuteText(b.remindMinute), onClick = { pickTime = true })
        Spacer(Modifier.height(14.dp))
        Field(b.note, { b = b.copy(note = it) }, "Идеи подарков, заметка", singleLine = false, minLines = 2)
        Spacer(Modifier.height(8.dp))
        Text("Напоминание повторяется каждый год", style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
    if (pick) DatePick(run {
        val y = b.year ?: today().year
        val first = LocalDate.of(y, b.month, 1)
        first.withDayOfMonth(b.day.coerceIn(1, first.lengthOfMonth()))
    }, { d ->
        b = b.copy(day = d.dayOfMonth, month = d.monthValue, year = if (b.year != null) d.year else null)
    }) { pick = false }
    if (pickTime) TimePick(b.remindMinute, { b = b.copy(remindMinute = it) }) { pickTime = false }
}

// ---------------- Money ----------------

@Composable
fun MoneyEditor(nav: Nav, id: String?, incomeDefault: Boolean) {
    val p = pal()
    val existing = id?.let { Store.get(it) as? Money }
    var m by remember { mutableStateOf(existing ?: Money(income = incomeDefault, date = today().ep())) }
    var amount by remember { mutableStateOf(amountText(m.amount)) }
    var pick by remember { mutableStateOf(false) }
    val cats = if (m.income) INCOME_CATEGORIES else EXPENSE_CATEGORIES

    Screen(if (m.income) "Доход" else "Расход", onBack = { nav.back() },
        actions = { if (existing != null) DeleteAction("запись") { Store.delete(m.id); nav.back() } },
        bottom = {
            BigButton("Сохранить", enabled = parseAmount(amount) != null, onClick = {
                Store.save(m.copy(amount = parseAmount(amount)!!, note = m.note.trim(),
                    category = m.category.ifBlank { "Другое" })); nav.back()
            })
        }) {
        Spacer(Modifier.height(4.dp))
        PillGroup(listOf(false to "Расход", true to "Доход"), { it == m.income }) {
            m = m.copy(income = it, category = "")
        }
        Spacer(Modifier.height(14.dp))
        Field(amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == ' ' } },
            "Сумма, ${Settings.p.currency}", number = true)
        Label("Категория")
        PillGroup(cats.map { it to it }, { it == m.category }) { m = m.copy(category = it) }
        Label("Дата")
        PickerRow(Icons.Outlined.CalendarToday, "Дата", fullDate(day(m.date)), onClick = { pick = true })
        Spacer(Modifier.height(14.dp))
        Field(m.note, { m = m.copy(note = it) }, "Комментарий (необязательно)")
    }
    if (pick) DatePick(day(m.date), { m = m.copy(date = it.ep()) }) { pick = false }
}

// ---------------- Debt ----------------

@Composable
fun DebtEditor(nav: Nav, id: String?) {
    val p = pal()
    val existing = id?.let { Store.get(it) as? Debt }
    var x by remember { mutableStateOf(existing ?: Debt(date = today().ep())) }
    var amount by remember { mutableStateOf(amountText(x.amount)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var payDialog by remember { mutableStateOf(false) }

    fun current() = x.copy(amount = parseAmount(amount) ?: x.amount, person = x.person.trim(), note = x.note.trim())

    Screen(if (existing != null) "Долг" else "Новый долг", onBack = { nav.back() },
        actions = { if (existing != null) DeleteAction("долг") { Store.delete(x.id); nav.back() } },
        bottom = {
            BigButton("Сохранить", enabled = x.person.isNotBlank() && parseAmount(amount) != null,
                onClick = { Store.save(current()); nav.back() })
        }) {
        Spacer(Modifier.height(4.dp))
        PillGroup(listOf(true to "Мне должны", false to "Я должен"), { it == x.theyOweMe }) { x = x.copy(theyOweMe = it) }
        Spacer(Modifier.height(14.dp))
        Field(x.person, { x = x.copy(person = it) }, if (x.theyOweMe) "Кто должен" else "Кому должен")
        Spacer(Modifier.height(10.dp))
        Field(amount, { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == ' ' } },
            "Сумма, ${Settings.p.currency}", number = true)
        Label("Даты")
        PickerRow(Icons.Outlined.CalendarToday, "Когда взяли", fullDate(day(x.date)), onClick = { pickDate = true })
        Spacer(Modifier.height(10.dp))
        PickerRow(Icons.Outlined.EventBusy, "Вернуть до", x.due?.let { fullDate(day(it)) } ?: "Без срока",
            onClick = { pickDue = true }, onClear = if (x.due != null) ({ x = x.copy(due = null) }) else null)
        if (x.due != null) {
            Spacer(Modifier.height(4.dp))
            Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                SwitchRow("Напомнить о сроке", "За день и в сам день", x.remind, { x = x.copy(remind = it) })
                if (x.remind) SettingRow(Icons.Outlined.Schedule, "Время напоминания", minuteText(x.remindMinute), { pickTime = true })
            }
        }
        Spacer(Modifier.height(14.dp))
        Field(x.note, { x = x.copy(note = it) }, "Заметка (необязательно)", singleLine = false, minLines = 2)

        if (existing != null) {
            SectionTitle("Возвраты")
            val cur = current()
            Card {
                Text("Осталось вернуть: ${money(cur.left)}", style = MaterialTheme.typography.titleMedium, color = p.text)
                if (cur.payments.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    cur.payments.forEach { pay ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(fullDate(day(pay.date)), style = MaterialTheme.typography.bodyLarge, color = p.muted, modifier = Modifier.weight(1f))
                            Text(money(pay.amount), style = MaterialTheme.typography.bodyLarge, color = p.text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!x.closed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Частичный возврат", { payDialog = true }, Modifier.weight(1f), Icons.Outlined.Payments)
                    SoftButton("Вернули всё", {
                        val c = current()
                        val rest = c.left
                        x = c.copy(closed = true, payments = if (rest > 0) c.payments + Payment(rest, today().ep()) else c.payments)
                        Store.save(x); nav.back()
                    }, Modifier.weight(1f), color = p.good)
                }
            } else {
                SoftButton("Открыть снова", { x = x.copy(closed = false) }, Modifier.fillMaxWidth())
            }
        }
    }
    if (pickDate) DatePick(day(x.date), { x = x.copy(date = it.ep()) }) { pickDate = false }
    if (pickDue) DatePick(x.due?.let(::day) ?: today().plusDays(7), { x = x.copy(due = it.ep()) }) { pickDue = false }
    if (pickTime) TimePick(x.remindMinute, { x = x.copy(remindMinute = it) }) { pickTime = false }
    if (payDialog) {
        var v by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { payDialog = false },
            title = { Text("Сколько вернули?") },
            text = { Field(v, { v = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, "Сумма", number = true) },
            confirmButton = {
                TextButton({
                    parseAmount(v)?.let { a ->
                        val c = current()
                        val n = c.copy(payments = c.payments + Payment(a, today().ep()))
                        x = if (n.left <= 0.0) n.copy(closed = true) else n
                        Store.save(x)
                    }
                    payDialog = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton({ payDialog = false }) { Text("Отмена") } },
            containerColor = p.card,
        )
    }
}
