package com.daniviaworld.daniviaflix

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hm = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun hhmm(ms: Long): String = hm.format(Date(ms))

private fun progressOf(p: Program, now: Long): Float {
    val d = p.endMs - p.startMs
    return if (d <= 0) 0f else ((now - p.startMs).toFloat() / d).coerceIn(0f, 1f)
}

private fun digitOf(k: Key): String? = when (k) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}

/** Reproduce un canal en directo a pantalla completa, sin controles de vídeo. */
@Composable
private fun LivePlayer(client: EmbyClient, ch: Channel, modifier: Modifier) {
    val ctx = LocalContext.current
    var plan by remember { mutableStateOf<PlayPlan?>(null) }
    var force by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    val direct = ch.url != null
    var useAlt by remember { mutableStateOf(false) }

    LaunchedEffect(force, useAlt) {
        plan = null
        error = null
        try {
            plan = if (direct) {
                PlayPlan(if (useAlt) (ch.alt ?: ch.url!!) else ch.url!!, "DirectPlay", "", "", 0L, emptyList())
            } else {
                client.planPlayback(ch.asItem(), 0L, force, Prefs.quality(ctx), live = true)
            }
        } catch (e: Exception) {
            error = friendlyError(e)
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val p = plan
        if (p != null) {
            val exo = remember(p) {
                val http = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(if (direct) emptyMap() else mapOf("X-Emby-Token" to (client.session?.token ?: "")))
                ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
                    .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(p.url))
                        playWhenReady = true
                        prepare()
                    }
            }
            DisposableEffect(exo) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                        } else if (direct) {
                            if (!useAlt && ch.alt != null) useAlt = true else error = "Este canal no está disponible ahora mismo."
                        } else if (!force) {
                            force = true // reintenta dejando que el servidor adapte el canal
                        } else {
                            error = "Este canal no está disponible ahora mismo."
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                    }
                }
                exo.addListener(listener)
                if (!direct) CoroutineScope(Dispatchers.IO).launch { client.report("start", p, ch.id, 0L, false) }
                onDispose {
                    exo.removeListener(listener)
                    exo.release()
                    if (!direct) CoroutineScope(Dispatchers.IO).launch { client.report("stop", p, ch.id, 0L, false) }
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        player = exo
                        useController = false
                        keepScreenOn = true
                    }
                },
            )
        }
        val err = error
        if (err != null) {
            Text(err, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
        } else if (p == null || buffering) {
            CircularProgressIndicator(color = Amber)
        }
    }
}

@Composable
private fun ChannelRow(
    client: EmbyClient,
    ch: Channel,
    current: Boolean,
    now: Long,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .clip(shape)
            .focusRing(focused, shape)
            .background(if (focused) Color(0x33FFFFFF) else if (current) Color(0x26FFB547) else Color(0x0FFFFFFF))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ch.number, color = if (current) Amber else TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
        Box(Modifier.size(width = 58.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xF2FFFFFF))) {
            val logo = client.logo(ch, 120)
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val np = ch.now
            if (np != null && np.endMs > now) {
                Text(np.name, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { progressOf(np, now) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                    color = Amber, trackColor = Color(0x33FFFFFF),
                )
            }
        }
    }
}

/** Modo TV: canal en directo a pantalla completa, zapping con el mando, lista de canales y programación. */
@Composable
fun TvScreen(client: EmbyClient, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var idx by remember { mutableStateOf(0) }
    var overlay by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var diag by remember { mutableStateOf<String?>(null) }
    var showIptv by remember { mutableStateOf(false) }
    var browseId by remember { mutableStateOf<String?>(null) }
    var progs by remember { mutableStateOf<Pair<String, List<Program>>?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(20_000)
            value = System.currentTimeMillis()
        }
    }
    val rootFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Pantalla completa horizontal.
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

    LaunchedEffect(reload) {
        loaded = false
        error = null
        val emby = try {
            client.channels()
        } catch (e: Exception) {
            error = friendlyError(e)
            emptyList()
        }
        val extra = Iptv.loadAll(ctx).let { l ->
            l.mapIndexed { i, c -> if (c.number.isBlank()) c.copy(number = (emby.size + i + 1).toString()) else c }
        }
        channels = emby + extra
        val last = Prefs.lastChannel(ctx)
        idx = channels.indexOfFirst { it.id == last }.coerceAtLeast(0)
        if (channels.isEmpty()) diag = try { client.liveDiag() } catch (e: Exception) { null }
        loaded = true
    }

    val cur = channels.getOrNull(idx)
    val shownId = if (overlay) (browseId ?: cur?.id) else cur?.id

    // Al cambiar de canal: se recuerda, se enseña el cartel un momento y se oculta.
    LaunchedEffect(cur?.id) {
        val c = cur ?: return@LaunchedEffect
        Prefs.setLastChannel(ctx, c.id)
        banner = true
        delay(5000)
        banner = false
    }
    // Programación del canal que se está viendo (o del que estás explorando en la lista).
    LaunchedEffect(shownId) {
        val id = shownId ?: return@LaunchedEffect
        if (id.startsWith("iptv:")) {
            progs = id to emptyList()
            return@LaunchedEffect
        }
        delay(250)
        val l = try {
            client.programs(id)
        } catch (e: Exception) {
            emptyList()
        }
        progs = id to l
    }
    // Número tecleado con el mando: salta al canal con ese número.
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        val n = digits
        digits = ""
        val i = channels.indexOfFirst { it.number == n }
        if (i >= 0) idx = i
    }
    // El foco va a la lista al abrirla y vuelve al vídeo al cerrarla.
    LaunchedEffect(overlay, loaded) {
        if (!loaded || channels.isEmpty()) return@LaunchedEffect
        if (overlay) {
            listState.scrollToItem((idx - 3).coerceAtLeast(0))
            delay(80)
            try {
                rowFocus.requestFocus()
            } catch (e: Exception) {
                // la fila aún no existe
            }
        } else {
            try {
                rootFocus.requestFocus()
            } catch (e: Exception) {
                // sin foco todavía
            }
        }
    }

    BackHandler(enabled = overlay) { overlay = false }

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        idx = (idx + delta + channels.size) % channels.size
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || overlay || channels.isEmpty()) return@onKeyEvent false
                val d = digitOf(e.key)
                when {
                    d != null -> {
                        if (digits.length < 4) digits += d
                        true
                    }
                    e.key == Key.DirectionUp || e.key == Key.ChannelUp -> {
                        zap(+1)
                        true
                    }
                    e.key == Key.DirectionDown || e.key == Key.ChannelDown -> {
                        zap(-1)
                        true
                    }
                    e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter ||
                        e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> {
                        overlay = true
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { overlay = !overlay } }
    ) {
        if (showIptv) IptvDialog(onClose = { showIptv = false }, onChanged = { reload++ })
        val wide = maxWidth > 700.dp
        val panelW = if (maxWidth > 1000.dp) 460.dp else if (wide) 360.dp else 300.dp

        if (cur != null) {
            androidx.compose.runtime.key(cur.id) { LivePlayer(client, cur, Modifier.fillMaxSize()) }
        } else if (!loaded) {
            CircularProgressIndicator(color = Amber, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Aún no tienes canales.\nAñade una lista M3U o Xtream Codes.",
                    color = Color.White, textAlign = TextAlign.Center,
                )
                diag?.let { Text("\nDiagnóstico Emby:\n$it", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = { showIptv = true }) { Text("Añadir canales") }
                androidx.compose.material3.OutlinedButton(onClick = onExit) { Text("Volver") }
            }
        }

        // Cartel del canal (al cambiar).
        if (cur != null && banner && !overlay) {
            val list = progs?.takeIf { it.first == cur.id }?.second.orEmpty()
            val nowP = list.firstOrNull { now in it.startMs until it.endMs } ?: cur.now
            val nextP = list.firstOrNull { nowP != null && it.startMs >= nowP.endMs }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 84.dp, height = 58.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xF2FFFFFF))) {
                    val logo = client.logo(cur, 160)
                    if (logo != null) {
                        AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("${cur.number}  ${cur.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    if (nowP != null) {
                        Text("${hhmm(nowP.startMs)}  ${nowP.name}", fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(
                            progress = { progressOf(nowP, now) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(3.dp),
                            color = Amber, trackColor = Color(0x44FFFFFF),
                        )
                    }
                    if (nextP != null) {
                        Text("A continuación · ${hhmm(nextP.startMs)}  ${nextP.name}", fontSize = 13.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Número que se está tecleando.
        if (digits.isNotEmpty()) {
            Text(
                digits, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp),
            )
        }

        // Lista de canales + programación.
        if (overlay && channels.isNotEmpty()) {
            Row(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF20B0B14), Color(0xCC0B0B14), Color(0x330B0B14))))) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(panelW).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        androidx.compose.material3.OutlinedButton(onClick = { showIptv = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Añadir o quitar listas (M3U / Xtream)")
                        }
                    }
                    items(channels.size, key = { channels[it].id }) { i ->
                        val ch = channels[i]
                        ChannelRow(
                            client, ch, i == idx, now,
                            if (i == idx) Modifier.focusRequester(rowFocus) else Modifier,
                            onFocus = { browseId = ch.id },
                        ) {
                            idx = i
                            overlay = false
                        }
                    }
                }
                if (wide) {
                    val shown = channels.firstOrNull { it.id == shownId } ?: cur
                    val list = progs?.takeIf { it.first == shown?.id }?.second.orEmpty()
                    Column(Modifier.weight(1f).fillMaxHeight().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shown != null) {
                            Text("${shown.number}  ${shown.name}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (list.isEmpty()) {
                                Text("Sin información de programación.", color = TextMuted, fontSize = 14.sp)
                            }
                            list.take(7).forEach { p ->
                                val isNow = now in p.startMs until p.endMs
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(hhmm(p.startMs), color = if (isNow) Amber else TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
                                    Text(p.name, color = if (isNow) Color.White else Color(0xFFC9C9D8), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isNow) {
                                    LinearProgressIndicator(
                                        progress = { progressOf(p, now) },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = Amber, trackColor = Color(0x33FFFFFF),
                                    )
                                    if (p.overview.isNotBlank()) {
                                        Text(p.overview, color = Color(0xFFE6E6F0), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
