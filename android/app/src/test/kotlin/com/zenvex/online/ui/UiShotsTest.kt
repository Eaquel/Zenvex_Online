package com.zenvex.online.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Arayüz ekran görüntüleri (emülatörsüz). Örnek veriler Rust'tan üretilir:
 * ZX_DUMP_UI=android/app/src/test/resources/ui cargo test dump_ui
 * Çalıştırma: ./gradlew :app:recordPaparazziDebug -x cargoBuild → app/src/test/snapshots/images
 */
class UiShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    private fun sample(name: String) = JSONObject(javaClass.getResource("/ui/$name.json")!!.readText())

    @Test
    fun buttonsAndToasts() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF2A3020)).padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    PxButton("OYNA", color = Px.Green, size = 22.sp, height = 56.dp) {}
                    PxButton("Satın Al", color = Px.Blue) {}
                    PxButton("Sil", color = Px.Red) {}
                    PxButton("Kilitli", enabled = false) {}
                    PxButton("Harita", icon = "map", color = Px.Dark) {}
                    PxIcon("mail", badge = 3) {}
                }
                ToastPlate("Doktor yaralarını sardı (-20 altın)", Px.Clan, "coin")
                ToastPlate("Yeni dostun seninle! Köpek seni takip edecek.", Px.Name, "check")
                ToastPlate("30 altın gerekli", androidx.compose.ui.graphics.Color(0xFFE0483C), "close")
            }
        }
    }

    @Test
    fun skillTree() {
        val data = sample("tree")
        paparazzi.snapshot { TreeView(data) {} }
    }
}
