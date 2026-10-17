package online.zenvex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import online.zenvex.game.GameSession
import online.zenvex.game.GameView
import online.zenvex.game.Native

private enum class Overlay { None, Pause, Settings, Bag, Dead }

@Composable
fun GameScreen(toLobby: () -> Unit) {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Overlay.None) }
    var levelUps by remember { mutableStateOf(0) }
    val view = remember {
        GameView(ctx, onDeath = { overlay = Overlay.Dead }, onLevelUp = { levelUps++ })
    }
    view.paused = overlay != Overlay.None
    DisposableEffect(Unit) { onDispose { GameSession.save() } }
    BackHandler { overlay = if (overlay == Overlay.None) Overlay.Pause else if (overlay == Overlay.Dead) Overlay.Dead else Overlay.None }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ view }, Modifier.fillMaxSize())
        if (overlay == Overlay.None) {
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("", icon = "bag", badge = 0) { overlay = Overlay.Bag }
                PixelButton("", icon = "scroll", locked = true) {}
                PixelButton("", icon = "pause") { overlay = Overlay.Pause }
            }
        }
        when (overlay) {
            Overlay.Pause -> Dim {
                PixelPanel(Modifier.width(300.dp)) {
                    PText("DURAKLATILDI", 20.sp, Pal.Cream, Modifier.align(Alignment.CenterHorizontally), bold = true)
                    Spacer(Modifier.height(12.dp))
                    PixelButton("Devam Et", Modifier.width(276.dp), BtnStyle.Primary, icon = "play") { overlay = Overlay.None }
                    Spacer(Modifier.height(8.dp))
                    PixelButton("Ayarlar", Modifier.width(276.dp), icon = "gear") { overlay = Overlay.Settings }
                    Spacer(Modifier.height(8.dp))
                    PixelButton("Lobiye Dön", Modifier.width(276.dp), BtnStyle.Danger, icon = "back") {
                        GameSession.save(); toLobby()
                    }
                }
            }
            Overlay.Settings -> SettingsScreen(back = { view.onSettingsChanged(); overlay = Overlay.Pause }, inGame = true)
            Overlay.Bag -> Dim {
                val hud = remember { GameSession.hud() }
                PixelPanel(Modifier.width(360.dp)) {
                    PText("Çanta", 20.sp, Pal.Cream, bold = true)
                    Spacer(Modifier.height(10.dp))
                    StatRow("Odun", "${hud[Native.HUD_WOOD].toInt()}")
                    StatRow("Zombi Dişi", "${hud[Native.HUD_TEETH].toInt()}")
                    StatRow("Kesilen ağaç", "${hud[Native.HUD_TREES].toInt()}")
                    StatRow("Öldürülen zombi", "${hud[Native.HUD_KILLS].toInt()}")
                    Spacer(Modifier.height(10.dp))
                    PixelButton("Kapat", Modifier.width(336.dp), BtnStyle.Primary) { overlay = Overlay.None }
                }
            }
            Overlay.Dead -> Box(Modifier.fillMaxSize().background(Color(0xAA3A0A14)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PText("ÖLDÜN", 56.sp, Pal.Red, bold = true)
                    PText("Zombiler bu sefer kazandı... ama XP'n duruyor.", 14.sp, Pal.Cream)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelButton("Yeniden Doğ", style = BtnStyle.Primary, icon = "heart") {
                            GameSession.respawn(); overlay = Overlay.None
                        }
                        PixelButton("Lobiye Dön", icon = "back") { GameSession.respawn(); GameSession.save(); toLobby() }
                    }
                }
            }
            Overlay.None -> {}
        }
    }
}

@Composable
private fun Dim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Pal.Night.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { content() }
}
