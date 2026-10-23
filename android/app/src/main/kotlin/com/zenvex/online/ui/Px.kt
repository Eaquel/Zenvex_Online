package com.zenvex.online.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.R
import com.zenvex.online.game.Audio
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Native
import kotlin.math.sin

// =========================================================================== piksel arayüz (lobi + oyun içi)
val PixelFont = FontFamily(Font(R.font.pixel))

object Px {
    val Panel = Color(0xF02B2B26)
    val PanelDark = Color(0xF0201F1C)
    val Row = Color(0xFF34342E)
    val RowHi = Color(0xFF3E3E36)
    val Edge = Color(0xFF15140F)
    val Text = Color(0xFFE8E4D4)
    val Sub = Color(0xFFA8A48E)
    val Green = Color(0xFF6E8C3A)
    val GreenHi = Color(0xFF86A74A)
    val Blue = Color(0xFF4A6FB5)
    val Red = Color(0xFF8A4A44)
    val Dark = Color(0xFF3A3A33)
    val Name = Color(0xFF45E04F)
    val Level = Color(0xFF9F92FF)
    val Clan = Color(0xFFF6E05A)
    val Orange = Color(0xFFFFB347)
    val Hard = Color(0xFFE8A060)
}

private val TR = java.util.Locale("tr", "TR")

/**
 * Türkçe "Her Kelimenin İlk Harfi Büyük": yalnız küçük harfle başlayan kelimelerin ilk harfi büyütülür
 * (i → İ doğru çevrilir; XP, Sv, rakamlar ve simgeler olduğu gibi kalır).
 */
fun String.titleTr(): String {
    val sb = StringBuilder(length)
    var start = true
    for (ch in this) {
        if (start && ch.isLetter() && ch.isLowerCase()) sb.append(ch.toString().uppercase(TR)) else sb.append(ch)
        start = ch.isWhitespace() || ch == '(' || ch == '/' || ch == '"' || ch == '·' || ch == '-' || ch == '→'
    }
    return sb.toString()
}

/** Piksel yazı: sert (bulanıksız) gölge. Tek satırlık yazılar (başlık, tuş, etiket) Her Kelime Büyük yazılır. */
@Composable
fun PT(
    text: String,
    size: TextUnit = 16.sp,
    color: Color = Px.Text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = 1,
    outline: Boolean = false,
) {
    Text(
        if (maxLines == 1) text.titleTr() else text, modifier, color = color, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = PixelFont, fontSize = size,
            shadow = Shadow(Color(0xE6000000), Offset(if (outline) 3f else 2f, if (outline) 3f else 2f), 0f),
        ),
    )
}

/** Düz koyu panel (piksel kenarlı). */
fun Modifier.pxPanel(color: Color = Px.Panel): Modifier = this
    .clip(RoundedCornerShape(3.dp))
    .background(color)
    .border(2.dp, Px.Edge, RoundedCornerShape(3.dp))

@Composable
fun PxPanel(modifier: Modifier = Modifier, color: Color = Px.Panel, pad: Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.pxPanel(color).padding(pad), content = content)
}

/**
 * Piksel buton: perçinli metal plaka. Üstte ışık şeridi, altta kalın gölge şeridi (kabartma), köşelerde perçin;
 * basılınca plaka gölge kadar aşağı iner (gömülme), devre dışıyken çapraz çizgili.
 */
@Composable
fun PxButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Px.Green,
    icon: String? = null,
    size: TextUnit = 18.sp,
    height: Dp = 44.dp,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed && enabled) 1f else 0f, spring(0.6f, 1400f), label = "px")
    val c = if (enabled) color else Px.Dark
    // V10 metal tuş görselleri: kızıl (normal / basılı) ve kapalı; diğer renkler kodla çizilir
    val ctx = LocalContext.current
    val art = when {
        !enabled -> remember { UiArt.image(ctx, "btn_off") }
        color == Px.Red -> if (pressed) remember { UiArt.image(ctx, "btn_red_down") } else remember { UiArt.image(ctx, "btn_red") }
        else -> null
    }
    Box(
        modifier.height(height).clip(RoundedCornerShape(3.dp))
            .drawBehind {
                val e = 4.dp.toPx(); val down = e * sink
                val w = this.size.width; val h = this.size.height
                if (art != null) { drawSlice3(art, 0f, 0f, w, h, 32); return@drawBehind }
                // gövde gölgesi (kabartma alt kenarı)
                drawRect(Px.Edge, Offset(0f, 0f), Size(w, h))
                drawRect(Color(c.red * 0.55f, c.green * 0.55f, c.blue * 0.55f, 1f), Offset(2f, down + 2f), Size(w - 4f, h - 4f - down))
                // yüz
                drawRect(c, Offset(2f, down + 2f), Size(w - 4f, h - 4f - e))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), down, down + h * 0.6f),
                    Offset(2f, down + 2f), Size(w - 4f, (h - 4f - e) * 0.6f))
                drawRect(Color.White.copy(alpha = 0.3f), Offset(2f, down + 2f), Size(w - 4f, 2.dp.toPx()))
                if (!enabled) {
                    val st = 8.dp.toPx(); var x = -h
                    while (x < w) { drawLine(Color.Black.copy(alpha = 0.18f), Offset(x, h), Offset(x + h, 0f), 2.dp.toPx()); x += st }
                }
                // perçinler
                val rv = 1.6.dp.toPx(); val ins = 5.dp.toPx()
                for ((rx, ry) in listOf(ins to ins + down, w - ins to ins + down, ins to h - e - ins + down, w - ins to h - e - ins + down)) {
                    drawCircle(Color.Black.copy(alpha = 0.45f), rv, Offset(rx + 0.6f, ry + 0.6f))
                    drawCircle(Color.White.copy(alpha = 0.45f), rv, Offset(rx, ry))
                }
            }
            .clickable(interactionSource = src, indication = null) {
                Audio.play("click", bus = Audio.Bus.Ui, pitch = if (enabled) 1f else 0.7f)
                if (enabled) onClick()
            }
            .padding(start = 14.dp, end = 14.dp, top = (2 + 4 * sink).dp, bottom = (6 - 4 * sink).dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, (height.value * 0.45f).dp, textColor)
                if (text.isNotEmpty()) Spacer(Modifier.width(10.dp))
            }
            if (text.isNotEmpty()) PT(text, size, if (enabled) textColor else Px.Sub)
        }
    }
}

/** Oyun içi kare ikon tuşu (rozetli). */
@Composable
fun PxIcon(icon: String, modifier: Modifier = Modifier, size: Dp = 48.dp, badge: Int = 0, tint: Color = Px.Text, color: Color = Color(0xCC2B2B26), onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 900f), label = "pxi")
    Box(modifier.scale(sc)) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(color).border(2.dp, Px.Edge, RoundedCornerShape(6.dp))
                .clickable(interactionSource = src, indication = null) { Audio.play("click", bus = Audio.Bus.Ui); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, size * (if (icon in PIXEL_ICONS) 0.72f else 0.56f), tint) }
        // posta: kırmızı mühürlü zarf rozeti (V13), sayı üstünde
        if (badge > 0 && icon == "mail") Box(Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp).size(24.dp), contentAlignment = Alignment.Center) {
            ArtImage("mail_badge", Modifier.fillMaxSize())
            PT(if (badge > 9) "9+" else "$badge", 10.sp, outline = true, modifier = Modifier.offset(7.dp, 7.dp))
        } else if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape).background(Color(0xFFE02828))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { PT(if (badge > 9) "9+" else "$badge", 11.sp) }
    }
}

/** Zombix tarzı iki sekme (Sunucular / Karakter). */
@Composable
fun PxTabs(items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(48.dp)
                    .artBehind(if (on) "tab_on" else "tab_off", slice = 28,
                        fallback = Modifier.clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (on) Px.Panel else Px.PanelDark))
                    .clickable { Audio.play("click", bus = Audio.Bus.Ui); onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { PT(s, 22.sp, if (on) Px.Text else Px.Sub) }
        }
    }
}

/** Seviye rozeti (yuvarlak, rakamlı). */
@Composable
fun LevelBadge(level: Int, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Color(0xFF26262A)).border(2.dp, Color(0xFFB8B8C8), CircleShape), contentAlignment = Alignment.Center) {
        PT("$level", (size.value * 0.48f).sp)
    }
}

/** Basit piksel çubuk. */
@Composable
fun PxBar(value: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(modifier.height(height).background(Color(0xFF15140F)).padding(2.dp)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(height - 4.dp).background(color)
            .drawBehind { drawRect(Color.White.copy(alpha = 0.25f), Offset.Zero, Size(size.width, size.height * 0.35f)) })
    }
}

// =========================================================================== dünya haritası arka planı
object WorldMapImage {
    /** Harita çözünürlüğü: 1 piksel ≈ 30 dünya pikseli (V8: harita 720 kare; binalar, ağaçlar seçilir) */
    const val SIZE = 1536
    @Volatile private var bmp: Bitmap? = null
    @Volatile private var img: ImageBitmap? = null

    /** Dünyanın piksel haritası (ilk çağrıda Rust'tan üretilir, önbelleklenir; mini harita da bunu kullanır). */
    fun bitmap(): Bitmap? {
        bmp?.let { return it }
        if (GameSession.handle == 0L) return null
        val px = IntArray(SIZE * SIZE)
        synchronized(GameSession.lock) { Native.minimap(GameSession.handle, SIZE, px) }
        val b = Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bmp = b
        return b
    }

    fun get(): ImageBitmap? {
        img?.let { return it }
        val b = bitmap()?.asImageBitmap() ?: return null
        img = b
        return b
    }
}

/** Lobi arka planı: yavaşça kayan piksel dünya haritası. */
@Composable
fun MapBackground(t: Float, modifier: Modifier = Modifier.fillMaxSize(), dim: Float = 0.25f, content: @Composable BoxScope.() -> Unit = {}) {
    val img = remember { WorldMapImage.get() }
    Box(modifier.background(Color(0xFF3A5A2A))) {
        if (img != null) Canvas(Modifier.fillMaxSize()) {
            // ekranı kaplayacak ölçek + yavaş gezinme
            val k = maxOf(size.width / img.width, size.height / img.height) * 1.55f
            val dw = img.width * k; val dh = img.height * k
            val ox = (size.width - dw) * (0.5f + 0.42f * sin(t * 0.021f))
            val oy = (size.height - dh) * (0.5f + 0.42f * sin(t * 0.017f + 1.3f))
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(ox.toInt(), oy.toInt()), IntSize(dw.toInt(), dh.toInt()),
                filterQuality = FilterQuality.None)
            drawRect(Color.Black.copy(alpha = dim))
        }
        content()
    }
}
