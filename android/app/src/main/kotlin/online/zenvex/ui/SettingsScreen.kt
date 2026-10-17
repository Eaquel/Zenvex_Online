package online.zenvex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import online.zenvex.BuildConfig
import online.zenvex.game.Settings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(back: () -> Unit, inGame: Boolean = false, onReset: () -> Unit = {}) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Settings.current) }
    var tab by remember { mutableIntStateOf(0) }
    fun set(n: Settings) { s = n; Settings.save(ctx, n) }
    fun custom(n: Settings) = set(n.copy(preset = 4))

    Box(Modifier.fillMaxSize()) {
        if (inGame) Box(Modifier.fillMaxSize().background(Pal.Night.copy(alpha = 0.85f))) else Background("lobby_bg", 0.65f)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Ayarlar", back)
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Grafik" to "gear", "Kontroller" to "fist", "Ses" to "sound", "Oyun" to "map", "Hesap" to "user", "Hakkında" to "scroll")
                        .forEachIndexed { i, (n, ic) ->
                            PixelButton(n, Modifier.fillMaxWidth(), if (tab == i) BtnStyle.Primary else BtnStyle.Secondary, icon = ic) { tab = i }
                        }
                }
                Spacer(Modifier.width(12.dp))
                PixelPanel(Modifier.weight(1f).fillMaxHeight().padding(bottom = 12.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (tab) {
                            0 -> {
                                Choice("Grafik kalitesi", listOf("Düşük", "Orta", "Yüksek", "Ultra", "Özel"), s.preset) { set(s.withPreset(it)) }
                                Choice("FPS sınırı", listOf("30", "45", "60", "90", "120"), listOf(30, 45, 60, 90, 120).indexOf(s.fpsLimit).coerceAtLeast(0)) {
                                    custom(s.copy(fpsLimit = listOf(30, 45, 60, 90, 120)[it]))
                                }
                                Choice("Kamera uzaklığı", listOf("Yakın", "Normal", "Uzak"), s.zoom) { set(s.copy(zoom = it)) }
                                Choice("Bitki yoğunluğu", listOf("Düşük", "Orta", "Yüksek"), s.vegetation) { custom(s.copy(vegetation = it)) }
                                Toggle("Rüzgâr animasyonu (ağaç/ot sallanması)", s.wind) { custom(s.copy(wind = it)) }
                                Toggle("Gölgeler", s.shadows) { custom(s.copy(shadows = it)) }
                                Toggle("Parçacık efektleri", s.particles) { custom(s.copy(particles = it)) }
                                Toggle("Arkasındayken ağacı saydamlaştır", s.treeFade) { set(s.copy(treeFade = it)) }
                                Slider("Ekran sarsıntısı", s.screenShake, 0f, 1f, pct = true) { set(s.copy(screenShake = it)) }
                                Toggle("Hasar sayıları", s.damageNumbers) { set(s.copy(damageNumbers = it)) }
                                Toggle("Pil tasarrufu (30 FPS)", s.batterySaver) { set(s.copy(batterySaver = it)) }
                                Toggle("FPS göstergesi", s.showFps) { set(s.copy(showFps = it)) }
                            }
                            1 -> {
                                PText("Hareket joysticki", 15.sp, Pal.Cream)
                                Toggle("Dinamik joystick (dokunduğun yerde açılır)", s.moveDynamic) { set(s.copy(moveDynamic = it)) }
                                Slider("Hareket hassasiyeti", s.moveSensitivity, 0.5f, 2f) { set(s.copy(moveSensitivity = it)) }
                                Slider("Hareket ölü bölge", s.moveDeadZone, 0f, 0.3f, pct = true) { set(s.copy(moveDeadZone = it)) }
                                Slider("Koşma eşiği", s.runThreshold, 0.4f, 0.9f, pct = true) { set(s.copy(runThreshold = it)) }
                                Spacer(Modifier.height(8.dp))
                                PText("Saldırı joysticki", 15.sp, Pal.Cream)
                                PText("Çevirdiğin yöne yumruk atar, basılı tuttukça devam eder.", 11.sp, Pal.Gray)
                                Slider("Saldırı hassasiyeti", s.attackSensitivity, 0.5f, 2f) { set(s.copy(attackSensitivity = it)) }
                                Slider("Saldırı ölü bölge", s.attackDeadZone, 0f, 0.3f, pct = true) { set(s.copy(attackDeadZone = it)) }
                                Spacer(Modifier.height(8.dp))
                                PText("Genel", 15.sp, Pal.Cream)
                                Slider("Joystick boyutu", s.joystickSize, 0.7f, 1.5f, pct = true) { set(s.copy(joystickSize = it)) }
                                Slider("Joystick saydamlığı", s.joystickOpacity, 0.2f, 1f, pct = true) { set(s.copy(joystickOpacity = it)) }
                                Choice("Titreşim", listOf("Kapalı", "Hafif", "Güçlü"), s.haptics) { set(s.copy(haptics = it)) }
                                Toggle("Sol el modu (joystickleri değiştir)", s.leftHanded) { set(s.copy(leftHanded = it)) }
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelButton("Arayüzü Düzenle", icon = "map", locked = true, textSize = 12.sp) {}
                                    PixelButton("Varsayılana Dön", textSize = 12.sp) {
                                        val d = Settings()
                                        set(s.copy(moveDynamic = d.moveDynamic, moveSensitivity = d.moveSensitivity, moveDeadZone = d.moveDeadZone,
                                            runThreshold = d.runThreshold, attackSensitivity = d.attackSensitivity, attackDeadZone = d.attackDeadZone,
                                            joystickSize = d.joystickSize, joystickOpacity = d.joystickOpacity, haptics = d.haptics, leftHanded = d.leftHanded))
                                    }
                                }
                            }
                            2 -> {
                                PText("Müzik ve ortam sesleri bir sonraki sürümde ekleniyor.", 11.sp, Pal.Gray)
                                Slider("Ana ses", s.master, 0f, 1f, pct = true) { set(s.copy(master = it)) }
                                Slider("Müzik", s.music, 0f, 1f, pct = true) { set(s.copy(music = it)) }
                                Slider("Efektler", s.sfx, 0f, 1f, pct = true) { set(s.copy(sfx = it)) }
                                Slider("Ortam (rüzgâr, kuş)", s.ambient, 0f, 1f, pct = true) { set(s.copy(ambient = it)) }
                                Slider("Arayüz sesleri", s.uiSound, 0f, 1f, pct = true) { set(s.copy(uiSound = it)) }
                            }
                            3 -> {
                                Choice("Dil", listOf("Türkçe", "English"), if (s.language == "tr") 0 else 1) {
                                    if (it == 1) Toaster.show("İngilizce yakında!", "globe")
                                }
                                Choice("Renk körlüğü modu", listOf("Kapalı", "Deuteranopi", "Protanopi", "Tritanopi"), s.colorBlind) {
                                    if (it != 0) Toaster.locked()
                                }
                                Toggle("Bildirimler", false) { Toaster.locked() }
                            }
                            4 -> {
                                PText("Misafir hesabı", 15.sp, Pal.Cream)
                                PText("İlerlemen bu cihazda saklanıyor.", 12.sp, Pal.Gray)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelButton("Hesabı Bağla", icon = "globe", locked = true) {}
                                    PixelButton("Bulut Kayıt", icon = "map", locked = true) {}
                                }
                                Spacer(Modifier.height(8.dp))
                                if (!inGame) {
                                    var confirm by remember { mutableStateOf(false) }
                                    PixelButton(if (confirm) "Emin misin? Tekrar bas" else "Tüm İlerlemeyi Sil", style = BtnStyle.Danger) {
                                        if (confirm) { onReset(); confirm = false } else confirm = true
                                    }
                                }
                            }
                            else -> {
                                PText("Zenvex Online", 18.sp, Pal.Cream)
                                PText("Sürüm ${BuildConfig.VERSION_NAME}", 12.sp, Pal.Gray)
                                Spacer(Modifier.height(8.dp))
                                PText("Oyun çekirdeği Rust, Android arayüzü Kotlin ile yazıldı.", 12.sp, Pal.White)
                                PText("Tüm görseller kodla üretildi (tools/assetgen).", 12.sp, Pal.White)
                                PText("Yazı tipi: Pixelify Sans (SIL Open Font License).", 12.sp, Pal.White)
                                Spacer(Modifier.height(8.dp))
                                PixelButton("Destek / Geri Bildirim", icon = "mail", locked = true) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(t: String) = PText(t, 13.sp, Pal.White)

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label)
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(52.dp).height(26.dp).pixelFrame(if (value) Pal.GreenDark else Pal.GrayDark, if (value) Pal.Green else Pal.Gray, Pal.Night)) {
            Box(
                Modifier.align(if (value) Alignment.CenterEnd else Alignment.CenterStart).padding(4.dp).size(18.dp)
                    .pixelFrame(Pal.White, Color.White, Pal.Gray),
            )
        }
    }
}

@Composable
fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Label(label)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                PixelButton(o, style = if (i == selected) BtnStyle.Primary else BtnStyle.Secondary, textSize = 12.sp) { onSelect(i) }
            }
        }
    }
}

@Composable
fun Slider(label: String, value: Float, min: Float, max: Float, pct: Boolean = false, onChange: (Float) -> Unit) {
    var w by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    fun pick(x: Float) {
        val f = (x / w).coerceIn(0f, 1f)
        val v = min + f * (max - min)
        onChange((v * 100).roundToInt() / 100f)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.width(200.dp).height(26.dp).onSizeChanged { w = it.width }
                .pointerInput(min, max) { detectTapGestures { pick(it.x) } }
                .pointerInput(min, max) { detectDragGestures { ch, _ -> pick(ch.position.x) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(Pal.Outline).padding(2.dp).background(Pal.GrayDark))
            Box(Modifier.fillMaxWidth(frac).height(8.dp).padding(2.dp).background(Pal.Mustard))
            val knobX = with(density) { (w * frac).toDp() } - 9.dp
            Box(Modifier.offset(x = knobX).size(18.dp, 24.dp).pixelFrame(Pal.MustardLight, Pal.Cream, Pal.MustardDark))
        }
        Spacer(Modifier.width(10.dp))
        PText(if (pct) "%${(value * 100).roundToInt()}" else String.format("%.2fx", value), 12.sp, Pal.Cream, Modifier.width(52.dp))
    }
}
