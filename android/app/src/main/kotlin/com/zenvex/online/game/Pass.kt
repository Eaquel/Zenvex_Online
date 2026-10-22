package com.zenvex.online.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zenvex.online.ui.Toaster

/**
 * Sezon Bileti: sezon bilgisi sunucudan gelir, bilet elmasla alınır ve sunucuya kaydedilir,
 * elmaslı kademe ödülleri sunucudan onaylanır (kayıt doğrulayıcısı elmas artışını ancak böyle kabul eder).
 */
object Pass {
    var name by mutableStateOf("Sezon 1")
    var ends by mutableLongStateOf(0L)
    /** sunucu saati − cihaz saati (cihaz saatini oynayarak görev yenileme engellenir) */
    @Volatile private var offset = 0L
    var busy by mutableStateOf(false)
        private set

    fun now(): Long = System.currentTimeMillis() + offset

    /** Oturum başında: sezon değiştiyse bilet sıfırlanır; sunucuda kayıtlı bilet yerelde yoksa uygulanır. */
    fun sync() = Account.passState { r ->
        r.onSuccess { o ->
            offset = o.optLong("now", System.currentTimeMillis()) - System.currentTimeMillis()
            name = o.optString("name").ifEmpty { "Sezon" }
            ends = o.optLong("ends")
            val res = GameSession.cmdNoSave("pass_sync", "season" to o.optInt("season"), "normal" to o.optBoolean("normal"), "elite" to o.optBoolean("elite"))
            if (res.msg.isNotEmpty()) Toaster.show(res.msg)
            GameSession.save(cloud = true)
        }
    }

    /** Bilet al: önce son durum sunucuya kaydedilir, sunucu bileti yazar, sonra elmas yerelde düşülür ve hemen kaydedilir. */
    fun buy(elite: Boolean, price: Int, gems: Int, done: () -> Unit) {
        if (busy) return
        if (gems < price) { Toaster.show("$price Elmas Gerekli", false); return }
        busy = true
        GameSession.save(cloud = true) { ok ->
            if (!ok) { busy = false; Toaster.show("Sunucuya Ulaşılamadı", false); return@save }
            Account.passBuy(elite) { r ->
                r.onSuccess {
                    val res = GameSession.cmdNoSave("pass_buy", "elite" to elite)
                    GameSession.save(cloud = true)
                    Toaster.show(res.msg, res.ok)
                }.onFailure { Toaster.show(it.message ?: "Hata", false) }
                busy = false
                done()
            }
        }
    }

    /** Kademe ödülü: elmas içeriyorsa sunucu onayı alınır. */
    fun claim(tier: Int, premium: Boolean, gem: Boolean, done: () -> Unit) {
        if (busy) return
        if (!gem) {
            val res = GameSession.cmd("claim_pass", "tier" to tier, "premium" to premium)
            Toaster.show(res.msg, res.ok)
            if (res.ok) GameSession.save(cloud = true)
            done(); return
        }
        busy = true
        GameSession.save(cloud = true) { ok ->
            if (!ok) { busy = false; Toaster.show("Sunucuya Ulaşılamadı", false); return@save }
            Account.passClaim(tier, premium) { r ->
                r.onSuccess {
                    val res = GameSession.cmdNoSave("claim_pass", "tier" to tier, "premium" to premium)
                    GameSession.save(cloud = true)
                    Toaster.show(res.msg, res.ok)
                }.onFailure { Toaster.show(it.message ?: "Hata", false) }
                busy = false
                done()
            }
        }
    }
}
