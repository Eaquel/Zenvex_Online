package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.Pass
import com.zenvex.online.game.Store
import org.json.JSONObject
import kotlin.math.sin

private val GEM = Color(0xFF7FE0FF)
private val GOLD = Color(0xFFFFC83A)
private val VIP = Color(0xFFE6B4FF)

@Composable
fun ShopScreen(close: () -> Unit, startTab: Int = 0) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { Store.refresh() }
    ShopView(Store.state.value, Account.loggedIn, Pass.now(), startTab, close) { tick++ }
}

@Composable
internal fun ShopView(s: JSONObject?, loggedIn: Boolean, now: Long, startTab: Int, close: () -> Unit, refresh: () -> Unit) {
    var tab by remember { mutableIntStateOf(startTab) }
    val t = rememberTime()
    ScreenFrame("MAĞAZA", "crate", close, bg = if (UiArt.has(LocalContext.current, "shop_bg")) "shop_bg" else null) {
        if (!loggedIn) { PT("Mağaza İçin Hesabına Gir", 18.sp, Px.Sub); return@ScreenFrame }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // sol: sekmeler
            Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val offers = s?.optJSONArray("offers")?.length() ?: 0
                listOf(Triple("Öne Çıkan", "star", offers), Triple("Elmas", "gem", 0), Triple("Paketler", "crate", offers),
                    Triple("Kartlar", "calendar", if (s?.optJSONArray("cards")?.objs()?.any { it.optBoolean("claimable") } == true) 1 else 0),
                    Triple("Sezon Bileti", "pass", 0)).forEachIndexed { i, (n, ic, badge) ->
                    val on = i == tab
                    Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (on) Brush.horizontalGradient(listOf(Color(0xFF6A4A16), Color(0xFF2A2012))) else Brush.horizontalGradient(listOf(Px.PanelDark, Px.PanelDark)))
                        .border(1.dp, if (on) GOLD.copy(alpha = 0.7f) else Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                        .clickable { tab = i }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // sekme görselleri docs/25 numarasıyla; VIP sekmesi kalktığı için Sezon Bileti (4) görseli shoptab_5
                        val art = "shoptab_${if (i == 4) 5 else i}"
                        if (UiArt.has(LocalContext.current, art)) ArtImage(art, Modifier.size(26.dp)) else Icon(ic, 22.dp, if (on) GOLD else Px.Sub)
                        Spacer(Modifier.width(6.dp))
                        PT(n, 15.sp, if (on) Color.White else Px.Sub, Modifier.weight(1f))
                        if (badge > 0) Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE04040)
                            .copy(alpha = 0.6f + 0.4f * sin(t * 5f))))
                    }
                }
            }
            // sağ: içerik
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                if (s == null) { PT("Yükleniyor…", 16.sp, Px.Sub); return@Column }
                val products = s.optJSONArray("products").objs().associateBy { it.optString("id") }
                when (tab) {
                    0 -> Featured(s, products, now, t) { tab = it }
                    1 -> GemsTab(products, t)
                    2 -> PacksTab(s, products, now, t)
                    3 -> CardsTab(s, products, now, refresh)
                    else -> PassTab(s, products, t)
                }
            }
        }
    }
}

/** Ürün kartı: parlak kenar, kayan ışık, köşede "İlk alım 2×" kurdelesi, altta fiyat tuşu */
@Composable
private fun ProductCard(modifier: Modifier, accent: Color, t: Float, ribbon: String? = null, price: String, priceEnabled: Boolean = true,
                        onBuy: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color(0xF0121014))))
        .border(1.5.dp, accent.copy(alpha = 0.55f + 0.2f * sin(t * 2f)), RoundedCornerShape(6.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val u = ((t * 0.35f) % 1.6f - 0.3f) * size.width
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                Offset(u - 60f, 0f), Offset(u + 60f, size.height)))
        }
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center, content = content)
            PxButton(price, Modifier.fillMaxWidth(), if (priceEnabled) Px.Green else Px.Dark, size = 16.sp, height = 38.dp,
                enabled = priceEnabled && Store.busy.value == null) { onBuy() }
        }
        if (ribbon != null) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFD02A20), Color(0xFFFF7A2A)))).padding(horizontal = 6.dp, vertical = 2.dp)) {
            PT(ribbon, 11.sp, Color.White)
        }
    }
}

private fun fmtLeftMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60; val sec = s % 60
    return if (d > 0) "${d}g %02d:%02d:%02d".format(h, m, sec) else "%02d:%02d:%02d".format(h, m, sec)
}

@Composable
private fun Contents(p: JSONObject) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (p.optInt("gems") > 0) Row(verticalAlignment = Alignment.CenterVertically) { Icon("gem", 18.dp, GEM); PT(" ${fmtL(p.optLong("gems"))} Elmas", 14.sp, GEM) }
        if (p.optLong("gold") > 0) Row(verticalAlignment = Alignment.CenterVertically) { Icon("coin", 18.dp, GOLD); PT(" ${fmtL(p.optLong("gold"))} Altın", 14.sp, GOLD) }
        val items = p.optJSONArray("items").objs()
        if (items.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (it in items.take(5)) Box {
                ItemSlot(it.optString("id"), it.optInt("n"), size = 40.dp)
            }
        }
    }
}

@Composable
private fun Featured(s: JSONObject, products: Map<String, JSONObject>, now: Long, t: Float, goTab: (Int) -> Unit) {
    val offers = s.optJSONArray("offers").objs()
    if (offers.isEmpty()) {
        PxPanel(Modifier.fillMaxWidth()) {
            PT("Sınırlı Süreli Paketler", 18.sp, GOLD)
            PT("5., 10. ve 25. seviyeye ulaşınca sana özel, süreli bir paket açılır. Şimdilik elmas paketlerine göz at: her paketin ilk alımında elmaslar 2 katı!",
                14.sp, Px.Text, maxLines = 3)
            Spacer(Modifier.height(6.dp))
            PxButton("Elmas Paketleri", icon = "gem", color = Px.Blue) { goTab(1) }
        }
    }
    for (o in offers) {
        val p = products[o.optString("id")] ?: continue
        val left = o.optLong("ends") - now
        ProductCard(Modifier.fillMaxWidth().height(220.dp), GOLD, t, ribbon = "SÜRELİ", price = Store.price(p.optString("id"), p.optDouble("price")),
            onBuy = { Store.buy(p.optString("id")) }) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    val art = p.optString("id")
                    if (UiArt.has(LocalContext.current, art)) ArtImage(art, Modifier.size(126.dp).graphicsLayer { rotationZ = sin(t * 1.5f) * 2f })
                    else Icon("crate", 90.dp, GOLD)
                }
                Column(Modifier.weight(1f)) {
                    PT(p.optString("title"), 24.sp, GOLD, outline = true)
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon("clock", 16.dp, Color(0xFFFF7A6A)); PT(" Kalan: ${fmtLeftMs(left)}", 15.sp, Color(0xFFFF7A6A)) }
                    PT("5 Katı Değerinde. Bir Daha Gelmez!", 13.sp, Px.Sub)
                    Spacer(Modifier.height(4.dp))
                    Contents(p)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GemsTab(products: Map<String, JSONObject>, t: Float) {
    val gems = products.values.filter { it.optString("kind") == "gems" }
    for (row in gems.chunked(3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, p) in row.withIndex()) {
                val id = p.optString("id")
                val bonus = p.optJSONObject("meta")?.optInt("bonus") ?: 0
                ProductCard(Modifier.weight(1f).height(200.dp), GEM, t + i, ribbon = if (p.optBoolean("first")) "İLK ALIM 2×" else if (bonus > 0) "+%$bonus" else null,
                    price = Store.price(id, p.optDouble("price")), onBuy = { Store.buy(id) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val tier = gems.indexOf(p)
                        if (UiArt.has(LocalContext.current, "gems_${tier + 1}")) ArtImage("gems_${tier + 1}", Modifier.size(84.dp))
                        else Icon("gem", (40 + tier * 8).dp, GEM)
                        PT(fmtL(p.optLong("gems")), 22.sp, GEM, outline = true)
                        PT(p.optString("title"), 12.sp, Px.Sub)
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
    PT("Ödeme Google Play ile yapılır; fiyatlara vergiler dahildir, içerik postana gelir. Tüm ürünler tek seferliktir, otomatik yenilenmez.", 12.sp, Px.Sub, maxLines = 2)
}

@Composable
private fun PacksTab(s: JSONObject, products: Map<String, JSONObject>, now: Long, t: Float) {
    val offers = s.optJSONArray("offers").objs().associateBy { it.optString("id") }
    val lv = com.zenvex.online.game.GameSession.hud()[com.zenvex.online.game.Native.LEVEL].toInt()
    for (p in products.values.filter { it.optString("kind") == "pack" }) {
        val id = p.optString("id")
        val m = p.optJSONObject("meta") ?: JSONObject()
        val o = offers[id]
        val need = m.optInt("level")
        val state = when { o != null -> "Kalan: ${fmtLeftMs(o.optLong("ends") - now)}"; lv < need -> "Seviye $need'de açılır (${m.optInt("hours")} saat)"; else -> "Süresi doldu ya da alındı" }
        ProductCard(Modifier.fillMaxWidth().height(170.dp), if (o != null) GOLD else Color(0xFF6A6A70), t, price = Store.price(id, p.optDouble("price")),
            priceEnabled = o != null, onBuy = { Store.buy(id) }) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PT(p.optString("title"), 20.sp, if (o != null) GOLD else Px.Sub)
                    PT(state, 13.sp, if (o != null) Color(0xFFFF7A6A) else Px.Sub)
                }
                Contents(p)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CardsTab(s: JSONObject, products: Map<String, JSONObject>, now: Long, changed: () -> Unit) {
    val cards = s.optJSONArray("cards").objs().associateBy { it.optString("id") }
    val t = rememberTime()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in products.values.filter { it.optString("kind") == "card" }) {
            val id = p.optString("id")
            val m = p.optJSONObject("meta") ?: JSONObject()
            val c = cards[id]
            val days = m.optInt("days")
            val total = p.optInt("gems") + m.optInt("daily_gems") * days
            ProductCard(Modifier.weight(1f).height(300.dp), if (id == "card_month") GOLD else Color(0xFFDCE4EE), t,
                ribbon = "TOPLAM ${fmtL(total.toLong())} ELMAS", price = Store.price(id, p.optDouble("price")), onBuy = { Store.buy(id) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (UiArt.has(LocalContext.current, id)) ArtImage(id, Modifier.size(90.dp)) else Icon("calendar", 60.dp, GOLD)
                    PT(p.optString("title"), 20.sp, Color.White)
                    PT("Anında ${p.optInt("gems")} elmas", 13.sp, GEM)
                    PT("$days gün boyunca her gün ${m.optInt("daily_gems")} elmas + ${fmtL(m.optLong("daily_gold"))} altın" +
                        (if (m.has("daily_items")) " + karakter kartı" else ""), 12.sp, Px.Text, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
                    if (c != null) {
                        PT("Aktif · ${fmtLeftMs(c.optLong("ends") - now)}", 12.sp, Px.Name)
                        if (c.optBoolean("claimable")) PxButton("Bugünkü Ödülü Al", color = Px.Orange, size = 14.sp, height = 34.dp) { Store.claimCards(changed) }
                    }
                }
            }
        }
    }
    PT("Kartlar tek seferlik satın almadır: abonelik değildir, otomatik yenilenmez. Alınmayan günlük ödüller 3 güne kadar birikir; kart bitmeden yeniden alırsan süresi uzar.", 12.sp, Px.Sub, maxLines = 2)
}

@Composable
private fun PassTab(s: JSONObject, products: Map<String, JSONObject>, t: Float) {
    val pass = s.optJSONObject("pass") ?: JSONObject()
    val normal = pass.optBoolean("normal"); val elite = pass.optBoolean("elite")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        products["pass_vip"]?.let { p ->
            ProductCard(Modifier.weight(1f).height(280.dp), VIP, t, price = if (normal || elite) "Sende" else Store.price("pass_vip", p.optDouble("price")),
                priceEnabled = !normal && !elite, onBuy = { Store.buy("pass_vip") }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (UiArt.has(LocalContext.current, "ticket_vip")) ArtImage("ticket_vip", Modifier.size(90.dp)) else Icon("pass", 60.dp, VIP)
                    PT("VIP Bilet", 22.sp, VIP)
                    PT("Bu sezonun bütün premium ödüllerini açar.", 13.sp, Px.Text, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        val upgrade = normal && !elite
        val pid = if (upgrade) "pass_upgrade" else "pass_premium"
        products[pid]?.let { p ->
            ProductCard(Modifier.weight(1f).height(280.dp), GOLD, t + 1, ribbon = "EN İYİ", price = if (elite) "Sende" else Store.price(pid, p.optDouble("price")),
                priceEnabled = !elite, onBuy = { Store.buy(pid) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (UiArt.has(LocalContext.current, "ticket_premium")) ArtImage("ticket_premium", Modifier.size(90.dp)) else Icon("crown", 60.dp, GOLD)
                    PT(if (upgrade) "Premium'a Yükselt" else "Premium Bilet", 22.sp, GOLD)
                    PT("Premium ödüller + S1 Çerçevesi + \"Sezon 1 Öncüsü\" unvanı + anında 10.000 kademe XP'si", 13.sp, Px.Text, maxLines = 3,
                        align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    PT("Bilet, şu an oynadığın sunucudaki karakterinde açılır.", 12.sp, Px.Sub)
}
