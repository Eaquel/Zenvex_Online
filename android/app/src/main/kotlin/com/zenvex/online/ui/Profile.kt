package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zenvex.online.game.Account
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =========================================================================== profil fotoğrafları
/** Profil fotoğrafı: atlas görseli + kadraj (üstten alınan yükseklik oranı; insanlarda baş-omuz). */
/** [pick]: seçilebilir mi (yalnız NPC görselleri; eski kahraman/asker/köpek/zombi resimleri sunucudaki sıra numaraları bozulmasın diye listede kalır) */
data class AvatarDef(val key: String, val name: String, val crop: Float, val pick: Boolean = true,
                     /** yüz merkezi (görsel pikseli) ve kare kırpma kenarı: hepsi aynı oranda baş + omuz gösterir */
                     val fx: Float = -1f, val fy: Float = -1f, val side: Float = 42f)

val AVATARS = listOf(
    AvatarDef("l/kaan/0_0", "Savaşçı", 0.42f, false),
    AvatarDef("npc/merchant", "Tüccar", 0.4f, fx = 51f, fy = 29f), AvatarDef("npc/quest", "Görevci", 0.4f, fx = 24f, fy = 21f),
    AvatarDef("npc/doctor", "Doktor", 0.4f, fx = 37f, fy = 16f),
    AvatarDef("npc/mechanic", "Tamirci", 0.4f, fx = 29f, fy = 18f), AvatarDef("npc/trainer", "Eğitmen", 0.4f, fx = 37f, fy = 16f),
    AvatarDef("npc/soldier", "Muhafız", 0.4f, false),
    AvatarDef("p/dog0/idle_0_0", "Yavru Köpek", 1f, false), AvatarDef("p/dog5/idle_0_0", "Kurt Köpeği", 1f, false), AvatarDef("p/dog10/idle_0_0", "Alfa", 1f, false),
    AvatarDef("z/walker/idle_0_0", "Aylak", 0.5f, false), AvatarDef("z/runner/idle_0_0", "Koşucu", 0.5f, false), AvatarDef("z/toxic/idle_0_0", "Zehirli", 0.5f, false),
    AvatarDef("z/dark/idle_0_0", "Karanlık", 0.5f, false), AvatarDef("z/dog/idle_0_0", "Kuduz", 1f, false), AvatarDef("z/titan/idle_0_0", "Titan", 0.45f, false),
    AvatarDef("npc/wizard", "Büyücü Aldric", 0.4f, fx = 30f, fy = 20f),
)

// =========================================================================== cihazdan fotoğraf
/** Sunucudaki base64 WebP profil fotoğraflarının çözülmüş hali (aynı fotoğraf her satırda yeniden çözülmesin). */
object PhotoCache {
    private val cache = android.util.LruCache<Int, ImageBitmap>(64)
    fun get(b64: String): ImageBitmap? {
        if (b64.isEmpty() || b64 == "null") return null
        cache.get(b64.hashCode())?.let { return it }
        return runCatching {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.also { cache.put(b64.hashCode(), it) }
    }

    /** Seçilen görseli ortadan kare kırpar, 96×96'ya küçültür, WebP olarak sıkıştırır (sunucu sınırı 20.000 karakter). */
    fun encode(ctx: android.content.Context, uri: android.net.Uri): String? = runCatching {
        val cr = ctx.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 192) sample *= 2
        val src = cr.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val side = minOf(src.width, src.height)
        val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = android.graphics.Bitmap.createScaledBitmap(sq, 96, 96, true)
        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                  else @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
        var q = 85
        var b64: String
        do {
            val bo = java.io.ByteArrayOutputStream()
            out.compress(fmt, q, bo)
            b64 = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)
            q -= 15
        } while (b64.length >= 19000 && q >= 30)
        b64
    }.getOrNull()
}

// =========================================================================== çerçeveler
/** Çerçeve: kimlik, ad, açılma koşulu metni. Açık olanlar profildeki "frames" listesinden (sunucu _zx_frames ile denetler). */
data class FrameDef(val id: String, val name: String, val need: String)

/** V11: şimdilik yalnız bu çerçeveler (sunucu _zx_frames ile doğrular) */
val FRAMES = listOf(
    FrameDef("", "Demir", "Herkes"),
    FrameDef("lv10", "Bronz Savaşçı", "Seviye 10"),
    FrameDef("lv25", "Gümüş Muhafız", "Seviye 25"),
    FrameDef("lv50", "Efsanevi Alev", "Seviye 50"),
    FrameDef("season1", "Sezon #1", "Sezon #1 Bileti"),
    FrameDef("blood", "Kanlı Ay", "Kanlı Ay Etkinliğinde İlk 10"),
)

fun frameUnlocked(id: String, p: JSONObject): Boolean {
    if (id.isEmpty()) return true
    val a = p.optJSONArray("frames") ?: return when (id) { "lv10" -> p.optInt("level") >= 10; "lv25" -> p.optInt("level") >= 25; "lv50" -> p.optInt("level") >= 50; else -> false }
    return (0 until a.length()).any { a.optString(it) == id }
}

/**
 * Profil fotoğrafı + efektli çerçeve (çizim tamamen kodla, bkz. drawFrame). NPC resimleri yüz merkezli kırpılır.
 */
@Composable
fun ProfileAvatar(avatar: Int, frame: String, size: Dp, modifier: Modifier = Modifier, photo: String = "") {
    val ctx = LocalContext.current
    // kaldırılan resimler (kahraman, asker, köpek, zombi) Tüccar olarak görünür
    val def = AVATARS.getOrNull(avatar)?.takeIf { it.pick } ?: AVATARS[1]
    val img = remember(def.key) { SpriteCache.get(ctx, def.key) }
    val pic = remember(photo) { PhotoCache.get(photo) }
    val t = rememberTime()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val inner = size * 0.8f
        Box(Modifier.size(inner).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3A4A5A), Color(0xFF151A22))))) {
            if (pic != null) Canvas(Modifier.fillMaxSize()) {
                drawImage(pic, IntOffset.Zero, IntSize(pic.width, pic.height), IntOffset.Zero,
                    IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.Low)
            }
            else if (img != null && def.fx >= 0f) Canvas(Modifier.fillMaxSize()) {
                // yüz merkezli kare: yüz biraz yukarıda, baş ve omuzlar görünür (tüm NPC'ler aynı oranda)
                val side = def.side.coerceAtMost(minOf(img.width, img.height).toFloat())
                val sx = (def.fx - side / 2).coerceIn(0f, img.width - side).toInt()
                val sy = (def.fy - side * 0.42f).coerceIn(0f, img.height - side).toInt()
                drawImage(img, IntOffset(sx, sy), IntSize(side.toInt(), side.toInt()), IntOffset.Zero,
                    IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.None)
            }
            else if (img != null) Canvas(Modifier.fillMaxSize()) {
                val srcH = (img.height * def.crop).toInt().coerceAtLeast(1)
                val k = minOf(this.size.width / img.width, this.size.height / srcH) * (if (def.crop < 1f) 1.35f else 0.95f)
                val dw = img.width * k; val dh = srcH * k
                drawImage(img, IntOffset.Zero, IntSize(img.width, srcH),
                    IntOffset(((this.size.width - dw) / 2).toInt(), if (def.crop < 1f) 0 else ((this.size.height - dh) / 2).toInt()),
                    IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.None)
            }
        }
        Canvas(Modifier.size(size)) { drawFrame(frame, t) }
    }
}

/** 3×5 piksel rakam ve harf (çerçeve rozetleri) */
private val PIX_GLYPH = mapOf(
    '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111", '5' to "111100111001111",
    'S' to "011100010001110", '#' to "101111101111101",
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pixText(s: String, cx: Float, cy: Float, px: Float, color: Color, shadow: Color = Color(0xE0100C08)) {
    val w = s.length * 4 * px - px
    var x0 = cx - w / 2
    val y0 = cy - 2.5f * px
    for (ch in s) {
        val g = PIX_GLYPH[ch] ?: "000000000000000"
        for (i in 0 until 15) if (g[i] == '1') {
            val x = x0 + (i % 3) * px; val y = y0 + (i / 3) * px
            drawRect(shadow, Offset(x + px * 0.6f, y + px * 0.6f), Size(px, px))
            drawRect(color, Offset(x, y), Size(px, px))
        }
        x0 += 4 * px
    }
}

/**
 * Çerçeveler (tamamen kodla, animasyonlu): metal bant + eğim çizgileri + çerçeveye özel efekt.
 *  - Demir: sade koyu demir, perçinler
 *  - Sv 10 Bronz Savaşçı: bronz, perçinler, bant boyunca dolaşan ışık, "10" kalkan rozeti
 *  - Sv 25 Gümüş Muhafız: gümüş, köşelerde nabız gibi parlayan safirler, yanlarda kanatlar, dolaşan yıldızlar, "25"
 *  - Sv 50 Efsanevi Alev: kalın altın, tepede yakutlu taç, üst ve yanlardan yükselen alevler ve kıvılcımlar, "50"
 *  - Sezon #1: koyu çelik, neon camgöbeği iç hat ve mor dış ışıma, bant boyunca kuyruklu enerji topu, "S1"
 *  - Kanlı Ay: kızıl demir, dış dikenler, tepede nabız gibi atan kanlı ay ve kanat çırpan yarasalar, yanlardan süzülen kan damlaları
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(frame: String, t: Float) {
    val w = size.width
    val s = w * 0.1f
    val dark = Color(0xFF0E0B08)
    fun edge(u: Float, inset: Float = s * 0.5f): Offset {
        val p = ((u % 1f) + 1f) % 1f * 4f; val e = w - inset * 2
        return when (p.toInt()) {
            0 -> Offset(inset + e * p, inset); 1 -> Offset(w - inset, inset + e * (p - 1))
            2 -> Offset(w - inset - e * (p - 2), w - inset); else -> Offset(inset, w - inset - e * (p - 3))
        }
    }
    fun band(hi: Color, mid: Color, lo: Color, th: Float = s) {
        drawRect(Brush.linearGradient(listOf(hi, mid, lo), Offset(0f, 0f), Offset(w, w)), Offset(th / 2, th / 2), Size(w - th, w - th), style = Stroke(th))
        drawRect(dark, Offset.Zero, Size(w, w), style = Stroke(s * 0.22f))
        drawRect(dark, Offset(th, th), Size(w - th * 2, w - th * 2), style = Stroke(s * 0.16f))
        // eğim: üst/sol açık, alt/sağ koyu ince çizgi
        drawLine(Color.White.copy(alpha = 0.45f), Offset(s * 0.25f, s * 0.25f), Offset(w - s * 0.25f, s * 0.25f), s * 0.1f)
        drawLine(Color.White.copy(alpha = 0.3f), Offset(s * 0.25f, s * 0.25f), Offset(s * 0.25f, w - s * 0.25f), s * 0.1f)
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(s * 0.25f, w - s * 0.25f), Offset(w - s * 0.25f, w - s * 0.25f), s * 0.1f)
        drawLine(Color.Black.copy(alpha = 0.3f), Offset(w - s * 0.25f, s * 0.25f), Offset(w - s * 0.25f, w - s * 0.25f), s * 0.1f)
    }
    fun rivet(c: Offset, r: Float, col: Color) {
        drawCircle(dark, r * 1.25f, c)
        drawCircle(col, r, c)
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.35f, Offset(c.x - r * 0.3f, c.y - r * 0.3f))
    }
    fun glow(c: Offset, r: Float, col: Color, a: Float) = drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), c, r), r, c)
    fun sparkle(c: Offset, r: Float, col: Color) {
        drawRect(col, Offset(c.x - r * 0.18f, c.y - r), Size(r * 0.36f, r * 2))
        drawRect(col, Offset(c.x - r, c.y - r * 0.18f), Size(r * 2, r * 0.36f))
    }
    fun badge(text: String, fill: Color, rim: Color, txt: Color) {
        val bw = s * 3.4f; val bh = s * 1.7f
        val cx = w / 2; val cy = w - s * 0.55f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - bw / 2, cy - bh / 2); lineTo(cx + bw / 2, cy - bh / 2); lineTo(cx + bw / 2, cy + bh * 0.2f)
            lineTo(cx, cy + bh / 2 + s * 0.2f); lineTo(cx - bw / 2, cy + bh * 0.2f); close()
        }
        drawPath(path, dark, style = Stroke(s * 0.45f))
        drawPath(path, Brush.verticalGradient(listOf(rim, fill), cy - bh / 2, cy + bh / 2))
        drawPath(path, rim, style = Stroke(s * 0.12f))
        pixText(text, cx, cy - s * 0.05f, s * 0.24f, txt)
    }
    fun fract(v: Float) = ((v % 1f) + 1f) % 1f
    val corners = listOf(Offset(s * 0.5f, s * 0.5f), Offset(w - s * 0.5f, s * 0.5f), Offset(s * 0.5f, w - s * 0.5f), Offset(w - s * 0.5f, w - s * 0.5f))
    when (frame) {
        "lv10" -> {
            band(Color(0xFFF6C08A), Color(0xFFB8733A), Color(0xFF5A2E12))
            corners.forEach { rivet(it, s * 0.32f, Color(0xFFD08A4A)) }
            for (u in listOf(0.125f, 0.375f, 0.625f, 0.875f)) rivet(edge(u), s * 0.2f, Color(0xFFC07A3A))
            val c = edge(t * 0.18f)
            glow(c, s * 1.6f, Color(0xFFFFE0B0), 0.75f)
            badge("10", Color(0xFF8A4A1E), Color(0xFFF2B27A), Color(0xFFFFF0D8))
        }
        "lv25" -> {
            band(Color(0xFFFFFFFF), Color(0xFFC8D0DA), Color(0xFF5E6878))
            // tepede safirli gümüş arma
            run {
                val cx = w / 2; val cy = s * 0.45f; val r = s * 1.15f
                val crest = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - r * 1.3f, cy - r * 0.3f); lineTo(cx + r * 1.3f, cy - r * 0.3f); lineTo(cx + r * 0.9f, cy + r * 0.55f)
                    lineTo(cx, cy + r * 0.9f); lineTo(cx - r * 0.9f, cy + r * 0.55f); close()
                }
                drawPath(crest, dark, style = Stroke(s * 0.3f))
                drawPath(crest, Brush.verticalGradient(listOf(Color.White, Color(0xFF8A96A6)), cy - r * 0.3f, cy + r * 0.9f))
                glow(Offset(cx, cy + r * 0.2f), s * 1.4f, Color(0xFF5AA8FF), 0.5f + 0.3f * sin(t * 3f))
                drawCircle(dark, r * 0.42f, Offset(cx, cy + r * 0.2f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFBFE4FF), Color(0xFF2A6AE0)), Offset(cx - r * 0.1f, cy + r * 0.1f), r * 0.4f), r * 0.34f, Offset(cx, cy + r * 0.2f))
            }
            val pulse = 0.5f + 0.5f * sin(t * 3f)
            corners.forEach { c ->
                glow(c, s * (1.4f + 0.5f * pulse), Color(0xFF5AA8FF), 0.55f + 0.35f * pulse)
                val r = s * 0.55f
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r * 0.8f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.8f, c.y); close() }
                drawPath(path, dark, style = Stroke(s * 0.2f))
                drawPath(path, Brush.linearGradient(listOf(Color(0xFFBFE4FF), Color(0xFF2A6AE0)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
                drawRect(Color.White, Offset(c.x - r * 0.3f, c.y - r * 0.5f), Size(r * 0.25f, r * 0.25f))
            }
            for (k in 0..1) sparkle(edge(t * 0.12f + k * 0.5f), s * (0.45f + 0.15f * sin(t * 8f + k)), Color.White.copy(alpha = 0.95f))
            badge("25", Color(0xFF6A7686), Color(0xFFF2F6FA), Color.White)
        }
        "lv50" -> {
            // alevler bandın arkasında: üst kenar ve yanların üst yarısı, yukarı doğru titreyerek
            val n = 16
            for (k in 0 until n) {
                val u = (k + 0.5f) / n
                val c = if (u < 0.5f) Offset(s * 0.5f + (w - s) * (u * 2f), s * 0.3f)
                        else { val v = (u - 0.5f) * 2f; if (k % 2 == 0) Offset(s * 0.3f, s + v * w * 0.45f) else Offset(w - s * 0.3f, s + v * w * 0.45f) }
                val fl = 0.65f + 0.35f * sin(t * 11f + k * 2.3f) * sin(t * 7f + k)
                val h = s * (1.5f + 0.9f * fl)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x - s * 0.55f, c.y + s * 0.3f)
                    quadraticTo(c.x - s * 0.5f, c.y - h * 0.5f, c.x + sin(t * 6f + k) * s * 0.3f, c.y - h)
                    quadraticTo(c.x + s * 0.5f, c.y - h * 0.5f, c.x + s * 0.55f, c.y + s * 0.3f); close()
                }
                drawPath(path, Brush.verticalGradient(listOf(Color(0x00FF3A10), Color(0xFFFF5A1F), Color(0xFFFFC040), Color(0xFFFFF4B0)), c.y - h, c.y + s * 0.3f))
            }
            band(Color(0xFFFFF4B0), Color(0xFFFFC83A), Color(0xFF8A4A08), s * 1.1f)
            // köşe süsleri
            corners.forEach { c ->
                val r = s * 0.7f
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
                drawPath(path, dark, style = Stroke(s * 0.2f)); drawPath(path, Color(0xFFFFD860))
                drawCircle(Color(0xFFE0302A), r * 0.35f, c); drawCircle(Color.White.copy(alpha = 0.8f), r * 0.12f, Offset(c.x - r * 0.12f, c.y - r * 0.12f))
            }
            // taç
            val cx = w / 2; val cy = s * 0.2f; val cw = s * 3.2f; val ch = s * 1.6f
            val crown = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - cw / 2, cy + ch * 0.5f); lineTo(cx - cw / 2, cy - ch * 0.3f); lineTo(cx - cw / 4, cy + ch * 0.05f)
                lineTo(cx, cy - ch * 0.55f); lineTo(cx + cw / 4, cy + ch * 0.05f); lineTo(cx + cw / 2, cy - ch * 0.3f); lineTo(cx + cw / 2, cy + ch * 0.5f); close()
            }
            glow(Offset(cx, cy), s * 2.4f, Color(0xFFFFD860), 0.45f + 0.25f * sin(t * 4f))
            drawPath(crown, dark, style = Stroke(s * 0.3f))
            drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFF4B0), Color(0xFFE0A020)), cy - ch * 0.55f, cy + ch * 0.5f))
            drawCircle(Color(0xFFE0302A), s * 0.28f, Offset(cx, cy + ch * 0.12f))
            // yükselen kıvılcımlar
            for (k in 0 until 7) {
                val ph = fract(t * 0.55f + k * 0.29f)
                val x = w * (0.08f + 0.84f * ((k * 0.618f) % 1f)) + sin(t * 3f + k) * s * 0.4f
                val y = s * 1.2f - ph * s * 3.5f + w * 0.2f * ((k % 3) / 3f)
                drawRect(Color(0xFFFFE07A).copy(alpha = (1f - ph) * 0.9f), Offset(x, y), Size(s * 0.22f, s * 0.22f))
            }
            badge("50", Color(0xFFA8640C), Color(0xFFFFE07A), Color(0xFFFFF8E0))
        }
        "season1" -> {
            val pulse = 0.5f + 0.5f * sin(t * 2.6f)
            drawRect(Color(0xFFB05AFF).copy(alpha = 0.25f + 0.25f * pulse), Offset(-s * 0.15f, -s * 0.15f), Size(w + s * 0.3f, w + s * 0.3f), style = Stroke(s * 0.5f))
            band(Color(0xFF4A5068), Color(0xFF242836), Color(0xFF0E1018))
            drawRect(Color(0xFF3AF0E0).copy(alpha = 0.55f + 0.45f * pulse), Offset(s * 0.95f, s * 0.95f), Size(w - s * 1.9f, w - s * 1.9f), style = Stroke(s * 0.14f))
            // kuyruklu enerji topu
            for (i in 0 until 14) {
                val c = edge(t * 0.22f - i * 0.009f)
                val a = 1f - i / 14f
                drawCircle(androidx.compose.ui.graphics.lerp(Color(0xFFE8FFFF), Color(0xFFB05AFF), i / 14f).copy(alpha = a), s * 0.38f * (1f - i / 20f), c)
            }
            glow(edge(t * 0.22f), s * 1.5f, Color(0xFF3AF0E0), 0.7f)
            corners.forEach { c ->
                val r = s * 0.4f
                val hex = androidx.compose.ui.graphics.Path().apply {
                    for (k in 0 until 6) { val a = k * PI.toFloat() / 3f; val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r); if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close()
                }
                drawPath(hex, dark, style = Stroke(s * 0.15f)); drawPath(hex, Color(0xFF8A90A8))
                drawCircle(Color(0xFF3AF0E0).copy(alpha = 0.6f + 0.4f * pulse), r * 0.35f, c)
            }
            badge("S1", Color(0xFF1A1630), Color(0xFFB05AFF), Color(0xFF7AFFF0))
        }
        "blood" -> {
            val pulse = 0.5f + 0.5f * sin(t * 2.2f)
            drawRect(Color(0xFFFF2020).copy(alpha = 0.18f + 0.22f * pulse), Offset(-s * 0.2f, -s * 0.2f), Size(w + s * 0.4f, w + s * 0.4f), style = Stroke(s * 0.6f))
            // dikenler (dışa doğru)
            val nT = 20
            for (k in 0 until nT) {
                val u = k / nT.toFloat()
                val c = edge(u, 0f)
                val side = ((u * 4f).toInt())
                val (nx, ny) = when (side) { 0 -> 0f to -1f; 1 -> 1f to 0f; 2 -> 0f to 1f; else -> -1f to 0f }
                val tx = -ny; val ty = nx
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x + tx * s * 0.3f, c.y + ty * s * 0.3f); lineTo(c.x + nx * s * 0.55f, c.y + ny * s * 0.55f); lineTo(c.x - tx * s * 0.3f, c.y - ty * s * 0.3f); close()
                }
                drawPath(path, Color(0xFF3A0606))
            }
            band(Color(0xFFD8302A), Color(0xFF8A1010), Color(0xFF2A0404))
            // yanlardan süzülen kan damlaları
            for (k in 0 until 6) {
                val left = k % 2 == 0
                val ph = fract(t * 0.32f + k * 0.37f)
                val x = if (left) s * 0.5f else w - s * 0.5f
                val y = s * 1.2f + ph * (w - s * 2.4f)
                val r = s * 0.22f * (1f - ph * 0.3f)
                val a = if (ph > 0.85f) (1f - ph) / 0.15f else 1f
                val drop = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, y - r * 3.2f); quadraticTo(x + r * 1.2f, y, x, y + r); quadraticTo(x - r * 1.2f, y, x, y - r * 3.2f); close()
                }
                drawPath(drop, Color(0xFF8A0000).copy(alpha = a))
                drawCircle(Color(0xFFFF6A5A).copy(alpha = a * 0.8f), r * 0.3f, Offset(x - r * 0.3f, y - r * 0.2f))
            }
            // üst bandın iç kenarından sarkan kan
            for (k in 0 until 5) {
                val x = w * (0.22f + k * 0.14f)
                val len = s * (0.35f + 0.45f * (0.5f + 0.5f * sin(t * 1.3f + k * 1.9f)))
                drawLine(Color(0xFF7A0000), Offset(x, s), Offset(x, s + len), s * 0.22f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            // kanlı ay ve yarasalar
            val mc = Offset(w / 2, s * 0.45f)
            glow(mc, s * (2.6f + 0.6f * pulse), Color(0xFFFF2A1A), 0.55f + 0.3f * pulse)
            // ay: koyu kenarlı kızıl disk, sağda hilal gölgesi, kraterler ve ince parlak kenar
            val mr = s * 0.85f
            drawCircle(dark, mr * 1.15f, mc)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A7A), Color(0xFFD02A18), Color(0xFF7A0A06)), Offset(mc.x - mr * 0.35f, mc.y - mr * 0.35f), mr * 1.4f), mr, mc)
            drawCircle(Color(0x80300000), mr * 0.85f, Offset(mc.x + mr * 0.45f, mc.y + mr * 0.1f))
            for ((dx, dy, rr) in listOf(Triple(-0.3f, -0.2f, 0.18f), Triple(0.1f, 0.35f, 0.13f), Triple(-0.45f, 0.3f, 0.1f)))
                drawCircle(Color(0x70500000), mr * rr, Offset(mc.x + mr * dx, mc.y + mr * dy))
            drawCircle(Color(0xFFFFB098).copy(alpha = 0.5f + 0.3f * pulse), mr, mc, style = Stroke(s * 0.08f))
            for (k in 0..1) {
                val bx = mc.x + (if (k == 0) -1f else 1f) * s * (2.1f + 0.3f * sin(t * 1.5f + k)); val by = mc.y - s * 0.1f + sin(t * 2f + k * 2f) * s * 0.25f
                val flap = sin(t * 14f + k * 1.3f) * s * 0.35f
                val bat = androidx.compose.ui.graphics.Path().apply {
                    moveTo(bx, by); lineTo(bx - s * 0.55f, by - s * 0.2f - flap); lineTo(bx - s * 0.3f, by + s * 0.05f)
                    lineTo(bx, by + s * 0.15f); lineTo(bx + s * 0.3f, by + s * 0.05f); lineTo(bx + s * 0.55f, by - s * 0.2f - flap); close()
                }
                drawPath(bat, Color(0xFF140404))
            }
        }
        else -> {
            band(Color(0xFF8A8A90), Color(0xFF4A4A50), Color(0xFF1E1E22))
            corners.forEach { rivet(it, s * 0.26f, Color(0xFF7A7A82)) }
        }
    }
}

// =========================================================================== etiketler
/** Etiket rozeti: VIP parlayan altın yazı, sıralama rozetleri. */
@Composable
fun TagChip(tag: JSONObject) {
    val id = tag.optString("id")
    val t = rememberTime()
    val (bg, fg) = when {
        id == "vip" -> Color(0xFF2A1E06) to Color(0xFFFFD34A)
        id == "lvl1" -> Color(0xFF3A2A06) to Color(0xFFFFE27A)
        id == "lvl2" -> Color(0xFF2A2E34) to Color(0xFFE6EEF6)
        id == "lvl3" -> Color(0xFF3A2414) to Color(0xFFE8A060)
        id.startsWith("kill") -> Color(0xFF3A0E0E) to Color(0xFFFF6A5A)
        else -> Color(0xFF14243A) to Color(0xFF8CC8FF)
    }
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(bg).border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        if (id == "vip") {
            // parlayan altın VIP: dalgalanan ışıltı + yumuşak hale
            val glow = 0.5f + 0.5f * sin(t * 3f)
            Text("VIP", style = TextStyle(fontFamily = PixelFont, fontSize = 14.sp,
                brush = Brush.linearGradient(listOf(Color(0xFFB8860B), Color(0xFFFFF3B0), Color(0xFFFFC83A)),
                    start = Offset(-60f + 160f * ((t * 0.5f) % 1f), 0f), end = Offset(40f + 160f * ((t * 0.5f) % 1f), 20f)),
                shadow = Shadow(Color(0xFFFFC83A).copy(alpha = glow), Offset.Zero, 10f + 8f * glow)))
        } else PT(tag.optString("name"), 11.sp, fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: JSONArray?, modifier: Modifier = Modifier) {
    if (tags == null || tags.length() == 0) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until tags.length()) TagChip(tags.getJSONObject(i))
    }
}

// =========================================================================== lobi: sol üst kimlik kartı
@Composable
fun ProfileBadge(p: JSONObject, guest: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.pxPanel(Color(0xE01E1D19)).clickable { onClick() }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(p.optInt("avatar"), p.optString("frame"), 58.dp, photo = p.optString("photo"))
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT(p.optString("name").ifEmpty { "Misafir" }, 17.sp, Px.Name)
                if (p.optBoolean("vip")) { Spacer(Modifier.width(6.dp)); TagChip(JSONObject().put("id", "vip")) }
            }
            if (guest) PT("Hesap Aç · İlerlemen Kaybolmasın", 11.sp, Px.Clan)
            else PT("ID: ${fmtId(p.optLong("id"))}  ·  Sv ${p.optInt("level", 1)}", 12.sp, Px.Sub)
            val tags = p.optJSONArray("tags")
            if (tags != null && tags.length() > 0) {
                Spacer(Modifier.height(3.dp))
                val first = (0 until tags.length()).map { tags.getJSONObject(it) }.firstOrNull { it.optString("id") != "vip" }
                if (first != null) TagChip(first)
            }
        }
    }
}

// =========================================================================== profil düzenleyici
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditor(close: () -> Unit, rename: () -> Unit) {
    val ctx = LocalContext.current
    val p = Account.profile.value
    var busy by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            val b64 = PhotoCache.encode(ctx, uri)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (b64 == null) { busy = false; Toaster.show("Fotoğraf Okunamadı", false) }
                else Account.setPhoto(b64) { e -> busy = false; if (e != null) Toaster.show(e, false) else Toaster.show("Profil Fotoğrafı Yüklendi") }
            }
        }.start()
    }
    ProfileEditorView(p, close, rename,
        pickPhoto = {
            if (!busy) pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        clearPhoto = { Account.setPhoto("") { e -> if (e != null) Toaster.show(e, false) else Toaster.show("Profil Fotoğrafı Kaldırıldı") } },
        setTitle = { k -> Account.setTitle(k) { e -> if (e != null) Toaster.show(e, false) else Toaster.show(if (k.isEmpty()) "Unvan Kaldırıldı" else "Unvan Seçildi") } },
    ) { avatar, frame ->
        Account.setProfile(avatar, frame) { e -> if (e != null) Toaster.show(e, false) else { Toaster.show("Profil Kaydedildi"); close() } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileEditorView(
    p: JSONObject, close: () -> Unit, rename: () -> Unit,
    pickPhoto: () -> Unit = {}, clearPhoto: () -> Unit = {}, setTitle: (String) -> Unit = {},
    save: (Int, String) -> Unit,
) {
    var avatar by remember { mutableIntStateOf(p.optInt("avatar")) }
    var frame by remember { mutableStateOf(p.optString("frame")) }
    val photo = p.optString("photo").takeIf { it != "null" } ?: ""
    val title = p.optJSONObject("title")
    PxDialog("Profil", close, width = 760.dp) {
        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // önizleme
            Column(Modifier.width(220.dp).fillMaxHeight().pxPanel(Px.PanelDark).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileAvatar(avatar, frame, 96.dp, photo = photo)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT(p.optString("name"), 18.sp, Px.Name)
                    Spacer(Modifier.width(4.dp))
                    PxIcon("craft", size = 26.dp) { rename() }
                }
                PT("ID: ${fmtId(p.optLong("id"))}", 12.sp, Px.Sub)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PT("Sv ${p.optInt("level", 1)}", 14.sp, Px.Level)
                    PT("${fmt(p.optInt("kills"))} Öldürme", 14.sp, Color(0xFFFF8A7A))
                }
                p.optJSONObject("clan")?.let { c -> PT("${c.optString("name")} · ${c.optString("rank_name")}", 12.sp, Px.Clan) }
                if (title != null) { Spacer(Modifier.height(4.dp)); TitleChip(title, 11f) }
                PT("${fmt(p.optInt("hours"))} Saat Oynadın", 12.sp, Px.Sub)
                Spacer(Modifier.height(6.dp))
                TagRow(p.optJSONArray("tags"))
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                PT("Cihazdan Fotoğraf", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PxButton(if (photo.isEmpty()) "Fotoğraf Seç" else "Değiştir", icon = "profile", color = Px.Blue, height = 38.dp, size = 13.sp) { pickPhoto() }
                    if (photo.isNotEmpty()) PxButton("Kaldır", icon = "trash", color = Px.Red, height = 38.dp, size = 13.sp) { clearPhoto() }
                }
                PT("Uygunsuz Fotoğraflar Yöneticiler Tarafından Kaldırılır.", 10.sp, Px.Sub)
                Spacer(Modifier.height(10.dp))
                PT("Unvan", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                val titles = p.optJSONArray("titles") ?: JSONArray()
                if (titles.length() == 0) PT("Sıralamada İlk 10'a Ya Da Bir Etkinlikte İlk 3'e Girince Unvan Kazanırsın. Etkinlik Unvanları 30 Gün Geçerlidir.", 12.sp, Px.Sub, maxLines = 2)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (title == null) Color(0x553A6AB0) else Color(0x33000000))
                        .clickable { setTitle("") }.padding(horizontal = 8.dp, vertical = 5.dp)) { PT("Unvansız", 12.sp, Px.Sub) }
                    for (i in 0 until titles.length()) {
                        val t = titles.getJSONObject(i)
                        val on = title?.optString("kind") == t.optString("kind")
                        Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) Color(0x553A6AB0) else Color(0x33000000))
                            .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { setTitle(t.optString("kind")) }.padding(4.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TitleChip(t, 11f)
                                // etkinlik unvanı: kalan gün
                                if (t.has("until")) PT("${((t.optLong("until") - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0) + 1} Gün", 9.sp, Px.Sub)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PT(if (photo.isEmpty()) "Karakter Resmi" else "Karakter Resmi (Fotoğraf Kaldırılınca Görünür)", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AVATARS.forEachIndexed { i, a ->
                        if (!a.pick) return@forEachIndexed
                        Box(Modifier.border(2.dp, if (i == avatar) Color.White else Color.Transparent).clickable { avatar = i }) {
                            ProfileAvatar(i, "", 56.dp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PT("Çerçeve", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in FRAMES) {
                        val ok = frameUnlocked(f.id, p)
                        Column(Modifier.width(92.dp).clip(RoundedCornerShape(3.dp)).background(if (f.id == frame) Color(0x553A6AB0) else Color(0x33000000))
                            .clickable { if (ok) frame = f.id else Toaster.show("${f.name}: ${f.need}", false) }.padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                ProfileAvatar(avatar, f.id, 54.dp, photo = photo)
                                if (!ok) Box(Modifier.size(54.dp).background(Color(0xA0000000)), contentAlignment = Alignment.Center) { Icon("lock", 20.dp, Px.Sub) }
                            }
                            PT(f.name, 11.sp, if (ok) Px.Text else Px.Sub)
                            PT(f.need, 9.sp, Px.Sub, maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PxButton("Vazgeç", color = Px.Dark, height = 42.dp, size = 15.sp) { close() }
            Spacer(Modifier.width(8.dp))
            PxButton("Kaydet", height = 42.dp, size = 15.sp) { save(avatar, frame) }
        }
    }
}
