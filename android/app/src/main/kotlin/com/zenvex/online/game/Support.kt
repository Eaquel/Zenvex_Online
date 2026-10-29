package com.zenvex.online.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.zenvex.online.net.Supa
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Destek talebi (R41b): konu + açıklama + en çok 10 fotoğraf (her biri en çok 10 MB). Aynı cihazdan ve hesaptan
 * 15 dakikada bir talep (sunucu denetler). Önce talep açılır, sonra fotoğraflar tek tek zx-support'a yüklenir.
 * Büyük fotoğraflar gönderilmeden önce en uzun kenarı 1920 px olacak şekilde küçültülüp JPEG'e çevrilir.
 */
object Support {
    const val MAX_PHOTOS = 10
    const val MAX_BYTES = 10 * 1024 * 1024
    /** "KVKK": veri sahibi başvurusu (KVKK m.11/13, 30 gün içinde yanıt); "İtiraz": yaptırıma itiraz */
    val TOPICS = listOf("Hata", "Ödeme", "Hesap", "KVKK", "İtiraz", "Öneri", "Diğer")

    /** Arka planda gönderir; [progress] (yüklenen, toplam), [done] hata mesajı ya da null. Ana iş parçacığında çağrılır. */
    fun send(ctx: Context, topic: String, body: String, photos: List<Uri>, progress: (Int, Int) -> Unit, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Supa.bg {
            val err = runCatching {
                val dev = Guard.deviceId(app)
                val open = JSONObject(Supa.rpcSync("zx_support_open", Account.authJson().put("p_device", dev).put("p_topic", topic)
                    .put("p_body", body).put("p_photos", photos.size.coerceAtMost(MAX_PHOTOS))))
                val ticket = open.getLong("ticket")
                photos.take(MAX_PHOTOS).forEachIndexed { i, uri ->
                    val (bytes, ext) = prepare(app, uri)
                    if (bytes.size > MAX_BYTES) throw IllegalStateException("${i + 1}. fotoğraf 10 MB'den büyük")
                    val auth = Account.authJson()
                    Supa.fnSync("zx-support", JSONObject().put("op", "photo").put("id", auth.optLong("p_id")).put("key", auth.optString("p_key"))
                        .put("ticket", ticket).put("idx", i).put("ext", ext).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
                    main.post { progress(i + 1, photos.size) }
                }
            }.exceptionOrNull()?.let { it.message ?: "Gönderilemedi" }
            main.post { done(err) }
        }
    }

    /** Fotoğrafı okur; büyükse küçültüp JPEG yapar. (baytlar, uzantı) */
    private fun prepare(ctx: Context, uri: Uri): Pair<ByteArray, String> {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Fotoğraf okunamadı")
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        val side = maxOf(o.outWidth, o.outHeight)
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        if (side in 1..1920 && raw.size <= 2 * 1024 * 1024 && (mime == "image/jpeg" || mime == "image/png" || mime == "image/webp")) {
            return raw to when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        }
        var sample = 1
        while (side / (sample * 2) >= 1920) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("Fotoğraf açılamadı")
        val k = 1920f / maxOf(bmp.width, bmp.height)
        val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return bos.toByteArray() to "jpg"
    }

    // ---------------------------------------------------------------- yönetici
    fun adminList(open: Boolean, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_support", Account.authJson().put("p_open", open), done)

    fun adminReply(ticket: Long, text: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_support_reply", Account.authJson().put("p_ticket", ticket).put("p_reply", text), done)

    /** Talebin fotoğraflarına 1 saatlik imzalı bağlantılar */
    fun adminPhotos(ticket: Long, done: (Result<List<String>>) -> Unit) {
        val auth = Account.authJson()
        Supa.fn("zx-support", JSONObject().put("op", "sign").put("id", auth.optLong("p_id")).put("key", auth.optString("p_key")).put("ticket", ticket)) { r ->
            done(r.mapCatching { s -> JSONObject(s).optJSONArray("urls").let { a -> if (a == null) emptyList() else List(a.length()) { a.getString(it) } } })
        }
    }

    fun adminReports(open: Boolean, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_reports", Account.authJson().put("p_open", open), done)

    fun adminReportDone(report: Long, action: String, done: (Result<String>) -> Unit) =
        Supa.rpc("zx_admin_report_done", Account.authJson().put("p_report", report).put("p_action", action), done)
}
