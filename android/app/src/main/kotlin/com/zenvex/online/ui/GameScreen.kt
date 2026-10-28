package com.zenvex.online.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.sin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.GameUi
import com.zenvex.online.game.GameView
import com.zenvex.online.game.Native
import com.zenvex.online.game.Online
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Arkasında oyunun her zaman görüneceği katmanlar (yan menü, NPC konuşması, sohbet, ölüm ekranı) */
private val SEE_THROUGH = setOf(Overlay.None, Overlay.Menu, Overlay.Npc, Overlay.Chat, Overlay.Dead, Overlay.Player)

/** Oyunu tamamen örten (opak, tam ekran) pencereler */
private val FULL_OVERLAYS = setOf(
    Overlay.Settings, Overlay.Bag, Overlay.Craft, Overlay.CraftBench, Overlay.Merchant, Overlay.Map, Overlay.Quests, Overlay.Pass,
    Overlay.Crate, Overlay.Event, Overlay.Skills, Overlay.Me, Overlay.Mail, Overlay.Clan, Overlay.Top, Overlay.Cheat, Overlay.Garage,
    Overlay.PlayerProfile, Overlay.WizardMap, Overlay.Shop, Overlay.Chars,
)

internal enum class Overlay {
    None, Menu, Settings, Bag, Craft, Merchant, Chat, Map, Dead, Trade,
    Quests, Pass, Crate, Event, Skills, Me, Mail, Clan, Top, Cheat, Dog, Npc, Garage, Player, PlayerProfile, WizardMap, CraftBench, Shop, Chars,
}

/**
 * Oyun ekranı: GameView (dünya + dövüş tuşları) ve üstünde Zombix düzeninde piksel arayüz.
 * Envanter, üretim, tüccar, görevler, sezon, yetenekler, posta ve klan buradan açılır.
 */
@Composable
fun GameScreen(toLobby: () -> Unit) {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Overlay.None) }
    var npcKind by remember { mutableIntStateOf(1) }
    // dokunulan oyuncu (Online uid, ad)
    var picked by remember { mutableStateOf("" to "") }
    val view = remember {
        lateinit var v: GameView
        v = GameView(ctx, object : GameUi {
            override fun onDeath() { v.post { overlay = Overlay.Dead } }
            override fun onOpen(panel: String) {
                v.post {
                    // bir pencere açıkken oyun ekranındaki tuşlar (mini harita, etkileşim, NPC) başka pencere açmaz
                    if (overlay != Overlay.None) return@post
                    if (panel.startsWith("player:")) {
                        val (u, n) = panel.removePrefix("player:").split('|', limit = 2).let { it[0] to it.getOrElse(1) { "Oyuncu" } }
                        picked = u to n; overlay = Overlay.Player; return@post
                    }
                    if (panel.startsWith("npc:")) { npcKind = panel.substring(4).toIntOrNull() ?: 1; overlay = Overlay.Npc; return@post }
                    overlay = when (panel) {
                        "bag" -> Overlay.Bag; "craft" -> Overlay.Craft; "craft_bench" -> Overlay.CraftBench; "merchant" -> Overlay.Merchant
                        "chat" -> Overlay.Chat; "map" -> Overlay.Map; "trade" -> Overlay.Trade
                        "quests" -> Overlay.Quests; "dog" -> Overlay.Dog; else -> Overlay.Menu
                    }
                }
            }
            override fun onToast(msg: String) { v.post { Toaster.show(msg) } }
            override fun onLevelUp(level: Int) { v.post { Online.system("Seviye $level oldun!"); Account.claimLevelGems(); Account.refresh() } }
        })
        v.localName = Account.name.value.ifEmpty { GameSession.ui("profile").optString("name") }
        v.localClan = Account.clan.value?.name ?: ""
        v.serverTitle = GameSession.server.value.title
        v
    }
    // çevrimiçi dünya menüde de durmaz (yalnız bağlantı koparsa oturum dünyayı durdurur)
    view.paused = false
    // tam ekran pencere oyunu tamamen örtüyorsa sahne çizilmez (dünya 20 Hz işlemeye devam eder): görünmeyen sahneyi
    // saniyede 60 kez çizmek boşa GPU/işlemci ve ısı demekti. Saydam arka plan ayarında oyun görünür, çizim sürer.
    view.covered = overlay in FULL_OVERLAYS && !com.zenvex.online.game.Settings.current.uiTransparent
    // oturum düştü (başka cihaz, bakım, yasak, bağlantı): lobiye dön, nedeni lobide gösterilir
    val kicked = com.zenvex.online.game.Session.kicked.value
    LaunchedEffect(kicked) { if (kicked != null) toLobby() }
    // güvenlik ağı: oturum oyundayken sessizce kapandıysa (atılma nedeni yoksa) donuk dünyada bekletme, lobiye dön
    val sessionState = com.zenvex.online.game.Session.state.value
    LaunchedEffect(sessionState) {
        if (sessionState == com.zenvex.online.game.Session.State.Off && !Demo.on) {
            delay(800)
            if (com.zenvex.online.game.Session.state.value == com.zenvex.online.game.Session.State.Off) {
                Toaster.show("Oturum Kapandı, Tekrar Gir", false); toLobby()
            }
        }
    }
    // öğretici: harita, yetenek ağacı ve posta açılınca ilgili adım ilerler
    LaunchedEffect(overlay) {
        when (overlay) {
            Overlay.Map -> Tut.ping("map")
            Overlay.Skills -> Tut.ping("skills")
            Overlay.Mail -> Tut.ping("mail")
            else -> {}
        }
    }
    DisposableEffect(Unit) {
        Online.onChat = { l ->
            if (l.channel != "system") view.bubble(l.name, l.text)
            view.addChat((if (l.tag == "Fısıltı") "[Fısıltı] " else "") + "${l.name}: ${l.text}") }
        onDispose {
            Online.onChat = null
            if (Online.trade.value != null) Online.cancelTrade()
            GameSession.save(cloud = true)
            Online.stop()
            // son kayıt gönderildikten sonra oturum kapanır
            val my = com.zenvex.online.game.Session.sid
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ com.zenvex.online.game.Session.endIf(my) }, 1500)
        }
    }
    BackHandler {
        overlay = when (overlay) { Overlay.None -> Overlay.Menu; Overlay.Dead -> Overlay.Dead; Overlay.Settings -> Overlay.Menu; else -> Overlay.None }
    }
    val close = { overlay = Overlay.None }
    // yönetici işlemleri: 30 sn'de bir kuyruk okunur ve uygulanır (eşya silme, altın, atma, mesaj)
    LaunchedEffect(Unit) {
        while (Account.loggedIn) {
            Account.actionsPull { r ->
                r.onSuccess { list ->
                    val ids = ArrayList<Long>()
                    for (i in 0 until list.length()) {
                        val o = list.getJSONObject(i); val a = o.getJSONObject("a")
                        ids += o.optLong("id")
                        when (a.optString("t")) {
                            "kick" -> { Toaster.show(a.optString("msg").ifEmpty { "Yönetici seni oyundan çıkardı" }, false); GameSession.save(cloud = true); toLobby() }
                            "msg" -> { Online.system("Yönetici: " + a.optString("msg")); Toaster.show(a.optString("msg")) }
                            else -> { val res = GameSession.cmd("admin_action", "a" to a); if (res.msg.isNotEmpty()) Toaster.show(res.msg, false) }
                        }
                    }
                    if (ids.isNotEmpty()) { Account.actionsDone(ids); GameSession.save(cloud = true) }
                }
            }
            delay(30_000)
        }
    }
    // etkinlik puanı: 20 sn'de bir sayaç farkı sunucuya
    LaunchedEffect(Unit) { while (true) { delay(20_000); com.zenvex.online.game.Events.tick() } }
    // oyun içinde isim değişince: başımızın üstündeki ad, oyun kaydı ve diğer oyunculara yayın anında yenilenir
    val myName = Account.name.value
    LaunchedEffect(myName) {
        if (myName.isNotEmpty() && myName != view.localName) {
            view.localName = myName
            GameSession.cmd("set_name", "name" to myName)
            Online.rename(myName)
        }
    }
    // klan güçlendirmeleri oyuna uygulanır (hasar, tecrübe, hız)
    LaunchedEffect(Account.clan.value) {
        val b = Account.clan.value?.json?.optJSONObject("boosts")
        GameSession.cmd("clan_boost", "dmg" to (b?.optInt("dmg") ?: 0), "xp" to (b?.optInt("xp") ?: 0), "speed" to (b?.optInt("speed") ?: 0))
    }
    // test gösterimi: ekranları sırayla aç ve görüntüle (Demo.kt)
    if (Demo.on) LaunchedEffect(Unit) {
        val act = ctx as android.app.Activity
        fun cheat(t: String) = GameSession.cmd("cheat", "text" to t)
        suspend fun step(name: String, o: Overlay, wait: Long = 1800) { overlay = o; kotlinx.coroutines.delay(wait); Demo.shot(act, name) }
        kotlinx.coroutines.delay(4000)
        Demo.shot(act, "01_spawn")
        for (t in listOf("level 6", "potions", "item medkit 4", "item cloth 6", "item tape 4", "item w_katana 1", "dog 3", "points 5")) cheat(t)
        val inv = GameSession.ui("inventory").optJSONArray("slots")
        for (i in 4 until (inv?.length() ?: 0)) if (inv!!.optJSONObject(i)?.optString("id") == "potion_heal") { GameSession.cmd("move", "from" to i, "to" to 0); break }
        step("02_bag", Overlay.Bag)
        step("03_map", Overlay.Map)
        npcKind = 6; step("04_npc_guide", Overlay.Npc, 2500)
        step("05_quests", Overlay.Quests)
        step("06_skills", Overlay.Skills)
        step("07_craft", Overlay.Craft)
        step("08_dog", Overlay.Dog)
        step("09_menu", Overlay.Menu)
        step("10_settings", Overlay.Settings)
        step("11_chat", Overlay.Chat)
        overlay = Overlay.None
        cheat("tp deep"); cheat("spawn walker 4 3"); cheat("spawn dog 2 3")
        kotlinx.coroutines.delay(3500)
        Demo.shot(act, "12_combat")
        cheat("night"); kotlinx.coroutines.delay(1500)
        Demo.shot(act, "13_night")
        Demo.done(act)
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ view }, Modifier.fillMaxSize())
        if (overlay == Overlay.None && !view.building.value) Hud { overlay = it }
        // tam ekran pencerelerin arkası düz (oyun görünmez); ayarlardan şeffaf yapılabilir
        // her pencerenin altında dokunuşu yutan katman: pencere dışına dokunmak alttaki oyun tuşlarına geçmez
        // (sohbet hariç: sohbet açıkken oyun oynanmaya devam eder, ama oyun tuşları pencere açmaz)
        if (overlay != Overlay.None && overlay != Overlay.Chat) {
            val solid = overlay !in SEE_THROUGH && !com.zenvex.online.game.Settings.current.uiTransparent
            Box(Modifier.fillMaxSize().then(if (solid) Modifier.background(Color(0xFF15140F)) else Modifier)
                .clickable(remember { MutableInteractionSource() }, null) {})
        }
        when (overlay) {
            Overlay.Menu -> SideMenu(close, { overlay = it }) { GameSession.save(cloud = true); toLobby() }
            Overlay.Settings -> SettingsScreen(back = { overlay = Overlay.Menu; view.post { view.requestLayout() } }, inGame = true)
            Overlay.Bag -> InventoryScreen(close, inGame = true, onBuild = { s -> overlay = Overlay.None; view.startBuild(s) }) { overlay = Overlay.Craft }
            Overlay.Craft -> CraftScreen(close)
            Overlay.CraftBench -> CraftScreen(close, bench = true)
            Overlay.Merchant -> MerchantScreen(close) { overlay = Overlay.Shop }
            Overlay.Quests -> QuestsScreen(close)
            Overlay.Pass -> PassScreen(close)
            Overlay.Crate -> CrateScreen(close)
            Overlay.Event -> EventDialog(close)
            Overlay.Skills -> TreeScreen(close)
            Overlay.Dog -> DogDialog(close)
            Overlay.Npc -> NpcDialogScreen(npcKind, close) { o ->
                overlay = when (o) {
                    "merchant" -> Overlay.Merchant; "quests" -> Overlay.Quests; "dog" -> Overlay.Dog; "garage" -> Overlay.Garage
                    "wizard_map" -> Overlay.WizardMap; else -> Overlay.None
                }
            }
            // imePadding: klavye açılınca sohbet paneli klavyenin üstüne kayar
            // sohbet: oyun arkada görünür ve oynanır (karartma yok); panel yarı saydam, ekranın yüksekliği boyunca
            Overlay.Chat -> Box(Modifier.fillMaxSize().imePadding().padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                ChatPanel(Modifier.width(520.dp).fillMaxHeight().align(Alignment.CenterStart), close,
                    onPlayer = { u, n -> picked = u to n; overlay = Overlay.Player })
            }
            Overlay.Map -> WorldMapScreen(close)
            Overlay.WizardMap -> WizardMapScreen(close)
            Overlay.Trade -> TradeScreen(close)
            Overlay.Mail -> MailScreen(close)
            Overlay.Clan -> ClanScreen({ overlay = Overlay.None; view.localClan = Account.clan.value?.name ?: ""; Online.setIdentity(view.localName, "") }, inGame = true)
            Overlay.Top -> TopDialog(close)
            Overlay.Garage -> GarageScreen(close)
            Overlay.Me -> PlayerProfileDialog(Account.id.value.toString(), close)
            Overlay.Player -> PlayerMenu(picked.first, picked.second, close,
                trade = { Online.requestTrade(picked.first); overlay = Overlay.Trade },
                profile = { overlay = Overlay.PlayerProfile })
            Overlay.PlayerProfile -> PlayerProfileDialog(picked.second, close)
            Overlay.Cheat -> DevMenu(close)
            Overlay.Shop -> ShopScreen(close)
            Overlay.Chars -> CharactersScreen(close)
            Overlay.Dead -> DeadDialog({ overlay = Overlay.None }, toLobby)
            Overlay.None -> {}
        }
        // bağlantı koptu: dünya durdu, yeniden bağlanılıyor
        if (com.zenvex.online.game.Session.state.value == com.zenvex.online.game.Session.State.Lost && !Demo.on) ConnectionLost()
        // gelen takas isteği
        Online.tradeRequest.value?.let { (_, n) ->
            Box(Modifier.align(Alignment.TopCenter).padding(top = 90.dp)) {
                PxPanel(Modifier.width(340.dp)) {
                    PT("$n seninle takas yapmak istiyor", 16.sp, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PxButton("Reddet", Modifier.weight(1f), Px.Red, height = 40.dp, size = 15.sp) { Online.declineTrade() }
                        PxButton("Kabul", Modifier.weight(1f), height = 40.dp, size = 15.sp) { Online.acceptTrade(); overlay = Overlay.Trade }
                    }
                }
            }
        }
    }
}

/** Battle Pass tuşu: altın taç, nabız gibi parlayan halka; alınmamış ödül varsa rozet. */
@Composable
private fun PassButton(onClick: () -> Unit) {
    val t = rememberTime()
    val p = remember { GameSession.ui("pass") }
    val ready = p.optJSONArray("rewards").objs().count { it.optBoolean("reached") && !it.optBoolean("free_claimed") }
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(54.dp).border(2.dp, Px.Clan.copy(alpha = 0.35f + 0.35f * kotlin.math.sin(t * 3f)), CircleShape))
        // V8 piksel tuş (taç); alınabilir ödül sayısı köşede
        ArtImage("btn_pass", Modifier.size(50.dp).clickable(remember { MutableInteractionSource() }, null) { com.zenvex.online.game.Audio.play("click", 0.6f, bus = com.zenvex.online.game.Audio.Bus.Ui); onClick() })
        if (ready > 0) Box(Modifier.align(Alignment.TopEnd).size(18.dp).background(Px.Red, CircleShape).border(1.dp, Color.Black, CircleShape),
            contentAlignment = Alignment.Center) { PT("$ready", 10.sp, Color.White) }
    }
}

/** Bağlantı koptuğunda tüm ekranı kaplayan uyarı (oyun durur; 90 sn içinde dönmezse lobiye atılır). */
@Composable
private fun ConnectionLost() {
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Color(0xB0000000)).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.metalFrame().padding(horizontal = 30.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon("radio", 40.dp, Px.Orange.copy(alpha = 0.5f + 0.5f * kotlin.math.sin(t * 4f)))
            Spacer(Modifier.height(8.dp))
            PT("Bağlantı Koptu", 26.sp, Px.Orange, outline = true)
            PT("Yeniden Bağlanılıyor… Dünya Bağlantı Gelene Kadar Durdu.", 14.sp, Px.Text, maxLines = 2)
        }
    }
}

// =========================================================================== HUD
@Composable
internal fun BoxScope.Hud(open: (Overlay) -> Unit) {
    var inv by remember { mutableStateOf(JSONObject()) }
    var points by remember { mutableIntStateOf(0) }
    var gold by remember { mutableLongStateOf(0L) }
    var gems by remember { mutableLongStateOf(0L) }
    var story by remember { mutableStateOf(JSONArray()) }
    var level by remember { mutableIntStateOf(0) }
    // seviye atlayınca mağaza durumu yenilenir: seviye paketi teklifi (5/10/25) o an açılır
    LaunchedEffect(level) { if (level > 0) com.zenvex.online.game.Store.refresh() }
    LaunchedEffect(Unit) {
        // ham metin değişmediyse çözümlenmez ve durum yeniden atanmaz: HUD her 400 ms'de baştan oluşturulmaz
        var lastInv = ""; var lastProf = ""; var lastQuests = ""
        while (true) {
            val ri = GameSession.uiRaw("inventory")
            if (ri != lastInv) { lastInv = ri; inv = runCatching { JSONObject(ri) }.getOrDefault(JSONObject()) }
            val rp = GameSession.uiRaw("profile")
            if (rp != lastProf) {
                lastProf = rp
                val prof = runCatching { JSONObject(rp) }.getOrDefault(JSONObject())
                level = prof.optInt("level")
                points = prof.optInt("points")
                gold = prof.optLong("gold"); gems = prof.optLong("gems")
            }
            val rq = GameSession.uiRaw("quests")
            if (rq != lastQuests) { lastQuests = rq; story = runCatching { JSONObject(rq) }.getOrDefault(JSONObject()).optJSONArray("story") ?: JSONArray() }
            delay(400)
        }
    }
    // etkin rehber adımı: hangi tuşun parlayıp ok göstereceğini belirler
    val gstep = (0 until story.length()).map { story.getJSONObject(it) }
        .firstOrNull { it.optBoolean("guide") && it.optBoolean("on") && !it.optBoolean("ready") }?.optString("id").orEmpty()
    // sol üst portre (GameView çizer): dokununca kendi profilin (başkalarının gördüğü gibi) + düzenleme
    Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp).size(68.dp)
        .clickable(remember { MutableInteractionSource() }, null) { open(Overlay.Me) })
    // sol üst (portre/barların altı): harita, görevler, sezon, yetenekler
    Row(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PassButton { open(Overlay.Pass) }
        PxIcon("quest", size = 46.dp) { open(Overlay.Quests) }
        Box {
            PxIcon("magic", size = 46.dp, badge = points, tint = Px.Clan) { open(Overlay.Skills) }
            if (gstep == "g6e") { TutPulse(Modifier.matchParentSize()); TutTap("Yetenekler", above = false) }
        }
        PxIcon("profile", size = 46.dp, tint = Color(0xFFB277FF)) { open(Overlay.Chars) }
        // Elmas Sandığı kaplamaları henüz hazır değil: tuş yalnız haber verir (ekran kodda duruyor)
        PxIcon("crate", size = 46.dp, color = Color(0xCC1A2A44)) { Toaster.show("Koleksiyon Eşyaları Çok Yakında Geliyor") }
    }
    // etkinlik afişi: üst orta, bölge adının altında; 10. seviyeden önce yok, boss çubuğu ya da üs kartı varken gizli
    val evOn = level >= com.zenvex.online.game.Events.MIN_LEVEL && !com.zenvex.online.game.HudLayout.topBusy &&
        com.zenvex.online.game.Events.current.optBoolean("active")
    com.zenvex.online.game.HudLayout.eventShown = evOn
    if (evOn) EventBanner(Modifier.align(Alignment.TopCenter).padding(top = 50.dp)) { open(Overlay.Event) }
    // (görev takipçisi kaldırıldı: görevler Görevler tuşunda; ekranda yalnız haritadaki hedef işareti kalır)
    // sağ üst (radarın solunda): mağaza + posta + menü, altında altın ve elmas (+ → mağaza)
    Column(Modifier.align(Alignment.TopEnd).padding(end = 150.dp, top = 12.dp), horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // yönetici paneli: üst orta etkinlik afişinin altında kalmasın diye sağ üst gruba alındı
            if (Account.admin.value) PxIcon("dev", size = 44.dp, tint = Px.Orange, color = Color(0xCC3A2A1A)) { open(Overlay.Cheat) }
            ShopButton { open(Overlay.Shop) }
            Box {
                PxIcon("mail", size = 44.dp, badge = Account.unread.intValue) { open(Overlay.Mail) }
                if (gstep == "g6f") { TutPulse(Modifier.matchParentSize()); TutTap("Posta", above = false) }
            }
            PxIcon("menu", size = 44.dp) { open(Overlay.Menu) }
        }
        Spacer(Modifier.height(6.dp))
        // altın ve elmas tek kompakt kapsülde (genişlik içeriğe göre; dokununca mağaza)
        CurrencyPill(gold, gems) { open(Overlay.Shop) }
        OfferStrip { open(Overlay.Shop) }
    }
    // alt orta: hızlı eşyalar + çanta + sohbet çubuğu
    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.onGloballyPositioned { r -> val b = r.boundsInRoot(); com.zenvex.online.game.HudLayout.pocketRow = floatArrayOf(b.left, b.top, b.right, b.bottom) },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val slots = inv.optJSONArray("slots") ?: JSONArray()
            // cepler (çantada sol sütun): basınca kullanılır, bekleme süresi tuşun üstünde
            for (k in 0 until inv.optInt("pockets", 4)) {
                val o = slots.optJSONObject(k)
                Box {
                    PocketButton(o, k) {
                        if (o == null) { open(Overlay.Bag); return@PocketButton }
                        val r = GameSession.cmd("use", "slot" to k)
                        if (r.msg.isNotEmpty() && !r.msg.startsWith("Bekleme")) Toaster.show(r.msg, r.ok)
                        inv = GameSession.ui("inventory")
                    }
                    // öğretici: cepteki bandaj parlar ("Cepten Kullan" adımı)
                    if (gstep == "g5b" && o?.optString("id") == "bandage") {
                        TutPulse(Modifier.matchParentSize()); TutTap("Dokun: Kullan")
                    }
                }
            }
            Box(Modifier.onGloballyPositioned { r -> val b = r.boundsInRoot(); com.zenvex.online.game.HudLayout.bagBtn = floatArrayOf(b.left, b.top, b.right, b.bottom) }) {
                PxIcon("bag", size = 54.dp, color = Color(0xCC4A3A26)) { open(Overlay.Bag) }
                // öğretici: üretim, kuşanma, tezgâh ve cep adımlarında çantayı göster (bandaj cepteyse cep parlar)
                val bandInPocket = (0 until inv.optInt("pockets", 4)).any { inv.optJSONArray("slots")?.optJSONObject(it)?.optString("id") == "bandage" }
                // ana görevlerin el üretimi adımları da (ör. İlk Yardım Kiti) çantayı gösterir
                val craftHand = (0 until story.length()).map { story.getJSONObject(it) }
                    .any { it.optBoolean("on") && !it.optBoolean("ready") && it.has("craft") && !it.optBoolean("bench") }
                // kaya kırma adımı: kazma elde değilse çanta gösterilir
                val pickHeld = inv.optJSONObject("eq")?.optJSONObject("weapon")?.optString("id")?.startsWith("t_pick_") == true
                if ((gstep in BAG_STEPS || craftHand || (gstep == "g4e" && !pickHeld)) && !(gstep == "g5b" && bandInPocket)) {
                    TutPulse(Modifier.matchParentSize())
                    TutTap("Çantayı Aç")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box {
        Row(
            Modifier.width(380.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xB0201F1C))
                .clickable { open(Overlay.Chat) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxHeight().background(Px.Green).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                PT("Genel Sohbet", 15.sp)
            }
            PT("Mesaj", 15.sp, Px.Sub, Modifier.weight(1f).padding(start = 14.dp))
            ArtImage("btn_chat", Modifier.padding(end = 4.dp).size(34.dp))
        }
        if (gstep == "g7b") {
            Box(Modifier.matchParentSize()) { TutPulseWide() }
            TutTap("Sohbete Yaz")
        }
        }
    }
    // ilk giriş kontrol rehberi: her şeyin üstünde
    ControlsGuide()
    // öğretici balonu: kontrol rehberinin de üstünde (arkada kalıp okunmuyordu)
    GuideBubble()
}

private val BAG_STEPS = setOf("g4", "g4b", "g4c", "g4d", "g4f", "g4g", "g4h", "g4i", "g5", "g5b")

/** Para gösterimi: 100.000'e kadar tam ("12.345"), sonra kısaltılmış ("123,4 B", "12,50 M", "1,25 Mr") */
fun fmtMoney(n: Long): String = when {
    n < 100_000 -> fmtL(n)
    n < 1_000_000 -> "%.1f B".format(n / 1_000.0)
    n < 1_000_000_000 -> "%.2f M".format(n / 1_000_000.0)
    else -> "%.2f Mr".format(n / 1_000_000_000.0)
}.replace('.', ',').let { if (n < 100_000) fmtL(n) else it }

/**
 * Altın + elmas tek kapsülde: genişlik içeriğe göre (100.000 üstü kısaltılır, en geniş hâli ~170 dp), sonda küçük "+"
 * (mağaza). Eskiden iki ayrı 130 dp'lik kapsül haritanın solunu kaplıyordu.
 */
@Composable
private fun CurrencyPill(gold: Long, gems: Long, shop: () -> Unit) {
    Row(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xD0141310)).border(1.dp, Color(0x55FFD34A), RoundedCornerShape(14.dp))
        .clickable { shop() }.padding(start = 5.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon("coin", 18.dp, Px.Clan)
        PT(fmtMoney(gold), 13.sp, Px.Clan, Modifier.padding(start = 3.dp, end = 8.dp), maxLines = 1)
        Icon("gem", 18.dp, Color(0xFF7FE0FF))
        PT(fmtMoney(gems), 13.sp, Color(0xFF7FE0FF), Modifier.padding(start = 3.dp, end = 5.dp), maxLines = 1)
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Px.Green), contentAlignment = Alignment.Center) { PT("+", 14.sp, Color.White) }
    }
}

/** Mağaza tuşu: kapağı aralık sandık, nabız gibi altın parıltı */
/** Süreli seviye paketi teklifi: en yakın biten paketin geri sayımı (dokununca Mağaza) */
@Composable
private fun OfferStrip(onClick: () -> Unit) {
    val s = com.zenvex.online.game.Store.state.value ?: return
    val offers = s.optJSONArray("offers").objs()
    if (offers.isEmpty()) return
    val skew = remember(s) { s.optLong("now", System.currentTimeMillis()) - System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() + skew) }
    LaunchedEffect(skew) { while (true) { now = System.currentTimeMillis() + skew; delay(1000) } }
    val o = offers.filter { it.optLong("ends") > now }.minByOrNull { it.optLong("ends") } ?: return
    val title = s.optJSONArray("products").objs().firstOrNull { it.optString("id") == o.optString("id") }?.optString("title") ?: "Paket"
    val t = rememberTime()
    Spacer(Modifier.height(6.dp))
    Row(Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xD02A1608))
        .border(1.dp, Px.Orange.copy(alpha = 0.5f + 0.5f * sin(t * 4f)), RoundedCornerShape(5.dp))
        .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon("crate", 16.dp, Px.Orange)
        Spacer(Modifier.width(5.dp))
        Column {
            PT(title, 11.sp, Px.Text, maxLines = 1)
            PT(fmtClock(o.optLong("ends") - now), 12.sp, Px.Orange)
        }
    }
}

/** Geri sayım: "2g 04:12:09" ya da "04:12:09" */
internal fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val d = s / 86400
    val hms = "%02d:%02d:%02d".format(s % 86400 / 3600, s % 3600 / 60, s % 60)
    return if (d > 0) "${d}g $hms" else hms
}

@Composable
private fun ShopButton(onClick: () -> Unit) {
    val t = rememberTime()
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC3A2A12)).border(1.5.dp, Px.Clan.copy(alpha = 0.55f + 0.45f * sin(t * 3f)), RoundedCornerShape(6.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (UiArt.has(LocalContext.current, "btn_shop")) ArtImage("btn_shop", Modifier.size(38.dp)) else Icon("crate", 30.dp, Px.Clan)
    }
}

@Composable
private fun QuickSlot(item: JSONObject?, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(Color(0x99201F1C)).border(2.dp, Color(0x66FFFFFF), CircleShape)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            SpriteImage("i/${item.optString("id")}", Modifier.size(38.dp))
            val n = item.optInt("count")
            if (n > 1) PT("$n", 12.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 2.dp))
        }
    }
}

// =========================================================================== menü
@Composable
private fun SideMenu(close: () -> Unit, open: (Overlay) -> Unit, toLobby: () -> Unit) {
    Dim(close) {
        PxPanel(Modifier.width(440.dp).clickable(remember { MutableInteractionSource() }, null) {}, pad = 12.dp) {
            PT(GameSession.server.value.title, 22.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
            PT(Online.status.value + if (Online.online.value) " · ${Online.playerCount.intValue} oyuncu" else "", 13.sp,
                if (Online.online.value) Px.Name else Px.Sub, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            val items = listOf(
                Triple("Devam Et", "play", Overlay.None), Triple("Klan", "people", Overlay.Clan),
                Triple("Profil", "profile", Overlay.Me), Triple("Sıralama", "trophy", Overlay.Top),
                Triple("Battle Pass", "crown", Overlay.Pass), Triple("Elmas Sandığı", "gem", Overlay.Crate),
                Triple("Karakterler", "profile", Overlay.Chars), Triple("Mağaza", "crate", Overlay.Shop),
                Triple("Ayarlar", "gear", Overlay.Settings),
            )
            // iki sütun: küçük ekranda da taşmaz
            for (row in items.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, icon, o) in row) {
                        PxButton(label, Modifier.weight(1f), if (o == Overlay.None) Px.Green else Px.Dark, icon = icon, size = 14.sp, height = 40.dp) { open(o) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            PxButton("Lobiye Dön", Modifier.fillMaxWidth(), Px.Red, icon = "exit", size = 17.sp, height = 42.dp) { toLobby() }
        }
    }
}

@Composable
private fun DeadDialog(respawned: () -> Unit, toLobby: () -> Unit) {
    val hard = GameSession.server.value.hardcore
    Dim {
        PxPanel(Modifier.width(380.dp), pad = 14.dp) {
            PT("ÖLDÜN", 44.sp, Color(0xFFE04040), Modifier.align(Alignment.CenterHorizontally), outline = true)
            PT(if (hard) "Hardcore: çantan ölüm yerinde kaldı. Koş, geri al!" else "Ekipmanın sende kalır. Bu bölgenin giriş meydanında doğarsın.",
                14.sp, Px.Sub, Modifier.align(Alignment.CenterHorizontally), maxLines = 2)
            Spacer(Modifier.height(14.dp))
            PxButton("Bölgede Yeniden Doğ", Modifier.fillMaxWidth(), Px.Green, icon = "respawn") { GameSession.cmd("respawn"); respawned() }
            Spacer(Modifier.height(8.dp))
            PxButton("Lobiye Dön", Modifier.fillMaxWidth(), Px.Dark, icon = "exit") { GameSession.cmd("respawn"); toLobby() }
        }
    }
}

// =========================================================================== harita + seyahat

// =========================================================================== köpek
@Composable
private fun DogDialog(close: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val d = remember(tick) { GameSession.ui("dog") }
    PxDialog("Köpek", close, 520.dp) {
        val near = d.optBoolean("near")
        if (!d.optBoolean("owned")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteImage("z/dog/idle_0_0", Modifier.size(90.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    PT("Sadık bir yol arkadaşı", 18.sp)
                    PT("Seni takip eder, yakındaki zombilere saldırır. Bayılırsa 30 sn sonra döner.", 13.sp, Px.Sub, maxLines = 3)
                }
            }
            Spacer(Modifier.height(10.dp))
            PxButton("Satın al · ${fmt(d.optInt("price"))} altın", Modifier.fillMaxWidth(), if (near) Px.Green else Px.Dark, icon = "paw", size = 17.sp, enabled = near) {
                val r = GameSession.cmd("dog_buy"); Toaster.show(r.msg, r.ok); tick++
            }
            if (!near) PT("Köpek yalnızca barınaktaki Eğitmen Jack'ten alınır. Barınak haritada işaretli.", 13.sp, Px.Clan, maxLines = 2)
            return@PxDialog
        }
        val dead = d.optDouble("dead")
        PT(if (dead > 0) "Baygın · ${dead.toInt()} sn sonra döner" else "Can: ${d.optDouble("hp").toInt()}", 15.sp, if (dead > 0) Color(0xFFFF8A6A) else Px.Name)
        Spacer(Modifier.height(8.dp))
        val stats = d.optJSONArray("stats").objs()
        for ((i, s) in stats.withIndex()) {
            Row(Modifier.fillMaxWidth().background(Px.Row).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PT("${s.optString("name")} · Sv ${s.optInt("lv")}/${s.optInt("max")}", 16.sp)
                    PT(when (i) { 0 -> "Isırık hasarı ${s.optDouble("val").toInt()}"; 1 -> "En fazla can ${s.optDouble("val").toInt()}"; else -> "Saniyede +${"%.1f".format(s.optDouble("val"))} can" }, 13.sp, Px.Sub)
                    PxBar(s.optInt("lv") / s.optInt("max", 10).toFloat(), Color(0xFFE0B070), Modifier.width(180.dp), 8.dp)
                }
                if (s.optInt("lv") < s.optInt("max")) PxButton("${fmt(s.optInt("cost"))} altın", color = if (near) Px.Green else Px.Dark, size = 14.sp, height = 38.dp, enabled = near) {
                    val r = GameSession.cmd("dog_up", "stat" to i); Toaster.show(r.msg, r.ok); tick++
                } else PT("EN YÜKSEK", 14.sp, Px.Clan)
            }
            Spacer(Modifier.height(6.dp))
        }
        PT(if (near) "Altın: ${fmt(d.optInt("gold"))}" else "Geliştirme barınaktaki Eğitmen Jack'te yapılır.", 13.sp, Px.Clan)
    }
}
