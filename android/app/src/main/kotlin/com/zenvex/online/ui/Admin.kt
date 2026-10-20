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
import androidx.compose.foundation.layout.width
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tsFmt = SimpleDateFormat("dd.MM HH:mm", Locale("tr"))
private fun ts(ms: Long) = if (ms <= 0) "" else tsFmt.format(Date(ms))

/**
 * Yönetici: oyuncu dosyası. Özet, envanter (eşya silme), son işlemler, tüm sohbetler, özel mesajlar;
 * uzaktan işlemler (oyundan at, mesaj, altın ayarla, çantayı boşalt).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPlayerFile(target: Long, close: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { Account.adminPlayer(target) { r -> r.onSuccess { data = it }.onFailure { err = it.message ?: "Hata" } } }
    val res: (Result<String>) -> Unit = { r -> r.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) }; tick++ }
    // eşya dizini → (kimlik, ad)
    val items = remember { GameSession.world().optJSONArray("items") ?: JSONArray() }
    fun itemName(i: Int) = items.optJSONArray(i)?.optString(1) ?: "#$i"
    fun itemId(i: Int) = items.optJSONArray(i)?.optString(0) ?: ""
    PxDialog("Oyuncu Dosyası", close, width = 900.dp) {
        val d = data
        if (d == null) { PT(err.ifEmpty { "Yükleniyor…" }, 14.sp, Px.Sub); return@PxDialog }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(d.optInt("avatar"), d.optString("frame"), 56.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PT("${d.optString("name")} · ID ${fmtId(d.optLong("id"))}", 18.sp, Px.Name)
                PT("Sv ${d.optInt("level")} · ${fmt(d.optInt("kills"))} Öldürme · Klan: ${d.optJSONObject("clan")?.optString("name") ?: "—"}" +
                    (if (d.optBoolean("google")) " · Google" else ""), 12.sp, Px.Sub)
                if (!d.isNull("banned_until")) PT("YASAKLI: ${d.optString("banned_until").take(16)} · ${d.optString("ban_reason")}", 12.sp, Color(0xFFFF6A5A))
            }
            TagRow(d.optJSONArray("tags"))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Envanter", "Son İşlemler", "Sohbetler", "Özel Mesajlar", "Uzaktan İşlem").forEachIndexed { i, n ->
                Box(Modifier.background(if (i == tab) Px.Panel else Px.PanelDark).clickable { tab = i }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    PT(n, 13.sp, if (i == tab) Px.Text else Px.Sub)
                }
            }
            Spacer(Modifier.weight(1f))
            if (d.optInt("pending") > 0) PT("Bekleyen İşlem: ${d.optInt("pending")}", 12.sp, Px.Orange)
        }
        Column(Modifier.fillMaxWidth().height(300.dp).pxPanel(Px.PanelDark).padding(8.dp).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> {
                    val saves = d.optJSONArray("saves") ?: JSONArray()
                    for (k in 0 until saves.length()) {
                        val s = saves.getJSONObject(k)
                        PT("Sunucu ${s.optString("server")} · Sv ${s.optInt("level")} · ${s.optString("gold")} Altın · ${s.optString("gems")} Elmas · ${s.optString("updated").take(16)}",
                            13.sp, Px.Clan)
                        PT("Eşyaya dokun: o eşyanın tamamı silinir (oyuncu çevrimiçiyse 30 sn içinde, değilse bulut kaydından hemen).", 11.sp, Px.Sub, maxLines = 2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val slots = s.optJSONArray("slots") ?: JSONArray()
                            for (i in 0 until slots.length()) {
                                val sl = slots.optJSONObject(i) ?: continue
                                val idx = sl.optInt("item")
                                Column(Modifier.width(70.dp).clickable {
                                    Account.adminAction(target, JSONObject().put("t", "remove_item").put("item", idx), res)
                                }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    PxSlot(JSONObject().put("id", itemId(idx)).put("count", sl.optInt("count")), 50.dp)
                                    PT(itemName(idx), 9.sp, Px.Sub, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                1 -> LogList(d.optJSONArray("activity")) { o -> "[${ts(o.optLong("ts"))}] ${o.optString("t")}" }
                2 -> LogList(d.optJSONArray("chat")) { o -> "[${ts(o.optLong("ts"))}] (${o.optString("ch")}) ${o.optString("t")}" }
                3 -> LogList(d.optJSONArray("dm")) { o -> "[${ts(o.optLong("ts"))}] ${if (o.optBoolean("out")) "→" else "←"} ${o.optString("to")}: ${o.optString("t")}" }
                else -> {
                    var msg by remember { mutableStateOf("") }
                    var gold by remember { mutableStateOf("") }
                    Field(msg, { msg = it.take(200) }, "Mesaj / Atma Nedeni", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PxButton("Mesaj Gönder", color = Px.Blue, height = 38.dp, size = 13.sp) { Account.adminAction(target, JSONObject().put("t", "msg").put("msg", msg), res) }
                        PxButton("Oyundan At", color = Px.Red, height = 38.dp, size = 13.sp) { Account.adminAction(target, JSONObject().put("t", "kick").put("msg", msg), res) }
                        PxButton("Çantayı Boşalt", color = Px.Red, height = 38.dp, size = 13.sp) { Account.adminAction(target, JSONObject().put("t", "clear_bag"), res) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(gold, { gold = it.filter(Char::isDigit).take(8) }, "Yeni Altın Miktarı", Modifier.width(200.dp))
                        Spacer(Modifier.width(6.dp))
                        PxButton("Altını Ayarla", color = Px.Dark, height = 44.dp, size = 13.sp) {
                            Account.adminAction(target, JSONObject().put("t", "set_gold").put("v", gold.toLongOrNull() ?: 0), res)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((l, h) in listOf("1 Gün" to 24, "7 Gün" to 168, "Kalıcı" to 87600)) PxButton("Yasakla $l", color = Px.Red, height = 34.dp, size = 12.sp) {
                            Account.adminBan(target, h, msg, res)
                        }
                        PxButton("Yasağı Kaldır", color = Px.Dark, height = 34.dp, size = 12.sp) { Account.adminBan(target, 0, "", res) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogList(a: JSONArray?, line: (JSONObject) -> String) {
    if (a == null || a.length() == 0) { PT("Kayıt Yok", 13.sp, Px.Sub); return }
    for (i in 0 until a.length()) PT(line(a.getJSONObject(i)), 12.sp, Px.Text, maxLines = 3)
}
