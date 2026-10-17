package online.zenvex.game

/** Rust çekirdeği (rust/zenvex_jni). Tüm çağrılar GameSession kilidi altında yapılmalı. */
object Native {
    init {
        System.loadLibrary("zenvex")
    }

    /** Çizim listesi kaydı: [tür, id, x, y, yön, anim, animZamanı, ek1, ek2] */
    const val STRIDE = 9
    const val DRAW_STATIC = 0
    const val DRAW_PLAYER = 1
    const val DRAW_ZOMBIE = 2

    // HUD dizisindeki alanlar
    const val HUD_HP = 0
    const val HUD_MAXHP = 1
    const val HUD_ST = 2
    const val HUD_MAXST = 3
    const val HUD_XP = 4
    const val HUD_XPNEXT = 5
    const val HUD_LEVEL = 6
    const val HUD_POINTS = 7
    const val HUD_WOOD = 8
    const val HUD_KILLS = 9
    const val HUD_PX = 10
    const val HUD_PY = 11
    const val HUD_DEAD = 12
    const val HUD_TEETH = 13
    const val HUD_TREES = 14
    const val HUD_SIZE = 15

    @JvmStatic external fun create(seed: Long, save: String): Long
    @JvmStatic external fun destroy(h: Long)
    @JvmStatic external fun worldJson(h: Long): String
    /** input: [hareketX, hareketY, saldırıX, saldırıY, saldırıyor(0/1)] */
    @JvmStatic external fun step(h: Long, dt: Float, input: FloatArray)
    /** view: [x, y, w, h, bitkiYoğunluğu] */
    @JvmStatic external fun drawList(h: Long, view: FloatArray, out: FloatArray): Int
    @JvmStatic external fun hud(h: Long, out: FloatArray)
    @JvmStatic external fun radar(h: Long, out: FloatArray): Int
    @JvmStatic external fun events(h: Long): String
    @JvmStatic external fun saveJson(h: Long): String
    @JvmStatic external fun skillsJson(h: Long): String
    @JvmStatic external fun upgradeSkill(h: Long, id: Int): Boolean
    @JvmStatic external fun respawn(h: Long)
}
