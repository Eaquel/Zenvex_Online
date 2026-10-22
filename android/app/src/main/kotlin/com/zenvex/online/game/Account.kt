package com.zenvex.online.game

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.zenvex.online.net.Supa
import org.json.JSONArray
import org.json.JSONObject

/** Oyun sunucusu: kendi karakter ilerlemesi ve kendi çevrimiçi dünyası. Şimdilik tek sunucu var. */
data class GameServer(val id: String, val title: String, val hardcore: Boolean = false)

val SERVERS = listOf(GameServer("n1", "Sunucu #1"))

fun serverById(id: String) = SERVERS.firstOrNull { it.id == id } ?: SERVERS[0]

/**
 * Zenvex hesabı: Zenvex ID + hesap anahtarı (Supabase `zx_*` fonksiyonları).
 * Hesap yoksa oyun çevrimdışı/misafir olarak çalışır; ilerleme cihazda kalır.
 */
object Account {
    data class Clan(val id: Long, val name: String, val color: Int, val owner: Boolean, val rank: Int = 0, val json: JSONObject = JSONObject())

    /** -1 = hesap yok (0 yönetici hesabının kimliği) */
    val id = mutableStateOf(-1L)
    val name = mutableStateOf("")
    val clan = mutableStateOf<Clan?>(null)
    val levels = mutableStateMapOf<String, Int>()
    val unread = mutableIntStateOf(0)
    /** sunucuda is_admin: hile panelinden oyunculara posta gönderebilir */
    val admin = mutableStateOf(false)
    val online = mutableStateMapOf<String, Int>()
    val status = mutableStateOf("")
    /** sunucudaki tam profil (avatar, çerçeve, VIP, etiketler, seviye, öldürme, bildirim sayıları) */
    val profile = mutableStateOf(JSONObject())
    /** yasaklıysa: "tarih|neden" */
    val banned = mutableStateOf<String?>(null)
    var key = ""
        private set
    private lateinit var prefs: android.content.SharedPreferences

    val loggedIn: Boolean get() = id.value >= 0 && key.isNotEmpty()

    private var appCtx: Context? = null
    private var notifyOn = false

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        prefs = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)
        key = prefs.getString("key", "") ?: ""
        id.value = if (key.isEmpty()) -1L else prefs.getLong("id", -1L)
        name.value = prefs.getString("name", "") ?: ""
        if (loggedIn) refresh()
    }

    private fun persist() {
        prefs.edit().putLong("id", id.value).putString("key", key).putString("name", name.value).apply()
        // cihaz bildirimleri: giriş yapınca kurulur, çıkışta durur
        val c = appCtx
        if (c != null && loggedIn != notifyOn) {
            notifyOn = loggedIn
            runCatching { if (loggedIn) Notifier.schedule(c) else Notifier.cancel(c) }
        }
    }

    private fun applyProfile(p: JSONObject) {
        id.value = p.optLong("id", id.value)
        name.value = p.optString("name", name.value)
        clan.value = p.optJSONObject("clan")?.let { Clan(it.optLong("id"), it.optString("name"), it.optInt("color"), it.optBoolean("owner"), it.optInt("rank"), it) }
        if (p.has("avatar")) profile.value = p
        p.optJSONObject("chars")?.let { c -> levels.clear(); for (k in c.keys()) levels[k] = c.optInt(k) }
        unread.intValue = p.optInt("unread")
        admin.value = p.optBoolean("admin")
        persist()
    }

    private fun auth() = JSONObject().put("p_id", id.value).put("p_key", key)
    /** Kimlik + anahtar (oturum / takas çağrıları için) */
    fun authJson(): JSONObject = auth()

    /** Sunucu "BAN|tarih|neden" hatası verirse yasak ekranı gösterilir. */
    fun checkBan(msg: String?) {
        if (msg == null) return
        if (msg.contains("BAN|")) banned.value = msg.substringAfter("BAN|")
        else if (msg.contains("Bu hesap silindi")) banned.value = "—|Bu hesap yönetici tarafından silindi"
    }

    private fun obj(r: Result<String>) = r.mapCatching { JSONObject(it) }
    private fun arr(r: Result<String>) = r.mapCatching { JSONArray(it) }
    private fun txt(r: Result<String>) = r.mapCatching { runCatching { JSONArray("[$it]").optString(0) }.getOrDefault(it) }

    // ------------------------------------------------------------------ bakım
    /** Bakım durumu: {"on":bool,"msg":".."} (giriş gerekmez) */
    fun status(done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_status") { r -> done(obj(r).map { it.optJSONObject("maintenance") ?: JSONObject() }) }

    // ------------------------------------------------------------------ profil
    fun setProfile(avatar: Int, frame: String, done: (String?) -> Unit) =
        Supa.rpc("zx_profile_set", auth().put("p_avatar", avatar).put("p_frame", frame)) { r ->
            r.onSuccess { applyProfile(JSONObject(it)) }; done(r.exceptionOrNull()?.message)
        }

    fun viewProfile(name: String, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_profile_view", JSONObject().put("p_name", name)) { r -> done(obj(r)) }

    fun topPlayers(kind: String, done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_top2", JSONObject().put("p_kind", kind)) { r -> done(arr(r)) }

    // ------------------------------------------------------------------ sezon bileti (oturum gerekir)
    private fun sess() = auth().put("p_sid", Session.sid)
    fun passState(done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_pass_state", sess()) { r -> done(obj(r)) }
    fun passBuy(elite: Boolean, done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_pass_buy", sess().put("p_elite", elite)) { r -> done(obj(r)) }
    fun passClaim(tier: Int, premium: Boolean, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_pass_claim", sess().put("p_tier", tier).put("p_premium", premium)) { r -> done(obj(r)) }

    /** Sıralama: kategoride ilk 10 (level, kills, pvp, titan, soldiers, bases, dog, crafted, time) */
    fun board(kind: String, done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_board", JSONObject().put("p_kind", kind)) { r -> done(arr(r)) }

    /** Unvan seç ("" = unvansız); sunucu yalnız o kategoride ilk 10'daysa kabul eder */
    fun setTitle(kind: String, done: (String?) -> Unit) =
        Supa.rpc("zx_title_set", auth().put("p_kind", kind)) { r ->
            r.onSuccess { applyProfile(JSONObject(it)); Online.retrack() }; done(r.exceptionOrNull()?.message)
        }

    /** Cihazdan profil fotoğrafı (96×96 WebP, base64; "" = kaldır) */
    fun setPhoto(b64: String, done: (String?) -> Unit) =
        Supa.rpc("zx_photo_set", auth().put("p_photo", b64)) { r ->
            r.onSuccess { applyProfile(JSONObject(it)) }; done(r.exceptionOrNull()?.message)
        }

    // ------------------------------------------------------------------ arkadaşlar + özel mesaj
    fun friends(done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_friends", auth()) { r -> done(obj(r)) }
    fun friendRequest(name: String, done: (Result<String>) -> Unit) = Supa.rpc("zx_friend_request", auth().put("p_name", name)) { r -> done(txt(r)) }
    fun friendRespond(from: Long, accept: Boolean, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_friend_respond", auth().put("p_from", from).put("p_accept", accept)) { r -> done(txt(r)) }
    fun friendRemove(other: Long, done: (Result<String>) -> Unit) = Supa.rpc("zx_friend_remove", auth().put("p_other", other)) { r -> done(txt(r)) }
    fun dmSend(to: Long, text: String, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_dm_send", auth().put("p_to", to).put("p_text", text)) { r -> done(obj(r)) }
    fun dmList(with: Long, after: Long, done: (Result<JSONArray>) -> Unit) =
        Supa.rpc("zx_dm_list", auth().put("p_with", with).put("p_after", after)) { r -> done(arr(r)) }

    // ------------------------------------------------------------------ klan v2
    fun clanInfo(clanId: Long, done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_clan_info", JSONObject().put("p_clan", clanId)) { r -> done(obj(r)) }
    fun clanInvites(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_clan_invites", auth()) { r -> done(arr(r)) }
    fun clanInvite(name: String, done: (Result<String>) -> Unit) = Supa.rpc("zx_clan_invite", auth().put("p_name", name)) { r -> done(txt(r)) }
    fun clanRank(member: Long, rank: Int, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_clan_set_rank", auth().put("p_member", member).put("p_rank", rank)) { r -> done(txt(r)) }
    fun clanKick(member: Long, done: (Result<String>) -> Unit) = Supa.rpc("zx_clan_kick", auth().put("p_member", member)) { r -> done(txt(r)) }
    fun clanMute(member: Long, minutes: Int, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_clan_mute", auth().put("p_member", member).put("p_minutes", minutes)) { r -> done(txt(r)) }
    fun clanDeposit(item: String, n: Int, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_clan_deposit", auth().put("p_item", item).put("p_n", n)) { r -> done(txt(r)) }
    fun clanSend(member: Long, item: String, n: Int, note: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_clan_send", auth().put("p_member", member).put("p_item", item).put("p_n", n).put("p_note", note)) { r -> done(txt(r)) }
    fun clanSettings(officerSend: Boolean, motto: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_clan_settings", auth().put("p_officer_send", officerSend).put("p_motto", motto)) { r -> done(txt(r)) }
    fun clanBoost(kind: String, done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_clan_boost", auth().put("p_kind", kind)) { r -> done(obj(r)) }
    fun clanRanking(kind: String, done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_clan_ranking", JSONObject().put("p_kind", kind)) { r -> done(arr(r)) }

    // ------------------------------------------------------------------ posta
    fun favoriteMail(mailId: Long, on: Boolean, done: (String?) -> Unit) =
        Supa.rpc("zx_mail_favorite", auth().put("p_mail", mailId).put("p_on", on)) { r -> done(r.exceptionOrNull()?.message) }
    fun removeMail(mailId: Long, done: (String?) -> Unit) = Supa.rpc("zx_mail_remove", auth().put("p_mail", mailId)) { r -> done(r.exceptionOrNull()?.message) }

    fun requestDelete(done: (String?) -> Unit) = Supa.rpc("zx_request_delete", auth()) { r -> done(r.exceptionOrNull()?.message) }

    // ------------------------------------------------------------------ yönetici işlem kuyruğu (oyuncu tarafı)
    fun actionsPull(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_actions_pull", auth()) { r -> done(arr(r)) }
    fun actionsDone(ids: List<Long>) = Supa.rpc("zx_actions_done", auth().put("p_ids", JSONArray(ids))) { }
    /** Yakın ve klan sohbetinin kaydı (yönetici denetimi için) */
    fun chatLog(channel: String, text: String) {
        if (!loggedIn) return
        Supa.bg { Supa.rpcSync("zx_chat_log", auth().put("p_channel", channel).put("p_text", text)) }
    }

    /** V8 devlet üssü ele geçirme: sunucu güncel üs listesini döndürür */
    fun baseCapture(server: String, base: Int, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_base_capture", auth().put("p_server", server).put("p_base", base)) { r -> done(r) }

    // ------------------------------------------------------------------ yönetici
    fun adminPlayer(target: Long, done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_admin_player", auth().put("p_target", target)) { r -> done(obj(r)) }
    fun adminAction(target: Long, action: JSONObject, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_action", auth().put("p_target", target).put("p_action", action)) { r -> done(txt(r)) }
    fun adminMaintenance(on: Boolean, msg: String, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_admin_maintenance", auth().put("p_on", on).put("p_msg", msg)) { r -> done(obj(r)) }
    fun adminFind(name: String, done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_admin_find", auth().put("p_name", name)) { r -> done(arr(r)) }
    fun adminBan(target: Long, hours: Int, reason: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_ban", auth().put("p_target", target).put("p_hours", hours).put("p_reason", reason)) { r -> done(txt(r)) }
    fun adminDelete(target: Long, reason: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_delete", auth().put("p_target", target).put("p_reason", reason)) { r -> done(txt(r)) }
    fun adminRestore(target: Long, done: (Result<String>) -> Unit) = Supa.rpc("zx_admin_restore", auth().put("p_target", target)) { r -> done(txt(r)) }
    /** yeni anahtarı döndürür (oyuncuya iletilir) */
    fun adminResetKey(target: Long, done: (Result<String>) -> Unit) = Supa.rpc("zx_admin_reset_key", auth().put("p_target", target)) { r -> done(txt(r)) }
    fun adminDeleteRequests(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_admin_delete_requests", auth()) { r -> done(arr(r)) }
    fun adminFlags(target: Long, done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_admin_flags", auth().put("p_target", target)) { r -> done(obj(r)) }
    fun adminBanDevice(device: String, on: Boolean, reason: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_ban_device", auth().put("p_device", device).put("p_on", on).put("p_reason", reason)) { r -> done(txt(r)) }
    // ------------------------------------------------------------------ etkinlikler
    fun eventCurrent(done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_event_current") { r -> done(obj(r)) }
    fun eventBoard(done: (Result<JSONObject>) -> Unit) = Supa.rpc("zx_event_board", auth()) { r -> done(obj(r)) }
    /** Tamirci Viktor: araç siparişi (0 motosiklet, 1 araba) ve bekleyen siparişler */
    fun vehicleOrder(kind: Int, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_vehicle_order", auth().put("p_sid", Session.sid).put("p_kind", kind)) { r -> done(obj(r)) }
    fun vehicleOrders(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_vehicle_orders", auth()) { r -> done(arr(r)) }
    fun adminEventStart(kind: String, done: (Result<String>) -> Unit) = Supa.rpc("zx_admin_event_start", auth().put("p_kind", kind)) { r -> done(txt(r)) }
    fun adminEventStop(done: (Result<String>) -> Unit) = Supa.rpc("zx_admin_event_stop", auth()) { r -> done(txt(r)) }
    fun adminVip(target: Long, days: Int, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_vip", auth().put("p_target", target).put("p_days", days)) { r -> done(txt(r)) }
    fun adminMail2(to: String, title: String, body: String, gold: Int, gems: Int, xp: Int, items: JSONArray, kind: String, done: (Result<Int>) -> Unit) =
        Supa.rpc("zx_admin_mail2", auth().put("p_to", to).put("p_title", title).put("p_body", body).put("p_gold", gold).put("p_gems", gems)
            .put("p_xp", xp).put("p_items", items).put("p_kind", kind)) { r -> done(obj(r).map { it.optInt("sent") }) }

    fun refresh(done: (String?) -> Unit = {}) {
        if (!loggedIn) { done("Hesap yok"); return }
        status.value = "Bağlanıyor…"
        Supa.rpc("zx_login", auth()) { r ->
            r.onSuccess { applyProfile(JSONObject(it)); status.value = "Çevrimiçi"; banned.value = null; done(null) }
                .onFailure { e -> checkBan(e.message); status.value = e.message ?: "Hata"; done(e.message) }
        }
        servers()
    }

    fun servers() = Supa.rpc("zx_servers") { r ->
        r.onSuccess { s -> runCatching { JSONObject(s) }.getOrNull()?.let { o -> online.clear(); for (k in o.keys()) online[k] = o.optInt(k) } }
    }

    fun register(nick: String, done: (String?) -> Unit) {
        Supa.rpc("zx_register", JSONObject().put("p_name", nick.trim())) { r ->
            r.onSuccess {
                val o = JSONObject(it)
                key = o.optString("key")
                applyProfile(o)
                status.value = "Çevrimiçi"
                done(null)
            }.onFailure { done(it.message) }
        }
    }

    fun login(newId: Long, newKey: String, done: (String?) -> Unit) {
        Supa.rpc("zx_login", JSONObject().put("p_id", newId).put("p_key", newKey.trim().uppercase())) { r ->
            r.onSuccess {
                key = newKey.trim().uppercase()
                applyProfile(JSONObject(it))
                status.value = "Çevrimiçi"
                done(null)
            }.onFailure { done(it.message) }
        }
    }

    /**
     * Google ile giriş (link = false) ya da açık hesabı Google'a bağlama (link = true).
     * Google ID jetonu sunucuda (zx-google edge function) doğrulanır; girişte yeni anahtar döner ve saklanır.
     */
    fun google(idToken: String, link: Boolean, done: (String?) -> Unit) {
        val body = JSONObject().put("id_token", idToken).put("action", if (link) "link" else "login")
        if (link) body.put("id", id.value).put("key", key)
        Supa.fn("zx-google", body) { r ->
            r.onSuccess {
                val o = JSONObject(it)
                if (!link) key = o.optString("key")
                applyProfile(o)
                status.value = "Çevrimiçi"
                done(null)
            }.onFailure { done(it.message) }
        }
    }

    fun changeKey(done: (String?) -> Unit) = Supa.rpc("zx_change_key", auth()) { r ->
        r.onSuccess { key = JSONObject(it).optString("key"); persist(); done(null) }.onFailure { done(it.message) }
    }

    fun rename(nick: String, done: (String?) -> Unit) = Supa.rpc("zx_rename", auth().put("p_name", nick.trim())) { r ->
        r.onSuccess { applyProfile(JSONObject(it)); done(null) }.onFailure { done(it.message) }
    }

    fun logout() {
        id.value = -1L; key = ""; name.value = ""; clan.value = null; levels.clear(); unread.intValue = 0
        persist()
    }

    // ------------------------------------------------------------------ karakter kayıtları
    /** Bulut kaydı (arka plan iş parçacığında çağrılmalı). Hesap yoksa null. */
    fun loadCloudSync(server: String): String? {
        if (!loggedIn) return null
        val s = runCatching { Supa.rpcSync("zx_load", auth().put("p_server", server)) }.getOrNull() ?: return null
        // PostgREST metin döndürür: "\"...\"" ya da null
        return runCatching { JSONArray("[$s]").optString(0, "") }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
    }

    /** Kayıt + öldürme ve toplam XP (klan katkısı ve sıralamalar sunucuda bundan hesaplanır). */
    fun saveCloud(server: String, level: Int, data: String, kills: Int = 0, xpTotal: Long = 0) {
        if (!loggedIn) return
        levels[server] = level
        Supa.bg {
            runCatching { Supa.rpcSync("zx_save2", auth().put("p_server", server).put("p_level", level).put("p_data", data).put("p_kills", kills).put("p_xp", xpTotal)) }
                .onFailure { checkBan(it.message) }
        }
    }

    // ------------------------------------------------------------------ posta
    fun mail(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_mail_list", auth()) { r ->
        done(r.map { JSONArray(it) }); if (r.isSuccess) unread.intValue = 0
    }

    fun claimMail(mailId: Long, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_mail_claim", auth().put("p_mail", mailId)) { r -> done(r.map { JSONObject(it) }) }

    /** Yönetici postası: to = oyuncu adı ya da "*" (herkes). items = [{"id":..,"n":..}] */
    fun adminMail(to: String, title: String, body: String, gold: Int, xp: Int, items: JSONArray, done: (Result<Int>) -> Unit) =
        Supa.rpc("zx_admin_mail", auth().put("p_to", to).put("p_title", title).put("p_body", body).put("p_gold", gold).put("p_xp", xp).put("p_items", items)) { r ->
            done(r.map { JSONObject(it).optInt("sent") })
        }

    // ------------------------------------------------------------------ klan
    fun clans(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_clans") { r -> done(r.map { JSONArray(it) }) }

    fun clanMembers(clanId: Long, done: (Result<JSONArray>) -> Unit) =
        Supa.rpc("zx_clan_members", JSONObject().put("p_clan", clanId)) { r -> done(r.map { JSONArray(it) }) }

    fun createClan(clanName: String, color: Int, done: (String?) -> Unit) =
        Supa.rpc("zx_clan_create", auth().put("p_name", clanName).put("p_color", color)) { r ->
            r.onSuccess { applyProfile(JSONObject(it)) }; done(r.exceptionOrNull()?.message)
        }

    fun joinClan(clanId: Long, done: (String?) -> Unit) = Supa.rpc("zx_clan_join", auth().put("p_clan", clanId)) { r ->
        r.onSuccess { applyProfile(JSONObject(it)) }; done(r.exceptionOrNull()?.message)
    }

    fun leaveClan(done: (String?) -> Unit) = Supa.rpc("zx_clan_leave", auth()) { r ->
        r.onSuccess { applyProfile(JSONObject(it)) }; done(r.exceptionOrNull()?.message)
    }

    // ------------------------------------------------------------------ sıralama / sohbet geçmişi
    fun top(server: String, done: (Result<JSONArray>) -> Unit) =
        Supa.rpc("zx_top", JSONObject().put("p_server", server)) { r -> done(r.map { JSONArray(it) }) }

    fun postChat(server: String, text: String) {
        if (!loggedIn) return
        Supa.bg { Supa.rpcSync("zx_chat_post", auth().put("p_server", server).put("p_text", text)) }
    }

    fun recentChat(server: String, done: (Result<JSONArray>) -> Unit) =
        Supa.rpc("zx_chat_recent", JSONObject().put("p_server", server)) { r -> done(r.map { JSONArray(it) }) }
}
