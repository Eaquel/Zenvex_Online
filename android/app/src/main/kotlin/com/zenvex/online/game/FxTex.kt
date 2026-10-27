package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Efekt dokuları: açılışta kodla üretilen, piksel sanatı görünümünde (3-4 tonlu gölgeleme, sol üstten ışık, 1 px koyu kenar,
 * Bayer titreşimi) küçük gri dokular. Çizerken renk çarpımıyla boyanır; toz, duman, alev, kıvılcım, kırık parça, leke ve halka
 * için aynı doku kitaplığı kullanılır. Hiçbiri düz daire değildir: her biri gürültüyle oyulmuş, kendi gölgesi olan bir şekil.
 */
object FxTex {
    // ------------------------------------------------------------------ gürültü
    private fun h(x: Int, y: Int, s: Int): Float {
        var n = x * 374761393 + y * 668265263 + s * 1442695041
        n = (n xor (n ushr 13)) * 1274126177.toInt()
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    private fun sm(t: Float) = t * t * (3f - 2f * t)

    private fun vn(x: Float, y: Float, s: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = sm(x - xi); val fy = sm(y - yi)
        val a = h(xi, yi, s); val b = h(xi + 1, yi, s); val c = h(xi, yi + 1, s); val d = h(xi + 1, yi + 1, s)
        val top = a + (b - a) * fx
        return top + ((c + (d - c) * fx) - top) * fy
    }

    private fun fbm(x: Float, y: Float, s: Int) = vn(x, y, s) * 0.55f + vn(x * 2f, y * 2f, s + 7) * 0.28f + vn(x * 4f, y * 4f, s + 13) * 0.17f

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    private fun bayer(x: Int, y: Int) = BAYER[(y and 3) * 4 + (x and 3)] / 16f - 0.5f

    private fun gray(t: Int, a: Int = 255) = (a shl 24) or (t shl 16) or (t shl 8) or t

    private fun tone(b: Float): Int = when { b > 0.80f -> 255; b > 0.62f -> 208; b > 0.44f -> 152; else -> 104 }

    // ------------------------------------------------------------------ toz / duman bulutu
    /** Lobları oyulmuş, sol üstten aydınlatılmış 4 tonlu bulut. [n] piksel. */
    private fun puff(n: Int, seed: Int): Bitmap {
        val dens = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val u = (x + 0.5f) / n * 2f - 1f; val v = (y + 0.5f) / n * 2f - 1f
            // alan bükme: düzgün daire yerine kabarık lobların birleşimi
            val wx = u + (fbm(u * 1.7f + 3f, v * 1.7f, seed) - 0.5f) * 1.0f
            val wy = v + (fbm(u * 1.7f, v * 1.7f + 5f, seed + 3) - 0.5f) * 1.0f
            val r = sqrt(wx * wx + wy * wy)
            val lump = fbm(u * 2.6f + seed, v * 2.6f, seed + 11)
            dens[y * n + x] = 1.08f - r * 1.18f + (lump - 0.5f) * 0.75f
        }
        fun d(x: Int, y: Int) = if (x in 0 until n && y in 0 until n) dens[y * n + x] else -1f
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dd = dens[y * n + x]
            if (dd < 0.02f) continue
            val lit = ((d(x + 2, y + 2) - d(x - 2, y - 2)) * 3.2f).coerceIn(-1f, 1f)
            var b = 0.46f + 0.30f * min(dd, 0.8f) + 0.34f * lit + bayer(x, y) * 0.16f
            if (dd < 0.15f) b = 0.2f // 1 px koyu kenar
            px[y * n + x] = gray(tone(b))
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ alev dili
    /** Tabanı geniş, ucu savrulan, kenarları yalayan loblarla oyulmuş alev dili (w×h); özü beyaz-sıcak. Toplamalı karışımla çizilir. */
    private fun flame(w: Int, hgt: Int, seed: Int): Bitmap {
        val px = IntArray(w * hgt)
        for (y in 0 until hgt) {
            val tt = y / (hgt - 1f) // 0 uç, 1 taban
            val rise = 1f - tt
            // S eğrisi + gürültüyle savrulma: uç tarafı daha çok oynar
            val sway = sin(rise * 2.8f + seed) * 0.30f * rise * rise + (fbm(rise * 2f, seed * 0.3f, seed) - 0.5f) * 0.55f * rise
            val width = 0.64f * (1f - Math.pow(rise.toDouble(), 1.45).toFloat() * 0.86f)
            for (x in 0 until w) {
                val u = (x + 0.5f) / w * 2f - 1f
                val lick = fbm(u * 2.7f + seed, rise * 3.4f, seed + 9)
                var d = 1f - abs(u - sway) / max(width, 0.02f) + (lick - 0.5f) * 0.95f * (0.35f + rise) - rise * rise * 0.55f
                if (tt > 0.93f) d -= (tt - 0.93f) * 9f // tabanı yumuşat: düz çizgi olmasın
                if (d < 0.02f) continue
                if (rise > 0.5f && d < 0.20f + bayer(x, y) * 0.18f) continue // ucu dağılır
                val t = when { d > 0.64f -> 255; d > 0.42f -> 216; d > 0.22f -> 166; else -> 118 }
                px[y * w + x] = gray(t)
            }
        }
        return Bitmap.createBitmap(px, w, hgt, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ dört köşeli parıltı
    private fun sparkle(n: Int): Bitmap {
        val px = IntArray(n * n)
        val c = n / 2
        for (i in 0 until n) {
            val d = abs(i - c)
            val t = (255 - d * (255 / max(1, c))).coerceIn(60, 255)
            val thick = if (d < c / 3) 1 else 0
            for (o in -thick..thick) {
                px[c * n + i] = gray(t); px[i * n + c] = gray(t)
                if (c + o in 0 until n) { px[(c + o) * n + i] = gray((t * 0.7f).toInt().coerceAtLeast(60)); px[i * n + c + o] = gray((t * 0.7f).toInt().coerceAtLeast(60)) }
            }
        }
        // çapraz kısa kollar
        for (i in 1 until c / 2 + 1) { val t = 190 - i * 30; for ((sx, sy) in listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)) px[(c + sy * i) * n + c + sx * i] = gray(t.coerceAtLeast(70)) }
        px[c * n + c] = gray(255)
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ kırık parça
    private fun chunk(n: Int, seed: Int): Bitmap {
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        val cx = n / 2f; val cy = n / 2f
        val pts = 6
        val path = Path()
        for (i in 0 until pts) {
            val a = i * 2f * PI.toFloat() / pts + h(i, 1, seed) * 0.5f
            val r = n * (0.28f + 0.22f * h(i, 2, seed))
            val x = cx + cos(a) * r; val y = cy + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.color = gray(120); cv.drawPath(path, p)
        // üst-sol yüzey aydınlık, alt-sağ koyu (iki tonlu)
        cv.save(); cv.clipPath(path)
        p.color = gray(215); cv.drawRect(0f, 0f, cx + 1f, cy + 1f, p)
        p.color = gray(255); cv.drawRect(0f, 0f, cx - 1.5f, cy - 1.5f, p)
        p.color = gray(90); cv.drawRect(cx + 1.5f, cy + 1.5f, n.toFloat(), n.toFloat(), p)
        cv.restore()
        return bmp
    }

    // ------------------------------------------------------------------ leke (kan, yağ, çamur)
    private fun splat(n: Int, seed: Int): Bitmap {
        val px = IntArray(n * n)
        val c = n / 2f
        val spokes = 9
        val sp = FloatArray(spokes) { 0.35f + 0.65f * h(it, 3, seed) }
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - c) / c; val dy = (y + 0.5f - c) / c
            val r = sqrt(dx * dx + dy * dy)
            val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
            val f = (ang / (2f * PI.toFloat()) + 0.5f) * spokes
            val i0 = floor(f).toInt().mod(spokes); val i1 = (i0 + 1) % spokes
            val tt = f - floor(f)
            val spoke = sp[i0] + (sp[i1] - sp[i0]) * sm(tt)
            val edge = 0.30f + 0.12f * (fbm(dx * 3f, dy * 3f, seed) - 0.5f) + 0.55f * spoke * spoke * (1f - tt * 0.0f) * (0.55f + 0.45f * abs(sin(ang * spokes * 0.5f)))
            if (r < min(edge, 0.98f)) {
                val inner = r / edge
                val wet = if (dx < -0.05f && dy < -0.05f && inner < 0.55f && r > 0.06f) 0.30f else 0f
                px[y * n + x] = gray(tone(0.62f - inner * 0.30f + wet + bayer(x, y) * 0.10f))
            }
        }
        // saçılmış damlalar
        for (k in 0 until 7) {
            val a = h(k, 5, seed) * 2f * PI.toFloat(); val r = 0.62f + 0.32f * h(k, 6, seed)
            val x = (c + cos(a) * r * c).toInt(); val y = (c + sin(a) * r * c).toInt()
            if (x in 1 until n - 1 && y in 1 until n - 1) { px[y * n + x] = gray(150); px[y * n + x + 1] = gray(110) }
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ şok halkası (titreşimli kalın halka)
    private fun ring(n: Int): Bitmap {
        val px = IntArray(n * n)
        val c = n / 2f
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - c) / c; val dy = (y + 0.5f - c) / c
            val r = sqrt(dx * dx + dy * dy)
            val wob = (fbm(dx * 2.4f, dy * 2.4f, 21) - 0.5f) * 0.10f
            val m = abs(r - 0.82f - wob)
            val th = 0.11f
            if (m < th) {
                val k = 1f - m / th
                val dith = bayer(x, y) * 0.5f
                if (k + dith > 0.35f) px[y * n + x] = gray(tone(0.45f + k * 0.55f + dith * 0.2f))
            }
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ yayımlanan kitaplık
    val puffs: Array<Bitmap> by lazy { Array(8) { puff(44, 17 + it * 31) } }
    val flames: Array<Bitmap> by lazy { Array(6) { flame(26, 48, 5 + it * 19) } }
    val sparkle: Bitmap by lazy { sparkle(15) }
    val chunks: Array<Bitmap> by lazy { Array(5) { chunk(12, 3 + it * 13) } }
    val splats: Array<Bitmap> by lazy { Array(5) { splat(40, 9 + it * 23) } }
    val ring: Bitmap by lazy { ring(96) }
}
