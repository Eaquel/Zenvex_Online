package com.zenvex.online.game

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Katmanlı istemci güvenliği. Sunucu oturum açarken bu bilgileri alır:
 *  - sig: uygulama imzası bizim mi (Integrity)        → değilse giriş yok
 *  - dbg: hata ayıklayıcı bağlı mı                     → bağlıysa giriş yok
 *  - hook: Frida / Xposed / LSPosed izi var mı          → varsa giriş yok
 *  - root, emu: root / emülatör                         → yalnız kayda geçer (yönetici görür)
 *  - id: cihaz kimliği (ANDROID_ID + uygulama tuzu, SHA-256) → cihaz yasağı ve çoklu hesap tespiti
 */
object Guard {
    @SuppressLint("HardwareIds")
    fun deviceId(ctx: Context): String {
        val aid = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val raw = "zx|$aid|${Build.MANUFACTURER}|${Build.MODEL}|${Build.BOARD}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
    }

    private fun anyExists(paths: List<String>) = paths.any { runCatching { File(it).exists() }.getOrDefault(false) }

    fun rooted(): Boolean =
        Build.TAGS?.contains("test-keys") == true ||
            anyExists(listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/adb/magisk",
                "/data/adb/modules", "/system/bin/magisk", "/data/local/xbin/su", "/data/local/bin/su"))

    fun emulator(): Boolean {
        val f = Build.FINGERPRINT.orEmpty().lowercase()
        val h = Build.HARDWARE.orEmpty().lowercase()
        return f.startsWith("generic") || f.contains("emulator") || h.contains("goldfish") || h.contains("ranchu") ||
            Build.MODEL.orEmpty().contains("sdk_gphone", true) || Build.PRODUCT.orEmpty().contains("sdk", true)
    }

    fun hooked(): Boolean {
        // Xposed / LSPosed sınıfları
        val xposed = listOf("de.robv.android.xposed.XposedBridge", "org.lsposed.lspd.core.Main").any {
            runCatching { Class.forName(it); true }.getOrDefault(false)
        }
        if (xposed) return true
        // Frida: belleğe yüklenmiş ajan ya da açık varsayılan port
        val maps = runCatching { File("/proc/self/maps").readText() }.getOrDefault("")
        if (maps.contains("frida", true) || maps.contains("gadget", true) || maps.contains("libxposed", true)) return true
        return runCatching { Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", 27042), 60); true } }.getOrDefault(false)
    }

    fun debugging(): Boolean = Debug.isDebuggerConnected() || Debug.waitingForDebugger()

    /** Oturum açılışında sunucuya giden cihaz raporu (ağ iş parçacığında çağrılmalı: port denemesi var). */
    fun report(ctx: Context): JSONObject = JSONObject()
        .put("id", deviceId(ctx))
        .put("model", "${Build.MANUFACTURER} ${Build.MODEL}".take(60))
        .put("sdk", Build.VERSION.SDK_INT)
        .put("app", com.zenvex.online.BuildConfig.VERSION_NAME)
        .put("sig", Integrity.ok)
        .put("dbg", debugging())
        .put("hook", hooked())
        .put("root", rooted())
        .put("emu", emulator())
        .put("installer", runCatching {
            if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
            else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName)
        }.getOrNull() ?: "")
}
