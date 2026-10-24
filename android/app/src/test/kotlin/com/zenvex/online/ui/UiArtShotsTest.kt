package com.zenvex.online.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** Arayüz görselleri: sahne arka planları, amblem, metal çerçeveli pencere, bakım ekranı, HUD tuşları. */
class UiArtShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    @Test
    fun maintenance() {
        paparazzi.snapshot { MaintenanceScreen("Yeni Bölgeler Ekleniyor. Yarım Saat İçinde Dönüyoruz!") }
    }

    @Test
    fun lobbyArtAndDialog() {
        paparazzi.snapshot {
            SceneBackground("bg_lobby", 0f, dim = 0.3f, zoom = 1.75f, ax = 0.97f, ay = 0.55f, drift = 0.03f) {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameLogo(62.dp)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    for (b in listOf("btn_attack", "btn_roll", "btn_chat", "btn_drive", "btn_drive2", "btn_swap", "btn_hand"))
                        ArtImage(b, Modifier.padding(4.dp).size(72.dp))
                }
            }
        }
    }

    @Test
    fun metalDialog() {
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { Box(Modifier.fillMaxSize()) { NewsDialog {} } } }
    }

    @Test
    fun rulesDialog() {
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { Box(Modifier.fillMaxSize()) { NewsDialog(4) {} } } }
    }

    @Test
    fun craftBackground() {
        paparazzi.snapshot { ScreenFrame("ÜRETİM", "craft", {}, bg = "bg_craft") { PT("Tarifler", 20.sp, Px.Text) } }
    }
}
