package com.zenvex.online.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sin

// =========================================================================== posta kutusu
/** Posta: yalnız ödüller ve etkinlik duyuruları. 30 gün sonra kendiliğinden silinir; yıldızlananlar kalır. */
@Composable
fun MailScreen(close: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { GameSession.prefs() }
    // ilk açılışta bildirim izni (Android 13+) için özel ekran
    var askNotif by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notif_asked", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
    }
    var list by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        if (Account.loggedIn) Account.mail { r -> r.onSuccess { list = it }.onFailure { err = it.message ?: "" } }
    }
    if (askNotif) {
        NotifPermissionScreen { prefs.edit().putBoolean("notif_asked", true).apply(); askNotif = false }
        return
    }
    MailView(list, err, Account.loggedIn, close,
        claim = { m ->
            Account.claimMail(m.optLong("id")) { r ->
                r.onSuccess { g ->
                    val res = GameSession.cmd("mail_claim", "gold" to g.optInt("gold"), "gems" to g.optInt("gems"), "xp" to g.optInt("xp"),
                        "items" to (g.optJSONArray("items") ?: JSONArray()))
                    Toaster.show(res.msg, res.ok); GameSession.save(cloud = true); tick++
                }.onFailure { Toaster.show(it.message ?: "Hata", false) }
            }
        },
        favorite = { m -> Account.favoriteMail(m.optLong("id"), !m.optBoolean("favorite")) { e -> if (e != null) Toaster.show(e, false); tick++ } },
        remove = { m -> Account.removeMail(m.optLong("id")) { e -> if (e != null) Toaster.show(e, false) else Toaster.show("Posta Silindi"); tick++ } },
    )
}

private fun hasGift(m: JSONObject) = m.optInt("gold") > 0 || m.optInt("gems") > 0 || m.optInt("xp") > 0 || (m.optJSONArray("items")?.length() ?: 0) > 0

private fun daysLeft(m: JSONObject): Int? {
    if (m.isNull("expires") || !m.has("expires")) return null
    return ((m.optLong("expires") - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
}

@Composable
internal fun MailView(
    list: JSONArray?, err: String, logged: Boolean, close: () -> Unit,
    claim: (JSONObject) -> Unit, favorite: (JSONObject) -> Unit, remove: (JSONObject) -> Unit,
) {
    var sel by remember { mutableIntStateOf(0) }
    var confirmDel by remember { mutableStateOf<JSONObject?>(null) }
    PxFrame("Posta Kutusu", "mail", close, right = {
        PT("Ödüller Ve Duyurular · 30 Günde Silinir (Yıldızlılar Kalır)", 12.sp, Px.Sub)
    }) {
        if (!logged) { PT("Posta İçin Hesap Gerekli (Lobi → Profil → Hesap Aç).", 15.sp, Px.Sub, maxLines = 3); return@PxFrame }
        val l = list
        if (l == null || l.length() == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PT(if (l == null) err.ifEmpty { "Yükleniyor…" } else "Posta Kutun Boş", 16.sp, Px.Sub)
            }
            return@PxFrame
        }
        val mails = (0 until l.length()).map { l.getJSONObject(it) }
        val cur = mails.getOrNull(sel.coerceAtMost(mails.size - 1))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyColumn(Modifier.width(330.dp).fillMaxHeight().pxPanel(Px.PanelDark).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(mails.size) { i ->
                    val m = mails[i]
                    Row(Modifier.fillMaxWidth().background(if (i == sel) Color(0x553A6AB0) else Px.Row).clickable { sel = i }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (m.optString("kind")) { "event" -> "calendar"; "clan" -> "people"; "system" -> "info"; else -> "gift" }, 22.dp,
                            if (!m.optBoolean("read")) Px.Clan else Px.Sub)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            PT(m.optString("title"), 15.sp, if (!m.optBoolean("read")) Px.Clan else Px.Text)
                            val d = daysLeft(m)
                            PT(m.optString("from") + if (d != null) " · $d Gün" else " · Kalıcı", 11.sp, if (d != null && d <= 3) Color(0xFFE07060) else Px.Sub)
                        }
                        if (m.optBoolean("favorite")) Icon("star", 18.dp, Px.Clan)
                        if (hasGift(m) && !m.optBoolean("claimed")) Box(Modifier.padding(start = 4.dp).size(8.dp).background(Px.Name))
                    }
                }
            }
            if (cur != null) Column(Modifier.weight(1f).fillMaxHeight().pxPanel(Px.Panel).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT(cur.optString("title"), 20.sp, Px.Orange, Modifier.weight(1f))
                    PxIcon("star", size = 38.dp, tint = if (cur.optBoolean("favorite")) Px.Clan else Px.Sub) { favorite(cur) }
                    Spacer(Modifier.width(6.dp))
                    PxIcon("trash", size = 38.dp, tint = Color(0xFFE07060)) {
                        if (hasGift(cur) && !cur.optBoolean("claimed")) confirmDel = cur else remove(cur)
                    }
                }
                PT("Gönderen: ${cur.optString("from")}", 12.sp, Px.Sub)
                Spacer(Modifier.height(8.dp))
                PT(cur.optString("body"), 15.sp, Px.Text, maxLines = 8)
                Spacer(Modifier.weight(1f))
                if (hasGift(cur)) {
                    PT("Ekler", 14.sp, Px.Clan)
                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cur.optInt("gold") > 0) GiftBox("coin", "${fmt(cur.optInt("gold"))}", Px.Clan)
                        if (cur.optInt("gems") > 0) GiftBox("gem", "${cur.optInt("gems")}", Color(0xFF7FE0FF), bound = true)
                        if (cur.optInt("xp") > 0) GiftBox("star", "${cur.optInt("xp")} XP", Px.Level)
                        val items = cur.optJSONArray("items") ?: JSONArray()
                        for (k in 0 until items.length()) {
                            val it = items.getJSONObject(k)
                            PxSlot(JSONObject().put("id", it.optString("id")).put("count", it.optInt("n")), 52.dp)
                        }
                    }
                    if (cur.optBoolean("claimed")) PT("Alındı", 15.sp, Px.Sub)
                    else PxButton("Ekleri Al", Modifier.fillMaxWidth(), height = 44.dp, size = 17.sp, icon = "gift") { claim(cur) }
                }
            }
        }
    }
    confirmDel?.let { m ->
        PxDialog("Posta Silinsin Mi?", { confirmDel = null }) {
            PT("Bu postanın ekleri henüz alınmadı. Silersen ekler kaybolur.", 14.sp, Px.Text, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PxButton("Vazgeç", Modifier.weight(1f), Px.Dark, height = 40.dp, size = 15.sp) { confirmDel = null }
                PxButton("Sil", Modifier.weight(1f), Px.Red, height = 40.dp, size = 15.sp) { remove(m); confirmDel = null }
            }
        }
    }
}

@Composable
private fun GiftBox(icon: String, v: String, col: Color, bound: Boolean = false) {
    Column(Modifier.pxPanel(Px.PanelDark).padding(horizontal = 8.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 20.dp, col); Spacer(Modifier.width(4.dp)); PT(v, 14.sp, col)
        }
        // hesaba bağlı ödül simgesi: kilit + yazı
        if (bound) Row(verticalAlignment = Alignment.CenterVertically) { Icon("lock", 11.dp, Px.Sub); PT(" Hesaba Bağlı", 9.sp, Px.Sub) }
    }
}

// =========================================================================== bildirim izni
/**
 * Bildirim izni ekranı: piksel posta güvercini zarfı taşıyarak süzülür, zil sallanır. "İzin Ver" sistem iznini ister.
 */
@Composable
fun NotifPermissionScreen(done: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Toaster.show(if (ok) "Bildirimler Açık" else "Bildirimleri Ayarlardan Açabilirsin", ok); done()
    }
    NotifPermissionView(
        allow = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else done() },
        later = done,
    )
}

@Composable
internal fun NotifPermissionView(allow: () -> Unit, later: () -> Unit) {
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Color(0xE0101014)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(520.dp).pxPanel(Px.Panel).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val px = 6.dp.toPx()
                fun block(x: Int, y: Int, c: Color, ox: Float, oy: Float) = drawRect(c, Offset(ox + x * px, oy + y * px), Size(px, px))
                // gökyüzü şeridi + yıldızlar
                for (k in 0 until 18) {
                    val a = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 2f + k))
                    drawRect(Color(0xFFFFF2B0).copy(alpha = a), Offset((k * 97f) % size.width, (k * 53f) % (size.height * 0.7f)), Size(px * 0.6f, px * 0.6f))
                }
                // zil: sağa sola sallanır
                val bx = size.width * 0.72f; val by = size.height * 0.2f
                val swing = sin(t * 7f) * 10f
                rotateBell(swing, bx + 4 * px, by) {
                    val bell = listOf("..XXXX..", ".XYYYYX.", ".XYYYYX.", "XYYYYYYX", "XYYYYYYX", "XXXXXXXX", "...XX...")
                    bell.forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
                        if (ch == 'X') block(c, r, Color(0xFF8A5A10), bx, by) else if (ch == 'Y') block(c, r, Color(0xFFFFC83A), bx, by)
                    } }
                }
                // zarf: yukarı aşağı süzülerek gelir
                val ex = size.width * 0.22f + sin(t * 1.3f) * 30f; val ey = size.height * 0.35f + sin(t * 2.6f) * 10f
                val env = listOf("XXXXXXXXXXXX", "XWWWWWWWWWWX", "XRWWWWWWWWRX", "XWRWWWWWWRWX", "XWWRRWWRRWWX", "XWWWWRRWWWWX", "XWWWWWWWWWWX", "XXXXXXXXXXXX")
                env.forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
                    val col = when (ch) { 'X' -> Color(0xFF3A2A1A); 'W' -> Color(0xFFF2E8D0); 'R' -> Color(0xFFD04A3A); else -> Color.Transparent }
                    if (col != Color.Transparent) block(c, r, col, ex, ey)
                } }
                // ses dalgaları
                for (k in 0..2) {
                    val ph = ((t * 1.2f + k / 3f) % 1f)
                    drawCircle(Color(0xFFFFC83A).copy(alpha = (1 - ph) * 0.6f), px * (3 + ph * 8), Offset(bx + 4 * px, by + 3 * px), style = androidx.compose.ui.graphics.drawscope.Stroke(px * 0.5f))
                }
            }
            PT("Haberdar Ol!", 26.sp, Px.Orange, outline = true)
            Spacer(Modifier.height(6.dp))
            PT("Ödül postaları, etkinlik duyuruları, arkadaşlık istekleri ve klan davetleri geldiğinde sana haber verelim mi?", 15.sp, Px.Text,
                maxLines = 3)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PxButton("Şimdi Değil", color = Px.Dark, height = 46.dp, size = 16.sp) { later() }
                PxButton("İzin Ver", height = 46.dp, size = 18.sp, icon = "check") { allow() }
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateBell(deg: Float, px: Float, py: Float, block: () -> Unit) {
    drawContext.canvas.save()
    drawContext.transform.rotate(deg, Offset(px, py))
    block()
    drawContext.canvas.restore()
}
