package com.daniviaworld.daniviaflix

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Hub : Screen
    data class Home(val mode: String) : Screen
    data object Accounts : Screen
    data object Tv : Screen
    data class Library(val view: Item) : Screen
    data object Settings : Screen
    data object Search : Screen
    data class Group(val title: String, val view: Item?, val kind: String, val arg: String) : Screen
    data class Detail(val item: Item) : Screen
    data class Player(val item: Item, val startMs: Long) : Screen
}

@Composable
fun rememberIsTv(): Boolean {
    val ctx = LocalContext.current
    return remember {
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }
}

internal fun Modifier.focusRing(focused: Boolean, shape: Shape): Modifier =
    if (focused) this.border(3.dp, Amber, shape) else this

private fun fmt(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun isFolder(i: Item) = i.type == "Folder" || i.type == "CollectionFolder" || i.type == "UserView"

/** Color dominante del póster: tiñe el fondo de la ficha (efecto "Ambiente"). */
@Composable
fun rememberAmbient(url: String?): Color {
    val ctx = LocalContext.current
    var c by remember(url) { mutableStateOf(Color(0xFF1A1030)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        try {
            val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(200).build()
            val res = ctx.imageLoader.execute(req)
            val bmp = (res as? SuccessResult)?.drawable?.toBitmap()
            if (bmp != null) {
                val p = withContext(Dispatchers.Default) { Palette.from(bmp).generate() }
                c = Color(p.getDarkVibrantColor(p.getDarkMutedColor(0xFF1A1030.toInt())))
            }
        } catch (e: Exception) {
            // sin color: se queda el violeta oscuro por defecto
        }
    }
    return animateColorAsState(c, tween(600), label = "ambient").value
}

// ---------------------------------------------------------------- Componentes

@Composable
fun PosterCard(client: EmbyClient, item: Item, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val ar = if (item.type == "Episode") 16f / 9f else 2f / 3f
    Column(
        modifier
            .scale(if (focused) 1.06f else 1f)
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ar).clip(shape).focusRing(focused, shape).background(Surface2)
        ) {
            val url = client.image(item, "Primary", 450)
            if (url != null) {
                AsyncImage(model = url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(item.name, modifier = Modifier.align(Alignment.Center).padding(8.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
            }
            val rt = item.runtimeMin
            if (item.resumeMs > 0 && rt != null && rt > 0) {
                LinearProgressIndicator(
                    progress = { (item.resumeMs / (rt * 60_000f)).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = Amber,
                    trackColor = Color(0x66000000),
                )
            }
        }
        Text(
            if (item.type == "Episode") (item.seriesName ?: item.name) else item.name,
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (item.type == "Episode") {
            Text(
                "T${item.seasonNo ?: 1}·E${item.episodeNo ?: 1}  ${item.name}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = TextMuted,
            )
        } else if (item.year != null) {
            Text("${item.year}", fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun LibraryTile(client: EmbyClient, view: Item, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .scale(if (focused) 1.05f else 1f)
            .clip(shape)
            .focusRing(focused, shape)
            .background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.6f), Coral.copy(alpha = 0.5f))))
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        val url = client.image(view, "Primary", 400)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Text(view.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(10.dp))
    }
}

@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Section(title: String, pad: Dp, content: @Composable () -> Unit) {
    Column {
        Text(
            title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = pad, top = 14.dp, bottom = 8.dp),
        )
        content()
    }
}

// ---------------------------------------------------------------- Login

@Composable
fun LoginScreen(client: EmbyClient, initialServer: String, onCancel: (() -> Unit)? = null, onLoggedIn: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(initialServer) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy || server.isBlank() || user.isBlank()) return
        scope.launch {
            busy = true
            error = null
            try {
                onLoggedIn(client.login(server, user.trim(), pass))
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DMark(96.dp)
            Wordmark(26.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = server, onValueChange = { server = it }, singleLine = true,
                label = { Text("Servidor (ej. 192.168.1.10:8096)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it }, singleLine = true,
                label = { Text("Usuario") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, singleLine = true,
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            val err = error
            if (err != null) Text(err, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
            Button(
                onClick = { submit() },
                enabled = !busy && server.isNotBlank() && user.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Conectando…" else "Entrar", fontWeight = FontWeight.Bold) }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancelar", color = TextMuted) }
        }
    }
}

// ---------------------------------------------------------------- Ficha

@Composable
fun DetailScreen(
    client: EmbyClient,
    base: Item,
    onBack: () -> Unit,
    onPlay: (Item, Long) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val isTv = rememberIsTv()
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf(base) }
    var seasons by remember { mutableStateOf<List<Item>>(emptyList()) }
    var season by remember { mutableStateOf<Item?>(null) }
    var episodes by remember { mutableStateOf<List<Item>>(emptyList()) }
    var children by remember { mutableStateOf<List<Item>>(emptyList()) }
    var trailers by remember { mutableStateOf<List<Item>>(emptyList()) }
    var remoteTrailer by remember { mutableStateOf<String?>(null) }
    var nextEp by remember { mutableStateOf<Item?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(base.id) {
        if (base.type == "Movie" || base.type == "Series" || base.type == "Video") {
            trailers = client.localTrailers(base.id)
            if (trailers.isEmpty()) remoteTrailer = client.remoteTrailer(base.id)
        }
        if (base.type == "Series") nextEp = client.nextUp(base.id)
        try {
            item = client.item(base.id)
        } catch (e: Exception) {
            // nos quedamos con los datos básicos
        }
        try {
            when (base.type) {
                "Series" -> {
                    seasons = client.seasons(base.id)
                    season = seasons.firstOrNull()
                }
                "BoxSet" -> children = client.children(base.id)
            }
        } catch (e: Exception) {
            // la lista queda vacía
        }
    }
    LaunchedEffect(season?.id) {
        val s = season
        if (s != null) {
            try {
                episodes = client.episodes(base.id, s.id)
            } catch (e: Exception) {
                episodes = emptyList()
            }
        }
    }

    val ambient = rememberAmbient(client.image(item, "Primary", 300))
    val pad = if (isTv) 48.dp else 16.dp
    val playable = item.type == "Movie" || item.type == "Episode" || item.type == "Video" || item.type == "MusicVideo"
    val cols = if (isTv) 6 else 3

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ambient, Bg)))) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(if (isTv) 320.dp else 210.dp)) {
                    val img = client.image(item, "Backdrop", 720) ?: client.image(item, "Primary", 600)
                    if (img != null) {
                        AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, ambient))))
                    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.type == "Episode" && item.seriesName != null) {
                        Text("${item.seriesName} · T${item.seasonNo ?: 1}·E${item.episodeNo ?: 1}", color = Amber, fontSize = 14.sp)
                    }
                    Text(item.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    val meta = listOfNotNull(
                        item.year?.toString(),
                        item.runtimeMin?.let { "$it min" },
                        item.rating?.let { "★ %.1f".format(it) },
                    ).joinToString("   ·   ")
                    if (meta.isNotEmpty()) Text(meta, color = TextMuted, fontSize = 14.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (playable) {
                            if (item.resumeMs > 30_000) {
                                Button(onClick = { onPlay(item, item.resumeMs) }) {
                                    Text("Continuar por donde lo dejaste · ${fmt(item.resumeMs)}", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { onPlay(item, 0) }) { Text("Desde el principio") }
                            } else {
                                Button(onClick = { onPlay(item, 0) }) { Text("▶  Reproducir", fontWeight = FontWeight.Bold) }
                            }
                        }
                        val ne = nextEp ?: episodes.firstOrNull()
                        if (item.type == "Series" && ne != null) {
                            val code = "T${ne.seasonNo ?: 1}·E${ne.episodeNo ?: 1}"
                            val started = ne.resumeMs > 30_000 || (ne.seasonNo ?: 1) > 1 || (ne.episodeNo ?: 1) > 1
                            Button(onClick = { onPlay(ne, if (ne.resumeMs > 30_000) ne.resumeMs else 0L) }) {
                                Text(if (started) "Continuar $code" else "▶  Reproducir $code", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (trailers.isNotEmpty()) {
                            OutlinedButton(onClick = { onPlay(trailers.first(), 0) }) { Text("Tráiler") }
                        } else if (remoteTrailer != null) {
                            OutlinedButton(onClick = {
                                try {
                                    ctx.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(remoteTrailer))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (ex: Exception) {
                                    // sin aplicación para abrir el enlace
                                }
                            }) { Text("Tráiler") }
                        }
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                client.setFavorite(item.id, !item.favorite)
                                item = item.copy(favorite = !item.favorite)
                            } catch (e: Exception) {
                                // sin cambio si el servidor no responde
                            }
                        }
                    }) {
                        Icon(
                            if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (item.favorite) "En favoritos" else "Añadir a favoritos")
                    }
                    if (item.overview.isNotBlank()) Text(item.overview, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFE6E6F0))
                }
            }
            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = pad, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons, key = { it.id }) { s ->
                            FilterChip(selected = s.id == season?.id, onClick = { season = s }, label = { Text(s.name) })
                        }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(client, ep, pad) { onPlay(ep, if (ep.resumeMs > 30_000) ep.resumeMs else 0L) }
                }
            }
            if (children.isNotEmpty()) {
                item { Section("En esta colección", pad) {} }
                items(children.chunked(cols)) { row ->
                    Row(
                        Modifier.padding(horizontal = pad, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { c -> PosterCard(client, c, Modifier.width(if (isTv) 150.dp else 104.dp)) { onOpen(c) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(client: EmbyClient, ep: Item, pad: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad - 4.dp, vertical = 4.dp)
            .clip(shape)
            .focusRing(focused, shape)
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface2)) {
            val url = client.image(ep, "Primary", 300)
            if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val rt = ep.runtimeMin
            if (ep.resumeMs > 0 && rt != null && rt > 0) {
                LinearProgressIndicator(
                    progress = { (ep.resumeMs / (rt * 60_000f)).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = Amber, trackColor = Color(0x66000000),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text("${ep.episodeNo ?: ""}. ${ep.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rt = ep.runtimeMin
            if (rt != null) Text("$rt min${if (ep.played) "  ·  ✓ visto" else ""}", fontSize = 12.sp, color = TextMuted)
            if (ep.overview.isNotBlank()) Text(ep.overview, fontSize = 12.sp, color = Color(0xFFD0D0DC), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
