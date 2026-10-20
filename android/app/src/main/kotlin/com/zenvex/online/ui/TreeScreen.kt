package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin


/**
 * Gelişim ekranı: "Skill Ağacı" (her seviye 1 puan, bağlı düğümden ilerlenir) ve "Savaşçı" (karakter seviyesi 1–10).
 */
@Composable
fun TreeScreen(close: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("tree") }
    Box(Modifier.fillMaxSize().background(Color(0xF2141310))) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxIcon("close", size = 40.dp) { close() }
                Spacer(Modifier.width(10.dp))
                PxButton("Skill Ağacı", color = if (tab == 0) Px.Green else Px.Dark, size = 16.sp, height = 40.dp) { tab = 0 }
                Spacer(Modifier.width(6.dp))
                PxButton("Savaşçı", color = if (tab == 1) Px.Green else Px.Dark, size = 16.sp, height = 40.dp) { tab = 1 }
                Spacer(Modifier.weight(1f))
                if (tab == 0) {
                    PT("Puan: ${data.optInt("points")}", 20.sp, if (data.optInt("points") > 0) Px.Clan else Px.Sub)
                    Spacer(Modifier.width(10.dp))
                    var confirm by remember { mutableStateOf(false) }
                    PxButton(if (confirm) "Emin misin?" else "Sıfırla (${fmt(data.optInt("reset_cost"))} altın)", color = Px.Red, size = 13.sp, height = 36.dp) {
                        if (!confirm) { confirm = true; return@PxButton }
                        confirm = false
                        val r = GameSession.cmd("tree_reset"); Toaster.show(r.msg, r.ok); tick++
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (tab == 0) TreeView(data) { tick++ } else WarriorView(data)
        }
    }
}

@Composable
internal fun TreeView(data: JSONObject, changed: () -> Unit) {
    val nodes = remember(data) { data.optJSONArray("nodes").objs() }
    val byId = remember(nodes) { nodes.associateBy { it.optInt("id") } }
    var zoom by remember { mutableFloatStateOf(0.85f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var sel by remember { mutableIntStateOf(-1) }
    // açılış patlaması: (düğüm, başlangıç zamanı)
    var burst by remember { mutableStateOf(-1 to 0f) }
    val haptic = LocalHapticFeedback.current
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(Color(0xFF0E0D0B))
                .pointerInput(Unit) {
                    detectTransformGestures { _, p, z, _ -> zoom = (zoom * z).coerceIn(0.35f, 2.5f); pan += p }
                },
        ) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(nodes) {
                    detectTapGestures { o ->
                        val unit = 46.dp.toPx() * zoom
                        val c = Offset(size.width / 2f, size.height / 2f) + pan
                        val hit = nodes.minByOrNull { n ->
                            hypot(c.x + n.optDouble("x").toFloat() * unit - o.x, c.y + n.optDouble("y").toFloat() * unit - o.y)
                        }
                        if (hit != null && hypot(c.x + hit.optDouble("x").toFloat() * unit - o.x, c.y + hit.optDouble("y").toFloat() * unit - o.y) < 26.dp.toPx() * zoom.coerceAtLeast(0.7f))
                            sel = hit.optInt("id")
                    }
                },
            ) {
                val unit = 46.dp.toPx() * zoom
                val c = Offset(size.width / 2f, size.height / 2f) + pan
                fun pos(n: JSONObject) = Offset(c.x + n.optDouble("x").toFloat() * unit, c.y + n.optDouble("y").toFloat() * unit)
                // arka plan: merkez ışığı + 5/10/15 derinlik halkaları (anahtar düğüm kademeleri)
                drawCircle(Brush.radialGradient(listOf(Color(0x332A2418), Color.Transparent), c, unit * 17f), unit * 17f, c)
                for (d in listOf(5f, 10f, 15f)) drawCircle(Color(0x14F6E05A), unit * d, c, style = Stroke(1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()), time * 12f)))
                // bağlantılar
                for (n in nodes) {
                    val a = pos(n)
                    val links = n.optJSONArray("links") ?: continue
                    for (i in 0 until links.length()) {
                        val o = byId[links.getInt(i)] ?: continue
                        if (o.optInt("id") < n.optInt("id")) continue
                        val b = pos(o)
                        val oa = n.optBoolean("own"); val ob = o.optBoolean("own")
                        when {
                            oa && ob -> {
                                drawLine(Color(0x40F6E05A), a, b, 10.dp.toPx() * zoom.coerceIn(0.6f, 1.2f), StrokeCap.Round)
                                drawLine(Color(0xFFF6E05A), a, b, 3.5.dp.toPx(), StrokeCap.Round)
                                // akan enerji zerreleri
                                val ph = ((n.optInt("id") * 37 + o.optInt("id") * 11) % 100) / 100f
                                for (k in 0..1) {
                                    val f = (time * 0.6f + ph + k * 0.5f) % 1f
                                    drawCircle(Color(0xFFFFF6C0), 2.6.dp.toPx(), Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
                                }
                            }
                            oa || ob -> {
                                val fc = SkillGlyphs.family((if (oa) o else n).optString("s"))
                                drawLine(fc.copy(alpha = 0.85f), a, b, 2.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), -time * 20f))
                            }
                            else -> drawLine(Color(0xFF34322B), a, b, 2.dp.toPx())
                        }
                    }
                }
                // düğümler
                val pulse = 0.5f + 0.5f * sin(time * 4.5f)
                for (n in nodes) {
                    val p = pos(n)
                    val k = n.optInt("k")
                    val zs = zoom.coerceIn(0.6f, 1.5f)
                    val r = (if (k == 2) 21.dp else if (k == 1) 16.dp else 12.dp).toPx() * zs
                    val stat = if (n.optInt("id") == 0) "core" else n.optString("s")
                    val fam = SkillGlyphs.family(stat)
                    val own = n.optBoolean("own"); val av = n.optBoolean("av")
                    val frame = when { own -> Color(0xFFF6E05A); av -> fam; else -> Color(0xFF4A4840) }
                    val fillC = if (own) Color(0xFF2A2116) else Color(0xFF181713)
                    if (own) drawCircle(Brush.radialGradient(listOf(fam.copy(alpha = 0.55f), Color.Transparent), p, r * 2.2f), r * 2.2f, p)
                    if (av) drawCircle(fam.copy(alpha = 0.25f + 0.45f * pulse), r * (1.35f + 0.15f * pulse), p, style = Stroke(2.5.dp.toPx()))
                    when (k) {
                        2 -> with(SkillGlyphs) { keystoneFrame(p, r, fillC, frame, 3.dp.toPx(), if (own) time * 12f else 0f) }
                        1 -> {
                            drawCircle(fillC, r, p)
                            drawCircle(frame, r, p, style = Stroke(2.5.dp.toPx()))
                            drawCircle(frame.copy(alpha = 0.7f), r * 0.82f, p, style = Stroke(1.2.dp.toPx()))
                        }
                        else -> {
                            drawCircle(fillC, r, p)
                            drawCircle(frame, r, p, style = Stroke(2.dp.toPx()))
                        }
                    }
                    val gc = when { own -> fam; av -> fam.copy(alpha = 0.85f); else -> Color(0xFF6A675C) }
                    with(SkillGlyphs) { glyph(stat, p, r * 0.62f, gc) }
                    if (n.optInt("id") == sel) drawCircle(Color.White, r + 6.dp.toPx(), p, style = Stroke(2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), time * 30f)))
                }
                // açılış patlaması: genişleyen halka + saçılan kıvılcımlar
                val (bid, bt) = burst
                val bn = byId[bid]
                if (bn != null && time - bt < 0.7f) {
                    val e = (time - bt) / 0.7f
                    val p = pos(bn); val fam = SkillGlyphs.family(bn.optString("s"))
                    drawCircle(Color.White.copy(alpha = 1f - e), 18.dp.toPx() * (1f + 2f * e), p, style = Stroke(3.dp.toPx() * (1f - e) + 1f))
                    for (i in 0 until 16) {
                        val ang = i * 6.2832f / 16 + bid
                        val d = 60.dp.toPx() * e
                        drawCircle(fam.copy(alpha = 1f - e), 3.dp.toPx() * (1f - e * 0.5f), Offset(p.x + cos(ang) * d, p.y + sin(ang) * d + 30.dp.toPx() * e * e))
                    }
                }
            }
            // dal adları
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                val br = data.optJSONArray("branches")
                val fams = listOf(SkillGlyphs.Damage, SkillGlyphs.Weapon, SkillGlyphs.Life, SkillGlyphs.Agility)
                for (i in 0 until (br?.length() ?: 0)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(fams[i]))
                    Spacer(Modifier.width(6.dp))
                    PT(br!!.getString(i) + listOf(" ↑", " →", " ↓", " ←")[i], 12.sp)
                }
            }
            PT("İki parmakla yakınlaştır / kaydır", 11.sp, Px.Sub, Modifier.align(Alignment.TopCenter).padding(6.dp))
        }
        Spacer(Modifier.width(8.dp))
        // seçili düğüm
        PxPanel(Modifier.width(230.dp).fillMaxHeight(), pad = 10.dp) {
            val n = byId[sel]
            if (n == null) {
                PT("Bir düğüme dokun", 16.sp, Px.Sub)
                Spacer(Modifier.height(8.dp))
                PT("Her seviyede 1 puan kazanırsın. Ortadan başla, açık bir düğüme bağlı olanları alabilirsin. Parlayanlar şu an alınabilir.",
                    13.sp, Px.Text, maxLines = 8)
                Spacer(Modifier.height(10.dp))
                for (st in listOf("FistPct" to "Hasar", "Crit" to "Kritik", "BladePct" to "Silah", "HpFlat" to "Can", "DmgTaken" to "Savunma", "Dodge" to "Çeviklik"))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Canvas(Modifier.size(22.dp)) { with(SkillGlyphs) { glyph(st.first, center, size.minDimension * 0.42f, family(st.first)) } }
                        Spacer(Modifier.width(8.dp)); PT(st.second, 13.sp, SkillGlyphs.family(st.first))
                    }
            } else {
                val k = n.optInt("k")
                val stat = if (n.optInt("id") == 0) "core" else n.optString("s")
                val fam = SkillGlyphs.family(stat)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(58.dp)) {
                        val r = size.minDimension * 0.4f
                        drawCircle(Brush.radialGradient(listOf(fam.copy(alpha = 0.5f), Color.Transparent), center, r * 1.4f), r * 1.4f, center)
                        drawCircle(Color(0xFF181713), r, center); drawCircle(fam, r, center, style = Stroke(2.5.dp.toPx()))
                        with(SkillGlyphs) { glyph(stat, center, r * 0.62f, fam) }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        PT(listOf("Küçük", "Belirgin", "ANAHTAR")[k.coerceIn(0, 2)], 12.sp, if (k == 2) Px.Clan else Px.Sub)
                        PT(SkillGlyphs.familyName(stat), 12.sp, fam)
                    }
                }
                Spacer(Modifier.height(4.dp))
                PT(n.optString("name"), 18.sp, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                PT(n.optString("desc"), 14.sp, Px.Name, maxLines = 4)
                Spacer(Modifier.weight(1f))
                when {
                    n.optBoolean("own") -> PT("✔ Açık", 16.sp, Px.Name)
                    n.optBoolean("av") -> PxButton("Aç (1 puan)", Modifier.fillMaxWidth(), Px.Green, size = 16.sp, enabled = data.optInt("points") > 0) {
                        val r = GameSession.cmd("tree_unlock", "id" to n.optInt("id")); Toaster.show(r.msg, r.ok)
                        if (r.ok) { burst = n.optInt("id") to time; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        changed()
                    }
                    else -> PT("Önce bağlı bir düğümü aç", 13.sp, Px.Sub, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun WarriorView(data: JSONObject) {
    val lv = data.optInt("char_level", 1)
    Row(Modifier.fillMaxSize()) {
        PxPanel(Modifier.width(260.dp).fillMaxHeight(), pad = 12.dp) {
            PT("SAVAŞÇI", 26.sp, Color(0xFFB277FF), outline = true)
            PT("Karakter seviyesi $lv / 10", 16.sp)
            Spacer(Modifier.height(6.dp))
            if (lv < 10) {
                PxBar(data.optInt("char_xp") / data.optInt("char_next", 1).toFloat().coerceAtLeast(1f), Color(0xFFB277FF), Modifier.fillMaxWidth(), 12.dp)
                PT("${fmt(data.optInt("char_xp"))} / ${fmt(data.optInt("char_next"))}", 12.sp, Px.Sub)
            }
            Spacer(Modifier.height(8.dp))
            PT("Karakter XP'si zombi öldürdükçe dolar. Her seviye yeni bir özellik açar.", 13.sp, Px.Sub, maxLines = 4)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val perks = data.optJSONArray("perks")
            for (i in 0 until (perks?.length() ?: 0)) {
                val on = i < lv
                Row(Modifier.fillMaxWidth().background(if (on) Color(0xFF2E2A3A) else Px.Row).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(i + 1, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    PT(perks!!.getString(i), 15.sp, if (on) Px.Text else Px.Sub, maxLines = 2)
                    Spacer(Modifier.weight(1f))
                    PT(if (on) "✔" else "🔒", 16.sp, if (on) Px.Name else Px.Sub)
                }
            }
        }
    }
}
