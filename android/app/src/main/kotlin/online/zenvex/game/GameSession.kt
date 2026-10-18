package online.zenvex.game

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/**
 * Uygulama boyunca yaşayan tek oyun oturumu. Lobi ekranları ve oyun aynı Rust nesnesini kullanır.
 * Tüm Native çağrıları [lock] altında yapılır.
 */
object GameSession {
    val lock = Any()
    @Volatile var handle: Long = 0L
        private set
    private lateinit var saveFile: File
    private lateinit var appCtx: Context
    const val WORLD_SEED = 20261017L

    /** Gün indeksi (günlük görevler için) */
    private fun today(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    fun init(ctx: Context) {
        if (handle != 0L) return
        appCtx = ctx.applicationContext
        saveFile = File(ctx.filesDir, "save_v2.json")
        val save = runCatching { saveFile.readText() }.getOrDefault("")
        synchronized(lock) { handle = Native.create(WORLD_SEED, save, today()) }
        loadGroundTextures()
    }

    private fun loadGroundTextures() {
        val names = listOf("grass", "dark_grass", "dry_grass", "dirt", "gravel", "asphalt", "sidewalk", "concrete", "mud")
        var size = 0
        var all: IntArray? = null
        names.forEachIndexed { i, n ->
            val bmp = runCatching { appCtx.assets.open("v2/ground/$n.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return
            if (size == 0) { size = bmp.width; all = IntArray(size * size * names.size) }
            if (bmp.width != size) return
            bmp.getPixels(all!!, i * size * size, size, 0, 0, size, size)
            bmp.recycle()
        }
        all?.let { px -> synchronized(lock) { Native.setTextures(handle, size, px) } }
    }

    fun save() {
        if (handle == 0L) return
        val json = synchronized(lock) { Native.saveJson(handle) }
        runCatching {
            val tmp = File(saveFile.parentFile, "save_v2.json.tmp")
            tmp.writeText(json)
            tmp.renameTo(saveFile)
        }
    }

    fun resetSave() {
        synchronized(lock) {
            Native.command(handle, """{"cmd":"cheat","text":"reset"}""")
        }
        save()
    }

    fun hud(out: FloatArray = FloatArray(Native.HUD_SIZE)): FloatArray {
        synchronized(lock) { if (handle != 0L) Native.hud(handle, out) }
        return out
    }

    fun ui(kind: String): JSONObject =
        runCatching { JSONObject(synchronized(lock) { Native.uiJson(handle, kind) }) }.getOrDefault(JSONObject())

    data class Reply(val ok: Boolean, val msg: String)

    fun cmd(json: JSONObject): Reply {
        val r = runCatching { JSONObject(synchronized(lock) { Native.command(handle, json.toString()) }) }.getOrNull()
            ?: return Reply(false, "Hata")
        val rep = Reply(r.optBoolean("ok"), r.optString("msg"))
        if (rep.ok) save()
        return rep
    }

    fun cmd(name: String, vararg kv: Pair<String, Any>): Reply {
        val o = JSONObject().put("cmd", name)
        for ((k, v) in kv) o.put(k, v)
        return cmd(o)
    }

    fun world(): JSONObject = JSONObject(synchronized(lock) { Native.worldJson(handle) })
}
