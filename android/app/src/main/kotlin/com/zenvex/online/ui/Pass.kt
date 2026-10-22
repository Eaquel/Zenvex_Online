package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Pass
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val PGOLD = Color(0xFFFFC83A)
private val PGOLD_D = Color(0xFF8A5A08)
private val PBLUE = Color(0xFF6AB8FF)
private val GEM = Color(0xFF7FE0FF)

// =========================================================================== sezon bileti
@Composable
fun PassScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(Pass.now()) }
    LaunchedEffect(Unit) { while (true) { now = Pass.now(); kotlinx.coroutines.delay(1000) } }
    val p = remember(tick) { GameSession.ui("pass") }
    PassView(p, Pass.name, Pass.ends, now, back, changed = { tick++ },
        buy = { elite, price -> Pass.buy(elite, price, p.optInt("gems")) { tick++ } },
        claim = { tier, premium, gem -> Pass.claim(tier, premium, gem) { tick++ } })
}

/** Süre yazısı: "12 Gün 4 Saat", "3 Saat 12 Dk", "8 Dk 20 Sn" */
fun fmtLeft(ms: Long): String {
    if (ms <= 0) return "Bitti"
    val s = ms / 1000; val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60
    return when { d > 0 -> "$d Gün $h Saat"; h > 0 -> "$h Saat $m Dk"; else -> "$m Dk ${s % 60} Sn" }
}

/** Türkiye saatiyle bir sonraki gece yarısı / Pazartesi gece yarısı */
private fun nextDay(now: Long): Long { val tr = now + 3 * 3_600_000L; return (tr / 86_400_000L + 1) * 86_400_000L - 3 * 3_600_000L }
private fun nextWeek(now: Long): Long {
    val day = (now + 3 * 3_600_000L) / 86_400_000L
    val wk = (day + 3) / 7
    return ((wk + 1) * 7 - 3) * 86_400_000L - 3 * 3_600_000L
}

@Composable
internal fun PassView(
    p: JSONObject, season: String, ends: Long, now: Long, back: () -> Unit, changed: () -> Unit,
    buy: (Boolean, Int) -> Unit, claim: (Int, Boolean, Boolean) -> Unit, startTab: Int = 0,
) {
    var tab by remember { mutableIntStateOf(startTab) }
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241A10), Color(0xFF120E0A), Color(0xFF0A0806))))
        .clickable(remember { MutableInteractionSource() }, null) {}) {
        // arka plan: yükselen kor parçacıkları
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 40) {
                val sx = ((i * 97) % 100) / 100f * size.width
                val sp = 0.04f + (i % 7) * 0.012f
                val y = size.height * (1f - ((t * sp + i * 0.137f) % 1f))
                val a = 0.25f + 0.25f * sin(t * 2f + i)
                drawCircle(Color(0xFFFF9A3A).copy(alpha = a), 1.5f + (i % 3), Offset(sx + sin(t + i) * 12f, y))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            PassHeader(p, season, ends, now, back, buy)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val claimable = countClaimable(p)
                PassTab("Ödüller", "crown", tab == 0, claimable) { tab = 0 }
                PassTab("Günlük", "calendar", tab == 1, 0) { tab = 1 }
                PassTab("Haftalık", "star", tab == 2, 0) { tab = 2 }
                PassTab("Sezon", "flame", tab == 3, 0) { tab = 3 }
                Spacer(Modifier.weight(1f))
                when (tab) {
                    1 -> PT("Yenilenmeye: ${fmtLeft(nextDay(now) - now)}", 13.sp, Px.Sub)
                    2 -> PT("Yenilenmeye: ${fmtLeft(nextWeek(now) - now)}", 13.sp, Px.Sub)
                    3 -> PT("Sezon Sonuna Kadar", 13.sp, Px.Sub)
                    else -> PT("1.000 Bilet XP = 1 Kademe", 12.sp, Px.Sub)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> RewardTrack(p, t, claim)
                    1 -> QuestColumn(p.optJSONArray("daily"))
                    2 -> QuestColumn(p.optJSONArray("weekly"))
                    else -> QuestColumn(p.optJSONArray("season"))
                }
            }
        }
    }
}

private fun countClaimable(p: JSONObject): Int {
    val prem = p.optBoolean("normal") || p.optBoolean("elite")
    var n = 0
    for (r in p.optJSONArray("rewards").objs()) {
        if (!r.optBoolean("reached")) continue
        if (!r.optBoolean("free_claimed")) n++
        if (prem && !r.optBoolean("premium_claimed")) n++
    }
    return n
}

@Composable
private fun PassHeader(p: JSONObject, season: String, ends: Long, now: Long, back: () -> Unit, buy: (Boolean, Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PxIcon("close", size = 40.dp) { back() }
        Spacer(Modifier.width(10.dp))
        TierBadge(p.optInt("tier"), 62.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT("Sezon Bileti", 24.sp, PGOLD, outline = true)
                Spacer(Modifier.width(10.dp))
                PT(season, 14.sp, Px.Orange)
            }
            PT(if (ends > 0) "Sezon Bitimine: ${fmtLeft(ends - now)}" else "Sezon Bilgisi Yükleniyor…", 12.sp, Px.Sub)
            Spacer(Modifier.height(4.dp))
            val per = p.optInt("xp_per_tier", 1000)
            val max = p.optInt("tier") >= p.optInt("tiers", 30)
            XpBar(if (max) 1f else p.optInt("xp") / per.toFloat(), if (max) "Son Kademe!" else "Kademe ${p.optInt("tier")} / ${p.optInt("tiers")} · ${fmt(p.optInt("xp"))} / ${fmt(per)} XP",
                Modifier.width(330.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
            Icon("gem", 20.dp, GEM); Spacer(Modifier.width(4.dp)); PT(fmt(p.optInt("gems")), 17.sp, GEM)
        }
        val normal = p.optBoolean("normal"); val elite = p.optBoolean("elite")
        val pn = p.optInt("price_normal", 600); val pe = p.optInt("price_elite", 1500)
        TicketCard("Premium Bilet", "Tüm Premium Ödüller", if (normal) 0 else pn, normal, false) { buy(false, pn) }
        Spacer(Modifier.width(8.dp))
        TicketCard("Elit Bilet", "Premium + 10 Kademe", if (elite) 0 else if (normal) pe - pn else pe, elite, true) { buy(true, if (normal) pe - pn else pe) }
    }
}

/** Kademe rozeti: altıgen, altın kenar, dönen ışık */
@Composable
private fun TierBadge(tier: Int, size: Dp) {
    val t = rememberTime()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val r = this.size.minDimension / 2
            fun hex(rr: Float) = Path().apply {
                for (i in 0 until 6) {
                    val a = PI.toFloat() / 3 * i - PI.toFloat() / 2
                    val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(hex(r), Color(0xFF15140F))
            drawPath(hex(r * 0.9f), Brush.linearGradient(listOf(Color(0xFFFFF0B0), PGOLD, PGOLD_D), Offset(0f, 0f), Offset(this.size.width, this.size.height)))
            drawPath(hex(r * 0.72f), Brush.radialGradient(listOf(Color(0xFF3A2A12), Color(0xFF1A120A)), c, r))
            val a = t * 1.4f
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent), Offset(c.x + cos(a) * r * 0.8f, c.y + sin(a) * r * 0.8f), r * 0.3f),
                r * 0.3f, Offset(c.x + cos(a) * r * 0.8f, c.y + sin(a) * r * 0.8f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PT("$tier", (size.value * 0.36f).sp, PGOLD)
            PT("Kademe", (size.value * 0.13f).sp, Px.Sub)
        }
    }
}

/** Bilet XP çubuğu: piksel kenar, dolgu üstünde kayan parıltı */
@Composable
private fun XpBar(k: Float, label: String, modifier: Modifier) {
    val t = rememberTime()
    Box(modifier.height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF15140F)).padding(2.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF2A241A))
            val w = size.width * k.coerceIn(0f, 1f)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE07A), PGOLD, Color(0xFFC07A10))), size = Size(w, size.height))
            val sx = (t * 0.6f % 1.4f) * size.width - size.width * 0.2f
            if (sx < w) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), sx, sx + 40f),
                Offset(sx, 0f), Size(minOf(40f, w - sx), size.height))
            drawRect(Color.White.copy(alpha = 0.25f), size = Size(w, 2f))
        }
        PT(label, 11.sp, Color.White, Modifier.align(Alignment.Center))
    }
}

/**
 * Bilet kartı (sıradan buton değil): altın/mavi kabartmalı plaka, kayan parlama, taç simgesi, elmas fiyatı.
 * Sahipse yeşil "Açık" mührü.
 */
@Composable
private fun TicketCard(title: String, sub: String, price: Int, owned: Boolean, elite: Boolean, onClick: () -> Unit) {
    val t = rememberTime()
    val main = if (elite) PGOLD else PBLUE
    val dark = if (elite) Color(0xFF5A3A06) else Color(0xFF14305A)
    Box(Modifier.width(150.dp).height(68.dp).clip(RoundedCornerShape(4.dp))
        .drawBehind {
            drawRect(Color(0xFF15140F))
            drawRect(Brush.verticalGradient(listOf(main.copy(alpha = 0.95f), dark)), Offset(3f, 3f), Size(size.width - 6f, size.height - 6f))
            // kayan parlama
            val u = ((t * 0.35f + if (elite) 0.5f else 0f) % 1.6f) * size.width - size.width * 0.3f
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), Offset(u, 0f), Offset(u + 60f, size.height)),
                Offset(3f, 3f), Size(size.width - 6f, size.height - 6f))
            drawRect(Color.White.copy(alpha = 0.35f), Offset(3f, 3f), Size(size.width - 6f, 3f))
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(3f, size.height - 8f), Size(size.width - 6f, 5f))
            // köşe perçinleri
            for ((x, y) in listOf(9f to 9f, size.width - 9f to 9f, 9f to size.height - 12f, size.width - 9f to size.height - 12f)) drawCircle(Color.White.copy(alpha = 0.5f), 3f, Offset(x, y))
            if (elite) {
                val k = 0.5f + 0.5f * sin(t * 3f)
                drawRect(PGOLD.copy(alpha = 0.3f + 0.4f * k), style = Stroke(3f))
            }
        }
        .clickable(enabled = !owned) { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("crown", 18.dp, if (elite) Color(0xFF3A2406) else Color.White)
                Spacer(Modifier.width(5.dp))
                PT(title, 14.sp, if (elite) Color(0xFF2A1A04) else Color.White)
            }
            PT(sub, 10.sp, if (elite) Color(0xFF4A3008) else Color(0xFFD8E8FF))
            if (owned) PT("✔ Açık", 13.sp, Color(0xFF8CFF7A))
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("gem", 15.dp, if (elite) Color(0xFF1A3A5A) else GEM)
                Spacer(Modifier.width(4.dp))
                PT(fmt(price), 14.sp, if (elite) Color(0xFF1A2A3A) else Color.White)
            }
        }
    }
}

@Composable
private fun PassTab(name: String, icon: String, on: Boolean, badge: Int, onClick: () -> Unit) {
    Box {
        Row(Modifier.clip(RoundedCornerShape(3.dp))
            .background(if (on) Brush.verticalGradient(listOf(Color(0xFF6A4A16), Color(0xFF3A2808))) else Brush.verticalGradient(listOf(Color(0xFF2E2A22), Color(0xFF1E1B16))))
            .border(2.dp, if (on) PGOLD.copy(alpha = 0.8f) else Color(0xFF3A362C), RoundedCornerShape(3.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 16.dp, if (on) PGOLD else Px.Sub)
            Spacer(Modifier.width(6.dp))
            PT(name, 14.sp, if (on) Px.Text else Px.Sub)
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE0302A)),
            contentAlignment = Alignment.Center) { PT("$badge", 10.sp, Color.White) }
    }
}

// --------------------------------------------------------------------------- ödül yolu
@Composable
private fun RewardTrack(p: JSONObject, t: Float, claim: (Int, Boolean, Boolean) -> Unit) {
    val rewards = p.optJSONArray("rewards").objs()
    val prem = p.optBoolean("normal") || p.optBoolean("elite")
    val tier = p.optInt("tier")
    val scroll = rememberScrollState()
    val colW = 96.dp
    val px = with(LocalDensity.current) { colW.toPx() }
    LaunchedEffect(Unit) { scroll.scrollTo(((tier - 2).coerceAtLeast(0) * px).toInt()) }
    Row(Modifier.fillMaxSize()) {
        // satır etiketleri
        Column(Modifier.width(74.dp).fillMaxHeight().padding(top = 30.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color(0x55304050)), contentAlignment = Alignment.Center) {
                PT("Ücretsiz", 12.sp, Px.Text)
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0x88A0700E), Color(0x55402A04)))),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon("crown", 16.dp, PGOLD)
                    PT("Premium", 12.sp, PGOLD)
                    if (!prem) Icon("lock", 14.dp, Px.Sub)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.fillMaxHeight().horizontalScroll(scroll)) {
            for (r in rewards) {
                val tr = r.optInt("tier")
                val reached = r.optBoolean("reached")
                Column(Modifier.width(colW).fillMaxHeight().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // kademe düğümü + çizgi
                    Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val y = size.height / 2
                            drawLine(if (reached) PGOLD else Color(0xFF3A362C), Offset(-6f, y), Offset(size.width + 6f, y), 5f)
                        }
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(3.dp)).background(if (reached) PGOLD else Color(0xFF2A2620))
                            .border(2.dp, if (tr == tier) Color.White else Color(0xFF15140F), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                            PT("$tr", 11.sp, if (reached) Color(0xFF2A1A04) else Px.Sub)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val free = r.optJSONObject("free") ?: JSONObject()
                    RewardSlot(free, reached, r.optBoolean("free_claimed"), true, false, t, Modifier.weight(1f)) { claim(tr, false, free.optString("type") == "gem") }
                    Spacer(Modifier.height(8.dp))
                    val pr = r.optJSONObject("premium") ?: JSONObject()
                    RewardSlot(pr, reached, r.optBoolean("premium_claimed"), prem, true, t, Modifier.weight(1f)) { claim(tr, true, pr.optString("type") == "gem") }
                }
            }
        }
    }
}

@Composable
private fun RewardSlot(r: JSONObject, reached: Boolean, claimed: Boolean, allowed: Boolean, gold: Boolean, t: Float, modifier: Modifier, claim: () -> Unit) {
    val ready = reached && allowed && !claimed
    val rc = rarityCol(r.optInt("rarity"))
    val pulse = if (ready) 0.5f + 0.5f * sin(t * 5f) else 0f
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
        .background(Brush.verticalGradient(if (gold) listOf(Color(0xFF4A3410), Color(0xFF1E160A)) else listOf(Color(0xFF26303A), Color(0xFF14181E))))
        .border(2.dp, if (ready) lerp(rc, Color.White, pulse * 0.6f) else if (gold) PGOLD.copy(alpha = 0.45f) else Color(0xFF3A4048), RoundedCornerShape(3.dp))
        .clickable(enabled = ready) { claim() }.padding(4.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (ready) Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(rc.copy(alpha = 0.55f * (0.5f + pulse * 0.5f)), Color.Transparent)), size.minDimension * 0.6f)
                }
                when (r.optString("type")) {
                    "gold" -> Icon("coin", 32.dp, PGOLD)
                    "gem" -> Icon("gem", 32.dp, GEM)
                    else -> ItemArt(r.optString("id"), Modifier.fillMaxSize())
                }
            }
            PT(r.optString("name"), 9.sp, rc, align = TextAlign.Center, maxLines = 2)
            PT("×${fmt(r.optInt("count", 1))}", 11.sp, Px.Text)
        }
        when {
            claimed -> Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon("check", 26.dp, Px.Name) }
            !allowed -> Box(Modifier.align(Alignment.TopEnd)) { Icon("lock", 14.dp, Px.Sub) }
            ready -> Box(Modifier.align(Alignment.BottomCenter).offset(y = 2.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3E8A2A)).padding(horizontal = 8.dp, vertical = 1.dp)) {
                PT("Al", 11.sp, Color.White)
            }
        }
    }
}

private fun rarityCol(r: Int) = when (r) { 1 -> Color(0xFF6AB8FF); 2 -> Color(0xFFC07AFF); 3 -> PGOLD; 4 -> Color(0xFFFF6A5A); else -> Color(0xFFD8D4C4) }
private fun lerp(a: Color, b: Color, k: Float) = Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)

// --------------------------------------------------------------------------- görev listeleri
@Composable
private fun QuestColumn(list: JSONArray?) {
    val qs = list.objs()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (qs.isEmpty()) PT("Görev Yok", 14.sp, Px.Sub)
        for (q in qs) QuestRow(q)
    }
}

@Composable
private fun QuestRow(q: JSONObject) {
    val done = q.optBoolean("done")
    val goal = q.optInt("goal", 1); val prog = q.optInt("progress")
    val icon = when {
        q.optString("id").contains("titan") -> "flame"; q.optString("id").contains("soldier") -> "shield"
        q.optString("id").contains("base") -> "map"; q.optString("id").contains("craft") -> "hammer"
        q.optString("id").contains("crate") -> "gift"; q.optString("id").contains("chop") -> "wood"
        q.optString("id").contains("dog") -> "paw"; q.optString("id").contains("level") -> "star"
        q.optString("id").contains("gold") -> "coin"; else -> "skull"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(if (done) Color(0x5530602A) else Color(0xCC1E1B16))
        .border(2.dp, if (done) Color(0xFF5AA040) else Color(0xFF3A362C), RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2A2620)), contentAlignment = Alignment.Center) {
            Icon(icon, 22.dp, if (done) Px.Name else Px.Orange)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            PT(q.optString("title"), 15.sp, if (done) Px.Sub else Px.Text)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(260.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF15140F)).padding(1.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((prog / goal.toFloat()).coerceIn(0f, 1f)).background(if (done) Color(0xFF5AA040) else Px.Orange))
                PT("${fmt(prog)} / ${fmt(goal)}", 9.sp, Color.White, Modifier.align(Alignment.Center))
            }
        }
        RewardChip("crown", "${fmt(q.optInt("pass_xp"))} Bilet XP", PGOLD)
        Spacer(Modifier.width(6.dp))
        RewardChip("coin", fmt(q.optInt("gold")), Color(0xFFFFE07A))
        if (q.optInt("xp") > 0) { Spacer(Modifier.width(6.dp)); RewardChip("star", "${fmt(q.optInt("xp"))} XP", Color(0xFF8CE0FF)) }
        Spacer(Modifier.width(8.dp))
        if (done) Icon("check", 24.dp, Px.Name) else Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun RewardChip(icon: String, text: String, col: Color) {
    Row(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0x66000000)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, 13.dp, col); Spacer(Modifier.width(4.dp)); PT(text, 11.sp, col)
    }
}

/** Eşya görseli; atlasta yoksa (yeni eşya, görseli henüz eklenmemiş) türüne uygun simge. */
@Composable
fun ItemArt(id: String, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val has = remember(id) { SpriteCache.get(ctx, "i/$id") != null }
    if (has) SpriteImage("i/$id", modifier)
    else Box(modifier, contentAlignment = Alignment.Center) {
        val icon = when {
            id.startsWith("pants") -> "shirt"; id.contains("ring") -> "star"; id.contains("neck") -> "heart"
            id.contains("helm") -> "shield"; id.startsWith("chest") -> "gift"; else -> "bag"
        }
        Icon(icon, 30.dp, rarityCol(3))
    }
}
