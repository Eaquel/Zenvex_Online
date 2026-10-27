package com.zenvex.online.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Session
import com.zenvex.online.net.Supa
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Nadirlik rengi ve adı (karakter kartları) */
val RARITY_COL = listOf(Color(0xFFB8C0C8), Color(0xFF5AA8FF), Color(0xFFC07AFF), Color(0xFFFFB43A))
val RARITY_TR = listOf("Sıradan", "Nadir", "Destansı", "Efsanevi")

/**
 * Kart açma: önce son durum sunucuya kaydedilir, zarı sunucu atar (zx_char_pull), sonuç oyuna uygulanır ve kaydedilir.
 * [done] çıkan karakterlerin sırası ve elmas karşılığıyla çağrılır.
 */
object CharPull {
    @Volatile var busy = false
    fun pull(count: Int, done: (results: List<Int>, gems: Int) -> Unit) {
        if (busy) return
        if (!Account.loggedIn || Session.sid.isEmpty()) { Toaster.show("Kart açmak için çevrimiçi olmalısın", false); return }
        busy = true
        GameSession.save(cloud = true) { ok ->
            if (!ok) { busy = false; Toaster.show("Sunucuya ulaşılamadı", false); return@save }
            Supa.rpc("zx_char_pull", Account.authJson().put("p_sid", Session.sid).put("p_count", count)) { r ->
                r.onSuccess { s ->
                    val o = JSONObject(s)
                    val res = o.optJSONArray("results") ?: JSONArray()
                    GameSession.cmdNoSave("char_apply", "results" to res, "gems" to o.optInt("gems"), "cost" to o.optInt("cost"))
                    GameSession.save(cloud = true)
                    done(List(res.length()) { res.getInt(it) }, o.optInt("gems"))
                }.onFailure { Toaster.show(it.message ?: "Kart açılamadı", false) }
                busy = false
            }
        }
    }
}

@Composable
fun CharactersScreen(close: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val data = remember(tick) { GameSession.ui("characters") }
    var reveal by remember { mutableStateOf<Pair<List<Int>, Int>?>(null) }
    CharactersView(data, { tick++ }, close,
        pull = { n -> CharPull.pull(n) { r, g -> reveal = r to g; tick++ } })
    reveal?.let { (r, g) -> CardReveal(r, g, data) { reveal = null } }
}

@Composable
internal fun CharactersView(data: JSONObject, changed: () -> Unit, close: () -> Unit, pull: (Int) -> Unit) {
    val chars = data.optJSONArray("chars").objs()
    var sel by remember { mutableIntStateOf(chars.indexOfFirst { it.optBoolean("selected") }.coerceAtLeast(0)) }
    var odds by remember { mutableStateOf(false) }
    val t = rememberTime()
    ScreenFrame("KARAKTERLER", "profile", close, right = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon("coin", 20.dp, Px.Clan); PT(" ${fmtMoney(data.optLong("gold"))}", 15.sp, Px.Clan)
            Spacer(Modifier.width(10.dp))
            Icon("gem", 20.dp, Color(0xFF7FE0FF)); PT(" ${fmtL(data.optLong("gems"))}", 15.sp, Color(0xFF7FE0FF))
        }
    }, bg = if (UiArt.has(LocalContext.current, "char_bg")) "char_bg" else null) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // sol: dört karakter kartı (kilitliler silüet)
            Column(Modifier.width(430.dp).fillMaxHeight()) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chars.forEachIndexed { i, c -> CharCard(c, i, i == sel, t, Modifier.weight(1f).fillMaxHeight()) { sel = i } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PxButton("1 Kart · ${fmtL(data.optLong("pull_one"))}", Modifier.weight(1f), Px.Blue, icon = "coin", size = 14.sp, height = 42.dp,
                        enabled = data.optLong("gold") >= data.optLong("pull_one") && !CharPull.busy) { pull(1) }
                    PxButton("10 Kart · ${fmtL(data.optLong("pull_ten"))}", Modifier.weight(1.2f), Px.Orange, icon = "coin", size = 14.sp, height = 42.dp,
                        enabled = data.optLong("gold") >= data.optLong("pull_ten") && !CharPull.busy) { pull(10) }
                    PxIcon("info", size = 42.dp) { odds = true }
                }
                PT("10'luk açılış %20 indirimli ve en az bir Nadir garantili.", 11.sp, Px.Sub)
            }
            // sağ: seçili karakterin ayrıntısı
            chars.getOrNull(sel)?.let { c -> CharDetail(c, sel, changed, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
    if (odds) PxDialog("Olasılıklar", { odds = false }, width = 420.dp) {
        val o = data.optJSONArray("odds"); val g = data.optJSONArray("dupe_gems")
        for (i in 0 until 4) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(RARITY_COL[i]))
            Spacer(Modifier.width(8.dp))
            PT("${RARITY_TR[i]} · ${chars.getOrNull(i)?.optString("name") ?: ""}", 15.sp, RARITY_COL[i], Modifier.weight(1f))
            PT("%${o?.optInt(i)}", 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        PT("İlk kart karakteri açar, her 25 kart 1 seviye (en çok 10). En üst seviyedeki karakterin kartı elmasa döner: " +
            (0 until 4).joinToString(", ") { "${RARITY_TR[it]} ${g?.optInt(it)}" } + " elmas.", 13.sp, Px.Sub, maxLines = 4)
    }
}

/** Kart: karakter görseli (char_<id>_card) ya da kahraman silüeti; kilitliyse karanlık + kilit; nadirlik renginde kenar */
@Composable
private fun CharCard(c: JSONObject, index: Int, selected: Boolean, t: Float, modifier: Modifier, onClick: () -> Unit) {
    val r = c.optInt("rarity").coerceIn(0, 3)
    val col = RARITY_COL[r]
    val locked = !c.optBoolean("unlocked")
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(col.copy(alpha = if (locked) 0.08f else 0.28f), Color(0xF0101014))))
        .border(if (selected) 3.dp else 1.5.dp, col.copy(alpha = if (selected) 0.6f + 0.4f * sin(t * 3f) else 0.5f), RoundedCornerShape(6.dp))
        .clickable { onClick() }) {
        val art = "char_${c.optString("id")}_card"
        if (UiArt.has(LocalContext.current, art)) ArtImage(art, Modifier.fillMaxSize().padding(4.dp), silhouette = locked)
        else Box(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 40.dp)) { HeroSprite(index, 0, t + r, !locked, actor = "card$index") }
        if (locked) Icon("lock", 30.dp, Color(0xCCFFFFFF), Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xC0000000)).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            PT(c.optString("name"), 14.sp, col)
            PT(if (locked) RARITY_TR[r] else "Sv ${c.optInt("level")}", 11.sp, Px.Sub)
        }
    }
}

@Composable
private fun CharDetail(c: JSONObject, i: Int, changed: () -> Unit, modifier: Modifier) {
    val r = c.optInt("rarity").coerceIn(0, 3)
    val col = RARITY_COL[r]
    val locked = !c.optBoolean("unlocked")
    val lv = c.optInt("level")
    Column(modifier.pxPanel().padding(12.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PT(c.optString("name").uppercase(), 26.sp, col, outline = true)
                PT("${RARITY_TR[r]} · ${c.optString("class")}", 14.sp, Px.Text)
            }
            when {
                locked -> PT("Kilitli: ilk kartı bul", 14.sp, Px.Orange)
                c.optBoolean("selected") -> PT("Seçili", 16.sp, Px.Name)
                else -> PxButton("Seç", color = Px.Green, size = 16.sp, height = 40.dp) {
                    val res = GameSession.cmd("select_char", "i" to i); Toaster.show(res.msg, res.ok); changed()
                }
            }
        }
        PT(c.optString("desc"), 13.sp, Px.Sub, maxLines = 3)
        Spacer(Modifier.height(6.dp))
        WingsRow(changed)
        Spacer(Modifier.height(6.dp))
        // kart ilerlemesi
        val cards = c.optInt("cards"); val per = c.optInt("per_level", 25)
        val into = if (cards == 0) 0 else (cards - 1) % per
        PT(if (lv >= 10) "En Üst Seviye · Fazla kartlar elmasa döner" else if (locked) "0 Kart" else "Sv $lv → ${lv + 1}: $into / $per kart",
            13.sp, col)
        PxBar(if (lv >= 10) 1f else into / per.toFloat(), col, Modifier.fillMaxWidth(), 10.dp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().background(Color(0x332A2040)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon("magic", 26.dp, col)
            Spacer(Modifier.width(8.dp))
            Column { PT(c.optString("ability"), 16.sp, Color.White); PT(c.optString("ability_desc"), 12.sp, Px.Sub, maxLines = 2) }
        }
        // günlük karakter görevi
        val d = c.optJSONObject("daily") ?: JSONObject()
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().background(Px.Row).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon("quest", 24.dp, Px.Clan)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                PT("Günlük Görev: ${c.optString("name")} ile ${d.optInt("goal")} zombi öldür", 13.sp, Px.Text, maxLines = 2)
                PxBar(d.optInt("kills") / d.optInt("goal", 1).toFloat(), Px.Clan, Modifier.fillMaxWidth(), 8.dp)
            }
            Spacer(Modifier.width(8.dp))
            when {
                d.optBoolean("claimed") -> PT("Alındı", 13.sp, Px.Name)
                d.optInt("kills") >= d.optInt("goal", 40) -> PxButton("1 Kart Al", color = Px.Orange, size = 13.sp, height = 34.dp) {
                    val res = GameSession.cmd("char_daily_claim", "i" to i); Toaster.show(res.msg, res.ok); changed()
                }
                else -> PT("${d.optInt("kills")}/${d.optInt("goal")}", 13.sp, Px.Sub)
            }
        }
        Spacer(Modifier.height(6.dp))
        val perks = c.optJSONArray("perks")
        for (k in 0 until (perks?.length() ?: 0)) {
            val on = k < lv
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).background(if (on) Color(0xFF2E2A3A) else Px.Row).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(k + 1, size = 24.dp)
                Spacer(Modifier.width(8.dp))
                PT(perks!!.getString(k), 13.sp, if (on) Px.Text else Px.Sub, Modifier.weight(1f), maxLines = 2)
                if (on) Icon("check", 16.dp, Px.Name) else Icon("lock", 14.dp, Px.Sub)
            }
        }
    }
}

/** Nasıl kazanılır (kilitli kanat) */
private val WING_HOW = mapOf("wings_ash" to "VIP 3", "wings_dragon" to "VIP 6", "wings_titan" to "VIP 9", "wings_s1" to "Sezon Kartı 30")
private val WING_COL = mapOf("wings_ash" to Color(0xFFFF9A50), "wings_dragon" to Color(0xFFFF4A4A), "wings_titan" to Color(0xFFB07AFF), "wings_s1" to Color(0xFF6AA8FF))

/** Sırta takılan 3B kanatlar: sahip olunanlar dokununca takılır/çıkarılır, kilitliler nasıl kazanılacağını yazar */
@Composable
private fun WingsRow(changed: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val wings = remember(tick) { GameSession.ui("cosmetics").optJSONArray("items").objs().filter { it.optString("kind") == "wings" } }
    if (wings.isEmpty()) return
    PT("Kanatlar", 15.sp, Px.Clan)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (w in wings) {
            val id = w.optString("id"); val owned = w.optBoolean("owned"); val on = w.optBoolean("equipped")
            val col = WING_COL[id] ?: Px.Clan
            Column(Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(6.dp))
                .background(if (on) col.copy(alpha = 0.30f) else Px.Row)
                .border(if (on) 2.dp else 1.dp, if (owned) col else Px.Sub.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(enabled = owned) {
                    val res = GameSession.cmd("equip_cosmetic", "id" to id); Toaster.show(res.msg, res.ok)
                    com.zenvex.online.game.Skins.refresh(); tick++; changed()
                }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (owned) "wings" else "lock", 22.dp, if (owned) col else Px.Sub)
                PT(w.optString("name"), 11.sp, if (owned) Px.Text else Px.Sub, maxLines = 2)
                PT(if (on) "Takılı" else if (owned) "Tak" else WING_HOW[id] ?: "", 11.sp, if (on) Px.Name else Px.Sub, maxLines = 1)
            }
        }
    }
}

/** Kart açılışı: kartlar sırayla düşer, nadirlik renginde ışık sütunu ve parıltılarla döner */
@Composable
private fun CardReveal(results: List<Int>, gems: Int, data: JSONObject, close: () -> Unit) {
    val chars = data.optJSONArray("chars").objs()
    val t = rememberTime()
    val shown = remember { Animatable(0f) }
    LaunchedEffect(results) { shown.animateTo(results.size.toFloat(), tween(450 * results.size)) }
    Box(Modifier.fillMaxSize().background(Color(0xF0050407)).clickable(remember { MutableInteractionSource() }, null) {
        if (shown.value >= results.size) close()
    }, contentAlignment = Alignment.Center) {
        val best = results.maxOrNull() ?: 0
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            for (k in 0 until 14) {
                val a = (k / 14f) * 2 * PI.toFloat() + t * 0.3f
                drawLine(RARITY_COL[best].copy(alpha = 0.18f), c, Offset(c.x + cos(a) * size.maxDimension, c.y + sin(a) * size.maxDimension), 30f)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEachIndexed { k, r ->
                    val p = (shown.value - k).coerceIn(0f, 1f)
                    val c = chars.getOrNull(r) ?: JSONObject()
                    Box(Modifier.width(if (results.size > 5) 92.dp else 150.dp).aspectRatio(0.72f).graphicsLayer {
                        alpha = p; rotationY = (1f - p) * 180f; translationY = (1f - p) * -120f; scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p
                    }.clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(RARITY_COL[r].copy(alpha = 0.55f), Color(0xFF0E0C12))))
                        .border(2.dp, RARITY_COL[r], RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        val art = "char_${c.optString("id")}_card"
                        if (UiArt.has(LocalContext.current, art)) ArtImage(art, Modifier.fillMaxSize().padding(4.dp))
                        else Box(Modifier.fillMaxSize().padding(bottom = 26.dp)) { HeroSprite(r, 0, t, true, actor = "pull$k") }
                        PT(c.optString("name"), 12.sp, RARITY_COL[r], Modifier.align(Alignment.BottomCenter).padding(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (shown.value >= results.size) {
                PT(if (gems > 0) "+$gems Elmas (en üst seviyedeki karakterlerden)" else "Kartlar karakterlerine eklendi!", 16.sp, Px.Clan)
                PT("Kapatmak için dokun", 12.sp, Px.Sub)
            }
        }
    }
}
