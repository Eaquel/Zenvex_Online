package com.zenvex.online.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.BuildConfig
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Online
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val HERO_IDS = listOf("kaan", "nova", "rady", "elif")
val HERO_COLORS = listOf(Color(0xFFFF7A4A), Color(0xFF4AD8FF), Color(0xFFB277FF), Color(0xFF5BE38A))

@Composable
fun LobbyScreen(go: (Screen) -> Unit, epoch: Int) {
    val t = rememberTime()
    val prof = remember(epoch) { GameSession.ui("profile") }
    val chars = remember(epoch) { GameSession.ui("characters") }
    val quests = remember(epoch) { GameSession.ui("quests") }
    var sel by remember(epoch) { mutableIntStateOf(prof.optInt("character")) }
    var dir by remember { mutableIntStateOf(0) }
    var devTaps by remember { mutableIntStateOf(0) }
    var showDev by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val ch = chars.optJSONArray("chars")?.optJSONObject(sel) ?: JSONObject()
    val unlocked = ch.optBoolean("unlocked", sel == 0)

    Box(Modifier.fillMaxSize()) {
        LobbyBackground(t, HERO_COLORS[sel])
        // ---- kahraman sahnesi (sürükleyerek döndür)
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures { _, dx -> acc += dx; if (kotlin.math.abs(acc) > 40f) { dir = (dir + (if (acc > 0) 7 else 1)) % 8; acc = 0f } }
            },
            contentAlignment = Alignment.Center,
        ) {
            HeroStage(sel, dir, t, unlocked, Modifier.offset(y = 30.dp))
        }
        // ---- karakter seçici
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIcon("back", size = 40.dp) { sel = (sel + 3) % 4; dir = 0 }
                Spacer(Modifier.width(14.dp))
                AnimatedContent(sel, transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                }, label = "char") { s ->
                    val c = chars.optJSONArray("chars")?.optJSONObject(s) ?: JSONObject()
                    Column(Modifier.widthIn(min = 220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(c.optString("name").uppercase(), 30.sp, Color.White, display = true, glow = HERO_COLORS[s].copy(alpha = 0.8f))
                        T("${c.optString("class")} · Sv ${c.optInt("level")}/${c.optInt("max", 10)}", 13.sp, HERO_COLORS[s].lighten(1.2f), bold = true)
                    }
                }
                Spacer(Modifier.width(14.dp))
                RoundIcon("next", size = 40.dp) { sel = (sel + 1) % 4; dir = 0 }
            }
            Spacer(Modifier.height(8.dp))
            if (unlocked) {
                if (prof.optInt("character") != sel) GButton("SEÇ", tone = Tone.Green, height = 36.dp, size = 13.sp) {
                    val r = GameSession.cmd("select_char", "i" to sel); Toaster.show(r.msg, r.ok); go(Screen.Lobby)
                }
            } else {
                val can = ch.optBoolean("can_unlock")
                GButton("AÇ", tone = if (can) Tone.Gold else Tone.Dark, icon = "lock", height = 36.dp, size = 13.sp,
                    sub = "Sv ${ch.optInt("req")} · ${ch.optInt("gold")} altın") {
                    val r = GameSession.cmd("unlock_char", "i" to sel); Toaster.show(r.msg, r.ok); if (r.ok) go(Screen.Lobby)
                }
            }
        }
        // ---- üst sol: profil kartı
        ProfileCard(prof, Modifier.align(Alignment.TopStart).padding(14.dp)) { go(Screen.Profile) }
        // ---- üst sağ: para birimleri + ayarlar
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("coin", fmt(prof.optInt("gold")), Pal.Gold)
            Chip("gem", fmt(prof.optInt("gems")), Pal.Gem) { go(Screen.Shop) }
            RoundIcon("chat", badge = 0) { go(Screen.Chat) }
            RoundIcon("gear") { go(Screen.Settings) }
        }
        // ---- sol: görevler ve etkinlik
        Column(Modifier.align(Alignment.CenterStart).padding(start = 14.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuestMini(quests) { go(Screen.Quests) }
            SideCard("calendar", "ETKİNLİK", "Kızıl Ay: gece 2× ganimet", Pal.Red) { Toaster.show("Kızıl Ay etkinliği her gece aktif: gece 2× altın!") }
            SideCard("globe", "SUNUCU", Online.status.value + if (Online.online.value) " · ${Online.playerCount.value} oyuncu" else "", if (Online.online.value) Pal.Green else Pal.Sub) {
                Toaster.show(Online.status.value)
            }
        }
        // ---- sağ: sezon kartı
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = 40.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PassCard(prof, t) { go(Screen.Pass) }
            SideCard("hanger", "KOZMETİK", "Kostüm, çerçeve, kaplama", Pal.Purple) { go(Screen.Cosmetics) }
            SideCard("shop", "TÜCCAR", "Silah, zırh, malzeme", Pal.Gold) { go(Screen.Shop) }
        }
        // ---- alt dok + OYNA
        Row(
            Modifier.align(Alignment.BottomStart).padding(14.dp).glass(22.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DockItem("user", "Karakter") { go(Screen.Character) }
            DockItem("bag", "Envanter") { go(Screen.Inventory) }
            DockItem("craft", "Üretim") { go(Screen.Craft) }
            DockItem("magic", "Yetenek", badge = prof.optInt("points")) { go(Screen.Skills) }
            DockItem("quest", "Görevler") { go(Screen.Quests) }
        }
        PlayButton(prof, t, Modifier.align(Alignment.BottomEnd).padding(14.dp)) { go(Screen.Loading) }
        // sürüm (7 dokunuş → geliştirici menüsü)
        T("v${BuildConfig.VERSION_NAME}", 10.sp, Pal.Sub.copy(alpha = 0.6f),
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clickable(remember { MutableInteractionSource() }, null) {
                devTaps++
                if (devTaps >= 7) { devTaps = 0; showDev = true } else if (devTaps >= 4) Toaster.show("Geliştirici menüsüne ${7 - devTaps} dokunuş")
            })
        if (showDev) DevMenu(onClose = { showDev = false; refresh++; go(Screen.Lobby) })
    }
}

fun fmt(n: Int): String = "%,d".format(n).replace(',', '.')

@Composable
private fun HeroStage(sel: Int, dir: Int, t: Float, unlocked: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val frame = ((t * 6f).toInt()) % 6
    val key = "l/${HERO_IDS[sel]}/${dir}_$frame"
    val img = remember(key) { SpriteCache.get(ctx, key) ?: SpriteCache.get(ctx, "l/${HERO_IDS[sel]}/0_0") }
    val col = HERO_COLORS[sel]
    Box(modifier.size(360.dp, 420.dp), contentAlignment = Alignment.BottomCenter) {
        // platform
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height * 0.9f
            drawOval(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), size.width * 0.42f),
                Offset(cx - size.width * 0.42f, cy - size.width * 0.12f), Size(size.width * 0.84f, size.width * 0.24f))
            drawOval(Color(0xFF0C1420), Offset(cx - size.width * 0.3f, cy - size.width * 0.075f), Size(size.width * 0.6f, size.width * 0.15f))
            drawOval(col.copy(alpha = 0.9f), Offset(cx - size.width * 0.3f, cy - size.width * 0.075f), Size(size.width * 0.6f, size.width * 0.15f), style = Stroke(2.dp.toPx()))
            // dönen işaretler
            for (k in 0 until 12) {
                val a = t * 0.6f + k * (2 * PI.toFloat() / 12)
                val x = cx + cos(a) * size.width * 0.27f
                val y = cy + sin(a) * size.width * 0.066f
                drawCircle(col.copy(alpha = 0.4f + 0.4f * sin(a).coerceAtLeast(0f)), 2.2.dp.toPx(), Offset(x, y))
            }
            // ışık sütunu
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = 0.12f)), 0f, cy), Offset(cx - size.width * 0.22f, 0f), Size(size.width * 0.44f, cy))
        }
        if (img != null) {
            val dark = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.15f, 0.15f, 0.2f, 1f) }) }
            Image(img, null, Modifier.fillMaxSize().padding(bottom = 4.dp), filterQuality = FilterQuality.High,
                colorFilter = if (unlocked) null else dark)
        } else {
            T("Görseller yükleniyor…", 14.sp, Pal.Sub, Modifier.align(Alignment.Center))
        }
        if (!unlocked) Icon("lock", 54.dp, Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ProfileCard(prof: JSONObject, modifier: Modifier, onClick: () -> Unit) {
    val lvl = prof.optInt("level", 1)
    val xp = prof.optInt("xp"); val next = prof.optInt("xp_next", 1)
    Row(modifier.glass(26.dp).clickable { onClick() }.padding(6.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(prof.optInt("character"), frameColor(prof.optString("frame")), 52.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(prof.optString("name"), 16.sp, bold = true)
                if (prof.optString("tag_name").isNotEmpty()) { Spacer(Modifier.width(6.dp)); T(prof.optString("tag_name"), 11.sp, Pal.Gold, bold = true) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Pal.Gold).padding(horizontal = 6.dp, vertical = 1.dp)) { T("Sv $lvl", 11.sp, Color(0xFF2A1A04), bold = true) }
                Spacer(Modifier.width(8.dp))
                Bar(xp / next.toFloat().coerceAtLeast(1f), Pal.Blue, Modifier.width(120.dp), 8.dp)
            }
            T("$xp / $next XP", 10.sp, Pal.Sub)
        }
    }
}

fun frameColor(f: String) = when (f) {
    "frame_bronze" -> Color(0xFFCD7F32); "frame_silver" -> Color(0xFFD8DEE6); "frame_gold" -> Pal.Gold; "frame_s1" -> Pal.Red; else -> Color(0xFF4D9BFF)
}

@Composable
fun Avatar(ch: Int, frame: Color, size: Dp) {
    val ctx = LocalContext.current
    val img = remember(ch) { SpriteCache.get(ctx, "l/${HERO_IDS.getOrElse(ch) { "kaan" }}/0_0") }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(HERO_COLORS.getOrElse(ch) { Pal.Blue }.copy(alpha = 0.6f), Color(0xFF0A101A))))) {
            if (img != null) Canvas(Modifier.fillMaxSize()) {
                // baş-omuz kadrajı
                val srcH = (img.height * 0.4f).toInt()
                val k = size.toPx() * 1.25f / srcH
                val dw = img.width * k
                drawImage(img, androidx.compose.ui.unit.IntOffset(0, 0), androidx.compose.ui.unit.IntSize(img.width, srcH),
                    androidx.compose.ui.unit.IntOffset(((this.size.width - dw) / 2).toInt(), (-this.size.height * 0.02f).toInt()),
                    androidx.compose.ui.unit.IntSize(dw.toInt(), (srcH * k).toInt()), filterQuality = FilterQuality.High)
            }
        }
        FrameRing(frame, Modifier.size(size))
    }
}

@Composable
private fun SideCard(icon: String, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.width(210.dp).glass(16.dp, color).clickable { onClick() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, 22.dp, color) }
        Spacer(Modifier.width(10.dp))
        Column {
            T(title, 13.sp, bold = true)
            T(sub, 11.sp, Pal.Sub, maxLines = 1)
        }
    }
}

@Composable
private fun QuestMini(q: JSONObject, onClick: () -> Unit) {
    val m = q.optJSONObject("main")
    Column(Modifier.width(210.dp).glass(16.dp, Pal.Gold).clickable { onClick() }.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon("quest", 18.dp, Pal.Gold); Spacer(Modifier.width(6.dp)); T("ANA GÖREV", 12.sp, Pal.Gold, bold = true)
            Spacer(Modifier.weight(1f))
            if (m != null) T("${m.optInt("index")}/${m.optInt("total")}", 11.sp, Pal.Sub)
        }
        if (m != null) {
            T(m.optString("title"), 12.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Bar(m.optInt("progress") / m.optInt("goal", 1).toFloat(), Pal.Gold, Modifier.fillMaxWidth(), 7.dp)
        } else T("Tüm ana görevler tamamlandı!", 12.sp, Pal.Green)
    }
}

@Composable
private fun PassCard(prof: JSONObject, t: Float, onClick: () -> Unit) {
    val tier = prof.optInt("pass_tier")
    Box(Modifier.width(210.dp).glass(16.dp, Pal.Gold).clickable { onClick() }) {
        Canvas(Modifier.matchParentSize()) {
            // parlama süpürmesi
            val x = ((t * 0.35f) % 1.6f - 0.3f) * size.width
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x30FFE9A8), Color.Transparent), Offset(x - 60f, 0f), Offset(x + 60f, size.height)))
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE07A), Color(0xFFE8961C)))), contentAlignment = Alignment.Center) {
                Icon("crown", 26.dp, Color(0xFF3A2204))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                T("SEZON 1", 13.sp, Pal.Gold, bold = true)
                T("Kademe $tier/30", 11.sp, Pal.Text)
                Bar(tier / 30f, Pal.Gold, Modifier.width(120.dp).padding(top = 3.dp), 6.dp)
            }
        }
    }
}

@Composable
private fun DockItem(icon: String, label: String, badge: Int = 0, onClick: () -> Unit) {
    Box {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable { com.zenvex.online.game.Audio.play("click", bus = com.zenvex.online.game.Audio.Bus.Ui); onClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, 26.dp, Pal.Text)
            T(label, 11.sp, Pal.Sub, bold = true)
        }
        if (badge > 0) Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(Pal.Red), contentAlignment = Alignment.Center) { T("$badge", 9.sp, bold = true) }
    }
}

@Composable
private fun PlayButton(prof: JSONObject, t: Float, modifier: Modifier, onClick: () -> Unit) {
    val pulse = 0.5f + 0.5f * sin(t * 3f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(230.dp, 86.dp)) {
            drawRoundRect(Brush.radialGradient(listOf(Pal.Gold.copy(alpha = 0.25f + 0.2f * pulse), Color.Transparent), radius = size.width * 0.7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f))
        }
        GButton("OYNA", Modifier.width(210.dp), Tone.Gold, icon = "play", size = 22.sp, height = 66.dp, sub = prof.optString("zone")) { onClick() }
    }
}

// =========================================================================== arka plan
@Composable
fun LobbyBackground(t: Float, accent: Color) {
    val stars = remember { List(90) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat() * 0.55f, Math.random().toFloat()) } }
    val city = remember { buildSkyline(41, 26, 0.32f, 0.16f) }
    val city2 = remember { buildSkyline(77, 18, 0.22f, 0.1f) }
    val trees = remember { List(34) { floatArrayOf(it / 33f + (Math.random().toFloat() - 0.5f) * 0.02f, 0.8f + Math.random().toFloat() * 0.5f) } }
    val embers = remember { List(40) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.5f + Math.random().toFloat(), Math.random().toFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        // gökyüzü
        drawRect(Brush.verticalGradient(listOf(Color(0xFF050814), Color(0xFF0E1A34), Color(0xFF2A2440), Color(0xFF5A3428)), 0f, h * 0.75f))
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(t * (1f + s[2] * 2f) + s[2] * 10f)
            drawCircle(Color.White.copy(alpha = 0.3f + 0.6f * tw * s[2]), (0.6f + s[2]) * density, Offset(s[0] * w, s[1] * h))
        }
        // ay
        val mx = w * 0.78f; val my = h * 0.2f
        drawCircle(Brush.radialGradient(listOf(Color(0x40FFE8C0), Color.Transparent), Offset(mx, my), h * 0.3f), h * 0.3f, Offset(mx, my))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6E0), Color(0xFFE8D8B0)), Offset(mx - 10, my - 10), h * 0.07f), h * 0.065f, Offset(mx, my))
        drawCircle(Color(0x30A09070), h * 0.015f, Offset(mx - h * 0.02f, my + h * 0.01f))
        drawCircle(Color(0x30A09070), h * 0.01f, Offset(mx + h * 0.022f, my - h * 0.018f))
        // bulutlar
        for (k in 0 until 6) {
            val cx = ((k * 0.23f + t * 0.006f * (1 + k % 3)) % 1.4f - 0.2f) * w
            val cy = h * (0.12f + (k % 3) * 0.08f)
            for (j in 0..3) drawOval(Color(0x18C8B8D8), Offset(cx + j * 60f - 120f, cy - 20f + (j % 2) * 10f), Size(220f, 50f))
        }
        // ufuk parıltısı (yanan şehir)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55FF6A2A), Color.Transparent), h * 0.45f, h * 0.75f), Offset(0f, h * 0.45f), Size(w, h * 0.3f))
        // uzak şehir siluetleri (paralaks)
        val par = sin(t * 0.08f) * 12f
        drawSkyline(city2, w, h, h * 0.66f, Color(0xFF1C1A2C), par * 0.4f, t, 0.15f)
        drawSkyline(city, w, h, h * 0.7f, Color(0xFF12121E), par * 0.7f, t, 0.35f)
        // orman silueti
        for (tr in trees) {
            val x = tr[0] * w + par
            val th = h * 0.22f * tr[1]
            val base = h * 0.8f
            val p = Path().apply { moveTo(x, base - th); lineTo(x - th * 0.28f, base); lineTo(x + th * 0.28f, base); close() }
            drawPath(p, Color(0xFF080C10))
        }
        // zemin + sis
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0E12), Color(0xFF05070A)), h * 0.78f, h), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
        for (k in 0 until 4) {
            val fx = ((t * 0.012f * (k + 1) + k * 0.3f) % 1f) * w
            drawOval(Brush.radialGradient(listOf(Color(0x22B8C8E0), Color.Transparent)), Offset(fx - w * 0.4f, h * (0.7f + k * 0.04f)), Size(w * 0.8f, h * 0.16f))
            drawOval(Brush.radialGradient(listOf(Color(0x22B8C8E0), Color.Transparent)), Offset(fx - w * 1.4f, h * (0.7f + k * 0.04f)), Size(w * 0.8f, h * 0.16f))
        }
        // korlar
        for (e in embers) {
            val y = (((e[1] - t * 0.04f * e[2]) % 1f) + 1f) % 1f
            val x = e[0] * w + sin(t * e[2] + e[3] * 6f) * 20f
            val a = sin(y * PI.toFloat()).coerceAtLeast(0f)
            drawCircle(Color(0xFFFFA040).copy(alpha = 0.7f * a), (1.2f + e[2] * 1.8f) * density, Offset(x, y * h))
        }
        // vurgu rengi tonu + vinyet
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent), Offset(w / 2, h * 0.6f), h * 0.6f))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xB0000000)), Offset(w / 2, h / 2), w * 0.75f))
    }
}

private class Building(val x: Float, val w: Float, val h: Float, val windows: List<FloatArray>, val broken: Boolean)

private fun buildSkyline(seed: Int, n: Int, maxH: Float, minH: Float): List<Building> {
    val r = java.util.Random(seed.toLong())
    var x = -0.05f
    val out = ArrayList<Building>()
    while (x < 1.05f) {
        val bw = 0.025f + r.nextFloat() * 0.05f
        val bh = minH + r.nextFloat() * (maxH - minH)
        val wins = ArrayList<FloatArray>()
        for (i in 0 until 14) if (r.nextFloat() < 0.35f) wins += floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat())
        out += Building(x, bw, bh, wins, r.nextFloat() < 0.3f)
        x += bw + r.nextFloat() * 0.01f
    }
    return out
}

private fun DrawScope.drawSkyline(list: List<Building>, w: Float, h: Float, base: Float, col: Color, shift: Float, t: Float, winA: Float) {
    for (b in list) {
        val l = b.x * w + shift; val bw = b.w * w; val bh = b.h * h
        if (b.broken) {
            val p = Path().apply { moveTo(l, base); lineTo(l, base - bh); lineTo(l + bw * 0.4f, base - bh * 0.85f); lineTo(l + bw * 0.6f, base - bh * 0.95f); lineTo(l + bw, base - bh * 0.7f); lineTo(l + bw, base); close() }
            drawPath(p, col)
        } else drawRect(col, Offset(l, base - bh), Size(bw, bh))
        for (wn in b.windows) {
            val flick = if (sin(t * 3f + wn[2] * 50f) > 0.96f) 0.2f else 1f
            drawRect(Color(0xFFFFC870).copy(alpha = winA * wn[2] * flick), Offset(l + wn[0] * (bw - 4f), base - bh + 6f + wn[1] * (bh - 12f)), Size(3f, 4f))
        }
    }
}
