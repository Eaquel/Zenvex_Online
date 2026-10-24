package com.zenvex.online.net

import android.os.SystemClock
import android.util.Log
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Kendi oyun sunucumuza (rust/zenvex_server) tek WebSocket bağlantısı. Supabase kanalıyla aynı olay adlarını taşır,
 * böylece [com.zenvex.online.game.Online] iki bağlantıyı da aynı şekilde kullanır. Farklar: bölge değişince [area]
 * çağrılır (oda değişir), konumlar sunucudan "P" paketiyle toplu gelir, ev sahibini sunucu seçer ([onHost]).
 *
 * [hello]: her bağlanışta kimlik mesajı (oturum numarası + oturum sırrıyla imza); null ise bağlanılmaz.
 */
class GameNet(
    private val url: String,
    private val hello: () -> JSONObject?,
    private val onBroadcast: (event: String, payload: JSONObject) -> Unit,
    private val onPresence: (joins: JSONObject, leaves: JSONObject, full: Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onHost: (String) -> Unit,
) : Link {
    private var ws: WebSocket? = null
    @Volatile override var joined = false
        private set
    @Volatile override var pingMs = -1
        private set
    @Volatile private var closed = false
    private var meta: JSONObject? = null
    @Volatile private var areaIdx = -1
    private var backoff = 1000L
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private var beat: ScheduledFuture<*>? = null
    @Volatile private var pingId = 0L
    @Volatile private var pingAt = 0L

    fun open() {
        closed = false
        connect()
    }

    private fun connect() {
        if (closed) return
        val h = hello() ?: run { onStatus("Oturum yok"); return }
        onStatus("Bağlanıyor…")
        ws = Supa.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(h.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("zenvex", "oyun sunucusu: ${t.message}")
                drop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = drop()
        })
    }

    private fun handle(socket: WebSocket, text: String) {
        val m = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (val t = m.optString("t")) {
            "welcome" -> {
                joined = true
                backoff = 1000L
                onStatus("Çevrimiçi")
                meta?.let { socket.send(JSONObject().put("t", "track").put("meta", it).toString()) }
                if (areaIdx >= 0) socket.send(JSONObject().put("t", "area").put("a", areaIdx).toString())
                beat?.cancel(false)
                beat = timer.scheduleWithFixedDelay({
                    pingId++; pingAt = SystemClock.elapsedRealtime()
                    ws?.send(JSONObject().put("t", "ping").put("i", pingId).toString())
                }, 1, 3, TimeUnit.SECONDS)
            }
            "pong" -> if (m.optLong("i") == pingId) pingMs = (SystemClock.elapsedRealtime() - pingAt).toInt()
            "err" -> { onStatus(m.optString("msg").take(60)); closed = true; socket.close(1000, null) }
            "pres" -> onPresence(m.optJSONObject("joins") ?: JSONObject(), m.optJSONObject("leaves") ?: JSONObject(), m.optBoolean("full"))
            "host" -> onHost(m.optString("h"))
            // konum paketi: [[uid, s0, s1, ...], ...] → her biri bir "p" olayı
            "P" -> {
                val l = m.optJSONArray("l") ?: return
                for (i in 0 until l.length()) {
                    val e = l.optJSONArray(i) ?: continue
                    val s = org.json.JSONArray()
                    for (k in 1 until e.length()) s.put(e.optDouble(k))
                    onBroadcast("p", JSONObject().put("u", e.optString(0)).put("s", s))
                }
            }
            else -> onBroadcast(t, m)
        }
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

    /** Bölge değişti: sunucu oyuncuyu o bölgenin odasına alır */
    fun area(a: Int) {
        areaIdx = a
        if (joined) ws?.send(JSONObject().put("t", "area").put("a", a).toString())
    }

    override fun send(event: String, payload: JSONObject) {
        if (!joined) return
        val o = JSONObject(payload.toString()).put("t", event)
        ws?.send(o.toString())
    }

    override fun track(meta: JSONObject) {
        this.meta = meta
        if (joined) ws?.send(JSONObject().put("t", "track").put("meta", meta).toString())
    }

    override fun close() {
        closed = true
        joined = false
        beat?.cancel(false)
        ws?.close(1000, null)
        ws = null
        timer.shutdown()
    }

    override fun reconnectNow() {
        if (closed || joined) return
        backoff = 200L
        ws?.cancel()
    }
}
