package com.zenvex.online.game

import com.zenvex.online.net.Supa
import com.zenvex.online.ui.Toaster
import org.json.JSONObject

/**
 * V8 devlet üsleri: sunucudaki sahiplik/ödül/süre durumunu her 30 sn'de (ve harita açılınca) çeker, Rust çekirdeğe
 * verir; çekirdek ele geçirme süresini doldurunca ("basecap" olayı) sunucuya bildirir.
 */
object Bases {
    private var t = 2f
    @Volatile private var busy = false

    /** GameView güncellemesinden (ana döngü) */
    fun tick(dt: Float) {
        t -= dt
        if (t <= 0f) { t = 30f; refresh() }
    }

    fun refresh() {
        if (busy || !Supa.configured) return
        busy = true
        Supa.rpc("zx_bases", JSONObject().put("p_server", GameSession.server.value.id)) { r ->
            busy = false
            r.onSuccess { apply(it) }
        }
    }

    private fun apply(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        o.put("clan", Account.clan.value?.id ?: 0L)
        GameSession.cmd("bases", "data" to o.toString())
    }

    /** Çekirdek: ele geçirme alanında 10 sn kalındı */
    fun capture(id: Int) {
        if (!Account.loggedIn) {
            GameSession.cmd("base_cap_fail", "id" to id)
            Toaster.show("Üs ele geçirmek için hesabınla giriş yapmalısın", false)
            return
        }
        Account.baseCapture(GameSession.server.value.id, id) { r ->
            r.onSuccess {
                apply(it)
                Toaster.show("Devlet Üssü ${id + 1} artık klanının! Dakikada 60 klan tecrübesi.", true)
                Audio.play("levelup", 0.9f, 0.8f)
            }.onFailure {
                GameSession.cmd("base_cap_fail", "id" to id)
                Toaster.show(it.message ?: "Ele geçirilemedi", false)
            }
        }
    }
}
