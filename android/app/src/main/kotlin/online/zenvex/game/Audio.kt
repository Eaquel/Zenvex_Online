package online.zenvex.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Kısa ses efektleri (tools/assetgen/sfx.py ile üretilir). Ses düzeyi ayarlardan okunur. */
object Audio {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    enum class Bus { Sfx, Ui }

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        val am = ctx.assets
        for (f in am.list("sfx").orEmpty()) {
            if (!f.endsWith(".wav")) continue
            runCatching { am.openFd("sfx/$f").use { fd -> ids[f.removeSuffix(".wav")] = p.load(fd, 1) } }
        }
        pool = p
    }

    /** volume: 0..1 ek çarpan (ör. uzaklık); pitch: 0.5..2 */
    fun play(name: String, volume: Float = 1f, pitch: Float = 1f, bus: Bus = Bus.Sfx) {
        val p = pool ?: return
        val id = ids[name] ?: return
        if (!synchronized(loaded) { id in loaded }) return
        val s = Settings.current
        val v = (s.master * (if (bus == Bus.Ui) s.uiSound else s.sfx) * volume).coerceIn(0f, 1f)
        if (v <= 0.01f) return
        p.play(id, v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }
}
