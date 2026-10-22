package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Skins
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DIA = Color(0xFF7FE0FF)
private val DIA_D = Color(0xFF1E5A8A)
private val LEG = Color(0xFFFFC83A)
private val EPIC = Color(0xFFC07AFF)
private val RARE = Color(0xFF6AB8FF)

/** Sandıkta çıkabilecekler (supabase zx_crate_open ile aynı oranlar) */
private val ODDS = listOf(
    "Efsanevi Kaplama (6 Çeşit)" to "%1,5", "Destansı Kaplama (4 Çeşit)" to "%4", "Titan Yüzüğü" to "%1",
    "Titan Parçası ×2" to "%5", "Karanlık Kolye" to "%3,5", "Karanlık Kristal ×2" to "%9", "Güç İksiri ×3" to "%11",
    "Askerî Sandık" to "%11", "İlk Yardım Kiti ×5" to "%15", "Can İksiri ×3" to "%17", "1.000 Altın" to "%22",
)

private enum class Phase { Idle, Opening, Reveal }

@Composable
fun CrateScreen(back: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("skins") }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }
    CrateView(data, tab, phase, results, Skins.pity, back, { tab = it },
        open = { n, price ->
            phase = Phase.Opening
            Skins.open(n, price, data.optInt("gems")) { r ->
                if (r == null) phase = Phase.Idle else { results = r; tick++ }
            }
        },
        opened = { if (results.isNotEmpty()) phase = Phase.Reveal },
        done = { phase = Phase.Idle; results = emptyList(); tick++ },
        equip = { id, kind -> val r = Skins.equip(id, kind); Toaster.show(r.msg, r.ok); tick++ })
}

@Composable
private fun CrateView(
    data: JSONObject, tab: Int, phase: Phase, results: List<String>, pity: Int, back: () -> Unit, setTab: (Int) -> Unit,
    open: (Int, Int) -> Unit, opened: () -> Unit, done: () -> Unit, equip: (String, String) -> Unit, revealAt: Float? = null,
) {
    val t = rememberTime()
    var odds by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {}) {
        CrateBackground(t)
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PxIcon("close", size = 40.dp) { back() }
                Spacer(Modifier.width(12.dp))
                PT("Elmas Sandığı", 26.sp, DIA, outline = true)
                Spacer(Modifier.width(14.dp))
                CrateTab("Sandık", "gift", tab == 0) { setTab(0) }
                Spacer(Modifier.width(6.dp))
                CrateTab("Koleksiyon", "hanger", tab == 1) { setTab(1) }
                Spacer(Modifier.weight(1f))
                Icon("gem", 22.dp, DIA); Spacer(Modifier.width(5.dp)); PT(fmt(data.optInt("gems")), 20.sp, DIA)
            }
            if (tab == 0) CrateMain(data, phase, pity, t, open, opened) { odds = true }
            else Collection(data, equip)
        }
        if (phase == Phase.Reveal) RevealOverlay(results, data, t, done, revealAt)
        if (odds) PxDialog("Olasılıklar", { odds = false }, 460.dp) {
            Column(Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                PT("Her açılış bağımsızdır. 50 açılışta en az bir Efsanevi Kaplama garantidir; önce sahip olmadıkların çıkar. Sahip olduğun kaplama tekrar çıkarsa Altına dönüşür (Efsanevi 2.000, Destansı 800).",
                    12.sp, Px.Sub, maxLines = 5)
                Spacer(Modifier.height(6.dp))
                for ((n, p) in ODDS) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    PT(n, 14.sp, if (n.startsWith("Efsanevi")) LEG else if (n.startsWith("Destansı")) EPIC else Px.Text, Modifier.weight(1f)); PT(p, 14.sp, Px.Clan)
                }
            }
        }
    }
}

/** Kasa odası: derin mavi-mor gradyan, sandığın arkasından dönen ışık huzmeleri, yükselen elmas tozu, kenar karartma */
@Composable
private fun CrateBackground(t: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF2A2F6A), Color(0xFF14123A), Color(0xFF07060F)), Offset(size.width * 0.38f, size.height * 0.55f), size.width * 0.7f))
        val c = Offset(size.width * 0.38f, size.height * 0.56f)
        rotate(t * 8f, c) {
            for (i in 0 until 14) {
                val a = i * (2 * PI.toFloat() / 14)
                val p = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + cos(a - 0.07f) * size.width, c.y + sin(a - 0.07f) * size.width)
                    lineTo(c.x + cos(a + 0.07f) * size.width, c.y + sin(a + 0.07f) * size.width)
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(DIA.copy(alpha = 0.16f), Color.Transparent), c, size.width * 0.55f))
            }
        }
        // zemin: parlak kasa platformu
        drawOval(Brush.radialGradient(listOf(DIA.copy(alpha = 0.35f), Color.Transparent), Offset(c.x, size.height * 0.86f), size.width * 0.22f),
            Offset(c.x - size.width * 0.22f, size.height * 0.80f), Size(size.width * 0.44f, size.height * 0.12f))
        for (i in 0 until 46) {
            val x = ((i * 53) % 100) / 100f * size.width
            val sp = 0.03f + (i % 6) * 0.01f
            val y = size.height * (1f - ((t * sp + i * 0.21f) % 1f))
            val k = 0.3f + 0.4f * sin(t * 3f + i)
            val r = 1.5f + (i % 3)
            drawRect(DIA.copy(alpha = k), Offset(x + sin(t + i) * 8f, y), Size(r * 2, r * 2))
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)), center, size.maxDimension * 0.75f))
    }
}

@Composable
private fun CrateTab(name: String, icon: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) Brush.verticalGradient(listOf(Color(0xFF2E6A9A), DIA_D)) else Brush.verticalGradient(listOf(Color(0xCC201E30), Color(0xCC14122A))))
        .border(2.dp, if (on) DIA.copy(alpha = 0.8f) else Color(0xFF34305A), RoundedCornerShape(3.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, 16.dp, if (on) Color.White else Px.Sub); Spacer(Modifier.width(6.dp)); PT(name, 14.sp, if (on) Color.White else Px.Sub)
    }
}

@Composable
private fun CrateMain(data: JSONObject, phase: Phase, pity: Int, t: Float, open: (Int, Int) -> Unit, opened: () -> Unit, showOdds: () -> Unit) {
    // açılış animasyonu: sandık sallanır, kapak kalkar, ışık patlar; sunucu yanıtı gelince açılış biter
    var openT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(phase) {
        if (phase == Phase.Opening) {
            openT = 0f
            val start = System.nanoTime()
            while (openT < 1.6f) { androidx.compose.runtime.withFrameNanos { openT = (it - start) / 1e9f } }
            opened()
        } else openT = 0f
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            DiamondCrate(t, if (phase == Phase.Opening) openT else 0f, Modifier.size(250.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                PT("Efsanevi Kaplamalar", 18.sp, LEG)
                PT("Karakterine ve aracına özel efsanevi görünümler, sürerken ve yürürken ışık izi bırakır.", 12.sp, Px.Text, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in listOf("sk_gold", "sk_blood", "sk_frost", "vs_flame")) SkinPreview(id, Modifier.size(58.dp), t, small = true)
                }
                Spacer(Modifier.height(10.dp))
                // garanti sayacı
                PT("Efsanevi Garantisine: $pity Açılış", 13.sp, LEG)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(280.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF15140F)).padding(1.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(((50 - pity) / 50f).coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(Color(0xFFB07A10), LEG))))
                }
                Spacer(Modifier.height(4.dp))
                PT("Olasılıkları Gör", 12.sp, DIA, Modifier.clickable { showOdds() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val busy = phase != Phase.Idle
                OpenButton("1 Kez Aç", data.optInt("price", 120), null, !busy, t) { open(1, data.optInt("price", 120)) }
                OpenButton("10 Kez Aç", data.optInt("price10", 1080), "%10 İndirim", !busy, t) { open(10, data.optInt("price10", 1080)) }
            }
        }
    }
}

/**
 * Kodla çizilmiş elmas sandığı: koyu çelik gövde, altın bantlar ve perçinler, önünde parlayan kesme elmas.
 * [open] 0..1.6 sn: 0-0.9 sallanma (artan), 0.9-1.6 kapak kalkar ve içinden ışık patlar.
 */
@Composable
private fun DiamondCrate(t: Float, open: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val shake = if (open in 0.01f..0.9f) sin(open * 70f) * open * 9f else 0f
        val bob = if (open == 0f) sin(t * 2f) * 4f else 0f
        val lid = ((open - 0.9f) / 0.5f).coerceIn(0f, 1f)
        val cx = w / 2 + shake; val top = h * 0.36f + bob
        val bw = w * 0.78f; val bh = h * 0.42f; val lx = cx - bw / 2
        // gölge
        drawOval(Color.Black.copy(alpha = 0.45f), Offset(cx - bw * 0.55f, top + bh - 4f), Size(bw * 1.1f, h * 0.1f))
        // içten gelen ışık (kapak kalkarken)
        if (lid > 0f) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = lid), DIA.copy(alpha = 0.7f * lid), Color.Transparent), Offset(cx, top), w * 0.7f * lid), w * 0.7f * lid, Offset(cx, top))
            for (i in 0 until 9) {
                val a = -PI.toFloat() / 2 + (i - 4) * 0.22f
                drawLine(Color.White.copy(alpha = 0.6f * lid), Offset(cx, top), Offset(cx + cos(a) * w * lid, top + sin(a) * w * lid), 6f * lid)
            }
        }
        // gövde
        drawRect(Color(0xFF0E0D16), Offset(lx - 4f, top - 4f), Size(bw + 8f, bh + 8f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A3F6A), Color(0xFF1A1C38)), top, top + bh), Offset(lx, top), Size(bw, bh))
        // altın bantlar
        for (fx in listOf(0.12f, 0.88f)) drawRect(Brush.horizontalGradient(listOf(Color(0xFF8A5A08), LEG, Color(0xFF8A5A08)), lx + bw * fx - 9f, lx + bw * fx + 9f),
            Offset(lx + bw * fx - 9f, top), Size(18f, bh))
        drawRect(Brush.verticalGradient(listOf(LEG, Color(0xFF8A5A08)), top + bh - 16f, top + bh), Offset(lx, top + bh - 16f), Size(bw, 16f))
        // perçinler
        for (fx in listOf(0.12f, 0.88f)) for (fy in listOf(0.2f, 0.5f, 0.8f)) {
            drawCircle(Color(0xFF5A3A04), 5f, Offset(lx + bw * fx, top + bh * fy)); drawCircle(Color(0xFFFFF0B0), 2.5f, Offset(lx + bw * fx - 1f, top + bh * fy - 1f))
        }
        // kapak (kalkarken yukarı ve geriye)
        val lidUp = lid * h * 0.22f
        val lh = h * 0.16f; val ly = top - lh - lidUp
        val lidPath = Path().apply {
            moveTo(lx - 8f, ly + lh); lineTo(lx + bw + 8f, ly + lh); lineTo(lx + bw - 6f, ly + 8f)
            quadraticTo(cx, ly - lh * 0.5f, lx + 6f, ly + 8f); close()
        }
        drawPath(lidPath, Color(0xFF0E0D16), style = Stroke(8f))
        drawPath(lidPath, Brush.verticalGradient(listOf(Color(0xFF4A5090), Color(0xFF242650)), ly - lh * 0.4f, ly + lh))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF8A5A08), LEG, Color(0xFF8A5A08))), Offset(lx - 8f, ly + lh - 14f), Size(bw + 16f, 14f))
        // kilit: kesme elmas
        drawGem(Offset(cx, top + bh * 0.42f - lidUp * 0.15f), bw * 0.13f, t)
        // parıltılar
        for (i in 0 until 4) {
            val a = t * 1.3f + i * 1.7f
            val p = Offset(cx + cos(a) * bw * 0.55f, top + bh * 0.4f + sin(a * 1.3f) * bh * 0.6f)
            val k = (sin(t * 4f + i * 2f) * 0.5f + 0.5f)
            drawLine(Color.White.copy(alpha = k), Offset(p.x - 10f * k, p.y), Offset(p.x + 10f * k, p.y), 2.5f)
            drawLine(Color.White.copy(alpha = k), Offset(p.x, p.y - 10f * k), Offset(p.x, p.y + 10f * k), 2.5f)
        }
    }
}

private fun DrawScope.drawGem(c: Offset, r: Float, t: Float) {
    drawCircle(Brush.radialGradient(listOf(DIA.copy(alpha = 0.6f + 0.3f * sin(t * 3f)), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
    val p = Path().apply { moveTo(c.x - r, c.y - r * 0.35f); lineTo(c.x - r * 0.55f, c.y - r * 0.8f); lineTo(c.x + r * 0.55f, c.y - r * 0.8f); lineTo(c.x + r, c.y - r * 0.35f); lineTo(c.x, c.y + r); close() }
    drawPath(p, Brush.linearGradient(listOf(Color.White, DIA, DIA_D), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
    drawPath(p, Color(0xFF0E2A40), style = Stroke(3f))
    drawLine(Color.White.copy(alpha = 0.8f), Offset(c.x - r * 0.55f, c.y - r * 0.8f), Offset(c.x, c.y + r), 2f)
    drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - r, c.y - r * 0.35f), Offset(c.x + r, c.y - r * 0.35f), 2f)
}

/** Açma tuşu: elmas renkli kabartmalı plaka, kayan parıltı, elmas fiyatı, isteğe bağlı indirim kurdelesi */
@Composable
private fun OpenButton(label: String, price: Int, ribbon: String?, enabled: Boolean, t: Float, onClick: () -> Unit) {
    Box {
        Column(Modifier.width(170.dp).height(64.dp).clip(RoundedCornerShape(4.dp))
            .drawBehind {
                drawRect(Color(0xFF0E0D16))
                val top = if (enabled) Color(0xFF5AC0F0) else Color(0xFF3A3A44); val bot = if (enabled) DIA_D else Color(0xFF22222A)
                drawRect(Brush.verticalGradient(listOf(top, bot)), Offset(3f, 3f), Size(size.width - 6f, size.height - 6f))
                if (enabled) {
                    val u = ((t * 0.4f) % 1.6f) * size.width - size.width * 0.3f
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent), Offset(u, 0f), Offset(u + 70f, size.height)),
                        Offset(3f, 3f), Size(size.width - 6f, size.height - 6f))
                }
                drawRect(Color.White.copy(alpha = 0.35f), Offset(3f, 3f), Size(size.width - 6f, 3f))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(3f, size.height - 8f), Size(size.width - 6f, 5f))
            }
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PT(label, 17.sp, Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("gem", 15.dp, Color.White); Spacer(Modifier.width(4.dp)); PT(fmt(price), 15.sp, Color.White)
            }
        }
        if (ribbon != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 0.dp).graphicsLayer { translationY = -18f; translationX = 10f; rotationZ = 8f }
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE0302A)).padding(horizontal = 6.dp, vertical = 2.dp)) { PT(ribbon, 10.sp, Color.White) }
    }
}

/** Kaplama önizlemesi: karakter (kahraman çizimi) ya da araç (V8 cip) renk matrisiyle; efsanevide dönen hale */
@Composable
fun SkinPreview(id: String, modifier: Modifier, t: Float, small: Boolean = false) {
    val ctx = LocalContext.current
    val vehicle = id.startsWith("vs_")
    val key = if (vehicle) "x/car" else "l/kaan/0_0"
    val img = remember(key) { SpriteCache.get(ctx, key) ?: SpriteCache.get(ctx, if (vehicle) "v/car/2" else "h/kaan/idle_0_0") }
    val mtx = remember(id) { Skins.matrix(id)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    val rar = com.zenvex.online.game.Skins.trail(id) != 0
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(Brush.radialGradient(listOf(if (rar) LEG.copy(alpha = 0.35f) else EPIC.copy(alpha = 0.3f), Color(0xFF10101C))))
        .border(2.dp, if (rar) LEG.copy(alpha = 0.7f) else EPIC.copy(alpha = 0.6f), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (rar) rotate(t * 30f) {
                for (i in 0 until 6) {
                    val a = i * PI.toFloat() / 3
                    drawLine(LEG.copy(alpha = 0.18f), center, Offset(center.x + cos(a) * size.width, center.y + sin(a) * size.width), size.width * 0.08f)
                }
            }
            if (img != null) {
                val k = minOf(size.width / img.width, size.height / img.height) * (if (small) 0.9f else 0.85f)
                val dw = img.width * k; val dh = img.height * k
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
                    IntSize(dw.toInt(), dh.toInt()), colorFilter = mtx, filterQuality = FilterQuality.None)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Collection(data: JSONObject, equip: (String, String) -> Unit) {
    val t = rememberTime()
    val skins = data.optJSONArray("skins").objs()
    Column(Modifier.fillMaxSize().padding(top = 8.dp).verticalScroll(rememberScrollState())) {
        for ((kind, title) in listOf("char" to "Karakter Kaplamaları", "vehicle" to "Araç Kaplamaları")) {
            PT(title, 16.sp, Px.Orange)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in skins.filter { it.optString("kind") == kind }) {
                    val owned = s.optBoolean("owned"); val eq = s.optBoolean("equipped")
                    val col = if (s.optInt("rarity") >= 3) LEG else EPIC
                    Column(Modifier.width(176.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xCC14122A))
                        .border(2.dp, if (eq) Px.Name else col.copy(alpha = if (owned) 0.7f else 0.25f), RoundedCornerShape(3.dp)).padding(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                SkinPreview(s.optString("id"), Modifier.size(64.dp), t)
                                if (!owned) Box(Modifier.size(64.dp).background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon("lock", 22.dp, Px.Sub) }
                            }
                            Spacer(Modifier.width(6.dp))
                            Column {
                                PT(s.optString("name"), 12.sp, col, maxLines = 2)
                                PT(if (s.optInt("rarity") >= 3) "Efsanevi" else "Destansı", 10.sp, Px.Sub)
                            }
                        }
                        PT(s.optString("desc"), 10.sp, Px.Text, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        when {
                            eq -> PxButton("Çıkar", Modifier.fillMaxWidth(), Px.Dark, height = 30.dp, size = 12.sp) { equip("", kind) }
                            owned -> PxButton("Giy", Modifier.fillMaxWidth(), Px.Green, height = 30.dp, size = 12.sp) { equip(s.optString("id"), kind) }
                            else -> PT("Elmas Sandığından Çıkar", 10.sp, Px.Sub)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Sonuç: kartlar sırayla çevrilir, arkalarında nadirlik ışını; efsanevide altın patlama ve "Efsanevi!" yazısı */
@Composable
private fun BoxScope.RevealOverlay(results: List<String>, data: JSONObject, t: Float, done: () -> Unit, fixed: Float? = null) {
    val start = remember { t }
    val el = fixed ?: (t - start)
    val skins = data.optJSONArray("skins").objs().associateBy { it.optString("id") }
    val legend = results.any { skins[it]?.optInt("rarity") == 3 }
    Box(Modifier.matchParentSize().background(Color(0xD0050410)).clickable(remember { MutableInteractionSource() }, null) { if (el > 0.3f * results.size + 0.6f) done() },
        contentAlignment = Alignment.Center) {
        if (legend && el > 0.3f) Canvas(Modifier.fillMaxSize()) {
            rotate(t * 20f) {
                for (i in 0 until 16) {
                    val a = i * PI.toFloat() / 8
                    drawLine(LEG.copy(alpha = 0.12f), center, Offset(center.x + cos(a) * size.width, center.y + sin(a) * size.width), size.width * 0.05f)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (legend) PT("Efsanevi!", 34.sp, LEG, outline = true)
            else PT("Sandık Açıldı", 26.sp, DIA, outline = true)
            Spacer(Modifier.height(10.dp))
            val rows = results.chunked(5)
            for ((ri, row) in rows.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((ci, code) in row.withIndex()) {
                        val i = ri * 5 + ci
                        val flip = ((el - 0.25f * i) / 0.3f).coerceIn(0f, 1f)
                        RevealCard(code, skins[code], flip, t)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (el > 0.3f * results.size + 0.6f) PT("Devam Etmek İçin Dokun", 13.sp, Px.Sub)
        }
    }
}

@Composable
private fun RevealCard(code: String, skin: JSONObject?, flip: Float, t: Float) {
    val itemId = code.substringBefore(':'); val count = code.substringAfter(':', "1")
    val rarity = when {
        skin != null -> skin.optInt("rarity"); itemId == "gold" -> 0
        itemId in setOf("ring_titan", "titan_shard") -> 3; itemId in setOf("neck_dark", "dark_crystal", "potion_power", "chest_military") -> 2; else -> 1
    }
    val col = when (rarity) { 3 -> LEG; 2 -> EPIC; 1 -> RARE; else -> Color(0xFFD8D4C4) }
    val front = flip > 0.5f
    Box(Modifier.width(112.dp).height(132.dp).graphicsLayer { rotationY = (1f - flip) * 180f; cameraDistance = 12f * density }) {
        if (!front) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A2F6A), Color(0xFF14123A))))
            .border(2.dp, DIA.copy(alpha = 0.6f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(50.dp)) { drawGem(center, size.width * 0.35f, t) }
        } else Column(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(col.copy(alpha = 0.45f), Color(0xFF10101C))))
            .border(2.dp, col, RoundedCornerShape(4.dp)).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f), Color.Transparent)), size.width * 0.55f)
                }
                when {
                    skin != null -> SkinPreview(code, Modifier.size(66.dp), t, small = true)
                    itemId == "gold" -> Icon("coin", 46.dp, LEG)
                    else -> ItemArt(itemId, Modifier.fillMaxSize())
                }
            }
            PT(skin?.optString("name") ?: itemName(itemId), 11.sp, col, align = TextAlign.Center, maxLines = 2)
            if (skin == null) PT("×" + fmt(count.toIntOrNull() ?: 1), 13.sp, Px.Text)
            else PT(if (rarity >= 3) "Efsanevi Kaplama" else "Destansı Kaplama", 9.sp, Px.Sub)
        }
    }
}

private fun itemName(id: String): String = when (id) {
    "gold" -> "Altın"; "ring_titan" -> "Titan Yüzüğü"; "titan_shard" -> "Titan Parçası"; "neck_dark" -> "Karanlık Kolye"
    "dark_crystal" -> "Karanlık Kristal"; "potion_power" -> "Güç İksiri"; "chest_military" -> "Askerî Sandık"; "medkit" -> "İlk Yardım Kiti"
    "potion_heal" -> "Can İksiri"; else -> id
}

/** Önizleme (Paparazzi): sandık ana ekranı ya da açılış sonucu */
@Composable
internal fun CratePreview(data: JSONObject, tab: Int = 0, reveal: List<String> = emptyList()) {
    CrateView(data, tab, if (reveal.isEmpty()) Phase.Idle else Phase.Reveal, reveal, 21, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, revealAt = 10f)
}
