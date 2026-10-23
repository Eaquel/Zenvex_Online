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
    val data = remember { GameSession.ui("areas") }
    val items = remember { itemNames(GameSession.world()) }
    val img = remember { WorldMapImage.get() }
    WizardMapView(data, items, img, close) { id ->
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

/**
 * Büyücü Aldric'in haritası: dünya bölgelere ayrılmış olarak görünür. Bir bölgeye dokununca sağda kartı açılır:
 * önerilen seviye, gereken seviye, yaratıklar, çıkan eşyalar, ücret ve "Işınlan". Kilitli bölgeler kararır.
 * Çizilmiş harita görseli (assets/ui/wizard_map.png) varsa zemin o; yoksa dünyanın piksel haritası.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WizardMapView(data: JSONObject, items: Map<String, Pair<String, Int>>, worldImg: ImageBitmap?, close: () -> Unit, initial: Int = -1,
                           teleport: (String) -> Unit) {
    val ctx = LocalContext.current
    val art = remember { UiArt.image(ctx, "wizard_map") }
    val areas = remember(data) { data.optJSONArray("areas") ?: JSONArray() }
    val here = data.optInt("here", 0)
    var sel by remember { mutableStateOf(if (initial >= 0) initial else -1) }
    val t = rememberTime()
    // harita dünyası kare: tüm bölgelerin dış sınırı
    val mapPx = remember(areas) {
        var m = 1f
        for (i in 0 until areas.length()) {
            val rs = areas.getJSONObject(i).optJSONArray("rects") ?: continue
            for (k in 0 until rs.length()) { val r = rs.getJSONArray(k); m = maxOf(m, r.getDouble(2).toFloat(), r.getDouble(3).toFloat()) }
        }
        m
    }
    fun rectsOf(i: Int): List<FloatArray> {
        val rs = areas.getJSONObject(i).optJSONArray("rects") ?: return emptyList()
        return (0 until rs.length()).map { k -> val r = rs.getJSONArray(k); FloatArray(4) { r.getDouble(it).toFloat() } }
    }
    val rects = remember(areas) { (0 until areas.length()).map { rectsOf(it) } }
    val homeIdx = remember(areas) { (0 until areas.length()).firstOrNull { areas.getJSONObject(it).optString("id") == "home" } ?: -1 }
    fun areaAt(x: Float, y: Float): Int {
        fun inside(i: Int) = rects[i].any { x >= it[0] && x < it[2] && y >= it[1] && y < it[3] }
        if (homeIdx >= 0 && inside(homeIdx)) return homeIdx
        return rects.indices.firstOrNull { inside(it) } ?: -1
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A1E3A), Color(0xFF0E0A14))))) {
        Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- harita
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth.value, maxHeight.value).dp
                val sidePx = with(LocalDensity.current) { side.toPx() }
                val k = sidePx / mapPx
                Box(Modifier.size(side)) {
                    Canvas(Modifier.fillMaxSize().pointerInput(rects) {
                        detectTapGestures { p -> areaAt(p.x / k, p.y / k).takeIf { it >= 0 }?.let { sel = it } }
                    }) {
                        val bg = art ?: worldImg
                        if (bg != null) {
                            drawImage(bg, IntOffset.Zero, IntSize(bg.width, bg.height), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()),
                                filterQuality = if (art != null) FilterQuality.Medium else FilterQuality.None)
                        } else drawRect(Color(0xFF243018))
                        // büyülü parşömen tonu
                        drawRect(Color(0x302A1040))
                        for (i in rects.indices) {
                            val a = areas.getJSONObject(i)
                            val col = Color(AREA_COLOR[a.optString("id")] ?: 0xFFCCCCCC)
                            val open = a.optBoolean("open")
                            for (r in rects[i]) {
                                val o = Offset(r[0] * k, r[1] * k); val s = Size((r[2] - r[0]) * k, (r[3] - r[1]) * k)
                                when {
                                    !open -> drawRect(Color(0xB8080610), o, s)
                                    i == sel -> drawRect(col.copy(alpha = 0.22f + 0.1f * sin(t * 4f)), o, s)
                                    art == null -> drawRect(col.copy(alpha = 0.12f), o, s)
                                }
                            }
                        }
                        // sınırlar: önce koyu, sonra renkli; seçili bölge parlak altın
                        for (i in rects.indices) {
                            val a = areas.getJSONObject(i)
                            val col = Color(AREA_COLOR[a.optString("id")] ?: 0xFFCCCCCC)
                            val w = if (i == sel) 3.dp.toPx() else 1.5f.dp.toPx()
                            for (r in rects[i]) {
                                val o = Offset(r[0] * k, r[1] * k); val s = Size((r[2] - r[0]) * k, (r[3] - r[1]) * k)
                                drawRect(Color(0xE0100C14), o, s, style = Stroke(w + 2.dp.toPx()))
                                drawRect(if (i == sel) Color(0xFFFFD860) else col.copy(alpha = 0.85f), o, s, style = Stroke(w))
                            }
                        }
                        // buradasın: nabız gibi atan mavi işaret bölgenin girişinde
                        areas.optJSONObject(here)?.optJSONArray("hub")?.let { h ->
                            val p = Offset(h.getDouble(0).toFloat() * k, h.getDouble(1).toFloat() * k)
                            drawCircle(Color(0x553A8AFF), 14.dp.toPx() * (1f + 0.25f * sin(t * 3f)), p)
                            drawCircle(Color.White, 6.dp.toPx(), p); drawCircle(Color(0xFF3A8AFF), 4.dp.toPx(), p)
                        }
                    }
                    // bölge adları (kilitliyse kilit ve seviye)
                    for (i in rects.indices) {
                        val a = areas.getJSONObject(i)
                        val r = rects[i].firstOrNull() ?: continue
                        val home = a.optString("id") == "home"
                        val cx = (r[0] + r[2]) / 2f * k; val cy = (if (home) r[3] + 30f * 64f else (r[1] + r[3]) / 2f) * k
                        val d = LocalDensity.current
                        // etiket bölgenin genişliğine sığar (dar bölgede iki satır)
                        val lw = with(d) { min(140.dp.toPx(), if (home) 120.dp.toPx() else (r[2] - r[0]) * k - 6.dp.toPx()) }
                        Column(Modifier.offset { IntOffset((cx - lw / 2).toInt(), (cy - with(d) { 16.dp.toPx() }).toInt()) }.width(with(d) { lw.toDp() }),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            val open = a.optBoolean("open")
                            PT(a.optString("name"), if (home) 11.sp else 14.sp, if (i == sel) Px.Clan else if (open) Px.Text else Px.Sub,
                                align = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, outline = true)
                            if (!open) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon("lock", 12.dp, Px.Sub); Spacer(Modifier.width(3.dp)); PT("Sv ${a.optInt("level")}", 12.sp, Color(0xFFE07060))
                            } else if (i == here) PT("Buradasın", 11.sp, Color(0xFF7AB8FF))
                        }
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
                    PT("Bölgeler artık ayrı haritalar; aralarında yürünmez, kapımdan geçilir.", 13.sp, Px.Sub, maxLines = 3)
                } else AreaCard(a, sel == here, data.optInt("gold"), data.optDouble("cd", 0.0).toFloat(), data.optBoolean("near", true), items, teleport)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AreaCard(
    a: JSONObject, here: Boolean, gold: Int, cd: Float, near: Boolean, items: Map<String, Pair<String, Int>>, teleport: (String) -> Unit,
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
    PxButton(label, Modifier.fillMaxWidth(), color = Color(0xFF7A4AB5), icon = "map", height = 48.dp, size = 16.sp, enabled = ok) { teleport(id) }
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
