package com.daniviaworld.daniviaflix

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class UpdateInfo(val name: String, val code: Long, val url: String, val notes: String)

/**
 * Actualización desde la propia app, igual que DANIVIA Home y Momentos:
 * mira la última release de GitHub (el tag es el número de versión), y si es más nueva
 * pregunta, descarga el APK y abre el instalador.
 */
object Updater {
    const val REPO = "daniviaworld/daniviaflix"
    private val http = OkHttpClient()

    fun versionCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun versionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    /** null = no hay nada nuevo (o no se pudo comprobar; nunca debe romper el arranque). */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val j = JSONObject(r.body?.string() ?: return@use null)
                val tag = j.optString("tag_name").toLongOrNull() ?: return@use null
                if (tag <= versionCode(ctx)) return@use null
                val assets = j.optJSONArray("assets") ?: return@use null
                var found: UpdateInfo? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        found = UpdateInfo(
                            name = j.optString("name").ifEmpty { tag.toString() },
                            code = tag,
                            url = a.getString("browser_download_url"),
                            notes = j.optString("body"),
                        )
                        break
                    }
                }
                found
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "DaniviaFlix.apk")
            val req = Request.Builder().url(info.url).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("No se pudo descargar la actualización (HTTP ${r.code}).")
                val body = r.body ?: throw IOException("La descarga llegó vacía.")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            out
        }

    fun install(ctx: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            throw IOException("Activa «Instalar apps desconocidas» para DaniviaFlix y pulsa Actualizar otra vez.")
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("Nueva versión: ${info.name}") },
        text = {
            Column {
                Text(info.notes.ifBlank { "Hay una versión nueva de DaniviaFlix disponible." })
                val p = progress
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    Text("Descargando… ${(p * 100).toInt()} %", modifier = Modifier.padding(top = 6.dp))
                }
                val err = error
                if (err != null) {
                    Text(err, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = progress == null,
                onClick = {
                    scope.launch {
                        error = null
                        progress = 0f
                        try {
                            val f = Updater.download(ctx, info) { progress = it }
                            Updater.install(ctx, f)
                            progress = null
                        } catch (e: Exception) {
                            error = friendlyError(e)
                            progress = null
                        }
                    }
                },
            ) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(enabled = progress == null, onClick = onDismiss) { Text("Más tarde") }
        },
    )
}
