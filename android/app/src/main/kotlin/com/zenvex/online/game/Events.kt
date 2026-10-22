package com.zenvex.online.game

import com.zenvex.online.net.Supa
import com.zenvex.online.ui.Toaster
import org.json.JSONArray
import org.json.JSONObject

/**
 * Oyun içi etkinlik istemcisi:
 *  - oturum açılınca süresi dolan etkinlik ödüllerini siler (zx_expired_items)
 *  - etkinlik sürüyorsa 20 sn'de bir puan farkını yollar (zx_event_score; sunucu dakika başı tavan uygular)
 * Puan, Rust'taki sayaçların (profile.ev) son gönderimden bu yana artışından hesaplanır.
 */
object Events {
    @Volatile var current: JSONObject = JSONObject()
        private set
    private var base: JSONObject? = null
    private var pending = 0

    fun onSessionStart() {
        base = null; pending = 0
        Supa.rpc("zx_expired_items", Account.authJson()) { r ->
            r.onSuccess { body ->
                val arr = runCatching { JSONArray(body) }.getOrNull() ?: return@onSuccess
                for (i in 0 until arr.length()) {
                    val res = GameSession.cmdNoSave("expire_item", "id" to arr.optString(i))
                    if (res.ok) Toaster.show(res.msg, false)
                }
                if (arr.length() > 0) GameSession.save(cloud = true)
            }
        }
        refresh()
        Pass.sync()
    }

    fun refresh() = Account.eventCurrent { r -> r.onSuccess { current = it } }

    private fun points(kind: String, a: JSONObject, b: JSONObject): Int {
        fun d(k: String) = (a.optLong(k) - b.optLong(k)).coerceAtLeast(0)
        return when (kind) {
            "blood_moon" -> {
                val w = intArrayOf(1, 2, 2, 3, 4, 8, 10, 50)
                val ka = a.optJSONArray("kills_by") ?: JSONArray(); val kb = b.optJSONArray("kills_by") ?: JSONArray()
                (0 until 8).sumOf { ((ka.optLong(it) - kb.optLong(it)).coerceAtLeast(0) * w[it]).toInt() }
            }
            "titan_hunt" -> {
                val ka = a.optJSONArray("kills_by") ?: JSONArray(); val kb = b.optJSONArray("kills_by") ?: JSONArray()
                ((ka.optLong(7) - kb.optLong(7)).coerceAtLeast(0) * 1000).toInt()
            }
            "base_storm" -> (d("soldiers") * 10 + d("caps") * 500).toInt()
            "scavengers" -> (d("trees") * 2 + d("crates") * 5 + d("crafted") * 10).toInt()
            "pack_leader" -> (d("dog") * 5).toInt()
            "last_breath" -> d("danger").toInt()
            else -> 0
        }
    }

    /** GameScreen 20 sn'de bir çağırır. */
    fun tick() {
        if (Session.state.value != Session.State.Live) return
        val ev = GameSession.ui("profile").optJSONObject("ev") ?: return
        val b = base
        base = ev
        if (b == null || !current.optBoolean("active")) return
        if (current.optLong("ends") < System.currentTimeMillis()) { refresh(); return }
        pending += points(current.optString("kind"), ev, b)
        if (pending <= 0) return
        val send = pending
        Supa.rpc("zx_event_score", Account.authJson().put("p_sid", Session.sid).put("p_points", send)) { r ->
            r.onSuccess { pending -= send }
        }
    }
}
