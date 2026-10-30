package kz.raw.moyden.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppData(
    val entries: List<Entry> = emptyList(),
    val birthdays: List<Birthday> = emptyList(),
    val money: List<Money> = emptyList(),
    val debts: List<Debt> = emptyList(),
) {
    val liveEntries get() = entries.filter { !it.deleted }
    val liveBirthdays get() = birthdays.filter { !it.deleted }
    val liveMoney get() = money.filter { !it.deleted }
    val liveDebts get() = debts.filter { !it.deleted }

    fun all(): List<Item> = entries + birthdays + money + debts
    fun find(id: String): Item? = all().firstOrNull { it.id == id }

    companion object {
        fun of(items: Collection<Item>) = AppData(
            entries = items.filterIsInstance<Entry>(),
            birthdays = items.filterIsInstance<Birthday>(),
            money = items.filterIsInstance<Money>(),
            debts = items.filterIsInstance<Debt>(),
        )
    }
}

/**
 * Local source of truth. Every change is written to disk at once, the item is marked
 * dirty for cloud sync, and reminders are rescheduled through [onChanged].
 */
object Store {
    private val lock = Any()
    private lateinit var file: File
    private lateinit var dirtyFile: File
    private val dirty = mutableSetOf<String>()
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data
    var onChanged: (() -> Unit)? = null

    @Volatile private var loaded = false

    fun init(ctx: Context) {
        synchronized(lock) {
            if (loaded) return
            file = File(ctx.filesDir, "data.json")
            dirtyFile = File(ctx.filesDir, "dirty.json")
            val items = mutableListOf<Item>()
            runCatching {
                if (file.exists()) {
                    val arr = JSONArray(file.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        itemFromJson(o.optString("_tag"), o)?.let(items::add)
                    }
                }
            }
            runCatching {
                if (dirtyFile.exists()) dirty += JSONArray(dirtyFile.readText()).strings()
            }
            _data.value = AppData.of(items)
            loaded = true
        }
    }

    private fun persist(d: AppData) {
        val arr = JSONArray()
        d.all().forEach { arr.put(it.toJson().put("_tag", it.tag)) }
        val tmp = File(file.parentFile, "data.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
        dirtyFile.writeText(JSONArray(dirty.toList()).toString())
    }

    private fun replaceIn(d: AppData, item: Item): AppData {
        fun <T : Item> List<T>.put(x: T): List<T> {
            val i = indexOfFirst { it.id == x.id }
            return if (i >= 0) toMutableList().also { it[i] = x } else this + x
        }
        return when (item) {
            is Entry -> d.copy(entries = d.entries.put(item))
            is Birthday -> d.copy(birthdays = d.birthdays.put(item))
            is Money -> d.copy(money = d.money.put(item))
            is Debt -> d.copy(debts = d.debts.put(item))
        }
    }

    fun save(item: Item) {
        synchronized(lock) {
            val x = item.touched()
            val d = replaceIn(_data.value, x)
            dirty += x.id
            _data.value = d
            persist(d)
        }
        onChanged?.invoke()
    }

    fun delete(id: String) {
        val it = _data.value.find(id) ?: return
        save(it.asDeleted())
    }

    fun get(id: String): Item? = _data.value.find(id)

    fun toggleDone(entryId: String, key: String, value: Boolean? = null) {
        val e = get(entryId) as? Entry ?: return
        val on = value ?: !e.done.contains(key)
        // keep the set small: forget marks older than 90 days
        val cutoff = today().ep() - 90
        val pruned = e.done.filter { k -> k.substringBefore(":").toLongOrNull()?.let { it >= cutoff } ?: true }.toSet()
        save(e.copy(done = if (on) pruned + key else pruned - key))
    }

    // ---- sync support ----

    fun dirtyItems(): List<Item> = synchronized(lock) { dirty.mapNotNull { _data.value.find(it) } }

    fun markAllDirty() {
        synchronized(lock) {
            dirty += _data.value.all().map { it.id }
            persist(_data.value)
        }
    }

    fun clearDirty(pushed: List<Item>) {
        synchronized(lock) {
            pushed.forEach { p ->
                val cur = _data.value.find(p.id)
                if (cur == null || cur.updatedAt == p.updatedAt) dirty -= p.id
            }
            persist(_data.value)
        }
    }

    /** Merge items from the cloud; newest version wins. Returns true if anything changed. */
    fun mergeRemote(items: List<Item>): Boolean {
        var changed = false
        synchronized(lock) {
            var d = _data.value
            for (r in items) {
                val local = d.find(r.id)
                if (local == null || local.updatedAt < r.updatedAt) {
                    d = replaceIn(d, r); changed = true
                    dirty -= r.id
                }
            }
            if (changed) { _data.value = d; persist(d) }
        }
        if (changed) onChanged?.invoke()
        return changed
    }

    fun exportJson(): String {
        val arr = JSONArray()
        _data.value.all().forEach { arr.put(it.toJson().put("_tag", it.tag)) }
        return JSONObject().put("app", "moy-den").put("items", arr).toString(2)
    }
}
