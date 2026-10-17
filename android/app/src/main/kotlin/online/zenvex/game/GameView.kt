package online.zenvex.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import online.zenvex.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Oyun ekranı: Rust'tan gelen y-sıralı çizim listesini pixel-perfect çizer,
 * iki joystick'i (hareket + saldırı) ve HUD'u yönetir.
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val onDeath: () -> Unit,
    private val onLevelUp: (Int) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {

    private val assets = Assets.get(context)
    @Volatile var paused = false

    // ---- dünya verisi
    private val tileSize: Int
    private val mapTiles: Int
    private val tiles: IntArray
    private val decals = ArrayList<Triple<Bitmap, Float, Float>>()
    private val staticSprites: Array<StaticSprite?>

    init {
        holder.addCallback(this)
        isFocusable = true
        val w = JSONObject(synchronized(GameSession.lock) { Native.worldJson(GameSession.handle) })
        tileSize = w.getDouble("tile").toInt()
        mapTiles = w.getInt("size")
        val t = w.getJSONArray("tiles")
        tiles = IntArray(t.length()) { t.getInt(it) }
        val d = w.getJSONArray("decals")
        for (i in 0 until d.length()) {
            val a = d.getJSONArray(i)
            assets.decals[a.getString(0)]?.let { decals += Triple(it, a.getDouble(1).toFloat(), a.getDouble(2).toFloat()) }
        }
        val names = w.getJSONArray("names")
        staticSprites = Array(names.length()) { assets.static(names.getString(it)) }
    }

    // ---- çizim araçları
    private val px = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val pxAlpha = Paint(px)
    private val flashPaint = Paint(px).apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP) }
    private val fill = Paint().apply { isAntiAlias = false }
    private val font = ResourcesCompat.getFont(context, R.font.pixelify)
    private val text = Paint().apply { typeface = font; isAntiAlias = false; color = Color.WHITE }
    private val textShadow = Paint(text).apply { color = Color.rgb(27, 28, 38) }
    private val src = Rect()
    private val dst = Rect()
    private val dstF = RectF()

    // ---- yer karoları önbelleği (16x16 karoluk parçalar)
    private val chunkTiles = 16
    private val chunks = object : LinkedHashMap<Int, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean {
            if (size > 20) { eldest.value.recycle(); return true }
            return false
        }
    }

    // ---- kamera
    private var camX = 0f
    private var camY = 0f
    private var scale = 3
    private var viewW = 0f
    private var viewH = 0f
    private var shakeT = 0f
    private var shakeAmp = 0f

    // ---- Rust arabellekleri
    private val drawBuf = FloatArray(Native.STRIDE * 6000)
    private val viewArr = FloatArray(5)
    private val inputArr = FloatArray(5)
    private val hud = FloatArray(Native.HUD_SIZE)
    private val radarBuf = FloatArray(128)

    // ---- joystickler (ekran pikseli)
    private inner class Stick {
        var pointer = -1
        var cx = 0f; var cy = 0f
        var x = 0f; var y = 0f
        var homeX = 0f; var homeY = 0f
        var vx = 0f; var vy = 0f
        val active get() = pointer >= 0
    }
    private val moveStick = Stick()
    private val atkStick = Stick()
    private var lastAtkX = 0f
    private var lastAtkY = 1f
    private val density = resources.displayMetrics.density

    // ---- efektler
    private class Floater(var x: Float, var y: Float, val text: String, val color: Int, val big: Boolean, var life: Float = 1.1f)
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float)
    private val floaters = ArrayList<Floater>()
    private val particles = ArrayList<Particle>()
    private var bannerText = ""
    private var bannerT = 0f
    private var fps = 0f
    private var autosaveT = 0f
    private var deadNotified = false
    private var time = 0f

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    // ------------------------------------------------------------------ döngü
    private var thread: Thread? = null
    @Volatile private var running = false

    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "zenvex-render").also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) = layoutSticks(width, height)

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        thread?.join(500)
        thread = null
        GameSession.save()
    }

    private fun layoutSticks(w: Int, h: Int) {
        val s = Settings.current
        val m = 110 * density * s.joystickSize
        val left = if (s.leftHanded) atkStick else moveStick
        val right = if (s.leftHanded) moveStick else atkStick
        left.homeX = m + 20 * density; left.homeY = h - m * 0.95f
        right.homeX = w - m - 20 * density; right.homeY = h - m * 0.95f
        for (st in listOf(left, right)) if (!st.active) { st.cx = st.homeX; st.cy = st.homeY; st.x = st.cx; st.y = st.cy }
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
            update(dt)
            val c = try { holder.lockHardwareCanvas() } catch (_: Exception) { null }
            if (c != null) {
                try { render(c) } finally { holder.unlockCanvasAndPost(c) }
            }
            val target = 1_000_000_000L / Settings.current.effectiveFps
            val spent = SystemClock.elapsedRealtimeNanos() - now
            if (spent < target) Thread.sleep((target - spent) / 1_000_000L, ((target - spent) % 1_000_000L).toInt())
        }
    }

    private fun update(dt: Float) {
        val s = Settings.current
        // joystick -> giriş
        inputArr[0] = moveStick.vx; inputArr[1] = moveStick.vy
        if (atkStick.active) {
            if (atkStick.vx != 0f || atkStick.vy != 0f) { lastAtkX = atkStick.vx; lastAtkY = atkStick.vy }
            inputArr[2] = lastAtkX; inputArr[3] = lastAtkY; inputArr[4] = 1f
        } else {
            inputArr[2] = 0f; inputArr[3] = 0f; inputArr[4] = 0f
        }
        val events: String
        synchronized(GameSession.lock) {
            val h = GameSession.handle
            if (!paused) Native.step(h, dt, inputArr)
            Native.hud(h, hud)
            events = Native.events(h)
        }
        if (!paused) handleEvents(events, s)
        // kamera: oyuncuyu yumuşak takip
        val tx = hud[Native.HUD_PX] - viewW / 2
        val ty = hud[Native.HUD_PY] - 30 - viewH / 2
        if (camX == 0f && camY == 0f) { camX = tx; camY = ty }
        val k = 1f - Math.pow(0.0005, dt.toDouble()).toFloat()
        camX += (tx - camX) * k
        camY += (ty - camY) * k
        val mapPx = (mapTiles * tileSize).toFloat()
        camX = camX.coerceIn(0f, max(0f, mapPx - viewW))
        camY = camY.coerceIn(0f, max(0f, mapPx - viewH))
        shakeT = max(0f, shakeT - dt)
        bannerT = max(0f, bannerT - dt)
        floaters.forEach { it.life -= dt; it.y -= 28 * dt }
        floaters.removeAll { it.life <= 0f }
        particles.forEach { it.life -= dt; it.x += it.vx * dt; it.y += it.vy * dt; it.vy += 120 * dt }
        particles.removeAll { it.life <= 0f }
        autosaveT += dt
        if (autosaveT > 60f) { autosaveT = 0f; GameSession.save() }
        if (hud[Native.HUD_DEAD] > 0.5f && !deadNotified) {
            deadNotified = true
            post { onDeath() }
        } else if (hud[Native.HUD_DEAD] < 0.5f) deadNotified = false
    }

    private fun handleEvents(json: String, s: Settings) {
        if (json.length <= 2) return
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val x = e.optDouble("x", 0.0).toFloat()
            val y = e.optDouble("y", 0.0).toFloat()
            when (e.getString("t")) {
                "dmg" -> if (s.damageNumbers) {
                    val crit = e.getBoolean("crit")
                    floaters += Floater(x, y, e.getInt("v").toString() + if (crit) "!" else "",
                        if (crit) Color.rgb(255, 210, 94) else Color.WHITE, crit)
                }
                "pdmg" -> {
                    if (s.damageNumbers) floaters += Floater(x, y, "-" + e.getInt("v"), Color.rgb(224, 72, 72), false)
                    shake(3f); vibrate(40, 160)
                }
                "xp" -> floaters += Floater(x, y, "+" + e.getInt("v") + " XP", Color.rgb(94, 224, 255), false, 1.4f)
                "wood" -> floaters += Floater(x, y, "+" + e.getInt("v") + " Odun", Color.rgb(217, 160, 102), true, 1.6f)
                "item" -> floaters += Floater(x, y, "+1 " + e.getString("name"), Color.rgb(238, 242, 240), false, 1.6f)
                "lvl" -> {
                    val lv = e.getInt("level")
                    bannerText = "SEVİYE $lv!"
                    bannerT = 2.6f
                    burst(hud[Native.HUD_PX], hud[Native.HUD_PY] - 30, Color.rgb(255, 210, 94), 40, 140f)
                    vibrate(120, 255)
                    post { onLevelUp(lv) }
                }
                "hit" -> {
                    val tree = e.getBoolean("tree")
                    if (s.particles) {
                        if (tree) burst(x, y, Color.rgb(176, 120, 72), 6, 70f)
                        else burst(x, y, Color.rgb(160, 44, 60), 5, 60f)
                    }
                    shake(if (tree) 1f else 2f); vibrate(18, 90)
                }
                "treefall" -> {
                    if (s.particles) {
                        burst(x, y - 70, Color.rgb(127, 196, 74), 24, 120f)
                        burst(x, y - 10, Color.rgb(217, 160, 102), 10, 80f)
                    }
                    shake(4f); vibrate(60, 200)
                }
                "kill" -> if (s.particles) {
                    burst(x, y - 20, Color.rgb(127, 159, 106), 14, 90f)
                    burst(x, y - 20, Color.rgb(106, 83, 133), 10, 90f)
                }
                "lastbreath" -> { bannerText = "SON NEFES!"; bannerT = 1.8f }
            }
        }
    }

    private fun shake(amount: Float) {
        val s = Settings.current.screenShake
        if (s <= 0f) return
        shakeAmp = max(shakeAmp * (shakeT / 0.25f), amount * s)
        shakeT = 0.25f
    }

    private fun vibrate(ms: Long, amp: Int) {
        val lvl = Settings.current.haptics
        if (lvl == 0) return
        val a = if (lvl == 1) max(1, amp / 2) else amp
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, a.coerceIn(1, 255))) }
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float) {
        if (particles.size > 400) return
        repeat(n) {
            val a = Math.random() * Math.PI * 2
            val v = speed * (0.4f + Math.random().toFloat() * 0.6f)
            particles += Particle(x, y, (cos(a) * v).toFloat(), (sin(a) * v).toFloat() - speed * 0.5f,
                0.4f + Math.random().toFloat() * 0.5f, color, if (Math.random() < 0.3) 2f else 1f)
        }
    }

    // ------------------------------------------------------------------ çizim
    private fun render(c: Canvas) {
        val s = Settings.current
        val sw = c.width; val sh = c.height
        scale = max(1, (sh.toFloat() / s.virtualHeight).roundToInt())
        viewW = sw.toFloat() / scale
        viewH = sh.toFloat() / scale
        var ox = 0f; var oy = 0f
        if (shakeT > 0f) {
            val k = shakeT / 0.25f * shakeAmp
            ox = ((Math.random() * 2 - 1) * k).toFloat(); oy = ((Math.random() * 2 - 1) * k).toFloat()
        }
        val cx = floor(camX + ox)
        val cy = floor(camY + oy)
        c.drawColor(Color.rgb(29, 68, 40))
        c.save()
        c.scale(scale.toFloat(), scale.toFloat())
        c.translate(-cx, -cy)
        drawGround(c, cx, cy)

        viewArr[0] = cx; viewArr[1] = cy; viewArr[2] = viewW; viewArr[3] = viewH; viewArr[4] = s.vegetationDensity
        val n = synchronized(GameSession.lock) { Native.drawList(GameSession.handle, viewArr, drawBuf) }
        val ppx = hud[Native.HUD_PX]; val ppy = hud[Native.HUD_PY]
        // önce gölgeler (hepsi nesnelerin altında kalsın)
        if (s.shadows) for (k in 0 until n) {
            val o = k * Native.STRIDE
            val kind = drawBuf[o].toInt()
            val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
            if (kind == Native.DRAW_STATIC) {
                val sp = staticSprites[drawBuf[o + 1].toInt()] ?: continue
                if (sp.type == "tree" && drawBuf[o + 5] < 0.5f) blit(c, assets.shadowBig, x - 32, y - 10, pxAlpha.apply { alpha = 255 })
                else if (sp.type == "bush" || sp.type == "rock") blit(c, assets.shadow, x - 14, y - 5, px)
            } else {
                blit(c, assets.shadow, x - 14, y - 5, px)
            }
        }
        for (k in 0 until n) {
            val o = k * Native.STRIDE
            when (drawBuf[o].toInt()) {
                Native.DRAW_STATIC -> drawStatic(c, o, s, ppx, ppy)
                Native.DRAW_PLAYER -> drawActor(c, assets.player, o, 1f)
                Native.DRAW_ZOMBIE -> drawActor(c, assets.zombie, o, drawBuf[o + 1])
            }
        }
        // parçacıklar
        for (p in particles) {
            fill.color = p.color
            fill.alpha = (min(1f, p.life * 3f) * 255).toInt()
            c.drawRect(floor(p.x), floor(p.y), floor(p.x) + p.size, floor(p.y) + p.size, fill)
        }
        fill.alpha = 255
        c.restore()
        drawWorldText(c, cx, cy)
        drawHud(c, sw, sh, s)
    }

    private fun blit(c: Canvas, b: Bitmap, x: Float, y: Float, p: Paint) {
        c.drawBitmap(b, floor(x), floor(y), p)
    }

    private fun drawGround(c: Canvas, cx: Float, cy: Float) {
        val cp = chunkTiles * tileSize
        val x0 = max(0, (cx / cp).toInt()); val y0 = max(0, (cy / cp).toInt())
        val maxC = (mapTiles + chunkTiles - 1) / chunkTiles - 1
        val x1 = min(maxC, ((cx + viewW) / cp).toInt()); val y1 = min(maxC, ((cy + viewH) / cp).toInt())
        for (gy in y0..y1) for (gx in x0..x1) {
            val b = chunks.getOrPut(gy * 1000 + gx) { buildChunk(gx, gy) }
            c.drawBitmap(b, (gx * cp).toFloat(), (gy * cp).toFloat(), px)
        }
    }

    private fun buildChunk(gx: Int, gy: Int): Bitmap {
        val cp = chunkTiles * tileSize
        val b = Bitmap.createBitmap(cp, cp, Bitmap.Config.ARGB_8888)
        val cc = Canvas(b)
        val cols = assets.tileset.width / tileSize
        for (ty in 0 until chunkTiles) for (tx in 0 until chunkTiles) {
            val mx = gx * chunkTiles + tx; val my = gy * chunkTiles + ty
            if (mx >= mapTiles || my >= mapTiles) continue
            val t = tiles[my * mapTiles + mx]
            src.set((t % cols) * tileSize, (t / cols) * tileSize, (t % cols + 1) * tileSize, (t / cols + 1) * tileSize)
            dst.set(tx * tileSize, ty * tileSize, (tx + 1) * tileSize, (ty + 1) * tileSize)
            cc.drawBitmap(assets.tileset, src, dst, px)
        }
        val ox = gx * cp; val oy = gy * cp
        for ((bmp, x, y) in decals) {
            val l = x - bmp.width / 2 - ox; val t = y - bmp.height / 2 - oy
            if (l > cp || t > cp || l + bmp.width < 0 || t + bmp.height < 0) continue
            cc.drawBitmap(bmp, floor(l), floor(t), px)
        }
        return b
    }

    private fun drawStatic(c: Canvas, o: Int, s: Settings, ppx: Float, ppy: Float) {
        val sp = staticSprites[drawBuf[o + 1].toInt()] ?: return
        val x = drawBuf[o + 2] + drawBuf[o + 7]
        val y = drawBuf[o + 3]
        val b = sp.bitmap
        var paint = px
        // oyuncu ağacın arkasındaysa ağacı saydamlaştır
        if (s.treeFade && sp.type == "tree" && ppy < y - 4 && ppy > y - sp.anchorY * 0.9f && abs(ppx - x) < b.width * 0.4f) {
            pxAlpha.alpha = 120; paint = pxAlpha
        }
        val sway = s.wind && (sp.type == "tree" || sp.type == "bush" || sp.type == "deco")
        if (sway) {
            val amp = when (sp.type) { "tree" -> 0.018f; "bush" -> 0.03f; else -> 0.09f }
            val k = sin(time * 1.6f + drawBuf[o + 6] + x * 0.004f) * amp
            c.save()
            c.translate(floor(x), floor(y))
            c.skew(k, 0f)
            c.drawBitmap(b, -sp.anchorX.toFloat(), -sp.anchorY.toFloat(), paint)
            c.restore()
        } else {
            blit(c, b, x - sp.anchorX, y - sp.anchorY, paint)
        }
        val hp = drawBuf[o + 8]
        if (hp >= 0f) bar(c, x - 12, y - sp.anchorY + 6, 24f, hp, Color.rgb(127, 196, 74))
    }

    private fun drawActor(c: Canvas, a: Actor, o: Int, alpha: Float) {
        val anim = a.anims.getOrNull(drawBuf[o + 5].toInt()) ?: return
        val dir = drawBuf[o + 4].toInt().coerceIn(0, 7)
        val f = anim.frameAt(drawBuf[o + 6])
        src.set(f * anim.fw, dir * anim.fh, (f + 1) * anim.fw, (dir + 1) * anim.fh)
        val x = floor(drawBuf[o + 2]) - a.anchorX
        val y = floor(drawBuf[o + 3]) - a.anchorY
        dstF.set(x, y, x + anim.fw, y + anim.fh)
        val p = if (drawBuf[o + 7] > 0.5f) flashPaint else if (alpha < 1f) pxAlpha.apply { this.alpha = (alpha * 255).toInt() } else px
        c.drawBitmap(anim.bitmap, src, dstF, p)
        val hp = drawBuf[o + 8]
        if (hp >= 0f) bar(c, drawBuf[o + 2] - 12, drawBuf[o + 3] - 58, 24f, hp, Color.rgb(224, 72, 72))
    }

    private fun bar(c: Canvas, x: Float, y: Float, w: Float, v: Float, color: Int) {
        val l = floor(x); val t = floor(y)
        fill.color = Color.rgb(27, 28, 38)
        c.drawRect(l - 1, t - 1, l + w + 1, t + 4, fill)
        fill.color = Color.rgb(62, 65, 87)
        c.drawRect(l, t, l + w, t + 3, fill)
        fill.color = color
        c.drawRect(l, t, l + floor(w * v.coerceIn(0f, 1f)), t + 3, fill)
    }

    private fun drawWorldText(c: Canvas, cx: Float, cy: Float) {
        val base = 7f * scale
        for (f in floaters) {
            val sx = (f.x - cx) * scale
            val sy = (f.y - cy) * scale
            text.textSize = if (f.big) base * 1.35f else base
            textShadow.textSize = text.textSize
            val a = (min(1f, f.life * 2.5f) * 255).toInt()
            text.color = f.color; text.alpha = a; textShadow.alpha = a
            val w = text.measureText(f.text)
            c.drawText(f.text, sx - w / 2 + scale, sy + scale, textShadow)
            c.drawText(f.text, sx - w / 2, sy, text)
        }
        text.alpha = 255; textShadow.alpha = 255
    }

    // ------------------------------------------------------------------ HUD
    private fun drawHud(c: Canvas, sw: Int, sh: Int, s: Settings) {
        val u = scale.toFloat() // HUD pikseli = oyun pikseli
        val hp = hud[Native.HUD_HP]; val maxHp = hud[Native.HUD_MAXHP]
        val st = hud[Native.HUD_ST]; val maxSt = hud[Native.HUD_MAXST]
        val xp = hud[Native.HUD_XP]; val xpN = hud[Native.HUD_XPNEXT]
        val lvl = hud[Native.HUD_LEVEL].toInt()
        val inset = 8 * u
        // XP şeridi (en üst)
        fill.color = Color.argb(200, 23, 34, 56)
        c.drawRect(0f, 0f, sw.toFloat(), 3 * u, fill)
        fill.color = Color.rgb(94, 224, 255)
        c.drawRect(0f, 0f, sw * (xp / max(1f, xpN)), 3 * u, fill)
        // portre paneli
        val px0 = inset; val py0 = inset
        panel(c, px0, py0, 34 * u, 34 * u)
        val idle = assets.player.anims[0]
        src.set(28, 6, 68, 46)
        dstF.set(px0 + 2 * u, py0 + 2 * u, px0 + 32 * u, py0 + 32 * u)
        c.drawBitmap(idle.bitmap, src, dstF, px)
        // seviye rozeti
        text.textSize = 8 * u; textShadow.textSize = 8 * u
        val lt = "Lv $lvl"
        panel(c, px0, py0 + 30 * u, 34 * u, 11 * u)
        text.color = Color.rgb(255, 233, 168)
        val lw = text.measureText(lt)
        c.drawText(lt, px0 + 17 * u - lw / 2, py0 + 38.5f * u, text)
        // can + dayanıklılık
        val bx = px0 + 38 * u
        hudBar(c, bx, py0 + 2 * u, 90 * u, 9 * u, hp / max(1f, maxHp), Color.rgb(224, 72, 72), "${hp.toInt()}/${maxHp.toInt()}", u)
        hudBar(c, bx, py0 + 14 * u, 70 * u, 6 * u, st / max(1f, maxSt), Color.rgb(255, 210, 94), null, u)
        // kaynaklar
        text.textSize = 7 * u
        drawIconText(c, "wood", "${hud[Native.HUD_WOOD].toInt()}", bx, py0 + 24 * u, u)
        drawIconText(c, "fist", "${hud[Native.HUD_KILLS].toInt()}", bx + 40 * u, py0 + 24 * u, u)
        if (hud[Native.HUD_POINTS] > 0) {
            text.color = Color.rgb(255, 210, 94)
            c.drawText("+${hud[Native.HUD_POINTS].toInt()} yetenek puanı", bx, py0 + 44 * u, text)
        }
        // mini harita (sağ üst, duraklat/çanta tuşlarının altında)
        drawMinimap(c, sw - 70 * u - inset, 30 * u, 70 * u, u)
        // düşük can uyarısı
        if (hp > 0 && hp / max(1f, maxHp) < 0.3f) {
            val a = ((sin(time * 6f) * 0.5f + 0.5f) * 90).toInt()
            fill.color = Color.argb(a, 160, 20, 40)
            val e = 10 * u
            c.drawRect(0f, 0f, sw.toFloat(), e, fill); c.drawRect(0f, sh - e, sw.toFloat(), sh.toFloat(), fill)
            c.drawRect(0f, 0f, e, sh.toFloat(), fill); c.drawRect(sw - e, 0f, sw.toFloat(), sh.toFloat(), fill)
        }
        // seviye atlama yazısı
        if (bannerT > 0f) {
            text.textSize = 22 * u; textShadow.textSize = text.textSize
            val a = (min(1f, bannerT * 2f) * 255).toInt()
            text.color = Color.rgb(255, 210, 94); text.alpha = a; textShadow.alpha = a
            val w = text.measureText(bannerText)
            val y = sh * 0.32f - (2.6f - bannerT).coerceAtMost(0.3f) * 30 * u
            c.drawText(bannerText, sw / 2f - w / 2 + 2 * u, y + 2 * u, textShadow)
            c.drawText(bannerText, sw / 2f - w / 2, y, text)
            text.alpha = 255; textShadow.alpha = 255
        }
        drawStick(c, moveStick, assets.ui["joy_base"]!!, assets.ui["joy_knob"]!!, s)
        drawStick(c, atkStick, assets.ui["atk_base"]!!, assets.ui["atk_knob"]!!, s)
        if (s.showFps) {
            text.textSize = 7 * u; text.color = Color.WHITE
            c.drawText("${fps.roundToInt()} FPS", inset, sh - 6 * u, text)
        }
    }

    private fun panel(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val u = scale.toFloat()
        fill.color = Color.rgb(14, 20, 36)
        c.drawRect(x, y, x + w, y + h, fill)
        fill.color = Color.argb(230, 35, 54, 90)
        c.drawRect(x + u, y + u, x + w - u, y + h - u, fill)
        fill.color = Color.rgb(51, 80, 126)
        c.drawRect(x + u, y + u, x + w - u, y + 2 * u, fill)
    }

    private fun hudBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, v: Float, color: Int, label: String?, u: Float) {
        fill.color = Color.rgb(27, 28, 38)
        c.drawRect(x, y, x + w, y + h, fill)
        fill.color = Color.rgb(62, 65, 87)
        c.drawRect(x + u, y + u, x + w - u, y + h - u, fill)
        fill.color = color
        c.drawRect(x + u, y + u, x + u + (w - 2 * u) * v.coerceIn(0f, 1f), y + h - u, fill)
        fill.color = Color.argb(90, 255, 255, 255)
        c.drawRect(x + u, y + u, x + u + (w - 2 * u) * v.coerceIn(0f, 1f), y + 2 * u, fill)
        if (label != null) {
            text.textSize = h - u; textShadow.textSize = text.textSize
            text.color = Color.WHITE
            c.drawText(label, x + 3 * u + u * 0.5f, y + h - 2 * u + u * 0.5f, textShadow)
            c.drawText(label, x + 3 * u, y + h - 2 * u, text)
        }
    }

    private fun drawIconText(c: Canvas, icon: String, s: String, x: Float, y: Float, u: Float) {
        assets.icons[icon]?.let {
            src.set(0, 0, 16, 16)
            dstF.set(x, y, x + 10 * u, y + 10 * u)
            c.drawBitmap(it, src, dstF, px)
        }
        text.color = Color.WHITE
        c.drawText(s, x + 12 * u, y + 8 * u, text)
    }

    private fun drawMinimap(c: Canvas, x: Float, y: Float, size: Float, u: Float) {
        panel(c, x, y, size, size)
        val range = 700f
        val pxw = hud[Native.HUD_PX]; val pyw = hud[Native.HUD_PY]
        val inner = size - 4 * u
        fill.color = Color.rgb(47, 107, 51)
        c.drawRect(x + 2 * u, y + 2 * u, x + size - 2 * u, y + size - 2 * u, fill)
        val nZ = synchronized(GameSession.lock) { Native.radar(GameSession.handle, radarBuf) }
        fill.color = Color.rgb(224, 72, 72)
        for (i in 0 until nZ) {
            val dx = (radarBuf[i * 2] - pxw) / range
            val dy = (radarBuf[i * 2 + 1] - pyw) / range
            if (abs(dx) > 0.5f || abs(dy) > 0.5f) continue
            val mx = x + size / 2 + dx * inner; val my = y + size / 2 + dy * inner
            c.drawRect(mx - u, my - u, mx + u, my + u, fill)
        }
        fill.color = Color.rgb(255, 210, 94)
        c.drawRect(x + size / 2 - 1.5f * u, y + size / 2 - 1.5f * u, x + size / 2 + 1.5f * u, y + size / 2 + 1.5f * u, fill)
    }

    private fun drawStick(c: Canvas, st: Stick, base: Bitmap, knob: Bitmap, s: Settings) {
        val sz = 120 * density * s.joystickSize
        val kz = sz * 0.43f
        val a = if (st.active) 0.95f else s.joystickOpacity
        pxAlpha.alpha = (a * 255).toInt()
        dstF.set(st.cx - sz / 2, st.cy - sz / 2, st.cx + sz / 2, st.cy + sz / 2)
        c.drawBitmap(base, null, dstF, pxAlpha)
        dstF.set(st.x - kz / 2, st.y - kz / 2, st.x + kz / 2, st.y + kz / 2)
        c.drawBitmap(knob, null, dstF, pxAlpha)
        pxAlpha.alpha = 255
    }

    // ------------------------------------------------------------------ dokunma
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = Settings.current
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i); val y = e.getY(i)
                val leftSide = x < width / 2f
                val st = if (leftSide != s.leftHanded) moveStick else atkStick
                if (!st.active && y > height * 0.25f) {
                    st.pointer = e.getPointerId(i)
                    if (s.moveDynamic) { st.cx = x; st.cy = y } else { st.cx = st.homeX; st.cy = st.homeY }
                    moveTo(st, x, y, s)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == moveStick.pointer) moveTo(moveStick, e.getX(i), e.getY(i), s)
                    if (id == atkStick.pointer) moveTo(atkStick, e.getX(i), e.getY(i), s)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = e.getPointerId(e.actionIndex)
                for (st in listOf(moveStick, atkStick)) {
                    if (e.actionMasked == MotionEvent.ACTION_CANCEL || id == st.pointer) release(st)
                }
            }
        }
        return true
    }

    private fun moveTo(st: Stick, x: Float, y: Float, s: Settings) {
        val radius = 50 * density * s.joystickSize
        var dx = x - st.cx; var dy = y - st.cy
        val d = hypot(dx, dy)
        if (d > radius) { dx = dx / d * radius; dy = dy / d * radius }
        st.x = st.cx + dx; st.y = st.cy + dy
        val isMove = st === moveStick
        val dz = if (isMove) s.moveDeadZone else s.attackDeadZone
        val sens = if (isMove) s.moveSensitivity else s.attackSensitivity
        var len = min(1f, d / radius)
        if (len < dz) { st.vx = 0f; st.vy = 0f; return }
        len = min(1f, (len - dz) / (1f - dz) * sens)
        // koşma eşiği ayarını Rust'taki sabit 0.6 eşiğine eşle
        if (isMove) len = if (len >= s.runThreshold) max(0.61f, len) else min(0.59f, len * 0.6f / s.runThreshold)
        st.vx = dx / max(d, 1e-3f) * len
        st.vy = dy / max(d, 1e-3f) * len
    }

    private fun release(st: Stick) {
        st.pointer = -1
        st.vx = 0f; st.vy = 0f
        st.cx = st.homeX; st.cy = st.homeY
        st.x = st.cx; st.y = st.cy
    }

    fun onSettingsChanged() {
        layoutSticks(width, height)
    }
}
