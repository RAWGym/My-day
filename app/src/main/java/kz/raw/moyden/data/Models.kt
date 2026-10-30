package kz.raw.moyden.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Anything that is stored locally and synced to the cloud. */
sealed interface Item {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val tag: String
    fun toJson(): JSONObject
    fun touched(): Item
    fun asDeleted(): Item
}

enum class Kind(val title: String) { TASK("Дело"), MEDICINE("Лекарство"), EVENT("Событие") }

enum class Repeat(val title: String) {
    NONE("Один раз"), DAILY("Каждый день"), WEEKLY("По дням недели"),
    MONTHLY("Каждый месяц"), YEARLY("Каждый год"), INTERVAL("Через N дней")
}

/** Task, medicine or event. Times are minutes of day. Dates are epoch days. */
data class Entry(
    override val id: String = newId(),
    val kind: Kind = Kind.TASK,
    val title: String = "",
    val note: String = "",
    val date: Long? = null,
    val times: List<Int> = emptyList(),
    val repeat: Repeat = Repeat.NONE,
    val weekdays: Set<Int> = emptySet(),
    val interval: Int = 2,
    val endDate: Long? = null,
    val remind: Boolean = true,
    val remindBefore: Int = 0,
    val loud: Boolean = false,
    val done: Set<String> = emptySet(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Item {
    override val tag get() = "entry"
    override fun touched() = copy(updatedAt = System.currentTimeMillis())
    override fun asDeleted() = copy(deleted = true, updatedAt = System.currentTimeMillis())

    override fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("kind", kind.name); put("title", title); put("note", note)
        put("date", date ?: JSONObject.NULL)
        put("times", JSONArray(times)); put("repeat", repeat.name)
        put("weekdays", JSONArray(weekdays.toList())); put("interval", interval)
        put("endDate", endDate ?: JSONObject.NULL)
        put("remind", remind); put("remindBefore", remindBefore); put("loud", loud)
        put("done", JSONArray(done.toList()))
        put("updatedAt", updatedAt); put("deleted", deleted)
    }

    companion object {
        fun from(o: JSONObject) = Entry(
            id = o.getString("id"),
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.TASK),
            title = o.optString("title"),
            note = o.optString("note"),
            date = o.optLongOrNull("date"),
            times = o.optJSONArray("times").ints(),
            repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.NONE),
            weekdays = o.optJSONArray("weekdays").ints().toSet(),
            interval = o.optInt("interval", 2).coerceAtLeast(1),
            endDate = o.optLongOrNull("endDate"),
            remind = o.optBoolean("remind", true),
            remindBefore = o.optInt("remindBefore", 0),
            loud = o.optBoolean("loud", false),
            done = o.optJSONArray("done").strings().toSet(),
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted", false),
        )
    }
}

data class Birthday(
    override val id: String = newId(),
    val name: String = "",
    val day: Int = 1,
    val month: Int = 1,
    val year: Int? = null,
    val remindDays: Set<Int> = setOf(0, 1),
    val remindMinute: Int = 9 * 60,
    val note: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Item {
    override val tag get() = "birthday"
    override fun touched() = copy(updatedAt = System.currentTimeMillis())
    override fun asDeleted() = copy(deleted = true, updatedAt = System.currentTimeMillis())
    override fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("day", day); put("month", month)
        put("year", year ?: JSONObject.NULL)
        put("remindDays", JSONArray(remindDays.toList())); put("remindMinute", remindMinute)
        put("note", note); put("updatedAt", updatedAt); put("deleted", deleted)
    }

    companion object {
        fun from(o: JSONObject) = Birthday(
            id = o.getString("id"), name = o.optString("name"),
            day = o.optInt("day", 1), month = o.optInt("month", 1),
            year = if (o.isNull("year") || !o.has("year")) null else o.optInt("year"),
            remindDays = o.optJSONArray("remindDays").ints().toSet(),
            remindMinute = o.optInt("remindMinute", 9 * 60),
            note = o.optString("note"), updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted", false),
        )
    }
}

data class Money(
    override val id: String = newId(),
    val amount: Double = 0.0,
    val income: Boolean = false,
    val category: String = "",
    val date: Long = 0,
    val note: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Item {
    override val tag get() = "money"
    override fun touched() = copy(updatedAt = System.currentTimeMillis())
    override fun asDeleted() = copy(deleted = true, updatedAt = System.currentTimeMillis())
    override fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("amount", amount); put("income", income); put("category", category)
        put("date", date); put("note", note); put("updatedAt", updatedAt); put("deleted", deleted)
    }

    companion object {
        fun from(o: JSONObject) = Money(
            id = o.getString("id"), amount = o.optDouble("amount", 0.0),
            income = o.optBoolean("income"), category = o.optString("category"),
            date = o.optLong("date"), note = o.optString("note"),
            updatedAt = o.optLong("updatedAt"), deleted = o.optBoolean("deleted", false),
        )
    }
}

data class Payment(val amount: Double, val date: Long)

data class Debt(
    override val id: String = newId(),
    val person: String = "",
    val amount: Double = 0.0,
    val theyOweMe: Boolean = true,
    val date: Long = 0,
    val due: Long? = null,
    val remind: Boolean = true,
    val remindMinute: Int = 10 * 60,
    val payments: List<Payment> = emptyList(),
    val closed: Boolean = false,
    val note: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
) : Item {
    val paid get() = payments.sumOf { it.amount }
    val left get() = (amount - paid).coerceAtLeast(0.0)

    override val tag get() = "debt"
    override fun touched() = copy(updatedAt = System.currentTimeMillis())
    override fun asDeleted() = copy(deleted = true, updatedAt = System.currentTimeMillis())
    override fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("person", person); put("amount", amount); put("theyOweMe", theyOweMe)
        put("date", date); put("due", due ?: JSONObject.NULL); put("remind", remind)
        put("remindMinute", remindMinute)
        put("payments", JSONArray(payments.map { JSONObject().put("amount", it.amount).put("date", it.date) }))
        put("closed", closed); put("note", note); put("updatedAt", updatedAt); put("deleted", deleted)
    }

    companion object {
        fun from(o: JSONObject): Debt {
            val pa = o.optJSONArray("payments") ?: JSONArray()
            return Debt(
                id = o.getString("id"), person = o.optString("person"),
                amount = o.optDouble("amount", 0.0), theyOweMe = o.optBoolean("theyOweMe", true),
                date = o.optLong("date"), due = o.optLongOrNull("due"),
                remind = o.optBoolean("remind", true), remindMinute = o.optInt("remindMinute", 600),
                payments = (0 until pa.length()).map {
                    val p = pa.getJSONObject(it); Payment(p.optDouble("amount"), p.optLong("date"))
                },
                closed = o.optBoolean("closed"), note = o.optString("note"),
                updatedAt = o.optLong("updatedAt"), deleted = o.optBoolean("deleted", false),
            )
        }
    }
}

fun itemFromJson(tag: String, o: JSONObject): Item? = runCatching {
    when (tag) {
        "entry" -> Entry.from(o)
        "birthday" -> Birthday.from(o)
        "money" -> Money.from(o)
        "debt" -> Debt.from(o)
        else -> null
    }
}.getOrNull()

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

internal fun JSONArray?.ints(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { optInt(it) }

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

val EXPENSE_CATEGORIES = listOf(
    "Продукты", "Аптека", "Коммуналка", "Транспорт", "Связь", "Дом",
    "Одежда", "Подарки", "Здоровье", "Внуки", "Другое"
)
val INCOME_CATEGORIES = listOf("Пенсия", "Зарплата", "Подарок", "Подработка", "Другое")
