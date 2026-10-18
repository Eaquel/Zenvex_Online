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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** Piksel yazı: sert (bulanıksız) gölge. */
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
        text, modifier, color = color, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
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

/** Piksel buton: düz renk, alt kenarda koyu şerit (kabartma). */
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
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.5f, 900f), label = "px")
    val c = if (enabled) color else Px.Dark
    Box(
        modifier.scale(sc).height(height).clip(RoundedCornerShape(3.dp)).background(c)
            .drawBehind {
                val e = 3.dp.toPx()
                drawRect(Color.Black.copy(alpha = 0.32f), Offset(0f, this.size.height - e), Size(this.size.width, e))
                drawRect(Color.White.copy(alpha = 0.16f), Offset(0f, 0f), Size(this.size.width, e * 0.7f))
            }
            .border(2.dp, Px.Edge, RoundedCornerShape(3.dp))
            .clickable(interactionSource = src, indication = null) {
                Audio.play("click", bus = Audio.Bus.Ui, pitch = if (enabled) 1f else 0.7f)
                if (enabled) onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, (height.value * 0.5f).dp, textColor)
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
        ) { Icon(icon, size * 0.56f, tint) }
        if (badge > 0) Box(
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
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (on) Px.Panel else Px.PanelDark)
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
    @Volatile private var img: ImageBitmap? = null
    private var n = 0

    /** Dünyanın piksel haritası (ilk çağrıda Rust'tan üretilir, önbelleklenir). */
    fun get(size: Int = 420): ImageBitmap? {
        img?.let { if (n == size) return it }
        if (GameSession.handle == 0L) return null
        val px = IntArray(size * size)
        synchronized(GameSession.lock) { Native.minimap(GameSession.handle, size, px) }
        val b = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
        img = b; n = size
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
