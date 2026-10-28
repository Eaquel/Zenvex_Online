package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Yönetici paneli (yalnız ID 0 yönetici hesabında görünür; sunucu da her işlemde yetkiyi doğrular).
 *  Oyuncular · Posta · Etkinlik · Bakım · Hileler (kendi karakterin, serbest değerlerle)
 */
@Composable
fun DevMenu(onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("Hazır.") }
    var ok by remember { mutableStateOf(true) }
    val report: (String, Boolean) -> Unit = { m, g -> log = m; ok = g }
    var file by remember { mutableStateOf(-1L) }
    Dim {
        Column(Modifier.width(820.dp).fillMaxHeight(0.94f).metalFrame().clickable(remember { MutableInteractionSource() }, null) {}
            .padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("shield", 26.dp, Px.Orange)
                Spacer(Modifier.width(8.dp))
                PT("Yönetici Paneli", 24.sp, Px.Orange)
                Spacer(Modifier.width(10.dp))
                PT("${Account.name.value} · ID ${fmtId(Account.id.value)}", 13.sp, Px.Sub)
                Spacer(Modifier.weight(1f))
                PxIcon("close", size = 38.dp) { onClose() }
            }
            if (!Account.admin.value) { PT("Bu Hesapta Yönetici Yetkisi Yok.", 15.sp, Px.Sub); return@Column }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Oyuncular" to "people", "Bildirimler" to "shield", "Destek" to "chat", "Ekonomi" to "coin", "Posta" to "mail", "Etkinlik" to "star", "Bakım" to "gear",
                    "Hileler" to "dev").forEachIndexed { i, (n, ic) ->
                    PxButton(n, Modifier.weight(1f), if (tab == i) Px.Green else Px.Dark, icon = ic, size = 13.sp, height = 38.dp) { tab = i }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (tab) {
                    0 -> PlayersTab(report)
                    // V15 (R41c, R41b): bildirimler iki profil kartıyla, destek talepleri fotoğraflarıyla
                    1 -> AdminReportsTab({ file = it }, report)
                    2 -> AdminSupportTab({ file = it }, report)
                    3 -> AdminEconomyTab({ file = it }, report)
                    4 -> MailTab(report)
                    5 -> EventTab(report)
                    6 -> MaintTab(report)
                    else -> CheatTab(report)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().background(Color(0xFF15140F)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                PT((if (ok) "✔ " else "✖ ") + log, 14.sp, if (ok) Px.Name else Color(0xFFFF7A6A), maxLines = 3)
            }
        }
    }
    if (file > 0) AdminPlayerFile(file) { file = -1 }
}

@Composable
private fun Section(t: String, sub: String = "") {
    PT(t, 16.sp, Px.Clan, Modifier.padding(top = 8.dp, bottom = 2.dp))
    if (sub.isNotEmpty()) PT(sub, 12.sp, Px.Sub, maxLines = 2)
    Spacer(Modifier.height(4.dp))
}

// =========================================================================== oyuncular
@Composable
private fun PlayersTab(report: (String, Boolean) -> Unit) {
    val A = Account
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(JSONArray()) }
    var file by remember { mutableStateOf(-1L) }
    if (file >= 0) AdminPlayerFile(file) { file = -1 }
    Section("Oyuncu Bul", "Ad ya da ID yaz; oyuncuya dokununca dosyası açılır (yasak, eşya, güvenlik, hesap işlemleri oradadır).")
    Row(verticalAlignment = Alignment.CenterVertically) {
        val search = { A.adminFind(q) { r -> r.onSuccess { found = it; report("${it.length()} Oyuncu Bulundu", true) }.onFailure { report(it.message ?: "", false) } } }
        Field(q, { q = it.take(20) }, "Oyuncu Adı / ID", Modifier.weight(1f)) { search() }
        Spacer(Modifier.width(6.dp))
        PxButton("Ara", height = 44.dp, size = 14.sp, icon = "search") { search() }
    }
    Spacer(Modifier.height(4.dp))
    for (i in 0 until found.length()) {
        val o = found.getJSONObject(i)
        val id = o.optLong("id")
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(Px.Row).clickable { file = id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PT("${o.optString("name")} · ${fmtId(id)} · Sv ${o.optInt("level")} · ${o.optInt("kills")} Öldürme" +
                (o.optString("clan").takeIf { it.isNotEmpty() && it != "null" }?.let { " · $it" } ?: ""), 14.sp, modifier = Modifier.weight(1f))
            if (o.optBoolean("deleted")) PT("SİLİNDİ ", 12.sp, Color(0xFFFF6A5A))
            else if (o.optBoolean("delete_req")) PT("SİLME TALEBİ ", 12.sp, Px.Orange)
            if (!o.isNull("banned_until")) PT("YASAKLI ", 12.sp, Color(0xFFFF6A5A))
            Icon("arrow", 18.dp, Px.Sub)
        }
    }
    Section("Hesap Silme Talepleri")
    AdminDeleteRequests { file = it }
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
    Section("Alıcı", "Tek oyuncuya ya da sunucudaki herkese. Alıcının cihazına bildirim de gider.")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Tek Oyuncu", color = if (!all) Px.Blue else Px.Dark, height = 38.dp, size = 13.sp) { all = false; ask = false }
        PxButton("Herkes", color = if (all) Px.Red else Px.Dark, height = 38.dp, size = 13.sp) { all = true; ask = false }
        if (!all) Field(to, { to = it.take(16) }, "Oyuncu Adı ya da ID", Modifier.weight(1f))
        else PT("Tüm oyunculara gidecek", 14.sp, Px.Orange)
    }
    Section("Tür")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Hediye" to "gift", "Etkinlik Duyurusu" to "star", "Sistem Mesajı" to "info").forEachIndexed { i, (n, ic) ->
            PxButton(n, color = if (kind == i) Px.Green else Px.Dark, icon = ic, height = 36.dp, size = 13.sp) { kind = i }
        }
    }
    Section("İçerik", "Başlık ve mesaj, oyuncunun posta kutusunda ve bildirimde aynen görünür.")
    Field(title, { title = it.take(40) }, "Başlık (Örn: Hafta Sonu Hediyesi)", Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Field(body, { body = it.take(500) }, "Mesaj", Modifier.fillMaxWidth())
    Section("Ekler", "Boş bırakılabilir. Eşyaları seçiciden adetle ekle; aynı eşya tekrar eklenince adet toplanır.")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(gold, { gold = it.filter(Char::isDigit).take(7) }, "Altın", Modifier.width(120.dp))
        Field(gems, { gems = it.filter(Char::isDigit).take(6) }, "Elmas", Modifier.width(110.dp))
        Field(xp, { xp = it.filter(Char::isDigit).take(7) }, "XP", Modifier.width(110.dp))
        PxButton("Eşya Ekle", color = Px.Blue, icon = "bag", height = 42.dp, size = 13.sp) { picking = true }
    }
    if (items.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((id, n) in items.toList()) {
                Column(Modifier.clickable { items.remove(id to n) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    PxSlot(JSONObject().put("id", id).put("count", n), 50.dp)
                    PT("Kaldır", 9.sp, Px.Sub)
                }
            }
        }
    }
    // önizleme: oyuncunun göreceği posta
    Section("Önizleme")
    Column(Modifier.fillMaxWidth().pxPanel(Color(0xE0201F1C)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(when (kind) { 1 -> "star"; 2 -> "info"; else -> "gift" }, 22.dp, Px.Clan)
            Spacer(Modifier.width(6.dp))
            PT(title.ifBlank { "Başlık" }, 16.sp, Px.Name)
            Spacer(Modifier.weight(1f))
            PT("Zenvex", 12.sp, Px.Sub)
        }
        PT(body.ifBlank { "Mesaj metni burada görünür." }, 13.sp, Px.Text, maxLines = 6)
        val g = gold.toIntOrNull() ?: 0; val gm = gems.toIntOrNull() ?: 0; val x = xp.toIntOrNull() ?: 0
        if (g > 0 || gm > 0 || x > 0 || items.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            PT("Ekler: " + listOfNotNull("$g Altın".takeIf { g > 0 }, "$gm Elmas".takeIf { gm > 0 }, "$x XP".takeIf { x > 0 }).plus(
                items.map { (id, n) -> "$n × " + (allItems().let { a -> (0 until a.length()).map { a.optJSONArray(it) }.firstOrNull { it?.optString(0) == id }?.optString(1) ?: id }) }
            ).joinToString(", "), 12.sp, Px.Clan, maxLines = 3)
        }
    }
    Spacer(Modifier.height(8.dp))
    val canSend = !busy && title.isNotBlank() && (all || to.isNotBlank())
    PxButton(if (busy) "Gönderiliyor…" else if (ask) "Emin Misin? ${if (all) "Herkese" else to} Gönderilecek" else "Gönder",
        Modifier.fillMaxWidth(), if (ask) Px.Red else Px.Green, icon = "mail", enabled = canSend, height = 46.dp, size = 16.sp) {
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

// =========================================================================== etkinlik
@Composable
private fun EventTab(report: (String, Boolean) -> Unit) {
    var cur by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var ask by remember { mutableStateOf("") }
    LaunchedEffect(tick) { Account.eventCurrent { r -> cur = r.getOrNull() ?: JSONObject() } }
    val c = cur
    Section("Etkinlikler", "Her etkinlik 2 hafta sürer. Bitince ilk 100, ilk 10 ve 1-2-3'e ödüller posta ile otomatik gider.")
    if (c == null) { PT("Yükleniyor…", 13.sp, Px.Sub); return }
    if (c.optBoolean("active")) {
        Column(Modifier.fillMaxWidth().pxPanel(Color(0xE0302A1C)).padding(10.dp)) {
            PT("Şu An: ${c.optString("title")}", 17.sp, Px.Name)
            PT("Bitişe ${fmtLeft(c.optLong("ends") - System.currentTimeMillis())} · ${c.optInt("players")} Katılımcı", 13.sp, Px.Sub)
            Spacer(Modifier.height(6.dp))
            PxButton(if (ask == "stop") "Emin Misin? Ödüller Şimdi Dağıtılır" else "Etkinliği Bitir", color = Px.Red, height = 38.dp, size = 13.sp) {
                if (ask != "stop") ask = "stop" else { ask = ""; Account.adminEventStop { r -> r.onSuccess { report(it, true) }.onFailure { report(it.message ?: "", false) }; tick++ } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    for (e in EVENTS) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Px.Row).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(e.icon, 30.dp, Px.Clan)
            Spacer(Modifier.width(8.dp))
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

// =========================================================================== bakım
@Composable
private fun MaintTab(report: (String, Boolean) -> Unit) {
    // çok dilli mesaj: her dil ayrı yazılır, oyuncu kendi dilini görür (dil yoksa İngilizce, sonra Türkçe)
    val msgs = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(MaintMsg.parse(Gate.maintenance.value?.optString("msg") ?: "")) } }
    var lang by remember { mutableStateOf("tr") }
    val on = Gate.maintenance.value != null
    Section("Bakım Modu", "Açıkken yönetici dışında kimse giremez; oyundakiler 15 sn içinde bakım ekranına düşer.")
    PT(if (on) "Durum: BAKIMDA" else "Durum: Açık", 15.sp, if (on) Px.Orange else Px.Name)
    Spacer(Modifier.height(6.dp))
    PT("Mesaj Dili", 13.sp, Px.Sub)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((code, name) in com.zenvex.online.game.I18n.LANGS) {
            val filled = !msgs[code].isNullOrBlank()
            PxButton((if (filled) "✓ " else "") + code.uppercase(), Modifier.weight(1f), if (lang == code) Px.Green else if (filled) Px.Blue else Px.Dark, height = 34.dp, size = 12.sp) { lang = code }
        }
    }
    Spacer(Modifier.height(4.dp))
    PT(com.zenvex.online.game.I18n.LANGS[lang] ?: lang, 12.sp, Px.Clan)
    Field(msgs[lang] ?: "", { msgs[lang] = it.take(300) }, "Bakım Mesajı (Bu Dili Konuşan Oyuncular Görür)", Modifier.fillMaxWidth())
    PT("Boş bırakılan dilde İngilizce, o da yoksa Türkçe gösterilir.", 11.sp, Px.Sub, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    PxButton(if (on) "Bakımı Bitir" else "Bakımı Başlat", color = if (on) Px.Green else Px.Red, height = 44.dp, size = 15.sp) {
        val packed = MaintMsg.encode(msgs)
        if (packed.length > 1900) { report("Mesajlar çok uzun (en çok ~1900 karakter)", false); return@PxButton }
        Account.adminMaintenance(!on, packed) { r -> r.onSuccess { Gate.poll(); report(if (!on) "Bakım Başladı" else "Bakım Bitti", true) }.onFailure { report(it.message ?: "", false) } }
    }
}

// =========================================================================== hileler (kendi karakterin)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheatTab(report: (String, Boolean) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val st = remember(tick) { GameSession.ui("cheat") }
    fun run(c: String) {
        val r = GameSession.cmd("cheat", "text" to c)
        report(r.msg.ifEmpty { if (r.ok) "Tamam" else "Hata" }, r.ok); tick++
    }
    var level by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("") }
    var gems by remember { mutableStateOf("") }
    var xp by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var zKind by remember { mutableStateOf("walker") }
    var zN by remember { mutableStateOf("5") }
    var zLv by remember { mutableStateOf("") }
    var tx by remember { mutableStateOf("") }
    var ty by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var resetAsk by remember { mutableStateOf(false) }
    if (picking) ItemPickerDialog("Eşya Al", { picking = false }) { id, n -> run("item $id $n") }
    PT("Sv ${st.optInt("level")} · ${fmt(st.optInt("gold"))} Altın · X ${st.optInt("x")} Y ${st.optInt("y")}", 13.sp, Px.Sub)
    Section("Durum")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, key, cmd) in listOf(Triple("Ölümsüzlük", "god", "god"), Triple("Tek Vuruş", "onehit", "onehit"),
            Triple("Beklemesiz", "nocd", "nocd"), Triple("Görünmez", "invis", "invis"))) {
            val on = st.optBoolean(key)
            PxButton("$label: ${if (on) "Açık" else "Kapalı"}", color = if (on) Px.Green else Px.Dark, height = 36.dp, size = 13.sp) { run(cmd) }
        }
        PxButton("Can + Enerji Doldur", color = Px.Dark, height = 36.dp, size = 13.sp) { run("heal") }
    }
    Section("Değerler", "İstediğin sayıyı yaz ve uygula.")
    @Composable fun NumRow(label: String, v: String, set: (String) -> Unit, btn: String, cmd: (String) -> String) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            PT(label, 14.sp, Px.Text, Modifier.width(110.dp))
            Field(v, { set(it.filter { c -> c.isDigit() || c == '.' }.take(9)) }, "Sayı", Modifier.width(140.dp))
            PxButton(btn, color = Px.Blue, height = 40.dp, size = 13.sp, enabled = v.isNotBlank()) { run(cmd(v)) }
        }
    }
    NumRow("Seviye", level, { level = it }, "Ayarla") { "level $it" }
    NumRow("Altın", gold, { gold = it }, "Ekle") { "gold $it" }
    NumRow("Elmas", gems, { gems = it }, "Ekle") { "gem $it" }
    NumRow("XP", xp, { xp = it }, "Ekle") { "xp $it" }
    NumRow("Köpek Seviyesi", dog, { dog = it }, "Ayarla") { "dog $it" }
    NumRow("Saat (0-23)", hour, { hour = it }, "Ayarla") { "time $it" }
    NumRow("Hız Çarpanı", speed, { speed = it }, "Ayarla") { "speed $it" }
    Section("Eşya", "Seçiciden eşya ve adet seç.")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Eşya Seç Ve Al", color = Px.Blue, icon = "bag", height = 40.dp, size = 14.sp) { picking = true }
        PxButton("Çantamı Boşalt", color = Px.Dark, height = 40.dp, size = 13.sp) { run("clearbag") }
    }
    Section("Zombi Çağır")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((k, n) in listOf("walker" to "Aylak", "runner" to "Koşucu", "dog" to "Köpek", "toxic" to "Zehirli", "dark" to "Karanlık",
            "tank" to "Tank", "mystery" to "Gizemli", "titan" to "Titan")) {
            PxButton(n, color = if (zKind == k) Px.Green else Px.Dark, height = 34.dp, size = 12.sp) { zKind = k }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(zN, { zN = it.filter(Char::isDigit).take(3) }, "Adet", Modifier.width(90.dp))
        Field(zLv, { zLv = it.filter(Char::isDigit).take(3) }, "Seviye (Boş: Benim)", Modifier.width(180.dp))
        PxButton("Çağır", color = Px.Red, height = 40.dp, size = 13.sp) {
            run(if (zKind == "titan") "boss" else "spawn $zKind ${zN.ifBlank { "1" }}" + (if (zLv.isNotBlank()) " $zLv" else ""))
        }
        PxButton("Hepsini Sil", color = Px.Dark, height = 40.dp, size = 13.sp) { run("killall") }
    }
    Section("Işınlan", "Kare koordinatı (harita 720 × 720) ya da hazır yer.")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(tx, { tx = it.filter(Char::isDigit).take(3) }, "X", Modifier.width(90.dp))
        Field(ty, { ty = it.filter(Char::isDigit).take(3) }, "Y", Modifier.width(90.dp))
        PxButton("Işınlan", color = Px.Blue, height = 40.dp, size = 13.sp, enabled = tx.isNotBlank() && ty.isNotBlank()) { run("tp $tx $ty") }
    }
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((n, c) in listOf("Kamp" to "camp", "Kişisel Üs" to "home", "Şehir" to "city", "Şehir Merkezi" to "downtown", "Derin Orman" to "deep",
            "Bataklık" to "swamp", "Yanık Orman" to "burnt", "Çöl Otoyolu" to "desert", "Kara Bataklık" to "bswamp", "Askerî Bölge" to "military",
            "Donmuş Liman" to "harbor", "Titan Arenası" to "boss", "Cephe Hattı" to "front")) {
            PxButton(n, color = Px.Dark, height = 34.dp, size = 12.sp) { run("tp $c") }
        }
    }
    Section("Araç")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Motor Getir", color = Px.Dark, height = 38.dp, size = 13.sp) { run("vehicle moto") }
        PxButton("Araba Getir", color = Px.Dark, height = 38.dp, size = 13.sp) { run("vehicle car") }
        PxButton("Yakıt + Tamir", color = Px.Dark, height = 38.dp, size = 13.sp) { run("fuel") }
    }
    Section("Profil")
    if (!resetAsk) PxButton("Profilimi Sıfırla", color = Px.Red, size = 14.sp, height = 38.dp) { resetAsk = true }
    else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        PT("Tüm ilerleme silinsin mi?", 14.sp, Color(0xFFFF7A6A))
        PxButton("Evet, Sıfırla", color = Px.Red, size = 14.sp, height = 38.dp) { resetAsk = false; run("reset") }
        PxButton("Vazgeç", color = Px.Dark, size = 14.sp, height = 38.dp) { resetAsk = false }
    }
}

/** Etkinlik tanımları (sunucudaki tür kimlikleriyle aynı). */
data class EventDef(val id: String, val title: String, val icon: String, val desc: String)

val EVENTS = listOf(
    EventDef("blood_moon", "Kan Ayı", "skull", "Zombi öldür; güçlü zombiler daha çok puan verir."),
    EventDef("titan_hunt", "Titan Avı", "flame", "Titanlara verdiğin hasar ve titan öldürmeleri puan."),
    EventDef("base_storm", "Üs Fırtınası", "shield", "Devlet üssü ele geçir ve asker öldür."),
    EventDef("scavengers", "Hurdacılar", "hammer", "Topla ve üret: ağaç, taş, hurda, üretilen eşya."),
    EventDef("pack_leader", "Sürü Lideri", "paw", "Köpeğinin öldürdükleri ve köpek seviyesi puan."),
    EventDef("last_breath", "Son Nefes", "clock", "Tehlikeli bölgede ölmeden geçirdiğin süre puan."),
)
