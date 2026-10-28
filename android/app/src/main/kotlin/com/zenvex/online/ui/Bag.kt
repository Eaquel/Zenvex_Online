package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Audio
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Model3D
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

// =========================================================================== piksel ekran çerçevesi
/** Piksel tam ekran: koyu zemin, perçinli başlık şeridi, geri tuşu. */
@Composable
fun PxFrame(title: String, icon: String, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {}, bg: String? = null,
            content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF14130F))) {
        // arka plan: sahne görseli (karartılmış) ya da ince ızgara deseni (piksel zemin)
        if (bg != null) SceneBackground(bg, null, dim = 0.62f)
        else Canvas(Modifier.fillMaxSize()) {
            val st = 24.dp.toPx()
            var x = 0f
            while (x < size.width) { drawRect(Color(0x08FFFFFF), Offset(x, 0f), Size(1f, size.height)); x += st }
            var y = 0f
            while (y < size.height) { drawRect(Color(0x08FFFFFF), Offset(0f, y), Size(size.width, 1f)); y += st }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(50.dp).pxPanel(Px.PanelDark).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PxIcon("back", size = 38.dp) { onBack() }
                Spacer(Modifier.width(12.dp))
                Icon(icon, 24.dp, Px.Clan)
                Spacer(Modifier.width(8.dp))
                PT(title, 22.sp, Px.Text, outline = true)
                Spacer(Modifier.weight(1f))
                right()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Piksel eşya kutusu; bekleme süresi varsa üstten aşağı kararan perde + kalan saniye. */
@Composable
fun PxSlot(item: JSONObject?, size: Dp, selected: Boolean = false, hint: String? = null, glow: Boolean = false, modifier: Modifier = Modifier, ghost: String? = null) {
    val r = item?.optInt("rarity") ?: 0
    val col = Pal.rarity(r)
    // V10 yuva çerçeveleri: sıradan/nadir kızıl, epik mor ışıltılı, efsanevi ve sezonluk altın ışıltılı
    val frame = when { item == null || r < 2 -> "slot_common"; r == 2 -> "slot_epic"; else -> "slot_legend" }
    val old = Modifier.clip(RoundedCornerShape(3.dp))
        .background(if (item == null) Color(0xB81C1B17) else Color(0xE62A2924))
        .border(2.dp, Px.Edge, RoundedCornerShape(3.dp))
        .padding(2.dp)
        .border(1.dp, if (item == null) Color(0x14FFFFFF) else col.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
    Box(
        modifier.size(size).artBehind(frame, alpha = if (item == null) 0.7f else 1f, fallback = old)
            .then(if (selected || glow) Modifier.border(2.dp, if (selected) Color.White else Px.Clan, RoundedCornerShape(3.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            SpriteImage("i/${item.optString("id")}", Modifier.padding(size * 0.17f).fillMaxSize())
            val cd = item.optDouble("cd", 0.0).toFloat()
            val max = item.optDouble("cdmax", 0.0).toFloat()
            if (cd > 0f && max > 0f) {
                val k = (cd / max).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xB0000000), Offset.Zero, Size(this.size.width, this.size.height * k)) }
                PT("${cd.toInt() + 1}", (size.value * 0.3f).sp, Color.White, outline = true)
            }
            val n = item.optInt("count")
            if (n > 1) PT("$n", (size.value * 0.22f).sp, Color.White, Modifier.align(Alignment.BottomEnd).padding(end = 3.dp), outline = true)
            // dayanıklılık çubuğu: yuvanın altında, kalan cana göre yeşil → sarı → kırmızı; kırıkta kırmızı çarpı
            if (item.has("dur")) {
                val d = item.optInt("dur", 100).coerceIn(0, 100)
                Box(Modifier.align(Alignment.BottomCenter).padding(start = size * 0.12f, end = size * 0.12f, bottom = size * 0.07f)
                    .fillMaxWidth().height((size.value * 0.07f).coerceAtLeast(3f).dp).background(Color(0xCC000000))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(d / 100f).background(durColor(d)))
                }
                if (d == 0) Icon("close", size * 0.42f, Color(0xDDFF4A3A))
            }
        } else if (ghost != null) {
            // boş ekipman yuvası: soluk piksel siluet + küçük ad
            // koyu siluet: eşya varmış gibi görünmesin
            ArtImage(ghost, Modifier.padding(size * 0.14f).fillMaxSize().alpha(0.55f), silhouette = true)
            if (hint != null) PT(hint, (size.value * 0.16f).sp, Color(0x80FFFFFF), Modifier.align(Alignment.BottomCenter), outline = true)
        } else if (hint != null) PT(hint, (size.value * 0.2f).sp, Color(0x55FFFFFF))
    }
}

// =========================================================================== çanta (sürükle-bırak + cepler + bölmeler + sandık)
private const val TAB = 100000
private const val CHEST = 200000
private const val EQ = 300000

private class DragState {
    var from by mutableStateOf(-1)
    var item by mutableStateOf<JSONObject?>(null)
    var pos by mutableStateOf(Offset.Zero)
    val rects = HashMap<Int, Rect>()
    fun target(): Int? = rects.entries.firstOrNull { it.value.contains(pos) }?.key
}

/** Miktar seçimi: yere bırak, böl, sandığa koy, çantaya al */
internal class AmountAsk(val action: String, val slot: Int, val max: Int, val start: Int, val title: String)

@Composable
fun InventoryScreen(back: () -> Unit, inGame: Boolean = false, onBuild: ((Int) -> Unit)? = null, onCraft: (() -> Unit)? = null) {
    var tick by remember { mutableIntStateOf(0) }
    val inv = remember(tick) { GameSession.ui("inventory") }
    val hero = remember(tick / 20) { GameSession.ui("profile").optInt("character") }
    val ci = inv.optInt("chest", -1)
    val chest = remember(tick, ci) { if (ci >= 0) GameSession.ui("chest:$ci") else null }
    // bekleme süreleri akarken cepleri tazele
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    BagView(inv, hero, inGame, back, { name, kv -> GameSession.cmd(name, *kv) }, onCraft = onCraft, chest = chest, onBuild = onBuild) { tick++ }
}

private val EQ_LEFT = listOf("helmet" to "Kask", "jacket" to "Ceket", "pants" to "Pantolon", "boots" to "Bot")
private val EQ_RIGHT = listOf("weapon" to "Silah", "bag" to "Çanta", "ring" to "Yüzük", "necklace" to "Kolye")
private val EQUIP_KINDS = listOf("Weapon", "Helmet", "Jacket", "Boots", "Bag", "Cloak", "Ring", "Necklace")
private val BagPanel = Color(0xB4201F1C)
private val BagPanel2 = Color(0xA82B2B26)

/**
 * Çanta görünümü (veri dışarıdan: ekran görüntüsü testinde örnek JSON ile çizilir).
 * Oyun arkada görünür (yarı saydam karartma). Solda ekipman (yakında sandık varsa sandık), cepler, bölme ızgarası,
 * kenarda 4 bölme sekmesi, sağda seçili eşyanın ayrıntısı ve eylemleri.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BagView(
    inv: JSONObject, hero: Int, inGame: Boolean, back: () -> Unit,
    cmd: (String, Array<Pair<String, Any>>) -> GameSession.Reply, onCraft: (() -> Unit)? = null,
    chest: JSONObject? = null, onBuild: ((Int) -> Unit)? = null, refresh: () -> Unit,
) {
    var sel by remember { mutableStateOf<Int?>(null) }
    var selEq by remember { mutableStateOf<String?>(null) }
    var selChest by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var showChest by remember { mutableStateOf(true) }
    var ask by remember { mutableStateOf<AmountAsk?>(null) }
    val drag = remember { DragState() }
    val haptic = LocalHapticFeedback.current
    val slots = inv.optJSONArray("slots")
    val pockets = inv.optInt("pockets", 4)
    val pages = inv.optJSONArray("pages")
    val n = slots?.length() ?: 0
    val pg = pages?.optJSONObject(page)?.takeIf { it.optBoolean("open") } ?: pages?.optJSONObject(0)
    val pStart = pg?.optInt("start", pockets) ?: pockets
    val pLen = pg?.optInt("len", n - pockets) ?: (n - pockets)
    val ci = chest?.optInt("i", -1) ?: -1
    val chestOn = chest != null && showChest
    val chestSlots = chest?.optJSONArray("slots")
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    val quiet = remember { MutableInteractionSource() }
    // öğretici: etkin rehber adımı ve işaretlenecek denetimlerin ekran konumları
    val tutR = remember { HashMap<String, Rect>() }
    val tstep = remember(inv) { Tut.step() }

    fun done(r: GameSession.Reply, ok: String? = null) {
        if (!r.ok) Toaster.show(r.msg, false) else if (r.msg.isNotEmpty()) Toaster.show(r.msg) else if (ok != null) Toaster.show(ok)
        refresh()
    }

    fun drop(from: Int, to: Int?) {
        // V15: kuşanılan eşya ekipman alanının dışına (çantaya ya da boşluğa) sürüklenince çıkarılır
        if (from >= EQ) {
            if (to != null && to >= EQ) return
            val k = (EQ_LEFT + EQ_RIGHT).getOrNull(from - EQ)?.first ?: return
            val r = cmd("unequip", arrayOf("slot" to k))
            if (!r.ok) Toaster.show(r.msg, false) else Audio.play("pickup", 0.6f, 1.2f, bus = Audio.Bus.Ui)
            selEq = null
            return
        }
        if (to == null || to == from) return
        val r = when {
            from >= CHEST -> when {
                to >= EQ -> null
                to >= CHEST -> cmd("chest_move", arrayOf("chest" to ci, "from" to from - CHEST, "to" to to - CHEST))
                else -> cmd("chest_take", arrayOf("chest" to ci, "cslot" to from - CHEST))
            }
            to >= EQ -> cmd("use", arrayOf("slot" to from))
            to >= CHEST -> cmd("chest_put", arrayOf("chest" to ci, "slot" to from))
            to >= TAB -> cmd("move_page", arrayOf("from" to from, "page" to to - TAB))
            else -> cmd("move", arrayOf("from" to from, "to" to to))
        } ?: return
        if (!r.ok && r.msg.isNotEmpty()) Toaster.show(r.msg, false) else Audio.play("pickup", 0.6f, 1.2f, bus = Audio.Bus.Ui)
        sel = null; selChest = null
    }

    fun dragMod(key: Int, s: JSONObject?) = Modifier
        .onGloballyPositioned { drag.rects[key] = it.boundsInRoot() }
        .then(if (s == null) Modifier else Modifier.pointerInput(key, s.optString("id"), s.optInt("count")) {
            detectDragGesturesAfterLongPress(
                onDragStart = { o ->
                    drag.from = key; drag.item = s
                    drag.pos = (drag.rects[key]?.topLeft ?: Offset.Zero) + o
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Audio.play("click", 0.7f, 0.8f, bus = Audio.Bus.Ui)
                },
                onDrag = { ch, d -> ch.consume(); drag.pos += d },
                onDragEnd = { drop(drag.from, drag.target()); drag.from = -1; drag.item = null; refresh() },
                onDragCancel = { drag.from = -1; drag.item = null },
            )
        })

    fun slotMod(i: Int, s: JSONObject?) = dragMod(i, s).clickable(quiet, null) {
        if (s != null) { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); sel = i; selEq = null; selChest = null }
    }

    // kapalı bölme seçiliyse ana çantaya dön
    if (pages?.optJSONObject(page)?.optBoolean("open") == false) page = 0

    Box(Modifier.fillMaxSize().background(Color(0x59000000)).onGloballyPositioned { rootPos = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // ---- üst şerit
            Row(Modifier.fillMaxWidth().height(48.dp).pxPanel(BagPanel).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PxIcon("back", Modifier.tutRect(tutR, "back"), size = 36.dp) { back() }
                Spacer(Modifier.width(10.dp))
                Icon("bag", 22.dp, Px.Clan)
                Spacer(Modifier.width(6.dp))
                PT("Çanta", 20.sp, Px.Text, outline = true)
                Spacer(Modifier.width(10.dp))
                PT("${inv.optInt("used")} / ${inv.optInt("cap")}", 14.sp, Px.Sub)
                Spacer(Modifier.weight(1f))
                Icon("coin", 18.dp, Px.Clan); Spacer(Modifier.width(4.dp)); PT(fmt(inv.optInt("gold")), 15.sp, Px.Clan)
                Spacer(Modifier.width(10.dp))
                if (chest != null) {
                    PxButton(if (chestOn) "Ekipman" else "Sandık", height = 36.dp, size = 13.sp, color = Color(0xFF8A6A3A), icon = if (chestOn) "shirt" else "bag") {
                        showChest = !showChest; selChest = null
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (onCraft != null) {
                    PxButton("Üret", Modifier.tutRect(tutR, "craft"), height = 36.dp, size = 13.sp, color = Px.Blue, icon = "craft") { onCraft() }
                    Spacer(Modifier.width(6.dp))
                }
                PxButton("Sırala", height = 36.dp, size = 13.sp, color = Px.Dark, icon = "refresh") { done(cmd("sort", arrayOf("page" to page))) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ---- sol: ekipman ya da sandık
                if (chestOn) {
                    PxPanel(Modifier.width(250.dp).fillMaxHeight(), color = Color(0xB82A2116), pad = 8.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PT(chest.optString("name"), 14.sp, Px.Clan)
                            Spacer(Modifier.weight(1f))
                            PT("${chest.optInt("used")} / ${chest.optInt("cap")}", 12.sp, Px.Sub)
                        }
                        Spacer(Modifier.height(4.dp))
                        FlowRow(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (k in 0 until (chestSlots?.length() ?: 0)) {
                                val s = chestSlots!!.optJSONObject(k)
                                PxSlot(s, 54.dp, selChest == k, glow = drag.item != null && drag.target() == CHEST + k,
                                    modifier = dragMod(CHEST + k, s).clickable(quiet, null) {
                                        if (s != null) { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); selChest = k; sel = null; selEq = null }
                                    }.scale(if (drag.from == CHEST + k) 0.9f else 1f))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PxButton("Hepsini Koy", Modifier.weight(1f), color = Px.Blue, height = 34.dp, size = 11.sp) {
                                done(cmd("chest_all", arrayOf("chest" to ci, "put" to true)))
                            }
                            PxButton("Hepsini Al", Modifier.weight(1f), color = Px.Green, height = 34.dp, size = 11.sp) {
                                done(cmd("chest_all", arrayOf("chest" to ci, "put" to false)))
                            }
                        }
                        if (chest.optInt("used") == 0) {
                            Spacer(Modifier.height(4.dp))
                            PxButton("Sandığı Sök", Modifier.fillMaxWidth(), color = Px.Dark, height = 30.dp, size = 11.sp) {
                                done(cmd("pickup_chest", arrayOf("chest" to ci)))
                            }
                        }
                    }
                } else {
                    val eq = inv.optJSONObject("eq") ?: JSONObject()
                    PxPanel(Modifier.width(196.dp).fillMaxHeight(), color = BagPanel2, pad = 8.dp) {
                        PT("Ekipman", 14.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
                        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for ((k, label) in EQ_LEFT) EqBox(eq, k, label, selEq == k, dragMod(EQ + EQ_LEFT.indexOfFirst { it.first == k }, eq.optJSONObject(k))) { selEq = k; sel = null; selChest = null }
                            }
                            Box(Modifier.width(54.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                // 3B karakter elindeki silahla (kuşanınca hemen değişir)
                                val bt = rememberTime()
                                val wm = Model3D.itemModel(eq.optJSONObject("weapon")?.optString("id").orEmpty())
                                Box(Modifier.fillMaxWidth().height(150.dp)) { HeroSprite(hero, 0, bt, true, actor = "bag", weapon = wm) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for ((k, label) in EQ_RIGHT) EqBox(eq, k, label, selEq == k, dragMod(EQ + 4 + EQ_RIGHT.indexOfFirst { it.first == k }, eq.optJSONObject(k))) { selEq = k; sel = null; selChest = null }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            PxStat("heart", "${inv.optDouble("max_hp").toInt()}", Px.Hard)
                            PxStat("fist", "×${"%.2f".format(inv.optDouble("dmg"))}", Px.Orange)
                            PxStat("shield", "${inv.optDouble("armor").toInt()}", Px.Sub)
                        }
                    }
                }
                // ---- cepler: oyunda hızlı kullanım tuşları
                PxPanel(Modifier.width(70.dp).fillMaxHeight(), color = BagPanel, pad = 6.dp) {
                    PT("Cepler", 12.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(4.dp))
                    for (i in 0 until pockets) {
                        val s = slots?.optJSONObject(i)
                        PxSlot(s, 54.dp, sel == i, hint = "${i + 1}", glow = drag.item != null && drag.target() == i,
                            modifier = slotMod(i, s).align(Alignment.CenterHorizontally).scale(if (drag.from == i) 0.9f else 1f))
                        Spacer(Modifier.height(4.dp))
                    }
                }
                // ---- bölme ızgarası + kenarda bölme sekmeleri
                PxPanel(Modifier.weight(1f).fillMaxHeight(), color = BagPanel2, pad = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PT(pg?.optString("name") ?: "Ana Çanta", 14.sp, Px.Clan)
                        Spacer(Modifier.width(8.dp))
                        PT("${pg?.optInt("used") ?: 0} / $pLen", 12.sp, Px.Sub)
                        Spacer(Modifier.weight(1f))
                        PT("Basılı Tut: Sürükle", 10.sp, Px.Sub)
                    }
                    Spacer(Modifier.height(4.dp))
                    FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in pStart until (pStart + pLen).coerceAtMost(n)) {
                            val s = slots!!.optJSONObject(i)
                            PxSlot(s, 54.dp, sel == i, glow = drag.item != null && drag.target() == i,
                                modifier = slotMod(i, s).scale(if (drag.from == i) 0.9f else 1f))
                        }
                    }
                }
                Column(Modifier.width(52.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in 0 until (pages?.length() ?: 1)) {
                        val o = pages?.optJSONObject(p) ?: JSONObject().put("open", true).put("name", "Ana Çanta")
                        BagTab(p, o, p == page, drag.item != null && drag.target() == TAB + p,
                            Modifier.onGloballyPositioned { drag.rects[TAB + p] = it.boundsInRoot() }) {
                            if (o.optBoolean("open")) { page = p; sel = null; Audio.play("click", 0.6f, bus = Audio.Bus.Ui) }
                            else Toaster.show("${o.optString("need")} Kuşanınca Açılır", false)
                        }
                    }
                }
                // ---- ayrıntı ve eylemler
                PxPanel(Modifier.width(196.dp).fillMaxHeight(), color = BagPanel, pad = 8.dp) {
                    val item: JSONObject? = sel?.let { slots?.optJSONObject(it) } ?: selEq?.let { (inv.optJSONObject("eq") ?: JSONObject()).optJSONObject(it) }
                        ?: selChest?.let { chestSlots?.optJSONObject(it) }
                    if (item == null) {
                        PT("Bir Eşya Seç", 14.sp, Px.Sub, Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
                        Spacer(Modifier.height(8.dp))
                        PT("İksir ve ilk yardımı cebe koy: oyunda tek dokunuşla kullanılır. Eşyayı basılı tutup bölme sekmesine, sandığa ya da ekipmana sürükleyebilirsin.",
                            11.sp, Px.Sub, maxLines = 7)
                    } else {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { PxItemDetail(item) }
                        val count = item.optInt("count", 1)
                        val kind = item.optString("kind")
                        val s = sel; val cs = selChest
                        // onarım: tezgâhta ya da Tamirci Viktor'un yanında (bedel eksik canla orantılı)
                        if (item.optInt("dur", 100) < 100 && (s != null || selEq != null)) {
                            val near = inv.optBoolean("can_repair")
                            PxButton(if (near) "Onar · ${item.optInt("repair")} Altın" else "Onar (Tezgâh/Viktor)", Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                color = Color(0xFF8A6A3A), height = 34.dp, size = 12.sp, enabled = near, icon = "hammer") {
                                val r = if (s != null) cmd("repair", arrayOf("slot" to s)) else cmd("repair", arrayOf("eq" to selEq!!))
                                Toaster.show(r.msg, r.ok); refresh()
                            }
                        }
                        if (s != null) {
                            val equippable = kind in EQUIP_KINDS
                            val usable = kind == "Consumable" || item.optString("id") == "fuel"
                            val placeable = kind == "Chest" && inGame
                            val key = item.optString("id").startsWith("veh_") && inGame
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // V15 (R8b): tezgâh → inşa modu (yeri parmakla seç, ✓ ile kur)
                                if (kind == "Bench" && onBuild != null) PxButton("Kur", Modifier.fillMaxWidth().tutRect(tutR, "act"), icon = "hammer", height = 38.dp, size = 14.sp) {
                                    sel = null; onBuild(s)
                                }
                                if (equippable || usable || placeable || key) PxButton(when { equippable -> "Kuşan"; placeable -> "Üsse Kur"; key -> "Aracı Çağır"; else -> "Kullan" },
                                    Modifier.fillMaxWidth().tutRect(tutR, "act"), height = 38.dp, size = 14.sp) {
                                    val r = cmd("use", arrayOf("slot" to s)); Toaster.show(r.msg, r.ok); sel = null; refresh()
                                    // V16: balta/kazma kuşanılınca elde tutmanın faydası bir kez hatırlatılır
                                    if (equippable && r.ok && isTool(item.optString("id"))) Toaster.show("Balta ve kazma yalnız elindeyken ağaca ve kayaya güçlü vurur.")
                                }
                                if (usable && s >= pockets) PxButton("Cebe Koy", Modifier.fillMaxWidth().tutRect(tutR, "act"), color = Px.Blue, height = 32.dp, size = 12.sp) {
                                    val free = (0 until pockets).firstOrNull { slots!!.isNull(it) } ?: 0
                                    val r = cmd("move", arrayOf("from" to s, "to" to free)); if (!r.ok) Toaster.show(r.msg, false); sel = null; refresh()
                                }
                                if (chestOn) PxButton("Sandığa Koy", Modifier.fillMaxWidth(), color = Color(0xFF8A6A3A), height = 32.dp, size = 12.sp) {
                                    if (count > 1) ask = AmountAsk("put", s, count, count, "Sandığa Koy")
                                    else { done(cmd("chest_put", arrayOf("chest" to ci, "slot" to s))); sel = null }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (count > 1) PxButton("Böl", Modifier.weight(1f), color = Px.Dark, height = 32.dp, size = 12.sp) {
                                        ask = AmountAsk("split", s, count - 1, count / 2, "Yığını Böl")
                                    }
                                    if (inGame) PxButton("Yere Bırak", Modifier.weight(1f), color = Px.Red, height = 32.dp, size = 11.sp) {
                                        if (count > 1) ask = AmountAsk("drop", s, count, count, "Yere Bırak")
                                        else { done(cmd("drop", arrayOf("slot" to s))); sel = null }
                                    }
                                }
                            }
                        } else if (cs != null) {
                            PxButton("Çantaya Al", Modifier.fillMaxWidth(), color = Px.Green, height = 38.dp, size = 14.sp) {
                                if (count > 1) ask = AmountAsk("take", cs, count, count, "Çantaya Al")
                                else { done(cmd("chest_take", arrayOf("chest" to ci, "cslot" to cs))); selChest = null }
                            }
                        } else if (selEq != null) {
                            PxButton("Çıkar", Modifier.fillMaxWidth(), color = Px.Dark, height = 38.dp, size = 14.sp) {
                                val r = cmd("unequip", arrayOf("slot" to selEq!!)); Toaster.show(r.msg, r.ok); selEq = null; refresh()
                            }
                        }
                    }
                }
            }
        }
        TutorialBagLayer(tstep, slots, pockets, n, sel, drag.item != null, tutR, drag.rects, inv, onCraft != null)
        // sürüklenen eşyanın hayaleti
        val it = drag.item
        if (it != null) {
            val half = with(LocalDensity.current) { 30.dp.toPx() }
            PxSlot(it, 60.dp, glow = true, modifier = Modifier.offset { IntOffset((drag.pos.x - rootPos.x - half).roundToInt(), (drag.pos.y - rootPos.y - half * 1.6f).roundToInt()) }.scale(1.1f))
        }
        // miktar seçimi
        ask?.let { a ->
            AmountDialog(a, close = { ask = null }) { k ->
                val r = when (a.action) {
                    "drop" -> cmd("drop", arrayOf("slot" to a.slot, "n" to k))
                    "split" -> cmd("split", arrayOf("slot" to a.slot, "n" to k))
                    "put" -> cmd("chest_put", arrayOf("chest" to ci, "slot" to a.slot, "n" to k))
                    else -> cmd("chest_take", arrayOf("chest" to ci, "cslot" to a.slot, "n" to k))
                }
                ask = null; sel = null; selChest = null
                done(r)
            }
        }
    }
}

/** Kenardaki bölme sekmesi: açık bölmede doluluk, kapalıda kilit + gereken çanta. */
@Composable
private fun BagTab(p: Int, o: JSONObject, selected: Boolean, glow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val open = o.optBoolean("open")
    Column(
        modifier.size(52.dp, 58.dp).clip(RoundedCornerShape(3.dp))
            .background(if (selected) Color(0xE04A3E22) else if (open) Color(0xD02B2B26) else Color(0xB0181815))
            .border(2.dp, if (glow) Px.Clan else if (selected) Px.Clan.copy(alpha = 0.7f) else Px.Edge, RoundedCornerShape(3.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // bölme sekmesi: 4 çanta resmi (bez kese → büyük sefer çantası); kilitli bölmede soluk resim + kilit
        Box(contentAlignment = Alignment.Center) {
            ArtImage("bagtab_$p", Modifier.size(32.dp).alpha(if (open) 1f else 0.3f))
            if (!open) Icon("lock", 16.dp, Px.Sub)
        }
        PT(if (open) "${p + 1}" else "Kilitli", if (open) 12.sp else 9.sp, if (open) Px.Text else Px.Sub)
        if (open) PT("${o.optInt("used")}/${o.optInt("len")}", 9.sp, Px.Sub)
    }
}

/** Miktar penceresi: −/+ adım, kaydırıcı, hızlı seçimler (1, yarısı, tümü). */
@Composable
internal fun AmountDialog(a: AmountAsk, close: () -> Unit, ok: (Int) -> Unit) {
    var k by remember(a) { mutableIntStateOf(a.start.coerceIn(1, a.max.coerceAtLeast(1))) }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { close() }, contentAlignment = Alignment.Center) {
        PxPanel(Modifier.width(360.dp).clickable(remember { MutableInteractionSource() }, null) {}, color = Color(0xF0201F1C), pad = 16.dp) {
            PT(a.title, 18.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                PxIcon("minus", size = 42.dp) { if (k > 1) k-- }
                Spacer(Modifier.width(14.dp))
                PT("$k", 28.sp, Px.Text, outline = true)
                Spacer(Modifier.width(14.dp))
                PxIcon("plus", size = 42.dp) { if (k < a.max) k++ }
            }
            if (a.max > 1) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Slider(
                    value = k.toFloat(), onValueChange = { k = it.roundToInt().coerceIn(1, a.max) },
                    valueRange = 1f..a.max.toFloat(), steps = (a.max - 2).coerceIn(0, 60),
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Px.Clan, activeTrackColor = Px.Orange, inactiveTrackColor = Px.Dark),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, v) in listOf("1" to 1, "Yarısı" to (a.max / 2).coerceAtLeast(1), "Tümü" to a.max))
                    PxButton(label, Modifier.weight(1f), color = if (k == v) Px.Blue else Px.Dark, height = 34.dp, size = 13.sp) { k = v }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PxButton("Vazgeç", Modifier.weight(1f), color = Px.Dark, height = 40.dp, size = 14.sp) { close() }
                PxButton("Onayla", Modifier.weight(1f), height = 40.dp, size = 14.sp) { ok(k) }
            }
        }
    }
}

@Composable
private fun EqBox(eq: JSONObject, key: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ghost = when (key) { "weapon" -> null; "necklace" -> "slot_neck"; else -> "slot_$key" }
    PxSlot(eq.optJSONObject(key), 52.dp, selected, hint = label, ghost = ghost,
        modifier = modifier.clickable(remember { MutableInteractionSource() }, null) { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); onClick() })
}

@Composable
private fun PxStat(icon: String, v: String, col: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, 14.dp, col); Spacer(Modifier.width(3.dp)); PT(v, 12.sp)
    }
}

private val RARITY_NAMES = listOf("Sıradan", "Nadir", "Destansı", "Efsanevi", "Sezonluk")
private val KIND_NAMES = mapOf("Material" to "Malzeme", "Consumable" to "Tüketilebilir", "Ammo" to "Mermi", "Weapon" to "Silah", "Helmet" to "Kask",
    "Jacket" to "Ceket", "Boots" to "Bot", "Bag" to "Çanta", "Cloak" to "Pelerin", "Pants" to "Pantolon", "Chest" to "Sandık", "Currency" to "Para", "Ring" to "Yüzük", "Necklace" to "Kolye",
    "Tool" to "Alet")

/** balta ve kazma: çantada durunca da çalışan aletler */
private fun isTool(id: String) = id.startsWith("t_axe_") || id.startsWith("t_pick_")

@Composable
fun PxItemDetail(item: JSONObject) {
    val r = item.optInt("rarity")
    Row(verticalAlignment = Alignment.CenterVertically) {
        PxSlot(item, 64.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            PT(item.optString("name"), 15.sp, Pal.rarity(r), maxLines = 2)
            PT("${RARITY_NAMES.getOrElse(r) { "" }} · ${KIND_NAMES[item.optString("kind")] ?: ""}", 11.sp, Px.Sub)
        }
    }
    Spacer(Modifier.height(8.dp))
    if (item.has("dur")) {
        val d = item.optInt("dur", 100)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PT(if (d == 0) "Kırık · Onarılmalı" else "Dayanıklılık %$d", 13.sp, durColor(d))
        }
        PxBar(d / 100f, durColor(d), Modifier.fillMaxWidth(), 6.dp)
        Spacer(Modifier.height(6.dp))
    }
    PT(item.optString("desc"), 12.sp, Px.Text, maxLines = 6)
    if (isTool(item.optString("id"))) PT("Alet yalnız elindeyken işe yarar; çantada dururken ağaca ve kayaya yumruk gücüyle vurursun.", 11.sp, Px.Sub, maxLines = 3)
    Spacer(Modifier.height(4.dp))
    PT("Değer: ${item.optInt("value")} Altın", 11.sp, Px.Clan)
}

/** Dayanıklılık rengi: %50 üstü yeşil, %20-50 sarı, altı kırmızı */
fun durColor(d: Int): Color = when {
    d > 50 -> Color(0xFF6AD86A)
    d > 20 -> Color(0xFFFFC840)
    else -> Color(0xFFFF4A3A)
}

/** Oyun içi cep tuşu (HUD): eşya + bekleme perdesi. */
@Composable
fun PocketButton(item: JSONObject?, index: Int, onClick: () -> Unit) {
    PxSlot(item, 56.dp, hint = "${index + 1}",
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onClick() })
}


private fun eqKeyFor(kind: String): Int? {
    val k = when (kind) { "Weapon" -> "weapon"; "Helmet" -> "helmet"; "Jacket" -> "jacket"; "Boots" -> "boots"; "Bag" -> "bag"; "Ring" -> "ring"; "Necklace" -> "necklace"; else -> return null }
    val l = EQ_LEFT.indexOfFirst { it.first == k }
    if (l >= 0) return EQ + l
    val r = EQ_RIGHT.indexOfFirst { it.first == k }
    return if (r >= 0) EQ + 4 + r else null
}

/** Çantadaki öğretici: hangi eşyaya dokunulacak, hangi düğmeye basılacak, eşya nereye oturacak; her aşama ayrı gösterilir */
@Composable
private fun TutorialBagLayer(
    step: String, slots: org.json.JSONArray?, pockets: Int, n: Int, sel: Int?, dragging: Boolean,
    tut: Map<String, Rect>, rects: Map<Int, Rect>, inv: JSONObject, hasCraft: Boolean,
) {
    if (step.isEmpty()) return
    val tg = ArrayList<TutTarget>()
    var text: String? = null
    when (step) {
        "g4", "g4b", "g4d", "g4f" -> if (hasCraft) {
            tg += TutTarget(tut["craft"], "Üret'e Bas", below = true)
            text = when (step) {
                "g4" -> "Üst şeritteki parlayan Üret düğmesine bas. Açılan ekranda Şifa sekmesinden Bandaj'ı seçip Üret'e basacaksın."
                "g4b" -> "Üret düğmesine bas; Silah Ve Alet sekmesinden Taş Balta'yı seç ve Üret'e bas. Malzemeyi sana ben verdim."
                "g4d" -> "Üret düğmesine bas; Silah Ve Alet sekmesinden Taş Kazma'yı seç ve Üret'e bas."
                else -> "Üret düğmesine bas; Silah Ve Alet sekmesinden Bıçak'ı seç ve Üret'e bas."
            }
        }
        "g4c", "g4g", "g4h", "g4i", "g5", "g5b" -> {
            val from = if (step == "g5" || step == "g5b") pockets else 0
            val idx = (from until n).firstOrNull { i ->
                val o = slots?.optJSONObject(i) ?: return@firstOrNull false
                val id = o.optString("id"); val kind = o.optString("kind")
                when (step) {
                    "g4c" -> id.startsWith("t_axe_")
                    "g4g" -> id == "w_knife"
                    "g4h" -> id == "helm_basic" || id == "jacket_basic"
                    "g4i" -> kind == "Bench"
                    "g5" -> kind == "Consumable"
                    else -> id == "bandage"
                }
            }
            if (idx == null) {
                // g5b: bandaj zaten cepte; çantayı kapatıp cebe dokunması gerekir
                if (step == "g5b") {
                    tg += TutTarget(tut["back"], "Çantayı Kapat", below = true)
                    text = "Bandaj cebinde. Çantayı kapat ve ekranın altındaki parlayan cebe dokun: kullanılır, bekleme süresine girer."
                }
            } else {
                val item = slots?.optJSONObject(idx)
                val picked = sel == idx
                val pocketFree = (0 until pockets).firstOrNull { slots?.isNull(it) != false } ?: 0
                val pocket = rects[pocketFree]
                val wear = step == "g4c" || step == "g4g" || step == "g4h"
                when {
                    dragging -> if (step == "g5" || step == "g5b") {
                        tg += TutTarget(pocket, "Burada Bırak")
                        text = "Parmağını kaldırınca eşya cebe girer. Cep boşsa parlayan yuvaya bırak."
                    }
                    !picked -> {
                        tg += TutTarget(rects[idx], if (step == "g5" || step == "g5b") "Basılı Tut" else "Dokun")
                        if (step == "g5" || step == "g5b") tg += TutTarget(pocket, "Buraya Sürükle")
                        text = when (step) {
                            "g4c" -> "Parlayan Taş Balta'ya dokun: seçilince sağ panelde bilgisi ve Kuşan düğmesi çıkar."
                            "g4g" -> "Parlayan Bıçağa dokun: seçilince sağ panelde bilgisi ve Kuşan düğmesi çıkar."
                            "g4h" -> "Parlayan kask ya da ceketi seç. İkisini de giyeceksin, sırayla dokun."
                            "g4i" -> "Parlayan Tezgâh'a dokun: sağ panelde Kur düğmesi çıkar."
                            else -> "Parlayan eşyaya parmağını yarım saniye basılı tut, sonra soldaki parlayan cebe sürükleyip bırak. Dokunup Cebe Koy'u seçmek de olur."
                        }
                    }
                    else -> {
                        val kind = item?.optString("kind").orEmpty()
                        when {
                            wear -> {
                                tg += TutTarget(tut["act"], "Kuşan'a Bas")
                                eqKeyFor(kind)?.let { tg += TutTarget(rects[it], "Buraya Oturur") }
                                text = "Kuşan'a bas: eşya parlayan ekipman yuvasına oturur. Çıkarmak istersen yuvadan dışarı sürüklersin."
                            }
                            step == "g4i" -> {
                                tg += TutTarget(tut["act"], "Kur'a Bas")
                                text = "Kur'a bas, sonra yerde yeşil çıkan bir noktaya dokunup ✓ ile tezgâhı yerleştir."
                            }
                            else -> {
                                tg += TutTarget(tut["act"], "Cebe Koy'a Bas")
                                tg += TutTarget(pocket, "Buraya Girer")
                                text = "Cebe Koy'a bas: eşya parlayan cebe girer. Sürükleyerek de koyabilirsin."
                            }
                        }
                    }
                }
            }
        }
    }
    TutLayer(tg, text)
}
