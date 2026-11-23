package com.daniviaworld.daniviaflix

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(client: EmbyClient, item: Item, startMs: Long, onExit: () -> Unit, onNext: (Item) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<PlayPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var forceTranscode by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resumeAt by remember { mutableLongStateOf(startMs) }
    var maxMbps by remember { mutableStateOf(Prefs.quality(ctx)) }
    var controls by remember { mutableStateOf(true) }
    var showQuality by remember { mutableStateOf(false) }
    var posGetter by remember { mutableStateOf<(() -> Long)?>(null) }

    // Pide al servidor el mejor modo según lo que decodifica este dispositivo (4K, 1080p, 720p...)
    // y el tope de calidad elegido (automático = la mejor disponible).
    LaunchedEffect(forceTranscode, maxMbps) {
        plan = null
        error = null
        try {
            plan = client.planPlayback(item, resumeAt, forceTranscode, maxMbps)
        } catch (e: Exception) {
            error = friendlyError(e)
        }
    }

    // Pantalla completa horizontal mientras se reproduce.
    DisposableEffect(Unit) {
        val act = ctx.findActivity()
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctrl = act?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val p = plan
        val err = error
        when {
            p != null -> VideoSurface(
                client, item, p,
                onEnded = {
                    // Al terminar un episodio pasa solo al siguiente.
                    scope.launch {
                        val n = if (item.type == "Episode") {
                            try {
                                client.nextEpisode(item)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                        if (n != null) onNext(n) else onExit()
                    }
                },
                onError = { pos ->
                    if (p.method != "Transcode") {
                        resumeAt = pos
                        notice = "Adaptando la calidad a tu dispositivo…"
                        forceTranscode = true
                    } else {
                        error = "No se pudo reproducir este vídeo."
                    }
                },
                onControls = { controls = it },
                onPos = { posGetter = it },
            )
            err != null -> Text(err, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            else -> CircularProgressIndicator(color = Amber)
        }
        val n = notice
        if (n != null && p == null && err == null) {
            Text(n, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
        }

        // Barra superior (se ve junto a los controles): volver, qué se está reproduciendo y calidad.
        if (p != null && controls) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    val title = if (item.type == "Episode") "${item.seriesName ?: ""} · T${item.seasonNo ?: 1}·E${item.episodeNo ?: 1}" else item.name
                    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val how = when (p.method) {
                        "DirectPlay" -> "Reproducción directa"
                        "DirectStream" -> "Directo"
                        else -> "Adaptada por el servidor"
                    }
                    Text(listOf(p.info, how).filter { it.isNotEmpty() }.joinToString(" · "), color = TextMuted, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = { showQuality = true }) { Text("Calidad", color = Amber, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showQuality) {
        AlertDialog(
            onDismissRequest = { showQuality = false },
            containerColor = Surface2,
            title = { Text("Calidad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Prefs.qualityOptions.forEach { (mbps, label) ->
                        TextButton(onClick = {
                            showQuality = false
                            if (mbps != maxMbps) {
                                resumeAt = posGetter?.invoke() ?: resumeAt
                                notice = null
                                maxMbps = mbps
                            }
                        }) {
                            Text(
                                (if (mbps == maxMbps) "● " else "○ ") + label,
                                color = if (mbps == maxMbps) Amber else Color.White,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQuality = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun VideoSurface(
    client: EmbyClient,
    item: Item,
    plan: PlayPlan,
    onEnded: () -> Unit,
    onError: (Long) -> Unit,
    onControls: (Boolean) -> Unit,
    onPos: (() -> Long) -> Unit,
) {
    val ctx = LocalContext.current
    // En transcodificación el servidor ya arranca en el punto pedido, y la línea de tiempo empieza en 0.
    val offset = if (plan.method == "Transcode") plan.startMs else 0L
    val subLang = remember { Prefs.subLang(ctx) }
    val audioLang = remember { Prefs.audioLang(ctx) }

    val exo = remember(plan) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("X-Emby-Token" to (client.session?.token ?: "")))
        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        val subs = plan.subs.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(s.mime)
                .setLanguage(s.lang)
                .setLabel(s.label)
                .build()
        }
        val mediaItem = MediaItem.Builder().setUri(plan.url).setSubtitleConfigurations(subs).build()
        ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                // Idiomas preferidos de Ajustes (audio y subtítulos).
                var tp = trackSelectionParameters.buildUpon()
                if (audioLang != "auto") tp = tp.setPreferredAudioLanguage(audioLang)
                when (subLang) {
                    "off" -> tp = tp.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    "auto" -> {}
                    else -> tp = tp.setPreferredTextLanguage(subLang)
                }
                trackSelectionParameters = tp.build()
                setMediaItem(mediaItem, if (offset == 0L) plan.startMs else 0L)
                playWhenReady = true
                prepare()
            }
    }

    LaunchedEffect(exo) { onPos { exo.currentPosition + offset } }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError(exo.currentPosition + offset)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        exo.addListener(listener)
        onDispose {
            val pos = exo.currentPosition + offset
            exo.removeListener(listener)
            exo.release()
            CoroutineScope(Dispatchers.IO).launch { client.report("stop", plan, item.id, pos, false) }
        }
    }

    LaunchedEffect(exo) {
        client.report("start", plan, item.id, plan.startMs, false)
        while (true) {
            delay(10_000)
            client.report("progress", plan, item.id, exo.currentPosition + offset, !exo.isPlaying)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            PlayerView(c).apply {
                player = exo
                setShowSubtitleButton(true)
                keepScreenOn = true
                controllerShowTimeoutMs = 4000
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> onControls(v == View.VISIBLE) })
                requestFocus()
            }
        },
    )
}
