package kz.raw.moyden.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun today(): LocalDate = LocalDate.now()
fun LocalDate.ep(): Long = toEpochDay()
fun day(ep: Long): LocalDate = LocalDate.ofEpochDay(ep)
fun LocalDateTime.millis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun occKey(dayEp: Long?, minute: Int?): String = "${dayEp ?: "x"}:${minute ?: -1}"

/** Whether a dated entry happens on the given date. */
fun Entry.occursOn(d: LocalDate): Boolean {
    val start = date?.let(::day) ?: return false
    if (d.isBefore(start)) return false
    endDate?.let { if (d.isAfter(day(it))) return false }
    return when (repeat) {
        Repeat.NONE -> d == start
        Repeat.DAILY -> true
        Repeat.WEEKLY -> (if (weekdays.isEmpty()) setOf(start.dayOfWeek.value) else weekdays)
            .contains(d.dayOfWeek.value)
        Repeat.MONTHLY -> {
            val target = minOf(start.dayOfMonth, d.lengthOfMonth())
            d.dayOfMonth == target
        }
        Repeat.YEARLY -> d.monthValue == start.monthValue &&
            d.dayOfMonth == minOf(start.dayOfMonth, d.lengthOfMonth())
        Repeat.INTERVAL -> ChronoUnit.DAYS.between(start, d) % interval.coerceAtLeast(1) == 0L
    }
}

fun Entry.isDone(dayEp: Long?, minute: Int?) = done.contains(occKey(dayEp, minute))

/** One-off entries are finished when every occurrence is done. */
fun Entry.isFinished(): Boolean {
    if (repeat != Repeat.NONE) return endDate?.let { today().ep() > it } ?: false
    return if (times.isEmpty()) isDone(date, null) else times.all { isDone(date, it) }
}

data class Occurrence(val entry: Entry, val day: Long, val minute: Int?) {
    val done get() = entry.isDone(day, minute)
    val key get() = occKey(day, minute)
}

fun Entry.occurrencesOn(d: LocalDate): List<Occurrence> {
    if (!occursOn(d)) return emptyList()
    return if (times.isEmpty()) listOf(Occurrence(this, d.ep(), null))
    else times.sorted().map { Occurrence(this, d.ep(), it) }
}

data class Trigger(val atMillis: Long, val day: Long, val minute: Int)

/** Next reminder moment strictly after [nowMillis], skipping occurrences already marked done. */
fun Entry.nextTrigger(nowMillis: Long): Trigger? {
    if (deleted || !remind || times.isEmpty() || date == null) return null
    val start = day(date)
    val nowDate = LocalDate.now()
    var d = if (start.isAfter(nowDate)) start else nowDate.minusDays(2)
    val limit = nowDate.plusDays(800)
    while (!d.isAfter(limit)) {
        endDate?.let { if (d.isAfter(day(it))) return null }
        if (occursOn(d)) {
            for (m in times.sorted()) {
                if (isDone(d.ep(), m)) continue
                val at = d.atTime(LocalTime.of(m / 60, m % 60)).minusMinutes(remindBefore.toLong()).millis()
                if (at > nowMillis) return Trigger(at, d.ep(), m)
            }
        }
        if (repeat == Repeat.NONE && d.isAfter(start)) return null
        d = d.plusDays(1)
    }
    return null
}

fun Birthday.dateIn(year: Int): LocalDate {
    val m = month.coerceIn(1, 12)
    val len = LocalDate.of(year, m, 1).lengthOfMonth()
    return LocalDate.of(year, m, day.coerceIn(1, len))
}

fun Birthday.nextDate(from: LocalDate = today()): LocalDate {
    val thisYear = dateIn(from.year)
    return if (thisYear.isBefore(from)) dateIn(from.year + 1) else thisYear
}

fun Birthday.ageOn(d: LocalDate): Int? = year?.let { d.year - it }

data class BirthdayTrigger(val atMillis: Long, val daysBefore: Int, val date: LocalDate)

fun Birthday.nextTrigger(nowMillis: Long): BirthdayTrigger? {
    if (deleted || remindDays.isEmpty()) return null
    val base = today()
    val candidates = mutableListOf<BirthdayTrigger>()
    for (y in listOf(base.year - 1, base.year, base.year + 1)) {
        val bd = dateIn(y)
        for (before in remindDays) {
            val at = bd.minusDays(before.toLong())
                .atTime(LocalTime.of(remindMinute / 60, remindMinute % 60)).millis()
            if (at > nowMillis) candidates += BirthdayTrigger(at, before, bd)
        }
    }
    return candidates.minByOrNull { it.atMillis }
}

fun Debt.nextTrigger(nowMillis: Long): Long? {
    if (deleted || closed || !remind || due == null) return null
    val t = LocalTime.of(remindMinute / 60, remindMinute % 60)
    return listOf(day(due).minusDays(1), day(due))
        .map { it.atTime(t).millis() }
        .firstOrNull { it > nowMillis }
}

fun minuteText(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
