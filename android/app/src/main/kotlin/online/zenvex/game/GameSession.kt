package online.zenvex.game

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Uygulama boyunca yaşayan tek oyun oturumu. Lobi (istatistik, yetenekler) ve oyun ekranı
 * aynı Rust nesnesini kullanır. Tüm Native çağrıları [lock] altında yapılır.
 */
object GameSession {
    val lock = Any()
    var handle: Long = 0L
        private set
    private lateinit var saveFile: File
    const val WORLD_SEED = 20261017L

    fun init(ctx: Context) {
        if (handle != 0L) return
        saveFile = File(ctx.filesDir, "save.json")
        val save = runCatching { saveFile.readText() }.getOrDefault("")
        synchronized(lock) { handle = Native.create(WORLD_SEED, save) }
    }

    fun save() {
        if (handle == 0L) return
        val json = synchronized(lock) { Native.saveJson(handle) }
        runCatching {
            val tmp = File(saveFile.parentFile, "save.json.tmp")
            tmp.writeText(json)
            tmp.renameTo(saveFile)
        }
    }

    fun resetSave() {
        synchronized(lock) {
            if (handle != 0L) Native.destroy(handle)
            runCatching { saveFile.delete() }
            handle = Native.create(WORLD_SEED, "")
        }
    }

    fun hud(): FloatArray {
        val out = FloatArray(Native.HUD_SIZE)
        synchronized(lock) { if (handle != 0L) Native.hud(handle, out) }
        return out
    }

    fun progress(): JSONObject =
        JSONObject(synchronized(lock) { if (handle != 0L) Native.saveJson(handle) else "{}" })

    fun skills(): JSONObject =
        JSONObject(synchronized(lock) { if (handle != 0L) Native.skillsJson(handle) else "{}" })

    fun upgradeSkill(id: Int): Boolean {
        val ok = synchronized(lock) { Native.upgradeSkill(handle, id) }
        if (ok) save()
        return ok
    }

    fun respawn() = synchronized(lock) { Native.respawn(handle) }
}
