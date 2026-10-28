package com.zenvex.online.game

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
    private val lastPlay = HashMap<String, Long>()

    /** Test/tanıtım: çalınan her sesi dinler (ad, ses, perde); kare kare video için sesleri olayın anına yerleştirmek içindir */
    @JvmField var tap: ((String, Float, Float) -> Unit)? = null

    /**
     * Karışım tablosu: ses başına seviye (sesler farklı yükseklikte üretildi; burada dengelenir) ve öncelik
     * (0 arka plan: adım, inilti … 3 kritik: patlama, boss, seviye). 16 kanal dolunca SoundPool düşük öncelikliyi keser.
     */
    private class Mix(val gain: Float, val prio: Int, val jitter: Float)
    private val MIX = mapOf(
        "step0" to Mix(0.45f, 0, 0.08f), "step1" to Mix(0.45f, 0, 0.08f),
        "groan0" to Mix(0.55f, 0, 0.10f), "groan1" to Mix(0.55f, 0, 0.10f), "groan2" to Mix(0.55f, 0, 0.10f),
        "swing" to Mix(0.6f, 1, 0.08f), "hit" to Mix(0.8f, 1, 0.07f), "chop" to Mix(0.8f, 1, 0.06f), "zattack" to Mix(0.75f, 1, 0.08f),
        "zdeath" to Mix(0.8f, 1, 0.08f), "pickup" to Mix(0.6f, 1, 0.05f), "coin" to Mix(0.6f, 1, 0.06f), "drone" to Mix(0.5f, 0, 0.0f),
        "shot_pistol" to Mix(0.75f, 2, 0.04f), "shot_rifle" to Mix(0.7f, 2, 0.04f), "shot_sniper" to Mix(0.9f, 2, 0.02f),
        "reload" to Mix(0.75f, 2, 0.0f), "empty" to Mix(0.8f, 2, 0.0f), "hurt" to Mix(0.85f, 2, 0.05f), "car_hit" to Mix(0.8f, 2, 0.05f),
        "engine" to Mix(0.7f, 1, 0.0f), "crate" to Mix(0.8f, 1, 0.05f), "craft" to Mix(0.8f, 2, 0.0f), "heal" to Mix(0.75f, 2, 0.0f),
        "shield" to Mix(0.8f, 2, 0.0f), "toxic" to Mix(0.7f, 1, 0.05f), "treefall" to Mix(0.85f, 2, 0.0f), "rocket" to Mix(0.85f, 2, 0.03f),
        "explosion" to Mix(1.0f, 3, 0.05f), "thunder" to Mix(1.0f, 3, 0.0f), "boss" to Mix(1.0f, 3, 0.0f), "levelup" to Mix(0.9f, 3, 0.0f),
        "quest" to Mix(0.85f, 3, 0.0f), "rage" to Mix(0.9f, 3, 0.0f), "teleport" to Mix(0.85f, 3, 0.0f), "death" to Mix(1.0f, 3, 0.0f),
        "click" to Mix(0.7f, 3, 0.0f),
    )
    private val DEFAULT_MIX = Mix(0.8f, 1, 0.0f)
    /** son 120 ms'de çalınan ses sayısı (kalabalık çatışmada arka plan sesleri düşer, önemli sesler duyulur) */
    private val recent = ArrayDeque<Long>()
    private val rnd = java.util.Random()

    fun play(name: String, volume: Float = 1f, pitch: Float = 1f, bus: Bus = Bus.Sfx) {
        tap?.invoke(name, volume, pitch)
        val p = pool ?: return
        val id = ids[name] ?: return
        val mix = MIX[name] ?: DEFAULT_MIX
        // aynı ses 45 ms içinde tekrar çalınmaz (çok askerli çatışmada ses kanalı ve işlemci boğulmasın)
        val now = android.os.SystemClock.uptimeMillis()
        synchronized(lastPlay) {
            if (now - (lastPlay[name] ?: 0L) < 45) return
            while (recent.isNotEmpty() && now - recent.first() > 120) recent.removeFirst()
            // 120 ms'de 8'den çok ses: arka plan (adım, inilti) çalmaz; 12'den çok: yalnız önemli sesler
            if ((recent.size >= 8 && mix.prio == 0) || (recent.size >= 12 && mix.prio < 2)) return
            recent.addLast(now)
            lastPlay[name] = now
        }
        if (!synchronized(loaded) { id in loaded }) return
        val s = Settings.current
        val v = (s.master * (if (bus == Bus.Ui) s.uiSound else s.sfx) * volume * mix.gain).coerceIn(0f, 1f)
        if (v <= 0.01f) return
        // tekrarlayan seslerde küçük perde oynaması (aynı sesin makine gibi tekrarı yorar)
        val pj = if (mix.jitter > 0f) 1f + (rnd.nextFloat() * 2f - 1f) * mix.jitter else 1f
        p.play(id, v, v, mix.prio, 0, (pitch * pj).coerceIn(0.5f, 2f))
        // büyük anlarda müzik kısa süre kısılır, sonra yumuşakça geri gelir
        if (bus == Bus.Sfx && mix.prio == 3 && name != "click") duck(if (name == "explosion" || name == "thunder") 0.45f else 0.6f, 1.6f)
    }

    // ------------------------------------------------------------------ müzik kısma (ducking)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var duckK = 1f
    private var duckUntil = 0L
    private val duckStep = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.uptimeMillis()
            if (now >= duckUntil) {
                duckK = (duckK + 0.06f).coerceAtMost(1f)
                updateVolume()
                if (duckK < 1f) main.postDelayed(this, 50)
            } else main.postDelayed(this, 50)
        }
    }

    /** Müziği [k] oranına çeker, [secs] saniye tutar, sonra ~0,8 sn'de geri açar */
    fun duck(k: Float, secs: Float) {
        main.post {
            duckK = minOf(duckK, k)
            duckUntil = android.os.SystemClock.uptimeMillis() + (secs * 1000).toLong()
            updateVolume()
            main.removeCallbacks(duckStep)
            main.postDelayed(duckStep, 50)
        }
    }

    /** Uygulama ön planda mı: arka plandayken ekran değişse de (oturum düşüp lobiye dönmek gibi) müzik başlamaz */
    @Volatile private var foreground = true

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
                if (foreground) start()
            }
            updateVolume()
        }
    }

    fun updateVolume() {
        val s = Settings.current
        val v = (s.master * s.music).coerceIn(0f, 1f) * 0.6f * duckK
        runCatching { player?.setVolume(v, v) }
    }

    fun pauseMusic() { foreground = false; runCatching { player?.pause() } }
    fun resumeMusic() { foreground = true; runCatching { player?.start(); updateVolume() } }
}
