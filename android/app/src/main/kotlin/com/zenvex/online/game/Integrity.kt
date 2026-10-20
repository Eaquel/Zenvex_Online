package com.zenvex.online.game

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.security.MessageDigest

/**
 * Kurcalama koruması: APK'nın imza sertifikası beklenen parmak iziyle eşleşmezse (yeniden paketlenmiş / değiştirilmiş APK)
 * ya da sürüm derlemesine hata ayıklayıcı bağlanmışsa çevrimiçi özellikler kapanır. Sunucu ayrıca her isteği hesap anahtarıyla doğrular.
 */
object Integrity {
    /** keystore/zenvex-dev.jks (alias zenvex) sertifikasının SHA-256 parmak izi */
    private const val EXPECTED = "B20EC8AD1D76772448CEA975F7F4293A23020F8909FF253499695EE4A68A237B"

    @Volatile var ok = true
        private set
    @Volatile var reason = ""
        private set

    fun check(ctx: Context) {
        val sig = runCatching { signingSha256(ctx) }.getOrDefault(emptyList())
        val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ok = when {
            sig.isEmpty() || EXPECTED !in sig -> { reason = "Uygulama imzası doğrulanamadı. Oyunu resmi kaynaktan yükle."; false }
            !debuggable && Debug.isDebuggerConnected() -> { reason = "Hata ayıklayıcı algılandı."; false }
            else -> true
        }
    }

    @Suppress("DEPRECATION")
    private fun signingSha256(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val si = info.signingInfo ?: return emptyList()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: return emptyList()
        val md = MessageDigest.getInstance("SHA-256")
        return certs.map { c -> md.digest(c.toByteArray()).joinToString("") { "%02X".format(it) } }
    }
}
