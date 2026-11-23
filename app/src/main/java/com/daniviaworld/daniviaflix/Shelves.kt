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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
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

/** Tarjeta de biblioteca: tres carátulas tuyas en abanico, con nombre y número de títulos. */
@Composable
fun LibraryStack(client: EmbyClient, view: Item, index: Int, width: Dp, height: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var posters by remember(view.id) { mutableStateOf<List<Item>>(emptyList()) }
    var count by remember(view.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(view.id) {
        try {
            posters = client.sample(view)
        } catch (e: Exception) {
            // sin carátulas: se queda el degradado
        }
        try {
            count = client.countIn(view)
        } catch (e: Exception) {
            // sin contador
        }
    }
    val (c1, c2) = stackColors[index % stackColors.size]
    val shape = RoundedCornerShape(20.dp)
    val pw = width * 0.31f
    val unit = when (view.collectionType) {
        "movies" -> "películas"
        "tvshows" -> "series"
        "boxsets" -> "colecciones"
        else -> "elementos"
    }
    Box(
        Modifier
            .width(width)
            .height(height)
            .scale(if (focused) 1.05f else 1f)
            .clip(shape)
            .focusRing(focused, shape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
    ) {
        posters.getOrNull(1)?.let {
            MiniPoster(client, it, pw, Modifier.align(Alignment.TopStart).offset(x = width * 0.08f, y = height * 0.13f).rotate(-12f))
        }
        posters.getOrNull(2)?.let {
            MiniPoster(client, it, pw, Modifier.align(Alignment.TopStart).offset(x = width * 0.61f, y = height * 0.13f).rotate(12f))
        }
        posters.getOrNull(0)?.let {
            MiniPoster(client, it, pw, Modifier.align(Alignment.TopStart).offset(x = width * 0.345f, y = height * 0.05f))
        }
        Box(
            Modifier.fillMaxWidth().height(height * 0.4f).align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA6000000))))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(view.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            count?.let { Text("$it $unit", fontSize = 12.sp, color = Color(0xFFE6E6F0)) }
        }
    }
}

data class Mood(val label: String, val sub: String, val kind: String, val icon: ImageVector, val c1: Color, val c2: Color)

val moods = listOf(
    Mood("Tengo 90 min", "Pelis cortas", "short", Icons.Filled.DateRange, Amber, Coral),
    Mood("Noche de peli", "Largas y épicas", "epic", Icons.Filled.Star, Violet, Coral),
    Mood("Para los peques", "Toda la familia", "kids", Icons.Filled.Face, Color(0xFF34D399), Color(0xFF3B82F6)),
    Mood("Maratón", "Series enteras", "marathon", Icons.Filled.PlayArrow, Color(0xFF22D3EE), Violet),
)

@Composable
private fun MoodCard(m: Mood, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(68.dp)
            .scale(if (focused) 1.04f else 1f)
            .clip(shape)
            .focusRing(focused, shape)
            .background(Color(0x0FFFFFFF))
            .onFocusChanged { focused = it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(m.c1, m.c2))),
            contentAlignment = Alignment.Center,
        ) { Icon(m.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(m.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(m.sub, fontSize = 11.sp, color = TextMuted, maxLines = 1)
        }
    }
}

/** "¿Qué te apetece?": en vez de filas repetidas, eliges según el momento. */
@Composable
fun MoodGrid(pad: Dp, onPick: (Mood) -> Unit) {
    Column(Modifier.padding(horizontal = pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        moods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> MoodCard(m, Modifier.weight(1f)) { onPick(m) } }
            }
        }
    }
}
