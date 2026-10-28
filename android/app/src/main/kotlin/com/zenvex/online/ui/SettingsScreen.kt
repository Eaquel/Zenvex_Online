package com.zenvex.online.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.BuildConfig
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Settings
import kotlin.math.roundToInt

private const val LEGAL_URL = "https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal"

/** Ayarlar: sekmeler üstte, içerik tek alanda çok sütunlu; hiçbir sekmede kaydırma yok (en küçük yatay ekran 800×360 dp'ye göre). */
@Composable
fun SettingsScreen(back: () -> Unit, inGame: Boolean = false, onReset: () -> Unit = {}, initialTab: Int = 0) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Settings.current) }
    var tab by remember { mutableIntStateOf(initialTab) }
    fun set(n: Settings) { s = n; Settings.save(ctx, n) }
    fun custom(n: Settings) = set(n.copy(preset = 4))
    val tabs = listOf("Grafik", "Kontroller", "Ses", "Bildirimler", "Hesap", "Destek", "Dil · Language", "Yasal")

    PxFrame("Ayarlar", "gear", back) {
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i, n ->
                val on = tab == i
                Box(Modifier.weight(n.length + 5f).fillMaxHeight().background(if (on) Px.Green else Px.Dark).border(2.dp, Px.Edge).clickable { tab = i },
                    contentAlignment = Alignment.Center) { PT(n, 12.sp, if (on) Color.White else Px.Sub) }
            }
        }
        Spacer(Modifier.height(6.dp))
        PxPanel(Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp), pad = 8.dp) {
            when (tab) {
                0 -> Cols {
                    Col {
                        Choice("Grafik Kalitesi", listOf("Düşük", "Orta", "Yüksek", "Ultra", "Özel"), s.preset) { set(s.withPreset(it)) }
                        Choice("FPS Sınırı", listOf("30", "45", "60", "90", "120"), listOf(30, 45, 60, 90, 120).indexOf(s.fpsLimit).coerceAtLeast(0)) {
                            custom(s.copy(fpsLimit = listOf(30, 45, 60, 90, 120)[it]))
                        }
                        Choice("Çözünürlük", listOf("%67", "%80", "%100"), s.resolution) { custom(s.copy(resolution = it)) }
                        Choice("Kamera Uzaklığı", listOf("Yakın", "Normal", "Uzak"), s.zoom) { set(s.copy(zoom = it)) }
                        Choice("Bitki Yoğunluğu", listOf("Düşük", "Orta", "Yüksek"), s.vegetation) { custom(s.copy(vegetation = it)) }
                    }
                    Col {
                        Choice("3B Karakter Kalitesi", listOf("Düşük", "Orta", "Yüksek"), s.model3d) { custom(s.copy(model3d = it)) }
                        Choice("Efekt Kalitesi", listOf("Düşük", "Orta", "Yüksek", "Ultra"), s.fx) { custom(s.copy(fx = it)) }
                        Toggle("Rüzgâr Animasyonu", s.wind) { custom(s.copy(wind = it)) }
                        Toggle("Gölgeler", s.shadows) { custom(s.copy(shadows = it)) }
                        Toggle("Parçacık Efektleri", s.particles) { custom(s.copy(particles = it)) }
                        Toggle("Arkasındayken Ağacı Saydamlaştır", s.treeFade) { set(s.copy(treeFade = it)) }
                    }
                    Col {
                        Slider("Ekran Sarsıntısı", s.screenShake, 0f, 1f, pct = true) { set(s.copy(screenShake = it)) }
                        Toggle("Hasar Sayıları", s.damageNumbers) { set(s.copy(damageNumbers = it)) }
                        Toggle("Pil Tasarrufu (30 FPS)", s.batterySaver) { set(s.copy(batterySaver = it)) }
                        Toggle("FPS Göstergesi", s.showFps) { set(s.copy(showFps = it)) }
                        Toggle("Ping Göstergesi", s.showPing) { set(s.copy(showPing = it)) }
                        Toggle("Oyuncu Unvanlarını Gizle (Kendininki Dahil)", s.hideTitles) { set(s.copy(hideTitles = it)) }
                        Toggle("Ekran Arka Planlarını Şeffaflaştır", s.uiTransparent) { set(s.copy(uiTransparent = it)) }
                    }
                }
                1 -> Cols {
                    Col {
                        Head("Hareket Joysticki")
                        Toggle("Dinamik Joystick (Dokunduğun Yerde Açılır)", s.moveDynamic) { set(s.copy(moveDynamic = it)) }
                        Slider("Hareket Hassasiyeti", s.moveSensitivity, 0.5f, 2f) { set(s.copy(moveSensitivity = it)) }
                        Slider("Hareket Ölü Bölgesi", s.moveDeadZone, 0f, 0.3f, pct = true) { set(s.copy(moveDeadZone = it)) }
                        Slider("Koşma Eşiği", s.runThreshold, 0.4f, 0.9f, pct = true) { set(s.copy(runThreshold = it)) }
                    }
                    Col {
                        Head("Saldırı Joysticki")
                        PT("Çevirdiğin yöne saldırır, basılı tuttukça devam eder.", 11.sp, Px.Sub, maxLines = 3)
                        Slider("Saldırı Hassasiyeti", s.attackSensitivity, 0.5f, 2f) { set(s.copy(attackSensitivity = it)) }
                        Slider("Saldırı Ölü Bölgesi", s.attackDeadZone, 0f, 0.3f, pct = true) { set(s.copy(attackDeadZone = it)) }
                    }
                    Col {
                        Head("Genel")
                        Slider("Joystick Boyutu", s.joystickSize, 0.7f, 1.5f, pct = true) { set(s.copy(joystickSize = it)) }
                        Slider("Joystick Saydamlığı", s.joystickOpacity, 0.2f, 1f, pct = true) { set(s.copy(joystickOpacity = it)) }
                        Choice("Titreşim", listOf("Kapalı", "Hafif", "Güçlü"), s.haptics) { set(s.copy(haptics = it)) }
                        Toggle("Sol El Modu (Joystickleri Değiştir)", s.leftHanded) { set(s.copy(leftHanded = it)) }
                        Spacer(Modifier.weight(1f))
                        PxButton("Varsayılana Dön", Modifier.fillMaxWidth(), color = Px.Dark, icon = "refresh", height = 34.dp, size = 13.sp) {
                            val d = Settings()
                            set(s.copy(moveDynamic = d.moveDynamic, moveSensitivity = d.moveSensitivity, moveDeadZone = d.moveDeadZone,
                                runThreshold = d.runThreshold, attackSensitivity = d.attackSensitivity, attackDeadZone = d.attackDeadZone,
                                joystickSize = d.joystickSize, joystickOpacity = d.joystickOpacity, haptics = d.haptics, leftHanded = d.leftHanded))
                        }
                    }
                }
                2 -> Cols {
                    Col {
                        PT("Müzik lobide ve oyunda çalar; efektler oyun içindir.", 12.sp, Px.Sub, maxLines = 3)
                        Spacer(Modifier.height(6.dp))
                        Slider("Ana Ses", s.master, 0f, 1f, pct = true) { set(s.copy(master = it)) }
                        Slider("Müzik", s.music, 0f, 1f, pct = true) { set(s.copy(music = it)) }
                    }
                    Col {
                        Slider("Efektler", s.sfx, 0f, 1f, pct = true) { set(s.copy(sfx = it)) }
                        Slider("Ortam Sesleri", s.ambient, 0f, 1f, pct = true) { set(s.copy(ambient = it)) }
                        Slider("Arayüz Sesleri", s.uiSound, 0f, 1f, pct = true) { set(s.copy(uiSound = it)) }
                    }
                }
                3 -> NotificationSettings()
                4 -> AccountSettings(inGame, onReset)
                5 -> SupportSection()
                6 -> LanguageSection(s.language) { Settings.markLanguageChosen(ctx); set(s.copy(language = it)) }
                else -> LegalSection()
            }
        }
    }
}

/** Panelin içinde yan yana sütunlar: kalan yüksekliği doldurur, taşmaz. */
@Composable
private fun ColumnScope.Cols(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)

@Composable
private fun RowScope.Col(weight: Float = 1f, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.weight(weight).fillMaxHeight(), content = content)

private fun notifEnabled(ctx: android.content.Context) = runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled() }.getOrDefault(true)

@Composable
private fun NotificationSettings() {
    val ctx = LocalContext.current
    PT("Ödül postaları, etkinlik duyuruları, arkadaşlık istekleri ve klan davetleri için bildirim alırsın.", 13.sp, Px.Text, maxLines = 3)
    Spacer(Modifier.height(10.dp))
    // izin durumu: sistem ayarlarından dönünce de güncellenir
    var allowed by remember { mutableStateOf(notifEnabled(ctx)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            allowed = notifEnabled(ctx)
            kotlinx.coroutines.delay(1000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (allowed) "check" else "mail", 26.dp, if (allowed) Px.Name else Px.Sub)
        Spacer(Modifier.width(8.dp))
        PT(if (allowed) "Bildirimler Açık" else "Bildirimler Kapalı", 16.sp, if (allowed) Px.Name else Color(0xFFFF7A6A))
    }
    Spacer(Modifier.height(8.dp))
    PxButton(if (allowed) "Bildirim Ayarlarını Düzenle" else "Bildirim İzinlerini Aç",
        color = if (allowed) Px.Dark else Px.Blue, icon = "mail", height = 40.dp, size = 15.sp) {
        val i = if (Build.VERSION.SDK_INT >= 26) Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName)
        else Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
        runCatching { ctx.startActivity(i) }
    }
}

/** Dil seçimi: her dil kendi adıyla yazılır (seçen kişi okuyabilsin); seçince tüm arayüz hemen değişir. */
@Composable
private fun ColumnScope.LanguageSection(current: String, pick: (String) -> Unit) {
    PT("Oyunun dili. Oyuncuların sohbet mesajları çevrilmez.", 12.sp, Px.Sub, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    Cols {
        for (chunk in com.zenvex.online.game.I18n.LANGS.entries.toList().chunked(4)) Col {
            for ((code, name) in chunk) {
                val on = code == current
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (on) Px.Green else Px.PanelDark).border(2.dp, Px.Edge)
                    .clickable { pick(code) }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ad çevrilmesin: düz Text
                    androidx.compose.material3.Text(name, color = if (on) Color.White else Px.Text,
                        style = androidx.compose.ui.text.TextStyle(fontFamily = PixelFont, fontSize = 15.sp), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (on) Icon("check", 18.dp, Color.White)
                }
            }
        }
    }
}

@Composable
private fun Head(t: String) = PT(t, 14.sp, Px.Clan, Modifier.padding(top = 2.dp, bottom = 3.dp))

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).clickable { onChange(!value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PT(label, 12.sp, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(8.dp))
        // piksel anahtar: koyu yuva, kayan kare düğme
        Box(Modifier.width(44.dp).height(22.dp).background(Px.Edge).padding(2.dp).background(if (value) Px.Green else Px.Dark).padding(2.dp)) {
            Box(Modifier.align(if (value) Alignment.CenterEnd else Alignment.CenterStart).size(14.dp).background(if (value) Color(0xFFE8F0D0) else Px.Sub)
                .border(1.dp, Px.Edge))
        }
    }
}

@Composable
fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PT(label, 12.sp)
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { i, o ->
                Box(Modifier.weight(1f).background(if (i == selected) Px.Green else Px.PanelDark).border(2.dp, Px.Edge).clickable { onSelect(i) }
                    .padding(vertical = 5.dp), contentAlignment = Alignment.Center) { PT(o, 11.sp, if (i == selected) Color.White else Px.Sub) }
            }
        }
    }
}

@Composable
fun Slider(label: String, value: Float, min: Float, max: Float, pct: Boolean = false, onChange: (Float) -> Unit) {
    var w by remember { mutableIntStateOf(1) }
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    fun pick(x: Float) {
        val f = (x / w).coerceIn(0f, 1f)
        onChange(((min + f * (max - min)) * 100).roundToInt() / 100f)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PT(label, 12.sp, modifier = Modifier.weight(1f))
            PT(if (pct) "%${(value * 100).roundToInt()}" else String.format("%.2f×", value), 12.sp, Px.Clan)
        }
        Box(
            Modifier.fillMaxWidth().height(22.dp).onSizeChanged { w = it.width }
                .pointerInput(min, max) { detectTapGestures { pick(it.x) } }
                .pointerInput(min, max) { detectDragGestures { ch, _ -> pick(ch.position.x) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            PxBar(frac, Px.Clan, Modifier.fillMaxWidth(), 8.dp)
            // düğme ölçülmüş genişliğe bağlı değil (ilk karede 0'a atlıyordu): doluluk oranındaki kutunun ucunda durur
            Box(Modifier.fillMaxWidth(frac).height(20.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).offset(x = 6.dp).size(12.dp, 20.dp).background(Color(0xFFE8E4D4)).border(2.dp, Px.Edge))
            }
        }
    }
}

/** Yasal: gizlilik, kullanım şartları, hakkında (hesap silme talebi, sürüm). Metin sütunlara bölünür, kaydırma yok. */
@Composable
private fun ColumnScope.LegalSection() {
    val ctx = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PxButton("Gizlilik Politikası", height = 30.dp, size = 12.sp, color = if (page == 0) Px.Green else Px.Dark) { page = 0 }
        PxButton("Kullanım Şartları", height = 30.dp, size = 12.sp, color = if (page == 1) Px.Green else Px.Dark) { page = 1 }
        PxButton("Hakkında", height = 30.dp, size = 12.sp, color = if (page == 2) Px.Green else Px.Dark) { page = 2 }
        PxButton("Web'de Aç", height = 30.dp, size = 12.sp, color = Px.Blue) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LEGAL_URL + if (page == 1) "?p=terms" else "?p=privacy"))) }
        }
    }
    Spacer(Modifier.height(6.dp))
    when (page) {
        0, 1 -> {
            val paras = if (page == 0) PRIVACY else TERMS
            // paragraflar sütunlara uzunluklarına göre dengeli dağıtılır
            val total = paras.sumOf { it.length }.coerceAtLeast(1)
            val groups = List(3) { ArrayList<String>() }
            var acc = 0
            for (p in paras) { groups[((acc + p.length / 2) * 3 / total).coerceIn(0, 2)] += p; acc += p.length }
            Cols { for (g in groups) Col { for (p in g) PT(p, 10.sp, Px.Text, Modifier.padding(bottom = 4.dp), maxLines = 20) } }
        }
        else -> Cols {
            Col {
                PT("Zenvex Online", 20.sp, Px.Orange)
                PT("Sürüm: ${appVersion()}", 12.sp, Px.Sub)
                Spacer(Modifier.height(6.dp))
                PT("Oyun çekirdeği Rust, Android arayüzü Kotlin ile yazıldı. Çevrimiçi altyapı: Supabase (AB, Frankfurt).", 12.sp, maxLines = 4)
            }
            Col {
                PT("Yazı tipleri: Outfit, Big Shoulders, Pixelify Sans (SIL Open Font License).", 12.sp, maxLines = 3)
                if (Account.loggedIn) {
                    Spacer(Modifier.height(10.dp))
                    var ask by remember { mutableStateOf(false) }
                    PxButton(if (ask) "Emin Misin? Hesabın 30 Gün İçinde Silinir" else "Hesabımın Silinmesini İste", Modifier.fillMaxWidth(), color = Px.Red, height = 40.dp, size = 12.sp) {
                        if (!ask) { ask = true; return@PxButton }
                        Account.requestDelete { e -> Toaster.show(e ?: "Silme Talebin Alındı", e == null) }
                        ask = false
                    }
                }
            }
        }
    }
}

private val PRIVACY = listOf(
    "Son güncelleme: 20 Ekim 2026. Bu politika Zenvex Online'ın hangi verileri neden işlediğini açıklar.",
    "Toplanan veriler: Zenvex kimliği, oyuncu adı ve şifrelenmiş hesap anahtarı. Google ile girişte Google hesap kimliğin, e-postan ve adın (yalnız hesabını tanımak için; Google şifren oyuna ulaşmaz). Oyun verileri: seviye, envanter, ilerleme, klan üyeliği, posta, sohbet ve özel mesajlar.",
    "Çevrimiçi oyunda konumun, adın ve ekipmanın yalnız oyun süresince aynı sunucudaki oyuncularla paylaşılır.",
    "Kullanım amacı: oyunu çalıştırmak (giriş, bulut kaydı, sıralama, klan, sohbet, çok oyunculu oynanış) ve kural ihlallerini önlemek. Sohbet ve işlem kayıtları yöneticiler tarafından kötüye kullanım incelemesi için görülebilir. Reklam ya da profil çıkarma yapılmaz, veriler satılmaz.",
    "Saklama: Supabase (AB, Frankfurt). Hesap anahtarın cihaz yedeklerine alınmaz.",
    "Hakların: hesabının ve verilerinin silinmesini bu ekrandan isteyebilirsin; talep 30 gün içinde yerine getirilir.",
    "Oyun 13 yaşın altındaki çocuklara yönelik değildir.",
)

private val TERMS = listOf(
    "Zenvex Online'ı oynayarak aşağıdaki kuralları kabul edersin.",
    "Hile, açık istismarı, değiştirilmiş uygulama kullanımı ve diğer oyunculara taciz yasaktır; ihlalde hesap süreli ya da kalıcı yasaklanabilir.",
    "Oyun içi eşya, altın ve elmasın gerçek parayla değeri yoktur; oyun dışında alınıp satılamaz. Hesaba bağlı ödüller takas edilemez.",
    "Sohbette küfür, hakaret, kişisel bilgi paylaşımı ve reklam yasaktır; klan yöneticileri üyelerini susturabilir.",
    "Oyun \"olduğu gibi\" sunulur; bakım ve güncellemeler sırasında kesinti olabilir.",
)

/** Zenvex hesabı: ID, anahtar, isim, Google bağlantısı, başka hesaba giriş; iki sütun. */
@Composable
private fun ColumnScope.AccountSettings(inGame: Boolean, onReset: () -> Unit) {
    val ctx = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var nick by remember { mutableStateOf(Account.name.value) }
    var confirmKey by remember { mutableStateOf(false) }
    // oyundan atıldıysak (başka cihaz, bakım, yasak, bağlantı) nedeni göster
    val kicked = com.zenvex.online.game.Session.kicked.value
    androidx.compose.runtime.LaunchedEffect(kicked) { if (kicked != null) { Toaster.show(kicked, false); com.zenvex.online.game.Session.kicked.value = null } }
    var confirmReset by remember { mutableStateOf(false) }
    val resetBtn: @Composable () -> Unit = {
        if (!inGame) PxButton(if (confirmReset) "Emin Misin? Tekrar Bas" else "Bu Sunucudaki İlerlemeyi Sil", Modifier.fillMaxWidth(), color = Px.Red, height = 34.dp, size = 12.sp) {
            if (confirmReset) { onReset(); confirmReset = false } else confirmReset = true
        }
    }
    if (!Account.loggedIn) {
        Head("Zenvex Hesabı")
        PT("Hesabına girmedin. Zenvex çevrimiçi bir oyundur: oynamak için hesap aç ya da gir.", 13.sp, Px.Sub, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        PxButton("Hesap Aç / Giriş Yap", icon = "user", height = 44.dp, size = 15.sp) { dialog = true }
        Spacer(Modifier.height(8.dp))
        resetBtn()
    } else Cols {
        Col {
            Head("Zenvex Hesabı")
            PT("ID: ${fmtId(Account.id.value)}", 18.sp, Px.Name)
            PT("Durum: " + Account.status.value, 13.sp, if (Account.status.value == "Çevrimiçi") Px.Name else Px.Sub)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PT("Anahtar: " + if (showKey) Account.key else "••••••••••••", 13.sp, Px.Clan, Modifier.weight(1f))
                PxButton(if (showKey) "Gizle" else "Göster", color = Px.Dark, height = 30.dp, size = 12.sp) { showKey = !showKey }
                PxButton("Kopyala", color = Px.Dark, height = 30.dp, size = 12.sp) {
                    copy(ctx, "Zenvex ID: ${Account.id.value}  Anahtar: ${Account.key}"); Toaster.show("Kopyalandı")
                }
            }
            PT("ID ve anahtarı bir yere not et: telefon değişirse bunlarla girersin.", 11.sp, Px.Sub, maxLines = 3)
            if (Account.profile.value.optBoolean("google")) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("check", 20.dp, Px.Name)
                    Spacer(Modifier.width(6.dp))
                    PT("Google Hesabına Bağlı: Bu Telefonda Ya Da Yenisinde Google İle Girebilirsin", 12.sp, Px.Name, maxLines = 3)
                }
            }
        }
        Col {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(nick, { nick = it.take(16) }, "Oyuncu Adı", Modifier.weight(1f))
                PxButton("İsmi Değiştir", color = Px.Blue, height = 44.dp, size = 13.sp) {
                    Account.rename(nick) { e ->
                        if (e != null) Toaster.show(e, false) else { GameSession.cmd("set_name", "name" to Account.name.value); Toaster.show("İsim Değişti") }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PxButton(if (confirmKey) "Emin Misin?" else "Anahtarı Değiştir", Modifier.weight(1f), color = Px.Red, height = 34.dp, size = 12.sp) {
                    if (!confirmKey) { confirmKey = true; return@PxButton }
                    confirmKey = false
                    Account.changeKey { e -> if (e != null) Toaster.show(e, false) else { showKey = true; Toaster.show("Yeni Anahtar Oluşturuldu, Not Et!") } }
                }
                if (!Account.profile.value.optBoolean("google")) PxButton("Google'a Bağla", Modifier.weight(1f), height = 34.dp, size = 12.sp) {
                    val act = ctx as? android.app.Activity ?: return@PxButton
                    com.zenvex.online.net.GoogleSignIn.start(act, link = true) { e ->
                        when { e == null -> { Toaster.show("Hesabın Google'a Bağlandı"); Account.refresh() }; e.isNotEmpty() -> Toaster.show(e, false) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PxButton("Başka Hesaba Gir", Modifier.weight(1f), color = Px.Dark, height = 34.dp, size = 12.sp) { dialog = true }
                PxButton("Çıkış", Modifier.weight(1f), color = Px.Dark, height = 34.dp, size = 12.sp) {
                    GameSession.save(cloud = true); com.zenvex.online.game.Session.end(); Account.logout(); GameSession.reload { Toaster.show("Hesaptan Çıkıldı") }
                }
            }
            Spacer(Modifier.height(6.dp))
            resetBtn()
        }
    }
    if (dialog) AccountDialog { dialog = false }
}
