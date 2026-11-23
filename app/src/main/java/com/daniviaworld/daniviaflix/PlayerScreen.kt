package com.daniviaworld.daniviaflix

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(client: EmbyClient, item: Item, startMs: Long, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var plan by remember { mutableStateOf<PlayPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var forceTranscode by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resumeAt by remember { mutableLongStateOf(startMs) }

    // Pide al servidor el mejor modo según lo que decodifica este dispositivo (4K, 1080p, 720p...).
    LaunchedEffect(forceTranscode) {
        plan = null
        error = null
        try {
            plan = client.planPlayback(item, resumeAt, forceTranscode)
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
            p != null -> VideoSurface(client, item, p, onEnded = onExit, onError = { pos ->
                if (p.method != "Transcode") {
                    resumeAt = pos
                    notice = "Adaptando la calidad a tu dispositivo…"
                    forceTranscode = true
                } else {
                    error = "No se pudo reproducir este vídeo."
                }
            })
            err != null -> Text(err, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            else -> CircularProgressIndicator(color = Amber)
        }
        val n = notice
        if (n != null && p == null && err == null) {
            Text(n, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
        }
    }
}

@Composable
private fun VideoSurface(
    client: EmbyClient,
    item: Item,
    plan: PlayPlan,
    onEnded: () -> Unit,
    onError: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    // En transcodificación el servidor ya arranca en el punto pedido, y la línea de tiempo empieza en 0.
    val offset = if (plan.method == "Transcode") plan.startMs else 0L

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
                setMediaItem(mediaItem, if (offset == 0L) plan.startMs else 0L)
                playWhenReady = true
                prepare()
            }
    }

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
                requestFocus()
            }
        },
    )
}
