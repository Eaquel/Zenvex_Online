package com.zenvex.online.ui

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
import androidx.compose.runtime.mutableLongStateOf
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
import com.zenvex.online.game.Atlas
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Online
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { Splash, Lobby, Settings, Loading, Game }

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
    SceneBackground("bg_lobby", t, dim = 0.35f) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Emblem(150.dp)
            PT("Zenvex Online", 48.sp, Px.Orange, outline = true)
            Spacer(Modifier.height(18.dp))
            PxBar(progress, Px.Green, Modifier.width(320.dp), 14.dp)
            Spacer(Modifier.height(8.dp))
            PT("Dünya hazırlanıyor… %${(progress * 100).toInt()}", 15.sp, Px.Sub)
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    // siyah yarı saydam zemin, beyaz yazı, piksel kenar
    Box(modifier.height(44.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x99000000)).border(2.dp, Color(0xFF15140F), RoundedCornerShape(3.dp))
        .padding(2.dp).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(2.dp)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) PT(hint, 14.sp, Color(0x99FFFFFF))
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = PixelFont),
            cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
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
fun LoadingScreen(back: () -> Unit = {}, done: () -> Unit) {
    val ctx = LocalContext.current
    val t = rememberTime()
    var p by remember { mutableStateOf(0f) }
    var err by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val tip = remember { TIPS.random() }
    LaunchedEffect(attempt) {
        err = null; p = 0f
        // 1) sunucu oturumu: kayıt sunucudan gelir; bağlantı / bakım / yasak varsa oyuna girilmez
        // otomatik gösterim (yalnız hata ayıklama sürümü): oturumsuz, cihazdaki kayıtla
        val data = if (Demo.on) null else kotlinx.coroutines.suspendCancellableCoroutine<String?> { c ->
            com.zenvex.online.game.Session.start(ctx, GameSession.server.value.id) { d, e ->
                if (e != null) err = e
                if (c.isActive) c.resumeWith(Result.success(if (e == null) d.orEmpty() else null))
            }
        }
        if (!Demo.on) {
            if (data == null) return@LaunchedEffect
            GameSession.openFromServer(data)
        }
        p = 0.15f
        val ch = GameSession.ui("profile").optInt("character")
        val groups = listOf("world", "items", "gear", "vehicles", "npcs", "hero_${HERO_IDS[ch]}", "zombie_walker", "zombie_runner", "zombie_dog",
            "zombie_toxic", "zombie_dark", "zombie_tank", "zombie_mystery")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            groups.forEachIndexed { i, g -> runCatching { Atlas.get(ctx).preload(g) }; p = 0.15f + 0.85f * (i + 1f) / groups.size }
        }
        Online.start(Account.name.value.ifEmpty { GameSession.ui("profile").optString("name") }, GameSession.ui("profile").optString("tag_name"))
        if (!Demo.on) com.zenvex.online.game.Events.onSessionStart()
        delay(200)
        done()
    }
    SceneBackground("bg_lobby", t, dim = 0.3f) {
        Emblem(120.dp, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PT(GameSession.server.value.title, 26.sp, Px.Clan, outline = true)
            Spacer(Modifier.height(10.dp))
            val e = err
            if (e != null) {
                PT(e, 17.sp, Color(0xFFFF7A6A), maxLines = 2, align = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PxButton("Tekrar Dene", height = 44.dp, size = 16.sp, icon = "refresh") { attempt++ }
                    PxButton("Lobiye Dön", color = Px.Dark, height = 44.dp, size = 16.sp) { back() }
                }
            } else {
                PT(if (p < 0.15f) "Sunucuya Bağlanılıyor…" else "Dünya Yükleniyor…", 15.sp, Px.Sub)
                Spacer(Modifier.height(6.dp))
                PxBar(p, Px.Green, Modifier.width(420.dp), 14.dp)
            }
            Spacer(Modifier.height(10.dp))
            PT(tip, 14.sp, Px.Text, align = TextAlign.Center, maxLines = 2)
        }
    }
}

private val RARITY_NAMES = listOf("Sıradan", "Nadir", "Destansı", "Efsanevi", "Sezonluk")
private val KIND_NAMES = mapOf("Material" to "Malzeme", "Consumable" to "Tüketilebilir", "Ammo" to "Mermi", "Weapon" to "Silah", "Helmet" to "Kask",
    "Jacket" to "Ceket", "Boots" to "Bot", "Bag" to "Çanta", "Pants" to "Pantolon", "Ring" to "Yüzük", "Necklace" to "Kolye", "Currency" to "Para", "Tool" to "Alet")

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
/**
 * Üretim: üstte sekmeler (Şifa, İksir, Silah Ve Alet, Ekipman, Takı, Malzeme, Araç, Üs), solda sekmenin tarifleri
 * (aynı eşyanın farklı tarifleri tek satırda), sağda seçili eşya: Üret tuşları en üstte (kaydırmadan görünür),
 * altında malzemeler ve her malzemenin nereden çıktığı.
 *
 * V12.1: iki ayrı ekran. [bench] false → El Üretimi (çanta ve HUD'dan; yalnız elle yapılan tarifler), true → Tezgâh
 * (tezgâha dokununca; yalnız tezgâh tarifleri). Bir ekrandaki tarif diğerinde görünmez; boş sekmeler gizlenir.
 */
@Composable
fun CraftScreen(back: () -> Unit, bench: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui(if (bench) "craft_bench" else "craft") }
    val all = data.optJSONArray("recipes").objs()
    val tabNames = data.optJSONArray("tabs")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
    // yalnız tarif olan sekmeler (sekme numarası korunur)
    val tabs = tabNames.withIndex().filter { (i, _) -> all.any { it.optInt("tab") == i } }
    var tab by remember { mutableIntStateOf(tabs.firstOrNull()?.index ?: 0) }
    var onlyCan by remember { mutableStateOf(false) }
    var selId by remember { mutableStateOf<String?>(null) }
    var variant by remember { mutableIntStateOf(0) }
    val list = all.filter { it.optInt("tab") == tab && (!onlyCan || it.optBoolean("can")) }
    val r = list.firstOrNull { it.optJSONObject("out")?.optString("id") == selId } ?: list.firstOrNull()
    val near = data.optBoolean("near_bench")

    fun craft(v: JSONObject, n: Int) {
        val res = GameSession.cmd("craft", "i" to v.optInt("i"), "n" to n)
        Toaster.show(res.msg, res.ok); tick++
        if (res.ok) com.zenvex.online.game.Audio.play("craft")
    }

    PxFrame(if (bench) "Tezgâh" else "El Üretimi", "craft", back, right = {
        PxButton(if (onlyCan) "Yapılabilenler" else "Hepsi", height = 36.dp, size = 12.sp, color = if (onlyCan) Px.Green else Px.Dark, icon = "check") {
            onlyCan = !onlyCan
        }
        Spacer(Modifier.width(8.dp))
        if (bench) {
            val left = data.optDouble("temp_left", -1.0)
            when {
                // üsteki kalıcı tezgâh çantaya geri alınabilir
                data.optBoolean("own_bench") -> PxButton("Tezgâhı Kaldır", height = 36.dp, size = 12.sp, color = Px.Red, icon = "hammer") {
                    val res = GameSession.cmd("pickup_bench"); Toaster.show(res.msg, res.ok); if (res.ok) back()
                }
                left > 0 -> PT("Tezgâh ${(left / 60).toInt()}:${"%02d".format((left % 60).toInt())} Daha Durur", 13.sp, Px.Clan)
                !near -> PT("Tezgâhtan Uzaklaştın", 13.sp, Px.Hard)
            }
        } else PT("Gelişmiş Tarifler Tezgâhta", 13.sp, Px.Sub)
    }, bg = "bg_craft") {
        // sekmeler (her sekmede yapılabilir tarif sayısı)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { (i, name) ->
                val ready = all.count { it.optInt("tab") == i && it.optBoolean("can") }
                val on = i == tab
                Row(Modifier.height(36.dp).clip(RoundedCornerShape(3.dp)).background(if (on) Color(0xE6524428) else Color(0xCC23221D))
                    .border(2.dp, if (on) Px.Clan else Px.Edge, RoundedCornerShape(3.dp))
                    .clickable { com.zenvex.online.game.Audio.play("click", bus = com.zenvex.online.game.Audio.Bus.Ui); tab = i; selId = null; variant = 0 }
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PT(name, 13.sp, if (on) Px.Text else Px.Sub)
                    if (ready > 0) { Spacer(Modifier.width(6.dp)); PT("$ready", 11.sp, Px.Green) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PxPanel(Modifier.weight(1f).fillMaxHeight(), color = Color(0xB4201F1C), pad = 6.dp) {
                if (list.isEmpty()) PT(if (onlyCan) "Bu Sekmede Şu An Yapabileceğin Bir Şey Yok" else "Tarif Yok", 13.sp, Px.Sub)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(list.size) { k ->
                        val g = list[k]
                        val out = g.optJSONObject("out") ?: JSONObject()
                        val on = g === r
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(if (on) Color(0x80524428) else Color(0x40000000))
                            .clickable { selId = out.optString("id"); variant = 0 }.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            PxSlot(out, 42.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                PT(out.optString("name"), 14.sp, Pal.rarity(out.optInt("rarity")))
                                val vs = g.optJSONArray("variants").objs()
                                PT(when {
                                    vs.size > 1 -> "${vs.size} Farklı Tarif"
                                    bench -> "Tezgâhta"
                                    else -> "Her Yerde"
                                } + if (g.optInt("have") > 0) " · Çantada ${g.optInt("have")}" else "", 11.sp, Px.Sub)
                            }
                            if (g.optBoolean("can")) Icon("check", 20.dp, Px.Green)
                        }
                    }
                }
            }
            PxPanel(Modifier.width(340.dp).fillMaxHeight(), color = Color(0xC0201F1C), pad = 8.dp) {
                if (r != null) {
                    val out = r.optJSONObject("out") ?: JSONObject()
                    val vs = r.optJSONArray("variants").objs()
                    val v = vs.getOrNull(variant) ?: vs.firstOrNull() ?: JSONObject()
                    val can = v.optBoolean("can")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PxSlot(out, 52.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            PT(out.optString("name"), 15.sp, Pal.rarity(out.optInt("rarity")))
                            PT("${RARITY_NAMES.getOrElse(out.optInt("rarity")) { "" }} · ×${v.optInt("count", 1)} · Çantada ${r.optInt("have")}", 11.sp, Px.Sub)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // tuşlar her zaman görünür (malzeme listesi uzasa da kaydırma gerekmez)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PxButton("Üret", Modifier.weight(1f), color = Px.Green, icon = "craft", height = 40.dp, size = 15.sp, enabled = can) { craft(v, 1) }
                        PxButton("×5", Modifier.width(76.dp), color = Px.Blue, height = 40.dp, size = 15.sp, enabled = can) { craft(v, 5) }
                    }
                    if (!can) {
                        Spacer(Modifier.height(4.dp))
                        PT(if (v.optBoolean("bench") && !near) "Tezgâhın Yanına Dön" else "Malzemeler Eksik", 12.sp, Px.Hard)
                    }
                    if (vs.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            vs.forEachIndexed { i, o ->
                                val on = i == variant
                                Box(Modifier.height(28.dp).clip(RoundedCornerShape(3.dp)).background(if (on) Color(0xE6524428) else Color(0xCC23221D))
                                    .border(1.dp, if (o.optBoolean("can")) Px.Green else Px.Edge, RoundedCornerShape(3.dp))
                                    .clickable { variant = i }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    PT("Tarif ${i + 1}", 12.sp, if (on) Px.Text else Px.Sub)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    PT("Malzemeler", 13.sp, Px.Clan)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        for (inp in v.optJSONArray("inputs").objs()) {
                            val ok = inp.optInt("have") >= inp.optInt("need")
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                ItemSlot(inp.optString("id"), 0, 0, 34.dp)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    PT(inp.optString("name"), 13.sp)
                                    PT(inp.optString("src"), 10.sp, Px.Sub, maxLines = 2)
                                }
                                PT("${inp.optInt("have")}/${inp.optInt("need")}", 13.sp, if (ok) Px.Green else Px.Hard)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        PT(out.optString("desc"), 11.sp, Px.Sub, maxLines = 5)
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
    ScreenFrame("GEZGİN TÜCCAR MARCO", "shop", back, right = {
        Chip("coin", fmt(data.optInt("gold")), Pal.Gold); Spacer(Modifier.width(8.dp)); Chip("gem", fmt(prof.optInt("gems")), Pal.Gem)
    }) {
        Tabs(listOf("shop" to "Satın Al", "coin" to "Sat", "gem" to "Elmas"), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // V12.2 gezgin tüccar: bu gelişin malları (gelme olasılığı, stok), süre ve getirmedikleri
                val next = data.optDouble("next").toInt()
                T("Tüccar Marco her gelişinde başka mallar getirir; aynı anda herkes aynı malları görür. " +
                    "${next / 60}:${"%02d".format(next % 60)} sonra kamyonetiyle başka bir karakola gider.", 13.sp, Pal.Sub)
                Spacer(Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (o in data.optJSONArray("buy").objs()) {
                        val it = o.optJSONObject("item") ?: JSONObject()
                        val locked = o.optBoolean("locked")
                        val left = o.optInt("left")
                        Column(Modifier.width(150.dp).glass(16.dp, Pal.rarity(it.optInt("rarity"))).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ItemSlot(it.optString("id"), 0, it.optInt("rarity"), 70.dp)
                            T(it.optString("name"), 13.sp, Pal.rarity(it.optInt("rarity")), bold = true, maxLines = 1)
                            T("Gelme Olasılığı %${o.optInt("chance")}", 11.sp, Pal.Sub, maxLines = 1)
                            T("Stok $left", 11.sp, if (left > 0) Pal.Gold else Pal.Sub, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            if (locked) GButton("Sv ${o.optInt("req")}", Modifier.fillMaxWidth(), Tone.Dark, icon = "lock", height = 34.dp, size = 12.sp) {}
                            else if (left == 0) GButton("Tükendi", Modifier.fillMaxWidth(), Tone.Dark, height = 34.dp, size = 12.sp) {}
                            else GButton("${o.optInt("price")}", Modifier.fillMaxWidth(), if (o.optBoolean("afford")) Tone.Gold else Tone.Dark, icon = "coin", height = 34.dp, size = 13.sp) {
                                val r = GameSession.cmd("buy", "i" to o.optInt("i"), "n" to 1); Toaster.show(r.msg, r.ok); tick++
                                if (r.ok) com.zenvex.online.game.Audio.play("coin")
                            }
                        }
                    }
                }
                val missing = data.optJSONArray("missing").objs()
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    T("Bu Sefer Getirmedikleri (Gelme Olasılığı)", 14.sp, Pal.Sub, bold = true)
                    Spacer(Modifier.height(6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (o in missing) {
                            val it = o.optJSONObject("item") ?: JSONObject()
                            Row(Modifier.glass(10.dp).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                ItemSlot(it.optString("id"), 0, it.optInt("rarity"), 30.dp)
                                Spacer(Modifier.width(6.dp))
                                T("${it.optString("name")} %${o.optInt("chance")}", 12.sp, Pal.Sub, maxLines = 1)
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
    QuestsView(q, back)
}

@Composable
internal fun QuestsView(q: JSONObject, back: () -> Unit) {
    PxFrame("Görevler", "quest", back) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PxPanel(Modifier.weight(1.2f).fillMaxHeight(), pad = 10.dp) {
                PT("Hikâye", 16.sp, Px.Clan)
                Spacer(Modifier.height(6.dp))
                val st = q.optJSONArray("story").objs()
                if (st.isEmpty()) PT("Tüm Görevleri Tamamladın. Efsanesin!", 15.sp, Px.Name)
                for (m in st) {
                    val on = m.optBoolean("on"); val ready = m.optBoolean("ready")
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).pxPanel(Px.PanelDark).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).background(if (m.optBoolean("guide")) Px.Blue else Color(0xFFB08A2A)), contentAlignment = Alignment.Center) {
                                PT(if (ready) "?" else "!", 13.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            PT(m.optString("title"), 17.sp, if (ready) Px.Clan else Px.Text, Modifier.weight(1f))
                            PT(if (m.optBoolean("guide")) "Rehber" else "Görevci", 12.sp, Px.Sub)
                        }
                        Spacer(Modifier.height(4.dp))
                        val txt = when {
                            ready -> "Tamamlandı! ${m.optString("giver")} İle Konuşup Teslim Et."
                            on -> m.optString("hint")
                            m.has("x") -> "${m.optString("giver")} İle Konuşarak Görevi Al."
                            else -> "${m.optInt("min_level")}. Seviyede Açılır."
                        }
                        PT(txt, 13.sp, if (ready) Px.Name else Px.Sub, maxLines = 2)
                        if (on) {
                            Spacer(Modifier.height(5.dp))
                            PxBar(m.optInt("n") / m.optInt("goal", 1).toFloat(), if (ready) Px.GreenHi else Px.Orange, Modifier.fillMaxWidth(), 12.dp)
                            PT("${m.optInt("n")} / ${m.optInt("goal")}", 12.sp, Px.Sub)
                        }
                    }
                }
            }
            PxPanel(Modifier.weight(1f).fillMaxHeight(), pad = 10.dp) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    for ((key, title, sub) in listOf(Triple("daily", "Günlük Görevler", "Her Gün Yenilenir"), Triple("weekly", "Haftalık Görevler", "Her Pazartesi Yenilenir"))) {
                        PT(title, 16.sp, Px.Clan)
                        PT(sub + " · Sezon Bileti XP'si Verir", 11.sp, Px.Sub)
                        Spacer(Modifier.height(6.dp))
                        for (d in q.optJSONArray(key).objs()) {
                            val done = d.optBoolean("done")
                            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).pxPanel(if (done) Color(0xF0243A20) else Px.PanelDark).padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PT(d.optString("title"), 14.sp, if (done) Px.Name else Px.Text, Modifier.weight(1f))
                                    if (done) Icon("check", 18.dp, Px.Name)
                                }
                                PxBar(d.optInt("progress") / d.optInt("goal", 1).toFloat(), if (done) Px.GreenHi else Px.Blue, Modifier.fillMaxWidth().padding(vertical = 3.dp), 9.dp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PT("${fmt(d.optInt("progress"))}/${fmt(d.optInt("goal"))}", 11.sp, Px.Sub)
                                    PT("+${fmt(d.optInt("gold"))} Altın", 11.sp, Px.Clan)
                                    PT("+${fmt(d.optInt("xp"))} XP", 11.sp, Px.Level)
                                    PT("+${fmt(d.optInt("pass_xp"))} Bilet XP", 11.sp, Px.Orange)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================== sohbet
@Composable
fun ChatPanel(modifier: Modifier = Modifier, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val channels = listOf("global", "local", "clan", "dm", "system")
    val lines = Online.chat.filter { it.channel == channels[tab] }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1) }
    Column(modifier.pxPanel(Color(0x9C141310)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("Genel", "Yakın", "Klan", "Özel", "Sistem").forEachIndexed { i, n ->
                Box(Modifier.background(if (i == tab) Px.Panel else Px.PanelDark).clickable { tab = i }.padding(horizontal = 9.dp, vertical = 6.dp)) {
                    PT(n, 14.sp, if (i == tab) Px.Text else Px.Sub)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (Online.online.value) Px.Name else Px.Sub))
            Spacer(Modifier.width(8.dp))
            PxIcon("close", size = 34.dp) { onClose() }
        }
        Spacer(Modifier.height(8.dp))
        if (channels[tab] == "dm") { FriendsPanel(Modifier.weight(1f).fillMaxWidth()); return@Column }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state) {
            items(lines) { l ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // gönderenin resmi ve çerçevesi (animasyonlu); sistem satırlarında yok
                    if (l.channel != "system" && l.avatar >= 0) { ProfileAvatar(l.avatar, l.frame, 30.dp); Spacer(Modifier.width(5.dp)) }
                    if (l.tag.isNotEmpty() && l.channel != "clan") PT("[${l.tag}] ", 13.sp, Px.Clan)
                    PT(l.name + ": ", 13.sp, if (l.mine) Px.Name else if (l.channel == "system") Px.Orange else Color(0xFF8CC8FF))
                    PT(l.text, 14.sp, maxLines = 6, outline = true)
                }
            }
        }
        val needClan = channels[tab] == "clan" && Account.clan.value == null
        if (tab != 4 && (Online.chatLocked() || needClan)) {
            Row(Modifier.fillMaxWidth().pxPanel(Px.PanelDark).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("lock", 18.dp, Px.Sub)
                Spacer(Modifier.width(8.dp))
                PT(if (needClan) "Klan Sohbeti İçin Bir Klana Katıl" else "Sohbet ${Online.CHAT_LEVEL}. Seviyede Açılır", 14.sp, Px.Sub)
            }
        } else if (tab != 4) Row(verticalAlignment = Alignment.CenterVertically) {
            var wait by remember { mutableLongStateOf(0L) }
            LaunchedEffect(wait) { if (wait > 0) { delay(200); wait = Online.chatWait() } }
            fun go() {
                val err = Online.send(channels[tab], msg)
                if (err == null) msg = "" else Toaster.show(err, false)
                wait = Online.chatWait()
            }
            Field(msg, { if (it.length <= 140) msg = it }, when (tab) { 0 -> "Herkese Yaz…"; 1 -> "Yakındakilere Yaz…"; else -> "Klanına Yaz…" }, Modifier.weight(1f)) { go() }
            Spacer(Modifier.width(8.dp))
            if (wait > 0) Box(Modifier.size(44.dp).pxPanel(Px.PanelDark), contentAlignment = Alignment.Center) { PT("${(wait + 999) / 1000}", 16.sp, Px.Sub) }
            else PxButton("", height = 44.dp, icon = "play", color = Px.Blue) { go() }
        }
    }
}

// =========================================================================== takas
@Composable
fun TradeScreen(close: () -> Unit) {
    val st = Online.trade.value
    var tick by remember { mutableIntStateOf(0) }
    val inv = remember(tick) { GameSession.ui("inventory") }
    val offer = remember { androidx.compose.runtime.mutableStateListOf<Online.TradeItem>() }
    var gold by remember { mutableStateOf("") }
    fun push() {
        val g = gold.toIntOrNull() ?: 0
        Online.setTradeItems(offer.toList() + if (g > 0) listOf(Online.TradeItem("coin", g)) else emptyList())
    }
    ScreenFrame("TAKAS", "trade", { Online.cancelTrade(); close() }) {
        if (st == null) {
            T("Karşı tarafın kabul etmesi bekleniyor…", 15.sp, Pal.Sub)
            return@ScreenFrame
        }
        if (st.done) { T("Takas tamamlandı!", 18.sp, Pal.Green, bold = true); LaunchedEffect(Unit) { delay(1200); close() }; return@ScreenFrame }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // çanta
            Column(Modifier.weight(1f).fillMaxHeight().glass(20.dp).padding(10.dp)) {
                SectionTitle("Çantan (eklemek için dokun)")
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val slots = inv.optJSONArray("slots")
                    for (i in 0 until (slots?.length() ?: 0)) {
                        val s = slots!!.optJSONObject(i) ?: continue
                        val id = s.optString("id")
                        val have = s.optInt("count")
                        val used = offer.filter { it.id == id }.sumOf { it.n }
                        ItemSlot(id, have - used, s.optInt("rarity"), 54.dp) {
                            if (have - used > 0) {
                                val ix = offer.indexOfFirst { it.id == id }
                                if (ix >= 0) offer[ix] = Online.TradeItem(id, offer[ix].n + 1) else offer += Online.TradeItem(id, 1)
                                push()
                            }
                        }
                    }
                }
            }
            // teklifler
            Column(Modifier.weight(1.2f).fillMaxHeight().glass(20.dp, Pal.Blue).padding(12.dp)) {
                SectionTitle("Senin teklifin" + if (st.myOk) " ✔" else "")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (it in offer) ItemSlot(it.id, it.n, 0, 54.dp) { offer.remove(it); push() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("coin", 22.dp, Pal.Gold); Spacer(Modifier.width(6.dp))
                    Field(gold, { v -> gold = v.filter { c -> c.isDigit() }.take(7); push() }, "Altın", Modifier.width(140.dp))
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle("${st.otherName} teklifi" + if (st.theirOk) " ✔" else "")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if (st.theirs.isEmpty()) T("Henüz bir şey eklemedi", 12.sp, Pal.Sub)
                    for (it in st.theirs) ItemSlot(it.id, it.n, 0, 54.dp)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GButton("İptal", Modifier.weight(1f), Tone.Red, height = 44.dp) { Online.cancelTrade(); close() }
                    GButton(if (st.myOk) "Hazırım ✔" else "Onayla", Modifier.weight(1.4f), if (st.myOk) Tone.Dark else Tone.Green, height = 44.dp) {
                        Online.setTradeReady(!st.myOk)
                    }
                }
                T("İki taraf da onaylayınca eşyalar takas edilir. Teklif değişirse onaylar sıfırlanır.", 11.sp, Pal.Sub)
            }
        }
    }
}
