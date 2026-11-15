package com.example.autocall

import android.content.Context
import android.util.Base64
import android.util.Patterns
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * حسابات محلية على الجهاز (لا يوجد خادم). عدة حسابات مسموحة.
 * كلمة المرور وجواب سؤال الأمان يُخزَّنان مشفّرَين (PBKDF2 مع salt) ولا يُحفظان كنص.
 */
object Auth {
    const val MIN_PASS = 6

    val QUESTIONS = listOf(
        "ما اسم أول مدرسة التحقت بها؟",
        "ما اسم مدينتك المفضلة؟",
        "ما اسم أقرب صديق لك في الطفولة؟",
        "ما اسم أول حيوان أليف امتلكته؟",
        "ما اسم الشارع الذي نشأت فيه؟"
    )

    private fun p(c: Context) = c.getSharedPreferences("hamko_accounts", Context.MODE_PRIVATE)

    private fun load(c: Context): JSONArray =
        try { JSONArray(p(c).getString("accounts", "[]")) } catch (e: Exception) { JSONArray() }

    private fun save(c: Context, arr: JSONArray) {
        p(c).edit().putString("accounts", arr.toString()).commit()
    }

    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun isValidEmail(s: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

    private fun find(arr: JSONArray, key: String): Int {
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("email") == key) return i
        return -1
    }

    fun hasAccounts(c: Context): Boolean = load(c).length() > 0

    fun exists(c: Context, email: String): Boolean = find(load(c), normalize(email)) >= 0

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

    fun userName(c: Context): String = currentObj(c)?.optString("name", "") ?: ""

    fun userEmail(c: Context): String = currentObj(c)?.optString("email", "") ?: ""

    fun logout(c: Context) {
        p(c).edit().remove("current").commit()
    }

    /** يعيد رسالة خطأ أو null عند النجاح (ويُسجَّل الدخول مباشرة). */
    fun register(c: Context, name: String, email: String, pass: String, question: String, answer: String): String? {
        val key = normalize(email)
        if (name.isBlank()) return "أدخل اسمك"
        if (!isValidEmail(key)) return "البريد الإلكتروني غير صالح"
        if (pass.length < MIN_PASS) return "كلمة المرور يجب ألا تقل عن $MIN_PASS أحرف"
        if (question.isBlank()) return "اختر سؤال الأمان"
        if (answer.trim().length < 2) return "أدخل جواب سؤال الأمان"
        val arr = load(c)
        if (find(arr, key) >= 0) return "هذا البريد مسجّل مسبقاً، سجّل الدخول بدلاً من ذلك"

        val salt = newSalt()
        val aSalt = newSalt()
        arr.put(
            JSONObject()
                .put("email", key)
                .put("name", name.trim())
                .put("salt", b64(salt))
                .put("hash", hash(salt, pass))
                .put("question", question)
                .put("aSalt", b64(aSalt))
                .put("aHash", hash(aSalt, normAnswer(answer)))
                .put("google", false)
        )
        save(c, arr)
        p(c).edit().putString("current", key).commit()
        return null
    }

    fun login(c: Context, email: String, pass: String): String? {
        val key = normalize(email)
        val arr = load(c)
        val i = find(arr, key)
        if (i < 0) return "لا يوجد حساب بهذا البريد"
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
            arr.put(
                JSONObject()
                    .put("email", key)
                    .put("name", key.substringBefore("@"))
                    .put("google", true)
            )
        } else {
            arr.getJSONObject(i).put("google", true)
        }
        save(c, arr)
        p(c).edit().putString("current", key).commit()
    }

    /** سؤال الأمان لحساب، أو null إن لم يوجد الحساب/السؤال. */
    fun questionFor(c: Context, email: String): String? {
        val arr = load(c)
        val i = find(arr, normalize(email))
        if (i < 0) return null
        val q = arr.getJSONObject(i).optString("question", "")
        return if (q.isEmpty()) null else q
    }

    /** إعادة تعيين كلمة المرور بجواب سؤال الأمان. null = نجاح. */
    fun resetPassword(c: Context, email: String, answer: String, newPass: String): String? {
        val key = normalize(email)
        val arr = load(c)
        val i = find(arr, key)
        if (i < 0) return "لا يوجد حساب بهذا البريد"
        val o = arr.getJSONObject(i)
        val aSalt = o.optString("aSalt", "")
        if (aSalt.isEmpty()) return "لا يوجد سؤال أمان لهذا الحساب"
        if (!same(hash(Base64.decode(aSalt, Base64.NO_WRAP), normAnswer(answer)), o.optString("aHash"))) {
            return "جواب سؤال الأمان غير صحيح"
        }
        if (newPass.length < MIN_PASS) return "كلمة المرور يجب ألا تقل عن $MIN_PASS أحرف"
        val salt = newSalt()
        o.put("salt", b64(salt)).put("hash", hash(salt, newPass))
        save(c, arr)
        p(c).edit().putString("current", key).commit()
        return null
    }

    private fun normAnswer(s: String) = s.trim().lowercase(Locale.ROOT)

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
