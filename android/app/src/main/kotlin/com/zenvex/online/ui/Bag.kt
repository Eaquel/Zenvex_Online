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
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

// =========================================================================== piksel ekran çerçevesi
/** Piksel tam ekran: koyu zemin, perçinli başlık şeridi, geri tuşu. */
@Composable
fun PxFrame(title: String, icon: String, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF14130F))) {
        // ince ızgara deseni (piksel zemin)
        Canvas(Modifier.fillMaxSize()) {
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
fun PxSlot(item: JSONObject?, size: Dp, selected: Boolean = false, hint: String? = null, glow: Boolean = false, modifier: Modifier = Modifier) {
    val r = item?.optInt("rarity") ?: 0
    val col = Pal.rarity(r)
    Box(
        modifier.size(size).clip(RoundedCornerShape(3.dp))
            .background(if (item == null) Color(0xFF1C1B17) else Color(0xFF2A2924))
            .border(2.dp, if (selected) Color.White else if (glow) Px.Clan else Px.Edge, RoundedCornerShape(3.dp))
            .padding(2.dp)
            .border(1.dp, if (item == null) Color(0x14FFFFFF) else col.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            SpriteImage("i/${item.optString("id")}", Modifier.padding(size * 0.12f).fillMaxSize())
            val cd = item.optDouble("cd", 0.0).toFloat()
            val max = item.optDouble("cdmax", 0.0).toFloat()
            if (cd > 0f && max > 0f) {
                val k = (cd / max).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xB0000000), Offset.Zero, Size(this.size.width, this.size.height * k)) }
                PT("${cd.toInt() + 1}", (size.value * 0.3f).sp, Color.White, outline = true)
            }
            val n = item.optInt("count")
            if (n > 1) PT("$n", (size.value * 0.22f).sp, Color.White, Modifier.align(Alignment.BottomEnd).padding(end = 3.dp), outline = true)
        } else if (hint != null) PT(hint, (size.value * 0.2f).sp, Color(0x55FFFFFF))
    }
}

// =========================================================================== çanta (sürükle-bırak + cepler)
private class DragState {
    var from by mutableStateOf(-1)
    var item by mutableStateOf<JSONObject?>(null)
    var pos by mutableStateOf(Offset.Zero)
    val rects = HashMap<Int, Rect>()
    fun target(): Int? = rects.entries.firstOrNull { it.value.contains(pos) }?.key
}

@Composable
fun InventoryScreen(back: () -> Unit, inGame: Boolean = false, onCraft: (() -> Unit)? = null) {
    var tick by remember { mutableIntStateOf(0) }
    val inv = remember(tick) { GameSession.ui("inventory") }
    val hero = remember(tick / 20) { GameSession.ui("profile").optInt("character") }
    // bekleme süreleri akarken cepleri tazele
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    BagView(inv, hero, inGame, back, { name, kv -> GameSession.cmd(name, *kv) }, onCraft = onCraft) { tick++ }
}

/** Çanta görünümü (veri dışarıdan: ekran görüntüsü testinde örnek JSON ile çizilir). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BagView(
    inv: JSONObject, hero: Int, inGame: Boolean, back: () -> Unit,
    cmd: (String, Array<Pair<String, Any>>) -> GameSession.Reply, onCraft: (() -> Unit)? = null, refresh: () -> Unit,
) {
    var sel by remember { mutableStateOf<Int?>(null) }
    var selEq by remember { mutableStateOf<String?>(null) }
    val drag = remember { DragState() }
    val haptic = LocalHapticFeedback.current
    val slots = inv.optJSONArray("slots")
    val pockets = inv.optInt("pockets", 4)
    val n = slots?.length() ?: 0
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    val quiet = remember { MutableInteractionSource() }

    fun slotMod(i: Int, s: JSONObject?) = Modifier
        .onGloballyPositioned { drag.rects[i] = it.boundsInRoot() }
        .then(if (s == null) Modifier else Modifier.pointerInput(i, s.optString("id")) {
            detectDragGesturesAfterLongPress(
                onDragStart = { o ->
                    drag.from = i; drag.item = s
                    drag.pos = (drag.rects[i]?.topLeft ?: Offset.Zero) + o
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Audio.play("click", 0.7f, 0.8f, bus = Audio.Bus.Ui)
                },
                onDrag = { ch, d -> ch.consume(); drag.pos += d },
                onDragEnd = {
                    val to = drag.target()
                    if (to != null && to != drag.from) {
                        val r = cmd("move", arrayOf("from" to drag.from, "to" to to))
                        if (!r.ok && r.msg.isNotEmpty()) Toaster.show(r.msg, false) else Audio.play("pickup", 0.6f, 1.2f, bus = Audio.Bus.Ui)
                        sel = null
                    }
                    drag.from = -1; drag.item = null; refresh()
                },
                onDragCancel = { drag.from = -1; drag.item = null },
            )
        })
        .clickable(quiet, null) {
            if (s != null) { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); sel = i; selEq = null }
        }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }) {
        PxFrame("Çanta", "bag", back, right = {
            Icon("coin", 18.dp, Px.Clan); Spacer(Modifier.width(4.dp)); PT(fmt(inv.optInt("gold")), 16.sp, Px.Clan)
            Spacer(Modifier.width(12.dp))
            if (onCraft != null) {
                PxButton("Üret", height = 38.dp, size = 14.sp, color = Px.Blue, icon = "craft") { onCraft() }
                Spacer(Modifier.width(8.dp))
            }
            PxButton("Sırala", height = 38.dp, size = 14.sp, color = Px.Dark, icon = "refresh") { cmd("sort", emptyArray()); refresh() }
        }) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // ---- cepler: oyunda hızlı kullanım tuşları
                PxPanel(Modifier.width(92.dp).fillMaxHeight(), color = Px.PanelDark, pad = 8.dp) {
                    PT("Cepler", 14.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(6.dp))
                    for (i in 0 until pockets) {
                        val s = slots?.optJSONObject(i)
                        PxSlot(s, 66.dp, sel == i, hint = "${i + 1}", glow = drag.item != null && drag.target() == i,
                            modifier = slotMod(i, s).align(Alignment.CenterHorizontally).scale(if (drag.from == i) 0.9f else 1f))
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    PT("Basılı Tut\nSürükle", 10.sp, Px.Sub, Modifier.align(Alignment.CenterHorizontally), maxLines = 2)
                }
                // ---- ekipman
                val eq = inv.optJSONObject("eq") ?: JSONObject()
                PxPanel(Modifier.width(230.dp).fillMaxHeight(), pad = 8.dp) {
                    PT("Ekipman", 14.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((k, label) in listOf("weapon" to "Silah", "cloak" to "Pelerin")) EqBox(eq, k, label, selEq == k) { selEq = k; sel = null }
                        }
                        Box(Modifier.width(86.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            SpriteImage("l/${HERO_IDS.getOrElse(hero) { "kaan" }}/0_0", Modifier.fillMaxSize())
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((k, label) in listOf("helmet" to "Kask", "boots" to "Bot")) EqBox(eq, k, label, selEq == k) { selEq = k; sel = null }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PxStat("heart", "${inv.optDouble("max_hp").toInt()}", Px.Hard)
                        PxStat("sword", "×${"%.2f".format(inv.optDouble("dmg"))}", Px.Orange)
                        PxStat("bolt", "×${"%.2f".format(inv.optDouble("speed"))}", Px.GreenHi)
                    }
                }
                // ---- çanta ızgarası
                PxPanel(Modifier.weight(1f).fillMaxHeight(), pad = 8.dp) {
                    val used = (pockets until n).count { !slots!!.isNull(it) }
                    PT("Çanta  $used / ${inv.optInt("cap")}", 14.sp, Px.Clan)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in pockets until n) {
                            val s = slots!!.optJSONObject(i)
                            PxSlot(s, 58.dp, sel == i, glow = drag.item != null && drag.target() == i,
                                modifier = slotMod(i, s).scale(if (drag.from == i) 0.9f else 1f))
                        }
                    }
                }
                // ---- ayrıntı
                PxPanel(Modifier.width(230.dp).fillMaxHeight(), color = Px.PanelDark, pad = 10.dp) {
                    val item: JSONObject? = sel?.let { slots?.optJSONObject(it) } ?: selEq?.let { eq.optJSONObject(it) }
                    if (item == null) {
                        PT("Bir Eşya Seç", 14.sp, Px.Sub, Modifier.align(Alignment.CenterHorizontally).padding(top = 30.dp))
                        Spacer(Modifier.height(10.dp))
                        PT("İksir ve ilk yardımı basılı tutup sola, ceplere sürükle: oyunda tek dokunuşla kullanılır.", 12.sp, Px.Sub, maxLines = 5)
                    } else {
                        PxItemDetail(item)
                        Spacer(Modifier.weight(1f))
                        val s = sel
                        if (s != null) {
                            val kind = item.optString("kind")
                            val equippable = kind in listOf("Weapon", "Helmet", "Jacket", "Boots", "Bag", "Cloak")
                            val usable = kind == "Consumable" || item.optString("id") == "fuel"
                            if (equippable || usable) PxButton(if (equippable) "Kuşan" else "Kullan", Modifier.fillMaxWidth(), height = 42.dp, size = 16.sp) {
                                val r = cmd("use", arrayOf("slot" to s)); Toaster.show(r.msg, r.ok); sel = null; refresh()
                            }
                            if (usable && s >= pockets) {
                                Spacer(Modifier.height(6.dp))
                                PxButton("Cebe Koy", Modifier.fillMaxWidth(), color = Px.Blue, height = 38.dp, size = 14.sp) {
                                    val free = (0 until pockets).firstOrNull { slots!!.isNull(it) } ?: 0
                                    val r = cmd("move", arrayOf("from" to s, "to" to free)); if (!r.ok) Toaster.show(r.msg, false); sel = null; refresh()
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            if (inGame) PxButton("Yere Bırak", Modifier.fillMaxWidth(), color = Px.Red, icon = "trash", height = 38.dp, size = 14.sp) {
                                val r = cmd("drop", arrayOf("slot" to s)); Toaster.show(r.msg, r.ok); sel = null; refresh()
                            }
                        } else if (selEq != null) {
                            PxButton("Çıkar", Modifier.fillMaxWidth(), color = Px.Dark, height = 42.dp, size = 16.sp) {
                                val r = cmd("unequip", arrayOf("slot" to selEq!!)); Toaster.show(r.msg, r.ok); selEq = null; refresh()
                            }
                        }
                    }
                }
            }
        }
        // sürüklenen eşyanın hayaleti
        val it = drag.item
        if (it != null) {
            val half = with(LocalDensity.current) { 30.dp.toPx() }
            PxSlot(it, 60.dp, glow = true, modifier = Modifier.offset { IntOffset((drag.pos.x - rootPos.x - half).roundToInt(), (drag.pos.y - rootPos.y - half * 1.6f).roundToInt()) }.scale(1.1f))
        }
    }
}

@Composable
private fun EqBox(eq: JSONObject, key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PxSlot(eq.optJSONObject(key), 56.dp, selected, hint = label,
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { Audio.play("click", 0.6f, bus = Audio.Bus.Ui); onClick() })
    }
}

@Composable
private fun PxStat(icon: String, v: String, col: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, 15.dp, col); Spacer(Modifier.width(3.dp)); PT(v, 13.sp)
    }
}

private val RARITY_NAMES = listOf("Sıradan", "Nadir", "Destansı", "Efsanevi", "Sezonluk")
private val KIND_NAMES = mapOf("Material" to "Malzeme", "Consumable" to "Tüketilebilir", "Ammo" to "Mermi", "Weapon" to "Silah", "Helmet" to "Kask",
    "Jacket" to "Ceket", "Boots" to "Bot", "Bag" to "Çanta", "Cloak" to "Pelerin", "Currency" to "Para")

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
    PT(item.optString("desc"), 12.sp, Px.Text, maxLines = 6)
    Spacer(Modifier.height(4.dp))
    PT("Değer: ${item.optInt("value")} Altın", 11.sp, Px.Clan)
}

/** Oyun içi cep tuşu (HUD): eşya + bekleme perdesi. */
@Composable
fun PocketButton(item: JSONObject?, index: Int, onClick: () -> Unit) {
    PxSlot(item, 56.dp, hint = "${index + 1}",
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onClick() })
}
