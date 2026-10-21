package com.zenvex.online.game

import android.os.Handler
import android.os.Looper
import com.zenvex.online.net.Supa
import com.zenvex.online.ui.Toaster
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sunucu emanetli takas (Online.kt yalnız ekrandaki teklifleri eşler; eşya hareketi burada ve sunucuda olur).
 *  1) Kabul eden taraf zx_trade_open ile takas açar, numarayı karşıya yollar.
 *  2) "Hazırım": teklif edilen eşyalar çantadan düşülür, imzalı kayıtla zx_trade_ready'ye gider; sunucu eşyaların gerçekten
 *     olduğunu ve düşüldüğünü doğrular, emanete alır. Reddedilirse eşyalar geri konur.
 *  3) İki taraf da hazırsa takas biter; zx_trade_claim karşı tarafın eşyasını bir kez verir. İptalde emanet geri gelir.
 */
object Trade {
    private val main = Handler(Looper.getMainLooper())
    private val claiming = HashSet<Long>()

    /** eşya kimliği → kayıttaki sıra numarası (altın = -1) */
    fun indexOf(id: String): Int {
        if (id == "coin") return -1
        val items = GameSession.world().optJSONArray("items") ?: return -2
        for (i in 0 until items.length()) if (items.optJSONArray(i)?.optString(0) == id) return i
        return -2
    }

    private fun idOf(idx: Int): String =
        if (idx == -1) "coin" else GameSession.world().optJSONArray("items")?.optJSONArray(idx)?.optString(0).orEmpty()

    fun accountOf(uid: String): Long = uid.removePrefix("a").toLongOrNull() ?: -1

    fun open(otherUid: String, done: (Long) -> Unit) {
        val other = accountOf(otherUid)
        if (other < 0) { Toaster.show("Bu oyuncuyla takas yapılamaz", false); return }
        Supa.rpc("zx_trade_open", Account.authJson().put("p_sid", Session.sid).put("p_other", other)) { r ->
            r.onSuccess { done(it.trim().toLongOrNull() ?: 0L) }.onFailure { Toaster.show(it.message ?: "Takas açılamadı", false) }
        }
    }

    /** Teklifi kilitler (emanet). [done] true → sunucu kabul etti. */
    fun ready(tid: Long, items: List<Online.TradeItem>, done: (Boolean) -> Unit) {
        if (tid <= 0) { Toaster.show("Takas henüz açılmadı, bekle", false); done(false); return }
        val arr = JSONArray()
        val taken = ArrayList<Online.TradeItem>()
        for (it in items) {
            val idx = indexOf(it.id)
            if (idx < -1 || it.n <= 0) continue
            val r = GameSession.cmdNoSave("trade_take", "id" to it.id, "n" to it.n)
            if (!r.ok) {
                for (b in taken) GameSession.cmdNoSave("trade_give", "id" to b.id, "n" to b.n)
                Toaster.show(r.msg, false); done(false); return
            }
            taken += it
            arr.put(JSONObject().put("item", idx).put("n", it.n))
        }
        val snap = GameSession.snapshot()
        val args = Session.signedSave(snap.level, snap.data, snap.kills, snap.xp).put("p_trade", tid).put("p_items", arr)
        Supa.rpc("zx_trade_ready", args) { r ->
            r.onSuccess { done(true); poll(tid) }
                .onFailure { e ->
                    for (b in taken) GameSession.cmdNoSave("trade_give", "id" to b.id, "n" to b.n)
                    GameSession.save(cloud = true)
                    Toaster.show(e.message ?: "Takas onaylanamadı", false)
                    done(false)
                }
        }
    }

    /** Hazır olduktan sonra sonucu bekler (2 sn aralıkla). */
    private fun poll(tid: Long) {
        main.postDelayed({
            if (Session.state.value == Session.State.Off) return@postDelayed
            Supa.rpc("zx_trade_claim", Account.authJson().put("p_trade", tid)) { r ->
                r.onSuccess { body ->
                    val o = runCatching { JSONObject(body) }.getOrNull() ?: return@onSuccess
                    if (o.optString("state") == "open") { if (Online.trade.value?.tid == tid) poll(tid); return@onSuccess }
                    apply(tid, o)
                }.onFailure { if (Online.trade.value?.tid == tid) poll(tid) }
            }
        }, 2000)
    }

    fun cancel(tid: Long) {
        if (tid <= 0) return
        Supa.rpc("zx_trade_cancel", Account.authJson().put("p_trade", tid)) { claimPending(tid) }
    }

    /** Nabızda bildirilen ya da iptal sonrası alınmamış takas sonucu. */
    fun claimPending(tid: Long) {
        if (tid <= 0 || !claiming.add(tid)) return
        Supa.rpc("zx_trade_claim", Account.authJson().put("p_trade", tid)) { r ->
            claiming.remove(tid)
            r.onSuccess { body -> runCatching { JSONObject(body) }.getOrNull()?.let { if (it.optString("state") != "open") apply(tid, it) } }
        }
    }

    private fun apply(tid: Long, o: JSONObject) {
        val items = o.optJSONArray("items") ?: JSONArray()
        val got = ArrayList<String>()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val id = idOf(it.optInt("item", -2))
            if (id.isEmpty()) continue
            val r = GameSession.cmdNoSave("trade_give", "id" to id, "n" to it.optInt("n"))
            if (r.ok) got += r.msg
        }
        GameSession.save(cloud = true)
        if (o.optString("state") == "done") {
            Online.tradeFinished(tid)
            Toaster.show(if (got.isEmpty()) "Takas Tamamlandı" else "Takas Tamamlandı: " + got.joinToString(", "))
        } else if (got.isNotEmpty()) Toaster.show("Takas İptal: Eşyaların Geri Geldi", false)
    }
}
