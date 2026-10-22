package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Çözünürlükten bağımsız vektör ikonlar (Canvas ile çizilir). */
@Composable
fun Icon(name: String, size: Dp, tint: Color = Pal.Text, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIcon(name, tint) }
}

private fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

fun DrawScope.drawIcon(name: String, c: Color) {
    val w = size.width
    val st = Stroke(w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(vararg pts: Float, close: Boolean = true): Path = Path().apply {
        moveTo(pts[0] * w, pts[1] * w)
        var i = 2
        while (i < pts.size) { lineTo(pts[i] * w, pts[i + 1] * w); i += 2 }
        if (close) close()
    }
    when (name) {
        "back" -> drawPath(path(0.62f, 0.2f, 0.32f, 0.5f, 0.62f, 0.8f, close = false), c, style = st)
        "next" -> drawPath(path(0.38f, 0.2f, 0.68f, 0.5f, 0.38f, 0.8f, close = false), c, style = st)
        "plus" -> { drawLine(c, p(0.5f, 0.2f), p(0.5f, 0.8f), w * 0.14f, StrokeCap.Round); drawLine(c, p(0.2f, 0.5f), p(0.8f, 0.5f), w * 0.14f, StrokeCap.Round) }
        "minus" -> drawLine(c, p(0.2f, 0.5f), p(0.8f, 0.5f), w * 0.14f, StrokeCap.Round)
        "check" -> drawPath(path(0.2f, 0.52f, 0.42f, 0.74f, 0.82f, 0.28f, close = false), c, style = st)
        "close" -> { drawLine(c, p(0.25f, 0.25f), p(0.75f, 0.75f), w * 0.12f, StrokeCap.Round); drawLine(c, p(0.75f, 0.25f), p(0.25f, 0.75f), w * 0.12f, StrokeCap.Round) }
        "coin" -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A8), Color(0xFFFFC23A), Color(0xFFB87A0C)), center = p(0.4f, 0.35f), radius = w * 0.55f), w * 0.42f)
            drawCircle(Color(0xFF8A5A08), w * 0.3f, style = Stroke(w * 0.05f))
            drawPath(path(0.5f, 0.3f, 0.56f, 0.45f, 0.7f, 0.46f, 0.59f, 0.56f, 0.63f, 0.71f, 0.5f, 0.62f, 0.37f, 0.71f, 0.41f, 0.56f, 0.3f, 0.46f, 0.44f, 0.45f), Color(0xFFFFF4C0))
        }
        "gem" -> {
            val g = path(0.5f, 0.88f, 0.1f, 0.38f, 0.28f, 0.14f, 0.72f, 0.14f, 0.9f, 0.38f)
            drawPath(g, Brush.linearGradient(listOf(Color(0xFFB8F4FF), Color(0xFF2AA8E8), Color(0xFF1A5AB0)), p(0.2f, 0.1f), p(0.8f, 0.9f)))
            drawPath(path(0.1f, 0.38f, 0.9f, 0.38f, close = false), Color.White.copy(alpha = 0.6f), style = thin)
            drawPath(path(0.35f, 0.38f, 0.5f, 0.88f, 0.65f, 0.38f, close = false), Color.White.copy(alpha = 0.35f), style = thin)
        }
        "star" -> star(c, 0.5f, 0.52f, 0.42f, 0.18f)
        "sword", "attack" -> {
            drawLine(c, p(0.22f, 0.78f), p(0.8f, 0.2f), w * 0.11f, StrokeCap.Round)
            drawLine(c, p(0.18f, 0.55f), p(0.45f, 0.82f), w * 0.11f, StrokeCap.Round)
            drawLine(c, p(0.12f, 0.88f), p(0.25f, 0.75f), w * 0.13f, StrokeCap.Round)
        }
        "bag" -> { // sırt çantası: kapak, ön cep, askılar
            drawRoundRect(c, p(0.2f, 0.22f), Size(w * 0.6f, w * 0.68f), CornerRadius(w * 0.16f))
            drawRoundRect(Color.Black.copy(alpha = 0.28f), p(0.2f, 0.22f), Size(w * 0.6f, w * 0.24f), CornerRadius(w * 0.16f))
            drawRoundRect(Color.Black.copy(alpha = 0.3f), p(0.3f, 0.56f), Size(w * 0.4f, w * 0.24f), CornerRadius(w * 0.06f))
            drawLine(c.copy(alpha = 0.9f), p(0.36f, 0.6f), p(0.64f, 0.6f), w * 0.05f)
            drawArc(c, 180f, 180f, false, p(0.36f, 0.08f), Size(w * 0.28f, w * 0.26f), style = thin)
            drawRect(Color.Black.copy(alpha = 0.45f), p(0.46f, 0.4f), Size(w * 0.08f, w * 0.1f))
        }
        "craft", "hammer" -> {
            drawLine(c, p(0.25f, 0.82f), p(0.6f, 0.42f), w * 0.11f, StrokeCap.Round)
            rotate(45f, p(0.6f, 0.36f)) { drawRoundRect(c, p(0.38f, 0.26f), Size(w * 0.46f, w * 0.2f), CornerRadius(w * 0.05f)) }
        }
        "shop" -> {
            drawPath(path(0.12f, 0.4f, 0.88f, 0.4f, 0.8f, 0.86f, 0.2f, 0.86f), c)
            drawArc(c, 180f, 180f, false, p(0.3f, 0.14f), Size(w * 0.4f, w * 0.48f), style = st)
        }
        "quest", "scroll" -> {
            drawRoundRect(c, p(0.22f, 0.14f), Size(w * 0.56f, w * 0.72f), CornerRadius(w * 0.08f), style = st)
            for (k in 0..2) drawLine(c, p(0.34f, 0.34f + k * 0.16f), p(0.66f, 0.34f + k * 0.16f), w * 0.07f, StrokeCap.Round)
        }
        "trophy", "pass" -> {
            drawPath(path(0.26f, 0.14f, 0.74f, 0.14f, 0.7f, 0.42f, 0.5f, 0.58f, 0.3f, 0.42f), c)
            drawArc(c, 90f, 180f, false, p(0.1f, 0.18f), Size(w * 0.3f, w * 0.26f), style = thin)
            drawArc(c, 270f, 180f, false, p(0.6f, 0.18f), Size(w * 0.3f, w * 0.26f), style = thin)
            drawRect(c, p(0.44f, 0.56f), Size(w * 0.12f, w * 0.16f))
            drawRoundRect(c, p(0.28f, 0.72f), Size(w * 0.44f, w * 0.12f), CornerRadius(w * 0.04f))
        }
        "gear", "settings" -> {
            for (k in 0 until 8) rotate(k * 45f) { drawRoundRect(c, p(0.43f, 0.06f), Size(w * 0.14f, w * 0.2f), CornerRadius(w * 0.03f)) }
            drawCircle(c, w * 0.3f)
            drawCircle(Color.Black.copy(alpha = 0.55f), w * 0.12f)
        }
        "chat" -> {
            drawRoundRect(c, p(0.1f, 0.16f), Size(w * 0.8f, w * 0.54f), CornerRadius(w * 0.18f))
            drawPath(path(0.28f, 0.6f, 0.22f, 0.88f, 0.48f, 0.66f), c)
            for (k in 0..2) drawCircle(Color.Black.copy(alpha = 0.45f), w * 0.05f, p(0.32f + k * 0.18f, 0.43f))
        }
        "user", "profile" -> { drawCircle(c, w * 0.18f, p(0.5f, 0.32f)); drawArc(c, 180f, 180f, true, p(0.16f, 0.54f), Size(w * 0.68f, w * 0.64f)) }
        "people" -> {
            drawCircle(c.copy(alpha = 0.6f), w * 0.13f, p(0.68f, 0.32f)); drawArc(c.copy(alpha = 0.6f), 180f, 180f, true, p(0.46f, 0.5f), Size(w * 0.46f, w * 0.48f))
            drawCircle(c, w * 0.15f, p(0.38f, 0.36f)); drawArc(c, 180f, 180f, true, p(0.1f, 0.56f), Size(w * 0.56f, w * 0.52f))
        }
        "play" -> drawPath(path(0.3f, 0.16f, 0.84f, 0.5f, 0.3f, 0.84f), c)
        "lock" -> {
            drawArc(c, 180f, 180f, false, p(0.3f, 0.14f), Size(w * 0.4f, w * 0.44f), style = st)
            drawRoundRect(c, p(0.2f, 0.4f), Size(w * 0.6f, w * 0.46f), CornerRadius(w * 0.08f))
        }
        "heart" -> {
            val h = Path().apply {
                moveTo(0.5f * w, 0.85f * w)
                cubicTo(0.05f * w, 0.55f * w, 0.1f * w, 0.12f * w, 0.5f * w, 0.3f * w)
                cubicTo(0.9f * w, 0.12f * w, 0.95f * w, 0.55f * w, 0.5f * w, 0.85f * w)
                close()
            }
            drawPath(h, c)
        }
        "shield" -> {
            val s = Path().apply {
                moveTo(0.5f * w, 0.1f * w); lineTo(0.84f * w, 0.22f * w)
                cubicTo(0.84f * w, 0.6f * w, 0.7f * w, 0.78f * w, 0.5f * w, 0.9f * w)
                cubicTo(0.3f * w, 0.78f * w, 0.16f * w, 0.6f * w, 0.16f * w, 0.22f * w); close()
            }
            drawPath(s, c)
            drawPath(path(0.5f, 0.2f, 0.5f, 0.8f, close = false), Color.Black.copy(alpha = 0.25f), style = thin)
        }
        "bolt" -> drawPath(path(0.58f, 0.08f, 0.22f, 0.56f, 0.46f, 0.56f, 0.38f, 0.92f, 0.78f, 0.42f, 0.54f, 0.42f), c)
        "map" -> drawPath(path(0.1f, 0.22f, 0.36f, 0.12f, 0.64f, 0.22f, 0.9f, 0.12f, 0.9f, 0.78f, 0.64f, 0.88f, 0.36f, 0.78f, 0.1f, 0.88f), c, style = thin)
        "crown" -> drawPath(path(0.12f, 0.76f, 0.1f, 0.3f, 0.32f, 0.5f, 0.5f, 0.2f, 0.68f, 0.5f, 0.9f, 0.3f, 0.88f, 0.76f), c)
        "gift" -> {
            drawRoundRect(c, p(0.14f, 0.4f), Size(w * 0.72f, w * 0.48f), CornerRadius(w * 0.06f))
            drawRoundRect(c, p(0.1f, 0.28f), Size(w * 0.8f, w * 0.16f), CornerRadius(w * 0.05f))
            drawRect(Color.Black.copy(alpha = 0.35f), p(0.45f, 0.28f), Size(w * 0.1f, w * 0.6f))
            drawCircle(c, w * 0.1f, p(0.38f, 0.2f), style = thin); drawCircle(c, w * 0.1f, p(0.62f, 0.2f), style = thin)
        }
        "fist" -> {
            drawRoundRect(c, p(0.2f, 0.3f), Size(w * 0.56f, w * 0.5f), CornerRadius(w * 0.16f))
            for (k in 0..3) drawLine(Color.Black.copy(alpha = 0.35f), p(0.3f + k * 0.13f, 0.32f), p(0.3f + k * 0.13f, 0.5f), w * 0.04f)
            drawRoundRect(c, p(0.62f, 0.45f), Size(w * 0.22f, w * 0.18f), CornerRadius(w * 0.08f))
        }
        "magic", "skill" -> {
            star(c, 0.38f, 0.45f, 0.3f, 0.12f)
            star(c.copy(alpha = 0.7f), 0.74f, 0.72f, 0.16f, 0.06f)
            star(c.copy(alpha = 0.7f), 0.76f, 0.22f, 0.12f, 0.05f)
        }
        "wood" -> {
            drawRoundRect(Color(0xFFA0703E), p(0.12f, 0.36f), Size(w * 0.76f, w * 0.32f), CornerRadius(w * 0.16f))
            drawCircle(Color(0xFFE0B880), w * 0.15f, p(0.74f, 0.52f)); drawCircle(Color(0xFFA0703E), w * 0.08f, p(0.74f, 0.52f), style = thin)
        }
        "refresh" -> {
            drawArc(c, 30f, 290f, false, p(0.2f, 0.2f), Size(w * 0.6f, w * 0.6f), style = st)
            drawPath(path(0.72f, 0.12f, 0.86f, 0.42f, 0.56f, 0.38f), c)
        }
        "trash" -> {
            drawRoundRect(c, p(0.24f, 0.3f), Size(w * 0.52f, w * 0.58f), CornerRadius(w * 0.06f))
            drawLine(c, p(0.16f, 0.24f), p(0.84f, 0.24f), w * 0.09f, StrokeCap.Round)
            drawLine(c, p(0.4f, 0.14f), p(0.6f, 0.14f), w * 0.09f, StrokeCap.Round)
        }
        "info" -> { drawCircle(c, w * 0.42f, style = thin); drawLine(c, p(0.5f, 0.45f), p(0.5f, 0.72f), w * 0.1f, StrokeCap.Round); drawCircle(c, w * 0.06f, p(0.5f, 0.3f)) }
        "flame" -> {
            val f = Path().apply {
                moveTo(0.5f * w, 0.08f * w)
                cubicTo(0.9f * w, 0.4f * w, 0.85f * w, 0.9f * w, 0.5f * w, 0.92f * w)
                cubicTo(0.15f * w, 0.9f * w, 0.1f * w, 0.5f * w, 0.36f * w, 0.36f * w)
                cubicTo(0.38f * w, 0.5f * w, 0.48f * w, 0.52f * w, 0.5f * w, 0.08f * w); close()
            }
            drawPath(f, Brush.verticalGradient(listOf(Color(0xFFFFE070), Color(0xFFFF5A20)), 0f, w))
        }
        "skull" -> {
            drawCircle(c, w * 0.32f, p(0.5f, 0.42f))
            drawRoundRect(c, p(0.32f, 0.56f), Size(w * 0.36f, w * 0.3f), CornerRadius(w * 0.06f))
            drawCircle(Color.Black.copy(alpha = 0.7f), w * 0.09f, p(0.38f, 0.44f)); drawCircle(Color.Black.copy(alpha = 0.7f), w * 0.09f, p(0.62f, 0.44f))
        }
        "clock" -> {
            drawCircle(c, w * 0.36f, p(0.5f, 0.5f), style = thin)
            drawLine(c, p(0.5f, 0.5f), p(0.5f, 0.28f), w * 0.08f, StrokeCap.Round)
            drawLine(c, p(0.5f, 0.5f), p(0.66f, 0.58f), w * 0.08f, StrokeCap.Round)
        }
        "calendar" -> {
            drawRoundRect(c, p(0.14f, 0.2f), Size(w * 0.72f, w * 0.66f), CornerRadius(w * 0.1f), style = thin)
            drawRect(c, p(0.14f, 0.2f), Size(w * 0.72f, w * 0.16f))
            for (k in 0..2) for (j in 0..1) drawCircle(c, w * 0.05f, p(0.3f + k * 0.2f, 0.52f + j * 0.18f))
        }
        "shirt" -> drawPath(path(0.32f, 0.14f, 0.12f, 0.28f, 0.22f, 0.46f, 0.3f, 0.42f, 0.3f, 0.88f, 0.7f, 0.88f, 0.7f, 0.42f, 0.78f, 0.46f, 0.88f, 0.28f, 0.68f, 0.14f, 0.5f, 0.24f), c)
        "menu" -> for (k in 0..2) drawLine(c, p(0.2f, 0.3f + k * 0.2f), p(0.8f, 0.3f + k * 0.2f), w * 0.1f, StrokeCap.Round)
        "exit" -> {
            drawRoundRect(c, p(0.16f, 0.14f), Size(w * 0.42f, w * 0.72f), CornerRadius(w * 0.06f), style = thin)
            drawLine(c, p(0.42f, 0.5f), p(0.88f, 0.5f), w * 0.09f, StrokeCap.Round)
            drawPath(path(0.72f, 0.34f, 0.88f, 0.5f, 0.72f, 0.66f, close = false), c, style = thin)
        }
        "car" -> {
            drawRoundRect(c, p(0.08f, 0.44f), Size(w * 0.84f, w * 0.26f), CornerRadius(w * 0.1f))
            drawPath(path(0.24f, 0.46f, 0.34f, 0.24f, 0.66f, 0.24f, 0.78f, 0.46f), c)
            drawCircle(Color.Black.copy(alpha = 0.8f), w * 0.1f, p(0.28f, 0.72f)); drawCircle(Color.Black.copy(alpha = 0.8f), w * 0.1f, p(0.72f, 0.72f))
        }
        "volume" -> {
            drawPath(path(0.12f, 0.38f, 0.3f, 0.38f, 0.52f, 0.18f, 0.52f, 0.82f, 0.3f, 0.62f, 0.12f, 0.62f), c)
            drawArc(c, -45f, 90f, false, p(0.46f, 0.3f), Size(w * 0.32f, w * 0.4f), style = thin)
        }
        "eye" -> {
            drawOval(c, p(0.08f, 0.28f), Size(w * 0.84f, w * 0.44f), style = thin)
            drawCircle(c, w * 0.13f)
        }
        "globe" -> {
            drawCircle(c, w * 0.4f, style = thin)
            drawOval(c, p(0.32f, 0.1f), Size(w * 0.36f, w * 0.8f), style = thin)
            drawLine(c, p(0.1f, 0.5f), p(0.9f, 0.5f), w * 0.06f)
        }
        "pin" -> {
            drawCircle(c, w * 0.26f, p(0.5f, 0.38f))
            drawPath(path(0.28f, 0.48f, 0.5f, 0.92f, 0.72f, 0.48f), c)
            drawCircle(Color.Black.copy(alpha = 0.5f), w * 0.1f, p(0.5f, 0.38f))
        }
        "hanger" -> {
            drawPath(path(0.1f, 0.78f, 0.5f, 0.46f, 0.9f, 0.78f), c, style = thin)
            drawArc(c, 180f, 270f, false, p(0.4f, 0.14f), Size(w * 0.2f, w * 0.22f), style = thin)
        }
        "trade" -> {
            drawPath(path(0.15f, 0.38f, 0.85f, 0.38f, 0.66f, 0.2f, close = false), c, style = st)
            drawPath(path(0.85f, 0.62f, 0.15f, 0.62f, 0.34f, 0.8f, close = false), c, style = st)
        }
        "paw" -> {
            drawOval(c, p(0.3f, 0.5f), Size(w * 0.4f, w * 0.34f))
            for ((x, y) in listOf(0.22f to 0.36f, 0.38f to 0.22f, 0.6f to 0.22f, 0.76f to 0.36f)) drawCircle(c, w * 0.09f, p(x, y))
        }
        "mail" -> {
            drawRoundRect(c, p(0.1f, 0.22f), Size(w * 0.8f, w * 0.56f), CornerRadius(w * 0.06f))
            drawPath(path(0.12f, 0.26f, 0.5f, 0.56f, 0.88f, 0.26f, close = false), Color.Black.copy(alpha = 0.55f), style = thin)
        }
        "radio" -> {
            drawRoundRect(c, p(0.28f, 0.3f), Size(w * 0.44f, w * 0.62f), CornerRadius(w * 0.08f))
            drawLine(c, p(0.62f, 0.32f), p(0.62f, 0.06f), w * 0.07f, StrokeCap.Round)
            for (k in 0..2) drawLine(Color.Black.copy(alpha = 0.5f), p(0.36f, 0.5f + k * 0.12f), p(0.64f, 0.5f + k * 0.12f), w * 0.05f)
        }
        "dev" -> {
            drawPath(path(0.34f, 0.26f, 0.12f, 0.5f, 0.34f, 0.74f, close = false), c, style = st)
            drawPath(path(0.66f, 0.26f, 0.88f, 0.5f, 0.66f, 0.74f, close = false), c, style = st)
            drawLine(c, p(0.56f, 0.2f), p(0.44f, 0.8f), w * 0.08f, StrokeCap.Round)
        }
        else -> drawCircle(c, w * 0.3f)
    }
}

private fun DrawScope.star(c: Color, cx: Float, cy: Float, r: Float, ri: Float) {
    val w = size.width
    val pth = Path()
    for (k in 0 until 10) {
        val a = -PI / 2 + k * PI / 5
        val rr = if (k % 2 == 0) r else ri
        val x = (cx + cos(a).toFloat() * rr) * w; val y = (cy + sin(a).toFloat() * rr) * w
        if (k == 0) pth.moveTo(x, y) else pth.lineTo(x, y)
    }
    pth.close()
    drawPath(pth, c)
}
