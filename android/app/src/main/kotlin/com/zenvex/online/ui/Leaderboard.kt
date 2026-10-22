package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.Pass
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =========================================================================== kategoriler
/** Sıralama kategorisi: sunucu anahtarı, sekme adı, simge, ilk 10'a verilen unvan, değer biçimi. */
data class BoardCat(val kind: String, val name: String, val icon: String, val title: String, val art: String? = null, val unit: (Long) -> String)

// art: V10 piksel kategori simgesi (assets/ui); yoksa çizim ikon
val BOARD_CATS = listOf(
    BoardCat("level", "Seviye", "star", "Efsane") { "Sv $it" },
    BoardCat("kills", "Zombi Avı", "skull", "Canavar Avcısı", "lb_kills") { "${fmtL(it)} Zombi" },
    BoardCat("pvp", "Oyuncu Avı", "attack", "Oyuncu Katili", "lb_pvp") { "${fmtL(it)} Oyuncu" },
    BoardCat("titan", "Titan", "flame", "Titan Avcısı", "lb_titan") { "${fmtL(it)} Titan" },
    BoardCat("soldiers", "Asker", "shield", "Savaş Lordu") { "${fmtL(it)} Asker" },
    BoardCat("bases", "Üs Fethi", "map", "Fatih", "lb_bases") { "${fmtL(it)} Üs" },
    BoardCat("dog", "Köpek", "paw", "Sürü Lideri", "lb_dog") { "${fmtL(it)} Av" },
    BoardCat("crafted", "Zanaat", "hammer", "Usta Zanaatkâr", "lb_crafted") { "${fmtL(it)} Üretim" },
    BoardCat("time", "Emektar", "calendar", "Emektar", "lb_time") { "${fmtL(it / 3600)} Saat" },
)

fun fmtL(n: Long): String = "%,d".format(n).replace(',', '.')

private val GOLD = Color(0xFFFFC83A)
private val SILVER = Color(0xFFDCE4EE)
private val BRONZE = Color(0xFFE0904A)
fun rankColor(r: Int): Color = when (r) { 1 -> GOLD; 2 -> SILVER; 3 -> BRONZE; else -> Color(0xFF8CC8FF) }

// =========================================================================== madalya
/**
 * Sıra madalyası (tamamen kodla): ilk 3 kurdeleli, kabartmalı, yıldızlı ve parıltılı; 1. dönen ışınlı.
 * 4 ve sonrası sade numara plakası.
 */
@Composable
fun RankMedal(rank: Int, size: Dp = 40.dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // V10: 1-2-3 taç/defne/kalkan madalyaları, 4-10 çelik altıgen rozet (numara alt yuvada)
    if (rank in 1..10 && remember { UiArt.image(ctx, "badge_top10") } != null) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (rank == 1) {
                val t = rememberTime()
                Canvas(Modifier.fillMaxSize()) {
                    val w = this.size.width; val c = Offset(w / 2, w * 0.62f)
                    for (i in 0 until 8) {
                        val a = t * 0.8f + i * PI.toFloat() / 4
                        val p = Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + cos(a - 0.12f) * w * 0.55f, c.y + sin(a - 0.12f) * w * 0.55f)
                            lineTo(c.x + cos(a + 0.12f) * w * 0.55f, c.y + sin(a + 0.12f) * w * 0.55f)
                            close()
                        }
                        drawPath(p, Brush.radialGradient(listOf(GOLD.copy(alpha = 0.45f), Color.Transparent), c, w * 0.55f))
                    }
                }
            }
            if (rank <= 3) {
                // madalya süzülür ve üstünden ışık geçer
                val mt = rememberTime()
                Box(Modifier.fillMaxSize().graphicsLayer { translationY = sin(mt * 2.4f + rank) * 1.5f * density; compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val u = ((mt * 0.4f + rank * 0.3f) % 1.5f) * (this.size.width + 60f) - 30f
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent), Offset(u - 14f, 0f), Offset(u + 14f, this.size.height)),
                            blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    }) { ArtImage("medal_$rank", Modifier.fillMaxSize()) }
            }
            else {
                ArtImage("badge_top10", Modifier.fillMaxSize())
                PT("$rank", (size.value * 0.26f).sp, Px.Text, Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.13f))
            }
        }
        return
    }
    if (rank !in 1..3) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size * 0.78f).clip(RoundedCornerShape(3.dp)).background(Color(0xFF26251F)).border(2.dp, Color(0xFF4A4A40), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center) { PT("$rank", (size.value * 0.36f).sp, Px.Sub) }
        }
        return
    }
    val t = rememberTime()
    val main = rankColor(rank)
    val dark = when (rank) { 1 -> Color(0xFF9A6A08); 2 -> Color(0xFF7A8696); else -> Color(0xFF7A4418) }
    val ribbon = when (rank) { 1 -> Color(0xFFC8283A); 2 -> Color(0xFF2E5AB8); else -> Color(0xFF2E8A4A) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val cx = w / 2; val cy = w * 0.58f; val r = w * 0.34f
            // ışınlar (yalnız 1.)
            if (rank == 1) for (i in 0 until 8) {
                val a = t * 0.8f + i * PI.toFloat() / 4
                val p = Path().apply {
                    moveTo(cx, cy)
                    lineTo(cx + cos(a - 0.12f) * w * 0.52f, cy + sin(a - 0.12f) * w * 0.52f)
                    lineTo(cx + cos(a + 0.12f) * w * 0.52f, cy + sin(a + 0.12f) * w * 0.52f)
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(GOLD.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), w * 0.52f))
            }
            // kurdele
            val rw = w * 0.16f
            drawPath(Path().apply { moveTo(cx - rw * 1.9f, 0f); lineTo(cx - rw * 0.9f, 0f); lineTo(cx + rw * 0.2f, cy - r * 0.6f); lineTo(cx - rw * 0.8f, cy - r * 0.6f); close() }, ribbon)
            drawPath(Path().apply { moveTo(cx + rw * 1.9f, 0f); lineTo(cx + rw * 0.9f, 0f); lineTo(cx - rw * 0.2f, cy - r * 0.6f); lineTo(cx + rw * 0.8f, cy - r * 0.6f); close() },
                Color(ribbon.red * 0.75f, ribbon.green * 0.75f, ribbon.blue * 0.75f))
            // madalya gövdesi: koyu kenar, metal yüz, iç halka
            drawCircle(Color(0xFF15140F), r + w * 0.04f, Offset(cx, cy))
            drawCircle(Brush.linearGradient(listOf(Color.White, main, dark), Offset(cx - r, cy - r), Offset(cx + r, cy + r)), r, Offset(cx, cy))
            drawCircle(dark, r * 0.78f, Offset(cx, cy), style = Stroke(w * 0.03f))
            // yıldız
            val star = Path()
            for (i in 0 until 10) {
                val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                val rr = if (i % 2 == 0) r * 0.58f else r * 0.25f
                val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, dark.copy(alpha = 0.55f))
            // parıltı: yüzeyde kayan ışık + köşede yanıp sönen kıvılcım
            val sh = ((t * 0.45f + rank * 0.3f) % 1f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent),
                Offset(cx - r + 2 * r * sh, cy - r * 0.4f), r * 0.5f), r * 0.5f, Offset(cx - r + 2 * r * sh, cy - r * 0.4f))
            val k = (sin(t * 4f + rank) * 0.5f + 0.5f)
            val sp = Offset(cx + r * 0.7f, cy - r * 0.75f)
            drawLine(Color.White.copy(alpha = k), Offset(sp.x - w * 0.09f * k, sp.y), Offset(sp.x + w * 0.09f * k, sp.y), w * 0.025f)
            drawLine(Color.White.copy(alpha = k), Offset(sp.x, sp.y - w * 0.09f * k), Offset(sp.x, sp.y + w * 0.09f * k), w * 0.025f)
        }
        PT("$rank", (size.value * 0.3f).sp, Color(0xFF2A1A06), Modifier.padding(top = size * 0.16f))
    }
}

// =========================================================================== unvan
/** Unvan şeridi görseli: dilim uçları ve yazı bandı (kaynak px). Oyun içi isim etiketi de kullanır. */
class Plate(val name: String, val capL: Int, val capR: Int, val barTop: Int, val barBot: Int) {
    companion object {
        private val P = listOf(Plate("plate_1", 139, 141, 17, 46), Plate("plate_2", 129, 111, 12, 41), Plate("plate_3", 55, 65, 15, 45),
            Plate("plate_n", 6, 6, 0, 29))
        fun of(rank: Int) = P[(rank - 1).coerceIn(0, 3)]
    }
}

/** Unvan rozeti: ilk 3 kayan altın/gümüş/bronz parıltılı ve hale efektli, 4-10 sade. */
@Composable
fun TitleChip(title: JSONObject?, size: Float = 12f) {
    if (title == null || title.optString("name").isEmpty()) return
    val rank = title.optInt("rank")
    val col = rankColor(rank)
    // V10 isim şeritleri: 1. kanatlı altın, 2. kanatlı gümüş, 3. boynuzlu bronz, 4-10 sade çelik
    val pl = Plate.of(rank)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val img = remember(pl.name) { UiArt.image(ctx, pl.name) }
    if (img != null) {
        val dens = androidx.compose.ui.platform.LocalDensity.current
        val barH = (size * 1.9f).dp
        val k = barH / (pl.barBot - pl.barTop).toFloat()  // kaynak px → dp
        val tt = if (rank in 1..3) rememberTime() else 0f
        Box(Modifier.height(k * img.height.toFloat())
            // ilk 3: şerit hafifçe süzülür, üstünden ışık geçer (yalnız şeridin piksellerine: SrcAtop)
            .graphicsLayer {
                if (rank in 1..3) { translationY = sin(tt * 2.2f + rank) * 1.5f * density; compositingStrategy = CompositingStrategy.Offscreen }
            }
            .drawBehind {
                drawSlice3(img, 0f, 0f, this.size.width, this.size.height, pl.capL, pl.capR)
                if (rank in 1..3) {
                    val w = this.size.width; val u = ((tt * 0.35f + rank * 0.27f) % 1.4f) * (w + 160f) - 80f
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), Offset(u - 40f, 0f), Offset(u + 40f, this.size.height)),
                        blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                }
            }
            .padding(start = k * pl.capL.toFloat(), end = k * pl.capR.toFloat(), top = k * pl.barTop.toFloat(),
                bottom = k * (img.height - pl.barBot).toFloat()),
            contentAlignment = Alignment.Center) {
            val glow = 0.5f + 0.5f * sin(tt * 3f)
            Text(title.optString("name"), style = TextStyle(fontFamily = PixelFont, fontSize = size.sp,
                color = if (rank in 1..3) Color.White else Color(0xFFCFE6FF),
                shadow = Shadow(if (rank in 1..3) col.copy(alpha = 0.6f + 0.4f * glow) else Color.Black, Offset(0f, with(dens) { 1.dp.toPx() }), 6f)),
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        return
    }
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xCC15140F)).border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        if (rank in 1..3) {
            val t = rememberTime()
            val glow = 0.5f + 0.5f * sin(t * 3f)
            val u = (t * 0.5f) % 1f
            Text("✦ " + title.optString("name") + " ✦", style = TextStyle(fontFamily = PixelFont, fontSize = size.sp,
                brush = Brush.linearGradient(listOf(col, Color.White, col), start = Offset(-80f + 260f * u, 0f), end = Offset(20f + 260f * u, 24f)),
                shadow = Shadow(col.copy(alpha = glow), Offset.Zero, 8f + 8f * glow)))
        } else PT(title.optString("name"), size.sp, col)
    }
}

/** "YASAKLI" damgası (sıralamada ve profilde yasaklı oyuncular görünür kalır) */
@Composable
fun BannedStamp(size: Float = 13f, modifier: Modifier = Modifier) {
    Box(modifier.rotate(-8f).border(2.dp, Color(0xFFE0302A), RoundedCornerShape(2.dp)).background(Color(0x55300000)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        PT("Yasaklı", size.sp, Color(0xFFFF5A4A))
    }
}

// =========================================================================== sıralama penceresi
@Composable
fun BoardDialog(close: () -> Unit, preview: JSONArray? = null) {
    var tab by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<String?>(null) }
    PxDialog("Sıralama", close, width = 860.dp) {
        Row(Modifier.fillMaxWidth().height(272.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // sol: kategoriler
            Column(Modifier.width(160.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (BOARD_CATS.map { it.name to it.icon } + ("Klanlar" to "people")).forEachIndexed { i, (n, ic) ->
                    val on = i == tab
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(if (on) Color(0xFF4A3A1E) else Px.PanelDark)
                        .border(if (on) 2.dp else 0.dp, if (on) GOLD.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(3.dp))
                        .clickable { tab = i }.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val art = BOARD_CATS.getOrNull(i)?.art
                        if (art != null) ArtImage(art, Modifier.size(20.dp)) else Icon(ic, 18.dp, if (on) GOLD else Px.Sub)
                        Spacer(Modifier.width(8.dp))
                        PT(n, 13.sp, if (on) Px.Text else Px.Sub)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().pxPanel(Px.PanelDark).padding(8.dp)) {
                if (tab < BOARD_CATS.size) BoardList(BOARD_CATS[tab], preview) { view = it } else ClanRanking()
            }
        }
    }
    view?.let { PlayerProfileDialog(it) { view = null } }
}

@Composable
private fun BoardList(cat: BoardCat, preview: JSONArray?, open: (String) -> Unit) {
    var rows by remember(cat.kind) { mutableStateOf(preview) }
    var err by remember(cat.kind) { mutableStateOf("") }
    // sunucu tabloyu 15 dakikada bir hesaplar: ekran açıkken yenileme anı gelince kendiliğinden yeniden yüklenir
    var next by remember(cat.kind) { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(Pass.now()) }
    LaunchedEffect(cat.kind) {
        if (preview != null) return@LaunchedEffect
        while (true) {
            Account.board(cat.kind) { r -> r.onSuccess { rows = it; err = "" }.onFailure { if (rows == null) err = it.message ?: "Bağlantı Yok" } }
            Account.boardNext(cat.kind) { next = it }
            do { kotlinx.coroutines.delay(1000); now = Pass.now() } while (next == 0L || now < next + 2000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PT(cat.name, 18.sp, Px.Orange)
        Spacer(Modifier.width(8.dp))
        if (next > 0) PT("Yenilenme ${fmtLeft(next - now)}", 11.sp, Px.Sub)
        Spacer(Modifier.weight(1f))
        PT("İlk 10 Unvanı:", 12.sp, Px.Sub)
        Spacer(Modifier.width(6.dp))
        TitleChip(JSONObject().put("name", cat.title + " #1").put("rank", 1), 11f)
    }
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val l = rows
        when {
            l == null -> PT(err.ifEmpty { "Yükleniyor…" }, 14.sp, Px.Sub, maxLines = 2)
            l.length() == 0 -> PT("Henüz Kimse Yok. İlk Sen Ol!", 14.sp, Px.Sub)
            else -> for (i in 0 until l.length()) {
                val o = l.getJSONObject(i)
                val rk = o.optInt("rank", i + 1)
                val me = o.optLong("id") == Account.id.value
                val bg = when (rk) { 1 -> Color(0x40FFC83A); 2 -> Color(0x30DCE4EE); 3 -> Color(0x30E0904A); else -> if (i % 2 == 0) Px.Row else Color(0x22000000) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(bg)
                    .border(if (me) 2.dp else 0.dp, if (me) Px.Name else Color.Transparent, RoundedCornerShape(3.dp))
                    .clickable { open(o.optLong("id").toString()) }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RankMedal(rk, if (rk <= 3) 44.dp else 36.dp)
                    Spacer(Modifier.width(6.dp))
                    ProfileAvatar(o.optInt("avatar"), o.optString("frame"), 42.dp, photo = o.optString("photo"))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val clan = o.optString("clan").takeIf { it.isNotEmpty() && it != "null" }
                            if (clan != null) { PT("[$clan]", 13.sp, Px.Clan); Spacer(Modifier.width(4.dp)) }
                            PT(o.optString("name"), 16.sp, if (o.optBoolean("banned")) Px.Sub else if (rk <= 3) rankColor(rk) else Px.Text)
                            if (o.optBoolean("vip")) { Spacer(Modifier.width(4.dp)); TagChip(JSONObject().put("id", "vip")) }
                            if (o.optBoolean("banned")) { Spacer(Modifier.width(6.dp)); BannedStamp(11f) }
                        }
                        val tt = o.optJSONObject("title")
                        if (tt != null) { Spacer(Modifier.height(2.dp)); TitleChip(tt, 10f) }
                    }
                    PT(cat.unit(o.optLong("v")), 15.sp, if (rk <= 3) rankColor(rk) else Px.Level)
                }
            }
        }
    }
}

// =========================================================================== oyuncu profili (başkalarının gördüğü)
/**
 * Bir oyuncunun herkese açık profili: fotoğraf, unvan, seviye, öldürme, oyun süresi, istatistikler, klan.
 * Yasaklıysa damgalı görünür. Kendi profilinse "Düzenle" açılır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerProfileDialog(key: String, close: () -> Unit) {
    var p by remember(key) { mutableStateOf<JSONObject?>(null) }
    var err by remember(key) { mutableStateOf("") }
    var edit by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(key, reload) { Account.viewProfile(key) { r -> r.onSuccess { p = it }.onFailure { err = it.message ?: "Profil Bulunamadı" } } }
    val o = p
    val mine = o != null && o.optLong("id") == Account.id.value
    PlayerProfileView(o, err, mine, close, { edit = true }) {
        Account.friendRequest(it) { r -> r.onSuccess { m -> Toaster.show(m) }.onFailure { e -> Toaster.show(e.message ?: "", false) } }
    }
    var renaming by remember { mutableStateOf(false) }
    if (edit) ProfileEditor({ edit = false; reload++ }) { renaming = true }
    if (renaming) RenameDialog { renaming = false; reload++ }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerProfileView(o: JSONObject?, err: String, mine: Boolean, close: () -> Unit, edit: () -> Unit, addFriend: (String) -> Unit) {
    PxDialog(if (mine) "Profilim" else "Oyuncu Profili", close, width = 640.dp) {
        if (o == null || o.length() == 0) { PT(if (o != null) "Profil Bulunamadı" else err.ifEmpty { "Yükleniyor…" }, 15.sp, Px.Sub, maxLines = 2); return@PxDialog }
        val banned = o.optBoolean("banned")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    ProfileAvatar(o.optInt("avatar"), o.optString("frame"), 132.dp, photo = o.optString("photo"))
                    if (banned) BannedStamp(18f)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (o.optBoolean("online")) Px.Name else Color(0xFF5A5A50)))
                    Spacer(Modifier.width(5.dp))
                    PT(if (o.optBoolean("online")) "Çevrimiçi" else "Çevrimdışı", 12.sp, Px.Sub)
                }
                PT("ID: ${fmtId(o.optLong("id"))}", 12.sp, Px.Sub)
                // tuş sabit yerde (avatarın altında): unvan sayısı arttıkça aşağı kaymaz
                Spacer(Modifier.height(8.dp))
                if (mine) PxButton("Profili Düzenle", Modifier.fillMaxWidth(), icon = "profile", height = 40.dp, size = 14.sp) { edit() }
                else if (!banned) PxButton("Arkadaş Ekle", Modifier.fillMaxWidth(), icon = "people", color = Px.Blue, height = 40.dp, size = 14.sp) { addFriend(o.optString("name")) }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT(o.optString("name"), 22.sp, if (banned) Px.Sub else Px.Name)
                    if (o.optBoolean("vip")) { Spacer(Modifier.width(6.dp)); TagChip(JSONObject().put("id", "vip")) }
                }
                o.optJSONObject("title")?.let { Spacer(Modifier.height(3.dp)); TitleChip(it, 13f) }
                o.optJSONObject("clan")?.let { c ->
                    Spacer(Modifier.height(4.dp))
                    PT("${c.optString("name")} · ${c.optString("rank_name")} · ${c.optString("title")}", 13.sp, Px.Clan)
                }
                if (banned) { Spacer(Modifier.height(4.dp)); PT("Bu Oyuncu Kuralları Çiğnediği İçin Yasaklandı.", 12.sp, Color(0xFFFF6A5A), maxLines = 2) }
                Spacer(Modifier.height(8.dp))
                val st = o.optJSONObject("stats") ?: JSONObject()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatBox("star", "Seviye", "${o.optInt("level", 1)}", Px.Level)
                    StatBox("skull", "Zombi", fmtL(o.optLong("kills")), Color(0xFFFF8A7A))
                    StatBox("calendar", "Oyun Süresi", "${fmtL(o.optLong("hours"))} Saat", Px.Text)
                    StatBox("flame", "Titan", fmtL(st.optLong("titan")), BRONZE)
                    StatBox("shield", "Asker", fmtL(st.optLong("soldiers")), Px.Text)
                    StatBox("map", "Üs Fethi", fmtL(st.optLong("bases")), GOLD)
                    StatBox("paw", "Köpek Avı", fmtL(st.optLong("dog")), Px.Text)
                    StatBox("hammer", "Üretim", fmtL(st.optLong("crafted")), Px.Text)
                }
                val titles = o.optJSONArray("titles")
                if (titles != null && titles.length() > 0) {
                    Spacer(Modifier.height(8.dp))
                    PT("Kazanılan Unvanlar", 13.sp, Px.Sub)
                    Spacer(Modifier.height(3.dp))
                    // çok unvan varsa alan büyümez, kendi içinde kayar
                    FlowRow(Modifier.heightIn(max = 96.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until titles.length()) TitleChip(titles.getJSONObject(i), 10f)
                    }
                }
                TagRow(o.optJSONArray("tags"), Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Profil istatistiklerinde çizim ikonun yerine V10 piksel simge */
private val STAT_ART = mapOf("skull" to "lb_kills", "calendar" to "lb_time", "flame" to "lb_titan", "map" to "lb_bases", "paw" to "lb_dog",
    "hammer" to "lb_crafted")

@Composable
private fun StatBox(icon: String, label: String, value: String, col: Color) {
    Row(Modifier.width(130.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55000000)).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val art = STAT_ART[icon]
        if (art != null) ArtImage(art, Modifier.size(20.dp)) else Icon(icon, 18.dp, Px.Sub)
        Spacer(Modifier.width(6.dp))
        Column {
            PT(label, 10.sp, Px.Sub)
            PT(value, 14.sp, col)
        }
    }
}

/** Kural listesi (lobi bilgi penceresi) */
val RULES = listOf(
    "Hile Ve Üçüncü Taraf Yazılım" to "Hile, hız/hasar değiştirici, otomatik tıklayıcı, bot, makro, oyun belleğini ya da ağ trafiğini değiştiren her türlü araç yasaktır. Root/emülatör ortamında oyunu değiştirmek de bu kapsamdadır.",
    "Hata İstismarı" to "Bulunan bir hatayı (eşya çoğaltma, duvardan geçme, sınırsız kaynak, ödülü tekrar alma vb.) kullanmak yasaktır. Hatayı gördüğünde ayarlardan ya da yöneticiye bildir.",
    "Hesap Paylaşımı Ve Satışı" to "Hesap, eşya, altın ya da elmas gerçek para karşılığı satılamaz, alınamaz ve kiralanamaz. Hesap anahtarını kimseyle paylaşma; paylaşılan hesaptaki kayıplardan oyuncu sorumludur.",
    "Çoklu Hesap" to "Etkinlik, sıralama, takas ya da klan ödüllerinden haksız kazanç sağlamak için birden fazla hesap kullanmak yasaktır.",
    "Dolandırıcılık" to "Takasta, klan deposunda ya da sohbette başka oyuncuyu kandırmak, sahte vaatlerle eşya almak yasaktır. Takasları her zaman oyunun güvenli takas penceresinden yap.",
    "Saygı Ve Sohbet" to "Küfür, hakaret, aşağılama, ırk, din, cinsiyet ya da kimlik üzerinden ayrımcılık, tehdit ve taciz yasaktır. Başkalarının kişisel bilgilerini paylaşmak yasaktır.",
    "Reklam Ve Spam" to "Başka oyun, site, sunucu ya da hizmetin reklamını yapmak, aynı mesajı tekrar tekrar göndermek ve sohbeti doldurmak yasaktır.",
    "İsim, Klan Adı Ve Fotoğraf" to "Uygunsuz, hakaret içeren, siyasi, müstehcen ya da başkasını taklit eden oyuncu adı, klan adı ve profil fotoğrafı kullanılamaz. Yöneticiler bunları değiştirebilir ya da kaldırabilir.",
    "Yönetici Taklidi" to "Yönetici, moderatör ya da Zenvex ekibinden biri gibi davranmak yasaktır. Yöneticiler hiçbir zaman hesap anahtarını istemez.",
    "PvP Alanları" to "Üs çevresi ve yüksek seviyeli bölgelerde PvP serbesttir ve oyunun parçasıdır. Ancak güvenli bölgelerde bekleyen oyuncuyu sürekli taciz etmek, yeni oyuncuları kasıtlı olarak tekrar tekrar öldürmek için takip etmek yasaktır.",
    "Klan Savaşları" to "Üs ele geçirme ve savunma serbesttir. Rakip klanla anlaşıp sıralama puanı, etkinlik puanı ya da ödül paylaşmak (puan ayarlama) yasaktır.",
    "Etkinlikler Ve Sıralama" to "Etkinlik puanını ya da sıralamayı hile, hata, çoklu hesap veya puan devriyle yükseltmek yasaktır. Haksız kazanılan ödüller ve unvanlar geri alınır.",
    "Ödeme Ve Elmas" to "Elmas ve ödüller yalnız oyun içinden kazanılır ya da resmi yoldan alınır. Ödeme iadesi istismarı ve izinsiz ödeme yasaktır.",
    "Bildirim" to "Kural ihlali gördüğünde oyuncunun adını, zamanını ve ne olduğunu yöneticiye bildir. Sahte şikayet de kural ihlalidir.",
    "Yönetici Kararları" to "Yöneticiler kurallara aykırı davranışlarda uyarı, susturma, eşya geri alma, sıralamadan çıkarma, geçici ya da kalıcı yasak uygulayabilir. Yasaklı oyuncular sıralamada yasaklı olarak görünür.",
    "Kuralların Güncellenmesi" to "Kurallar oyunun gelişimine göre güncellenebilir. Oyunu oynayarak güncel kuralları kabul etmiş olursun.",
)
