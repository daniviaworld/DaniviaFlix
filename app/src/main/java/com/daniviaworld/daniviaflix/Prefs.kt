package com.daniviaworld.daniviaflix

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes del usuario (se guardan en el dispositivo). */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("danivia", Context.MODE_PRIVATE)

    /** Tope de calidad en Mbps. 0 = automática: siempre la mejor que el dispositivo pueda reproducir. */
    val qualityOptions = listOf(
        0 to "Automática (la mejor)",
        40 to "4K · 40 Mbps",
        20 to "1080p · 20 Mbps",
        8 to "720p · 8 Mbps",
        4 to "480p · 4 Mbps",
    )
    val subOptions = listOf("auto" to "Automático", "es" to "Español", "en" to "Inglés", "off" to "Sin subtítulos")
    val audioOptions = listOf("auto" to "Automático", "es" to "Español", "en" to "Inglés")

    fun quality(c: Context) = sp(c).getInt("quality", 0)
    fun setQuality(c: Context, v: Int) = sp(c).edit().putInt("quality", v).apply()
    fun subLang(c: Context) = sp(c).getString("subLang", "auto") ?: "auto"
    fun setSubLang(c: Context, v: String) = sp(c).edit().putString("subLang", v).apply()
    fun audioLang(c: Context) = sp(c).getString("audioLang", "auto") ?: "auto"
    fun setAudioLang(c: Context, v: String) = sp(c).edit().putString("audioLang", v).apply()

    fun lastChannel(c: Context) = sp(c).getString("lastChannel", null)
    fun setLastChannel(c: Context, id: String) = sp(c).edit().putString("lastChannel", id).apply()
}

/** Una cuenta de Emby guardada (servidor + usuario). Puedes tener varias y cambiar entre ellas. */
data class Account(val server: String, val userId: String, val token: String, val userName: String) {
    val initial: String get() = userName.trim().take(1).uppercase().ifEmpty { "?" }
    fun toSession() = Session(server, userId, token, userName)
}

object Accounts {
    private fun sp(c: Context) = c.getSharedPreferences("danivia", Context.MODE_PRIVATE)

    fun list(c: Context): List<Account> {
        val out = mutableListOf<Account>()
        val raw = sp(c).getString("accounts", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out += Account(o.optString("server"), o.optString("userId"), o.optString("token"), o.optString("user"))
                }
            } catch (e: Exception) {
                // lista dañada: se ignora
            }
        }
        if (out.isEmpty()) {
            // Migra la sesión de las versiones anteriores (una sola cuenta).
            val t = sp(c).getString("token", null)
            if (t != null) {
                out += Account(
                    sp(c).getString("server", "") ?: "", sp(c).getString("userId", "") ?: "", t,
                    sp(c).getString("user", "") ?: "",
                )
                save(c, out)
                sp(c).edit().remove("token").remove("userId").remove("user").apply()
            }
        }
        return out
    }

    private fun save(c: Context, l: List<Account>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(JSONObject().put("server", it.server).put("userId", it.userId).put("token", it.token).put("user", it.userName))
        }
        sp(c).edit().putString("accounts", arr.toString()).apply()
    }

    fun activeIndex(c: Context): Int {
        val n = list(c).size
        return if (n == 0) 0 else sp(c).getInt("activeAccount", 0).coerceIn(0, n - 1)
    }

    fun active(c: Context): Account? = list(c).getOrNull(activeIndex(c))

    fun setActive(c: Context, idx: Int) = sp(c).edit().putInt("activeAccount", idx).apply()

    /** Añade (o actualiza si ya existe ese usuario en ese servidor) y la deja como activa. */
    fun add(c: Context, a: Account): Int {
        val l = list(c).toMutableList()
        val existing = l.indexOfFirst { it.server == a.server && it.userName.equals(a.userName, true) }
        val idx = if (existing >= 0) {
            l[existing] = a
            existing
        } else {
            l += a
            l.lastIndex
        }
        save(c, l)
        setActive(c, idx)
        return idx
    }

    fun remove(c: Context, idx: Int) {
        val l = list(c).toMutableList()
        if (idx !in l.indices) return
        val active = activeIndex(c)
        l.removeAt(idx)
        save(c, l)
        setActive(c, if (idx < active) active - 1 else if (idx == active) 0 else active)
    }
}
