package com.daniviaworld.daniviaflix

import android.content.Context

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
}
