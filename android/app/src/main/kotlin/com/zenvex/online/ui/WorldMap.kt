package com.zenvex.online.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Native
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** V12 bölgesi: ad ve dünya pikseli dikdörtgenleri (x0, y0, x1, y1) */
class MapArea(val id: String, val name: String, val rects: List<FloatArray>) {
    fun contains(x: Float, y: Float) = rects.any { x >= it[0] && x < it[2] && y >= it[1] && y < it[3] }
}

fun mapAreas(w: JSONObject): List<MapArea> {
    val a = w.optJSONArray("areas") ?: return emptyList()
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val rs = o.optJSONArray("rects") ?: JSONArray()
        MapArea(o.optString("id"), o.optString("name"), (0 until rs.length()).map { k -> val r = rs.getJSONArray(k); FloatArray(4) { r.getDouble(it).toFloat() } })
    }
}

/** Noktanın bölgesi (kişisel üs, içinde durduğu Donmuş Liman'dan önce); bölge yoksa -1 */
fun areaIndexAt(areas: List<MapArea>, x: Float, y: Float): Int {
    val home = areas.indexOfFirst { it.id == "home" }
    if (home >= 0 && areas[home].contains(x, y)) return home
    return areas.indexOfFirst { it.contains(x, y) }
}

/** Haritadaki bir işaret (dünya pikseli). kind: 0 oyuncu, 1 seyahat, 2 titan, 3 görev, 4 kamp NPC'si, 5 devlet üssü */
data class MapMark(val x: Float, val y: Float, val kind: Int, val label: String = "", val sub: String = "", val color: Int = 0, val icon: String = "")

/** Keşif bulutu maskesi: keşfedilmemiş hücre opak (yumuşak kenar için küçük bitmap büyütülerek çizilir). */
fun fogMask(fog: JSONObject): ImageBitmap? {
    val n = fog.optInt("n")
    val rows = fog.optJSONArray("rows") ?: return null
    if (n <= 0) return null
    val px = IntArray(n * n)
    for (y in 0 until n) {
        // satır: 16 onaltılık hane = 64 hücre; kelimeler yüksekten alçağa (V8: 2 kelime, V11: 3 kelime)
        val row = rows.optString(y, "0")
        val words = (row.length + 15) / 16
        val w = LongArray(words) { k ->
            val end = row.length - k * 16
            java.lang.Long.parseUnsignedLong(row.substring(maxOf(0, end - 16), end), 16)
        }
        for (x in 0 until n) {
            val bit = w.getOrElse(x / 64) { 0L } ushr (x % 64) and 1L
            px[y * n + x] = if (bit == 1L) 0 else 0xFF000000.toInt()
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Bulut dokusu: katmanlı değer gürültüsü (beyaz-gri, tekrarlanabilir). */
private val cloudTex: ImageBitmap by lazy {
    val n = 128
    val rnd = java.util.Random(7)
    fun grid(s: Int) = Array(s + 1) { FloatArray(s + 1) { rnd.nextFloat() } }.also { g -> for (i in 0..s) { g[i][s] = g[i][0]; g[s][i] = g[0][i] } }
    val octs = listOf(4 to 0.5f, 8 to 0.3f, 16 to 0.2f).map { (s, w) -> Triple(s, w, grid(s)) }
    val px = IntArray(n * n)
    for (y in 0 until n) for (x in 0 until n) {
        var v = 0f
        for ((s, w, g) in octs) {
            val fx = x * s / n.toFloat(); val fy = y * s / n.toFloat()
            val ix = fx.toInt(); val iy = fy.toInt(); val tx = fx - ix; val ty = fy - iy
            val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
            val a = g[iy][ix] + (g[iy][ix + 1] - g[iy][ix]) * sx
            val b = g[iy + 1][ix] + (g[iy + 1][ix + 1] - g[iy + 1][ix]) * sx
            v += (a + (b - a) * sy) * w
        }
        val c = (150 + v * 105).toInt().coerceIn(0, 255)
        px[y * n + x] = (0xFF shl 24) or (c shl 16) or (c shl 8) or min(255, c + 12)
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Dünya haritası: iki parmakla yakınlaştır, sürükleyerek kaydır; keşfedilmemiş yerler bulutlu. */
@Composable
fun WorldMapScreen(close: () -> Unit) {
    val img = remember { WorldMapImage.get() }
    val hud = remember { GameSession.hud() }
    val w = remember { GameSession.world() }
    val fog = remember { fogMask(GameSession.ui("fog")) }
    val prof = remember { GameSession.ui("profile") }
    val marks = remember { mapMarks(w, GameSession.ui("quests")) + baseMarks(GameSession.ui("bases")) + titanMark(GameSession.ui("areas")) }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.zenvex.online.game.Bases.refresh() }
    WorldMapView(img, fog, w, hud[Native.PX], hud[Native.PY], prof.optInt("level", 1), marks, close) { to ->
        val r = GameSession.cmd("travel", "to" to to)
        Toaster.show(r.msg, r.ok)
        if (r.ok) close()
    }
}

/** V12.1: Titan her 15 dakikada başka bir bölgede; bu dilimde öldürülmediyse yeri ve kalan süresi */
fun titanMark(areas: JSONObject): List<MapMark> {
    val t = areas.optJSONObject("titan") ?: return emptyList()
    if (t.optBoolean("dead")) return emptyList()
    val left = t.optDouble("left").toInt()
    return listOf(MapMark(t.optDouble("x").toFloat(), t.optDouble("y").toFloat(), 2, "Titan", "${left / 60}:${"%02d".format(left % 60)} Sonra Başka Bölgeye Geçer"))
}

/** Keşfedilen devlet üsleri: sahip, ödül, kalan süre (dokununca kart). */
fun baseMarks(b: JSONObject): List<MapMark> {
    val out = ArrayList<MapMark>()
    val list = b.optJSONArray("bases") ?: return out
    for (i in 0 until list.length()) {
        val o = list.getJSONObject(i)
        if (!o.optBoolean("found")) continue
        val left = o.optDouble("left", -1.0)
        val time = if (left >= 0) " · ${(left / 60).toInt()}:${"%02d".format((left % 60).toInt())} Kaldı" else ""
        val mine = o.optBoolean("mine")
        val owner = if (mine) "Klanının Üssü" else "Sahibi: ${o.optString("owner")}"
        val col = when { mine -> 0xFF60E080.toInt(); o.optLong("clan") == 0L -> 0xFF6E7A3E.toInt(); else -> 0xFF000000.toInt() or (o.optInt("color") and 0xFFFFFF) }
        val reward = if (o.optString("reward").isNotEmpty()) "Ödül: ${o.optString("reward")} ×${o.optInt("n", 1)}$time" else "Ödül Bilgisi Bekleniyor"
        // rozet: devlet (yeşil yıldız), kendi klanın (kalkan), başka klan (çapraz tüfek)
        val icon = when { mine -> "map_clan"; o.optLong("clan") == 0L -> "map_gov"; else -> "map_war" }
        out += MapMark(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), 5, "${o.optString("name")} · Sv ${o.optInt("level")}",
            "$owner\n$reward" + if (o.optLong("clan") == 0L) "\nDevlet Üssü Ödül Vermez; Klanla Ele Geçir" else "\nKlana Dakikada 15 Tecrübe", col, icon)
    }
    return out
}

/** Seyahat noktaları, titan, kamp NPC'leri ve etkin görev hedefi. */
fun mapMarks(w: JSONObject, quests: JSONObject): List<MapMark> {
    val out = ArrayList<MapMark>()
    val travel = w.optJSONArray("travel") ?: JSONArray()
    for (i in 0 until travel.length()) {
        val a = travel.getJSONArray(i)
        out += MapMark(a.getDouble(2).toFloat(), a.getDouble(3).toFloat(), 1, a.getString(1))
    }
    val st = quests.optJSONArray("story") ?: JSONArray()
    for (i in 0 until st.length()) {
        val q = st.getJSONObject(i)
        if (q.has("x")) out += MapMark(q.getDouble("x").toFloat(), q.getDouble("y").toFloat(), 3,
            if (q.optBoolean("ready")) "${q.optString("title")} · Teslim: ${q.optString("giver")}" else q.optString("title"))
    }
    for (l in w.optJSONArray("landmarks").let { it ?: JSONArray() }.let { a -> (0 until a.length()).map { a.getJSONArray(it) } }) {
        out += MapMark(l.getDouble(1).toFloat(), l.getDouble(2).toFloat(), 4, l.getString(0))
    }
    return out
}

@Composable
internal fun WorldMapView(
    img: ImageBitmap?, fog: ImageBitmap?, w: JSONObject, meX: Float, meY: Float, level: Int, allMarks: List<MapMark>,
    close: () -> Unit, initialPick: MapMark? = null, travel: (String) -> Unit,
) {
    val mapPx = w.optDouble("map_px", 23040.0).toFloat()
    // V12: harita yalnız bulunulan bölgeyi gösterir (kamera bölgeye sınırlı, dışı karanlık; diğer bölgeler büyücüden)
    val areas = remember(w) { mapAreas(w) }
    val here = remember(areas) { areaIndexAt(areas, meX, meY) }
    val area = areas.getOrNull(here)
    val bx0 = area?.rects?.minOf { it[0] } ?: 0f; val by0 = area?.rects?.minOf { it[1] } ?: 0f
    val bx1 = area?.rects?.maxOf { it[2] } ?: mapPx; val by1 = area?.rects?.maxOf { it[3] } ?: mapPx
    val travelAll = w.optJSONArray("travel") ?: JSONArray()
    val travelList = remember(travelAll) {
        JSONArray().also { out -> for (i in 0 until travelAll.length()) { val a = travelAll.getJSONArray(i); if (here < 0 || a.optInt(6, here) == here) out.put(a) } }
    }
    val marks = remember(allMarks, areas) { if (area == null) allMarks else allMarks.filter { areaIndexAt(areas, it.x, it.y) == here } }
    var view by remember { mutableStateOf(IntSize.Zero) }
    // kamera: dünya pikseli cinsinden merkez + ekran pikseli/dünya pikseli ölçeği
    var zoom by remember { mutableFloatStateOf(0f) }
    var cx by remember { mutableFloatStateOf(meX) }
    var cy by remember { mutableFloatStateOf(meY) }
    var showTravel by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(initialPick) }
    val t = rememberTime()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val badges = remember { listOf("map_gov", "map_clan", "map_war", "map_unknown").associateWith { UiArt.image(ctx, it) } }
    fun minZoom() = if (view.width == 0) 0.02f else min(view.width / (bx1 - bx0), view.height / (by1 - by0))
    fun clampCam() {
        val z = zoom
        val hw = view.width / 2f / z; val hh = view.height / 2f / z
        cx = if (hw * 2 >= bx1 - bx0) (bx0 + bx1) / 2 else cx.coerceIn(bx0 + hw, bx1 - hw)
        cy = if (hh * 2 >= by1 - by0) (by0 + by1) / 2 else cy.coerceIn(by0 + hh, by1 - hh)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF1A2414)).onSizeChanged {
        view = it
        if (zoom == 0f) zoom = (minZoom() * 2.5f).coerceAtMost(0.6f)
        clampCam()
    }) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gz, _ ->
                        val old = zoom
                        val nz = (zoom * gz).coerceIn(minZoom(), 0.6f)
                        // parmakların altındaki nokta yerinde kalsın
                        val wx = cx + (centroid.x - view.width / 2f) / old
                        val wy = cy + (centroid.y - view.height / 2f) / old
                        zoom = nz
                        cx = wx - (centroid.x - view.width / 2f) / nz - pan.x / nz
                        cy = wy - (centroid.y - view.height / 2f) / nz - pan.y / nz
                        clampCam()
                    }
                }
                .pointerInput(marks) {
                    detectTapGestures { p ->
                        val z = zoom
                        picked = marks.minByOrNull { m ->
                            val sx = (m.x - cx) * z + view.width / 2f; val sy = (m.y - cy) * z + view.height / 2f
                            (sx - p.x) * (sx - p.x) + (sy - p.y) * (sy - p.y)
                        }?.takeIf { m ->
                            val sx = (m.x - cx) * z + view.width / 2f; val sy = (m.y - cy) * z + view.height / 2f
                            (sx - p.x) * (sx - p.x) + (sy - p.y) * (sy - p.y) < (40.dp.toPx() * 40.dp.toPx())
                        }
                    }
                },
        ) {
            val z = zoom
            if (z == 0f) return@Canvas
            val ox = size.width / 2f - cx * z
            val oy = size.height / 2f - cy * z
            fun at(x: Float, y: Float) = Offset(ox + x * z, oy + y * z)
            if (img != null) {
                val k = mapPx / img.width * z
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(ox.toInt(), oy.toInt()),
                    IntSize((img.width * k).toInt(), (img.height * k).toInt()), filterQuality = FilterQuality.None)
            }
            // bulutlar: doku katmanı, keşif maskesiyle kesilir (yumuşak kenar: maske büyütülürken süzülür)
            if (fog != null) drawClouds(fog, ox, oy, mapPx * z, t)
            // bölge dışı: karanlık (başka harita), sınırda büyülü mor çizgi
            if (area != null) {
                val hole = androidx.compose.ui.graphics.Path().apply {
                    for (r in area.rects) addRect(androidx.compose.ui.geometry.Rect(at(r[0], r[1]), at(r[2], r[3])))
                }
                clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) {
                    drawRect(Color(0xF00C0A12))
                }
                drawPath(hole, Color(0xFFB080FF).copy(alpha = 0.55f + 0.25f * sin(t * 2f)), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            // işaretler
            for (m in marks) {
                val p = at(m.x, m.y)
                when (m.kind) {
                    1 -> { drawCircle(Color(0xFF15140F), 9.dp.toPx(), p); drawCircle(Px.Clan, 6.dp.toPx(), p) }
                    2 -> { drawCircle(Color(0xFF15140F), 10.dp.toPx(), p); drawCircle(Color(0xFFE03030), 7.dp.toPx(), p) }
                    3 -> {
                        val pulse = 1f + 0.25f * sin(t * 4f)
                        drawCircle(Color(0x55FF9A2A), 18.dp.toPx() * pulse, p)
                        drawCircle(Color(0xFF15140F), 9.dp.toPx(), p); drawCircle(Color(0xFFFF9A2A), 6.dp.toPx(), p)
                    }
                    5 -> {
                        val s = 9.dp.toPx()
                        val b = badges[m.icon]
                        if (b != null) {
                            // klan rengi halkası (başka klanın üssü), rozet en yakın komşu ile büyütülür
                            val r = 15.dp.toPx()
                            if (m.icon == "map_war") drawCircle(Color(m.color), r + 3.dp.toPx(), p)
                            drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), IntOffset((p.x - r).toInt(), (p.y - r).toInt()),
                                IntSize((2 * r).toInt(), (2 * r).toInt()), filterQuality = FilterQuality.None)
                            continue
                        }
                        drawRect(Color(0xFF15140F), Offset(p.x - s - 2, p.y - s - 2), Size(2 * s + 4, 2 * s + 4))
                        drawRect(Color(m.color), Offset(p.x - s, p.y - s), Size(2 * s, 2 * s))
                        drawRect(Color(0x80FFFFFF), Offset(p.x - s * 0.35f, p.y - s * 0.35f), Size(s * 0.7f, s * 0.7f))
                    }
                    else -> drawCircle(Color(0xFFB0E0FF), 4.dp.toPx(), p)
                }
            }
            val me = at(meX, meY)
            drawCircle(Color(0x553A8AFF), 16.dp.toPx() * (1f + 0.2f * sin(t * 3f)), me)
            drawCircle(Color.White, 8.dp.toPx(), me)
            drawCircle(Color(0xFF3A8AFF), 5.5f.dp.toPx(), me)
        }
        // üst şerit
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PxIcon("close", size = 44.dp) { close() }
            Spacer(Modifier.width(10.dp))
            Column {
                PT(area?.name ?: "Dünya Haritası", 24.sp, Px.Orange, outline = true)
                if (area != null) PT("Diğer Bölgelere Büyücü Aldric Işınlar", 12.sp, Color(0xFFC8A0FF))
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PxIcon("pin", size = 44.dp) { cx = meX; cy = meY; clampCam() }
            PxIcon("plus", size = 44.dp) { zoom = (zoom * 1.6f).coerceIn(minZoom(), 0.6f); clampCam() }
            PxIcon("minus", size = 44.dp) { zoom = (zoom / 1.6f).coerceIn(minZoom(), 0.6f); clampCam() }
            PxButton("Seyahat", height = 44.dp, size = 16.sp, color = Px.Blue, icon = "map") { showTravel = !showTravel }
        }
        PxPanel(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color(0xD0201F1C), pad = 8.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(Color(0xFF3A8AFF), "Sen"); Legend(Px.Clan, "Seyahat"); Legend(Color(0xFFFF9A2A), "Görev"); Legend(Color(0xFFE03030), "Titan")
                LegendArt("map_gov", "Devlet Üssü"); LegendArt("map_war", "Klan Üssü")
                PT("İki Parmakla Yakınlaştır", 12.sp, Px.Sub)
            }
        }
        picked?.let { m ->
            if (m.kind == 5) {
                // üs kartı: V8 tehlike şeritli askeri panel + rozet
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp).metalFrame(1.dp, "panel_base", 32)
                    .padding(horizontal = 30.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (m.icon.isNotEmpty()) { ArtImage(m.icon, Modifier.size(44.dp)); Spacer(Modifier.width(10.dp)) }
                    Column {
                        PT(m.label, 18.sp, Px.Text, outline = true)
                        for (l in m.sub.split('\n').filter { it.isNotEmpty() }) PT(l, 14.sp, Px.Clan, maxLines = 2)
                    }
                }
            } else PxPanel(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), color = Color(0xE02B2B26), pad = 10.dp) {
                PT(m.label.ifEmpty { "İşaret" }, 18.sp, Px.Text)
                for (l in m.sub.split('\n').filter { it.isNotEmpty() }) PT(l, 14.sp, Px.Sub, maxLines = 2)
            }
        }
        AnimatedVisibility(showTravel, Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier.padding(top = 68.dp, end = 12.dp, bottom = 12.dp).width(290.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0 until travelList.length()) {
                    val a = travelList.getJSONArray(i)
                    val need = a.getInt(4); val cost = a.getInt(5)
                    val ok = level >= need
                    PxPanel(Modifier.fillMaxWidth(), Color(0xE02B2B26), pad = 8.dp) {
                        PT(a.getString(1), 17.sp, if (ok) Px.Text else Px.Sub)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PxButton("Git", height = 36.dp, size = 15.sp, enabled = ok) { travel(a.getString(0)) }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                PT(if (ok) "Seviye $need+" else "Seviye $need Gerekli", 13.sp, if (ok) Px.Level else Color(0xFFE07060))
                                PT(if (cost > 0) "$cost Altın" else "Ücretsiz", 13.sp, Px.Clan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendArt(name: String, s: String) {
    ArtImage(name, Modifier.size(16.dp))
    Spacer(Modifier.width(5.dp)); PT(s, 13.sp); Spacer(Modifier.width(12.dp))
}

@Composable
private fun Legend(c: Color, s: String) {
    Box(Modifier.size(12.dp).background(Color(0xFF15140F)).padding(2.dp).background(c))
    Spacer(Modifier.width(5.dp)); PT(s, 13.sp); Spacer(Modifier.width(12.dp))
}

private fun DrawScope.drawClouds(fog: ImageBitmap, ox: Float, oy: Float, side: Float, t: Float) {
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint())
    // doku: ekran boyunca kayan bulut
    val tile = 220.dp.toPx()
    val sx = (ox * 0.5f + t * 6f) % tile - tile; val sy = (oy * 0.5f) % tile - tile
    var y = sy
    while (y < size.height) {
        var x = sx
        while (x < size.width) {
            drawImage(cloudTex, IntOffset.Zero, IntSize(cloudTex.width, cloudTex.height), IntOffset(x.toInt(), y.toInt()),
                IntSize(tile.toInt() + 1, tile.toInt() + 1), filterQuality = FilterQuality.High)
            x += tile
        }
        y += tile
    }
    // maske: keşfedilmemiş hücreler kalır (yumuşak geçiş için büyütmede süzme)
    drawImage(fog, IntOffset.Zero, IntSize(fog.width, fog.height), IntOffset(ox.toInt(), oy.toInt()), IntSize(side.toInt(), side.toInt()),
        blendMode = BlendMode.DstIn, filterQuality = FilterQuality.High)
    drawContext.canvas.restore()
}
