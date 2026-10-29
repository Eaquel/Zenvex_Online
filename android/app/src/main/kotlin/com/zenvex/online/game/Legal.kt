package com.zenvex.online.game

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.zenvex.online.net.Supa
import org.json.JSONObject

/**
 * Yasal metinler ve onaylar. Metinlerin tek kaynağı supabase/functions/zx-legal/content/legal.json: uygulamaya
 * assets/legal.json olarak paketlenir, web sayfaları (zx-legal) da aynı dosyayı okur; metinler birebir aynıdır.
 *
 *  - Aydınlatma (KVKK m.10): ilk açılışta (ve metin sürümü değişince) gösterilir. Görülmeden cihaz kimliği sunucuya gitmez.
 *  - Kullanım Şartları: hesap açarken ya da girerken onaylanır; sürüm değişince yeniden onay istenir (sunucuda kayıtlı).
 *  - Ticari elektronik ileti (etkinlik/kampanya bildirimi): yalnız açık onayla, varsayılan kapalı (6563, KVKK m.5/1).
 */
object Legal {
    const val URL = "https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal"

    data class Page(val id: String, val title: String, val paras: List<String>)

    var version = 0
        private set
    var pages: List<Page> = emptyList()
        private set
    var notice: List<String> = emptyList()
        private set
    /** künye bilgileri (UNVAN, ADRES, EPOSTA, KEP, TELEFON, MERSIS, OYUN_SUNUCUSU) */
    var operator: Map<String, String> = emptyMap()
        private set
    /** künyede doldurulmamış yer tutucu kaldı mı (geliştirici sürümünde uyarı gösterilir) */
    var incomplete = false
        private set

    /** bu metin sürümünün aydınlatması görüldü mü */
    val noticeSeen = mutableStateOf(true)
    /** hesabın onayladığı şartlar eski: yeniden onay penceresi açılır */
    val termsNeeded = mutableStateOf(false)
    /** etkinlik/kampanya bildirimi onayı */
    val promo = mutableStateOf(false)
    /** tam ekran okuyucuda açık sayfa (her ekrandan açılabilir) */
    val open = mutableStateOf<String?>(null)

    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        runCatching {
            val o = JSONObject(ctx.assets.open("legal.json").bufferedReader().use { it.readText() })
            version = o.optInt("version")
            val op = o.optJSONObject("operator") ?: JSONObject()
            val updated = o.optString("updated")
            fun fill(s: String) = Regex("\\{\\{([A-Z_]+)\\}\\}").replace(s) { m ->
                when (val k = m.groupValues[1]) { "TARIH" -> updated; "URL" -> URL; else -> op.optString(k) }
            }
            fun list(a: org.json.JSONArray?) = (0 until (a?.length() ?: 0)).map { fill(a!!.getString(it)) }
            notice = list(o.optJSONArray("notice"))
            val arr = o.optJSONArray("pages")
            pages = (0 until (arr?.length() ?: 0)).map { i ->
                val pg = arr!!.getJSONObject(i)
                Page(pg.optString("id"), pg.optString("title"), list(pg.optJSONArray("paras")))
            }
            operator = op.keys().asSequence().associateWith { op.optString(it) }
            incomplete = operator.values.any { it.startsWith("[DOLDURULACAK") }
        }
        noticeSeen.value = p.getInt("notice", 0) >= version
        promo.value = p.getBoolean(KEY_PROMO, false)
    }

    fun page(id: String) = pages.firstOrNull { it.id == id }
    fun url(id: String) = "$URL?p=$id"

    /** Aydınlatma metni görüldü: bundan sonra cihaz kimliği gönderilir. */
    fun ackNotice() {
        prefs?.edit()?.putInt("notice", version)?.apply()
        noticeSeen.value = true
        record("notice", true)
        com.zenvex.online.ui.Gate.poll()
    }

    /** Kullanım Şartları onayı sunucuya kaydedilir (hesap açılınca / girince ya da güncel sürüm onaylanınca). */
    fun acceptTerms(done: ((String?) -> Unit)? = null) {
        if (!Account.loggedIn) { done?.invoke(null); return }
        termsNeeded.value = false
        // önce cihazda: sunucu yanıtı gecikse de pencere yeniden açılmasın
        prefs?.edit()?.putInt("terms_${Account.id.value}", version)?.apply()
        Supa.rpc("zx_consent", Account.authJson().put("p_kind", "terms").put("p_version", version).put("p_granted", true)) { r ->
            done?.invoke(r.exceptionOrNull()?.message)
        }
        record("notice", true)
    }

    /** Ticari elektronik ileti onayı ver / geri al (her değişiklik tarih ve sürümle kaydedilir). */
    fun setPromo(on: Boolean) {
        promo.value = on
        prefs?.edit()?.putBoolean(KEY_PROMO, on)?.apply()
        record("promo", on)
    }

    private fun record(kind: String, granted: Boolean) {
        if (!Account.loggedIn) return
        Supa.rpc("zx_consent", Account.authJson().put("p_kind", kind).put("p_version", version).put("p_granted", granted)) { }
    }

    /** Giriş yapılınca: hesabın onayladığı şartlar sürümü ve son bildirim tercihi sunucudan alınır. */
    fun sync() {
        if (!Account.loggedIn) return
        Supa.rpc("zx_consent_state", Account.authJson()) { r ->
            r.onSuccess { s ->
                val o = runCatching { JSONObject(s) }.getOrNull() ?: return@onSuccess
                val local = prefs?.getInt("terms_${Account.id.value}", 0) ?: 0
                termsNeeded.value = o.optInt("terms") < version && local < version
                // cihazda onaylanıp sunucuya ulaşamamışsa yeniden gönder
                if (o.optInt("terms") < version && local >= version) record("terms", true)
                if (!o.isNull("promo")) {
                    val on = o.optBoolean("promo")
                    promo.value = on
                    prefs?.edit()?.putBoolean(KEY_PROMO, on)?.apply()
                }
            }
        }
    }

    const val PREFS = "legal"
    const val KEY_PROMO = "promo"

    /** Bildirim işçisi (uygulama kapalıyken) için: ticari ileti onayı var mı */
    fun promoAllowed(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PROMO, false)
}
