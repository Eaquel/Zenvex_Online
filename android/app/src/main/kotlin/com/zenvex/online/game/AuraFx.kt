package com.zenvex.online.game

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Kahraman aurası: yerde dönen çok halkalı **rün mühürü** (çift dış halka, çentik halkası, 12 rün, ters dönen yıldız, nabız
 * dalgası) ve ondan yükselen ışık sütunları. Alev dilleri ile kor parçacıkları GameView'de bu mührün çevresinden doğar.
 * Mühür ayak merkezinde, yere yatık elips olarak (perspektif) toplamalı karışımla çizilir; iki geçiş: geniş soluk parıltı + ince parlak çizgi.
 */
object AuraFx {
    class Palette(val main: Int, val dark: Int, val hot: Int)

    /** 0 karanlık (karakter Sv10) · 1 gümüş-mavi (Sv25) · 2 kızıl-mor (Sv40) · 3 altın-kızıl (Sv50) */
    val PALETTES = arrayOf(
        Palette(0xFFB070FF.toInt(), 0xFF3A1478.toInt(), 0xFFEBD8FF.toInt()),
        Palette(0xFF9CC8FF.toInt(), 0xFF1E3E70.toInt(), 0xFFFFFFFF.toInt()),
        Palette(0xFFFF4A78.toInt(), 0xFF4A0E5A.toInt(), 0xFFFFC8D8.toInt()),
        Palette(0xFFFFC850.toInt(), 0xFF8A1A10.toInt(), 0xFFFFF4C0.toInt()),
    )

    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = add }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = add }
    private val path = Path()
    private val rect = RectF()

    // rün parçaları: birim kutuda çizgi dizileri (x1, y1, x2, y2); oyun dünyasındaki bir "yazı" izlenimi
    private val GLYPHS = arrayOf(
        floatArrayOf(0f, -1f, 0f, 1f, 0f, -0.2f, 0.7f, -0.7f, 0f, 0.3f, -0.7f, 0.8f),
        floatArrayOf(-0.7f, -1f, 0.7f, 1f, 0.7f, -1f, -0.7f, 1f, -0.9f, 0f, 0.9f, 0f),
        floatArrayOf(0f, -1f, 0.8f, 0f, 0.8f, 0f, 0f, 1f, 0f, 1f, -0.8f, 0f, -0.8f, 0f, 0f, -1f),
        floatArrayOf(-0.6f, -1f, -0.6f, 1f, -0.6f, -1f, 0.7f, -0.2f, 0.7f, -0.2f, -0.6f, 0.2f),
        floatArrayOf(0f, -1f, 0f, 1f, -0.8f, -0.4f, 0.8f, -0.4f, -0.5f, 1f, 0.5f, 1f),
        floatArrayOf(-0.8f, 1f, 0f, -1f, 0f, -1f, 0.8f, 1f, -0.5f, 0.2f, 0.5f, 0.2f),
    )

    private fun ellipse(c: Canvas, rx: Float, ry: Float, p: Paint) { rect.set(-rx, -ry, rx, ry); c.drawOval(rect, p) }

    private fun stroke(c: Canvas, w: Float, color: Int, a: Float, body: (Paint) -> Unit) {
        // geniş soluk parıltı + ince parlak çizgi
        glow.strokeWidth = w * 3.2f; glow.color = withA(color, a * 0.22f); body(glow)
        core.strokeWidth = w; core.color = withA(color, a); body(core)
    }

    private fun withA(c: Int, a: Float) = (c and 0xFFFFFF) or (((c ushr 24) * a.coerceIn(0f, 1f)).toInt() shl 24)

    /**
     * (sx, sy) ayak merkezi (ekran), [rx] yatay yarıçap (px), [t] saniye, [power] 0..1 ışıma gücü (seviyeye göre).
     */
    fun draw(c: Canvas, sx: Float, sy: Float, rx: Float, t: Float, pal: Palette, density: Float, power: Float) {
        val ry = rx * 0.5f
        val lw = max(1.2f, 1.6f * density)
        val pulse = 0.78f + 0.22f * sin(t * 3.1f)
        c.save(); c.translate(sx, sy)

        // 1) zemine oturan iz: koyu leke halkası (disk değil): mührün dış-iç bandı boyalı gibi görünür
        glow.strokeWidth = rx * 0.16f; glow.color = withA(pal.dark, 0.34f * power)
        glow.xfermode = null; ellipse(c, rx * 0.86f, ry * 0.86f, glow); glow.xfermode = add

        // 2) dış çift halka (biri dönen kesik çizgili)
        stroke(c, lw * 1.35f, pal.main, 0.95f * pulse * power) { ellipse(c, rx, ry, it) }
        stroke(c, lw, pal.main, 0.75f * power) { ellipse(c, rx * 0.93f, ry * 0.93f, it) }
        core.pathEffect = DashPathEffect(floatArrayOf(rx * 0.10f, rx * 0.07f), -t * rx * 0.35f)
        stroke(c, lw * 1.1f, pal.hot, 0.85f * power) { ellipse(c, rx * 0.80f, ry * 0.80f, it) }
        core.pathEffect = null; glow.pathEffect = null

        // 3) çentik halkası: 48 çentik, yavaş döner
        val ticks = 48
        val spin = t * 0.22f
        for (i in 0 until ticks) {
            val a = spin + i * 2f * PI.toFloat() / ticks
            val major = i % 4 == 0
            val r0 = if (major) 0.70f else 0.73f; val r1 = 0.79f
            val ca = cos(a); val sa = sin(a)
            val al = (if (major) 0.95f else 0.5f) * power
            core.strokeWidth = lw * (if (major) 1.1f else 0.8f); core.color = withA(pal.main, al)
            c.drawLine(ca * rx * r0, sa * ry * r0, ca * rx * r1, sa * ry * r1, core)
        }

        // 4) rün halkası: 12 rün ters yönde döner, her biri hafifçe titreşir
        val runeR = 0.60f
        val g = rx * 0.058f
        for (i in 0 until 12) {
            val a = -t * 0.32f + i * 2f * PI.toFloat() / 12f
            val ca = cos(a); val sa = sin(a)
            // rün: halka teğetine dik yönde okunur (merkeze doğru üstü)
            val flick = 0.65f + 0.35f * sin(t * 5.3f + i * 1.9f)
            val gl = GLYPHS[i % GLYPHS.size]
            path.reset()
            var k = 0
            while (k + 3 < gl.size) {
                // yerel (u, v) → halka uzayında: v merkeze bakan eksen
                fun mapX(u: Float, v: Float) = (ca * (runeR * rx / g - v) - sa * u) * g
                fun mapY(u: Float, v: Float) = (sa * (runeR * rx / g - v) + ca * u) * g * 0.5f
                path.moveTo(mapX(gl[k], gl[k + 1]), mapY(gl[k], gl[k + 1]))
                path.lineTo(mapX(gl[k + 2], gl[k + 3]), mapY(gl[k + 2], gl[k + 3]))
                k += 4
            }
            stroke(c, lw * 0.95f, pal.hot, 0.9f * flick * power) { c.drawPath(path, it) }
        }

        // 5) ters dönen yıldız (iki üçgen) + iç halka
        for (tri in 0 until 2) {
            path.reset()
            for (i in 0 until 3) {
                val a = (if (tri == 0) t * 0.5f else -t * 0.5f + PI.toFloat() / 3f) + i * 2f * PI.toFloat() / 3f
                val x = cos(a) * rx * 0.42f; val y = sin(a) * ry * 0.42f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            stroke(c, lw, if (tri == 0) pal.main else pal.hot, 0.8f * power) { c.drawPath(path, it) }
        }
        stroke(c, lw, pal.main, 0.7f * power) { ellipse(c, rx * 0.17f, ry * 0.17f, it) }
        // merkez nabzı
        glow.style = Paint.Style.FILL; glow.color = withA(pal.hot, 0.45f * pulse * power); ellipse(c, rx * 0.05f, ry * 0.05f, glow); glow.style = Paint.Style.STROKE

        // 6) nabız dalgası: her 2,4 sn'de içten dışa genişleyen halka
        val ph = (t % 2.4f) / 2.4f
        stroke(c, lw * (1.6f - ph), pal.hot, (1f - ph) * (1f - ph) * 0.9f * power) { ellipse(c, rx * (0.2f + 0.95f * ph), ry * (0.2f + 0.95f * ph), it) }

        // 7) ışık sütunları: mührün noktalarından yükselen ince çizgiler (altı parlak, üstü söner)
        for (i in 0 until 7) {
            val a = i * 2f * PI.toFloat() / 7f + t * 0.4f
            val x = cos(a) * rx * 0.88f; val y = sin(a) * ry * 0.88f
            val grow = 0.55f + 0.45f * sin(t * 2.2f + i * 1.3f)
            val len = rx * 0.55f * grow
            var seg = 0
            while (seg < 6) {
                val f0 = seg / 6f; val f1 = (seg + 1) / 6f
                val al = (1f - f0) * (1f - f0) * 0.7f * power
                core.strokeWidth = lw * (1.3f - f0); core.color = withA(pal.main, al)
                c.drawLine(x, y - len * f0, x, y - len * f1, core)
                seg++
            }
        }
        c.restore()
    }

    /** Mührün çevresinde rastgele bir nokta (dünya birimi, ayak merkezine göre): alev ve kor doğurmak için */
    fun ringPoint(r: Float): Pair<Float, Float> {
        val a = Math.random() * 2.0 * PI
        val rr = r * (0.55 + 0.4 * Math.random())
        return Pair((cos(a) * rr).toFloat(), (sin(a) * rr * 0.5).toFloat())
    }

    fun tierOf(playerLevel: Int, charLevel: Int): Int = when {
        playerLevel >= 50 -> 3
        playerLevel >= 40 -> 2
        playerLevel >= 25 -> 1
        charLevel >= 10 -> 0
        else -> -1
    }

}
