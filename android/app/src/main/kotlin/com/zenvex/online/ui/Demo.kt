package com.zenvex.online.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Otomatik gösterim (yalnız test): oyunu açıp ekranları sırayla gezer ve her birinin görüntüsünü kaydeder.
 * Başlatma: adb shell am start -n com.zenvex.online/.MainActivity --es screen Loading --ez demo true
 * Görüntüler: /sdcard/Android/data/com.zenvex.online/files/demo altında PNG (CI bunları çekip yükler).
 */
object Demo {
    @Volatile var on = false
    private val thread by lazy { HandlerThread("demo-shot").also { it.start() } }

    fun dir(act: Activity) = File(act.getExternalFilesDir(null), "demo").also { it.mkdirs() }

    /** Pencerenin tamamını (SurfaceView dahil) PNG olarak kaydeder. */
    suspend fun shot(act: Activity, name: String) {
        // CI betiği bu satırı görünce adb screencap alır; ekran bu sırada sabit tutulur
        Log.i("ZXDEMO", "ready $name")
        kotlinx.coroutines.delay(1500)
        val v = act.window.decorView
        if (v.width == 0) return
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val ok = suspendCancellableCoroutine { c ->
            PixelCopy.request(act.window, bmp, { r -> c.resume(r == PixelCopy.SUCCESS) }, Handler(thread.looper))
        }
        if (!ok) { Log.w("ZXDEMO", "shot failed $name"); return }
        File(dir(act), "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("ZXDEMO", "shot $name")
    }

    fun done(act: Activity) {
        File(dir(act), "done").writeText("ok")
        Log.i("ZXDEMO", "done")
    }
}
