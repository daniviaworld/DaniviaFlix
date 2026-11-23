package com.daniviaworld.daniviaflix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val stackColors = listOf(
    Color(0xFF3A1C71) to Color(0xFFD76D77),
    Color(0xFF1F3A93) to Color(0xFF7B2FF7),
    Color(0xFFB45309) to Color(0xFFFF4D6D),
    Color(0xFF0F766E) to Color(0xFF3B82F6),
    Color(0xFF7B2FF7) to Color(0xFFFF4D6D),
)

@Composable
private fun MiniPoster(client: EmbyClient, item: Item, w: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.width(w).height(w * 1.5f).shadow(8.dp, shape).clip(shape).background(Surface2)) {
        val url = client.poster(item, 300)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Tarjeta de biblioteca: tu imagen (o 3 carátulas en abanico) y debajo el nombre y el número de títulos. */
@Composable
fun LibraryStack(client: EmbyClient, view: Item, index: Int, width: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var posters by remember(view.id) { mutableStateOf<List<Item>>(emptyList()) }
    var count by remember(view.id) { mutableStateOf<Int?>(null) }
    val ownImage = client.image(view, "Primary", 500)
    LaunchedEffect(view.id) {
        if (ownImage == null) {
            try {
                posters = client.sample(view)
            } catch (e: Exception) {
                // sin carátulas: se queda el degradado
            }
        }
        try {
            count = client.countIn(view)
        } catch (e: Exception) {
            // sin contador
        }
    }
    val (c1, c2) = stackColors[index % stackColors.size]
    val shape = RoundedCornerShape(18.dp)
    val boxH = width * 0.5625f
    val pw = boxH * 0.42f
    val unit = when (view.collectionType) {
        "movies" -> "películas"
        "tvshows" -> "series"
        "boxsets" -> "colecciones"
        else -> "elementos"
    }
    Column(
        Modifier
            .width(width)
            .scale(if (focused) 1.05f else 1f)
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(boxH)
                .clip(shape)
                .focusRing(focused, shape)
                .background(Brush.linearGradient(listOf(c1, c2)))
        ) {
            if (ownImage != null) {
                // Tu propia imagen de la biblioteca (por ejemplo el logo de Amazon): se respeta tal cual.
                AsyncImage(model = ownImage, contentDescription = view.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                posters.getOrNull(1)?.let {
                    MiniPoster(client, it, pw, Modifier.align(Alignment.TopStart).offset(x = width * 0.08f, y = boxH * 0.14f).rotate(-12f))
                }
                posters.getOrNull(2)?.let {
                    MiniPoster(client, it, pw, Modifier.align(Alignment.TopStart).offset(x = width * 0.92f - pw, y = boxH * 0.14f).rotate(12f))
                }
                posters.getOrNull(0)?.let {
                    MiniPoster(client, it, pw, Modifier.align(Alignment.TopStart).offset(x = (width - pw) / 2, y = boxH * 0.05f))
                }
            }
        }
        Text(
            view.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        count?.let { Text("$it $unit", fontSize = 12.sp, color = TextMuted) }
    }
}

data class Mood(val label: String, val sub: String, val kind: String, val icon: ImageVector, val c1: Color, val c2: Color)

val moods = listOf(
    Mood("Tengo 90 min", "Pelis cortas", "short", Icons.Filled.DateRange, Amber, Coral),
    Mood("Noche de peli", "Largas y épicas", "epic", Icons.Filled.Star, Violet, Coral),
    Mood("Para los peques", "Toda la familia", "kids", Icons.Filled.Face, Color(0xFF34D399), Color(0xFF3B82F6)),
    Mood("Maratón", "Series enteras", "marathon", Icons.Filled.PlayArrow, Color(0xFF22D3EE), Violet),
    Mood("Sorpréndeme", "Una peli al azar", "random", Icons.Filled.Refresh, Amber, Violet),
    Mood("Mis favoritos", "Lo que guardaste", "favorites", Icons.Filled.Favorite, Coral, Violet),
)

@Composable
private fun MoodCard(m: Mood, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 68.dp)
            .scale(if (focused) 1.04f else 1f)
            .clip(shape)
            .focusRing(focused, shape)
            .background(Color(0x0FFFFFFF))
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(m.c1, m.c2))),
            contentAlignment = Alignment.Center,
        ) { Icon(m.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(m.sub, fontSize = 11.sp, color = TextMuted, maxLines = 2)
        }
    }
}

/** "¿Qué te apetece?": en vez de filas repetidas, eliges según el momento. */
@Composable
fun MoodGrid(pad: Dp, list: List<Mood> = moods, onPick: (Mood) -> Unit) {
    Column(Modifier.padding(horizontal = pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> MoodCard(m, Modifier.weight(1f)) { onPick(m) } }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Botón redondo de la cabecera (buscar, favoritos, ajustes), pensado también para el mando de la TV. */
@Composable
fun NavIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(44.dp)
            .scale(if (focused) 1.12f else 1f)
            .clip(CircleShape)
            .focusRing(focused, CircleShape)
            .background(Color(0x1FFFFFFF))
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}
