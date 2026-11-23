package com.daniviaworld.daniviaflix

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun HomeScreen(
    client: EmbyClient,
    mode: String,
    onOpen: (Item) -> Unit,
    onPlay: (Item, Long) -> Unit,
    onGroup: (Screen.Group) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAuthError: () -> Unit,
) {
    val isTv = rememberIsTv()
    val pad = if (isTv) 48.dp else 16.dp
    val posterW = if (isTv) 150.dp else 118.dp
    val wideW = if (isTv) 250.dp else 190.dp
    val cols = if (isTv) 4 else 2
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var views by remember { mutableStateOf<List<Item>>(emptyList()) }
    var resume by remember { mutableStateOf<List<Item>>(emptyList()) }
    var favs by remember { mutableStateOf<List<Item>>(emptyList()) }
    var latest by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var resumeDone by remember { mutableStateOf(false) }
    var latestDone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    val isSeries = mode == "series"
    val typesForMode = if (isSeries) "Series" else "Movie"
    fun inMode(i: Item) = if (isSeries) (i.type == "Series" || i.type == "Episode") else (i.type != "Series" && i.type != "Episode")
    val moodList = moods.filter { m ->
        if (isSeries) m.kind in setOf("marathon", "kids", "favorites", "random")
        else m.kind in setOf("short", "epic", "kids", "favorites", "random")
    }

    // Todo se pide a la vez: la portada va apareciendo según llegan los datos, sin esperas en cadena.
    LaunchedEffect(Unit) {
        var authFailed = false
        coroutineScope {
            launch {
                try {
                    views = client.views().filter {
                        if (isSeries) it.collectionType == "tvshows"
                        else it.collectionType != "tvshows" && it.collectionType != "livetv" && it.collectionType != "music" && !isTvView(it)
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("401") == true) authFailed = true
                    error = friendlyError(e)
                }
                loading = false
            }
            launch {
                try {
                    resume = client.resume().filter { inMode(it) }
                } catch (e: Exception) {
                    // sin "continuar viendo"
                }
                resumeDone = true
            }
            launch {
                try {
                    latest = client.latest(typesForMode)
                } catch (e: Exception) {
                    // sin novedades
                }
                latestDone = true
            }
            launch {
                favs = try {
                    client.favorites().filter { inMode(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        loading = false
        if (authFailed) onAuthError()
    }

    // La cartelera se monta una sola vez, cuando ya están los dos datos (evita saltos y fallos del carrusel).
    val hero = remember(resume, latest, resumeDone, latestDone) {
        if (resumeDone && latestDone) (resume.take(4) + latest).distinctBy { it.id }.take(12) else emptyList()
    }

    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DMark(30.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Wordmark(if (isTv) 20.sp else 14.sp) }
                NavIcon(Icons.Filled.Search, "Buscar") { onSearch() }
                Spacer(Modifier.width(8.dp))
                NavIcon(Icons.Filled.Favorite, "Favoritos") { onGroup(Screen.Group("Mis favoritos", null, "favorites", "")) }
                Spacer(Modifier.width(8.dp))
                NavIcon(Icons.Filled.Settings, "Ajustes") { onSettings() }
            }
        }
        val err = error
        if (err != null) item {
            Text(err, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = pad, vertical = 8.dp))
        }
        if (loading) item {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        }

        // Cartelera: carrusel curvo con el color del póster de fondo.
        if (hero.isNotEmpty()) item {
            // Si la lista cambia (llegan más datos), se recrea la cartelera para que el carrusel no se descuadre.
            key(hero.map { it.id }) { Cartelera(client, hero, isTv, onOpen, onPlay) }
        }

        // ¿Qué te apetece?: elige según el momento en lugar de filas repetidas.
        item { Section("¿Qué te apetece?", pad) {} }
        item {
            MoodGrid(pad, moodList) { m ->
                when (m.kind) {
                    "random" -> scope.launch {
                        try {
                            client.random(typesForMode)?.let { onOpen(it) }
                        } catch (e: Exception) {
                            error = friendlyError(e)
                        }
                    }
                    else -> onGroup(Screen.Group(m.label, null, m.kind, m.kind))
                }
            }
        }

        // Tus bibliotecas, en el orden de Emby.
        if (views.isNotEmpty()) {
            item { Section("Mis bibliotecas", pad) {} }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(views, key = { _, v -> v.id }) { idx, v ->
                        LibraryStack(client, v, idx, if (isTv) 320.dp else 250.dp) { onOpen(v) }
                    }
                }
            }
        }

        if (resume.isNotEmpty()) item {
            Section("Continuar viendo", pad) {
                LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(resume, key = { it.id }) { i ->
                        PosterCard(client, i, Modifier.width(if (i.type == "Episode") wideW else posterW)) { onPlay(i, i.resumeMs) }
                    }
                }
            }
        }

        if (favs.isNotEmpty()) item {
            Section("Mis favoritos", pad) {
                LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favs, key = { it.id }) { i -> PosterCard(client, i, Modifier.width(posterW)) { onOpen(i) } }
                }
            }
        }

        // Una fila "Lo último en…" por cada biblioteca, en tu orden.
        items(views, key = { "lib-" + it.id }) { v -> LibraryRow(client, v, pad, posterW, onOpen) }
    }

    update?.let { u -> UpdateDialog(u) { update = null } }
}

@Composable
private fun LibraryRow(client: EmbyClient, view: Item, pad: androidx.compose.ui.unit.Dp, posterW: androidx.compose.ui.unit.Dp, onOpen: (Item) -> Unit) {
    var list by remember(view.id) { mutableStateOf<List<Item>?>(null) }
    LaunchedEffect(view.id) {
        list = try {
            client.latestIn(view)
        } catch (e: Exception) {
            emptyList()
        }
    }
    val l = list
    if (l != null && l.isNotEmpty()) {
        Section("Lo último en ${view.name}", pad) {
            LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(l, key = { it.id }) { i -> PosterCard(client, i, Modifier.width(posterW)) { onOpen(i) } }
            }
        }
    }
}

/** Galería "Cartelera": pósters en arco 3D. El de en medio crece y el fondo se tiñe con su color. */
@Composable
private fun Cartelera(
    client: EmbyClient,
    items: List<Item>,
    isTv: Boolean,
    onOpen: (Item) -> Unit,
    onPlay: (Item, Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPagerState(pageCount = { items.size })
    val cur = items[state.currentPage.coerceIn(0, items.lastIndex)]
    val ambient = rememberAmbient(client.poster(cur, 300))
    val pageW = if (isTv) 250.dp else 190.dp
    val pageH = pageW * 1.5f
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ambient, Bg)))
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(pageH + 16.dp)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionRight -> {
                            if (state.currentPage < items.lastIndex) scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (state.currentPage > 0) scope.launch { state.animateScrollToPage(state.currentPage - 1) }
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onOpen(cur)
                            true
                        }
                        else -> false
                    }
                }
                .onFocusChanged { focused = it.hasFocus }
                .focusable()
        ) {
            val side = (maxWidth - pageW) / 2
            HorizontalPager(
                state = state,
                contentPadding = PaddingValues(horizontal = side),
                pageSize = PageSize.Fixed(pageW),
                key = { items.getOrNull(it)?.id ?: it },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                Box(
                    Modifier
                        .fillMaxHeight()
                        .zIndex(if (page == state.currentPage) 1f else 0f)
                        .graphicsLayer {
                            val pos = page - state.currentPage - state.currentPageOffsetFraction
                            val a = abs(pos).coerceAtMost(2f)
                            val sc = (1f - 0.16f * a).coerceAtLeast(0.62f)
                            scaleX = sc
                            scaleY = sc
                            rotationY = -pos.coerceIn(-2f, 2f) * 32f
                            alpha = (1f - 0.3f * a).coerceAtLeast(0.3f)
                            translationX = -pos * size.width * 0.12f
                            cameraDistance = 14f * density
                        }
                        .pointerInput(item.id) { detectTapGestures { onOpen(item) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(pageW)
                            .height(pageH)
                            .clip(shape)
                            .background(Surface2)
                            .then(if (focused && page == state.currentPage) Modifier.border(3.dp, Amber, shape) else Modifier)
                    ) {
                        val url = client.poster(item, 600)
                        if (url != null) {
                            AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(item.name, modifier = Modifier.align(Alignment.Center).padding(12.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (cur.type == "Episode") {
                Text("${cur.seriesName ?: ""} · T${cur.seasonNo ?: 1}·E${cur.episodeNo ?: 1}", color = Amber, fontSize = 13.sp, maxLines = 1)
            }
            Text(cur.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                cur.year?.toString(),
                cur.runtimeMin?.let { "$it min" },
                cur.rating?.let { "★ %.1f".format(it) },
            ).joinToString("   ·   ")
            if (meta.isNotEmpty()) Text(meta, color = TextMuted, fontSize = 13.sp)
            if (isTv && cur.overview.isNotBlank()) {
                Text(
                    cur.overview, fontSize = 14.sp, color = Color(0xFFE6E6F0), maxLines = 3,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
            val playable = cur.type == "Movie" || cur.type == "Episode" || cur.type == "Video"
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (playable) {
                    val resumable = cur.resumeMs > 30_000
                    HeroButton(if (resumable) "Reanudar" else "▶  Reproducir", true) { onPlay(cur, if (resumable) cur.resumeMs else 0L) }
                    HeroButton("Ficha", false) { onOpen(cur) }
                } else {
                    HeroButton("Ver ficha", true) { onOpen(cur) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                items.forEachIndexed { i, _ ->
                    Box(
                        Modifier.size(if (i == state.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == state.currentPage) Amber else Color(0x55FFFFFF))
                    )
                }
            }
        }
    }
}

/** Botón de la cartelera con foco bien visible para el mando de la TV. */
@Composable
private fun HeroButton(text: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val mod = Modifier
        .scale(if (focused) 1.08f else 1f)
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
    if (primary) {
        Button(onClick = onClick, modifier = mod) { Text(text, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = mod) { Text(text) }
    }
}
