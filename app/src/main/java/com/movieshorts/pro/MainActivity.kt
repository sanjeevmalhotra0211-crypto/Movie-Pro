package com.movieshorts.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val BG = Color(0xFF05080F)
val CardC = Color(0xFF111A2E)
val Blue = Color(0xFF1E90FF)
val Cyan = Color(0xFF22D3EE)
val Muted = Color(0xFF8FA3C8)

sealed class Screen {
    object Splash : Screen()
    object Auth : Screen()
    object Home : Screen()
    object Projects : Screen()
    object Wallet : Screen()
    object Settings : Screen()
    data class Detail(val pid: Long) : Screen()
    data class Editor(val pid: Long, val no: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = BG, surface = CardC, onPrimary = Color.White)) {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Splash)) }
    var tick by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { tick++ }
    val go = { s: Screen -> stack = stack + s }
    val back = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1 && stack.last() != Screen.Splash) { back() }
    Surface(Modifier.fillMaxSize(), color = BG) {
        when (val s = stack.last()) {
            Screen.Splash -> SplashScreen { stack = listOf(if (store.token.isNotEmpty()) Screen.Home else Screen.Auth) }
            Screen.Auth -> AuthScreen(store) { stack = listOf(Screen.Home) }
            Screen.Home -> HomeScreen(store, tick, go)
            Screen.Projects -> ProjectsScreen(store, tick, bump, { go(Screen.Detail(it)) }, { back() })
            is Screen.Detail -> DetailScreen(store, s.pid, tick, bump, { go(Screen.Editor(s.pid, it)) }, { back() })
            is Screen.Editor -> EditorScreen(store, s.pid, s.no, bump) { back() }
            Screen.Wallet -> WalletScreen(store) { back() }
            Screen.Settings -> SettingsScreen(store, { back() }) { store.logout(); stack = listOf(Screen.Auth) }
        }
    }
}
