package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import kotlin.math.roundToInt
import kotlin.math.sin

/** Öğretici katmanı: adım okuma, ekran rect'i kaydı ve parlak çerçeve + zıplayan ok + açıklama kartı çizimi */
object Tut {
    /** Şu an etkin (kabul edilmiş, bitmemiş) rehber adımının kimliği; yoksa boş */
    fun step(): String {
        val a = GameSession.ui("quests").optJSONArray("story") ?: return ""
        for (i in 0 until a.length()) {
            val q = a.optJSONObject(i) ?: continue
            if (q.optBoolean("guide") && q.optBoolean("on") && !q.optBoolean("ready")) return q.optString("id")
        }
        return ""
    }

    /** Etkin üretim görevinin istediği eşya ve tezgâh tarifi mi (rehber önce; yoksa null) */
    fun craftWant(): Pair<String, Boolean>? {
        val a = GameSession.ui("quests").optJSONArray("story") ?: return null
        val qs = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optBoolean("on") && !it.optBoolean("ready") && it.has("craft") }
        val q = qs.firstOrNull { it.optBoolean("guide") } ?: qs.firstOrNull() ?: return null
        return q.optString("craft") to q.optBoolean("bench")
    }

    /** Olayı oyuna bildirir (görev hedefi "Ping"); bilinmeyen ad Rust'ta yok sayılır */
    fun ping(name: String) { GameSession.cmdNoSave("story_ping", "name" to name) }
}

/**
 * Parlatılacak bir ekran bölgesi: [rect] kök koordinatında (boundsInRoot), [label] kısa yazı. [tap]: dokunulacak yer
 * (parmak ucu ortasına inen el); değilse eşyanın varacağı yer (ok).
 */
class TutTarget(val rect: Rect?, val label: String, val below: Boolean = false, val tap: Boolean = true)

/** Bir bileşenin kök koordinatındaki dikdörtgenini [map] içine yazar */
fun Modifier.tutRect(map: MutableMap<String, Rect>, key: String): Modifier = onGloballyPositioned { map[key] = it.boundsInRoot() }

/**
 * Hedeflerin çevresine nabız gibi atan altın çerçeve ve ok çizer; [text] varsa sol üstte açıklama kartı gösterir.
 * Ekranı kaplayan bir Box içinde, her şeyin üstünde çağrılır (dokunuşları yutmaz).
 */
@Composable
fun TutLayer(targets: List<TutTarget>, text: String?, cardStart: Int = 230, cardEnd: Int = 340) {
    if (targets.isEmpty() && text == null) return
    val t = rememberTime()
    var root by remember { mutableStateOf(Offset.Zero) }
    val dn = LocalDensity.current
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.positionInRoot() }) {
        for (g in targets) {
            val r = g.rect ?: continue
            TutMark(r, root, g.label, g.below, g.tap, t, dn)
        }
        if (text != null) {
            Box(Modifier.align(Alignment.TopStart).padding(start = cardStart.dp, end = cardEnd.dp, top = 4.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color(0xE8241E10))
                .border(1.5.dp, Color(0xFFFFD34A).copy(alpha = 0.7f + 0.3f * sin(t * 4f)), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                PT(text, 10.5.sp, Color(0xFFFFF0C0), maxLines = 3)
            }
        }
    }
}

@Composable
private fun TutMark(r: Rect, root: Offset, label: String, below: Boolean, tap: Boolean, t: Float, dn: androidx.compose.ui.unit.Density) {
    val pad = with(dn) { 3.dp.toPx() }
    val x = r.left - root.x - pad; val y = r.top - root.y - pad
    val w = r.width + pad * 2; val h = r.height + pad * 2
    val pulse = 0.5f + 0.5f * sin(t * 5f)
    val gold = Color(0xFFFFD34A)
    Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()) }
        .size(with(dn) { w.toDp() }, with(dn) { h.toDp() })) {
        Canvas(Modifier.fillMaxSize()) {
            val rr = CornerRadius(6.dp.toPx())
            // dış parıltı: üç halka, nabızla genişleyip sönen
            for (k in 3 downTo 1) {
                val g = k * 3.dp.toPx() * (0.7f + 0.6f * pulse)
                drawRoundRect(gold.copy(alpha = (0.16f - k * 0.035f) * (0.6f + 0.8f * pulse)), Offset(-g, -g), Size(size.width + g * 2, size.height + g * 2),
                    rr, Stroke(3.dp.toPx()))
            }
            drawRoundRect(Color(0xFFFFE08A).copy(alpha = 0.65f + 0.35f * pulse), Offset.Zero, size, rr, Stroke(2.5.dp.toPx()))
            drawRoundRect(gold.copy(alpha = 0.10f + 0.10f * pulse), Offset.Zero, size, rr)
        }
        if (tap) {
            // dokun eli: parmak ucu hedefin ortasına iner (tut_hand), etiket hedefin üstünde/altında
            TutTap(label, above = !below)
        } else {
            val bounce = ((1f + sin(t * 5f)) * 3).dp
            Column(Modifier.align(if (below) Alignment.BottomCenter else Alignment.TopCenter)
                .offset(y = if (below) 4.dp + bounce else (-4).dp - bounce).tutAnchor(0.5f, if (below) 0f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (below) Arrow(true, pulse)
                if (label.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xE0101010)).border(1.dp, gold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp)) { PT(label, 12.sp, Color(0xFFFFF0C0)) }
                if (!below) Arrow(false, pulse)
            }
        }
    }
}

@Composable
private fun Arrow(up: Boolean, pulse: Float) {
    Canvas(Modifier.size(18.dp, 14.dp)) {
        val p = Path().apply {
            if (up) { moveTo(size.width / 2, 0f); lineTo(0f, size.height); lineTo(size.width / 2, size.height * 0.6f); lineTo(size.width, size.height) }
            else { moveTo(size.width / 2, size.height); lineTo(0f, 0f); lineTo(size.width / 2, size.height * 0.4f); lineTo(size.width, 0f) }
            close()
        }
        drawPath(p, Color(0xFFFFD34A).copy(alpha = 0.75f + 0.25f * pulse))
        drawPath(p, Color(0xB015140F), style = Stroke(1.5f))
    }
}

/**
 * Öğretici konuşma balonu (V19): eskiden oyun tuvalinde çiziliyordu ve üstteki arayüz katmanlarının (kontrol rehberi,
 * pencereler) arkasında kalıyordu. Şimdi HUD'ın en üst katmanında: GameView metni ve konumu HudLayout'a yazar.
 */
@Composable
fun GuideBubble() {
    rememberTime() // her karede yeniden okunur (HudLayout değerleri Compose durumu değil)
    val msg = com.zenvex.online.game.HudLayout.tutMsg
    val a = com.zenvex.online.game.HudLayout.tutAlpha
    if (msg.isEmpty() || a <= 0.01f) return
    val diana = com.zenvex.online.game.HudLayout.tutDiana
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // sol üst kısayol satırının (y ≈ 84-130 dp, işaret etiketi dahil) ve sağ üst para kapsülünün altında: tuşları örtmez
        val top = maxOf(maxHeight * com.zenvex.online.game.HudLayout.tutTop, 158.dp)
        androidx.compose.foundation.layout.Row(Modifier.align(Alignment.TopCenter).padding(top = top + ((1f - a) * -10f).dp)
            .alpha(a), verticalAlignment = Alignment.Top) {
            if (diana) {
                Box(Modifier.size(58.dp, 64.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xD8141310)).border(1.5.dp, Color(0xFF6A5A3A), RoundedCornerShape(6.dp))) {
                    ArtImage("commander_portrait", Modifier.fillMaxSize())
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.widthIn(max = 430.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE6141310))
                .border(1.5.dp, Color(0xFF6A5A3A), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                PT(if (diana) "Komutan Diana" else "İlk Adımlar", 12.sp, Color(0xFFFFD34A))
                PT(msg, 13.sp, Color(0xFFF2EEE4), maxLines = 5)
            }
        }
    }
}
