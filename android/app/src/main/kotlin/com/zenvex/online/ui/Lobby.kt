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
import kotlin.math.sin
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope

val HERO_IDS = listOf("kaan", "nova", "rady", "elif")
val HERO_COLORS = listOf(Color(0xFFFF7A4A), Color(0xFF4AD8FF), Color(0xFFB277FF), Color(0xFF5BE38A))

fun fmt(n: Int): String = "%,d".format(n).replace(',', '.')

/** 6 039 464 biçimi */
/** 7 haneli gösterim: 1 → "0 000 001", yönetici 0 → "0 000 000"; hesap yoksa "—" */
fun fmtId(id: Long): String = if (id < 0) "—" else id.toString().padStart(7, '0').reversed().chunked(3).joinToString(" ").reversed()

/**
 * Lobi: kayan dünya haritası üstünde solda büyük karakter vitrini, sağda oyuncu kartı ve OYNA.
 * Envanter, üretim, görevler vb. oyunun içinde.
 */
@Composable
fun LobbyScreen(go: (Screen) -> Unit, epoch: Int) {
    var tick by remember { mutableIntStateOf(0) }
    val prof = remember(epoch, tick) { GameSession.ui("profile") }
    // oyun tamamen çevrimiçi: hesapsız oynanmaz (misafir yok)
    var dialog by remember { mutableStateOf(if (!Account.loggedIn) "welcome" else "") }
    var devTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Account.servers(); if (Account.loggedIn) Account.refresh() }
    // oyundan atıldıysak (başka cihaz, bakım, yasak, bağlantı) nedeni göster
    val kicked = com.zenvex.online.game.Session.kicked.value
    LaunchedEffect(kicked) { if (kicked != null) { Toaster.show(kicked, false); com.zenvex.online.game.Session.kicked.value = null } }

    // sahne: oyunun kendi kampı (oyun motorundan çekilmiş gece görüntüsü, yavaşça kayar) + önünde senin 3B kahramanın,
    // elindeki silah ve sırtındaki kanatla; kamp görseli yoksa eski kodla çizilen sahne
    val ctx = LocalContext.current
    val camp = remember { UiArt.has(ctx, "bg_lobby_camp") }
    val t = rememberTime()
    val scene: @Composable (@Composable BoxScope.() -> Unit) -> Unit = { body ->
        if (camp) SceneBackground("bg_lobby_camp", t, dim = 0.18f, zoom = 1.08f, drift = 0.35f) {
            // kenar karartma + kor ışığı: arayüz okunur kalsın, sahne sıcak dursun
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color(0xCC07060A), Color(0x3307060A), Color(0x00000000), Color(0x8807060A))))
                drawRect(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x00000000), Color(0x00000000), Color(0x99000000))))
                val k = 0.5f + 0.5f * sin(t * 2.3f) * sin(t * 0.7f + 1f)
                drawCircle(Brush.radialGradient(listOf(Color(0x33FF8A2A).copy(alpha = 0.16f + 0.08f * k), Color.Transparent), Offset(size.width * 0.36f, size.height * 0.78f), size.height * 0.55f),
                    size.height * 0.55f, Offset(size.width * 0.36f, size.height * 0.78f))
            }
            body()
        } else LiveScene(SceneKind.LOBBY) { body() }
    }
    scene {
        // ---- sol: logo + slogan; altta gerçek oyundan alınmış kareler (kahraman seçimi ve satın alma oyunun içinde)
        Column(Modifier.align(Alignment.TopStart).padding(start = 54.dp, top = 66.dp), horizontalAlignment = Alignment.Start) {
            CodeLogo(100.dp)
            Spacer(Modifier.height(6.dp))
            PT("Hayatta Kal. Güçlen. Üssünü Savun.", 16.sp, Color(0xFFFFE2B8), outline = true)
        }
        // ---- orta: senin kahramanın (seçili karakter, kuşanılı silah, kanat) kamp ateşinin başında
        val heroCh = prof.optInt("character", 0)
        val heroWeapon = remember(epoch, tick) { equippedWeaponModel() }
        val heroWings = prof.optString("wings")
        // yer: galerinin sağı ile oyuncu kartının solu arası (kart 340 dp + kenar); dar ekranda küçülür, logoya binmez
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val left = 272.dp
            val room = maxWidth - 372.dp - left
            val w = room.coerceIn(120.dp, 220.dp)
            val h = (maxHeight * 0.64f).coerceAtMost(w * 1.4f)
            Box(Modifier.align(Alignment.BottomStart).padding(start = left + (room - w) / 2, bottom = 10.dp).size(w, h)) {
                Hero3DView("lobby", heroCh, 1, t, true, heroWeapon, Modifier.fillMaxSize(), wings = heroWings)
            }
        }
        ShotGallery(Modifier.align(Alignment.BottomStart).padding(start = 54.dp, bottom = 12.dp))
        // ---- sol üst: profil (fotoğraf, ad, ID, etiketler) → dokununca profil düzenleme
        val pp = if (Account.loggedIn) Account.profile.value else JSONObject().put("name", prof.optString("name")).put("level", prof.optInt("level", 1))
        ProfileBadge(pp, !Account.loggedIn, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp)) {
            dialog = if (Account.loggedIn) "profile" else "welcome"
        }
        // ---- sağ üst: kısayollar
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PxIcon("trophy", size = 48.dp, tint = Px.Clan) { dialog = "top" }
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
            PxButton("OYNA", Modifier.fillMaxWidth(), Px.Green, icon = "play", size = 32.sp, height = 64.dp) { if (Account.loggedIn) go(Screen.Loading) else dialog = "welcome" }
        }
        // ---- sağ alt: sürüm (7 dokunuş → geliştirici menüsü)
        PT(appVersion(), 13.sp, Px.Sub, Modifier.align(Alignment.BottomEnd).padding(10.dp)
            .clickable(remember { MutableInteractionSource() }, null) {
                devTaps++
                if (devTaps >= 7) { devTaps = 0; dialog = "dev" } else if (devTaps >= 4) Toaster.show("Geliştirici menüsüne ${7 - devTaps} dokunuş")
            })

        when (dialog) {
            "welcome" -> AccountDialog(first = true) { dialog = if (Account.loggedIn) "" else "welcome"; tick++ }
            "top" -> TopDialog { dialog = "" }
            "news" -> NewsDialog { dialog = "" }
            "dev" -> DevMenu(onClose = { dialog = ""; tick++ })
            "profile" -> ProfileEditor({ dialog = "" }) { dialog = "rename" }
            "rename" -> RenameDialog { dialog = "profile" }
        }
    }
}


@Composable
internal fun HeroSprite(sel: Int, dir: Int, t: Float, unlocked: Boolean, actor: String = "lobby", weapon: String = "") {
    // 3B vitrin (modeller yüklendiyse); kilit simgesi üstte
    Box(Modifier.fillMaxSize()) {
        if (Hero3DView(actor, sel, dir, t, unlocked, weapon, Modifier.fillMaxSize().padding(bottom = 26.dp))) {
            if (!unlocked) Icon("lock", 44.dp, Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.Center))
            return@Box
        }
        HeroSpritePixel(sel, dir, t, unlocked)
    }
}

@Composable
private fun HeroSpritePixel(sel: Int, dir: Int, t: Float, unlocked: Boolean) {
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
                PT("Giriş Yapılmadı", 15.sp)
                PT("Oynamak için hesabına gir", 12.sp, Px.Clan)
            }
        }
    }
}

// =========================================================================== hesap diyaloğu
/** Hesap aç / giriş yap. [first]: hesap yokken açılır ve hesap olmadan kapanmaz (oyun tamamen çevrimiçi). */
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
            // Google ile tek dokunuşla giriş (yoksa hesap açılır)
            PxButton(if (busy) "Bekle…" else "Google ile devam et", Modifier.fillMaxWidth(), Color(0xFFE8E4D4), icon = "user",
                textColor = Color(0xFF1E1D19), enabled = !busy) {
                val act = ctx as? android.app.Activity ?: return@PxButton
                busy = true; err = ""
                com.zenvex.online.net.GoogleSignIn.start(act, link = false) { e ->
                    if (e != null) { busy = false; err = e } else {
                        GameSession.prefs().edit().putBoolean("guest_ok", true).apply()
                        GameSession.reload { busy = false; Toaster.show("Hoş geldin ${Account.name.value}!"); close() }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
                Field(idText, { idText = it.filter { c -> c.isDigit() }.take(12) }, "örn: 0000001", Modifier.fillMaxWidth())
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
            if (first) {
                Spacer(Modifier.height(8.dp))
                PT("Zenvex tamamen çevrimiçi bir oyundur: ilerlemen sunucuda saklanır, her telefondan devam edersin.", 12.sp, Px.Sub, maxLines = 3)
            } else {
                Spacer(Modifier.height(8.dp))
                PxButton("Kapat", Modifier.fillMaxWidth(), Px.Dark, size = 15.sp, height = 38.dp) { close() }
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
    // pencere dışına dokunmak kapatmaz (yanlışlıkla kapanıyordu): yalnız X ya da geri tuşu
    Dim {
        // perçinli metal çerçeveli pencere (köşeler esnemez)
        // V10 kurukafa köşeli pencere çerçevesi (yoksa eski metal çerçeve)
        Column(Modifier.width(width).metalFrame(px = 0.9.dp, name = "frame_panel", corner = 32)
            .clickable(remember { MutableInteractionSource() }, null) {}.padding(horizontal = 30.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT(title, 22.sp, Px.Orange)
                Spacer(Modifier.weight(1f))
                CloseButton(36.dp) { close() }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun TopDialog(close: () -> Unit) = BoardDialog(close)

/** Ekranlarda görünen sürüm: "Beta 0.3.12" */
fun appVersion(): String = "Beta " + BuildConfig.VERSION_NAME.substringBefore("-")

/** Haberler: oyunun amacı, nasıl oynanır, ilerleme ve bu sürümdeki yenilikler. */
@Composable
fun NewsDialog(start: Int = 0, close: () -> Unit) {
    var tab by remember { mutableIntStateOf(start) }
    PxDialog("Bilgi Ve Haberler", close, 640.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Amaç", "Nasıl Oynanır", "İlerleme", "Yenilikler", "Kurallar").forEachIndexed { i, n ->
                PxButton(n, color = if (i == tab) Px.Blue else Px.Dark, height = 36.dp, size = 13.sp) { tab = i }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 360 dp yüksekliğindeki telefonda da pencere ekrana sığar (içerik kayar)
        Column(Modifier.height(220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 4) PT("Zenvex Online'ı herkes için adil ve keyifli tutmak için bu kurallara uy. Kuralları çiğneyen hesaplara yöneticiler işlem uygular.", 13.sp, Px.Sub, maxLines = 3)
            for ((head, body) in if (tab == 4) RULES else NEWS[tab]) {
                PT(head, 16.sp, Px.Clan)
                PT(body, 14.sp, Px.Text, maxLines = 8)
            }
            if (tab == 3) PT(appVersion() + " · Bu bir Beta sürümüdür: hataları ayarlardan ya da sohbetten bildir.", 12.sp, Px.Sub, maxLines = 2)
        }
    }
}

private val NEWS: List<List<Pair<String, String>>> = listOf(
    listOf(
        "Hayatta Kal" to "Salgından sonra şehir zombilerle doldu. Amacın kamptan başlayarak güçlenmek, malzeme toplamak, eşya üretmek ve haritanın tehlikeli bölgelerini adım adım geri almak.",
        "Güçlen" to "Zombileri öldür, görevleri bitir, seviye atla. Her seviye +5 Can ve +1 Yumruk Hasarı verir; yetenek ağacındaki düğümleri elmasla açarak gücünü, canını ve becerilerini geliştir.",
        "Birlikte Oyna" to "Bir klana katıl ya da kendi klanını kur. Klanınla devlet üslerine baskın yap, üssü ele geçir ve klan deponu doldur.",
    ),
    listOf(
        "Hareket" to "Sol joystick ile yürü; joystick'i sonuna kadar itersen koşarsın. Yürümek ve koşmak dayanıklılık harcamaz.",
        "Saldırı" to "Sağ joystick ile saldır: elin boşsa yumruk, bıçak ya da katana takılıysa kılıç darbesi. Saldırmak ve takla atmak dayanıklılık harcar; dayanıklılık bitince biraz dinlen.",
        "Takla" to "Takla tuşu seni kısa süre hasardan korur ve kuşatmadan kurtarır. Bekleme süresine dikkat et.",
        "NPC'ler" to "Bir NPC'ye dokununca konuşursun. Görevci görev verir, tüccar alır satar, doktor iyileştirir, tamirci aracını onarır, barınaktaki eğitmen köpek satar ve eğitir.",
        "Güvenli Bölge" to "Kamp ve ışınlanma noktaları güvenli bölgedir: zombiler giremez, girerlerse yıldırım çarpar. Can azalınca buraya dön.",
        "Çanta Ve Cepler" to "Çantadan eşyalarını kuşanırsın. İlk dört cep oyunda hızlı kullanım tuşu olur; ilaç ve iksirleri buraya koy.",
    ),
    listOf(
        "Seviye Ve Yetenekler" to "XP ile seviye atlarsın; her seviye +5 Can ve +1 Yumruk Hasarı verir. Yetenek ağacında hasar, can, hız ve takla dallarındaki düğümleri elmasla aç. Ağacı sıfırlarsan harcadığın elmas kredi olarak kalır.",
        "Eşya Üretimi" to "Ağaç, taş ve hurda topla; çalışma tezgâhında silah, ekipman, ilaç ve sandık üret.",
        "Köpek" to "Köpeğini barınaktaki eğitmenden alırsın. Köpeğin seninle savaşır, seviye atlar ve eğitmende geliştirilir.",
        "Devlet Üsleri" to "Haritada askerlerin koruduğu devlet üsleri var. Keşfettiğinde üssün vereceği ödülü ve kalan süreyi haritada görürsün. Klan olarak ele geçir, süre bitene kadar savun.",
        "Klan" to "Klan seviyesi arttıkça üye sınırı, güçlendirmeler ve klan ödülleri açılır. Ele geçirilen üsler klana dakikada XP kazandırır.",
    ),
    listOf(
        "Canlı NPC'ler Ve Kanatlar" to "Marco, Silas, Elena, Viktor, Jack, Aldric, Diana ve üs askerleri artık 3B: yaklaşınca sana döner ve konuşur. VIP 3, 6 ve 9'da sırtına takılan çırpan kanatlar (Karakterler ekranından takılır); Sezon Kartı 30'da Sezon 1 Kanatları.",
        "Her Şeyin Canı Var" to "Silah, alet, zırh, çanta, yüzük ve kolye kullandıkça yıpranır: yuvanın altındaki çubuk kalan canı, eşyaya dokununca yüzdesi görünür. Kırık eşya onarılana dek işe yaramaz; tezgâhta ya da Tamirci Viktor'da onar.",
        "Araçların Canı" to "Araba 500, motosiklet 200 can. Sürerken araç hasarı üstlenir ve yavaşça aşınır; patlarsa araba 60, motosiklet 30 saniye sonra yeniden çağrılır.",
        "Daha Düzenli Ekran" to "Üstteki yazılar artık sırayla gelir, üst üste binmez. Altın ve elmas tek kapsülde, şarjör tuşu saldırı çemberinin yanında. Etkinlikler 10. seviyede açılır.",
        "Aletler Elde Çalışır" to "Balta ve kazma yalnız elindeyken güçlü vurur (taş 50, demir 100). El Baltası kalktı; öğretici artık bıçak yaptırır.",
        "Güvenli Takas" to "Ekipman dayanıklılığıyla takas edilir; karşı teklif değişince onay 3 saniye bekler, onayından sonra teklif değişirse takas tamamlanmaz.",
    ),
)

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

/** İsim değiştirme (profil düzenleyiciden). */
@Composable
fun RenameDialog(close: () -> Unit) {
    var nick by remember { mutableStateOf(Account.name.value) }
    PxDialog("İsim Değiştir", close) {
        Field(nick, { if (it.length <= 16) nick = it }, "Yeni İsim (3-16)", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PxButton("Kaydet", Modifier.fillMaxWidth(), height = 44.dp) {
            Account.rename(nick) { e -> if (e != null) Toaster.show(e, false) else { Toaster.show("İsim Değişti"); Account.refresh(); close() } }
        }
    }
}
