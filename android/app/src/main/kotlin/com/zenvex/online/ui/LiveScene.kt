package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.zenvex.online.game.Settings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Sahne teması: gökyüzü, ışık ve yangın renkleri */
enum class SceneKind(
    val skyTop: Long, val skyMid: Long, val horizon: Long, val sun: Long, val fire: Long,
    val far: Long, val mid: Long, val near: Long, val fog: Long,
) {
    /** yıkık şehir, gün batımı */
    LOBBY(0xFF0A0720, 0xFF3B1447, 0xFFFF7B3A, 0xFFFFC27A, 0xFFFF8A2A, 0xFF4A2656, 0xFF2A1634, 0xFF120A1A, 0xFFB5527A),
    /** kor kızıl gece, yanık bölge */
    EMBER(0xFF120508, 0xFF4A0E12, 0xFFFF4A24, 0xFFFFA060, 0xFFFF5A1E, 0xFF5A1C1E, 0xFF331012, 0xFF140708, 0xFFC03A2A),
    /** soğuk mavi gece, liman */
    ICE(0xFF040A18, 0xFF10284A, 0xFF4AA8D8, 0xFFDCF4FF, 0xFF7AD0FF, 0xFF1E3A5C, 0xFF12263E, 0xFF081018, 0xFF5A9ACB),
    /** mor-altın kasa/mağaza ışığı */
    VAULT(0xFF08060F, 0xFF241338, 0xFFB070FF, 0xFFFFE0A0, 0xFFFFC850, 0xFF352450, 0xFF1D1330, 0xFF0C0814, 0xFF8A5ACB),
}

/** Saniye cinsinden zamanı bir State olarak tutar: yalnız çizim aşaması okur, kompozisyon yeniden kurulmaz. */
@Composable
fun rememberTimeState(): androidx.compose.runtime.MutableFloatState {
    val s = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) androidx.compose.runtime.withFrameNanos { now -> s.floatValue = (now - start) / 1e9f }
    }
    return s
}

private class Bld(val x: Float, val w: Float, val h: Float, val steps: Int, val ant: Boolean, val seed: Int, val tilt: Float)

private class Skyline(val layer: Int, val blds: List<Bld>)

/**
 * Kodla çizilen canlı sahne: yıkık şehir silueti, dönen bulutlar, titreyen pencereler, yangınlar ve duman sütunları, kor
 * yağmuru, uzaktaki zombi silüetleri ve ön planda kamp ateşi. Hepsi zamandan türetilir (parçacık durumu tutulmaz).
 * Efekt kalitesi ayarı kor sayısını, ışık huzmelerini ve duman katmanlarını azaltır.
 */
@Composable
fun LiveScene(
    kind: SceneKind = SceneKind.LOBBY, modifier: Modifier = Modifier.fillMaxSize(), dim: Float = 0f, still: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val ts = rememberTimeState()
    val world = remember(kind) { buildWorld(kind) }
    val fx = Settings.current.fx
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val t = still ?: ts.floatValue
            drawScene(world, kind, t, fx)
            if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        }
        content()
    }
}

private class World(val stars: List<FloatArray>, val skylines: List<Skyline>, val fires: List<FloatArray>, val embers: List<FloatArray>, val walkers: List<FloatArray>, val clouds: List<FloatArray>)

private fun buildWorld(kind: SceneKind): World {
    val r = Random(kind.ordinal * 31 + 5)
    val stars = List(110) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.55f, 0.4f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f) }
    fun layer(i: Int, n: Int, hMin: Float, hMax: Float, wMin: Float, wMax: Float): Skyline {
        var x = -0.12f
        val l = ArrayList<Bld>()
        repeat(n) {
            val w = wMin + r.nextFloat() * (wMax - wMin)
            l += Bld(x, w, hMin + r.nextFloat() * (hMax - hMin), 2 + r.nextInt(4), r.nextFloat() < 0.4f, r.nextInt(1000), (r.nextFloat() - 0.5f) * 0.08f)
            x += w * (0.78f + r.nextFloat() * 0.3f)
        }
        return Skyline(i, l)
    }
    val skylines = listOf(layer(0, 26, 0.10f, 0.30f, 0.035f, 0.075f), layer(1, 20, 0.14f, 0.40f, 0.05f, 0.10f), layer(2, 14, 0.18f, 0.50f, 0.07f, 0.14f))
    // yangınlar: orta katmanın çatıları (x, katman, ölçek, faz)
    val fires = listOf(floatArrayOf(0.22f, 1f, 1f, 0f), floatArrayOf(0.51f, 1f, 1.3f, 2.1f), floatArrayOf(0.74f, 1f, 0.9f, 4.4f), floatArrayOf(0.36f, 0f, 0.7f, 1.3f), floatArrayOf(0.88f, 0f, 0.8f, 3.3f))
    val embers = List(90) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.0f, r.nextFloat() * 6.28f, r.nextFloat()) }
    val walkers = List(7) { floatArrayOf(r.nextFloat(), 0.7f + r.nextFloat() * 0.6f, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 0.5f) }
    val clouds = List(9) { floatArrayOf(r.nextFloat(), 0.08f + r.nextFloat() * 0.36f, 0.25f + r.nextFloat() * 0.35f, 0.02f + r.nextFloat() * 0.04f, 0.04f + r.nextFloat() * 0.05f) }
    return World(stars, skylines, fires, embers, walkers, clouds)
}

private fun rg(colors: List<Color>, center: Offset, r: Float): Brush = Brush.radialGradient(colors, center, if (r.isFinite()) max(r, 1f) else 1f)

private fun c(v: Long, a: Float = 1f) = Color(v).copy(alpha = a)

private fun mix(a: Color, b: Color, k: Float) = Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k)

private fun DrawScope.drawScene(w: World, k: SceneKind, t: Float, fx: Int) {
    val W = size.width; val H = size.height
    if (W < 16f || H < 16f) return
    val horizonY = H * 0.66f
    // yıldırım / uzak patlama parlaması: ~11 sn'de bir kısa
    val fl = (sin(t * 0.57f) * sin(t * 1.31f + 0.6f)).let { if (it > 0.985f) (it - 0.985f) / 0.015f else 0f }
    val breath = 0.5f + 0.5f * sin(t * 0.21f)

    // ---- gökyüzü
    drawRect(Brush.verticalGradient(
        0f to c(k.skyTop), 0.45f to mix(c(k.skyMid), c(k.horizon), 0.1f * breath), 0.72f to mix(c(k.skyMid), c(k.horizon), 0.55f + 0.1f * breath), 1f to c(k.horizon, 1f),
        startY = 0f, endY = horizonY + H * 0.088f,
    ), size = Size(W, H))
    if (fl > 0f) drawRect(Color.White.copy(alpha = 0.18f * fl), size = Size(W, horizonY))
    // yıldızlar (üst gökyüzü, titrer)
    for (s in w.stars) {
        val a = (0.25f + 0.75f * (0.5f + 0.5f * sin(t * s[2] * 1.6f + s[3]))) * (1f - s[1] / 0.6f).coerceIn(0f, 1f) * (if (k == SceneKind.LOBBY) 0.55f else 0.9f)
        drawCircle(Color.White.copy(alpha = a), (0.6f + s[2] * 0.9f) * density, Offset(s[0] * W, s[1] * H))
    }

    // ---- güneş/ay + hale
    val sunX = W * 0.70f + sin(t * 0.04f) * W * 0.01f; val sunY = horizonY - H * 0.25f
    val sunR = H * 0.085f
    drawCircle(rg(listOf(c(k.sun, 0.55f), c(k.sun, 0.18f), Color.Transparent), Offset(sunX, sunY), H * 0.55f), H * 0.55f, Offset(sunX, sunY))
    drawCircle(rg(listOf(c(k.sun, 1f), c(k.sun, 0.9f), c(k.sun, 0f)), Offset(sunX, sunY), sunR * 1.6f), sunR * 1.6f, Offset(sunX, sunY))

    // ---- bulutlar: geniş, yassı, yavaş akan şeritler (üst yüzü güneşle aydınlık)
    for (cl in w.clouds) {
        val x = ((cl[0] + t * cl[3] * 0.1f) % 1.4f) - 0.2f
        val y = horizonY * (0.15f + cl[1]) * 1.2f
        val rw = W * (0.18f + cl[4] * 2.4f); val rh = H * cl[2] * 0.09f
        val cx = x * W
        drawOval(Brush.verticalGradient(listOf(mix(c(k.skyMid), c(k.sun), 0.35f).copy(alpha = 0.55f), c(k.skyTop, 0.45f)), y - rh, y + rh),
            Offset(cx - rw, y - rh), Size(rw * 2f, rh * 2f))
    }

    // ---- ışık huzmeleri (güneşten, yavaş nabız)
    if (fx >= 2) for (i in 0 until 5) {
        val a0 = (-0.55f + i * 0.28f) + sin(t * 0.13f + i) * 0.04f
        val p = Path().apply {
            moveTo(sunX, sunY)
            lineTo(sunX + cos(PI.toFloat() / 2f + a0 - 0.06f) * H * 1.6f, sunY + sin(PI.toFloat() / 2f + a0 - 0.06f) * H * 1.6f)
            lineTo(sunX + cos(PI.toFloat() / 2f + a0 + 0.06f) * H * 1.6f, sunY + sin(PI.toFloat() / 2f + a0 + 0.06f) * H * 1.6f)
            close()
        }
        drawPath(p, rg(listOf(c(k.sun, 0.26f + 0.08f * sin(t * 0.4f + i)), Color.Transparent), Offset(sunX, sunY), H * 1.2f), blendMode = BlendMode.Plus)
    }

    // ---- şehir katmanları (uzaktan yakına): siluet + titreyen pencereler + sis
    val par = sin(t * 0.045f)
    for (sl in w.skylines) {
        val depth = sl.layer
        val col = when (depth) { 0 -> c(k.far); 1 -> c(k.mid); else -> c(k.near) }
        val baseY = horizonY + H * (0.012f + 0.03f * depth) + (if (depth == 2) H * 0.015f else 0f)
        val offX = par * W * (0.006f + 0.012f * depth)
        for (b in sl.blds) {
            val bx = b.x * W + offX; val bw = b.w * W; val bh = b.h * H * (0.62f + 0.22f * depth)
            val top = baseY - bh
            val p = Path().apply {
                moveTo(bx, baseY)
                lineTo(bx, top + bh * 0.05f)
                // kırık çatı: basamaklı, eğik
                var cx = bx
                val stepW = bw / b.steps
                for (s in 0 until b.steps) {
                    val hh = top + bh * (0.04f * ((b.seed + s * 7) % 5))
                    lineTo(cx, hh); lineTo(cx + stepW, hh + bw * b.tilt * (if (s % 2 == 0) 1f else -1f))
                    cx += stepW
                }
                lineTo(bx + bw, baseY)
                close()
            }
            drawPath(p, col)
            if (depth >= 1) {
                val rimX = if (bx + bw * 0.5f < sunX) bx + bw else bx
                drawLine(c(k.sun, 0.13f - 0.03f * (2 - depth)), Offset(rimX, top + bh * 0.06f), Offset(rimX, top + bh * 0.55f), 1.3f * density, blendMode = BlendMode.Plus)
            }
            if (b.ant) drawLine(col, Offset(bx + bw * 0.5f, top), Offset(bx + bw * 0.5f, top - bh * 0.07f), 1.4f * density)
            // pencereler: yakın katmanlarda; çoğu karanlık, birkaçı sıcak ışıkla titrer
            if (depth >= 1) {
                val cols = max(2, (bw / (9f * density)).toInt()); val rows = max(3, (bh / (13f * density)).toInt())
                for (ry in 0 until rows) for (cx2 in 0 until cols) {
                    val hsh = (b.seed * 31 + ry * 17 + cx2 * 53) % 100
                    if (hsh < 9) {
                        val fk = 0.55f + 0.45f * sin(t * (1.5f + hsh * 0.15f) + hsh)
                        val wx = bx + bw * (0.14f + 0.72f * (cx2 + 0.5f) / cols) - 2f * density
                        val wy = top + bh * (0.14f + 0.76f * (ry + 0.5f) / rows)
                        drawRect(c(0xFFFFC060, 0.55f * fk), Offset(wx, wy), Size(3.2f * density, 4.6f * density))
                    }
                }
            }
        }
        // katman arası sis
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, c(k.fog, 0.20f - 0.04f * depth), Color.Transparent), baseY - H * 0.16f, baseY + H * 0.03f),
            Offset(0f, baseY - H * 0.16f), Size(W, H * 0.19f))
        // yangınlar bu katmanda
        for (f in w.fires) if (f[1].toInt() == depth) drawFire(k, f, t, W, H, baseY - H * 0.14f * (0.4f + depth * 0.5f) + offX * 0f, offX, fx)
    }

    // ---- sokak zemini ve ön plan
    val groundTop = horizonY + H * 0.088f
    drawRect(Brush.verticalGradient(listOf(c(k.near), mix(c(k.near), Color.Black, 0.5f)), groundTop, H), Offset(0f, groundTop), Size(W, H - groundTop))
    // zemin: ufka yaklaşırken sisle karışır, perspektif yol çizgileri ve ateşin yansıması
    drawRect(Brush.verticalGradient(listOf(c(k.fog, 0.30f), Color.Transparent), groundTop, groundTop + H * 0.10f), Offset(0f, groundTop), Size(W, H * 0.10f))
    val vx = W * 0.52f
    for (i in -6..6) {
        val bx2 = vx + i * W * 0.17f
        drawLine(Color.White.copy(alpha = 0.035f), Offset(vx + i * W * 0.012f, groundTop), Offset(bx2, H), 1.2f * density)
    }
    for (i in 0 until 7) {
        val ph = ((i / 7f + t * 0.0f) % 1f)
        val yy = groundTop + (H - groundTop) * ph * ph
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(0f, yy), Offset(W, yy), 1f * density)
    }
    // sokak lambası: ön planda sağ üstte titreyen ampul
    val lampX = W * 0.58f; val lampTop = groundTop - H * 0.02f
    drawLine(Color(0xFF07050A), Offset(lampX, groundTop + H * 0.08f), Offset(lampX, lampTop - H * 0.16f), 3.2f * density)
    drawLine(Color(0xFF07050A), Offset(lampX, lampTop - H * 0.16f), Offset(lampX + W * 0.018f, lampTop - H * 0.16f), 2.6f * density)
    val lf = (if (sin(t * 23f) * sin(t * 4.1f) > 0.93f) 0.2f else 1f)
    drawCircle(rg(listOf(c(0xFFFFE0A0, 0.65f * lf), Color.Transparent), Offset(lampX + W * 0.018f, lampTop - H * 0.15f), H * 0.12f), H * 0.12f,
        Offset(lampX + W * 0.018f, lampTop - H * 0.15f), blendMode = BlendMode.Plus)

    // uzaktaki zombi silüetleri: sokakta yavaş sürünür
    for (z in w.walkers) {
        val span = 1.3f
        val x = ((z[0] + t * 0.004f * z[3]) % span) - 0.15f
        drawZombie(x * W, groundTop + H * 0.03f * z[1], H * 0.125f * z[1], t * z[3] + z[2], c(k.near))
    }

    // kamp ateşi + barikat (ön plan, sol alt)
    drawForeground(k, t, W, H, groundTop, fx)

    // ---- kor / kül yağmuru (additive)
    val n = when (fx) { 0 -> 22; 1 -> 45; 2 -> 70; else -> 90 }
    for (i in 0 until n) {
        val e = w.embers[i % w.embers.size]
        val ph = ((t * 0.05f * e[2] + e[4]) % 1f)
        val x = (e[0] + sin(t * 0.4f * e[2] + e[3]) * 0.03f + ph * 0.06f) * W
        val y = H * (1.02f - ph * 1.05f)
        val a = sin(ph * PI.toFloat()).coerceIn(0f, 1f) * (0.4f + 0.6f * (0.5f + 0.5f * sin(t * 6f * e[2] + e[3])))
        val r = (1.1f + e[2] * 1.6f) * density
        drawCircle(c(k.fire, 0.18f * a), r * 3.0f, Offset(x, y), blendMode = BlendMode.Plus)
        drawCircle(c(k.fire, 0.55f * a), r * 1.5f, Offset(x, y), blendMode = BlendMode.Plus)
        drawCircle(Color(0xFFFFF0C0).copy(alpha = a), r * 0.55f, Offset(x, y), blendMode = BlendMode.Plus)
    }
    // bokeh: büyük bulanık ışık daireleri
    if (fx >= 1) for (i in 0 until 6) {
        val ph = ((t * 0.012f + i * 0.17f) % 1f)
        val x = (0.1f + i * 0.17f + sin(t * 0.1f + i) * 0.02f) * W
        val y = H * (0.9f - ph * 0.5f)
        drawCircle(rg(listOf(c(k.fire, 0.10f), Color.Transparent), Offset(x, y), H * 0.06f), H * 0.06f, Offset(x, y), blendMode = BlendMode.Plus)
    }

    // ---- vinyet
    drawRect(rg(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(W * 0.5f, H * 0.5f), max(W, H) * 0.72f), size = Size(W, H))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), 0f, H * 0.2f), size = Size(W, H * 0.2f))
}

private fun DrawScope.drawFire(k: SceneKind, f: FloatArray, t: Float, W: Float, H: Float, y0: Float, offX: Float, fx: Int) {
    val x = f[0] * W + offX
    val s = f[2] * H * 0.10f
    val y = y0
    // ısı hâlesi
    drawCircle(rg(listOf(c(k.fire, 0.55f), c(k.fire, 0.12f), Color.Transparent), Offset(x, y), s * 4.2f), s * 4.2f, Offset(x, y), blendMode = BlendMode.Plus)
    // duman sütunu: yükselen, genişleyen, soluk yumuşak daireler
    val puffs = when (fx) { 0 -> 5; 1 -> 8; else -> 12 }
    for (i in 0 until puffs) {
        val ph = ((t * 0.09f + i / puffs.toFloat() + f[3] * 0.13f) % 1f)
        val px = x + sin(ph * 5f + f[3] + i) * s * 0.9f + ph * s * 3.2f
        val py = y - ph * H * 0.34f
        val pr = s * (0.5f + ph * 2.6f)
        val a = (sin(ph * PI.toFloat()) * 0.30f)
        drawCircle(rg(listOf(Color(0xFF2A2026).copy(alpha = a), Color.Transparent), Offset(px, py), pr), pr, Offset(px, py))
    }
    // alev dilleri
    for (i in 0 until 5) {
        val ph = t * (2.4f + i * 0.5f) + f[3] + i * 1.7f
        val fw = s * (0.34f - i * 0.04f); val fh = s * (1.0f + 0.35f * sin(ph) + 0.15f * i)
        val dx = (i - 2) * s * 0.28f + sin(ph * 0.7f) * s * 0.10f
        flame(x + dx, y, fw, fh, sin(ph * 1.3f) * fw * 0.7f, k.fire)
    }
    drawCircle(Color(0xFFFFE8A0).copy(alpha = 0.7f), s * 0.18f, Offset(x, y - s * 0.12f), blendMode = BlendMode.Plus)
}

private fun DrawScope.flame(x: Float, y: Float, w: Float, h: Float, sway: Float, color: Long) {
    val p = Path().apply {
        moveTo(x - w, y)
        cubicTo(x - w * 1.1f, y - h * 0.45f, x - w * 0.2f + sway * 0.4f, y - h * 0.7f, x + sway, y - h)
        cubicTo(x + w * 0.3f + sway * 0.5f, y - h * 0.62f, x + w * 1.1f, y - h * 0.4f, x + w, y)
        close()
    }
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF0B0).copy(alpha = 0.95f), c(color, 0.85f), c(color, 0f)), y - h, y), blendMode = BlendMode.Plus)
}

private fun DrawScope.drawZombie(x: Float, y: Float, hgt: Float, ph: Float, col: Color) {
    // sürünen insan silüeti: eğik gövde, öne uzanmış kollar, sallanan adım
    val bob = sin(ph * 3f) * hgt * 0.02f
    val lean = hgt * 0.10f
    val sw = sin(ph * 3f) * hgt * 0.10f
    val bodyTop = y - hgt * 0.78f + bob
    // bacaklar
    drawLine(col, Offset(x - lean * 0.2f, y - hgt * 0.38f), Offset(x - hgt * 0.07f + sw, y), hgt * 0.07f)
    drawLine(col, Offset(x - lean * 0.2f, y - hgt * 0.38f), Offset(x + hgt * 0.07f - sw, y), hgt * 0.07f)
    // gövde
    drawLine(col, Offset(x - lean * 0.2f, y - hgt * 0.38f), Offset(x + lean, bodyTop + hgt * 0.1f), hgt * 0.16f)
    // baş
    drawCircle(col, hgt * 0.085f, Offset(x + lean * 1.4f, bodyTop))
    // kollar öne
    drawLine(col, Offset(x + lean * 0.8f, bodyTop + hgt * 0.16f), Offset(x + hgt * 0.30f, bodyTop + hgt * 0.20f + sw * 0.4f), hgt * 0.05f)
    drawLine(col, Offset(x + lean * 0.6f, bodyTop + hgt * 0.20f), Offset(x + hgt * 0.27f, bodyTop + hgt * 0.30f - sw * 0.4f), hgt * 0.05f)
}

private fun DrawScope.drawForeground(k: SceneKind, t: Float, W: Float, H: Float, groundTop: Float, fx: Int) {
    val dark = Color(0xFF07050A)
    // barikat: eğik tahta ve çuvallar (sağ alt), araba enkazı silueti (sol)
    val bx = W * 0.80f; val by = H * 0.98f
    for (i in 0 until 6) {
        val ang = (-0.9f + i * 0.32f)
        val len = H * (0.22f + 0.04f * ((i * 5) % 3))
        drawLine(dark, Offset(bx + i * W * 0.025f, by), Offset(bx + i * W * 0.025f + sin(ang) * len, by - cos(ang) * len), H * 0.012f)
    }
    drawRoundRect(dark, Offset(W * 0.74f, H * 0.90f), Size(W * 0.2f, H * 0.12f), CornerRadius(H * 0.02f))
    // araba enkazı
    val cx = W * 0.07f; val cyy = groundTop + H * 0.12f
    val car = Path().apply {
        moveTo(cx, cyy); lineTo(cx, cyy - H * 0.06f); lineTo(cx + W * 0.025f, cyy - H * 0.10f); lineTo(cx + W * 0.085f, cyy - H * 0.11f)
        lineTo(cx + W * 0.115f, cyy - H * 0.07f); lineTo(cx + W * 0.16f, cyy - H * 0.06f); lineTo(cx + W * 0.16f, cyy); close()
    }
    drawPath(car, dark)
    drawCircle(dark, H * 0.035f, Offset(cx + W * 0.035f, cyy)); drawCircle(dark, H * 0.035f, Offset(cx + W * 0.125f, cyy))
    // kamp ateşi (alt orta-sol): alev dilleri + kor + ışık
    val fx0 = W * 0.30f; val fy0 = H * 0.93f
    val flick = 0.82f + 0.18f * sin(t * 13f) * sin(t * 7.3f)
    drawCircle(rg(listOf(c(k.fire, 0.50f * flick), c(k.fire, 0.14f), Color.Transparent), Offset(fx0, fy0 - H * 0.03f), H * 0.5f), H * 0.5f, Offset(fx0, fy0 - H * 0.03f), blendMode = BlendMode.Plus)
    // odun
    drawLine(dark, Offset(fx0 - W * 0.04f, fy0 + H * 0.015f), Offset(fx0 + W * 0.04f, fy0 - H * 0.005f), H * 0.018f)
    drawLine(dark, Offset(fx0 - W * 0.04f, fy0 - H * 0.005f), Offset(fx0 + W * 0.04f, fy0 + H * 0.015f), H * 0.018f)
    val tongues = if (fx >= 1) 7 else 4
    for (i in 0 until tongues) {
        val ph = t * (3.0f + i * 0.7f) + i * 2.3f
        val fw = H * (0.030f - abs(i - tongues / 2) * 0.003f); val fh = H * (0.15f + 0.04f * sin(ph) - abs(i - tongues / 2) * 0.012f)
        flame(fx0 + (i - tongues / 2) * W * 0.012f + sin(ph * 0.6f) * W * 0.004f, fy0, fw, fh, sin(ph * 1.2f) * fw * 0.8f, k.fire)
    }
    drawCircle(Color(0xFFFFE8A0).copy(alpha = 0.6f * flick), H * 0.012f, Offset(fx0, fy0 - H * 0.012f), blendMode = BlendMode.Plus)
    // yakın ışık ön plan siluetlerini turuncu kenarla yıkar
    drawRect(rg(listOf(c(k.fire, 0.20f * flick), Color.Transparent), Offset(fx0, fy0), H * 0.7f), Offset(0f, groundTop), Size(W, H - groundTop), blendMode = BlendMode.Plus)
}

/** Kodla çizilen oyun logosu: tek satır metalik "Zenvex Online", altında nabız gibi atan kor çizgisi (görsel dosyası gerekmez). */
@Composable
fun CodeLogo(height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val ts = rememberTimeState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val face = remember { runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, com.zenvex.online.R.font.display_bold) }.getOrNull() }
    val ratio = 3.1f
    Canvas(modifier.then(Modifier.height(height).width(height * ratio))) {
        val W = size.width; val H = size.height
        val t = ts.floatValue
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.typeface = face ?: android.graphics.Typeface.DEFAULT_BOLD
            p.textAlign = android.graphics.Paint.Align.CENTER
            p.letterSpacing = 0.02f
            val text = "Zenvex Online"
            // genişliğe sığacak boy (en çok yüksekliğin %52'si)
            p.textSize = H * 0.52f
            val tw = p.measureText(text)
            if (tw > W * 0.96f) p.textSize *= W * 0.96f / tw
            val tx = W / 2f; val ty = H * 0.60f
            // sıcak yayılan parıltı
            p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = H * 0.08f; p.color = 0x40FF7A2A.toInt()
            p.maskFilter = android.graphics.BlurMaskFilter(H * 0.08f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            n.drawText(text, tx, ty, p)
            p.maskFilter = null
            // koyu dış hat
            p.strokeWidth = H * 0.05f; p.color = 0xFF150A06.toInt(); p.strokeJoin = android.graphics.Paint.Join.ROUND
            n.drawText(text, tx, ty + H * 0.012f, p)
            // metalik dolgu: üst parlak → alt paslı
            p.style = android.graphics.Paint.Style.FILL
            p.shader = android.graphics.LinearGradient(0f, ty - p.textSize * 0.8f, 0f, ty, intArrayOf(0xFFFFF4DC.toInt(), 0xFFD8D2C4.toInt(), 0xFF8A7F74.toInt(), 0xFFB05A28.toInt()), floatArrayOf(0f, 0.38f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP)
            n.drawText(text, tx, ty, p)
            p.shader = null
            // kor çizgisi (nabız atar)
            val pulse = 0.65f + 0.35f * sin(t * 2.2f)
            p.color = android.graphics.Color.argb((255 * pulse).toInt(), 255, 110, 40); p.strokeWidth = H * 0.018f; p.style = android.graphics.Paint.Style.STROKE
            n.drawLine(W * 0.12f, ty + H * 0.12f, W * 0.88f, ty + H * 0.12f, p)
        }
    }
}
