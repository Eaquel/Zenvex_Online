package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.Events
import com.zenvex.online.game.Pass
import org.json.JSONObject
import kotlin.math.sin

/** Etkinlik türü: tema rengi, simge, kısa hikâye, puan kuralı, ödül eşyası öneki (data.rs ev_<önek>_ring/neck/weapon) */
data class EventInfo(val color: Color, val icon: String, val story: String, val rule: String, val prefix: String, val art: String = "")

val EVENT_INFO = mapOf(
    "blood_moon" to EventInfo(Color(0xFFE0302A), "skull", "Kızıl ay doğdu, zombiler kudurdu. Ne kadar çok ve ne kadar güçlü zombi devirirsen o kadar yükselirsin.",
        "Aylak 1 · Koşucu 2 · Köpek 2 · Zehirli 3 · Karanlık 4 · Tank 8 · Gizemli 10 · Titan 50", "blood", "ev_blood"),
    "titan_hunt" to EventInfo(Color(0xFFE0904A), "flame", "Titanlar uyandı. Dev avcılarının en iyisi kim?", "Her Titan 1.000 Puan", "titan", "ev_titan"),
    "base_storm" to EventInfo(Color(0xFF6AB8FF), "shield", "Devlet üslerine fırtına gibi gir. Askerleri düşür, üsleri ele geçir.", "Asker 10 · Üs Ele Geçirme 500", "base", "ev_base"),
    "scavengers" to EventInfo(Color(0xFF9AD84A), "hammer", "Şehir yağmalanmayı bekliyor. Topla, aç, üret.", "Ağaç 2 · Sandık 5 · Üretim 10", "scrap", "ev_scrap"),
    "pack_leader" to EventInfo(Color(0xFFC07AFF), "paw", "Köpeğinle birlikte avlan. Sürünün lideri ol.", "Köpeğinin Her Avı 5 Puan", "pack", "ev_pack"),
    "last_breath" to EventInfo(Color(0xFF7FE0FF), "heart", "Güvenli bölgenin dışında ne kadar dayanabilirsin?", "Güvenli Bölge Dışında Her Saniye 1 Puan", "breath", "ev_breath"),
)

/** 3. sıra ödülü etkinlik silahları (data.rs ev_<önek>_weapon) */
val EVENT_WEAPON = mapOf("blood" to "Kan Ayı Orağı", "titan" to "Titan Kırıcı Balyoz", "base" to "Fırtına Baltası",
    "scrap" to "Hurdacı Testere Kılıcı", "pack" to "Sürü Lideri Pençesi", "breath" to "Son Nefes Tırpanı")

/**
 * HUD üst ortadaki etkinlik afişi (etkinlik sürüyorsa): dokununca etkinlik penceresi.
 * Canlı: afiş görseli yavaşça kayar, üstünden ışık geçer, alttan etkinlik renginde kıvılcımlar yükselir, simge zıplar;
 * son saatte süre kırmızı yanıp söner. Açılışta yukarıdan kayarak gelir.
 */
@Composable
fun EventBanner(modifier: Modifier, onClick: () -> Unit) {
    val e = Events.current
    if (!e.optBoolean("active")) return
    val info = EVENT_INFO[e.optString("kind")] ?: return
    val t = rememberTime()
    var now by remember { mutableLongStateOf(Pass.now()) }
    LaunchedEffect(Unit) { while (true) { now = Pass.now(); kotlinx.coroutines.delay(1000) } }
    val enter by androidx.compose.animation.core.animateFloatAsState(1f, androidx.compose.animation.core.tween(700), label = "evIn")
    val k = 0.5f + 0.5f * sin(t * 3f)
    val left = e.optLong("ends") - now
    val urgent = left in 1..3_600_000
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val frame = remember { UiArt.image(ctx, "event_frame") }
    val glow = remember { UiArt.image(ctx, "event_frame_glow") }
    Box(modifier.graphicsLayer { translationY = (1f - enter) * -60f * density; alpha = enter }
        .clip(RoundedCornerShape(3.dp)).clickable { onClick() }) {
        // zemin: afiş görseli (yavaş kayan), etkinlik rengi geçişi, geçen ışık, kıvılcımlar
        Box(Modifier.matchParentSize().artCover(info.art, 0.4f + 0.2f * sin(t * 0.25f), 0.42f))
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.horizontalGradient(listOf(info.color.copy(alpha = 0.45f + 0.2f * k), Color(0x9015140F), info.color.copy(alpha = 0.3f))))
            val u = ((t * 0.3f) % 1.6f) * (w + 120f) - 60f
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent), Offset(u - 50f, 0f), Offset(u + 10f, h)))
            for (i in 0 until 14) {
                val x = ((i * 37 + 11) % 100) / 100f * w
                val p = (t * (0.5f + (i % 4) * 0.15f) + i * 0.27f) % 1f
                val sz = 2.5f + (i % 3)
                drawRect(info.color.copy(alpha = (1f - p) * 0.9f), Offset(x + sin(t * 2f + i) * 4f, h * (1f - p)), Size(sz, sz))
            }
            if (frame != null) {
                // V13 kurukafa köşeli demir çerçeve + etkinlik rengine boyanmış nabız gibi parlayan dış hat
                val dst = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())
                if (glow != null) drawImage(glow, dstSize = dst, alpha = 0.35f + 0.55f * k,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(info.color, androidx.compose.ui.graphics.BlendMode.Modulate),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                drawImage(frame, dstSize = dst, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
            } else {
                val bw = 2.dp.toPx()
                drawRect(info.color.copy(alpha = 0.55f + 0.45f * k), style = androidx.compose.ui.graphics.drawscope.Stroke(bw))
                // köşe perçinleri
                for ((cx, cy) in listOf(4f to 4f, w - 4f to 4f, 4f to h - 4f, w - 4f to h - 4f)) drawRect(Color(0xFFE8D8A0), Offset(cx - 2f, cy - 2f), Size(4f, 4f))
            }
        }
        Row(Modifier.padding(horizontal = if (frame != null) 22.dp else 12.dp, vertical = if (frame != null) 9.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.graphicsLayer { translationY = -kotlin.math.abs(sin(t * 4f)) * 3f * density; rotationZ = sin(t * 2f) * 6f }) {
                Icon(info.icon, 18.dp, Color.White)
            }
            Spacer(Modifier.width(6.dp))
            Column {
                PT(e.optString("title"), 14.sp, Color.White, outline = true)
                PT("Etkinlik · Dokun", 9.sp, Color(0xCCFFFFFF))
            }
            Spacer(Modifier.width(10.dp))
            val blink = if (urgent) (sin(t * 8f) > 0f) else true
            Row(Modifier.clip(RoundedCornerShape(2.dp)).background(Color(0xA0000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon("clock", 12.dp, if (urgent) Px.Hard else Px.Clan)
                Spacer(Modifier.width(4.dp))
                PT(fmtLeft(left), 12.sp, if (urgent && blink) Color(0xFFFF5040) else Px.Clan)
            }
        }
    }
}

@Composable
fun EventDialog(close: () -> Unit) {
    var b by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Account.eventBoard { r -> r.onSuccess { b = it }.onFailure { err = it.message ?: "Bağlantı Yok" } } }
    EventView(b, err, Pass.now(), close)
}

@Composable
internal fun EventView(b: JSONObject?, err: String, now: Long, close: () -> Unit) {
    val t = rememberTime()
    Box(Modifier.fillMaxSize().background(Color(0xF00A0806)).clickable(remember { MutableInteractionSource() }, null) {}) {
        if (b == null || !b.optBoolean("active")) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                CloseButton(40.dp) { close() }
                Spacer(Modifier.height(30.dp))
                PT(if (b == null) err.ifEmpty { "Yükleniyor…" } else "Şu An Etkinlik Yok. Yeni Etkinlik Başlayınca Bildirim Alırsın.", 16.sp, Px.Sub, maxLines = 2)
            }
            return@Box
        }
        val info = EVENT_INFO[b.optString("kind")] ?: EVENT_INFO.values.first()
        // tema: V10 etkinlik afişi tam ekran (karartılmış), etkinlik renginde geçiş + yükselen kıvılcımlar
        SceneBackground(info.art, t, dim = 0.62f)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(info.color.copy(alpha = 0.25f), Color(0xC00A0806)), 0f, size.height * 0.8f))
            for (i in 0 until 36) {
                val x = ((i * 71) % 100) / 100f * size.width
                val y = size.height * (1f - ((t * (0.04f + (i % 5) * 0.01f) + i * 0.19f) % 1f))
                drawRect(info.color.copy(alpha = 0.35f + 0.3f * sin(t * 2f + i)), Offset(x, y), Size(4f, 4f))
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // sol: başlık, süre, puanım, kurallar, ödüller
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloseButton(40.dp) { close() }
                    Spacer(Modifier.width(10.dp))
                    Icon(info.icon, 30.dp, info.color)
                    Spacer(Modifier.width(8.dp))
                    PT(b.optString("title"), 26.sp, info.color, outline = true)
                }
                PT("Bitimine: ${fmtLeft(b.optLong("ends") - now)}", 14.sp, Px.Clan)
                Spacer(Modifier.height(4.dp))
                // etkinlik afişi (V10 çizim)
                Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(3.dp)).artCover(info.art)
                    .border(2.dp, info.color.copy(alpha = 0.7f), RoundedCornerShape(3.dp)))
                Spacer(Modifier.height(4.dp))
                PT(info.story, 12.sp, Px.Text, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color(0xCC15140F)).padding(8.dp)) {
                    Column {
                        PT("Puan Kuralı", 12.sp, Px.Sub)
                        PT(info.rule, 13.sp, Color.White, maxLines = 3)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(info.color.copy(alpha = 0.4f), Color(0xCC15140F))))
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val rk = if (b.isNull("my_rank")) 0 else b.optInt("my_rank")
                    if (rk in 1..3) RankMedal(rk, 40.dp) else Icon("star", 26.dp, Px.Clan)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        PT("Puanın: ${fmtL(b.optLong("my_score"))}", 16.sp, Color.White)
                        PT(if (rk > 0) "Sıran: #$rk" else "Henüz Puanın Yok", 12.sp, Px.Clan)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PT("Ödüller", 15.sp, Px.Orange)
                val title = b.optString("title")
                RewardLine(1, "ev_${info.prefix}_ring", "$title Kralı Yüzüğü", "+1.000 Elmas · 30 Gün")
                RewardLine(2, "ev_${info.prefix}_neck", "$title Şampiyonu Kolyesi", "+700 Elmas · 30 Gün")
                RewardLine(3, "ev_${info.prefix}_weapon", EVENT_WEAPON[info.prefix] ?: "$title Silahı", "+500 Elmas · 30 Gün")
                PT("İlk 3 Ayrıca 30 Gün Takabileceği Etkinlik Unvanı Alır ($title Kralı / Şampiyonu / Savaşçısı).", 11.sp, Px.Sub, maxLines = 2)
                RewardLine(10, "titan_shard", "İlk 10", "300 Elmas · Titan Parçası ×5 · Güç İksiri ×5 · İlk Yardım ×10")
                RewardLine(100, "medkit", "İlk 100", "100 Elmas · İlk Yardım ×5")
                PT("Ödüller etkinlik bitince postana gelir. Hile ile kazanılan puanlar silinir.", 10.sp, Px.Sub, maxLines = 2)
            }
            // sağ: ilk 100
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color(0xCC15140F)).padding(8.dp)) {
                PT("İlk 100", 16.sp, Px.Orange)
                Spacer(Modifier.height(4.dp))
                val top = b.optJSONArray("top").objs()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (top.isEmpty()) PT("Henüz Puan Alan Yok. İlk Sen Ol!", 13.sp, Px.Sub)
                    top.forEachIndexed { i, o ->
                        val rk = i + 1
                        val me = o.optLong("id") == Account.id.value
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
                            .background(when { me -> Color(0x5545E04F); rk <= 3 -> rankColor(rk).copy(alpha = 0.2f); i % 2 == 0 -> Px.Row; else -> Color.Transparent })
                            .padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            RankMedal(rk, if (rk <= 3) 34.dp else 28.dp)
                            Spacer(Modifier.width(6.dp))
                            PT(o.optString("name"), 14.sp, if (rk <= 3) rankColor(rk) else Px.Text, Modifier.weight(1f))
                            PT(fmtL(o.optLong("score")), 14.sp, Px.Clan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardLine(rank: Int, item: String, name: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rank <= 3) RankMedal(rank, 34.dp) else Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { PT(if (rank == 10) "4-10" else "11-100", 10.sp, Px.Sub) }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x66000000))) { ItemArt(item, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(6.dp))
        Column {
            PT(name, 13.sp, if (rank <= 3) rankColor(rank) else Px.Text)
            PT(sub, 10.sp, Px.Sub, maxLines = 2)
        }
    }
}
