package com.example.autocall

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AlarmItem(
    val id: Int,
    val name: String,
    val number: String,
    val hour: Int,                 // 0..23
    val minute: Int,
    val repeat: Int,               // ONCE / DAILY / CUSTOM
    val days: Set<Int>,            // Calendar.SUNDAY..SATURDAY (للتكرار المخصص)
    val autoCall: Boolean,         // true = اتصال تلقائي، false = تنبيه فقط
    val status: Int = SCHEDULED,   // SCHEDULED / COMPLETED / CANCELED
    val date: Long = 0L,           // يوم الموعد (للمرة الواحدة)
    val owner: String = "",        // بريد صاحب الحساب
    val lastRun: Long = 0L,        // آخر مرة نُفّذ فيها
    val lastResult: String = "",   // نتيجة آخر تنفيذ
    val createdAt: Long = System.currentTimeMillis()
) {
    val enabled: Boolean get() = status == SCHEDULED

    companion object {
        const val ONCE = 0
        const val DAILY = 1
        const val CUSTOM = 2

        const val SCHEDULED = 0
        const val COMPLETED = 1
        const val CANCELED = 2

        fun fromJson(o: JSONObject): AlarmItem {
            val d = mutableSetOf<Int>()
            val arr = o.optJSONArray("days")
            if (arr != null) for (i in 0 until arr.length()) d.add(arr.getInt(i))
            val st = if (o.has("status")) o.optInt("status", SCHEDULED)
            else if (o.optBoolean("enabled", true)) SCHEDULED else CANCELED
            return AlarmItem(
                id = o.getInt("id"),
                name = o.optString("name"),
                number = o.optString("number"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                repeat = o.optInt("repeat", DAILY),
                days = d,
                autoCall = o.optBoolean("autoCall", true),
                status = st,
                date = o.optLong("date", 0L),
                owner = o.optString("owner", ""),
                lastRun = o.optLong("lastRun", 0L),
                lastResult = o.optString("lastResult", ""),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
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
        put("status", status)
        put("date", date)
        put("owner", owner)
        put("lastRun", lastRun)
        put("lastResult", lastResult)
        put("createdAt", createdAt)
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

/** عربي مع أرقام لاتينية لتجنّب اختلاط الأرقام. */
private val LOC: Locale = Locale.forLanguageTag("ar-u-nu-latn")

fun formatTime(h: Int, m: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", hh, m, if (h < 12) "ص" else "م")
}

fun formatDateLong(ms: Long): String = SimpleDateFormat("EEEE d/M/yyyy", LOC).format(Date(ms))

fun formatStamp(ms: Long): String = SimpleDateFormat("EEEE d/M  h:mm a", LOC).format(Date(ms))

fun statusLabel(s: Int): String = when (s) {
    AlarmItem.COMPLETED -> "مكتملة"
    AlarmItem.CANCELED -> "ملغية"
    else -> "مجدولة"
}

fun repeatSummary(a: AlarmItem): String = when (a.repeat) {
    AlarmItem.DAILY -> "يومياً"
    AlarmItem.CUSTOM -> DAY_ORDER.filter { a.days.contains(it) }.joinToString("، ") { dayName(it) }
    else -> if (a.date > 0L) "مرة واحدة • " + formatDateLong(a.date) else "مرة واحدة"
}

object AlarmStore {
    private const val PREFS = "autocall_data"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<AlarmItem> {
        val s = prefs(c).getString("alarms", "[]") ?: "[]"
        val arr = try { JSONArray(s) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<AlarmItem>()
        for (i in 0 until arr.length()) {
            try { out.add(AlarmItem.fromJson(arr.getJSONObject(i))) } catch (e: Exception) { /* تجاهل سجل تالف */ }
        }
        return out
    }

    /** مواعيد الحساب الحالي فقط. */
    fun mine(c: Context): List<AlarmItem> {
        val k = Auth.currentKey(c)
        return all(c).filter { it.owner == k }
    }

    @Synchronized
    private fun saveAll(c: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("alarms", arr.toString()).commit()
    }

    @Synchronized
    fun nextId(c: Context): Int {
        val n = prefs(c).getInt("next_id", 1)
        prefs(c).edit().putInt("next_id", n + 1).commit()
        return n
    }

    fun get(c: Context, id: Int): AlarmItem? = all(c).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(c: Context, a: AlarmItem) {
        val list = all(c).toMutableList()
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list.add(a)
        saveAll(c, list)
    }

    @Synchronized
    fun delete(c: Context, id: Int) {
        saveAll(c, all(c).filter { it.id != id })
    }

    /** المواعيد القديمة بلا مالك تُنسب لأول حساب يدخل. */
    @Synchronized
    fun claimOrphans(c: Context, owner: String) {
        val l = all(c)
        if (l.any { it.owner.isEmpty() }) {
            saveAll(c, l.map { if (it.owner.isEmpty()) it.copy(owner = owner) else it })
        }
    }

    /**
     * أي موعد «مرة واحدة» فات وقته بأكثر من دقيقتين دون تنفيذ (الجهاز كان مغلقاً مثلاً)
     * يُعلَّم مكتملاً بدل أن يبقى «مجدولاً» للأبد.
     */
    @Synchronized
    fun reconcile(c: Context) {
        val now = System.currentTimeMillis()
        for (a in all(c)) {
            if (a.status == AlarmItem.SCHEDULED && a.repeat == AlarmItem.ONCE && a.date > 0L &&
                now - AlarmScheduler.onceTime(a) > 120_000L
            ) {
                upsert(
                    c,
                    a.copy(
                        status = AlarmItem.COMPLETED,
                        lastResult = if (a.lastRun == 0L) "فات الموعد دون تنفيذ" else a.lastResult
                    )
                )
            }
        }
    }

    /** إعادة جدولة موعد مكتمل أو ملغي. */
    fun reactivate(c: Context, a: AlarmItem): AlarmItem {
        var n = a.copy(status = AlarmItem.SCHEDULED)
        if (a.repeat == AlarmItem.ONCE) {
            val t = AlarmScheduler.nextTrigger(a.copy(date = 0L)) ?: System.currentTimeMillis()
            n = n.copy(date = t)
        }
        upsert(c, n)
        AlarmScheduler.schedule(c, n)
        return n
    }
}
