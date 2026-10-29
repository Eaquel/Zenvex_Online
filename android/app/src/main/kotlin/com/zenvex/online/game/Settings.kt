package com.zenvex.online.game

import android.content.Context

/** Tüm ayarlar. SharedPreferences'ta saklanır, oyun ekranı her karede okur. */
data class Settings(
    // Grafik
    val preset: Int = 2, // 0 Düşük, 1 Orta, 2 Yüksek, 3 Ultra, 4 Özel
    val fpsLimit: Int = 60,
    val zoom: Int = 1, // 0 Yakın, 1 Normal, 2 Uzak
    val vegetation: Int = 2, // 0 düşük, 1 orta, 2 yüksek
    /** 3B karakter kalitesi: 0 düşük (küçük ölçek), 1 orta, 2 yüksek (kenar yumuşatma) */
    val model3d: Int = 2,
    val wind: Boolean = true,
    val shadows: Boolean = true,
    val particles: Boolean = true,
    /** efekt yoğunluğu: 0 düşük, 1 orta, 2 yüksek, 3 ultra (parçacık sayısı, ışık halesi, aura ayrıntısı) */
    val fx: Int = 2,
    /** çizim çözünürlüğü: 0 %67, 1 %80, 2 %100 (yüzey gerçekten küçülür, GPU dolgu maliyeti düşer) */
    val resolution: Int = 2,
    val treeFade: Boolean = true,
    val screenShake: Float = 0.7f,
    val damageNumbers: Boolean = true,
    val showFps: Boolean = false,
    val showPing: Boolean = true,
    /** oyuncuların (kendin dahil) başındaki unvanları gizle */
    val hideTitles: Boolean = false,
    /** oyun içi tam ekran pencerelerin (çanta, üretim...) arkasında oyun görünsün mü */
    val uiTransparent: Boolean = false,
    val batterySaver: Boolean = false,
    // Kontrol & hassasiyet
    val moveDynamic: Boolean = true,
    val moveSensitivity: Float = 1.0f,
    val moveDeadZone: Float = 0.12f,
    val runThreshold: Float = 0.6f,
    val attackSensitivity: Float = 1.0f,
    val attackDeadZone: Float = 0.2f,
    val joystickSize: Float = 1.0f,
    val joystickOpacity: Float = 0.6f,
    val haptics: Int = 1, // 0 kapalı, 1 hafif, 2 güçlü
    val leftHanded: Boolean = false,
    // Ses
    val master: Float = 0.8f,
    val music: Float = 0.6f,
    val sfx: Float = 0.8f,
    val ambient: Float = 0.6f,
    val uiSound: Float = 0.7f,
    // Oyun
    val language: String = "tr",
    /** ilk girişte gösterilen numaralı kontrol rehberi (gösterilince ya da kapatılınca false) */
    val controlsGuide: Boolean = true,
) {
    val vegetationDensity: Float get() = when (vegetation) { 0 -> 0.3f; 1 -> 0.6f; else -> 1f }
    /** parçacık ve efekt çarpanı (yeni parçacık sayısı, tavan) */
    val fxScale: Float get() = if (!particles) 0f else Thermal.fxK * when (fx) { 0 -> 0.3f; 1 -> 0.6f; 2 -> 1f; else -> 1.35f }
    val renderScale: Float get() = when (resolution) { 0 -> 0.67f; 1 -> 0.8f; else -> 1f }
    val effectiveFps: Int get() = (if (batterySaver) 30 else fpsLimit).let { f -> Thermal.fpsCap.let { c -> if (c > 0) minOf(f, c) else f } }
    /** Sanal ekran yüksekliği (piksel sanat pikseli cinsinden) */
    val virtualHeight: Int get() = when (zoom) { 0 -> 230; 1 -> 290; else -> 360 }

    fun withPreset(p: Int): Settings = when (p) {
        0 -> copy(preset = 0, vegetation = 0, model3d = 0, wind = false, shadows = false, particles = true, fx = 0, resolution = 0, fpsLimit = 30)
        1 -> copy(preset = 1, vegetation = 1, model3d = 1, wind = true, shadows = true, particles = true, fx = 1, resolution = 1, fpsLimit = 60)
        2 -> copy(preset = 2, vegetation = 2, model3d = 2, wind = true, shadows = true, particles = true, fx = 2, resolution = 2, fpsLimit = 60)
        3 -> copy(preset = 3, vegetation = 2, model3d = 2, wind = true, shadows = true, particles = true, fx = 3, resolution = 2, fpsLimit = 120)
        else -> copy(preset = 4)
    }

    companion object {
        @Volatile var current = Settings()
            private set

        /** Testler: ayarı kalıcı depoya yazmadan uygula */
        internal fun preview(s: Settings) { current = s }

        fun load(ctx: Context) {
            val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
            // ilk kurulumda düşük bellekli cihaz (1-2 GB) "Orta" ön ayarla başlar (2× kenar yumuşatma, %80 çözünürlük): akıcı
            // ve serin kalır; oyuncu Ayarlar'dan yükseltebilir. Kayıtlı ayarı olan ve güçlü cihazlarda değişiklik yok.
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val low = am?.isLowRamDevice == true || (am?.memoryClass ?: 256) < 192
            val d = if (!p.contains("preset") && low) Settings().withPreset(1) else Settings()
            current = Settings(
                preset = p.getInt("preset", d.preset),
                fpsLimit = p.getInt("fpsLimit", d.fpsLimit),
                zoom = p.getInt("zoom", d.zoom),
                vegetation = p.getInt("vegetation", d.vegetation),
                model3d = p.getInt("model3d", d.model3d),
                wind = p.getBoolean("wind", d.wind),
                shadows = p.getBoolean("shadows", d.shadows),
                particles = p.getBoolean("particles", d.particles),
                fx = p.getInt("fx", d.fx),
                resolution = p.getInt("resolution", d.resolution),
                treeFade = p.getBoolean("treeFade", d.treeFade),
                screenShake = p.getFloat("screenShake", d.screenShake),
                damageNumbers = p.getBoolean("damageNumbers", d.damageNumbers),
                showFps = p.getBoolean("showFps", d.showFps),
                showPing = p.getBoolean("showPing", d.showPing),
                hideTitles = p.getBoolean("hideTitles", d.hideTitles),
                uiTransparent = p.getBoolean("uiTransparent", d.uiTransparent),
                batterySaver = p.getBoolean("batterySaver", d.batterySaver),
                moveDynamic = p.getBoolean("moveDynamic", d.moveDynamic),
                moveSensitivity = p.getFloat("moveSensitivity", d.moveSensitivity),
                moveDeadZone = p.getFloat("moveDeadZone", d.moveDeadZone),
                runThreshold = p.getFloat("runThreshold", d.runThreshold),
                attackSensitivity = p.getFloat("attackSensitivity", d.attackSensitivity),
                attackDeadZone = p.getFloat("attackDeadZone", d.attackDeadZone),
                joystickSize = p.getFloat("joystickSize", d.joystickSize),
                joystickOpacity = p.getFloat("joystickOpacity", d.joystickOpacity),
                haptics = p.getInt("haptics", d.haptics),
                leftHanded = p.getBoolean("leftHanded", d.leftHanded),
                master = p.getFloat("master", d.master),
                music = p.getFloat("music", d.music),
                sfx = p.getFloat("sfx", d.sfx),
                ambient = p.getFloat("ambient", d.ambient),
                uiSound = p.getFloat("uiSound", d.uiSound),
                // oyuncu Ayarlar'dan dil seçmediyse her açılışta cihaz dili (destekleniyorsa; yoksa İngilizce)
                controlsGuide = p.getBoolean("controlsGuide", d.controlsGuide),
                language = (if (p.getBoolean("langChosen", false)) p.getString("language", null) else null) ?: I18n.deviceDefault(),
            )
            I18n.load(ctx, current.language)
        }

        /** Dil ekranı yok: oyun cihaz dilini kullanır; oyuncu Ayarlar'dan dil değiştirince bu işaretlenir ve o dil kalır */
        fun markLanguageChosen(ctx: Context) {
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("langChosen", true).apply()
        }

        fun save(ctx: Context, s: Settings) {
            current = s
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putInt("preset", s.preset).putInt("fpsLimit", s.fpsLimit).putInt("zoom", s.zoom)
                .putInt("vegetation", s.vegetation).putInt("model3d", s.model3d).putBoolean("wind", s.wind)
                .putBoolean("shadows", s.shadows).putBoolean("particles", s.particles).putInt("fx", s.fx).putInt("resolution", s.resolution)
                .putBoolean("treeFade", s.treeFade).putFloat("screenShake", s.screenShake)
                .putBoolean("damageNumbers", s.damageNumbers).putBoolean("showFps", s.showFps).putBoolean("showPing", s.showPing).putBoolean("hideTitles", s.hideTitles).putBoolean("uiTransparent", s.uiTransparent)
                .putBoolean("batterySaver", s.batterySaver).putBoolean("moveDynamic", s.moveDynamic)
                .putFloat("moveSensitivity", s.moveSensitivity).putFloat("moveDeadZone", s.moveDeadZone)
                .putFloat("runThreshold", s.runThreshold).putFloat("attackSensitivity", s.attackSensitivity)
                .putFloat("attackDeadZone", s.attackDeadZone).putFloat("joystickSize", s.joystickSize)
                .putFloat("joystickOpacity", s.joystickOpacity).putInt("haptics", s.haptics)
                .putBoolean("leftHanded", s.leftHanded).putBoolean("controlsGuide", s.controlsGuide).putFloat("master", s.master)
                .putFloat("music", s.music).putFloat("sfx", s.sfx).putFloat("ambient", s.ambient)
                .putFloat("uiSound", s.uiSound).putString("language", s.language)
                .apply()
            Audio.updateVolume()
            if (s.language != I18n.lang) I18n.load(ctx, s.language)
        }
    }
}
