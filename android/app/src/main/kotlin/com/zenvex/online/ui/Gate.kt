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

    /** Sunucuya ulaşılamıyor: oyun tamamen çevrimiçi olduğu için hiçbir ekran açılmaz (bakım ekranı da atlanamaz). */
    val offline = androidx.compose.runtime.mutableStateOf(false)

    fun poll() = Account.status { r ->
        r.onSuccess { m -> offline.value = false; maintenance.value = if (m.optBoolean("on")) m else null }
            .onFailure { e -> if (e.message.orEmpty().contains("ulaşılamadı")) offline.value = true }
    }
}

@Composable
fun GateHost() {
    LaunchedEffect(Unit) { while (true) { Gate.poll(); delay(if (Gate.offline.value) 6_000 else 45_000) } }
    val ban = Account.banned.value
    val m = Gate.maintenance.value
    when {
        ban != null -> BanScreen(ban)
        Gate.offline.value && !Demo.on -> NoConnectionScreen()
        m != null && !Account.admin.value -> MaintenanceScreen(m.optString("msg"))
    }
}

/** İnternet yok: oyun çevrimiçi olduğu için her şeyin üstünde kalır; bağlantı gelince kendiliğinden kapanır. */
@Composable
fun NoConnectionScreen() {
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Color(0xFF0C0E16)).clickable(remember { MutableInteractionSource() }, null) {}) {
        SceneBackground("bg_lobby", t, dim = 0.6f)
        Column(Modifier.align(Alignment.Center).metalFrame().padding(horizontal = 30.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon("radio", 44.dp, Px.Orange.copy(alpha = 0.5f + 0.5f * kotlin.math.sin(t * 4f)))
            Spacer(Modifier.height(6.dp))
            PT("Bağlantı Yok", 32.sp, Px.Orange, outline = true)
            Spacer(Modifier.height(6.dp))
            PT("Zenvex Online tamamen çevrimiçi oynanır. İnternetini kontrol et; bağlantı gelince devam edeceksin.", 15.sp, Px.Text, maxLines = 3)
            Spacer(Modifier.height(14.dp))
            PxButton("Tekrar Dene", height = 46.dp, size = 17.sp, icon = "refresh") { Gate.poll() }
        }
    }
}

/** Bakım ekranı: gece şehri, dönen dişliler, kıvılcım saçan kaynakçı, yanıp sönen uyarı lambaları. */
@Composable
fun MaintenanceScreen(msg: String) {
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Color(0xFF0C0E16)).clickable(remember { MutableInteractionSource() }, null) {}) {
        // gece şantiyesi görseli + kaynak kıvılcımı, yanıp sönen ikaz lambaları
        SceneBackground("bg_maint", null, dim = 0.15f)
        Canvas(Modifier.fillMaxSize()) { maintenanceFx(t) }
        Column(Modifier.align(Alignment.Center).padding(top = 40.dp).metalFrame().padding(horizontal = 30.dp, vertical = 26.dp),
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

/** Bakım görselinin üstüne canlılık: görsel koordinatlarında (478x267) kaynak ışığı, kıvılcımlar, ikaz lambaları. */
private fun DrawScope.maintenanceFx(t: Float) {
    val iw = 478f; val ih = 267f
    val k = maxOf(size.width / iw, size.height / ih) * 1.06f
    val ox = (size.width - iw * k) / 2; val oy = (size.height - ih * k) / 2
    fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
    // kaynak: düzensiz titreyen mavi-beyaz ışık ve sıçrayan kıvılcımlar
    val fl = (0.55f + 0.45f * sin(t * 31f) * sin(t * 7.3f + 1f)).coerceIn(0f, 1f)
    val w = p(73f, 162f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFBFE8FF).copy(alpha = 0.55f * fl), Color.Transparent), w, 42f * k), 42f * k, w)
    for (n in 0 until 14) {
        val ph = (t * 1.7f + n * 0.37f) % 1f
        val a = (n * 2.39f) + (t * 0.2f)
        val d = ph * (18f + (n % 5) * 5f)
        val sp = p(73f + cos(a) * d, 162f + sin(a) * d * 0.6f + ph * ph * 22f)
        drawRect(Color(0xFFFFF2B0).copy(alpha = (1f - ph) * fl), sp, Size(k * 0.9f, k * 0.9f))
    }
    // ikaz lambaları (vinç ve bariyerler)
    // sağdaki tezgâhta ikinci kaynakçı (daha sönük)
    val fl2 = (0.5f + 0.5f * sin(t * 23f + 2f) * sin(t * 5.1f)).coerceIn(0f, 1f)
    val w2 = p(355f, 203f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9FD8FF).copy(alpha = 0.4f * fl2), Color.Transparent), w2, 22f * k), 22f * k, w2)
    val lamps = listOf(361f to 7f, 431f to 90f, 150f to 128f, 418f to 203f, 434f to 221f, 470f to 221f,
        452f to 89f, 24f to 49f, 27f to 118f, 16f to 222f, 51f to 219f, 397f to 202f, 463f to 179f)
    for ((i, xy) in lamps.withIndex()) {
        val on = sin(t * 3.2f + i * 1.7f) > 0.2f
        if (!on) continue
        val c = p(xy.first, xy.second)
        val col = if (i < 6) Color(0xFFFF3A2A) else Color(0xFFFFB02A)
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f), Color.Transparent), c, 9f * k), 9f * k, c)
    }
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
