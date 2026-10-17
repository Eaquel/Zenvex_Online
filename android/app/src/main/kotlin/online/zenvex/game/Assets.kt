package online.zenvex.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject

/** Bir animasyon sayfası: satırlar 8 yön, sütunlar kareler. */
class AnimSheet(val bitmap: Bitmap, val frames: Int, val fps: Float, val loop: Boolean, val fw: Int, val fh: Int) {
    fun frameAt(t: Float): Int {
        val f = (t * fps).toInt()
        return if (loop) f.mod(frames) else f.coerceAtMost(frames - 1)
    }
}

class Actor(val anims: List<AnimSheet>, val anchorX: Int, val anchorY: Int)

class StaticSprite(val bitmap: Bitmap, val anchorX: Int, val anchorY: Int, val type: String)

/** Tüm görseller: tools/assetgen/build_assets.py tarafından üretilen manifest.json'dan yüklenir. */
class Assets(ctx: Context) {
    private val am = ctx.assets
    private val opts = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val manifest = JSONObject(am.open("sprites/manifest.json").bufferedReader().readText())

    fun bmp(path: String): Bitmap = am.open("sprites/$path").use { BitmapFactory.decodeStream(it, null, opts)!! }

    private fun actor(key: String, order: List<String>): Actor {
        val m = manifest.getJSONObject(key)
        val fr = m.getJSONArray("frame")
        val an = m.getJSONArray("anchor")
        val anims = m.getJSONObject("anims")
        val list = order.map { name ->
            val a = anims.getJSONObject(name)
            AnimSheet(bmp(a.getString("file")), a.getInt("frames"), a.getDouble("fps").toFloat(),
                a.getBoolean("loop"), fr.getInt(0), fr.getInt(1))
        }
        return Actor(list, an.getInt(0), an.getInt(1))
    }

    // Rust'taki animasyon kimlikleriyle aynı sıra
    val player = actor("player", listOf("idle", "walk", "run", "punch", "hurt", "death"))
    val zombie = actor("zombie", listOf("idle", "walk", "attack", "hurt", "death"))

    val tileset: Bitmap = bmp(manifest.getJSONObject("tileset").getString("file"))
    val shadow: Bitmap = bmp(manifest.getString("shadow"))
    val shadowBig: Bitmap = bmp(manifest.getString("shadow_big"))

    private val statics = HashMap<String, StaticSprite>()
    val decals = HashMap<String, Bitmap>()
    val ui = HashMap<String, Bitmap>()
    val icons = HashMap<String, Bitmap>()

    init {
        val nat = manifest.getJSONObject("nature")
        for (k in nat.keys()) {
            val o = nat.getJSONObject(k)
            val an = o.getJSONArray("anchor")
            statics[k] = StaticSprite(bmp(o.getString("file")), an.getInt(0), an.getInt(1), o.getString("type"))
        }
        val deco = manifest.getJSONObject("deco")
        for (k in deco.keys()) {
            val b = bmp(deco.getString(k))
            if (k.startsWith("dirt_patch")) decals[k] = b
            else statics[k] = StaticSprite(b, b.width / 2, b.height - 2, "deco")
        }
        val u = manifest.getJSONObject("ui")
        for (k in u.keys()) ui[k] = bmp(u.getString(k))
        val ic = manifest.getJSONObject("icons")
        for (k in ic.keys()) icons[k] = bmp(ic.getString(k))
    }

    fun static(name: String): StaticSprite? = statics[name]

    companion object {
        @Volatile private var instance: Assets? = null
        fun get(ctx: Context): Assets = instance ?: synchronized(this) {
            instance ?: Assets(ctx.applicationContext).also { instance = it }
        }
    }
}
