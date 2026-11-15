package com.example.autocall

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * حسابات محلية على الجهاز (لا يوجد خادم). الحساب = بريد أو اسم مستخدم + كلمة مرور.
 * كلمة المرور تُخزَّن مشفّرة (PBKDF2 مع salt) ولا تُحفظ كنص.
 */
object Auth {
    const val MIN_PASS = 4
    const val MIN_ID = 3

    private fun p(c: Context) = c.getSharedPreferences("hamko_accounts", Context.MODE_PRIVATE)

    private fun load(c: Context): JSONArray =
        try { JSONArray(p(c).getString("accounts", "[]")) } catch (e: Exception) { JSONArray() }

    private fun save(c: Context, arr: JSONArray) {
        p(c).edit().putString("accounts", arr.toString()).commit()
    }

    /** المعرّف (بريد أو اسم مستخدم) بصيغة موحّدة. */
    fun normalize(id: String): String = id.trim().lowercase(Locale.ROOT)

    private fun find(arr: JSONArray, key: String): Int {
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("email") == key) return i
        return -1
    }

    fun hasAccounts(c: Context): Boolean = load(c).length() > 0

    fun exists(c: Context, id: String): Boolean = find(load(c), normalize(id)) >= 0

    fun currentKey(c: Context): String = p(c).getString("current", "") ?: ""

    fun isLoggedIn(c: Context): Boolean {
        val k = currentKey(c)
        return k.isNotEmpty() && find(load(c), k) >= 0
    }

    private fun currentObj(c: Context): JSONObject? {
        val arr = load(c)
        val i = find(arr, currentKey(c))
        return if (i >= 0) arr.getJSONObject(i) else null
    }

    /** الاسم المعروض: جزء ما قبل @ إن كان بريداً. */
    fun userName(c: Context): String = currentKey(c).substringBefore("@")

    fun userEmail(c: Context): String = currentObj(c)?.optString("email", "") ?: ""

    fun logout(c: Context) {
        p(c).edit().remove("current").commit()
    }

    /** إنشاء حساب: معرّف + كلمة مرور + تأكيدها. يعيد رسالة خطأ أو null عند النجاح. */
    fun register(c: Context, id: String, pass: String, confirm: String): String? {
        val key = normalize(id)
        if (key.length < MIN_ID) return "أدخل بريداً أو اسم مستخدم (٣ أحرف على الأقل)"
        if (key.any { it.isWhitespace() }) return "اسم المستخدم لا يحتوي مسافات"
        if (pass.length < MIN_PASS) return "كلمة المرور يجب ألا تقل عن $MIN_PASS أحرف"
        if (pass != confirm) return "كلمتا المرور غير متطابقتين"
        val arr = load(c)
        if (find(arr, key) >= 0) return "هذا الحساب موجود مسبقاً، سجّل الدخول بدلاً من ذلك"

        val salt = newSalt()
        arr.put(
            JSONObject()
                .put("email", key)
                .put("name", key.substringBefore("@"))
                .put("salt", b64(salt))
                .put("hash", hash(salt, pass))
                .put("google", false)
        )
        save(c, arr)
        p(c).edit().putString("current", key).commit()
        return null
    }

    fun login(c: Context, id: String, pass: String): String? {
        val key = normalize(id)
        val arr = load(c)
        val i = find(arr, key)
        if (i < 0) return "لا يوجد حساب بهذا الاسم"
        val o = arr.getJSONObject(i)
        val saltB64 = o.optString("salt", "")
        if (saltB64.isEmpty()) return "هذا الحساب مرتبط بـ Google، استخدم زر Google للدخول"
        if (!same(hash(Base64.decode(saltB64, Base64.NO_WRAP), pass), o.optString("hash"))) {
            return "كلمة المرور غير صحيحة"
        }
        p(c).edit().putString("current", key).commit()
        return null
    }

    /** دخول/ربط حساب Google الموجود على الجهاز. ينشئ الحساب إن لم يكن موجوداً. */
    fun loginWithGoogle(c: Context, email: String) {
        val key = normalize(email)
        val arr = load(c)
        val i = find(arr, key)
        if (i < 0) {
            arr.put(JSONObject().put("email", key).put("name", key.substringBefore("@")).put("google", true))
        } else {
            arr.getJSONObject(i).put("google", true)
        }
        save(c, arr)
        p(c).edit().putString("current", key).commit()
    }

    /**
     * «نسيت كلمة المرور»: يحذف بيانات الدخول لهذا الحساب فقط (لا المواعيد)،
     * فيُنشئه المستخدم من جديد بكلمة مرور جديدة بنفس الاسم وتعود مواعيده.
     */
    fun resetAccount(c: Context, id: String): Boolean {
        val key = normalize(id)
        val arr = load(c)
        val i = find(arr, key)
        if (i < 0) return false
        val out = JSONArray()
        for (j in 0 until arr.length()) if (j != i) out.put(arr.getJSONObject(j))
        save(c, out)
        if (currentKey(c) == key) logout(c)
        return true
    }

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun hash(salt: ByteArray, secret: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 10000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return b64(bytes)
    }

    private fun same(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
