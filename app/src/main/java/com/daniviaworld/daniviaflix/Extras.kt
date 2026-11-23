package com.daniviaworld.daniviaflix

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(client: EmbyClient, onBack: () -> Unit, onOpen: (Item) -> Unit) {
    val isTv = rememberIsTv()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        if (q.trim().length < 2) {
            results = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(400)
        results = try {
            client.search(q.trim())
        } catch (e: Exception) {
            emptyList()
        }
        searched = true
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Buscar", onBack)
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("Película o serie") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (searched && results.isEmpty()) {
            Text("Sin resultados para «${q.trim()}».", color = TextMuted, modifier = Modifier.padding(16.dp))
        } else {
            PosterGrid(client, results, isTv, onOpen)
        }
    }
}

@Composable
private fun OptionRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(options.size) { i ->
            val (key, label) = options[i]
            FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}

private fun deviceLines(): List<String> {
    val out = mutableListOf<String>()
    listOf(
        "H.264" to "video/avc",
        "HEVC (H.265)" to "video/hevc",
        "VP9" to "video/x-vnd.on2.vp9",
        "AV1" to "video/av01",
    ).forEach { (name, mime) ->
        if (Caps.has(mime)) {
            val h = Caps.maxSize(mime).second
            out += "$name · hasta ${if (h >= 2160) "4K" else if (h >= 1080) "1080p" else "720p"}"
        }
    }
    if (Caps.has("video/hevc")) out += "HEVC 10 bits (HDR): " + if (Caps.supportsHevc10()) "sí" else "no"
    return out
}

@Composable
fun SettingsScreen(client: EmbyClient, onBack: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = client.session
    var server by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(Prefs.quality(ctx).toString()) }
    var sub by remember { mutableStateOf(Prefs.subLang(ctx)) }
    var audio by remember { mutableStateOf(Prefs.audioLang(ctx)) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        server = try {
            client.serverInfo()
        } catch (e: Exception) {
            ""
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Ajustes", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Section("Tu cuenta", 0.dp) {}
            Text(s?.userName ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(s?.server ?: "", color = TextMuted, fontSize = 13.sp)
            if (server.isNotEmpty()) Text(server, color = TextMuted, fontSize = 13.sp)

            Section("Reproducción", 0.dp) {}
            OptionRow(
                "Calidad máxima", Prefs.qualityOptions.map { it.first.toString() to it.second }, quality,
            ) { quality = it; Prefs.setQuality(ctx, it.toInt()) }
            Text(
                "Automática elige siempre la mejor calidad que tu dispositivo puede reproducir: el original si lo soporta, y si no, el servidor lo adapta.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
            OptionRow("Subtítulos", Prefs.subOptions, sub) { sub = it; Prefs.setSubLang(ctx, it) }
            OptionRow("Idioma del audio", Prefs.audioOptions, audio) { audio = it; Prefs.setAudioLang(ctx, it) }

            Section("Este dispositivo", 0.dp) {}
            deviceLines().forEach { Text(it, color = TextMuted, fontSize = 13.sp) }

            Section("Aplicación", 0.dp) {}
            Text("DaniviaFlix ${Updater.versionName(ctx)}", fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val u = Updater.check(ctx)
                    if (u != null) update = u
                    else Toast.makeText(ctx, "Ya tienes la última versión", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Buscar actualización") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLogout) { Text("Cerrar sesión") }
        }
    }
    update?.let { u -> UpdateDialog(u) { update = null } }
}
