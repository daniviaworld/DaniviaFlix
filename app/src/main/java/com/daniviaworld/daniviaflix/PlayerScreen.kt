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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.TrackSelectionOverride
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
                onQuality = { showQuality = true },
                onExit = onExit,
                dialogOpen = showQuality,
                onPos = { posGetter = it },
            )
            err != null -> Text(err, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            else -> CircularProgressIndicator(color = Amber)
        }
        val n = notice
        if (n != null && p == null && err == null) {
            Text(n, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
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
    onQuality: () -> Unit,
    onExit: () -> Unit,
    dialogOpen: Boolean,
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

    var controls by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Int?>(null) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val anyDialog = dialogOpen || info || picker != null

    LaunchedEffect(exo) {
        while (true) {
            pos = exo.currentPosition + offset
            val d = exo.duration
            dur = if (plan.method == "Transcode") (item.runtimeMin?.times(60_000L) ?: 0L) else if (d != C.TIME_UNSET) d else 0L
            playing = exo.playWhenReady
            delay(400)
        }
    }
    // La barra se esconde sola a los 5 s si se está reproduciendo.
    LaunchedEffect(controls, touch, anyDialog, playing) {
        if (controls && playing && !anyDialog) {
            delay(5000)
            controls = false
        }
    }
    // Foco: en el botón de pausa cuando aparecen los controles; en la pantalla limpia cuando se esconden.
    LaunchedEffect(controls, anyDialog) {
        if (anyDialog) return@LaunchedEffect
        delay(60)
        try {
            if (controls) playFocus.requestFocus() else rootFocus.requestFocus()
        } catch (e: Exception) {
            // aún sin foco
        }
    }
    BackHandler(enabled = controls) { controls = false }

    val seekBy: (Long) -> Unit = { d ->
        val max = exo.duration.takeIf { it != C.TIME_UNSET }
        val t = (exo.currentPosition + d).coerceAtLeast(0L)
        exo.seekTo(if (max != null) t.coerceAtMost(max) else t)
        touch++
    }
    val toggle: () -> Unit = {
        exo.playWhenReady = !exo.playWhenReady
        touch++
    }

    Box(
        Modifier
            .fillMaxSize()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                touch++
                when (e.key) {
                    Key.MediaPlayPause -> { toggle(); controls = true; true }
                    Key.MediaPlay -> { exo.playWhenReady = true; true }
                    Key.MediaPause -> { exo.playWhenReady = false; controls = true; true }
                    Key.MediaFastForward -> { seekBy(30_000L); controls = true; true }
                    Key.MediaRewind -> { seekBy(-10_000L); controls = true; true }
                    else -> if (!controls) {
                        when (e.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> { controls = true; true }
                            Key.DirectionLeft -> { seekBy(-10_000L); controls = true; true }
                            Key.DirectionRight -> { seekBy(10_000L); controls = true; true }
                            else -> false
                        }
                    } else false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { controls = !controls; touch++ } }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                }
            },
        )

        if (controls) {
            val title = if (item.type == "Episode") "${item.seriesName ?: ""} · T${item.seasonNo ?: 1}·E${item.episodeNo ?: 1}" else item.name
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 32.dp, vertical = 18.dp)
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (item.type == "Episode") Text(item.name, color = TextMuted, fontSize = 14.sp, maxLines = 1)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(clock(pos), color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(clock(dur), color = TextMuted, fontSize = 13.sp)
                }
                SeekBar(if (dur > 0) pos.toFloat() / dur else 0f) { f ->
                    if (dur > 0) {
                        exo.seekTo(((f * dur).toLong() - offset).coerceAtLeast(0L))
                        touch++
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtlButton("− 10 s") { seekBy(-10_000L) }
                    CtlButton(if (playing) "Pausa" else "Reproducir", Modifier.focusRequester(playFocus)) { toggle() }
                    CtlButton("+ 10 s") { seekBy(10_000L) }
                    CtlButton("Info") { info = true }
                    CtlButton("Calidad") { onQuality() }
                    CtlButton("Subtítulos") { picker = C.TRACK_TYPE_TEXT }
                    CtlButton("Audio") { picker = C.TRACK_TYPE_AUDIO }
                }
            }
        }
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            containerColor = Surface2,
            title = { Text(item.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val meta = listOfNotNull(item.year?.toString(), item.runtimeMin?.let { "$it min" }, item.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
                    if (meta.isNotEmpty()) Text(meta, color = TextMuted, fontSize = 13.sp)
                    val how = when (plan.method) {
                        "DirectPlay" -> "Reproducción directa"
                        "DirectStream" -> "Directo"
                        else -> "Adaptada por el servidor"
                    }
                    Text(listOf(plan.info, how).filter { it.isNotEmpty() }.joinToString(" · "), color = Amber, fontSize = 13.sp)
                    if (item.overview.isNotBlank()) Text(item.overview, fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text("Cerrar") } },
        )
    }
    picker?.let { t -> TrackDialog(exo, t) { picker = null } }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun CtlButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .clip(shape)
            .focusRing(focused, shape)
            .background(if (focused) Color.White else Color(0x33FFFFFF))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun SeekBar(frac: Float, onSeek: (Float) -> Unit) {
    var w by remember { mutableIntStateOf(1) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { w = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / w).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x44FFFFFF)))
        Box(
            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(Amber, Coral)))
        )
    }
}

/** Elegir pista de subtítulos o de audio. */
@Composable
private fun TrackDialog(exo: ExoPlayer, type: Int, onClose: () -> Unit) {
    val groups = remember { exo.currentTracks.groups.filter { it.type == type } }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface2,
        title = { Text(if (type == C.TRACK_TYPE_TEXT) "Subtítulos" else "Audio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (type == C.TRACK_TYPE_TEXT) {
                    TextButton(onClick = {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        onClose()
                    }) { Text("Sin subtítulos") }
                }
                if (groups.isEmpty()) Text("No hay pistas disponibles.", color = TextMuted)
                groups.forEach { g ->
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        val lang = f.language?.takeIf { it != "und" }?.let { java.util.Locale(it).displayLanguage }
                        val label = listOfNotNull(f.label, lang).distinct().joinToString(" · ").ifEmpty { "Pista ${i + 1}" }
                        val sel = g.isTrackSelected(i)
                        TextButton(onClick = {
                            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(type, false)
                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                                .build()
                            onClose()
                        }) { Text((if (sel) "● " else "○ ") + label, color = if (sel) Amber else Color.White) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}
