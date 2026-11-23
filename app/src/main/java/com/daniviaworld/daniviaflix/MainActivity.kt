package com.daniviaworld.daniviaflix

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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
        setContent {
            DaniviaTheme { App() }
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
                        onLogout = {
                            val server = prefs.getString("server", "") ?: ""
                            prefs.edit().clear().putString("server", server).apply()
                            client.session = null
                            stack.clear()
                            stack.add(Screen.Home)
                            phase = "login"
                        },
                    )
                    is Screen.Library -> LibraryScreen(client, current.view, onBack = pop, onOpen = open)
                    is Screen.Detail -> DetailScreen(
                        client, current.item, onBack = pop,
                        onPlay = { i, ms -> stack.add(Screen.Player(i, ms)) },
                        onOpen = open,
                    )
                    is Screen.Player -> PlayerScreen(client, current.item, current.startMs, onExit = pop)
                }
            }
        }
    }
}
