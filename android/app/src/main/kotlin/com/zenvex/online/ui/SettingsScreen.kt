package com.zenvex.online.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Ayarlar: sekmeler üstte, içerik tek alanda çok sütunlu; hiçbir sekmede kaydırma yok (en küçük yatay ekran 800×360 dp'ye göre). */
@Composable
fun SettingsScreen(back: () -> Unit, inGame: Boolean = false, onReset: () -> Unit = {}, initialTab: Int = 0) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Settings.current) }
    var tab by remember { mutableIntStateOf(initialTab) }
    fun set(n: Settings) { s = n; Settings.save(ctx, n) }
    fun custom(n: Settings) = set(n.copy(preset = 4))
    val tabs = listOf("Grafik", "Kontroller", "Ses", "Bildirimler", "Hesap", "Destek", "Dil · Language", "Yasal")
    val tabIcons = listOf("eye", "fist", "volume", "mail", "user", "info", "globe", "scroll")

    PxFrame("Ayarlar", "gear", back) {
        // sekmeler: kabartma plaka; etkin olan yeşil, üstten ışıklı ve altında altın çizgi
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i, n ->
                val on = tab == i
                Row(Modifier.weight(n.length + 7f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (on) Px.Green else Px.PanelDark)
                    .pxBevel(light = if (on) 0.16f else 0.05f)
                    .drawBehind { if (on) drawRect(Px.Clan, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                    .border(2.dp, Px.Edge, RoundedCornerShape(3.dp)).clickable { com.zenvex.online.game.Audio.play("click", bus = com.zenvex.online.game.Audio.Bus.Ui); tab = i },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(tabIcons[i], 14.dp, if (on) Px.Clan else Px.Sub.copy(alpha = 0.8f))
                    Spacer(Modifier.width(5.dp))
                    PT(n, 12.sp, if (on) Color.White else Px.Sub)
                }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PxButton("Rehber", Modifier.weight(1f), color = Px.Blue, icon = "info", height = 34.dp, size = 12.sp) {
                                com.zenvex.online.game.HudLayout.guideReplay = true
                                Toaster.show("Oyuna dönünce gösterilir")
                            }
                            PxButton("Sıfırla", Modifier.weight(1f), color = Px.Dark, icon = "refresh", height = 34.dp, size = 12.sp) {
                                val d = Settings()
                                set(s.copy(moveDynamic = d.moveDynamic, moveSensitivity = d.moveSensitivity, moveDeadZone = d.moveDeadZone,
                                    runThreshold = d.runThreshold, attackSensitivity = d.attackSensitivity, attackDeadZone = d.attackDeadZone,
                                    joystickSize = d.joystickSize, joystickOpacity = d.joystickOpacity, haptics = d.haptics, leftHanded = d.leftHanded))
                            }
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
                else -> LegalPanel()
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
private fun ColumnScope.NotificationSettings() {
    val ctx = LocalContext.current
    // izin durumu: sistem ayarlarından dönünce de güncellenir
    var allowed by remember { mutableStateOf(notifEnabled(ctx)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            allowed = notifEnabled(ctx)
            kotlinx.coroutines.delay(1000)
        }
    }
    val N = com.zenvex.online.game.Notifier
    var mail by remember { mutableStateOf(N.enabled(ctx, N.K_MAIL)) }
    var dm by remember { mutableStateOf(N.enabled(ctx, N.K_DM)) }
    var social by remember { mutableStateOf(N.enabled(ctx, N.K_SOCIAL)) }
    val promo = com.zenvex.online.game.Legal.promo.value
    Cols {
        Col {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (allowed) "check" else "mail", 26.dp, if (allowed) Px.Name else Px.Sub)
                Spacer(Modifier.width(8.dp))
                PT(if (allowed) "Bildirimler Açık" else "Bildirimler Kapalı", 16.sp, if (allowed) Px.Name else Color(0xFFFF7A6A))
            }
            Spacer(Modifier.height(6.dp))
            PxButton(if (allowed) "Bildirim Ayarlarını Düzenle" else "Bildirim İzinlerini Aç",
                color = if (allowed) Px.Dark else Px.Blue, icon = "mail", height = 40.dp, size = 14.sp) {
                val i = if (Build.VERSION.SDK_INT >= 26) Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                else Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                runCatching { ctx.startActivity(i) }
            }
            Spacer(Modifier.height(6.dp))
            Head("Hizmet Bildirimleri")
            Toggle("Posta ve hediyeler", mail) { mail = it; N.setEnabled(ctx, N.K_MAIL, it) }
            Toggle("Özel mesajlar", dm) { dm = it; N.setEnabled(ctx, N.K_DM, it) }
            Toggle("Arkadaşlık istekleri ve klan davetleri", social) { social = it; N.setEnabled(ctx, N.K_SOCIAL, it) }
        }
        Col {
            Head("Etkinlik Ve Kampanya Duyuruları")
            Toggle("Yeni etkinlik, sezon ve kampanyalardan haberdar olmak istiyorum", promo) { com.zenvex.online.game.Legal.setPromo(it) }
            PT("İsteğe bağlıdır (ticari elektronik ileti, 6563 sayılı Kanun). Onayını istediğin an buradan ya da bildirimdeki \"Bu Duyuruları Kapat\" ile ücretsiz geri alabilirsin. SMS veya e-posta gönderilmez.",
                11.sp, Px.Sub, maxLines = 6)
            LegalLink("promo")
        }
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
private fun Head(t: String) {
    // bölüm başlığı: sol altın çubuk, yazı ve sağa uzayan ince çizgi
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 14.dp).background(Px.Clan))
        Spacer(Modifier.width(6.dp))
        PT(t, 14.sp, Px.Clan)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Px.Clan.copy(alpha = 0.4f), Color.Transparent))))
    }
}

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).clickable { onChange(!value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PT(label, 12.sp, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(8.dp))
        // piksel anahtar: gömük yuva (açıkken yeşil ve ışıklı), kabartma kare düğme
        val k by androidx.compose.animation.core.animateFloatAsState(if (value) 1f else 0f, androidx.compose.animation.core.tween(140), label = "tg")
        Box(Modifier.width(44.dp).height(22.dp).clip(RoundedCornerShape(3.dp)).background(Px.Edge).padding(2.dp)
            .background(androidx.compose.ui.graphics.lerp(Color(0xFF26251F), Px.Green, k)).pxBevel(false).padding(2.dp)) {
            Box(Modifier.offset(x = (22 * k).dp).size(14.dp).background(androidx.compose.ui.graphics.lerp(Px.Sub, Color(0xFFF0F6DC), k))
                .pxBevel(light = 0.3f).border(1.dp, Px.Edge))
        }
    }
}

@Composable
fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PT(label, 12.sp)
        Spacer(Modifier.height(1.dp))
        // gömük ray içinde seçenekler; seçili olan yeşil kabartma plaka
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color(0xFF17160F)).pxBevel(false)
            .border(1.dp, Px.Edge, RoundedCornerShape(3.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Box(Modifier.weight(1f).clip(RoundedCornerShape(2.dp)).background(if (on) Px.Green else Color(0xFF2A2924))
                    .pxBevel(light = if (on) 0.18f else 0.04f)
                    .then(if (on) Modifier.border(1.dp, Px.GreenHi, RoundedCornerShape(2.dp)) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(vertical = 3.dp), contentAlignment = Alignment.Center) { PT(o, 11.sp, if (on) Color.White else Px.Sub) }
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
                Box(Modifier.align(Alignment.CenterEnd).offset(x = 6.dp).size(12.dp, 20.dp).background(Color(0xFFE8E4D4)).pxBevel(light = 0.4f)
                    .border(2.dp, Px.Edge))
            }
        }
    }
}

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
