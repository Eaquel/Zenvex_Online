package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.sin

/** Bölge renkleri (haritada dolgu ve kenar) */
private val AREA_COLOR = mapOf(
    "forest" to 0xFF5AA040, "city" to 0xFFA8A8B8, "burnt" to 0xFFE0702A, "desert" to 0xFFE8C068, "bswamp" to 0xFF7A9A4A,
    "military" to 0xFF8A9A5A, "harbor" to 0xFF9AD8F8, "front" to 0xFFE04A3A, "home" to 0xFFC080FF,
)

/** Büyücü haritası (oyunda): bölgeler Rust'tan, eşya adları dünya tablosundan; ışınlanma komutu `wizard_tp`. */
@Composable
fun WizardMapScreen(close: () -> Unit) {
    // bekleme süresi ve altın canlı: veri yarım saniyede bir yenilenir (eskiden açılışta bir kez okunuyor, süre donuyordu)
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(500); tick++ } }
    val data = remember(tick) { GameSession.ui("areas") }
    // öğretici "Işınlan": önce Kişisel Üssüm, oradan Başlangıç Ormanı işaretlenir
    val tut = remember(tick) {
        if (Tut.step() != "g6c") null else {
            val a = data.optJSONArray("areas")
            val hereId = a?.optJSONObject(data.optInt("here", 0))?.optString("id")
            if (hereId == "home") "forest" else "home"
        }
    }
    val items = remember { itemNames(GameSession.world()) }
    val img = remember { WorldMapImage.get() }
    WizardMapView(data, items, img, close, tutTarget = tut) { id ->
        val r = GameSession.cmd("wizard_tp", "to" to id)
        Toaster.show(r.msg, r.ok)
        if (r.ok) close()
    }
}

/** Eşya kimliği → (ad, nadirlik) */
fun itemNames(w: JSONObject): Map<String, Pair<String, Int>> {
    val a = w.optJSONArray("items") ?: return emptyMap()
    return (0 until a.length()).associate { val e = a.getJSONArray(it); e.getString(0) to (e.getString(1) to e.optInt(2)) }
}

/** Aldric'in haritasında adaların yeri: (sütun, satır) — 4×2 ızgara (docs/18) */
private val ISLAND_SLOT = mapOf(
    "forest" to (0 to 0), "city" to (1 to 0), "burnt" to (2 to 0), "desert" to (3 to 0),
    "bswamp" to (0 to 1), "harbor" to (1 to 1), "front" to (2 to 1), "home" to (3 to 1),
)

/**
 * Piksel kilit (12×12 ızgara): demir halka, altın gövde, anahtar deliği; hafif nefes alır.
 * Kilitli adanın ortasında çizilir.
 */
private val LOCK_PX = listOf(
    "....XXXX....", "...X....X...", "..X......X..", "..X......X..", "..X......X..",
    ".BBBBBBBBBB.", ".BGGGGGGGGB.", ".BGGG..GGGB.", ".BGGG..GGGB.", ".BGGGG.GGGB.", ".BGGGGGGGGB.", ".BBBBBBBBBB.",
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pixelLock(c: Offset, size: Float, t: Float) {
    val k = 1f + 0.04f * sin(t * 2.4f)
    val px = size * 2f / 12f * k
    val x0 = c.x - px * 6f; val y0 = c.y - px * 6f
    // gölge ve parıltı
    drawCircle(Color(0x66FFC85A), size * 1.1f * k, c)
    for ((r, row) in LOCK_PX.withIndex()) for ((q, ch) in row.withIndex()) {
        val col = when (ch) { 'X' -> Color(0xFFB8C0CC); 'B' -> Color(0xFF3A2406); 'G' -> if (r < 7) Color(0xFFFFD86A) else Color(0xFFE0A830); else -> continue }
        drawRect(Color(0xFF0C0810), Offset(x0 + q * px + px * 0.25f, y0 + r * px + px * 0.25f), Size(px, px))
        drawRect(col, Offset(x0 + q * px, y0 + r * px), Size(px + 0.5f, px + 0.5f))
    }
}

/** Arka plan yıldızları (sabit): x, y (0-1), boy */
private val STARS = java.util.Random(11).let { r -> List(70) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.4f) } }

/**
 * Büyücü Aldric'in haritası: her bölge ayrı bir harita olduğu için karanlık büyülü boşlukta süzülen **ayrı adalar**
 * olarak görünür (4×2). Adaya dokununca sağda kartı açılır: önerilen seviye, gereken seviye, yaratıklar, çıkan eşyalar,
 * ücret ve "Işınlan". Kilitli ada kararır, bulunulan adada mavi işaret, Titan'ın adasında kırmızı rozet.
 * Görseller: assets/ui/island_<kimlik>.png (saydam zeminli ada) ve wizard_bg.png (boşluk); yoksa adalar bölgenin kendi
 * piksel haritasından bir kesitle, boşluk yıldızlarla kodla çizilir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardMapView(data: JSONObject, items: Map<String, Pair<String, Int>>, worldImg: ImageBitmap?, close: () -> Unit, initial: Int = -1,
                           tutTarget: String? = null, teleport: (String) -> Unit) {
    val tutR = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val ctx = LocalContext.current
    val areas = remember(data) { data.optJSONArray("areas") ?: JSONArray() }
    val ids = remember(areas) { List(areas.length()) { areas.getJSONObject(it).optString("id") } }
    val islandArt = remember(ids) { ids.map { UiArt.image(ctx, "island_$it") } }
    val bgArt = remember { UiArt.image(ctx, "wizard_bg") }
    val here = data.optInt("here", 0)
    var sel by remember { mutableStateOf(if (initial >= 0) initial else -1) }
    val t = rememberTime()
    // bölgenin harita kesiti (görsel yoksa): en büyük dikdörtgeninin ortasından kare
    val crops = remember(areas, worldImg) {
        List(areas.length()) { i ->
            val rs = areas.getJSONObject(i).optJSONArray("rects") ?: JSONArray()
            val r = (0 until rs.length()).map { k -> rs.getJSONArray(k) }.maxByOrNull { (it.getDouble(2) - it.getDouble(0)) * (it.getDouble(3) - it.getDouble(1)) }
            if (r == null || worldImg == null) null else {
                val k = worldImg.width / 57600f
                val (x0, y0, x1, y1) = List(4) { r.getDouble(it).toFloat() * k }
                val side = min(x1 - x0, y1 - y0)
                IntOffset(((x0 + x1 - side) / 2).toInt(), ((y0 + y1 - side) / 2).toInt()) to IntSize(side.toInt().coerceAtLeast(1), side.toInt().coerceAtLeast(1))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A1E3A), Color(0xFF0B0812))))) {
        Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- adalar
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val d = LocalDensity.current
                val wPx = with(d) { maxWidth.toPx() }; val hPx = with(d) { maxHeight.toPx() }
                val cw = wPx / 4f; val ch = hPx / 2f
                val s = min(cw, ch * 0.72f) * 0.8f
                // adanın merkezi (tek sütunlar biraz aşağıda: dağınık görünsün)
                fun center(i: Int): Offset {
                    val (c, r) = ISLAND_SLOT[ids[i]] ?: ((i % 4) to (i / 4))
                    return Offset(cw * (c + 0.5f), ch * (r + 0.42f) + (if (c % 2 == 1) ch * 0.06f else -ch * 0.02f))
                }
                Canvas(Modifier.fillMaxSize().pointerInput(ids) {
                    detectTapGestures { p ->
                        ids.indices.firstOrNull { i -> val c = center(i); kotlin.math.abs(p.x - c.x) < s * 0.6f && kotlin.math.abs(p.y - c.y) < s * 0.7f }?.let { sel = it }
                    }
                }) {
                    // boşluk: görsel ya da yıldızlar
                    if (bgArt != null) {
                        val k = maxOf(size.width / bgArt.width, size.height / bgArt.height)
                        val sw = (size.width / k).toInt(); val sh = (size.height / k).toInt()
                        drawImage(bgArt, IntOffset((bgArt.width - sw) / 2, (bgArt.height - sh) / 2), IntSize(sw, sh), IntOffset.Zero,
                            IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
                    } else for ((k, st) in STARS.withIndex()) {
                        val tw = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * (0.8f + k % 5 * 0.3f) + k))
                        drawCircle(Color(0xFFE8D8FF).copy(alpha = 0.55f * tw), st[2].dp.toPx(), Offset(st[0] * size.width, st[1] * size.height))
                    }
                    // adalar arası ışık iplikleri (ızgara komşuları)
                    for (i in ids.indices) for (j in ids.indices) {
                        if (j <= i) continue
                        val (ci, ri) = ISLAND_SLOT[ids[i]] ?: continue; val (cj, rj) = ISLAND_SLOT[ids[j]] ?: continue
                        if (kotlin.math.abs(ci - cj) + kotlin.math.abs(ri - rj) != 1) continue
                        val a = center(i); val b = center(j)
                        drawLine(Color(0xFFB080FF).copy(alpha = 0.18f + 0.1f * sin(t * 2f + i + j)), a, b, 1.5f.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 7.dp.toPx()), -t * 30f))
                    }
                    for (i in ids.indices) {
                        val a = areas.getJSONObject(i)
                        val col = Color(AREA_COLOR[ids[i]] ?: 0xFFCCCCCC)
                        val open = a.optBoolean("open")
                        val c = center(i) + Offset(0f, sin(t * 1.1f + i * 1.7f) * 3.dp.toPx())
                        val half = s / 2f
                        // altında büyü halkası
                        drawOval(Brush.radialGradient(listOf(Color(0xFFB080FF).copy(alpha = if (i == sel) 0.6f else 0.3f), Color.Transparent),
                            Offset(c.x, c.y + half * 1.05f), half * 1.1f), Offset(c.x - half * 1.1f, c.y + half * 0.8f), Size(half * 2.2f, half * 0.5f))
                        val art = islandArt[i]
                        if (art != null) {
                            val k = min(s * 1.25f / art.width, s * 1.25f / art.height)
                            val dw = art.width * k; val dh = art.height * k
                            drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset((c.x - dw / 2).toInt(), (c.y - dh / 2).toInt()),
                                IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.Medium)
                            // V15: gidilemeyen ada adanın kendi silüetiyle kararır (yalnız piksellerine)
                            if (!open) drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset((c.x - dw / 2).toInt(), (c.y - dh / 2).toInt()),
                                IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.Medium,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xD0080610), androidx.compose.ui.graphics.BlendMode.SrcAtop))
                        } else {
                            // kayalık alt yüz (süzülen ada) + üstte bölgenin kendi haritası
                            val rock = androidx.compose.ui.graphics.Path().apply {
                                moveTo(c.x - half, c.y + half * 0.6f); lineTo(c.x + half, c.y + half * 0.6f)
                                lineTo(c.x + half * 0.55f, c.y + half * 1.05f); lineTo(c.x + half * 0.1f, c.y + half * 1.35f)
                                lineTo(c.x - half * 0.35f, c.y + half * 1.1f); lineTo(c.x - half * 0.7f, c.y + half * 0.95f); close()
                            }
                            drawPath(rock, Brush.verticalGradient(listOf(Color(0xFF5A4636), Color(0xFF241A14)), c.y + half * 0.6f, c.y + half * 1.35f))
                            val top = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - half, c.y - half * 0.8f, c.x + half, c.y + half * 0.75f,
                                    androidx.compose.ui.geometry.CornerRadius(half * 0.35f)))
                            }
                            val crop = crops[i]
                            clipPath(top) {
                                if (crop != null && worldImg != null) drawImage(worldImg, crop.first, crop.second,
                                    IntOffset((c.x - half).toInt(), (c.y - half * 0.8f).toInt()), IntSize(s.toInt(), (half * 1.55f).toInt()), filterQuality = FilterQuality.None)
                                else drawRect(col.copy(alpha = 0.5f))
                            }
                            drawPath(top, Color(0xFF120C18), style = Stroke(4.dp.toPx()))
                            drawPath(top, col.copy(alpha = 0.9f), style = Stroke(2.dp.toPx()))
                            if (!open) drawPath(top, Color(0xC0080610))
                        }
                        if (!open) pixelLock(c, half * 0.55f, t + i)
                        if (i == sel) drawRoundRect(Color(0xFFFFD860).copy(alpha = 0.7f + 0.3f * sin(t * 4f)), Offset(c.x - half - 4.dp.toPx(), c.y - half * 0.8f - 4.dp.toPx()),
                            Size(s + 8.dp.toPx(), half * 1.55f + 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(half * 0.4f), style = Stroke(3.dp.toPx()))
                        if (i == here) {
                            drawCircle(Color(0x553A8AFF), 12.dp.toPx() * (1f + 0.25f * sin(t * 3f)), c)
                            drawCircle(Color.White, 6.dp.toPx(), c); drawCircle(Color(0xFF3A8AFF), 4.dp.toPx(), c)
                        }
                        if (a.optBoolean("titan")) {
                            val p = Offset(c.x + half * 0.75f, c.y - half * 0.65f)
                            drawCircle(Color(0x66FF2A1A), 14.dp.toPx() * (1f + 0.2f * sin(t * 5f)), p)
                            drawCircle(Color(0xFF120C18), 10.dp.toPx(), p); drawCircle(Color(0xFFE03A2A), 8.dp.toPx(), p)
                        }
                    }
                }
                // öğretici: gidilecek adanın kutusu
                (if (tutTarget == null) -1 else ids.indexOf(tutTarget)).takeIf { it >= 0 }?.let { i ->
                    val c = center(i)
                    androidx.compose.foundation.layout.Box(Modifier.offset { IntOffset((c.x - s / 2).toInt(), (c.y - s * 0.45f).toInt()) }
                        .size(with(d) { s.toDp() }, with(d) { (s * 0.9f).toDp() }).tutRect(tutR, "island"))
                }
                // ada adları (kilitliyse kilit ve seviye)
                for (i in ids.indices) {
                    val a = areas.getJSONObject(i)
                    val c = center(i)
                    val lw = cw * 0.96f
                    Column(Modifier.offset { IntOffset((c.x - lw / 2).toInt(), (c.y + s * 0.72f + with(d) { 4.dp.toPx() }).toInt()) }.width(with(d) { lw.toDp() }),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val open = a.optBoolean("open")
                        PT(a.optString("name"), 13.sp, if (i == sel) Px.Clan else if (open) Px.Text else Px.Sub, outline = true)
                        if (!open) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon("lock", 12.dp, Px.Sub); Spacer(Modifier.width(3.dp)); PT("Sv ${a.optInt("level")}", 12.sp, Color(0xFFE07060))
                        } else if (i == here) PT("Buradasın", 11.sp, Color(0xFF7AB8FF))
                        else if (a.optBoolean("titan")) PT("Titan Burada!", 11.sp, Color(0xFFFF5A4A))
                    }
                }
            }
            // ---- bölge kartı
            PxPanel(Modifier.width(330.dp).fillMaxHeight(), color = Color(0xF01A1622), pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT("Aldric'in Haritası", 20.sp, Color(0xFFC8A0FF), Modifier.weight(1f), outline = true)
                    PxIcon("close", size = 38.dp) { close() }
                }
                Spacer(Modifier.height(6.dp))
                val a = areas.optJSONObject(sel)
                if (a == null) {
                    PT("Bir bölgeye dokun: orada ne yaşadığını, ne düştüğünü ve kaçıncı seviyede gitmen gerektiğini söylerim.", 14.sp, Px.Sub, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    PT("Her bölge ayrı bir diyar; aralarında yürünmez, kapımdan geçilir.", 13.sp, Px.Sub, maxLines = 3)
                } else AreaCard(a, sel == here, data.optInt("gold"), data.optDouble("cd", 0.0).toFloat(), data.optBoolean("near", true), items, tutR, teleport)
            }
        }
        if (tutTarget != null) {
            val ti = ids.indexOf(tutTarget)
            val name = areas.optJSONObject(ti)?.optString("name").orEmpty()
            val cd = data.optDouble("cd", 0.0)
            when {
                ti < 0 -> {}
                sel != ti -> TutLayer(listOf(TutTarget(tutR["island"], "$name'a Dokun", below = true)),
                    if (tutTarget == "home") "Parlayan Kişisel Üssüm adasına dokun: sağda kartı açılır." else "Şimdi parlayan Başlangıç Ormanı adasına dokun: oradan geri dönersin.",
                    cardStart = 12, cardEnd = 360)
                cd > 0 -> TutLayer(emptyList(), "Büyü toparlanıyor: süre bitince Işınlan düğmesi açılır.", cardStart = 12, cardEnd = 360)
                else -> TutLayer(listOf(TutTarget(tutR["tp"], "Işınlan'a Bas")), "Sağdaki Işınlan düğmesine bas.", cardStart = 12, cardEnd = 360)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AreaCard(
    a: JSONObject, here: Boolean, gold: Int, cd: Float, near: Boolean, items: Map<String, Pair<String, Int>>,
    tutR: MutableMap<String, androidx.compose.ui.geometry.Rect>, teleport: (String) -> Unit,
) {
    val id = a.optString("id")
    val open = a.optBoolean("open")
    val cost = a.optInt("cost")
    val rec = a.optJSONArray("rec")
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        // bölge görseli (assets/ui/region_<id>.png, 3:1) varsa üstte
        RegionBanner("region_$id", Modifier.fillMaxWidth().height(100.dp))
        PT(a.optString("name"), 22.sp, Color(AREA_COLOR[id] ?: 0xFFE8E4D4), outline = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rec != null) PT("Önerilen Sv ${rec.optInt(0)}–${rec.optInt(1)}", 14.sp, Px.Level)
            Spacer(Modifier.width(10.dp))
            if (a.optBoolean("safe")) PT("Güvenli", 13.sp, Px.Name)
        }
        if (!open) PT("Işınlanmak İçin Sv ${a.optInt("level")} Gerekli", 13.sp, Color(0xFFE07060))
        Spacer(Modifier.height(4.dp))
        PT(a.optString("desc"), 13.sp, Px.Sub, maxLines = 5)
        val mobs = a.optJSONArray("mobs") ?: JSONArray()
        Spacer(Modifier.height(8.dp))
        PT(if (mobs.length() == 0) "Yaratık Yok" else "Yaratıklar", 15.sp, Px.Orange)
        for (i in 0 until mobs.length()) {
            val m = mobs.getJSONObject(i)
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(Color(0x40000000)), contentAlignment = Alignment.Center) {
                    SpriteImage(m.optString("sprite"), Modifier.size(32.dp))
                }
                Spacer(Modifier.width(8.dp))
                PT(m.optString("name"), 14.sp, Color(m.optLong("color", 0xFFE8E4D4).toInt()).copy(alpha = 1f), Modifier.weight(1f))
                PT("Sv ${m.optInt("lo")}–${m.optInt("hi")}", 13.sp, Px.Level)
            }
        }
        val drops = a.optJSONArray("drops") ?: JSONArray()
        if (drops.length() > 0) {
            Spacer(Modifier.height(8.dp))
            PT("Çıkan Eşyalar", 15.sp, Px.Orange)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until drops.length()) {
                    val d = drops.getString(i)
                    val (name, rarity) = items[d] ?: (d to 0)
                    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ItemSlot(d, rarity = rarity, size = 40.dp)
                        PT(name, 9.sp, Px.Sub, maxLines = 2)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val label = when {
        here -> "Buradasın"
        !open -> "Sv ${a.optInt("level")} Gerekli"
        cd > 0f -> "Büyü Toparlanıyor: ${cd.toInt() + 1} sn"
        cost > 0 -> "Işınlan · $cost Altın"
        else -> "Işınlan · Ücretsiz"
    }
    val ok = !here && open && cd <= 0f && near && gold >= cost
    PxButton(label, Modifier.fillMaxWidth().tutRect(tutR, "tp"), color = Color(0xFF7A4AB5), icon = "map", height = 48.dp, size = 16.sp, enabled = ok) { teleport(id) }
    if (open && !here && gold < cost) PT("Yetersiz Altın", 12.sp, Color(0xFFE07060))
}

/** Bölge afişi: kutuyu doldurur (taşan kırpılır), yumuşak ölçek (görsel büyük çözünürlüklü); yoksa hiç yer kaplamaz. */
@Composable
private fun RegionBanner(name: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val img = remember(name) { UiArt.image(ctx, name) } ?: return
    Canvas(modifier) {
        val k = maxOf(size.width / img.width, size.height / img.height)
        val sw = (size.width / k).toInt(); val sh = (size.height / k).toInt()
        drawImage(img, IntOffset((img.width - sw) / 2, (img.height - sh) / 2), IntSize(sw, sh), IntOffset.Zero,
            IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
        drawRect(Color(0xFF0E0A14), style = Stroke(2.dp.toPx()))
    }
}
