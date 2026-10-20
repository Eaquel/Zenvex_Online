package com.zenvex.online.game

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.zenvex.online.net.Supa
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.hypot

/**
 * Çevrimiçi dünya: Supabase Realtime, sunucu başına bir kanal (`zx-<sunucu>`), "ev sahibi" modeli.
 *  presence  isim / klan (kim bağlı)
 *  p         konum + görünüm (5 Hz, herkes)
 *  z         ev sahibinin zombi anlık görüntüsü (5 Hz)
 *  hit       istemcinin zombi vuruşu → ev sahibi uygular
 *  kill      öldürme ödülü → vuran oyuncu
 *  pd        zombi hasarı → hedef oyuncu
 *  c         sohbet (genel / yakın); genel sohbet ayrıca veritabanına yazılır (geçmiş)
 *  treq, tr  takas isteği ve takas teklifleri
 * Ev sahibi: kanaldaki oyuncular arasında en küçük kimlik. O ayrılırsa sıradaki devralır.
 */
object Online {
    data class ChatLine(val channel: String, val name: String, val text: String, val tag: String, val mine: Boolean, val ts: Long)
    data class Near(val uid: String, val name: String, val x: Float, val y: Float, val vehicle: Int, val dist: Float)
    data class TradeItem(val id: String, val n: Int)
    data class TradeState(
        val sid: String, val other: String, val otherName: String,
        val mine: List<TradeItem>, val theirs: List<TradeItem>, val myOk: Boolean, val theirOk: Boolean, val done: Boolean,
    )

    val status = mutableStateOf("Çevrimdışı")
    val online = mutableStateOf(false)
    val playerCount = mutableIntStateOf(1)
    val role = mutableStateOf("Tek oyunculu")
    val chat = mutableStateListOf<ChatLine>()
    val tradeRequest = mutableStateOf<Pair<String, String>?>(null) // (uid, isim)
    val trade = mutableStateOf<TradeState?>(null)
    @Volatile var onChat: ((ChatLine) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var channel: Supa.Channel? = null
    private var serverId = ""
    @Volatile var uid: String = ""
        private set
    private var name = "Oyuncu"
    private var tag = ""

    private class Remote(var name: String, var clan: String = "") {
        val cur = FloatArray(Native.REMOTE_STRIDE)
        val target = FloatArray(Native.REMOTE_STRIDE)
        var ts = 0L
        var present = true
    }
    private val remotes = HashMap<String, Remote>()
    private val local = FloatArray(Native.REMOTE_STRIDE)
    private var lastPub = 0L
    private var lastSnap = 0L
    @Volatile var myX = 0f
        private set
    @Volatile var myY = 0f
        private set
    private var mode = 0
    private val snapBuf = FloatArray(12 * 80)
    @Volatile private var pendingSnap: FloatArray? = null
    private val hitQ = ConcurrentLinkedQueue<JSONObject>()
    private val cmdQ = ConcurrentLinkedQueue<JSONObject>()
    private val killerIds = ArrayList<String>()
    @Volatile var lastNames: Array<String> = emptyArray()
        private set
    @Volatile var lastClans: Array<String> = emptyArray()
        private set
    @Volatile var lastIds: Array<String> = emptyArray()
        private set

    /** Seçili sunucunun kanalına bağlanır (zaten bağlıysa yalnızca kimliği günceller). */
    fun start(playerName: String, playerTag: String) {
        name = playerName; tag = playerTag
        if (!Integrity.ok) { status.value = Integrity.reason; return }
        val sid = GameSession.server.value.id
        val me = if (Account.loggedIn) "a${Account.id.value}" else guestId()
        if (channel != null && sid == serverId && me == uid) { trackMe(); return }
        stop()
        if (!Supa.configured) { status.value = "Çevrimdışı (sunucu ayarlanmadı)"; return }
        serverId = sid
        uid = me
        chat.clear()
        Account.recentChat(sid) { r ->
            r.onSuccess { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    chat += ChatLine("global", o.optString("n"), o.optString("m"), o.optString("g"), "a${o.optLong("u")}" == uid, o.optLong("ts"))
                }
            }
        }
        channel = Supa.Channel(
            topic = "realtime:zx-$sid",
            presenceKey = uid,
            onBroadcast = ::onBroadcast,
            onPresence = ::onPresence,
            onStatus = { s -> main.post { status.value = s; online.value = s == "Çevrimiçi" } },
        ).also { it.open() }
        trackMe()
    }

    private fun guestId(): String {
        val p = GameSession.prefs()
        var g = p.getString("guest", null)
        if (g == null) { g = "g" + java.util.UUID.randomUUID().toString().take(8); p.edit().putString("guest", g).apply() }
        return g
    }

    fun stop() {
        channel?.close()
        channel = null
        synchronized(remotes) { remotes.clear() }
        online.value = false
        status.value = "Çevrimdışı"
        playerCount.intValue = 1
        trade.value = null
        tradeRequest.value = null
        mode = 0
    }

    fun setIdentity(playerName: String, playerTag: String) { name = playerName; tag = playerTag; trackMe() }

    private fun trackMe() {
        channel?.track(JSONObject().put("n", name).put("g", Account.clan.value?.name ?: tag))
    }

    // =========================================================== gelen
    private fun onPresence(joins: JSONObject, leaves: JSONObject, full: Boolean) {
        synchronized(remotes) {
            if (full) remotes.values.forEach { it.present = false }
            for (k in joins.keys()) {
                if (k == uid) continue
                val meta = joins.optJSONObject(k)?.optJSONArray("metas")?.optJSONObject(0) ?: continue
                val r = remotes.getOrPut(k) { Remote("") }
                r.name = meta.optString("n", "Oyuncu"); r.clan = meta.optString("g"); r.present = true
            }
            for (k in leaves.keys()) remotes.remove(k)
            if (full) remotes.values.removeAll { !it.present }
            val n = remotes.size + 1
            main.post { playerCount.intValue = n }
        }
    }

    private fun onBroadcast(ev: String, p: JSONObject) {
        val me = uid
        when (ev) {
            "p" -> {
                val k = p.optString("u"); if (k.isEmpty() || k == me) return
                val arr = p.optJSONArray("s") ?: return
                synchronized(remotes) {
                    val r = remotes.getOrPut(k) { Remote(p.optString("n", "Oyuncu")) }
                    for (i in 0 until minOf(arr.length(), Native.REMOTE_STRIDE)) r.target[i] = arr.optDouble(i).toFloat()
                    if (r.ts == 0L) System.arraycopy(r.target, 0, r.cur, 0, Native.REMOTE_STRIDE)
                    r.ts = SystemClock.elapsedRealtime()
                }
            }
            "z" -> if (mode == 2 && p.optString("h") != me) {
                val arr = p.optJSONArray("d") ?: return
                pendingSnap = FloatArray(arr.length()) { arr.optDouble(it).toFloat() }
            }
            "hit" -> if (mode == 1) hitQ += p
            "kill" -> if (p.optString("to") == me) {
                cmdQ += JSONObject().put("cmd", "net_kill").put("kind", p.optInt("kind")).put("level", p.optInt("level"))
                    .put("x", p.optDouble("x")).put("y", p.optDouble("y"))
            }
            "pd" -> if (p.optString("to") == me) cmdQ += JSONObject().put("cmd", "net_pdmg").put("v", p.optInt("v")).put("poison", p.optBoolean("p"))
            "c" -> {
                val ch = p.optString("ch", "global")
                if (ch == "local" && hypot(p.optDouble("x").toFloat() - myX, p.optDouble("y").toFloat() - myY) > 1500f) return
                // klan sohbeti yalnız aynı klanın üyelerine
                if (ch == "clan" && (p.optLong("cid") != (Account.clan.value?.id ?: -1L))) return
                addLine(ChatLine(ch, p.optString("n", "?"), p.optString("m").take(140), p.optString("g"), false, System.currentTimeMillis()))
            }
            "treq" -> if (p.optString("to") == me) main.post { tradeRequest.value = p.optString("u") to p.optString("n", "Oyuncu") }
            "tr" -> if (p.optString("to") == me) main.post { onTradeMsg(p) }
        }
    }

    private fun addLine(l: ChatLine) = main.post {
        chat += l
        while (chat.size > 300) chat.removeAt(0)
        onChat?.invoke(l)
    }

    // =========================================================== sohbet
    /** Sohbet 5. seviyede açılır; iki mesaj arasında 3 sn beklenir (sunucu da denetler). */
    const val CHAT_LEVEL = 5
    const val CHAT_COOLDOWN_MS = 3000L
    private var lastSend = 0L

    /** Kalan bekleme (ms); 0 ise yazılabilir. */
    fun chatWait(): Long = (CHAT_COOLDOWN_MS - (SystemClock.elapsedRealtime() - lastSend)).coerceAtLeast(0L)

    /** Özel mesaj gönderildi: aynı 3 sn bekleme */
    fun markSent() { lastSend = SystemClock.elapsedRealtime() }

    fun chatLocked(): Boolean = GameSession.hud()[Native.LEVEL] < CHAT_LEVEL

    /** Gönderir; olmazsa nedenini döner. */
    fun send(channelName: String, text: String): String? {
        val t = text.trim().take(140)
        if (t.isEmpty()) return null
        if (chatLocked()) return "Sohbet $CHAT_LEVEL. Seviyede Açılır"
        if (channelName == "clan") {
            val c = Account.clan.value ?: return "Bir Klanda Değilsin"
            val mu = c.json.optString("muted_until")
            if (mu.isNotEmpty() && mu != "null" && runCatching { java.time.OffsetDateTime.parse(mu).toInstant().toEpochMilli() > System.currentTimeMillis() }.getOrDefault(false))
                return "Klan Sohbetinde Susturuldun"
        }
        val w = chatWait()
        if (w > 0) return "${(w + 999) / 1000} Sn Bekle"
        lastSend = SystemClock.elapsedRealtime()
        val g = Account.clan.value?.name ?: tag
        addLine(ChatLine(channelName, name, t, g, true, System.currentTimeMillis()))
        val c = channel ?: return null
        val m = JSONObject().put("u", uid).put("n", name).put("g", g).put("m", t).put("ch", channelName)
        if (channelName == "local") m.put("x", myX.toDouble()).put("y", myY.toDouble())
        if (channelName == "clan") m.put("cid", Account.clan.value?.id ?: 0)
        c.send("c", m)
        if (channelName == "global") Account.postChat(serverId, t)
        else if (channelName == "local") Account.chatLog("$serverId:local", t)
        else if (channelName == "clan") Account.chatLog("clan:${Account.clan.value?.id ?: 0}", t)
        return null
    }

    fun system(text: String) = addLine(ChatLine("system", "Sistem", text, "", false, System.currentTimeMillis()))

    // =========================================================== oyun döngüsü (GameSession.lock altında)
    /** Yerel durumu yayınlar, ev sahibini seçer, kuyruktaki ağ olaylarını uygular. */
    fun tick(h: Long) {
        val c = channel
        val now = SystemClock.elapsedRealtime()
        if (now - lastPub >= 200) {
            lastPub = now
            Native.localState(h, local)
            myX = local[0]; myY = local[1]
            if (c != null && c.joined) {
                val s = JSONArray()
                for (v in local) s.put((v * 10).toInt() / 10.0)
                c.send("p", JSONObject().put("u", uid).put("n", name).put("s", s))
            }
            elect(h)
        }
        if (c == null) return
        when (mode) {
            1 -> {
                while (true) {
                    val o = hitQ.poll() ?: break
                    Native.command(h, JSONObject().put("cmd", "net_hit").put("id", o.optLong("id")).put("dmg", o.optDouble("dmg"))
                        .put("kx", o.optDouble("kx")).put("ky", o.optDouble("ky")).put("stun", o.optBoolean("st"))
                        .put("killer", killerIndex(o.optString("u"))).toString())
                }
                if (now - lastSnap >= 200) {
                    lastSnap = now
                    val n = Native.netSnapshot(h, snapBuf)
                    val d = JSONArray()
                    for (i in 0 until n * 12) d.put((snapBuf[i] * 10).toInt() / 10.0)
                    c.send("z", JSONObject().put("h", uid).put("d", d))
                }
            }
            2 -> pendingSnap?.let { p -> pendingSnap = null; Native.netApply(h, p, p.size / 12) }
        }
        while (true) {
            val q = cmdQ.poll() ?: break
            Native.command(h, q.toString())
        }
    }

    private fun elect(h: Long) {
        val now = SystemClock.elapsedRealtime()
        val active = synchronized(remotes) { remotes.filter { now - it.value.ts < 8000 }.keys.toList() }
        val newMode = when {
            channel == null || active.isEmpty() -> 0
            active.all { uid < it } -> 1
            else -> 2
        }
        if (newMode != mode) {
            mode = newMode
            Native.command(h, JSONObject().put("cmd", "net_mode").put("mode", mode).toString())
            val r = when (mode) { 1 -> "Ev sahibi (zombiler senden)"; 2 -> "Katılımcı"; else -> "Tek oyunculu" }
            main.post { role.value = r }
        }
    }

    private fun killerIndex(u: String): Int {
        if (u.isEmpty()) return -1
        val i = killerIds.indexOf(u)
        if (i >= 0) return i
        killerIds += u
        return killerIds.size - 1
    }

    /** Oyun olaylarından ağ ile ilgili olanlar (GameView çağırır). */
    fun onEvent(e: JSONObject) {
        val c = channel ?: return
        when (e.optString("t")) {
            "zhit" -> if (mode == 2) c.send("hit", JSONObject().put("u", uid).put("id", e.optLong("id")).put("dmg", e.optDouble("dmg"))
                .put("kx", e.optDouble("kx")).put("ky", e.optDouble("ky")).put("st", e.optBoolean("stun")))
            "rdmg" -> lastIds.getOrNull(e.optInt("i"))?.let { u ->
                c.send("pd", JSONObject().put("to", u).put("v", e.optInt("v")).put("p", e.optBoolean("poison")))
            }
            "nkill" -> killerIds.getOrNull(e.optInt("k"))?.let { u ->
                c.send("kill", JSONObject().put("to", u).put("kind", e.optInt("kind")).put("level", e.optInt("level"))
                    .put("x", e.optDouble("x")).put("y", e.optDouble("y")))
            }
        }
    }

    /** Uzak oyuncuları ara değerleyerek arabelleğe yazar; sayıyı döndürür (çevrimdışıysa null). */
    fun remotes(out: FloatArray): Int? {
        if (channel == null) return null
        val now = SystemClock.elapsedRealtime()
        synchronized(remotes) {
            var n = 0
            val nm = ArrayList<String>()
            val cl = ArrayList<String>()
            val ids = ArrayList<String>()
            for ((k, r) in remotes) {
                if (r.ts == 0L || now - r.ts > 10000) continue
                if ((n + 1) * Native.REMOTE_STRIDE > out.size) break
                if (hypot(r.target[0] - myX, r.target[1] - myY) > 3000f) continue
                r.cur[0] += (r.target[0] - r.cur[0]) * 0.2f
                r.cur[1] += (r.target[1] - r.cur[1]) * 0.2f
                for (i in 2 until Native.REMOTE_STRIDE) r.cur[i] = r.target[i]
                r.cur[4] += 1f / 60f
                System.arraycopy(r.cur, 0, out, n * Native.REMOTE_STRIDE, Native.REMOTE_STRIDE)
                nm += r.name
                cl += r.clan
                ids += k
                n++
            }
            lastNames = nm.toTypedArray()
            lastClans = cl.toTypedArray()
            lastIds = ids.toTypedArray()
            return n
        }
    }

    /** Bağlı oyuncular (isim, klan) — oyuncu listesi için. */
    fun players(): List<Pair<String, String>> = synchronized(remotes) { remotes.values.map { it.name to it.clan } }

    /** En yakın uzak oyuncu (takas / yolculuk tuşları için). */
    fun nearest(maxD: Float): Near? = synchronized(remotes) {
        remotes.entries.filter { it.value.ts > 0 }
            .map { (k, r) -> Near(k, r.name, r.cur[0], r.cur[1], r.cur[9].toInt(), hypot(r.cur[0] - myX, r.cur[1] - myY)) }
            .filter { it.dist < maxD }.minByOrNull { it.dist }
    }

    fun position(u: String): FloatArray? = synchronized(remotes) { remotes[u]?.cur?.copyOf() }

    // =========================================================== takas
    // Her taraf kendi teklifini (eşyalar + onay) karşı tarafa yollar; ikisi de onaylayınca iki taraf da kendi tarafını uygular.
    private var myItems: List<TradeItem> = emptyList()
    private var myOk = false

    private fun sid(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    fun requestTrade(other: String) {
        val c = channel ?: return
        c.send("treq", JSONObject().put("to", other).put("u", uid).put("n", name))
        openTrade(other, synchronized(remotes) { remotes[other]?.name ?: "Oyuncu" })
        system("Takas isteği gönderildi")
    }

    fun acceptTrade() {
        val (u, n) = tradeRequest.value ?: return
        tradeRequest.value = null
        openTrade(u, n)
        sendOffer()
    }

    fun declineTrade() { tradeRequest.value = null }

    private fun openTrade(other: String, otherName: String) {
        myItems = emptyList(); myOk = false
        trade.value = TradeState(sid(uid, other), other, otherName, emptyList(), emptyList(), false, false, false)
    }

    private fun sendOffer(cancel: Boolean = false) {
        val t = trade.value ?: return
        val items = JSONArray()
        for (it in myItems) items.put(JSONObject().put("id", it.id).put("n", it.n))
        channel?.send("tr", JSONObject().put("to", t.other).put("u", uid).put("sid", t.sid).put("items", items).put("ok", myOk).put("cancel", cancel))
    }

    private fun onTradeMsg(p: JSONObject) {
        val t = trade.value ?: return
        if (p.optString("sid") != t.sid) return
        if (p.optBoolean("cancel")) { trade.value = null; system("Takas iptal edildi"); return }
        val arr = p.optJSONArray("items") ?: JSONArray()
        val theirs = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> TradeItem(o.optString("id"), o.optInt("n", 1)) } }
        // karşı taraf teklifini değiştirdiyse benim onayım düşer
        if (theirs != t.theirs && myOk) { myOk = false; sendOffer() }
        val theirOk = p.optBoolean("ok")
        trade.value = t.copy(mine = myItems, theirs = theirs, myOk = myOk, theirOk = theirOk)
        if (myOk && theirOk && !t.done) execute()
    }

    private fun execute() {
        val t = trade.value ?: return
        trade.value = t.copy(done = true)
        for (it in t.mine) cmdQ += JSONObject().put("cmd", "trade_take").put("id", it.id).put("n", it.n)
        for (it in t.theirs) cmdQ += JSONObject().put("cmd", "trade_give").put("id", it.id).put("n", it.n)
        system("Takas tamamlandı")
        main.postDelayed({ trade.value = null }, 1500)
        main.postDelayed({ GameSession.save(cloud = true) }, 400)
    }

    fun setTradeItems(items: List<TradeItem>) {
        myItems = items; myOk = false
        trade.value = trade.value?.copy(mine = items, myOk = false)
        sendOffer()
    }

    fun setTradeReady(ok: Boolean) {
        myOk = ok
        val t = trade.value ?: return
        trade.value = t.copy(myOk = ok)
        sendOffer()
        if (ok && t.theirOk && !t.done) execute()
    }

    fun cancelTrade() {
        sendOffer(cancel = true)
        trade.value = null
    }
}
