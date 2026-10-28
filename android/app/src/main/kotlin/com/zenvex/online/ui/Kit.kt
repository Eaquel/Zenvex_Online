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
import androidx.compose.foundation.layout.widthIn
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
import com.zenvex.online.game.I18n
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
        I18n.show(text, maxLines == 1), modifier, color = color, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            // tüm arayüz piksel yazı tipiyle (eski cam görünüm kaldırıldı)
            fontFamily = PixelFont, fontSize = size,
            shadow = Shadow(glow ?: Color(0xE6000000), Offset(if (glow != null) 0f else 2f, if (glow != null) 0f else 2f), if (glow != null) 14f else 0f),
        ),
    )
}

// =========================================================================== paneller
/** Eski cam panelin yerine piksel panel: koyu zemin, sert kenar, isteğe bağlı renkli iç çizgi. */
fun Modifier.glass(radius: Dp = 18.dp, accent: Color? = null, alpha: Float = 1f): Modifier = this
    .clip(RoundedCornerShape(3.dp))
    .background(Px.Panel.copy(alpha = Px.Panel.alpha * alpha))
    .pxBevel()
    .border(2.dp, Px.Edge, RoundedCornerShape(3.dp))
    .then(if (accent != null) Modifier.padding(2.dp).border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(2.dp)) else Modifier)

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
    // piksel perçinli plaka (PxButton); ton → renk
    val col = when (tone) {
        Tone.Gold -> Color(0xFFB08A2A); Tone.Blue -> Px.Blue; Tone.Green -> Px.Green; Tone.Red -> Px.Red
        Tone.Purple -> Color(0xFF6A4AA0); Tone.Dark -> Px.Dark
    }
    // boyut verilmezse içeriği kadar, verilirse (fillMaxWidth / weight / width) o kadar geniş
    Box(modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
        PxButton(if (sub != null) "$text · $sub" else text, Modifier.fillMaxWidth(), col, icon, size, height, enabled, onClick = onClick)
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape).background(Color(0xFFE02828))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { PT(if (badge > 9) "9+" else "$badge", 10.sp) }
    }
}

/** İkon butonu (piksel kare) */
@Composable
fun RoundIcon(icon: String, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = Pal.Text, badge: Int = 0, ring: Color = Color.White.copy(alpha = 0.2f), onClick: () -> Unit) {
    PxIcon(icon, modifier, size, badge, tint, onClick = onClick)
}

// =========================================================================== küçük parçalar
@Composable
fun Chip(icon: String, value: String, color: Color, modifier: Modifier = Modifier, plus: (() -> Unit)? = null) {
    Row(
        modifier.height(34.dp).pxPanel(Px.PanelDark).padding(start = 4.dp, end = if (plus != null) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 24.dp, color)
        Spacer(Modifier.width(6.dp))
        PT(value, 14.sp, color)
        if (plus != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(24.dp).background(Px.Green).border(2.dp, Px.Edge).clickable { plus() }, contentAlignment = Alignment.Center) {
                Icon("plus", 14.dp, Color.White)
            }
        }
    }
}

@Composable
fun Bar(value: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, label: String? = null, back: Color = Color(0xCC060A12)) {
    Box(modifier, contentAlignment = Alignment.Center) {
        PxBar(value, color, Modifier.fillMaxWidth(), height)
        if (label != null) PT(label, (height.value * 0.7f).sp, Color.White)
    }
}

fun Color.lighten(k: Float) = Color((red * k).coerceAtMost(1f), (green * k).coerceAtMost(1f), (blue * k).coerceAtMost(1f), alpha)

@Composable
fun Tabs(items: List<Pair<String, String>>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Row(
                Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) Px.Panel else Px.PanelDark).pxBevel(light = if (on) 0.10f else 0.04f)
                    .drawBehind { if (on) drawRect(Px.Clan, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                    .border(2.dp, Px.Edge, RoundedCornerShape(3.dp))
                    .clickable { Audio.play("click", bus = Audio.Bus.Ui); onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != "dot") { Icon(icon, 18.dp, if (on) Px.Clan else Px.Sub); Spacer(Modifier.width(6.dp)) }
                PT(label, 14.sp, if (on) Px.Text else Px.Sub)
            }
        }
    }
}

/** Atlas'tan tek bir görseli Compose resmi olarak verir (önbellekli). */
object SpriteCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>) = size > 220
    }
    fun clear() = synchronized(cache) { cache.clear() }
    fun get(ctx: android.content.Context, key: String): ImageBitmap? = synchronized(cache) {
        if (cache.containsKey(key)) return cache[key]
        // eşya simgesi yoksa aynı adlı dünya nesnesi (ör. kurulabilir tezgâh: s/workbench)
        val b: Bitmap? = runCatching { Atlas.get(ctx).extract(key) }.getOrNull()
            ?: if (key.startsWith("i/")) runCatching { Atlas.get(ctx).extract("s/" + key.removePrefix("i/")) }.getOrNull() else null
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
fun ItemSlot(id: String?, count: Int = 0, rarity: Int = 0, size: Dp = 64.dp, selected: Boolean = false, label: String? = null, modifier: Modifier = Modifier,
             dur: Int = -1, onClick: (() -> Unit)? = null) {
    // piksel eşya kutusu (PxSlot ile aynı görünüm); dur ≥ 0 → dayanıklılık çubuğu (yüzde)
    val item = id?.let { org.json.JSONObject().put("id", it).put("count", count).put("rarity", rarity).apply { if (dur >= 0) put("dur", dur) } }
    PxSlot(item, size, selected, hint = label,
        modifier = modifier.then(if (onClick != null) Modifier.clickable { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); onClick() } else Modifier))
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
fun ScreenFrame(title: String, icon: String, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {}, bg: String? = null,
                content: @Composable ColumnScope.() -> Unit) {
    PxFrame(if (I18n.turkish) title.lowercase(java.util.Locale.forLanguageTag("tr-TR")).titleTr() else I18n.t(title), icon, onBack, right, bg, content)
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

/**
 * Bildirim: piksel demir plaka, solda renkli şerit + simge karesi; yukarıdan kayarak gelir, 2,2 sn sonra çekilir.
 * Yeşil başarı, kırmızı hata; altın/ödül içeren mesajlar altın şeritle.
 */
@Composable
fun BoxScope.ToastHost() {
    val msg = Toaster.message
    val serial = Toaster.serial
    var shown by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(serial) {
        if (msg != null) { shown = msg; visible = true; delay(2200); visible = false; delay(250); Toaster.message = null }
    }
    val text = shown ?: return
    val reward = Toaster.good && (text.contains("altın") || text.contains("XP") || text.contains("kazandın") || text.contains("eklendi"))
    val accent = when { !Toaster.good -> Color(0xFFE0483C); reward -> Px.Clan; else -> Px.Name }
    val icon = when { !Toaster.good -> "close"; reward -> "coin"; else -> "check" }
    // alt orta, cep/çanta sırasının üstünde: üst orta bölge adı, etkinlik ve duyuru afişlerine ayrıldı (üst üste binmez)
    androidx.compose.animation.AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomCenter).padding(bottom = 124.dp),
        enter = androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { it / 2 } + androidx.compose.animation.fadeOut(),
    ) { ToastPlate(text, accent, icon) }
}

@Composable
fun ToastPlate(text: String, accent: Color, icon: String) {
    Row(
        Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xF21E1D19))
            .border(2.dp, Px.Edge, RoundedCornerShape(3.dp))
            .drawBehind {
                drawRect(accent, Offset.Zero, Size(5.dp.toPx(), size.height))
                drawRect(Color.White.copy(alpha = 0.08f), Offset(5.dp.toPx(), 0f), Size(size.width, 2.dp.toPx()))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(5.dp.toPx(), size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()))
            }
            .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.22f)).border(1.5.dp, accent, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center) { Icon(icon, 16.dp, accent) }
        Spacer(Modifier.width(10.dp))
        PT(if (I18n.turkish) text.titleTr() else I18n.t(text), 15.sp, Px.Text, maxLines = 2)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Pal.Line, Color.Transparent))))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp, 16.dp).background(Px.Clan))
        Spacer(Modifier.width(8.dp))
        PT(text, 15.sp, Px.Clan)
    }
}

/** İnce çerçeveli halka (avatar çerçevesi). */
@Composable
fun FrameRing(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(Brush.sweepGradient(listOf(color, color.copy(alpha = 0.3f), color)), style = Stroke(3.dp.toPx()))
    }
}
