package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import com.zenvex.online.game.GameSession
import org.json.JSONArray

/**
 * Hile paneli (geliştirici): oyunda menüden ya da HUD'daki </> tuşundan, lobide sürüm yazısına 7 dokunuşla açılır.
 * Her düğme Rust'taki `cheat` komutunu çalıştırır (rust/zenvex_core/src/ui.rs); hepsi birim testle denenir.
 */
@Composable
fun DevMenu(onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("Bir düğmeye bas ya da Konsol'a komut yaz.") }
    var ok by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val st = remember(tick) { GameSession.ui("cheat") }
    fun run(c: String) {
        val r = GameSession.cmd("cheat", "text" to c)
        ok = r.ok
        log = r.msg.ifEmpty { if (r.ok) "Tamam" else "Hata" }
        tick++
    }
    Dim(onClose) {
        Column(Modifier.width(680.dp).fillMaxHeight(0.92f).pxPanel(Color(0xF5201F1C)).clickable(remember { MutableInteractionSource() }, null) {}.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("dev", 26.dp, Px.Orange)
                Spacer(Modifier.width(8.dp))
                PT("Hile Paneli", 24.sp, Px.Orange)
                Spacer(Modifier.width(12.dp))
                PT("Sv ${st.optInt("level")} · ${fmt(st.optInt("gold"))} altın · X ${st.optInt("x")} Y ${st.optInt("y")}", 13.sp, Px.Sub)
                Spacer(Modifier.weight(1f))
                PxIcon("close", size = 38.dp) { onClose() }
            }
            if (st.optInt("net") == 2) PT("Çevrimiçi katılımcısın: zombi hileleri yalnız ev sahibinde çalışır.", 12.sp, Color(0xFFFFB070), maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Oyuncu", "Eşya", "Dünya", "Zombi", "Araç/Işın", "Yönetici", "Konsol").forEachIndexed { i, n ->
                    PxButton(n, Modifier.weight(1f), if (tab == i) Px.Green else Px.Dark, size = 13.sp, height = 36.dp) { tab = i }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (tab) {
                    0 -> {
                        Section("Durum")
                        Buttons(
                            listOf(
                                (if (st.optBoolean("god")) "Ölümsüzlük: AÇIK" else "Ölümsüzlük: kapalı") to "god",
                                (if (st.optBoolean("onehit")) "Tek vuruş: AÇIK" else "Tek vuruş: kapalı") to "onehit",
                                "Can + enerji doldur" to "heal",
                                (if (st.optBoolean("nocd")) "Beklemesiz takla/iksir: AÇIK" else "Beklemesiz takla/iksir: kapalı") to "nocd",
                                (if (st.optBoolean("invis")) "Zombilere görünmez: AÇIK" else "Zombilere görünmez: kapalı") to "invis",
                            ),
                            ::run, highlight = setOf("god".takeIf { st.optBoolean("god") }, "onehit".takeIf { st.optBoolean("onehit") },
                                "nocd".takeIf { st.optBoolean("nocd") }, "invis".takeIf { st.optBoolean("invis") }),
                        )
                        Section("Hız (şu an ×${"%.1f".format(st.optDouble("speed", 1.0))})")
                        Buttons(listOf("×1" to "speed 1", "×1.5" to "speed 1.5", "×2" to "speed 2", "×3" to "speed 3"), ::run)
                        Section("Seviye ve para")
                        Buttons(
                            listOf(
                                "+10.000 Altın" to "gold 10000", "+1.000 Elmas" to "gem 1000",
                                "Seviye 5" to "level 5", "Seviye 10" to "level 10", "Seviye 30" to "level 30", "+5.000 XP" to "xp 5000",
                                "+10 Skill Puanı" to "points 10", "Savaşçı Sv 10" to "charlevel 10", "Köpek Sv 5" to "dog 5", "Köpek Sv 10" to "dog 10",
                            ), ::run,
                        )
                    }
                    1 -> {
                        Section("Hazır paketler")
                        Buttons(
                            listOf(
                                "Bıçak + Katana" to "weapons", "Tüm Malzemeler ×20" to "items",
                                "İksirler ×5" to "potions", "İlk Yardım ×10" to "item medkit 10", "Bandaj ×20" to "item bandage 20", "Çantayı Boşalt" to "clearbag",
                            ), ::run,
                        )
                        Section("Tek tek eşya (dokun: 1 adet, uzun liste kaydırılır)")
                        ItemGrid(st.optJSONArray("items") ?: JSONArray()) { id -> run("item $id 1") }
                    }
                    2 -> {
                        Section("Zaman")
                        Buttons(listOf("Gündüz" to "day", "Gece" to "night", "Saat 06" to "time 6", "Saat 12" to "time 12", "Saat 18" to "time 18", "Saat 22" to "time 22"), ::run)
                        Section("Zaman hızı (şu an ×${"%.2f".format(st.optDouble("timescale", 1.0))})")
                        Buttons(listOf("×0.25" to "timescale 0.25", "×0.5" to "timescale 0.5", "×1" to "timescale 1", "×2" to "timescale 2", "×4" to "timescale 4"), ::run)
                    }
                    3 -> {
                        Section("Yanına zombi çağır")
                        Buttons(
                            listOf(
                                "Normal ×5" to "spawn walker 5", "Koşucu ×3" to "spawn runner 3", "Köpek ×3" to "spawn dog 3",
                                "Toksik ×3" to "spawn toxic 3", "Karanlık ×3" to "spawn dark 3", "Tank ×1" to "spawn tank 1",
                                "Gizemli ×1" to "spawn mystery 1", "TİTAN (boss)" to "boss",
                            ), ::run,
                        )
                        Section("Temizle")
                        Buttons(listOf("Tüm zombileri sil" to "killall"), ::run)
                    }
                    4 -> {
                        Section("Araç getir")
                        Buttons(listOf("Bisiklet" to "vehicle bike", "Motor" to "vehicle moto", "Araba (2 kişi)" to "vehicle car", "SUV (4 kişi)" to "vehicle suv", "Yakıt + tamir" to "fuel"), ::run)
                        Section("Işınlan")
                        Buttons(
                            listOf(
                                "Kamp" to "tp camp", "Derin Orman" to "tp deep", "Bataklık" to "tp swamp", "Şehir" to "tp city",
                                "Şehir Merkezi" to "tp downtown", "Titan Arenası" to "tp boss",
                            ), ::run,
                        )
                    }
                    5 -> AdminTab { m, good -> ok = good; log = m }
                    else -> Console(::run)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().background(Color(0xFF15140F)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                PT((if (ok) "✔ " else "✖ ") + log, 14.sp, if (ok) Px.Name else Color(0xFFFF7A6A), maxLines = 3)
            }
        }
    }
}

@Composable
private fun Section(t: String) {
    PT(t, 15.sp, Px.Clan, Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(list: List<Pair<String, String>>, run: (String) -> Unit, highlight: Set<String?> = emptySet()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, cmd) in list) PxButton(label, color = if (cmd in highlight) Px.Green else Px.Dark, size = 14.sp, height = 38.dp) { run(cmd) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemGrid(items: JSONArray, pick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until items.length()) {
            val a = items.getJSONArray(i)
            val id = a.getString(0)
            Column(
                Modifier.width(74.dp).clip(RoundedCornerShape(4.dp)).background(Px.Row)
                    .border(1.dp, Pal.rarity(a.optInt(3)).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable { pick(id) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpriteImage("i/$id", Modifier.size(40.dp))
                PT(a.getString(1), 10.sp, maxLines = 2)
            }
        }
    }
}

/** Yönetici: bakım modu, oyuncu bul → yasakla / VIP, posta (altın, elmas, XP, eşya). */
@Composable
private fun AdminTab(report: (String, Boolean) -> Unit) {
    val A = com.zenvex.online.game.Account
    if (!A.loggedIn) { PT("Önce Hesabınla Giriş Yap.", 14.sp, Px.Sub); return }
    if (!A.admin.value) { PT("Bu Hesapta Yönetici Yetkisi Yok.", 14.sp, Px.Sub); return }
    val res: (Result<String>) -> Unit = { r -> r.onSuccess { report(it, true) }.onFailure { report(it.message ?: "Hata", false) } }
    // ---- bakım
    Section("Bakım Modu (Yöneticiler Dışında Herkes Bakım Ekranında Kalır)")
    var mMsg by remember { mutableStateOf("") }
    val on = Gate.maintenance.value != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(mMsg, { mMsg = it.take(200) }, "Bakım Mesajı", Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        PxButton(if (on) "Bakımı Bitir" else "Bakımı Başlat", color = if (on) Px.Green else Px.Red, height = 44.dp, size = 14.sp) {
            A.adminMaintenance(!on, mMsg) { r -> r.onSuccess { Gate.poll(); report(if (!on) "Bakım Başladı" else "Bakım Bitti", true) }.onFailure { report(it.message ?: "", false) } }
        }
    }
    // ---- oyuncu bul
    Section("Oyuncu Bul (Ad ya da ID)")
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(JSONArray()) }
    var file by remember { mutableStateOf(0L) }
    if (file > 0) AdminPlayerFile(file) { file = 0 }
    var reason by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(q, { q = it.take(20) }, "Oyuncu", Modifier.weight(1f)) { A.adminFind(q) { r -> r.onSuccess { found = it }.onFailure { report(it.message ?: "", false) } } }
        Spacer(Modifier.width(6.dp))
        Field(reason, { reason = it.take(120) }, "Yasak Nedeni", Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        PxButton("Ara", height = 44.dp, size = 14.sp) { A.adminFind(q) { r -> r.onSuccess { found = it }.onFailure { report(it.message ?: "", false) } } }
    }
    for (i in 0 until found.length()) {
        val o = found.getJSONObject(i)
        val id = o.optLong("id")
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Px.Row).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT("${o.optString("name")} · ${fmtId(id)} · Sv ${o.optInt("level")} · ${o.optInt("kills")} Öldürme", 14.sp, modifier = Modifier.weight(1f))
                if (!o.isNull("banned_until")) PT("YASAKLI", 12.sp, Color(0xFFFF6A5A))
                if (!o.isNull("vip_until")) PT(" VIP", 12.sp, Px.Clan)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for ((l, h) in listOf("1 Sa" to 1, "1 Gün" to 24, "7 Gün" to 168, "Kalıcı" to 87600)) PxButton("Yasakla $l", color = Px.Red, height = 32.dp, size = 11.sp) { A.adminBan(id, h, reason, res) }
                PxButton("Yasağı Kaldır", color = Px.Dark, height = 32.dp, size = 11.sp) { A.adminBan(id, 0, "", res) }
                PxButton("VIP +30 Gün", color = Color(0xFFB08A2A), height = 32.dp, size = 11.sp) { A.adminVip(id, 30, res) }
                PxButton("VIP Kaldır", color = Px.Dark, height = 32.dp, size = 11.sp) { A.adminVip(id, 0, res) }
                PxButton("Dosya", color = Px.Blue, height = 32.dp, size = 11.sp, icon = "user") { file = id }
            }
        }
    }
    // ---- posta
    Section("Posta (Ödül / Etkinlik Duyurusu)")
    var to by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("Hediye") }
    var body by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("0") }
    var gems by remember { mutableStateOf("0") }
    var xp by remember { mutableStateOf("0") }
    var item by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var event by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(to, { to = it.take(16) }, "Oyuncu Adı / ID / *", Modifier.weight(1f))
        Field(title, { title = it.take(40) }, "Konu", Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Field(body, { body = it.take(500) }, "Mesaj", Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(gold, { gold = it.filter(Char::isDigit).take(7) }, "Altın", Modifier.weight(1f))
        Field(gems, { gems = it.filter(Char::isDigit).take(6) }, "Elmas", Modifier.weight(1f))
        Field(xp, { xp = it.filter(Char::isDigit).take(7) }, "XP", Modifier.weight(1f))
        Field(item, { item = it.trim().take(24) }, "Eşya (Örn medkit)", Modifier.weight(1.5f))
        Field(count, { count = it.filter(Char::isDigit).take(4) }, "Adet", Modifier.weight(0.6f))
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable { event = !event }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(if (event) Px.Green else Px.Dark)); PT("  Etkinlik Duyurusu", 13.sp)
        }
        Spacer(Modifier.weight(1f))
        PxButton(if (busy) "Gönderiliyor…" else "Gönder", color = Px.Blue, icon = "mail", enabled = !busy && to.isNotBlank(), height = 42.dp) {
            val items = JSONArray()
            if (item.isNotBlank()) items.put(org.json.JSONObject().put("id", item).put("n", count.toIntOrNull() ?: 1))
            busy = true
            A.adminMail2(to, title.ifBlank { "Hediye" }, body, gold.toIntOrNull() ?: 0, gems.toIntOrNull() ?: 0, xp.toIntOrNull() ?: 0, items,
                if (event) "event" else "reward") { r ->
                busy = false
                r.onSuccess { report("$it Oyuncuya Posta Gönderildi", true) }.onFailure { report(it.message ?: "Hata", false) }
            }
        }
    }
}

@Composable
private fun Console(run: (String) -> Unit) {
    var cmd by remember { mutableStateOf("") }
    PT("Komut Yaz (Örn: item w_katana 1, spawn tank 3 40, tp 120 300, speed 2.5):", 13.sp, Px.Sub, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(cmd, { cmd = it }, "komut", Modifier.weight(1f)) { run(cmd); cmd = "" }
        Spacer(Modifier.width(8.dp))
        PxButton("Çalıştır", height = 44.dp) { run(cmd); cmd = "" }
    }
    Spacer(Modifier.height(8.dp))
    PT(
        "gold N · gem N · item id N · items · weapons · potions · level N · xp N · charlevel N · points N · dog N · " +
            "god · onehit · nocd · invis · speed X · heal · time H · day · night · timescale X · spawn tür N seviye · boss · killall · " +
            "vehicle bike|moto|car|suv · fuel · tp camp|city|boss|swamp|downtown|deep|x y · clearbag · reset",
        12.sp, Px.Sub, maxLines = 12,
    )
}
