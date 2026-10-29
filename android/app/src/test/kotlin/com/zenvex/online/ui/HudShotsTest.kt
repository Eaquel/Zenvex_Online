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

    /** Öğretici: çanta tuşunda dokun eli (yerleşimi kaydırmadan) ve en üst katmandaki konuşma balonu */
    @Test fun hudTutorialBag() = tutorialShot("g4", "Alttaki parlayan çanta tuşuna dokun, üstteki Üret düğmesine bas ve Şifa sekmesinden Bandaj'ı üret.")

    /** Öğretici: yetenek tuşu işaretliyken sol üst tuşlar arası boşluk değişmemeli */
    @Test fun hudTutorialSkills() = tutorialShot("g6e", "Sol üstteki parlayan mor Yetenekler tuşuna dokun: ağaç açılır, düğümleri elmasla açarsın.")

    private fun tutorialShot(step: String, msg: String) {
        org.junit.Assume.assumeTrue("libzenvex.so yok", runCatching { com.zenvex.online.game.Native.hashCode(); true }.getOrDefault(false))
        Demo.on = true
        com.zenvex.online.game.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.game.Account.previewLogin(1234567L)
        com.zenvex.online.game.GameSession.cmd("cheat", "text" to "guide $step")
        com.zenvex.online.game.HudLayout.tutMsg = msg
        com.zenvex.online.game.HudLayout.tutAlpha = 1f
        com.zenvex.online.game.HudLayout.tutTop = 0.14f
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Color(0xFF2E3A26))) { Hud {} }
        }
        com.zenvex.online.game.HudLayout.tutMsg = ""
    }

    /** Hesap penceresi (yeni hesap / giriş): küçük yatay telefonda düğmeler ekranda kalmalı */
    @Test fun accountNewSmallPhone() { paparazzi.snapshot { AccountDialog(false) {} } }

    /** Klan deposundan gönder penceresi: alanlar ve Gönder tuşu küçük ekranda görünmeli */
    @Test fun clanSendSmallPhone() {
        val c = org.json.JSONObject().put("members", org.json.JSONArray((1..9).map { org.json.JSONObject().put("id", it).put("name", "Üye $it") }))
        val item = org.json.JSONObject().put("id", "wood").put("name", "Odun").put("n", 120)
        paparazzi.snapshot { DepotSend(c, item, {}, {}) }
    }

    /** Ayarlar: her sekme küçük ekranda kaydırmadan tek alanda sığmalı */
    private fun settingsTab(i: Int) {
        com.zenvex.online.game.Legal.init(paparazzi.context)
        Demo.on = true
        com.zenvex.online.game.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.game.Account.previewLogin(1234567L)
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
            }
        }
        paparazzi.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) { SettingsScreen({}, initialTab = i) }
        }
    }
    @Test fun settingsGraphics() = settingsTab(0)
    @Test fun settingsControls() = settingsTab(1)
    @Test fun settingsSound() = settingsTab(2)
    @Test fun settingsNotif() = settingsTab(3)
    @Test fun settingsAccount() = settingsTab(4)
    @Test fun settingsSupport() = settingsTab(5)
    @Test fun settingsLanguage() = settingsTab(6)
    @Test fun settingsLegal() = settingsTab(7)

    // ------------------------------------------------------------------ yasal pencereler (küçük yatay telefon)
    private fun legal() {
        com.zenvex.online.game.Legal.init(paparazzi.context)
        com.zenvex.online.game.Legal.noticeSeen.value = true
        com.zenvex.online.game.Legal.termsNeeded.value = false
        com.zenvex.online.game.Store.confirm.value = null
        com.zenvex.online.game.Legal.open.value = null
    }
    private fun bg(content: @androidx.compose.runtime.Composable () -> Unit) =
        paparazzi.snapshot { Box(Modifier.fillMaxSize().background(Color(0xFF2E3A26))) { content() } }

    /** İlk açılış KVKK aydınlatması */
    @Test fun legalNotice() { legal(); com.zenvex.online.game.Legal.noticeSeen.value = false; bg { LegalHost() } }

    /** Güncellenen Kullanım Şartları'nın yeniden onayı */
    @Test fun legalTermsUpdate() {
        legal(); com.zenvex.online.game.Account.previewLogin(1234567L); com.zenvex.online.game.Legal.termsNeeded.value = true
        bg { LegalHost() }
    }

    /** Satın alma öncesi bilgilendirme ve onay (aylık kart) */
    @Test fun legalPurchaseConfirm() {
        legal()
        com.zenvex.online.game.Store.state.value = org.json.JSONObject(javaClass.getResource("/ui/store.json")!!.readText())
        com.zenvex.online.game.Store.confirm.value = "card_month"
        bg { LegalHost() }
    }

    /** Tam ekran yasal metin okuyucu */
    @Test fun legalReader() { legal(); com.zenvex.online.game.Legal.open.value = "presale"; bg { LegalHost() } }

    /** Profil fotoğrafı yükleme bildirimi */
    @Test fun legalPhotoNotice() { legal(); bg { PhotoNoticeDialog({}) {} } }

    /** Profilden şikâyet (hukuka aykırı içerik / telif) */
    @Test fun legalReport() { legal(); bg { ReportDialog(1000002L, "Veli") {} } }
}
