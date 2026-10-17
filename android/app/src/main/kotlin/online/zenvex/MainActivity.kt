package online.zenvex

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import online.zenvex.game.Audio
import online.zenvex.game.GameSession
import online.zenvex.game.Settings
import online.zenvex.ui.CharacterScreen
import online.zenvex.ui.GameScreen
import online.zenvex.ui.InventoryScreen
import online.zenvex.ui.LoadingScreen
import online.zenvex.ui.LobbyScreen
import online.zenvex.ui.Pal
import online.zenvex.ui.ProfileScreen
import online.zenvex.ui.Screen
import online.zenvex.ui.ServersScreen
import online.zenvex.ui.SettingsScreen
import online.zenvex.ui.ShopScreen
import online.zenvex.ui.SkillsScreen
import online.zenvex.ui.SplashScreen
import online.zenvex.ui.TitleScreen
import online.zenvex.ui.ToastHost
import online.zenvex.ui.Toaster

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
            // Test/kısayol: adb shell am start -n online.zenvex/.MainActivity --es screen Game
            val start = intent?.getStringExtra("screen")?.let { n -> Screen.entries.firstOrNull { it.name == n } } ?: Screen.Splash
            var screen by remember { mutableStateOf(start) }
            var settingsFrom by remember { mutableStateOf(Screen.Lobby) }
            // ekranlar yeniden okunsun diye (ör. ilerleme silinince)
            var epoch by remember { mutableStateOf(0) }
            val go: (Screen) -> Unit = {
                if (it == Screen.Settings) settingsFrom = screen
                screen = it
            }
            BackHandler(enabled = screen != Screen.Game && screen != Screen.Title && screen != Screen.Splash) {
                screen = when (screen) {
                    Screen.Lobby -> Screen.Title
                    Screen.Settings -> settingsFrom
                    else -> Screen.Lobby
                }
            }
            Box(Modifier.fillMaxSize().background(Pal.NavyDark)) {
                val back = { screen = Screen.Lobby }
                val reset = {
                    GameSession.resetSave(); epoch++
                    Toaster.show("İlerleme silindi.", "close"); screen = Screen.Lobby
                }
                androidx.compose.runtime.key(epoch) {
                    when (screen) {
                        Screen.Splash -> SplashScreen { screen = Screen.Title }
                        Screen.Title -> TitleScreen(onGuest = { screen = Screen.Lobby }, onSettings = { go(Screen.Settings) })
                        Screen.Lobby -> LobbyScreen(go)
                        Screen.Character -> CharacterScreen(back)
                        Screen.Skills -> SkillsScreen(back)
                        Screen.Inventory -> InventoryScreen(back)
                        Screen.Shop -> ShopScreen(back)
                        Screen.Profile -> ProfileScreen(back, reset)
                        Screen.Servers -> ServersScreen(back)
                        Screen.Settings -> SettingsScreen(back = { screen = settingsFrom }, onReset = reset)
                        Screen.Loading -> LoadingScreen { screen = Screen.Game }
                        Screen.Game -> GameScreen(toLobby = { screen = Screen.Lobby })
                    }
                }
                ToastHost()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        GameSession.save()
    }
}
