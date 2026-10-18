package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.BuildConfig
import com.zenvex.online.game.Online
import com.zenvex.online.game.Settings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(back: () -> Unit, inGame: Boolean = false, onReset: () -> Unit = {}) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Settings.current) }
    var tab by remember { mutableIntStateOf(0) }
    fun set(n: Settings) { s = n; Settings.save(ctx, n) }
    fun custom(n: Settings) = set(n.copy(preset = 4))

    ScreenFrame("AYARLAR", "gear", back) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Grafik" to "eye", "Kontroller" to "fist", "Ses" to "volume", "Oyun" to "map", "Hesap" to "user", "Hakkında" to "info")
                        .forEachIndexed { i, (n, ic) ->
                            GButton(n, Modifier.fillMaxWidth(), if (tab == i) Tone.Gold else Tone.Dark, icon = ic, height = 42.dp, size = 14.sp) { tab = i }
                        }
                }
                Spacer(Modifier.width(12.dp))
                Panel(Modifier.weight(1f).fillMaxHeight().padding(bottom = 8.dp)) {
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
                                SectionTitle("Hareket joysticki")
                                Toggle("Dinamik joystick (dokunduğun yerde açılır)", s.moveDynamic) { set(s.copy(moveDynamic = it)) }
                                Slider("Hareket hassasiyeti", s.moveSensitivity, 0.5f, 2f) { set(s.copy(moveSensitivity = it)) }
                                Slider("Hareket ölü bölge", s.moveDeadZone, 0f, 0.3f, pct = true) { set(s.copy(moveDeadZone = it)) }
                                Slider("Koşma eşiği", s.runThreshold, 0.4f, 0.9f, pct = true) { set(s.copy(runThreshold = it)) }
                                Spacer(Modifier.height(8.dp))
                                SectionTitle("Saldırı joysticki")
                                T("Çevirdiğin yöne saldırır, basılı tuttukça devam eder.", 11.sp, Pal.Sub)
                                Slider("Saldırı hassasiyeti", s.attackSensitivity, 0.5f, 2f) { set(s.copy(attackSensitivity = it)) }
                                Slider("Saldırı ölü bölge", s.attackDeadZone, 0f, 0.3f, pct = true) { set(s.copy(attackDeadZone = it)) }
                                Spacer(Modifier.height(8.dp))
                                SectionTitle("Genel")
                                Slider("Joystick boyutu", s.joystickSize, 0.7f, 1.5f, pct = true) { set(s.copy(joystickSize = it)) }
                                Slider("Joystick saydamlığı", s.joystickOpacity, 0.2f, 1f, pct = true) { set(s.copy(joystickOpacity = it)) }
                                Choice("Titreşim", listOf("Kapalı", "Hafif", "Güçlü"), s.haptics) { set(s.copy(haptics = it)) }
                                Toggle("Sol el modu (joystickleri değiştir)", s.leftHanded) { set(s.copy(leftHanded = it)) }
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GButton("Varsayılana Dön", tone = Tone.Dark, icon = "refresh", height = 38.dp, size = 13.sp) {
                                        val d = Settings()
                                        set(s.copy(moveDynamic = d.moveDynamic, moveSensitivity = d.moveSensitivity, moveDeadZone = d.moveDeadZone,
                                            runThreshold = d.runThreshold, attackSensitivity = d.attackSensitivity, attackDeadZone = d.attackDeadZone,
                                            joystickSize = d.joystickSize, joystickOpacity = d.joystickOpacity, haptics = d.haptics, leftHanded = d.leftHanded))
                                    }
                                }
                            }
                            2 -> {
                                T("Müzik lobide ve oyunda çalar; efektler oyun içi.", 11.sp, Pal.Sub)
                                Slider("Ana ses", s.master, 0f, 1f, pct = true) { set(s.copy(master = it)) }
                                Slider("Müzik", s.music, 0f, 1f, pct = true) { set(s.copy(music = it)) }
                                Slider("Efektler", s.sfx, 0f, 1f, pct = true) { set(s.copy(sfx = it)) }
                                Slider("Ortam (rüzgâr, kuş)", s.ambient, 0f, 1f, pct = true) { set(s.copy(ambient = it)) }
                                Slider("Arayüz sesleri", s.uiSound, 0f, 1f, pct = true) { set(s.copy(uiSound = it)) }
                            }
                            3 -> {
                                Choice("Dil", listOf("Türkçe", "English"), if (s.language == "tr") 0 else 1) {
                                    if (it == 1) Toaster.show("İngilizce yakında!", false)
                                }
                                Choice("Renk körlüğü modu", listOf("Kapalı", "Deuteranopi", "Protanopi", "Tritanopi"), s.colorBlind) {
                                    if (it != 0) Toaster.show("Yakında!", false)
                                }
                                Toggle("Bildirimler", false) { Toaster.show("Yakında!", false) }
                            }
                            4 -> {
                                SectionTitle("Misafir hesabı")
                                T("İlerlemen bu cihazda saklanıyor.", 12.sp, Pal.Sub)
                                T("Sunucu: " + Online.status.value, 12.sp, if (Online.online.value) Pal.Green else Pal.Sub)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GButton("Hesabı Bağla", tone = Tone.Dark, icon = "globe", height = 40.dp, size = 13.sp) { Toaster.show("Yakında!", false) }
                                }
                                Spacer(Modifier.height(8.dp))
                                if (!inGame) {
                                    var confirm by remember { mutableStateOf(false) }
                                    GButton(if (confirm) "Emin misin? Tekrar bas" else "Tüm İlerlemeyi Sil", tone = Tone.Red, height = 40.dp, size = 13.sp) {
                                        if (confirm) { onReset(); confirm = false } else confirm = true
                                    }
                                }
                            }
                            else -> {
                                T("ZENVEX ONLINE", 22.sp, Pal.Gold, display = true)
                                T("Sürüm ${BuildConfig.VERSION_NAME}", 12.sp, Pal.Sub)
                                Spacer(Modifier.height(8.dp))
                                T("Oyun çekirdeği Rust, Android arayüzü Kotlin ile yazıldı.", 12.sp)
                                T("Tüm 3D görseller Blender ile kodla üretildi (tools/blender).", 12.sp)
                                T("Yazı tipleri: Outfit, Big Shoulders (SIL Open Font License).", 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(t: String) = T(t, 13.sp)

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label)
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(50.dp).height(28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (value) Brush.horizontalGradient(listOf(Color(0xFF1E9A52), Color(0xFF5BE38A))) else Brush.horizontalGradient(listOf(Color(0xFF2A3446), Color(0xFF1A2230))))
            .padding(3.dp)) {
            Box(Modifier.align(if (value) Alignment.CenterEnd else Alignment.CenterStart).size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Label(label)
        Spacer(Modifier.height(4.dp))
        Tabs(options.map { "" to it }.map { "dot" to it.second }, selected) { onSelect(it) }
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
            Modifier.width(220.dp).height(28.dp).onSizeChanged { w = it.width }
                .pointerInput(min, max) { detectTapGestures { pick(it.x) } }
                .pointerInput(min, max) { detectDragGestures { ch, _ -> pick(ch.position.x) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Bar(frac, Pal.Gold, Modifier.fillMaxWidth(), 8.dp)
            val knobX = with(density) { (w * frac).toDp() } - 11.dp
            Box(Modifier.offset(x = knobX).size(22.dp).clip(CircleShape).background(Color.White).border(3.dp, Pal.Gold, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        T(if (pct) "%${(value * 100).roundToInt()}" else String.format("%.2fx", value), 12.sp, Pal.Gold, Modifier.width(52.dp), bold = true)
    }
}
