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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zenvex.online.game.Model3D
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
fun rememberTimeState(minStep: Float = 0f): androidx.compose.runtime.MutableFloatState {
    val s = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) androidx.compose.runtime.withFrameNanos { now ->
            val t = (now - start) / 1e9f
            // minStep > 0: en çok 1/minStep sıklığında güncellenir (lobi sahnesi 40 kare/sn: göz fark etmez, cihaz daha az ısınır)
            if (t - s.floatValue >= minStep) s.floatValue = t
        }
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
    val ts = rememberTimeState(1f / 40f)
    val world = remember(kind) { buildWorld(kind) }
    val fx = Settings.current.fx
    val ctx = LocalContext.current
    // GPU gölgelendiricileri (Android 13+); en düşük efekt ayarında ve eski cihazlarda yol çizimi
    val shaders = remember { SceneShaders.create() }
    // oyunun kendi zombileri (yan görünüm yürüme kareleri; sola, kamp ateşine doğru yürürler)
    val zombies = remember {
        SCENE_ZOMBIES.map { (id, _) -> (0 until 8).mapNotNull { runCatching { SpriteCache.get(ctx, "z/$id/walk_0_$it") }.getOrNull() } }
    }
    // oyunun kendi arabası (Hellhound, 3B model; hazır olunca belirir)
    var car by remember { mutableStateOf<SceneCar?>(null) }
    LaunchedEffect(Unit) {
        if (!Model3D.sync) runCatching { Model3D.init(ctx) }
        repeat(120) {
            val f = Model3D.vehicle("scene_car", "hellhound", "", 112f, 0f, 0f, 0f, 0f, 70f, 3)
            if (f != null) { car = SceneCar(f.bmp.asImageBitmap(), f.ax, f.ay, f.pxPerM); return@LaunchedEffect }
            kotlinx.coroutines.delay(120)
        }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val t = still ?: ts.floatValue
            drawScene(world, kind, t, fx, zombies, car, if (fx >= 1) shaders else null)
            if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        }
        content()
    }
}

/** çatı yangınının alev dilleri: (yatay kayma, yarı genişlik, yükseklik, zaman hızı) — alev boyu biriminde */
private val FIRE_TONGUES = listOf(floatArrayOf(0f, 1.15f, 3.0f, 1f), floatArrayOf(-0.95f, 0.7f, 1.8f, 1.2f), floatArrayOf(0.9f, 0.6f, 1.5f, 1.35f))

/** bir yürüyüş turunda alınan yol (zombi boyu cinsinden): hız = tur sıklığı × bu × boy */
private const val STRIDE_PER_HEIGHT = 0.46f

/** sahnedeki zombi türleri (atlas önekleri) ve boy çarpanı */
private val SCENE_ZOMBIES = listOf("walker" to 1f, "runner" to 1.02f, "tank" to 1.22f, "dark" to 1f, "dog" to 0.62f, "walker" to 1f, "runner" to 1.02f)

/** 3B arabanın sahne karesi: görsel + ayak çapası + kare ölçeği (metre başına piksel) */
private class SceneCar(val img: ImageBitmap, val ax: Float, val ay: Float, val pxPerM: Float)

private class World(val stars: List<FloatArray>, val skylines: List<Skyline>, val fires: List<FloatArray>, val embers: List<FloatArray>, val walkers: List<FloatArray>, val clouds: List<FloatArray>) {
    /** ekran boyutuna göre bir kez pişirilen durağan katmanlar (binalar, sokak); her karede yalnız canlı ışıklar çizilir */
    var baked: Baked? = null
}

/** pişmiş şehir katmanı: görsel (iki yanda pad taşmalı), ekrandaki üst kenarı, bina kırpma yolu ve canlı ışıklar */
private class BakedLayer(val img: ImageBitmap, val top: Float, val pad: Float, val clip: Path, val lights: List<FloatArray>)

/** pişmiş sahne: katmanlar, sokak görseli, her yangının çatı yüksekliği ve su birikintileri (x, y, yarıçapX, yarıçapY) */
private class Baked(val w: Int, val h: Int, val layers: List<BakedLayer>, val ground: ImageBitmap, val groundTop: Float, val fireY: FloatArray, val puddles: List<FloatArray>)

private const val L_WINDOW = 0
private const val L_BURN = 1
private const val L_TV = 2
private const val L_BEACON = 3

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
    // yangınlar: çatılarda (x, katman, ölçek, faz)
    val fires = listOf(floatArrayOf(0.22f, 1f, 1f, 0f), floatArrayOf(0.51f, 1f, 1.3f, 2.1f), floatArrayOf(0.74f, 1f, 0.9f, 4.4f), floatArrayOf(0.36f, 0f, 0.7f, 1.3f), floatArrayOf(0.88f, 0f, 0.8f, 3.3f))
    val embers = List(90) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.0f, r.nextFloat() * 6.28f, r.nextFloat()) }
    val walkers = List(7) { floatArrayOf(r.nextFloat(), 0.7f + r.nextFloat() * 0.6f, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 0.5f) }
    val clouds = List(9) { floatArrayOf(r.nextFloat(), 0.08f + r.nextFloat() * 0.36f, 0.25f + r.nextFloat() * 0.35f, 0.02f + r.nextFloat() * 0.04f, 0.04f + r.nextFloat() * 0.05f) }
    return World(stars, skylines, fires, embers, walkers, clouds)
}

private fun rg(colors: List<Color>, center: Offset, r: Float): Brush = Brush.radialGradient(colors, center, if (r.isFinite()) max(r, 1f) else 1f)

private fun c(v: Long, a: Float = 1f) = Color(v).copy(alpha = a)

private fun mix(a: Color, b: Color, k: Float) = Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k)

private fun layerBaseY(depth: Int, H: Float) = H * 0.66f + H * (0.012f + 0.03f * depth) + (if (depth == 2) H * 0.015f else 0f)

private fun bldH(b: Bld, depth: Int, H: Float) = b.h * H * (0.62f + 0.22f * depth)

/** kırık çatının s. basamağının yüksekliği */
private fun roofY(b: Bld, s: Int, top: Float, bh: Float) = top + bh * (0.04f * ((b.seed + s * 7) % 5))

private fun bldPath(b: Bld, bx: Float, bw: Float, top: Float, bh: Float, baseY: Float) = Path().apply {
    moveTo(bx, baseY)
    lineTo(bx, top + bh * 0.05f)
    // kırık çatı: basamaklı, eğik
    var cx = bx
    val stepW = bw / b.steps
    for (s in 0 until b.steps) {
        val hh = roofY(b, s, top, bh)
        lineTo(cx, hh); lineTo(cx + stepW, hh + bw * b.tilt * (if (s % 2 == 0) 1f else -1f))
        cx += stepW
    }
    lineTo(bx + bw, baseY)
    close()
}

private fun DrawScope.bakeInto(w: Int, h: Int, block: DrawScope.() -> Unit): ImageBitmap {
    val img = ImageBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat()), block)
    return img
}

/** Durağan katmanları ekran boyutunda bir kez çizer: cepheler, katlar, pencereler, çatı eşyaları, asfalt ve birikintiler. */
private fun DrawScope.bakeWorld(w: World, k: SceneKind, W: Float, H: Float): Baked {
    val sunX = W * 0.70f
    val pad = W * 0.05f
    val iw = (W + pad * 2).toInt()
    val fireY = FloatArray(w.fires.size)
    val layers = w.skylines.map { sl ->
        val depth = sl.layer
        val baseY = layerBaseY(depth, H)
        val top = baseY - sl.blds.maxOf { bldH(it, depth, H) } * 1.15f
        // yangınların oturduğu bina: x'i kapsayan en yüksek bina, alev çatıdaki basamağa oturur
        val hosts = HashSet<Int>()
        for ((fi, f) in w.fires.withIndex()) if (f[1].toInt() == depth) {
            val x = f[0] * W
            var best = -1; var bestTop = Float.MAX_VALUE
            for ((bi, b) in sl.blds.withIndex()) {
                if (x < b.x * W + b.w * W * 0.15f || x > (b.x + b.w) * W - b.w * W * 0.15f) continue
                val tp = baseY - bldH(b, depth, H)
                if (tp < bestTop) { bestTop = tp; best = bi }
            }
            fireY[fi] = if (best < 0) baseY - H * 0.1f else {
                val b = sl.blds[best]; val bx = b.x * W; val bw = b.w * W; val bh = bldH(b, depth, H)
                val s = ((x - bx) / (bw / b.steps)).toInt().coerceIn(0, b.steps - 1)
                hosts += best
                roofY(b, s, baseY - bh, bh) + bh * 0.03f
            }
        }
        val lights = ArrayList<FloatArray>()
        val clip = Path()
        val img = bakeInto(iw, (baseY - top + H * 0.01f).toInt()) {
            translate(pad, -top) {
                for ((bi, b) in sl.blds.withIndex()) drawBuilding(b, depth, k, W, H, baseY, sunX, bi in hosts, lights, clip)
            }
        }
        BakedLayer(img, top, pad, clip, lights)
    }
    val groundTop = H * 0.66f + H * 0.088f
    val puddles = ArrayList<FloatArray>()
    val ground = bakeInto(W.toInt(), (H - groundTop).toInt() + 1) { translate(0f, -groundTop) { drawStreet(k, W, H, groundTop, puddles) } }
    return Baked(W.toInt(), H.toInt(), layers, ground, groundTop, fireY, puddles)
}

/** Tek bina: cephe ışığı, üç çeyrek yan yüz, kat döşemeleri, pencere ızgarası (karanlık cam / kırık / yanan / ışıklı), hasar, çatı eşyaları. */
private fun DrawScope.drawBuilding(b: Bld, depth: Int, k: SceneKind, W: Float, H: Float, baseY: Float, sunX: Float, burning: Boolean, lights: MutableList<FloatArray>, clip: Path) {
    val col = when (depth) { 0 -> c(k.far); 1 -> c(k.mid); else -> c(k.near) }
    val bx = b.x * W; val bw = b.w * W; val bh = bldH(b, depth, H)
    val top = baseY - bh
    val p = bldPath(b, bx, bw, top, bh, baseY)
    clip.addPath(p)
    val r = Random(b.seed * 7 + depth * 131)
    // güneş binanın solundaysa sol yan yüz aydınlık
    val sunLeft = bx + bw * 0.5f > sunX
    val floorH = H * (0.016f + 0.007f * depth)
    clipPath(p) {
        // cephe: üst kısım gökten soğuk ışık alır, taban gölgede
        drawRect(Brush.verticalGradient(0f to mix(col, c(k.fog), 0.22f - 0.05f * depth), 0.5f to col, 1f to mix(col, Color.Black, 0.32f), startY = top, endY = baseY),
            Offset(bx, top - bh * 0.1f), Size(bw, bh * 1.1f))
        // yan yüzler: güneşe bakan sıcak düzlem, karşısı gölge
        val sw = bw * (0.10f + 0.05f * depth)
        val litX = if (sunLeft) bx else bx + bw - sw
        drawRect(Brush.verticalGradient(listOf(c(k.sun, 0.20f - 0.04f * (2 - depth)), c(k.sun, 0.04f)), top, baseY), Offset(litX, top - bh * 0.1f), Size(sw, bh * 1.1f), blendMode = BlendMode.Plus)
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(if (sunLeft) bx + sw else bx + bw - sw, top), Offset(if (sunLeft) bx + sw else bx + bw - sw, baseY), max(1f, density * 0.8f))
        val darkX = if (sunLeft) bx + bw - sw * 0.7f else bx
        drawRect(Color.Black.copy(alpha = 0.28f), Offset(darkX, top - bh * 0.1f), Size(sw * 0.7f, bh * 1.1f))
        // kat döşemeleri: koyu derz + üstünde ince ışık
        var fy = top + floorH * 1.3f
        while (fy < baseY) {
            drawLine(Color.Black.copy(alpha = 0.26f), Offset(bx, fy), Offset(bx + bw, fy), max(1f, floorH * 0.09f))
            drawLine(c(k.fog, 0.07f), Offset(bx, fy - max(1f, floorH * 0.09f)), Offset(bx + bw, fy - max(1f, floorH * 0.09f)), max(1f, floorH * 0.05f))
            fy += floorH
        }
        if (depth >= 1) {
            val cols = max(2, (bw / (floorH * 1.05f)).toInt())
            val colW = bw / cols
            var wy = top + floorH * 1.3f + floorH * 0.24f
            val ww = colW * 0.46f; val wh = floorH * 0.52f
            while (wy + wh < baseY - floorH * 0.7f) {
                val upper = (wy - top) / bh < 0.5f
                for (cx in 0 until cols) {
                    val h = r.nextInt(100)
                    val wx = bx + colW * cx + (colW - ww) / 2f
                    when {
                        burning && upper && h < 30 -> {
                            // yanan pencere: üstünde is lekesi
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), wy - floorH * 1.6f, wy), Offset(wx - ww * 0.2f, wy - floorH * 1.6f), Size(ww * 1.4f, floorH * 1.6f))
                            drawRect(Color(0xFF140604), Offset(wx, wy), Size(ww, wh))
                            lights += floatArrayOf(wx, wy, ww, wh, h * 0.37f, L_BURN.toFloat())
                        }
                        h < 3 -> { drawRect(Color(0xFF1A1008), Offset(wx, wy), Size(ww, wh)); lights += floatArrayOf(wx, wy, ww, wh, h * 1.7f + cx, L_WINDOW.toFloat()) }
                        h == 5 && depth == 2 -> { drawRect(Color(0xFF080A12), Offset(wx, wy), Size(ww, wh)); lights += floatArrayOf(wx, wy, ww, wh, cx * 2.3f, L_TV.toFloat()) }
                        h < 24 -> {
                            // kırık cam: kapkara boşluk, kenarda sivri cam parçası
                            drawRect(Color.Black.copy(alpha = 0.9f), Offset(wx, wy), Size(ww, wh))
                            if (h % 2 == 0) drawPath(Path().apply { moveTo(wx, wy); lineTo(wx + ww * 0.45f, wy); lineTo(wx, wy + wh * 0.7f); close() }, mix(col, c(k.skyMid), 0.5f).copy(alpha = 0.8f))
                        }
                        else -> {
                            // karanlık cam: gökyüzünü soluk yansıtır
                            drawRect(Brush.verticalGradient(listOf(mix(mix(col, Color.Black, 0.35f), c(k.skyMid), 0.35f), mix(col, Color.Black, 0.55f)), wy, wy + wh), Offset(wx, wy), Size(ww, wh))
                            drawLine(c(k.sun, 0.10f), Offset(wx, wy + wh), Offset(wx + ww, wy + wh), max(1f, density * 0.6f))
                        }
                    }
                }
                wy += floorH
            }
        } else {
            // uzak katman: sis içinde seyrek ışık noktaları
            val cols = max(2, (bw / (floorH * 1.1f)).toInt())
            var wy = top + floorH * 1.6f
            while (wy < baseY - floorH) {
                for (cx in 0 until cols) if (r.nextInt(1000) < 25) {
                    val wx = bx + bw * (cx + 0.5f) / cols - floorH * 0.15f
                    lights += floatArrayOf(wx, wy, floorH * 0.3f, floorH * 0.4f, r.nextFloat() * 6f, L_WINDOW.toFloat())
                }
                wy += floorH
            }
        }
        // hasar: yakın katmanda cephede yırtık delik, kenarında demirler
        if (depth == 2 && b.seed % 3 == 0) {
            val hx = bx + bw * (0.25f + r.nextFloat() * 0.4f); val hy = top + bh * (0.25f + r.nextFloat() * 0.3f); val hr = bw * (0.08f + r.nextFloat() * 0.06f)
            val hole = Path().apply {
                for (i in 0 until 11) {
                    val a = i / 11f * 2f * PI.toFloat(); val rr = hr * (if (i % 2 == 0) 1f else 0.5f) * (0.75f + r.nextFloat() * 0.4f)
                    val px = hx + cos(a) * rr; val py = hy + sin(a) * rr * 0.8f
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(hole, Color.Black.copy(alpha = 0.92f))
                        repeat(4) {
                val a = r.nextFloat() * 6.28f
                drawLine(mix(col, Color.Black, 0.5f), Offset(hx + cos(a) * hr * 0.8f, hy + sin(a) * hr * 0.6f), Offset(hx + cos(a) * hr * 0.35f, hy + sin(a) * hr * 0.25f), max(1f, density * 0.9f))
            }
        }
        // taban: ortam gölgesi (sokak seviyesinde koyulaşır)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), baseY - bh * 0.2f, baseY), Offset(bx, baseY - bh * 0.2f), Size(bw, bh * 0.2f))
    }
    // ters ışık: çatı kenarları ve güneş tarafındaki köşe parlar
    val rim = c(k.sun, 0.30f - 0.08f * (2 - depth))
    val stepW = bw / b.steps
    for (s in 0 until b.steps) {
        val hh = roofY(b, s, top, bh)
        drawLine(rim, Offset(bx + stepW * s, hh), Offset(bx + stepW * (s + 1), hh + bw * b.tilt * (if (s % 2 == 0) 1f else -1f)), max(1f, density * (0.6f + 0.3f * depth)), blendMode = BlendMode.Plus)
    }
    val rimX = if (sunLeft) bx else bx + bw
    drawLine(c(k.sun, 0.16f - 0.04f * (2 - depth)), Offset(rimX, top + bh * 0.06f), Offset(rimX, top + bh * 0.6f), max(1f, density * 1.2f), blendMode = BlendMode.Plus)
    // çatı eşyaları
    val dark = mix(col, Color.Black, 0.15f)
    fun segY(s: Int) = roofY(b, s, top, bh) + bw * b.tilt * (if (s % 2 == 0) 0.5f else -0.5f)
    if (depth >= 1 && r.nextFloat() < 0.5f) {
        // su deposu: ayaklar, gövde, konik kapak
        val s = r.nextInt(b.steps); val cx = bx + stepW * (s + 0.5f); val y = segY(s)
        val tw = min(stepW * 0.7f, H * (0.022f + 0.012f * depth)); val th = tw * 1.05f; val legs = tw * 0.55f
        drawLine(dark, Offset(cx - tw * 0.35f, y), Offset(cx - tw * 0.3f, y - legs), max(1f, tw * 0.07f))
        drawLine(dark, Offset(cx + tw * 0.35f, y), Offset(cx + tw * 0.3f, y - legs), max(1f, tw * 0.07f))
        drawLine(dark, Offset(cx - tw * 0.33f, y - legs * 0.5f), Offset(cx + tw * 0.33f, y - legs * 0.5f), max(1f, tw * 0.05f))
        drawRoundRect(dark, Offset(cx - tw / 2f, y - legs - th), Size(tw, th), CornerRadius(tw * 0.08f))
        drawPath(Path().apply { moveTo(cx - tw * 0.55f, y - legs - th); lineTo(cx, y - legs - th - tw * 0.35f); lineTo(cx + tw * 0.55f, y - legs - th); close() }, dark)
        for (i in 1..3) drawLine(Color.Black.copy(alpha = 0.3f), Offset(cx - tw / 2f, y - legs - th * i / 4f), Offset(cx + tw / 2f, y - legs - th * i / 4f), max(1f, tw * 0.03f))
        val ex = if (sunLeft) cx - tw / 2f else cx + tw / 2f
        drawLine(c(k.sun, 0.35f), Offset(ex, y - legs - th), Offset(ex, y - legs), max(1f, density * 0.8f), blendMode = BlendMode.Plus)
    }
    if (depth >= 1 && r.nextFloat() < 0.55f) {
        // klima / havalandırma kutuları
        repeat(1 + r.nextInt(2)) {
            val s = r.nextInt(b.steps); val y = segY(s); val bw2 = stepW * (0.2f + r.nextFloat() * 0.2f); val bh2 = floorH * (0.5f + r.nextFloat() * 0.4f)
            val x = bx + stepW * s + r.nextFloat() * (stepW - bw2)
            drawRect(dark, Offset(x, y - bh2), Size(bw2, bh2 + 1f))
            drawLine(rim, Offset(x, y - bh2), Offset(x + bw2, y - bh2), max(1f, density * 0.6f), blendMode = BlendMode.Plus)
        }
    }
    if (b.ant) {
        // anten direği, çapraz kollar ve yanıp sönen kırmızı uçak ışığı
        val ax = bx + bw * 0.5f; val y = segY((b.steps / 2).coerceAtMost(b.steps - 1)); val ah = bh * 0.12f
        drawLine(dark, Offset(ax, y), Offset(ax, y - ah), max(1f, density * 1.2f))
        drawLine(dark, Offset(ax - ah * 0.12f, y - ah * 0.7f), Offset(ax + ah * 0.12f, y - ah * 0.7f), max(1f, density))
        drawLine(dark, Offset(ax - ah * 0.08f, y - ah * 0.5f), Offset(ax + ah * 0.08f, y - ah * 0.5f), max(1f, density))
        if (depth >= 1) lights += floatArrayOf(ax, y - ah, 0f, 0f, r.nextFloat(), L_BEACON.toFloat())
    }
    if (b.steps >= 4 || r.nextFloat() < 0.25f) {
        // yıkık çatıdan fırlayan inşaat demirleri
        var low = 0
        for (s in 1 until b.steps) if (roofY(b, s, top, bh) > roofY(b, low, top, bh)) low = s
        val y = segY(low); val x0 = bx + stepW * low
        repeat(3 + r.nextInt(3)) {
            val x = x0 + r.nextFloat() * stepW; val l = floorH * (0.6f + r.nextFloat() * 1.2f); val a = (r.nextFloat() - 0.5f) * 0.7f
            drawLine(dark, Offset(x, y + 1f), Offset(x + sin(a) * l, y - cos(a) * l), max(1f, density * 0.7f))
        }
    }
}

/** Sokak: ıslak asfalt, kaldırım ve bordür, aşınmış şerit, çatlaklar, moloz ve gökyüzünü yansıtan birikintiler. */
private fun DrawScope.drawStreet(k: SceneKind, W: Float, H: Float, groundTop: Float, puddles: MutableList<FloatArray>) {
    val gh = H - groundTop
    val near = c(k.near)
    drawRect(Brush.verticalGradient(0f to mix(near, c(k.fog), 0.22f), 0.12f to mix(near, c(k.fog), 0.08f), 1f to mix(near, Color.Black, 0.55f), startY = groundTop, endY = H), Offset(0f, groundTop), Size(W, gh))
    // kaldırım + bordür
    val curb = groundTop + gh * 0.07f
    drawRect(Brush.verticalGradient(listOf(mix(near, c(k.fog), 0.32f), mix(near, c(k.fog), 0.18f)), groundTop, curb), Offset(0f, groundTop), Size(W, curb - groundTop))
    var px = 0f
    val r = Random(k.ordinal * 7 + 91)
    while (px < W) { drawLine(Color.Black.copy(alpha = 0.25f), Offset(px, groundTop), Offset(px - gh * 0.02f, curb), max(1f, density * 0.6f)); px += W * (0.035f + r.nextFloat() * 0.02f) }
    drawLine(c(k.sun, 0.22f), Offset(0f, curb), Offset(W, curb), max(1f, density * 0.9f), blendMode = BlendMode.Plus)
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), curb, curb + gh * 0.05f), Offset(0f, curb), Size(W, gh * 0.05f))
    // asfalt dokusu: perspektifle büyüyen tanecikler
    repeat(1600) {
        val v = r.nextFloat(); val y = curb + (H - curb) * v
        val sz = (0.8f + v * 2.6f) * density * (0.5f + r.nextFloat())
        val light = r.nextFloat() < 0.4f
        drawRect(if (light) c(k.fog, 0.06f + 0.05f * r.nextFloat()) else Color.Black.copy(alpha = 0.10f + 0.12f * r.nextFloat()), Offset(r.nextFloat() * W, y), Size(sz, sz * 0.55f))
    }
    // aşınmış orta şerit (kesik, yer yer silinmiş)
    val laneY = curb + (H - curb) * 0.36f
    var lx = -W * 0.02f
    while (lx < W) {
        if (r.nextFloat() > 0.2f) drawRect(Color(0xFFE8D9A8).copy(alpha = 0.12f + r.nextFloat() * 0.08f), Offset(lx, laneY), Size(W * 0.055f, gh * 0.012f))
        lx += W * 0.10f
    }
    // çatlaklar
    repeat(16) {
        var x = r.nextFloat() * W; var y = curb + (H - curb) * (0.1f + r.nextFloat() * 0.9f)
        val pth = Path().apply { moveTo(x, y) }
        repeat(6) { x += (r.nextFloat() - 0.3f) * W * 0.03f; y += (r.nextFloat() - 0.5f) * gh * 0.05f; pth.lineTo(x, y) }
        drawPath(pth, Color.Black.copy(alpha = 0.38f), style = androidx.compose.ui.graphics.drawscope.Stroke(max(1f, density * (0.6f + (y - curb) / gh))))
    }
    // birikintiler: göğü yansıtır, kenarı koyu
    repeat(4) {
        val cx = W * (0.05f + r.nextFloat() * 0.9f); val cy = curb + (H - curb) * (0.25f + r.nextFloat() * 0.7f)
        val rx = W * (0.03f + r.nextFloat() * 0.05f) * (0.6f + (cy - curb) / gh); val ry = rx * 0.10f
        drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - rx * 1.05f, cy - ry * 1.2f), Size(rx * 2.1f, ry * 2.4f))
        drawOval(Brush.verticalGradient(listOf(mix(c(k.skyMid), c(k.horizon), 0.45f).copy(alpha = 0.32f), c(k.skyTop, 0.5f)), cy - ry, cy + ry), Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
        puddles += floatArrayOf(cx, cy, rx, ry)
    }
    // moloz: bordür boyunca ve yolda kırık beton parçaları
    repeat(38) {
        val v = r.nextFloat().let { it * it }
        val y = curb + (H - curb) * v + gh * 0.01f
        val s = (2f + v * 9f + r.nextFloat() * 3f) * density
        val x = r.nextFloat() * W
        val rock = Path().apply { moveTo(x - s, y); lineTo(x - s * 0.6f, y - s * 0.7f); lineTo(x + s * 0.3f, y - s * 0.9f); lineTo(x + s, y - s * 0.2f); lineTo(x + s * 0.8f, y); close() }
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(x - s * 1.1f, y - s * 0.15f), Size(s * 2.4f, s * 0.4f))
        drawPath(rock, mix(mix(near, Color.Black, 0.25f), c(k.fog), 0.04f + r.nextFloat() * 0.08f))
        drawLine(c(k.sun, 0.10f), Offset(x - s * 0.6f, y - s * 0.7f), Offset(x + s * 0.3f, y - s * 0.9f), max(1f, density * 0.6f), blendMode = BlendMode.Plus)
    }
}

/** pişmiş katmandaki canlı ışık: ışıklı/yanan pencere, televizyon titremesi, anten uçak ışığı */
private fun DrawScope.drawLight(l: FloatArray, offX: Float, t: Float, k: SceneKind, depth: Int) {
    val x = l[0] + offX; val y = l[1]; val w = l[2]; val h = l[3]; val ph = l[4]
    when (l[5].toInt()) {
        L_WINDOW -> {
            val fk = 0.78f + 0.22f * sin(t * (0.6f + ph % 1.3f) + ph)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFD58A).copy(alpha = 0.92f * fk), Color(0xFFE0883A).copy(alpha = 0.85f * fk)), y, y + h), Offset(x, y), Size(w, h))
            if (depth >= 1) {
                val ctr = Offset(x + w / 2f, y + h / 2f)
                drawCircle(rg(listOf(Color(0xFFFFB860).copy(alpha = 0.16f * fk), Color.Transparent), ctr, w * 2.4f), w * 2.4f, ctr, blendMode = BlendMode.Plus)
            }
        }
        L_BURN -> {
            val fk = (0.62f + 0.38f * sin(t * 9f + ph) * sin(t * 5.3f + ph * 2f)).coerceIn(0.2f, 1f)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE6A0).copy(alpha = fk), c(k.fire, 0.95f), Color(0xFF8A1A08)), y, y + h), Offset(x, y), Size(w, h))
            val ctr = Offset(x + w / 2f, y + h * 0.4f)
            drawCircle(rg(listOf(c(k.fire, 0.42f * fk), Color.Transparent), ctr, w * 3.2f), w * 3.2f, ctr, blendMode = BlendMode.Plus)
            if (depth == 2) flame(x + w / 2f, y + h * 0.35f, w * 0.42f, h * (1.1f + 0.4f * sin(t * 6f + ph)), sin(t * 4f + ph) * w * 0.3f, k.fire)
        }
        L_TV -> {
            val v = (((t * 5f + ph).toInt() * 7919) % 100) / 100f
            drawRect(Color(0xFF9EC4FF).copy(alpha = 0.35f + 0.4f * v), Offset(x, y), Size(w, h))
            val ctr = Offset(x + w / 2f, y + h / 2f)
            drawCircle(rg(listOf(Color(0xFF7AA8FF).copy(alpha = 0.12f * v), Color.Transparent), ctr, w * 2f), w * 2f, ctr, blendMode = BlendMode.Plus)
        }
        L_BEACON -> if ((t * 0.7f + ph) % 1f < 0.14f) {
            val ctr = Offset(x, y)
            drawCircle(rg(listOf(Color(0xFFFF3020).copy(alpha = 0.55f), Color.Transparent), ctr, density * 9f), density * 9f, ctr, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFB0A0), density * 1.4f, ctr)
        }
    }
}

private fun DrawScope.drawScene(w: World, k: SceneKind, t: Float, fx: Int, zombies: List<List<ImageBitmap>> = emptyList(), car: SceneCar? = null, sh: SceneShaders? = null) {
    val W = size.width; val H = size.height
    if (W < 16f || H < 16f) return
    val bk = w.baked?.takeIf { it.w == W.toInt() && it.h == H.toInt() } ?: bakeWorld(w, k, W, H).also { w.baked = it }
    val horizonY = H * 0.66f
    // yıldırım / uzak patlama parlaması: ~11 sn'de bir kısa
    val fl = (sin(t * 0.57f) * sin(t * 1.31f + 0.6f)).let { if (it > 0.985f) (it - 0.985f) / 0.015f else 0f }
    val breath = 0.5f + 0.5f * sin(t * 0.21f)
    val sunX = W * 0.70f + sin(t * 0.04f) * W * 0.01f; val sunY = horizonY - H * 0.25f
    val sunR = H * 0.085f
    val groundTop = bk.groundTop

    // ---- gökyüzü: gölgelendiriciyle saçılma + hacimli bulut; yoksa eski çizim
    if (sh != null) {
        drawRect(sh.sky(W, H, t, horizonY, sunX, sunY, sunR * 0.62f, breath, fl, fx >= 2, c(k.skyTop), c(k.skyMid), c(k.horizon), c(k.sun)), size = Size(W, groundTop + 2f))
    } else {
        drawRect(Brush.verticalGradient(
            0f to c(k.skyTop), 0.45f to mix(c(k.skyMid), c(k.horizon), 0.1f * breath), 0.72f to mix(c(k.skyMid), c(k.horizon), 0.55f + 0.1f * breath), 1f to c(k.horizon, 1f),
            startY = 0f, endY = horizonY + H * 0.088f,
        ), size = Size(W, H))
        if (fl > 0f) drawRect(Color.White.copy(alpha = 0.18f * fl), size = Size(W, horizonY))
        for (s in w.stars) {
            val a = (0.25f + 0.75f * (0.5f + 0.5f * sin(t * s[2] * 1.6f + s[3]))) * (1f - s[1] / 0.6f).coerceIn(0f, 1f) * (if (k == SceneKind.LOBBY) 0.55f else 0.9f)
            drawCircle(Color.White.copy(alpha = a), (0.6f + s[2] * 0.9f) * density, Offset(s[0] * W, s[1] * H))
        }
        drawCircle(rg(listOf(c(k.sun, 0.55f), c(k.sun, 0.18f), Color.Transparent), Offset(sunX, sunY), H * 0.55f), H * 0.55f, Offset(sunX, sunY))
        drawCircle(rg(listOf(c(k.sun, 1f), c(k.sun, 0.9f), c(k.sun, 0f)), Offset(sunX, sunY), sunR * 1.6f), sunR * 1.6f, Offset(sunX, sunY))
        for (cl in w.clouds) {
            val x = ((cl[0] + t * cl[3] * 0.1f) % 1.4f) - 0.2f
            val y = horizonY * (0.15f + cl[1]) * 1.2f
            val rw = W * (0.18f + cl[4] * 2.4f); val rh = H * cl[2] * 0.09f
            val cx = x * W
            drawOval(Brush.verticalGradient(listOf(mix(c(k.skyMid), c(k.sun), 0.35f).copy(alpha = 0.55f), c(k.skyTop, 0.45f)), y - rh, y + rh),
                Offset(cx - rw, y - rh), Size(rw * 2f, rh * 2f))
        }
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
    }

    // ---- şehir katmanları (uzaktan yakına): pişmiş cepheler + canlı ışıklar + yangın ışığı + duman + sis + alev
    val par = sin(t * 0.045f)
    for ((li, sl) in w.skylines.withIndex()) {
        val depth = sl.layer
        val L = bk.layers[li]
        val baseY = layerBaseY(depth, H)
        val offX = par * W * (0.006f + 0.012f * depth)
        drawImage(L.img, Offset(offX - L.pad, L.top))
        for (l in L.lights) drawLight(l, offX, t, k, depth)
        // yangınlar kendi ve komşu binaların cephesini turuncuya boyar (yalnız bina siluetinde)
        translate(offX, 0f) {
            clipPath(L.clip) {
                for ((fi, f) in w.fires.withIndex()) if (f[1].toInt() == depth) {
                    val x = f[0] * W; val y = bk.fireY[fi]; val s = f[2] * H * (0.045f + 0.02f * depth)
                    val fk = 0.8f + 0.2f * sin(t * 9f + f[3]) * sin(t * 5.3f + f[3] * 2f)
                    val gr = s * 7f
                    drawCircle(rg(listOf(c(k.fire, 0.42f * fk), c(k.fire, 0.12f * fk), Color.Transparent), Offset(x, y + s), gr), gr, Offset(x, y + s), blendMode = BlendMode.Plus)
                }
            }
        }
        for ((fi, f) in w.fires.withIndex()) if (f[1].toInt() == depth && sh != null) {
            val x = f[0] * W + offX; val y = bk.fireY[fi]; val s = f[2] * H * (0.045f + 0.02f * depth)
            val u = s * 1.5f; val hh = H * (0.40f + 0.12f * depth); val drift = 3.2f
            val smoke = mix(Color(0xFF110E12), c(k.far), 0.25f)
            drawRect(sh.smoke(fi, x, y - s * 0.6f, u, hh, drift, t, f[3], smoke, mix(c(k.fire), smoke, 0.25f)),
                Offset(x - u * 1.7f, y - s * 0.6f - hh), Size(u * (drift + 3.6f), hh))
        }
        // katman arası sis
        val fogTop = baseY - H * 0.16f
        if (sh != null) drawRect(sh.fog(li, fogTop, baseY + H * 0.03f, H, t, li * 3.7f, 0.34f - 0.06f * depth, c(k.fog)), Offset(0f, fogTop), Size(W, H * 0.19f))
        else drawRect(Brush.verticalGradient(listOf(Color.Transparent, c(k.fog, 0.20f - 0.04f * depth), Color.Transparent), fogTop, baseY + H * 0.03f), Offset(0f, fogTop), Size(W, H * 0.19f))
        for ((fi, f) in w.fires.withIndex()) if (f[1].toInt() == depth) {
            val y = bk.fireY[fi]
            if (sh != null) {
                val x = f[0] * W + offX; val s = f[2] * H * (0.05f + 0.025f * depth)
                val fk = 0.8f + 0.2f * sin(t * 9f + f[3]) * sin(t * 5.3f + f[3] * 2f)
                drawCircle(rg(listOf(c(k.fire, 0.45f * fk), c(k.fire, 0.10f), Color.Transparent), Offset(x, y - s * 0.5f), s * 3.2f), s * 3.2f, Offset(x, y - s * 0.5f), blendMode = BlendMode.Plus)
                // yangın çatı boyunca yayılır: ortada büyük alev, yanlarda daha kısa diller
                for ((j, o) in FIRE_TONGUES.withIndex()) {
                    val fx2 = x + o[0] * s; val hw = s * o[1]; val hh = s * o[2]
                    drawRect(sh.fire(fi * 3 + j, fx2, y, hw, hh, t * o[3], f[3] * 3.1f + j * 7.3f, c(k.fire)), Offset(fx2 - hw * 1.35f, y - hh * 1.12f), Size(hw * 2.7f, hh * 1.32f), blendMode = BlendMode.Plus)
                }
            } else drawFire(k, f, t, W, H, y, offX, fx)
        }
    }

    // ---- sokak: pişmiş asfalt + sokak hizasında akan sis
    drawImage(bk.ground, Offset(0f, groundTop))
    val flick = 0.82f + 0.18f * sin(t * 13f) * sin(t * 7.3f)
    // birikintilerde ateşin ve göğün titrek yansıması
    for (p in bk.puddles) {
        drawOval(rg(listOf(c(k.fire, 0.07f * flick), Color.Transparent), Offset(p[0], p[1]), p[2]), Offset(p[0] - p[2], p[1] - p[3]), Size(p[2] * 2f, p[3] * 2f), blendMode = BlendMode.Plus)
    }
    if (sh != null) drawRect(sh.fog(3, groundTop - H * 0.03f, groundTop + H * 0.12f, H, t * 1.4f, 9.1f, 0.30f, mix(c(k.fog), c(k.sun), 0.2f)), Offset(0f, groundTop - H * 0.03f), Size(W, H * 0.15f))
    else drawRect(Brush.verticalGradient(listOf(c(k.fog, 0.30f), Color.Transparent), groundTop, groundTop + H * 0.10f), Offset(0f, groundTop), Size(W, H * 0.10f))

    // sokak lambası: ışık konisi, yerde ışık havuzu ve ıslak asfaltta yansıma
    val lampX = W * 0.58f; val lampTop = groundTop - H * 0.02f
    val lamp = Offset(lampX + W * 0.018f, lampTop - H * 0.15f)
    val lf = (if (sin(t * 23f) * sin(t * 4.1f) > 0.93f) 0.2f else 1f)
    drawPath(Path().apply { moveTo(lamp.x - W * 0.004f, lamp.y); lineTo(lamp.x + W * 0.004f, lamp.y); lineTo(lamp.x + W * 0.05f, groundTop + H * 0.06f); lineTo(lamp.x - W * 0.045f, groundTop + H * 0.06f); close() },
        Brush.verticalGradient(listOf(c(0xFFFFE0A0, 0.16f * lf), c(0xFFFFE0A0, 0.03f * lf)), lamp.y, groundTop + H * 0.06f), blendMode = BlendMode.Plus)
    drawOval(rg(listOf(c(0xFFFFE0A0, 0.22f * lf), Color.Transparent), Offset(lamp.x, groundTop + H * 0.06f), W * 0.06f), Offset(lamp.x - W * 0.06f, groundTop + H * 0.035f), Size(W * 0.12f, H * 0.05f), blendMode = BlendMode.Plus)
    drawRect(Brush.verticalGradient(listOf(c(0xFFFFE0A0, 0.07f * lf), Color.Transparent), groundTop + H * 0.06f, groundTop + H * 0.16f), Offset(lamp.x - W * 0.005f, groundTop + H * 0.06f), Size(W * 0.01f, H * 0.10f), blendMode = BlendMode.Plus)
    drawLine(Color(0xFF07050A), Offset(lampX, groundTop + H * 0.08f), Offset(lampX, lampTop - H * 0.16f), 3.2f * density)
    drawLine(Color(0xFF07050A), Offset(lampX, lampTop - H * 0.16f), Offset(lampX + W * 0.018f, lampTop - H * 0.16f), 2.6f * density)
    drawLine(c(k.sun, 0.25f), Offset(lampX + 1.4f * density, groundTop + H * 0.06f), Offset(lampX + 1.4f * density, lampTop - H * 0.16f), 0.8f * density, blendMode = BlendMode.Plus)
    drawCircle(rg(listOf(c(0xFFFFE0A0, 0.65f * lf), Color.Transparent), lamp, H * 0.12f), H * 0.12f, lamp, blendMode = BlendMode.Plus)
    drawCircle(Color(0xFFFFF4D8).copy(alpha = 0.9f * lf), 2.2f * density, lamp)

    // uzaktaki zombiler: oyunun kendi zombileri sokakta yavaşça kamp ateşine (sola) yürür; uzaktakiler sisle solar.
    // Görseller yüklenemediyse eski silüetler.
    val dusk = ColorFilter.tint(mix(c(k.fog), Color(0xFF8A8494), 0.55f), BlendMode.Modulate)
    for ((i, z) in w.walkers.withIndex()) {
        val span = 1.3f
        val frames = zombies.getOrNull(i % zombies.size.coerceAtLeast(1)).orEmpty()
        val hgt = H * 0.18f * z[1] * SCENE_ZOMBIES[i % SCENE_ZOMBIES.size].second
        // yürüyüş hızı animasyona bağlı: bir tur (kare sayısı) boyunca bir adım boyu yol alınır, ayaklar yerde kaymaz
        val fps = 7f * z[3]
        val cycles = fps / frames.size.coerceAtLeast(8)
        val speed = cycles * hgt * STRIDE_PER_HEIGHT / W
        val x = span - ((z[0] * span + t * speed) % span) - 0.15f
        val y = groundTop + H * 0.03f * z[1]
        if (frames.isEmpty()) { drawZombie(x * W, y, H * 0.125f * z[1], t * z[3] + z[2], c(k.near)); continue }
        val img = frames[((t * fps + z[2] * 3f).toInt() % frames.size + frames.size) % frames.size]
        val wid = hgt * img.width / img.height.coerceAtLeast(1)
        // ayak gölgesi
        drawOval(Color.Black.copy(alpha = 0.4f), Offset(x * W - wid * 0.3f, y - hgt * 0.035f), Size(wid * 0.6f, hgt * 0.07f))
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset((x * W - wid / 2).toInt(), (y - hgt).toInt()), IntSize(wid.toInt(), hgt.toInt()),
            alpha = (0.55f + 0.35f * (z[1] - 0.7f) / 0.6f).coerceIn(0.5f, 0.95f), colorFilter = dusk, filterQuality = FilterQuality.None)
    }

    // kamp ateşi + barikat (ön plan); araba enkazı yerine oyunun arabası
    drawForeground(k, t, W, H, groundTop, fx, car, sh)

    // ---- kor / kül yağmuru (additive)
    val n = when (fx) { 0 -> 22; 1 -> 45; 2 -> 70; else -> 90 }
    for (i in 0 until n) {
        val e = w.embers[i % w.embers.size]
        val ph = ((t * 0.05f * e[2] + e[4]) % 1f)
        val x = (e[0] + sin(t * 0.4f * e[2] + e[3]) * 0.03f + ph * 0.06f) * W
        val y = H * (1.02f - ph * 1.05f)
        val a = sin(ph * PI.toFloat()).coerceIn(0f, 1f) * (0.4f + 0.6f * (0.5f + 0.5f * sin(t * 6f * e[2] + e[3])))
        val r = (0.9f + e[2] * 1.3f) * density
        drawCircle(c(k.fire, 0.14f * a), r * 3.0f, Offset(x, y), blendMode = BlendMode.Plus)
        drawCircle(c(k.fire, 0.5f * a), r * 1.4f, Offset(x, y), blendMode = BlendMode.Plus)
        drawCircle(Color(0xFFFFF0C0).copy(alpha = a), r * 0.5f, Offset(x, y), blendMode = BlendMode.Plus)
    }
    // gri kül taneleri: yavaşça süzülerek düşer
    if (fx >= 2) for (i in 0 until 36) {
        val e = w.embers[(i * 7) % w.embers.size]
        val ph = ((t * 0.018f * (0.6f + e[2]) + e[1]) % 1f)
        val x = (e[4] + sin(t * 0.7f * e[2] + e[3]) * 0.02f - ph * 0.08f) * W
        val y = H * (ph * 1.05f - 0.02f)
        drawRect(Color(0xFFBDB4B0).copy(alpha = 0.22f * sin(ph * PI.toFloat())), Offset(x, y), Size((1.2f + e[2]) * density, (0.8f + e[2] * 0.5f) * density))
    }
    // bokeh: büyük bulanık ışık daireleri
    if (fx >= 1) for (i in 0 until 6) {
        val ph = ((t * 0.012f + i * 0.17f) % 1f)
        val x = (0.1f + i * 0.17f + sin(t * 0.1f + i) * 0.02f) * W
        val y = H * (0.9f - ph * 0.5f)
        drawCircle(rg(listOf(c(k.fire, 0.08f), Color.Transparent), Offset(x, y), H * 0.06f), H * 0.06f, Offset(x, y), blendMode = BlendMode.Plus)
    }

    // ---- vinyet
    drawRect(rg(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(W * 0.5f, H * 0.5f), max(W, H) * 0.72f), size = Size(W, H))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), 0f, H * 0.2f), size = Size(W, H * 0.2f))
}

/** yedek (gölgelendiricisiz) çatı yangını: ısı hâlesi, yumuşak duman daireleri, yol çizimi alev dilleri */
private fun DrawScope.drawFire(k: SceneKind, f: FloatArray, t: Float, W: Float, H: Float, y0: Float, offX: Float, fx: Int) {
    val x = f[0] * W + offX
    val s = f[2] * H * 0.08f
    val y = y0
    drawCircle(rg(listOf(c(k.fire, 0.55f), c(k.fire, 0.12f), Color.Transparent), Offset(x, y), s * 4.2f), s * 4.2f, Offset(x, y), blendMode = BlendMode.Plus)
    val puffs = when (fx) { 0 -> 5; 1 -> 8; else -> 12 }
    for (i in 0 until puffs) {
        val ph = ((t * 0.09f + i / puffs.toFloat() + f[3] * 0.13f) % 1f)
        val px = x + sin(ph * 5f + f[3] + i) * s * 0.9f + ph * s * 3.2f
        val py = y - ph * H * 0.34f
        val pr = s * (0.5f + ph * 2.6f)
        val a = (sin(ph * PI.toFloat()) * 0.30f)
        drawCircle(rg(listOf(Color(0xFF2A2026).copy(alpha = a), Color.Transparent), Offset(px, py), pr), pr, Offset(px, py))
    }
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
    drawLine(col, Offset(x - lean * 0.2f, y - hgt * 0.38f), Offset(x - hgt * 0.07f + sw, y), hgt * 0.07f)
    drawLine(col, Offset(x - lean * 0.2f, y - hgt * 0.38f), Offset(x + hgt * 0.07f - sw, y), hgt * 0.07f)
    drawLine(col, Offset(x - lean * 0.2f, y - hgt * 0.38f), Offset(x + lean, bodyTop + hgt * 0.1f), hgt * 0.16f)
    drawCircle(col, hgt * 0.085f, Offset(x + lean * 1.4f, bodyTop))
    drawLine(col, Offset(x + lean * 0.8f, bodyTop + hgt * 0.16f), Offset(x + hgt * 0.30f, bodyTop + hgt * 0.20f + sw * 0.4f), hgt * 0.05f)
    drawLine(col, Offset(x + lean * 0.6f, bodyTop + hgt * 0.20f), Offset(x + hgt * 0.27f, bodyTop + hgt * 0.30f - sw * 0.4f), hgt * 0.05f)
}

private fun DrawScope.drawForeground(k: SceneKind, t: Float, W: Float, H: Float, groundTop: Float, fx: Int, car: SceneCar? = null, sh: SceneShaders? = null) {
    val dark = Color(0xFF07050A)
    val fx0 = W * 0.30f; val fy0 = H * 0.93f
    val flick = 0.82f + 0.18f * sin(t * 13f) * sin(t * 7.3f)
    // barikat (sağ alt): eğik kalaslar ve kum torbaları; kamp ateşine bakan kenarları turuncu
    val bx = W * 0.80f; val by = H * 0.98f
    val wood = Color(0xFF1C130E)
    for (i in 0 until 6) {
        val ang = (-0.9f + i * 0.32f)
        val len = H * (0.22f + 0.04f * ((i * 5) % 3))
        val a = Offset(bx + i * W * 0.025f, by); val b = Offset(bx + i * W * 0.025f + sin(ang) * len, by - cos(ang) * len)
        drawLine(wood, a, b, H * 0.012f)
        drawLine(c(k.fire, 0.22f * flick), Offset(a.x - H * 0.004f, a.y), Offset(b.x - H * 0.004f, b.y), H * 0.0025f, blendMode = BlendMode.Plus)
        drawCircle(Color(0xFF6A6058), H * 0.0022f, Offset(a.x + (b.x - a.x) * 0.3f, a.y + (b.y - a.y) * 0.3f))
    }
    for (row in 0 until 2) for (i in 0 until 5 - row) {
        val sx = W * 0.74f + i * W * 0.042f + row * W * 0.021f; val sy = H * 0.905f - row * H * 0.045f
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4A3C2C), Color(0xFF241C14)), sy, sy + H * 0.05f), Offset(sx, sy), Size(W * 0.046f, H * 0.05f), CornerRadius(H * 0.022f))
        drawRoundRect(Brush.verticalGradient(listOf(c(k.fire, 0.18f * flick), Color.Transparent), sy, sy + H * 0.03f), Offset(sx, sy), Size(W * 0.046f, H * 0.05f), CornerRadius(H * 0.022f), blendMode = BlendMode.Plus)
        drawLine(Color.Black.copy(alpha = 0.5f), Offset(sx + W * 0.008f, sy + H * 0.025f), Offset(sx + W * 0.038f, sy + H * 0.025f), density)
    }
    drawRect(dark, Offset(W * 0.74f, H * 0.95f), Size(W * 0.26f, H * 0.05f))
    // oyunun arabası (Hellhound): sokağın ortasında, gün batımı tonunda, kamp ateşinin ışığı yanına vurur
    val cx = W * 0.07f; val cyy = groundTop + H * 0.12f
    if (car != null) {
        val wid = W * 0.21f
        val k2 = wid / car.img.width.coerceAtLeast(1)
        val hgt = car.img.height * k2
        val fx1 = W * 0.46f
        drawOval(rg(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), Offset(fx1, cyy), wid * 0.55f), Offset(fx1 - wid * 0.55f, cyy - H * 0.025f), Size(wid * 1.1f, H * 0.06f))
        drawImage(car.img, IntOffset.Zero, IntSize(car.img.width, car.img.height),
            IntOffset((fx1 - car.ax * k2).toInt(), (cyy - car.ay * k2).toInt()), IntSize(wid.toInt(), hgt.toInt()),
            colorFilter = ColorFilter.tint(mix(c(k.fog), Color(0xFF9A8E9E), 0.6f), BlendMode.Modulate), filterQuality = FilterQuality.Medium)
        // farlar: sönük, titrek; ıslak asfaltta uzun yansıma
        val fl = 0.5f + 0.5f * sin(t * 3.1f) * sin(t * 1.7f)
        val hl = Offset(fx1 + wid * 0.44f, cyy - hgt * 0.32f)
        drawCircle(rg(listOf(Color(0xFFFFE6B0).copy(alpha = 0.22f + 0.08f * fl), Color.Transparent), hl, H * 0.08f), H * 0.08f, hl, blendMode = BlendMode.Plus)
        drawOval(rg(listOf(Color(0xFFFFE6B0).copy(alpha = 0.10f + 0.04f * fl), Color.Transparent), Offset(hl.x + W * 0.03f, cyy + H * 0.01f), W * 0.07f),
            Offset(hl.x - W * 0.04f, cyy - H * 0.005f), Size(W * 0.14f, H * 0.03f), blendMode = BlendMode.Plus)
    } else {
        val wreck = Path().apply {
            moveTo(cx, cyy); lineTo(cx, cyy - H * 0.06f); lineTo(cx + W * 0.025f, cyy - H * 0.10f); lineTo(cx + W * 0.085f, cyy - H * 0.11f)
            lineTo(cx + W * 0.115f, cyy - H * 0.07f); lineTo(cx + W * 0.16f, cyy - H * 0.06f); lineTo(cx + W * 0.16f, cyy); close()
        }
        drawPath(wreck, dark)
        drawCircle(dark, H * 0.035f, Offset(cx + W * 0.035f, cyy)); drawCircle(dark, H * 0.035f, Offset(cx + W * 0.125f, cyy))
    }
    // kamp ateşi: geniş ışık havuzu
    drawCircle(rg(listOf(c(k.fire, 0.50f * flick), c(k.fire, 0.14f), Color.Transparent), Offset(fx0, fy0 - H * 0.03f), H * 0.5f), H * 0.5f, Offset(fx0, fy0 - H * 0.03f), blendMode = BlendMode.Plus)
    // taş halka (arka taşlar), odunlar ve korlar
    val stone = Color(0xFF302822)
    for (i in 0 until 5) {
        val a = PI.toFloat() * (1.1f + i * 0.2f)
        val sx = fx0 + cos(a) * W * 0.05f; val sy = fy0 + H * 0.012f + sin(a) * H * 0.018f
        drawOval(stone, Offset(sx - W * 0.012f, sy - H * 0.014f), Size(W * 0.024f, H * 0.026f))
        drawOval(Brush.verticalGradient(listOf(c(k.fire, 0.35f * flick), Color.Transparent), sy - H * 0.014f, sy), Offset(sx - W * 0.012f, sy - H * 0.014f), Size(W * 0.024f, H * 0.026f), blendMode = BlendMode.Plus)
    }
    val log = Color(0xFF2B1A10)
    drawLine(log, Offset(fx0 - W * 0.042f, fy0 + H * 0.016f), Offset(fx0 + W * 0.04f, fy0 - H * 0.006f), H * 0.02f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(log, Offset(fx0 - W * 0.04f, fy0 - H * 0.006f), Offset(fx0 + W * 0.042f, fy0 + H * 0.016f), H * 0.02f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    for (i in 0 until 7) {
        val u = i / 6f - 0.5f
        val ex = fx0 + u * W * 0.06f; val ey = fy0 + H * 0.004f + abs(u) * H * 0.012f
        val ek = (0.5f + 0.5f * sin(t * (2.1f + i * 0.7f) + i * 1.9f))
        drawCircle(rg(listOf(Color(0xFFFFB040).copy(alpha = 0.75f * ek), c(k.fire, 0.3f * ek), Color.Transparent), Offset(ex, ey), H * 0.012f), H * 0.012f, Offset(ex, ey), blendMode = BlendMode.Plus)
    }
    if (sh != null) {
        // ince duman ve gölgelendirici alev
        if (fx >= 2) {
            val smoke = mix(Color(0xFF1C181C), c(k.fog), 0.25f)
            drawRect(sh.smoke(6, fx0, fy0 - H * 0.12f, H * 0.04f, H * 0.6f, 2.5f, t, 7.7f, smoke, mix(c(k.fire), smoke, 0.5f)),
                Offset(fx0 - H * 0.07f, fy0 - H * 0.72f), Size(H * 0.04f * 6.2f, H * 0.6f))
        }
        drawRect(sh.fire(6, fx0, fy0 + H * 0.004f, H * 0.065f, H * 0.21f, t * 1.15f, 5.3f, c(k.fire)), Offset(fx0 - H * 0.09f, fy0 - H * 0.23f), Size(H * 0.18f, H * 0.25f), blendMode = BlendMode.Plus)
        drawRect(sh.fire(7, fx0 + W * 0.012f, fy0 + H * 0.006f, H * 0.04f, H * 0.14f, t * 1.3f, 11.9f, c(k.fire)), Offset(fx0 + W * 0.012f - H * 0.06f, fy0 - H * 0.15f), Size(H * 0.12f, H * 0.17f), blendMode = BlendMode.Plus)
    } else {
        val tongues = if (fx >= 1) 7 else 4
        for (i in 0 until tongues) {
            val ph = t * (3.0f + i * 0.7f) + i * 2.3f
            val fw = H * (0.030f - abs(i - tongues / 2) * 0.003f); val fh = H * (0.15f + 0.04f * sin(ph) - abs(i - tongues / 2) * 0.012f)
            flame(fx0 + (i - tongues / 2) * W * 0.012f + sin(ph * 0.6f) * W * 0.004f, fy0, fw, fh, sin(ph * 1.2f) * fw * 0.8f, k.fire)
        }
    }
    drawCircle(Color(0xFFFFE8A0).copy(alpha = 0.5f * flick), H * 0.012f, Offset(fx0, fy0 - H * 0.012f), blendMode = BlendMode.Plus)
    // ön taşlar
    for (i in 0 until 4) {
        val a = PI.toFloat() * (0.15f + i * 0.23f)
        val sx = fx0 + cos(a) * W * 0.05f; val sy = fy0 + H * 0.012f + sin(a) * H * 0.018f
        drawOval(stone, Offset(sx - W * 0.013f, sy - H * 0.014f), Size(W * 0.026f, H * 0.028f))
        drawOval(Brush.verticalGradient(listOf(c(k.fire, 0.4f * flick), Color.Transparent), sy - H * 0.014f, sy), Offset(sx - W * 0.013f, sy - H * 0.014f), Size(W * 0.026f, H * 0.028f), blendMode = BlendMode.Plus)
    }
    // yakın ışık ön planı turuncuya boyar; ıslak asfaltta ateşin uzun yansıması
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
