package com.daniviaworld.daniviaflix

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si la app se cierra por un fallo, guarda el motivo para enseñarlo al volver a abrirla.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                getSharedPreferences("danivia", Context.MODE_PRIVATE).edit()
                    .putString("lastCrash", android.util.Log.getStackTraceString(e).take(2500)).commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
        setContent {
            DaniviaTheme {
                // Sin esta superficie el texto salía negro sobre el fondo oscuro.
                Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("danivia", Context.MODE_PRIVATE) }
    val client = remember { EmbyClient(ctx) }

    var phase by remember { mutableStateOf("splash") } // splash | login | main
    var splashDone by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf<Boolean?>(null) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var crash by remember { mutableStateOf(prefs.getString("lastCrash", null)) }

    // Recupera la sesión guardada (si el servidor responde 401 se pide el login de nuevo).
    LaunchedEffect(Unit) {
        val token = prefs.getString("token", null)
        if (token == null) {
            restored = false
        } else {
            client.session = Session(
                prefs.getString("server", "") ?: "",
                prefs.getString("userId", "") ?: "",
                token,
                prefs.getString("user", "") ?: "",
            )
            restored = try {
                client.views()
                true
            } catch (e: Exception) {
                e.message?.contains("401") != true
            }
        }
    }
    LaunchedEffect(splashDone, restored) {
        val r = restored
        if (splashDone && r != null) phase = if (r) "main" else "login"
    }

    val open: (Item) -> Unit = { i ->
        if (i.type == "Folder" || i.type == "CollectionFolder" || i.type == "UserView") {
            stack.add(Screen.Library(i))
        } else {
            stack.add(Screen.Detail(i))
        }
    }
    val logout: () -> Unit = {
        // Se conservan la dirección del servidor y los ajustes; solo se borra la sesión.
        prefs.edit().remove("token").remove("userId").remove("user").apply()
        client.session = null
        stack.clear()
        stack.add(Screen.Home)
        phase = "login"
    }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    when (phase) {
        "splash" -> Splash { splashDone = true }
        "login" -> LoginScreen(client, prefs.getString("server", "") ?: "") { s ->
            prefs.edit()
                .putString("server", s.server).putString("userId", s.userId)
                .putString("token", s.token).putString("user", s.userName)
                .apply()
            stack.clear()
            stack.add(Screen.Home)
            phase = "main"
        }
        else -> {
            BackHandler(enabled = stack.size > 1) { pop() }
            val current = stack.last()
            key(current) {
                when (current) {
                    is Screen.Home -> HomeScreen(
                        client,
                        onOpen = open,
                        onPlay = { i, ms -> stack.add(Screen.Player(i, ms)) },
                        onGroup = { g -> stack.add(g) },
                        onSearch = { stack.add(Screen.Search) },
                        onSettings = { stack.add(Screen.Settings) },
                    )
                    is Screen.Settings -> SettingsScreen(client, onBack = pop, onLogout = logout)
                    is Screen.Search -> SearchScreen(client, onBack = pop, onOpen = open)
                    is Screen.Library -> LibraryScreen(client, current.view, onBack = pop, onOpen = open, onGroup = { g -> stack.add(g) })
                    is Screen.Group -> GroupScreen(client, current, onBack = pop, onOpen = open)
                    is Screen.Detail -> DetailScreen(
                        client, current.item, onBack = pop,
                        onPlay = { i, ms -> stack.add(Screen.Player(i, ms)) },
                        onOpen = open,
                    )
                    is Screen.Player -> PlayerScreen(
                        client, current.item, current.startMs, onExit = pop,
                        onNext = { n ->
                            stack.removeAt(stack.lastIndex)
                            stack.add(Screen.Player(n, 0L))
                        },
                    )
                }
            }
        }
    }

    crash?.let { text ->
        AlertDialog(
            onDismissRequest = { },
            containerColor = Surface2,
            title = { Text("La app se cerró por un fallo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Haz una captura de este mensaje y envíasela a Claude:", fontSize = 13.sp)
                    Text(text, fontSize = 10.sp, color = TextMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().remove("lastCrash").apply()
                    crash = null
                }) { Text("Cerrar") }
            },
        )
    }
}
