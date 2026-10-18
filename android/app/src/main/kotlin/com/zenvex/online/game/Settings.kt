package com.zenvex.online.game

import android.content.Context

/** Tüm ayarlar. SharedPreferences'ta saklanır, oyun ekranı her karede okur. */
data class Settings(
    // Grafik
    val preset: Int = 2, // 0 Düşük, 1 Orta, 2 Yüksek, 3 Ultra, 4 Özel
    val fpsLimit: Int = 60,
    val zoom: Int = 1, // 0 Yakın, 1 Normal, 2 Uzak
    val vegetation: Int = 2, // 0 düşük, 1 orta, 2 yüksek
    val wind: Boolean = true,
    val shadows: Boolean = true,
    val particles: Boolean = true,
    val treeFade: Boolean = true,
    val screenShake: Float = 0.7f,
    val damageNumbers: Boolean = true,
    val showFps: Boolean = false,
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
    val colorBlind: Int = 0,
) {
    val vegetationDensity: Float get() = when (vegetation) { 0 -> 0.3f; 1 -> 0.6f; else -> 1f }
    val effectiveFps: Int get() = if (batterySaver) 30 else fpsLimit
    /** Sanal ekran yüksekliği (piksel sanat pikseli cinsinden) */
    val virtualHeight: Int get() = when (zoom) { 0 -> 230; 1 -> 290; else -> 360 }

    fun withPreset(p: Int): Settings = when (p) {
        0 -> copy(preset = 0, vegetation = 0, wind = false, shadows = false, particles = false, fpsLimit = 30)
        1 -> copy(preset = 1, vegetation = 1, wind = true, shadows = true, particles = false, fpsLimit = 60)
        2 -> copy(preset = 2, vegetation = 2, wind = true, shadows = true, particles = true, fpsLimit = 60)
        3 -> copy(preset = 3, vegetation = 2, wind = true, shadows = true, particles = true, fpsLimit = 120)
        else -> copy(preset = 4)
    }

    companion object {
        @Volatile var current = Settings()
            private set

        fun load(ctx: Context) {
            val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val d = Settings()
            current = Settings(
                preset = p.getInt("preset", d.preset),
                fpsLimit = p.getInt("fpsLimit", d.fpsLimit),
                zoom = p.getInt("zoom", d.zoom),
                vegetation = p.getInt("vegetation", d.vegetation),
                wind = p.getBoolean("wind", d.wind),
                shadows = p.getBoolean("shadows", d.shadows),
                particles = p.getBoolean("particles", d.particles),
                treeFade = p.getBoolean("treeFade", d.treeFade),
                screenShake = p.getFloat("screenShake", d.screenShake),
                damageNumbers = p.getBoolean("damageNumbers", d.damageNumbers),
                showFps = p.getBoolean("showFps", d.showFps),
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
                language = p.getString("language", d.language) ?: d.language,
                colorBlind = p.getInt("colorBlind", d.colorBlind),
            )
        }

        fun save(ctx: Context, s: Settings) {
            current = s
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putInt("preset", s.preset).putInt("fpsLimit", s.fpsLimit).putInt("zoom", s.zoom)
                .putInt("vegetation", s.vegetation).putBoolean("wind", s.wind)
                .putBoolean("shadows", s.shadows).putBoolean("particles", s.particles)
                .putBoolean("treeFade", s.treeFade).putFloat("screenShake", s.screenShake)
                .putBoolean("damageNumbers", s.damageNumbers).putBoolean("showFps", s.showFps)
                .putBoolean("batterySaver", s.batterySaver).putBoolean("moveDynamic", s.moveDynamic)
                .putFloat("moveSensitivity", s.moveSensitivity).putFloat("moveDeadZone", s.moveDeadZone)
                .putFloat("runThreshold", s.runThreshold).putFloat("attackSensitivity", s.attackSensitivity)
                .putFloat("attackDeadZone", s.attackDeadZone).putFloat("joystickSize", s.joystickSize)
                .putFloat("joystickOpacity", s.joystickOpacity).putInt("haptics", s.haptics)
                .putBoolean("leftHanded", s.leftHanded).putFloat("master", s.master)
                .putFloat("music", s.music).putFloat("sfx", s.sfx).putFloat("ambient", s.ambient)
                .putFloat("uiSound", s.uiSound).putString("language", s.language)
                .putInt("colorBlind", s.colorBlind)
                .apply()
            Audio.updateVolume()
        }
    }
}
