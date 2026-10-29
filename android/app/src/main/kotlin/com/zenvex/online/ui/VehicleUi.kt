package com.zenvex.online.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Audio
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Model3D
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Araç eşyasının 3B modeli: veh_moto → Nightstalker, veh_car → Hellhound */
fun vehicleModel(id: String): String = if (id == "veh_car") "hellhound" else "nightstalker"

/**
 * V20: çantada araç eşyaları anahtar simgesiyle değil aracın kendi görüntüsüyle görünür: oyundaki 3B model 3/4 yandan
 * bir kez çizilip siluetine kırpılır ve saklanır.
 */
object VehicleIcons {
    private val cache = HashMap<String, Bitmap>()

    @Synchronized fun bitmap(model: String): Bitmap? {
        cache[model]?.let { return it }
        if (!Model3D.ready) return null
        val fr = Model3D.vehicle("icon_$model", model, "", 120f, 0f, 0f, 0f, 0f, if (model == "hellhound") 46f else 70f, 3) ?: return null
        val src = fr.bmp
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 24) {
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        if (maxX <= minX || maxY <= minY) return null
        val out = Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
        cache[model] = out
        return out
    }
}

/** Araç görüntüsü hazır olana dek yoklar (model yüklenip çizilene kadar) */
@Composable
fun rememberVehicleIcon(id: String): ImageBitmap? {
    val model = vehicleModel(id)
    val img by produceState<ImageBitmap?>(VehicleIcons.bitmap(model)?.asImageBitmap(), model) {
        var n = 0
        while (value == null && n++ < 100) {
            VehicleIcons.bitmap(model)?.let { value = it.asImageBitmap(); return@produceState }
            delay(150)
        }
    }
    return img
}

/** Araç eşyası kutunun içinde: kendi görüntüsü (yoksa atlas simgesi) */
@Composable
fun VehicleImage(id: String, modifier: Modifier) {
    val img = rememberVehicleIcon(id)
    if (img == null) { SpriteImage("i/$id", modifier); return }
    Canvas(modifier) {
        val k = minOf(size.width / img.width, size.height / img.height)
        val dw = img.width * k; val dh = img.height * k
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
            IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.Medium)
    }
}

/** Motosiklet silueti (boş araç yuvası ve araç tuşu): iki teker, gövde, sele, gidon */
fun DrawScope.motoGlyph(color: Color, stroke: Float = size.minDimension * 0.07f) {
    val w = size.width; val h = size.height
    val r = w * 0.17f
    val back = Offset(w * 0.22f, h * 0.68f); val front = Offset(w * 0.78f, h * 0.68f)
    drawCircle(color, r, back, style = Stroke(stroke))
    drawCircle(color, r, front, style = Stroke(stroke))
    drawCircle(color, r * 0.25f, back); drawCircle(color, r * 0.25f, front)
    // gövde: arka teker → motor → ön çatal
    val engine = Offset(w * 0.47f, h * 0.62f)
    drawLine(color, back, engine, stroke, StrokeCap.Round)
    drawLine(color, engine, Offset(w * 0.66f, h * 0.38f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.66f, h * 0.38f), front, stroke, StrokeCap.Round)
    // depo ve sele
    drawLine(color, Offset(w * 0.30f, h * 0.44f), Offset(w * 0.60f, h * 0.42f), stroke * 1.6f, StrokeCap.Round)
    drawRect(color, Offset(w * 0.40f, h * 0.55f), Size(w * 0.14f, h * 0.12f))
    // gidon
    drawLine(color, Offset(w * 0.66f, h * 0.38f), Offset(w * 0.72f, h * 0.24f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.66f, h * 0.24f), Offset(w * 0.80f, h * 0.24f), stroke, StrokeCap.Round)
}

@Composable
fun MotoIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) { motoGlyph(tint) }

/**
 * Çantanın sağındaki yuvarlak araç tuşu: basınca yuvadaki araç yanına gelir ve binersin; araçtayken basınca inersin.
 * Patladıysa kalan bekleme süresi, değilse aracın canı halkada görünür.
 */
@Composable
fun VehRideButton(size: Dp = 50.dp, onEmpty: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    val v = remember(tick) { GameSession.ui("vehicle") }
    val id = v.optString("id")
    val riding = v.optBoolean("riding")
    val cd = v.optInt("cd")
    val hp = v.optInt("hp", 100)
    var busy by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (riding) Color(0xE0305A2A) else Color(0xCC2A2620))
            .border(2.dp, if (id.isEmpty()) Color(0x66FFFFFF) else if (riding) Px.GreenHi else Px.Clan, CircleShape)
            .clickable {
                if (busy) return@clickable
                if (id.isEmpty()) { onEmpty(); return@clickable }
                busy = true
                Audio.play("click", 0.6f, bus = Audio.Bus.Ui)
                val r = GameSession.cmd("veh_ride")
                if (r.msg.isNotEmpty()) Toaster.show(r.msg, r.ok)
                busy = false; tick++
            },
        contentAlignment = Alignment.Center,
    ) {
        // can halkası (yeşil → kırmızı)
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            if (id.isNotEmpty() && cd <= 0) drawArc(durColor(hp), -90f, 360f * hp / 100f, false, style = Stroke(3.dp.toPx()))
        }
        MotoIcon(size * 0.62f, if (id.isEmpty()) Color(0x88FFFFFF) else if (cd > 0) Color(0x88FFFFFF) else Color(0xFFF0E6C8))
        if (cd > 0) PT("$cd", 14.sp, Color.White, outline = true)
    }
}

/** Çantada ceplerin altındaki araç yuvası: boşken motosiklet silueti; doluyken aracın görüntüsü, canı ve beklemesi */
@Composable
fun VehSlot(veh: JSONObject?, size: Dp, selected: Boolean, glow: Boolean, modifier: Modifier) {
    val item = veh?.takeIf { it.optString("id").isNotEmpty() }
    Box(contentAlignment = Alignment.Center) {
        PxSlot(item, size, selected, glow = glow, modifier = modifier)
        if (item == null) Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            MotoIcon(size * 0.62f, Color(0x55FFFFFF))
            PT("Araç", (size.value * 0.17f).sp, Color(0x80FFFFFF), Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp), outline = true)
        }
    }
}
