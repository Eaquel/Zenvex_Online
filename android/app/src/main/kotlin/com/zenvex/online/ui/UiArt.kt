package com.zenvex.online.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

/**
 * Arayüz görselleri (assets/ui): amblem, sahne arka planları (lobi, bakım, üretim), HUD tuşları, metal çerçeve.
 * Hepsi gerçek piksel ızgarasında saklanır; ekrana en yakın komşu ile büyütülür (net piksel).
 */
object UiArt {
    private val bmps = ConcurrentHashMap<String, Bitmap>()
    private val imgs = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /** assets/ui/<ad>.png ya da .webp (önbellekli; yoksa null) */
    fun bitmap(ctx: Context, name: String): Bitmap? {
        bmps[name]?.let { return it }
        if (name in missing) return null
        val b = listOf("png", "webp").firstNotNullOfOrNull { ext ->
            runCatching { ctx.assets.open("ui/$name.$ext").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        if (b == null) { missing += name; return null }
        bmps[name] = b
        return b
    }

    fun image(ctx: Context, name: String): ImageBitmap? {
        imgs[name]?.let { return it }
        val i = bitmap(ctx, name)?.asImageBitmap() ?: return null
        imgs[name] = i
        return i
    }
}

/** Piksel görsel (en yakın komşu, oran korunur, ortalanır). */
@Composable
fun ArtImage(name: String, modifier: Modifier, silhouette: Boolean = false) {
    val ctx = LocalContext.current
    val img = remember(name) { UiArt.image(ctx, name) } ?: return
    Canvas(modifier) {
        val k = minOf(size.width / img.width, size.height / img.height)
        val w = img.width * k; val h = img.height * k
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.None,
            colorFilter = if (silhouette) androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF0A0908)) else null)
    }
}

/** Oyun amblemi (kapüşonlu savaşçı + çapraz katana). */
@Composable
fun Emblem(size: Dp, modifier: Modifier = Modifier) = ArtImage("emblem", modifier.size(size))

/**
 * Sahne arka planı: ekranı kaplar, yavaşça kayar ve hafif yakınlaşır (Ken Burns); [dim] kadar karartılır.
 * [t] saniye cinsinden zaman (rememberTime); null ise sabit durur.
 */
@Composable
fun SceneBackground(
    name: String, t: Float?, modifier: Modifier = Modifier.fillMaxSize(), dim: Float = 0.2f,
    zoom: Float = 1.06f, ax: Float = 0.5f, ay: Float = 0.5f, drift: Float = 0.45f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val ctx = LocalContext.current
    val img = remember(name) { UiArt.image(ctx, name) }
    Box(modifier.then(Modifier.fillMaxSize())) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF15120F))
            if (img != null) {
                val z = zoom + (if (t != null) 0.04f * sin(t * 0.05f) else 0f)
                val k = maxOf(size.width / img.width, size.height / img.height) * z
                val w = img.width * k; val h = img.height * k
                // [ax, ay] kadraj merkezi (0 sol/üst, 1 sağ/alt), [drift] gezinme genliği
                val px = (ax + (if (t != null) drift * sin(t * 0.031f) else 0f)).coerceIn(0f, 1f)
                val py = (ay + (if (t != null) drift * 0.9f * sin(t * 0.023f + 1.1f) else 0f)).coerceIn(0f, 1f)
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height),
                    IntOffset(((size.width - w) * px).toInt(), ((size.height - h) * py).toInt()), IntSize(w.toInt(), h.toInt()),
                    filterQuality = FilterQuality.None)
            }
            if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        }
        content()
    }
}

/**
 * Perçinli metal çerçeve (assets/ui/frame.png) dokuz parçalı çizim: köşeler hiç esnemez, kenarlar tek eksende,
 * orta panel iki eksende esner. 1 kaynak piksel = [px] dp. İçerik için en az ~22 dp iç boşluk bırakılmalı.
 */
@Composable
fun Modifier.metalFrame(px: Dp = 1.6.dp, name: String = "frame", corner: Int = 18): Modifier {
    val ctx = LocalContext.current
    val img = remember(name) { UiArt.image(ctx, name) } ?: return this.pxPanel(Px.Panel)
    return this.drawBehind {
        val k = px.toPx()
        val c = corner // köşe kaynak boyu
        val sw = img.width; val sh = img.height
        val dc = (c * k).toInt()
        val W = size.width.toInt(); val H = size.height.toInt()
        fun part(sx: Int, sy: Int, sw2: Int, sh2: Int, dx: Int, dy: Int, dw: Int, dh: Int) {
            if (dw > 0 && dh > 0) drawImage(img, IntOffset(sx, sy), IntSize(sw2, sh2), IntOffset(dx, dy), IntSize(dw, dh), filterQuality = FilterQuality.None)
        }
        val mw = sw - 2 * c; val mh = sh - 2 * c
        part(c, c, mw, mh, dc, dc, W - 2 * dc, H - 2 * dc)               // orta
        part(c, 0, mw, c, dc, 0, W - 2 * dc, dc)                         // üst
        part(c, sh - c, mw, c, dc, H - dc, W - 2 * dc, dc)               // alt
        part(0, c, c, mh, 0, dc, dc, H - 2 * dc)                         // sol
        part(sw - c, c, c, mh, W - dc, dc, dc, H - 2 * dc)               // sağ
        part(0, 0, c, c, 0, 0, dc, dc)
        part(sw - c, 0, c, c, W - dc, 0, dc, dc)
        part(0, sh - c, c, c, 0, H - dc, dc, dc)
        part(sw - c, sh - c, c, c, W - dc, H - dc, dc, dc)
    }
}

/** Görseli kutuya çizer (DrawScope içinde): [fill] doğruysa tam kaplar, değilse oranı koruyup ortalar. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArt(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f,
                                                            fq: FilterQuality = FilterQuality.None) {
    if (w <= 0f || h <= 0f) return
    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(x.toInt(), y.toInt()), IntSize(w.toInt(), h.toInt()),
        alpha = alpha, filterQuality = fq)
}

/**
 * Yatay üç parçalı çizim (V10 tuş, sekme, unvan şeridi): uçlar ([capL]/[capR] kaynak px) oranı koruyarak yüksekliğe
 * göre ölçeklenir, orta kısım yatayda esner.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlice3(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, capL: Int, capR: Int = capL,
                                                               alpha: Float = 1f) {
    val k = h / img.height
    // dar kutuda uçlar daralır (her biri en çok genişliğin %40'ı), orta şerit kalır
    val f = minOf(1f, w * 0.8f / ((capL + capR) * k))
    val l = capL * k * f; val r = capR * k * f
    fun part(sx: Int, sw: Int, dx: Float, dw: Float) {
        if (sw > 0 && dw > 0f) drawImage(img, IntOffset(sx, 0), IntSize(sw, img.height), IntOffset(dx.toInt(), y.toInt()),
            IntSize((dx + dw).toInt() - dx.toInt(), h.toInt()), alpha = alpha, filterQuality = FilterQuality.None)
    }
    part(0, capL, x, l)
    part(capL, img.width - capL - capR, x + l, w - l - r)
    part(img.width - capR, capR, x + w - r, r)
}

/** Arayüz görseliyle zemin: yoksa [fallback] (null ise hiçbir şey) */
@Composable
fun Modifier.artBehind(name: String, slice: Int = 0, alpha: Float = 1f, fallback: Modifier? = null): Modifier {
    val ctx = LocalContext.current
    val img = remember(name) { UiArt.image(ctx, name) } ?: return if (fallback != null) this.then(fallback) else this
    return this.drawBehind {
        if (slice > 0) drawSlice3(img, 0f, 0f, size.width, size.height, slice, alpha = alpha)
        else drawArt(img, 0f, 0f, size.width, size.height, alpha, FilterQuality.Low)
    }
}

/** Kapat tuşu: V10 çarpı görseli (yoksa ikonlu kare tuş). */
@Composable
fun CloseButton(size: Dp = 36.dp, onClick: () -> Unit) {
    val ctx = LocalContext.current
    if (remember { UiArt.image(ctx, "btn_close") } == null) { PxIcon("close", size = size, onClick = onClick); return }
    ArtImage("btn_close", Modifier.size(size).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        com.zenvex.online.game.Audio.play("click", bus = com.zenvex.online.game.Audio.Bus.Ui); onClick()
    })
}

/** Görseli kutuyu kaplayacak şekilde ortadan kırparak zemine çizer ([dim] kadar karartır). Yoksa değişiklik yok. */
@Composable
fun Modifier.artCover(name: String, dim: Float = 0f, ay: Float = 0.5f): Modifier {
    val ctx = LocalContext.current
    val img = remember(name) { UiArt.image(ctx, name) } ?: return this
    return this.drawBehind {
        val k = maxOf(size.width / img.width, size.height / img.height)
        val sw = (size.width / k).toInt().coerceAtMost(img.width); val sh = (size.height / k).toInt().coerceAtMost(img.height)
        val sx = (img.width - sw) / 2; val sy = ((img.height - sh) * ay).toInt()
        drawImage(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None)
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}
