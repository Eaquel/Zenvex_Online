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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.Events
import com.zenvex.online.game.Pass
import org.json.JSONObject
import kotlin.math.sin

/** Etkinlik türü: tema rengi, simge, kısa hikâye, puan kuralı, ödül eşyası öneki (data.rs ev_<önek>_ring/neck/helm) */
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

/** HUD üst ortadaki etkinlik afişi (etkinlik sürüyorsa): dokununca etkinlik penceresi */
@Composable
fun EventBanner(modifier: Modifier, onClick: () -> Unit) {
    val e = Events.current
    if (!e.optBoolean("active")) return
    val info = EVENT_INFO[e.optString("kind")] ?: return
    val t = rememberTime()
    var now by remember { mutableLongStateOf(Pass.now()) }
    LaunchedEffect(Unit) { while (true) { now = Pass.now(); kotlinx.coroutines.delay(1000) } }
    val k = 0.5f + 0.5f * sin(t * 3f)
    // V10 afiş görseli zeminde (koyulaştırılmış), üstünde etkinlik rengi geçişi
    Row(modifier.clip(RoundedCornerShape(3.dp)).artCover(info.art, 0.35f, 0.4f)
        .background(Brush.horizontalGradient(listOf(info.color.copy(alpha = 0.35f + 0.2f * k), Color(0x8015140F))))
        .border(2.dp, info.color.copy(alpha = 0.6f + 0.4f * k), RoundedCornerShape(3.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(info.icon, 16.dp, Color.White)
        Spacer(Modifier.width(6.dp))
        PT(e.optString("title"), 13.sp, Color.White)
        Spacer(Modifier.width(8.dp))
        PT(fmtLeft(e.optLong("ends") - now), 12.sp, Px.Clan)
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
                RewardLine(3, "ev_${info.prefix}_helm", "$title Muhafız Kaskı", "+500 Elmas · 30 Gün")
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
