package online.zenvex.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import online.zenvex.R
import online.zenvex.game.Assets
import online.zenvex.game.Audio

object Pal {
    val Outline = Color(0xFF1B1C26)
    val Night = Color(0xFF0E1424)
    val Navy = Color(0xFF23365A)
    val NavyLight = Color(0xFF33507E)
    val NavyDark = Color(0xFF172238)
    val Blue = Color(0xFF4A6FA5)
    val Mustard = Color(0xFFE8A93A)
    val MustardLight = Color(0xFFFFD25E)
    val MustardDark = Color(0xFFB9772A)
    val Cream = Color(0xFFFFE9A8)
    val Red = Color(0xFFE04848)
    val RedDark = Color(0xFFA02C3C)
    val RedDeep = Color(0xFF5E1A2A)
    val Green = Color(0xFF7FC44A)
    val GreenDark = Color(0xFF2F6B33)
    val Cyan = Color(0xFF5EE0FF)
    val White = Color(0xFFEEF2F0)
    val Gray = Color(0xFF9A9AA6)
    val GrayDark = Color(0xFF45465A)
}

val Pixel = FontFamily(Font(R.font.pixelify, FontWeight.Normal), Font(R.font.pixelify, FontWeight.Bold))

@Composable
fun PText(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = Pal.White,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    bold: Boolean = false,
    shadow: Boolean = true,
) {
    Box(modifier) {
        if (shadow) Text(
            text, color = Pal.Outline, modifier = Modifier.offset(1.5.dp, 1.5.dp), textAlign = align,
            style = TextStyle(fontFamily = Pixel, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        )
        Text(
            text, color = color, textAlign = align,
            style = TextStyle(fontFamily = Pixel, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

/** Pixel çerçeve: koyu dış çizgi, üstte açık şerit, altta gölge. */
fun Modifier.pixelFrame(fill: Color, light: Color, shade: Color, edge: Color = Pal.Outline, px: Dp = 2.dp): Modifier =
    this.drawBehind {
        val p = px.toPx()
        drawRect(edge, Offset(p, 0f), Size(size.width - 2 * p, size.height))
        drawRect(edge, Offset(0f, p), Size(size.width, size.height - 2 * p))
        drawRect(fill, Offset(p, p), Size(size.width - 2 * p, size.height - 2 * p))
        drawRect(light, Offset(p, p), Size(size.width - 2 * p, p))
        drawRect(shade, Offset(p, size.height - 3 * p), Size(size.width - 2 * p, 2 * p))
    }

enum class BtnStyle(val fill: Color, val light: Color, val shade: Color, val text: Color) {
    Primary(Pal.Mustard, Pal.MustardLight, Pal.MustardDark, Pal.Outline),
    Secondary(Pal.NavyLight, Pal.Blue, Pal.Navy, Pal.White),
    Danger(Pal.RedDark, Pal.Red, Pal.RedDeep, Pal.White),
    Green(Color(0xFF4F9A3A), Pal.Green, Pal.GreenDark, Pal.White),
}

@Composable
fun PixelButton(
    text: String,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Secondary,
    icon: String? = null,
    locked: Boolean = false,
    textSize: TextUnit = 14.sp,
    enabled: Boolean = true,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val st = if (!enabled) BtnStyle.Secondary else style
    Box(modifier) {
        Box(
            Modifier
                .offset(y = if (pressed) 2.dp else 0.dp)
                .pixelFrame(if (enabled) st.fill else Pal.GrayDark, if (enabled) st.light else Pal.Gray, if (enabled) st.shade else Pal.Outline)
                .clickable(interactionSource = src, indication = null) {
                    Audio.play("click", bus = Audio.Bus.Ui, pitch = if (locked) 0.7f else 1f)
                    if (locked) Toaster.locked() else if (enabled) onClick()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    PixelIcon(icon, 20.dp)
                    if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
                }
                if (text.isNotEmpty()) PText(text, textSize, if (enabled) st.text else Pal.Gray, shadow = st.text != Pal.Outline)
            }
        }
        if (locked) PixelIcon("lock", 14.dp, Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(18.dp).background(Pal.Outline).padding(2.dp).background(Pal.Red),
            contentAlignment = Alignment.Center,
        ) { PText("$badge", 10.sp, Pal.White) }
    }
}

@Composable
fun PixelPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.pixelFrame(Pal.Navy.copy(alpha = 0.94f), Pal.NavyLight, Pal.NavyDark, Pal.Night).padding(12.dp),
        content = content,
    )
}

@Composable
fun PixelIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(name) { Assets.get(ctx).icons[name] }
    if (bmp != null) PixelBitmap(bmp, modifier.size(size))
}

@Composable
fun PixelBitmap(bmp: Bitmap, modifier: Modifier) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    Image(img, null, modifier, filterQuality = FilterQuality.None)
}

@Composable
fun Bar(value: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, label: String? = null) {
    Box(modifier.height(height).background(Pal.Outline).padding(2.dp).background(Pal.GrayDark)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(height).background(color))
        if (label != null) PText(label, (height.value * 0.7f).sp, Pal.White, Modifier.align(Alignment.Center))
    }
}

/** Ekran başlığı + geri tuşu. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelButton("", icon = "back", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        PText(title, 22.sp, Pal.Cream, bold = true)
        Spacer(Modifier.weight(1f))
        right()
    }
}

/** Ekranın üstünde kısa bildirim ("Yakında!" vb.) */
object Toaster {
    var message by mutableStateOf<String?>(null)
    var icon by mutableStateOf("lock")
    var serial by mutableStateOf(0)
    fun show(m: String, icon: String = "star") { message = m; this.icon = icon; serial++ }
    fun locked() = show("Yakında! Bu özellik geliştiriliyor.", "lock")
}

@Composable
fun BoxScope.ToastHost() {
    val msg = Toaster.message
    val serial = Toaster.serial
    LaunchedEffect(serial) {
        if (msg != null) { delay(1600); Toaster.message = null }
    }
    if (msg != null) Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp)) {
        Row(
            Modifier.pixelFrame(Pal.NavyDark, Pal.NavyLight, Pal.Night).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(Toaster.icon, 18.dp)
            Spacer(Modifier.width(8.dp))
            PText(msg, 14.sp, Pal.Cream)
        }
    }
}
