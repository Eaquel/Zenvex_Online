package com.zenvex.online.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import org.json.JSONObject
import androidx.compose.ui.graphics.asImageBitmap
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

    /** 3B karakter vitrini (lobi, karakterler, çanta): yerel kütüphane varsa modeller anında çizilir */
    @org.junit.Before
    fun models() {
        if (runCatching { com.zenvex.online.game.Native.hashCode(); true }.getOrDefault(false))
            com.zenvex.online.game.Model3D.initSync(paparazzi.context)
    }

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
                    PxIcon("bag", size = 54.dp) {}
                }
                ToastPlate("Doktor yaralarını sardı (-20 altın)", Px.Clan, "coin")
                ToastPlate("Yeni dostun seninle! Köpek seni takip edecek.", Px.Name, "check")
                ToastPlate("30 altın gerekli", androidx.compose.ui.graphics.Color(0xFFE0483C), "close")
            }
        }
    }

    @Test fun shopFeatured() { val d = sample("store"); paparazzi.snapshot { ShopView(d, true, d.optLong("now"), 0, {}) {} } }
    @Test fun shopGems() { val d = sample("store"); paparazzi.snapshot { ShopView(d, true, d.optLong("now"), 1, {}) {} } }
    @Test fun shopCards() { val d = sample("store"); paparazzi.snapshot { ShopView(d, true, d.optLong("now"), 3, {}) {} } }
    @Test fun merchantSell() { val d = sample("merchant"); paparazzi.snapshot { MerchantView(d, 1, {}, {}, {}, {}, {}) } }
    @Test fun merchantOdds() { val d = sample("merchant"); paparazzi.snapshot { MerchantView(d, 2, {}, {}, {}, {}, {}) } }
    @Test fun characters() { val d = sample("characters"); paparazzi.snapshot { CharactersView(d, {}, {}) {} } }

    @Test
    fun skillTree() {
        val data = sample("tree")
        paparazzi.snapshot { TreeView(data) {} }
    }

    /** Çanta yarı saydam: arkada oyun görünsün diye çimen renginde zemin üstüne çizilir. */
    @Test
    fun bagScreen() {
        val data = sample("inventory")
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF3E5A2A))) {
                BagView(data, 0, true, {}, { _, _ -> com.zenvex.online.game.GameSession.Reply(true, "") }) {}
            }
        }
    }

    /** Kişisel üste sandık açıkken: solda sandık içeriği. */
    @Test
    fun bagChestScreen() {
        val data = sample("inventory")
        val chest = sample("chest")
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF3E5A2A))) {
                BagView(data, 0, true, {}, { _, _ -> com.zenvex.online.game.GameSession.Reply(true, "") }, chest = chest) {}
            }
        }
    }

    @Test
    fun worldMap() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val w = sample("world")
        val me = sample("player")
        val fog = fogMask(sample("fog"))
        val marks = mapMarks(w, JSONObject()) + baseMarks(sample("bases")) +
            MapMark(me.getDouble("x").toFloat() + 900f, me.getDouble("y").toFloat() - 1400f, 3, "Gizemli Ağaç")
        paparazzi.snapshot {
            WorldMapView(img, fog, w, me.getDouble("x").toFloat(), me.getDouble("y").toFloat(), 3, marks, {})
        }
    }

    /** V12 büyücü haritası: bölgeye dokununca kart (yaratıklar, ganimet, seviye, ücret); kilitli bölge */
    @Test
    fun wizardMap() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val items = itemNames(sample("world"))
        paparazzi.snapshot { WizardMapView(sample("areas"), items, img, {}, initial = 2) {} }
    }

    @Test
    fun wizardMapLocked() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        paparazzi.snapshot { WizardMapView(sample("areas"), itemNames(sample("world")), img, {}, initial = 6) {} }
    }

    /** V18: kamp karakterleri ve önemli yerler etiketli (nerede ne olduğu belli) */
    @Test
    fun worldMapLabels() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val w = sample("world")
        val me = sample("player")
        val mx = me.getDouble("x").toFloat(); val my = me.getDouble("y").toFloat()
        val npcs = org.json.JSONArray()
            .put(org.json.JSONArray().put("Tüccar Marco").put("Tüccar").put(mx + 420.0).put(my - 260.0).put(1))
            .put(org.json.JSONArray().put("Yaşlı Silas").put("Görevci").put(mx - 380.0).put(my - 120.0).put(2))
            .put(org.json.JSONArray().put("Doktor Elena").put("Doktor").put(mx - 120.0).put(my + 360.0).put(3))
            .put(org.json.JSONArray().put("Tamirci Viktor").put("Tamirci").put(mx + 90.0).put(my - 520.0).put(4))
        w.put("npcs", npcs)
        val marks = mapMarks(w, JSONObject()) + MapMark(mx + 900f, my - 1400f, 3, "Gizemli Ağaç")
        paparazzi.snapshot { WorldMapView(img, null, w, mx, my, 3, marks, {}) }
    }

    /** V8: keşfedilen devlet üsleri (devlet / başka klan / kendi klanın) */
    @Test
    fun worldMapBases() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val w = sample("world")
        val b = sample("bases").getJSONArray("bases").getJSONObject(1)
        val all = org.json.JSONObject().put("bases", org.json.JSONArray().apply {
            for (i in 0 until 50) put(org.json.JSONObject(sample("bases").getJSONArray("bases").getJSONObject(i).toString()).put("found", true))
        })
        paparazzi.snapshot {
            WorldMapView(img, null, w, b.getDouble("x").toFloat(), b.getDouble("y").toFloat(), 30, mapMarks(w, JSONObject()) + baseMarks(all), {}, baseMarks(all).firstOrNull { it.icon == "map_war" })
        }
    }

    @Test
    fun npcDialog() {
        val d = sample("talk")
        // son satır + seçenekler görünsün diye tek satırlık örnek
        d.put("lines", org.json.JSONArray().put(d.getJSONArray("lines").getString(0)))
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF3A5A2A))) {
                NpcDialogView(d, {}, {}, instant = true)
            }
        }
    }

    @Test
    fun questsScreen() {
        val q = sample("quests")
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box {
                QuestsView(q) {}
                QuestTracker(q.getJSONArray("story"), androidx.compose.ui.Modifier.padding(start = 300.dp, top = 300.dp)) {}
            }
        }
    }

    @Test
    fun kitScreen() {
        paparazzi.snapshot {
            ScreenFrame("ÜRETİM", "craft", {}, right = { Chip("coin", "1.250", Pal.Gold) }) {
                Tabs(listOf("shop" to "satın al", "coin" to "sat", "gem" to "elmas"), 0) {}
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Panel(androidx.compose.ui.Modifier.width(360.dp)) {
                        SectionTitle("Malzemeler")
                        T("Kumaş ve bant ile bandaj yapılır.", 14.sp, maxLines = 2)
                        Bar(0.6f, Pal.Green, androidx.compose.ui.Modifier.fillMaxWidth(), 14.dp, "3 / 5")
                        androidx.compose.foundation.layout.Row { ItemSlot("bandage", 3, 0, 56.dp); ItemSlot(null, 0, 0, 56.dp, label = "Boş") }
                    }
                    androidx.compose.foundation.layout.Column {
                        GButton("ÜRET", tone = Tone.Green, icon = "craft") {}
                        GButton("×5", tone = Tone.Blue) {}
                        GButton("Kilitli", enabled = false) {}
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sıralama + profil
    private fun fakePhoto(): String {
        val img = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until 96) for (x in 0 until 96) img.setPixel(x, y, (0xFF shl 24) or ((x * 2) shl 16) or ((y * 2) shl 8) or 120)
        val bo = java.io.ByteArrayOutputStream(); img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo)
        return java.util.Base64.getEncoder().encodeToString(bo.toByteArray())
    }

    private fun title(k: String, r: Int) = JSONObject().put("kind", k).put("rank", r).put("name", (BOARD_CATS.first { it.kind == k }.title) + " #" + r)

    @Test
    fun leaderboard() {
        val names = listOf("Karanlık", "Bozkurt", "Gölge", "Yılmaz", "ZeroOne", "Kaan", "Ateş", "Demir", "Fırtına", "Ayaz")
        val rows = org.json.JSONArray()
        names.forEachIndexed { i, n ->
            rows.put(JSONObject().put("rank", i + 1).put("id", 100 + i).put("name", n).put("avatar", i % 6).put("frame", listOf("gold", "silver", "bronze", "", "vip")[i % 5])
                .put("photo", if (i == 1) fakePhoto() else "").put("v", 52000L - i * 4100).put("clan", if (i % 3 == 0) "KRT" else "")
                .put("title", if (i < 5) title("kills", i + 1) else JSONObject.NULL).put("banned", i == 6).put("vip", i == 0))
        }
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { BoardDialog({}, rows) } } }
    }

    /** Klan seviye armaları 1-9: kalkan her seviyede aynı boyda, rakam ortada */
    @Test
    fun clanEmblems() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF2B2B26)).padding(16.dp)) {
                for (s in listOf(42, 72)) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    for (l in 1..9) { ClanEmblem(l, if (l <= 3) l else 0, s.dp); androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp)) }
                }
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    for (r in 1..8) { RankMedal(r, if (r <= 3) 44.dp else 36.dp); androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(10.dp)) }
                }
            }
        }
    }

    /** 4.-10. sıralar (unvan rozetleri satırdan taşmamalı) */
    @Test
    fun leaderboardLower() {
        val names = listOf("Yılmaz", "ZeroOne", "Kaan", "Ateş", "Demir", "Fırtına", "Ayaz")
        val rows = org.json.JSONArray()
        names.forEachIndexed { k, n ->
            val i = k + 3
            rows.put(JSONObject().put("rank", i + 1).put("id", 100 + i).put("name", n).put("avatar", i % 6).put("frame", "")
                .put("photo", "").put("v", 52000L - i * 4100).put("clan", if (i % 3 == 0) "KRT" else "")
                .put("title", title("kills", i + 1)).put("banned", false).put("vip", false))
        }
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { BoardDialog({}, rows) } } }
    }

    @Test
    fun playerProfile() {
        val p = JSONObject().put("id", 1234567).put("name", "Karanlık").put("avatar", 0).put("frame", "gold").put("photo", fakePhoto()).put("vip", true)
            .put("level", 37).put("kills", 52310).put("hours", 214).put("online", true).put("title", title("kills", 1))
            .put("titles", org.json.JSONArray().put(title("kills", 1)).put(title("pvp", 3)).put(title("time", 7)))
            .put("stats", JSONObject().put("titan", 41).put("soldiers", 1820).put("bases", 12).put("dog", 3400).put("crafted", 980))
            .put("clan", JSONObject().put("name", "KRT").put("rank_name", "Lider").put("title", "Efsanevi Klan"))
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { PlayerProfileView(p, "", false, {}, {}) {} } } }
    }

    @Test
    fun playerProfileBanned() {
        val p = JSONObject().put("id", 7654321).put("name", "Hileci").put("avatar", 3).put("frame", "").put("level", 12).put("kills", 900).put("hours", 8)
            .put("banned", true).put("stats", JSONObject())
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { PlayerProfileView(p, "", false, {}, {}) {} } } }
    }

    @Test
    fun profileEditorTitles() {
        val p = JSONObject().put("id", 1234567).put("name", "Karanlık").put("avatar", 0).put("frame", "gold").put("photo", fakePhoto()).put("level", 41).put("kills", 52310)
            .put("hours", 214).put("title", title("kills", 1)).put("titles", org.json.JSONArray().put(title("kills", 1)).put(title("pvp", 3)).put(title("time", 7)))
        paparazzi.snapshot { SceneBackground("bg_lobby", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { ProfileEditorView(p, {}, {}) { _, _ -> } } } }
    }

    @Test
    fun passRewards() {
        val now = 1_793_000_000_000L
        paparazzi.snapshot { PassView(sample("pass"), "Sezon 1: Kan Ve Pas", now + 6_480_000_000L, now, {}, {}, {}, { _, _, _ -> }) }
    }

    @Test
    fun passWeekly() {
        val now = 1_793_000_000_000L
        paparazzi.snapshot { PassView(sample("pass"), "Sezon 1: Kan Ve Pas", now + 6_480_000_000L, now, {}, {}, {}, { _, _, _ -> }, startTab = 2) }
    }

    @Test
    fun crateMain() {
        paparazzi.snapshot { CratePreview(sample("skins")) }
    }

    @Test
    fun crateReveal() {
        paparazzi.snapshot { CratePreview(sample("skins"), reveal = listOf("sk_frost", "medkit:5", "gold:1000", "potion_power:3", "vs_camo", "dark_crystal:2", "potion_heal:3", "chest_military:1", "titan_shard:2", "gold:1000")) }
    }

    @Test
    fun crateCollection() {
        paparazzi.snapshot { CratePreview(sample("skins"), tab = 1) }
    }

    @Test
    fun eventScreen() {
        val now = 1_793_000_000_000L
        val top = org.json.JSONArray()
        listOf("Karanlık", "Bozkurt", "Gölge", "Yılmaz", "ZeroOne", "Kaan", "Ateş", "Demir", "Fırtına", "Ayaz", "Toprak", "Bora").forEachIndexed { i, n ->
            top.put(JSONObject().put("name", n).put("id", 100 + i).put("score", 18450L - i * 1230))
        }
        val b = JSONObject().put("active", true).put("kind", "blood_moon").put("title", "Kan Ayı").put("ends", now + 9 * 86_400_000L + 4 * 3_600_000L)
            .put("my_score", 7390).put("my_rank", 23).put("top", top)
        paparazzi.snapshot { EventView(b, "", now, {}) }
    }

    @Test
    fun framesAndAvatars() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF1E1D19)).padding(18.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                for (row in FRAMES.chunked(3)) androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(40.dp)) {
                    row.forEach { f -> ProfileAvatar(listOf(1, 2, 3, 4, 5, 16)[FRAMES.indexOf(f) % 6], f.id, 120.dp) }
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    for (a in listOf(1, 2, 3, 4, 5, 16)) ProfileAvatar(a, "", 90.dp)
                }
            }
        }
    }

    @Test
    fun framesLarge() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF1E1D19)).padding(40.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(60.dp),
            ) {
                ProfileAvatar(16, "blood", 220.dp)
                ProfileAvatar(4, "lv50", 220.dp)
                ProfileAvatar(5, "season1", 220.dp)
            }
        }
    }

    @Test
    fun playerMenu() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A4A2A))) {
                PlayerMenu("a42", "Bozkurt", {}, {}, {})
            }
        }
    }

    // ---- V16 kodla çizilen canlı sahneler
    private fun scene(kind: SceneKind, t: Float) = paparazzi.snapshot {
        LiveScene(kind, still = t) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterStart).padding(start = 54.dp),
            ) { CodeLogo(150.dp) }
        }
    }
    @Test fun liveSceneLobby() = scene(SceneKind.LOBBY, 7.3f)
    @Test fun liveSceneLobbyLater() = scene(SceneKind.LOBBY, 41.9f)
    @Test fun liveSceneEmber() = scene(SceneKind.EMBER, 12.1f)
    @Test fun liveSceneIce() = scene(SceneKind.ICE, 20.5f)
    @Test fun liveSceneVault() = scene(SceneKind.VAULT, 5.2f)

    /** Gerçek lobi ekranı (canlı sahne + logo + oyuncu kartı) */
    @Test fun lobbyScreenLive() {
        org.junit.Assume.assumeTrue("libzenvex.so yok", runCatching { com.zenvex.online.game.Native.hashCode(); true }.getOrDefault(false))
        com.zenvex.online.ui.Demo.on = true
        com.zenvex.online.game.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.game.Account.previewLogin(1234567L)
        paparazzi.snapshot { LobbyScreen({}, 0) }
    }
}
