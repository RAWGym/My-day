package kz.raw.moyden.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val ru = Locale("ru")

val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля",
    "августа", "сентября", "октября", "ноября", "декабря")
val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль",
    "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
val WEEKDAYS = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
val WD_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun money(v: Double, cur: String = Settings.p.currency): String {
    val sym = DecimalFormatSymbols(ru).apply { groupingSeparator = ' ' }
    val f = DecimalFormat(if (abs(v - Math.round(v)) < 0.005) "#,##0" else "#,##0.00", sym)
    return "${f.format(v)} $cur"
}

fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}"

fun fullDate(d: LocalDate): String {
    val base = dayMonth(d)
    return if (d.year != today().year) "$base ${d.year}" else base
}

fun weekdayDate(d: LocalDate) = "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${dayMonth(d)}"

fun relativeDay(d: LocalDate): String = when (ChronoUnit.DAYS.between(today(), d)) {
    0L -> "Сегодня"
    1L -> "Завтра"
    -1L -> "Вчера"
    2L -> "Послезавтра"
    else -> fullDate(d).replaceFirstChar { it.uppercase() }
}

fun plural(n: Long, one: String, few: String, many: String): String {
    val a = abs(n) % 100; val b = a % 10
    return when {
        a in 11..19 -> many
        b == 1L -> one
        b in 2..4 -> few
        else -> many
    }
}

fun inDays(n: Long): String = when (n) {
    0L -> "сегодня"
    1L -> "завтра"
    else -> "через $n ${plural(n, "день", "дня", "дней")}"
}

fun yearsWord(n: Int) = "$n ${plural(n.toLong(), "год", "года", "лет")}"

fun beforeText(min: Int): String = when {
    min <= 0 -> "В момент события"
    min < 60 -> "За $min мин"
    min < 1440 -> "За ${min / 60} ${plural((min / 60).toLong(), "час", "часа", "часов")}"
    else -> "За ${min / 1440} ${plural((min / 1440).toLong(), "день", "дня", "дней")}"
}

fun Entry.scheduleText(): String {
    val t = if (times.isEmpty()) "" else " в " + times.sorted().joinToString(", ") { minuteText(it) }
    val start = date?.let { day(it) }
    val r = when (repeat) {
        Repeat.NONE -> start?.let { relativeDay(it) } ?: "Без даты"
        Repeat.DAILY -> "Каждый день"
        Repeat.WEEKLY -> {
            val ds = if (weekdays.isEmpty()) setOfNotNull(start?.dayOfWeek?.value) else weekdays
            if (ds.size == 7) "Каждый день" else ds.sorted().joinToString(", ") { WD_SHORT[it - 1] }
        }
        Repeat.MONTHLY -> "Каждый месяц, ${start?.dayOfMonth ?: ""} числа"
        Repeat.YEARLY -> "Каждый год, ${start?.let { dayMonth(it) } ?: ""}"
        Repeat.INTERVAL -> "Раз в $interval ${plural(interval.toLong(), "день", "дня", "дней")}"
    }
    val end = if (repeat != Repeat.NONE && endDate != null) ", до ${fullDate(day(endDate))}" else ""
    return r + t + end
}
