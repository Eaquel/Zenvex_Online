package online.zenvex.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Ses efektleri (SoundPool) ve döngüsel müzik (MediaPlayer). Tüm sesler tools/assetgen/sfx.py ile kodla üretilir. */
object Audio {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private var ctx: Context? = null
    private var player: MediaPlayer? = null
    private var current: String? = null

    enum class Bus { Sfx, Ui }

    fun init(c: Context) {
        if (pool != null) return
        ctx = c.applicationContext
        val p = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        val am = c.assets
        for (f in am.list("sfx").orEmpty()) {
            if (!f.endsWith(".wav") && !f.endsWith(".ogg")) continue
            runCatching { am.openFd("sfx/$f").use { fd -> ids[f.substringBeforeLast('.')] = p.load(fd, 1) } }
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

    fun music(name: String) {
        if (current == name && player != null) { updateVolume(); return }
        current = name
        runCatching { player?.release() }
        player = null
        val c = ctx ?: return
        runCatching {
            val fd = c.assets.openFd("music/$name.ogg")
            player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                isLooping = true
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                prepare()
                start()
            }
            updateVolume()
        }
    }

    fun updateVolume() {
        val s = Settings.current
        val v = (s.master * s.music).coerceIn(0f, 1f) * 0.6f
        runCatching { player?.setVolume(v, v) }
    }

    fun pauseMusic() { runCatching { player?.pause() } }
    fun resumeMusic() { runCatching { player?.start(); updateVolume() } }
}
