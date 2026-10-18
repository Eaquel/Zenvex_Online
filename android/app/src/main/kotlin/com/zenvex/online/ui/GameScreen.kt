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

private enum class Overlay {
    None, Menu, Settings, Bag, Craft, Merchant, Chat, Map, Dead, Trade,
    Quests, Pass, Skills, Profile, Characters, Cosmetics, Mail, Clan, Top, Players, Cheat,
}

/**
 * Oyun ekranı: GameView (dünya + dövüş tuşları) ve üstünde Zombix düzeninde piksel arayüz.
 * Envanter, üretim, tüccar, görevler, sezon, yetenekler, posta ve klan buradan açılır.
 */
@Composable
fun GameScreen(toLobby: () -> Unit) {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Overlay.None) }
    val view = remember {
        lateinit var v: GameView
        v = GameView(ctx, object : GameUi {
            override fun onDeath() { v.post { overlay = Overlay.Dead } }
            override fun onOpen(panel: String) {
                v.post {
                    overlay = when (panel) {
                        "bag" -> Overlay.Bag; "craft" -> Overlay.Craft; "merchant" -> Overlay.Merchant
                        "chat" -> Overlay.Chat; "map" -> Overlay.Map; "trade" -> Overlay.Trade; else -> Overlay.Menu
                    }
                }
            }
            override fun onToast(msg: String) { v.post { Toaster.show(msg) } }
            override fun onLevelUp(level: Int) { v.post { Online.system("Seviye $level oldun!") } }
        })
        v.localName = Account.name.value.ifEmpty { GameSession.ui("profile").optString("name") }
        v.localClan = Account.clan.value?.name ?: ""
        v.serverTitle = GameSession.server.value.title
        v
    }
    // çevrimiçiyken dünya durmaz; yalnızca tek başınayken menü oyunu duraklatır
    view.paused = !Online.online.value && (overlay == Overlay.Menu || overlay == Overlay.Settings)
    DisposableEffect(Unit) {
        Online.onChat = { l -> view.addChat((if (l.channel == "local") "[Yakın] " else if (l.channel == "system") "" else "") + "${l.name}: ${l.text}") }
        onDispose { Online.onChat = null; GameSession.save(cloud = true); Online.stop() }
    }
    BackHandler {
        overlay = when (overlay) { Overlay.None -> Overlay.Menu; Overlay.Dead -> Overlay.Dead; Overlay.Settings -> Overlay.Menu; else -> Overlay.None }
    }
    val close = { overlay = Overlay.None }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ view }, Modifier.fillMaxSize())
        if (overlay == Overlay.None) Hud { overlay = it }
        when (overlay) {
            Overlay.Menu -> SideMenu(close, { overlay = it }) { GameSession.save(cloud = true); toLobby() }
            Overlay.Settings -> SettingsScreen(back = { overlay = Overlay.Menu; view.post { view.requestLayout() } }, inGame = true)
            Overlay.Bag -> InventoryScreen(close, inGame = true)
            Overlay.Craft -> CraftScreen(close)
            Overlay.Merchant -> ShopScreen(close)
            Overlay.Quests -> QuestsScreen(close)
            Overlay.Pass -> PassScreen(close)
            Overlay.Skills -> SkillsScreen(close)
            Overlay.Profile -> ProfileScreen(close) {}
            Overlay.Characters -> CharacterScreen(close)
            Overlay.Cosmetics -> CosmeticsScreen(close)
            Overlay.Chat -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                ChatPanel(Modifier.width(460.dp).fillMaxHeight().align(Alignment.CenterStart), close)
            }
            Overlay.Map -> TravelMap(close)
            Overlay.Trade -> TradeScreen(close)
            Overlay.Mail -> MailDialog(close)
            Overlay.Clan -> ClanDialog { overlay = Overlay.None; view.localClan = Account.clan.value?.name ?: ""; Online.setIdentity(view.localName, "") }
            Overlay.Top -> TopDialog(close)
            Overlay.Players -> PlayersDialog(close)
            Overlay.Cheat -> DevMenu(close)
            Overlay.Dead -> DeadDialog({ overlay = Overlay.None }, toLobby)
            Overlay.None -> {}
        }
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

// =========================================================================== HUD
@Composable
private fun BoxScope.Hud(open: (Overlay) -> Unit) {
    var inv by remember { mutableStateOf(JSONObject()) }
    var points by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            inv = GameSession.ui("inventory")
            points = GameSession.ui("profile").optInt("points")
            delay(700)
        }
    }
    // sol üst (portre/barların altı): harita, görevler, sezon, yetenekler
    Row(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PxIcon("map", size = 46.dp) { open(Overlay.Map) }
        PxIcon("quest", size = 46.dp) { open(Overlay.Quests) }
        PxIcon("star", size = 46.dp, tint = Px.Orange, color = Color(0xCC6A4A1A)) { open(Overlay.Pass) }
        PxIcon("magic", size = 46.dp, badge = points) { open(Overlay.Skills) }
        PxIcon("dev", size = 46.dp, tint = Px.Orange, color = Color(0xCC3A2A1A)) { open(Overlay.Cheat) }
    }
    // sağ üst (radarın altı): posta, oyuncular, klan, menü
    Row(Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 146.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PxIcon("mail", size = 46.dp, badge = Account.unread.intValue) { open(Overlay.Mail) }
        PxIcon("radio", size = 46.dp, badge = if (Online.online.value) 0 else 0) { open(Overlay.Players) }
        PxIcon("people", size = 46.dp, tint = Px.Clan) { open(Overlay.Clan) }
        PxIcon("menu", size = 46.dp) { open(Overlay.Menu) }
    }
    // alt orta: hızlı eşyalar + çanta + sohbet çubuğu
    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val slots = inv.optJSONArray("slots") ?: JSONArray()
            // aynı türden ilk yığın: bandaj, ilk yardım, yiyecek, enerji
            val quick = listOf("medkit", "bandage", "food", "energy", "antidote")
            val shown = ArrayList<Pair<Int, JSONObject>>()
            for (want in quick) {
                if (shown.size >= 4) break
                for (i in 0 until slots.length()) {
                    val o = slots.optJSONObject(i) ?: continue
                    if (o.optString("id") == want) { shown += i to o; break }
                }
            }
            for (k in 0 until 4) {
                val p = shown.getOrNull(k)
                QuickSlot(p?.second) {
                    if (p != null) {
                        val r = GameSession.cmd("use", "slot" to p.first)
                        if (r.msg.isNotEmpty()) Toaster.show(r.msg, r.ok)
                        inv = GameSession.ui("inventory")
                    }
                }
            }
            PxIcon("bag", size = 54.dp, color = Color(0xCC4A3A26)) { open(Overlay.Bag) }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.width(380.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xB0201F1C))
                .clickable { open(Overlay.Chat) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxHeight().background(Px.Green).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                PT("Genel Sohbet", 15.sp)
            }
            PT("Mesaj", 15.sp, Px.Sub, Modifier.weight(1f).padding(start = 14.dp))
            Icon("chat", 24.dp, Px.Clan, Modifier.padding(end = 10.dp))
        }
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
        PxPanel(Modifier.width(320.dp).clickable(remember { MutableInteractionSource() }, null) {}, pad = 12.dp) {
            PT(GameSession.server.value.title, 22.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
            PT(Online.status.value + if (Online.online.value) " · ${Online.playerCount.intValue} oyuncu" else "", 13.sp,
                if (Online.online.value) Px.Name else Px.Sub, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            val items = listOf(
                Triple("Devam Et", "play", Overlay.None), Triple("Karakterler", "user", Overlay.Characters),
                Triple("Profil", "profile", Overlay.Profile), Triple("Kozmetik", "hanger", Overlay.Cosmetics),
                Triple("Sıralama", "trophy", Overlay.Top), Triple("Hile Paneli", "dev", Overlay.Cheat), Triple("Ayarlar", "gear", Overlay.Settings),
            )
            for ((label, icon, o) in items) {
                PxButton(label, Modifier.fillMaxWidth(), if (o == Overlay.None) Px.Green else Px.Dark, icon = icon, size = 17.sp, height = 42.dp) { open(o) }
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
            PT(if (hard) "Hardcore: çantan ölüm yerinde kaldı. Koş, geri al!" else "Ekipmanın sende kalır. Nereden başlamak istersin?",
                14.sp, Px.Sub, Modifier.align(Alignment.CenterHorizontally), maxLines = 2)
            Spacer(Modifier.height(14.dp))
            PxButton("Kampta Doğ", Modifier.fillMaxWidth(), Px.Green, icon = "flame") { GameSession.cmd("respawn", "city" to false); respawned() }
            Spacer(Modifier.height(8.dp))
            val city = GameSession.ui("profile").optBoolean("city")
            PxButton(if (city) "Şehirde Doğ" else "Şehirde Doğ (Sv 10)", Modifier.fillMaxWidth(), if (city) Px.Blue else Px.Dark, icon = "shield", enabled = city) {
                GameSession.cmd("respawn", "city" to true); respawned()
            }
            Spacer(Modifier.height(8.dp))
            PxButton("Lobiye Dön", Modifier.fillMaxWidth(), Px.Dark, icon = "exit") { GameSession.cmd("respawn", "city" to false); toLobby() }
        }
    }
}

// =========================================================================== harita + seyahat
@Composable
private fun TravelMap(close: () -> Unit) {
    val img = remember { WorldMapImage.get() }
    val hud = remember { GameSession.hud() }
    val w = remember { GameSession.world() }
    val mapPx = w.optDouble("map_px", 23040.0).toFloat()
    val travel = w.optJSONArray("travel") ?: JSONArray()
    val prof = remember { GameSession.ui("profile") }
    val lvl = prof.optInt("level", 1)
    Box(Modifier.fillMaxSize().background(Color(0xFF2A3A20))) {
        if (img != null) Canvas(Modifier.fillMaxSize()) {
            // haritayı ekrana yay (kırp), oyuncu ve noktalar
            val k = maxOf(size.width, size.height) / img.width
            val ox = (size.width - img.width * k) / 2; val oy = (size.height - img.height * k) / 2
            drawImage(img, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(img.width, img.height),
                androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()), androidx.compose.ui.unit.IntSize((img.width * k).toInt(), (img.height * k).toInt()),
                filterQuality = FilterQuality.None)
            val s = img.width * k / mapPx
            fun at(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
            for (i in 0 until travel.length()) {
                val a = travel.getJSONArray(i)
                drawCircle(Color(0xFF15140F), 9.dp.toPx(), at(a.getDouble(2).toFloat(), a.getDouble(3).toFloat()))
                drawCircle(if (lvl >= a.getInt(4)) Px.Clan else Color(0xFF8A8A80), 6.dp.toPx(), at(a.getDouble(2).toFloat(), a.getDouble(3).toFloat()))
            }
            w.optJSONArray("boss")?.let { drawCircle(Color(0xFFE03030), 8.dp.toPx(), at(it.getDouble(0).toFloat(), it.getDouble(1).toFloat())) }
            val me = at(hud[Native.PX], hud[Native.PY])
            drawCircle(Color.White, 9.dp.toPx(), me)
            drawCircle(Color(0xFF3A8AFF), 6.dp.toPx(), me)
        }
        // seyahat kartları (sağ sütun)
        Column(
            Modifier.align(Alignment.CenterEnd).padding(12.dp).width(300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 0 until travel.length()) {
                val a = travel.getJSONArray(i)
                val need = a.getInt(4); val cost = a.getInt(5)
                val ok = lvl >= need
                PxPanel(Modifier.fillMaxWidth(), Color(0xE02B2B26), pad = 8.dp) {
                    PT(a.getString(1), 18.sp, if (ok) Px.Text else Px.Sub)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PxButton("Seyahat", height = 36.dp, size = 16.sp, enabled = ok) {
                            val r = GameSession.cmd("travel", "to" to a.getString(0))
                            Toaster.show(r.msg, r.ok)
                            if (r.ok) close()
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            PT(if (ok) "Seviye $need+" else "Seviye $need gerekli", 13.sp, if (ok) Px.Level else Color(0xFFE07060))
                            PT(if (cost > 0) "$cost altın" else "Ücretsiz", 13.sp, Px.Clan)
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PxIcon("close", size = 44.dp) { close() }
            Spacer(Modifier.width(10.dp))
            PT("Dünya Haritası", 26.sp, Px.Orange, outline = true)
        }
        PT("● Sen   ● Seyahat noktası   ● Titan", 14.sp, Px.Text, Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

// =========================================================================== posta
@Composable
private fun MailDialog(close: () -> Unit) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var compose by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (Account.loggedIn) Account.mail { r -> r.onSuccess { list = it }.onFailure { err = it.message ?: "" } }
    }
    PxDialog("Posta Kutusu", close, 560.dp) {
        if (!Account.loggedIn) { PT("Posta için hesap gerekli (Lobi → sol alt → Hesap aç).", 15.sp, Px.Sub, maxLines = 3); return@PxDialog }
        if (compose) { MailCompose { compose = false }; return@PxDialog }
        Column(Modifier.height(250.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val l = list
            when {
                l == null -> PT(err.ifEmpty { "Yükleniyor…" }, 15.sp, Px.Sub, maxLines = 3)
                l.length() == 0 -> PT("Posta kutun boş.", 15.sp, Px.Sub)
                else -> for (i in 0 until l.length()) {
                    val m = l.getJSONObject(i)
                    val items = m.optJSONArray("items") ?: JSONArray()
                    val gift = m.optInt("gold") > 0 || items.length() > 0
                    Column(Modifier.fillMaxWidth().background(Px.Row).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PT(m.optString("title"), 17.sp, if (m.optBoolean("read")) Px.Text else Px.Clan, Modifier.weight(1f))
                            PT(m.optString("from"), 13.sp, Px.Sub)
                        }
                        if (m.optString("body").isNotEmpty()) PT(m.optString("body"), 14.sp, Px.Text, maxLines = 4)
                        if (gift) Row(verticalAlignment = Alignment.CenterVertically) {
                            if (m.optInt("gold") > 0) { Icon("coin", 20.dp, Pal.Gold); PT(" ${m.optInt("gold")}  ", 14.sp, Px.Clan) }
                            for (k in 0 until items.length()) {
                                val it = items.getJSONObject(k)
                                SpriteImage("i/${it.optString("id")}", Modifier.size(26.dp)); PT("x${it.optInt("n")} ", 13.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            if (m.optBoolean("claimed")) PT("Alındı", 14.sp, Px.Sub)
                            else PxButton("Al", height = 34.dp, size = 15.sp) {
                                Account.claimMail(m.optLong("id")) { r ->
                                    r.onSuccess { g ->
                                        val res = GameSession.cmd("mail_claim", "gold" to g.optInt("gold"), "items" to (g.optJSONArray("items") ?: JSONArray()))
                                        Toaster.show(res.msg, res.ok); GameSession.save(cloud = true); tick++
                                    }.onFailure { Toaster.show(it.message ?: "Hata", false) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PxButton("Mektup Yaz", Modifier.fillMaxWidth(), Px.Blue, icon = "mail", size = 16.sp, height = 40.dp) { compose = true }
    }
}

@Composable
private fun MailCompose(done: () -> Unit) {
    var to by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(to, { to = it.take(16) }, "Alıcı oyuncu adı", Modifier.fillMaxWidth())
        Field(title, { title = it.take(40) }, "Konu", Modifier.fillMaxWidth())
        Field(body, { body = it.take(500) }, "Mesaj", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PxButton("Vazgeç", Modifier.weight(1f), Px.Dark, height = 40.dp, size = 16.sp) { done() }
            PxButton("Gönder", Modifier.weight(1f), height = 40.dp, size = 16.sp) {
                Account.sendMail(to, title.ifEmpty { "Mektup" }, body) { e -> if (e != null) Toaster.show(e, false) else { Toaster.show("Gönderildi"); done() } }
            }
        }
    }
}

// =========================================================================== oyuncular
@Composable
private fun PlayersDialog(close: () -> Unit) {
    var list by remember { mutableStateOf(Online.players()) }
    LaunchedEffect(Unit) { while (true) { list = Online.players(); delay(1500) } }
    PxDialog("Sunucudaki Oyuncular", close) {
        PT("${GameSession.server.value.title} · ${Online.status.value} · ${Online.role.value}", 13.sp, if (Online.online.value) Px.Name else Px.Sub, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.height(230.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().background(Px.RowHi).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Account.clan.value?.let { PT(it.name + " ", 15.sp, Px.Clan) }
                PT(Account.name.value.ifEmpty { "Sen" } + " (sen)", 15.sp, Px.Name)
            }
            if (list.isEmpty()) PT(if (Online.online.value) "Şu an bu sunucuda başka kimse yok." else "Çevrimdışısın.", 15.sp, Px.Sub, maxLines = 2)
            list.forEachIndexed { i, (n, c) ->
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Px.Row else Color.Transparent).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    if (c.isNotEmpty()) PT("$c ", 15.sp, Px.Clan)
                    PT(n, 15.sp)
                }
            }
        }
        PT("Takas için oyuncuya yaklaş, takas tuşu çıkar.", 12.sp, Px.Sub)
    }
}
