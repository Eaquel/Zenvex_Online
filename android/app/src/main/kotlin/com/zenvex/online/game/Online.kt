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
 * Çevrimiçi dünya: Supabase Realtime, "ev sahibi" modeli. İki kanal: sunucu kanalı (`zx-<sunucu>`: presence, sohbet,
 * fısıltı, takas) ve bulunulan bölgenin kanalı (`zx-<sunucu>-a<bölge>`: konum, zombi, vuruş, PvP).
 *  presence  isim / klan (kim bağlı)
 *  p         konum + görünüm (hareket ederken 3,3 Hz, dururken 2 sn'de bir; yalnız aynı bölge)
 *  z         ev sahibinin zombi anlık görüntüsü (2,5 Hz)
 *  hit       istemcinin zombi vuruşu → ev sahibi uygular
 *  kill      öldürme ödülü → vuran oyuncu
 *  pd        zombi hasarı → hedef oyuncu
 *  c         sohbet (genel / yakın); genel sohbet ayrıca veritabanına yazılır (geçmiş)
 *  treq, tr  takas isteği ve takas teklifleri
 *  pvp       oyuncu vuruşu → hedef (hasarı hedefin cihazı, PvP bölgesindeyse uygular)
 *  pvpk      öldürme bildirimi → öldüren
 * Ev sahibi: bölgedeki oyuncular arasında en küçük kimlik. O ayrılırsa sıradaki devralır.
 */
object Online {
    /** [avatar]/[frame]: gönderenin profil resmi ve çerçevesi (sohbette yanında çizilir; -1: bilinmiyor) */
    /** [uid]: gönderenin oyun kimliği ("a<hesap>"); sohbette adına dokununca oyuncu menüsü açılır */
    data class ChatLine(val channel: String, val name: String, val text: String, val tag: String, val mine: Boolean, val ts: Long,
                        val avatar: Int = -1, val frame: String = "", val uid: String = "")

    private fun myAvatar() = Account.profile.value.optInt("avatar", 1)
    private fun myFrame() = Account.profile.value.optString("frame").takeIf { it != "null" } ?: ""
    data class Near(val uid: String, val name: String, val x: Float, val y: Float, val vehicle: Int, val dist: Float)
    /** Teklifteki eşya: tekil ekipmanda çantadaki yuvası ve dayanıklılığı (binde) da taşınır */
    data class TradeItem(val id: String, val n: Int, val slot: Int = -1, val dur: Int = 1000)
    data class TradeState(
        val sid: String, val other: String, val otherName: String,
        val mine: List<TradeItem>, val theirs: List<TradeItem>, val myOk: Boolean, val theirOk: Boolean, val done: Boolean,
        /** sunucudaki emanetli takas numarası (0 = henüz açılmadı) */
        val tid: Long = 0,
        /** karşı teklifin son değiştiği an (ms): değişince onay tuşu 3 sn kilitlenir */
        val theirsAt: Long = 0,
        /** ben hazırken karşı taraf teklifini değiştirdi: takas tamamlanamaz, iptal edip yeniden başlatılmalı */
        val changedAfterReady: Boolean = false,
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
    private var channel: com.zenvex.online.net.Link? = null
    /** kendi oyun sunucumuz kullanılıyorsa (Account.gameServer dolu ve oturum açık): tek bağlantı, oda = bölge */
    private var gameNet: com.zenvex.online.net.GameNet? = null
    /** oyun sunucusunun bu bölge için seçtiği ev sahibi */
    @Volatile private var hostUid = ""
    /**
     * Bölge kanalı (`zx-<sunucu>-a<bölge>`): konum, zombi ve vuruş yalnız aynı bölgedekilere gider. Ana kanal sohbet,
     * kimlik (presence), takas ve fısıltı için. Supabase her yayını alıcı sayısı kadar sayar; bölgelere bölmek ve
     * seyrek göndermek (duran oyuncu 2 sn'de bir) mesaj sayısını ~10 kat düşürür.
     */
    private var areaCh: com.zenvex.online.net.Link? = null
    /** konum / zombi / vuruş bağlantısı: oyun sunucusu ya da Supabase bölge kanalı */
    private val areaLink: com.zenvex.online.net.Link? get() = gameNet ?: areaCh
    private var areaIdx = -1
    private var areaList: List<com.zenvex.online.ui.MapArea>? = null
    private val lastSent = FloatArray(Native.REMOTE_STRIDE)
    private var lastSentAt = 0L
    private var serverId = ""
    @Volatile var uid: String = ""
        private set
    private var name = "Oyuncu"
    private var tag = ""

    private class Remote(var name: String, var clan: String = "", var title: String = "", var titleRank: Int = 0, var skin: String = "") {
        val cur = FloatArray(Native.REMOTE_STRIDE)
        val target = FloatArray(Native.REMOTE_STRIDE)
        var ts = 0L
        /** iki yayın arası hız (px/sn): yayınlar arasında konum ileri tahmin edilir */
        var vx = 0f
        var vy = 0f
        var present = true
    }
    private val remotes = HashMap<String, Remote>()
    private var lastRemotes = 0L
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
    /** Görünen oyuncuların seçili unvanı ("Canavar Avcısı #1") ve sırası (isim etiketinin üstünde, ilk 3 efektli) */
    @Volatile var lastTitles: Array<Pair<String, Int>> = emptyArray()
        private set
    /** görünen oyuncuların karakter kaplaması (Elmas Sandığı) */
    @Volatile var lastSkins: Array<String> = emptyArray()
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
                    chat += ChatLine("global", o.optString("n"), o.optString("m"), (if (o.isNull("g")) "" else o.optString("g")), "a${o.optLong("u")}" == uid, o.optLong("ts"),
                        o.optInt("av", -1), o.optString("fr").takeIf { it != "null" } ?: "", "a${o.optLong("u")}")
                }
            }
        }
        val onStatus = { s: String -> main.post { status.value = s; online.value = s == "Çevrimiçi" }; Unit }
        val gs = Account.gameServer
        channel = if (gs.isNotEmpty() && Account.loggedIn && Session.sid.isNotEmpty()) {
            com.zenvex.online.net.GameNet(gs, { Session.netHello(sid, myMeta()) }, ::onBroadcast, ::onPresence, onStatus) { h -> hostUid = h }
                .also { gameNet = it; it.open() }
        } else Supa.Channel(
            topic = "realtime:zx-$sid",
            presenceKey = uid,
            onBroadcast = ::onBroadcast,
            onPresence = ::onPresence,
            onStatus = onStatus,
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
        gameNet = null
        hostUid = ""
        areaCh?.close()
        areaCh = null
        areaIdx = -1
        synchronized(remotes) { remotes.clear() }
        online.value = false
        status.value = "Çevrimdışı"
        playerCount.intValue = 1
        trade.value = null
        tradeRequest.value = null
        mode = 0
    }

    /** Uygulama öne geldi: kopmuş kanal hemen yeniden bağlansın */
    fun onForeground() { channel?.reconnectNow(); areaCh?.reconnectNow() }

    private fun switchArea(a: Int) {
        areaCh?.close()
        areaIdx = a
        // isim/klan (ana kanaldaki presence) kalır; konumu bilinmeyenler yeni bölgeden yayın gelene dek çizilmez
        synchronized(remotes) { remotes.values.forEach { it.ts = 0L; it.vx = 0f; it.vy = 0f } }
        pendingSnap = null
        gameNet?.let { hostUid = ""; it.area(a); areaCh = null; return }
        areaCh = if (a < 0 || channel == null) null else Supa.Channel(
            topic = "realtime:zx-$serverId-a$a",
            presenceKey = uid,
            onBroadcast = ::onBroadcast,
            onPresence = { _, _, _ -> },
            onStatus = { },
        ).also { it.open() }
    }

    fun setIdentity(playerName: String, playerTag: String) { name = playerName; tag = playerTag; trackMe() }
    /** İsim değişti: sonraki konum paketiyle (≤0,2 sn) diğer oyuncularda da yenilenir */
    fun rename(playerName: String) { name = playerName; trackMe() }
    /** Sunucuya gidiş-dönüş süresi (ms); bağlı değilse -1 */
    fun ping(): Int = channel?.takeIf { it.joined }?.pingMs ?: -1

    /** Unvan değişince yayındaki kimliği yenile */
    fun retrack() = trackMe()

    private fun myMeta(): JSONObject {
        val t = Account.profile.value.optJSONObject("title")
        return JSONObject().put("n", name).put("g", Account.clan.value?.name ?: tag)
            .put("tt", t?.optString("name") ?: "").put("tr", t?.optInt("rank") ?: 0).put("sk", Skins.char)
    }

    private fun trackMe() { channel?.track(myMeta()) }

    // =========================================================== gelen
    private fun onPresence(joins: JSONObject, leaves: JSONObject, full: Boolean) {
        synchronized(remotes) {
            if (full) remotes.values.forEach { it.present = false }
            for (k in joins.keys()) {
                if (k == uid) continue
                val meta = joins.optJSONObject(k)?.optJSONArray("metas")?.optJSONObject(0) ?: continue
                val r = remotes.getOrPut(k) { Remote("") }
                r.name = meta.optString("n", "Oyuncu"); r.clan = if (meta.isNull("g")) "" else meta.optString("g"); r.present = true
                r.title = meta.optString("tt"); r.titleRank = meta.optInt("tr"); r.skin = meta.optString("sk")
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
                    p.optString("n").takeIf { it.isNotEmpty() }?.let { r.name = it }
                    val now = SystemClock.elapsedRealtime()
                    val ox = r.target[0]; val oy = r.target[1]
                    for (i in 0 until minOf(arr.length(), Native.REMOTE_STRIDE)) r.target[i] = arr.optDouble(i).toFloat()
                    val gap = (now - r.ts) / 1000f
                    if (r.ts > 0L && gap in 0.05f..1f) {
                        r.vx = (r.target[0] - ox) / gap; r.vy = (r.target[1] - oy) / gap
                        if (hypot(r.vx, r.vy) > 1500f) { r.vx = 0f; r.vy = 0f }  // ışınlanma / seyahat
                    } else { r.vx = 0f; r.vy = 0f }
                    if (r.ts == 0L) System.arraycopy(r.target, 0, r.cur, 0, Native.REMOTE_STRIDE)
                    r.ts = now
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
            "pvp" -> if (p.optString("to") == me) {
                lastPvpFrom = p.optString("u"); lastPvpName = p.optString("n", "Oyuncu"); lastPvpAt = SystemClock.elapsedRealtime()
                cmdQ += JSONObject().put("cmd", "pvp_dmg").put("v", p.optDouble("v").coerceIn(0.0, 400.0))
            }
            "pvpk" -> if (p.optString("to") == me) {
                cmdQ += JSONObject().put("cmd", "pvp_kill")
                main.post { system("${p.optString("n", "Oyuncu")} oyuncusunu öldürdün!") }
            }
            "c" -> {
                val ch = p.optString("ch", "global")
                if (ch != "global" && ch != "clan") return
                // klan sohbeti yalnız aynı klanın üyelerine
                if (ch == "clan" && (p.optLong("cid") != (Account.clan.value?.id ?: -1L))) return
                val g = p.optString("g").takeUnless { it == "null" } ?: ""
                addLine(ChatLine(ch, p.optString("n", "?"), p.optString("m").take(140), g, false, System.currentTimeMillis(),
                    p.optInt("av", -1), p.optString("fr"), p.optString("u")))
            }
            // fısıltı: yalnız alıcı görür (Genel sekmesinde [Fısıltı] etiketiyle)
            "w" -> if (p.optString("to") == me) addLine(ChatLine("global", p.optString("n", "?"), p.optString("m").take(140), "Fısıltı", false, System.currentTimeMillis(),
                p.optInt("av", -1), p.optString("fr"), p.optString("u")))
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
        addLine(ChatLine(channelName, name, t, g, true, System.currentTimeMillis(), myAvatar(), myFrame(), uid))
        val c = channel ?: return null
        val m = JSONObject().put("u", uid).put("n", name).put("g", g).put("m", t).put("ch", channelName).put("av", myAvatar()).put("fr", myFrame())
        if (channelName == "clan") m.put("cid", Account.clan.value?.id ?: 0)
        c.send("c", m)
        if (channelName == "global") Account.postChat(serverId, t)
        else if (channelName == "clan") Account.chatLog("clan:${Account.clan.value?.id ?: 0}", t)
        return null
    }

    /** Fısıltı: yalnız [toUid] oyuncusuna giden kısa mesaj (sohbet kuralları ve bekleme süresi geçerli). */
    fun whisper(toUid: String, toName: String, text: String): String? {
        val t = text.trim().take(140)
        if (t.isEmpty()) return null
        if (chatLocked()) return "Sohbet $CHAT_LEVEL. Seviyede Açılır"
        val w = chatWait()
        if (w > 0) return "${(w + 999) / 1000} Sn Bekle"
        val c = channel ?: return "Çevrimdışısın"
        lastSend = SystemClock.elapsedRealtime()
        addLine(ChatLine("global", "→ $toName", t, "Fısıltı", true, System.currentTimeMillis(), myAvatar(), myFrame(), uid))
        c.send("w", JSONObject().put("to", toUid).put("u", uid).put("n", name).put("m", t).put("av", myAvatar()).put("fr", myFrame()))
        Account.chatLog("$serverId:whisper:$toUid", t)
        return null
    }

    fun system(text: String) = addLine(ChatLine("system", "Sistem", text, "", false, System.currentTimeMillis()))

    // =========================================================== oyun döngüsü (GameSession.lock altında)
    /** Yerel durumu yayınlar, ev sahibini seçer, kuyruktaki ağ olaylarını uygular. */
    fun tick(h: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPub >= 100) {
            lastPub = now
            Native.localState(h, local)
            myX = local[0]; myY = local[1]
            if (channel != null) {
                val areas = areaList ?: com.zenvex.online.ui.mapAreas(GameSession.world()).also { areaList = it }
                val a = com.zenvex.online.ui.areaIndexAt(areas, myX, myY)
                if (a != areaIdx) switchArea(a)
            }
            // hareket ediyorsan 3,3 kez/sn, duruyorsan 2 sn'de bir (yalnız bölge kanalına)
            var moved = false
            for (i in local.indices) if (kotlin.math.abs(local[i] - lastSent[i]) > (if (i < 2) 4f else 0.01f)) { moved = true; break }
            val ac = areaLink
            if (ac != null && ac.joined && ((moved && now - lastSentAt >= 300) || now - lastSentAt >= 2000)) {
                lastSentAt = now
                System.arraycopy(local, 0, lastSent, 0, local.size)
                val s = JSONArray()
                for (v in local) s.put((v * 10).toInt() / 10.0)
                ac.send("p", JSONObject().put("u", uid).put("n", name).put("s", s))
            }
            elect(h)
        }
        val c = areaLink
        if (c == null) return
        when (mode) {
            1 -> {
                while (true) {
                    val o = hitQ.poll() ?: break
                    Native.command(h, JSONObject().put("cmd", "net_hit").put("id", o.optLong("id")).put("dmg", o.optDouble("dmg"))
                        .put("kx", o.optDouble("kx")).put("ky", o.optDouble("ky")).put("stun", o.optBoolean("st"))
                        .put("killer", killerIndex(o.optString("u"))).toString())
                }
                if (now - lastSnap >= 400) {
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
            // oyun sunucusu ev sahibini kendisi seçer
            gameNet != null -> if (hostUid == uid) 1 else if (hostUid.isNotEmpty()) 2 else 0
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
        val c = areaLink ?: return
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
        val dt = ((now - lastRemotes) / 1000f).coerceIn(0f, 0.1f)
        lastRemotes = now
        val kf = 1f - kotlin.math.exp(-14f * dt)
        synchronized(remotes) {
            var n = 0
            val nm = ArrayList<String>()
            val cl = ArrayList<String>()
            val tl = ArrayList<Pair<String, Int>>()
            val sk = ArrayList<String>()
            val ids = ArrayList<String>()
            for ((k, r) in remotes) {
                if (r.ts == 0L || now - r.ts > 10000) continue
                if ((n + 1) * Native.REMOTE_STRIDE > out.size) break
                if (hypot(r.target[0] - myX, r.target[1] - myY) > 3000f) continue
                // son yayından hızla ileri tahmin (en çok 0.35 sn), oraya kare hızından bağımsız yumuşak takip
                val ahead = ((now - r.ts) / 1000f).coerceIn(0f, 0.35f)
                val tx = r.target[0] + r.vx * ahead; val ty = r.target[1] + r.vy * ahead
                if (hypot(tx - r.cur[0], ty - r.cur[1]) > 300f) { r.cur[0] = tx; r.cur[1] = ty }
                r.cur[0] += (tx - r.cur[0]) * kf
                r.cur[1] += (ty - r.cur[1]) * kf
                for (i in 2 until Native.REMOTE_STRIDE) r.cur[i] = r.target[i]
                r.cur[4] += 1f / 60f
                System.arraycopy(r.cur, 0, out, n * Native.REMOTE_STRIDE, Native.REMOTE_STRIDE)
                nm += r.name
                cl += r.clan
                tl += r.title to r.titleRank
                sk += r.skin
                ids += k
                n++
            }
            lastNames = nm.toTypedArray()
            lastClans = cl.toTypedArray()
            lastTitles = tl.toTypedArray()
            lastSkins = sk.toTypedArray()
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

    /** Uzak oyuncunun son bildirdiği konum (son 10 sn içinde yayın geldiyse; uzaktaysa da) */
    fun position(u: String): FloatArray? = synchronized(remotes) {
        remotes[u]?.takeIf { it.ts > 0L && SystemClock.elapsedRealtime() - it.ts < 10000 }?.target?.copyOf()
    }

    // =========================================================== PvP
    @Volatile private var lastPvpFrom = ""
    @Volatile private var lastPvpName = ""
    @Volatile private var lastPvpAt = 0L

    /** Yerel vuruş uzak oyuncuya (Rust "pvp" olayı, k = uzak oyuncu sırası). */
    fun sendPvp(k: Int, v: Float, crit: Boolean) {
        val to = lastIds.getOrNull(k) ?: return
        areaLink?.send("pvp", JSONObject().put("to", to).put("u", uid).put("n", name).put("v", v.toDouble()).put("c", crit))
    }

    /** Yerel oyuncu öldü: son 8 sn içinde bir oyuncu vurduysa öldürme ona yazılır. */
    fun onLocalDeath() {
        if (lastPvpFrom.isEmpty() || SystemClock.elapsedRealtime() - lastPvpAt > 8000) return
        areaLink?.send("pvpk", JSONObject().put("to", lastPvpFrom).put("u", uid).put("n", name))
        system("$lastPvpName seni öldürdü")
        lastPvpFrom = ""
    }

    /** Uzak oyuncular düşman mı (klan arkadaşı değilse). */
    fun hostileFlags(myClan: String): JSONArray {
        val a = JSONArray()
        for (c in lastClans) a.put(myClan.isEmpty() || c.isEmpty() || c != myClan)
        return a
    }

    /** Ele geçirme halkasında sahip klandan biri var mı (klan savaşı: savunucu varken ele geçirme durur). */
    fun defenderInRing(owner: String, bx: Float, by: Float, r: Float): Boolean = synchronized(remotes) {
        remotes.values.any { it.clan == owner && it.present && hypot(it.cur[0] - bx, it.cur[1] - by) < r && it.cur[9] < 1f }
    }

    // =========================================================== takas
    // Ekrandaki teklifler kanal üzerinden eşlenir; eşyalar yalnız sunucu emanetiyle el değiştirir (Trade.kt).
    private var myItems: List<TradeItem> = emptyList()
    private var myOk = false

    private fun sid(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    fun requestTrade(other: String) {
        val c = channel ?: return
        if (Session.state.value != Session.State.Live) { system("Takas için çevrimiçi olmalısın"); return }
        c.send("treq", JSONObject().put("to", other).put("u", uid).put("n", name))
        openTrade(other, synchronized(remotes) { remotes[other]?.name ?: "Oyuncu" })
        system("Takas isteği gönderildi")
    }

    /** Kabul eden taraf takası sunucuda açar ve numarayı karşıya yollar. */
    fun acceptTrade() {
        val (u, n) = tradeRequest.value ?: return
        tradeRequest.value = null
        openTrade(u, n)
        Trade.open(u) { tid ->
            trade.value = trade.value?.copy(tid = tid)
            sendOffer()
        }
    }

    fun declineTrade() { tradeRequest.value = null }

    private fun openTrade(other: String, otherName: String) {
        myItems = emptyList(); myOk = false
        trade.value = TradeState(sid(uid, other), other, otherName, emptyList(), emptyList(), false, false, false)
    }

    private fun sendOffer(cancel: Boolean = false) {
        val t = trade.value ?: return
        val items = JSONArray()
        for (it in myItems) items.put(JSONObject().put("id", it.id).put("n", it.n).put("dur", it.dur))
        channel?.send("tr", JSONObject().put("to", t.other).put("u", uid).put("sid", t.sid).put("tid", t.tid).put("items", items)
            .put("ok", myOk).put("cancel", cancel))
    }

    private fun onTradeMsg(p: JSONObject) {
        val t = trade.value ?: return
        if (p.optString("sid") != t.sid) return
        if (p.optBoolean("cancel")) { Trade.cancel(t.tid); trade.value = null; system("Takas iptal edildi"); return }
        val arr = p.optJSONArray("items") ?: JSONArray()
        val theirs = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> TradeItem(o.optString("id"), o.optInt("n", 1), dur = o.optInt("dur", 1000)) } }
        val tid = if (t.tid == 0L) p.optLong("tid") else t.tid
        val changed = theirs != t.theirs
        trade.value = t.copy(mine = myItems, theirs = theirs, myOk = myOk, theirOk = p.optBoolean("ok"), tid = tid,
            theirsAt = if (changed) System.currentTimeMillis() else t.theirsAt,
            changedAfterReady = t.changedAfterReady || (changed && myOk))
    }

    /** Sunucu takası bitirdi (Trade.apply). */
    fun tradeFinished(tid: Long) {
        val t = trade.value ?: return
        if (t.tid != tid) return
        trade.value = t.copy(done = true)
        system("Takas tamamlandı")
        main.postDelayed({ if (trade.value?.tid == tid) trade.value = null }, 1500)
    }

    fun setTradeItems(items: List<TradeItem>) {
        if (myOk) return // emanete alınan teklif değişmez
        myItems = items
        trade.value = trade.value?.copy(mine = items, myOk = false)
        sendOffer()
    }

    /** "Hazırım": teklif sunucu emanetine girer; geri alınamaz (yalnız iptal). */
    fun setTradeReady(ok: Boolean) {
        val t = trade.value ?: return
        if (!ok || myOk) return
        Trade.ready(t.tid, myItems, t.theirs) { accepted ->
            if (!accepted) return@ready
            myOk = true
            trade.value = trade.value?.copy(myOk = true)
            sendOffer()
        }
    }

    fun cancelTrade() {
        val t = trade.value
        sendOffer(cancel = true)
        if (t != null) Trade.cancel(t.tid)
        trade.value = null
        myOk = false
    }
}
