package com.zenvex.online.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
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
    private lateinit var appCtx: Context
    const val WORLD_SEED = 20261017L
    /** Seçili sunucu (her sunucunun ayrı kaydı ve ayrı çevrimiçi dünyası var) */
    val server = mutableStateOf(SERVERS[0])
    private var groundSize = 0
    private var groundPx: IntArray? = null
    @Volatile private var lastCloud = 0L

    /** Gün indeksi (günlük görevler için) */
    private fun today(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    private fun saveFile(id: String) = File(appCtx.filesDir, "save_$id.json")

    fun init(ctx: Context) {
        if (handle != 0L) return
        appCtx = ctx.applicationContext
        // eski tek kayıt → Normal #1
        val old = File(ctx.filesDir, "save_v2.json")
        if (old.exists() && !saveFile("n1").exists()) old.renameTo(saveFile("n1"))
        val sid = ctx.getSharedPreferences("account", Context.MODE_PRIVATE).getString("server", "n1") ?: "n1"
        server.value = serverById(sid)
        loadGroundTextures()
        open(readLocal(sid))
    }

    fun prefs(): android.content.SharedPreferences = appCtx.getSharedPreferences("account", Context.MODE_PRIVATE)

    private fun readLocal(id: String) = runCatching { saveFile(id).readText() }.getOrDefault("")

    private fun open(save: String) {
        synchronized(lock) {
            if (handle != 0L) Native.destroy(handle)
            handle = Native.create(WORLD_SEED, save, today())
            groundPx?.let { Native.setTextures(handle, groundSize, it) }
            Native.command(handle, JSONObject().put("cmd", "set_rules").put("hardcore", server.value.hardcore).toString())
            if (Account.name.value.isNotEmpty()) Native.command(handle, JSONObject().put("cmd", "set_name").put("name", Account.name.value).toString())
        }
    }

    private fun localLevel(save: String): Int = runCatching { JSONObject(save).optInt("level", 0) }.getOrDefault(0)

    /**
     * Sunucu değiştirir: mevcut kaydı yazar, yeni sunucunun kaydını (bulut daha ileriyse buluttan) yükler.
     * Ağ beklediği için arka planda çalışır; [done] ana iş parçacığında çağrılır.
     */
    fun switchServer(s: GameServer, done: () -> Unit) {
        save()
        server.value = s
        appCtx.getSharedPreferences("account", Context.MODE_PRIVATE).edit().putString("server", s.id).apply()
        Thread {
            var data = readLocal(s.id)
            val cloud = Account.loadCloudSync(s.id)
            if (cloud != null && (data.isEmpty() || localLevel(cloud) > localLevel(data))) data = cloud
            open(data)
            save()
            android.os.Handler(android.os.Looper.getMainLooper()).post(done)
        }.start()
    }

    /** Hesap değişince (giriş/kayıt) mevcut sunucuyu yeniden yükler. */
    fun reload(done: () -> Unit) = switchServer(server.value, done)

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
        groundSize = size
        groundPx = all
    }

    fun save(cloud: Boolean = false) {
        if (handle == 0L) return
        val (json, level) = synchronized(lock) { Native.saveJson(handle) to Native.hud(handle, hudTmp).let { hudTmp[Native.LEVEL].toInt() } }
        val f = saveFile(server.value.id)
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(f)
        }
        val now = System.currentTimeMillis()
        if (cloud || now - lastCloud > 60_000) {
            lastCloud = now
            val p = ui("profile")
            Account.saveCloud(server.value.id, level, json, p.optJSONObject("stats")?.optInt("kills") ?: 0, p.optLong("xp_total"))
        }
    }
    private val hudTmp = FloatArray(Native.HUD_SIZE)

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
