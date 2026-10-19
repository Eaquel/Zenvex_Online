package com.zenvex.online.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.BuildConfig
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

val HERO_IDS = listOf("kaan", "nova", "rady", "elif")
val HERO_COLORS = listOf(Color(0xFFFF7A4A), Color(0xFF4AD8FF), Color(0xFFB277FF), Color(0xFF5BE38A))

fun fmt(n: Int): String = "%,d".format(n).replace(',', '.')

/** 6 039 464 biçimi */
fun fmtId(id: Long): String = id.toString().reversed().chunked(3).joinToString(" ").reversed()

/**
 * Lobi: kayan dünya haritası üstünde solda büyük karakter vitrini, sağda oyuncu kartı ve OYNA.
 * Envanter, üretim, görevler vb. oyunun içinde.
 */
@Composable
fun LobbyScreen(go: (Screen) -> Unit, epoch: Int) {
    val t = rememberTime()
    var tick by remember { mutableIntStateOf(0) }
    val prof = remember(epoch, tick) { GameSession.ui("profile") }
    val chars = remember(epoch, tick) { GameSession.ui("characters") }
    var dialog by remember { mutableStateOf(if (!Account.loggedIn && !GameSession.prefs().getBoolean("guest_ok", false)) "welcome" else "") }
    var devTaps by remember { mutableIntStateOf(0) }
    val sel = 0
    var dir by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { Account.servers(); if (Account.loggedIn) Account.refresh() }
    val ch = chars.optJSONArray("chars")?.optJSONObject(sel) ?: JSONObject()
    val unlocked = ch.optBoolean("unlocked", sel == 0)
    val col = Color(0xFFB277FF)

    MapBackground(t, dim = 0.38f) {
        // ---- sol: karakter vitrini (sürükleyerek döndür)
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.56f).pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures { _, dx -> acc += dx; if (kotlin.math.abs(acc) > 36f) { dir = (dir + (if (acc > 0) 7 else 1)) % 8; acc = 0f } }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // karakter rengiyle ışık huzmesi
                val c = center.copy(y = size.height * 0.62f)
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), c, size.height * 0.45f), size.height * 0.45f, c)
            }
            Box(Modifier.align(Alignment.Center).padding(top = 30.dp).size(330.dp, 380.dp)) { HeroSprite(sel, dir, t, unlocked) }
            // tek kahraman: Savaşçı (karakter seviyesi oyunda dolar)
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PT(ch.optString("name").uppercase(), 28.sp, Color(0xFFB277FF), outline = true)
                PT(ch.optString("class") + " · Karakter Sv ${ch.optInt("level")}/${ch.optInt("max", 10)}", 13.sp, Px.Text)
                PT("Takla: bakılan yöne yuvarlan, hasardan kaç", 12.sp, Px.Sub)
            }
        }
        // ---- sol üst: logo
        Column(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp)) {
            PT("ZENVEX", 46.sp, Px.Orange, outline = true)
            PT("O N L I N E", 17.sp, Px.Clan, outline = true)
        }
        // ---- sağ üst: kısayollar
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountCard(Modifier) { if (Account.loggedIn) go(Screen.Settings) else dialog = "welcome" }
            PxIcon("trophy", size = 48.dp, tint = Px.Clan) { dialog = "top" }
            PxIcon("people", size = 48.dp) { dialog = "clans" }
            PxIcon("info", size = 48.dp) { dialog = "news" }
            PxIcon("gear", size = 48.dp, color = Px.Blue) { go(Screen.Settings) }
        }
        // ---- sağ: oyuncu kartı + OYNA
        PxPanel(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, top = 36.dp).width(340.dp), pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(prof.optInt("level", 1), size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Account.clan.value?.let { PT(it.name, 14.sp, Px.Clan) }
                    PT(Account.name.value.ifEmpty { prof.optString("name") }, 20.sp, Px.Name)
                }
                Icon("coin", 22.dp, Pal.Gold)
                PT(" " + fmt(prof.optInt("gold")), 15.sp, Px.Clan)
            }
            Spacer(Modifier.height(6.dp))
            PxBar(prof.optInt("xp") / prof.optInt("xp_next", 1).toFloat().coerceAtLeast(1f), Color(0xFFE8C840), Modifier.fillMaxWidth(), 12.dp)
            PT("${fmt(prof.optInt("xp"))} / ${fmt(prof.optInt("xp_next"))} XP", 12.sp, Px.Sub)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().background(Px.Row).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                val n = Account.online[GameSession.server.value.id] ?: 0
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (Account.status.value == "Çevrimiçi") Px.Name else Px.Sub))
                Spacer(Modifier.width(8.dp))
                PT(GameSession.server.value.title, 16.sp, modifier = Modifier.weight(1f))
                PT(if (n > 0) "$n çevrimiçi" else "", 13.sp, Px.Name)
            }
            Spacer(Modifier.height(10.dp))
            when {
                !unlocked -> {
                    val can = ch.optBoolean("can_unlock")
                    PxButton("Aç · Sv ${ch.optInt("req")} · ${ch.optInt("gold")} altın", Modifier.fillMaxWidth(), if (can) Px.Orange else Px.Dark,
                        icon = "lock", size = 16.sp, textColor = if (can) Color(0xFF2A1A04) else Color.White) {
                        val r = GameSession.cmd("unlock_char", "i" to sel); Toaster.show(r.msg, r.ok); if (r.ok) tick++
                    }
                }
                prof.optInt("character") != sel -> PxButton("Bu karakteri seç", Modifier.fillMaxWidth(), Px.Blue, size = 18.sp, height = 52.dp) {
                    val r = GameSession.cmd("select_char", "i" to sel); Toaster.show(r.msg, r.ok); tick++
                }
                else -> PxButton("OYNA", Modifier.fillMaxWidth(), Px.Green, icon = "play", size = 32.sp, height = 64.dp) { go(Screen.Loading) }
            }
        }
        // ---- sağ alt: sürüm (7 dokunuş → geliştirici menüsü)
        PT("v${BuildConfig.VERSION_NAME}", 13.sp, Px.Sub, Modifier.align(Alignment.BottomEnd).padding(10.dp)
            .clickable(remember { MutableInteractionSource() }, null) {
                devTaps++
                if (devTaps >= 7) { devTaps = 0; dialog = "dev" } else if (devTaps >= 4) Toaster.show("Geliştirici menüsüne ${7 - devTaps} dokunuş")
            })

        when (dialog) {
            "welcome" -> AccountDialog(first = true) { dialog = ""; tick++ }
            "top" -> TopDialog { dialog = "" }
            "clans" -> ClanDialog { dialog = "" }
            "news" -> NewsDialog { dialog = "" }
            "dev" -> DevMenu(onClose = { dialog = ""; tick++ })
        }
    }
}


@Composable
private fun HeroSprite(sel: Int, dir: Int, t: Float, unlocked: Boolean) {
    val ctx = LocalContext.current
    val frame = ((t * 6f).toInt()) % 6
    val key = "l/${HERO_IDS[sel]}/0_$frame"  // tek görünüm; sağa bakış aynalanır
    val flip = dir in 1..3
    val img = remember(key) { SpriteCache.get(ctx, key) ?: SpriteCache.get(ctx, "l/${HERO_IDS[sel]}/0_0") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(Color(0x66000000), androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.8f),
                androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.08f))
        }
        if (img != null) {
            val dark = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.12f, 0.12f, 0.15f, 1f) }) }
            Image(img, null, Modifier.fillMaxSize().padding(bottom = 26.dp).graphicsLayer { scaleX = if (flip) -1f else 1f },
                filterQuality = FilterQuality.None, colorFilter = if (unlocked) null else dark)
        }
        if (!unlocked) Icon("lock", 44.dp, Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.Center))
    }
}

@Composable
private fun AccountCard(modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.pxPanel(Color(0xE01E1D19)).clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF5A3A2A)), contentAlignment = Alignment.Center) {
            Icon("user", 28.dp, Color(0xFFE8D8C8))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            if (Account.loggedIn) {
                PT("ID: ${fmtId(Account.id.value)}", 15.sp)
                PT(Account.status.value.ifEmpty { "Çevrimiçi" }, 12.sp, if (Account.status.value == "Çevrimiçi") Px.Name else Px.Sub)
            } else {
                PT("Misafir", 15.sp)
                PT("Hesap aç → ilerlemen kaybolmasın", 12.sp, Px.Clan)
            }
        }
    }
}

// =========================================================================== hesap diyaloğu
/** Hesap aç / giriş yap. [first]: oyunun ilk açılışı (misafir devam seçeneği var). */
@Composable
fun AccountDialog(first: Boolean = false, close: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0 yeni hesap, 1 giriş
    var nick by remember { mutableStateOf(GameSession.ui("profile").optString("name")) }
    var idText by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    Dim {
        PxPanel(Modifier.width(440.dp).clickable(remember { MutableInteractionSource() }, null) {}, pad = 14.dp) {
            if (newKey.isNotEmpty()) {
                PT("Hesabın hazır!", 22.sp, Px.Name)
                Spacer(Modifier.height(6.dp))
                PT("Zenvex ID: ${fmtId(Account.id.value)}", 18.sp)
                PT("Hesap anahtarı: $newKey", 18.sp, Px.Clan)
                Spacer(Modifier.height(6.dp))
                PT("Bu ikisini bir yere not et. Telefon değişirse bunlarla girersin.", 13.sp, Px.Sub, maxLines = 3)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PxButton("Kopyala", Modifier.weight(1f), Px.Dark) {
                        copy(ctx, "Zenvex ID: ${Account.id.value}  Anahtar: $newKey"); Toaster.show("Kopyalandı")
                    }
                    PxButton("Tamam", Modifier.weight(1f)) { close() }
                }
                return@PxPanel
            }
            PxTabs(listOf("Yeni hesap", "Giriş yap"), mode) { mode = it; err = "" }
            Spacer(Modifier.height(10.dp))
            if (mode == 0) {
                PT("Oyuncu adın", 14.sp, Px.Sub)
                Field(nick, { if (it.length <= 16) nick = it }, "İsim (3-16)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                PxButton(if (busy) "Bekle…" else "Hesap Aç", Modifier.fillMaxWidth(), enabled = !busy) {
                    busy = true; err = ""
                    Account.register(nick) { e ->
                        busy = false
                        if (e != null) err = e else {
                            newKey = Account.key
                            GameSession.prefs().edit().putBoolean("guest_ok", true).apply()
                            GameSession.cmd("set_name", "name" to Account.name.value)
                            GameSession.save(cloud = true)
                        }
                    }
                }
            } else {
                PT("Zenvex ID", 14.sp, Px.Sub)
                Field(idText, { idText = it.filter { c -> c.isDigit() }.take(12) }, "örn: 1000001", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                PT("Hesap anahtarı", 14.sp, Px.Sub)
                Field(keyText, { keyText = it.take(16) }, "12 harfli anahtar", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                PxButton(if (busy) "Bekle…" else "Giriş Yap", Modifier.fillMaxWidth(), enabled = !busy) {
                    val id = idText.toLongOrNull() ?: run { err = "ID'yi yaz"; return@PxButton }
                    busy = true; err = ""
                    Account.login(id, keyText) { e ->
                        if (e != null) { busy = false; err = e } else {
                            GameSession.prefs().edit().putBoolean("guest_ok", true).apply()
                            GameSession.reload { busy = false; Toaster.show("Hoş geldin ${Account.name.value}!"); close() }
                        }
                    }
                }
            }
            if (err.isNotEmpty()) { Spacer(Modifier.height(6.dp)); PT(err, 14.sp, Color(0xFFFF7A6A), maxLines = 3) }
            Spacer(Modifier.height(8.dp))
            PxButton(if (first) "Misafir olarak devam et" else "Kapat", Modifier.fillMaxWidth(), Px.Dark, size = 15.sp, height = 38.dp) {
                GameSession.prefs().edit().putBoolean("guest_ok", true).apply(); close()
            }
        }
    }
}

fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("zenvex", text))
}

// =========================================================================== küçük pencereler
@Composable
fun PxDialog(title: String, close: () -> Unit, width: Dp = 480.dp, content: @Composable () -> Unit) {
    Dim(close) {
        PxPanel(Modifier.width(width).clickable(remember { MutableInteractionSource() }, null) {}, pad = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT(title, 22.sp, Px.Orange)
                Spacer(Modifier.weight(1f))
                PxIcon("close", size = 36.dp) { close() }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun TopDialog(close: () -> Unit) {
    val sid = GameSession.server.value.id
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(sid) { rows = null; Account.top(sid) { r -> r.onSuccess { rows = it }.onFailure { err = it.message ?: "" } } }
    PxDialog("Sıralama", close) {
        Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
            val r = rows
            when {
                r == null -> PT(if (err.isNotEmpty()) err else "Yükleniyor…", 15.sp, Px.Sub, maxLines = 3)
                r.length() == 0 -> PT("Henüz kimse yok. İlk sen ol!", 15.sp, Px.Sub)
                else -> for (i in 0 until r.length()) {
                    val o = r.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Px.Row else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PT("${i + 1}.", 15.sp, if (i < 3) Px.Clan else Px.Sub, Modifier.width(36.dp))
                        if (o.optString("clan").isNotEmpty() && o.optString("clan") != "null") { PT(o.optString("clan"), 13.sp, Px.Clan); Spacer(Modifier.width(6.dp)) }
                        PT(o.optString("name"), 15.sp, modifier = Modifier.weight(1f))
                        PT("Sv ${o.optInt("level")}", 15.sp, Px.Level)
                    }
                }
            }
        }
    }
}

@Composable
fun ClanDialog(close: () -> Unit) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    var members by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val my = Account.clan.value
    LaunchedEffect(tick, my?.id) {
        if (my != null) Account.clanMembers(my.id) { r -> r.onSuccess { members = it }.onFailure { err = it.message ?: "" } }
        else Account.clans { r -> r.onSuccess { list = it }.onFailure { err = it.message ?: "" } }
    }
    PxDialog(if (my != null) "Klan: ${my.name}" else "Klanlar", close, 520.dp) {
        if (!Account.loggedIn) { PT("Klanlar için önce hesap aç (lobi → sol alt).", 15.sp, Px.Sub, maxLines = 3); return@PxDialog }
        if (my != null) {
            Column(Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
                val m = members
                if (m == null) PT(err.ifEmpty { "Yükleniyor…" }, 15.sp, Px.Sub)
                else for (i in 0 until m.length()) {
                    val o = m.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Px.Row else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (o.optBoolean("online")) Px.Name else Px.Sub))
                        Spacer(Modifier.width(8.dp))
                        PT(o.optString("name") + if (o.optBoolean("owner")) "  (Lider)" else "", 15.sp, modifier = Modifier.weight(1f))
                        PT("Sv ${o.optInt("level")}", 15.sp, Px.Level)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PxButton("Klandan ayrıl", Modifier.fillMaxWidth(), Px.Red, size = 15.sp, height = 38.dp) {
                Account.leaveClan { e -> if (e != null) Toaster.show(e, false) else { members = null; tick++ } }
            }
        } else {
            Column(Modifier.height(180.dp).verticalScroll(rememberScrollState())) {
                val l = list
                if (l == null) PT(err.ifEmpty { "Yükleniyor…" }, 15.sp, Px.Sub, maxLines = 3)
                else if (l.length() == 0) PT("Henüz klan yok. İlkini sen kur!", 15.sp, Px.Sub)
                else for (i in 0 until l.length()) {
                    val o = l.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Px.Row else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PT(o.optString("name"), 16.sp, Px.Clan, modifier = Modifier.weight(1f))
                        PT("${o.optInt("members")}/30", 14.sp, Px.Sub)
                        Spacer(Modifier.width(8.dp))
                        PxButton("Katıl", height = 32.dp, size = 13.sp) {
                            Account.joinClan(o.optLong("id")) { e -> if (e != null) Toaster.show(e, false) else tick++ }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(newName, { newName = it.take(20) }, "Yeni klan adı", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                PxButton("Kur", height = 44.dp) {
                    Account.createClan(newName, 0) { e -> if (e != null) Toaster.show(e, false) else tick++ }
                }
            }
        }
    }
}

@Composable
fun NewsDialog(close: () -> Unit) {
    PxDialog("Haberler", close, 520.dp) {
        Column(Modifier.height(240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PT("v${BuildConfig.VERSION_NAME}", 18.sp, Px.Clan)
            for (l in listOf(
                "• Yeni lobi ve yeni uygulama simgesi",
                "• Zenvex hesabı: ID + anahtar ile her telefondan gir",
                "• Çevrimiçi dünya Supabase'e taşındı",
                "• Posta kutusu, klanlar, sıralama",
                "• Haritadan bölgeler arası seyahat",
                "• Envanter, üretim, görevler artık oyunun içinde",
            )) PT(l, 15.sp, maxLines = 2)
        }
    }
}

fun frameColor(f: String) = when (f) {
    "frame_bronze" -> Color(0xFFCD7F32); "frame_silver" -> Color(0xFFD8DEE6); "frame_gold" -> Pal.Gold; "frame_s1" -> Pal.Red; else -> Color(0xFF4D9BFF)
}

@Composable
fun Avatar(ch: Int, frame: Color, size: Dp) {
    val ctx = LocalContext.current
    val img = remember(ch) { SpriteCache.get(ctx, "l/${HERO_IDS.getOrElse(ch) { "kaan" }}/0_0") }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(HERO_COLORS.getOrElse(ch) { Pal.Blue }.copy(alpha = 0.6f), Color(0xFF0A101A))))) {
            if (img != null) Canvas(Modifier.fillMaxSize()) {
                // baş-omuz kadrajı
                val srcH = (img.height * 0.4f).toInt()
                val k = size.toPx() * 1.25f / srcH
                val dw = img.width * k
                drawImage(img, androidx.compose.ui.unit.IntOffset(0, 0), androidx.compose.ui.unit.IntSize(img.width, srcH),
                    androidx.compose.ui.unit.IntOffset(((this.size.width - dw) / 2).toInt(), (-this.size.height * 0.02f).toInt()),
                    androidx.compose.ui.unit.IntSize(dw.toInt(), (srcH * k).toInt()), filterQuality = FilterQuality.High)
            }
        }
        FrameRing(frame, Modifier.size(size))
    }
}
