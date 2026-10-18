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
    data class Clan(val id: Long, val name: String, val color: Int, val owner: Boolean)

    val id = mutableStateOf(0L)
    val name = mutableStateOf("")
    val clan = mutableStateOf<Clan?>(null)
    val levels = mutableStateMapOf<String, Int>()
    val unread = mutableIntStateOf(0)
    val online = mutableStateMapOf<String, Int>()
    val status = mutableStateOf("")
    var key = ""
        private set
    private lateinit var prefs: android.content.SharedPreferences

    val loggedIn: Boolean get() = id.value > 0 && key.isNotEmpty()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)
        id.value = prefs.getLong("id", 0L)
        key = prefs.getString("key", "") ?: ""
        name.value = prefs.getString("name", "") ?: ""
        if (loggedIn) refresh()
    }

    private fun persist() {
        prefs.edit().putLong("id", id.value).putString("key", key).putString("name", name.value).apply()
    }

    private fun applyProfile(p: JSONObject) {
        id.value = p.optLong("id", id.value)
        name.value = p.optString("name", name.value)
        clan.value = p.optJSONObject("clan")?.let { Clan(it.optLong("id"), it.optString("name"), it.optInt("color"), it.optBoolean("owner")) }
        p.optJSONObject("chars")?.let { c -> levels.clear(); for (k in c.keys()) levels[k] = c.optInt(k) }
        unread.intValue = p.optInt("unread")
        persist()
    }

    private fun auth() = JSONObject().put("p_id", id.value).put("p_key", key)

    fun refresh(done: (String?) -> Unit = {}) {
        if (!loggedIn) { done("Hesap yok"); return }
        status.value = "Bağlanıyor…"
        Supa.rpc("zx_login", auth()) { r ->
            r.onSuccess { applyProfile(JSONObject(it)); status.value = "Çevrimiçi"; done(null) }
                .onFailure { status.value = it.message ?: "Hata"; done(it.message) }
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

    fun changeKey(done: (String?) -> Unit) = Supa.rpc("zx_change_key", auth()) { r ->
        r.onSuccess { key = JSONObject(it).optString("key"); persist(); done(null) }.onFailure { done(it.message) }
    }

    fun rename(nick: String, done: (String?) -> Unit) = Supa.rpc("zx_rename", auth().put("p_name", nick.trim())) { r ->
        r.onSuccess { applyProfile(JSONObject(it)); done(null) }.onFailure { done(it.message) }
    }

    fun logout() {
        id.value = 0L; key = ""; name.value = ""; clan.value = null; levels.clear(); unread.intValue = 0
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

    fun saveCloud(server: String, level: Int, data: String) {
        if (!loggedIn) return
        levels[server] = level
        Supa.bg { Supa.rpcSync("zx_save", auth().put("p_server", server).put("p_level", level).put("p_data", data)) }
    }

    // ------------------------------------------------------------------ posta
    fun mail(done: (Result<JSONArray>) -> Unit) = Supa.rpc("zx_mail_list", auth()) { r ->
        done(r.map { JSONArray(it) }); if (r.isSuccess) unread.intValue = 0
    }

    fun claimMail(mailId: Long, done: (Result<JSONObject>) -> Unit) =
        Supa.rpc("zx_mail_claim", auth().put("p_mail", mailId)) { r -> done(r.map { JSONObject(it) }) }

    fun sendMail(to: String, title: String, body: String, done: (String?) -> Unit) =
        Supa.rpc("zx_mail_send", auth().put("p_to", to).put("p_title", title).put("p_body", body)) { r -> done(r.exceptionOrNull()?.message) }

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
