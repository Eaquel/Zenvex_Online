package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
                     after: ((GameView) -> Unit)? = null, overlay: (@androidx.compose.runtime.Composable () -> Unit)? = null,
                     prep: ((GameView) -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        setup()
        val view = GameView(paparazzi.context, ui)
        view.localName = "Oyuncu"
        prep?.invoke(view)
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

    /** Kalabalık kıyası (yazılım tuvali; cihazdan yavaş ama göreli): 1000 yaratık, kare başına ms → konsol */
    @Test fun p01_crowdPerf() = shot("crowd_perf", {
        cheat("level 30"); cheat("god"); cheat("tp deep"); cheat("killall")
        repeat(34) { cheat("spawn walker 30 20") }
    }, steps = 5, prep = { v ->
        val bmp = Bitmap.createBitmap(2340, 1080, Bitmap.Config.ARGB_8888)
        v.previewFrames(Canvas(bmp), 2340, 1080, 10)
        val t0 = System.nanoTime()
        v.previewFrames(Canvas(bmp), 2340, 1080, 30)
        println("PERF kalabalık: " + (System.nanoTime() - t0) / 30 / 1_000_000.0 + " ms/kare")
    })

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
    /** Gizemli Ağaç (görev noktası) ve çevresi */
    @Test fun g31_mysteryTree() = shot("mystery_tree", { cheat("level 8"); cheat("tp 232 264") }, steps = 40)

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

    /** Büyük harita gerçek oyundaki gibi: keşif bulutu ve bölge dışı bulut denizi */
    @Test fun g17b_regionMapFog() {
        var snap: RegionMap.Snap? = null
        shot("region_map_fog", { cheat("level 8"); cheat("tp camp") }, steps = 30, hud = false, after = {
            val w = GameSession.world()
            val areas = com.zenvex.online.ui.mapAreas(w)
            val names = w.getJSONArray("names")
            val atlas = Atlas.get(paparazzi.context)
            val statics = Array(names.length()) { atlas["s/" + names.getString(it)] }
            val hud = GameSession.hud()
            snap = RegionMap.buildNow(com.zenvex.online.ui.areaIndexAt(areas, hud[Native.PX], hud[Native.PY]), areas[com.zenvex.online.ui.areaIndexAt(areas, hud[Native.PX], hud[Native.PY])], statics)
        }, overlay = {
            val hud = GameSession.hud()
            com.zenvex.online.ui.WorldMapView(null, com.zenvex.online.ui.fogMask(GameSession.ui("fog")), GameSession.world(), hud[Native.PX], hud[Native.PY], 8,
                emptyList(), {}, region = snap)
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

    /** V18 bölge sınırı: komşu bölgenin nesneleri görünmez, sınırın ötesi bulut denizi */
    @Test fun e01_borderEast() = shot("border_east", { cheat("level 20"); cheat("tp 355 470") }, steps = 30)
    @Test fun e02_borderCorner() = shot("border_corner", { cheat("level 20"); cheat("tp 193 383") }, steps = 30)
    /** V18 kontrol rehberi: ilk girişte numaralı işaretler ve açıklama paneli */
    @Test fun e03_controlsGuide() = shot("controls_guide", { cheat("heal") }, steps = 40)
    @Test fun g10_vehicle() = shot("vehicle", { cheat("level 20"); cheat("tp desert"); cheat("vehicle 2") }, steps = 60)
    // ------------------------------------------------------------------ V16 3B kahramanlar, silahlar, araçlar
    private fun equip(item: String) {
        cheat("item $item 1")
        val slots = GameSession.ui("inventory").optJSONArray("slots") ?: return
        val k = (0 until slots.length()).firstOrNull { slots.optJSONObject(it)?.optString("id") == item } ?: return
        cmd(JSONObject().put("cmd", "equip").put("slot", k))
    }

    private var heroLevel = 30
    private fun hero3d(ch: Int, item: String?, vararg zombies: String) {
        cheat("level $heroLevel"); cheat("god"); cheat("hero $ch"); cheat("tp deep"); cheat("killall")
        if (item != null) equip(item)
        for (z in zombies) cheat("spawn $z 3 6")
    }

    /** sağa doğru saldırı (atak çubuğu basılı) */
    private val attackRight = floatArrayOf(0f, 0f, 1f, 0.15f, 1f)

    @Test fun h01_kaelKatana() = shot("h3d_kael_katana", { hero3d(0, "w_katana", "walker") }, steps = 47, input = attackRight)
    @Test fun h02_lyraAk() = shot("h3d_lyra_ak47", { hero3d(1, "w_ak47", "walker") }, steps = 40, input = attackRight)
    @Test fun h03_vexMagma() = shot("h3d_vex_magma", { hero3d(2, "ev_titan_weapon", "walker") }, steps = 50, input = attackRight)
    @Test fun h04_morvathIce() = shot("h3d_morvath_ice", { hero3d(3, "ev_breath_weapon", "walker") }, steps = 44, input = attackRight)
    @Test fun h05_bloodScythe() = shot("h3d_blood", { hero3d(0, "ev_blood_weapon", "walker") }, steps = 44, input = attackRight)
    @Test fun h06_stormAxe() = shot("h3d_storm", { hero3d(2, "ev_base_weapon", "walker") }, steps = 50, input = attackRight)
    @Test fun h07_walk() = shot("h3d_walk", { hero3d(1, "w_uzi") }, steps = 40, input = floatArrayOf(-0.7f, 0.7f, 0f, 0f, 0f))
    /** araca yürü, bin, sür (sürüş kareleri ayrı tuvale; son kareler ekran görüntüsü) */
    private fun drive(v: GameView, mx: Float, my: Float) {
        val tmp = Bitmap.createBitmap(2340, 1080, Bitmap.Config.ARGB_8888)
        cmd(JSONObject().put("cmd", "vehicle"))
        v.previewFrames(Canvas(tmp), 2340, 1080, 45, input = floatArrayOf(mx, my, 0f, 0f, 0f))
    }

    @Test fun h08_moto() = shot("h3d_moto", { hero3d(0, null); cheat("vehicle moto") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 1f, 0.3f) })
    @Test fun h09_car() = shot("h3d_car", { hero3d(2, null); cheat("vehicle car") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 0.8f, -0.6f) })
    /** Saldırı kare dizisi (incelemek için build/game-shots/seq_<ad>.png): oyuncu çevresi, 2 karede bir */
    private fun sequence(name: String, ch: Int, item: String?, input: FloatArray, frames: Int = 12, warm: Int = 20, stepPer: Int = 2, zoom: Float = 1.7f, setup: (() -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        if (setup != null) setup() else hero3d(ch, item, "walker", "walker")
        val view = GameView(paparazzi.context, ui)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(Canvas(bmp), w, h, warm, input = input)
        val cw = 520; val chh = 520
        val sheet = Bitmap.createBitmap(cw * 6, chh * ((frames + 5) / 6), Bitmap.Config.ARGB_8888)
        val sc = Canvas(sheet)
        for (i in 0 until frames) {
            view.previewFrames(Canvas(bmp), w, h, stepPer, input = input)
            val hw = (cw / zoom / 2).toInt(); val hh = (chh / zoom / 2).toInt()
            val src = android.graphics.Rect(w / 2 - hw, h / 2 - hh - 40, w / 2 + hw, h / 2 + hh - 40)
            sc.drawBitmap(bmp, src, android.graphics.Rect((i % 6) * cw, (i / 6) * chh, (i % 6 + 1) * cw, (i / 6 + 1) * chh), null)
        }
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "seq_$name.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun s01_katana() = sequence("katana", 0, "w_katana", attackRight)
    @Test fun s02_magma() = sequence("magma", 2, "ev_titan_weapon", attackRight)
    @Test fun s03_ice() = sequence("ice", 3, "ev_breath_weapon", attackRight)
    @Test fun s04_storm() = sequence("storm", 2, "ev_base_weapon", attackRight)
    @Test fun s05_ak() = sequence("ak", 1, "w_ak47", attackRight, 18, warm = 75)
    @Test fun s06_chainsaw() = sequence("chainsaw", 3, "ev_scrap_weapon", attackRight)
    @Test fun s07_scythe() { heroLevel = 8; sequence("scythe", 0, "ev_blood_weapon", attackRight, 24, warm = 10, stepPer = 3) }
    @Test fun s08_rpg() { heroLevel = 8; sequence("rpg", 1, "w_rpg", attackRight, 24, warm = 40, stepPer = 7) }
    private fun node(kind: String, ch: Int, item: String?) {
        cheat("level $heroLevel"); cheat("god"); cheat("hero $ch"); cheat("tp deep"); cheat("killall")
        if (item != null) equip(item)
        cheat("tp $kind")
    }
    @Test fun s09_chopStone() { heroLevel = 8; sequence("chop", 0, null, attackRight, 12, warm = 6, stepPer = 3, setup = { cheat("item t_axe_stone 1"); node("tree", 0, null) }) }
    @Test fun s10_chopIronHeld() { heroLevel = 8; sequence("chop_iron", 2, null, attackRight, 12, warm = 6, stepPer = 3, setup = { node("tree", 2, "t_axe_iron") }) }
    @Test fun s11_mineIron() { heroLevel = 8; sequence("mine", 1, null, attackRight, 12, warm = 6, stepPer = 3, setup = { cheat("item t_pick_iron 1"); node("rock", 1, null) }) }
    // ------------------------------------------------------------------ V16 efekt motoru v2
    private fun fxShot(name: String, setup: () -> Unit, steps: Int, input: FloatArray? = null, frames: Int = 6, after: ((GameView) -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        setup()
        val view = GameView(paparazzi.context, ui)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(Canvas(bmp), w, h, steps, input = input)
        after?.invoke(view)
        val cw = 600; val ch = 420
        val sheet = Bitmap.createBitmap(cw * 3, ch * ((frames + 2) / 3), Bitmap.Config.ARGB_8888)
        val sc = Canvas(sheet)
        for (i in 0 until frames) {
            view.previewFrames(Canvas(bmp), w, h, 4, input = input)
            val src = android.graphics.Rect(w / 2 - cw / 2, h / 2 - ch / 2 - 40, w / 2 + cw / 2, h / 2 + ch / 2 - 40)
            sc.drawBitmap(bmp, src, android.graphics.Rect((i % 3) * cw, (i / 3) * ch, (i % 3 + 1) * cw, (i / 3 + 1) * ch), null)
        }
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "fx_$name.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun f01_aura50() = fxShot("aura50", { cheat("level 50"); cheat("god"); cheat("hero 0"); cheat("tp camp"); cheat("night") }, steps = 40)
    @Test fun f02_aura25() = fxShot("aura25", { cheat("level 25"); cheat("god"); cheat("hero 1"); cheat("tp camp") }, steps = 40)
    @Test fun f03_aura10() = fxShot("aura10", { cheat("level 8"); cheat("god"); cheat("hero 0"); cheat("charlevel 10"); cheat("tp camp"); cheat("night") }, steps = 40)
    @Test fun f04_runDust() = fxShot("rundust", { cheat("level 12"); cheat("god"); cheat("hero 2"); cheat("tp desert") }, steps = 30, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))
    @Test fun f05_motoDesert() = fxShot("motodesert", { cheat("level 25"); cheat("god"); cheat("hero 0"); cheat("tp desert"); cheat("vehicle moto") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 1f, 0.2f) })
    @Test fun f06_carForest() = fxShot("carforest", { cheat("level 25"); cheat("god"); cheat("hero 2"); cheat("tp deep"); cheat("vehicle car") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 1f, 0.1f) })

    /** Çizim çağrılarını sayan tuval: ön ayarların gerçekte ne kadar iş azalttığını ölçer */
    private class CountingCanvas(b: Bitmap) : Canvas(b) {
        var calls = 0
        override fun drawBitmap(bitmap: Bitmap, src: android.graphics.Rect?, dst: RectF, paint: Paint?) { calls++; super.drawBitmap(bitmap, src, dst, paint) }
        override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) { calls++; super.drawBitmap(bitmap, left, top, paint) }
        override fun drawBitmap(bitmap: Bitmap, matrix: android.graphics.Matrix, paint: Paint?) { calls++; super.drawBitmap(bitmap, matrix, paint) }
        override fun drawPath(path: Path, paint: Paint) { calls++; super.drawPath(path, paint) }
        override fun drawOval(oval: RectF, paint: Paint) { calls++; super.drawOval(oval, paint) }
        override fun drawOval(l: Float, t: Float, r: Float, b: Float, paint: Paint) { calls++; super.drawOval(l, t, r, b, paint) }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { calls++; super.drawCircle(cx, cy, radius, paint) }
        override fun drawLine(sx: Float, sy: Float, ex: Float, ey: Float, paint: Paint) { calls++; super.drawLine(sx, sy, ex, ey, paint) }
        override fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) { calls++; super.drawRect(l, t, r, b, paint) }
        override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, paint: Paint) { calls++; super.drawRoundRect(l, t, r, b, rx, ry, paint) }
        override fun drawBitmapMesh(bitmap: Bitmap, mw: Int, mh: Int, v: FloatArray, vo: Int, col: IntArray?, co: Int, paint: Paint?) { calls++; super.drawBitmapMesh(bitmap, mw, mh, v, vo, col, co, paint) }
    }

    /** Grafik ön ayarları gerçekten maliyeti düşürür: aynı kalabalık sahne 4 ön ayarda çizilir, kare süresi sıralanır. */
    @Test fun p01_presetCost() {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        cheat("level 50"); cheat("god"); cheat("hero 0"); cheat("tp downtown"); cheat("killall")
        equip("ev_blood_weapon")
        for (z in listOf("walker", "runner", "dark", "tank", "toxic")) cheat("spawn $z 3 3")
        val view = GameView(paparazzi.context, ui)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = CountingCanvas(bmp)
        val cost = FloatArray(4); val calls = IntArray(4)
        val before = Settings.current
        try {
            for (preset in intArrayOf(3, 0, 1, 2, 3, 0)) {
                Settings.preview(Settings().withPreset(preset))
                view.previewFrames(c, w, h, 30, input = attackRight)
                c.calls = 0
                val t0 = System.nanoTime()
                repeat(5) { view.previewFrames(c, w, h, 8, input = attackRight) }
                cost[preset] = (System.nanoTime() - t0) / 1e6f / 40f
                calls[preset] = c.calls / (5 * 6)
            }
        } finally { Settings.preview(before) }
        println("PRESET_COST çağrı/kare düşük=${calls[0]} orta=${calls[1]} yüksek=${calls[2]} ultra=${calls[3]} · ms/kare ${cost.toList()}")
        org.junit.Assert.assertTrue("çizim çağrısı düşük < orta < yüksek olmalı: ${calls.toList()}", calls[0] < calls[1] && calls[1] < calls[2])
    }

    private fun fxCalm(name: String): () -> Unit = { cheat("level 8"); cheat("god"); cheat("hero 0"); cheat("tp deep"); cheat("killall") }
    @Test fun f07_explosion() = fxShot("explode", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("explode") })
    @Test fun f08_bomb() = fxShot("bomb", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("bomb", 200f, -40f) })
    @Test fun f09_levelup() = fxShot("levelup", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("levelup") })
    @Test fun f10_toxic() = fxShot("toxic", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("toxic", 90f, 20f) })
    @Test fun f11_teleport() = fxShot("teleport", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("teleport", 0f, 30f) })
    @Test fun f12_bolt() = fxShot("bolt", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("bolt", 90f, 30f) })
    @Test fun f13_fireRage() = fxShot("firerage", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("fire", 100f, 30f); it.previewEffect("rage", 0f, 0f) })
    @Test fun f14_hitFx() = fxShot("hitfx", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("crit", 60f, -40f); it.previewEffect("blood", 110f, -10f); it.previewEffect("frost", -70f, 10f); it.previewEffect("spark", 160f, -30f) })

    // ------------------------------------------------------------------ "gerçek oyuncu gibi oynanış" klipleri (reklam/tanıtım)
    /**
     * Kahraman durmaz: koşar, kaçar, takla atar, sürüye döner; kamera yaklaşıp uzaklaşır.
     * PROMO_HUD=1: gerçek oyun arayüzüyle (build/promo_hud/<ad>), yoksa arayüzsüz (build/promo/<ad>). PROMO_EVERY=n: yalnız her n. kareyi kaydet (ön izleme).
     */
    private fun playClip(name: String, setup: () -> Unit, warm: Int, frames: Int, move: (Int) -> FloatArray, meters: (Int) -> Float,
                         rolls: Set<Int> = emptySet(), onFrame: ((GameView, Int) -> Unit)? = null, who: String = "Kael") {
        Assume.assumeTrue("libzenvex.so yok", native())
        val hud = System.getenv("PROMO_HUD") == "1"
        val every = (System.getenv("PROMO_EVERY") ?: "1").toInt().coerceAtLeast(1)
        com.zenvex.online.ui.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        val before = Settings.current
        Settings.preview(Settings().withPreset(3).copy(zoom = 0, screenShake = 0.6f, showFps = false, showPing = false))
        try {
            setup()
            cheat("guide off"); cheat("heal")
            val view = GameView(paparazzi.context, ui)
            view.localName = who
            view.clean = !hud
            val portrait = System.getenv("PROMO_PORTRAIT") == "1"
            val w = if (portrait) 1080 else 2340; val h = if (portrait) 1920 else 1080
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val mk = if (portrait) 1.55f else 1f   // dikeyde ekranın yüksekliği daha çok metre gösterir
            view.promoZoom = meters(0) * mk
            view.previewFrames(c, w, h, warm, input = floatArrayOf(0f, 0f, 0f, 0f, 0f))
            // oyunun çaldığı her ses (olayın anında) kaydedilir: video sesi gerçek oyun sesleriyle birebir eşlenir
            var frameNo = 0
            val sfxLog = org.json.JSONArray()
            Audio.tap = { n, v, p -> sfxLog.put(org.json.JSONObject().put("f", frameNo).put("n", n).put("v", v.toDouble()).put("p", p.toDouble())) }
            val dir = java.io.File(if (portrait) "build/promo_v/$name" else if (hud) "build/promo_hud/$name" else "build/promo/$name").apply { deleteRecursively(); mkdirs() }
            for (i in 0 until frames) {
                view.promoZoom = meters(i) * mk
                frameNo = i
                if (i in rolls) GameSession.cmd("ability")
                onFrame?.invoke(view, i)
                view.previewFrames(c, w, h, 1, input = move(i))
                if (i % every == 0) java.io.FileOutputStream(java.io.File(dir, "f%04d.png".format(i))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            java.io.File(dir, "sfx.json").writeText(sfxLog.toString())
        } finally { Audio.tap = null; Settings.preview(before) }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun ease(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** Sürü: koşarak çekip toplar, sonra ortasında dönerek biçer, arada takla atar. */
    @Test fun y01_playHorde() = playClip("horde",
        { cheat("level 30"); cheat("god"); cheat("hero 0"); cheat("tp camp"); cheat("killall"); equip("ev_blood_weapon"); cheat("night"); cheat("spawn walker 9 20"); cheat("spawn runner 4 20"); cheat("spawn dark 3 20") },
        110, 240,
        move = { i ->
            if (i < 50) { val a = i * 0.11f; floatArrayOf(kotlin.math.cos(a), kotlin.math.sin(a), kotlin.math.cos(a), kotlin.math.sin(a), 0f) }
            else { val a = i * 0.16f; floatArrayOf(0.4f * kotlin.math.cos(a * 0.5f), 0.4f * kotlin.math.sin(a * 0.5f), kotlin.math.cos(a), kotlin.math.sin(a), 1f) }
        },
        meters = { i -> lerp(8.6f, 6.0f, ease((i - 30) / 50f)) + lerp(0f, 1.4f, ease((i - 170) / 60f)) },
        rolls = setOf(95, 160))

    /** Roketatar: geri çekilerek ateş eder, tank ve sürü patlar. */
    @Test fun y02_playRocket() = playClip("rocket",
        { cheat("level 30"); cheat("god"); cheat("hero 1"); cheat("tp downtown"); cheat("killall"); equip("w_rpg"); cheat("spawn tank 2 30"); cheat("spawn walker 10 30"); cheat("spawn runner 4 30") },
        110, 240, who = "Lyra",
        move = { i -> floatArrayOf(-0.55f, 0.1f * kotlin.math.sin(i / 14f), 1f, 0.05f * kotlin.math.sin(i / 20f), 1f) },
        meters = { i -> lerp(9.0f, 7.0f, ease(i / 120f)) + lerp(0f, 1.0f, ease((i - 170) / 60f)) },
        rolls = setOf(120))

    /** Motosiklet: çölde sürüyle yarışır, zikzak çizer. */
    @Test fun y03_playChase() = playClip("chase",
        { cheat("level 30"); cheat("god"); cheat("hero 0"); cheat("tp desert"); cheat("killall"); cheat("spawn runner 8 30"); cheat("spawn walker 8 30"); cheat("vehicle moto") },
        8, 270,
        move = { i -> floatArrayOf(1f, 0.55f * kotlin.math.sin(i / 16f), 0f, 0f, 0f) },
        meters = { i -> lerp(9.5f, 8.0f, ease(i / 90f)) },
        onFrame = { v, i -> if (i == 0) drive(v, 1f, 0f) })

    /** Titan: etrafında dönerek takla atar ve vurur. */
    @Test fun y04_playTitan() = playClip("titan",
        { cheat("level 50"); cheat("god"); cheat("hero 2"); cheat("tp desert"); cheat("killall"); equip("ev_titan_weapon"); cheat("boss") },
        140, 240, who = "Vex",
        move = { i ->
            val a = i * 0.05f
            if (i < 40) floatArrayOf(0.9f, -0.43f, 0.9f, -0.43f, 0f)
            else floatArrayOf(-0.8f * kotlin.math.sin(a), 0.8f * kotlin.math.cos(a), 0.9f, -0.43f, 1f)
        },
        meters = { i -> lerp(9.2f, 7.0f, ease((i - 20) / 60f)) },
        rolls = setOf(70, 120, 175))

    /** Donmuş liman: buz tırpanıyla sürüyü biçer. */
    @Test fun y05_playFrost() = playClip("frost",
        { cheat("level 45"); cheat("god"); cheat("hero 3"); cheat("tp harbor"); cheat("killall"); equip("ev_breath_weapon"); cheat("spawn walker 8 40"); cheat("spawn dark 5 40"); cheat("spawn runner 3 40") },
        110, 210, who = "Morvath",
        move = { i ->
            if (i < 40) floatArrayOf(-0.7f, 0.3f, -0.7f, 0.3f, 0f)
            else { val a = i * 0.17f; floatArrayOf(0.35f * kotlin.math.cos(a * 0.4f), 0.35f * kotlin.math.sin(a * 0.4f), kotlin.math.cos(a), kotlin.math.sin(a), 1f) }
        },
        meters = { i -> lerp(8.4f, 6.2f, ease((i - 25) / 50f)) },
        rolls = setOf(85, 150))
}
