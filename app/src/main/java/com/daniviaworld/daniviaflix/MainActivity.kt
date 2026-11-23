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
            } catch (ignored: Throwable) {
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
    var adding by remember { mutableStateOf(false) } // true = login para añadir otra cuenta
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var activeIdx by remember { mutableStateOf(0) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Hub) }
    var crash by remember { mutableStateOf(prefs.getString("lastCrash", null)) }

    // Recupera las cuentas guardadas y entra directo con la activa (sin esperar a la red).
    LaunchedEffect(Unit) {
        accounts = Accounts.list(ctx)
        activeIdx = Accounts.activeIndex(ctx)
        val a = accounts.getOrNull(activeIdx)
        if (a == null) {
            restored = false
        } else {
            client.session = a.toSession()
            restored = true
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

    // Pasa a otra cuenta y vuelve a la pantalla de inicio.
    val activate: (Int) -> Unit = { i ->
        Accounts.setActive(ctx, i)
        accounts = Accounts.list(ctx)
        activeIdx = Accounts.activeIndex(ctx)
        accounts.getOrNull(activeIdx)?.let { client.session = it.toSession() }
        stack.clear()
        stack.add(Screen.Hub)
    }

    // Quita una cuenta. Si era la activa pasa a otra; si no queda ninguna, pide el login.
    val removeAccount: (Int) -> Unit = { i ->
        val wasActive = i == activeIdx
        Accounts.remove(ctx, i)
        accounts = Accounts.list(ctx)
        activeIdx = Accounts.activeIndex(ctx)
        if (accounts.isEmpty()) {
            client.session = null
            stack.clear()
            stack.add(Screen.Hub)
            adding = false
            phase = "login"
        } else if (wasActive) {
            accounts.getOrNull(activeIdx)?.let { client.session = it.toSession() }
            stack.clear()
            stack.add(Screen.Hub)
        }
    }
    val logout: () -> Unit = { removeAccount(activeIdx) }

    when (phase) {
        "splash" -> Splash { splashDone = true }
        "login" -> LoginScreen(
            client, prefs.getString("server", "") ?: "",
            onCancel = if (adding) {
                {
                    // Vuelve a la cuenta que estaba activa.
                    accounts.getOrNull(activeIdx)?.let { client.session = it.toSession() }
                    adding = false
                    phase = "main"
                }
            } else null,
        ) { s ->
            activeIdx = Accounts.add(ctx, Account(s.server, s.userId, s.token, s.userName))
            accounts = Accounts.list(ctx)
            prefs.edit().putString("server", s.server).apply()
            stack.clear()
            stack.add(Screen.Hub)
            adding = false
            phase = "main"
        }
        else -> {
            BackHandler(enabled = stack.size > 1) { pop() }
            val current = stack.last()
            key(current) {
                when (current) {
                    is Screen.Hub -> HubScreen(
                        client,
                        account = accounts.getOrNull(activeIdx),
                        onMovies = { stack.add(Screen.Home("movies")) },
                        onSeries = { stack.add(Screen.Home("series")) },
                        onTv = { stack.add(Screen.Tv) },
                        onAccounts = { stack.add(Screen.Accounts) },
                        onSettings = { stack.add(Screen.Settings) },
                        onAuthError = logout,
                    )
                    is Screen.Home -> HomeScreen(
                        client,
                        current.mode,
                        onOpen = open,
                        onPlay = { i, ms -> stack.add(Screen.Player(i, ms)) },
                        onGroup = { g -> stack.add(g) },
                        onSearch = { stack.add(Screen.Search) },
                        onSettings = { stack.add(Screen.Settings) },
                        onAuthError = logout,
                    )
                    is Screen.Tv -> TvScreen(client, onExit = pop)
                    is Screen.Accounts -> AccountsScreen(
                        accounts, activeIdx, onBack = pop,
                        onPick = { i -> if (i == activeIdx) pop() else activate(i) },
                        onAdd = {
                            adding = true
                            phase = "login"
                        },
                        onRemove = { i -> removeAccount(i) },
                    )
                    is Screen.Settings -> SettingsScreen(
                        client, onBack = pop, onAccounts = { stack.add(Screen.Accounts) }, onLogout = logout,
                    )
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
