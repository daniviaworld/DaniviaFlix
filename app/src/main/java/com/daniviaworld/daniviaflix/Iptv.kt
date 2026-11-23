package com.daniviaworld.daniviaflix

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Una lista de canales añadida a mano: M3U (enlace) o Xtream Codes (servidor + usuario + contraseña). */
data class IptvSource(val id: String, val name: String, val kind: String, val url: String, val user: String, val pass: String)

object Iptv {
    private fun sp(c: Context) = c.getSharedPreferences("danivia", Context.MODE_PRIVATE)

    fun list(c: Context): List<IptvSource> {
        val raw = sp(c).getString("iptv", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                IptvSource(o.optString("id"), o.optString("name"), o.optString("kind"), o.optString("url"), o.optString("user"), o.optString("pass"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(c: Context, l: List<IptvSource>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("url", it.url).put("user", it.user).put("pass", it.pass))
        }
        sp(c).edit().putString("iptv", a.toString()).apply()
    }

    private fun fetch(url: String): String {
        val con = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 60000
        con.instanceFollowRedirects = true
        con.setRequestProperty("User-Agent", "DaniviaFlix")
        return try {
            con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }

    private fun attr(line: String, key: String): String? =
        Regex(key + "=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun http(s: String?): String? = s?.takeIf { it.startsWith("http") }

    internal fun parseM3u(src: IptvSource, txt: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var logo: String? = null
        var num = ""
        for (raw in txt.lineSequence()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF")) {
                name = l.substringAfterLast(",").trim()
                logo = attr(l, "tvg-logo")
                num = attr(l, "tvg-chno") ?: ""
            } else if (!l.startsWith("#")) {
                out.add(Channel("iptv:${src.id}:${out.size}", name.ifEmpty { l }, num, null, null, url = l, logoUrl = http(logo)))
                name = ""
                logo = null
                num = ""
            }
        }
        return out
    }

    /** Descarga y convierte una lista en canales. Lanza excepción si no se puede leer. */
    suspend fun load(src: IptvSource): List<Channel> = withContext(Dispatchers.IO) {
        if (src.kind == "m3u") {
            return@withContext parseM3u(src, fetch(src.url.trim()))
        }
        var base = src.url.trim().trimEnd('/')
        if (!base.startsWith("http")) base = "http://$base"
        val u = src.user.trim()
        val p = src.pass.trim()
        val q = "username=${URLEncoder.encode(u, "UTF-8")}&password=${URLEncoder.encode(p, "UTF-8")}"
        val arr = JSONArray(fetch("$base/player_api.php?$q&action=get_live_streams"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val sid = o.optString("stream_id")
            Channel(
                "iptv:${src.id}:$sid", o.optString("name"), o.optString("num"), null, null,
                url = "$base/live/$u/$p/$sid.m3u8", logoUrl = http(o.optString("stream_icon")),
                alt = "$base/live/$u/$p/$sid.ts",
            )
        }
    }

    /** Todos los canales de todas las listas guardadas (una lista que falle se ignora). */
    suspend fun loadAll(c: Context): List<Channel> {
        val out = ArrayList<Channel>()
        for (s in list(c)) {
            try {
                out.addAll(load(s))
            } catch (e: Exception) {
                // lista caída: se sigue con las demás
            }
        }
        return out
    }
}

/** Diálogo para añadir o quitar listas de canales (M3U o Xtream Codes). */
@Composable
fun IptvDialog(onClose: () -> Unit, onChanged: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var sources by remember { mutableStateOf(Iptv.list(ctx)) }
    var kind by remember { mutableStateOf("m3u") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Listas de canales") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${s.name.ifEmpty { "Lista" }} · ${if (s.kind == "m3u") "M3U" else "Xtream"}",
                            fontSize = 14.sp, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            sources = sources.filter { it.id != s.id }
                            Iptv.save(ctx, sources)
                            onChanged()
                        }) { Text("Quitar") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == "m3u") Button(onClick = {}) { Text("M3U") } else OutlinedButton(onClick = { kind = "m3u" }) { Text("M3U") }
                    if (kind == "xtream") Button(onClick = {}) { Text("Xtream Codes") } else OutlinedButton(onClick = { kind = "xtream" }) { Text("Xtream Codes") }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it },
                    label = { Text(if (kind == "m3u") "Enlace de la lista M3U" else "Servidor (http://host:puerto)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (kind == "xtream") {
                    OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                }
                msg?.let { Text(it, fontSize = 13.sp, color = TextMuted) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && url.isNotBlank() && (kind == "m3u" || (user.isNotBlank() && pass.isNotBlank())), onClick = {
                val s = IptvSource(System.currentTimeMillis().toString(), name.trim(), kind, url.trim(), user, pass)
                busy = true
                msg = "Comprobando la lista…"
                scope.launch {
                    try {
                        val n = Iptv.load(s).size
                        if (n == 0) {
                            msg = "La lista no tiene canales. Revisa los datos."
                        } else {
                            sources = sources + s
                            Iptv.save(ctx, sources)
                            onChanged()
                            msg = "Añadida: $n canales."
                            name = ""; url = ""; user = ""; pass = ""
                        }
                    } catch (e: Exception) {
                        msg = "No se pudo leer la lista. Revisa los datos."
                    }
                    busy = false
                }
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}
