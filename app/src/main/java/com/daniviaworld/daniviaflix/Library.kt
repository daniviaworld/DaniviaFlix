package com.daniviaworld.daniviaflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SortOpt(val label: String, val by: String, val order: String)

private val sortOptions = listOf(
    SortOpt("Nombre", "SortName", "Ascending"),
    SortOpt("Añadido", "DateCreated,SortName", "Descending"),
    SortOpt("Año", "ProductionYear,SortName", "Descending"),
    SortOpt("Valoración", "CommunityRating,SortName", "Descending"),
)

@Composable
private fun PosterGrid(client: EmbyClient, list: List<Item>, isTv: Boolean, onOpen: (Item) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 160.dp else 112.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(list, key = { it.id }) { i -> PosterCard(client, i, Modifier.fillMaxWidth()) { onOpen(i) } }
    }
}

@Composable
private fun GenreShelf(
    client: EmbyClient, view: Item, genre: String, pad: Dp, posterW: Dp,
    onOpen: (Item) -> Unit, onAll: () -> Unit,
) {
    var list by remember(view.id, genre) { mutableStateOf<List<Item>?>(null) }
    LaunchedEffect(view.id, genre) {
        list = try {
            client.byGenre(view, genre, 20, "CommunityRating,SortName", "Descending")
        } catch (e: Exception) {
            emptyList()
        }
    }
    val l = list
    if (l != null && l.isNotEmpty()) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = pad, end = pad - 8.dp, top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(Amber, Coral)))
                )
                Spacer(Modifier.width(8.dp))
                Text(genre, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = onAll) { Text("Ver todo ›", color = Amber, fontSize = 13.sp) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(l, key = { it.id }) { i -> PosterCard(client, i, Modifier.width(posterW)) { onOpen(i) } }
            }
        }
    }
}

/** Biblioteca: Películas y Series se muestran por categorías con tus carátulas; el resto, en cuadrícula. */
@Composable
fun LibraryScreen(
    client: EmbyClient,
    view: Item,
    onBack: () -> Unit,
    onOpen: (Item) -> Unit,
    onGroup: (Screen.Group) -> Unit,
) {
    val isTv = rememberIsTv()
    val pad = if (isTv) 48.dp else 16.dp
    val posterW = if (isTv) 150.dp else 120.dp
    val rich = view.collectionType == "movies" || view.collectionType == "tvshows"

    var mode by remember { mutableStateOf(0) } // 0 = categorías, 1 = todo
    var sortIdx by remember { mutableStateOf(0) }
    var all by remember { mutableStateOf<List<Item>>(emptyList()) }
    var latest by remember { mutableStateOf<List<Item>>(emptyList()) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var count by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(view.id) {
        if (!rich) return@LaunchedEffect
        try {
            latest = client.latestIn(view)
        } catch (e: Exception) {
            // la fila "Recién añadido" es opcional
        }
        try {
            genres = client.genres(view)
        } catch (e: Exception) {
            error = friendlyError(e)
        }
        try {
            count = client.countIn(view)
        } catch (e: Exception) {
            // sin contador
        }
        loading = false
    }
    val showGrid = !rich || mode == 1
    LaunchedEffect(view.id, sortIdx, showGrid) {
        if (!showGrid) return@LaunchedEffect
        loading = true
        error = null
        try {
            val s = sortOptions[sortIdx]
            all = client.library(view, s.by, s.order)
        } catch (e: Exception) {
            error = friendlyError(e)
        }
        loading = false
    }

    val ambient = rememberAmbient(latest.firstOrNull()?.let { client.poster(it, 300) })

    Column(
        Modifier.fillMaxSize().background(Bg)
            .background(Brush.verticalGradient(listOf(ambient, Bg), endY = 900f))
    ) {
        TopBar(view.name, onBack)
        if (rich) {
            count?.let {
                Text(
                    "$it ${if (view.collectionType == "movies") "películas" else "series"} · ${genres.size} categorías",
                    color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = pad, bottom = 8.dp),
                )
            }
            LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Categorías") }) }
                item { FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Todo") }) }
            }
        }
        if (showGrid) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = if (rich) 6.dp else 0.dp),
            ) {
                items(sortOptions.size) { idx ->
                    FilterChip(selected = idx == sortIdx, onClick = { sortIdx = idx }, label = { Text(sortOptions[idx].label) })
                }
            }
        }
        val err = error
        if (err != null) Text(err, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(16.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        } else if (showGrid) {
            PosterGrid(client, all, isTv, onOpen)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (latest.isNotEmpty()) item {
                    Section("Recién añadido", pad) {
                        LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(latest, key = { it.id }) { i -> PosterCard(client, i, Modifier.width(posterW)) { onOpen(i) } }
                        }
                    }
                }
                items(genres, key = { "g-$it" }) { g ->
                    GenreShelf(client, view, g, pad, posterW, onOpen) { onGroup(Screen.Group(g, view, "genre", g)) }
                }
            }
        }
    }
}

/** Pantalla de una categoría completa ("Ver todo") o de un momento ("Tengo 90 min"). */
@Composable
fun GroupScreen(client: EmbyClient, g: Screen.Group, onBack: () -> Unit, onOpen: (Item) -> Unit) {
    val isTv = rememberIsTv()
    var list by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(g) {
        try {
            val v = g.view
            list = if (g.kind == "genre" && v != null) client.byGenre(v, g.arg, 500) else client.mood(g.arg)
        } catch (e: Exception) {
            error = friendlyError(e)
        }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(g.title, onBack)
        val err = error
        if (err != null) Text(err, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(16.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        } else if (list.isEmpty() && err == null) {
            Text("No hay nada que mostrar aquí todavía.", color = TextMuted, modifier = Modifier.padding(16.dp))
        } else {
            PosterGrid(client, list, isTv, onOpen)
        }
    }
}
