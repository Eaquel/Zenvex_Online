package com.zenvex.online.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zenvex.online.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Supabase'e çok ince bir istemci:
 *  - [rpc]: PostgREST üzerinden `zx_*` veritabanı fonksiyonları (hesap, kayıt, sohbet geçmişi, posta, klan)
 *  - [Channel]: Realtime WebSocket (Phoenix protokolü 1.0.0) — yayın (broadcast) ve kimler bağlı (presence)
 */
object Supa {
    /** ilk ağ isteğinde kurulur (ekran görüntüsü testlerinde ağ hiç kullanılmaz; OkHttp 5 Android TLS katmanını kurarken arar) */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val json = "application/json".toMediaType()

    val configured: Boolean get() = BuildConfig.SB_URL.isNotEmpty() && BuildConfig.SB_KEY.isNotEmpty()

    class RpcError(msg: String) : Exception(msg)

    /** Eşzamanlı çağrı (arka plan iş parçacığında kullan). Dönen gövde ham JSON metnidir. */
    fun rpcSync(fn: String, args: JSONObject = JSONObject()): String {
        if (!configured) throw RpcError("Sunucu ayarlanmadı")
        if (!com.zenvex.online.game.Integrity.ok) throw RpcError(com.zenvex.online.game.Integrity.reason)
        val req = Request.Builder()
            .url("${BuildConfig.SB_URL}/rest/v1/rpc/$fn")
            .header("apikey", BuildConfig.SB_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SB_KEY}")
            .post(args.toString().toRequestBody(json))
            .build()
        val body: String
        val code: Int
        try {
            http.newCall(req).execute().use { r: Response -> code = r.code; body = r.body.string() }
        } catch (e: Exception) {
            throw RpcError("Sunucuya ulaşılamadı")
        }
        if (code !in 200..299) {
            val m = runCatching { JSONObject(body).optString("message") }.getOrNull()
            Log.w("zenvex", "rpc $fn $code $body")
            // oyun sırasında yasaklanan / silinen hesap: hangi çağrıda olursa olsun hemen yasak ekranına düşer
            if (m != null && (m.contains("BAN|") || m.contains("Bu hesap silindi"))) main.post { com.zenvex.online.game.Account.checkBan(m) }
            throw RpcError(
                when {
                    code == 404 -> "Sunucu kurulumu eksik (veritabanı)"
                    !m.isNullOrBlank() -> m
                    else -> "Sunucu hatası ($code)"
                },
            )
        }
        return body
    }

    /** Edge function çağrısı (ör. zx-google). Hata gövdesindeki "error" mesajı istisnaya taşınır. */
    fun fnSync(name: String, args: JSONObject): String {
        if (!configured) throw RpcError("Sunucu ayarlanmadı")
        val req = Request.Builder()
            .url("${BuildConfig.SB_URL}/functions/v1/$name")
            .header("apikey", BuildConfig.SB_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SB_KEY}")
            .post(args.toString().toRequestBody(json))
            .build()
        val body: String
        val code: Int
        try {
            http.newCall(req).execute().use { r: Response -> code = r.code; body = r.body.string() }
        } catch (e: Exception) {
            throw RpcError("Sunucuya ulaşılamadı")
        }
        if (code !in 200..299) {
            val m = runCatching { JSONObject(body).optString("error") }.getOrNull()
            Log.w("zenvex", "fn $name $code $body")
            throw RpcError(if (!m.isNullOrBlank()) m else "Sunucu hatası ($code)")
        }
        return body
    }

    fun fn(name: String, args: JSONObject, done: (Result<String>) -> Unit) {
        io.execute {
            val r = runCatching { fnSync(name, args) }
            main.post { done(r) }
        }
    }

    /** Arka planda çağırır, sonucu ana iş parçacığında verir. */
    fun rpc(fn: String, args: JSONObject = JSONObject(), done: (Result<String>) -> Unit) {
        io.execute {
            val r = runCatching { rpcSync(fn, args) }
            main.post { done(r) }
        }
    }

    fun bg(block: () -> Unit) = io.execute { runCatching(block).onFailure { Log.w("zenvex", "bg", it) } }

    // ======================================================================== Realtime
    /**
     * Tek bir Realtime kanalı (kendi soketiyle). Bağlantı koparsa kendiliğinden yeniden bağlanır.
     * Olaylar OkHttp iş parçacığında gelir; dinleyiciler kendi senkronizasyonunu yapmalı.
     */
    class Channel(
        private val topic: String,
        private val presenceKey: String,
        private val onBroadcast: (event: String, payload: JSONObject) -> Unit,
        private val onPresence: (joins: JSONObject, leaves: JSONObject, full: Boolean) -> Unit,
        private val onStatus: (String) -> Unit,
    ) : Link {
        private var ws: WebSocket? = null
        @Volatile override var joined = false
            private set
        @Volatile private var closed = false
        private var ref = 0
        private var joinRef = "0"
        private var beat: ScheduledFuture<*>? = null
        private var trackMeta: JSONObject? = null
        /** gidiş-dönüş süresi (ms): 5 sn'de bir kalp atışı yanıtından; -1 = ölçülmedi */
        @Volatile override var pingMs = -1
            private set
        @Volatile private var pingRef = ""
        @Volatile private var pingAt = 0L
        private var backoff = 1000L
        private val timer = Executors.newSingleThreadScheduledExecutor()

        fun open() {
            closed = false
            connect()
        }

        /** Uygulama öne gelince: bağlı değilse beklemeden yeniden bağlan */
        override fun reconnectNow() {
            if (closed || joined) return
            backoff = 200L
            ws?.cancel()
        }

        private fun connect() {
            if (closed) return
            onStatus("Bağlanıyor…")
            val url = BuildConfig.SB_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
                "/realtime/v1/websocket?apikey=${BuildConfig.SB_KEY}&vsn=1.0.0"
            ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoff = 1000L
                    join()
                    beat?.cancel(false)
                    // kalp atışı aynı zamanda ping ölçer (sunucu aynı ref ile yanıtlar)
                    beat = timer.scheduleWithFixedDelay({
                        pingRef = (++ref).toString(); pingAt = android.os.SystemClock.elapsedRealtime()
                        push("phoenix", "heartbeat", JSONObject(), null, pingRef)
                    }, 1, 5, TimeUnit.SECONDS)
                }

                override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("zenvex", "realtime: ${t.message}")
                    drop()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = drop()
            })
        }

        private fun drop() {
            joined = false
            pingMs = -1
            beat?.cancel(false)
            if (closed) return
            onStatus("Bağlantı koptu, yeniden deneniyor…")
            val wait = backoff
            backoff = (backoff * 2).coerceAtMost(15000L)
            timer.schedule({ connect() }, wait, TimeUnit.MILLISECONDS)
        }

        private fun join() {
            joinRef = (++ref).toString()
            val cfg = JSONObject()
                .put("broadcast", JSONObject().put("self", false).put("ack", false))
                .put("presence", JSONObject().put("enabled", true).put("key", presenceKey))
                .put("private", false)
            // katılım mesajının kendi ref'i join_ref ile aynı olmalı: sunucu cevabı mesaj ref'iyle döner (join_ref göndermez)
            push(topic, "phx_join", JSONObject().put("config", cfg), joinRef, joinRef)
        }

        private fun handle(text: String) {
            val m = runCatching { JSONObject(text) }.getOrNull() ?: return
            val ev = m.optString("event")
            val p = m.optJSONObject("payload") ?: JSONObject()
            when (ev) {
                "phx_reply" -> if (m.optString("topic") == "phoenix") {
                    if (m.optString("ref") == pingRef) pingMs = (android.os.SystemClock.elapsedRealtime() - pingAt).toInt()
                } else if (m.optString("ref") == joinRef && m.optString("topic") == topic) {
                    if (p.optString("status") == "ok") {
                        joined = true
                        onStatus("Çevrimiçi")
                        trackMeta?.let { track(it) }
                    } else {
                        onStatus("Katılamadı: " + p.optJSONObject("response")?.optString("reason").orEmpty().take(60))
                    }
                }
                "broadcast" -> onBroadcast(p.optString("event"), p.optJSONObject("payload") ?: JSONObject())
                "presence_state" -> onPresence(p, JSONObject(), true)
                "presence_diff" -> onPresence(p.optJSONObject("joins") ?: JSONObject(), p.optJSONObject("leaves") ?: JSONObject(), false)
                "phx_error", "phx_close" -> if (m.optString("topic") == topic && !closed) {
                    joined = false
                    timer.schedule({ if (!closed) join() }, 2000, TimeUnit.MILLISECONDS)
                }
                "system" -> if (p.optString("status") == "error") onStatus(p.optString("message").take(60))
            }
        }

        private fun push(t: String, event: String, payload: JSONObject, jr: String?, msgRef: String? = null) {
            val m = JSONObject().put("topic", t).put("event", event).put("payload", payload).put("ref", msgRef ?: (++ref).toString())
            if (jr != null) m.put("join_ref", jr)
            ws?.send(m.toString())
        }

        /** Herkese yayın (gönderen hariç). */
        override fun send(event: String, payload: JSONObject) {
            if (!joined) return
            push(topic, "broadcast", JSONObject().put("type", "broadcast").put("event", event).put("payload", payload), joinRef)
        }

        /** Kimlik bilgisini (isim, klan…) tüm kanala bildirir. */
        override fun track(meta: JSONObject) {
            trackMeta = meta
            if (!joined) return
            push(topic, "presence", JSONObject().put("type", "presence").put("event", "track").put("payload", meta), joinRef)
        }

        override fun close() {
            closed = true
            joined = false
            beat?.cancel(false)
            runCatching { push(topic, "phx_leave", JSONObject(), joinRef) }
            ws?.close(1000, "bye")
            ws = null
            timer.shutdown()
        }
    }
}
