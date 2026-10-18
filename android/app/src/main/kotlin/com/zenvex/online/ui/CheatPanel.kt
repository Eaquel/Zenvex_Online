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
                listOf("Oyuncu", "Eşya", "Dünya", "Zombi", "Araç/Işınlan", "Konsol").forEachIndexed { i, n ->
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
                            ),
                            ::run, highlight = setOf("god".takeIf { st.optBoolean("god") }, "onehit".takeIf { st.optBoolean("onehit") }),
                        )
                        Section("Hız (şu an ×${"%.1f".format(st.optDouble("speed", 1.0))})")
                        Buttons(listOf("×1" to "speed 1", "×1.5" to "speed 1.5", "×2" to "speed 2", "×3" to "speed 3"), ::run)
                        Section("Seviye ve para")
                        Buttons(
                            listOf(
                                "+10.000 altın" to "gold 10000", "+100.000 altın" to "gold 100000", "+1.000 elmas" to "gem 1000",
                                "Seviye 10" to "level 10", "Seviye 30" to "level 30", "Seviye 50" to "level 50", "+5.000 XP" to "xp 5000",
                                "+10 yetenek puanı" to "points 10", "Karakter Sv 10" to "charlevel 10",
                            ), ::run,
                        )
                        Section("Kilitler")
                        Buttons(listOf("Tüm karakter/kozmetik aç" to "unlock", "Sezon kademesi 30" to "pass 30", "Pass al" to "pass buy", "Elit pass al" to "pass buy elite"), ::run)
                    }
                    1 -> {
                        Section("Hazır paketler")
                        Buttons(
                            listOf(
                                "Tüm silahlar" to "weapons", "Mermi ×200" to "ammo", "Tüm malzemeler ×20" to "items",
                                "Temel set" to "set basic", "Polis seti" to "set police", "Asker seti" to "set military", "Titan seti" to "set titan",
                                "İlk yardım ×10" to "item medkit 10", "Bandaj ×20" to "item bandage 20", "Çantayı boşalt" to "clearbag",
                            ), ::run,
                        )
                        Section("Tek tek eşya (dokun: 1 adet, uzun liste kaydırılır)")
                        ItemGrid(st.optJSONArray("items") ?: JSONArray()) { id -> run("item $id 1") }
                    }
                    2 -> {
                        Section("Zaman")
                        Buttons(listOf("Gündüz" to "day", "Gece" to "night", "Saat 06" to "time 6", "Saat 12" to "time 12", "Saat 18" to "time 18", "Saat 22" to "time 22"), ::run)
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

@Composable
private fun Console(run: (String) -> Unit) {
    var cmd by remember { mutableStateOf("") }
    PT("Komut yaz (örn: item w_m4 1, spawn tank 3 40, tp 120 300, speed 2.5):", 13.sp, Px.Sub, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(cmd, { cmd = it }, "komut", Modifier.weight(1f)) { run(cmd); cmd = "" }
        Spacer(Modifier.width(8.dp))
        PxButton("Çalıştır", height = 44.dp) { run(cmd); cmd = "" }
    }
    Spacer(Modifier.height(8.dp))
    PT(
        "gold N · gem N · item id N · items · weapons · ammo · set basic|police|military|titan · level N · xp N · charlevel N · " +
            "points N · god · onehit · speed X · heal · time H · day · night · spawn tür N seviye · boss · killall · " +
            "vehicle bike|moto|car|suv · fuel · tp camp|city|boss|swamp|downtown|deep|x y · unlock · pass N · pass buy [elite] · clearbag · reset",
        12.sp, Px.Sub, maxLines = 12,
    )
}
