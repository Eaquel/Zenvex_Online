package com.zenvex.online.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.GameUi
import com.zenvex.online.game.GameView
import com.zenvex.online.game.Native
import com.zenvex.online.game.Online

private enum class Overlay { None, Pause, Settings, Bag, Craft, Merchant, Chat, Map, Dead }

@Composable
fun GameScreen(toLobby: () -> Unit) {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Overlay.None) }
    val view = remember {
        lateinit var v: GameView
        v = GameView(ctx, object : GameUi {
            override fun onDeath() { v.post { overlay = Overlay.Dead } }
            override fun onOpen(panel: String) {
                v.post {
                    overlay = when (panel) {
                        "bag" -> Overlay.Bag; "craft" -> Overlay.Craft; "merchant" -> Overlay.Merchant
                        "chat" -> Overlay.Chat; "map" -> Overlay.Map; else -> Overlay.Pause
                    }
                }
            }
            override fun onToast(msg: String) { v.post { Toaster.show(msg) } }
            override fun onLevelUp(level: Int) { v.post { Online.system("Seviye $level oldun!") } }
        })
        v.localName = GameSession.ui("profile").optString("name")
        v
    }
    view.paused = overlay == Overlay.Pause || overlay == Overlay.Settings
    DisposableEffect(Unit) {
        Online.onChat = { l -> view.addChat((if (l.channel == "local") "[Yakın] " else if (l.channel == "system") "" else "") + "${l.name}: ${l.text}") }
        onDispose { Online.onChat = null; GameSession.save() }
    }
    BackHandler {
        overlay = when (overlay) { Overlay.None -> Overlay.Pause; Overlay.Dead -> Overlay.Dead; Overlay.Settings -> Overlay.Pause; else -> Overlay.None }
    }
    val close = { overlay = Overlay.None }
    Box(Modifier.fillMaxSize()) {
        AndroidView({ view }, Modifier.fillMaxSize())
        when (overlay) {
            Overlay.Pause -> Dim(close) {
                Panel(Modifier.width(320.dp)) {
                    T("DURAKLATILDI", 24.sp, display = true, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                    GButton("Devam Et", Modifier.fillMaxWidth(), Tone.Gold, icon = "play") { overlay = Overlay.None }
                    Spacer(Modifier.height(8.dp))
                    GButton("Envanter", Modifier.fillMaxWidth(), Tone.Dark, icon = "bag") { overlay = Overlay.Bag }
                    Spacer(Modifier.height(8.dp))
                    GButton("Ayarlar", Modifier.fillMaxWidth(), Tone.Dark, icon = "gear") { overlay = Overlay.Settings }
                    Spacer(Modifier.height(8.dp))
                    GButton("Lobiye Dön", Modifier.fillMaxWidth(), Tone.Red, icon = "exit") { GameSession.save(); toLobby() }
                }
            }
            Overlay.Settings -> SettingsScreen(back = { overlay = Overlay.Pause; view.post { view.requestLayout() } }, inGame = true)
            Overlay.Bag -> InventoryScreen(close, inGame = true)
            Overlay.Craft -> CraftScreen(close)
            Overlay.Merchant -> ShopScreen(close)
            Overlay.Chat -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                ChatPanel(Modifier.width(460.dp).fillMaxHeight().align(Alignment.CenterStart), close)
            }
            Overlay.Map -> Dim(close) { WorldMap(close) }
            Overlay.Dead -> Dim {
                Panel(Modifier.width(360.dp), Pal.Red) {
                    T("ÖLDÜN", 40.sp, Pal.Red, display = true, glow = Pal.Red.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
                    T("Ekipmanın sende kalır. Nereden başlamak istersin?", 13.sp, Pal.Sub, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(14.dp))
                    GButton("Kampta Doğ", Modifier.fillMaxWidth(), Tone.Gold, icon = "flame") {
                        GameSession.cmd("respawn", "city" to false); overlay = Overlay.None
                    }
                    Spacer(Modifier.height(8.dp))
                    val city = GameSession.ui("profile").optBoolean("city")
                    GButton("Şehirde Doğ", Modifier.fillMaxWidth(), if (city) Tone.Blue else Tone.Dark, icon = "shield", sub = if (city) null else "Seviye 10 gerekli") {
                        if (city) { GameSession.cmd("respawn", "city" to true); overlay = Overlay.None }
                    }
                    Spacer(Modifier.height(8.dp))
                    GButton("Lobiye Dön", Modifier.fillMaxWidth(), Tone.Dark, icon = "exit") { GameSession.cmd("respawn", "city" to false); toLobby() }
                }
            }
            Overlay.None -> {}
        }
    }
}

@Composable
private fun WorldMap(close: () -> Unit) {
    val n = 360
    val img = remember {
        val px = IntArray(n * n)
        synchronized(GameSession.lock) { Native.minimap(GameSession.handle, n, px) }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val hud = remember { GameSession.hud() }
    val w = remember { GameSession.world() }
    val mapPx = w.optDouble("map_px", 23040.0).toFloat()
    Column(Modifier.glass(20.dp).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("DÜNYA HARİTASI", 20.sp, display = true)
            Spacer(Modifier.width(20.dp))
            RoundIcon("close", size = 34.dp) { close() }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(300.dp).clip(RoundedCornerShape(12.dp))) {
            Image(img, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.Low)
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / mapPx
                fun dot(x: Float, y: Float, c: Color, r: Float) = drawCircle(c, r * density, Offset(x * k, y * k))
                val wall = w.optDouble("wall_y").toFloat()
                drawLine(Color(0xFFD0C8B8), Offset(0f, wall * k), Offset(size.width, wall * k), 2 * density)
                w.optJSONArray("merchants")?.let { for (i in 0 until it.length()) { val a = it.getJSONArray(i); dot(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), Pal.Gold, 3.5f) } }
                w.optJSONArray("camp")?.let { dot(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), Color(0xFFFFA040), 5f) }
                w.optJSONArray("boss")?.let { dot(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), Pal.Red, 6f) }
                dot(hud[Native.PX], hud[Native.PY], Color.White, 5f)
                dot(hud[Native.PX], hud[Native.PY], Pal.Blue, 3f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            T("● Sen", 11.sp, Pal.Blue); T("● Kamp", 11.sp, Color(0xFFFFA040)); T("● Tüccar", 11.sp, Pal.Gold); T("● Titan", 11.sp, Pal.Red); T("— Şehir duvarı", 11.sp, Pal.Sub)
        }
    }
}
