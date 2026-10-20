package com.zenvex.online.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Skill ağacı simgeleri: her istatistik türü için kodla çizilen vektör glif (görsel dosyası yok, her boyutta keskin).
 * Aile renkleri: hasar kırmızı, kritik sarı, silah turuncu, can yeşil, savunma çelik mavi, çeviklik camgöbeği.
 */
object SkillGlyphs {
    val Damage = Color(0xFFE85A3C)
    val Crit = Color(0xFFF4C542)
    val Weapon = Color(0xFFF09040)
    val Life = Color(0xFF5CCB6E)
    val Defense = Color(0xFF8FB4D8)
    val Agility = Color(0xFF5FD0F0)
    val Core = Color(0xFFE8E0C8)

    fun family(stat: String): Color = when (stat) {
        "MeleeFlat", "FistPct", "DmgPct", "Stun" -> Damage
        "Crit", "CritDmg" -> Crit
        "BladePct", "Reach", "AtkSpeed" -> Weapon
        "HpFlat", "Regen", "Lifesteal", "LastBreath" -> Life
        "DmgTaken", "Reflect", "BlastRes" -> Defense
        "Dodge", "Speed", "RollCd", "Stamina", "IFrame" -> Agility
        else -> Core
    }

    fun familyName(stat: String): String = when (family(stat)) {
        Damage -> "Hasar"; Crit -> "Kritik"; Weapon -> "Silah"; Life -> "Can"; Defense -> "Savunma"; Agility -> "Çeviklik"; else -> "Merkez"
    }

    /** c merkezli, r yarıçaplı kutuya glif çizer. */
    fun DrawScope.glyph(stat: String, c: Offset, r: Float, col: Color) {
        val w = r * 0.16f
        fun p(x: Float, y: Float) = Offset(c.x + x * r, c.y + y * r)
        fun path(vararg pts: Pair<Float, Float>, close: Boolean = true) = Path().apply {
            moveTo(c.x + pts[0].first * r, c.y + pts[0].second * r)
            for (i in 1 until pts.size) lineTo(c.x + pts[i].first * r, c.y + pts[i].second * r)
            if (close) close()
        }
        val dark = Color(0xCC0A0908)
        when (stat) {
            "MeleeFlat", "FistPct" -> { // yumruk: dört parmak boğumu + avuç + başparmak
                for (i in 0..3) drawRoundRect(col, Offset(c.x + (-0.62f + i * 0.31f) * r, c.y - 0.62f * r), Size(0.28f * r, 0.5f * r),
                    androidx.compose.ui.geometry.CornerRadius(0.1f * r))
                drawRoundRect(col, Offset(c.x - 0.62f * r, c.y - 0.25f * r), Size(1.22f * r, 0.75f * r), androidx.compose.ui.geometry.CornerRadius(0.18f * r))
                drawRoundRect(col, Offset(c.x - 0.78f * r, c.y - 0.15f * r), Size(0.36f * r, 0.5f * r), androidx.compose.ui.geometry.CornerRadius(0.15f * r))
                for (i in 0..2) drawLine(dark, p(-0.33f + i * 0.31f, -0.55f), p(-0.33f + i * 0.31f, -0.2f), w * 0.6f)
            }
            "DmgPct" -> { // çapraz iki kılıç
                for (s in listOf(1f, -1f)) {
                    drawLine(col, p(-0.7f * s, 0.7f), p(0.65f * s, -0.65f), w * 1.3f, StrokeCap.Round)
                    drawLine(col, p(-0.55f * s, 0.25f), p(-0.2f * s, 0.6f), w * 1.2f, StrokeCap.Round)
                }
            }
            "BladePct" -> { // katana: ince kavisli bıçak + tsuba + kabza
                drawLine(col, p(-0.55f, 0.55f), p(0.72f, -0.72f), w * 1.4f, StrokeCap.Round)
                drawCircle(col, 0.16f * r, p(-0.38f, 0.38f))
                drawLine(col, p(-0.42f, 0.42f), p(-0.82f, 0.82f), w * 1.8f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.6f), p(-0.1f, 0.05f), p(0.55f, -0.58f), w * 0.5f)
            }
            "Reach" -> { // iki uçlu ok
                drawLine(col, p(-0.75f, 0f), p(0.75f, 0f), w * 1.3f)
                drawPath(path(-0.85f to 0f, -0.45f to -0.32f, -0.45f to 0.32f), col)
                drawPath(path(0.85f to 0f, 0.45f to -0.32f, 0.45f to 0.32f), col)
            }
            "AtkSpeed" -> { // bıçak + hız çizgileri
                drawLine(col, p(-0.2f, 0.6f), p(0.7f, -0.6f), w * 1.4f, StrokeCap.Round)
                for (i in 0..2) drawLine(col.copy(alpha = 0.8f), p(-0.85f, -0.35f + i * 0.3f), p(-0.3f, -0.35f + i * 0.3f), w * 0.8f, StrokeCap.Round)
            }
            "Crit" -> drawPath(path(0.15f to -0.9f, -0.5f to 0.1f, -0.05f to 0.1f, -0.25f to 0.9f, 0.5f to -0.15f, 0.05f to -0.15f), col) // şimşek
            "CritDmg" -> { // 8 köşeli patlama yıldızı
                val pp = Path()
                for (i in 0 until 16) {
                    val a = i * PI.toFloat() / 8; val rr = if (i % 2 == 0) 0.9f else 0.4f
                    val x = c.x + cos(a) * rr * r; val y = c.y + sin(a) * rr * r
                    if (i == 0) pp.moveTo(x, y) else pp.lineTo(x, y)
                }
                pp.close(); drawPath(pp, col); drawCircle(dark, 0.18f * r, c)
            }
            "Stun" -> { // baş dönmesi: üç yıldız yayı
                for (i in 0..2) {
                    val a = -PI.toFloat() * (0.15f + i * 0.35f)
                    star(Offset(c.x + cos(a) * 0.55f * r, c.y + sin(a) * 0.35f * r + 0.15f * r), 0.28f * r, col)
                }
                drawArc(col.copy(alpha = 0.6f), 200f, 140f, false, Offset(c.x - 0.7f * r, c.y - 0.3f * r), Size(1.4f * r, 0.9f * r), style = Stroke(w * 0.7f))
            }
            "Lifesteal" -> { // kan damlası + diş
                val d = Path().apply {
                    moveTo(c.x, c.y - 0.85f * r)
                    cubicTo(c.x + 0.7f * r, c.y - 0.1f * r, c.x + 0.6f * r, c.y + 0.75f * r, c.x, c.y + 0.75f * r)
                    cubicTo(c.x - 0.6f * r, c.y + 0.75f * r, c.x - 0.7f * r, c.y - 0.1f * r, c.x, c.y - 0.85f * r)
                }
                drawPath(d, Color(0xFFE04848))
                drawPath(path(-0.25f to 0.05f, -0.1f to 0.05f, -0.17f to 0.4f), Color.White)
                drawPath(path(0.1f to 0.05f, 0.25f to 0.05f, 0.17f to 0.4f), Color.White)
            }
            "HpFlat" -> heart(c, r, col)
            "LastBreath" -> { // kalp + hale
                heart(Offset(c.x, c.y + 0.12f * r), r * 0.85f, col)
                drawOval(Color(0xFFFFE680), Offset(c.x - 0.5f * r, c.y - 0.95f * r), Size(1f * r, 0.32f * r), style = Stroke(w * 0.8f))
            }
            "Regen" -> { // artı + yaprak
                drawLine(col, p(-0.2f, -0.65f), p(-0.2f, 0.45f), w * 2f); drawLine(col, p(-0.75f, -0.1f), p(0.35f, -0.1f), w * 2f)
                drawOval(col, Offset(c.x + 0.25f * r, c.y + 0.2f * r), Size(0.55f * r, 0.35f * r))
            }
            "DmgTaken" -> shield(c, r, col, dark)
            "Reflect" -> { // kalkan + seken ok
                shield(c, r * 0.9f, col, dark)
                drawLine(Color.White, p(-0.95f, -0.8f), p(-0.05f, -0.1f), w)
                drawLine(Color.White, p(-0.05f, -0.1f), p(0.85f, -0.75f), w)
                drawPath(path(0.95f to -0.85f, 0.6f to -0.8f, 0.82f to -0.55f), Color.White)
            }
            "BlastRes" -> { // bomba + kalkan çizgisi
                drawCircle(col, 0.55f * r, Offset(c.x - 0.1f * r, c.y + 0.15f * r))
                drawLine(col, p(0.2f, -0.25f), p(0.5f, -0.6f), w * 1.2f)
                star(p(0.6f, -0.72f), 0.25f * r, Color(0xFFFFB040))
                drawArc(Color.White.copy(alpha = 0.7f), 200f, 70f, false, Offset(c.x - 0.5f * r, c.y - 0.25f * r), Size(0.6f * r, 0.6f * r), style = Stroke(w * 0.6f))
            }
            "Dodge" -> { // hayalet
                val g = Path().apply {
                    moveTo(c.x - 0.6f * r, c.y + 0.8f * r)
                    lineTo(c.x - 0.6f * r, c.y - 0.1f * r)
                    cubicTo(c.x - 0.6f * r, c.y - 0.95f * r, c.x + 0.6f * r, c.y - 0.95f * r, c.x + 0.6f * r, c.y - 0.1f * r)
                    lineTo(c.x + 0.6f * r, c.y + 0.8f * r)
                    lineTo(c.x + 0.3f * r, c.y + 0.55f * r); lineTo(c.x, c.y + 0.8f * r); lineTo(c.x - 0.3f * r, c.y + 0.55f * r); close()
                }
                drawPath(g, col)
                drawCircle(dark, 0.12f * r, p(-0.22f, -0.15f)); drawCircle(dark, 0.12f * r, p(0.22f, -0.15f))
            }
            "Speed" -> { // kanatlı bot
                drawPath(path(-0.45f to -0.7f, 0.05f to -0.7f, 0.05f to 0.25f, 0.75f to 0.4f, 0.75f to 0.75f, -0.45f to 0.75f), col)
                for (i in 0..2) drawLine(col.copy(alpha = 0.85f), p(-0.5f, -0.5f + i * 0.22f), p(-0.95f, -0.65f + i * 0.2f), w * 0.9f, StrokeCap.Round)
            }
            "RollCd" -> { // dönen oklar
                drawArc(col, -60f, 260f, false, Offset(c.x - 0.65f * r, c.y - 0.65f * r), Size(1.3f * r, 1.3f * r), style = Stroke(w * 1.4f))
                val a = (200f * PI / 180f).toFloat()
                val tip = Offset(c.x + cos(a) * 0.65f * r, c.y + sin(a) * 0.65f * r)
                drawPath(Path().apply { moveTo(tip.x - 0.35f * r, tip.y - 0.05f * r); lineTo(tip.x + 0.1f * r, tip.y - 0.35f * r); lineTo(tip.x + 0.15f * r, tip.y + 0.2f * r); close() }, col)
            }
            "Stamina" -> { // rüzgâr çizgileri
                for (i in 0..2) drawArc(col, 180f, 200f, false, Offset(c.x - (0.75f - i * 0.12f) * r, c.y + (-0.7f + i * 0.45f) * r),
                    Size((1.3f - i * 0.25f) * r, 0.5f * r), style = Stroke(w * 1.1f, cap = StrokeCap.Round))
            }
            "IFrame" -> { // kum saati
                drawPath(path(-0.55f to -0.8f, 0.55f to -0.8f, 0f to 0f), col)
                drawPath(path(-0.55f to 0.8f, 0.55f to 0.8f, 0f to 0f), col)
                drawLine(col, p(-0.65f, -0.85f), p(0.65f, -0.85f), w * 1.2f); drawLine(col, p(-0.65f, 0.85f), p(0.65f, 0.85f), w * 1.2f)
            }
            else -> { // merkez: kafatası
                drawCircle(col, 0.6f * r, Offset(c.x, c.y - 0.12f * r))
                drawRoundRect(col, Offset(c.x - 0.35f * r, c.y + 0.2f * r), Size(0.7f * r, 0.45f * r), androidx.compose.ui.geometry.CornerRadius(0.1f * r))
                drawCircle(dark, 0.17f * r, p(-0.22f, -0.15f)); drawCircle(dark, 0.17f * r, p(0.22f, -0.15f))
                drawPath(path(0f to 0.05f, -0.08f to 0.22f, 0.08f to 0.22f), dark)
            }
        }
    }

    private fun DrawScope.star(c: Offset, r: Float, col: Color) {
        val pp = Path()
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5; val rr = if (i % 2 == 0) r else r * 0.45f
            val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
            if (i == 0) pp.moveTo(x, y) else pp.lineTo(x, y)
        }
        pp.close(); drawPath(pp, col)
    }

    private fun DrawScope.heart(c: Offset, r: Float, col: Color) {
        val h = Path().apply {
            moveTo(c.x, c.y + 0.8f * r)
            cubicTo(c.x - 1.1f * r, c.y, c.x - 0.6f * r, c.y - 0.95f * r, c.x, c.y - 0.35f * r)
            cubicTo(c.x + 0.6f * r, c.y - 0.95f * r, c.x + 1.1f * r, c.y, c.x, c.y + 0.8f * r)
            close()
        }
        drawPath(h, col)
        drawCircle(Color.White.copy(alpha = 0.55f), 0.13f * r, Offset(c.x - 0.38f * r, c.y - 0.3f * r))
    }

    private fun DrawScope.shield(c: Offset, r: Float, col: Color, dark: Color) {
        val s = Path().apply {
            moveTo(c.x, c.y - 0.85f * r)
            lineTo(c.x + 0.7f * r, c.y - 0.55f * r)
            cubicTo(c.x + 0.7f * r, c.y + 0.25f * r, c.x + 0.35f * r, c.y + 0.65f * r, c.x, c.y + 0.9f * r)
            cubicTo(c.x - 0.35f * r, c.y + 0.65f * r, c.x - 0.7f * r, c.y + 0.25f * r, c.x - 0.7f * r, c.y - 0.55f * r)
            close()
        }
        drawPath(s, col)
        drawLine(dark, Offset(c.x, c.y - 0.7f * r), Offset(c.x, c.y + 0.7f * r), r * 0.1f)
    }

    /** Anahtar düğüm çerçevesi: dikenli sekizgen. */
    fun DrawScope.keystoneFrame(c: Offset, r: Float, fill: Color, edge: Color, width: Float, spin: Float) {
        rotate(spin, c) {
            val pp = Path()
            for (i in 0 until 16) {
                val a = i * PI.toFloat() / 8 + PI.toFloat() / 16
                val rr = if (i % 2 == 0) r * 1.18f else r * 0.98f
                val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
                if (i == 0) pp.moveTo(x, y) else pp.lineTo(x, y)
            }
            pp.close()
            drawPath(pp, fill)
            drawPath(pp, edge, style = Stroke(width))
        }
    }
}
