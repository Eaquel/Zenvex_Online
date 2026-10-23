package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import org.json.JSONArray
import org.json.JSONObject

private val RANKS = listOf("Üye", "Kıdemli Üye", "Yardımcı Lider", "Lider")
private val RANK_COL = listOf(Px.Text, Color(0xFF8CC8FF), Color(0xFFB277FF), Px.Clan)

/** Klan ekranı: klanın yoksa liste/davetler/kurma; varsa sekmeler (genel, üyeler, depo, görevler, güçlendirme, sıralama, kayıt). */
@Composable
fun ClanScreen(close: () -> Unit, inGame: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    val my = Account.clan.value
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(tick, my?.id) {
        if (my != null) Account.clanInfo(my.id) { r -> r.onSuccess { info = it }.onFailure { err = it.message ?: "" } }
    }
    PxFrame(if (my != null) my.name else "Klanlar", "people", close, right = {
        if (my != null) info?.let { PT("${it.optString("title")} · Sv ${it.optInt("level")}", 15.sp, Px.Clan) }
    }) {
        if (!Account.loggedIn) { PT("Klanlar İçin Önce Hesap Aç.", 15.sp, Px.Sub); return@PxFrame }
        if (my == null) { NoClanView { Account.refresh(); tick++ }; return@PxFrame }
        val c = info
        if (c == null) { PT(err.ifEmpty { "Yükleniyor…" }, 15.sp, Px.Sub); return@PxFrame }
        ClanView(c, my.rank, inGame) { Account.refresh(); tick++ }
    }
}

@Composable
private fun NoClanView(changed: () -> Unit) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    var invites by remember { mutableStateOf(JSONArray()) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Account.clans { r -> r.onSuccess { list = it } }
        Account.clanInvites { r -> r.onSuccess { invites = it } }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PxPanel(Modifier.weight(1f).fillMaxHeight(), pad = 8.dp) {
            PT("Klanlar", 16.sp, Px.Clan)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val l = list
                if (l == null) PT("Yükleniyor…", 14.sp, Px.Sub)
                else if (l.length() == 0) PT("Henüz Klan Yok. İlkini Sen Kur!", 14.sp, Px.Sub)
                else for (i in 0 until l.length()) ClanRow(l.getJSONObject(i), "Katıl") { id ->
                    Account.joinClan(id) { e -> if (e != null) Toaster.show(e, false) else changed() }
                }
            }
        }
        Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PxPanel(Modifier.fillMaxWidth(), pad = 8.dp) {
                PT("Davetler", 16.sp, Px.Clan)
                if (invites.length() == 0) PT("Davet Yok", 13.sp, Px.Sub)
                for (i in 0 until invites.length()) {
                    val o = invites.getJSONObject(i)
                    ClanRow(o, "Kabul Et", sub = "${o.optString("by")} davet etti") { id ->
                        Account.joinClan(id) { e -> if (e != null) Toaster.show(e, false) else changed() }
                    }
                }
            }
            PxPanel(Modifier.fillMaxWidth(), pad = 8.dp) {
                PT("Klan Kur", 16.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                Field(name, { name = it.take(20) }, "Klan Adı (3-20)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                PxButton("Kur", Modifier.fillMaxWidth(), height = 42.dp) {
                    Account.createClan(name, 0) { e -> if (e != null) Toaster.show(e, false) else changed() }
                }
                PT("Klan 30 üyeyle başlar, 9. seviyede 50 üyeye çıkar.", 11.sp, Px.Sub, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ClanRow(o: JSONObject, action: String, sub: String? = null, onAction: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Px.Row).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ClanEmblem(o.optInt("level"), o.optInt("top"))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            PT(o.optString("name"), 16.sp, Px.Clan)
            PT(sub ?: "${o.optString("title")} · Sv ${o.optInt("level")} · ${o.optInt("members")}/${o.optInt("cap", 30)}", 11.sp, Px.Sub)
        }
        PxButton(action, height = 34.dp, size = 13.sp) { onAction(o.optLong("id")) }
    }
}

/** Klan arması: V10 seviye kalkanı (1 tahta … 9 alevli altın), ortasında klan rengi ve seviye; ilk 3 klanın rakamı renkli */
@Composable
fun ClanEmblem(level: Int, top: Int, size: androidx.compose.ui.unit.Dp = 38.dp, color: Int? = null) {
    val col = when (top) { 1 -> Color(0xFFFFC83A); 2 -> Color(0xFFD8DEE6); 3 -> Color(0xFFCD7F32); else -> Color(0xFF5A5A50) }
    val name = "clan_${level.coerceIn(1, 9)}"
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (remember(name) { UiArt.image(ctx, name) } != null) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            ArtImage(name, Modifier.fillMaxSize())
            if (color != null) Box(Modifier.size(size * 0.3f).clip(CircleShape).background(Color(color or 0xFF000000.toInt()).copy(alpha = 0.85f)))
            PT("$level", (size.value * 0.3f).sp, if (top in 1..3) col else Color.White, outline = true)
        }
        return
    }
    Box(Modifier.size(size).clip(CircleShape).background(col).padding(3.dp).clip(CircleShape).background(Color(0xFF201F1C)), contentAlignment = Alignment.Center) {
        PT("$level", (size.value * 0.42f).sp, if (top in 1..3) col else Px.Text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClanView(c: JSONObject, myRank: Int, inGame: Boolean, changed: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Genel", "Üyeler", "Depo", "Görevler", "Güçlendirme", "Kayıt")
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i, s ->
                Box(Modifier.background(if (i == tab) Px.Panel else Px.PanelDark).clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    PT(s, 15.sp, if (i == tab) Px.Text else Px.Sub)
                }
            }
        }
        Box(Modifier.fillMaxSize().pxPanel().padding(10.dp)) {
            when (tab) {
                0 -> ClanOverview(c, myRank, changed)
                1 -> ClanMembers(c, myRank, changed)
                2 -> ClanDepot(c, myRank, inGame, changed)
                3 -> ClanQuests(c)
                4 -> ClanBoosts(c, myRank, changed)
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    val l = c.optJSONArray("log") ?: JSONArray()
                    for (i in 0 until l.length()) PT("• " + l.getJSONObject(i).optString("text"), 13.sp, Px.Text, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun ClanOverview(c: JSONObject, myRank: Int, changed: () -> Unit) {
    var motto by remember { mutableStateOf(c.optString("motto")) }
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClanEmblem(c.optInt("level"), c.optInt("top"), 72.dp, c.optInt("color").takeIf { c.has("color") })
                Spacer(Modifier.width(12.dp))
                Column {
                    PT(c.optString("name"), 24.sp, Px.Clan, outline = true)
                    PT("${c.optString("title")} · Seviye ${c.optInt("level")} / 9", 15.sp, Px.Text)
                    if (c.optInt("top") in 1..3) PT("Genel Sıralama #${c.optInt("top")}", 12.sp, Px.Orange)
                }
            }
            Spacer(Modifier.height(10.dp))
            val prev = c.optLong("prev"); val need = c.optLong("need"); val xp = c.optLong("xp")
            PxBar(if (c.optInt("level") >= 9) 1f else ((xp - prev).toFloat() / (need - prev).coerceAtLeast(1)), Px.Clan, Modifier.fillMaxWidth(), 14.dp)
            PT(if (c.optInt("level") >= 9) "En Yüksek Seviye" else "${fmt(xp.toInt())} / ${fmt(need.toInt())} Klan XP", 12.sp, Px.Sub)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PT("Üye ${c.optInt("member_count")}/${c.optInt("cap")}", 15.sp)
                PT("Bu Hafta ${fmt(c.optLong("week_xp").toInt())} XP", 15.sp, Px.Level)
                PT("Depo ${fmt(c.optLong("gold").toInt())} Altın", 15.sp, Px.Clan)
            }
            Spacer(Modifier.height(10.dp))
            PT("Seviye atlayınca tüm üyelere hesaba bağlı elmas ödülü gelir. Her hafta ilk 10 klanın en aktif 10 üyesine altın ve elmas verilir.",
                12.sp, Px.Sub, maxLines = 3)
        }
        Column(Modifier.width(300.dp)) {
            PT("Slogan", 14.sp, Px.Clan)
            if (myRank >= 3) {
                Field(motto, { motto = it.take(80) }, "Klan Sloganı", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                var officer by remember { mutableStateOf(c.optBoolean("officer_send")) }
                Row(Modifier.clickable { officer = !officer }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).background(if (officer) Px.Green else Px.Dark))
                    Spacer(Modifier.width(6.dp))
                    PT("Yardımcı Liderler Depodan Gönderebilsin", 12.sp, maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                PxButton("Kaydet", Modifier.fillMaxWidth(), height = 38.dp, size = 14.sp) {
                    Account.clanSettings(officer, motto) { r -> r.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) }; changed() }
                }
            } else PT(c.optString("motto").ifEmpty { "—" }, 14.sp, maxLines = 3)
            Spacer(Modifier.weight(1f))
            PxButton("Klandan Ayrıl", Modifier.fillMaxWidth(), Px.Red, height = 38.dp, size = 14.sp) { confirm = true }
        }
    }
    if (confirm) PxDialog("Klandan Ayrıl?", { confirm = false }) {
        PT(if (myRank >= 3) "Liderlik en yüksek rütbeli üyeye geçer." else "Klan katkın klanda kalır.", 14.sp, Px.Text, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        PxButton("Ayrıl", Modifier.fillMaxWidth(), Px.Red, height = 40.dp) {
            Account.leaveClan { e -> if (e != null) Toaster.show(e, false) else changed() }; confirm = false
        }
    }
}

@Composable
private fun ClanMembers(c: JSONObject, myRank: Int, changed: () -> Unit) {
    var invite by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<JSONObject?>(null) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val m = c.optJSONArray("members") ?: JSONArray()
            for (i in 0 until m.length()) {
                val o = m.getJSONObject(i)
                val r = o.optInt("rank")
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Px.Row else Color.Transparent).clickable { pick = o }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(o.optInt("avatar"), "", 34.dp)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (o.optBoolean("online")) Px.Name else Px.Sub))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        PT(o.optString("name"), 15.sp)
                        PT(RANKS.getOrElse(r) { "" } + if (!o.isNull("muted_until") && o.optString("muted_until").isNotEmpty()) " · Susturuldu" else "", 11.sp, RANK_COL.getOrElse(r) { Px.Text })
                    }
                    PT("Sv ${o.optInt("level")}", 13.sp, Px.Level)
                    Spacer(Modifier.width(10.dp))
                    PT("${fmt(o.optLong("week_xp").toInt())} XP", 12.sp, Px.Sub)
                }
            }
        }
        Column(Modifier.width(280.dp)) {
            if (myRank >= 1) {
                PT("Oyuncu Davet Et", 14.sp, Px.Clan)
                Field(invite, { invite = it.take(16) }, "Oyuncu Adı", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                PxButton("Davet Gönder", Modifier.fillMaxWidth(), Px.Blue, height = 38.dp, size = 14.sp) {
                    Account.clanInvite(invite) { r -> r.onSuccess { Toaster.show(it); invite = "" }.onFailure { Toaster.show(it.message ?: "", false) } }
                }
            }
            Spacer(Modifier.height(10.dp))
            PT("Yetkiler", 14.sp, Px.Clan)
            PT("Kıdemli Üye: Davet, Üyeleri Susturma\nYardımcı Lider: Kıdemli Atama, Atma, İzinle Depodan Gönderme\nLider: Her Şey", 11.sp, Px.Sub, maxLines = 6)
        }
    }
    pick?.let { o -> MemberActions(o, myRank, { pick = null }, changed) }
}

@Composable
private fun MemberActions(o: JSONObject, myRank: Int, close: () -> Unit, changed: () -> Unit) {
    val r = o.optInt("rank"); val id = o.optLong("id")
    val res: (Result<String>) -> Unit = { x -> x.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) }; close(); changed() }
    PxDialog(o.optString("name"), close) {
        PT(RANKS.getOrElse(r) { "" } + " · Sv ${o.optInt("level")}", 14.sp, RANK_COL.getOrElse(r) { Px.Text })
        Spacer(Modifier.height(8.dp))
        if (id == Account.id.value) { PT("Bu Sensin.", 14.sp, Px.Sub); return@PxDialog }
        if (myRank >= 1 && r < myRank && (myRank > 1 || r == 0)) {
            PT("Sohbette Sustur", 13.sp, Px.Clan)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, min) in listOf("5 Dk" to 5, "1 Sa" to 60, "6 Sa" to 360, "24 Sa" to 1440, "Kaldır" to 0))
                    PxButton(label, height = 34.dp, size = 12.sp, color = if (min == 0) Px.Dark else Px.Blue) { Account.clanMute(id, min, res) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (myRank >= 2 && r < myRank) {
            PT("Rütbe", 13.sp, Px.Clan)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val allowed = if (myRank >= 3) listOf(0, 1, 2, 3) else listOf(0, 1)
                for (k in allowed) PxButton(if (k == 3) "Liderliği Devret" else RANKS[k], height = 34.dp, size = 12.sp,
                    color = if (k == r) Px.Green else Px.Dark) { Account.clanRank(id, k, res) }
            }
            Spacer(Modifier.height(8.dp))
            PxButton("Klandan At", Modifier.fillMaxWidth(), Px.Red, height = 38.dp, size = 14.sp) { Account.clanKick(id, res) }
        }
        if (myRank < 1 || r >= myRank) PT("Bu Üye Üzerinde Yetkin Yok.", 13.sp, Px.Sub)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClanDepot(c: JSONObject, myRank: Int, inGame: Boolean, changed: () -> Unit) {
    var send by remember { mutableStateOf<JSONObject?>(null) }
    var goldIn by remember { mutableStateOf("") }
    val canSend = myRank >= 3 || (myRank >= 2 && c.optBoolean("officer_send"))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("coin", 22.dp, Px.Clan); PT("  ${fmt(c.optLong("gold").toInt())} Altın", 18.sp, Px.Clan)
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val d = c.optJSONArray("depot") ?: JSONArray()
                if (d.length() == 0) PT("Depo Boş", 14.sp, Px.Sub)
                for (i in 0 until d.length()) {
                    val o = d.getJSONObject(i)
                    PxSlot(JSONObject().put("id", o.optString("id")).put("count", o.optInt("n")), 58.dp,
                        modifier = Modifier.clickable { if (canSend) send = o else Toaster.show("Depodan Göndermeye Yetkin Yok", false) })
                }
            }
        }
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (myRank >= 3) {
                PT("Depoya Altın Yatır (Yalnız Lider)", 13.sp, Px.Clan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(goldIn, { goldIn = it.filter(Char::isDigit).take(7) }, "Altın", Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    PxButton("Yatır", height = 44.dp, size = 14.sp) {
                        val n = goldIn.toIntOrNull() ?: 0
                        if (!inGame) { Toaster.show("Altın Oyun İçinden Yatırılır", false); return@PxButton }
                        val have = GameSession.ui("profile").optInt("gold")
                        if (n <= 0 || n > have) { Toaster.show("Yetersiz Altın", false); return@PxButton }
                        Account.clanDeposit("coin", n) { r ->
                            r.onSuccess { GameSession.cmd("spend_gold", "n" to n); Toaster.show(it); goldIn = ""; GameSession.save(cloud = true) }
                                .onFailure { Toaster.show(it.message ?: "", false) }
                            changed()
                        }
                    }
                }
            }
            if (inGame) {
                PT("Çantandan Depoya Bırak", 13.sp, Px.Clan)
                DepositPicker(changed)
            } else PT("Eşya bırakmak için oyuna gir.", 12.sp, Px.Sub)
        }
    }
    send?.let { o -> DepotSend(c, o, { send = null }, changed) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepositPicker(changed: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val inv = remember(tick) { GameSession.ui("inventory").optJSONArray("slots") ?: JSONArray() }
    FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until inv.length()) {
            val s = inv.optJSONObject(i) ?: continue
            PxSlot(s, 46.dp, modifier = Modifier.clickable {
                val id = s.optString("id"); val n = s.optInt("count")
                // önce sunucu: başarılıysa çantadan düş (eşya kaybolmaz)
                Account.clanDeposit(id, n) { r ->
                    r.onSuccess { GameSession.cmd("remove_item", "slot" to i, "n" to n); GameSession.save(cloud = true); Toaster.show("$n× ${s.optString("name")} Depoya Bırakıldı"); tick++; changed() }
                        .onFailure { Toaster.show(it.message ?: "", false) }
                }
            })
        }
    }
}

@Composable
private fun DepotSend(c: JSONObject, item: JSONObject, close: () -> Unit, changed: () -> Unit) {
    var to by remember { mutableStateOf<JSONObject?>(null) }
    var n by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    PxDialog("Depodan Gönder", close, 560.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PxSlot(JSONObject().put("id", item.optString("id")).put("count", item.optInt("n")), 52.dp)
            Spacer(Modifier.width(8.dp))
            Field(n, { n = it.filter(Char::isDigit).take(5) }, "Adet", Modifier.width(100.dp))
            Spacer(Modifier.width(8.dp))
            Field(note, { note = it.take(120) }, "Açıklama (Neden Gönderiliyor)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.height(150.dp).verticalScroll(rememberScrollState())) {
            val m = c.optJSONArray("members") ?: JSONArray()
            for (i in 0 until m.length()) {
                val o = m.getJSONObject(i)
                Row(Modifier.fillMaxWidth().background(if (to?.optLong("id") == o.optLong("id")) Color(0x553A6AB0) else Color.Transparent)
                    .clickable { to = o }.padding(5.dp)) { PT(o.optString("name"), 14.sp) }
            }
        }
        PxButton("Gönder", Modifier.fillMaxWidth(), height = 40.dp, size = 15.sp, enabled = to != null) {
            Account.clanSend(to!!.optLong("id"), item.optString("id"), n.toIntOrNull() ?: 1, note) { r ->
                r.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) }; close(); changed()
            }
        }
    }
}

@Composable
private fun ClanQuests(c: JSONObject) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PT("Haftalık Klan Görevleri (Pazartesi Yenilenir)", 15.sp, Px.Clan)
        val q = c.optJSONArray("quests") ?: JSONArray()
        for (i in 0 until q.length()) {
            val o = q.getJSONObject(i)
            val done = o.optLong("n") >= o.optLong("goal")
            Column(Modifier.fillMaxWidth().pxPanel(if (done) Color(0xF0243A20) else Px.PanelDark).padding(10.dp)) {
                Row { PT(o.optString("title"), 15.sp, if (done) Px.Name else Px.Text, Modifier.weight(1f)); PT(o.optString("reward"), 12.sp, Px.Clan) }
                PxBar(o.optLong("n").toFloat() / o.optLong("goal").coerceAtLeast(1), if (done) Px.GreenHi else Px.Orange, Modifier.fillMaxWidth().padding(vertical = 4.dp), 10.dp)
                PT("${fmt(o.optLong("n").toInt())} / ${fmt(o.optLong("goal").toInt())}", 11.sp, Px.Sub)
            }
        }
    }
}

@Composable
private fun ClanBoosts(c: JSONObject, myRank: Int, changed: () -> Unit) {
    val b = c.optJSONObject("boosts") ?: JSONObject()
    val maxLv = minOf(c.optInt("level"), 5)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PT("Güçlendirmeleri yalnız lider depodaki altınla alır. Seviye sınırı: klan seviyesi (en çok 5).", 12.sp, Px.Sub, maxLines = 2)
        for ((k, name, per) in listOf(Triple("dmg", "Hasar", "%2"), Triple("xp", "Tecrübe", "%3"), Triple("speed", "Hız", "%2"))) {
            val lv = b.optInt(k)
            Row(Modifier.fillMaxWidth().pxPanel(Px.PanelDark).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(when (k) { "dmg" -> "sword"; "xp" -> "star"; else -> "bolt" }, 26.dp, Px.Clan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    PT("$name Güçlendirmesi · Sv $lv / 5", 16.sp)
                    PT("Seviye Başı $per · Şu An +%${lv * per.drop(1).toInt()}", 12.sp, Px.Sub)
                }
                if (myRank >= 3 && lv < 5) PxButton(if (lv < maxLv) "${fmt(3000 * (lv + 1))} Altın" else "Klan Sv ${lv + 1}", height = 38.dp, size = 13.sp,
                    enabled = lv < maxLv) {
                    Account.clanBoost(k) { r -> r.onSuccess { Toaster.show("$name Güçlendirildi") }.onFailure { Toaster.show(it.message ?: "", false) }; changed() }
                }
            }
        }
    }
}

@Composable
fun ClanRanking() {
    var kind by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(kind) { list = null; Account.clanRanking(if (kind == 0) "week" else "total") { r -> r.onSuccess { list = it } } }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PxButton("Haftalık", height = 34.dp, size = 13.sp, color = if (kind == 0) Px.Green else Px.Dark) { kind = 0 }
            PxButton("Genel", height = 34.dp, size = 13.sp, color = if (kind == 1) Px.Green else Px.Dark) { kind = 1 }
            Spacer(Modifier.width(10.dp))
            PT(if (kind == 0) "İlk 10: 10.000 Altın + 100 Elmas → 1.000 Altın + 10 Elmas (En Aktif 10 Üye)" else "Klanların Tüm Zamanlar Sıralaması",
                11.sp, Px.Sub, maxLines = 2)
        }
        Spacer(Modifier.height(4.dp))
        PT("Nasıl Girilir: üyelerin kazandığı her tecrübe ve klanının tuttuğu her üs (dakikada 60) klana tecrübe yazar. Haftalık sıralama Pazartesi sıfırlanır.",
            11.sp, Px.Sub, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val l = list
            if (l == null) PT("Yükleniyor…", 14.sp, Px.Sub)
            else if (l.length() == 0) PT("Henüz Sıralama Yok", 14.sp, Px.Sub)
            else for (i in 0 until l.length()) {
                val o = l.getJSONObject(i)
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Px.Row else Color.Transparent).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RankMedal(i + 1, if (i < 3) 44.dp else 36.dp)
                    Spacer(Modifier.width(6.dp))
                    ClanEmblem(o.optInt("level"), o.optInt("top"), 30.dp)
                    Spacer(Modifier.width(8.dp))
                    PT(o.optString("name"), 16.sp, if (i < 3) rankColor(i + 1) else Px.Clan, Modifier.weight(1f))
                    PT(o.optString("title"), 12.sp, Px.Sub)
                    Spacer(Modifier.width(10.dp))
                    PT("${fmt(o.optLong("xp").toInt())} XP", 13.sp, Px.Level)
                }
            }
        }
    }
}
