package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bakım ve yasak kapıları: uygulama açıkken dakikada bir sunucuya sorulur.
 * Bakım açıkken yöneticiler dışında herkes bakım ekranında kalır.
 */
object Gate {
    val maintenance = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)

    fun poll() = Account.status { r -> r.onSuccess { m -> maintenance.value = if (m.optBoolean("on")) m else null } }
}

@Composable
fun GateHost() {
    LaunchedEffect(Unit) { while (true) { Gate.poll(); delay(60_000) } }
    val ban = Account.banned.value
    val m = Gate.maintenance.value
    when {
        ban != null -> BanScreen(ban)
        m != null && !Account.admin.value -> MaintenanceScreen(m.optString("msg"))
    }
}

/** Bakım ekranı: gece şehri, dönen dişliler, kıvılcım saçan kaynakçı, yanıp sönen uyarı lambaları. */
@Composable
fun MaintenanceScreen(msg: String) {
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Color(0xFF0C0E16)).clickable(remember { MutableInteractionSource() }, null) {}) {
        Canvas(Modifier.fillMaxSize()) { maintenanceScene(t) }
        Column(Modifier.align(Alignment.Center).padding(top = 40.dp).pxPanel(Color(0xE81E1D19)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            PT("Bakım Çalışması", 34.sp, Px.Orange, outline = true)
            Spacer(Modifier.height(6.dp))
            PT(msg.ifEmpty { "Hayatta kalanlar için kampı güçlendiriyoruz. Birazdan geri döneceğiz!" }, 16.sp, Px.Text, maxLines = 3)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PxButton("Tekrar Dene", height = 46.dp, size = 17.sp, icon = "refresh") { Gate.poll() }
            }
            Spacer(Modifier.height(6.dp))
            PT("Ekran her dakika kendiliğinden yenilenir.", 12.sp, Px.Sub)
        }
    }
}

private fun DrawScope.maintenanceScene(t: Float) {
    val w = size.width; val h = size.height; val px = 4.dp.toPx()
    // gökyüzü + yıldızlar
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0C18), Color(0xFF1C1830), Color(0xFF2A1E2A))))
    for (k in 0 until 60) {
        val a = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 1.5f + k * 2.1f))
        drawRect(Color(0xFFFFF4D0).copy(alpha = a * 0.8f), Offset((k * 137f) % w, (k * 71f) % (h * 0.5f)), Size(px * 0.5f, px * 0.5f))
    }
    // ay
    drawCircle(Color(0xFFEDE6C8), h * 0.07f, Offset(w * 0.82f, h * 0.18f))
    drawCircle(Color(0xFF1C1830), h * 0.06f, Offset(w * 0.8f, h * 0.165f))
    // yıkık şehir silueti (pikselli basamaklar)
    var x = 0f; var k = 0
    while (x < w) {
        val bw = px * (8 + (k * 7) % 11); val bh = h * (0.18f + ((k * 37) % 23) / 100f)
        drawRect(Color(0xFF14121E), Offset(x, h - bh), Size(bw, bh))
        // pencereler
        for (wy in 0 until (bh / (px * 4)).toInt()) for (wx in 0 until (bw / (px * 4)).toInt()) {
            if ((wx * 13 + wy * 7 + k) % 9 == 0) {
                val on = sin(t * 0.7f + wx + wy * 3 + k) > 0.2f
                drawRect(if (on) Color(0xFFE8B850) else Color(0xFF221E2C), Offset(x + px + wx * px * 4, h - bh + px * 2 + wy * px * 4), Size(px * 1.6f, px * 1.6f))
            }
        }
        x += bw + px; k++
    }
    // vinç
    val cx = w * 0.18f
    drawRect(Color(0xFF3A3020), Offset(cx, h * 0.25f), Size(px * 2, h * 0.75f))
    drawRect(Color(0xFF3A3020), Offset(cx - px * 10, h * 0.25f), Size(px * 50, px * 2))
    val hookY = h * 0.35f + sin(t * 0.8f) * px * 6
    drawLine(Color(0xFF6A6050), Offset(cx + px * 34, h * 0.25f), Offset(cx + px * 34, hookY), px * 0.6f)
    drawRect(Color(0xFFB08A2A), Offset(cx + px * 30, hookY), Size(px * 8, px * 5))
    // dişliler
    gear(Offset(w * 0.12f, h * 0.78f), h * 0.12f, t * 40f, Color(0xFF5A4A30))
    gear(Offset(w * 0.12f + h * 0.2f, h * 0.84f), h * 0.08f, -t * 60f, Color(0xFF6A5A38))
    gear(Offset(w * 0.9f, h * 0.8f), h * 0.1f, t * 50f, Color(0xFF5A4A30))
    // kaynak kıvılcımları
    val sx = w * 0.88f; val sy = h * 0.55f
    if (sin(t * 9f) > -0.3f) {
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF8CC8FF).copy(alpha = 0.6f), Color.Transparent), Offset(sx, sy), px * 8), px * 8, Offset(sx, sy))
        for (s in 0 until 10) {
            val ph = (t * 2.3f + s * 0.13f) % 1f
            val a = s * 0.63f
            drawRect(Color(0xFFFFD070).copy(alpha = 1 - ph), Offset(sx + cos(a) * ph * px * 26, sy + sin(a) * ph * px * 14 + ph * ph * px * 30), Size(px * 0.8f, px * 0.8f))
        }
    }
    // uyarı lambaları
    for ((lx, ph) in listOf(w * 0.35f to 0f, w * 0.65f to PI.toFloat())) {
        val on = sin(t * 4f + ph) > 0
        drawCircle(if (on) Color(0xFFFF8A20) else Color(0xFF4A2A10), px * 3, Offset(lx, h * 0.92f))
        if (on) drawCircle(Brush.radialGradient(listOf(Color(0x88FF8A20), Color.Transparent), Offset(lx, h * 0.92f), px * 22), px * 22, Offset(lx, h * 0.92f))
    }
    // şerit
    var bx = -((t * 40f) % (px * 16))
    while (bx < w) { drawRect(Color(0xFFE8C030), Offset(bx, h - px * 4), Size(px * 8, px * 4)); drawRect(Color(0xFF1A1A1A), Offset(bx + px * 8, h - px * 4), Size(px * 8, px * 4)); bx += px * 16 }
}

private fun DrawScope.gear(c: Offset, r: Float, deg: Float, col: Color) {
    rotate(deg, c) {
        val teeth = 10
        val p = Path()
        for (i in 0 until teeth * 2) {
            val a = i * PI.toFloat() / teeth
            val rr = if (i % 2 == 0) r else r * 0.82f
            val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, col)
        drawCircle(Color(0xFF0C0E16), r * 0.3f, c)
    }
}

/** Yasak ekranı: tarih ve neden. */
@Composable
fun BanScreen(info: String) {
    val parts = info.split("|")
    Box(Modifier.fillMaxSize().background(Color(0xF0100808)).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.width(520.dp).pxPanel(Color(0xF02A1414)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon("lock", 54.dp, Color(0xFFE05040))
            PT("Hesabın Askıya Alındı", 28.sp, Color(0xFFFF6A5A), outline = true)
            Spacer(Modifier.height(8.dp))
            PT("Bitiş: ${parts.getOrElse(0) { "" }}", 16.sp, Px.Text)
            if (parts.getOrElse(1) { "" }.isNotBlank()) PT("Neden: ${parts[1]}", 15.sp, Px.Sub, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            PT("Hata olduğunu düşünüyorsan Ayarlar → Destek bölümünden bize yaz.", 12.sp, Px.Sub, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            PxButton("Tekrar Dene", height = 44.dp, size = 16.sp, icon = "refresh") { Account.refresh() }
        }
    }
}
