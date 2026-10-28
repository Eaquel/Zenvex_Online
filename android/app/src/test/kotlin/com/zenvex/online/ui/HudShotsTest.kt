package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/**
 * Oyun içi Compose HUD'unun (kısayollar, mağaza/posta/menü, altın+elmas, etkinlik afişi, cepler, sohbet çubuğu, bildirim)
 * küçük telefon ekranında çakışma denetimi: 1600×720 piksel, 320 dpi → 800×360 dp (en dar yaygın yatay ekran).
 */
class HudShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1600, screenHeight = 720, xdpi = 320, ydpi = 320, density = Density.XHIGH,
            orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    @Test fun hudSmallPhone() {
        org.junit.Assume.assumeTrue("libzenvex.so yok", runCatching { com.zenvex.online.game.Native.hashCode(); true }.getOrDefault(false))
        Demo.on = true
        com.zenvex.online.game.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.game.Account.previewLogin(1234567L)
        com.zenvex.online.game.GameSession.cmd("cheat", "text" to "level 12")
        com.zenvex.online.game.GameSession.cmd("cheat", "text" to "gold 1234567")
        com.zenvex.online.game.GameSession.cmd("cheat", "text" to "gems 98765")
        Toaster.show("Doktor yaralarını sardı (-20 altın)")
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Color(0xFF2E3A26))) {
                Hud {}
                ToastHost()
            }
        }
    }

    /** Klan deposundan gönder penceresi: alanlar ve Gönder tuşu küçük ekranda görünmeli */
    @Test fun clanSendSmallPhone() {
        val c = org.json.JSONObject().put("members", org.json.JSONArray((1..9).map { org.json.JSONObject().put("id", it).put("name", "Üye $it") }))
        val item = org.json.JSONObject().put("id", "wood").put("name", "Odun").put("n", 120)
        paparazzi.snapshot { DepotSend(c, item, {}, {}) }
    }
}
