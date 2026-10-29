package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.zenvex.online.ui.MapArea

/**
 * V15 gerçek bölge haritası: bulunulan bölge, oyunun kendi zemin dokuları (Rust, küçük ölçekte) ve kendi görselleriyle
 * (ağaç, kaya, bina, süs: atlas sprite'ları) ekran dışı bir resme bir kez çizilir. Mini harita ve büyük harita bunu
 * kullanır; haritada görülen şey bölgenin gerçekten küçültülmüş hâlidir. Bölge değişince yeniden çizilir (~arka planda).
 */
object RegionMap {
    /** en uzun kenar (piksel) */
    private const val MAX_SIDE = 1024
    private const val MAX_STATICS = 30000

    class Snap(val area: Int, val bmp: Bitmap, val x0: Float, val y0: Float, val step: Float) {
        /** dünya pikseli → resim pikseli */
        fun u(x: Float) = (x - x0) / step
        fun v(y: Float) = (y - y0) / step
    }

    @Volatile var snap: Snap? = null
        private set
    @Volatile private var building = -1
    /** başarısız denemeden sonra bekleme (dokular yüklenirken her karede iş parçacığı açılmasın) */
    @Volatile private var retryAt = 0L
    /** zemin şeritler hâlinde çizilir: oyun döngüsü kilidi uzun süre tutulmaz */
    private const val BAND = 48

    /** Bölge [ai] için harita yoksa arka planda çizer. Dokular henüz yüklü değilse sonra yeniden denenir. */
    fun ensure(ai: Int, areas: List<MapArea>, statics: Array<Sprite?>) {
        if (ai < 0 || snap?.area == ai || building >= 0 || System.currentTimeMillis() < retryAt) return
        val a = areas.getOrNull(ai) ?: return
        building = ai
        Thread {
            try {
                val s = build(ai, a, statics)
                if (s != null) snap = s else retryAt = System.currentTimeMillis() + 3000
            } catch (e: Throwable) {
                Log.w("zenvex", "bölge haritası çizilemedi", e)
                // her karede yeni iş parçacığı açılmasın
                retryAt = System.currentTimeMillis() + 5000
            } finally {
                building = -1
            }
        }.apply { name = "region-map"; isDaemon = true }.start()
    }

    /** Testler için: eşzamanlı çizim */
    internal fun buildNow(ai: Int, a: MapArea, statics: Array<Sprite?>): Snap? = build(ai, a, statics)?.also { snap = it }

    private fun build(ai: Int, a: MapArea, statics: Array<Sprite?>): Snap? {
        val x0 = a.rects.minOf { it[0] }; val y0 = a.rects.minOf { it[1] }
        val x1 = a.rects.maxOf { it[2] }; val y1 = a.rects.maxOf { it[3] }
        val step = maxOf(x1 - x0, y1 - y0) / MAX_SIDE
        val w = ((x1 - x0) / step).toInt().coerceAtLeast(1); val h = ((y1 - y0) / step).toInt().coerceAtLeast(1)
        val px = IntArray(w * h)
        val band = IntArray(w * BAND)
        var y = 0
        while (y < h) {
            val bh = minOf(BAND, h - y)
            val ok = synchronized(GameSession.lock) {
                if (GameSession.handle == 0L) 0 else Native.renderRegion(GameSession.handle, x0, y0 + y * step, step, w, bh, band)
            }
            if (ok == 0) return null
            System.arraycopy(band, 0, px, y * w, w * bh)
            y += bh
        }
        val ground = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        // nesneler: oyundaki görselleriyle, y'ye göre sıralı (öndeki arkadakini örter)
        val buf = FloatArray(MAX_STATICS * 4)
        val n = synchronized(GameSession.lock) {
            if (GameSession.handle == 0L) 0 else Native.regionStatics(GameSession.handle, x0, y0, x1, y1, buf)
        }
        val order = (0 until n).sortedBy { buf[it * 4 + 2] }
        val atlas = Atlas.peek() ?: return Snap(ai, ground, x0, y0, step)
        val paint = Paint().apply { isFilterBitmap = true }
        // Atlas sayfaları GPU'da (HARDWARE): yazılımsal tuvale çizilemez (eskiden burada hata alınıyor, harita hiç
        // oluşmuyor, mini harita ve büyük harita eski düz renkli dünya haritasında kalıyordu). Android 9+ resim GPU'da
        // kaydedilip donanımla çizilir; olmazsa sayfaların yazılımsal kopyalarıyla.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val hw = runCatching {
                val pic = android.graphics.Picture()
                val pc = pic.beginRecording(w, h)
                pc.drawBitmap(ground, 0f, 0f, null)
                for (i in order) {
                    val sp = statics.getOrNull(buf[i * 4].toInt()) ?: continue
                    atlas.draw(pc, sp, (buf[i * 4 + 1] - x0) / step, (buf[i * 4 + 2] - y0) / step, 1f / step, paint)
                }
                pic.endRecording()
                Bitmap.createBitmap(pic, w, h, Bitmap.Config.HARDWARE)
            }.getOrNull()
            if (hw != null) { ground.recycle(); return Snap(ai, hw, x0, y0, step) }
        }
        val bmp = ground.copy(Bitmap.Config.ARGB_8888, true)
        ground.recycle()
        val c = Canvas(bmp)
        for (i in order) {
            val sp = statics.getOrNull(buf[i * 4].toInt()) ?: continue
            atlas.drawSoft(c, sp, (buf[i * 4 + 1] - x0) / step, (buf[i * 4 + 2] - y0) / step, 1f / step, paint)
        }
        return Snap(ai, bmp, x0, y0, step)
    }

    /** Dünya yeniden oluşturulunca (sunucu/karakter değişimi) eski resim atılır */
    fun clear() { snap = null }
}
