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
            WorldMapView(img, fog, w, me.getDouble("x").toFloat(), me.getDouble("y").toFloat(), 3, marks, {}, {})
        }
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
            WorldMapView(img, null, w, b.getDouble("x").toFloat(), b.getDouble("y").toFloat(), 30, mapMarks(w, JSONObject()) + baseMarks(all), {}, {})
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
        paparazzi.snapshot { PassView(sample("pass"), "Sezon 1: Kan Ve Pas", now + 6_480_000_000L, now, {}, {}, { _, _ -> }, { _, _, _ -> }) }
    }

    @Test
    fun passWeekly() {
        val now = 1_793_000_000_000L
        paparazzi.snapshot { PassView(sample("pass"), "Sezon 1: Kan Ve Pas", now + 6_480_000_000L, now, {}, {}, { _, _ -> }, { _, _, _ -> }, startTab = 2) }
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
}
