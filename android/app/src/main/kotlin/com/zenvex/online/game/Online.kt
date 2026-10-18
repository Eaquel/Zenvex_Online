package com.zenvex.online.game

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.zenvex.online.BuildConfig
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.hypot

/**
 * Çevrimiçi altyapı (geçici): Firebase Realtime Database, "ev sahibi" modeli.
 *  /presence/<oda>/<uid>        konum + görünüm (8 Hz), bağlantı kopunca silinir
 *  /world/<oda>/z               ev sahibinin zombi anlık görüntüsü (6 Hz)
 *  /world/<oda>/hits/<id>       istemcilerin zombi vuruşları (ev sahibi uygular ve siler)
 *  /world/<oda>/kills/<uid>/... öldürme ödülü bildirimi (vuran oyuncuya)
 *  /world/<oda>/pdmg/<uid>/...  zombi hasarı bildirimi (hedef oyuncuya)
 *  /trade/<sid>                 iki oyuncu arası takas oturumu
 *  /chat/global, /chat/local    sohbet
 * Ev sahibi: odadaki etkin oyuncular arasında en küçük uid. O ayrılırsa sıradaki devralır.
 * Yapılandırma (BuildConfig.FB_*) boşsa oyun tek oyunculu çalışır.
 */
object Online {
    data class ChatLine(val channel: String, val name: String, val text: String, val tag: String, val mine: Boolean, val ts: Long)
    data class Near(val uid: String, val name: String, val x: Float, val y: Float, val vehicle: Int, val dist: Float)
    data class TradeItem(val id: String, val n: Int)
    class TradeState(
        val sid: String, val other: String, val otherName: String,
        val mine: List<TradeItem>, val theirs: List<TradeItem>, val myOk: Boolean, val theirOk: Boolean, val done: Boolean,
    )

    val status = mutableStateOf("Çevrimdışı")
    val online = mutableStateOf(false)
    val playerCount = mutableStateOf(0)
    val role = mutableStateOf("Tek oyunculu")
    val chat = mutableStateListOf<ChatLine>()
    val tradeRequest = mutableStateOf<Pair<String, String>?>(null) // (uid, isim)
    val trade = mutableStateOf<TradeState?>(null)
    @Volatile var onChat: ((ChatLine) -> Unit)? = null

    private var db: FirebaseDatabase? = null
    @Volatile var uid: String? = null
        private set
    private var presenceRef: DatabaseReference? = null
    private const val ROOM = "dunya1"
    private var name = "Oyuncu"
    private var tag = ""

    private class Remote(var name: String) {
        val cur = FloatArray(Native.REMOTE_STRIDE)
        val target = FloatArray(Native.REMOTE_STRIDE)
        var ts = 0L
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

    /** remotes() ile aynı sırada isimler ve uid'ler */
    @Volatile var lastNames: Array<String> = emptyArray()
    @Volatile var lastIds: Array<String> = emptyArray()

    val configured: Boolean get() = BuildConfig.FB_API_KEY.isNotEmpty() && BuildConfig.FB_DB_URL.isNotEmpty()

    fun start(ctx: Context, playerName: String, playerTag: String) {
        name = playerName; tag = playerTag
        if (!configured) { status.value = "Çevrimdışı (sunucu ayarlanmadı)"; return }
        if (db != null) return
        try {
            val app = FirebaseApp.getApps(ctx).firstOrNull() ?: FirebaseApp.initializeApp(
                ctx, FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FB_API_KEY)
                    .setApplicationId(BuildConfig.FB_APP_ID)
                    .setProjectId(BuildConfig.FB_PROJECT_ID)
                    .setDatabaseUrl(BuildConfig.FB_DB_URL)
                    .build(),
            )
            status.value = "Bağlanıyor…"
            val auth = FirebaseAuth.getInstance(app)
            val go = { u: String ->
                uid = u
                db = FirebaseDatabase.getInstance(app, BuildConfig.FB_DB_URL)
                listen()
                online.value = true
                status.value = "Çevrimiçi"
            }
            val cur = auth.currentUser
            if (cur != null) go(cur.uid) else auth.signInAnonymously()
                .addOnSuccessListener { r -> r.user?.uid?.let(go) }
                .addOnFailureListener { e -> status.value = "Bağlanamadı: ${e.message?.take(60)}"; Log.w("zenvex", "auth", e) }
        } catch (e: Exception) {
            status.value = "Bağlanamadı"
            Log.w("zenvex", "firebase", e)
        }
    }

    fun setIdentity(playerName: String, playerTag: String) { name = playerName; tag = playerTag }

    private abstract class Child : ChildEventListener {
        override fun onChildChanged(s: DataSnapshot, p: String?) {}
        override fun onChildRemoved(s: DataSnapshot) {}
        override fun onChildMoved(s: DataSnapshot, p: String?) {}
        override fun onCancelled(e: DatabaseError) {}
    }

    private fun listen() {
        val d = db ?: return
        val me = uid ?: return
        presenceRef = d.getReference("presence/$ROOM/$me").also { it.onDisconnect().removeValue() }
        d.getReference("presence/$ROOM").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, p: String?) = upd(s)
            override fun onChildChanged(s: DataSnapshot, p: String?) = upd(s)
            override fun onChildRemoved(s: DataSnapshot) { synchronized(remotes) { remotes.remove(s.key); playerCount.value = remotes.size + 1 } }
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(e: DatabaseError) { status.value = "Bağlantı hatası" }
            fun upd(s: DataSnapshot) {
                val k = s.key ?: return
                if (k == me) return
                val arr = (s.child("s").value as? List<*>) ?: return
                synchronized(remotes) {
                    val r = remotes.getOrPut(k) { Remote("") }
                    r.name = s.child("n").getValue(String::class.java) ?: "Oyuncu"
                    for (i in 0 until minOf(arr.size, Native.REMOTE_STRIDE)) r.target[i] = (arr[i] as? Number)?.toFloat() ?: 0f
                    if (r.ts == 0L) System.arraycopy(r.target, 0, r.cur, 0, Native.REMOTE_STRIDE)
                    r.ts = SystemClock.elapsedRealtime()
                    playerCount.value = remotes.size + 1
                }
            }
        })
        // ---- zombi dünyası
        d.getReference("world/$ROOM/z").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (mode != 2) return
                if (s.child("h").getValue(String::class.java) == me) return
                val arr = (s.child("d").value as? List<*>) ?: return
                pendingSnap = FloatArray(arr.size) { (arr[it] as? Number)?.toFloat() ?: 0f }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        d.getReference("world/$ROOM/hits").addChildEventListener(object : Child() {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                if (mode != 1) return
                val o = JSONObject()
                for (c in s.children) o.put(c.key, c.value)
                hitQ += o
                s.ref.removeValue()
            }
        })
        d.getReference("world/$ROOM/kills/$me").addChildEventListener(object : Child() {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                val o = JSONObject().put("cmd", "net_kill")
                for (c in s.children) o.put(c.key, c.value)
                cmdQ += o
                s.ref.removeValue()
            }
        })
        d.getReference("world/$ROOM/pdmg/$me").addChildEventListener(object : Child() {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                cmdQ += JSONObject().put("cmd", "net_pdmg").put("v", s.child("v").value ?: 0).put("poison", s.child("p").value == true)
                s.ref.removeValue()
            }
        })
        // ---- takas
        d.getReference("trade_req/$me").addChildEventListener(object : Child() {
            override fun onChildAdded(s: DataSnapshot, p: String?) {
                val from = s.key ?: return
                tradeRequest.value = from to (s.child("n").getValue(String::class.java) ?: "Oyuncu")
                s.ref.removeValue()
            }
        })
        // ---- sohbet
        for (ch in listOf("global", "local")) {
            d.getReference("chat/$ch").limitToLast(30).addChildEventListener(object : Child() {
                override fun onChildAdded(s: DataSnapshot, p: String?) {
                    val text = s.child("m").getValue(String::class.java) ?: return
                    if (ch == "local") {
                        val x = s.child("x").getValue(Double::class.java)?.toFloat() ?: return
                        val y = s.child("y").getValue(Double::class.java)?.toFloat() ?: return
                        if (hypot(x - myX, y - myY) > 1500f) return
                    }
                    val line = ChatLine(ch, s.child("n").getValue(String::class.java) ?: "?", text.take(140),
                        s.child("g").getValue(String::class.java) ?: "", s.child("u").getValue(String::class.java) == me,
                        s.child("ts").getValue(Long::class.java) ?: 0L)
                    chat += line
                    while (chat.size > 80) chat.removeAt(0)
                    onChat?.invoke(line)
                }
            })
        }
    }

    // =========================================================== sohbet
    fun send(channel: String, text: String) {
        val t = text.trim().take(140)
        if (t.isEmpty()) return
        val d = db
        if (d == null || uid == null) {
            val line = ChatLine(channel, name, t, tag, true, System.currentTimeMillis())
            chat += line; onChat?.invoke(line); return
        }
        val m = hashMapOf<String, Any>("n" to name, "m" to t, "u" to uid!!, "g" to tag, "ts" to ServerValue.TIMESTAMP)
        if (channel == "local") { m["x"] = myX.toDouble(); m["y"] = myY.toDouble() }
        d.getReference("chat/$channel").push().setValue(m)
    }

    fun system(text: String) {
        val line = ChatLine("system", "Sistem", text, "", false, System.currentTimeMillis())
        chat += line; onChat?.invoke(line)
    }

    // =========================================================== oyun döngüsü (GameSession.lock altında)
    /** Yerel durumu yayınlar, ev sahibini seçer, kuyruktaki ağ olaylarını uygular. */
    fun tick(h: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPub >= 125) {
            lastPub = now
            Native.localState(h, local)
            myX = local[0]; myY = local[1]
            presenceRef?.setValue(hashMapOf("n" to name, "s" to local.map { (it * 10).toInt() / 10.0 }, "ts" to ServerValue.TIMESTAMP))
            elect(h)
        }
        val d = db ?: return
        when (mode) {
            1 -> {
                while (true) {
                    val o = hitQ.poll() ?: break
                    val killer = killerIndex(o.optString("u"))
                    Native.command(h, JSONObject().put("cmd", "net_hit").put("id", o.optLong("id")).put("dmg", o.optDouble("dmg"))
                        .put("kx", o.optDouble("kx")).put("ky", o.optDouble("ky")).put("stun", o.optBoolean("st")).put("killer", killer).toString())
                }
                if (now - lastSnap >= 160) {
                    lastSnap = now
                    val n = Native.netSnapshot(h, snapBuf)
                    val list = ArrayList<Double>(n * 12)
                    for (i in 0 until n * 12) list += (snapBuf[i] * 10).toInt() / 10.0
                    d.getReference("world/$ROOM/z").setValue(hashMapOf("h" to uid, "d" to list, "ts" to ServerValue.TIMESTAMP))
                }
            }
            2 -> pendingSnap?.let { p -> pendingSnap = null; Native.netApply(h, p, p.size / 12) }
        }
        while (true) {
            val c = cmdQ.poll() ?: break
            Native.command(h, c.toString())
        }
    }

    private fun elect(h: Long) {
        val me = uid ?: return
        val now = SystemClock.elapsedRealtime()
        val active = synchronized(remotes) { remotes.filter { now - it.value.ts < 10000 }.keys.toList() }
        val newMode = when {
            active.isEmpty() -> 0
            active.all { me < it } -> 1
            else -> 2
        }
        if (newMode != mode) {
            mode = newMode
            Native.command(h, JSONObject().put("cmd", "net_mode").put("mode", mode).toString())
            role.value = when (mode) { 1 -> "Ev sahibi (zombiler senden)"; 2 -> "Katılımcı"; else -> "Tek oyunculu" }
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
        val d = db ?: return
        val me = uid ?: return
        when (e.optString("t")) {
            "zhit" -> if (mode == 2) d.getReference("world/$ROOM/hits").push().setValue(
                hashMapOf("u" to me, "id" to e.optLong("id"), "dmg" to e.optDouble("dmg"), "kx" to e.optDouble("kx"),
                    "ky" to e.optDouble("ky"), "st" to e.optBoolean("stun")))
            "rdmg" -> lastIds.getOrNull(e.optInt("i"))?.let { u ->
                d.getReference("world/$ROOM/pdmg/$u").push().setValue(hashMapOf("v" to e.optInt("v"), "p" to e.optBoolean("poison")))
            }
            "nkill" -> killerIds.getOrNull(e.optInt("k"))?.let { u ->
                d.getReference("world/$ROOM/kills/$u").push().setValue(
                    hashMapOf("kind" to e.optInt("kind"), "level" to e.optInt("level"), "x" to e.optDouble("x"), "y" to e.optDouble("y")))
            }
        }
    }

    /** Uzak oyuncuları ara değerleyerek arabelleğe yazar; sayıyı döndürür (çevrimdışıysa null). */
    fun remotes(out: FloatArray): Int? {
        if (db == null) return null
        val now = SystemClock.elapsedRealtime()
        synchronized(remotes) {
            remotes.values.removeAll { now - it.ts > 15000 }
            var n = 0
            val nm = ArrayList<String>()
            val ids = ArrayList<String>()
            for ((k, r) in remotes) {
                if ((n + 1) * Native.REMOTE_STRIDE > out.size) break
                if (hypot(r.target[0] - myX, r.target[1] - myY) > 3000f) continue
                r.cur[0] += (r.target[0] - r.cur[0]) * 0.25f
                r.cur[1] += (r.target[1] - r.cur[1]) * 0.25f
                for (i in 2 until Native.REMOTE_STRIDE) r.cur[i] = r.target[i]
                r.cur[4] += 1f / 60f
                System.arraycopy(r.cur, 0, out, n * Native.REMOTE_STRIDE, Native.REMOTE_STRIDE)
                nm += r.name
                ids += k
                n++
            }
            lastNames = nm.toTypedArray()
            lastIds = ids.toTypedArray()
            return n
        }
    }

    /** En yakın uzak oyuncu (takas / yolculuk tuşları için). */
    fun nearest(maxD: Float): Near? = synchronized(remotes) {
        remotes.entries.map { (k, r) -> Near(k, r.name, r.cur[0], r.cur[1], r.cur[9].toInt(), hypot(r.cur[0] - myX, r.cur[1] - myY)) }
            .filter { it.dist < maxD }.minByOrNull { it.dist }
    }

    fun position(u: String): FloatArray? = synchronized(remotes) { remotes[u]?.cur?.copyOf() }

    // =========================================================== takas
    private var tradeListener: ValueEventListener? = null
    private var tradeRef: DatabaseReference? = null

    private fun sid(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    fun requestTrade(other: String) {
        val d = db ?: return
        val me = uid ?: return
        d.getReference("trade_req/$other/$me").setValue(hashMapOf("n" to name, "ts" to ServerValue.TIMESTAMP))
        openTrade(other, nearestName(other))
        system("Takas isteği gönderildi")
    }

    private fun nearestName(u: String) = synchronized(remotes) { remotes[u]?.name ?: "Oyuncu" }

    fun acceptTrade() {
        val (u, n) = tradeRequest.value ?: return
        tradeRequest.value = null
        openTrade(u, n)
    }

    fun declineTrade() { tradeRequest.value = null }

    private fun openTrade(other: String, otherName: String) {
        val d = db ?: return
        val me = uid ?: return
        closeTradeListener()
        val s = sid(me, other)
        val ref = d.getReference("trade/$s")
        tradeRef = ref
        ref.child("p/$me/n").setValue(name)
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                fun items(u: String) = snap.child("p/$u/items").children.mapNotNull {
                    val id = it.child("id").getValue(String::class.java) ?: return@mapNotNull null
                    TradeItem(id, (it.child("n").value as? Number)?.toInt() ?: 1)
                }
                val mine = items(me); val theirs = items(other)
                val myOk = snap.child("p/$me/ok").value == true
                val theirOk = snap.child("p/$other/ok").value == true
                val cancelled = snap.child("cancel").value == true
                if (cancelled) { trade.value = null; closeTradeListener(); system("Takas iptal edildi"); return }
                val prev = trade.value
                trade.value = TradeState(s, other, otherName, mine, theirs, myOk, theirOk, prev?.done == true)
                if (myOk && theirOk && prev?.done != true) execute(mine, theirs, ref)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        tradeListener = l
    }

    private fun execute(mine: List<TradeItem>, theirs: List<TradeItem>, ref: DatabaseReference) {
        val t = trade.value ?: return
        trade.value = TradeState(t.sid, t.other, t.otherName, t.mine, t.theirs, true, true, true)
        for (it in mine) cmdQ += JSONObject().put("cmd", "trade_take").put("id", it.id).put("n", it.n)
        for (it in theirs) cmdQ += JSONObject().put("cmd", "trade_give").put("id", it.id).put("n", it.n)
        system("Takas tamamlandı")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ref.removeValue(); trade.value = null; closeTradeListener()
        }, 1500)
        GameSession.save()
    }

    fun setTradeItems(items: List<TradeItem>) {
        val me = uid ?: return
        tradeRef?.child("p/$me/items")?.setValue(items.map { hashMapOf("id" to it.id, "n" to it.n) })
        tradeRef?.child("p/$me/ok")?.setValue(false)
        trade.value?.let { tradeRef?.child("p/${it.other}/ok")?.setValue(false) }
    }

    fun setTradeReady(ok: Boolean) {
        val me = uid ?: return
        tradeRef?.child("p/$me/ok")?.setValue(ok)
    }

    fun cancelTrade() {
        tradeRef?.child("cancel")?.setValue(true)
        trade.value = null
        closeTradeListener()
    }

    private fun closeTradeListener() {
        tradeListener?.let { tradeRef?.removeEventListener(it) }
        tradeListener = null
    }
}
