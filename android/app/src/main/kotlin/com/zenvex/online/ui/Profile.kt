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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zenvex.online.game.Account
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =========================================================================== profil fotoğrafları
/** Profil fotoğrafı: atlas görseli + kadraj (üstten alınan yükseklik oranı; insanlarda baş-omuz). */
data class AvatarDef(val key: String, val name: String, val crop: Float)

val AVATARS = listOf(
    AvatarDef("l/kaan/0_0", "Savaşçı", 0.42f),
    AvatarDef("npc/merchant", "Tüccar", 0.4f), AvatarDef("npc/quest", "Görevci", 0.4f), AvatarDef("npc/doctor", "Doktor", 0.4f),
    AvatarDef("npc/mechanic", "Tamirci", 0.4f), AvatarDef("npc/trainer", "Eğitmen", 0.4f), AvatarDef("npc/soldier", "Muhafız", 0.4f),
    AvatarDef("p/dog0/idle_0_0", "Yavru Köpek", 1f), AvatarDef("p/dog5/idle_0_0", "Kurt Köpeği", 1f), AvatarDef("p/dog10/idle_0_0", "Alfa", 1f),
    AvatarDef("z/walker/idle_0_0", "Aylak", 0.5f), AvatarDef("z/runner/idle_0_0", "Koşucu", 0.5f), AvatarDef("z/toxic/idle_0_0", "Zehirli", 0.5f),
    AvatarDef("z/dark/idle_0_0", "Karanlık", 0.5f), AvatarDef("z/dog/idle_0_0", "Kuduz", 1f), AvatarDef("z/titan/idle_0_0", "Titan", 0.45f),
)

// =========================================================================== cihazdan fotoğraf
/** Sunucudaki base64 WebP profil fotoğraflarının çözülmüş hali (aynı fotoğraf her satırda yeniden çözülmesin). */
object PhotoCache {
    private val cache = android.util.LruCache<Int, ImageBitmap>(64)
    fun get(b64: String): ImageBitmap? {
        if (b64.isEmpty() || b64 == "null") return null
        cache.get(b64.hashCode())?.let { return it }
        return runCatching {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.also { cache.put(b64.hashCode(), it) }
    }

    /** Seçilen görseli ortadan kare kırpar, 96×96'ya küçültür, WebP olarak sıkıştırır (sunucu sınırı 20.000 karakter). */
    fun encode(ctx: android.content.Context, uri: android.net.Uri): String? = runCatching {
        val cr = ctx.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 192) sample *= 2
        val src = cr.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val side = minOf(src.width, src.height)
        val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = android.graphics.Bitmap.createScaledBitmap(sq, 96, 96, true)
        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                  else @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
        var q = 85
        var b64: String
        do {
            val bo = java.io.ByteArrayOutputStream()
            out.compress(fmt, q, bo)
            b64 = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)
            q -= 15
        } while (b64.length >= 19000 && q >= 30)
        b64
    }.getOrNull()
}

// =========================================================================== çerçeveler
/** Çerçeve: kimlik, ad, açılma koşulu metni. Koşul profil verisinden hesaplanır (VIP ve klan çerçeveleri sunucuda da denetlenir). */
data class FrameDef(val id: String, val name: String, val need: String)

val FRAMES = listOf(
    FrameDef("", "Demir", "Herkes"),
    FrameDef("bronze", "Bronz", "Seviye 10"),
    FrameDef("silver", "Gümüş", "Seviye 25"),
    FrameDef("gold", "Altın", "Seviye 40"),
    FrameDef("vip", "VIP Işıltısı", "VIP Üyelik"),
    FrameDef("clan1", "Ejder Alevi", "Genel Klan Sıralaması #1"),
    FrameDef("clan2", "Fırtına", "Genel Klan Sıralaması #2"),
    FrameDef("clan3", "Buz Kristali", "Genel Klan Sıralaması #3"),
)

fun frameUnlocked(id: String, p: JSONObject): Boolean = when (id) {
    "" -> true
    "bronze" -> p.optInt("level") >= 10
    "silver" -> p.optInt("level") >= 25
    "gold" -> p.optInt("level") >= 40
    "vip" -> p.optBoolean("vip")
    else -> id.startsWith("clan") && p.optJSONObject("clan")?.optInt("top", 0) == id.removePrefix("clan").toIntOrNull()
}

/**
 * Profil fotoğrafı + efektli çerçeve. Çerçeve çizimi tamamen kodla: piksel halka, dönen parıltı, kıvılcım/alev/şimşek/buz efektleri.
 */
@Composable
fun ProfileAvatar(avatar: Int, frame: String, size: Dp, modifier: Modifier = Modifier, photo: String = "") {
    val ctx = LocalContext.current
    val def = AVATARS.getOrElse(avatar) { AVATARS[0] }
    val img = remember(def.key) { SpriteCache.get(ctx, def.key) }
    val pic = remember(photo) { PhotoCache.get(photo) }
    val t = rememberTime()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val inner = size * 0.8f
        Box(Modifier.size(inner).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3A4A5A), Color(0xFF151A22))))) {
            if (pic != null) Canvas(Modifier.fillMaxSize()) {
                drawImage(pic, IntOffset.Zero, IntSize(pic.width, pic.height), IntOffset.Zero,
                    IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.Low)
            }
            else if (img != null) Canvas(Modifier.fillMaxSize()) {
                val srcH = (img.height * def.crop).toInt().coerceAtLeast(1)
                val k = minOf(this.size.width / img.width, this.size.height / srcH) * (if (def.crop < 1f) 1.35f else 0.95f)
                val dw = img.width * k; val dh = srcH * k
                drawImage(img, IntOffset.Zero, IntSize(img.width, srcH),
                    IntOffset(((this.size.width - dw) / 2).toInt(), if (def.crop < 1f) 0 else ((this.size.height - dh) / 2).toInt()),
                    IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.None)
            }
        }
        Canvas(Modifier.size(size)) { drawFrame(frame, t) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(frame: String, t: Float) {
    val w = size.width; val s = w * 0.1f
    val base = when (frame) {
        "bronze" -> Color(0xFFB87333); "silver" -> Color(0xFFD8DEE6); "gold", "vip" -> Color(0xFFFFC83A)
        "clan1" -> Color(0xFFFF5A1F); "clan2" -> Color(0xFF9A7BFF); "clan3" -> Color(0xFF7FE0FF); else -> Color(0xFF6A6A70)
    }
    val o = s * 0.5f
    // piksel çerçeve: koyu dış hat + renkli bant + açık iç çizgi
    drawRect(Color(0xFF15140F), Offset(0f, 0f), Size(w, w), style = Stroke(s * 0.35f))
    drawRect(base, Offset(o * 0.4f, o * 0.4f), Size(w - o * 0.8f, w - o * 0.8f), style = Stroke(s * 0.7f))
    drawRect(Color.White.copy(alpha = 0.35f), Offset(s * 0.85f, s * 0.85f), Size(w - s * 1.7f, w - s * 1.7f), style = Stroke(s * 0.12f))
    // köşe perçinleri
    for ((x, y) in listOf(o to o, w - o to o, o to w - o, w - o to w - o)) drawRect(base.copy(alpha = 1f), Offset(x - s * 0.35f, y - s * 0.35f), Size(s * 0.7f, s * 0.7f))
    // çevre üzerinde dolaşan nokta (0..1 → çerçevede konum)
    fun edge(u: Float): Offset {
        val p = ((u % 1f) + 1f) % 1f * 4f; val e = w - o * 2
        return when (p.toInt()) { 0 -> Offset(o + e * p, o); 1 -> Offset(w - o, o + e * (p - 1)); 2 -> Offset(w - o - e * (p - 2), w - o); else -> Offset(o, w - o - e * (p - 3)) }
    }
    when (frame) {
        "gold", "vip" -> {
            // dönen parıltı
            for (k in 0..2) {
                val c = edge(t * 0.25f + k / 3f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), c, s * 1.4f), s * 1.4f, c)
            }
            if (frame == "vip") for (k in 0 until 8) {
                // yükselen altın kıvılcımlar
                val ph = (t * 0.6f + k * 0.37f) % 1f
                val x = w * (0.1f + 0.8f * ((k * 0.618f) % 1f)); val y = w * (1f - ph)
                drawRect(Color(0xFFFFE27A).copy(alpha = (1 - ph) * 0.9f), Offset(x, y), Size(s * 0.35f, s * 0.35f))
            }
        }
        "clan1" -> for (k in 0 until 14) {
            // ejder alevi: çerçeve boyunca titreyen alev dilleri
            val c = edge(k / 14f + t * 0.05f)
            val fl = 0.6f + 0.4f * sin(t * 9f + k * 1.7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE070), Color(0xFFFF5A1F).copy(alpha = 0.7f), Color.Transparent), c.copy(y = c.y - s * 0.4f * fl), s * 1.1f * fl),
                s * 1.1f * fl, c.copy(y = c.y - s * 0.4f * fl))
        }
        "clan2" -> {
            // fırtına: çerçeve boyunca zikzak şimşek
            val k0 = ((t * 3f).toInt() % 4) / 4f
            var prev = edge(k0)
            for (i in 1..6) {
                val n = edge(k0 + i * 0.04f)
                val j = Offset(n.x + (sin(t * 40f + i) * s * 0.6f), n.y + (cos(t * 37f + i) * s * 0.6f))
                drawLine(Color(0xFFE0D8FF), prev, j, s * 0.25f); prev = j
            }
            drawRect(Color(0x339A7BFF), Offset.Zero, Size(w, w), style = Stroke(s * (0.8f + 0.4f * sin(t * 6f))))
        }
        "clan3" -> for (k in 0 until 10) {
            // buz kristalleri: köşelerde parlayan elmas biçimleri
            val c = edge(k / 10f)
            val a = 0.5f + 0.5f * sin(t * 2.5f + k)
            val r = s * (0.5f + 0.3f * a)
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(c.x, c.y - r * 1.6f); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r * 1.6f); lineTo(c.x - r, c.y); close() }
            drawPath(path, Color(0xFFDFF8FF).copy(alpha = 0.6f + 0.4f * a))
        }
    }
}

// =========================================================================== etiketler
/** Etiket rozeti: VIP parlayan altın yazı, sıralama rozetleri. */
@Composable
fun TagChip(tag: JSONObject) {
    val id = tag.optString("id")
    val t = rememberTime()
    val (bg, fg) = when {
        id == "vip" -> Color(0xFF2A1E06) to Color(0xFFFFD34A)
        id == "lvl1" -> Color(0xFF3A2A06) to Color(0xFFFFE27A)
        id == "lvl2" -> Color(0xFF2A2E34) to Color(0xFFE6EEF6)
        id == "lvl3" -> Color(0xFF3A2414) to Color(0xFFE8A060)
        id.startsWith("kill") -> Color(0xFF3A0E0E) to Color(0xFFFF6A5A)
        else -> Color(0xFF14243A) to Color(0xFF8CC8FF)
    }
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(bg).border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        if (id == "vip") {
            // parlayan altın VIP: dalgalanan ışıltı + yumuşak hale
            val glow = 0.5f + 0.5f * sin(t * 3f)
            Text("VIP", style = TextStyle(fontFamily = PixelFont, fontSize = 14.sp,
                brush = Brush.linearGradient(listOf(Color(0xFFB8860B), Color(0xFFFFF3B0), Color(0xFFFFC83A)),
                    start = Offset(-60f + 160f * ((t * 0.5f) % 1f), 0f), end = Offset(40f + 160f * ((t * 0.5f) % 1f), 20f)),
                shadow = Shadow(Color(0xFFFFC83A).copy(alpha = glow), Offset.Zero, 10f + 8f * glow)))
        } else PT(tag.optString("name"), 11.sp, fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: JSONArray?, modifier: Modifier = Modifier) {
    if (tags == null || tags.length() == 0) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until tags.length()) TagChip(tags.getJSONObject(i))
    }
}

// =========================================================================== lobi: sol üst kimlik kartı
@Composable
fun ProfileBadge(p: JSONObject, guest: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.pxPanel(Color(0xE01E1D19)).clickable { onClick() }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(p.optInt("avatar"), p.optString("frame"), 58.dp, photo = p.optString("photo"))
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PT(p.optString("name").ifEmpty { "Misafir" }, 17.sp, Px.Name)
                if (p.optBoolean("vip")) { Spacer(Modifier.width(6.dp)); TagChip(JSONObject().put("id", "vip")) }
            }
            if (guest) PT("Hesap Aç · İlerlemen Kaybolmasın", 11.sp, Px.Clan)
            else PT("ID: ${fmtId(p.optLong("id"))}  ·  Sv ${p.optInt("level", 1)}", 12.sp, Px.Sub)
            val tags = p.optJSONArray("tags")
            if (tags != null && tags.length() > 0) {
                Spacer(Modifier.height(3.dp))
                val first = (0 until tags.length()).map { tags.getJSONObject(it) }.firstOrNull { it.optString("id") != "vip" }
                if (first != null) TagChip(first)
            }
        }
    }
}

// =========================================================================== profil düzenleyici
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditor(close: () -> Unit, rename: () -> Unit) {
    val ctx = LocalContext.current
    val p = Account.profile.value
    var busy by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            val b64 = PhotoCache.encode(ctx, uri)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (b64 == null) { busy = false; Toaster.show("Fotoğraf Okunamadı", false) }
                else Account.setPhoto(b64) { e -> busy = false; if (e != null) Toaster.show(e, false) else Toaster.show("Profil Fotoğrafı Yüklendi") }
            }
        }.start()
    }
    ProfileEditorView(p, close, rename,
        pickPhoto = {
            if (!busy) pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        clearPhoto = { Account.setPhoto("") { e -> if (e != null) Toaster.show(e, false) else Toaster.show("Profil Fotoğrafı Kaldırıldı") } },
        setTitle = { k -> Account.setTitle(k) { e -> if (e != null) Toaster.show(e, false) else Toaster.show(if (k.isEmpty()) "Unvan Kaldırıldı" else "Unvan Seçildi") } },
    ) { avatar, frame ->
        Account.setProfile(avatar, frame) { e -> if (e != null) Toaster.show(e, false) else { Toaster.show("Profil Kaydedildi"); close() } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileEditorView(
    p: JSONObject, close: () -> Unit, rename: () -> Unit,
    pickPhoto: () -> Unit = {}, clearPhoto: () -> Unit = {}, setTitle: (String) -> Unit = {},
    save: (Int, String) -> Unit,
) {
    var avatar by remember { mutableIntStateOf(p.optInt("avatar")) }
    var frame by remember { mutableStateOf(p.optString("frame")) }
    val photo = p.optString("photo").takeIf { it != "null" } ?: ""
    val title = p.optJSONObject("title")
    PxDialog("Profil", close, width = 760.dp) {
        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // önizleme
            Column(Modifier.width(220.dp).fillMaxHeight().pxPanel(Px.PanelDark).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileAvatar(avatar, frame, 96.dp, photo = photo)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT(p.optString("name"), 18.sp, Px.Name)
                    Spacer(Modifier.width(4.dp))
                    PxIcon("craft", size = 26.dp) { rename() }
                }
                PT("ID: ${fmtId(p.optLong("id"))}", 12.sp, Px.Sub)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PT("Sv ${p.optInt("level", 1)}", 14.sp, Px.Level)
                    PT("${fmt(p.optInt("kills"))} Öldürme", 14.sp, Color(0xFFFF8A7A))
                }
                p.optJSONObject("clan")?.let { c -> PT("${c.optString("name")} · ${c.optString("rank_name")}", 12.sp, Px.Clan) }
                if (title != null) { Spacer(Modifier.height(4.dp)); TitleChip(title, 11f) }
                PT("${fmt(p.optInt("hours"))} Saat Oynadın", 12.sp, Px.Sub)
                Spacer(Modifier.height(6.dp))
                TagRow(p.optJSONArray("tags"))
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                PT("Cihazdan Fotoğraf", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PxButton(if (photo.isEmpty()) "Fotoğraf Seç" else "Değiştir", icon = "profile", color = Px.Blue, height = 38.dp, size = 13.sp) { pickPhoto() }
                    if (photo.isNotEmpty()) PxButton("Kaldır", icon = "trash", color = Px.Red, height = 38.dp, size = 13.sp) { clearPhoto() }
                }
                PT("Uygunsuz Fotoğraflar Yöneticiler Tarafından Kaldırılır.", 10.sp, Px.Sub)
                Spacer(Modifier.height(10.dp))
                PT("Unvan", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                val titles = p.optJSONArray("titles") ?: JSONArray()
                if (titles.length() == 0) PT("Bir Kategoride İlk 10'a Girince Unvan Kazanırsın (Sıralama).", 12.sp, Px.Sub, maxLines = 2)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (title == null) Color(0x553A6AB0) else Color(0x33000000))
                        .clickable { setTitle("") }.padding(horizontal = 8.dp, vertical = 5.dp)) { PT("Unvansız", 12.sp, Px.Sub) }
                    for (i in 0 until titles.length()) {
                        val t = titles.getJSONObject(i)
                        val on = title?.optString("kind") == t.optString("kind")
                        Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) Color(0x553A6AB0) else Color(0x33000000))
                            .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { setTitle(t.optString("kind")) }.padding(4.dp)) { TitleChip(t, 11f) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PT(if (photo.isEmpty()) "Karakter Resmi" else "Karakter Resmi (Fotoğraf Kaldırılınca Görünür)", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AVATARS.forEachIndexed { i, a ->
                        Box(Modifier.border(2.dp, if (i == avatar) Color.White else Color.Transparent).clickable { avatar = i }) {
                            ProfileAvatar(i, "", 56.dp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PT("Çerçeve", 15.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in FRAMES) {
                        val ok = frameUnlocked(f.id, p)
                        Column(Modifier.width(92.dp).clip(RoundedCornerShape(3.dp)).background(if (f.id == frame) Color(0x553A6AB0) else Color(0x33000000))
                            .clickable { if (ok) frame = f.id else Toaster.show("${f.name}: ${f.need}", false) }.padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                ProfileAvatar(avatar, f.id, 54.dp, photo = photo)
                                if (!ok) Box(Modifier.size(54.dp).background(Color(0xA0000000)), contentAlignment = Alignment.Center) { Icon("lock", 20.dp, Px.Sub) }
                            }
                            PT(f.name, 11.sp, if (ok) Px.Text else Px.Sub)
                            PT(f.need, 9.sp, Px.Sub, maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PxButton("Vazgeç", color = Px.Dark, height = 42.dp, size = 15.sp) { close() }
            Spacer(Modifier.width(8.dp))
            PxButton("Kaydet", height = 42.dp, size = 15.sp) { save(avatar, frame) }
        }
    }
}
