package com.example.autocall

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class AlarmItem(
    val id: Int,
    val name: String,
    val number: String,
    val hour: Int,          // 0..23
    val minute: Int,
    val repeat: Int,        // ONCE / DAILY / CUSTOM
    val days: Set<Int>,     // Calendar.SUNDAY..SATURDAY (للتكرار المخصص)
    val autoCall: Boolean,  // true = اتصال تلقائي، false = تنبيه فقط
    val enabled: Boolean
) {
    companion object {
        const val ONCE = 0
        const val DAILY = 1
        const val CUSTOM = 2

        fun fromJson(o: JSONObject): AlarmItem {
            val d = mutableSetOf<Int>()
            val arr = o.optJSONArray("days")
            if (arr != null) for (i in 0 until arr.length()) d.add(arr.getInt(i))
            return AlarmItem(
                id = o.getInt("id"),
                name = o.optString("name"),
                number = o.optString("number"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                repeat = o.optInt("repeat", DAILY),
                days = d,
                autoCall = o.optBoolean("autoCall", true),
                enabled = o.optBoolean("enabled", true)
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("number", number)
        put("hour", hour)
        put("minute", minute)
        put("repeat", repeat)
        put("days", JSONArray(days.toList()))
        put("autoCall", autoCall)
        put("enabled", enabled)
    }
}

/** ترتيب الأسبوع كما في العراق: من السبت إلى الجمعة */
val DAY_ORDER: List<Int> = listOf(
    Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
    Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
)

private val DAY_NAMES: Map<Int, String> = mapOf(
    Calendar.SATURDAY to "السبت",
    Calendar.SUNDAY to "الأحد",
    Calendar.MONDAY to "الاثنين",
    Calendar.TUESDAY to "الثلاثاء",
    Calendar.WEDNESDAY to "الأربعاء",
    Calendar.THURSDAY to "الخميس",
    Calendar.FRIDAY to "الجمعة"
)

private val DAY_SHORT: Map<Int, String> = mapOf(
    Calendar.SATURDAY to "سبت",
    Calendar.SUNDAY to "أحد",
    Calendar.MONDAY to "اثن",
    Calendar.TUESDAY to "ثلا",
    Calendar.WEDNESDAY to "أرب",
    Calendar.THURSDAY to "خمي",
    Calendar.FRIDAY to "جمع"
)

fun dayName(d: Int): String = DAY_NAMES[d] ?: ""
fun dayShort(d: Int): String = DAY_SHORT[d] ?: ""

fun formatTime(h: Int, m: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", hh, m, if (h < 12) "ص" else "م")
}

fun repeatSummary(a: AlarmItem): String = when (a.repeat) {
    AlarmItem.DAILY -> "يومياً"
    AlarmItem.CUSTOM -> DAY_ORDER.filter { a.days.contains(it) }.joinToString("، ") { dayName(it) }
    else -> "مرة واحدة"
}

object AlarmStore {
    private const val PREFS = "autocall_data"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<AlarmItem> {
        val s = prefs(c).getString("alarms", "[]") ?: "[]"
        val arr = JSONArray(s)
        val out = ArrayList<AlarmItem>()
        for (i in 0 until arr.length()) out.add(AlarmItem.fromJson(arr.getJSONObject(i)))
        return out
    }

    @Synchronized
    private fun saveAll(c: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("alarms", arr.toString()).apply()
    }

    @Synchronized
    fun nextId(c: Context): Int {
        val n = prefs(c).getInt("next_id", 1)
        prefs(c).edit().putInt("next_id", n + 1).apply()
        return n
    }

    fun get(c: Context, id: Int): AlarmItem? = all(c).firstOrNull { it.id == id }

    fun upsert(c: Context, a: AlarmItem) {
        val list = all(c).toMutableList()
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list.add(a)
        saveAll(c, list)
    }

    fun delete(c: Context, id: Int) {
        saveAll(c, all(c).filter { it.id != id })
    }

    fun clear(c: Context) {
        all(c).forEach { AlarmScheduler.cancel(c, it.id) }
        saveAll(c, emptyList())
    }
}
