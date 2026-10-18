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
import com.zenvex.online.BuildConfig
import kotlin.math.hypot

/**
 * Geçici çevrimiçi altyapı: Firebase Realtime Database.
 *  /presence/<oda>/<uid>  : konum + görünüm (8 Hz), bağlantı kopunca silinir
 *  /chat/global           : herkese açık sohbet
 *  /chat/local            : konumlu sohbet (1500 px içindekiler görür)
 * Yapılandırma (BuildConfig.FB_*) boşsa çevrimdışı çalışır; oyun bundan etkilenmez.
 */
object Online {
    data class ChatLine(val channel: String, val name: String, val text: String, val tag: String, val mine: Boolean, val ts: Long)

    val status = mutableStateOf("Çevrimdışı")
    val online = mutableStateOf(false)
    val playerCount = mutableStateOf(0)
    val chat = mutableStateListOf<ChatLine>()
    @Volatile var onChat: ((ChatLine) -> Unit)? = null

    private var db: FirebaseDatabase? = null
    private var uid: String? = null
    private var presenceRef: DatabaseReference? = null
    private var room = "dunya1"
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
    private var myX = 0f
    private var myY = 0f

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

    private fun listen() {
        val d = db ?: return
        val me = uid ?: return
        presenceRef = d.getReference("presence/$room/$me").also { it.onDisconnect().removeValue() }
        d.getReference("presence/$room").addChildEventListener(object : ChildEventListener {
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
        for (ch in listOf("global", "local")) {
            d.getReference("chat/$ch").limitToLast(30).addChildEventListener(object : ChildEventListener {
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
                override fun onChildChanged(s: DataSnapshot, p: String?) {}
                override fun onChildRemoved(s: DataSnapshot) {}
                override fun onChildMoved(s: DataSnapshot, p: String?) {}
                override fun onCancelled(e: DatabaseError) {}
            })
        }
    }

    /** Sohbete mesaj gönder; çevrimdışıyken sadece yerel olarak gösterilir. */
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

    /** Oyun döngüsünden (kilit altında) çağrılır: yerel durumu 8 Hz yayınlar. */
    fun publishLocal(h: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPub < 125) return
        lastPub = now
        Native.localState(h, local)
        myX = local[0]; myY = local[1]
        val ref = presenceRef ?: return
        val list = local.map { (it * 10).toInt() / 10.0 }
        ref.setValue(hashMapOf("n" to name, "s" to list, "ts" to ServerValue.TIMESTAMP))
    }

    /** remotes() ile aynı sırada isimler */
    @Volatile var lastNames: Array<String> = emptyArray()

    /** Uzak oyuncuları ara değerleyerek arabelleğe yazar; sayıyı döndürür (çevrimdışıysa null). */
    fun remotes(out: FloatArray): Int? {
        if (db == null) return null
        val now = SystemClock.elapsedRealtime()
        synchronized(remotes) {
            remotes.values.removeAll { now - it.ts > 15000 }
            var n = 0
            val nm = ArrayList<String>()
            for (r in remotes.values) {
                if ((n + 1) * Native.REMOTE_STRIDE > out.size) break
                if (hypot(r.target[0] - myX, r.target[1] - myY) > 3000f) continue
                // konumu yumuşak takip et, diğerlerini doğrudan al
                r.cur[0] += (r.target[0] - r.cur[0]) * 0.25f
                r.cur[1] += (r.target[1] - r.cur[1]) * 0.25f
                for (i in 2 until Native.REMOTE_STRIDE) r.cur[i] = r.target[i]
                r.cur[4] += 1f / 60f
                System.arraycopy(r.cur, 0, out, n * Native.REMOTE_STRIDE, Native.REMOTE_STRIDE)
                nm += r.name
                n++
            }
            lastNames = nm.toTypedArray()
            return n
        }
    }
}
