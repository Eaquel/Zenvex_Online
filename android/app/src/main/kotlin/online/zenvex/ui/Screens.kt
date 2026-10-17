package online.zenvex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import online.zenvex.BuildConfig
import online.zenvex.game.Assets
import online.zenvex.game.GameSession
import online.zenvex.game.Native
import org.json.JSONObject

enum class Screen { Splash, Title, Lobby, Character, Skills, Inventory, Shop, Profile, Servers, Settings, Loading, Game }

@Composable
fun Background(name: String, dim: Float = 0f) {
    val ctx = LocalContext.current
    val bmp = remember(name) { runCatching { Assets.get(ctx).bmp("bg/$name.png") }.getOrNull() }
    Box(Modifier.fillMaxSize().background(Pal.NavyDark)) {
        if (bmp != null) Image(
            remember(bmp) { bmp.asImageBitmap() }, null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, filterQuality = FilterQuality.None,
        )
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

// ------------------------------------------------------------------ açılış
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < 1.8f) { t = (withFrameNanos { it } - start) / 1e9f }
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Pal.NavyDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelIcon("fist", 72.dp)
            Spacer(Modifier.height(12.dp))
            PText("ZENVEX STUDIO", 18.sp, Pal.Cream.copy(alpha = (t * 2f).coerceIn(0f, 1f)))
        }
    }
}

@Composable
fun Logo(size: Float = 1f) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PText("ZENVEX", (54 * size).sp, Pal.MustardLight, bold = true)
        Box(Modifier.pixelFrame(Pal.RedDark, Pal.Red, Pal.RedDeep).padding(horizontal = 18.dp, vertical = 2.dp)) {
            PText("ONLINE", (18 * size).sp, Pal.White, bold = true)
        }
    }
}

@Composable
fun TitleScreen(onGuest: () -> Unit, onSettings: () -> Unit) {
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(600); blink = !blink } }
    Box(Modifier.fillMaxSize()) {
        Background("title_bg", 0.25f)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo()
            Spacer(Modifier.height(24.dp))
            PixelButton("Misafir Olarak Oyna", Modifier.width(280.dp), BtnStyle.Primary, icon = "play", textSize = 18.sp, onClick = onGuest)
            Spacer(Modifier.height(10.dp))
            PixelButton("Google ile Giriş", Modifier.width(280.dp), icon = "globe", locked = true) {}
            Spacer(Modifier.height(10.dp))
            PixelButton("Hesap Oluştur", Modifier.width(280.dp), icon = "user", locked = true) {}
            Spacer(Modifier.height(14.dp))
            if (blink) PText("Oynamak için dokun", 12.sp, Pal.Cream)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            PixelButton("TR", icon = "globe") { Toaster.show("İngilizce dil desteği yakında!", "globe") }
            Spacer(Modifier.width(8.dp))
            PixelButton("", icon = "gear", onClick = onSettings)
        }
        PText("Sürüm ${BuildConfig.VERSION_NAME}", 11.sp, Pal.Gray, Modifier.align(Alignment.BottomStart).padding(12.dp))
        PText("Kullanım Şartları · Gizlilik", 11.sp, Pal.Gray,
            Modifier.align(Alignment.BottomCenter).padding(12.dp).clickable { Toaster.show("Metinler yakında eklenecek.", "scroll") })
    }
}

// ------------------------------------------------------------------ lobi
/** Lobide büyük, döndürülebilir karakter (8 yön, nefes animasyonu). */
@Composable
fun LobbyCharacter(modifier: Modifier, dirState: Int = 0, onDir: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    val assets = remember { Assets.get(ctx) }
    val m = remember { assets.manifest.getJSONObject("lobby_player") }
    val sheet = remember { assets.bmp(m.getString("file")).asImageBitmap() }
    val fw = m.getJSONArray("frame").getInt(0)
    val fh = m.getJSONArray("frame").getInt(1)
    val frames = m.getInt("frames")
    var t by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) t = (withFrameNanos { it } - start) / 1e9f
    }
    Canvas(modifier.pointerInput(Unit) {
        detectHorizontalDragGestures { _, dx ->
            drag += dx
            if (drag > 40) { drag = 0f; onDir((dirState + 7) % 8) }
            if (drag < -40) { drag = 0f; onDir((dirState + 1) % 8) }
        }
    }) {
        val f = ((t * 5).toInt()) % frames
        // karakter karenin ~100 pikselini kaplar; tam sayı ölçek (keskin pikseller)
        val scale = minOf(size.height / 100f, size.width / 72f).toInt().coerceAtLeast(1).toFloat()
        val w = fw * scale; val h = fh * scale
        // gölge
        drawOval(Color(0x55172238), topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 26 * scale, size.height - 16 * scale), size = androidx.compose.ui.geometry.Size(52 * scale, 12 * scale))
        drawImage(
            sheet, srcOffset = IntOffset(f * fw, dirState * fh), srcSize = IntSize(fw, fh),
            dstOffset = IntOffset((size.width / 2 - w / 2).toInt(), (size.height - h).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.None,
        )
    }
}

@Composable
fun TopBar(onProfile: () -> Unit, onSettings: () -> Unit) {
    val hud = remember { GameSession.hud() }
    val prog = remember { GameSession.progress() }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.pixelFrame(Pal.Navy, Pal.NavyLight, Pal.NavyDark).clickable(onClick = onProfile).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon("user", 32.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PText(prog.optString("name", "Kaan"), 15.sp, Pal.White)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.pixelFrame(Pal.Mustard, Pal.MustardLight, Pal.MustardDark).padding(horizontal = 6.dp)) {
                        PText("Lv ${hud[Native.HUD_LEVEL].toInt()}", 11.sp, Pal.Outline, shadow = false)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Bar(hud[Native.HUD_XP] / hud[Native.HUD_XPNEXT].coerceAtLeast(1f), Pal.Cyan, Modifier.width(150.dp), 9.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        Currency("coin", "0")
        Spacer(Modifier.width(8.dp))
        Currency("gem", "0")
        Spacer(Modifier.width(12.dp))
        PixelButton("", icon = "mail", locked = true, badge = 1) {}
        Spacer(Modifier.width(8.dp))
        PixelButton("", icon = "gear", onClick = onSettings)
    }
}

@Composable
fun Currency(icon: String, amount: String) {
    Row(
        Modifier.pixelFrame(Pal.NavyDark, Pal.Navy, Pal.Night).padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon(icon, 22.dp)
        Spacer(Modifier.width(6.dp))
        PText(amount, 14.sp, Pal.White, Modifier.width(48.dp))
        Box(Modifier.clickable { Toaster.locked() }) { PixelIcon("plus", 20.dp) }
    }
}

@Composable
fun LobbyScreen(go: (Screen) -> Unit) {
    var dir by remember { mutableIntStateOf(0) }
    val points = remember { GameSession.hud()[Native.HUD_POINTS].toInt() }
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg")
        LobbyCharacter(Modifier.align(Alignment.Center).padding(top = 40.dp).size(260.dp), dir) { dir = it }
        Column(Modifier.fillMaxSize()) {
            TopBar(onProfile = { go(Screen.Profile) }, onSettings = { go(Screen.Settings) })
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Görevler", Modifier.width(150.dp), icon = "scroll", locked = true) {}
                    PixelButton("Etkinlikler", Modifier.width(150.dp), icon = "star", locked = true) {}
                    PixelButton("Sıralama", Modifier.width(150.dp), icon = "trophy", locked = true) {}
                    PixelButton("Posta", Modifier.width(150.dp), icon = "mail", locked = true) {}
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        Modifier.width(220.dp).height(90.dp).pixelFrame(Pal.RedDark, Pal.Red, Pal.RedDeep)
                            .clickable { Toaster.locked() }.padding(10.dp),
                    ) {
                        Column {
                            PText("ETKİNLİK", 12.sp, Pal.Cream)
                            PText("Zombi Avı Haftası", 16.sp, Pal.White, bold = true)
                            PText("Yakında başlıyor...", 11.sp, Pal.Cream)
                        }
                        PixelIcon("lock", 16.dp, Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.height(6.dp))
                    PText("Karakteri döndürmek için sürükle", 11.sp, Pal.Cream)
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Karakter", icon = "user", compact = true, textSize = 12.sp) { go(Screen.Character) }
                        PixelButton("Yetenek", icon = "skill", badge = points, compact = true, textSize = 12.sp) { go(Screen.Skills) }
                        PixelButton("Envanter", icon = "bag", compact = true, textSize = 12.sp) { go(Screen.Inventory) }
                        PixelButton("Mağaza", icon = "cart", compact = true, textSize = 12.sp) { go(Screen.Shop) }
                        PixelButton("Arkadaş", icon = "people", locked = true, compact = true, textSize = 12.sp) {}
                        PixelButton("Klan", icon = "shield", locked = true, compact = true, textSize = 12.sp) {}
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.width(380.dp).pixelFrame(Pal.NavyDark.copy(alpha = 0.85f), Pal.Navy, Pal.Night)
                            .clickable { Toaster.locked() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelIcon("chat", 18.dp)
                        Spacer(Modifier.width(8.dp))
                        PText("[Dünya] Sohbet yakında açılacak...", 12.sp, Pal.Gray)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    PixelButton("Başlangıç Ormanı", icon = "map", textSize = 12.sp) { go(Screen.Servers) }
                    Spacer(Modifier.height(8.dp))
                    PlayButton { go(Screen.Loading) }
                }
            }
        }
    }
}

@Composable
fun PlayButton(onClick: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val s = withFrameNanos { it }; while (true) t = (withFrameNanos { it } - s) / 1e9f }
    val pulse = 1f + 0.04f * kotlin.math.sin(t * 4f)
    PixelButton(
        "OYNA", Modifier.width((200 * pulse).dp).height((64 * pulse).dp), BtnStyle.Primary,
        icon = "play", textSize = 26.sp, onClick = onClick,
    )
}

// ------------------------------------------------------------------ karakter
@Composable
fun CharacterScreen(back: () -> Unit) {
    val hud = remember { GameSession.hud() }
    val p = remember { GameSession.progress() }
    var dir by remember { mutableIntStateOf(1) }
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg", 0.55f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Karakter", back)
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
                PixelPanel(Modifier.weight(1f).fillMaxHeight()) {
                    PText(p.optString("name", "Kaan"), 20.sp, Pal.Cream, bold = true)
                    PText("Hayatta Kalan · Seviye ${hud[Native.HUD_LEVEL].toInt()}", 12.sp, Pal.Gray)
                    LobbyCharacter(Modifier.fillMaxWidth().weight(1f), dir) { dir = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Kıyafet", icon = "shield", locked = true, textSize = 12.sp) {}
                        PixelButton("Görünüm", icon = "user", locked = true, textSize = 12.sp) {}
                        PixelButton("İsim", icon = "scroll", locked = true, textSize = 12.sp) {}
                    }
                }
                Spacer(Modifier.width(12.dp))
                PixelPanel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    PText("İstatistikler", 16.sp, Pal.Cream)
                    Spacer(Modifier.height(8.dp))
                    StatRow("Can", "${hud[Native.HUD_MAXHP].toInt()}")
                    StatRow("Dayanıklılık", "${hud[Native.HUD_MAXST].toInt()}")
                    StatRow("Yumruk hasarı", String.format("%.1f", (8f + 0.6f * (hud[Native.HUD_LEVEL] - 1)) * (1f + 0.06f * skillLvl(0))))
                    StatRow("Kritik şansı", "%${5 + 2 * skillLvl(1)}")
                    StatRow("Öldürülen zombi", "${p.optInt("kills")}")
                    StatRow("Kesilen ağaç", "${p.optInt("trees")}")
                    StatRow("Ölüm", "${p.optInt("deaths")}")
                    StatRow("Oynama süresi", formatTime(p.optDouble("play_time", 0.0)))
                    Spacer(Modifier.height(10.dp))
                    PText("Ekipman", 16.sp, Pal.Cream)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Kafa", "Gövde", "Bacak", "Ayak", "Eldiven", "Çanta").forEach { EquipSlot(it) }
                    }
                }
            }
        }
    }
}

private fun skillLvl(id: Int): Int = GameSession.progress().optJSONArray("skills")?.optInt(id) ?: 0

fun formatTime(sec: Double): String {
    val s = sec.toLong()
    return if (s >= 3600) "${s / 3600} sa ${(s % 3600) / 60} dk" else "${s / 60} dk ${s % 60} sn"
}

@Composable
fun StatRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        PText(k, 13.sp, Pal.Gray)
        Spacer(Modifier.weight(1f))
        PText(v, 13.sp, Pal.White)
    }
}

@Composable
fun EquipSlot(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).pixelFrame(Pal.NavyDark, Pal.Navy, Pal.Night).clickable { Toaster.locked() },
            contentAlignment = Alignment.Center,
        ) { PixelIcon("lock", 16.dp) }
        PText(name, 9.sp, Pal.Gray)
    }
}

// ------------------------------------------------------------------ yetenekler
@Composable
fun SkillsScreen(back: () -> Unit) {
    var data by remember { mutableStateOf(GameSession.skills()) }
    val skills = data.getJSONArray("skills")
    val branches = listOf("Power" to ("Güç" to Pal.Red), "Endurance" to ("Dayanıklılık" to Pal.Green),
        "Agility" to ("Çeviklik" to Pal.Cyan), "Woodcutting" to ("Odunculuk" to Pal.Mustard))
    var selected by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg", 0.6f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Yetenekler", back) {
                Box(Modifier.pixelFrame(Pal.Mustard, Pal.MustardLight, Pal.MustardDark).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    PText("Yetenek Puanı: ${data.getInt("points")}", 14.sp, Pal.Outline, shadow = false)
                }
                Spacer(Modifier.width(8.dp))
                PixelButton("Sıfırla", locked = true, textSize = 12.sp) {}
            }
            Row(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((key, info) in branches) {
                    PixelPanel(Modifier.weight(1f).fillMaxHeight()) {
                        PText(info.first, 15.sp, info.second, bold = true)
                        Spacer(Modifier.height(8.dp))
                        for (i in 0 until skills.length()) {
                            val s = skills.getJSONObject(i)
                            if (s.getString("branch") != key) continue
                            SkillCard(s, selected == s.getInt("id"), info.second) { selected = s.getInt("id") }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            val s = skills.getJSONObject(selected)
            Row(
                Modifier.fillMaxWidth().padding(12.dp).pixelFrame(Pal.NavyDark, Pal.Navy, Pal.Night).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(s.getString("icon"), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    PText("${s.getString("name")}  (${s.getInt("lvl")}/${s.getInt("max")})", 15.sp, Pal.Cream)
                    PText(s.getString("desc") + " · Gereken seviye: ${s.getInt("req")}", 12.sp, Pal.Gray)
                }
                val can = s.getBoolean("can")
                PixelButton(if (can) "Yükselt" else s.getString("why").ifEmpty { "Maks" }, style = if (can) BtnStyle.Primary else BtnStyle.Secondary, enabled = can) {
                    if (GameSession.upgradeSkill(s.getInt("id"))) {
                        Toaster.show("${s.getString("name")} yükseltildi!", "skill")
                        data = GameSession.skills()
                    }
                }
            }
        }
    }
}

@Composable
fun SkillCard(s: JSONObject, selected: Boolean, color: Color, onClick: () -> Unit) {
    val lvl = s.getInt("lvl"); val max = s.getInt("max")
    val unlocked = lvl > 0 || s.getBoolean("can")
    Row(
        Modifier.fillMaxWidth()
            .pixelFrame(if (selected) Pal.NavyLight else Pal.NavyDark, if (selected) color else Pal.Navy, Pal.Night)
            .clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PixelIcon(s.getString("icon"), 28.dp)
            if (!unlocked) PixelIcon("lock", 14.dp, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(6.dp))
        Column {
            PText(s.getString("name"), 11.sp, if (unlocked) Pal.White else Pal.Gray)
            Bar(lvl / max.toFloat(), color, Modifier.width(80.dp), 7.dp)
        }
    }
}

// ------------------------------------------------------------------ envanter
@Composable
fun InventoryScreen(back: () -> Unit) {
    val p = remember { GameSession.progress() }
    val items = listOf(
        Triple("wood", "Odun", p.optInt("wood")),
        Triple("skill", "Zombi Dişi", p.optInt("zombie_teeth")),
    ).filter { it.third > 0 }
    var sel by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg", 0.6f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Envanter", back) {
                PixelButton("Sırala", icon = "bag", textSize = 12.sp) { Toaster.show("Envanter sıralandı.", "bag") }
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                PixelPanel(Modifier.weight(1.4f).fillMaxHeight()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tümü", "Malzeme", "Ekipman", "Tüketilebilir").forEachIndexed { i, n ->
                            PixelButton(n, style = if (tab == i) BtnStyle.Primary else BtnStyle.Secondary, textSize = 11.sp,
                                locked = i >= 2) { tab = i }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    for (r in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0 until 7) {
                                val idx = r * 7 + c
                                val it = items.getOrNull(idx)
                                Box(
                                    Modifier.size(50.dp)
                                        .pixelFrame(if (idx == sel && it != null) Pal.NavyLight else Pal.NavyDark,
                                            if (idx == sel && it != null) Pal.MustardLight else Pal.Navy, Pal.Night)
                                        .clickable { if (it != null) sel = idx },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (it != null) {
                                        PixelIcon(it.first, 30.dp)
                                        PText("${it.third}", 10.sp, Pal.White, Modifier.align(Alignment.BottomEnd).padding(3.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                PixelPanel(Modifier.weight(1f).fillMaxHeight()) {
                    val it = items.getOrNull(sel)
                    if (it == null) {
                        PText("Çantan boş.", 16.sp, Pal.Cream)
                        PText("Ağaçlara yumruk atarak odun, zombileri yenerek diş toplayabilirsin.", 12.sp, Pal.Gray)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelIcon(it.first, 48.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                PText(it.second, 18.sp, Pal.Cream)
                                PText("Malzeme · x${it.third}", 12.sp, Pal.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        PText(
                            if (it.second == "Odun") "Ağaçlardan toplanır. İleride inşa ve üretimde kullanılacak."
                            else "Zombilerden düşer. İleride takas ve üretimde kullanılacak.", 12.sp, Pal.White,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Kullan", locked = true) {}
                            PixelButton("At", style = BtnStyle.Danger, locked = true) {}
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ mağaza
@Composable
fun ShopScreen(back: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg", 0.6f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Mağaza", back) { Currency("coin", "0"); Spacer(Modifier.width(8.dp)); Currency("gem", "0") }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Öne Çıkan", "Kostüm", "Elmas", "Paketler").forEachIndexed { i, n ->
                    PixelButton(n, style = if (tab == i) BtnStyle.Primary else BtnStyle.Secondary) { tab = i }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.weight(1f).height(200.dp).pixelFrame(Pal.NavyDark, Pal.Navy, Pal.Night).clickable { Toaster.locked() }.padding(10.dp),
                    ) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelIcon(listOf("shield", "user", "gem", "bag")[i], 56.dp)
                            Spacer(Modifier.height(8.dp))
                            PText(listOf("Hayatta Kalan Paketi", "Gece Kapüşonu", "100 Elmas", "Başlangıç Seti")[i], 13.sp, Pal.Cream, align = TextAlign.Center)
                        }
                        Box(Modifier.align(Alignment.BottomCenter).pixelFrame(Pal.Mustard, Pal.MustardLight, Pal.MustardDark).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            PText("YAKINDA", 12.sp, Pal.Outline, shadow = false)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ profil / sunucular / yükleme
@Composable
fun ProfileScreen(back: () -> Unit, onReset: () -> Unit) {
    val p = remember { GameSession.progress() }
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg", 0.6f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Profil", back)
            PixelPanel(Modifier.padding(horizontal = 16.dp).width(460.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon("user", 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        PText(p.optString("name", "Kaan"), 20.sp, Pal.Cream)
                        PText("Misafir Hesabı · ID: ZX-${GameSession.WORLD_SEED % 100000}", 12.sp, Pal.Gray)
                    }
                }
                Spacer(Modifier.height(10.dp))
                StatRow("Seviye", "${p.optInt("level", 1)}")
                StatRow("Öldürülen zombi", "${p.optInt("kills")}")
                StatRow("Toplam odun", "${p.optInt("wood")}")
                StatRow("Oynama süresi", formatTime(p.optDouble("play_time", 0.0)))
                PText("Rozetler: henüz yok", 12.sp, Pal.Gray)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Hesabı Bağla", icon = "globe", locked = true) {}
                    PixelButton(if (confirm) "Emin misin? Tekrar bas" else "İlerlemeyi Sil", style = BtnStyle.Danger) {
                        if (confirm) { onReset(); confirm = false } else confirm = true
                    }
                }
            }
        }
    }
}

@Composable
fun ServersScreen(back: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Background("lobby_bg", 0.6f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Dünya Seç", back)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServerCard("Başlangıç Ormanı", "Tek Oyunculu · Kolay", "—", true, back)
                ServerCard("TR-1", "Online · Orta", "— ms", false) {}
                ServerCard("EU-1", "Online · Zor", "— ms", false) {}
            }
        }
    }
}

@Composable
fun ServerCard(name: String, info: String, ping: String, open: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(220.dp).pixelFrame(if (open) Pal.NavyLight else Pal.NavyDark, if (open) Pal.Green else Pal.Navy, Pal.Night)
            .clickable { if (open) onClick() else Toaster.locked() }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(if (open) "map" else "lock", 24.dp)
            Spacer(Modifier.width(8.dp))
            PText(name, 16.sp, Pal.Cream)
        }
        PText(info, 12.sp, Pal.Gray)
        PText("Ping: $ping · Oyuncu: ${if (open) "1" else "—"}", 11.sp, Pal.Gray)
        if (open) PText("SEÇİLİ", 12.sp, Pal.Green)
    }
}

private val TIPS = listOf(
    "Saldırı joystickini çevir: karakterin o yöne yumruk atar.",
    "Ağaçlara yumruk atarak odun ve XP toplayabilirsin.",
    "Koşmak dayanıklılık harcar. Sarı bar biterse yavaşlarsın.",
    "Haritanın kenarlarına yaklaştıkça zombiler güçlenir.",
    "Her seviyede yetenek puanı kazanırsın. Her 5 seviyede bir fazladan!",
    "Savaştan uzak durursan canın yavaş yavaş dolar.",
)

@Composable
fun LoadingScreen(onDone: () -> Unit) {
    var p by remember { mutableFloatStateOf(0f) }
    val tip = remember { TIPS.random() }
    LaunchedEffect(Unit) {
        while (p < 1f) { delay(30); p += 0.025f }
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        Background("title_bg", 0.45f)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Logo(0.8f) }
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PText("İpucu: $tip", 13.sp, Pal.Cream)
            Spacer(Modifier.height(10.dp))
            Bar(p, Pal.Mustard, Modifier.width(420.dp), 16.dp, "Başlangıç Ormanı yükleniyor... %${(p * 100).toInt()}")
        }
    }
}
