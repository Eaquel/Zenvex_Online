package online.zenvex.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import online.zenvex.game.Atlas
import online.zenvex.game.GameSession
import online.zenvex.game.Online
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { Splash, Title, Lobby, Character, Skills, Inventory, Craft, Shop, Quests, Pass, Cosmetics, Profile, Chat, Settings, Loading, Game }

fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else List(length()) { optJSONObject(it) ?: JSONObject() }

// =========================================================================== açılış
@Composable
fun SplashScreen(done: () -> Unit) {
    val ctx = LocalContext.current
    val t = rememberTime()
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        // lobi görsellerini ve eşya simgelerini önceden çöz
        val keys = HERO_IDS.map { "l/$it/0_0" } + listOf("i/wood")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            keys.forEachIndexed { i, k -> runCatching { SpriteCache.get(ctx, k) }; progress = (i + 1f) / keys.size }
        }
        delay(400)
        done()
    }
    Box(Modifier.fillMaxSize()) {
        LobbyBackground(t, Pal.Red)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(t)
            Spacer(Modifier.height(30.dp))
            Bar(progress, Pal.Gold, Modifier.width(320.dp), 8.dp)
            Spacer(Modifier.height(8.dp))
            T("Dünya hazırlanıyor… %${(progress * 100).toInt()}", 12.sp, Pal.Sub)
        }
    }
}

@Composable
fun Logo(t: Float) {
    val glow = 0.6f + 0.4f * kotlin.math.sin(t * 2f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T("ZENVEX", 74.sp, Color(0xFFF4ECDC), display = true, glow = Color(0xFFFF5A2A).copy(alpha = glow))
        T("O N L I N E", 22.sp, Pal.Gold, display = true, glow = Pal.Gold.copy(alpha = 0.5f))
    }
}

@Composable
fun TitleScreen(onPlay: () -> Unit, onSettings: () -> Unit) {
    val t = rememberTime()
    val prof = remember { GameSession.ui("profile") }
    var name by remember { mutableStateOf(prof.optString("name")) }
    Box(Modifier.fillMaxSize()) {
        LobbyBackground(t, Pal.Red)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(t)
            Spacer(Modifier.height(26.dp))
            Panel(Modifier.width(380.dp)) {
                T("Hayatta kalan adın", 12.sp, Pal.Sub)
                Spacer(Modifier.height(6.dp))
                Field(name, { if (it.length <= 16) name = it }, "İsim", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                GButton("MİSAFİR OLARAK OYNA", Modifier.fillMaxWidth(), Tone.Gold, icon = "play") {
                    if (name.trim() != prof.optString("name")) {
                        val r = GameSession.cmd("set_name", "name" to name)
                        if (!r.ok) { Toaster.show(r.msg, false); return@GButton }
                    }
                    onPlay()
                }
                Spacer(Modifier.height(8.dp))
                GButton("Google ile Giriş", Modifier.fillMaxWidth(), Tone.Dark, icon = "globe") {
                    Toaster.show("Google girişi yakında. Şimdilik misafir hesabın cihazda saklanıyor.", false)
                }
            }
        }
        RoundIcon("gear", Modifier.align(Alignment.TopEnd).padding(16.dp)) { onSettings() }
        T("© 2026 Zenvex · Tüm görseller kodla üretildi", 10.sp, Pal.Sub.copy(alpha = 0.6f), Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    Box(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC060A12)).border(1.dp, Pal.Line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) T(hint, 14.sp, Pal.Sub)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Pal.Text, fontSize = 15.sp, fontFamily = Body),
            cursorBrush = SolidColor(Pal.Gold), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }))
    }
}

val TIPS = listOf(
    "İpucu: Saldırı joystiğini çevirdiğin yöne vurursun; bırakınca durursun.",
    "İpucu: Seviye 10'da şehir kapısı açılır. Şehirdeki güvenli bölgede keskin nişancılar seni korur.",
    "İpucu: Toksik zombiler öldüklerinde zehir bulutu bırakır. Panzehir taşı!",
    "İpucu: Karanlık zombiler geceleri daha sık çıkar ve ışınlanır.",
    "İpucu: Çalışma tezgâhında kask, yelek, bot ve çanta üretebilirsin.",
    "İpucu: Titan'ı yenersen Titan Parçası düşer: efsanevi Titan setinin malzemesi.",
    "İpucu: Araçlar yakıt harcar; çantadaki benzin otomatik kullanılır.",
)

@Composable
fun LoadingScreen(done: () -> Unit) {
    val ctx = LocalContext.current
    val t = rememberTime()
    var p by remember { mutableStateOf(0f) }
    val tip = remember { TIPS.random() }
    LaunchedEffect(Unit) {
        val ch = GameSession.ui("profile").optInt("character")
        val groups = listOf("world", "items", "gear", "vehicles", "npcs", "hero_${HERO_IDS[ch]}", "zombie_walker", "zombie_runner", "zombie_dog",
            "zombie_toxic", "zombie_dark", "zombie_tank", "zombie_mystery")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            groups.forEachIndexed { i, g -> runCatching { Atlas.get(ctx).preload(g) }; p = (i + 1f) / groups.size }
        }
        Online.start(ctx, GameSession.ui("profile").optString("name"), GameSession.ui("profile").optString("tag_name"))
        delay(200)
        done()
    }
    Box(Modifier.fillMaxSize()) {
        LobbyBackground(t, Pal.Orange)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("YÜKLENİYOR", 22.sp, display = true)
            Spacer(Modifier.height(10.dp))
            Bar(p, Pal.Gold, Modifier.width(420.dp), 10.dp)
            Spacer(Modifier.height(10.dp))
            T(tip, 13.sp, Pal.Sub, align = TextAlign.Center)
        }
    }
}

// =========================================================================== karakterler
@Composable
fun CharacterScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("characters") }
    val prof = remember(tick) { GameSession.ui("profile") }
    ScreenFrame("KARAKTERLER", "user", back, right = { Chip("coin", fmt(data.optInt("gold")), Pal.Gold) }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            data.optJSONArray("chars").objs().forEachIndexed { i, c ->
                val col = HERO_COLORS[i]
                val unlocked = c.optBoolean("unlocked")
                Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp, if (c.optBoolean("selected")) col else null).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent))), contentAlignment = Alignment.BottomCenter) {
                        SpriteImage("l/${HERO_IDS[i]}/1_0", Modifier.fillMaxSize())
                        if (!unlocked) Icon("lock", 40.dp, Color.White, Modifier.align(Alignment.Center))
                    }
                    T(c.optString("name").uppercase(), 22.sp, display = true, glow = col.copy(alpha = 0.6f))
                    T(c.optString("class"), 12.sp, col, bold = true)
                    Spacer(Modifier.height(4.dp))
                    Bar(c.optInt("xp") / c.optInt("xp_next", 1).toFloat(), col, Modifier.fillMaxWidth(), 8.dp)
                    T("Seviye ${c.optInt("level")}/${c.optInt("max")}", 11.sp, Pal.Sub)
                    Spacer(Modifier.height(4.dp))
                    T(c.optString("ability"), 13.sp, Pal.Gold, bold = true)
                    T(c.optString("ability_desc"), 11.sp, Pal.Sub, align = TextAlign.Center, maxLines = 4)
                    Spacer(Modifier.height(6.dp))
                    when {
                        c.optBoolean("selected") -> GButton("SEÇİLİ", Modifier.fillMaxWidth(), Tone.Dark, icon = "check", height = 38.dp, size = 13.sp) {}
                        unlocked -> GButton("SEÇ", Modifier.fillMaxWidth(), Tone.Green, height = 38.dp, size = 13.sp) {
                            val r = GameSession.cmd("select_char", "i" to i); Toaster.show(r.msg, r.ok); tick++
                        }
                        else -> GButton("${fmt(c.optInt("gold"))}", Modifier.fillMaxWidth(), if (c.optBoolean("can_unlock")) Tone.Gold else Tone.Dark,
                            icon = "coin", height = 38.dp, size = 13.sp, sub = "Sv ${c.optInt("req")} gerekli") {
                            val r = GameSession.cmd("unlock_char", "i" to i); Toaster.show(r.msg, r.ok); tick++
                        }
                    }
                    if (i == 1 && unlocked) {
                        Spacer(Modifier.height(6.dp))
                        DroneTargets(prof) { tick++ }
                    }
                }
            }
        }
    }
}

@Composable
fun DroneTargets(prof: JSONObject, changed: () -> Unit) {
    val arr = prof.optJSONArray("drone_targets")
    val v = BooleanArray(3) { arr?.optBoolean(it, true) ?: true }
    val names = listOf("Normal", "Özel", "Boss")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEachIndexed { k, n ->
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (v[k]) Pal.Blue.copy(alpha = 0.5f) else Color(0x66000000))
                .clickable {
                    v[k] = !v[k]
                    GameSession.cmd(JSONObject().put("cmd", "drone_targets").put("targets", JSONArray(v.toList()))); changed()
                }.padding(horizontal = 8.dp, vertical = 4.dp)) { T(n, 10.sp, bold = v[k]) }
        }
    }
}

// =========================================================================== yetenekler
@Composable
fun SkillsScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("skills") }
    val branches = listOf("Power" to ("GÜÇ" to Pal.Red), "Endurance" to ("DAYANIKLILIK" to Pal.Green), "Agility" to ("ÇEVİKLİK" to Pal.Blue), "Survival" to ("HAYATTA KALMA" to Pal.Gold))
    ScreenFrame("YETENEKLER", "magic", back, right = {
        Chip("star", "${data.optInt("points")} puan", Pal.Gold)
        Spacer(Modifier.width(10.dp))
        GButton("Sıfırla", tone = Tone.Red, icon = "refresh", height = 38.dp, size = 13.sp, sub = "${data.optInt("reset_cost")} altın") {
            val r = GameSession.cmd("reset_skills"); Toaster.show(r.msg, r.ok); tick++
        }
    }) {
        val skills = data.optJSONArray("skills").objs()
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((b, meta) in branches) {
                val (title, col) = meta
                Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp, col).padding(10.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(title, 16.sp, col, display = true)
                    for (s in skills.filter { it.optString("branch") == b }) {
                        val lvl = s.optInt("lvl"); val max = s.optInt("max")
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x55000000)).padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(col.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(skillIcon(s.optString("icon")), 20.dp, col) }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    T(s.optString("name"), 13.sp, bold = true)
                                    T("$lvl / $max", 11.sp, Pal.Sub)
                                }
                            }
                            T(s.optString("desc"), 11.sp, Pal.Sub)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(max) { k -> Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (k < lvl) col else Color(0x40FFFFFF))) }
                            }
                            Spacer(Modifier.height(6.dp))
                            if (lvl < max) GButton(if (s.optBoolean("can")) "GELİŞTİR" else s.optString("why").ifEmpty { "Sv ${s.optInt("req")}" },
                                Modifier.fillMaxWidth(), if (s.optBoolean("can")) Tone.Green else Tone.Dark, height = 32.dp, size = 12.sp) {
                                val r = GameSession.cmd("upgrade_skill", "id" to s.optInt("id")); if (!r.ok) Toaster.show(r.msg, false); tick++
                            } else T("EN YÜKSEK", 12.sp, col, bold = true)
                        }
                    }
                }
            }
        }
    }
}

fun skillIcon(i: String) = when (i) { "fist" -> "fist"; "heart" -> "heart"; "shield" -> "shield"; "bolt" -> "bolt"; "wood" -> "wood"; "gem" -> "gem"; "coin" -> "coin"; "plus" -> "plus"; "play" -> "play"; else -> "star" }

// =========================================================================== envanter
@Composable
fun InventoryScreen(back: () -> Unit, inGame: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    val inv = remember(tick) { GameSession.ui("inventory") }
    var sel by remember { mutableStateOf<Int?>(null) }
    var selEq by remember { mutableStateOf<String?>(null) }
    val slots = inv.optJSONArray("slots")
    ScreenFrame("ENVANTER", "bag", back, right = {
        Chip("coin", fmt(inv.optInt("gold")), Pal.Gold)
        Spacer(Modifier.width(10.dp))
        GButton("Sırala", tone = Tone.Dark, icon = "refresh", height = 38.dp, size = 13.sp) { GameSession.cmd("sort"); tick++ }
    }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // ekipman
            val eq = inv.optJSONObject("eq") ?: JSONObject()
            Column(Modifier.width(270.dp).fillMaxHeight().glass(20.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionTitle("Ekipman")
                val prof = remember(tick) { GameSession.ui("profile") }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, label) in listOf("helmet" to "Kask", "jacket" to "Ceket", "boots" to "Bot")) EqSlot(eq, k, label, selEq == k) { selEq = k; sel = null }
                    }
                    Box(Modifier.width(110.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        SpriteImage("l/${HERO_IDS.getOrElse(prof.optInt("character")) { "kaan" }}/0_0", Modifier.fillMaxSize())
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, label) in listOf("weapon" to "Silah", "bag" to "Çanta", "cloak" to "Pelerin")) EqSlot(eq, k, label, selEq == k) { selEq = k; sel = null }
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("shield", "${inv.optDouble("armor").toInt()}", "Zırh", Pal.Blue)
                    Stat("heart", "${inv.optDouble("max_hp").toInt()}", "Can", Pal.Red)
                    Stat("sword", "×${"%.2f".format(inv.optDouble("dmg"))}", "Hasar", Pal.Orange)
                    Stat("bolt", "×${"%.2f".format(inv.optDouble("speed"))}", "Hız", Pal.Green)
                }
            }
            // ızgara
            @OptIn(ExperimentalLayoutApi::class)
            Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp).padding(12.dp)) {
                val used = (0 until (slots?.length() ?: 0)).count { !slots!!.isNull(it) }
                SectionTitle("Çanta  $used / ${inv.optInt("cap")}")
                FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until (slots?.length() ?: 0)) {
                        val s = slots!!.optJSONObject(i)
                        ItemSlot(s?.optString("id"), s?.optInt("count") ?: 0, s?.optInt("rarity") ?: 0, 62.dp, sel == i) { if (s != null) { sel = i; selEq = null } }
                    }
                }
            }
            // ayrıntı
            Column(Modifier.width(250.dp).fillMaxHeight().glass(20.dp).padding(12.dp)) {
                val item: JSONObject? = sel?.let { slots?.optJSONObject(it) } ?: selEq?.let { eq.optJSONObject(it) }
                if (item == null) {
                    T("Bir eşya seç", 14.sp, Pal.Sub, Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp))
                } else {
                    ItemDetail(item)
                    Spacer(Modifier.weight(1f))
                    if (sel != null) {
                        val kind = item.optString("kind")
                        val equippable = kind in listOf("Weapon", "Helmet", "Jacket", "Boots", "Bag", "Cloak")
                        val usable = kind == "Consumable" || item.optString("id") == "fuel"
                        if (equippable || usable) GButton(if (equippable) "KUŞAN" else "KULLAN", Modifier.fillMaxWidth(), Tone.Green, height = 42.dp) {
                            val r = GameSession.cmd("use", "slot" to sel!!); Toaster.show(r.msg, r.ok); sel = null; tick++
                        }
                        Spacer(Modifier.height(6.dp))
                        if (inGame) GButton("YERE BIRAK", Modifier.fillMaxWidth(), Tone.Red, icon = "trash", height = 38.dp, size = 13.sp) {
                            val r = GameSession.cmd("drop", "slot" to sel!!); Toaster.show(r.msg, r.ok); sel = null; tick++
                        }
                    } else if (selEq != null) {
                        GButton("ÇIKAR", Modifier.fillMaxWidth(), Tone.Dark, height = 42.dp) {
                            val r = GameSession.cmd("unequip", "slot" to selEq!!); Toaster.show(r.msg, r.ok); selEq = null; tick++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqSlot(eq: JSONObject, key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val it = eq.optJSONObject(key)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ItemSlot(it?.optString("id"), 0, it?.optInt("rarity") ?: 0, 58.dp, selected, label = label) { onClick() }
    }
}

@Composable
fun Stat(icon: String, v: String, label: String, col: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, 18.dp, col)
        T(v, 13.sp, bold = true)
        T(label, 10.sp, Pal.Sub)
    }
}

private val RARITY_NAMES = listOf("Sıradan", "Nadir", "Destansı", "Efsanevi", "Sezonluk")
private val KIND_NAMES = mapOf("Material" to "Malzeme", "Consumable" to "Tüketilebilir", "Ammo" to "Mermi", "Weapon" to "Silah", "Helmet" to "Kask",
    "Jacket" to "Ceket", "Boots" to "Bot", "Bag" to "Çanta", "Cloak" to "Pelerin", "Currency" to "Para")

@Composable
fun ItemDetail(item: JSONObject) {
    val r = item.optInt("rarity")
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemSlot(item.optString("id"), 0, r, 74.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            T(item.optString("name"), 16.sp, Pal.rarity(r), bold = true)
            T("${RARITY_NAMES.getOrElse(r) { "" }} · ${KIND_NAMES[item.optString("kind")] ?: ""}", 11.sp, Pal.Sub)
            if (item.optInt("count") > 1) T("Adet: ${item.optInt("count")}", 11.sp, Pal.Sub)
        }
    }
    Spacer(Modifier.height(8.dp))
    T(item.optString("desc"), 12.sp, Pal.Text)
    if (item.optDouble("armor", 0.0) > 0) T("Zırh +${item.optDouble("armor").toInt()}", 12.sp, Pal.Blue, bold = true)
    T("Değer: ${item.optInt("value")} altın", 11.sp, Pal.Gold)
}

// =========================================================================== üretim
@Composable
fun CraftScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("craft") }
    val recipes = data.optJSONArray("recipes").objs()
    var sel by remember { mutableIntStateOf(0) }
    ScreenFrame("ÜRETİM", "craft", back, right = {
        Chip("craft", if (data.optBoolean("near_bench")) "Tezgâh yakında" else "Tezgâh yok", if (data.optBoolean("near_bench")) Pal.Green else Pal.Sub)
    }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight().glass(20.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recipes.size) { i ->
                    val r = recipes[i]
                    val out = r.optJSONObject("out") ?: JSONObject()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel == i) Color(0x553A6AB0) else Color(0x33000000))
                        .clickable { sel = i }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemSlot(out.optString("id"), r.optInt("count"), out.optInt("rarity"), 46.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T(out.optString("name"), 14.sp, Pal.rarity(out.optInt("rarity")), bold = true)
                            T(if (r.optBoolean("bench")) "Tezgâh gerekli" else "Her yerde", 11.sp, Pal.Sub)
                        }
                        if (r.optBoolean("can")) Icon("check", 20.dp, Pal.Green)
                    }
                }
            }
            val r = recipes.getOrNull(sel)
            Column(Modifier.width(320.dp).fillMaxHeight().glass(20.dp).padding(14.dp)) {
                if (r != null) {
                    val out = r.optJSONObject("out") ?: JSONObject()
                    ItemDetail(out)
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Malzemeler")
                    for (inp in r.optJSONArray("inputs").objs()) {
                        val ok = inp.optInt("have") >= inp.optInt("need")
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemSlot(inp.optString("id"), 0, 0, 36.dp)
                            Spacer(Modifier.width(8.dp))
                            T(inp.optString("name"), 13.sp, modifier = Modifier.weight(1f))
                            T("${inp.optInt("have")} / ${inp.optInt("need")}", 13.sp, if (ok) Pal.Green else Pal.Red, bold = true)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GButton("ÜRET", Modifier.weight(1f), if (r.optBoolean("can")) Tone.Green else Tone.Dark, icon = "craft") {
                            val res = GameSession.cmd("craft", "i" to sel, "n" to 1); Toaster.show(res.msg, res.ok); tick++
                            if (res.ok) online.zenvex.game.Audio.play("craft")
                        }
                        GButton("×5", Modifier.width(80.dp), if (r.optBoolean("can")) Tone.Blue else Tone.Dark) {
                            val res = GameSession.cmd("craft", "i" to sel, "n" to 5); Toaster.show(res.msg, res.ok); tick++
                            if (res.ok) online.zenvex.game.Audio.play("craft")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================== tüccar / mağaza
@Composable
fun ShopScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("merchant") }
    val prof = remember(tick) { GameSession.ui("profile") }
    ScreenFrame("TÜCCAR", "shop", back, right = {
        Chip("coin", fmt(data.optInt("gold")), Pal.Gold); Spacer(Modifier.width(8.dp)); Chip("gem", fmt(prof.optInt("gems")), Pal.Gem)
    }) {
        Tabs(listOf("shop" to "Satın Al", "coin" to "Sat", "gem" to "Elmas"), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (o in data.optJSONArray("buy").objs()) {
                        val it = o.optJSONObject("item") ?: JSONObject()
                        val locked = o.optBoolean("locked")
                        Column(Modifier.width(150.dp).glass(16.dp, Pal.rarity(it.optInt("rarity"))).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ItemSlot(it.optString("id"), 0, it.optInt("rarity"), 70.dp)
                            T(it.optString("name"), 13.sp, Pal.rarity(it.optInt("rarity")), bold = true, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            if (locked) GButton("Sv ${o.optInt("req")}", Modifier.fillMaxWidth(), Tone.Dark, icon = "lock", height = 34.dp, size = 12.sp) {}
                            else GButton("${o.optInt("price")}", Modifier.fillMaxWidth(), if (o.optBoolean("afford")) Tone.Gold else Tone.Dark, icon = "coin", height = 34.dp, size = 13.sp) {
                                val r = GameSession.cmd("buy", "i" to o.optInt("i"), "n" to 1); Toaster.show(r.msg, r.ok); tick++
                                if (r.ok) online.zenvex.game.Audio.play("coin")
                            }
                        }
                    }
                }
            }
            1 -> {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val sell = data.optJSONArray("sell").objs()
                    if (sell.isEmpty()) T("Satacak eşyan yok.", 14.sp, Pal.Sub)
                    for (o in sell) {
                        val it = o.optJSONObject("item") ?: JSONObject()
                        Column(Modifier.width(150.dp).glass(16.dp).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ItemSlot(it.optString("id"), o.optInt("count"), it.optInt("rarity"), 70.dp)
                            T(it.optString("name"), 13.sp, bold = true, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            GButton("+${o.optInt("price")}", Modifier.fillMaxWidth(), Tone.Green, icon = "coin", height = 34.dp, size = 13.sp, sub = "tanesi") {
                                val r = GameSession.cmd("sell", "slot" to o.optInt("slot"), "n" to 1); Toaster.show(r.msg, r.ok); tick++
                            }
                        }
                    }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((n, p) in listOf(100 to "19,99 TL", 550 to "89,99 TL", 1200 to "179,99 TL", 2600 to "349,99 TL")) {
                    Column(Modifier.width(170.dp).glass(18.dp, Pal.Gem).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon("gem", 54.dp, Pal.Gem)
                        T("$n Elmas", 18.sp, Pal.Gem, bold = true)
                        Spacer(Modifier.height(8.dp))
                        GButton(p, Modifier.fillMaxWidth(), Tone.Blue, height = 38.dp, size = 14.sp) {
                            Toaster.show("Ödeme sistemi (Google Play) henüz bağlı değil.", false)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================== görevler
@Composable
fun QuestsScreen(back: () -> Unit) {
    val q = remember { GameSession.ui("quests") }
    ScreenFrame("GÖREVLER", "quest", back) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp, Pal.Gold).padding(14.dp)) {
                SectionTitle("Ana Görev")
                val m = q.optJSONObject("main")
                if (m == null) T("Tüm ana görevleri tamamladın. Efsanesin!", 15.sp, Pal.Green)
                else {
                    T("Bölüm ${m.optInt("index")} / ${m.optInt("total")}", 12.sp, Pal.Sub)
                    T(m.optString("title"), 20.sp, bold = true)
                    Spacer(Modifier.height(8.dp))
                    Bar(m.optInt("progress") / m.optInt("goal", 1).toFloat(), Pal.Gold, Modifier.fillMaxWidth(), 14.dp, "${m.optInt("progress")} / ${m.optInt("goal")}")
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Ödüller")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("coin", "${m.optInt("gold")}", Pal.Gold)
                        if (m.optInt("xp") > 0) Chip("star", "${m.optInt("xp")} XP", Pal.Blue)
                        Chip("crown", "${m.optInt("pass_xp")} Pass", Pal.Purple)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp, Pal.Blue).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Günlük Görevler (her gün yenilenir)")
                for (d in q.optJSONArray("daily").objs()) {
                    val done = d.optBoolean("done")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (done) Color(0x332E8A4A) else Color(0x44000000)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(d.optString("title"), 14.sp, if (done) Pal.Green else Pal.Text, Modifier.weight(1f), bold = true)
                            if (done) Icon("check", 20.dp, Pal.Green)
                        }
                        Bar(d.optInt("progress") / d.optInt("goal", 1).toFloat(), if (done) Pal.Green else Pal.Blue, Modifier.fillMaxWidth().padding(vertical = 4.dp), 9.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T("${d.optInt("progress")}/${d.optInt("goal")}", 11.sp, Pal.Sub)
                            T("+${d.optInt("gold")} altın", 11.sp, Pal.Gold)
                            T("+${d.optInt("xp")} XP", 11.sp, Pal.Blue)
                            T("+${d.optInt("pass_xp")} Pass", 11.sp, Pal.Purple)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================== sezon pass
@Composable
fun PassScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val p = remember(tick) { GameSession.ui("pass") }
    val t = rememberTime()
    ScreenFrame("SEZON PASS", "crown", back) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(p.optString("season"), 22.sp, Pal.Gold, display = true, glow = Pal.Gold.copy(alpha = 0.4f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Kademe ${p.optInt("tier")}/${p.optInt("tiers")}", 14.sp, bold = true)
                    Spacer(Modifier.width(10.dp))
                    Bar(p.optInt("xp") / p.optInt("xp_per_tier", 1).toFloat(), Pal.Gold, Modifier.width(220.dp), 10.dp, "${p.optInt("xp")} / ${p.optInt("xp_per_tier")}")
                }
                T("Sezon sonu ödülü: Tanrı Pelerini — ölümcül anda 3 sn ölümsüzlük, +%20 hız, +%25 hasar", 11.sp, Pal.Sub)
            }
            PassBuy("SEZON PASS", p.optString("price_normal"), p.optBoolean("normal"), Tone.Blue, "Tüm premium ödüller")
            Spacer(Modifier.width(10.dp))
            PassBuy("ELİT PASS", p.optString("price_elite"), p.optBoolean("elite"), Tone.Gold, "+10 kademe, özel çerçeve ve etiket")
        }
        Spacer(Modifier.height(10.dp))
        val premium = p.optBoolean("normal") || p.optBoolean("elite")
        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxHeight().padding(top = 26.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                T("ÜCRETSİZ", 12.sp, Pal.Sub, bold = true)
                T("PREMİUM", 12.sp, Pal.Gold, bold = true)
            }
            for (r in p.optJSONArray("rewards").objs()) {
                val tier = r.optInt("tier")
                val reached = r.optBoolean("reached")
                Column(Modifier.width(112.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (reached) Pal.Gold else Color(0x55FFFFFF)), contentAlignment = Alignment.Center) {
                        T("$tier", 11.sp, if (reached) Color(0xFF2A1A04) else Pal.Text, bold = true)
                    }
                    Spacer(Modifier.height(4.dp))
                    RewardCell(r.optJSONObject("free")!!, reached, r.optBoolean("free_claimed"), true, Modifier.weight(1f)) {
                        val res = GameSession.cmd("claim_pass", "tier" to tier, "premium" to false); Toaster.show(res.msg, res.ok); tick++
                    }
                    Spacer(Modifier.height(6.dp))
                    RewardCell(r.optJSONObject("premium")!!, reached, r.optBoolean("premium_claimed"), premium, Modifier.weight(1f), gold = true, t = t) {
                        val res = GameSession.cmd("claim_pass", "tier" to tier, "premium" to true); Toaster.show(res.msg, res.ok); tick++
                    }
                }
            }
        }
    }
}

@Composable
private fun PassBuy(title: String, price: String, owned: Boolean, tone: Tone, sub: String) {
    Column(Modifier.width(200.dp).glass(16.dp, if (tone == Tone.Gold) Pal.Gold else Pal.Blue).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(title, 15.sp, if (tone == Tone.Gold) Pal.Gold else Pal.Blue, display = true)
        T(sub, 10.sp, Pal.Sub, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        if (owned) GButton("SAHİPSİN", Modifier.fillMaxWidth(), Tone.Dark, icon = "check", height = 36.dp, size = 13.sp) {}
        else GButton(price, Modifier.fillMaxWidth(), tone, height = 36.dp, size = 14.sp) {
            val r = GameSession.cmd("buy_pass", "elite" to (tone == Tone.Gold)); Toaster.show(r.msg, r.ok)
        }
    }
}

@Composable
private fun RewardCell(r: JSONObject, reached: Boolean, claimed: Boolean, allowed: Boolean, modifier: Modifier, gold: Boolean = false, t: Float = 0f, claim: () -> Unit) {
    val col = Pal.rarity(r.optInt("rarity"))
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(if (gold) Color(0x55E8961C) else Color(0x55304060), Color(0xCC0A101A))))
            .border(1.dp, if (gold) Pal.Gold.copy(alpha = 0.5f) else Pal.Line, RoundedCornerShape(14.dp)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            when (r.optString("type")) {
                "gold" -> Icon("coin", 42.dp)
                "gem" -> Icon("gem", 42.dp)
                "cos" -> Icon(if (r.optString("kind") == "frame") "crown" else if (r.optString("kind") == "tag") "star" else "hanger", 38.dp, Pal.Legendary)
                else -> SpriteImage("i/${r.optString("id")}", Modifier.fillMaxSize())
            }
        }
        T(r.optString("name"), 10.sp, col, align = TextAlign.Center, maxLines = 2)
        if (r.optInt("count") > 1) T("×${r.optInt("count")}", 11.sp, bold = true)
        when {
            claimed -> Icon("check", 20.dp, Pal.Green)
            !allowed -> Icon("lock", 18.dp, Pal.Sub)
            reached -> GButton("AL", Modifier.fillMaxWidth(), Tone.Green, height = 28.dp, size = 11.sp) { claim() }
            else -> Spacer(Modifier.height(20.dp))
        }
    }
}

// =========================================================================== kozmetik
@Composable
fun CosmeticsScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("cosmetics") }
    val kinds = listOf("costume" to "Kostümler", "weapon_skin" to "Silah Kaplamaları", "frame" to "Çerçeveler", "tag" to "Etiketler")
    var tab by remember { mutableIntStateOf(0) }
    ScreenFrame("KOZMETİK", "hanger", back) {
        Tabs(listOf("user" to "Kostüm", "sword" to "Kaplama", "crown" to "Çerçeve", "star" to "Etiket"), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in data.optJSONArray("items").objs().filter { it.optString("kind") == kinds[tab].first }) {
                val owned = c.optBoolean("owned"); val eq = c.optBoolean("equipped")
                Column(Modifier.width(180.dp).glass(16.dp, if (eq) Pal.Green else if (owned) Pal.Legendary else null).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(14.dp)).background(Brush.radialGradient(listOf(Pal.Legendary.copy(alpha = 0.3f), Color.Transparent))), contentAlignment = Alignment.Center) {
                        when (c.optString("kind")) {
                            "frame" -> { Avatar(0, frameColor(c.optString("id")), 64.dp) }
                            "tag" -> T(c.optString("name"), 12.sp, Pal.Gold, bold = true, align = TextAlign.Center)
                            "costume" -> SpriteImage("l/${c.optString("id").substringBefore('_')}/0_0", Modifier.fillMaxSize())
                            else -> SpriteImage("i/w_${c.optString("id").removePrefix("skin_").substringBefore('_').let { if (it == "ak") "ak47" else it }}", Modifier.fillMaxSize().padding(6.dp))
                        }
                        if (!owned) Icon("lock", 26.dp, Color.White, Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                    T(c.optString("name"), 13.sp, bold = true, align = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    if (owned) GButton(if (eq) "ÇIKAR" else "KUŞAN", Modifier.fillMaxWidth(), if (eq) Tone.Dark else Tone.Green, height = 34.dp, size = 12.sp) {
                        val r = GameSession.cmd("equip_cosmetic", "id" to c.optString("id")); Toaster.show(r.msg, r.ok); tick++
                    } else T("Sezon Pass ödülü", 11.sp, Pal.Sub)
                }
            }
        }
    }
}

// =========================================================================== profil
@Composable
fun ProfileScreen(back: () -> Unit, reset: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val p = remember(tick) { GameSession.ui("profile") }
    var name by remember { mutableStateOf(p.optString("name")) }
    var confirm by remember { mutableStateOf(false) }
    ScreenFrame("PROFİL", "user", back) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(320.dp).fillMaxHeight().glass(20.dp).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.optInt("character"), frameColor(p.optString("frame")), 110.dp)
                Spacer(Modifier.height(8.dp))
                T(p.optString("name"), 22.sp, bold = true)
                if (p.optString("tag_name").isNotEmpty()) T(p.optString("tag_name"), 13.sp, Pal.Gold, bold = true)
                T("Seviye ${p.optInt("level")} · ${p.optString("char_name")} (${p.optString("char_class")})", 12.sp, Pal.Sub)
                Spacer(Modifier.height(12.dp))
                Field(name, { if (it.length <= 16) name = it }, "Yeni isim", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                GButton("İsmi Değiştir", Modifier.fillMaxWidth(), Tone.Blue, height = 40.dp, size = 13.sp) {
                    val r = GameSession.cmd("set_name", "name" to name); Toaster.show(r.msg, r.ok); tick++
                    if (r.ok) Online.setIdentity(name, p.optString("tag_name"))
                }
                Spacer(Modifier.weight(1f))
                GButton("İlerlemeyi Sıfırla", Modifier.fillMaxWidth(), Tone.Red, icon = "trash", height = 40.dp, size = 13.sp) { confirm = true }
            }
            Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp).padding(14.dp)) {
                SectionTitle("İstatistikler")
                val s = p.optJSONObject("stats") ?: JSONObject()
                val rows = listOf("skull" to ("Öldürülen zombi" to s.optInt("kills")), "crown" to ("Yenilen boss" to s.optInt("boss")),
                    "wood" to ("Kesilen ağaç" to s.optInt("trees")), "gift" to ("Açılan sandık" to s.optInt("crates")),
                    "craft" to ("Üretilen eşya" to s.optInt("crafted")), "coin" to ("Kazanılan altın" to s.optInt("gold_earned")),
                    "close" to ("Ölüm" to s.optInt("deaths")), "calendar" to ("Oynama süresi (dk)" to s.optInt("time") / 60))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((icon, kv) in rows) {
                        Row(Modifier.width(230.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x44000000)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, 26.dp, Pal.Gold)
                            Spacer(Modifier.width(10.dp))
                            Column { T(fmt(kv.second), 18.sp, bold = true); T(kv.first, 11.sp, Pal.Sub) }
                        }
                    }
                }
            }
        }
    }
    if (confirm) Dim({ confirm = false }) {
        Panel(Modifier.width(360.dp), Pal.Red) {
            T("Emin misin?", 20.sp, bold = true)
            T("Tüm seviye, eşya ve altın silinir. Bu işlem geri alınamaz.", 13.sp, Pal.Sub)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GButton("Vazgeç", Modifier.weight(1f), Tone.Dark) { confirm = false }
                GButton("SİL", Modifier.weight(1f), Tone.Red) { confirm = false; reset() }
            }
        }
    }
}

// =========================================================================== sohbet
@Composable
fun ChatPanel(modifier: Modifier = Modifier, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val channels = listOf("global", "local", "system")
    val lines = Online.chat.filter { it.channel == channels[tab] }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1) }
    Column(modifier.glass(20.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tabs(listOf("globe" to "Genel", "pin" to "Yakın", "info" to "Sistem"), tab) { tab = it }
            Spacer(Modifier.weight(1f))
            T(if (Online.online.value) "● Çevrimiçi" else "○ Çevrimdışı", 11.sp, if (Online.online.value) Pal.Green else Pal.Sub)
            Spacer(Modifier.width(8.dp))
            RoundIcon("close", size = 36.dp) { onClose() }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state) {
            items(lines) { l ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    if (l.tag.isNotEmpty()) T(l.tag + " ", 13.sp, Pal.Gold, bold = true)
                    T(l.name + ": ", 13.sp, if (l.mine) Pal.Green else if (l.channel == "system") Pal.Orange else Pal.Blue, bold = true)
                    T(l.text, 13.sp)
                }
            }
        }
        if (tab != 2) Row(verticalAlignment = Alignment.CenterVertically) {
            Field(msg, { if (it.length <= 140) msg = it }, if (tab == 0) "Herkese yaz…" else "Yakındakilere yaz…", Modifier.weight(1f)) {
                Online.send(channels[tab], msg); msg = ""
            }
            Spacer(Modifier.width(8.dp))
            GButton("", tone = Tone.Blue, icon = "play", height = 44.dp) { Online.send(channels[tab], msg); msg = "" }
        }
    }
}

@Composable
fun ChatScreen(back: () -> Unit) {
    ScreenFrame("SOHBET", "chat", back) { ChatPanel(Modifier.fillMaxSize(), back) }
}

// =========================================================================== geliştirici menüsü
@Composable
fun DevMenu(onClose: () -> Unit) {
    var cmd by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("Komut yaz ya da hızlı tuşları kullan.") }
    fun run(c: String) {
        val r = GameSession.cmd("cheat", "text" to c)
        log = (if (r.ok) "✔ " else "✖ ") + r.msg
    }
    Dim(onClose) {
        Column(Modifier.width(620.dp).glass(20.dp, Pal.Purple).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("dev", 24.dp, Pal.Purple); Spacer(Modifier.width(8.dp)); T("GELİŞTİRİCİ MENÜSÜ", 20.sp, Pal.Purple, display = true)
                Spacer(Modifier.weight(1f)); RoundIcon("close", size = 36.dp) { onClose() }
            }
            Spacer(Modifier.height(8.dp))
            val quick = listOf("gold 10000", "gem 1000", "level 10", "level 30", "charlevel 10", "items", "weapons", "set police", "set military", "set titan",
                "god", "onehit", "speed 2", "heal", "night", "day", "spawn tank 3", "spawn dark 3", "spawn toxic 3", "spawn mystery 1", "boss",
                "killall", "tp city", "tp camp", "tp boss", "tp swamp", "unlock", "pass 30", "pass buy elite", "vehicle car", "vehicle moto", "vehicle suv")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (q in quick) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x553A2A60)).border(1.dp, Pal.Purple.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { run(q) }.padding(horizontal = 10.dp, vertical = 6.dp)) { T(q, 12.sp) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(cmd, { cmd = it }, "örn: item w_ak47 1", Modifier.weight(1f)) { run(cmd); cmd = "" }
                Spacer(Modifier.width(8.dp))
                GButton("ÇALIŞTIR", tone = Tone.Purple, height = 44.dp) { run(cmd); cmd = "" }
            }
            Spacer(Modifier.height(6.dp))
            T(log, 12.sp, Pal.Sub, maxLines = 4)
        }
    }
}
