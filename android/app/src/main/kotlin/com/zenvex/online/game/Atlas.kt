package com.zenvex.online.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONObject

/** Atlas içindeki bir görsel. ax/ay: çapa (ayak noktası) kırpılmış görüntüde. */
class Sprite(val page: Int, val src: Rect, val ax: Float, val ay: Float, val k: Float = 1f) {
    val w get() = src.width()
    val h get() = src.height()
}

/**
 * tools/assetgen/pack.py çıktısı (assets/v2). Sayfalar gruplar halinde tembel yüklenir;
 * kahraman/zombi sayfaları sadece gerektiğinde belleğe girer.
 */
class Atlas private constructor(ctx: Context) {
    private val am = ctx.assets
    private val manifest = JSONObject(am.open("v2/manifest.json").bufferedReader().readText())
    private val pageFiles: List<String>
    private val pages = HashMap<Int, Bitmap>()
    private val pageGroup = HashMap<Int, String>()
    private val groups = HashMap<String, IntArray>()
    private val sprites = HashMap<String, Sprite>()
    private val attach = manifest.optJSONObject("attach") ?: JSONObject()
    // Sayfalar GPU belleğinde (HARDWARE) tutulur: Java yığınını doldurmaz, donanım tuvalinde doğrudan çizilir.
    private val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE; inScaled = false }
    private val softOpts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
    /** efekt meta: ad → [kare, fps, döngü(1/0), toplamalı(1/0), piksel(1/0: süzgeçsiz çizilir)] */
    val fx = HashMap<String, FloatArray>()
    val heroAnims = HashMap<String, IntArray>()   // isim → [kare, fps, döngü]
    val zombieAnims = HashMap<String, IntArray>()
    private val actorAnims = HashMap<String, HashMap<String, IntArray>>()

    /** Aktörün animasyon tablosu: kendine özel tablo varsa o, yoksa türün ortak tablosu ([fallback]) */
    fun anims(prefix: String, fallback: Map<String, IntArray>): Map<String, IntArray> = actorAnims[prefix] ?: fallback

    init {
        val pf = manifest.getJSONArray("pages")
        pageFiles = List(pf.length()) { pf.getString(it) }
        val g = manifest.getJSONObject("groups")
        for (k in g.keys()) {
            val a = g.getJSONArray(k)
            val ids = IntArray(a.length()) { a.getInt(it) }
            groups[k] = ids
            ids.forEach { pageGroup[it] = k }
        }
        val s = manifest.getJSONObject("sprites")
        for (k in s.keys()) {
            val a = s.getJSONArray(k)
            val x = a.getInt(1); val y = a.getInt(2)
            sprites[k] = Sprite(a.getInt(0), Rect(x, y, x + a.getInt(3), y + a.getInt(4)), a.getDouble(5).toFloat(), a.getDouble(6).toFloat(),
                if (a.length() > 7) a.getDouble(7).toFloat() else 1f)
        }
        manifest.optJSONObject("fx")?.let { f ->
            for (k in f.keys()) {
                val m = f.getJSONObject(k)
                fx[k] = floatArrayOf(m.getInt("frames").toFloat(), m.getDouble("fps").toFloat(), if (m.optBoolean("loop")) 1f else 0f,
                    if (m.optString("blend") == "add") 1f else 0f, if (m.optBoolean("pixel")) 1f else 0f)
            }
        }
        val an = manifest.getJSONObject("anims")
        for ((key, map) in listOf("hero" to heroAnims, "zombie" to zombieAnims)) {
            val o = an.getJSONObject(key)
            for (k in o.keys()) { val a = o.getJSONArray(k); map[k] = IntArray(3) { a.getInt(it) } }
        }
        // V11: aktöre özel tablolar ("z/walker", "p/dog0" ...): kare sayısı türden türe değişebilir
        for (key in an.keys()) {
            if (!key.contains('/')) continue
            val o = an.getJSONObject(key)
            val map = HashMap<String, IntArray>()
            for (k in o.keys()) { val a = o.getJSONArray(k); map[k] = IntArray(3) { a.getInt(it) } }
            actorAnims[key] = map
        }
    }

    operator fun get(key: String): Sprite? = sprites[key] ?: ITEM_STANDIN[key]?.let { sprites[it] }

    fun has(key: String) = sprites.containsKey(key)

    /** Sayfayı (gerekirse yükleyerek) döndürür. */
    fun page(i: Int): Bitmap? = pages[i] ?: synchronized(pages) {
        pages[i] ?: runCatching {
            am.open("v2/" + pageFiles[i]).use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()?.also { pages[i] = it }
    }

    fun preload(group: String) { groups[group]?.forEach { page(it) } }

    fun unload(group: String) {
        synchronized(pages) { groups[group]?.forEach { pages.remove(it)?.recycle() } }
    }

    fun loadedGroups(): Set<String> = synchronized(pages) { pages.keys.mapNotNull { pageGroup[it] }.toSet() }

    /** Çapa noktasına göre ek nokta (rhand, head, ...) — px, ölçeksiz */
    fun attach(actor: String, key: String, point: String): FloatArray? {
        val a = attach.optJSONObject(actor)?.optJSONObject(key)?.optJSONArray(point) ?: return null
        return floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
    }

    /** Çapaya göre iki nokta (ör. kahraman karesinde silahın kabza ve ucu: "w_katana") — px, ölçeksiz [x0, y0, x1, y1] */
    fun attach4(actor: String, key: String, point: String): FloatArray? {
        val a = attach.optJSONObject(actor)?.optJSONObject(key)?.optJSONArray(point) ?: return null
        if (a.length() < 4) return null
        return FloatArray(4) { a.getDouble(it).toFloat() }
    }

    /** Etkinlik silahının elde tutma verisi: [kabza x, kabza y, uç x, uç y, boy katsayısı] (doku pikseli) */
    fun evWeapon(id: String): FloatArray? {
        val o = attach.optJSONObject("w/ev")?.optJSONObject(id) ?: return null
        val g = o.getJSONArray("grip"); val t = o.getJSONArray("tip")
        return floatArrayOf(g.getDouble(0).toFloat(), g.getDouble(1).toFloat(), t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), o.optDouble("len", 1.0).toFloat())
    }

    private val dst = RectF()

    /** (x, y) ekran noktası çapaya gelecek şekilde çizer. */
    fun draw(c: Canvas, sp: Sprite, x: Float, y: Float, scale0: Float, paint: Paint?, flipX: Boolean = false) {
        val bmp = page(sp.page) ?: return
        val scale = scale0 * sp.k
        val l = x - sp.ax * scale
        val t = y - sp.ay * scale
        dst.set(l, t, l + sp.w * scale, t + sp.h * scale)
        if (flipX) {
            c.save(); c.scale(-1f, 1f, x, y); c.drawBitmap(bmp, sp.src, dst, paint); c.restore()
        } else c.drawBitmap(bmp, sp.src, dst, paint)
    }

    /** Görseli bir kutuya sığdırarak çizer (arayüz ikonları). */
    fun drawFit(c: Canvas, sp: Sprite, box: RectF, paint: Paint?) {
        val bmp = page(sp.page) ?: return
        val s = minOf(box.width() / sp.w, box.height() / sp.h)
        val w = sp.w * s; val h = sp.h * s
        dst.set(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
        c.drawBitmap(bmp, sp.src, dst, paint)
    }

    /** Tek bir görseli ayrı Bitmap olarak keser (Compose arayüzü için). */
    fun extract(key: String): Bitmap? {
        val sp = get(key) ?: return null
        val page = synchronized(softPages) {
            softPages[sp.page] ?: runCatching { am.open("v2/" + pageFiles[sp.page]).use { BitmapFactory.decodeStream(it, null, softOpts) } }
                .getOrNull()?.also { softPages[sp.page] = it } ?: return null
        }
        return Bitmap.createBitmap(page, sp.src.left, sp.src.top, sp.w, sp.h)
    }

    /** Arayüz için yazılımsal sayfa önbelleği (en fazla 6 sayfa). */
    private val softPages = object : LinkedHashMap<Int, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean {
            if (size > 6) { eldest.value.recycle(); return true }
            return false
        }
    }

    companion object {
        @Volatile private var inst: Atlas? = null
        /** yüklüyse atlas (bağlam olmadan; arka plan çizimleri için) */
        fun peek(): Atlas? = inst
        fun get(ctx: Context): Atlas = inst ?: synchronized(this) { inst ?: Atlas(ctx.applicationContext).also { inst = it } }
    }
}

/** Görseli henüz çizilmemiş V11 eşyaları: kendi simgesi atlasa girene dek yakın bir simge (istemler: docs/15_PROMPTLAR_V11.md) */
private val ITEM_STANDIN = mapOf(
    "i/t_axe_stone" to "i/stone", "i/t_axe_iron" to "i/w_knife", "i/t_pick_stone" to "i/stone", "i/t_pick_iron" to "i/scrap",
    "i/v_wheel" to "i/gear", "i/v_engine" to "i/electronics", "i/v_frame_moto" to "i/scrap", "i/v_frame_car" to "i/scrap",
    "i/veh_moto" to "i/rusty_key", "i/veh_car" to "i/rusty_key",
)
