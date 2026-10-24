package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.zenvex.online.ui.Hud
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

    private fun shot(name: String, setup: () -> Unit, steps: Int = 60, input: FloatArray? = null, hud: Boolean = true,
                     after: ((GameView) -> Unit)? = null, overlay: (@androidx.compose.runtime.Composable () -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        setup()
        val view = GameView(paparazzi.context, ui)
        view.localName = "Oyuncu"
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(Canvas(bmp), w, h, steps, input = input)
        if (after != null) { after(view); view.previewFrames(Canvas(bmp), w, h, 2) }
        // tam çözünürlüklü kopya (incelemek için): app/build/game-shots/<ad>.png
        runCatching {
            val dir = java.io.File("build/game-shots").apply { mkdirs() }
            java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // oyun görüntüsü + Compose HUD (gerçek oyundaki gibi üst üste)
        val img = bmp.asImageBitmap()
        paparazzi.snapshot(name) {
            Box(Modifier.fillMaxSize()) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (hud) Hud {}
                overlay?.invoke()
            }
        }
    }

    @Test fun g01_campSpawn() = shot("camp", { })

    @Test fun g02_forestFight() = shot("fight", {
        cheat("level 6"); cheat("god"); cheat("tp deep"); cheat("spawn walker 4 4"); cheat("spawn runner 2 4")
    }, steps = 150, input = floatArrayOf(0f, 0f, 1f, 0f, 1f))

    @Test fun g11_title() = shot("title", {
        Account.profile.value = JSONObject().put("title", JSONObject().put("name", "Canavar Avcısı #1").put("rank", 1).put("kind", "kills"))
        cheat("level 15"); cheat("tp city")
    }, steps = 170)

    @Test fun g03_city() = shot("city", { cheat("level 12"); cheat("tp city") })

    @Test fun g04_cityStreet() = shot("downtown", { cheat("level 25"); cheat("tp downtown"); cheat("spawn dark 3 3"); cheat("spawn tank 1 3") }, steps = 90)

    @Test fun g05_front() = shot("front_hq", { cheat("level 30"); cheat("tp front") })

    @Test fun g06_base() = shot("base", { cheat("level 40"); cheat("tp base 1") }, steps = 120)

    @Test fun g07_night() = shot("night", { cheat("level 8"); cheat("tp camp"); cheat("night") })

    @Test fun g08_dog() = shot("dog", { cheat("level 10"); cheat("dog 3") }, steps = 90, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))

    @Test fun g09_harbor() = shot("harbor", { cheat("level 45"); cheat("tp harbor"); cheat("spawn dark 3 3") }, steps = 90)

    /** V15: kare bölgenin kenarı (Başlangıç Ormanı'nın doğusu): ötesi boşluk, yürünmez */
    @Test fun g12_border() = shot("border", { cheat("level 12"); cheat("tp 268 300") }, steps = 90, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))

    /** V12.1: tezgâh eşyası kampın dışına kurulur (kalan süre), tezgâh ekranında yalnız tezgâh tarifleri */
    private fun placeBench() {
        cheat("level 12"); cheat("tp 150 320"); cheat("item workbench 1"); cheat("item swamp_moss 3"); cheat("item dried_hide 3"); cheat("item bandage 4")
        val slots = GameSession.ui("inventory").optJSONArray("slots") ?: return
        val k = (0 until slots.length()).firstOrNull { slots.optJSONObject(it)?.optString("id") == "workbench" } ?: return
        cmd(JSONObject().put("cmd", "use").put("slot", k))
    }

    @Test fun g13_bench() = shot("bench", { placeBench() }, steps = 40)

    /** V15 inşa modu: tezgâh çantadan "Kur" ile seçilir; önizleme yeşil (uygun) ya da kırmızı, ✓ / ✕ */
    @Test fun g16_buildMode() = shot("build_mode", {
        cheat("level 12"); cheat("tp 150 320"); cheat("item workbench 1")
    }, steps = 30, hud = false, after = { v ->
        val slots = GameSession.ui("inventory").optJSONArray("slots")!!
        v.startBuild((0 until slots.length()).first { slots.optJSONObject(it)?.optString("id") == "workbench" })
    })

    /** V15: her bölgenin gerçek haritası (incelemek için app/build/game-shots/region_<id>.png; ekran görüntüsü değil) */
    @Test fun g30_allRegionMaps() {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        cheat("level 50")
        val w = GameSession.world()
        val areas = com.zenvex.online.ui.mapAreas(w)
        val names = w.getJSONArray("names")
        val atlas = Atlas.get(paparazzi.context)
        val statics = Array(names.length()) { atlas["s/" + names.getString(it)] }
        val view = GameView(paparazzi.context, ui)
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        for ((ai, a) in areas.withIndex()) {
            cheat("tp ${(a.rects[0][0] + a.rects[0][2]) / 2 / 64} ${(a.rects[0][1] + a.rects[0][3]) / 2 / 64}")
            view.previewFrames(Canvas(bmp), 640, 360, 3)
            val s = RegionMap.buildNow(ai, a, statics) ?: continue
            java.io.FileOutputStream(java.io.File(dir, "region_${a.id}.png")).use { s.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** V15 gerçek bölge haritası: bölge oyunun kendi dokuları ve görselleriyle küçültülmüş; büyük harita bunu gösterir */
    @Test fun g17_regionMap() {
        var snap: RegionMap.Snap? = null
        shot("region_map", { cheat("level 8"); cheat("tp camp") }, steps = 30, hud = false, after = {
            val w = GameSession.world()
            val areas = com.zenvex.online.ui.mapAreas(w)
            val names = w.getJSONArray("names")
            val atlas = Atlas.get(paparazzi.context)
            val statics = Array(names.length()) { atlas["s/" + names.getString(it)] }
            val hud = GameSession.hud()
            val ai = com.zenvex.online.ui.areaIndexAt(areas, hud[Native.PX], hud[Native.PY])
            snap = RegionMap.buildNow(ai, areas[ai], statics)
            snap?.let { s ->
                val dir = java.io.File("build/game-shots").apply { mkdirs() }
                java.io.FileOutputStream(java.io.File(dir, "region_map_raw.png")).use { s.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }, overlay = {
            val hud = GameSession.hud()
            com.zenvex.online.ui.WorldMapView(null, null, GameSession.world(), hud[Native.PX], hud[Native.PY], 8, emptyList(), {}, region = snap)
        })
    }

    @Test fun g14_benchCraft() = shot("bench_craft", { placeBench() }, steps = 20, hud = false,
        overlay = { com.zenvex.online.ui.CraftScreen({}, bench = true) })

    @Test fun g15_handCraft() = shot("hand_craft", { cheat("item cloth 6"); cheat("item resin 4") }, steps = 20, hud = false,
        overlay = { com.zenvex.online.ui.CraftScreen({}) })

    /** V12.2 gezgin tüccar: kamyonet kampa gelir, park eder, tüccar iner; dükkânda gelme olasılıkları */
    private fun goCaravan() {
        val m = GameSession.ui("areas").optJSONArray("merchant")?.optJSONObject(0) ?: return
        val p = m.optJSONArray("park") ?: return
        cheat("tp ${(p.optDouble(0) / 64 - 2).toInt()} ${(p.optDouble(1) / 64 + 3).toInt()}")
    }

    @Test fun g16_caravan() = shot("caravan", { cheat("level 6"); goCaravan() }, steps = 420)

    @Test fun g17_shop() = shot("shop", { cheat("level 12"); cheat("gold 2000"); goCaravan() }, steps = 420, hud = false,
        overlay = { com.zenvex.online.ui.ShopScreen({}) })

    // V13 bölge yaratıkları (yürür, saldırır; isim ve seviye başlarında)
    private fun creatures(lv: Int, tp: String, vararg ids: String) {
        cheat("level $lv"); cheat("tp $tp"); cheat("killall")
        for (id in ids) cheat("spawn $id 2 $lv")
    }
    @Test fun g18_forestCreatures() = shot("cr_forest", { creatures(8, "camp", "lumberjack", "wolf", "shroom") }, steps = 70)
    @Test fun g19_cityCreatures() = shot("cr_city", { creatures(15, "downtown", "office", "riot", "crawler") }, steps = 70)
    @Test fun g20_burntCreatures() = shot("cr_burnt", { creatures(20, "burnt", "charred", "emberhound", "ashwraith") }, steps = 70)
    @Test fun g21_desertCreatures() = shot("cr_desert", { creatures(25, "desert", "mummy", "scorpion", "biker") }, steps = 70)
    @Test fun g22_swampCreatures() = shot("cr_swamp", { creatures(30, "swamp", "bloater", "boggiant", "witch") }, steps = 70)
    @Test fun g23_harborCreatures() = shot("cr_harbor", { creatures(45, "harbor", "sailor", "icecrab", "dockbrute") }, steps = 70)

    // V13 Magma Titan: yürür, yere vurur, ateş püskürtür
    @Test fun g24_titan() = shot("titan", { cheat("level 50"); cheat("tp desert"); cheat("god"); cheat("boss") }, steps = 150)
    @Test fun g25_titanLate() = shot("titan_late", { cheat("level 50"); cheat("tp desert"); cheat("god"); cheat("boss") }, steps = 420)

    // V13 Cephe botları: üsse girince kalkanlı ve alev makineli askerler
    @Test fun g26_baseBots() = shot("base_bots", { cheat("level 40"); cheat("god"); cheat("tp base 1") }, steps = 120)

    // etkinlik silahları elde (kabza/uç doğrusu katana karelerinden)
    private fun evWeapon(id: String) { cheat("level 20"); cheat("item ev_${id}_weapon 1"); cmd(JSONObject().put("cmd", "cycle_weapon")) }
    @Test fun g27_evBlood() = shot("ev_blood", { evWeapon("blood") }, steps = 30)
    @Test fun g28_evTitanWalk() = shot("ev_titan_walk", { evWeapon("titan") }, steps = 40, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))
    @Test fun g29_evPackAttack() = shot("ev_pack_attack", { evWeapon("pack"); cheat("spawn walker 2 2") }, steps = 50, input = floatArrayOf(0f, 0f, 1f, 0f, 0f))

    @Test fun g10_vehicle() = shot("vehicle", { cheat("level 20"); cheat("tp desert"); cheat("vehicle 2") }, steps = 60)
}
