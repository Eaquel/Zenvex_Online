package com.zenvex.online.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Lobideki oynanış penceresi: gerçek oyun motorundan alınmış kareler (assets/ui/shot_*), birkaç saniyede bir geçer. */
private val SHOTS = listOf(
    "shot_scythe" to "Tırpan Kombosu",
    "shot_rpg" to "Roketatar",
    "shot_moto" to "Araçlar",
    "shot_magma" to "Titan",
    "shot_ice" to "Donmuş Liman",
    "shot_rune" to "Rün Mührü",
)

@Composable
fun ShotGallery(modifier: Modifier = Modifier, start: Int = 0) {
    val ctx = LocalContext.current
    val shots = remember { SHOTS.filter { UiArt.has(ctx, it.first) } }
    if (shots.isEmpty()) return
    var i by remember { mutableIntStateOf(start % shots.size) }
    LaunchedEffect(shots.size) { while (true) { delay(4200); i = (i + 1) % shots.size } }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.size(208.dp, 96.dp).clip(shape).background(Color(0xFF0A0A0E)).border(1.dp, Color(0x88FFB070), shape)) {
        Crossfade(i, Modifier.fillMaxSize(), animationSpec = tween(700), label = "shot") { k ->
            val img = remember(k) { UiArt.image(ctx, shots[k].first) }
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomStart).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
        PT(shots[i].second, 13.sp, Color(0xFFFFE2B8), Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 5.dp), outline = true)
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (k in shots.indices) Box(Modifier.size(5.dp).clip(CircleShape).background(if (k == i) Color(0xFFFFB070) else Color(0x66FFFFFF)))
        }
    }
}
