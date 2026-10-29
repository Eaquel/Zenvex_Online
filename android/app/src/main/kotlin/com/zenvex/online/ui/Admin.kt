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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Online
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tsFmt = SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("tr"))
internal fun ts(ms: Long) = if (ms <= 0) "" else tsFmt.format(Date(ms))

/** Oyundaki eşyalar: [kimlik, ad, tür, nadirlik] (sıra = kayıttaki eşya numarası) */
internal fun allItems(): JSONArray = GameSession.world().optJSONArray("items") ?: JSONArray()

/**
 * Yönetici: oyuncu dosyası. Özet (yasak, mesaj, oyundan at, altın/elmas, yanına ışınlan), envanter (adetli silme + onay),
 * güvenlik (hile kayıtları, cihazlar, cihaz yasağı), son işlemler, sohbetler, özel mesajlar, hesap (sil / geri getir / anahtar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPlayerFile(target: Long, close: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var sec by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (eşya no, sahip olunan adet)
    LaunchedEffect(tick) {
        Account.adminPlayer(target) { r -> r.onSuccess { data = it }.onFailure { err = it.message ?: "Hata" } }
        Account.adminFlags(target) { r -> r.onSuccess { sec = it } }
    }
    val res: (Result<String>) -> Unit = { r -> r.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) }; tick++ }
    val items = remember { allItems() }
    fun itemName(i: Int) = items.optJSONArray(i)?.optString(1) ?: "#$i"
    fun itemId(i: Int) = items.optJSONArray(i)?.optString(0) ?: ""
    PxDialog("Oyuncu Dosyası", close, width = 920.dp) {
        val d = data
        if (d == null) { PT(err.ifEmpty { "Yükleniyor…" }, 14.sp, Px.Sub); return@PxDialog }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(d.optInt("avatar"), d.optString("frame"), 56.dp, photo = d.optString("photo"))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PT("${d.optString("name")} · ID ${fmtId(d.optLong("id"))}", 18.sp, Px.Name)
                val online = sec?.optBoolean("online") == true
                PT((if (online) "● Çevrimiçi (${sec?.optString("server")}) · " else "○ Çevrimdışı · ") +
                    "Sv ${d.optInt("level")} · ${fmt(d.optInt("kills"))} Öldürme · Klan: ${d.optJSONObject("clan")?.optString("name") ?: "—"}" +
                    (if (d.optBoolean("google")) " · Google" else ""), 12.sp, if (online) Px.Name else Px.Sub)
                if (d.optBoolean("deleted")) PT("HESAP SİLİNDİ · ${d.optString("deleted_reason")}", 12.sp, Color(0xFFFF6A5A))
                else if (d.optBoolean("delete_req")) PT("Oyuncu Hesabının Silinmesini İstedi", 12.sp, Px.Orange)
                if (!d.isNull("banned_until")) PT("YASAKLI: ${d.optString("banned_until").take(16)} · ${d.optString("ban_reason")}", 12.sp, Color(0xFFFF6A5A))
                val flags = sec?.optJSONArray("flags")?.length() ?: 0
                if (flags > 0) PT("Hile Şüphesi: $flags Kayıt (Güvenlik Sekmesi)", 12.sp, Px.Orange)
            }
            TagRow(d.optJSONArray("tags"))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Özet", "Envanter", "Güvenlik", "Son İşlemler", "Sohbetler", "Özel Mesajlar", "Hesap").forEachIndexed { i, n ->
                Box(Modifier.background(if (i == tab) Px.Panel else Px.PanelDark).clickable { tab = i }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    PT(n, 13.sp, if (i == tab) Px.Text else Px.Sub)
                }
            }
            Spacer(Modifier.weight(1f))
            if (d.optInt("pending") > 0) PT("Bekleyen İşlem: ${d.optInt("pending")}", 12.sp, Px.Orange)
        }
        Column(Modifier.fillMaxWidth().height(320.dp).pxPanel(Px.PanelDark).padding(10.dp).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> AdminSummary(target, d, sec, res)
                1 -> {
                    val saves = d.optJSONArray("saves") ?: JSONArray()
                    if (saves.length() == 0) PT("Kayıt Yok", 13.sp, Px.Sub)
                    for (k in 0 until saves.length()) {
                        val s = saves.getJSONObject(k)
                        PT("Sunucu ${s.optString("server")} · Sv ${s.optInt("level")} · ${s.optString("gold")} Altın · ${s.optString("gems")} Elmas · ${s.optString("updated").take(16)}",
                            13.sp, Px.Clan)
                        PT("Eşyaya dokun: kaç adet silineceğini seç ve onayla.", 11.sp, Px.Sub)
                        Spacer(Modifier.height(4.dp))
                        // aynı eşyanın yığınları toplanır
                        val slots = s.optJSONArray("slots") ?: JSONArray()
                        val totals = LinkedHashMap<Int, Int>()
                        for (i in 0 until slots.length()) slots.optJSONObject(i)?.let { totals[it.optInt("item")] = (totals[it.optInt("item")] ?: 0) + it.optInt("count") }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            for ((idx, n) in totals) {
                                Column(Modifier.width(72.dp).clickable { removing = idx to n }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    PxSlot(JSONObject().put("id", itemId(idx)).put("count", n).put("rarity", items.optJSONArray(idx)?.optInt(3) ?: 0), 52.dp)
                                    PT(itemName(idx), 9.sp, Px.Sub, maxLines = 2)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        var ask by remember { mutableStateOf(false) }
                        PxButton(if (ask) "Emin Misin? Çanta Tamamen Boşalır" else "Çantayı Boşalt", color = Px.Red, height = 36.dp, size = 12.sp) {
                            if (!ask) ask = true else { ask = false; Account.adminAction(target, JSONObject().put("t", "clear_bag"), res) }
                        }
                    }
                }
                2 -> AdminSecurity(sec, res)
                3 -> LogList(d.optJSONArray("activity")) { o -> "[${ts(o.optLong("ts"))}] ${o.optString("t")}" }
                4 -> LogList(d.optJSONArray("chat")) { o -> "[${ts(o.optLong("ts"))}] (${o.optString("ch")}) ${o.optString("t")}" }
                5 -> LogList(d.optJSONArray("dm")) { o -> "[${ts(o.optLong("ts"))}] ${if (o.optBoolean("out")) "→" else "←"} ${o.optString("to")}: ${o.optString("t")}" }
                else -> AdminAccountTab(target, d, res)
            }
        }
    }
    removing?.let { (idx, have) ->
        RemoveItemDialog(itemName(idx), itemId(idx), have, close = { removing = null }) { n ->
            removing = null
            Account.adminAction(target, JSONObject().put("t", "remove_item").put("item", idx).put("n", n), res)
        }
    }
}

/** Adetli eşya silme: kaydırıcı + hızlı tuşlar + onay. */
@Composable
private fun RemoveItemDialog(name: String, id: String, have: Int, close: () -> Unit, confirm: (Int) -> Unit) {
    var n by remember { mutableIntStateOf(1) }
    var ask by remember { mutableStateOf(false) }
    PxDialog("Eşya Sil", close, width = 460.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PxSlot(JSONObject().put("id", id).put("count", have), 56.dp)
            Spacer(Modifier.width(10.dp))
            Column { PT(name, 17.sp, Px.Name); PT("Oyuncuda: $have Adet", 13.sp, Px.Sub) }
        }
        Spacer(Modifier.height(10.dp))
        AmountPicker(n, have) { n = it; ask = false }
        Spacer(Modifier.height(10.dp))
        PxButton(if (ask) "Emin Misiniz? $n Adet $name Silinecek" else "$n Adet Sil", Modifier.fillMaxWidth(), Px.Red, height = 44.dp, size = 15.sp) {
            if (!ask) ask = true else confirm(n)
        }
    }
}

/** Adet seçici: −/+ , kaydırıcı ve 1 / Yarısı / Tümü. */
@Composable
fun AmountPicker(value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("−", color = Px.Dark, height = 38.dp, size = 18.sp) { onChange((value - 1).coerceAtLeast(1)) }
        PT("$value", 20.sp, Px.Name, Modifier.width(70.dp))
        PxButton("+", color = Px.Dark, height = 38.dp, size = 18.sp) { onChange((value + 1).coerceAtMost(max)) }
        Spacer(Modifier.width(6.dp))
        PxButton("1", color = Px.Dark, height = 34.dp, size = 13.sp) { onChange(1) }
        PxButton("Yarısı", color = Px.Dark, height = 34.dp, size = 13.sp) { onChange((max / 2).coerceAtLeast(1)) }
        PxButton("Tümü", color = Px.Dark, height = 34.dp, size = 13.sp) { onChange(max) }
    }
    if (max > 1) androidx.compose.material3.Slider(value.toFloat(), { onChange(it.toInt().coerceIn(1, max)) }, valueRange = 1f..max.toFloat())
}

/** Özet: yasak (süre + neden), mesaj, oyundan at, altın/elmas ayarla, yanına ışınlan. */
@Composable
private fun AdminSummary(target: Long, d: JSONObject, sec: JSONObject?, res: (Result<String>) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableIntStateOf(1) } // 0 saat, 1 gün
    var msg by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("") }
    var gems by remember { mutableStateOf("") }
    var lvl by remember { mutableStateOf("") }
    var clvl by remember { mutableStateOf("") }
    val st = d.optJSONObject("stats") ?: JSONObject()
    PT("Hesap Açılışı: ${d.optString("created_at").take(10)} · Son Görülme: ${d.optString("last_seen").take(16).replace('T', ' ')}", 12.sp, Px.Sub)
    if (st.length() > 0) PT("Oynama Süresi: ${st.optLong("time") / 3600} Saat", 12.sp, Px.Sub)
    Spacer(Modifier.height(8.dp))
    PT("Yasakla", 15.sp, Px.Clan)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(amount, { amount = it.filter(Char::isDigit).take(5) }, "Süre", Modifier.width(90.dp))
        PxButton("Saat", color = if (unit == 0) Px.Blue else Px.Dark, height = 36.dp, size = 12.sp) { unit = 0 }
        PxButton("Gün", color = if (unit == 1) Px.Blue else Px.Dark, height = 36.dp, size = 12.sp) { unit = 1 }
        Field(reason, { reason = it.take(120) }, "Neden (Oyuncu Görür)", Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val h = (amount.toIntOrNull() ?: 0) * (if (unit == 0) 1 else 24)
        PxButton("Yasakla", color = Px.Red, height = 38.dp, size = 13.sp, enabled = h > 0) { Account.adminBan(target, h, reason, res) }
        PxButton("Kalıcı Yasakla", color = Px.Red, height = 38.dp, size = 13.sp) { Account.adminBan(target, 87600, reason, res) }
        if (!d.isNull("banned_until")) PxButton("Yasağı Kaldır", color = Px.Green, height = 38.dp, size = 13.sp) { Account.adminBan(target, 0, "", res) }
    }
    Spacer(Modifier.height(10.dp))
    PT("Mesaj / Oyundan At", 15.sp, Px.Clan)
    Field(msg, { msg = it.take(200) }, "Mesaj (Oyuncunun Ekranına Düşer)", Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Mesaj Gönder", color = Px.Blue, height = 36.dp, size = 13.sp, enabled = msg.isNotBlank()) { Account.adminAction(target, JSONObject().put("t", "msg").put("msg", msg), res) }
        PxButton("Oyundan At", color = Px.Red, height = 36.dp, size = 13.sp) { Account.adminAction(target, JSONObject().put("t", "kick").put("msg", msg), res) }
        PxButton("Fotoğrafı Kaldır", color = Px.Dark, height = 36.dp, size = 13.sp, icon = "trash") { Account.adminAction(target, JSONObject().put("t", "clear_photo"), res) }
        // yanına ışınlan: oyuncu aynı sunucuda çevrimiçiyse anlık konumu
        PxButton("Yanına Işınlan", color = Px.Green, height = 36.dp, size = 13.sp, icon = "map") {
            val pos = Online.position("a$target")
            when {
                pos != null -> {
                    val r = GameSession.cmd("cheat", "text" to "tp ${(pos[0] / 64f).toInt()} ${(pos[1] / 64f).toInt()}")
                    Toaster.show(if (r.ok) "${d.optString("name")} Yanına Işınlandın" else r.msg, r.ok)
                }
                sec?.optBoolean("online") == true -> Toaster.show("Oyuncu ${sec.optString("server")} sunucusunda; o sunucuya geç", false)
                else -> Toaster.show("Oyuncu şu an çevrimiçi değil", false)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PT("Altın / Elmas", 15.sp, Px.Clan)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(gold, { gold = it.filter(Char::isDigit).take(9) }, "Altın", Modifier.width(130.dp))
        PxButton("Altını Ayarla", color = Px.Dark, height = 38.dp, size = 12.sp, enabled = gold.isNotEmpty()) {
            Account.adminAction(target, JSONObject().put("t", "set_gold").put("v", gold.toLongOrNull() ?: 0), res)
        }
        Field(gems, { gems = it.filter(Char::isDigit).take(7) }, "Elmas", Modifier.width(110.dp))
        PxButton("Elması Ayarla", color = Px.Dark, height = 38.dp, size = 12.sp, enabled = gems.isNotEmpty()) {
            Account.adminAction(target, JSONObject().put("t", "set_gems").put("v", gems.toLongOrNull() ?: 0), res)
        }
    }
    Spacer(Modifier.height(6.dp))
    // V20: oyuncu seviyesi (1-50) ve şu an seçili karakterin seviyesi; oyuncu çevrimdışıysa ilk girişte uygulanır
    PT("Seviye", 15.sp, Px.Clan)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(lvl, { lvl = it.filter(Char::isDigit).take(2) }, "Oyuncu Sv", Modifier.width(110.dp))
        PxButton("Seviyeyi Ayarla", color = Px.Dark, height = 38.dp, size = 12.sp, enabled = lvl.isNotEmpty()) {
            Account.adminAction(target, JSONObject().put("t", "set_level").put("v", lvl.toIntOrNull() ?: 1), res)
        }
        Field(clvl, { clvl = it.filter(Char::isDigit).take(2) }, "Karakter Sv", Modifier.width(120.dp))
        PxButton("Karakter Seviyesi", color = Px.Dark, height = 38.dp, size = 12.sp, enabled = clvl.isNotEmpty()) {
            Account.adminAction(target, JSONObject().put("t", "set_char_level").put("v", clvl.toIntOrNull() ?: 1), res)
        }
    }
    Spacer(Modifier.height(6.dp))
    PT("Kanat (yalnız yönetici takar)", 15.sp, Px.Clan)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((id, name) in listOf("wings_ash" to "Kor", "wings_dragon" to "Ejder", "wings_titan" to "Titan", "wings_s1" to "Sezon 1"))
            PxButton(name, color = Color(0xFF3A4A8A), height = 38.dp, size = 12.sp) {
                Account.adminAction(target, JSONObject().put("t", "set_wings").put("id", id), res)
            }
        PxButton("Kanadı Çıkar", color = Px.Dark, height = 38.dp, size = 12.sp) { Account.adminAction(target, JSONObject().put("t", "set_wings").put("id", ""), res) }
    }
}

/** Güvenlik: hile kayıtları, cihazlar (aynı cihazdaki diğer hesap sayısı), cihaz yasağı. */
@Composable
private fun AdminSecurity(sec: JSONObject?, res: (Result<String>) -> Unit) {
    if (sec == null) { PT("Yükleniyor…", 13.sp, Px.Sub); return }
    PT("Cihazlar", 15.sp, Px.Clan)
    val devs = sec.optJSONArray("devices") ?: JSONArray()
    if (devs.length() == 0) PT("Kayıt Yok", 13.sp, Px.Sub)
    for (i in 0 until devs.length()) {
        val o = devs.getJSONObject(i)
        val info = o.optJSONObject("info") ?: JSONObject()
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PT("${o.optString("model")} · Android ${info.optInt("sdk")} · ${ts(o.optLong("last"))}", 13.sp, Px.Text)
                val warn = listOfNotNull("Root".takeIf { info.optBoolean("root") }, "Emülatör".takeIf { info.optBoolean("emu") },
                    "Kanca".takeIf { info.optBoolean("hook") }, "İmza".takeIf { !info.optBoolean("sig", true) })
                PT("Kimlik ${o.optString("id").take(10)}… · Aynı Cihazda ${o.optInt("others")} Başka Hesap" +
                    (if (warn.isNotEmpty()) " · ⚠ " + warn.joinToString(", ") else ""), 11.sp, if (warn.isEmpty()) Px.Sub else Px.Orange, maxLines = 2)
            }
            val banned = o.optBoolean("banned")
            PxButton(if (banned) "Cihaz Yasağını Kaldır" else "Cihazı Yasakla", color = if (banned) Px.Green else Px.Red, height = 34.dp, size = 11.sp) {
                Account.adminBanDevice(o.optString("id"), !banned, "Yönetici kararı", res)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PT("Hile Şüphesi Kayıtları", 15.sp, Px.Clan)
    LogList(sec.optJSONArray("flags")) { o ->
        val k = when (o.optString("k")) { "save" -> "Kayıt Reddi"; "sig" -> "İmza"; "speed" -> "Hız"; "trade" -> "Takas"; "device" -> "Cihaz"; "client" -> "Uygulama"; else -> o.optString("k") }
        "[${ts(o.optLong("ts"))}] $k: ${o.optString("t")}"
    }
}

@Composable
private fun LogList(a: JSONArray?, line: (JSONObject) -> String) {
    if (a == null || a.length() == 0) { PT("Kayıt Yok", 13.sp, Px.Sub); return }
    for (i in 0 until a.length()) PT(line(a.getJSONObject(i)), 12.sp, Px.Text, maxLines = 3)
}

/** Hesap işlemleri: silme (onaylı, geri alınabilir), geri getirme, anahtar sıfırlama (yeni anahtar kopyalanır). */
@Composable
private fun AdminAccountTab(target: Long, d: JSONObject, res: (Result<String>) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var reason by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    if (d.optBoolean("admin")) { PT("Yönetici Hesabı: Silinemez Ve Anahtarı Buradan Değişmez.", 14.sp, Px.Sub); return }
    if (d.optBoolean("deleted")) {
        PT("Bu Hesap Silinmiş. Oyuncu Giriş Yapamaz; Kayıtları Duruyor.", 14.sp, Px.Sub, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        PxButton("Hesabı Geri Getir", color = Px.Green, height = 40.dp, size = 14.sp) { Account.adminRestore(target, res) }
        return
    }
    PT("Hesabı Sil", 15.sp, Px.Clan)
    PT("Oyuncu bir daha giriş yapamaz, klandan çıkar ve Google bağı kalkar. Kayıtlar silinmez; gerekirse geri getirilebilir.", 12.sp, Px.Sub, maxLines = 3)
    Spacer(Modifier.height(6.dp))
    Field(reason, { reason = it.take(120) }, "Silme Nedeni", Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    PxButton(if (confirm) "Emin Misin? Tekrar Bas" else "Hesabı Sil", color = Px.Red, height = 40.dp, size = 14.sp) {
        if (!confirm) confirm = true else { confirm = false; Account.adminDelete(target, reason, res) }
    }
    Spacer(Modifier.height(14.dp))
    PT("Anahtarı Sıfırla", 15.sp, Px.Clan)
    PT("Anahtarını kaybeden oyuncuya yeni anahtar üretir; eski anahtar hemen geçersiz olur.", 12.sp, Px.Sub, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PxButton("Yeni Anahtar Üret", color = Px.Blue, height = 40.dp, size = 14.sp) {
            Account.adminResetKey(target) { r -> r.onSuccess { newKey = it }.onFailure { Toaster.show(it.message ?: "", false) } }
        }
        if (newKey.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            PT(newKey, 18.sp, Px.Name)
            Spacer(Modifier.width(8.dp))
            PxButton("Kopyala", color = Px.Dark, height = 34.dp, size = 12.sp) {
                copy(ctx, "Zenvex ID: ${fmtId(target)}  Anahtar: $newKey"); Toaster.show("Kopyalandı")
            }
        }
    }
}

/** Silme talepleri listesi (Ayarlar → Hesabımı Sil ile gelenler); dokununca oyuncu dosyası açılır. */
@Composable
fun AdminDeleteRequests(open: (Long) -> Unit) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(Unit) { Account.adminDeleteRequests { r -> list = r.getOrNull() ?: JSONArray() } }
    val l = list ?: run { PT("Yükleniyor…", 13.sp, Px.Sub); return }
    if (l.length() == 0) { PT("Silme Talebi Yok.", 13.sp, Px.Sub); return }
    for (i in 0 until l.length()) {
        val o = l.getJSONObject(i)
        Row(Modifier.fillMaxWidth().clickable { open(o.optLong("id")) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PT("${o.optString("name")} · ${fmtId(o.optLong("id"))} · Sv ${o.optInt("level")}", 14.sp, Px.Text, modifier = Modifier.weight(1f))
            PT(ts(o.optLong("at")), 12.sp, Px.Sub)
        }
    }
}

/**
 * Eşya seçici (posta eki, hile): arama + nadirliğe göre renkli ızgara; seçilen eşya için adet girilir.
 * [pick] (kimlik, adet) ile döner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemPickerDialog(title: String, close: () -> Unit, maxAmount: Int = 9999, pick: (String, Int) -> Unit) {
    val items = remember { GameSession.ui("cheat").optJSONArray("items") ?: allItems() }
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<JSONArray?>(null) }
    var n by remember { mutableStateOf("1") }
    // V19: türe göre süzme (yalnız oyunda olan eşyalar gelir; kalkmış eşyalar listede yok)
    var kind by remember { mutableStateOf("") }
    val kinds = remember(items) { (0 until items.length()).mapNotNull { items.optJSONArray(it)?.optString(2) }.distinct() }
    PxDialog(title, close, width = 760.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (k in listOf("") + kinds) PxButton(ITEM_KIND_TR[k] ?: k, color = if (kind == k) Px.Green else Px.Dark, height = 30.dp, size = 11.sp) { kind = k }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(q, { q = it.take(24) }, "Eşya Ara (Ad)", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            val s = sel
            if (s != null) {
                PxSlot(JSONObject().put("id", s.optString(0)).put("rarity", s.optInt(3)), 44.dp)
                Spacer(Modifier.width(6.dp))
                Field(n, { n = it.filter(Char::isDigit).take(5) }, "Adet", Modifier.width(90.dp))
                Spacer(Modifier.width(6.dp))
                PxButton("Ekle", color = Px.Green, height = 42.dp, size = 14.sp) {
                    pick(s.optString(0), (n.toIntOrNull() ?: 1).coerceIn(1, maxAmount)); close()
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        sel?.let { PT("${it.optString(1)} Seçildi · Adet Yaz Ve Ekle", 13.sp, Px.Name) }
        Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until items.length()) {
                    val a = items.optJSONArray(i) ?: continue
                    if (q.isNotBlank() && !a.optString(1).contains(q.trim(), ignoreCase = true)) continue
                    if (kind.isNotEmpty() && a.optString(2) != kind) continue
                    val chosen = sel?.optString(0) == a.optString(0)
                    Column(Modifier.width(72.dp).clickable { sel = a }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PxSlot(JSONObject().put("id", a.optString(0)).put("rarity", a.optInt(3)), 50.dp, selected = chosen)
                        PT(a.optString(1), 9.sp, if (chosen) Px.Name else Px.Sub, maxLines = 2)
                    }
                }
            }
        }
    }
}

private val ITEM_KIND_TR = mapOf("" to "Hepsi", "Weapon" to "Silah", "Tool" to "Alet", "Helmet" to "Kask", "Jacket" to "Ceket", "Pants" to "Pantolon",
    "Boots" to "Bot", "Bag" to "Çanta", "Ring" to "Yüzük", "Necklace" to "Kolye", "Consumable" to "Tüketim", "Material" to "Malzeme",
    "Chest" to "Sandık", "Bench" to "Tezgâh", "Vehicle" to "Araç", "Part" to "Parça")
