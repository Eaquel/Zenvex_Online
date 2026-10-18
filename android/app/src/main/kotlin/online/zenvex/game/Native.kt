package online.zenvex.game

/** Rust çekirdeği (rust/zenvex_jni). Tüm çağrılar GameSession.lock altında yapılmalı. */
object Native {
    init {
        System.loadLibrary("zenvex")
    }

    /** Çizim listesi kaydı uzunluğu (rust game::STRIDE) */
    const val STRIDE = 12
    const val D_STATIC = 0
    const val D_HUMAN = 1
    const val D_ZOMBIE = 2
    const val D_DROP = 3
    const val D_PROJ = 4
    const val D_VEHICLE = 5
    const val D_DRONE = 6
    const val D_AREA = 7

    // HUD dizisi (rust draw.rs hud())
    const val HUD_SIZE = 32
    const val HP = 0; const val MAXHP = 1; const val ST = 2; const val MAXST = 3
    const val XP = 4; const val XPNEXT = 5; const val LEVEL = 6; const val POINTS = 7
    const val GOLD = 8; const val KILLS = 9; const val PX = 10; const val PY = 11
    const val DEAD = 12; const val SHIELD = 13; const val LIGHT = 14; const val WEAPON = 15
    const val MAG = 16; const val AMMO = 17; const val RELOADING = 18; const val ABILITY_CD = 19
    const val CHARACTER = 20; const val CHAR_LEVEL = 21; const val NEAR_VEHICLE = 22; const val NEAR_MERCHANT = 23
    const val NEAR_BENCH = 24; const val IN_VEHICLE = 25; const val FUEL = 26; const val VEHICLE_HP = 27
    const val BOSS_HP = 28; const val BOSS_MAX = 29; const val POISON = 30; const val ZONE = 31

    const val REMOTE_STRIDE = 10

    external fun create(seed: Long, save: String, day: Int): Long
    external fun destroy(h: Long)
    external fun worldJson(h: Long): String
    external fun uiJson(h: Long, kind: String): String
    external fun command(h: Long, json: String): String
    external fun step(h: Long, dt: Float, input: FloatArray)
    external fun drawList(h: Long, view: FloatArray, out: FloatArray): Int
    external fun hud(h: Long, out: FloatArray)
    external fun radar(h: Long, out: FloatArray): Int
    external fun events(h: Long): String
    external fun saveJson(h: Long): String
    external fun setTextures(h: Long, size: Int, pixels: IntArray)
    external fun renderChunk(h: Long, x0: Float, y0: Float, size: Int, out: IntArray): Int
    external fun minimap(h: Long, n: Int, out: IntArray)
    external fun setRemotes(h: Long, data: FloatArray, count: Int)
    external fun localState(h: Long, out: FloatArray)
}
