package com.zenvex.online.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Audio
import com.zenvex.online.game.HudLayout
import com.zenvex.online.game.Settings
import kotlin.math.sin

/** Rehberdeki bir madde: numara, başlık, açıklama ve işaretlenecek denetimin merkezi/yarıçapı (ekran pikseli; yoksa işaret çizilmez) */
private class GuideItem(val title: String, val text: String, val cx: Float, val cy: Float, val r: Float)

/**
 * İlk girişte (ya da Ayarlar'dan) gösterilen kontrol rehberi: denetimlerin üstünde numaralı nabız halkaları, ortada her numarayı
 * anlatan iki sütunlu panel. HUD'un üstünde çizilir; dokununca ya da "Anladım" ile kapanır ve bir daha kendiliğinden açılmaz.
 */
@Composable
fun BoxScope.ControlsGuide() {
    // zaman okuması her karede yeniden kurar: GameView'ın yazdığı bayrak (HudLayout.guideWanted) böylece izlenir
    val t = rememberTime()
    val ctx = LocalContext.current
    if (!HudLayout.guideWanted) return
    val tg = HudLayout.guideTargets; val surf = HudLayout.guideSurface
    val bag = HudLayout.bagBtn; val row = HudLayout.pocketRow
    val items = remember(tg.contentHashCode(), bag.contentHashCode(), row.contentHashCode()) {
        // GameView yüzey pikselini gerçek ekran pikseline çevirmek için Canvas boyutu gerekir: oran çizimde uygulanır (surf)
        val l = ArrayList<GuideItem>()
        l += GuideItem("Hareket", "Parmağını kaydır; sonuna kadar itersen koşarsın.", tg[0], tg[1], tg[2])
        l += GuideItem("Saldırı", "Çemberi düşmana doğru çevir; o yöne vurursun.", tg[3], tg[4], tg[5])
        l += GuideItem("Takla", "Baktığın yöne yuvarlanırsın; o an hasar almazsın.", tg[6], tg[7], tg[8])
        l += GuideItem("Çanta", "Eşyaların, üretim ve silahların burada.", -1f, -1f, 0f)
        l += GuideItem("Cepler", "Hızlı kullanım: iksir ve yiyecek; dokununca kullanılır.", -1f, -1f, 0f)
        l += GuideItem("Can ve Güç", "Kırmızı can, sarı dayanıklılık (koşmak ve vurmak yer), mavi tecrübe.", -1f, -1f, 0f)
        l += GuideItem("Harita", "Kırmızı noktalar zombi, turuncu ünlem görev; dokununca büyür.", tg[9], tg[10], tg[11])
        l
    }
    val done = {
        HudLayout.guideReplay = false
        Audio.play("click", 0.8f, bus = Audio.Bus.Ui)
        Settings.save(ctx, Settings.current.copy(controlsGuide = false))
    }
    Box(Modifier.fillMaxSize().background(Color(0xB0000000)).clickable(remember { MutableInteractionSource() }, null) { done() }) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / surf[0].coerceAtLeast(1f)
            fun badge(n: Int, c: Offset, r: Float) {
                val ph = (t * 1.1f) % 1f
                drawCircle(Color(0xFFFFD34A).copy(alpha = 0.9f * (1f - ph)), r * (0.9f + 0.4f * ph), c, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                drawCircle(Color(0xFFFFD34A).copy(alpha = 0.85f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f.dp.toPx()))
                drawCircle(Color(0xFF15140F), 12.dp.toPx(), c)
                drawCircle(Color(0xFFFFD34A), 10.dp.toPx(), c)
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF15140F.toInt(); textSize = 14.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText(n.toString(), c.x, c.y + 5.dp.toPx(), p)
                }
            }
            for ((i, it) in items.withIndex()) {
                val c = when (it.title) {
                    "Çanta" -> if (bag[2] > bag[0]) Offset((bag[0] + bag[2]) / 2, (bag[1] + bag[3]) / 2) else null
                    "Cepler" -> if (row[2] > row[0] && bag[0] > row[0]) Offset((row[0] + bag[0]) / 2, (row[1] + row[3]) / 2) else null
                    "Can ve Güç" -> Offset(150.dp.toPx(), 34.dp.toPx())
                    else -> if (it.cx >= 0f) Offset(it.cx * k, it.cy * k) else null
                } ?: continue
                val r = when (it.title) {
                    "Çanta" -> (bag[2] - bag[0]) / 2 + 3.dp.toPx()
                    "Cepler" -> (bag[0] - row[0]) / 2 + 2.dp.toPx()
                    "Can ve Güç" -> 26.dp.toPx()
                    else -> it.r * k + 3.dp.toPx()
                }
                badge(i + 1, c, r)
            }
        }
        Column(Modifier.align(Alignment.Center).widthIn(max = 640.dp).fillMaxWidth(0.66f).clip(RoundedCornerShape(10.dp)).background(Color(0xF0141310))
            .padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PT("Kontroller", 17.sp, Color(0xFFFFD34A), outline = true)
            Spacer(Modifier.height(6.dp))
            val half = (items.size + 1) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (col in 0..1) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in col * half until minOf(items.size, (col + 1) * half)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFFD34A)), contentAlignment = Alignment.Center) { PT("${i + 1}", 13.sp, Color(0xFF15140F)) }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                PT(items[i].title, 14.sp, Color(0xFFFFE6A0))
                                PT(items[i].text, 12.sp, Color(0xFFEDE8DA), maxLines = 3)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PxButton("Anladım", Modifier.widthIn(min = 140.dp), color = Px.Green, height = 34.dp, size = 14.sp) { done() }
        }
    }
}
