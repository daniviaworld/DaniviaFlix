package com.daniviaworld.daniviaflix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

private val IconMovie = icon(
    "movie",
    "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z",
)
private val IconSeries = icon(
    "series",
    "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,14.5v-9l6,4.5 -6,4.5z",
)
private val IconTv = icon(
    "tv",
    "M21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z",
)

private data class HubItem(
    val title: String,
    val sub: String,
    val icon: ImageVector,
    val c1: Color,
    val c2: Color,
    val images: List<String>,
    val logos: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun HubCard(item: HubItem, isTv: Boolean, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    val pw = if (isTv) 110.dp else 62.dp
    val ph = if (item.logos) pw * 0.7f else pw * 1.5f
    val step = pw * 0.5f
    Box(
        modifier
            .scale(if (focused) 1.05f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = item.onClick)
            .clip(shape)
            .focusRing(focused, shape)
            .background(Brush.linearGradient(listOf(item.c1, item.c2)))
    ) {
        // Tus carátulas (o logos de canales) en abanico.
        if (item.images.isNotEmpty()) {
            val urls = item.images.take(3)
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp)
                    .size(width = pw + step * 2, height = ph + 20.dp)
            ) {
                // orden de dibujo: izquierda, derecha y, encima, la del centro
                val order = listOf(0, 2, 1).filter { it < urls.size }
                order.forEach { i ->
                    val tilt = when (i) { 0 -> -8f; 2 -> 8f; else -> 0f }
                    val dy = if (i == 1) 0.dp else 16.dp
                    Box(
                        Modifier.align(Alignment.TopStart).offset(x = step * i, y = dy).rotate(tilt)
                            .size(pw, ph).clip(RoundedCornerShape(10.dp))
                            .background(if (item.logos) Color(0xF2FFFFFF) else Surface2)
                    ) {
                        AsyncImage(
                            model = urls[i], contentDescription = null,
                            contentScale = if (item.logos) ContentScale.Fit else ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(if (item.logos) 6.dp else 0.dp),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )
        Box(
            Modifier.align(Alignment.TopStart).padding(16.dp).size(if (isTv) 52.dp else 44.dp)
                .clip(CircleShape).background(Color(0x33000000)),
            contentAlignment = Alignment.Center,
        ) { Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (isTv) 28.dp else 24.dp)) }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(item.title, fontSize = if (isTv) 34.sp else 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            if (item.sub.isNotEmpty()) Text(item.sub, fontSize = if (isTv) 15.sp else 13.sp, color = Color(0xE6FFFFFF))
        }
    }
}

@Composable
private fun AccountChip(name: String, initial: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .clip(shape)
            .focusRing(focused, shape)
            .background(Color(0x1FFFFFFF))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Amber, Coral))),
            contentAlignment = Alignment.Center,
        ) { Text(initial, fontWeight = FontWeight.Bold, color = Color(0xFF1A1000)) }
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
    }
}

/** Pantalla de inicio: Películas, Series y TV (solo si esta cuenta tiene canales). */
@Composable
fun HubScreen(
    client: EmbyClient,
    account: Account?,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onTv: () -> Unit,
    onAccounts: () -> Unit,
    onSettings: () -> Unit,
    onAuthError: () -> Unit,
) {
    val ctx = LocalContext.current
    val isTv = rememberIsTv()
    val pad = if (isTv) 56.dp else 20.dp
    var views by remember { mutableStateOf<List<Item>?>(null) }
    var tvInfo by remember { mutableStateOf<Pair<Int, List<Channel>>?>(null) }
    var moviePosters by remember { mutableStateOf<List<String>>(emptyList()) }
    var seriesPosters by remember { mutableStateOf<List<String>>(emptyList()) }
    var movieCount by remember { mutableStateOf<Int?>(null) }
    var seriesCount by remember { mutableStateOf<Int?>(null) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) { update = Updater.check(ctx) }

    LaunchedEffect(Unit) {
        var authFailed = false
        coroutineScope {
            launch { tvInfo = client.channelPreview() }
            launch {
                try {
                    val v = client.views()
                    views = v
                    val mv = v.filter { it.collectionType == "movies" }
                    val sv = v.filter { it.collectionType == "tvshows" }
                    launch {
                        try {
                            movieCount = mv.sumOf { client.countIn(it) }
                            moviePosters = mv.firstOrNull()?.let { client.sample(it) }.orEmpty().mapNotNull { client.poster(it, 300) }
                        } catch (e: Exception) {
                            // sin carátulas
                        }
                    }
                    launch {
                        try {
                            seriesCount = sv.sumOf { client.countIn(it) }
                            seriesPosters = sv.firstOrNull()?.let { client.sample(it) }.orEmpty().mapNotNull { client.poster(it, 300) }
                        } catch (e: Exception) {
                            // sin carátulas
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("401") == true) authFailed = true
                    views = emptyList()
                }
            }
        }
        if (authFailed) onAuthError()
    }

    val v = views
    val tv = tvInfo
    val ready = v != null && tv != null
    val hasMovies = v?.any { it.collectionType == "movies" } == true
    val hasSeries = v?.any { it.collectionType == "tvshows" } == true
    val hasTv = (tv?.first ?: 0) > 0
    val none = !hasMovies && !hasSeries && !hasTv

    val items = buildList {
        if (ready && (hasMovies || none)) {
            add(
                HubItem(
                    "Películas", movieCount?.let { "$it títulos" } ?: "", IconMovie,
                    Color(0xFFFFB547), Color(0xFFFF4D6D), moviePosters, false, onMovies,
                )
            )
        }
        if (ready && (hasSeries || none)) {
            add(
                HubItem(
                    "Series", seriesCount?.let { "$it series" } ?: "", IconSeries,
                    Color(0xFF7B2FF7), Color(0xFF3B82F6), seriesPosters, false, onSeries,
                )
            )
        }
        if (ready && hasTv) {
            add(
                HubItem(
                    "TV", "${tv?.first ?: 0} canales en directo", IconTv,
                    Color(0xFF0F766E), Color(0xFF22D3EE), tv?.second.orEmpty().mapNotNull { client.logo(it, 120) }, true, onTv,
                )
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Fondo: luces de color y el nombre enorme, muy suave.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Violet.copy(alpha = 0.40f), Color.Transparent), center = Offset(0f, 0f), radius = 1500f)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Coral.copy(alpha = 0.25f), Color.Transparent), center = Offset(2200f, 2400f), radius = 1600f)
            )
        )
        Text(
            "DANIVIA\nFLIX",
            modifier = Modifier.align(Alignment.Center),
            fontSize = if (isTv) 170.sp else 84.sp,
            lineHeight = if (isTv) 160.sp else 80.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.05f),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DMark(if (isTv) 40.dp else 32.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Wordmark(if (isTv) 22.sp else 14.sp) }
                if (account != null) AccountChip(account.userName, account.initial, onAccounts)
                Spacer(Modifier.width(8.dp))
                NavIcon(Icons.Filled.Settings, "Ajustes") { onSettings() }
            }

            if (!ready) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
            } else if (isTv) {
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().height(380.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) { items.forEach { HubCard(it, true, Modifier.weight(1f).fillMaxHeight()) } }
                Spacer(Modifier.weight(1f))
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { items.forEach { HubCard(it, false, Modifier.fillMaxWidth().height(170.dp)) } }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Text("By ", color = TextMuted, fontSize = if (isTv) 16.sp else 13.sp)
                Text("DaniviaTV", color = Amber, fontWeight = FontWeight.Bold, fontSize = if (isTv) 16.sp else 13.sp)
            }
        }
    }
    update?.let { u -> UpdateDialog(u) { update = null } }
}

/** Elegir entre tus cuentas, añadir otra o quitar una. */
@Composable
fun AccountsScreen(
    accounts: List<Account>,
    activeIdx: Int,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Cuentas", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(accounts.size) { i ->
                val a = accounts[i]
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onPick(i) }
                        .clip(shape)
                        .focusRing(focused, shape)
                        .background(if (i == activeIdx) Color(0x33FFB547) else Color(0x14FFFFFF))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Amber, Coral))),
                        contentAlignment = Alignment.Center,
                    ) { Text(a.initial, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1A1000)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.userName, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(a.server, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (i == activeIdx) Text("Activa", color = Amber, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                    TextButton(onClick = { onRemove(i) }) { Text("Quitar", color = TextMuted) }
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
            Text("Añadir cuenta", fontWeight = FontWeight.Bold)
        }
    }
}
