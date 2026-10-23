package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import org.json.JSONObject
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

/**
 * Oyun ekranının kendisi (GameView) emülatörsüz: Rust çekirdeği bilgisayar için derlenir ve gerçek update/render yolu
 * bir bitmap'e çizilir. Oynanış turu gibi: kampta doğuş, savaş, şehir, Cephe Hattı, gece.
 * Ön koşul: (cd rust && cargo build -p zenvex_jni --release). Kütüphane yoksa test atlanır.
 * Çalıştırma: ./gradlew :app:recordPaparazziDebug -x cargoBuild --tests '*GameShotsTest*'
 */
class GameShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    private val ui = object : GameUi {
        override fun onDeath() {}
        override fun onOpen(panel: String) {}
        override fun onToast(msg: String) {}
        override fun onLevelUp(level: Int) {}
    }

    private fun native(): Boolean = runCatching { Native.hashCode(); true }.getOrDefault(false)

    private fun cheat(t: String) = synchronized(GameSession.lock) {
        Native.command(GameSession.handle, JSONObject().put("cmd", "cheat").put("text", t).toString())
    }

    private fun cmd(o: JSONObject) = synchronized(GameSession.lock) { Native.command(GameSession.handle, o.toString()) }

    private fun shot(name: String, setup: () -> Unit, steps: Int = 60, input: FloatArray? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        setup()
        val view = GameView(paparazzi.context, ui)
        view.localName = "Oyuncu"
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(Canvas(bmp), w, h, steps, input = input)
        val iv = ImageView(paparazzi.context).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_XY }
        paparazzi.snapshot(iv, name)
    }

    @Test fun g01_campSpawn() = shot("camp", { })

    @Test fun g02_forestFight() = shot("fight", {
        cheat("level 6"); cheat("tp deep"); cheat("spawn walker 4 2"); cheat("spawn runner 2 2")
    }, steps = 90, input = floatArrayOf(0f, 0f, 1f, 0f, 1f))

    @Test fun g03_city() = shot("city", { cheat("level 12"); cheat("tp city") })

    @Test fun g04_cityStreet() = shot("downtown", { cheat("level 25"); cheat("tp downtown"); cheat("spawn dark 3 3"); cheat("spawn tank 1 3") }, steps = 90)

    @Test fun g05_front() = shot("front_hq", { cheat("level 30"); cheat("tp front") })

    @Test fun g06_base() = shot("base", { cheat("level 40"); cheat("tp base 1") }, steps = 120)

    @Test fun g07_night() = shot("night", { cheat("level 8"); cheat("tp camp"); cheat("night") })

    @Test fun g08_dog() = shot("dog", { cheat("level 10"); cheat("dog 3") }, steps = 90, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))

    @Test fun g09_harbor() = shot("harbor", { cheat("level 45"); cheat("tp harbor"); cheat("spawn dark 3 3") }, steps = 90)

    @Test fun g10_vehicle() = shot("vehicle", { cheat("level 20"); cheat("tp desert"); cheat("vehicle 2") }, steps = 60)
}
