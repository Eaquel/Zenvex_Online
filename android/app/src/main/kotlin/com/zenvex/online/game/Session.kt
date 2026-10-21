package com.zenvex.online.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import com.zenvex.online.net.Supa
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Çevrimiçi oyun oturumu. Oyun yalnız geçerli bir sunucu oturumuyla oynanır:
 *  - Giriş: zx_session_start (bakım, yasak, cihaz yasağı ve güvenlik kontrolü; eski cihazdaki oturum düşer).
 *    Oyun kaydı her zaman sunucudan gelir; cihazdaki dosya yalnız lobide gösterim içindir.
 *  - Nabız (15 sn): bağlantı 30 sn yoksa oyun durur ("Bağlantı Koptu"), 90 sn yoksa lobiye dönülür.
 *  - Kayıt: zx_save3, sıra numarası + HMAC-SHA256 imza (oturum sırrı). Sunucu altın/elmas/seviye sıçramasını reddeder
 *    ve son geçerli kaydı geri yollar; oyun o kayda döner.
 */
object Session {
    enum class State { Off, Starting, Live, Lost }

    val state = mutableStateOf(State.Off)
    /** Lobiye atılma nedeni (başka cihaz, bakım, yasak, bağlantı) — lobi gösterir */
    val kicked = mutableStateOf<String?>(null)
    @Volatile var sid = ""
        private set
    @Volatile private var secret = ""
    @Volatile private var seq = 0L
    /** Oyunda geçen simülasyon süresi (sn): hız hilesi kontrolü için sunucuya gider */
    @Volatile var simTime = 0.0
    @Volatile private var lastOk = 0L
    @Volatile private var fails = 0
    private val main = Handler(Looper.getMainLooper())
    private var beatThread: Thread? = null

    /** Oyun dursun mu (bağlantı yok ya da oturum kapalı) */
    val blocked: Boolean get() = state.value != State.Live

    /** Oturumu açar; başarıda sunucudaki kayıt (boş olabilir) [done]'a gelir, hatada mesaj. */
    fun start(ctx: Context, server: String, done: (data: String?, err: String?) -> Unit) {
        if (!Account.loggedIn) { done(null, "Önce hesabına gir"); return }
        state.value = State.Starting
        kicked.value = null
        Supa.bg {
            val r = runCatching {
                val dev = Guard.report(ctx)
                JSONObject(Supa.rpcSync("zx_session_start", Account.authJson().put("p_server", server).put("p_device", dev)))
            }
            main.post {
                r.onSuccess { o ->
                    sid = o.optString("sid"); secret = o.optString("secret"); seq = 0; simTime = 0.0
                    lastOk = SystemClock.elapsedRealtime(); fails = 0
                    state.value = State.Live
                    startBeat()
                    done(o.optString("data"), null)
                }.onFailure { e ->
                    state.value = State.Off
                    val m = e.message.orEmpty()
                    Account.checkBan(m)
                    if (m.startsWith("BAKIM|")) com.zenvex.online.ui.Gate.poll()
                    done(null, friendly(m))
                }
            }
        }
    }

    private fun friendly(m: String) = when {
        m.startsWith("BAKIM|") -> "Sunucu bakımda"
        m.startsWith("OTURUM|") -> m.substringAfter("OTURUM|")
        m.startsWith("HILE|") -> m.substringAfter("HILE|")
        m.contains("BAN|") -> "Hesabın yasaklı"
        m.contains("ulaşılamadı") -> "İnternet bağlantısı yok"
        else -> m.ifEmpty { "Bağlanılamadı" }
    }

    private fun startBeat() {
        val my = sid
        beatThread?.interrupt()
        beatThread = Thread {
            while (sid == my && !Thread.currentThread().isInterrupted) {
                try { Thread.sleep(15_000) } catch (_: InterruptedException) { break }
                if (sid != my) break
                val r = runCatching {
                    Supa.rpcSync("zx_heartbeat", Account.authJson().put("p_sid", my).put("p_game_time", simTime))
                }
                main.post { onBeat(my, r) }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun onBeat(my: String, r: Result<String>) {
        if (sid != my) return
        r.onSuccess { body ->
            lastOk = SystemClock.elapsedRealtime(); fails = 0
            if (state.value == State.Lost) com.zenvex.online.ui.Toaster.show("Bağlantı Geri Geldi")
            state.value = State.Live
            runCatching { JSONObject(body) }.getOrNull()?.let { o ->
                if (!o.isNull("trade")) Trade.claimPending(o.optLong("trade"))
            }
        }.onFailure { e ->
            val m = e.message.orEmpty()
            when {
                m.startsWith("OTURUM|") || m.contains("BAN|") || m.startsWith("BAKIM|") || m.contains("Bu hesap silindi") -> {
                    if (m.startsWith("BAKIM|")) com.zenvex.online.ui.Gate.poll()
                    kick(friendly(m))
                }
                else -> {
                    fails++
                    if (fails >= 2) state.value = State.Lost
                    if (SystemClock.elapsedRealtime() - lastOk > 90_000) kick("Bağlantı koptu, tekrar gir")
                }
            }
        }
    }

    fun kick(reason: String) {
        if (state.value == State.Off) return
        end(send = false)
        kicked.value = reason
    }

    /** Oyundan çıkış: oturum kapanır (son kayıt önce gönderilmeli). */
    fun end(send: Boolean = true) {
        val my = sid
        sid = ""; secret = ""
        beatThread?.interrupt(); beatThread = null
        state.value = State.Off
        if (send && my.isNotEmpty()) Supa.bg { runCatching { Supa.rpcSync("zx_session_end", Account.authJson().put("p_sid", my)) } }
    }

    private fun sign(seq: Long, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal("$seq:$data".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** İmzalı kayıt argümanları (takas da kullanır). */
    @Synchronized
    fun signedSave(level: Int, data: String, kills: Int, xp: Long): JSONObject {
        seq++
        return Account.authJson().put("p_sid", sid).put("p_seq", seq).put("p_sig", sign(seq, data))
            .put("p_level", level).put("p_data", data).put("p_kills", kills).put("p_xp", xp)
    }

    /**
     * Kaydı sunucuya yollar (arka planda). Sunucu reddedip son geçerli kaydı dönerse [rollback] ana iş parçacığında çağrılır.
     */
    fun save(level: Int, data: String, kills: Int, xp: Long, rollback: (String, String) -> Unit) {
        if (state.value == State.Off || sid.isEmpty()) return
        val args = signedSave(level, data, kills, xp)
        Supa.bg {
            val r = runCatching { JSONObject(Supa.rpcSync("zx_save3", args)) }
            r.onSuccess { o ->
                if (!o.optBoolean("ok", true) && o.has("rollback")) main.post { rollback(o.optString("rollback"), o.optString("msg")) }
            }.onFailure { e ->
                val m = e.message.orEmpty()
                if (m.startsWith("OTURUM|") || m.contains("BAN|") || m.startsWith("BAKIM|") || m.startsWith("HILE|")) main.post { kick(friendly(m)) }
            }
        }
    }
}
