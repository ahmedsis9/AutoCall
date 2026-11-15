package com.example.autocall

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** حساب محلي على الجهاز فقط (لا يوجد خادم). كلمة المرور تُخزَّن مشفّرة (SHA-256 مع salt). */
object Auth {
    private fun p(c: Context) = c.getSharedPreferences("autocall_auth", Context.MODE_PRIVATE)

    fun hasAccount(c: Context): Boolean = p(c).contains("hash")

    fun userName(c: Context): String = p(c).getString("name", "") ?: ""

    fun isLoggedIn(c: Context): Boolean = hasAccount(c) && p(c).getBoolean("logged_in", false)

    fun setLoggedIn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("logged_in", v).apply()
    }

    fun register(c: Context, name: String, pass: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString("name", name)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(salt, pass))
            .putBoolean("logged_in", true)
            .apply()
    }

    fun verify(c: Context, pass: String): Boolean {
        val saltB64 = p(c).getString("salt", null) ?: return false
        val stored = p(c).getString("hash", null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        return hash(salt, pass) == stored
    }

    fun reset(c: Context) {
        p(c).edit().clear().apply()
    }

    private fun hash(salt: ByteArray, pass: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return Base64.encodeToString(md.digest(pass.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}
