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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import org.json.JSONObject
import kotlin.math.hypot

private val BRANCH_COLORS = listOf(Color(0xFFE0583C), Color(0xFFE8B440), Color(0xFF52B866), Color(0xFF5AA8F0))

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
private fun TreeView(data: JSONObject, changed: () -> Unit) {
    val nodes = remember(data) { data.optJSONArray("nodes").objs() }
    val byId = remember(nodes) { nodes.associateBy { it.optInt("id") } }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var sel by remember { mutableIntStateOf(-1) }
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(Color(0xFF1B1A16))
                .pointerInput(Unit) {
                    detectTransformGestures { _, p, z, _ -> zoom = (zoom * z).coerceIn(0.4f, 2.5f); pan += p }
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
                        if (hit != null && hypot(c.x + hit.optDouble("x").toFloat() * unit - o.x, c.y + hit.optDouble("y").toFloat() * unit - o.y) < 22.dp.toPx() * zoom.coerceAtLeast(0.7f))
                            sel = hit.optInt("id")
                    }
                },
            ) {
                val unit = 46.dp.toPx() * zoom
                val c = Offset(size.width / 2f, size.height / 2f) + pan
                fun pos(n: JSONObject) = Offset(c.x + n.optDouble("x").toFloat() * unit, c.y + n.optDouble("y").toFloat() * unit)
                // bağlantılar
                for (n in nodes) {
                    val a = pos(n)
                    val links = n.optJSONArray("links") ?: continue
                    for (i in 0 until links.length()) {
                        val o = byId[links.getInt(i)] ?: continue
                        if (o.optInt("id") < n.optInt("id")) continue
                        val lit = n.optBoolean("own") && o.optBoolean("own")
                        drawLine(if (lit) Color(0xFFF6E05A) else Color(0xFF3E3C34), a, pos(o), (if (lit) 4.dp else 2.dp).toPx())
                    }
                }
                // düğümler
                for (n in nodes) {
                    val p = pos(n)
                    val k = n.optInt("k")
                    val r = (if (k == 2) 15.dp else if (k == 1) 12.dp else 8.dp).toPx() * zoom.coerceIn(0.7f, 1.4f)
                    val b = n.optInt("b")
                    val col = if (b in 0..3) BRANCH_COLORS[b] else Color(0xFFE8E4D4)
                    val own = n.optBoolean("own"); val av = n.optBoolean("av")
                    if (av) drawCircle(col.copy(alpha = 0.35f), r * 1.7f, p)
                    drawCircle(if (own) col else Color(0xFF26251F), r, p)
                    drawCircle(if (own || av) col else Color(0xFF55534A), r, p, style = Stroke((if (k == 2) 3.dp else 2.dp).toPx()))
                    if (n.optInt("id") == sel) drawCircle(Color.White, r + 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                }
            }
            // dal adları
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                val br = data.optJSONArray("branches")
                for (i in 0 until (br?.length() ?: 0)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(BRANCH_COLORS[i]))
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
            } else {
                val k = n.optInt("k")
                PT(listOf("Küçük", "Belirgin", "ANAHTAR")[k.coerceIn(0, 2)], 12.sp, if (k == 2) Px.Clan else Px.Sub)
                PT(n.optString("name"), 18.sp, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                PT(n.optString("desc"), 14.sp, Px.Name, maxLines = 4)
                Spacer(Modifier.weight(1f))
                when {
                    n.optBoolean("own") -> PT("✔ Açık", 16.sp, Px.Name)
                    n.optBoolean("av") -> PxButton("Aç (1 puan)", Modifier.fillMaxWidth(), Px.Green, size = 16.sp, enabled = data.optInt("points") > 0) {
                        val r = GameSession.cmd("tree_unlock", "id" to n.optInt("id")); Toaster.show(r.msg, r.ok); changed()
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
