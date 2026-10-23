package com.zenvex.online.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * Tamirci Viktor'un atölyesi: motosiklet ve araba için gereken parçalar (elde / gereken, nereden çıktığı), parçaları ver
 * tuşu ve bekleyen üretimler (kalan süre). Araç hazır olunca anahtarı postayla gelir; anahtar kullanılınca araç yanına gelir.
 */
@Composable
fun GarageScreen(close: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("garage") }
    var orders by remember { mutableStateOf(JSONArray()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { Account.vehicleOrders { r -> r.onSuccess { orders = it } } }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    PxDialog("Tamirci Viktor · Araç Üretimi", close, 820.dp) {
        // atölye manzarası (yarım kalmış cip, lamba ışığı) ve Viktor'un notu
        SceneBackground("bg_garage", null, Modifier.fillMaxWidth().height(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            dim = 0.35f, zoom = 1.0f, ay = 0.62f) {
            PT("Parçaları tezgâhta üret, bana getir. Aracı hazırlarım, anahtarını postana yollarım.", 14.sp, Px.Text, maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (b in data.optJSONArray("builds").objs()) {
                Column(Modifier.weight(1f).fillMaxHeight().pxPanel(Px.PanelDark).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemSlot(b.optString("key"), 0, 3, 46.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            PT(b.optString("name"), 18.sp, Px.Orange)
                            PT("Üretim: ${b.optInt("mins")} Dakika" + if (b.optBoolean("owned")) " · Anahtarın Var" else "", 11.sp, Px.Sub)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (p in b.optJSONArray("parts").objs()) {
                            val ok = p.optInt("have") >= p.optInt("need")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemSlot(p.optString("id"), 0, 0, 34.dp)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    PT(p.optString("name"), 13.sp, Px.Text)
                                    PT(p.optString("src"), 10.sp, Px.Sub)
                                }
                                PT("${p.optInt("have")} / ${p.optInt("need")}", 13.sp, if (ok) Px.Name else Color(0xFFFF6A5A))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val can = b.optBoolean("can") && data.optBoolean("near") && !busy
                    PxButton("Parçaları Ver", Modifier.fillMaxWidth(), color = Px.Green, height = 38.dp, size = 14.sp, enabled = can) {
                        val k = b.optInt("kind")
                        val r = GameSession.cmd("vehicle_order", "kind" to k)
                        if (!r.ok) { Toaster.show(r.msg, false); return@PxButton }
                        busy = true
                        Account.vehicleOrder(k) { res ->
                            busy = false
                            res.onSuccess { Toaster.show(r.msg, true); GameSession.save(cloud = true) }
                                .onFailure { e -> GameSession.cmd("vehicle_refund", "kind" to k); Toaster.show(e.message ?: "Sipariş alınamadı", false) }
                            tick++
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PT("Üretimdekiler", 15.sp, Px.Orange)
        val list = orders.objs()
        if (list.isEmpty()) PT("Şu an üretimde araç yok.", 12.sp, Px.Sub)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in list.take(3)) OrderCard(o, data, now)
        }
    }
}

/** Üretimdeki araç: perçinli sipariş kartı (solda simge penceresi, ortada ad/süre, altta ilerleme çubuğu) */
@Composable
private fun OrderCard(o: org.json.JSONObject, data: org.json.JSONObject, now: Long) {
    val left = (o.optLong("ready") - now).coerceAtLeast(0L)
    val build = data.optJSONArray("builds").objs().firstOrNull { it.optString("name") == o.optString("name") }
    val total = (build?.optInt("mins") ?: 0) * 60000L
    val prog = if (total <= 0L) (if (left > 0) 0.5f else 1f) else (1f - left.toFloat() / total).coerceIn(0f, 1f)
    val w = 260.dp; val h = w / 3.1f
    Box(Modifier.size(w, h)) {
        ArtImage("card_order", Modifier.fillMaxSize())
        Box(Modifier.offset(w * 0.075f, h * 0.24f).size(w * 0.18f, h * 0.55f), contentAlignment = Alignment.Center) {
            ArtImage("tab_vehicle", Modifier.fillMaxSize().padding(3.dp))
            build?.let { ItemSlot(it.optString("key"), 0, 3, w * 0.16f) }
        }
        Column(Modifier.offset(w * 0.335f, h * 0.24f).width(w * 0.6f)) {
            PT(o.optString("name"), 14.sp, Px.Orange, maxLines = 1)
            PT(if (left > 0) "%d:%02d".format(left / 60000, left / 1000 % 60) + " kaldı" else "Hazır, postana bak", 11.sp,
                if (left > 0) Px.Text else Px.Name, maxLines = 1)
        }
        Box(Modifier.offset(w * 0.322f, h * 0.805f).size(w * 0.6f * prog, h * 0.07f).background(if (left > 0) Color(0xFFE0A040) else Color(0xFF7AE07A)))
    }
}
