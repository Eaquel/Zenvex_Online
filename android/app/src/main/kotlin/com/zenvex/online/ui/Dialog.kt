package com.zenvex.online.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Audio
import com.zenvex.online.game.GameSession
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sin

/** NPC portre görseli (atlas npc/<ad>) */
fun npcSprite(kind: Int) = when (kind) {
    1 -> "npc/merchant"; 2 -> "npc/quest"; 3 -> "npc/doctor"; 4 -> "npc/mechanic"; 5 -> "npc/trainer"; 7 -> "npc/quest"; else -> "npc/soldier"
}

/** Oyun içinde NPC'ye dokununca: konuşma (Rust talk komutu). */
@Composable
fun NpcDialogScreen(kind: Int, close: () -> Unit, open: (String) -> Unit) {
    var data by remember { mutableStateOf(talk(kind, "")) }
    if (data == null) { LaunchedEffect(Unit) { close() }; return }
    NpcDialogView(data!!, onOption = { id ->
        // "bye" ve "ok" (kabul sonrası Yola Çık, teslim sonrası Tamam) konuşmayı kapatır
        if (id == "bye" || id == "ok") { close(); return@NpcDialogView }
        val next = talk(kind, id)
        if (next == null) { close(); return@NpcDialogView }
        val o = next.optString("open")
        if (o.isNotEmpty()) open(o) else data = next
    }, onClose = close)
}

private fun talk(kind: Int, opt: String): JSONObject? {
    val r = GameSession.cmd("talk", "npc" to kind, "opt" to opt)
    if (!r.ok) { Toaster.show(r.msg, false); return null }
    return runCatching { JSONObject(r.msg) }.getOrNull()
}

/**
 * Konuşma paneli: solda portre (nefes alır), ad plakası, daktilo gibi akan satırlar; dokununca sonraki satır,
 * son satırda seçenek tuşları.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpcDialogView(d: JSONObject, onOption: (String) -> Unit, onClose: () -> Unit, instant: Boolean = false) {
    val lines = d.optJSONArray("lines") ?: JSONArray()
    val opts = d.optJSONArray("opts") ?: JSONArray()
    var line by remember(d) { mutableIntStateOf(0) }
    val text = if (lines.length() > 0) lines.optString(line.coerceAtMost(lines.length() - 1)) else ""
    var shown by remember(d, line) { mutableIntStateOf(if (instant) text.length else 0) }
    LaunchedEffect(d, line) {
        while (shown < text.length) {
            delay(18)
            shown++
            if (shown % 3 == 0) Audio.play("click", 0.18f, 1.6f + (shown % 5) * 0.05f, Audio.Bus.Ui)
        }
    }
    val last = line >= lines.length() - 1
    val t = rememberTime()
    val tap = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA0000000))))
            .clickable(tap, null) {
                // dokun: yazıyı tamamla → sonraki satır → son satırda boşluğa dokunmak konuşmayı kapatır
                if (shown < text.length) shown = text.length
                else if (!last) line++
                else onClose()
            },
    ) {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
            // portre
            Box(Modifier.size(170.dp, 220.dp), contentAlignment = Alignment.BottomCenter) {
                SpriteImage(npcSprite(d.optInt("npc")), Modifier.fillMaxSize().offset(y = (sin(t * 2f) * 1.5f).dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                // ad plakası
                Row(Modifier.pxPanel(Color(0xF0302A1C)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    PT(d.optString("name"), 18.sp, Px.Clan)
                    Spacer(Modifier.width(8.dp))
                    PT(d.optString("role"), 13.sp, Px.Sub)
                }
                Spacer(Modifier.height(4.dp))
                Column(Modifier.fillMaxWidth().heightIn(min = 110.dp).pxPanel(Color(0xF0201F1C)).padding(14.dp)) {
                    PT(text.take(shown), 16.sp, Px.Text, maxLines = 5)
                    Spacer(Modifier.weight(1f, fill = false))
                    if (!last || shown < text.length) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            PT(if (lines.length() > 1) "${line + 1}/${lines.length()}  ▼" else "▼", 12.sp,
                                Px.Sub.copy(alpha = 0.6f + 0.4f * sin(t * 5f)))
                        }
                    }
                }
                AnimatedVisibility(last && shown >= text.length, enter = fadeIn() + slideInVertically { it / 2 }) {
                    // seçenekler sarılarak dizilir (konu menüsünde 6 tuşa kadar)
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until opts.length()) {
                            val o = opts.getJSONObject(i)
                            val id = o.optString("id")
                            val col = when {
                                id == "accept" || id == "ok" -> Px.Green
                                id == "turnin" || id == "more" -> Color(0xFFB08A2A)
                                id == "bye" || id == "back" -> Px.Dark
                                id == "chat" || id.startsWith("t:") -> Color(0xFF4A4A40)
                                else -> Px.Blue
                            }
                            PxButton(o.optString("label"), height = 40.dp, size = 14.sp, color = col) { onOption(id) }
                        }
                    }
                }
            }
        }
        PxIcon("close", Modifier.align(Alignment.TopEnd).padding(14.dp), size = 40.dp) { onClose() }
    }
}

/** Sol üst görev takipçisi: rehber ve görevci görevleri; tamamlanınca teslim uyarısı. */
@Composable
fun QuestTracker(story: JSONArray, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = rememberTime()
    Column(modifier.widthIn(max = 260.dp).clickable(remember { MutableInteractionSource() }, null) { onClick() },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until story.length()) {
            val q = story.getJSONObject(i)
            val on = q.optBoolean("on"); val ready = q.optBoolean("ready")
            if (!on && !q.has("x")) continue
            Column(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xB0151410))
                .border(1.dp, if (ready) Px.Clan.copy(alpha = 0.5f + 0.5f * sin(t * 4f)) else Color(0x40FFFFFF), RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(if (q.optBoolean("guide")) Px.Blue else Color(0xFFB08A2A)), contentAlignment = Alignment.Center) {
                        PT(if (ready) "?" else "!", 12.sp, Color.White)
                    }
                    Spacer(Modifier.width(6.dp))
                    PT(q.optString("title"), 14.sp, if (ready) Px.Clan else Px.Text)
                }
                val hint = when {
                    ready -> "Teslim Et: ${q.optString("giver")}"
                    !on -> "${q.optString("giver")} Seni Bekliyor"
                    else -> "${q.optString("hint")}  ${q.optInt("n")}/${q.optInt("goal")}"
                }
                PT(hint, 12.sp, if (ready) Px.Name else Px.Sub, maxLines = 2)
            }
        }
    }
}

/** Öğretici işaretçisi (Compose): hedefin üstünde küçük zıplayan ok başı + yarı saydam etiket. Hedefin çevresine [TutPulse]. */
@Composable
fun TutArrow(label: String, modifier: Modifier = Modifier) {
    val t = rememberTime()
    Column(modifier.offset(y = (-58 + sin(t * 4f) * 3).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xB0101010)).padding(horizontal = 9.dp, vertical = 2.dp)) {
            PT(label, 12.sp, Color(0xFFFFF0C0))
        }
        androidx.compose.foundation.Canvas(Modifier.size(16.dp, 12.dp)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width / 2, size.height); lineTo(0f, 0f); lineTo(size.width / 2, size.height * 0.4f); lineTo(size.width, 0f); close()
            }
            drawPath(p, Color(0xFFFFD34A).copy(alpha = 0.75f + 0.25f * sin(t * 4f)))
            drawPath(p, Color(0xB015140F), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
    }
}

/** Hedefin çevresinde sönerek genişleyen yumuşak halka (dokunulacak tuşu belli eder) */
@Composable
fun TutPulse(modifier: Modifier = Modifier) {
    val t = rememberTime()
    androidx.compose.foundation.Canvas(modifier) {
        for (k in 0..1) {
            val ph = (t * 1.1f + k * 0.5f) % 1f
            drawCircle(Color(0xFFFFE6A0).copy(alpha = 0.55f * (1f - ph)), radius = size.minDimension / 2 * (0.9f + 0.35f * ph),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * density))
        }
    }
}
