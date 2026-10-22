package com.zenvex.online.ui

import androidx.compose.foundation.layout.Arrangement
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
        PT("Parçaları tezgâhta üret, bana getir. Aracı hazırlarım, anahtarını postana yollarım.", 13.sp, Px.Sub, maxLines = 2)
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
        for (o in list) {
            val left = (o.optLong("ready") - now).coerceAtLeast(0L)
            PT("${o.optString("name")}: ${if (left > 0) "%d:%02d".format(left / 60000, left / 1000 % 60) + " kaldı" else "Hazır, postana bak"}", 13.sp, Px.Text)
        }
    }
}
