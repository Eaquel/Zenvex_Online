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

    /** termal duruma göre kare sınırı (0 = sınır yok) */
    val fpsCap: Int get() = when { status >= 4 -> 30; status >= 3 -> 36; status >= 2 -> 48; else -> 0 }

    /** parçacık/efekt çarpanı */
    val fxK: Float get() = when { status >= 4 -> 0.5f; status >= 3 -> 0.65f; status >= 2 -> 0.85f; else -> 1f }

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val pm = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            status = pm.currentThermalStatus
            pm.addThermalStatusListener { s -> status = s }
        }
    }
}
