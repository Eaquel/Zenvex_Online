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
 * Her tür Ayarlar > Bildirimler'den ayrı kapatılır. Etkinlik duyurusu ticari elektronik iletidir (6563): yalnız oyuncu
 * açıkça onay verdiyse gönderilir (varsayılan kapalı) ve her bildirimde tek dokunuşla ret düğmesi bulunur.
 */
object Notifier {
    const val CH_MAIL = "posta"
    const val CH_DM = "mesaj"
    const val CH_EVENT = "etkinlik"
    @Volatile var foreground = false

    /** Bildirim türleri (Ayarlar > Bildirimler): posta/hediye, özel mesaj, arkadaşlık ve klan */
    const val K_MAIL = "n_mail"
    const val K_DM = "n_dm"
    const val K_SOCIAL = "n_social"
    fun enabled(ctx: Context, k: String) = ctx.getSharedPreferences(Legal.PREFS, Context.MODE_PRIVATE).getBoolean(k, true)
    fun setEnabled(ctx: Context, k: String, on: Boolean) = ctx.getSharedPreferences(Legal.PREFS, Context.MODE_PRIVATE).edit().putBoolean(k, on).apply()

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
        val mails = if (enabled(ctx, K_MAIL)) o.optJSONArray("mails") else null
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
        val dms = if (enabled(ctx, K_DM)) o.optJSONArray("dms") else null
        for (i in 0 until (dms?.length() ?: 0)) {
            val d = dms!!.getJSONObject(i)
            val n = d.optInt("n")
            post(ctx, CH_DM, 3000 + d.optString("from").hashCode() % 1000, d.optString("from") + if (n > 1) " ($n Mesaj)" else "", d.optString("text"))
        }
        // ticari ileti: yalnız onay varsa; gönderen belli, ret yolu bildirimin içinde
        if (Legal.promoAllowed(ctx)) o.optJSONObject("event")?.let { e ->
            post(ctx, CH_EVENT, 5000, "Yeni Etkinlik: " + e.optString("title"),
                "Zenvex Online'da yeni bir etkinlik başladı; ayrıntılar oyunda. Bu duyuruları almak istemiyorsan \"Bu Duyuruları Kapat\"a dokun.", promo = true)
        }
        if (enabled(ctx, K_SOCIAL) && o.optInt("friends") > 0) post(ctx, CH_DM, 6000, "Arkadaşlık İsteği", "${o.optInt("friends")} yeni arkadaşlık isteğin var.")
        if (enabled(ctx, K_SOCIAL) && o.optInt("invites") > 0) post(ctx, CH_MAIL, 6001, "Klan Daveti", "Bir klan seni aralarına çağırıyor.")
    }

    private fun post(ctx: Context, ch: String, id: Int, title: String, text: String, promo: Boolean = false) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFFFB347.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
        if (promo) {
            val off = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, PromoOptOut::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, "Bu Duyuruları Kapat", off)
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) }
    }

    /** Bildirimdeki "Bu Duyuruları Kapat": ticari ileti onayı geri alınır ve sunucuya kaydedilir (6563 m.8: kolay ret). */
    class PromoOptOut : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            ctx.getSharedPreferences(Legal.PREFS, Context.MODE_PRIVATE).edit().putBoolean(Legal.KEY_PROMO, false).apply()
            Legal.promo.value = false
            runCatching { NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("id", 5000)) }
            val ap = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)
            val key = ap.getString("key", "").orEmpty(); val acc = ap.getLong("id", -1L)
            if (key.isEmpty() || acc < 0) return
            val pending = goAsync()
            Thread {
                runCatching {
                    Supa.rpcSync("zx_consent", JSONObject().put("p_id", acc).put("p_key", key).put("p_kind", "promo").put("p_version", 0).put("p_granted", false))
                }
                pending.finish()
            }.start()
        }
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = runCatching { poll(applicationContext) }.fold({ Result.success() }, { Result.retry() })
    }
}
