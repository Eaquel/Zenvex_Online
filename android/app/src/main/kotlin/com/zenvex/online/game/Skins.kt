package com.zenvex.online.game

import com.zenvex.online.net.Supa
import com.zenvex.online.ui.Toaster
import org.json.JSONArray
import org.json.JSONObject

/**
 * Elmas Sandığı kaplamaları: karakter ve araç renk/ışık dönüşümü (renk matrisi) + efsanevilerde parçacık izi.
 * Sahiplik sunucuda tutulur (zx_skins); oturum başında eşitlenir. Sandık zarı sunucuda atılır (zx_crate_open).
 */
object Skins {
    /** giyili kaplamalar (GameView her karede okur) */
    @Volatile var char = ""
    @Volatile var vehicle = ""
    /** sırttaki 3B kanat (kozmetik kimliği = model adı) */
    @Volatile var wings = ""
    @Volatile var pity = 50

    /** 4×5 renk matrisi (Android ColorMatrix sırası). Karakter: alfa korunur, renk kaydırılır. */
    fun matrix(id: String): FloatArray? = when (id) {
        "sk_night" -> m(0.45f, 0.55f, 1.15f, -10f, -4f, 18f, 0.15f)
        "sk_blood" -> m(1.35f, 0.45f, 0.45f, 22f, -8f, -8f, 0.1f)
        "sk_frost" -> m(0.75f, 1.05f, 1.35f, 10f, 22f, 36f, 0.2f)
        "sk_gold" -> m(1.35f, 1.1f, 0.4f, 30f, 18f, -10f, 0.35f)
        "sk_toxic" -> m(0.7f, 1.3f, 0.55f, -6f, 16f, -6f, 0.15f)
        "sk_shadow" -> m(0.5f, 0.5f, 0.58f, -14f, -14f, -8f, 0.6f)
        "vs_gold" -> m(1.4f, 1.15f, 0.35f, 34f, 20f, -12f, 0.4f)
        "vs_flame" -> m(1.45f, 0.75f, 0.35f, 26f, 0f, -12f, 0.2f)
        "vs_camo" -> m(0.75f, 0.95f, 0.55f, -6f, 4f, -10f, 0.35f)
        "vs_neon" -> m(0.85f, 0.55f, 1.5f, 10f, -6f, 30f, 0.1f)
        else -> null
    }

    /**
     * Önce `desat` oranında griye çeker (kaplama rengi baskın olsun), sonra kanal çarpanı + kaydırma uygular.
     */
    private fun m(r: Float, g: Float, b: Float, ro: Float, go: Float, bo: Float, desat: Float): FloatArray {
        val lr = 0.299f * desat; val lg = 0.587f * desat; val lb = 0.114f * desat
        val k = 1f - desat
        return floatArrayOf(
            (k + lr) * r, lg * r, lb * r, 0f, ro,
            lr * g, (k + lg) * g, lb * g, 0f, go,
            lr * b, lg * b, (k + lb) * b, 0f, bo,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Efsanevi karakter kaplamasının parçacık rengi (yoksa 0) */
    fun trail(id: String): Int = when (id) {
        "sk_night" -> 0xFF9AB8FF.toInt(); "sk_blood" -> 0xFFFF5A3A.toInt(); "sk_frost" -> 0xFFE0F6FF.toInt(); "sk_gold" -> 0xFFFFD34A.toInt()
        "vs_gold" -> 0xFFFFD34A.toInt(); "vs_flame" -> 0xFFFF8A2A.toInt()
        else -> 0
    }

    /** Oyun açılınca ve kaplama değişince Rust profilinden okunur */
    fun refresh() {
        val p = GameSession.ui("profile")
        char = p.optString("skin_char"); vehicle = p.optString("skin_vehicle"); wings = p.optString("wings")
    }

    /** Oturum başında sunucudaki sahiplik listesi uygulanır */
    fun sync() = Supa.rpc("zx_skins", Account.authJson()) { r ->
        r.onSuccess { body ->
            val o = runCatching { JSONObject(body) }.getOrNull() ?: return@onSuccess
            pity = o.optInt("pity", 50)
            GameSession.cmdNoSave("skins_sync", "owned" to (o.optJSONArray("owned") ?: JSONArray()))
            refresh()
        }
    }

    fun equip(id: String, kind: String): GameSession.Reply {
        val r = GameSession.cmd("skin_equip", "id" to id, "kind" to kind)
        refresh(); Online.retrack()
        return r
    }

    /** Sandık aç: önce son durum kaydedilir, zar sunucuda atılır, sonuç yerelde uygulanıp hemen kaydedilir. */
    fun open(n: Int, price: Int, gems: Int, done: (List<String>?) -> Unit) {
        if (gems < price) { Toaster.show("$price Elmas Gerekli", false); done(null); return }
        GameSession.save(cloud = true) { ok ->
            if (!ok) { Toaster.show("Sunucuya Ulaşılamadı", false); done(null); return@save }
            Supa.rpc("zx_crate_open", Account.authJson().put("p_sid", Session.sid).put("p_n", n)) { r ->
                r.onSuccess { body ->
                    val o = JSONObject(body)
                    val arr = o.optJSONArray("rewards") ?: JSONArray()
                    pity = o.optInt("pity", pity)
                    GameSession.cmdNoSave("crate_apply", "cost" to o.optInt("cost"), "rewards" to arr)
                    GameSession.save(cloud = true)
                    done(List(arr.length()) { arr.optString(it) })
                }.onFailure { Toaster.show(it.message ?: "Hata", false); done(null) }
            }
        }
    }
}
