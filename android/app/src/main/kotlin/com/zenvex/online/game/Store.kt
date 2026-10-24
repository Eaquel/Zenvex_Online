package com.zenvex.online.game

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.zenvex.online.net.Supa
import com.zenvex.online.ui.Toaster
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Mağaza: ürünler, VIP, seviye paketi teklifleri, kartlar (Supabase zx_store_state) ve Google Play ile ödeme.
 *
 * Satın alma akışı: Play ödeme ekranı → satın alma jetonu → zx-billing edge function'ı jetonu Google'dan doğrular ve
 * içeriği posta olarak yollar → jeton tüketilir (aynı ürün yeniden alınabilir). Uygulama ödeme sırasında kapanırsa
 * bir sonraki açılışta tüketilmemiş satın almalar yeniden doğrulanır (sunucu aynı siparişi iki kez işlemez).
 */
object Store {
    val state = mutableStateOf<JSONObject?>(null)
    /** Play'den gelen yerel fiyatlar (ürün kimliği → "₺99,99"); yoksa veritabanındaki TL fiyatı gösterilir */
    val prices = mutableStateMapOf<String, String>()
    val busy = mutableStateOf<String?>(null)
    private var client: BillingClient? = null
    private val details = HashMap<String, ProductDetails>()
    private var activity: WeakReference<Activity>? = null
    @Volatile private var ready = false

    fun attach(a: Activity) {
        activity = WeakReference(a)
        if (client != null) return
        client = BillingClient.newBuilder(a.applicationContext)
            .setListener { r, list -> onPurchases(r, list) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                ready = r.responseCode == BillingClient.BillingResponseCode.OK
                if (ready) { loadDetails(); recover() }
            }

            override fun onBillingServiceDisconnected() { ready = false }
        })
    }

    private fun productIds(): List<String> {
        val arr = state.value?.optJSONArray("products") ?: return DEFAULT_IDS
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
    }

    private fun loadDetails() {
        val c = client ?: return
        val products = productIds().map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { r, res ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            for (d in res.productDetailsList) {
                details[d.productId] = d
                d.oneTimePurchaseOfferDetails?.formattedPrice?.let { p -> prices[d.productId] = p }
            }
        }
    }

    /** Ödeme sırasında yarım kalmış (doğrulanmamış / tüketilmemiş) satın almaları tamamla */
    private fun recover() {
        client?.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) list.forEach { verify(it) }
        }
    }

    /** Mağaza durumunu yeniler (seviye paketi teklifi seviyeye ulaşınca burada açılır) */
    fun refresh(done: (() -> Unit)? = null) {
        if (!Account.loggedIn) { done?.invoke(); return }
        val lv = GameSession.hud()[Native.LEVEL].toInt().coerceAtLeast(1)
        Supa.rpc("zx_store_state", Account.authJson().put("p_server", GameSession.server.value.id).put("p_level", lv)) { r ->
            r.onSuccess { s -> runCatching { JSONObject(s) }.getOrNull()?.let { state.value = it; if (ready && details.isEmpty()) loadDetails() } }
            done?.invoke()
        }
    }

    fun price(id: String, fallbackTry: Double): String = prices[id] ?: "₺" + "%.2f".format(fallbackTry).replace('.', ',')

    fun buy(id: String) {
        if (!Account.loggedIn) { Toaster.show("Satın almak için hesabına gir", false); return }
        val a = activity?.get()
        val d = details[id]
        val c = client
        if (a == null || c == null || !ready || d == null) {
            Toaster.show("Google Play ödeme sistemi şu an kullanılamıyor. Uygulamayı Play Store'dan kurduğundan emin ol.", false)
            return
        }
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        busy.value = id
        val r = c.launchBillingFlow(a, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p))
            .setObfuscatedAccountId(Account.id.value.toString()).build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) { busy.value = null; Toaster.show("Ödeme başlatılamadı", false) }
    }

    private fun onPurchases(r: BillingResult, list: List<Purchase>?) {
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> list?.forEach { verify(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> busy.value = null
            else -> { busy.value = null; Toaster.show("Ödeme tamamlanamadı (${r.responseCode})", false) }
        }
    }

    private fun verify(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) {
            if (p.purchaseState == Purchase.PurchaseState.PENDING) Toaster.show("Ödeme bekleniyor; tamamlanınca ürünün postana gelir")
            return
        }
        val product = p.products.firstOrNull() ?: return
        val args = JSONObject().put("id", Account.id.value).put("key", Account.authJson().optString("p_key"))
            .put("server", GameSession.server.value.id).put("product", product).put("token", p.purchaseToken)
        Supa.fn("zx-billing", args) { res ->
            busy.value = null
            res.onSuccess {
                // içerik postada: jeton tüketilir, ürün yeniden alınabilir
                client?.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _, _ -> }
                // bilet postayla değil doğrudan açılır: sunucudaki bilet oyuna uygulanır
                if (product.startsWith("pass_")) { Toaster.show("Satın alma tamam! Sezon Biletin açıldı."); Pass.sync() }
                else { Toaster.show("Satın alma tamam! Ödülün postana geldi."); Account.unread.intValue = Account.unread.intValue + 1 }
                refresh()
            }.onFailure { e ->
                Log.w("zenvex", "satın alma doğrulanamadı", e)
                Toaster.show(e.message ?: "Satın alma doğrulanamadı; bir sonraki açılışta yeniden denenecek", false)
            }
        }
    }

    fun claimCards(done: () -> Unit) = Supa.rpc("zx_card_claim", Account.authJson()) { r ->
        r.onSuccess { Toaster.show("Kart ödülün postana geldi"); Account.unread.intValue = Account.unread.intValue + 1 }
            .onFailure { Toaster.show(it.message ?: "", false) }
        refresh(done)
    }

    fun claimVip(done: () -> Unit) = Supa.rpc("zx_vip_daily", Account.authJson()) { r ->
        r.onSuccess { Toaster.show("VIP günlük elmasın postana geldi"); Account.unread.intValue = Account.unread.intValue + 1 }
            .onFailure { Toaster.show(it.message ?: "", false) }
        refresh(done)
    }

    /** Yönetici: ödeme olmadan test satın alması (VIP puanı vermez) */
    fun adminTest(target: Long, product: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_test_purchase", Account.authJson().put("p_target", target).put("p_product", product)
            .put("p_server", GameSession.server.value.id), done)

    /** VIP seviyesi (0-11; 11 = V10+) */
    fun vipLevel(): Int = state.value?.optJSONObject("vip")?.optInt("level") ?: 0
    fun vipName(level: Int): String = if (level >= 11) "V10+" else "V$level"

    private val DEFAULT_IDS = listOf("gems_60", "gems_220", "gems_600", "gems_1300", "gems_2800", "gems_6000", "pack_start", "pack_growth",
        "pack_war", "card_week", "card_month", "pass_vip", "pass_premium", "pass_upgrade")
}
