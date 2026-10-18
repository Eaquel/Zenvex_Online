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
import com.zenvex.online.ui.CharacterScreen
import com.zenvex.online.ui.ChatScreen
import com.zenvex.online.ui.CosmeticsScreen
import com.zenvex.online.ui.CraftScreen
import com.zenvex.online.ui.GameScreen
import com.zenvex.online.ui.InventoryScreen
import com.zenvex.online.ui.LoadingScreen
import com.zenvex.online.ui.LobbyScreen
import com.zenvex.online.ui.Pal
import com.zenvex.online.ui.PassScreen
import com.zenvex.online.ui.ProfileScreen
import com.zenvex.online.ui.QuestsScreen
import com.zenvex.online.ui.Screen
import com.zenvex.online.ui.SettingsScreen
import com.zenvex.online.ui.ShopScreen
import com.zenvex.online.ui.SkillsScreen
import com.zenvex.online.ui.SplashScreen
import com.zenvex.online.ui.TitleScreen
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
        Settings.load(this)
        GameSession.init(this)
        Audio.init(this)

        setContent {
            // Kısayol: adb shell am start -n com.zenvex.online/.MainActivity --es screen Game
            val start = intent?.getStringExtra("screen")?.let { n -> Screen.entries.firstOrNull { it.name == n } } ?: Screen.Splash
            var screen by remember { mutableStateOf(start) }
            var settingsFrom by remember { mutableStateOf(Screen.Lobby) }
            var epoch by remember { mutableIntStateOf(0) }
            val go: (Screen) -> Unit = {
                if (it == Screen.Settings) settingsFrom = screen
                if (it == Screen.Lobby) epoch++
                screen = it
            }
            LaunchedEffect(screen) { Audio.music(if (screen == Screen.Game) "music_game" else "music_lobby") }
            BackHandler(enabled = screen != Screen.Game && screen != Screen.Title && screen != Screen.Splash) {
                go(if (screen == Screen.Lobby) Screen.Title else if (screen == Screen.Settings) settingsFrom else Screen.Lobby)
            }
            Box(Modifier.fillMaxSize().background(Pal.Bg)) {
                val back = { go(Screen.Lobby) }
                val reset = {
                    GameSession.resetSave()
                    Toaster.show("İlerleme silindi.", false); go(Screen.Lobby)
                }
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }, label = "screen") { s ->
                    when (s) {
                        Screen.Splash -> SplashScreen { screen = Screen.Title }
                        Screen.Title -> TitleScreen(onPlay = { go(Screen.Lobby) }, onSettings = { go(Screen.Settings) })
                        Screen.Lobby -> LobbyScreen(go, epoch)
                        Screen.Character -> CharacterScreen(back)
                        Screen.Skills -> SkillsScreen(back)
                        Screen.Inventory -> InventoryScreen(back)
                        Screen.Craft -> CraftScreen(back)
                        Screen.Shop -> ShopScreen(back)
                        Screen.Quests -> QuestsScreen(back)
                        Screen.Pass -> PassScreen(back)
                        Screen.Cosmetics -> CosmeticsScreen(back)
                        Screen.Profile -> ProfileScreen(back, reset)
                        Screen.Chat -> ChatScreen(back)
                        Screen.Settings -> SettingsScreen(back = { go(settingsFrom) }, onReset = reset)
                        Screen.Loading -> LoadingScreen { screen = Screen.Game }
                        Screen.Game -> GameScreen(toLobby = { go(Screen.Lobby) })
                    }
                }
                ToastHost()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        GameSession.save()
        Audio.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
        Audio.resumeMusic()
    }
}
