package com.zenvex.online.game

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Isınma koruması: cihaz ısındıkça (Android termal durumu) kare sınırını ve parçacık yoğunluğunu kademeli düşürür. Soğuyunca
 * eski değerlere döner. Sistem kendi kısmadan önce davranıldığı için takılma yerine akıcı ama biraz daha az kare görülür.
 */
object Thermal {
    @Volatile private var status = 0
    /** öngörülen durum (Android 11+ ısınma payı tahmini): sistem kısmadan, cihaz ısınmadan önce hafifçe yavaşla */
    @Volatile private var predicted = 0
    private var pm: PowerManager? = null
    private var lastPoll = 0L

    private val level: Int get() = maxOf(status, predicted)

    /** termal duruma göre kare sınırı (0 = sınır yok) */
    val fpsCap: Int get() = when { level >= 4 -> 30; level >= 3 -> 36; level >= 2 -> 48; level >= 1 -> 55; else -> 0 }

    /** parçacık/efekt çarpanı */
    val fxK: Float get() = when { level >= 4 -> 0.5f; level >= 3 -> 0.65f; level >= 2 -> 0.85f; level >= 1 -> 0.95f; else -> 1f }

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val p = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm = p
            status = p.currentThermalStatus
            p.addThermalStatusListener { s -> status = s }
        }
    }

    /**
     * Oyun döngüsünden çağrılır; 10 sn'de bir sistemin 10 sn sonrası için ısınma payı tahminini okur (1.0 = kısma sınırı).
     * Pay %75'i geçince hafif, %88'i geçince orta, %96'yı geçince güçlü yavaşlama; düşünce kademeli geri döner.
     */
    fun poll(nowMs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || nowMs - lastPoll < 10_000L) return
        lastPoll = nowMs
        val h = runCatching { pm?.getThermalHeadroom(10) ?: Float.NaN }.getOrDefault(Float.NaN)
        if (h.isNaN()) return
        val want = when { h >= 0.96f -> 3; h >= 0.88f -> 2; h >= 0.75f -> 1; else -> 0 }
        // yükselişte hemen, düşüşte bir basamak (kare hızı inip çıkıp titremesin)
        predicted = if (want >= predicted) want else predicted - 1
    }
}
