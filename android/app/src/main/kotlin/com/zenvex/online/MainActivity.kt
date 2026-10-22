package com.zenvex.online

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.zenvex.online.game.Audio
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Settings
import com.zenvex.online.game.Account
import com.zenvex.online.ui.GameScreen
import com.zenvex.online.ui.LoadingScreen
import com.zenvex.online.ui.LobbyScreen
import com.zenvex.online.ui.Pal
import com.zenvex.online.ui.Screen
import com.zenvex.online.ui.SettingsScreen
import com.zenvex.online.ui.SplashScreen
import com.zenvex.online.ui.ToastHost
import com.zenvex.online.ui.Toaster

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        com.zenvex.online.game.Integrity.check(this)
        Settings.load(this)
        Account.init(this)
        if (Account.loggedIn) runCatching { com.zenvex.online.game.Notifier.schedule(this) }
        GameSession.init(this)
        Audio.init(this)
        // otomatik gösterim yalnız hata ayıklama sürümünde (yayın sürümünde çevrimdışı oynamaya kapı açmasın)
        com.zenvex.online.ui.Demo.on = BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true

        setContent {
            // Kısayol (yalnız hata ayıklama sürümü): adb shell am start -n com.zenvex.online/.MainActivity --es screen Lobby
            val start = intent?.getStringExtra("screen")?.takeIf { BuildConfig.DEBUG }
                ?.let { n -> Screen.entries.firstOrNull { it.name == n && it != Screen.Game } } ?: Screen.Splash
            var screen by remember { mutableStateOf(start) }
            var settingsFrom by remember { mutableStateOf(Screen.Lobby) }
            var epoch by remember { mutableIntStateOf(0) }
            val go: (Screen) -> Unit = {
                if (it == Screen.Settings) settingsFrom = screen
                if (it == Screen.Lobby) epoch++
                screen = it
            }
            LaunchedEffect(screen) { Audio.music(if (screen == Screen.Game) "music_game" else "music_lobby") }
            BackHandler(enabled = screen == Screen.Settings) { go(settingsFrom) }
            Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                val reset = {
                    GameSession.resetSave()
                    Toaster.show("Bu sunucudaki ilerleme silindi.", false); go(Screen.Lobby)
                }
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }, label = "screen") { s ->
                    when (s) {
                        Screen.Splash -> SplashScreen { screen = Screen.Lobby }
                        Screen.Lobby -> LobbyScreen(go, epoch)
                        Screen.Settings -> SettingsScreen(back = { go(settingsFrom) }, onReset = reset)
                        Screen.Loading -> LoadingScreen(back = { go(Screen.Lobby) }) { screen = Screen.Game }
                        Screen.Game -> GameScreen(toLobby = { go(Screen.Lobby) })
                    }
                }
                com.zenvex.online.ui.GateHost()
                ToastHost()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        com.zenvex.online.game.Notifier.foreground = false
        GameSession.save(cloud = true)
        Audio.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
        com.zenvex.online.game.Notifier.foreground = true
        Audio.resumeMusic()
    }
}
