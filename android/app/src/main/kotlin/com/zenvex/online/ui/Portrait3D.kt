package com.zenvex.online.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zenvex.online.game.Model3D
import kotlinx.coroutines.delay

/**
 * 3B model portreleri (V18): profil resimleri, sol üst oyuncu portresi ve konuşma paneli artık eski 2B görsellerden değil,
 * oyundaki 3B modellerden gelir. Önde duran modelin baş-omuz kadrajı bir kez kesilip saklanır.
 */
object Portrait3D {
    private val cache = HashMap<String, Bitmap>()

    /** [key]: Model3D.CHARS ya da NPC modeli ("npc_marco"); model henüz çizilmediyse null (çağıran tekrar dener) */
    @Synchronized fun bitmap(key: String): Bitmap? {
        cache[key]?.let { return it }
        if (!Model3D.ready || key.isEmpty()) return null
        val fr = Model3D.actor("portrait_$key", key, "", 0, 0f, 0f, 300f, 3) ?: return null
        val src = fr.bmp
        // gerçek siluet sınırları (kare kenarlarında boşluk olabilir)
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 24) {
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        if (maxY <= minY) return null
        val bodyH = (maxY - minY).toFloat(); val bodyW = (maxX - minX).toFloat()
        // baş ve omuzlar: gövde boyunun ~%30'u (geniş omuzlu modellerde omuz genişliğine göre)
        val side = maxOf(bodyH * 0.30f, bodyW * 0.62f).coerceAtMost(minOf(w, h).toFloat())
        val cx = fr.ax.coerceIn(side / 2, w - side / 2)
        val x0 = (cx - side / 2).toInt().coerceIn(0, (w - side).toInt().coerceAtLeast(0))
        val y0 = (minY - side * 0.05f).toInt().coerceIn(0, (h - side).toInt().coerceAtLeast(0))
        val out = Bitmap.createBitmap(src, x0, y0, side.toInt().coerceAtMost(w - x0), side.toInt().coerceAtMost(h - y0))
        cache[key] = out
        return out
    }
}

/** Portre bitmap'i hazır olana dek (model yüklenip bir kare çizilene kadar) yoklar; hazır olunca yeniler. */
@Composable
fun rememberPortrait(key: String): ImageBitmap? {
    val img by produceState<ImageBitmap?>(Portrait3D.bitmap(key)?.asImageBitmap(), key) {
        var n = 0
        while (value == null && n++ < 100) {
            Portrait3D.bitmap(key)?.let { value = it.asImageBitmap(); return@produceState }
            delay(150)
        }
    }
    return img
}

/** Konuşma paneli NPC'si: konuşma animasyonuyla (3B); model hazır değilse false (çağıran eski görsele düşer). */
@Composable
fun Npc3DView(kind: Int, t: Float, modifier: Modifier = Modifier.fillMaxSize()): Boolean {
    if (!Model3D.ready) return false
    val name = Model3D.NPCS.getOrElse(kind) { "npc_soldier" }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val hPx = with(density) { maxHeight.toPx() }
        val pxPerM = (hPx * 0.93f / 1.95f).coerceAtLeast(40f)
        val tq = ((t % 3f) / 3f * 24f).toInt() * (3f / 24f)
        val fr = Model3D.actor("dialog_npc", name, "", Model3D.A_TALK, tq, 0f, pxPerM, 3)
        val img = remember(fr) { fr?.bmp?.asImageBitmap() }
        Canvas(Modifier.fillMaxSize()) {
            if (fr != null && img != null) {
                val k = pxPerM / fr.pxPerM
                val foot = size.height * 0.98f
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height),
                    IntOffset((size.width / 2 - fr.ax * k).toInt(), (foot - fr.ay * k).toInt()),
                    IntSize((img.width * k).toInt(), (img.height * k).toInt()), filterQuality = FilterQuality.Medium)
            }
        }
    }
    return true
}
