package com.zenvex.online.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.Support
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ayarlar → Destek (R41b): konu, açıklama, en çok 10 fotoğraf (her biri en çok 10 MB). Aynı cihazdan 15 dakikada bir
 * talep. Yanıt posta kutusuna gelir.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.SupportSection() {
    val ctx = LocalContext.current
    var topic by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<Uri>() }
    var sending by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Support.MAX_PHOTOS)) { list ->
        for (u in list) if (photos.size < Support.MAX_PHOTOS && u !in photos) photos += u
    }
    if (!Account.loggedIn) {
        PT("Destek", 18.sp, Px.Clan)
        PT("Bir sorun mu var? Yaz, gerekirse ekran görüntüsü ekle. Yanıtımız posta kutuna gelir.", 12.sp, Px.Sub, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        PT("Destek talebi için hesabına gir.", 13.sp, Px.Hard)
        return
    }
    // iki sütun, kaydırma yok: solda konu ve metin, sağda fotoğraflar ve gönder
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1.2f).fillMaxHeight()) {
            PT("Destek", 16.sp, Px.Clan)
            PT("Bir sorun mu var? Yaz, gerekirse ekran görüntüsü ekle. Yanıtımız posta kutuna gelir.", 11.sp, Px.Sub, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            PT("Konu", 12.sp, Px.Text)
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in Support.TOPICS) {
                    val on = topic == t
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(3.dp)).background(if (on) Color(0xE6524428) else Color(0xCC23221D))
                        .border(2.dp, if (on) Px.Clan else Px.Edge, RoundedCornerShape(3.dp)).clickable { topic = t }
                        .padding(vertical = 6.dp), contentAlignment = Alignment.Center) { PT(t, 12.sp, if (on) Px.Text else Px.Sub) }
                }
            }
            Spacer(Modifier.height(5.dp))
            Field(body, { if (it.length <= 2000) body = it }, "Ne oldu? Ne zaman, hangi ekranda, ne yaparken…", Modifier.fillMaxWidth().weight(1f), multiline = true)
            PT("${body.length}/2000", 10.sp, Px.Sub)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxButton("Fotoğraf Ekle", color = Px.Blue, icon = "plus", height = 34.dp, size = 12.sp, enabled = photos.size < Support.MAX_PHOTOS && !sending) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Spacer(Modifier.width(8.dp))
                PT("${photos.size}/${Support.MAX_PHOTOS}", 12.sp, Px.Sub)
            }
            PT("Her biri en çok 10 MB", 11.sp, Px.Sub)
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (u in photos.toList()) {
                        val thumb = remember(u) {
                            runCatching {
                                ctx.contentResolver.openInputStream(u)?.use { s ->
                                    android.graphics.BitmapFactory.decodeStream(s, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                                }?.asImageBitmap()
                            }.getOrNull()
                        }
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF15140F))) {
                            if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(Color(0xCC8A2A22)).clickable { photos.remove(u) },
                                contentAlignment = Alignment.Center) { PT("✕", 11.sp) }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            PxButton(if (sending) prog.ifEmpty { "Gönderiliyor…" } else "Gönder", Modifier.fillMaxWidth(), color = Px.Green, icon = "mail", height = 40.dp, size = 14.sp,
                enabled = !sending && topic.isNotEmpty() && body.trim().length >= 10) {
                sending = true; prog = ""
                Support.send(ctx, topic, body.trim(), photos.toList(), progress = { k, n -> prog = "Fotoğraf $k/$n yüklendi" }) { err ->
                    sending = false
                    if (err != null) Toaster.show(err, false)
                    else { Toaster.show("Talebin alındı. Yanıtımız posta kutuna gelecek."); topic = ""; body = ""; photos.clear() }
                }
            }
            PT("Aynı cihazdan 15 dakikada bir talep gönderilebilir.", 10.sp, Px.Sub, maxLines = 2)
        }
    }
}

// =========================================================================== yönetici: destek ve bildirimler
/** İki profil kartı yan yana gösterilen kısa hesap özeti (seviye, klan, cihaz, bildirim sayıları, son sohbet) */
@Composable
internal fun AccountCard(o: JSONObject?, title: String, open: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x66000000)).clickable { o?.let { open(it.optLong("id")) } }.padding(8.dp)) {
        PT(title, 11.sp, Px.Sub)
        if (o == null) { PT("—", 13.sp); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(o.optInt("avatar"), o.optString("frame"), 40.dp, photo = o.optString("photo"))
            Spacer(Modifier.width(6.dp))
            Column {
                PT(o.optString("name"), 14.sp, if (o.optBoolean("banned")) Color(0xFFFF6A5A) else Px.Name, maxLines = 1)
                PT("Sv ${o.optInt("level")} · ${o.optString("clan").takeIf { it.isNotEmpty() && it != "null" } ?: "Klansız"}", 11.sp, Px.Sub, maxLines = 1)
            }
        }
        PT("ID ${o.optLong("id")} · Cihaz ${o.optString("device").take(10)}", 10.sp, Px.Sub, maxLines = 1)
        PT("Hakkında ${o.optInt("reported")} bildirim · Gönderdiği ${o.optInt("reports_made")}", 10.sp, Px.Orange, maxLines = 1)
        val chat = o.optJSONArray("chat") ?: JSONArray()
        for (i in 0 until chat.length().coerceAtMost(3)) PT("“${chat.optString(i)}”", 10.sp, Px.Text, maxLines = 1)
    }
}

@Composable
internal fun AdminReportsTab(open: (Long) -> Unit, report: (String, Boolean) -> Unit) {
    var showOpen by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(showOpen, tick) {
        Support.adminReports(showOpen) { r -> r.onSuccess { list = JSONArray(it).objs() }.onFailure { report(it.message ?: "", false) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Açık", color = if (showOpen) Px.Green else Px.Dark, height = 34.dp, size = 12.sp) { showOpen = true }
        PxButton("Kapanan", color = if (!showOpen) Px.Green else Px.Dark, height = 34.dp, size = 12.sp) { showOpen = false }
    }
    Spacer(Modifier.height(6.dp))
    if (list.isEmpty()) PT(if (showOpen) "Açık bildirim yok" else "Kapanan bildirim yok", 13.sp, Px.Sub)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in list) Column(Modifier.fillMaxWidth().pxPanel(Px.PanelDark).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT(o.optString("reason"), 14.sp, Px.Orange, Modifier.weight(1f))
                PT(ts(o.optLong("at")), 11.sp, Px.Sub)
            }
            if (o.optString("note").isNotEmpty()) PT("“${o.optString("note")}”", 12.sp, Px.Text, maxLines = 4)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountCard(o.optJSONObject("reporter"), "Bildiren", open, Modifier.weight(1f))
                AccountCard(o.optJSONObject("target"), "Bildirilen", open, Modifier.weight(1f))
            }
            if (showOpen) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PxButton("Bildirilenin Dosyası", color = Px.Blue, height = 32.dp, size = 11.sp) { open(o.optJSONObject("target")?.optLong("id") ?: 0L) }
                    for ((label, act) in listOf("Haklı (İşlem Yapıldı)" to "action", "Asılsız" to "dismissed")) {
                        PxButton(label, color = Px.Dark, height = 32.dp, size = 11.sp) {
                            Support.adminReportDone(o.optLong("id"), act) { r -> r.onSuccess { report(it, true); tick++ }.onFailure { report(it.message ?: "", false) } }
                        }
                    }
                }
            } else PT("Sonuç: ${o.optString("action")}", 11.sp, Px.Sub)
        }
    }
}

@Composable
internal fun AdminSupportTab(open: (Long) -> Unit, report: (String, Boolean) -> Unit) {
    var showOpen by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(showOpen, tick) {
        Support.adminList(showOpen) { r -> r.onSuccess { list = JSONArray(it).objs() }.onFailure { report(it.message ?: "", false) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Açık", color = if (showOpen) Px.Green else Px.Dark, height = 34.dp, size = 12.sp) { showOpen = true }
        PxButton("Yanıtlanan", color = if (!showOpen) Px.Green else Px.Dark, height = 34.dp, size = 12.sp) { showOpen = false }
    }
    Spacer(Modifier.height(6.dp))
    if (list.isEmpty()) PT(if (showOpen) "Açık destek talebi yok" else "Yanıtlanan talep yok", 13.sp, Px.Sub)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in list) SupportTicket(o, showOpen, open, report) { tick++ }
    }
}

@Composable
private fun SupportTicket(o: JSONObject, open: Boolean, openPlayer: (Long) -> Unit, report: (String, Boolean) -> Unit, changed: () -> Unit) {
    var reply by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<ImageBitmap>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val paths = o.optJSONArray("paths")?.length() ?: 0
    var big by remember { mutableStateOf<ImageBitmap?>(null) }
    big?.let { b ->
        Dim({ big = null }) {
            androidx.compose.foundation.Image(b, null, Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
        }
    }
    Column(Modifier.fillMaxWidth().pxPanel(Px.PanelDark).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PT("#${o.optLong("id")} · ${o.optString("topic")}", 14.sp, Px.Clan, Modifier.weight(1f))
            PT(ts(o.optLong("at")), 11.sp, Px.Sub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountCard(o.optJSONObject("from"), "Gönderen", openPlayer, Modifier.width(220.dp))
            Column(Modifier.weight(1f)) {
                PT(o.optString("body"), 12.sp, Px.Text, maxLines = 12)
                if (paths > 0) {
                    Spacer(Modifier.height(4.dp))
                    val imgs = images
                    if (imgs == null) PxButton(if (loading) "Yükleniyor…" else "$paths Fotoğrafı Göster", color = Px.Blue, height = 30.dp, size = 11.sp, enabled = !loading) {
                        loading = true
                        Support.adminPhotos(o.optLong("id")) { r ->
                            r.onSuccess { urls ->
                                com.zenvex.online.net.Supa.bg {
                                    val out = urls.mapNotNull { u ->
                                        runCatching { java.net.URL(u).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
                                    }
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { images = out; loading = false }
                                }
                            }.onFailure { loading = false; report(it.message ?: "", false) }
                        }
                    } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (imgs.isEmpty()) PT("Fotoğraflar Açılamadı", 11.sp, Px.Hard)
                        // dokununca tam ekran
                        for (b in imgs) androidx.compose.foundation.Image(b, null, Modifier.height(140.dp).width(100.dp).clickable { big = b }, contentScale = ContentScale.Fit)
                    }
                } else if (o.optInt("photos") > 0) {
                    Spacer(Modifier.height(4.dp))
                    PT("${o.optInt("photos")} Fotoğraf Eklenmişti Ama Yüklenemedi", 11.sp, Px.Hard)
                }
            }
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            Field(reply, { if (it.length <= 2000) reply = it }, "Yanıt (posta olarak gider)", Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            PxButton("Yanıtla ve Kapat", color = Px.Green, height = 34.dp, size = 12.sp, enabled = reply.isNotBlank()) {
                Support.adminReply(o.optLong("id"), reply) { r -> r.onSuccess { report(it, true); changed() }.onFailure { report(it.message ?: "", false) } }
            }
        } else PT("Yanıt: ${o.optString("reply")}", 11.sp, Px.Sub, maxLines = 4)
    }
}

/** Yönetici Ekonomi sekmesi (R45): satışlar, sunucunun verdiği altın/elmas, ürün dağılımı, son satın almalar, en zenginler */
@Composable
internal fun AdminEconomyTab(open: (Long) -> Unit, report: (String, Boolean) -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        com.zenvex.online.net.Supa.rpc("zx_admin_economy", Account.authJson()) { r ->
            r.onSuccess { d = JSONObject(it) }.onFailure { report(it.message ?: "", false) }
        }
    }
    val o = d ?: run { PT("Yükleniyor…", 13.sp, Px.Sub); return }
    fun money(j: JSONObject?) = "${j?.optInt("n") ?: 0} satış · ₺${"%.2f".format(j?.optDouble("try") ?: 0.0).replace('.', ',')}"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, k) in listOf("Son 24 Saat" to "sales_24h", "Son 7 Gün" to "sales_7d", "Tümü" to "sales_all")) {
            Column(Modifier.weight(1f).pxPanel(Px.PanelDark).padding(8.dp)) { PT(label, 11.sp, Px.Sub); PT(money(o.optJSONObject(k)), 14.sp, Px.Clan) }
        }
        val g = o.optJSONObject("grants_24h")
        Column(Modifier.weight(1f).pxPanel(Px.PanelDark).padding(8.dp)) {
            PT("Sunucu Verdi (24 Saat)", 11.sp, Px.Sub)
            PT("${fmtMoney(g?.optLong("gold") ?: 0)} Altın · ${g?.optLong("gems") ?: 0} Elmas", 13.sp, Px.Text)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            PT("En Zengin 20", 14.sp, Px.Clan)
            for ((i, r) in o.optJSONArray("richest").objs().withIndex()) {
                Row(Modifier.fillMaxWidth().clickable { open(r.optLong("id")) }.padding(vertical = 2.dp)) {
                    PT("${i + 1}. ${r.optString("name")} (${r.optString("server")}, Sv ${r.optInt("level")})", 12.sp, Px.Text, Modifier.weight(1f), maxLines = 1)
                    PT("${fmtMoney(r.optLong("gold"))} · ${r.optLong("gems")}◆", 12.sp, Px.Clan)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            PT("Ürünler", 14.sp, Px.Clan)
            val ps = o.optJSONArray("products").objs()
            if (ps.isEmpty()) PT("Henüz satış yok", 12.sp, Px.Sub)
            for (p in ps) PT("${p.optString("id")}: ${p.optInt("n")} · ₺${"%.2f".format(p.optDouble("try")).replace('.', ',')}", 12.sp, Px.Text)
            Spacer(Modifier.height(6.dp))
            PT("Son Satın Almalar", 14.sp, Px.Clan)
            for (p in o.optJSONArray("recent").objs()) {
                PT("${ts(p.optLong("at"))} · ${p.optString("name")} · ${p.optString("product")}" + if (p.optBoolean("test")) " (test)" else "", 11.sp,
                    if (p.optBoolean("test")) Px.Sub else Px.Text, maxLines = 1)
            }
        }
    }
}
