package com.zenvex.online.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Model3D

/** Kuşanılmış silahın 3B modeli (lobi vitrini; yoksa yumruk) */
fun equippedWeaponModel(): String = runCatching {
    val w = GameSession.ui("inventory").optJSONObject("eq")?.optJSONObject("weapon")?.optString("id").orEmpty()
    Model3D.itemModel(w)
}.getOrDefault("")

/**
 * Vitrin karakteri (3B): boşta animasyonu, yön değişince yumuşak döner, yerde yumuşak gölge. Kare arka planda çizilir;
 * ilk kare hazır olana dek hiçbir şey çizilmez (bir iki kare). [actor]: önbellekte aynı vitrinin son karesi için kimlik.
 * Modeller yüklenmemişse false döner (çağıran piksel görsele düşer).
 */
@Composable
fun Hero3DView(actor: String, ch: Int, dir: Int, t: Float, unlocked: Boolean, weapon: String = "", modifier: Modifier = Modifier.fillMaxSize()): Boolean {
    if (!Model3D.ready) return false
    // yön: 8 basamaklı seçimi en kısa yoldan sarılmamış açıya çevir (döner, ters dönmez); hafif yarı profil
    val unwrapped = remember { floatArrayOf(dir * 45f) }
    val lastDir = remember { intArrayOf(dir) }
    if (lastDir[0] != dir) {
        val d = ((dir - lastDir[0]) % 8 + 12) % 8 - 4
        unwrapped[0] += d * 45f
        lastDir[0] = dir
    }
    val yaw by animateFloatAsState(unwrapped[0] + 22f, tween(320), label = "yaw")
    val dark = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.12f, 0.12f, 0.15f, 1f) }) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val hPx = with(density) { maxHeight.toPx() }
        // boyu kutunun ~%86'sı (Vex 1,98 m en uzun)
        val pxPerM = (hPx * 0.86f / 2.0f).coerceAtLeast(30f)
        // boşta döngüsü 6,25 sn; vitrinde 30 basamak (önbellekte az yer)
        val tq = ((t % 6.25f) / 6.25f * 30f).toInt() * (6.25f / 30f)
        val fr = Model3D.actor(actor, Model3D.CHARS.getOrElse(ch) { "kael" }, weapon, 0, tq, yaw, pxPerM, 3)
        val img = remember(fr) { fr?.bmp?.asImageBitmap() }
        Canvas(Modifier.fillMaxSize()) {
            val foot = size.height * 0.95f
            drawOval(Brush.radialGradient(listOf(Color(0x8C000000), Color(0x00000000)), Offset(size.width / 2, foot), size.width * 0.24f),
                Offset(size.width / 2 - size.width * 0.24f, foot - size.height * 0.035f), Size(size.width * 0.48f, size.height * 0.07f))
            if (fr != null && img != null) {
                val k = pxPerM / fr.pxPerM
                val dw = img.width * k; val dh = img.height * k
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height),
                    IntOffset((size.width / 2 - fr.ax * k).toInt(), (foot - fr.ay * k).toInt()), IntSize(dw.toInt(), dh.toInt()),
                    filterQuality = FilterQuality.Medium, colorFilter = if (unlocked) null else dark)
            }
        }
    }
    return true
}
