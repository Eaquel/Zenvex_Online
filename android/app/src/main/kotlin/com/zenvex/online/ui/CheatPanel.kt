package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Yönetici paneli (yalnız ID 0 yönetici hesabında görünür; sunucu da her işlemde yetkiyi doğrular).
 *
 * V19 yeni düzen: tam ekran konsol. Solda gruplu menü (Topluluk · Ekonomi · Sunucu · Test), üstte bölüm başlığı ve
 * sunucu durumu çipleri, ortada kartlar, altta son işlemin sonucu. Görseller varsa (admin_bg, admin_emblem,
 * admin_nav_*) onları kullanır; yoksa kodla çizilen hâli gösterir (istemler: docs/28_GORSEL_PROMPTLARI_V19.md).
 */
private enum class AdminPage(val title: String, val sub: String, val icon: String, val group: String) {
    Home("Genel Bakış", "Sunucunun durumu ve hızlı işlemler", "star", "Topluluk"),
    Players("Oyuncular", "Oyuncu ara, dosyasını aç: yasak, eşya, kanat, güvenlik, hesap", "people", "Topluluk"),
    Reports("Bildirimler", "Oyuncuların gönderdiği şikâyetler", "shield", "Topluluk"),
    Support("Destek", "Destek talepleri ve fotoğrafları", "chat", "Topluluk"),
    Economy("Ekonomi", "Altın ve elmas hareketleri, şüpheli kazançlar", "coin", "Ekonomi"),
    Mail("Posta", "Tek oyuncuya ya da herkese hediye, duyuru, sistem mesajı", "mail", "Ekonomi"),
    Events("Etkinlik", "İki haftalık etkinliği başlat ya da bitir", "flame", "Sunucu"),
    Maint("Bakım", "Sunucuyu bakıma al; mesajı her dilde ayrı yaz", "gear", "Sunucu"),
    Hero("Karakterim", "Kendi karakterin: durumlar, değerler, eşya", "user", "Test"),
    World("Dünya", "Yaratık çağır, bölgeye ışınlan, araç getir", "map", "Test"),
}

private val AdminGold = Color(0xFFFFC94A)
private val AdminInk = Color(0xFF0E0D0A)
private val AdminCard = Color(0xE61B1A16)
private val AdminLine = Color(0x33FFC94A)

@Composable
fun DevMenu(onClose: () -> Unit, start: Int = 0) {
    var page by remember { mutableStateOf(AdminPage.entries[start]) }
    var log by remember { mutableStateOf("Hazır.") }
    var ok by remember { mutableStateOf(true) }
    val report: (String, Boolean) -> Unit = { m, g -> log = m; ok = g }
    var file by remember { mutableStateOf(-1L) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(AdminInk).clickable(remember { MutableInteractionSource() }, null) {}.imePadding()) {
        if (UiArt.has(ctx, "admin_bg")) ArtImage("admin_bg", Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF00E0D0A), Color(0xC80E0D0A), Color(0xE00E0D0A)))))
        if (!Account.admin.value) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon("lock", 40.dp, Px.Sub)
                PT("Bu Hesapta Yönetici Yetkisi Yok.", 16.sp, Px.Sub)
                Spacer(Modifier.height(10.dp))
                PxButton("Kapat", color = Px.Dark, height = 40.dp, size = 14.sp) { onClose() }
            }
            return@Box
        }
        Row(Modifier.fillMaxSize()) {
            Sidebar(page, { page = it }, onClose)
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
                TopBar(page)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (page) {
                        AdminPage.Home -> HomePage({ page = it }, report)
                        AdminPage.Players -> PlayersTab { file = it }
                        AdminPage.Reports -> AdminCardBox("Gelen Bildirimler", "shield") { AdminReportsTab({ file = it }, report) }
                        AdminPage.Support -> AdminCardBox("Destek Talepleri", "chat") { AdminSupportTab({ file = it }, report) }
                        AdminPage.Economy -> AdminCardBox("Ekonomi Hareketleri", "coin") { AdminEconomyTab({ file = it }, report) }
                        AdminPage.Mail -> MailTab(report)
                        AdminPage.Events -> EventTab(report)
                        AdminPage.Maint -> MaintTab(report)
                        AdminPage.Hero -> HeroTab(report)
                        AdminPage.World -> WorldTab(report)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xF0151410))
                    .border(1.dp, if (ok) Color(0x5545E04F) else Color(0x66FF7A6A), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) "check" else "close", 16.dp, if (ok) Px.Name else Color(0xFFFF7A6A))
                    Spacer(Modifier.width(8.dp))
                    PT(log, 13.sp, if (ok) Px.Text else Color(0xFFFF9A8A), maxLines = 2)
                }
            }
        }
    }
    if (file >= 0) AdminPlayerFile(file) { file = -1 }
}

@Composable
private fun Sidebar(page: AdminPage, go: (AdminPage) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.width(214.dp).fillMaxHeight().background(Color(0xF2121110)).border(1.dp, AdminLine)
        .padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (UiArt.has(ctx, "admin_emblem")) ArtImage("admin_emblem", Modifier.size(40.dp)) else Icon("shield", 32.dp, AdminGold)
            Spacer(Modifier.width(8.dp))
            Column {
                PT("Yönetici", 17.sp, AdminGold, outline = true)
                PT("${Account.name.value} · ${fmtId(Account.id.value)}", 10.sp, Px.Sub)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            var group = ""
            for (p in AdminPage.entries) {
                if (p.group != group) {
                    group = p.group
                    PT(group.uppercase(java.util.Locale.forLanguageTag("tr-TR")), 10.sp, Color(0xFF7D7860), Modifier.padding(start = 6.dp, top = 8.dp, bottom = 3.dp))
                }
                val on = p == page
                Row(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (on) Color(0x40FFC94A) else Color.Transparent)
                    .clickable { com.zenvex.online.game.Audio.play("click", 0.6f, bus = com.zenvex.online.game.Audio.Bus.Ui); go(p) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(if (on) AdminGold else Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    val art = "admin_nav_" + p.name.lowercase()
                    if (UiArt.has(ctx, art)) ArtImage(art, Modifier.size(22.dp)) else Icon(p.icon, 18.dp, if (on) AdminGold else Px.Sub)
                    Spacer(Modifier.width(8.dp))
                    PT(p.title, 13.sp, if (on) Px.Text else Px.Sub)
                }
            }
        }
        PxButton("Paneli Kapat", Modifier.fillMaxWidth(), Px.Dark, icon = "close", height = 38.dp, size = 13.sp) { close() }
    }
}

@Composable
private fun TopBar(page: AdminPage) {
    var ev by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { Account.eventCurrent { r -> ev = r.getOrNull() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(page.icon, 26.dp, AdminGold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            PT(page.title, 21.sp, Px.Text, outline = true)
            PT(page.sub, 11.sp, Px.Sub)
        }
        val maint = Gate.maintenance.value != null
        StatusChip(if (maint) "Bakımda" else "Sunucu Açık", if (maint) Px.Orange else Px.Name)
        Spacer(Modifier.width(6.dp))
        val e = ev
        StatusChip(if (e?.optBoolean("active") == true) e.optString("title") else "Etkinlik Yok", if (e?.optBoolean("active") == true) AdminGold else Px.Sub)
    }
    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(AdminLine))
}

@Composable
private fun StatusChip(text: String, col: Color) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC151410)).border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(col))
        Spacer(Modifier.width(6.dp))
        PT(text, 11.sp, col)
    }
}

/** Başlıklı kart: panelin bütün bölümleri bu kartlardan oluşur */
@Composable
private fun AdminCardBox(title: String, icon: String, modifier: Modifier = Modifier, sub: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(AdminCard)
        .border(1.dp, AdminLine, RoundedCornerShape(8.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 18.dp, AdminGold)
            Spacer(Modifier.width(8.dp))
            PT(title, 15.sp, AdminGold)
        }
        if (sub.isNotEmpty()) PT(sub, 11.sp, Px.Sub, Modifier.padding(top = 2.dp), maxLines = 2)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Label(t: String) { PT(t, 12.sp, Px.Sub, Modifier.padding(top = 6.dp, bottom = 3.dp)) }

// =========================================================================== genel bakış
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomePage(go: (AdminPage) -> Unit, report: (String, Boolean) -> Unit) {
    var ev by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { Account.eventCurrent { r -> ev = r.getOrNull() ?: JSONObject() } }
    val me = remember { GameSession.ui("cheat") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val maint = Gate.maintenance.value != null
        StatTile("Sunucu", if (maint) "Bakımda" else "Açık", if (maint) Px.Orange else Px.Name, "gear", Modifier.weight(1f)) { go(AdminPage.Maint) }
        val e = ev
        val active = e?.optBoolean("active") == true
        StatTile("Etkinlik", if (active) e.optString("title") else "Yok", if (active) AdminGold else Px.Sub, "flame", Modifier.weight(1f),
            if (active) "${fmtLeft(e.optLong("ends") - System.currentTimeMillis())} · ${e.optInt("players")} Katılımcı" else "Başlatmak İçin Dokun") { go(AdminPage.Events) }
        StatTile("Bulunduğun Bölge", me.optString("area"), Px.Text, "map", Modifier.weight(1f), "${me.optInt("zombies")} Yaratık Çevrede") { go(AdminPage.World) }
    }
    Spacer(Modifier.height(8.dp))
    AdminCardBox("Hızlı İşlemler", "bolt") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Oyuncu Ara", "search") { go(AdminPage.Players) }
            QuickAction("Herkese Posta", "mail") { go(AdminPage.Mail) }
            QuickAction("Bildirimler", "shield") { go(AdminPage.Reports) }
            QuickAction("Destek", "chat") { go(AdminPage.Support) }
            QuickAction("Etkinlik", "flame") { go(AdminPage.Events) }
            QuickAction("Bakım", "gear") { go(AdminPage.Maint) }
            QuickAction("Eşya Al", "bag") { go(AdminPage.Hero) }
            QuickAction("Yaratık Çağır", "skull") { go(AdminPage.World) }
        }
    }
    AdminCardBox("Kendi Karakterin", "user") {
        PT("Sv ${me.optInt("level")} · ${fmt(me.optInt("gold"))} Altın · Can ${me.optDouble("hp").toInt()}/${me.optDouble("hp_max").toInt()} · Kare ${me.optInt("x")}, ${me.optInt("y")}", 13.sp, Px.Text)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PxButton("Can + Enerji Doldur", color = Px.Dark, height = 36.dp, size = 12.sp) { val r = GameSession.cmd("cheat", "text" to "heal"); report(r.msg, r.ok) }
            PxButton("Çevreyi Temizle", color = Px.Dark, height = 36.dp, size = 12.sp) { val r = GameSession.cmd("cheat", "text" to "killall"); report(r.msg, r.ok) }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, col: Color, icon: String, modifier: Modifier, sub: String = "", onClick: () -> Unit) {
    Column(modifier.height(96.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xF0242219), Color(0xF0171612))))
        .border(1.dp, AdminLine, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 16.dp, Px.Sub)
            Spacer(Modifier.width(6.dp))
            PT(title, 11.sp, Px.Sub)
        }
        Spacer(Modifier.weight(1f))
        PT(value, 19.sp, col, outline = true)
        if (sub.isNotEmpty()) PT(sub, 10.sp, Px.Sub)
    }
}

@Composable
private fun QuickAction(t: String, icon: String, onClick: () -> Unit) {
    Column(Modifier.width(118.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC262419)).border(1.dp, AdminLine, RoundedCornerShape(8.dp))
        .clickable { onClick() }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, 24.dp, AdminGold)
        Spacer(Modifier.height(4.dp))
        PT(t, 12.sp, Px.Text)
    }
}

// =========================================================================== oyuncular
@Composable
private fun PlayersTab(open: (Long) -> Unit) {
    val A = Account
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(JSONArray()) }
    var msg by remember { mutableStateOf("") }
    AdminCardBox("Oyuncu Bul", "search", sub = "Ad ya da ID yaz; oyuncuya dokununca dosyası açılır (yasak, eşya, kanat, güvenlik, hesap işlemleri oradadır).") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val search = { A.adminFind(q) { r -> r.onSuccess { found = it; msg = "${it.length()} Oyuncu Bulundu" }.onFailure { msg = it.message ?: "" } } }
            Field(q, { q = it.take(20) }, "Oyuncu Adı / ID", Modifier.weight(1f)) { search() }
            Spacer(Modifier.width(6.dp))
            PxButton("Ara", height = 44.dp, size = 14.sp, icon = "search") { search() }
        }
        if (msg.isNotEmpty()) PT(msg, 12.sp, Px.Sub, Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(4.dp))
        for (i in 0 until found.length()) {
            val o = found.getJSONObject(i)
            val id = o.optLong("id")
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF26251F)).clickable { open(id) }.padding(9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon("user", 18.dp, Px.Sub)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    PT(o.optString("name"), 14.sp, Px.Text)
                    PT("ID ${fmtId(id)} · Sv ${o.optInt("level")} · ${o.optInt("kills")} Öldürme" +
                        (o.optString("clan").takeIf { it.isNotEmpty() && it != "null" }?.let { " · $it" } ?: ""), 11.sp, Px.Sub)
                }
                if (o.optBoolean("deleted")) StatusChip("Silindi", Color(0xFFFF6A5A))
                else if (o.optBoolean("delete_req")) StatusChip("Silme Talebi", Px.Orange)
                if (!o.isNull("banned_until")) { Spacer(Modifier.width(4.dp)); StatusChip("Yasaklı", Color(0xFFFF6A5A)) }
                Spacer(Modifier.width(6.dp))
                Icon("next", 18.dp, Px.Sub)
            }
        }
    }
    AdminCardBox("Hesap Silme Talepleri", "close") { AdminDeleteRequests { open(it) } }
}

// =========================================================================== posta / bildirim
@Composable
private fun MailTab(report: (String, Boolean) -> Unit) {
    var all by remember { mutableStateOf(false) }
    var to by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) } // 0 hediye, 1 etkinlik, 2 sistem
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("") }
    var gems by remember { mutableStateOf("") }
    var xp by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<Pair<String, Int>>() }
    var picking by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    if (picking) ItemPickerDialog("Eke Eşya Ekle", { picking = false }) { id, n ->
        val ix = items.indexOfFirst { it.first == id }
        if (ix >= 0) items[ix] = id to (items[ix].second + n) else items += id to n
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1.2f)) {
            AdminCardBox("Alıcı Ve Tür", "people", sub = "Tek oyuncuya ya da sunucudaki herkese. Alıcının cihazına bildirim de gider.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PxButton("Tek Oyuncu", color = if (!all) Px.Blue else Px.Dark, height = 36.dp, size = 12.sp) { all = false; ask = false }
                    PxButton("Herkes", color = if (all) Px.Red else Px.Dark, height = 36.dp, size = 12.sp) { all = true; ask = false }
                    if (!all) Field(to, { to = it.take(16) }, "Oyuncu Adı ya da ID", Modifier.weight(1f))
                    else PT("Tüm oyunculara gidecek", 13.sp, Px.Orange)
                }
                Label("Tür")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hediye" to "gift", "Etkinlik" to "star", "Sistem" to "info").forEachIndexed { i, (n, ic) ->
                        PxButton(n, Modifier.weight(1f), if (kind == i) Px.Green else Px.Dark, icon = ic, height = 34.dp, size = 12.sp) { kind = i }
                    }
                }
            }
            AdminCardBox("İçerik", "mail", sub = "Başlık ve mesaj, oyuncunun posta kutusunda ve bildirimde aynen görünür.") {
                Field(title, { title = it.take(40) }, "Başlık (Örn: Hafta Sonu Hediyesi)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Field(body, { body = it.take(500) }, "Mesaj", Modifier.fillMaxWidth().height(96.dp), multiline = true)
            }
            AdminCardBox("Ekler", "gift", sub = "Boş bırakılabilir. Aynı eşya tekrar eklenince adet toplanır; eşyaya dokununca kalkar.") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(gold, { gold = it.filter(Char::isDigit).take(7) }, "Altın", Modifier.weight(1f))
                    Field(gems, { gems = it.filter(Char::isDigit).take(6) }, "Elmas", Modifier.weight(1f))
                    Field(xp, { xp = it.filter(Char::isDigit).take(7) }, "XP", Modifier.weight(1f))
                    PxButton("Eşya", color = Px.Blue, icon = "bag", height = 42.dp, size = 13.sp) { picking = true }
                }
                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((id, n) in items.toList()) PxSlot(JSONObject().put("id", id).put("count", n), 48.dp, modifier = Modifier.clickable { items.remove(id to n) })
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            AdminCardBox("Önizleme", "info", sub = "Oyuncunun göreceği posta") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xE0201F1C)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (kind) { 1 -> "star"; 2 -> "info"; else -> "gift" }, 22.dp, Px.Clan)
                        Spacer(Modifier.width(6.dp))
                        PT(title.ifBlank { "Başlık" }, 15.sp, Px.Name, Modifier.weight(1f))
                        PT("Zenvex", 11.sp, Px.Sub)
                    }
                    PT(body.ifBlank { "Mesaj metni burada görünür." }, 12.sp, Px.Text, maxLines = 8)
                    val g = gold.toIntOrNull() ?: 0; val gm = gems.toIntOrNull() ?: 0; val x = xp.toIntOrNull() ?: 0
                    if (g > 0 || gm > 0 || x > 0 || items.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        PT("Ekler: " + listOfNotNull("$g Altın".takeIf { g > 0 }, "$gm Elmas".takeIf { gm > 0 }, "$x XP".takeIf { x > 0 }).plus(
                            items.map { (id, n) -> "$n × " + (allItems().let { a -> (0 until a.length()).map { a.optJSONArray(it) }.firstOrNull { it?.optString(0) == id }?.optString(1) ?: id }) }
                        ).joinToString(", "), 11.sp, Px.Clan, maxLines = 3)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val canSend = !busy && title.isNotBlank() && (all || to.isNotBlank())
                PxButton(if (busy) "Gönderiliyor…" else if (ask) "Emin Misin? ${if (all) "Herkese" else to}" else "Gönder",
                    Modifier.fillMaxWidth(), if (ask) Px.Red else Px.Green, icon = "mail", enabled = canSend, height = 46.dp, size = 15.sp) {
                    if (!ask) { ask = true; return@PxButton }
                    ask = false; busy = true
                    val arr = JSONArray()
                    for ((id, n) in items) arr.put(JSONObject().put("id", id).put("n", n))
                    Account.adminMail2(if (all) "*" else to, title, body, gold.toIntOrNull() ?: 0, gems.toIntOrNull() ?: 0, xp.toIntOrNull() ?: 0, arr,
                        when (kind) { 1 -> "event"; 2 -> "system"; else -> "reward" }) { r ->
                        busy = false
                        r.onSuccess { report("$it Oyuncuya Posta Gönderildi", true); items.clear() }.onFailure { report(it.message ?: "Hata", false) }
                    }
                }
            }
        }
    }
}

// =========================================================================== etkinlik
@Composable
private fun EventTab(report: (String, Boolean) -> Unit) {
    var cur by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var ask by remember { mutableStateOf("") }
    LaunchedEffect(tick) { Account.eventCurrent { r -> cur = r.getOrNull() ?: JSONObject() } }
    val c = cur
    if (c == null) { PT("Yükleniyor…", 13.sp, Px.Sub); return }
    if (c.optBoolean("active")) {
        AdminCardBox("Şu An: ${c.optString("title")}", "flame", sub = "Bitişe ${fmtLeft(c.optLong("ends") - System.currentTimeMillis())} · ${c.optInt("players")} Katılımcı") {
            PxButton(if (ask == "stop") "Emin Misin? Ödüller Şimdi Dağıtılır" else "Etkinliği Bitir", color = Px.Red, height = 38.dp, size = 13.sp) {
                if (ask != "stop") ask = "stop" else { ask = ""; Account.adminEventStop { r -> r.onSuccess { report(it, true) }.onFailure { report(it.message ?: "", false) }; tick++ } }
            }
        }
    }
    AdminCardBox("Etkinlikler", "star", sub = "Her etkinlik 2 hafta sürer. Bitince ilk 100, ilk 10 ve 1-2-3'e ödüller posta ile otomatik gider.") {
        for (e in EVENTS) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF26251F)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (UiArt.has(LocalContext.current, "ev_" + e.art)) ArtImage("ev_" + e.art, Modifier.size(54.dp, 40.dp)) else Icon(e.icon, 30.dp, Px.Clan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    PT(e.title, 15.sp, Px.Name)
                    PT(e.desc, 11.sp, Px.Sub, maxLines = 2)
                }
                if (!c.optBoolean("active")) PxButton(if (ask == e.id) "Emin Misin?" else "Başlat", color = if (ask == e.id) Px.Red else Px.Green, height = 36.dp, size = 13.sp) {
                    if (ask != e.id) ask = e.id else {
                        ask = ""
                        Account.adminEventStart(e.id) { r -> r.onSuccess { report(it, true) }.onFailure { report(it.message ?: "", false) }; tick++ }
                    }
                }
            }
        }
    }
}

// =========================================================================== bakım
@Composable
private fun MaintTab(report: (String, Boolean) -> Unit) {
    // çok dilli mesaj: her dil ayrı yazılır, oyuncu kendi dilini görür (dil yoksa İngilizce, sonra Türkçe)
    val msgs = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(MaintMsg.parse(Gate.maintenance.value?.optString("msg") ?: "")) } }
    var lang by remember { mutableStateOf("tr") }
    val on = Gate.maintenance.value != null
    AdminCardBox(if (on) "Durum: Bakımda" else "Durum: Açık", "gear", sub = "Açıkken yönetici dışında kimse giremez; oyundakiler 15 sn içinde bakım ekranına düşer.") {
        PxButton(if (on) "Bakımı Bitir" else "Bakımı Başlat", color = if (on) Px.Green else Px.Red, height = 44.dp, size = 15.sp) {
            val packed = MaintMsg.encode(msgs)
            if (packed.length > 1900) { report("Mesajlar çok uzun (en çok ~1900 karakter)", false); return@PxButton }
            Account.adminMaintenance(!on, packed) { r -> r.onSuccess { Gate.poll(); report(if (!on) "Bakım Başladı" else "Bakım Bitti", true) }.onFailure { report(it.message ?: "", false) } }
        }
    }
    AdminCardBox("Bakım Mesajı", "chat", sub = "Boş bırakılan dilde İngilizce, o da yoksa Türkçe gösterilir. ✓ işaretli diller yazılmıştır.") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((code, _) in com.zenvex.online.game.I18n.LANGS) {
                val filled = !msgs[code].isNullOrBlank()
                PxButton((if (filled) "✓ " else "") + code.uppercase(), Modifier.weight(1f), if (lang == code) Px.Green else if (filled) Px.Blue else Px.Dark, height = 34.dp, size = 12.sp) { lang = code }
            }
        }
        Spacer(Modifier.height(4.dp))
        PT(com.zenvex.online.game.I18n.LANGS[lang] ?: lang, 12.sp, Px.Clan)
        Field(msgs[lang] ?: "", { msgs[lang] = it.take(300) }, "Bakım Mesajı (Bu Dili Konuşan Oyuncular Görür)", Modifier.fillMaxWidth().height(96.dp), multiline = true)
    }
}

// =========================================================================== test: kendi karakterin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTab(report: (String, Boolean) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val st = remember(tick) { GameSession.ui("cheat") }
    fun run(c: String) {
        val r = GameSession.cmd("cheat", "text" to c)
        report(r.msg.ifEmpty { if (r.ok) "Tamam" else "Hata" }, r.ok); tick++
    }
    var picking by remember { mutableStateOf(false) }
    var resetAsk by remember { mutableStateOf(false) }
    if (picking) ItemPickerDialog("Eşya Al", { picking = false }) { id, n -> run("item $id $n") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            AdminCardBox("Durumlar", "shield", sub = "Sv ${st.optInt("level")} · ${fmt(st.optInt("gold"))} Altın · Can ${st.optDouble("hp").toInt()}/${st.optDouble("hp_max").toInt()}") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, key) in listOf("Ölümsüzlük" to "god", "Tek Vuruş" to "onehit", "Beklemesiz" to "nocd", "Görünmez" to "invis")) {
                        val on = st.optBoolean(key)
                        PxButton("$label: ${if (on) "Açık" else "Kapalı"}", color = if (on) Px.Green else Px.Dark, height = 36.dp, size = 12.sp) { run(key) }
                    }
                    PxButton("Can + Enerji Doldur", color = Px.Dark, height = 36.dp, size = 12.sp) { run("heal") }
                }
            }
            AdminCardBox("Eşya", "bag", sub = "Yalnız oyunda olan eşyalar listelenir; türe göre süzebilirsin.") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PxButton("Eşya Seç Ve Al", color = Px.Blue, icon = "bag", height = 40.dp, size = 13.sp) { picking = true }
                    PxButton("İksir Seti", color = Px.Dark, height = 40.dp, size = 12.sp) { run("potions") }
                    PxButton("Çantamı Boşalt", color = Px.Dark, height = 40.dp, size = 12.sp) { run("clearbag") }
                }
            }
            AdminCardBox("Profil", "close") {
                if (!resetAsk) PxButton("Profilimi Sıfırla", color = Px.Red, size = 13.sp, height = 38.dp) { resetAsk = true }
                else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PT("Tüm ilerleme silinsin mi?", 13.sp, Color(0xFFFF7A6A))
                    PxButton("Evet, Sıfırla", color = Px.Red, size = 13.sp, height = 38.dp) { resetAsk = false; run("reset") }
                    PxButton("Vazgeç", color = Px.Dark, size = 13.sp, height = 38.dp) { resetAsk = false }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            AdminCardBox("Değerler", "star", sub = "Sayıyı yaz ve uygula.") {
                NumLine("Seviye", "Ayarla", { "level $it" }, ::run)
                NumLine("Altın", "Ekle", { "gold $it" }, ::run)
                NumLine("Elmas", "Ekle", { "gem $it" }, ::run)
                NumLine("XP", "Ekle", { "xp $it" }, ::run)
                NumLine("Köpek Seviyesi", "Ayarla", { "dog $it" }, ::run)
                NumLine("Hız Çarpanı", "Ayarla", { "speed $it" }, ::run)
                NumLine("Yetenek Kredisi", "Ekle", { "points $it" }, ::run)
            }
        }
    }
}

@Composable
private fun NumLine(label: String, btn: String, cmd: (String) -> String, run: (String) -> Unit) {
    var v by remember(label) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        PT(label, 13.sp, Px.Text, Modifier.width(118.dp))
        Field(v, { v = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "Sayı", Modifier.weight(1f))
        PxButton(btn, color = Px.Blue, height = 40.dp, size = 12.sp, enabled = v.isNotBlank()) { run(cmd(v)) }
    }
}

// =========================================================================== test: dünya
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldTab(report: (String, Boolean) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val st = remember(tick) { GameSession.ui("cheat") }
    fun run(c: String) {
        val r = GameSession.cmd("cheat", "text" to c)
        report(r.msg.ifEmpty { if (r.ok) "Tamam" else "Hata" }, r.ok); tick++
    }
    val areas = st.optJSONArray("areas") ?: JSONArray()
    val kinds = st.optJSONArray("kinds") ?: JSONArray()
    var kind by remember { mutableStateOf("walker") }
    var n by remember { mutableStateOf("5") }
    var lv by remember { mutableStateOf("") }
    var tx by remember { mutableStateOf("") }
    var ty by remember { mutableStateOf("") }
    AdminCardBox("Yaratık Çağır", "skull", sub = "Temel türler ve her bölgenin kendi yaratıkları. Seviye boşsa senin seviyen. Çevrede ${st.optInt("zombies")} yaratık var.") {
        // bölgeye göre gruplu: temel türler önce
        val groups = LinkedHashMap<String, MutableList<JSONArray>>()
        for (i in 0 until kinds.length()) {
            val k = kinds.optJSONArray(i) ?: continue
            groups.getOrPut(k.optString(2)) { mutableListOf() } += k
        }
        for ((area, list) in groups) {
            val title = if (area.isEmpty()) "Temel" else (0 until areas.length()).map { areas.optJSONArray(it) }.firstOrNull { it?.optString(0) == area }?.optString(1) ?: area
            Label(title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (k in list) PxButton(k.optString(1), color = if (kind == k.optString(0)) Px.Green else Px.Dark, height = 32.dp, size = 11.sp) { kind = k.optString(0) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(n, { n = it.filter(Char::isDigit).take(2) }, "Adet", Modifier.width(80.dp))
            Field(lv, { lv = it.filter(Char::isDigit).take(2) }, "Seviye", Modifier.width(100.dp))
            PxButton("Çağır", color = Px.Red, icon = "skull", height = 40.dp, size = 13.sp) { run("spawn $kind ${n.ifBlank { "1" }}" + (if (lv.isNotBlank()) " $lv" else "")) }
            PxButton("Titan", color = Color(0xFF7A3A2A), height = 40.dp, size = 13.sp) { run("boss") }
            Spacer(Modifier.weight(1f))
            PxButton("Hepsini Sil", color = Px.Dark, height = 40.dp, size = 13.sp) { run("killall") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdminCardBox("Bölgeye Işınlan", "map", Modifier.weight(1.3f), sub = "Şu an: ${st.optString("area")} · Kare ${st.optInt("x")}, ${st.optInt("y")}") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until areas.length()) {
                    val a = areas.optJSONArray(i) ?: continue
                    PxButton(a.optString(1), color = Px.Dark, height = 34.dp, size = 12.sp) { run("tp area ${a.optString(0)}") }
                }
                PxButton("Kamp", color = Px.Dark, height = 34.dp, size = 12.sp) { run("tp camp") }
                PxButton("Şehir", color = Px.Dark, height = 34.dp, size = 12.sp) { run("tp city") }
            }
            Label("Kare Koordinatı")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(tx, { tx = it.filter(Char::isDigit).take(3) }, "X", Modifier.width(80.dp))
                Field(ty, { ty = it.filter(Char::isDigit).take(3) }, "Y", Modifier.width(80.dp))
                PxButton("Işınlan", color = Px.Blue, height = 40.dp, size = 13.sp, enabled = tx.isNotBlank() && ty.isNotBlank()) { run("tp $tx $ty") }
            }
        }
        AdminCardBox("Araç Ve Zaman", "car", Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PxButton("Motor", Modifier.weight(1f), Px.Dark, height = 36.dp, size = 12.sp) { run("vehicle moto") }
                PxButton("Araba", Modifier.weight(1f), Px.Dark, height = 36.dp, size = 12.sp) { run("vehicle car") }
            }
            Spacer(Modifier.height(6.dp))
            PxButton("Yakıt + Tamir", Modifier.fillMaxWidth(), Px.Dark, height = 36.dp, size = 12.sp) { run("fuel") }
            Label("Oyun Hızı")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (k in listOf("0.5", "1", "2")) PxButton("×$k", Modifier.weight(1f), Px.Dark, height = 34.dp, size = 12.sp) { run("timescale $k") }
            }
        }
    }
}

/** Etkinlik tanımları (sunucudaki tür kimlikleriyle aynı); [art] assets/ui/ev_<art> afişi */
data class EventDef(val id: String, val title: String, val icon: String, val desc: String, val art: String = "")

val EVENTS = listOf(
    EventDef("blood_moon", "Kan Ayı", "skull", "Zombi öldür; güçlü zombiler daha çok puan verir.", "blood"),
    EventDef("titan_hunt", "Titan Avı", "flame", "Titanlara verdiğin hasar ve titan öldürmeleri puan.", "titan"),
    EventDef("base_storm", "Üs Fırtınası", "shield", "Devlet üssü ele geçir ve asker öldür.", "base"),
    EventDef("scavengers", "Hurdacılar", "hammer", "Topla ve üret: ağaç, taş, hurda, üretilen eşya.", "scrap"),
    EventDef("pack_leader", "Sürü Lideri", "paw", "Köpeğinin öldürdükleri ve köpek seviyesi puan.", "pack"),
    EventDef("last_breath", "Son Nefes", "clock", "Tehlikeli bölgede ölmeden geçirdiğin süre puan.", "breath"),
)
