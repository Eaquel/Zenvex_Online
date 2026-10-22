package com.zenvex.online.game

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zenvex.online.MainActivity
import com.zenvex.online.R
import com.zenvex.online.net.Supa
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cihaz bildirimleri: posta/hediye, özel mesaj, etkinlik duyurusu, arkadaşlık isteği, klan daveti.
 * Arka plandaki işçi 15 dakikada bir sunucuya sorar (zx_notify); oyun açıkken bildirim gösterilmez (oyunda rozetler var).
 */
object Notifier {
    const val CH_MAIL = "posta"
    const val CH_DM = "mesaj"
    const val CH_EVENT = "etkinlik"
    @Volatile var foreground = false

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_MAIL, "Posta Ve Hediyeler", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_DM, "Özel Mesajlar", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_EVENT, "Etkinlik Duyuruları", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Giriş yapılıysa düzenli kontrolü kurar (aynı iş tekrar kurulmaz). */
    fun schedule(ctx: Context) {
        channels(ctx)
        val req = PeriodicWorkRequestBuilder<Worker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("zx_notify", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("zx_notify")

    /** Bir kez sor ve göster (işçi ve test için). */
    fun poll(ctx: Context) {
        val prefs = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)
        val key = prefs.getString("key", "") ?: ""
        val id = prefs.getLong("id", -1L)
        if (key.isEmpty() || id < 0) return
        val np = ctx.getSharedPreferences("notify", Context.MODE_PRIVATE)
        // ilk çalışmada geçmiş birikmiş postalar için bildirim yağmuru olmasın
        val since = np.getLong("since", System.currentTimeMillis())
        val o = JSONObject(Supa.rpcSync("zx_notify", JSONObject().put("p_id", id).put("p_key", key).put("p_since", since)))
        np.edit().putLong("since", o.optLong("now", System.currentTimeMillis())).apply()
        if (foreground) return
        show(ctx, o)
    }

    fun show(ctx: Context, o: JSONObject) {
        val mails = o.optJSONArray("mails")
        for (i in 0 until (mails?.length() ?: 0)) {
            val m = mails!!.getJSONObject(i)
            val gift = m.optInt("gems") > 0 || m.optInt("gold") > 0 || m.optInt("items") > 0
            val title = when (m.optString("kind")) { "event" -> "Etkinlik Ödülü!"; "system" -> "Sistem Mesajı"; else -> if (gift) "Hediye Geldi!" else "Yeni Posta" }
            val parts = buildList {
                if (m.optInt("gems") > 0) add("${m.optInt("gems")} Elmas")
                if (m.optInt("gold") > 0) add("${m.optInt("gold")} Altın")
                if (m.optInt("items") > 0) add("${m.optInt("items")} Eşya")
            }
            post(ctx, CH_MAIL, 1000 + (m.optLong("id") % 100000).toInt(), title,
                m.optString("title") + (if (parts.isNotEmpty()) " · " + parts.joinToString(", ") else "") + " — " + m.optString("sender"))
        }
        val dms = o.optJSONArray("dms")
        for (i in 0 until (dms?.length() ?: 0)) {
            val d = dms!!.getJSONObject(i)
            val n = d.optInt("n")
            post(ctx, CH_DM, 3000 + d.optString("from").hashCode() % 1000, d.optString("from") + if (n > 1) " ($n Mesaj)" else "", d.optString("text"))
        }
        o.optJSONObject("event")?.let { e ->
            post(ctx, CH_EVENT, 5000, "Etkinlik Başladı: " + e.optString("title"), "İki hafta sürecek! İlk 100'e özel ödüller seni bekliyor. Hemen gir!")
        }
        if (o.optInt("friends") > 0) post(ctx, CH_DM, 6000, "Arkadaşlık İsteği", "${o.optInt("friends")} yeni arkadaşlık isteğin var.")
        if (o.optInt("invites") > 0) post(ctx, CH_MAIL, 6001, "Klan Daveti", "Bir klan seni aralarına çağırıyor.")
    }

    private fun post(ctx: Context, ch: String, id: Int, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFFFB347.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = runCatching { poll(applicationContext) }.fold({ Result.success() }, { Result.retry() })
    }
}
