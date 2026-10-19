package com.zenvex.online.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import com.zenvex.online.R
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Oyun ekranından Compose katmanına çağrılar. */
interface GameUi {
    fun onDeath()
    fun onOpen(panel: String)
    fun onToast(msg: String)
    fun onLevelUp(level: Int)
    fun onChatLine(text: String) {}
}

/**
 * Oyun ekranı: Rust'ın y-sıralı çizim listesini yüksek çözünürlüklü atlaslardan çizer.
 * Zemin Rust'ta piksel piksel harmanlanır ve parçalar halinde önbelleğe alınır.
 * Joystickler ve hızlı tuşlar (yetenek, silah, araç, etkileşim) çoklu dokunuş için tuvalde çizilir.
 */
@SuppressLint("ViewConstructor")
private const val CLAN_COLOR = 0xFFF6E05A.toInt()
/** rust pet.rs D_PET */
private const val D_PET = 8

class GameView(context: Context, private val ui: GameUi) : SurfaceView(context), SurfaceHolder.Callback {
    private val atlas = Atlas.get(context)
    @Volatile var paused = false
    @Volatile var localName: String = ""
    @Volatile var localClan: String = ""
    @Volatile var serverTitle: String = ""

    // ------------------------------------------------------------------ dünya tabloları
    private val staticNames: Array<String>
    private val staticSprites: Array<Sprite?>
    private val zombieSprite: Array<String>
    private val zombieName: Array<String>
    private val zombieColor: IntArray
    private val vehicleIds: Array<String>
    private val weaponSprites: Array<String>
    private val itemIds: Array<String>
    private val itemSprites: Array<Sprite?>
    private val zones: Array<String>
    private val mapPx: Float
    private val tile: Float

    init {
        holder.addCallback(this)
        isFocusable = true
        val w = GameSession.world()
        val n = w.getJSONArray("names")
        staticNames = Array(n.length()) { n.getString(it) }
        staticSprites = Array(n.length()) { atlas["s/" + staticNames[it]] }
        val z = w.getJSONArray("zombies")
        zombieName = Array(z.length()) { z.getJSONArray(it).getString(0) }
        zombieSprite = Array(z.length()) { z.getJSONArray(it).getString(1) }
        zombieColor = IntArray(z.length()) { z.getJSONArray(it).getLong(2).toInt() }
        val v = w.getJSONArray("vehicles")
        vehicleIds = Array(v.length()) { v.getJSONArray(it).getString(0) }
        val wp = w.getJSONArray("weapons")
        weaponSprites = Array(wp.length()) { wp.getJSONArray(it).getString(1) }
        val it2 = w.getJSONArray("items")
        itemIds = Array(it2.length()) { it2.getJSONArray(it).getString(0) }
        itemSprites = Array(itemIds.size) { atlas["i/" + itemIds[it]] }
        val zs = w.getJSONArray("zones")
        zones = Array(zs.length()) { zs.getJSONArray(it).getString(0) }
        mapPx = w.getDouble("map_px").toFloat()
        tile = w.optDouble("tile", 64.0).toFloat()
        atlas.preload("world"); atlas.preload("items"); atlas.preload("npcs")
    }

    // ------------------------------------------------------------------ boyalar
    private val density = resources.displayMetrics.density
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    /** piksel sanat (kahraman): yumuşatmasız, keskin pikseller */
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val alphaPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(0xB0FFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val tintPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fontBold: Typeface = ResourcesCompat.getFont(context, R.font.outfit_bold) ?: Typeface.DEFAULT_BOLD
    private val fontDisplay: Typeface = ResourcesCompat.getFont(context, R.font.display_bold) ?: Typeface.DEFAULT_BOLD
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fontBold; color = Color.WHITE }
    private val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fontBold; color = 0xE0101418.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val multiply = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val rect = RectF()
    private val srcR = Rect()
    private val path = Path()

    // ------------------------------------------------------------------ zemin parçaları
    private val CH = 256
    private val chunkPx = IntArray(CH * CH)
    private val chunks = object : LinkedHashMap<Long, Bitmap>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>): Boolean {
            if (size > 140) { eldest.value.recycle(); return true }
            return false
        }
    }
    private var groundReady = true

    // ------------------------------------------------------------------ kamera
    private var camX = 0f
    private var camY = 0f
    private var scale = 1f
    private var viewW = 1f
    private var viewH = 1f
    private var shakeT = 0f
    private var shakeAmp = 0f
    private var sw = 1
    private var sh = 1

    // ------------------------------------------------------------------ arabellekler
    private val drawBuf = FloatArray(Native.STRIDE * 5000)
    private var drawN = 0
    private val viewArr = FloatArray(5)
    private val inputArr = FloatArray(5)
    private val hud = FloatArray(Native.HUD_SIZE)
    private val radarBuf = FloatArray(3 * 200)
    private var radarN = 0
    private val remoteBuf = FloatArray(Native.REMOTE_STRIDE * 32)

    // ------------------------------------------------------------------ dokunma
    private inner class Stick {
        var pointer = -1
        var cx = 0f; var cy = 0f; var x = 0f; var y = 0f
        var homeX = 0f; var homeY = 0f
        var vx = 0f; var vy = 0f
        val active get() = pointer >= 0
    }
    private val moveStick = Stick()
    private val atkStick = Stick()
    private var lastAtkX = 0f
    private var lastAtkY = 1f
    private var stickR = 70f

    private class Btn(val id: String) {
        val r = RectF()
        var visible = true
        var pressed = -1
        var flash = 0f
    }
    private val btnAbility = Btn("ability")
    private val btnWeapon = Btn("weapon")
    private val btnReload = Btn("reload")
    private val btnVehicle = Btn("vehicle")
    private val btnInteract = Btn("interact")
    private val btnTrade = Btn("trade")
    private val btnRide = Btn("ride")
    @Volatile private var rideUid: String? = null
    private var nearCache: Online.Near? = null
    private var abilityCdMax = 5f
    private val buttons = listOf(btnAbility, btnWeapon, btnReload, btnVehicle, btnInteract, btnTrade, btnRide)

    // ------------------------------------------------------------------ efektler
    private class Floater(var x: Float, var y: Float, val text: String, val color: Int, val size: Float, var life: Float = 1.2f, val max: Float = 1.2f)
    private class Particle(
        var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float,
        var life: Float, val max: Float, val color: Int, val size: Float, val kind: Int,
    )
    private class Light(val x: Float, val y: Float, val r: Float, val color: Int, var life: Float, val max: Float)
    private class Tracer(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, var life: Float, val width: Float)
    private val floaters = ArrayList<Floater>()
    private val particles = ArrayList<Particle>()
    private val flashes = ArrayList<Light>()
    private val tracers = ArrayList<Tracer>()
    private var banner = ""
    private var bannerSub = ""
    private var bannerT = 0f
    private var bannerColor = Color.WHITE
    private var vignetteRed = 0f
    private var fps = 0f
    private var autosaveT = 0f
    private var deadNotified = false
    private var time = 0f
    private var lastLevel = -1
    private val chatLines = ArrayList<Pair<String, Float>>()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    // ------------------------------------------------------------------ döngü
    private var thread: Thread? = null
    @Volatile private var running = false

    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "zenvex-render").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        sw = width; sh = height
        layoutControls(width, height)
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        thread?.join(600)
        thread = null
        GameSession.save()
    }

    private fun layoutControls(w: Int, h: Int) {
        val s = Settings.current
        stickR = 62 * density * s.joystickSize
        val m = stickR * 1.55f
        val left = if (s.leftHanded) atkStick else moveStick
        val right = if (s.leftHanded) moveStick else atkStick
        left.homeX = m + 24 * density; left.homeY = h - m * 0.92f
        right.homeX = w - m - 24 * density; right.homeY = h - m * 0.92f
        for (st in listOf(left, right)) if (!st.active) { st.cx = st.homeX; st.cy = st.homeY; st.x = st.cx; st.y = st.cy }
        // saldırı joystiğinin çevresinde yay şeklinde tuşlar
        val ax = right.homeX; val ay = right.homeY
        val br = 30 * density
        fun place(b: Btn, angDeg: Float, dist: Float, r: Float = br) {
            val a = Math.toRadians(angDeg.toDouble())
            val sx = if (s.leftHanded) -1 else 1
            val cx = ax - sx * cos(a).toFloat() * dist
            val cy = ay - sin(a).toFloat() * dist
            b.r.set(cx - r, cy - r, cx + r, cy + r)
        }
        place(btnAbility, 100f, stickR * 2.25f, br * 1.2f)
        place(btnWeapon, 140f, stickR * 2.15f)
        place(btnReload, 172f, stickR * 2.1f, br * 0.8f)
        place(btnVehicle, 62f, stickR * 2.3f)
        place(btnInteract, 30f, stickR * 2.45f)
        place(btnTrade, 4f, stickR * 2.6f, br * 0.85f)
        place(btnRide, -20f, stickR * 2.6f, br * 0.85f)
    }

    private fun loop() {
        var last = SystemClock.elapsedRealtimeNanos()
        var fpsAcc = 0f
        var fpsN = 0
        while (running) {
            val now = SystemClock.elapsedRealtimeNanos()
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            time += dt
            fpsAcc += dt; fpsN++
            if (fpsAcc >= 0.5f) { fps = fpsN / fpsAcc; fpsAcc = 0f; fpsN = 0 }
            try {
                update(dt)
                val c = try { holder.lockHardwareCanvas() } catch (_: Exception) { null }
                if (c != null) {
                    try { render(c, dt) } finally { holder.unlockCanvasAndPost(c) }
                }
            } catch (e: Exception) {
                android.util.Log.e("zenvex", "kare hatası", e)
            }
            val target = 1_000_000_000L / Settings.current.effectiveFps
            val spent = SystemClock.elapsedRealtimeNanos() - now
            if (spent < target) Thread.sleep((target - spent) / 1_000_000L, ((target - spent) % 1_000_000L).toInt())
        }
    }

    // ------------------------------------------------------------------ güncelleme
    private fun update(dt: Float) {
        val s = Settings.current
        inputArr[0] = moveStick.vx; inputArr[1] = moveStick.vy
        if (atkStick.active) {
            val l = hypot(atkStick.vx, atkStick.vy)
            if (l > 1e-3f) { lastAtkX = atkStick.vx / l; lastAtkY = atkStick.vy / l }
            inputArr[2] = lastAtkX; inputArr[3] = lastAtkY
            inputArr[4] = if (l > s.attackDeadZone) 1f else 0f
        } else { inputArr[2] = lastAtkX; inputArr[3] = lastAtkY; inputArr[4] = 0f }

        val events: String
        synchronized(GameSession.lock) {
            val h = GameSession.handle
            if (!paused) Native.step(h, dt, inputArr)
            Native.hud(h, hud)
            Online.remotes(remoteBuf)?.let { n -> Native.setRemotes(h, remoteBuf, n) }
            Online.tick(h)
            // yolcu: sürücünün konumunu izle
            rideUid?.let { u ->
                val pos = Online.position(u)
                if (pos == null || pos[9] < 2f) {
                    rideUid = null
                    Native.command(h, "{\"cmd\":\"ride\",\"on\":false,\"x\":${hud[Native.PX]},\"y\":${hud[Native.PY]}}")
                } else Native.command(h, "{\"cmd\":\"ride\",\"on\":true,\"x\":${pos[0]},\"y\":${pos[1]}}")
            }
            // kamera
            val px = hud[Native.PX]; val py = hud[Native.PY]
            val k = 1f - kotlin.math.exp(-8f * dt)
            camX += (px - camX) * k
            camY += (py - 40f - camY) * k
            if (abs(px - camX) > 1500 || abs(py - camY) > 1500) { camX = px; camY = py - 40f }
            val visibleM = when (s.zoom) { 0 -> 10.5f; 1 -> 13f; else -> 16.5f }
            scale = sh / (visibleM * 62.5f)
            viewW = sw / scale; viewH = sh / scale
            viewArr[0] = camX - viewW / 2; viewArr[1] = camY - viewH / 2
            viewArr[2] = viewW; viewArr[3] = viewH; viewArr[4] = s.vegetationDensity
            drawN = Native.drawList(h, viewArr, drawBuf)
            radarN = Native.radar(h, radarBuf)
            events = Native.events(h)
        }
        handleEvents(events)

        // efektler
        if (shakeT > 0) shakeT -= dt
        updateFx(dt)
        if (bannerT > 0) bannerT -= dt
        vignetteRed = max(0f, vignetteRed - dt * 1.6f)
        val fit = floaters.iterator()
        while (fit.hasNext()) { val f = fit.next(); f.life -= dt; f.y -= 42f * dt; if (f.life <= 0) fit.remove() }
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            if (p.kind == 0) { p.vz -= 900f * dt; if (p.z < 0) { p.z = 0f; p.vz *= -0.3f; p.vx *= 0.6f; p.vy *= 0.6f } }
            else { p.vx *= 0.97f; p.vy *= 0.97f }
            if (p.life <= 0) pit.remove()
        }
        flashes.removeAll { it.life -= dt; it.life <= 0 }
        tracers.removeAll { it.life -= dt; it.life <= 0 }
        synchronized(chatLines) { chatLines.replaceAll { it.first to it.second - dt }; chatLines.removeAll { it.second <= 0 } }
        for (b in buttons) b.flash = max(0f, b.flash - dt * 3)

        if (hud[Native.DEAD] > 0.5f && !deadNotified) { deadNotified = true; ui.onDeath() }
        if (hud[Native.DEAD] < 0.5f) deadNotified = false
        val lvl = hud[Native.LEVEL].toInt()
        if (lastLevel in 1 until lvl) ui.onLevelUp(lvl)
        lastLevel = lvl
        autosaveT += dt
        if (autosaveT > 25f) { autosaveT = 0f; Thread { GameSession.save() }.start() }
    }

    fun addChat(line: String) = synchronized(chatLines) {
        chatLines += line to 9f
        if (chatLines.size > 6) chatLines.removeAt(0)
    }

    private fun vibrate(ms: Long, amp: Int) {
        val lvl = Settings.current.haptics
        if (lvl == 0) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, (amp * (if (lvl == 2) 1.0 else 0.5)).toInt().coerceIn(1, 255))) }
    }

    private fun shake(a: Float, t: Float) {
        val k = Settings.current.screenShake
        if (k <= 0) return
        shakeAmp = max(shakeAmp * (shakeT / 0.3f).coerceIn(0f, 1f), a * k); shakeT = max(shakeT, t)
    }

    private fun near(x: Float, y: Float): Float {
        val d = hypot(x - hud[Native.PX], y - hud[Native.PY])
        return (1f - d / 1400f).coerceIn(0f, 1f)
    }

    private fun burst(x: Float, y: Float, n: Int, color: Int, speed: Float, size: Float, kind: Int = 0, life: Float = 0.6f, up: Float = 260f) {
        if (!Settings.current.particles && kind != 2) return
        repeat(n) {
            val a = Math.random() * 2 * PI
            val sp = speed * (0.4f + Math.random().toFloat() * 0.8f)
            particles += Particle(x, y, 30f + Math.random().toFloat() * 40f, cos(a).toFloat() * sp, sin(a).toFloat() * sp * 0.6f,
                up * (0.5f + Math.random().toFloat()), life * (0.6f + Math.random().toFloat() * 0.6f), life, color, size, kind)
        }
    }

    private fun handleEvents(json: String) {
        if (json.length < 3) return
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        val s = Settings.current
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val x = e.optDouble("x", 0.0).toFloat()
            val y = e.optDouble("y", 0.0).toFloat()
            when (e.getString("t")) {
                "zhit", "rdmg", "nkill" -> Online.onEvent(e)
                "roll" -> {
                    fx("dust", x, y, 120f)
                    burst(x, y, 10, 0xFF9A8A70.toInt(), 160f, 4f)
                    Audio.play("swing", 0.6f, 0.8f); vibrate(20, 80)
                }
                "dodge" -> floaters += Floater(x, y, "KAÇTI", 0xFFB8F0FF.toInt(), 20f)
                "potion" -> {
                    val k = e.optInt("kind")
                    val (txt, col) = when (k) { 0 -> "Can İksiri" to 0xFFFF6A6A.toInt(); 1 -> "Hız İksiri" to 0xFF6AE0FF.toInt(); else -> "Güç İksiri" to 0xFFFFA040.toInt() }
                    floaters += Floater(hud[Native.PX], hud[Native.PY] - 140, txt, col, 20f, 1.4f, 1.4f)
                    fx(if (k == 0) "heal" else "glow", 0f, 0f, 220f, follow = true)
                    Audio.play("pickup", 0.9f, 1.3f)
                }
                "knock" -> { shake(12f, 0.35f); vibrate(120, 255); floaters += Floater(hud[Native.PX], hud[Native.PY] - 140, "YERE DÜŞTÜN", 0xFFFF8A50.toInt(), 22f) }
                "dmg" -> {
                    fx("blood", x, y + 50, 90f, (Math.random() * 360).toFloat())
                    if (s.damageNumbers) floaters += Floater(x, y, e.getInt("v").toString(),
                        if (e.optBoolean("crit")) 0xFFFFD040.toInt() else Color.WHITE, if (e.optBoolean("crit")) 30f else 22f)
                    burst(x, y + 60, 6, 0xFF7A1A14.toInt(), 160f, 4f)
                }
                "pdmg" -> {
                    floaters += Floater(x, y, "-" + e.getInt("v"), 0xFFFF5050.toInt(), 24f)
                    vignetteRed = 0.6f; shake(7f, 0.18f); vibrate(40, 160); Audio.play("hurt", 0.8f)
                }
                "xp" -> floaters += Floater(x, y, "+" + e.getInt("v") + " XP", 0xFF8CE0FF.toInt(), 18f, 1.4f, 1.4f)
                "gold" -> { floaters += Floater(x, y, "+" + e.getInt("v"), 0xFFFFD34A.toInt(), 20f); Audio.play("coin", 0.7f) }
                "pickup" -> {
                    val rc = rarityColor(e.optInt("rarity"))
                    floaters += Floater(x, y, "+" + e.getInt("count") + " " + e.getString("name"), rc, 19f, 1.6f, 1.6f)
                    Audio.play("pickup", 0.7f)
                }
                "invfull" -> ui.onToast("Çanta dolu!")
                "lvl" -> {
                    fx("levelup", 0f, 0f, 340f, follow = true)
                    setBanner("SEVİYE ${e.getInt("level")}", "+1 skill puanı · skill ağacından bir yol seç", 0xFFFFD34A.toInt())
                    Audio.play("levelup"); vibrate(120, 200)
                    burst(hud[Native.PX], hud[Native.PY], 40, 0xFFFFE070.toInt(), 300f, 5f, 1, 1.2f)
                }
                "clvl" -> {
                    val lv = e.getInt("level")
                    val perk = GameSession.ui("tree").optJSONArray("perks")?.optString(lv - 1).orEmpty()
                    setBanner("SAVAŞÇI SEVİYE $lv", perk, 0xFFB277FF.toInt())
                }
                "hit" -> {
                    val kind = e.getInt("kind")
                    fx("spark", x, y, 85f, (Math.random() * 360).toFloat())
                    Audio.play(if (kind == 1) "chop" else "hit", near(x, y), 0.9f + Math.random().toFloat() * 0.2f)
                    burst(x, y + 40, 8, if (kind == 1) 0xFF8A6440.toInt() else if (kind == 2) 0xFF9A968E.toInt() else 0xFFB08A50.toInt(), 200f, 4f)
                    shake(2f, 0.08f)
                }
                "kill" -> fx("blood", x, y - 40, 150f)
                "treefall" -> { Audio.play("treefall", near(x, y)); burst(x, y, 24, 0xFF4E8A34.toInt(), 260f, 5f, 0, 1.0f) }
                "kill" -> {
                    Audio.play("zdeath", near(x, y), 0.9f + Math.random().toFloat() * 0.2f)
                    burst(x, y, 16, 0xFF6A1410.toInt(), 220f, 5f)
                }
                "died" -> { Audio.play("death"); vibrate(300, 255); shake(10f, 0.4f) }
                "swing" -> Audio.play("swing", 0.6f, 0.9f + Math.random().toFloat() * 0.25f)
                "shoot" -> {
                    val w = e.getInt("w")
                    val a = e.getDouble("a").toFloat()
                    Audio.play(when (w) { 3 -> "shot_pistol"; 6 -> "shot_sniper"; 7 -> "rocket"; else -> "shot_rifle" }, near(x, y), 0.95f + Math.random().toFloat() * 0.1f)
                    flashes += Light(x + sin(a) * 50, y + cos(a) * 30 - 70, 220f, 0xFFFFC870.toInt(), 0.06f, 0.06f)
                    fx("muzzle", x + sin(a) * 58, y + cos(a) * 36 - 78, if (w >= 6) 110f else 75f, Math.toDegrees((-a + PI / 2).toDouble()).toFloat(), dur = 0.07f, frame = (0..2).random())
                    shake(if (w == 6 || w == 7) 6f else 2.5f, 0.07f)
                    burst(x + sin(a) * 40, y + cos(a) * 25, 2, 0xFFD8B060.toInt(), 120f, 2.5f, 0, 0.8f, 200f)
                }
                "reload" -> Audio.play("reload", 0.8f)
                "noammo" -> { Audio.play("empty", 0.8f); ui.onToast("Mermi yok!") }
                "explode" -> {
                    val r = e.getDouble("r").toFloat()
                    fx("explosion", x, y - r * 0.4f, r * 2.8f)
                    fx("shockwave", x, y, r * 3.2f)
                    Audio.play("explosion", max(0.35f, near(x, y)))
                    flashes += Light(x, y - 40, r * 3f, 0xFFFFA040.toInt(), 0.5f, 0.5f)
                    burst(x, y, 30, 0xFFFF8A20.toInt(), r * 2.4f, 7f, 1, 0.7f)
                    burst(x, y, 24, 0xFF3A3632.toInt(), r * 1.4f, 14f, 1, 1.6f)
                    shake(14f * near(x, y) + 3f, 0.4f); vibrate(120, 220)
                }
                "groan" -> if (Math.random() < 0.6) Audio.play("groan" + (0..2).random(), near(x, y) * 0.7f, 0.85f + Math.random().toFloat() * 0.3f)
                "teleport" -> {
                    fx("teleport", x, y - 70, 220f)
                    Audio.play("teleport", near(x, y)); burst(x, y, 26, 0xFFB070FF.toInt(), 260f, 5f, 1, 0.8f) }
                "toxic" -> {
                    fx("toxic", x, y - 30, 300f, dur = 5f)
                    Audio.play("toxic", near(x, y)); burst(x, y, 30, 0xFF9AFF40.toInt(), 180f, 9f, 1, 1.4f) }
                "slam" -> {
                    fx("shockwave", x, y, e.getDouble("r").toFloat() * 2.4f)
                    fx("dust", x, y - 20, e.getDouble("r").toFloat() * 1.6f)
                    Audio.play("explosion", near(x, y) * 0.8f, 0.6f)
                    burst(x, y, 30, 0xFF8A7A60.toInt(), e.getDouble("r").toFloat() * 2f, 6f, 0, 0.8f)
                    shake(10f * near(x, y), 0.3f)
                }
                "boss" -> { setBanner(e.getString("name"), "Bir boss uyandı!", 0xFFFF4A3A.toInt()); Audio.play("boss"); shake(8f, 0.6f) }
                "quest" -> { setBanner("GÖREV TAMAMLANDI", e.getString("title"), 0xFF7FE07F.toInt()); Audio.play("quest") }
                "zone" -> {
                    val lo = e.getInt("lo"); val hi = e.getInt("hi")
                    setBanner(e.getString("name"), if (e.optBoolean("safe")) "Güvenli bölge" else "Seviye $lo–$hi",
                        if (e.optBoolean("safe")) 0xFF7FE0A0.toInt() else 0xFFFFE9A8.toInt())
                }
                "locked" -> ui.onToast("Şehir kapısı kilitli: Seviye 10 gerekli")
                "sniper" -> {
                    tracers += Tracer(e.getDouble("x1").toFloat(), e.getDouble("y1").toFloat(), e.getDouble("x2").toFloat(), e.getDouble("y2").toFloat(), 0xFFFFF0B0.toInt(), 0.12f, 3f)
                    Audio.play("shot_sniper", near(e.getDouble("x2").toFloat(), e.getDouble("y2").toFloat()) * 0.6f, 1.1f)
                }
                "drone" -> Audio.play("drone", near(x, y) * 0.5f, 1.3f)
                "shield" -> { Audio.play("shield"); floaters += Floater(hud[Native.PX], hud[Native.PY] - 140, "+" + e.getInt("v") + " KALKAN", 0xFF70C8FF.toInt(), 20f) }
                "heal" -> { Audio.play("heal", 0.6f); floaters += Floater(x, y - 140, "+" + e.getInt("v"), 0xFF60FF80.toInt(), 22f); burst(x, y, 10, 0xFF70FF90.toInt(), 80f, 5f, 1, 1f) }
                "rage" -> {
                    fx("rage", 0f, 0f, 220f, follow = true, dur = 5f)
                    Audio.play("rage"); setBanner("ÖFKE", "Hasar arttı!", 0xFFFF5A3A.toInt()) }
                "cloak" -> {
                    fx("levelup", 0f, 0f, 300f, follow = true)
                    Audio.play("shield", 1f, 0.7f); setBanner("TANRI PELERİNİ", "3 sn ölümsüzlük", 0xFFFFD34A.toInt()) }
                "lastbreath" -> setBanner("SON NEFES", "Ölümden döndün!", 0xFFFF8080.toInt())
                "crate" -> { Audio.play("crate", near(x, y)); burst(x, y, 18, 0xFFB08A50.toInt(), 260f, 5f) }
                "car_hit" -> { Audio.play("car_hit", near(x, y)); shake(5f, 0.15f); vibrate(40, 150) }
                "enter" -> Audio.play("engine", 0.8f)
                "exit" -> Audio.play("click", 0.6f)
                "msg" -> ui.onToast(e.getString("text"))
                "step" -> if (Math.random() < 0.5) Audio.play("step" + (0..1).random(), 0.25f, 0.9f + Math.random().toFloat() * 0.2f)
            }
        }
    }

    private fun setBanner(t: String, sub: String, color: Int) {
        banner = t; bannerSub = sub; bannerColor = color; bannerT = 2.8f
    }

    private fun rarityColor(r: Int) = when (r) {
        1 -> 0xFF5AA8FF.toInt(); 2 -> 0xFFC070FF.toInt(); 3 -> 0xFFFFB030.toInt(); 4 -> 0xFFFF5A5A.toInt(); else -> 0xFFE8E8E8.toInt()
    }

    // ------------------------------------------------------------------ çizim
    private fun wx(x: Float) = (x - camX) * scale + sw / 2f
    private fun wy(y: Float) = (y - camY) * scale + sh / 2f

    private class Plate(val x: Float, val y: Float, val text: String, val color: Int, val hp: Float, val boss: Boolean, val sub: String?, val subColor: Int = 0xFFDDDDDD.toInt())
    private val plates = ArrayList<Plate>()

    private fun render(c: Canvas, dt: Float) {
        val s = Settings.current
        c.drawColor(0xFF203018.toInt())
        c.save()
        if (shakeT > 0) {
            val a = shakeAmp * (shakeT / 0.3f).coerceIn(0f, 1f) * density
            c.translate((Math.random().toFloat() - 0.5f) * a * 2, (Math.random().toFloat() - 0.5f) * a * 2)
        }
        drawGround(c)
        plates.clear()
        val px = hud[Native.PX]; val py = hud[Native.PY]
        for (i in 0 until drawN) {
            val o = i * Native.STRIDE
            when (drawBuf[o].toInt()) {
                Native.D_STATIC -> drawStatic(c, o, px, py)
                Native.D_HUMAN -> drawHuman(c, o)
                Native.D_ZOMBIE -> drawZombie(c, o)
                Native.D_DROP -> drawDrop(c, o)
                Native.D_PROJ -> drawProj(c, o)
                Native.D_VEHICLE -> drawVehicle(c, o)
                Native.D_DRONE -> drawDrone(c, o)
                Native.D_AREA -> drawArea(c, o)
                D_PET -> drawPet(c, o)
            }
        }
        drawFx(c)
        drawParticles(c)
        drawTracers(c)
        drawLighting(c)
        drawPlates(c)
        drawFloaters(c)
        c.restore()
        drawHud(c)
        drawControls(c)
        if (s.showFps) drawText(c, "${fps.roundToInt()} FPS", 12 * density, sh - 10 * density, 12 * density, 0xFF9AFF9A.toInt())
    }

    private fun drawGround(c: Canvas) {
        val x0 = floor((camX - viewW / 2) / CH).toInt() - 0
        val y0 = floor((camY - viewH / 2) / CH).toInt() - 0
        val x1 = floor((camX + viewW / 2) / CH).toInt()
        val y1 = floor((camY + viewH / 2) / CH).toInt()
        var budget = if (chunks.isEmpty()) 999 else 4
        for (cy in y0..y1) for (cx in x0..x1) {
            val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
            var b = chunks[key]
            if (b == null && budget > 0) {
                budget--
                val ok = synchronized(GameSession.lock) { Native.renderChunk(GameSession.handle, cx * CH.toFloat(), cy * CH.toFloat(), CH, chunkPx) }
                if (ok != 0) {
                    b = Bitmap.createBitmap(CH, CH, Bitmap.Config.ARGB_8888)
                    b.setPixels(chunkPx, 0, CH, 0, 0, CH, CH)
                    chunks[key] = b
                } else groundReady = false
            }
            val l = wx(cx * CH.toFloat()); val t = wy(cy * CH.toFloat())
            rect.set(l, t, l + CH * scale + 1, t + CH * scale + 1)
            if (b != null) c.drawBitmap(b, null, rect, bmpPaint)
            else { fill.color = 0xFF3E6A2A.toInt(); c.drawRect(rect, fill) }
        }
    }

    private fun drawStatic(c: Canvas, o: Int, px: Float, py: Float) {
        val si = drawBuf[o + 1].toInt()
        val sp = staticSprites.getOrNull(si) ?: return
        val x = drawBuf[o + 2] + drawBuf[o + 7]
        val y = drawBuf[o + 3]
        val kind = drawBuf[o + 5].toInt()
        val phase = drawBuf[o + 6]
        val sx = wx(x); val sy = wy(y)
        val w = sp.w * scale; val h = sp.h * scale
        // ekran dışı ise atla
        if (sx + w < 0 || sx - w > sw || sy - h > sh || sy + h * 0.3f < 0) return
        var paint: Paint = bmpPaint
        // ağaç / bina arkasında kalan oyuncuyu göstermek için saydamlaştır
        if (Settings.current.treeFade && (kind == 0 || kind == 4 || kind == 11) && py < y - 10) {
            val l = x - sp.ax; val r = x - sp.ax + sp.w
            val t = y - sp.ay
            if (px > l + sp.w * 0.12f && px < r - sp.w * 0.12f && py > t + 20 && py < y) {
                alphaPaint.alpha = 115; paint = alphaPaint
            }
        }
        if (kind == 0 && Settings.current.wind) {
            val sway = sin(time * 1.3f + phase * 6.28f) * 0.9f + sin(time * 2.9f + phase * 3f) * 0.3f
            c.save()
            c.translate(sx, sy); c.skew(sway * 0.012f, 0f); c.translate(-sx, -sy)
            atlas.draw(c, sp, sx, sy, scale, paint)
            c.restore()
        } else atlas.draw(c, sp, sx, sy, scale, paint)
        val hp = drawBuf[o + 8]
        if (hp >= 0) bar(c, sx, sy - sp.ay * scale * 0.55f, 46 * density, hp, 0xFFE0C060.toInt())
        // ışık kaynakları
        if (kind == 7 || kind == 8) {
            val night = 1f - hud[Native.LIGHT]
            if (night > 0.05f) {
                val ly = if (kind == 7) y - sp.ay + 22 else y - 25
                val lx = if (kind == 7) x - sp.ax + sp.w * 0.5f else x
                lightSources += floatArrayOf(lx, ly, if (kind == 7) 420f else 330f, if (kind == 7) 1f else 2f)
            }
            if (kind == 8 && Settings.current.particles && Math.random() < 0.3) {
                particles += Particle(x + (Math.random().toFloat() - 0.5f) * 30, y, 30f, (Math.random().toFloat() - 0.5f) * 20, 0f,
                    80f + Math.random().toFloat() * 60, 0.9f, 0.9f, 0xFFFFA030.toInt(), 3f, 3)
            }
        }
    }

    private val lightSources = ArrayList<FloatArray>()

    private val heroIds = arrayOf("kaan", "nova", "rady", "elif")
    private val heroAnimNames = arrayOf("idle", "walk", "run", "punch", "melee", "pistol_idle", "pistol_walk", "rifle_idle", "rifle_walk", "cast", "hurt", "death", "ride", "roll", "knockdown", "getup")
    private val zAnimNames = arrayOf("idle", "walk", "attack", "hurt", "death")
    private val tiers = arrayOf("", "basic", "police", "military", "titan")
    private val keyBuf = StringBuilder(48)

    private fun frameOf(anims: Map<String, IntArray>, anim: String, t: Float): Int {
        val a = anims[anim] ?: return 0
        val f = (t * a[1]).toInt()
        return if (a[2] == 1) f.mod(a[0]) else f.coerceAtMost(a[0] - 1)
    }

    private fun spriteKey(prefix: String, anim: String, dir: Int, f: Int, suffix: String = ""): String {
        keyBuf.setLength(0)
        keyBuf.append(prefix).append('/').append(anim).append('_').append(dir).append('_').append(f).append(suffix)
        return keyBuf.toString()
    }

    private fun drawHuman(c: Canvas, o: Int) {
        val ch = drawBuf[o + 1].toInt()
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        var dir = drawBuf[o + 4].toInt()
        val animI = drawBuf[o + 5].toInt()
        val t = drawBuf[o + 6]
        val flash = drawBuf[o + 7]
        val who = drawBuf[o + 11]
        val sx = wx(x)
        var sy = wy(y)
        if (sx < -300 || sx > sw + 300 || sy < -200 || sy > sh + 500) return
        // aura karakterin altında, zeminde
        if (who == -1f && hud[Native.CHAR_LEVEL] >= 10 && hud[Native.DEAD] < 0.5f) drawAura(c, sx, sy)
        val prefix: String
        var anim: String
        var actor: String
        when (ch) {
            in 0..3 -> { prefix = "h/" + heroIds[ch]; anim = heroAnimNames.getOrElse(animI) { "idle" }; actor = prefix }
            4 -> { prefix = "n/soldier"; anim = "rifle_idle"; actor = prefix }
            else -> {
                prefix = "n/merchant"; anim = "idle"; actor = prefix
                dir = when (dir) { 0, 1, 7 -> dir; 2, 3 -> 1; 5, 6 -> 7; else -> 0 }
            }
        }
        if (who <= -3f) sy -= 235f * scale  // kule platformu (4.6 m × cos35°)
        val f = frameOf(atlas.heroAnims, anim, t)
        val wIdx = drawBuf[o + 10].toInt() % 16
        // piksel kahraman tek görünüm (sola bakar); sağa bakan yönler aynalanır
        val pix = ch in 0..3
        val flip = pix && dir in 1..3
        val kdir = if (pix) 0 else dir
        var key = spriteKey(prefix, anim, kdir, f)
        // elde gerçek silahla render edilmiş kare varsa onu kullan
        val wKey = if (wIdx > 0) "$key@" + weaponSprites.getOrElse(wIdx) { "" } else if (ch == 4) "$key@m4" else null
        var weaponBaked = false
        var sp = if (wKey != null) atlas[wKey]?.also { weaponBaked = true } else null
        if (sp == null) sp = atlas[key]
        if (sp == null) { key = spriteKey(prefix, "idle", kdir, 0); sp = atlas[key] }
        if (sp == null) { placeholder(c, sx, sy, 0xFF4A7AB0.toInt()); return }
        val paint = when {
            flash == 1f -> flashPaint
            flash == 2f -> { tintPaint.colorFilter = PorterDuffColorFilter(0x66FFD34A, PorterDuff.Mode.SRC_ATOP); tintPaint }
            prefix.startsWith("h/") -> pixelPaint
            else -> bmpPaint
        }
        // ekipman kodları
        val e0 = drawBuf[o + 8].toInt(); val e1 = drawBuf[o + 9].toInt(); val e2 = drawBuf[o + 10].toInt()
        val helmet = tiers.getOrElse(e0 / 8) { "" }; val jacket = tiers.getOrElse(e0 % 8) { "" }
        val boots = tiers.getOrElse(e1 / 8) { "" }; val bag = tiers.getOrElse(e1 % 8) { "" }
        val weapon = e2 % 16; val cloak = e2 / 16 > 0
        val back = dir in 3..5
        val akey = key
        fun att(point: String): FloatArray? = atlas.attach(actor, akey, point)?.also { if (flip) it[0] = -it[0] }
        fun gear(name: String, point: String) {
            val p = att(point) ?: return
            val g = atlas["e/$name/$dir"] ?: return
            atlas.draw(c, g, sx + p[0] * scale, sy + p[1] * scale, scale, paint)
        }
        val isHero = ch in 0..3
        if (isHero && !back) {
            if (cloak) gear("cloak", "neck")
            if (bag.isNotEmpty() && bag != "titan") gear("bag_$bag", "back")
        }
        atlas.draw(c, sp, sx, sy, scale, paint, flip)
        if (isHero) {
            if (sp.k == 1f) { // eski (düşük yoğunluklu) karakterlerde ceket/bot katmanı
                if (boots.isNotEmpty()) { gear("boots_$boots", "lfoot"); gear("boots_$boots", "rfoot") }
                if (jacket.isNotEmpty()) gear("jacket_$jacket", "chest")
            }
            if (helmet.isNotEmpty()) gear("helm_$helmet", "head")
            if (back) {
                if (bag.isNotEmpty() && bag != "titan") gear("bag_$bag", "back")
                if (cloak) gear("cloak", "neck")
            }
            // silah: gövde ile kollar arasında
            if (weapon > 0 && !weaponBaked) {
                val p = att("rhand")
                val ws = atlas["w/" + weaponSprites.getOrElse(weapon) { "" } + "/" + dir]
                if (p != null && ws != null) {
                    val arms = atlas[spriteKey(prefix, anim, dir, f, "_a")]
                    atlas.draw(c, ws, sx + p[0] * scale, sy + p[1] * scale, scale, paint)
                    if (arms != null && !back) atlas.draw(c, arms, sx, sy, scale, paint)
                }
            }
        }
        // isim etiketi
        val top = sy - sp.ay * scale
        when {
            who == -1f -> {
                if (localName.isNotEmpty()) plates += Plate(sx, top - 4 * density, localName, 0xFFFFFFFF.toInt(), -1f, false, localClan.ifEmpty { null }, CLAN_COLOR)
                if (hud[Native.SHIELD] > 0) drawFxSprite(c, "shield", x, y - 62, 175f, time)
                if (Settings.current.particles && hud[Native.POISON] > 0 && Math.random() < 0.2)
                    particles += Particle(x, y, 60f, 0f, 0f, 40f, 0.8f, 0.8f, 0xFF9AFF40.toInt(), 4f, 3)
            }
            who >= 0f -> {
                val n = Online.lastNames.getOrNull(who.toInt()) ?: "Oyuncu"
                val cl = Online.lastClans.getOrNull(who.toInt())?.ifEmpty { null }
                plates += Plate(sx, top - 4 * density, n, 0xFF7FE0FF.toInt(), -1f, false, cl, CLAN_COLOR)
            }
            who <= -2f && ch == 5 -> plates += Plate(sx, top - 4 * density, "Tüccar", 0xFFFFD34A.toInt(), -1f, false, "Alışveriş")
        }
    }

    /** Savaşçı Sv10 karanlık aura: zeminde dönen mor halka + yükselen kıvılcımlar */
    private fun drawAura(c: Canvas, sx: Float, sy: Float) {
        val r = 190f * scale
        val pulse = 0.75f + 0.25f * sin(time * 3f)
        fill.shader = RadialGradient(sx, sy, r, intArrayOf(0x00000000, alphaC(0xFF6A2AC0.toInt(), 0.28f * pulse), 0x00000000), floatArrayOf(0.2f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.55f, sx, sy); c.drawCircle(sx, sy, r, fill); c.restore(); fill.shader = null
        stroke.color = alphaC(0xFFB070FF.toInt(), 0.5f * pulse); stroke.strokeWidth = 2f * density
        c.save(); c.scale(1f, 0.55f, sx, sy)
        for (k in 0 until 3) { val a = time * 70f + k * 120f; c.drawArc(sx - r * 0.9f, sy - r * 0.9f, sx + r * 0.9f, sy + r * 0.9f, a, 60f, false, stroke) }
        c.restore()
        if (Settings.current.particles && Math.random() < 0.35)
            particles += Particle(hud[Native.PX] + ((Math.random() - 0.5) * 300).toFloat(), hud[Native.PY] + ((Math.random() - 0.5) * 160).toFloat(), 10f, 0f, 0f, 70f, 0.9f, 0.9f, 0xFFB070FF.toInt(), 4f, 3)
    }

    /** Köpek yardımcı: köpek zombi görselleriyle, sıcak tonda ve "Köpek" etiketiyle. */
    private fun drawPet(c: Canvas, o: Int) {
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val dir = drawBuf[o + 4].toInt()
        val anim = zAnimNames.getOrElse(drawBuf[o + 5].toInt()) { "idle" }
        val t = drawBuf[o + 6]
        val sx = wx(x); val sy = wy(y)
        if (sx < -300 || sx > sw + 300 || sy < -200 || sy > sh + 400) return
        val f = frameOf(atlas.zombieAnims, anim, t)
        val lv = drawBuf[o + 9].toInt()
        val pre = when { lv >= 10 -> "p/dog10"; lv >= 5 -> "p/dog5"; else -> "p/dog0" }
        val flip = dir in 1..3
        val sp = atlas[spriteKey(pre, anim, 0, f)] ?: atlas[spriteKey(pre, "idle", 0, 0)] ?: atlas[spriteKey("z/dog", "idle", 0, 0)]
        if (sp == null) { placeholder(c, sx, sy, 0xFFC08A40.toInt()); return }
        val paint = if (drawBuf[o + 7] > 0) flashPaint else pixelPaint
        atlas.draw(c, sp, sx, sy, scale, paint, flip)
        plates += Plate(sx, sy - sp.ay * scale - 2 * density, "Köpek Sv ${drawBuf[o + 9].toInt()}", 0xFF9BE07A.toInt(), drawBuf[o + 8], false, null)
    }

    private fun drawZombie(c: Canvas, o: Int) {
        val kind = drawBuf[o + 1].toInt()
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val dir = drawBuf[o + 4].toInt()
        val anim = zAnimNames.getOrElse(drawBuf[o + 5].toInt()) { "idle" }
        val t = drawBuf[o + 6]
        val flash = drawBuf[o + 7]
        val hp = drawBuf[o + 8]
        val lvl = drawBuf[o + 9].toInt()
        val alpha = drawBuf[o + 10]
        val enraged = drawBuf[o + 11] > 0
        val sx = wx(x); val sy = wy(y)
        if (sx < -400 || sx > sw + 400 || sy < -200 || sy > sh + 700) return
        val name = zombieSprite.getOrElse(kind) { "walker" }
        val f = frameOf(atlas.zombieAnims, anim, t)
        val zflip = name == "dog" && dir in 1..3  // piksel zombi köpek: tek görünüm + ayna
        val zdir = if (name == "dog") 0 else dir
        var sp = atlas[spriteKey("z/$name", anim, zdir, f)]
        if (sp == null) sp = atlas[spriteKey("z/$name", "idle", zdir, 0)]
        if (sp == null) sp = atlas[spriteKey("z/walker", anim, dir, f)]
        if (sp == null) { placeholder(c, sx, sy, zombieColor.getOrElse(kind) { -1 }); return }
        // aura
        if (kind == 4 || kind == 3 || kind == 7) {
            val col = when (kind) { 4 -> 0x60A040FF; 3 -> 0x5090FF30; else -> if (enraged) 0x70FF3020 else 0x40FF6020 }
            val r = sp.w * scale * 0.6f
            fill.shader = RadialGradient(sx, sy - sp.ay * scale * 0.45f, r, col, 0, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy - sp.ay * scale * 0.45f, r, fill)
            fill.shader = null
        }
        val paint = when {
            flash > 0 -> flashPaint
            alpha < 0.99f -> { alphaPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255); alphaPaint }
            enraged -> { tintPaint.colorFilter = PorterDuffColorFilter(0x55FF2010, PorterDuff.Mode.SRC_ATOP); tintPaint }
            name == "dog" -> pixelPaint
            else -> bmpPaint
        }
        atlas.draw(c, sp, sx, sy, scale, paint, zflip)
        if (alpha > 0.35f && anim != "death") {
            val boss = kind == 7
            // sıradan zombiler: yalnızca hasar aldıysa can barı; isim yalnızca seçkin (tank, gizemli) ve boss'ta
            val elite = kind == 5 || kind == 6
            val label = when { boss -> zombieName.getOrElse(kind) { "" }; elite -> "Sv $lvl " + zombieName.getOrElse(kind) { "" }; else -> "" }
            if (label.isNotEmpty() || hp < 0.999f)
                plates += Plate(sx, sy - sp.ay * scale - 2 * density, label, zombieColor.getOrElse(kind) { -1 }, hp, boss, null)
        }
        if (kind == 3 && Settings.current.particles && Math.random() < 0.12)
            particles += Particle(x + (Math.random().toFloat() - 0.5f) * 30, y, 50f, 0f, 0f, 30f, 1.0f, 1.0f, 0xFF9AFF40.toInt(), 5f, 3)
        if (kind == 4 && Settings.current.particles && Math.random() < 0.15)
            particles += Particle(x + (Math.random().toFloat() - 0.5f) * 30, y, 80f, 0f, 0f, 50f, 0.8f, 0.8f, 0xFFB070FF.toInt(), 4f, 3)
    }

    private fun drawDrop(c: Canvas, o: Int) {
        val item = drawBuf[o + 1].toInt()
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val age = drawBuf[o + 6]
        val rarity = drawBuf[o + 7].toInt()
        val count = drawBuf[o + 8].toInt()
        val pop = drawBuf[o + 9]
        val sx = wx(x); val sy = wy(y)
        if (sx < -60 || sx > sw + 60 || sy < -60 || sy > sh + 60) return
        val bob = sin(age * 3f) * 4f * density
        val sz = 38 * density * (0.5f + 0.5f * pop)
        val col = rarityColor(rarity)
        // gölge + parıltı halkası
        fill.color = 0x50000000; c.drawOval(sx - sz * 0.4f, sy - 4 * density, sx + sz * 0.4f, sy + 4 * density, fill)
        fill.shader = RadialGradient(sx, sy - sz * 0.5f + bob, sz * 0.9f, (col and 0x00FFFFFF) or 0x70000000, 0, Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy - sz * 0.5f + bob, sz * 0.9f, fill); fill.shader = null
        if (rarity >= 2) {
            stroke.color = col; stroke.strokeWidth = 2 * density; stroke.alpha = (160 + 90 * sin(age * 5f)).toInt().coerceIn(0, 255)
            c.drawCircle(sx, sy - sz * 0.5f + bob, sz * 0.62f, stroke); stroke.alpha = 255
        }
        val sp = itemSprites.getOrNull(item)
        rect.set(sx - sz / 2, sy - sz + bob, sx + sz / 2, sy + bob)
        if (sp != null) atlas.drawFit(c, sp, rect, bmpPaint) else { fill.color = col; c.drawCircle(sx, sy - sz / 2 + bob, sz / 3, fill) }
        if (count > 1) drawText(c, "x$count", sx + sz * 0.35f, sy + bob, 11 * density, Color.WHITE)
    }

    private fun drawProj(c: Canvas, o: Int) {
        val kind = drawBuf[o + 1].toInt()
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val ang = drawBuf[o + 7]
        val sx = wx(x); val sy = wy(y - 70)
        val dx = sin(ang); val dy = cos(ang)
        when (kind) {
            0, 2 -> {
                val len = 46f * scale
                stroke.strokeWidth = 2.6f * density
                stroke.shader = LinearGradient(sx, sy, sx - dx * len, sy - dy * len,
                    if (kind == 0) 0xFFFFF0A0.toInt() else 0xFF80F0FF.toInt(), 0, Shader.TileMode.CLAMP)
                c.drawLine(sx, sy, sx - dx * len, sy - dy * len, stroke)
                stroke.shader = null
            }
            1, 4 -> {
                fill.color = 0xFF505A40.toInt()
                c.save(); c.rotate(-Math.toDegrees(ang.toDouble()).toFloat() + 90f, sx, sy)
                c.drawRoundRect(sx - 16 * scale, sy - 4 * scale, sx + 16 * scale, sy + 4 * scale, 4f, 4f, fill)
                fill.color = 0xFFFFC040.toInt(); c.drawCircle(sx - 18 * scale, sy, 6 * scale, fill)
                c.restore()
                if (Settings.current.particles) particles += Particle(x - dx * 20, y - dy * 20, 70f, 0f, 0f, 20f, 0.7f, 0.7f, 0xFF9A9690.toInt(), 8f, 1)
            }
            3 -> {
                fill.shader = RadialGradient(sx, sy, 14 * scale, 0xFFB0FF50.toInt(), 0x0040A010, Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, 14 * scale, fill); fill.shader = null
            }
        }
    }

    private fun drawVehicle(c: Canvas, o: Int) {
        val kind = drawBuf[o + 1].toInt()
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val dir = drawBuf[o + 4].toInt()
        val speed = drawBuf[o + 6]
        val driver = drawBuf[o + 7].toInt()
        val hp = drawBuf[o + 8]
        val wreck = drawBuf[o + 9] > 0
        val sx = wx(x); val sy = wy(y)
        if (sx < -400 || sx > sw + 400 || sy < -300 || sy > sh + 300) return
        val id = vehicleIds.getOrElse(kind) { "car" }
        val sp = atlas["v/$id/$dir"] ?: atlas["s/wreck0"]
        if (sp == null) { placeholder(c, sx, sy, 0xFFB03030.toInt()); return }
        val paint = if (wreck) { tintPaint.colorFilter = PorterDuffColorFilter(0x90201810.toInt(), PorterDuff.Mode.SRC_ATOP); tintPaint } else bmpPaint
        val vib = if (speed > 5 && !wreck) sin(time * 50f) * 0.6f * density else 0f
        // binicili iki tekerlekliler: kahraman eyerde oturur, hızla pedal çevirir; önden/arkadan bakışta araç önde
        val rider = driver >= 0 && kind <= 1
        val riderFront = rider && (dir == 0 || dir == 4)
        if (!riderFront) atlas.draw(c, sp, sx, sy + vib, scale, paint)
        if (rider) drawRider(c, driver, kind, dir, sx, sy + vib, speed)
        if (riderFront) atlas.draw(c, sp, sx, sy + vib, scale, paint)
        if (hp < 0.999f && !wreck) bar(c, sx, sy - sp.ay * scale - 6 * density, 56 * density, hp, 0xFF60D0FF.toInt())
        if (driver >= 0 && speed > 30) {
            val bx = -sin(dir * PI.toFloat() / 4); val by = -cos(dir * PI.toFloat() / 4)
            val back = if (kind >= 2) 120f else 55f
            if (kind >= 1 && Math.random() < 0.35) fx("exhaust", x + bx * back, y + by * back * 0.7f - 15, 55f + speed * 0.06f)
            if (speed > 160 && Math.random() < 0.45) {
                val side = if (Math.random() < 0.5) -1 else 1
                fx("dust", x + bx * back * 0.8f + by * side * 30, y + by * back * 0.55f - bx * side * 20, 70f + speed * 0.1f)
            }
        }
        if (driver >= 0 && !wreck && 1f - hud[Native.LIGHT] > 0.05f)
            lightSources += floatArrayOf(x + sin(dir * PI.toFloat() / 4) * 160, y + cos(dir * PI.toFloat() / 4) * 120 - 30, 380f, 1f)
    }

    // eyer konumu (araç çapasına göre dünya birimi), yön 0..4; 5..7 aynası
    private val seatBike = arrayOf(floatArrayOf(0f, -30f), floatArrayOf(-12f, -36f), floatArrayOf(-20f, -42f), floatArrayOf(-12f, -40f), floatArrayOf(0f, -34f))
    private val seatMoto = arrayOf(floatArrayOf(0f, -40f), floatArrayOf(-14f, -44f), floatArrayOf(-22f, -48f), floatArrayOf(-14f, -48f), floatArrayOf(0f, -42f))

    private fun drawRider(c: Canvas, driver: Int, kind: Int, dir: Int, sx: Float, sy: Float, speed: Float) {
        val actor = "h/" + heroIds.getOrElse(driver) { "kaan" }
        val f = if (speed > 5f) ((time * (5f + speed * 0.02f)).toInt() and 3) else 0
        val flip = dir in 1..3
        val key = spriteKey(actor, "ride", 0, f)
        val hs = atlas[key] ?: return
        val d5 = if (dir <= 4) dir else 8 - dir
        val tbl = if (kind == 0) seatBike else seatMoto
        val ox = tbl[d5][0] * (if (dir <= 4) 1f else -1f); val oy = tbl[d5][1]
        val seat = atlas.attach(actor, key, "seat")
        val ax = sx + ox * scale - (seat?.get(0) ?: 0f) * scale * (if (flip) -1f else 1f)
        val ay = sy + oy * scale - (seat?.get(1) ?: -45f) * scale
        atlas.draw(c, hs, ax, ay, scale, pixelPaint, flip)
    }

    private fun drawDrone(c: Canvas, o: Int) {
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val t = drawBuf[o + 6]
        val sx = wx(x); val sy = wy(y)
        val hover = sin(t * 3f) * 6 * scale
        val lift = 150 * scale
        fill.color = 0x40000000; c.drawOval(sx - 26 * scale, sy - 8 * scale, sx + 26 * scale, sy + 8 * scale, fill)
        val cy = sy - lift + hover
        fill.color = 0xFF2A2E34.toInt()
        for ((ox, oy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
            stroke.color = 0xFF2A2E34.toInt(); stroke.strokeWidth = 4 * scale
            c.drawLine(sx, cy, sx + ox * 26 * scale, cy + oy * 12 * scale, stroke)
            fill.color = 0x60B0C0D0
            c.drawOval(sx + ox * 26 * scale - 14 * scale, cy + oy * 12 * scale - 4 * scale, sx + ox * 26 * scale + 14 * scale, cy + oy * 12 * scale + 4 * scale, fill)
        }
        fill.color = 0xFF3A4048.toInt(); c.drawRoundRect(sx - 14 * scale, cy - 8 * scale, sx + 14 * scale, cy + 8 * scale, 6 * scale, 6 * scale, fill)
        fill.color = if ((t * 4).toInt() % 2 == 0) 0xFF40E0FF.toInt() else 0xFF2080A0.toInt()
        c.drawCircle(sx, cy + 2 * scale, 4 * scale, fill)
    }

    private fun drawArea(c: Canvas, o: Int) {
        val kind = drawBuf[o + 1].toInt()
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val age = drawBuf[o + 6]
        val r = drawBuf[o + 7] * scale
        val lifeF = drawBuf[o + 8]
        val sx = wx(x); val sy = wy(y)
        if (sx + r < 0 || sx - r > sw || sy + r < 0 || sy - r > sh) return
        val fade = min(1f, lifeF * 3f)
        when (kind) {
            0 -> { // zehir birikintisi
                fill.shader = RadialGradient(sx, sy, r, intArrayOf(alphaC(0xFF8AE030.toInt(), 0.75f * fade), alphaC(0xFF5AA020.toInt(), 0.5f * fade), 0),
                    floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.save(); c.scale(1f, 0.6f, sx, sy); c.drawCircle(sx, sy, r, fill); c.restore(); fill.shader = null
                if (Settings.current.particles && Math.random() < 0.15) particles += Particle(x + (Math.random().toFloat() - 0.5f) * r / scale, y + (Math.random().toFloat() - 0.5f) * r / scale * 0.5f, 0f, 0f, 0f, 30f, 0.8f, 0.8f, 0xFFB0FF60.toInt(), 3f, 3)
            }
            1 -> { // zehir bulutu
                drawFxSprite(c, "toxic", x, y - 30, drawBuf[o + 7] * 2.4f, age, alpha = (200 * fade).toInt())
                for (k in 0..4) {
                    val a = age * 0.6f + k * 1.3f
                    val cx = sx + cos(a) * r * 0.35f; val cy = sy - 40 * scale + sin(a * 1.3f) * r * 0.2f
                    fill.shader = RadialGradient(cx, cy, r * 0.7f, alphaC(0xFF90E040.toInt(), 0.35f * fade), 0, Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, r * 0.7f, fill)
                }
                fill.shader = null
            }
            2 -> { // şifa alanı
                drawFxSprite(c, "heal", x, y - drawBuf[o + 7] * 0.6f, drawBuf[o + 7] * 2.2f, age, alpha = (230 * fade).toInt())
                stroke.color = alphaC(0xFF60FF90.toInt(), 0.8f * fade); stroke.strokeWidth = 3 * density
                c.save(); c.scale(1f, 0.55f, sx, sy)
                c.drawCircle(sx, sy, r, stroke)
                fill.shader = RadialGradient(sx, sy, r, alphaC(0xFF60FF90.toInt(), 0.25f * fade), 0, Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, r, fill); fill.shader = null
                c.restore()
                if (Settings.current.particles && Math.random() < 0.3) particles += Particle(x + (Math.random().toFloat() - 0.5f) * r / scale * 1.4f, y + (Math.random().toFloat() - 0.5f) * r / scale * 0.6f, 10f, 0f, 0f, 70f, 1f, 1f, 0xFF80FFA0.toInt(), 4f, 3)
            }
            3 -> {
                stroke.color = alphaC(0xFF70C8FF.toInt(), fade); stroke.strokeWidth = 4 * density
                c.save(); c.scale(1f, 0.55f, sx, sy); c.drawCircle(sx, sy, r * (1.2f - lifeF * 0.2f), stroke); c.restore()
            }
            4 -> { // şok dalgası
                val k = 1f - lifeF
                stroke.color = alphaC(0xFFFFD0A0.toInt(), lifeF); stroke.strokeWidth = 10 * density * lifeF
                c.save(); c.scale(1f, 0.55f, sx, sy); c.drawCircle(sx, sy, r * (0.2f + 0.8f * k), stroke); c.restore()
            }
            5 -> { // patlama
                val k = 1f - lifeF
                fill.shader = RadialGradient(sx, sy - 30 * scale, r * (0.4f + k), intArrayOf(alphaC(0xFFFFF0C0.toInt(), lifeF), alphaC(0xFFFF8020.toInt(), lifeF * 0.8f), 0),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy - 30 * scale, r * (0.4f + k), fill); fill.shader = null
                fill.color = alphaC(0xFF1A1410.toInt(), 0.5f * lifeF)
                c.save(); c.scale(1f, 0.5f, sx, sy); c.drawCircle(sx, sy, r * 0.9f, fill); c.restore()
            }
        }
    }

    private fun alphaC(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * ((c ushr 24) and 255)).toInt() shl 24)

    // ------------------------------------------------------------------ sprite efektleri
    private class Fx(val name: String, var x: Float, var y: Float, val size: Float, val rot: Float, val follow: Boolean, val dur: Float, val frame: Int) {
        var t = 0f
    }
    private val fxs = ArrayList<Fx>()
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

    private fun fx(name: String, x: Float, y: Float, size: Float, rot: Float = 0f, follow: Boolean = false, dur: Float = -1f, frame: Int = -1) {
        if (!Settings.current.particles && name in setOf("dust", "exhaust", "blood", "spark")) return
        if (fxs.size > 120) fxs.removeAt(0)
        fxs += Fx(name, x, y, size, rot, follow, dur, frame)
    }

    private fun updateFx(dt: Float) {
        val it = fxs.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t += dt
            val m = atlas.fx[f.name]
            val done = when {
                m == null -> true
                f.dur > 0 -> f.t > f.dur
                m[2] > 0f -> false
                else -> f.t * m[1] >= m[0]
            }
            if (done) it.remove()
        }
    }

    /** Döngüsel/tek seferlik efekti dünya konumunda çizer (size: dünya pikseli çap). */
    private fun drawFxSprite(c: Canvas, name: String, wxp: Float, wyp: Float, size: Float, t: Float, rot: Float = 0f, alpha: Int = 255, fixedFrame: Int = -1) {
        val m = atlas.fx[name] ?: return
        val frames = m[0].toInt()
        val fr = if (fixedFrame >= 0) fixedFrame.coerceAtMost(frames - 1) else {
            val f = (t * m[1]).toInt()
            if (m[2] > 0f) f.mod(frames) else f.coerceAtMost(frames - 1)
        }
        val sp = atlas["fx/$name/$fr"] ?: return
        val paint = if (m[3] > 0f) addPaint else alphaPaint
        paint.alpha = alpha
        val sx = wx(wxp); val sy = wy(wyp)
        val k = size * scale / sp.w
        if (rot != 0f) { c.save(); c.rotate(rot, sx, sy) }
        atlas.draw(c, sp, sx, sy, k, paint)
        if (rot != 0f) c.restore()
        paint.alpha = 255
    }

    private fun drawFx(c: Canvas) {
        for (f in fxs) {
            if (f.follow) { f.x = hud[Native.PX]; f.y = hud[Native.PY] - 60 }
            val fade = if (f.dur > 0) ((f.dur - f.t) / 0.4f).coerceIn(0f, 1f) else 1f
            drawFxSprite(c, f.name, f.x, f.y, f.size, f.t, f.rot, (255 * fade).toInt(), f.frame)
        }
    }

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            val k = (p.life / p.max).coerceIn(0f, 1f)
            val sx = wx(p.x); val sy = wy(p.y) - p.z * scale
            when (p.kind) {
                0 -> { fill.color = alphaC(p.color, min(1f, k * 2f)); c.drawCircle(sx, sy, p.size * scale * density * 0.6f, fill) }
                1 -> { // duman/kıvılcım: büyüyerek söner
                    fill.color = alphaC(p.color, k * 0.7f)
                    c.drawCircle(sx, sy, p.size * scale * density * (1.6f - k), fill)
                }
                3 -> { fill.color = alphaC(p.color, k); c.drawCircle(sx, sy, p.size * scale * density * 0.5f, fill) }
            }
        }
    }

    private fun drawTracers(c: Canvas) {
        for (t in tracers) {
            stroke.color = alphaC(t.color, t.life / 0.12f); stroke.strokeWidth = t.width * density
            c.drawLine(wx(t.x1), wy(t.y1), wx(t.x2), wy(t.y2), stroke)
        }
    }

    // gece ışık haritası (çeyrek çözünürlük)
    private var lightBmp: Bitmap? = null
    private var lightCanvas: Canvas? = null

    private fun drawLighting(c: Canvas) {
        val light = hud[Native.LIGHT]
        val night = (1f - light).coerceIn(0f, 1f)
        if (night < 0.04f) { lightSources.clear(); return }
        val lw = sw / 4; val lh = sh / 4
        if (lightBmp == null || lightBmp!!.width != lw || lightBmp!!.height != lh) {
            lightBmp?.recycle()
            lightBmp = Bitmap.createBitmap(max(1, lw), max(1, lh), Bitmap.Config.ARGB_8888)
            lightCanvas = Canvas(lightBmp!!)
        }
        val lc = lightCanvas!!
        val amb = 1f - night * 1.15f
        val r = (amb * 255 * 0.55f + 20).toInt().coerceIn(0, 255)
        val g = (amb * 255 * 0.62f + 25).toInt().coerceIn(0, 255)
        val b = (amb * 255 * 0.85f + 45).toInt().coerceIn(0, 255)
        lc.drawColor(Color.rgb(r, g, b), PorterDuff.Mode.SRC)
        fun glow(x: Float, y: Float, rad: Float, col: Int, k: Float) {
            val lx = wx(x) / 4; val ly = wy(y) / 4; val lr = rad * scale / 4
            lightPaint.shader = RadialGradient(lx, ly, lr, alphaC(col, k), 0, Shader.TileMode.CLAMP)
            lightPaint.xfermode = addMode
            lc.drawCircle(lx, ly, lr, lightPaint)
        }
        // oyuncu feneri
        glow(hud[Native.PX], hud[Native.PY] - 50, 520f, 0xFFFFE8C0.toInt(), 0.85f)
        for (ls in lightSources) glow(ls[0], ls[1], ls[2], if (ls[3] == 2f) 0xFFFFA040.toInt() else 0xFFFFE0A0.toInt(), 0.9f)
        for (f in flashes) glow(f.x, f.y, f.r, f.color, f.life / f.max)
        lightSources.clear()
        rect.set(0f, 0f, sw.toFloat(), sh.toFloat())
        c.drawBitmap(lightBmp!!, null, rect, multiply)
    }

    /** Konuşma balonları: isim → (metin, bitiş zamanı) */
    private val bubbles = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Float>>()
    private val plateRects = ArrayList<RectF>()

    /** Yazılan sohbet mesajını konuşanın kafasının üstünde 5 sn gösterir. */
    fun bubble(name: String, text: String) { bubbles[name] = text.take(60) to time + 5f }

    private fun drawPlates(c: Canvas) {
        plateRects.clear()
        val d = density
        // kameraya yakın olanlar önce: kalabalıkta en fazla 10 isim, üst üste binen isimler yukarı kaydırılır
        val cx = sw / 2f; val cy = sh / 2f
        plates.sortBy { hypot(it.x - cx, it.y - cy) }
        var named = 0
        for (p in plates) {
            val ts = if (p.boss) 15 * d else 11.5f * d
            var y = p.y - (if (p.hp >= 0) 10 * d else 2 * d)
            if (p.hp >= 0) bar(c, p.x, p.y - 3 * d, if (p.boss) 120 * d else 46 * d, p.hp,
                if (p.boss) 0xFFFF4A3A.toInt() else 0xFFE04848.toInt())
            if (p.text.isEmpty()) continue
            if (named >= 10 && !p.boss) continue
            val w = text.apply { typeface = fontBold; textSize = ts }.measureText(p.text) / 2 + 3 * d
            val lines = if (p.sub != null) 2 else 1
            val r = RectF(p.x - w, y - ts * lines - 2 * d, p.x + w, y + 3 * d)
            var tries = 0
            while (plateRects.any { RectF.intersects(it, r) } && tries < 4) { r.offset(0f, -(ts + 2 * d)); y -= ts + 2 * d; tries++ }
            if (tries >= 4 && !p.boss) continue
            plateRects += r
            named++
            drawText(c, p.text, p.x, y, ts, p.color, center = true)
            var top = y - ts
            if (p.sub != null) { drawText(c, p.sub, p.x, y - ts * 1.1f, ts * 0.85f, p.subColor, center = true); top -= ts }
            // konuşma balonu (oyuncular)
            bubbles[p.text]?.let { (msg, until) ->
                if (until < time) { bubbles.remove(p.text); return@let }
                val bs = 12.5f * d
                val bw = text.apply { typeface = fontBold; textSize = bs }.measureText(msg) / 2 + 8 * d
                val bb = RectF(p.x - bw, top - bs - 14 * d, p.x + bw, top - 4 * d)
                fill.color = 0xF0F4F0E6.toInt(); c.drawRoundRect(bb, 6 * d, 6 * d, fill)
                path.reset(); path.moveTo(p.x - 5 * d, bb.bottom); path.lineTo(p.x + 5 * d, bb.bottom); path.lineTo(p.x, bb.bottom + 6 * d); path.close()
                c.drawPath(path, fill)
                drawText(c, msg, p.x, bb.bottom - 5 * d, bs, 0xFF1A1A1A.toInt(), center = true)
            }
        }
    }

    private fun drawFloaters(c: Canvas) {
        for (f in floaters) {
            val k = (f.life / f.max)
            val pop = if (k > 0.85f) 1f + (k - 0.85f) * 3f else 1f
            drawText(c, f.text, wx(f.x), wy(f.y), f.size * density * pop * 0.75f, alphaC(f.color, min(1f, k * 2.5f)), center = true)
        }
    }

    private fun drawText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, center: Boolean = false, font: Typeface = fontBold) {
        text.typeface = font; textStroke.typeface = font
        text.textSize = size; textStroke.textSize = size
        textStroke.strokeWidth = size * 0.18f
        text.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        textStroke.textAlign = text.textAlign
        textStroke.alpha = (color ushr 24) and 255
        c.drawText(s, x, y, textStroke)
        text.color = color
        c.drawText(s, x, y, text)
    }

    private fun bar(c: Canvas, cx: Float, y: Float, w: Float, v: Float, color: Int) {
        val h = 5 * density
        rect.set(cx - w / 2 - 1.5f * density, y - h / 2 - 1.5f * density, cx + w / 2 + 1.5f * density, y + h / 2 + 1.5f * density)
        fill.color = 0xC0101418.toInt(); c.drawRoundRect(rect, h, h, fill)
        rect.set(cx - w / 2, y - h / 2, cx - w / 2 + w * v.coerceIn(0f, 1f), y + h / 2)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, lighten(color, 1.35f), color, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, h / 2, h / 2, fill); fill.shader = null
    }

    private fun lighten(c: Int, k: Float): Int = Color.argb(Color.alpha(c), min(255, (Color.red(c) * k).toInt()), min(255, (Color.green(c) * k).toInt()), min(255, (Color.blue(c) * k).toInt()))

    private fun placeholder(c: Canvas, x: Float, y: Float, col: Int) {
        fill.color = 0x50000000; c.drawOval(x - 20 * scale, y - 6 * scale, x + 20 * scale, y + 6 * scale, fill)
        fill.color = col; c.drawRoundRect(x - 14 * scale, y - 100 * scale, x + 14 * scale, y, 12 * scale, 12 * scale, fill)
    }

    // ------------------------------------------------------------------ HUD
    private val portraitCache = HashMap<Int, Sprite?>()

    private fun drawHud(c: Canvas) {
        val d = density
        val hp = hud[Native.HP]; val maxHp = max(1f, hud[Native.MAXHP])
        // kırmızı vinyet (hasar / düşük can)
        val low = ((0.3f - hp / maxHp) / 0.3f).coerceIn(0f, 1f) * (0.5f + 0.5f * sin(time * 5f))
        val vr = max(vignetteRed, low * 0.6f)
        if (vr > 0.01f) {
            fill.shader = RadialGradient(sw / 2f, sh / 2f, max(sw, sh) * 0.7f, intArrayOf(0, 0, alphaC(0xFFB00010.toInt(), vr)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), fill); fill.shader = null
        }
        // ---- sol üst: portre + barlar
        val px0 = 16 * d; val py0 = 14 * d
        val pr = 30 * d
        val ch = hud[Native.CHARACTER].toInt()
        val pcx = px0 + pr; val pcy = py0 + pr
        fill.shader = RadialGradient(pcx, pcy, pr, 0xFF2E4A70.toInt(), 0xFF101828.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(pcx, pcy, pr, fill); fill.shader = null
        val ps = portraitCache.getOrPut(ch) { atlas["l/" + heroIds.getOrElse(ch) { "kaan" } + "/0_0"] ?: atlas["h/" + heroIds.getOrElse(ch) { "kaan" } + "/idle_0_0"] }
        if (ps != null) {
            c.save()
            path.reset(); path.addCircle(pcx, pcy, pr - 2 * d, Path.Direction.CW); c.clipPath(path)
            val bmp = atlas.page(ps.page)
            if (bmp != null) {
                // baş-omuz kadrajı
                val hh = ps.h * 0.42f
                srcR.set(ps.src.left, ps.src.top, ps.src.right, (ps.src.top + hh).toInt())
                val k = (pr * 2.1f) / hh
                rect.set(pcx - ps.w * k / 2, pcy - pr * 0.95f, pcx + ps.w * k / 2, pcy - pr * 0.95f + hh * k)
                c.drawBitmap(bmp, srcR, rect, bmpPaint)
            }
            c.restore()
        }
        stroke.color = 0xFFFFD34A.toInt(); stroke.strokeWidth = 2.5f * d
        c.drawCircle(pcx, pcy, pr, stroke)
        // seviye rozeti
        fill.color = 0xFF101828.toInt(); c.drawCircle(pcx + pr * 0.72f, pcy + pr * 0.72f, 11 * d, fill)
        stroke.strokeWidth = 1.5f * d; c.drawCircle(pcx + pr * 0.72f, pcy + pr * 0.72f, 11 * d, stroke)
        drawText(c, hud[Native.LEVEL].toInt().toString(), pcx + pr * 0.72f, pcy + pr * 0.72f + 4.5f * d, 12 * d, 0xFFFFE9A8.toInt(), true)
        val bx = pcx + pr + 10 * d; val bw = 190 * d
        hudBar(c, bx, py0 + 6 * d, bw, 14 * d, hp / maxHp, 0xFFE8483C.toInt(), "${hp.roundToInt()} / ${maxHp.roundToInt()}",
            hud[Native.SHIELD] / maxHp)
        hudBar(c, bx, py0 + 25 * d, bw * 0.8f, 8 * d, hud[Native.ST] / max(1f, hud[Native.MAXST]), 0xFFF2C440.toInt(), null, 0f)
        hudBar(c, bx, py0 + 38 * d, bw * 0.8f, 6 * d, hud[Native.XP] / max(1f, hud[Native.XPNEXT]), 0xFF5AC8FF.toInt(), null, 0f)
        if (hud[Native.POISON] > 0) drawText(c, "☠ Zehirlendin", bx, py0 + 60 * d, 12 * d, 0xFF9AFF40.toInt())
        // ---- üst orta: bölge, saat
        val zone = zones.getOrElse(hud[Native.ZONE].toInt()) { "" }
        drawText(c, if (serverTitle.isNotEmpty()) "$serverTitle => $zone" else zone, sw / 2f, 26 * d, 15 * d, 0xFFFFE9A8.toInt(), true, fontDisplay)
        val light = hud[Native.LIGHT]
        drawText(c, if (light < 0.6f) "☾ Gece" else "☀ Gündüz", sw / 2f, 42 * d, 11 * d, 0xFFCCD6E0.toInt(), true)
        // boss barı
        if (hud[Native.BOSS_HP] >= 0 && hud[Native.BOSS_MAX] > 0) {
            val w = sw * 0.42f
            drawText(c, "TİTAN", sw / 2f, 64 * d, 16 * d, 0xFFFF6A50.toInt(), true, fontDisplay)
            hudBar(c, sw / 2f - w / 2, 70 * d, w, 12 * d, hud[Native.BOSS_HP] / hud[Native.BOSS_MAX], 0xFFD02A20.toInt(),
                "${hud[Native.BOSS_HP].roundToInt()}", 0f)
        }
        // ---- sağ üst: altın + radar
        val gold = hud[Native.GOLD].toInt()
        val rr = 58 * d
        val rcx = sw - 16 * d - rr; val rcy = 16 * d + rr
        drawRadar(c, rcx, rcy, rr)
        // altın: radarın hemen altında (sağ üst tuşlarla çakışmaz)
        val gs = "%,d".format(gold).replace(',', '.')
        val gw = text.apply { typeface = fontBold; textSize = 15 * d }.measureText(gs)
        drawCoin(c, rcx - gw / 2 - 6 * d, rcy + rr + 18 * d, 8 * d)
        drawText(c, gs, rcx - gw / 2 + 6 * d, rcy + rr + 23 * d, 15 * d, 0xFFFFD34A.toInt())
        // ---- araç durumu
        if (hud[Native.IN_VEHICLE] > 0) {
            hudBar(c, 16 * d, py0 + 76 * d, 150 * d, 8 * d, hud[Native.VEHICLE_HP], 0xFF60D0FF.toInt(), null, 0f)
            drawText(c, "Yakıt %${hud[Native.FUEL].roundToInt()}", 16 * d, py0 + 98 * d, 12 * d, 0xFFFFC870.toInt())
        }
        // ---- alt: XP şeridi + koordinat
        val xpv = hud[Native.XP]; val xpn = max(1f, hud[Native.XPNEXT])
        fill.color = 0xB0101010.toInt(); c.drawRect(0f, sh - 5 * d, sw.toFloat(), sh.toFloat(), fill)
        fill.color = 0xFFE8C840.toInt(); c.drawRect(0f, sh - 5 * d, sw * (xpv / xpn).coerceIn(0f, 1f), sh.toFloat(), fill)
        drawText(c, "Sv. ${hud[Native.LEVEL].toInt()}  ${xpv.toInt()}/${xpn.toInt()}  %${"%.2f".format(xpv / xpn * 100)}", 8 * d, sh - 8 * d, 11 * d, 0xFFFFF2B0.toInt())
        drawText(c, "X: ${(hud[Native.PX] / tile).toInt()}  Y: ${(hud[Native.PY] / tile).toInt()}", 8 * d, sh - 22 * d, 12 * d, 0xC0FFFFFF.toInt())
        // ---- sohbet satırları
        synchronized(chatLines) {
            var y = sh - stickR * 3.4f
            for ((line, life) in chatLines.reversed()) {
                drawText(c, line, 16 * d, y, 12.5f * d, alphaC(0xFFEEF2F0.toInt(), min(1f, life)))
                y -= 17 * d
            }
        }
        // ---- afiş
        if (bannerT > 0) {
            val k = bannerT / 2.8f
            val a = if (k > 0.85f) (1f - k) / 0.15f else if (k < 0.2f) k / 0.2f else 1f
            val y = sh * 0.28f
            fill.shader = LinearGradient(sw * 0.2f, 0f, sw * 0.8f, 0f, intArrayOf(0, alphaC(0xCC0A0E16.toInt(), a), 0), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, y - 44 * d, sw.toFloat(), y + 26 * d, fill); fill.shader = null
            drawText(c, banner, sw / 2f, y, 34 * d * (if (k > 0.85f) 1f + (k - 0.85f) else 1f), alphaC(bannerColor, a), true, fontDisplay)
            drawText(c, bannerSub, sw / 2f, y + 19 * d, 14 * d, alphaC(0xFFEEF2F0.toInt(), a), true)
        }
    }

    private fun hudBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, v: Float, color: Int, label: String?, over: Float) {
        val d = density
        rect.set(x - 2 * d, y - 2 * d, x + w + 2 * d, y + h + 2 * d)
        fill.color = 0xD0101418.toInt(); c.drawRoundRect(rect, h, h, fill)
        rect.set(x, y, x + w * v.coerceIn(0f, 1f), y + h)
        fill.shader = LinearGradient(0f, y, 0f, y + h, lighten(color, 1.4f), color, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, h / 2, h / 2, fill); fill.shader = null
        fill.color = 0x30FFFFFF; c.drawRoundRect(x, y, x + w * v.coerceIn(0f, 1f), y + h * 0.45f, h / 2, h / 2, fill)
        if (over > 0) {
            rect.set(x, y, x + w * over.coerceIn(0f, 1f), y + h)
            fill.color = 0x9070C8FF.toInt(); c.drawRoundRect(rect, h / 2, h / 2, fill)
        }
        if (label != null) drawText(c, label, x + w / 2, y + h * 0.8f, h * 0.85f, Color.WHITE, true)
    }

    private fun drawCoin(c: Canvas, x: Float, y: Float, r: Float) {
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, 0xFFFFF0A0.toInt(), 0xFFC88A10.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fill); fill.shader = null
        stroke.color = 0xFF8A5A08.toInt(); stroke.strokeWidth = r * 0.18f; c.drawCircle(x, y, r * 0.7f, stroke)
    }

    private fun drawRadar(c: Canvas, cx: Float, cy: Float, r: Float) {
        val d = density
        fill.shader = RadialGradient(cx, cy, r, 0xC0182030.toInt(), 0xE0080C14.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null
        stroke.color = 0x40FFFFFF; stroke.strokeWidth = 1 * d
        c.drawCircle(cx, cy, r * 0.5f, stroke)
        c.drawLine(cx - r, cy, cx + r, cy, stroke); c.drawLine(cx, cy - r, cx, cy + r, stroke)
        // tarama
        val a = (time * 1.6f) % (2 * PI.toFloat())
        fill.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(0, 0, 0x4060FF90), floatArrayOf(0f, 0.85f, 1f))
        c.save(); c.rotate(Math.toDegrees(a.toDouble()).toFloat(), cx, cy); c.drawCircle(cx, cy, r, fill); c.restore(); fill.shader = null
        val px = hud[Native.PX]; val py = hud[Native.PY]
        val range = 1600f
        for (i in 0 until radarN) {
            val x = radarBuf[i * 3]; val y = radarBuf[i * 3 + 1]; val t = radarBuf[i * 3 + 2].toInt()
            var dx = (x - px) / range; var dy = (y - py) / range
            val l = hypot(dx, dy)
            if (l > 1f) { if (t == 0) continue; dx /= l; dy /= l }
            val col = when (t) { 0 -> 0xFFFF5040.toInt(); 1 -> 0xFFFF2020.toInt(); 2 -> 0xFF60D0FF.toInt(); 3 -> 0xFFFFD34A.toInt(); else -> 0xFF7FFFE0.toInt() }
            fill.color = col
            c.drawCircle(cx + dx * r * 0.92f, cy + dy * r * 0.92f, (if (t == 1) 5f else if (t == 0) 2.4f else 3.5f) * d, fill)
        }
        // oyuncu oku
        path.reset()
        path.moveTo(cx, cy - 6 * d); path.lineTo(cx + 4.5f * d, cy + 5 * d); path.lineTo(cx - 4.5f * d, cy + 5 * d); path.close()
        fill.color = Color.WHITE; c.drawPath(path, fill)
        stroke.color = 0xFFFFD34A.toInt(); stroke.strokeWidth = 2 * d
        c.drawCircle(cx, cy, r, stroke)
    }

    // ------------------------------------------------------------------ kontroller
    private fun drawControls(c: Canvas) {
        val s = Settings.current
        val op = s.joystickOpacity
        for (st in listOf(moveStick, atkStick)) {
            val isAtk = st === atkStick
            fill.shader = RadialGradient(st.cx, st.cy, stickR, intArrayOf(alphaC(0xFF000000.toInt(), 0.1f * op), alphaC(0xFF000000.toInt(), 0.35f * op)), null, Shader.TileMode.CLAMP)
            c.drawCircle(st.cx, st.cy, stickR, fill); fill.shader = null
            stroke.color = alphaC(if (isAtk) 0xFFFF7A5A.toInt() else 0xFFFFFFFF.toInt(), 0.55f * op); stroke.strokeWidth = 2.5f * density
            c.drawCircle(st.cx, st.cy, stickR, stroke)
            val kr = stickR * 0.45f
            fill.shader = RadialGradient(st.x - kr * 0.3f, st.y - kr * 0.3f, kr * 1.4f,
                alphaC(if (isAtk) 0xFFFF9A7A.toInt() else 0xFFF4F6F8.toInt(), 0.95f * op), alphaC(if (isAtk) 0xFFB03A28.toInt() else 0xFF8A96A4.toInt(), 0.9f * op), Shader.TileMode.CLAMP)
            c.drawCircle(st.x, st.y, kr, fill); fill.shader = null
            if (isAtk && !st.active) drawIcon(c, "attack", st.x, st.y, kr * 0.9f, alphaC(0xFFFFFFFF.toInt(), op))
            if (isAtk && st.active) {
                // nişan çizgisi
                val l = hypot(st.vx, st.vy)
                if (l > 0.1f) {
                    val px = wx(hud[Native.PX]); val py = wy(hud[Native.PY] - 60)
                    stroke.color = 0x60FFFFFF; stroke.strokeWidth = 2 * density
                    stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10 * density, 8 * density), time * 40)
                    c.drawLine(px, py, px + st.vx / l * 160 * density, py + st.vy / l * 160 * density, stroke)
                    stroke.pathEffect = null
                }
            }
        }
        // hızlı tuşlar
        btnVehicle.visible = hud[Native.NEAR_VEHICLE] > 0 || hud[Native.IN_VEHICLE] > 0
        btnInteract.visible = hud[Native.NEAR_MERCHANT] > 0 || hud[Native.NEAR_BENCH] > 0
        nearCache = Online.nearest(220f)
        val nr = nearCache
        btnTrade.visible = nr != null && nr.dist < 170f && hud[Native.IN_VEHICLE] < 0.5f && rideUid == null
        btnRide.visible = rideUid != null || (nr != null && nr.vehicle >= 2 && hud[Native.IN_VEHICLE] < 0.5f)
        btnReload.visible = hud[Native.AMMO] >= 0
        val cd = hud[Native.ABILITY_CD]
        if (cd > abilityCdMax || cd <= 0f) abilityCdMax = max(cd, 0.1f)
        roundBtn(c, btnAbility, "roll", if (cd > 0) "${cd.roundToInt()}" else null, 0xFF5A3A8A.toInt(), cd > 0)
        val ammo = hud[Native.AMMO]
        val wlabel = if (ammo >= 0) "${hud[Native.MAG].toInt()}/${ammo.toInt()}" else null
        roundBtn(c, btnWeapon, "weapon", wlabel, 0xFF3A6AA0.toInt(), false)
        if (btnReload.visible) roundBtn(c, btnReload, "reload", null, 0xFF3A4A5A.toInt(), hud[Native.RELOADING] > 0)
        if (btnVehicle.visible) roundBtn(c, btnVehicle, if (hud[Native.IN_VEHICLE] > 0) "exit" else "car", null, 0xFF2E8A5A.toInt(), false)
        if (btnTrade.visible) roundBtn(c, btnTrade, "trade", null, 0xFF2E7AA0.toInt(), false)
        if (btnRide.visible) roundBtn(c, btnRide, if (rideUid != null) "exit" else "car", null, 0xFF8A5AC0.toInt(), false)
        if (btnInteract.visible) roundBtn(c, btnInteract, if (hud[Native.NEAR_MERCHANT] > 0) "shop" else "craft", null, 0xFFC08A20.toInt(), false)
    }

    private fun roundBtn(c: Canvas, b: Btn, icon: String, label: String?, color: Int, dim: Boolean, small: Boolean = false) {
        val cx = b.r.centerX(); val cy = b.r.centerY(); val r = b.r.width() / 2 * (if (b.pressed >= 0) 0.92f else 1f)
        val op = if (small) 0.92f else Settings.current.joystickOpacity.coerceAtLeast(0.55f)
        fill.color = alphaC(0xFF000000.toInt(), 0.35f * op); c.drawCircle(cx, cy + 2 * density, r, fill)
        fill.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.5f, alphaC(lighten(color, 1.6f), op), alphaC(color, op), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null
        stroke.color = alphaC(0xFFFFFFFF.toInt(), 0.35f * op + b.flash * 0.5f); stroke.strokeWidth = 2 * density
        c.drawCircle(cx, cy, r, stroke)
        drawIcon(c, icon, cx, cy - (if (label != null && !dim) r * 0.12f else 0f), r * 0.55f, alphaC(Color.WHITE, if (dim) 0.4f else 1f))
        if (icon == "roll" && dim) {
            fill.color = 0x80000000.toInt()
            val cd = hud[Native.ABILITY_CD]
            c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * (cd / abilityCdMax).coerceIn(0f, 1f), true, fill)
        }
        if (label != null) drawText(c, label, cx, if (dim) cy + r * 0.2f else cy + r * 0.62f, r * (if (dim) 0.62f else 0.36f), Color.WHITE, true)
    }

    /** Basit vektör ikonlar (tuşlar için). */
    private fun drawIcon(c: Canvas, name: String, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = s * 0.16f; stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        fill.color = color
        path.reset()
        when (name) {
            "attack" -> { // kılıç
                c.drawLine(cx - s * 0.6f, cy + s * 0.6f, cx + s * 0.6f, cy - s * 0.6f, stroke)
                c.drawLine(cx - s * 0.55f, cy + s * 0.15f, cx - s * 0.15f, cy + s * 0.55f, stroke)
            }
            "roll" -> { // takla: kıvrık ok
                rect.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f)
                c.drawArc(rect, 200f, 280f, false, stroke)
                val a = Math.toRadians(120.0)
                val ex = cx + cos(a).toFloat() * s * 0.7f; val ey = cy + sin(a).toFloat() * s * 0.7f
                path.moveTo(ex - s * 0.35f, ey - s * 0.05f); path.lineTo(ex, ey); path.lineTo(ex + s * 0.05f, ey - s * 0.38f)
                c.drawPath(path, stroke)
            }
            "ability" -> { // yıldız / güç
                for (k in 0 until 10) {
                    val a = -PI / 2 + k * PI / 5
                    val rr = if (k % 2 == 0) s else s * 0.45f
                    val x = cx + cos(a).toFloat() * rr; val y = cy + sin(a).toFloat() * rr
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, fill)
            }
            "weapon" -> { // tabanca silueti
                path.moveTo(cx - s * 0.8f, cy - s * 0.35f); path.lineTo(cx + s * 0.8f, cy - s * 0.35f); path.lineTo(cx + s * 0.8f, cy)
                path.lineTo(cx - s * 0.05f, cy); path.lineTo(cx - s * 0.2f, cy + s * 0.7f); path.lineTo(cx - s * 0.6f, cy + s * 0.7f)
                path.lineTo(cx - s * 0.5f, cy); path.lineTo(cx - s * 0.8f, cy); path.close(); c.drawPath(path, fill)
            }
            "reload" -> {
                c.drawArc(cx - s * 0.6f, cy - s * 0.6f, cx + s * 0.6f, cy + s * 0.6f, -60f, 280f, false, stroke)
                path.moveTo(cx + s * 0.55f, cy - s * 0.75f); path.lineTo(cx + s * 0.6f, cy - s * 0.25f); path.lineTo(cx + s * 0.15f, cy - s * 0.35f); path.close(); c.drawPath(path, fill)
            }
            "car" -> {
                rect.set(cx - s * 0.8f, cy - s * 0.1f, cx + s * 0.8f, cy + s * 0.4f); c.drawRoundRect(rect, s * 0.15f, s * 0.15f, fill)
                path.moveTo(cx - s * 0.5f, cy - s * 0.1f); path.lineTo(cx - s * 0.3f, cy - s * 0.5f); path.lineTo(cx + s * 0.35f, cy - s * 0.5f); path.lineTo(cx + s * 0.55f, cy - s * 0.1f); path.close()
                c.drawPath(path, fill)
                fill.color = 0xFF1A1A1A.toInt(); c.drawCircle(cx - s * 0.45f, cy + s * 0.45f, s * 0.2f, fill); c.drawCircle(cx + s * 0.45f, cy + s * 0.45f, s * 0.2f, fill)
            }
            "exit" -> {
                rect.set(cx - s * 0.6f, cy - s * 0.7f, cx + s * 0.1f, cy + s * 0.7f); c.drawRect(rect, stroke)
                c.drawLine(cx - s * 0.1f, cy, cx + s * 0.75f, cy, stroke)
                c.drawLine(cx + s * 0.45f, cy - s * 0.3f, cx + s * 0.75f, cy, stroke); c.drawLine(cx + s * 0.45f, cy + s * 0.3f, cx + s * 0.75f, cy, stroke)
            }
            "shop" -> {
                path.moveTo(cx - s * 0.7f, cy - s * 0.3f); path.lineTo(cx + s * 0.7f, cy - s * 0.3f); path.lineTo(cx + s * 0.55f, cy + s * 0.7f); path.lineTo(cx - s * 0.55f, cy + s * 0.7f); path.close()
                c.drawPath(path, fill)
                c.drawArc(cx - s * 0.35f, cy - s * 0.8f, cx + s * 0.35f, cy - s * 0.0f, 180f, 180f, false, stroke)
            }
            "craft" -> { // çekiç
                c.drawLine(cx - s * 0.5f, cy + s * 0.7f, cx + s * 0.25f, cy - s * 0.15f, stroke)
                c.save(); c.rotate(45f, cx + s * 0.3f, cy - s * 0.3f)
                rect.set(cx - s * 0.05f, cy - s * 0.5f, cx + s * 0.75f, cy - s * 0.1f); c.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill); c.restore()
            }
            "bag" -> {
                rect.set(cx - s * 0.65f, cy - s * 0.35f, cx + s * 0.65f, cy + s * 0.75f); c.drawRoundRect(rect, s * 0.25f, s * 0.25f, fill)
                c.drawArc(cx - s * 0.35f, cy - s * 0.8f, cx + s * 0.35f, cy - s * 0.1f, 180f, 180f, false, stroke)
            }
            "chat" -> {
                rect.set(cx - s * 0.8f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.4f); c.drawRoundRect(rect, s * 0.3f, s * 0.3f, fill)
                path.moveTo(cx - s * 0.4f, cy + s * 0.3f); path.lineTo(cx - s * 0.55f, cy + s * 0.8f); path.lineTo(cx, cy + s * 0.35f); path.close(); c.drawPath(path, fill)
            }
            "trade" -> {
                c.drawLine(cx - s * 0.7f, cy - s * 0.3f, cx + s * 0.7f, cy - s * 0.3f, stroke)
                c.drawLine(cx + s * 0.4f, cy - s * 0.6f, cx + s * 0.7f, cy - s * 0.3f, stroke)
                c.drawLine(cx - s * 0.7f, cy + s * 0.3f, cx + s * 0.7f, cy + s * 0.3f, stroke)
                c.drawLine(cx - s * 0.4f, cy + s * 0.6f, cx - s * 0.7f, cy + s * 0.3f, stroke)
            }
            "menu" -> for (k in -1..1) c.drawLine(cx - s * 0.6f, cy + k * s * 0.4f, cx + s * 0.6f, cy + k * s * 0.4f, stroke)
            "map" -> {
                path.moveTo(cx - s * 0.75f, cy - s * 0.5f); path.lineTo(cx - s * 0.25f, cy - s * 0.7f); path.lineTo(cx + s * 0.25f, cy - s * 0.5f)
                path.lineTo(cx + s * 0.75f, cy - s * 0.7f); path.lineTo(cx + s * 0.75f, cy + s * 0.5f); path.lineTo(cx + s * 0.25f, cy + s * 0.7f)
                path.lineTo(cx - s * 0.25f, cy + s * 0.5f); path.lineTo(cx - s * 0.75f, cy + s * 0.7f); path.close(); c.drawPath(path, stroke)
            }
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    // ------------------------------------------------------------------ dokunma
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = Settings.current
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val x = e.getX(i); val y = e.getY(i)
                for (b in buttons) {
                    if (!b.visible) continue
                    val r = b.r.width() / 2 * 1.2f
                    if (hypot(x - b.r.centerX(), y - b.r.centerY()) < r) { b.pressed = id; b.flash = 1f; press(b); return true }
                }
                val leftSide = x < sw / 2f
                val moveSide = if (s.leftHanded) !leftSide else leftSide
                val st = if (moveSide) moveStick else atkStick
                if (!st.active && y > sh * 0.28f) {
                    st.pointer = id
                    if (s.moveDynamic || st === atkStick) { st.cx = x; st.cy = y } else { st.cx = st.homeX; st.cy = st.homeY }
                    if (st === atkStick && !s.moveDynamic) { st.cx = st.homeX; st.cy = st.homeY }
                    moveStickTo(st, x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i)
                if (moveStick.pointer == id) moveStickTo(moveStick, e.getX(i), e.getY(i))
                if (atkStick.pointer == id) moveStickTo(atkStick, e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = e.getPointerId(e.actionIndex)
                for (st in listOf(moveStick, atkStick)) if (st.pointer == id || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    st.pointer = -1; st.vx = 0f; st.vy = 0f; st.cx = st.homeX; st.cy = st.homeY; st.x = st.cx; st.y = st.cy
                }
                for (b in buttons) if (b.pressed == id || e.actionMasked == MotionEvent.ACTION_CANCEL) b.pressed = -1
                if (e.actionMasked == MotionEvent.ACTION_UP) for (b in buttons) b.pressed = -1
            }
        }
        return true
    }

    private fun moveStickTo(st: Stick, x: Float, y: Float) {
        val s = Settings.current
        var dx = x - st.cx; var dy = y - st.cy
        val l = hypot(dx, dy)
        val max = stickR
        if (l > max) {
            // dinamik joystick parmağı takip eder
            if (st === moveStick && s.moveDynamic) { st.cx += dx / l * (l - max); st.cy += dy / l * (l - max) }
            dx = dx / l * max; dy = dy / l * max
        }
        st.x = st.cx + dx; st.y = st.cy + dy
        var vx = dx / max; var vy = dy / max
        val m = hypot(vx, vy)
        val dz = if (st === moveStick) s.moveDeadZone else 0.05f
        if (m < dz) { vx = 0f; vy = 0f } else {
            val sens = if (st === moveStick) s.moveSensitivity else s.attackSensitivity
            val k = (((m - dz) / (1 - dz)) * sens).coerceAtMost(1f) / m
            vx *= k; vy *= k
        }
        st.vx = vx; st.vy = vy
    }

    private fun press(b: Btn) {
        Audio.play("click", 0.6f, bus = Audio.Bus.Ui)
        vibrate(15, 90)
        when (b.id) {
            "ability" -> { val r = GameSession.cmd("ability"); if (!r.ok && r.msg.isNotEmpty()) ui.onToast(r.msg) }
            "weapon" -> { val r = GameSession.cmd("cycle_weapon"); if (r.msg.isNotEmpty()) ui.onToast(r.msg) }
            "reload" -> GameSession.cmd("reload")
            "vehicle" -> { val r = GameSession.cmd("vehicle"); if (r.msg.isNotEmpty()) ui.onToast(r.msg) }
            "interact" -> ui.onOpen(if (hud[Native.NEAR_MERCHANT] > 0) "merchant" else "craft")
            "trade" -> nearCache?.let { Online.requestTrade(it.uid); ui.onOpen("trade") }
            "ride" -> {
                if (rideUid != null) {
                    rideUid = null
                    GameSession.cmd("ride", "on" to false, "x" to hud[Native.PX], "y" to hud[Native.PY])
                    ui.onToast("Araçtan indin")
                } else nearCache?.let { n -> rideUid = n.uid; ui.onToast("${n.name} ile yolculuk") }
            }
            else -> ui.onOpen(b.id)
        }
    }

    fun clearChunks() = synchronized(GameSession.lock) { chunks.values.forEach { it.recycle() }; chunks.clear() }
}
