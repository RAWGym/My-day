package kz.raw.moyden

import kz.raw.moyden.data.Birthday
import kz.raw.moyden.data.Debt
import kz.raw.moyden.data.Entry
import kz.raw.moyden.data.Kind
import kz.raw.moyden.data.Repeat
import kz.raw.moyden.data.ep
import kz.raw.moyden.data.millis
import kz.raw.moyden.data.nextTrigger
import kz.raw.moyden.data.occKey
import kz.raw.moyden.data.occursOn
import kz.raw.moyden.data.isFinished
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {
    private val today = LocalDate.now()
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).millis()

    @Test fun dailyMedicineGoesToNextDoseThenTomorrow() {
        val e = Entry(kind = Kind.MEDICINE, title = "Таблетка", date = today.minusDays(3).ep(),
            times = listOf(8 * 60, 20 * 60), repeat = Repeat.DAILY)
        val noon = at(today, 12)
        val t = e.nextTrigger(noon)!!
        assertEquals(at(today, 20), t.atMillis)
        val late = at(today, 21)
        assertEquals(at(today.plusDays(1), 8), e.nextTrigger(late)!!.atMillis)
    }

    @Test fun doneDoseIsSkipped() {
        val e = Entry(kind = Kind.MEDICINE, title = "x", date = today.ep(), times = listOf(20 * 60), repeat = Repeat.DAILY,
            done = setOf(occKey(today.ep(), 20 * 60)))
        assertEquals(at(today.plusDays(1), 20), e.nextTrigger(at(today, 12))!!.atMillis)
    }

    @Test fun oneOffInPastHasNoTrigger() {
        val e = Entry(title = "x", date = today.minusDays(1).ep(), times = listOf(10 * 60))
        assertNull(e.nextTrigger(System.currentTimeMillis()))
    }

    @Test fun oneOffInFuture() {
        val d = today.plusDays(2)
        val e = Entry(title = "x", date = d.ep(), times = listOf(9 * 60 + 30))
        assertEquals(at(d, 9, 30), e.nextTrigger(System.currentTimeMillis())!!.atMillis)
        assertTrue(e.occursOn(d)); assertFalse(e.occursOn(d.plusDays(1)))
    }

    @Test fun eventRemindsBefore() {
        val d = today.plusDays(1)
        val e = Entry(kind = Kind.EVENT, title = "Врач", date = d.ep(), times = listOf(10 * 60), remindBefore = 60)
        assertEquals(at(d, 9), e.nextTrigger(System.currentTimeMillis())!!.atMillis)
    }

    @Test fun weeklyOnChosenDays() {
        val start = today
        val e = Entry(title = "x", date = start.ep(), times = listOf(9 * 60), repeat = Repeat.WEEKLY, weekdays = setOf(1, 4))
        val t = e.nextTrigger(at(today, 23, 59))!!
        val d = LocalDate.ofEpochDay(t.day)
        assertTrue(d.dayOfWeek.value in setOf(1, 4))
        assertTrue(d.isAfter(today))
    }

    @Test fun courseEnds() {
        val e = Entry(kind = Kind.MEDICINE, title = "x", date = today.minusDays(10).ep(), times = listOf(8 * 60),
            repeat = Repeat.DAILY, endDate = today.minusDays(1).ep())
        assertNull(e.nextTrigger(System.currentTimeMillis()))
        assertTrue(e.isFinished())
    }

    @Test fun intervalEveryThreeDays() {
        val e = Entry(title = "x", date = today.ep(), times = listOf(9 * 60), repeat = Repeat.INTERVAL, interval = 3)
        assertTrue(e.occursOn(today.plusDays(3))); assertFalse(e.occursOn(today.plusDays(2)))
    }

    @Test fun monthlyOn31stUsesLastDay() {
        val e = Entry(title = "x", date = LocalDate.of(2026, 1, 31).ep(), times = listOf(600), repeat = Repeat.MONTHLY)
        assertTrue(e.occursOn(LocalDate.of(2026, 2, 28)))
        assertTrue(e.occursOn(LocalDate.of(2026, 4, 30)))
    }

    @Test fun birthdayNextYearAndLeapDay() {
        val b = Birthday(name = "Ира", day = 29, month = 2, remindDays = setOf(0))
        val t = b.nextTrigger(System.currentTimeMillis())!!
        assertTrue(t.date.monthValue == 2 && t.date.dayOfMonth in 28..29)
        val tomorrow = today.plusDays(1)
        val b2 = Birthday(name = "x", day = tomorrow.dayOfMonth, month = tomorrow.monthValue, remindDays = setOf(1), remindMinute = 23 * 60 + 59)
        assertEquals(at(today, 23, 59), b2.nextTrigger(at(today, 0, 1))!!.atMillis)
    }

    @Test fun debtRemindsDayBeforeAndOnDay() {
        val due = today.plusDays(3)
        val d = Debt(person = "x", amount = 100.0, due = due.ep(), remindMinute = 600)
        assertEquals(at(due.minusDays(1), 10), d.nextTrigger(System.currentTimeMillis()))
        assertNull(d.copy(closed = true).nextTrigger(System.currentTimeMillis()))
    }

    @Test fun undatedTaskHasNoTrigger() {
        assertNull(Entry(title = "x").nextTrigger(System.currentTimeMillis()))
        assertNotNull(LocalDateTime.of(today, LocalTime.NOON))
    }
}
