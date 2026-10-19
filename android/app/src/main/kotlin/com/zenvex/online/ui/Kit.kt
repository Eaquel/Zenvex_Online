package com.zenvex.online.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.zenvex.online.R
import com.zenvex.online.game.Atlas
import com.zenvex.online.game.Audio

// =========================================================================== tema
object Pal {
    val Bg = Color(0xFF070B14)
    val Panel = Color(0xE6101826)
    val PanelHi = Color(0xF0182436)
    val Line = Color(0x33FFFFFF)
    val Text = Color(0xFFEEF2F6)
    val Sub = Color(0xFF9AA8BA)
    val Gold = Color(0xFFFFC93C)
    val GoldDeep = Color(0xFFD28A12)
    val Gem = Color(0xFF5AD8FF)
    val Red = Color(0xFFFF4D4D)
    val RedDeep = Color(0xFF9A1C24)
    val Green = Color(0xFF5BE38A)
    val GreenDeep = Color(0xFF1E8A4A)
    val Blue = Color(0xFF4D9BFF)
    val BlueDeep = Color(0xFF1E4C9A)
    val Purple = Color(0xFFB277FF)
    val Orange = Color(0xFFFF9A3C)
    val Toxic = Color(0xFF9DFF4A)
    val Common = Color(0xFFD8DEE6)
    val Rare = Color(0xFF5AA8FF)
    val Epic = Color(0xFFC070FF)
    val Legendary = Color(0xFFFFB030)
    val Seasonal = Color(0xFFFF5A5A)
    fun rarity(r: Int) = when (r) { 1 -> Rare; 2 -> Epic; 3 -> Legendary; 4 -> Seasonal; else -> Common }
}

val Body = FontFamily(Font(R.font.outfit_regular, FontWeight.Normal), Font(R.font.outfit_bold, FontWeight.Bold))
val Display = FontFamily(Font(R.font.display_bold, FontWeight.Bold))

@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = Pal.Text,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    display: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    glow: Color? = null,
) {
    Text(
        text, modifier, color = color, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = if (display) Display else Body, fontSize = size,
            fontWeight = if (bold || display) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = if (display) 1.2.sp else 0.sp,
            shadow = Shadow(glow ?: Color(0xCC000000), Offset(0f, if (glow != null) 0f else 2f), if (glow != null) 18f else 4f),
        ),
    )
}

// =========================================================================== paneller
/** Koyu cam panel: degrade dolgu, ince parlak kenar, yumuşak gölge. */
fun Modifier.glass(radius: Dp = 18.dp, accent: Color? = null, alpha: Float = 1f): Modifier = this
    .shadow(14.dp, RoundedCornerShape(radius), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(listOf(Color(0xF01A2638).copy(alpha = 0.94f * alpha), Color(0xF00C121E).copy(alpha = 0.96f * alpha))))
    .border(1.dp, Brush.verticalGradient(listOf((accent ?: Color.White).copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(radius))

@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color? = null, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(accent = accent).padding(pad), content = content)
}

// =========================================================================== butonlar
enum class Tone(val top: Color, val bottom: Color, val text: Color, val glow: Color) {
    Gold(Color(0xFFFFE07A), Color(0xFFE8961C), Color(0xFF2A1A04), Color(0xFFFFB030)),
    Blue(Color(0xFF6AB6FF), Color(0xFF2560C8), Color.White, Color(0xFF4D9BFF)),
    Green(Color(0xFF7CF0A0), Color(0xFF1E9A52), Color(0xFF062A14), Color(0xFF5BE38A)),
    Red(Color(0xFFFF7A6A), Color(0xFFC0242C), Color.White, Color(0xFFFF4D4D)),
    Purple(Color(0xFFD0A0FF), Color(0xFF7438D0), Color.White, Color(0xFFB277FF)),
    Dark(Color(0xFF2E3C52), Color(0xFF172232), Color(0xFFDDE6F0), Color(0x00000000)),
}

@Composable
fun GButton(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Blue,
    icon: String? = null,
    enabled: Boolean = true,
    size: TextUnit = 15.sp,
    height: Dp = 46.dp,
    badge: Int = 0,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.5f, 900f), label = "btn")
    val t = if (enabled) tone else Tone.Dark
    Box(modifier.scale(sc)) {
        Box(
            Modifier
                .height(height)
                .then(if (t.glow.alpha > 0 && enabled) Modifier.shadow(10.dp, RoundedCornerShape(height / 2.6f), ambientColor = t.glow, spotColor = t.glow) else Modifier)
                .clip(RoundedCornerShape(height / 2.6f))
                .background(Brush.verticalGradient(listOf(t.top, t.bottom)))
                .drawBehind {
                    // üst parlama
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, this.size.height * 0.55f),
                        Offset(3f, 3f), Size(this.size.width - 6f, this.size.height * 0.5f), CornerRadius(this.size.height / 3f))
                }
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(height / 2.6f))
                .clickable(interactionSource = src, indication = null) {
                    Audio.play("click", bus = Audio.Bus.Ui, pitch = if (enabled) 1f else 0.7f)
                    if (enabled) onClick()
                }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, (height.value * 0.46f).dp, if (enabled) t.text else Pal.Sub)
                    if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
                }
                if (text.isNotEmpty()) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text, color = if (enabled) t.text else Pal.Sub, maxLines = 1,
                        style = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = size))
                    if (sub != null) Text(sub, color = (if (enabled) t.text else Pal.Sub).copy(alpha = 0.8f), maxLines = 1,
                        style = TextStyle(fontFamily = Body, fontSize = (size.value * 0.7f).sp))
                }
            }
        }
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape).background(Pal.Red)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { T(if (badge > 9) "9+" else "$badge", 10.sp, bold = true) }
    }
}

/** Yuvarlak ikon butonu (üst çubuk vb.) */
@Composable
fun RoundIcon(icon: String, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Pal.Text, badge: Int = 0, ring: Color = Color.White.copy(alpha = 0.2f), onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 900f), label = "ri")
    Box(modifier.scale(sc)) {
        Box(
            Modifier.size(size).shadow(8.dp, CircleShape).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF26344A), Color(0xFF111A28))))
                .border(1.dp, ring, CircleShape)
                .clickable(interactionSource = src, indication = null) { Audio.play("click", bus = Audio.Bus.Ui); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, size * 0.5f, tint) }
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp).size(17.dp).clip(CircleShape).background(Pal.Red).border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { T(if (badge > 9) "9+" else "$badge", 9.sp, bold = true) }
    }
}

// =========================================================================== küçük parçalar
@Composable
fun Chip(icon: String, value: String, color: Color, modifier: Modifier = Modifier, plus: (() -> Unit)? = null) {
    Row(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xCC0A101A))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(17.dp)).padding(start = 4.dp, end = if (plus != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 26.dp, color)
        Spacer(Modifier.width(6.dp))
        T(value, 14.sp, Pal.Text, bold = true)
        if (plus != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(24.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF7CF0A0), Color(0xFF1E9A52))))
                .clickable { plus() }, contentAlignment = Alignment.Center) { Icon("plus", 14.dp, Color.White) }
        }
    }
}

@Composable
fun Bar(value: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, label: String? = null, back: Color = Color(0xCC060A12)) {
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(back), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).clip(RoundedCornerShape(height / 2))
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 1f).lighten(1.35f), color)))
        )
        if (label != null) T(label, (height.value * 0.68f).sp, Color.White, Modifier.align(Alignment.Center), bold = true)
    }
}

fun Color.lighten(k: Float) = Color((red * k).coerceAtMost(1f), (green * k).coerceAtMost(1f), (blue * k).coerceAtMost(1f), alpha)

@Composable
fun Tabs(items: List<Pair<String, String>>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xAA060A12)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (on) Brush.verticalGradient(listOf(Color(0xFF3A6AB0), Color(0xFF1E3A70))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { Audio.play("click", bus = Audio.Bus.Ui); onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, 18.dp, if (on) Color.White else Pal.Sub)
                Spacer(Modifier.width(6.dp))
                T(label, 13.sp, if (on) Color.White else Pal.Sub, bold = on)
            }
        }
    }
}

/** Atlas'tan tek bir görseli Compose resmi olarak verir (önbellekli). */
object SpriteCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>) = size > 220
    }
    fun get(ctx: android.content.Context, key: String): ImageBitmap? = synchronized(cache) {
        if (cache.containsKey(key)) return cache[key]
        val b: Bitmap? = runCatching { Atlas.get(ctx).extract(key) }.getOrNull()
        val img = b?.asImageBitmap()
        cache[key] = img
        img
    }
}

@Composable
fun SpriteImage(key: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val img = remember(key) { SpriteCache.get(ctx, key) }
    if (img != null) Image(img, null, modifier, filterQuality = FilterQuality.High)
}

/** Eşya kutusu: nadirlik çerçevesi + simge + adet. */
@Composable
fun ItemSlot(id: String?, count: Int = 0, rarity: Int = 0, size: Dp = 64.dp, selected: Boolean = false, label: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val col = Pal.rarity(rarity)
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp))
            .background(
                if (id == null) Brush.verticalGradient(listOf(Color(0x88202C3E), Color(0x88101722)))
                else Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color(0xEE0E1520)))
            )
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else if (id == null) Pal.Line else col.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (id != null) SpriteImage("i/$id", Modifier.padding(size * 0.1f).fillMaxSize())
        else if (label != null) T(label, (size.value * 0.16f).sp, Pal.Sub, align = TextAlign.Center)
        if (count > 1) T("$count", (size.value * 0.2f).sp, Color.White, Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 2.dp), bold = true)
    }
}

/** Tam ekran karartma + ortada içerik (diyaloglar). */
@Composable
fun Dim(onDismiss: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB0000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() }
            .imePadding(), // yazı alanı olan pencereler klavyenin üstünde kalsın
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Ekran iskeleti: arka plan + başlık + geri. */
@Composable
fun ScreenFrame(title: String, icon: String, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF16243A), Color(0xFF070B14)), radius = 1800f))) {
        AmbientParticles(Modifier.fillMaxSize(), Color(0x55FFC070), 26)
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon("back", onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Icon(icon, 26.dp, Pal.Gold)
                Spacer(Modifier.width(8.dp))
                T(title, 26.sp, Pal.Text, display = true)
                Spacer(Modifier.weight(1f))
                right()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Yavaş yükselen parıltı/kor parçacıkları (arka plan canlılığı). */
@Composable
fun AmbientParticles(modifier: Modifier, color: Color, count: Int) {
    val t = rememberTime()
    val seeds = remember { List(count) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.4f + Math.random().toFloat(), Math.random().toFloat()) } }
    Canvas(modifier) {
        for (s in seeds) {
            val y = ((s[1] - t * 0.03f * s[2]) % 1f + 1f) % 1f
            val x = s[0] + kotlin.math.sin(t * 0.5f * s[2] + s[3] * 6f) * 0.02f
            val a = kotlin.math.sin(y * Math.PI.toFloat()).coerceAtLeast(0f)
            drawCircle(color.copy(alpha = color.alpha * a), (1.5f + s[2] * 2.5f) * density, Offset(x * size.width, y * size.height))
        }
    }
}

/** Saniye cinsinden sürekli artan zaman (animasyonlar için). */
@Composable
fun rememberTime(): Float {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            androidx.compose.runtime.withFrameNanos { now -> t = (now - start) / 1e9f }
        }
    }
    return t
}

// =========================================================================== bildirimler
object Toaster {
    var message by mutableStateOf<String?>(null)
    var good by mutableStateOf(true)
    var serial by mutableStateOf(0)
    fun show(m: String, ok: Boolean = true) { if (m.isBlank()) return; message = m; good = ok; serial++ }
}

@Composable
fun BoxScope.ToastHost() {
    val msg = Toaster.message
    val serial = Toaster.serial
    LaunchedEffect(serial) { if (msg != null) { delay(2000); Toaster.message = null } }
    if (msg != null) Row(
        Modifier.align(Alignment.TopCenter).padding(top = 70.dp).glass(22.dp, if (Toaster.good) Pal.Green else Pal.Red)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (Toaster.good) "check" else "close", 18.dp, if (Toaster.good) Pal.Green else Pal.Red)
        Spacer(Modifier.width(8.dp))
        T(msg, 14.sp)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Pal.Line, Color.Transparent))))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(Pal.Gold))
        Spacer(Modifier.width(8.dp))
        T(text.uppercase(), 13.sp, Pal.Sub, bold = true)
    }
}

/** İnce çerçeveli halka (avatar çerçevesi). */
@Composable
fun FrameRing(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(Brush.sweepGradient(listOf(color, color.copy(alpha = 0.3f), color)), style = Stroke(3.dp.toPx()))
    }
}
