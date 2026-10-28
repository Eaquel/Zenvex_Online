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
import org.json.JSONObject
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

private const val CLAN_COLOR = 0xFFF6E05A.toInt()
/** rust pet.rs D_PET */
private const val D_PET = 8
/** rust bases.rs: devlet üssü askeri, helikopter, üs bayrağı */
private const val D_SOLDIER = 10
private const val D_HELI = 11
private const val D_FLAG = 12

/** bölge sınırı sisinin katmanları (sınırdan dışarı karo cinsinden) */
private val AREA_FADE = floatArrayOf(0f, 0.7f, 1.4f, 2.2f, 3.2f, 4.5f, 6f)

/**
 * Oyun ekranı: Rust'ın y-sıralı çizim listesini yüksek çözünürlüklü atlaslardan çizer.
 * Zemin Rust'ta piksel piksel harmanlanır ve parçalar halinde önbelleğe alınır.
 * Joystickler ve hızlı tuşlar (yetenek, silah, araç, etkileşim) çoklu dokunuş için tuvalde çizilir.
 */
@SuppressLint("ViewConstructor")
/** Bu kadar seviye altındaki yaratıktan XP gelmez (Rust: progress::XP_LEVEL_GAP ile aynı) */
const val XP_LEVEL_GAP = 10

/**
 * Ekranın üst ortası tek düzen: bölge adı → bağlamsal panel (boss çubuğu, üs kartı ya da etkinlik afişi; aynı anda yalnız
 * biri) → duyuru afişi. Compose tarafı (etkinlik afişi) burayı okur; boss/üs varken afiş gizlenir.
 */
/** duyuru afişinin ekranda kalma süresi (sn) */
private const val BANNER_DUR = 2.6f

object HudLayout {
    /** boss çubuğu ya da üs kartı üst ortada (etkinlik afişi gösterilmez) */
    @Volatile var topBusy = false
    /** etkinlik afişi görünüyor (Compose yazar; duyuru afişi onun altına iner) */
    @Volatile var eventShown = false
}

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
    /** V13 bölge yaratıkları: görsel kimliği ve adı (zombi tür alanı = temel tür + 8 × (indeks + 1)) */
    private val speciesId: Array<String>
    private val speciesName: Array<String>
    private val zombieColor: IntArray
    private val vehicleIds: Array<String>
    private val weaponSprites: Array<String>
    /** silah dizini → 3B model ("" yumruk) */
    private val weaponModels: Array<String>
    /** etkinlik silahları (ev_<etkinlik>_weapon): elde w/ev_<etkinlik> çizilir, animasyon katana / bıçak karelerinden */
    private val evWeapons: Array<String?>
    private val itemIds: Array<String>
    private val itemSprites: Array<Sprite?>
    private val zones: Array<String>
    private val mapPx: Float
    private val tile: Float
    /** V12 bölgeler (ayrı haritalar): bulunulan bölgenin dışı karanlık */
    private val areas: List<com.zenvex.online.ui.MapArea>

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
        val spj = w.optJSONArray("species") ?: org.json.JSONArray()
        speciesId = Array(spj.length()) { spj.getJSONArray(it).getString(0) }
        speciesName = Array(spj.length()) { spj.getJSONArray(it).getString(1) }
        zombieColor = IntArray(z.length()) { z.getJSONArray(it).getLong(2).toInt() }
        val v = w.getJSONArray("vehicles")
        vehicleIds = Array(v.length()) { v.getJSONArray(it).getString(0) }
        val wp = w.getJSONArray("weapons")
        weaponSprites = Array(wp.length()) { wp.getJSONArray(it).getString(1) }
        weaponModels = Array(wp.length()) { Model3D.itemModel(wp.getJSONArray(it).getString(0)) }
        evWeapons = Array(wp.length()) { i -> wp.getJSONArray(i).getString(0).takeIf { it.startsWith("ev_") }?.removePrefix("ev_")?.removeSuffix("_weapon") }
        val it2 = w.getJSONArray("items")
        itemIds = Array(it2.length()) { it2.getJSONArray(it).getString(0) }
        itemSprites = Array(itemIds.size) { atlas["i/" + itemIds[it]] ?: atlas["s/" + itemIds[it]] }
        val zs = w.getJSONArray("zones")
        zones = Array(zs.length()) { zs.getJSONArray(it).getString(0) }
        mapPx = w.getDouble("map_px").toFloat()
        tile = w.optDouble("tile", 64.0).toFloat()
        areas = com.zenvex.online.ui.mapAreas(w)
        atlas.preload("world"); atlas.preload("items"); atlas.preload("npcs")
    }

    // ------------------------------------------------------------------ boyalar
    private val baseDensity = resources.displayMetrics.density
    /** çizim yüzeyi küçültüldüğünde (çözünürlük ayarı) arayüz ölçüsü yüzeyle birlikte küçülür */
    private var density = baseDensity
    /** dokunma koordinatı → yüzey koordinatı çarpanı */
    private var touchK = 1f
    private var appliedRes = -1
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
    // Zemin parçaları arka planda çizilir (zx-ground): parça başına 8 şerit, her şerit oyun kilidini kısa süre tutar; oyun
    // döngüsü yeni parça beklemez (yeni bölgeye girerken eskiden kare başına 4 parça eşzamanlı çizilip takılma yapıyordu).
    // Görünen parçalar önce, sonra görüşün bir parça çevresi ve gidiş yönündeki iki parça önceden hazırlanır.
    private val CH = 256
    private val chunkPx = IntArray(CH * CH)
    /** erişim sırası LRU; yalnız `chunks` kilidiyle. Atılan bitmap geri dönüştürülmez (donanım tuvali kareyi sonra çizer) */
    private val chunks = object : LinkedHashMap<Long, Bitmap>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>): Boolean = size > 110
    }
    private val groundQ = java.util.concurrent.LinkedBlockingDeque<Long>()
    private val groundPending = HashSet<Long>()
    @Volatile private var groundEpoch = 0
    private var groundThread: Thread? = null
    private var groundReady = true
    private var lastCamCX = 0f; private var lastCamCY = 0f

    private fun chunkKey(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)

    /** Parçayı şeritler hâlinde çizer; dönem değiştiyse (bölge değişti) yarıda bırakır. null → dokular hazır değil */
    private fun renderChunkStrips(key: Long, epoch: Int, out: IntArray, strip: IntArray): Bitmap? {
        val cx = (key shr 32).toInt(); val cy = key.toInt()
        val rows = strip.size / CH
        var y = 0
        while (y < CH) {
            val ok = synchronized(GameSession.lock) {
                if (epoch != groundEpoch) 0 else Native.renderRegion(GameSession.handle, cx * CH.toFloat(), cy * CH.toFloat() + y, 1f, CH, rows, strip)
            }
            if (ok == 0) return null
            System.arraycopy(strip, 0, out, y * CH, CH * rows)
            y += rows
        }
        return Bitmap.createBitmap(out, 0, CH, CH, CH, Bitmap.Config.ARGB_8888).also { it.prepareToDraw() }
    }

    private fun startGroundWorker() {
        if (groundThread != null) return
        groundThread = Thread({
            val out = IntArray(CH * CH); val strip = IntArray(CH * 32)
            while (true) {
                val key = try { groundQ.takeFirst() } catch (_: InterruptedException) { return@Thread }
                val epoch = groundEpoch
                val b = try { renderChunkStrips(key, epoch, out, strip) } catch (_: Throwable) { null }
                synchronized(chunks) {
                    groundPending -= key
                    if (b != null && epoch == groundEpoch) chunks[key] = b
                }
                if (b == null && epoch == groundEpoch) groundReady = false
            }
        }, "zx-ground").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1; start() }
    }

    /** Parçayı kuyruğa ekler (önde: görünen, arkada: önceden yükleme); zaten varsa ya da sıradaysa bir şey yapmaz */
    private fun requestChunk(key: Long, urgent: Boolean) {
        synchronized(chunks) {
            if (chunks.containsKey(key) || key in groundPending) return
            groundPending += key
        }
        if (urgent) groundQ.addFirst(key) else groundQ.addLast(key)
        // sıra uzarsa en eski önceden yükleme istekleri düşer (görüş çoktan değişmiştir)
        while (groundQ.size > 24) groundQ.pollLast()?.let { k -> synchronized(chunks) { groundPending -= k } }
    }

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
    private val radarBuf = FloatArray(3 * 320)
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
    private var pvpSyncT = 0f
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
    /**
     * Parçacık türleri (hepsi FxTex dokusuyla çizilir): 0 kırık parça (yerçekimi, döner) · 1 toz/duman bulutu (büyür, söner) ·
     * 3 parıltı (dört köşeli yıldız, toplamalı) · 4 piksel kıvılcım · 6 alev dili (toplamalı, yükselir, savrulur)
     */
    private class Particle(
        var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float,
        var life: Float, val max: Float, val color: Int, val size: Float, val kind: Int,
        val seed: Float = Math.random().toFloat(),
    )
    private class Light(val x: Float, val y: Float, val r: Float, val color: Int, var life: Float, val max: Float)
    private class Tracer(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, var life: Float, val width: Float)
    private val floaters = ArrayList<Floater>()
    /** Yeni parçacıklar efekt yoğunluğuna göre seyreltilir (düşük kalitede gerçekten az parçacık) */
    private class PList : ArrayList<Particle>() {
        override fun add(element: Particle): Boolean {
            val k = Settings.current.fxScale
            if (k < 1f && Math.random() > k) return false
            return super.add(element)
        }
    }
    private val particles = PList()
    private val flashes = ArrayList<Light>()
    private val tracers = ArrayList<Tracer>()
    /** kule askerinin fırlattığı mızrak: (x1,y1)→(x2,y2) yay çizerek dur sn'de uçar */
    private class Spear(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val dur: Float) { var t = 0f }
    private val spears = ArrayList<Spear>()
    /** V11 eğitim üssüne düşen bomba (x, y hedef; dur düşüş süresi) */
    private class Bomb(val x: Float, val y: Float, val dur: Float) { var t = 0f }
    private val bombs = ArrayList<Bomb>()
    /** takla art görüntüsü: kahramanın son karelerinden mor, sönen kopyalar */
    private class Ghost(val x: Float, val y: Float, val key: String, val flip: Boolean, var life: Float, val fr: Model3D.Frame? = null)
    private val ghosts = ArrayList<Ghost>()
    private var ghostT = 0f
    private val ghostPaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFFB09AFF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    /** tanrıların yıldırımı: zikzak çizgi noktaları + ömür */
    private class Bolt(val x: Float, val y: Float, val pts: FloatArray) { var life = 0.45f }
    private val bolts = ArrayList<Bolt>()
    private var whiteFlash = 0f
    private var banner = ""
    private var bannerSub = ""
    private var bannerT = 0f
    private var bannerColor = Color.WHITE
    /** sıradaki afişler: aynı anda tek afiş, üst üste binmez (başlık, alt yazı, renk) */
    private val bannerQueue = ArrayDeque<Triple<String, String, Int>>()
    /** üst ortadaki bağlamsal panelin (boss, üs kartı, etkinlik afişi) alt kenarı: afiş ve öğretici balonu bunun altında */
    private var slotBottom = 0f
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
        post { applyResolution() }
        thread = Thread(::loop, "zenvex-render").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    /** Çözünürlük ayarı: yüzey gerçekten küçük boyda açılır (GPU ölçekleyerek ekrana yayar), arayüz ölçüsü ve dokunma buna uyar. */
    private fun applyResolution() {
        val rs = Settings.current.renderScale
        appliedRes = Settings.current.resolution
        val vw = this.width; val vh = this.height
        if (vw <= 0 || vh <= 0) return
        if (rs >= 0.99f) holder.setSizeFromLayout() else holder.setFixedSize((vw * rs).toInt().coerceAtLeast(320), (vh * rs).toInt().coerceAtLeast(180))
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        sw = width; sh = height
        val vw = this.width
        touchK = if (vw > 0) width.toFloat() / vw else 1f
        density = baseDensity * touchK
        layoutControls(width, height)
        if (appliedRes != Settings.current.resolution) applyResolution()
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
        // saldırı joystiğinin çevresinde tek yay: hepsi aynı uzaklıkta, komşular arasında boşluk kalacak açılarla
        // (en sık kullanılan takla en büyük, saldırı tuşunun hemen üstünde)
        val ax = right.homeX; val ay = right.homeY
        val d = density
        val ring = stickR + 58 * d
        fun place(b: Btn, angDeg: Float, r: Float, rr: Float = ring) {
            val a = Math.toRadians(angDeg.toDouble())
            val sx = if (s.leftHanded) -1 else 1
            val cx = ax - sx * cos(a).toFloat() * rr
            val cy = ay - sin(a).toFloat() * rr
            b.r.set(cx - r, cy - r, cx + r, cy + r)
        }
        place(btnAbility, 100f, 32 * d)
        place(btnWeapon, 140f, 28 * d)
        // şarjör: saldırı çemberinin dış kenarı ile takla arasında, ekran kenarına taşmadan (iç yay; başparmak saldırıdan
        // kaymadan ulaşır, taklayla ve saldırı çemberiyle çakışmaz)
        place(btnReload, 135f, 27 * d, stickR + 36 * d)
        place(btnVehicle, 62f, 28 * d)
        place(btnInteract, 28f, 28 * d)
        place(btnTrade, -2f, 24 * d)
        place(btnRide, -30f, 24 * d)
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
            if (appliedRes != Settings.current.resolution) post { applyResolution() }
            val target = 1_000_000_000L / Settings.current.effectiveFps
            val spent = SystemClock.elapsedRealtimeNanos() - now
            if (spent < target) Thread.sleep((target - spent) / 1_000_000L, ((target - spent) % 1_000_000L).toInt())
        }
    }

    /**
     * Ekran görüntüsü testleri: [steps] kare oyunu ilerletir ([input]: hareket x/y, saldırı x/y, saldırı 0/1) ve son kareyi
     * [c] tuvaline çizer. Gerçek döngüyle aynı update/render yolunu kullanır.
     */
    internal fun previewFrames(c: Canvas, w: Int, h: Int, steps: Int, dt: Float = 1f / 30f, input: FloatArray? = null) {
        if (sw != w || sh != h) { sw = w; sh = h; layoutControls(w, h) }
        repeat(steps) { i ->
            time += dt
            if (input != null) {
                moveStick.vx = input[0]; moveStick.vy = input[1]
                atkStick.pointer = if (input.getOrElse(4) { 0f } > 0f) 99 else -1
                atkStick.vx = input.getOrElse(2) { 0f }; atkStick.vy = input.getOrElse(3) { 0f }
            }
            update(dt)
            // son birkaç kare çizilir: 3B silah izi ve efektler kare kare birikir
            if (i >= steps - 6) render(c, dt)
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
            // çevrimiçi oyun: oturum yoksa ya da bağlantı koptuysa dünya durur (otomatik gösterim hariç)
            if (!paused && (!Session.blocked || com.zenvex.online.ui.Demo.on)) {
                Native.step(h, dt, inputArr)
                Session.simTime += dt
            }
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
            val visibleM = if (promoZoom > 0f) promoZoom else when (s.zoom) { 0 -> 10.5f; 1 -> 13f; else -> 16.5f }
            scale = sh / (visibleM * 62.5f)
            viewW = sw / scale; viewH = sh / scale
            viewArr[0] = camX - viewW / 2; viewArr[1] = camY - viewH / 2
            viewArr[2] = viewW; viewArr[3] = viewH; viewArr[4] = s.vegetationDensity
            drawN = Native.drawList(h, viewArr, drawBuf)
            radarN = Native.radar(h, radarBuf)
            events = Native.events(h)
        }
        handleEvents(events)
        // V8 devlet üsleri: sunucu durumu ve yakındaki üsün HUD bilgisi
        Bases.tick(dt)
        baseNearT -= dt
        if (baseNearT <= 0f) { baseNearT = 0.25f; baseNear = runCatching { GameSession.ui("base_near") }.getOrNull() }

        // efektler
        if (shakeT > 0) shakeT -= dt
        updateFx(dt)
        if (bannerT > 0) bannerT -= dt
        if (bannerT <= 0f && bannerQueue.isNotEmpty()) {
            val (t, sub, col) = bannerQueue.removeFirst()
            banner = t; bannerSub = sub; bannerColor = col; bannerT = BANNER_DUR
        }
        vignetteRed = max(0f, vignetteRed - dt * 1.6f)
        val fit = floaters.iterator()
        while (fit.hasNext()) { val f = fit.next(); f.life -= dt; f.y -= 42f * dt; if (f.life <= 0) fit.remove() }
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            when (p.kind) {
                0 -> { p.vz -= 900f * dt; if (p.z < 0) { p.z = 0f; p.vz *= -0.3f; p.vx *= 0.6f; p.vy *= 0.6f } }
                1 -> { val d = 1f - 2.2f * dt; p.vx *= d; p.vy *= d; p.vz *= 1f - 3.0f * dt }          // toz: hızla yavaşlar, süzülür
                6 -> { p.vx += sin(time * 7f + p.seed * 9f) * 90f * dt; p.vz *= 1f - 0.8f * dt }       // alev: yalpalar
                else -> { p.vx *= 0.97f; p.vy *= 0.97f }
            }
            if (p.life <= 0) pit.remove()
        }
        flashes.removeAll { it.life -= dt; it.life <= 0 }
        tracers.removeAll { it.life -= dt; it.life <= 0 }
        chestPollT -= dt
        if (chestPollT <= 0f) { chestPollT = 0.5f; pollChests() }
        questPollT -= dt
        if (questPollT <= 0f) {
            questPollT = 1f
            questMarks.clear()
            questTargets.clear()
            guideStep = ""; guideReady = false; guideOffer = false
            val st = GameSession.ui("quests").optJSONArray("story")
            for (i in 0 until (st?.length() ?: 0)) {
                val q = st!!.getJSONObject(i)
                // hedef başka bölgedeyse ok bu bölgenin büyücüsünü gösterir
                val via = q.optString("via")
                if (q.has("x")) questTargets += QuestTarget(q.getDouble("x").toFloat(), q.getDouble("y").toFloat(),
                    if (via.isNotEmpty()) "Büyücü → $via" else if (q.optBoolean("ready") || !q.optBoolean("on")) q.optString("giver") else q.optString("title"), q.optBoolean("guide"))
                if (q.optBoolean("guide") && q.optBoolean("on") && !q.optBoolean("ready")) {
                    guideStep = q.optString("id"); guideN = q.optInt("n"); guideGoal = q.optInt("goal")
                }
                if (q.optBoolean("guide") && q.optBoolean("ready")) guideReady = true
                if (q.optBoolean("guide") && !q.optBoolean("on") && q.has("x")) { guideOffer = true; guideGiver = q.optInt("giver_kind") }
                val ch = 4 + q.optInt("giver_kind")
                if (q.optBoolean("ready")) questMarks[ch] = "?"
                else if (!q.optBoolean("on") && q.has("x")) questMarks.putIfAbsent(ch, "!")
            }
        }
        bombs.removeAll { b -> b.t += dt; b.t >= b.dur }
        spears.removeAll { s -> s.t += dt; if (s.t >= s.dur) { burst(s.x2, s.y2 + 50, 6, 0xFF9A8A70.toInt(), 120f, 4f); Audio.play("hit", near(s.x2, s.y2) * 0.7f, 0.8f) }; s.t >= s.dur }
        bolts.removeAll { it.life -= dt; it.life <= 0 }
        ghosts.removeAll { it.life -= dt; it.life <= 0 }
        fx3d.enabled = Settings.current.particles
        fx3d.density = Settings.current.fxScale.coerceAtMost(1f)
        fx3d.followX = hud[Native.PX]; fx3d.followY = hud[Native.PY] - 30f
        fx3d.update(dt, 62.5f)
        whiteFlash = max(0f, whiteFlash - dt * 3f)
        synchronized(chatLines) { chatLines.replaceAll { it.first to it.second - dt }; chatLines.removeAll { it.second <= 0 } }
        for (b in buttons) b.flash = max(0f, b.flash - dt * 3)

        if (hud[Native.DEAD] > 0.5f && !deadNotified) { deadNotified = true; Online.onLocalDeath(); ui.onDeath() }
        // PvP / klan savaşı: saniyede bir düşman bayrakları ve üs savunucusu kontrolü Rust'a
        pvpSyncT -= dt
        if (pvpSyncT <= 0f) {
            pvpSyncT = 1f
            val myClan = Account.clan.value?.name ?: ""
            val base = if (hud[Native.PVP] > 0f) GameSession.ui("base_near") else null
            val blocked = base != null && base.optBoolean("owned") && !base.optBoolean("mine") &&
                Online.defenderInRing(base.optString("owner"), base.optDouble("x").toFloat(), base.optDouble("y").toFloat(), base.optDouble("ring").toFloat())
            synchronized(GameSession.lock) {
                val h = GameSession.handle
                Native.command(h, org.json.JSONObject().put("cmd", "pvp_hostile").put("h", Online.hostileFlags(myClan)).toString())
                Native.command(h, org.json.JSONObject().put("cmd", "capture_blocked").put("on", blocked).toString())
            }
        }
        if (hud[Native.DEAD] < 0.5f) deadNotified = false
        val lvl = hud[Native.LEVEL].toInt()
        if (lastLevel in 1 until lvl) ui.onLevelUp(lvl)
        lastLevel = lvl
        autosaveT += dt
        if (autosaveT > 25f) { autosaveT = 0f; Thread { GameSession.save() }.start() }
    }

    fun addChat(line: String) = synchronized(chatLines) {
        chatLines += line to 20f
        if (chatLines.size > 8) chatLines.removeAt(0)
    }

    private var lastVibe = 0L
    private var lastHurtFx = 0L
    private fun vibrate(ms: Long, amp: Int) {
        val lvl = Settings.current.haptics
        if (lvl == 0) return
        // titreşim bir sistem çağrısı: kurşun yağmurunda art arda çağrılınca kare düşürüyordu
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastVibe < 140) return
        lastVibe = now
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
                "botshot" -> {
                    val x1 = e.getDouble("x1").toFloat(); val y1 = e.getDouble("y1").toFloat()
                    val x2 = e.getDouble("x2").toFloat(); val y2 = e.getDouble("y2").toFloat()
                    val sniper = e.optInt("k") == 1
                    if (e.optInt("k") == 3) {
                        // alev makinesi: namludan hedefe alev konisi, ısı ışığı
                        val ang = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
                        val len = kotlin.math.hypot(x2 - x1, y2 - y1).coerceIn(120f, 380f)
                        fx("magma_breath", x1, y1, len * 1.15f, ang)
                        flashes += Light((x1 + x2) / 2, (y1 + y2) / 2, 320f, 0xFFFF9A30.toInt(), 0.15f, 0.15f)
                        if (Math.random() < 0.35) Audio.play("rocket", near(x1, y1) * 0.35f, 1.6f)
                        continue
                    }
                    tracers += Tracer(x1, y1, x2, y2, if (sniper) 0xFFFFE0A0.toInt() else 0xFFFFC870.toInt(), 0.12f, if (sniper) 2.6f else 1.6f)
                    val a = atan2(x2 - x1, y2 - y1)
                    fx("muzzle", x1, y1, if (sniper) 95f else 62f, Math.toDegrees(-a + PI / 2).toFloat(), dur = 0.06f, frame = (0..2).random())
                    flashes += Light(x1, y1, 180f, 0xFFFFC870.toInt(), 0.05f, 0.05f)
                    Audio.play(if (sniper) "shot_sniper" else "shot_rifle", near(x1, y1) * 0.8f, 0.9f + Math.random().toFloat() * 0.2f)
                    if (!e.optBoolean("hit")) { burst(x2, y2 + 40, 4, 0xFF9A8A70.toInt(), 90f, 3f, 0, 0.5f, 120f); fx(if (atlas.fx["miss"] != null) "miss" else "dust", x2, y2 + 30, 60f) }
                }
                "botdown" -> {
                    burst(x, y - 40, 14, 0xFF7A1A14.toInt(), 180f, 4f); fx("dust", x, y, 120f)
                    Audio.play("death", near(x, y) * 0.8f, 0.8f)
                    floaters += Floater(x, y - 150, "ASKER DÜŞTÜ", 0xFFFFB070.toInt(), 16f)
                }
                "immune" -> floaters += Floater(x, y, "KORUMALI", 0xFFFFE070.toInt(), 16f)
                "heli" -> { Audio.play("drone", 0.7f, 0.55f); ui.onToast("Helikopter Takviye Getiriyor!") }
                "basecap" -> Bases.capture(e.getInt("id"))
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
                    burst(hud[Native.PX], hud[Native.PY] - 60, 14, col, 140f, 5f, 1, 1f)
                    Audio.play("pickup", 0.9f, 1.3f)
                }
                "knock" -> { shake(12f, 0.35f); vibrate(120, 255); floaters += Floater(hud[Native.PX], hud[Native.PY] - 140, "YERE DÜŞTÜN", 0xFFFF8A50.toInt(), 22f) }
                "dmg" -> {
                    fx("blood", x, y + 50, 90f, (Math.random() * 50 - 25).toFloat())
                    fx("spark", x, y + 30, if (e.optBoolean("crit")) 105f else 78f, (Math.random() * 4).toInt() * 90f)
                    if (e.optBoolean("crit")) fx("crit", x, y + 30, 95f)
                    // Donmuş Liman: vuruşta buz kırıkları
                    if (hud[Native.ZONE].toInt() == ZONE_HARBOR) fx("frost", x, y + 30, 80f)
                    if (s.damageNumbers) floaters += Floater(x, y, e.getInt("v").toString(),
                        if (e.optBoolean("crit")) 0xFFFFD040.toInt() else Color.WHITE, if (e.optBoolean("crit")) 30f else 22f)
                    burst(x, y + 60, 6, 0xFF7A1A14.toInt(), 160f, 4f)
                }
                "pdmg" -> {
                    floaters += Floater(x, y, "-" + e.getInt("v"), 0xFFFF5050.toInt(), 24f)
                    vignetteRed = 0.6f
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastHurtFx > 180) { lastHurtFx = now; shake(7f, 0.18f); vibrate(40, 160); Audio.play("hurt", 0.8f) }
                }
                "xp" -> floaters += Floater(x, y, "+" + e.getInt("v") + " XP", 0xFF8CE0FF.toInt(), 18f, 1.4f, 1.4f)
                "gold" -> { floaters += Floater(x, y, "+" + e.getInt("v"), 0xFFFFD34A.toInt(), 20f); Audio.play("coin", 0.7f) }
                "pickup" -> {
                    val rc = rarityColor(e.optInt("rarity"))
                    floaters += Floater(x, y, "+" + e.getInt("count") + " " + e.getString("name"), rc, 19f, 1.6f, 1.6f)
                    Audio.play("pickup", 0.7f)
                    fx("sparkle", x, y - 30, 80f)
                }
                "invfull" -> ui.onToast("Çanta dolu!")
                "lvl" -> {
                    fx3d.levelUp(hud[Native.PX], hud[Native.PY], 62.5f)
                    setBanner("SEVİYE ${e.getInt("level")}", "+5 Can · +1 Yumruk Hasarı", 0xFFFFD34A.toInt())
                    Audio.play("levelup"); vibrate(120, 200)
                }
                "clvl" -> {
                    val lv = e.getInt("level")
                    val perk = GameSession.ui("tree").optJSONArray("perks")?.optString(lv - 1).orEmpty()
                    setBanner("SAVAŞÇI SEVİYE $lv", perk, 0xFFB277FF.toInt())
                }
                // PvP: vuruşu hedef oyuncuya ilet (hasarı onun cihazı uygular)
                "pvp" -> { Online.sendPvp(e.optInt("k"), e.optDouble("v").toFloat(), e.optBoolean("crit")); Audio.play("hit", 1f, 1.1f) }
                "hit" -> {
                    val kind = e.getInt("kind")
                    // V13: ağaçta odun kıymıkları + balta izi, kayada taş kırıkları ve kıvılcım
                    // 3B silahla vuruşta tek kaynak: silahın elementine göre patlama (magma, buz, kan, yıldırım, kıvılcım);
                    // eski kıvılcım sprite'ı ve parçacık patlaması ikinci kez çizilmesin (docs/22 §1.5)
                    val hit3d = kind == 0 && time - me3dT < 0.3f && me3dWeapon in 1..Fx3D.CHAINSAW
                    when (kind) {
                        1 -> fx3d.chopHit(x, y - 12f, 62.5f, y + 40f, lastAtkX)
                        2 -> fx3d.mineHit(x, y - 4f, 62.5f, y + 40f, lastAtkX)
                        else -> if (!hit3d) fx("spark", x, y, 70f, (Math.random() * 4).toInt() * 90f)
                    }
                    if (hit3d) fx3d.impact(x, y - 55f, me3dWeapon, 62.5f, y)
                    Audio.play(if (kind == 1) "chop" else "hit", near(x, y), 0.9f + Math.random().toFloat() * 0.2f)
                    if (!hit3d && kind == 0) burst(x, y + 40, 8, 0xFFB08A50.toInt(), 200f, 4f)
                    shake(2f, 0.08f)
                }
                "treefall" -> { Audio.play("treefall", near(x, y)); fx3d.treeFall(x, y, 62.5f) }
                "kill" -> {
                    fx("blood", x, y - 40, 150f)
                    if (Settings.current.particles) { if (pools.size > 30) pools.removeAt(0); pools += Pool(x, y, 95f) }
                    Audio.play("zdeath", near(x, y), 0.9f + Math.random().toFloat() * 0.2f)
                    burst(x, y, 16, 0xFF6A1410.toInt(), 220f, 5f)
                }
                "died" -> { Audio.play("death"); vibrate(300, 255); shake(10f, 0.4f) }
                "swing" -> {
                    swingT = time
                    if (time - me3dT < 0.3f && me3dWeapon in 1..Fx3D.CHAINSAW && hud[Native.IN_VEHICLE] < 0.5f)
                        fx3d.slash(hud[Native.PX], hud[Native.PY], if (lastAtkX == 0f && lastAtkY == 0f) 1f else lastAtkX, lastAtkY, me3dWeapon, me3dStage, 62.5f)
                    Audio.play("swing", 0.6f, 0.9f + Math.random().toFloat() * 0.25f)
                    // kesik izi: saldırı yönüne dönük kızıl yay (katana geniş, bıçak kısa); yumrukta yok
                    val wi = hud[Native.WEAPON].toInt()
                    if (wi > 0 && hud[Native.IN_VEHICLE] < 0.5f && time - me3dT > 0.3f) {
                        val big = weaponSprites.getOrElse(wi) { "" }.contains("katana")
                        val ax = if (lastAtkX == 0f && lastAtkY == 0f) 1f else lastAtkX
                        val ay = lastAtkY
                        val ang = Math.toDegrees(kotlin.math.atan2(ay, ax).toDouble()).toFloat()
                        val d = if (big) 62f else 44f
                        fx("slash", hud[Native.PX] + ax * d, hud[Native.PY] - 58 + ay * d * 0.6f, if (big) 130f else 80f, ang)
                    }
                }
                "shoot" -> {
                    val w = e.getInt("w")
                    val a = e.getDouble("a").toFloat()
                    Audio.play(when (w) { 3 -> "shot_pistol"; 6 -> "shot_sniper"; 7 -> "rocket"; else -> "shot_rifle" }, near(x, y), 0.95f + Math.random().toFloat() * 0.1f)
                    if (time - me3dT < 0.3f && me3dWeapon >= Fx3D.PISTOL) {
                        // 3B: alev, duman ve kovan silahın gerçek namlusundan
                        flashes += Light(me3dMx, me3dMy, 220f, 0xFFFFC870.toInt(), 0.06f, 0.06f)
                        fx3d.muzzle(me3dMx, me3dMy, sin(a), cos(a), me3dWeapon, 62.5f, y)
                        // roketatar: arkadan alev ve duman, ışık
                        if (me3dWeapon == Fx3D.RPG) {
                            fx3d.backblast(me3dRx, me3dRy, sin(a), cos(a), 62.5f, y)
                            flashes += Light(me3dRx, me3dRy, 260f, 0xFFFF9040.toInt(), 0.12f, 0.12f)
                        }
                    } else {
                        flashes += Light(x + sin(a) * 50, y + cos(a) * 30 - 70, 220f, 0xFFFFC870.toInt(), 0.06f, 0.06f)
                        fx("muzzle", x + sin(a) * 58, y + cos(a) * 36 - 78, if (w >= 6) 110f else 75f, Math.toDegrees(-a + PI / 2).toFloat(), dur = 0.07f, frame = (0..2).random())
                        burst(x + sin(a) * 40, y + cos(a) * 25, 2, 0xFFD8B060.toInt(), 120f, 2.5f, 0, 0.8f, 200f)
                    }
                    shake(if (w == 6 || w == 7) 6f else 2.5f, 0.07f)
                }
                "reload" -> Audio.play("reload", 0.8f)
                "noammo" -> { Audio.play("empty", 0.8f); ui.onToast("Mermi yok!") }
                "explode" -> {
                    val r = e.getDouble("r").toFloat()
                    // V16: tek tarif — ışık · ateş topu/halka/kıvılcım/enkaz · yanık leke ve duman sütunu (eski 4 sprite + 2 parçacık patlaması kalktı)
                    fx3d.explode(x, y - r * 0.3f, r, r >= 300f)
                    Audio.play("explosion", max(0.35f, near(x, y)))
                    flashes += Light(x, y - 40, r * 3f, 0xFFFFA040.toInt(), 0.5f, 0.5f)
                    shake(14f * near(x, y) + 3f, 0.4f); vibrate(120, 220)
                }
                "groan" -> if (Math.random() < 0.6) Audio.play("groan" + (0..2).random(), near(x, y) * 0.7f, 0.85f + Math.random().toFloat() * 0.3f)
                "teleport" -> {
                    fx("teleport", x, y - 70, 220f)
                    Audio.play("teleport", near(x, y)); burst(x, y, 26, 0xFFB070FF.toInt(), 260f, 5f, 1, 0.8f) }
                "toxic" -> {
                    fx("toxic", x, y + 10, 90f, dur = 5f)
                    Audio.play("toxic", near(x, y)); burst(x, y, 30, 0xFF9AFF40.toInt(), 180f, 9f, 1, 1.4f) }
                "slam" -> {
                    fx("shockwave", x, y, e.getDouble("r").toFloat() * 2.4f)
                    fx("dust", x, y - 20, e.getDouble("r").toFloat() * 1.6f)
                    Audio.play("explosion", near(x, y) * 0.8f, 0.6f)
                    burst(x, y, 30, 0xFF8A7A60.toInt(), e.getDouble("r").toFloat() * 2f, 6f, 0, 0.8f)
                    shake(10f * near(x, y), 0.3f)
                }
                "magma" -> {
                    // V13 Magma Titan: pençe alev izi, yere vurma lav patlaması, ateş püskürtme konisi
                    val tx = e.getDouble("tx").toFloat(); val ty = e.getDouble("ty").toFloat()
                    val ang = Math.toDegrees(kotlin.math.atan2((ty - y).toDouble(), (tx - x).toDouble())).toFloat()
                    when (e.getInt("a")) {
                        0 -> { fx("magma_claw", tx, ty - 110f, 240f, ang); Audio.play("swing", near(x, y), 0.6f); shake(4f, 0.15f) }
                        1 -> {
                            fx("magma_slam", x, y + 10f, 560f); fx("fire", x, y + 10, 200f, dur = 2.2f)
                            flashes += Light(x, y - 40, 900f, 0xFFFF7A20.toInt(), 0.6f, 0.6f)
                            burst(x, y, 40, 0xFFFF8A20.toInt(), 420f, 7f, 1, 0.9f)
                            Audio.play("explosion", max(0.4f, near(x, y)), 0.7f); shake(16f * near(x, y) + 4f, 0.5f); vibrate(160, 255)
                        }
                        else -> {
                            fx("magma_breath", x + (tx - x) * 0.08f, y - 150f + (ty - y) * 0.08f, 560f, ang)
                            flashes += Light((x + tx) / 2, (y + ty) / 2 - 60, 700f, 0xFFFF9A30.toInt(), 0.9f, 0.9f)
                            Audio.play("rocket", near(x, y), 0.5f); shake(6f, 0.6f)
                        }
                    }
                }
                "boss" -> { setBanner(e.getString("name"), "Bir boss uyandı!", 0xFFFF4A3A.toInt()); Audio.play("boss"); shake(8f, 0.6f) }
                "quest" -> {
                    // başlık olayın türüne göre: yeni görev, teslim, konuşmaya git ya da biten görev
                    val title = e.getString("title")
                    when {
                        title.startsWith("Yeni Görev: ") -> setBanner("YENİ GÖREV", title.removePrefix("Yeni Görev: "), 0xFFFFD34A.toInt())
                        title.startsWith("Görev Teslim Edildi: ") -> {
                            setBanner("GÖREV TAMAMLANDI", title.removePrefix("Görev Teslim Edildi: "), 0xFF7FE07F.toInt()); Audio.play("quest")
                            fx("sparkle", 0f, 0f, 190f, follow = true)
                        }
                        title.contains(" ile konuş") -> { setBanner("GÖREV HAZIR", title, 0xFF7FE07F.toInt()); Audio.play("quest") }
                        else -> {
                            setBanner("GÖREV TAMAMLANDI", title, 0xFF7FE07F.toInt()); Audio.play("quest")
                            fx("sparkle", 0f, 0f, 190f, follow = true)
                        }
                    }
                }
                "zone" -> {
                    val lo = e.getInt("lo"); val hi = e.getInt("hi")
                    zoneLabel = e.getString("name"); zoneLabelOf = hud[Native.ZONE].toInt()
                    setBanner(e.getString("name"), if (e.optBoolean("safe")) "Güvenli bölge" else "Seviye $lo–$hi",
                        if (e.optBoolean("safe")) 0xFF7FE0A0.toInt() else 0xFFFFE9A8.toInt())
                }
                "locked" -> ui.onToast("Şehir kapısı kilitli: Seviye 10 gerekli")
                "bomb" -> {
                    bombs += Bomb(x, y, e.optDouble("dur", 2.4).toFloat())
                    Audio.play("rocket", 0.8f, 0.6f)  // ıslık gibi alçak tonlu roket sesi
                    shake(4f, 0.4f)
                }
                "spear" -> {
                    val x1 = e.getDouble("x1").toFloat(); val y1 = e.getDouble("y1").toFloat()
                    spears += Spear(x1, y1, e.getDouble("x2").toFloat(), e.getDouble("y2").toFloat(), e.getDouble("dur").toFloat())
                    Audio.play("swing", near(x1, y1) * 0.7f, 0.7f)
                }
                "bolt" -> {
                    // gökten piksel yıldırım (V7 efekt; çapa alt uçta, hedefe iner) + toz
                    fx("bolt", x, y + 6, 150f)
                    fx("dust", x, y - 8, 150f)
                    whiteFlash = 0.55f
                    flashes += Light(x, y, 420f, 0xFFC8E0FF.toInt(), 0.4f, 0.4f)
                    burst(x, y, 18, 0xFFE0F0FF.toInt(), 260f, 5f, 1, 1f)
                    shake(10f, 0.3f)
                    Audio.play("thunder", near(x, y).coerceAtLeast(0.5f))
                }
                "doghit" -> floaters += Floater(x, y - 70, "-" + e.getInt("v"), 0xFFFFA060.toInt(), 18f)
                "drone" -> Audio.play("drone", near(x, y) * 0.5f, 1.3f)
                "shield" -> { Audio.play("shield"); floaters += Floater(hud[Native.PX], hud[Native.PY] - 140, "+" + e.getInt("v") + " KALKAN", 0xFF70C8FF.toInt(), 20f) }
                "heal" -> { Audio.play("heal", 0.6f); floaters += Floater(x, y - 140, "+" + e.getInt("v"), 0xFF60FF80.toInt(), 22f); burst(x, y, 10, 0xFF70FF90.toInt(), 80f, 5f, 1, 1f) }
                "rage" -> {
                    fx("rage", 0f, 0f, 220f, follow = true, dur = 5f)
                    Audio.play("rage"); setBanner("ÖFKE", "Hasar arttı!", 0xFFFF5A3A.toInt()) }
                "cloak" -> {
                    fx("levelup", 0f, 0f, 110f, follow = true)
                    Audio.play("shield", 1f, 0.7f); setBanner("TANRI PELERİNİ", "3 sn ölümsüzlük", 0xFFFFD34A.toInt()) }
                "lastbreath" -> setBanner("SON NEFES", "Ölümden döndün!", 0xFFFF8080.toInt())
                "crate" -> { Audio.play("crate", near(x, y)); burst(x, y, 18, 0xFFB08A50.toInt(), 260f, 5f) }
                "car_hit" -> { Audio.play("car_hit", near(x, y)); shake(5f, 0.15f); vibrate(40, 150) }
                "enter" -> { Audio.play("engine", 0.8f); fx("dust", hud[Native.PX], hud[Native.PY] - 10, 150f) }
                "exit" -> { Audio.play("click", 0.6f); fx("dust", hud[Native.PX], hud[Native.PY] - 10, 130f) }
                "msg" -> ui.onToast(e.getString("text"))
                "step" -> if (Math.random() < 0.5) Audio.play("step" + (0..1).random(), 0.25f, 0.9f + Math.random().toFloat() * 0.2f)
            }
        }
    }

    /** Afişi kuyruğa ekler: aynısı zaten gösteriliyor/sırada ise atlanır; kuyruk en çok 3 (en eskisi düşer) */
    private fun setBanner(t: String, sub: String, color: Int) {
        if ((bannerT > 0f && banner == t && bannerSub == sub) || bannerQueue.any { it.first == t && it.second == sub }) return
        if (bannerT <= 0f) { banner = t; bannerSub = sub; bannerColor = color; bannerT = BANNER_DUR; return }
        // bölge değişimi gibi eski haber yenisine yer açar
        if (bannerQueue.size >= 3) bannerQueue.removeFirst()
        bannerQueue.addLast(Triple(t, sub, color))
        // sırada bekleyen varken gösterilen afiş kısalır (bilgi gecikmesin)
        bannerT = min(bannerT, 1.2f)
    }

    private fun rarityColor(r: Int) = when (r) {
        1 -> 0xFF5AA8FF.toInt(); 2 -> 0xFFC070FF.toInt(); 3 -> 0xFFFFB030.toInt(); 4 -> 0xFFFF5A5A.toInt(); else -> 0xFFE8E8E8.toInt()
    }

    // ------------------------------------------------------------------ çizim
    private fun wx(x: Float) = (x - camX) * scale + sw / 2f
    private fun wy(y: Float) = (y - camY) * scale + sh / 2f

    /**
     * Baş üstü yazısı. [lvl] > 0: yaratık plakası (adı üstte; altında can çubuğu, çubuğun solunda seviye rozeti, içinde
     * "can / en çok"). [self]: kendi karakterin (asla gizlenmez). [hpCur] < 0: can sayısı yok.
     */
    private class Plate(val x: Float, val y: Float, val text: String, val color: Int, val hp: Float, val boss: Boolean, val sub: String?, val subColor: Int = 0xFFDDDDDD.toInt(),
                        val title: String? = null, val titleRank: Int = 0, val hpText: String? = null, val small: Boolean = false,
                        val lvl: Int = 0, val hpCur: Int = -1, val hpMax: Int = 0, val self: Boolean = false)
    private val plates = ArrayList<Plate>()

    /** tanıtım/test: arayüzsüz (temiz) kare ve sabit görüş yüksekliği (metre); oyuncu ayarlarına dokunmaz */
    internal var clean = false
    /** yerel oyuncu çizilmez (lobi arka planı çekimi: kahraman lobide ayrıca 3B çizilir) */
    internal var hideMe = false
    internal var promoZoom = 0f

    private fun render(c: Canvas, dt: Float) {
        val s = Settings.current
        frameDt = dt
        c.drawColor(0xFF203018.toInt())
        c.save()
        if (shakeT > 0) {
            val a = shakeAmp * (shakeT / 0.3f).coerceIn(0f, 1f) * density
            c.translate((Math.random().toFloat() - 0.5f) * a * 2, (Math.random().toFloat() - 0.5f) * a * 2)
        }
        drawGround(c)
        drawTracks(c, dt)
        drawPools(c, dt)
        drawChests(c)
        fx3d.drawGround(c, camX, camY, scale, sw.toFloat(), sh.toFloat())
        plates.clear()
        nearNpc = -1; nearNpcD = Float.MAX_VALUE
        npcBoxes.clear(); benchBoxes.clear(); synchronized(playerBoxes) { playerBoxes.clear() }
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
                D_SOLDIER -> drawSoldier(c, o)
                D_HELI -> drawHeli(c, o)
                D_FLAG -> drawFlag(c, o)
            }
        }
        drawFx(c)
        drawParticles(c)
        fx3d.draw(c, camX, camY, scale, sw.toFloat(), sh.toFloat())
        drawTracers(c)
        drawSpears(c)
        drawBombs(c)
        drawBolts(c)
        drawLighting(c)
        if (!clean) drawPlates(c)
        drawFloaters(c)
        drawAreaEdge(c, px, py)
        c.restore()
        if (!clean) {
            drawHud(c)
            drawBaseHud(c)
            drawControls(c)
            drawBuild(c)
            drawTutorial(c)
        }
        // FPS ve ping: sağ alt köşede tek satır (sol alttaki seviye/koordinat yazılarıyla çakışmaz)
        if (!clean && (s.showFps || s.showPing)) {
            val d = density
            var x = sw - 10 * d
            val y = sh - 9 * d
            text.typeface = fontBold; text.textSize = 11 * d
            if (s.showPing) {
                // ping: yeşil iyi, sarı orta, kırmızı kötü; bağlantı yoksa gri
                val pm = Online.ping()
                val col = when { pm < 0 -> 0xFF9A9A9A.toInt(); pm < 90 -> 0xFF7AE07A.toInt(); pm < 180 -> 0xFFF6D04A.toInt(); else -> 0xFFFF6A5A.toInt() }
                val t = if (pm < 0) "Ping —" else "Ping $pm ms"
                x -= text.measureText(t)
                drawText(c, t, x, y, 11 * d, col)
                // sinyal çubukları (1 kırmızı … 4 camgöbeği)
                val bars = when { pm < 0 -> -1; pm < 90 -> 3; pm < 180 -> 2; pm < 300 -> 1; else -> 0 }
                if (bars >= 0) com.zenvex.online.ui.UiArt.bitmap(context, "ping_$bars")?.let { b ->
                    val bs = 14 * d
                    x -= bs + 4 * d
                    tutOval.set(x, y - bs + 2 * d, x + bs, y + 2 * d); c.drawBitmap(b, null, tutOval, pixelPaint)
                }
                x -= 10 * d
            }
            if (s.showFps) {
                val t = "${fps.roundToInt()} FPS"
                text.textSize = 11 * d
                x -= text.measureText(t)
                drawText(c, t, x, y, 11 * d, 0xFF9AFF9A.toInt())
            }
        }
    }

    /**
     * Bölge sınırının ötesi başka harita: sınırdan dışarı doğru koyulaşan sis (katmanlı, her katman bölgeyi biraz daha
     * genişleterek dışarıda kalanı karartır). Ekran tamamen bölge içindeyse hiçbir şey çizilmez.
     */
    private fun drawAreaEdge(c: Canvas, px: Float, py: Float) {
        val i = com.zenvex.online.ui.areaIndexAt(areas, px, py)
        val a = areas.getOrNull(i) ?: return
        val vx0 = camX - viewW / 2 - tile * 5; val vy0 = camY - viewH / 2 - tile * 5
        val vx1 = camX + viewW / 2 + tile * 5; val vy1 = camY + viewH / 2 + tile * 5
        if (a.rects.any { vx0 >= it[0] && vy0 >= it[1] && vx1 <= it[2] && vy1 <= it[3] }) return
        fill.style = Paint.Style.FILL
        fill.color = 0x5A0C0A12
        for (e in AREA_FADE) {
            c.save()
            for (r in a.rects) {
                rect.set(wx(r[0] - e * tile), wy(r[1] - e * tile), wx(r[2] + e * tile), wy(r[3] + e * tile))
                c.clipOutRect(rect)
            }
            c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), fill)
            c.restore()
        }
    }

    /** zemin dokuları yüklü bölge (değişince bölgenin kendi dokuları yüklenir, parçalar yeniden çizilir) */
    private var groundArea = -2

    private fun drawGround(c: Canvas) {
        val ai = com.zenvex.online.ui.areaIndexAt(areas, hud[Native.PX], hud[Native.PY])
        if (ai != groundArea && ai >= 0) {
            groundArea = ai
            GameSession.setRegion(ai, areas[ai].id)
            clearChunks()
        }
        // gerçek bölge haritası (mini harita ve büyük harita): bölgenin dokuları yüklenince bir kez
        if (ai >= 0 && groundReady) RegionMap.ensure(ai, areas, staticSprites)
        val x0 = floor((camX - viewW / 2) / CH).toInt() - 0
        val y0 = floor((camY - viewH / 2) / CH).toInt() - 0
        val x1 = floor((camX + viewW / 2) / CH).toInt()
        val y1 = floor((camY + viewH / 2) / CH).toInt()
        // ilk kare / bölge değişimi (ışınlanma, yükleme ekranı arkası): görünenler eşzamanlı, sonra hep arka planda
        val sync = synchronized(chunks) { chunks.isEmpty() } || Model3D.sync
        if (!sync) startGroundWorker()
        for (cy in y0..y1) for (cx in x0..x1) {
            val key = chunkKey(cx, cy)
            var b = synchronized(chunks) { chunks[key] }
            if (b == null) {
                if (sync) {
                    val ok = synchronized(GameSession.lock) { Native.renderChunk(GameSession.handle, cx * CH.toFloat(), cy * CH.toFloat(), CH, chunkPx) }
                    if (ok != 0) {
                        b = Bitmap.createBitmap(chunkPx, 0, CH, CH, CH, Bitmap.Config.ARGB_8888)
                        synchronized(chunks) { chunks[key] = b }
                        groundFill = chunkPx[CH * CH / 2 + CH / 2] or 0xFF000000.toInt()
                    } else groundReady = false
                } else requestChunk(key, true)
            }
            val l = wx(cx * CH.toFloat()); val t = wy(cy * CH.toFloat())
            rect.set(l, t, l + CH * scale + 1, t + CH * scale + 1)
            if (b != null) c.drawBitmap(b, null, rect, bmpPaint)
            else { fill.color = groundFill; c.drawRect(rect, fill) }
        }
        if (sync) return
        // önceden yükleme: görüşün bir parça çevresi + gidiş yönünde iki parça ileri
        val vx = camX - lastCamCX; val vy = camY - lastCamCY
        lastCamCX = camX; lastCamCY = camY
        for (cy in y0 - 1..y1 + 1) for (cx in x0 - 1..x1 + 1) if (cx < x0 || cx > x1 || cy < y0 || cy > y1) requestChunk(chunkKey(cx, cy), false)
        if (kotlin.math.abs(vx) + kotlin.math.abs(vy) > 0.5f) {
            val dx = if (vx > 0.5f) 1 else if (vx < -0.5f) -1 else 0
            val dy = if (vy > 0.5f) 1 else if (vy < -0.5f) -1 else 0
            for (cy in y0..y1) for (cx in x0..x1) requestChunk(chunkKey(cx + dx * 2, cy + dy * 2), false)
        }
    }

    /** Hazır olmayan parçanın yerine bölgenin ortalama zemin tonu (yeşil kare göze batmasın) */
    private var groundFill = 0xFF3E4A2E.toInt()

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
        var paint: Paint = if (sp.k != 1f) pixelPaint else bmpPaint  // V6 piksel kamp nesneleri keskin çizilir
        // ağaç / bina arkasında kalan oyuncuyu göstermek için saydamlaştır
        if (Settings.current.treeFade && (kind == 0 || kind == 4 || kind == 11) && py < y - 10) {
            // görsel ölçüsü kaynak pikselinde: dünya birimine sp.k ile
            val sw0 = sp.w * sp.k
            val l = x - sp.ax * sp.k; val r = l + sw0
            val t = y - sp.ay * sp.k
            if (px > l + sw0 * 0.12f && px < r - sw0 * 0.12f && py > t + 20 && py < y) {
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
        if (hp >= 0) bar(c, sx, sy - sp.ay * sp.k * scale * 0.55f, 46 * density, hp, 0xFFE0C060.toInt())
        // tezgâh: yakındayken yerde nabız gibi atan halka ve üstünde çekiçli rozet (dokununca üretim açılır)
        if (kind == 9) {
            val dd = hypot(x - px, y - py)
            val top = sy - sp.ay * sp.k * scale
            benchBoxes += floatArrayOf(sx - sp.w * sp.k * scale * 0.5f, top, sx + sp.w * sp.k * scale * 0.5f, sy + 6 * density, dd)
            if (dd < 520f) {
                val a = ((520f - dd) / 200f).coerceIn(0f, 1f)
                val ph = (time * 1.2f) % 1f
                stroke.color = alphaC(0xFFFFC85A.toInt(), a * 0.75f * (1f - ph)); stroke.strokeWidth = 2f * density
                val rr = sp.w * sp.k * scale * (0.55f + 0.25f * ph)
                c.drawOval(sx - rr, sy - rr * 0.36f, sx + rr, sy + rr * 0.36f, stroke)
                val by = top - (14 + sin(time * 3f) * 3f) * density
                val br = 13 * density
                fill.color = alphaC(0xE01E1A12.toInt(), a); c.drawCircle(sx, by, br, fill)
                stroke.color = alphaC(0xFFFFC85A.toInt(), a); stroke.strokeWidth = 2f * density; c.drawCircle(sx, by, br, stroke)
                drawIcon(c, "craft", sx, by, br * 1.1f, alphaC(0xFFFFE6A8.toInt(), a))
                if (dd < 300f) drawText(c, "Tezgâh · Dokun", sx, by - br - 5 * density, 10.5f * density, alphaC(0xFFFFE6A8.toInt(), a), center = true)
            }
        }
        // ışık kaynakları
        if (kind == 7 || kind == 8) {
            val night = 1f - hud[Native.LIGHT]
            if (night > 0.05f) {
                val ly = if (kind == 7) y - sp.ay + 22 else y - 25
                val lx = if (kind == 7) x - sp.ax + sp.w * 0.5f else x
                lightSources += floatArrayOf(lx, ly, if (kind == 7) 420f else 330f, if (kind == 7) 1f else 2f)
            }
            // kamp ateşi: seyrek, küçük piksel kıvılcımlar (kare, titreşen, sarıdan kora döner); eskiden iri sarı daireler çıkıyordu
            if (kind == 8 && Settings.current.particles && Math.random() < 0.09) {
                val life = 0.9f + Math.random().toFloat() * 0.7f
                particles += Particle(x + (Math.random().toFloat() - 0.5f) * 22, y, 26f, (Math.random().toFloat() - 0.5f) * 14, 0f,
                    55f + Math.random().toFloat() * 45, life, life, 0xFFFFD060.toInt(), 1.6f + Math.random().toFloat(), 4)
            }
        }
    }

    private val lightSources = ArrayList<FloatArray>()
    /** son bölge girişinin adı (karakollarda yerin kendi adı) ve hangi bölge için geldiği */
    private var zoneLabel = ""
    private var zoneLabelOf = -1

    private val heroIds = arrayOf("kaan", "nova", "rady", "elif")
    private val heroAnimNames = arrayOf("idle", "walk", "run", "punch", "melee", "pistol_idle", "pistol_walk", "rifle_idle", "rifle_walk", "cast", "hurt", "death", "ride", "roll", "knockdown", "getup",
        "pickup", "chop", "mine", "open", "drink", "cheer", "point")
    private val zAnimNames = arrayOf("idle", "walk", "attack", "hurt", "death", "slam", "breath")
    private val tiers = arrayOf("", "basic", "police", "military", "titan")
    private val keyBuf = StringBuilder(48)

    private fun frameOf(anims: Map<String, IntArray>, anim: String, t: Float): Int {
        val a = anims[anim] ?: return 0
        val f = (t * a[1]).toInt()
        return if (a[2] == 1) f.mod(a[0]) else f.coerceAtMost(a[0] - 1)
    }

    /** [viewDir] sonucunun aynalanıp aynalanmayacağı (çizim iş parçacığı tek; ayırma yapmamak için alan) */
    private var viewFlip = false

    /**
     * Piksel aktör görünümü: yön 2/6 → yan kare (yön 6, sola bakar; 2 aynası), 3-5 → sırt (yön 4), diğerleri ön (yön 0, sola
     * bakar; 1-3 aynası). İstenen görünümün karesi yoksa ön görünüme düşer.
     */
    private fun viewDir(prefix: String, anim: String, dir: Int, f: Int): Int {
        if ((dir == 2 || dir == 6) && atlas.has(spriteKey(prefix, anim, 6, f))) { viewFlip = dir == 2; return 6 }
        if (dir in 3..5 && atlas.has(spriteKey(prefix, anim, 4, f))) { viewFlip = false; return 4 }
        viewFlip = dir in 1..3
        return 0
    }

    /**
     * Etkinlik silahı: kahramanın aynı karesinin katana / bıçaklı halinden çıkarılmış kabza→uç doğrusu boyunca
     * (manifest attach "w_katana" / "w_knife", tools/pixel/event_import.py) silah görseli döndürülüp ölçeklenerek çizilir.
     */
    private fun drawEvWeapon(c: Canvas, prefix: String, key: String, id: String, cls: String, sx: Float, sy: Float, s: Float, flip: Boolean) {
        val seg = atlas.attach4(prefix, key.substringAfter("$prefix/"), "w_$cls") ?: return
        val ws = atlas["w/ev_$id"] ?: return
        val m = atlas.evWeapon(id) ?: return
        val f = if (flip) -1f else 1f
        val gx = seg[0] * s * f; val gy = seg[1] * s
        val tx = seg[2] * s * f; val ty = seg[3] * s
        val len = hypot(tx - gx, ty - gy)
        val wl = hypot(m[2] - m[0], m[3] - m[1])
        if (len < 1f || wl < 1f) return
        val ang = Math.toDegrees(atan2((ty - gy).toDouble(), (tx - gx).toDouble()) - atan2((m[3] - m[1]).toDouble(), (m[2] - m[0]).toDouble()))
        c.save()
        c.translate(sx + gx, sy + gy)
        c.rotate(ang.toFloat())
        atlas.draw(c, ws, 0f, 0f, len * m[4] / wl / ws.k, pixelPaint)
        c.restore()
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
        if (hideMe && who == -1f) return
        if (ch >= 4 && drawNpc6(c, ch, x, y, sx, if (who <= -3f) sy - 235f * scale else sy, dir, t)) return
        // aura karakterin altında, zeminde
        if (who == -1f && hud[Native.DEAD] < 0.5f) {
            val tier = AuraFx.tierOf(hud[Native.LEVEL].toInt(), hud[Native.CHAR_LEVEL].toInt())
            if (tier >= 0) drawAura(c, sx, sy, tier)
        }
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
        // ekipman kodu: silah + 32 × pelerin (draw.rs EQ_CLOAK)
        val wIdx = drawBuf[o + 10].toInt() % 32
        // 3B kahraman (kare hazırsa); değilse piksel karakter. İsim, kalkan, zehir ortak.
        val drew3d = ch in 0..3 && drawHero3D(c, ch, x, y, sx, sy, dir, animI, t, flash, who, wIdx)
        if (!drew3d) {
            val f = when {
                anim == "melee" && who == -1f -> meleeFrame(t, wIdx)
                (anim == "walk" || anim == "run") && who == -1f -> strideFrame(x, y, anim, atlas.anims(prefix, atlas.heroAnims)[anim]?.get(0) ?: 8)
                else -> frameOf(atlas.anims(prefix, atlas.heroAnims), anim, t)
            }
            // piksel kahraman: ön / yan / sırt görünümü bakış yönüne göre (kare yoksa ön), sağa bakanlar aynalanır
            val pix = ch in 0..3
            val kdir = if (pix) viewDir(prefix, anim, dir, f) else dir
            val flip = pix && viewFlip
            var key = spriteKey(prefix, anim, kdir, f)
            // elde gerçek silahla render edilmiş kare varsa onu kullan
            val evw = if (wIdx > 0) evWeapons.getOrNull(wIdx) else null
            val wKey = if (evw != null) null else if (wIdx > 0) "$key@" + weaponSprites.getOrElse(wIdx) { "" } else if (ch == 4) "$key@m4" else null
            var weaponBaked = false
            var sp = if (wKey != null) atlas[wKey]?.also { weaponBaked = true } else null
            if (sp == null) sp = atlas[key]
            if (sp == null) { key = spriteKey(prefix, "idle", kdir, 0); sp = atlas[key] }
            if (sp == null) { placeholder(c, sx, sy, 0xFF4A7AB0.toInt()); return }
            // Elmas Sandığı kaplaması: kendi kahramanın ve diğer oyuncuların
            val skin = when { who == -1f -> Skins.char; who >= 0f -> Online.lastSkins.getOrNull(who.toInt()) ?: ""; else -> "" }
            val paint = when {
                flash == 1f -> flashPaint
                flash == 2f -> { tintPaint.colorFilter = PorterDuffColorFilter(0x66FFD34A, PorterDuff.Mode.SRC_ATOP); tintPaint }
                skin.isNotEmpty() -> skinPaint(skin, prefix.startsWith("h/"))
                prefix.startsWith("h/") -> pixelPaint
                else -> bmpPaint
            }
            if (skin.isNotEmpty() && Settings.current.particles) {
                val tc = Skins.trail(skin)
                if (tc != 0 && Math.random() < 0.18) particles += Particle(x + (Math.random().toFloat() - 0.5f) * 50f, y, 20f + Math.random().toFloat() * 90f,
                    (Math.random().toFloat() - 0.5f) * 30f, 0f, 35f + Math.random().toFloat() * 30f, 0.9f, 0.9f, tc, 3.5f, 3)
            }
            // ekipman kodları
            val e0 = drawBuf[o + 8].toInt(); val e1 = drawBuf[o + 9].toInt(); val e2 = drawBuf[o + 10].toInt()
            val helmet = tiers.getOrElse(e0 / 8) { "" }; val jacket = tiers.getOrElse(e0 % 8) { "" }
            val boots = tiers.getOrElse(e1 / 8) { "" }; val bag = tiers.getOrElse(e1 % 8) { "" }
            val weapon = e2 % 32; val cloak = e2 / 32 > 0
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
            if (who == -1f) {
                // takla: her 35 ms'de bir art görüntü bırak, eskileri sönerek çiz
                if (anim == "roll" && time - ghostT > 0.035f) { ghosts += Ghost(x, y, key, flip, 0.32f); ghostT = time }
                for (g in ghosts) {
                    if (g.fr != null) continue
                    val gs = atlas[g.key] ?: continue
                    ghostPaint.alpha = (g.life / 0.32f * 150).toInt().coerceIn(0, 255)
                    atlas.draw(c, gs, wx(g.x), wy(g.y), scale, ghostPaint, g.flip)
                }
            }
            atlas.draw(c, sp, sx, sy, scale, paint, flip)
            if (evw != null && isHero) drawEvWeapon(c, prefix, key, evw, weaponSprites.getOrElse(wIdx) { "katana" }, sx, sy, scale * sp.k, flip)
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
        }
        // isim etiketi: kafanın hemen üstü (sabit boy; silah ya da kalkan kare yüksekliğini değiştirmesin)
        val top = wy(y) - 1.95f * 62.5f * this.scale
        when {
            who == -1f -> {
                if (localName.isNotEmpty()) {
                    val t = Account.profile.value.optJSONObject("title")
                    plates += Plate(sx, top - 4 * density, localName, 0xFFFFFFFF.toInt(), -1f, false, localClan.ifEmpty { null }, CLAN_COLOR,
                        t?.optString("name")?.ifEmpty { null }, t?.optInt("rank") ?: 0, self = true)
                }
                if (hud[Native.SHIELD] > 0) drawFxSprite(c, "shield", x, y - 62, 175f, time)
                if (Settings.current.particles && hud[Native.POISON] > 0 && Math.random() < 0.2)
                    particles += Particle(x, y, 60f, 0f, 0f, 40f, 0.8f, 0.8f, 0xFF9AFF40.toInt(), 4f, 3)
            }
            who >= 0f -> {
                val n = Online.lastNames.getOrNull(who.toInt()) ?: "Oyuncu"
                val cl = Online.lastClans.getOrNull(who.toInt())?.ifEmpty { null }
                val tt = Online.lastTitles.getOrNull(who.toInt())
                plates += Plate(sx, top - 4 * density, n, 0xFF7FE0FF.toInt(), -1f, false, cl, CLAN_COLOR, tt?.first?.ifEmpty { null }, tt?.second ?: 0)
                // dokunulunca oyuncu menüsü (fısıltı, takas, arkadaş, profil, şikâyet)
                Online.lastIds.getOrNull(who.toInt())?.let { u -> synchronized(playerBoxes) { playerBoxes += PlayerBox(sx - 34 * density, top - 10 * density, sx + 34 * density, sy + 6 * density, u, n) } }
            }
            who <= -2f && ch == 5 -> plates += Plate(sx, top - 4 * density, "Tüccar", 0xFFFFD34A.toInt(), -1f, false, "Alışveriş")
        }
    }

    // ------------------------------------------------------------------ 3B kahramanlar (rust/zenvex_r3d)
    private val fx3d = Fx3D()
    private var frameDt = 0.016f
    /** aktör başına yumuşatılmış bakış yönü (derece) */
    private val yaw3d = HashMap<String, Float>()
    /** uzak sürücülerin son konumu ve yumuşatılmış hızı (tekerlek dönüşü için) */
    private val riderVel = HashMap<String, FloatArray>()
    private val dst3d = RectF()
    /** yerel kahramanın son 3B karesi: zaman, silah (Wk), namlu ucu (ekran dünyası) — ateş/vuruş efektleri buradan çıkar */
    private var me3dT = -9f
    private var me3dWeapon = 0
    private var me3dMx = 0f
    private var me3dMy = 0f
    /** silahın arka ucu (roketatar arka alevi) ve kombonun vuruş sırası */
    private var me3dRx = 0f
    private var me3dRy = 0f
    private var me3dStage = 0
    private val shadowM = android.graphics.Matrix()
    private val shadowVals = FloatArray(9).also { it[8] = 1f }
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(0xFF000000.toInt(), PorterDuff.Mode.SRC_IN) }

    /**
     * Gerçek siluet gölgesi: karenin kendisi zemine yatırılır (ayak çizgisi etrafında ters çevrilip ezilir, ışık sol üstten
     * olduğu için sağ alta savrulur); bu yüzden gölge duruşa, silaha, takla ve düşmeye aynen uyar. İki geçiş: koyu çekirdek + yumuşak yarı gölge.
     * [contact]: ayak altındaki küçük koyu temas lekesinin genişliği (px).
     */
    private fun drawShadow3d(c: Canvas, fr: Model3D.Frame, sx: Float, sy: Float, k: Float, contact: Float, strength: Float = 1f) {
        for ((flat, skew, alpha) in arrayOf(Triple(0.30f, 0.55f, 0.30f), Triple(0.38f, 0.68f, 0.16f))) {
            shadowVals[0] = k; shadowVals[1] = -k * skew; shadowVals[2] = sx - fr.ax * k + fr.ay * k * skew
            shadowVals[3] = 0f; shadowVals[4] = -k * flat; shadowVals[5] = sy + fr.ay * k * flat
            shadowM.setValues(shadowVals)
            shadowPaint.alpha = (alpha * strength * 255f).toInt().coerceIn(0, 255)
            c.drawBitmap(fr.bmp, shadowM, shadowPaint)
        }
        // temas lekesi: ayakların hemen altı (ortam tıkanması), ince ve koyu
        fill.color = alphaC(0xFF000000.toInt(), 0.34f * strength)
        c.drawOval(sx - contact / 2f, sy - contact * 0.12f, sx + contact / 2f, sy + contact * 0.12f, fill)
    }

    private val spShadowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(0xFF000000.toInt(), PorterDuff.Mode.SRC_IN) }
    private val spShadowM = android.graphics.Matrix()
    private val spShadowVals = FloatArray(9).also { it[8] = 1f }

    /**
     * Sprite siluet gölgesi: çizim kalıbı aynen zemine yatırılıp sağ alta savrulur (ışık sol üstte); [draw] sprite'ı verilen boyayla çizer.
     * [contact]: ayak altındaki küçük koyu temas lekesinin genişliği (px). "Gölgeler" kapalıysa hiçbir şey çizilmez; efekt
     * kalitesi düşükken tek geçiş.
     */
    private fun castShadow(c: Canvas, sx: Float, sy: Float, contact: Float, strength: Float = 1f, draw: (Paint) -> Unit) {
        val st = Settings.current
        if (!st.shadows) return
        val passes = if (st.fx >= 2) 2 else 1
        for (i in 0 until passes) {
            val flat = if (i == 0) 0.30f else 0.38f; val skew = if (i == 0) 0.55f else 0.70f
            val alpha = (if (passes == 1) 0.40f else if (i == 0) 0.30f else 0.16f) * strength
            spShadowVals[0] = 1f; spShadowVals[1] = -skew; spShadowVals[2] = 0f
            spShadowVals[3] = 0f; spShadowVals[4] = -flat; spShadowVals[5] = 0f
            spShadowM.setValues(spShadowVals)
            c.save(); c.translate(sx, sy); c.concat(spShadowM); c.translate(-sx, -sy)
            spShadowPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            draw(spShadowPaint)
            c.restore()
        }
        fill.color = alphaC(0xFF000000.toInt(), 0.30f * strength)
        c.drawOval(sx - contact / 2f, sy - contact * 0.12f, sx + contact / 2f, sy + contact * 0.12f, fill)
    }

    /** Siluet çıkarılamayan (gökteki, dönen, aracın içi) şeyler için yalnız yer lekesi: ayarla birlikte kapanır */
    private fun groundBlob(c: Canvas, sx: Float, sy: Float, rx: Float, ry: Float, a: Float) {
        if (!Settings.current.shadows) return
        fill.color = alphaC(0xFF000000.toInt(), a)
        c.drawOval(sx - rx, sy - ry, sx + rx, sy + ry, fill)
    }

    /** Ayak tozu zamanlayıcıları (aktör başına) */
    private val footT = HashMap<String, Float>()

    /**
     * 3B kahraman: kare arka planda (Model3D) çizilir ve önbellekten gelir; ilk kez istenen kare hazır olana dek aktörün
     * son karesi gösterilir. Kare hiç yoksa false (piksel karakter çizilir).
     */
    private fun drawHero3D(c: Canvas, ch: Int, x: Float, y: Float, sx: Float, sy: Float, dir: Int, animI: Int, t: Float, flash: Float,
                           who: Float, wIdx: Int): Boolean {
        if (!Model3D.ready) return false
        val id = when {
            who == -1f -> "me"
            who >= 0f -> "r" + (Online.lastIds.getOrNull(who.toInt()) ?: who.toInt().toString())
            else -> return false
        }
        // bakış: çekirdeğin 8 yönüne en kısa yoldan yumuşak dönüş; saldırı, ateş ve takla anında döner
        val target = dir * 45f
        val prev = yaw3d[id]
        val yaw = if (prev == null || animI == 3 || animI == 4 || animI == 13 || animI == 40) target else {
            val d = ((target - prev) % 360f + 540f) % 360f - 180f
            val step = 900f * frameDt
            prev + d.coerceIn(-step, step)
        }
        if (yaw3d.size > 80) yaw3d.clear()
        yaw3d[id] = (yaw % 360f + 360f) % 360f
        // uzak oyuncu araçta: aracıyla birlikte (sürüş pozu yerine motosiklet / araba + sürücü)
        if (animI == 12 && who >= 0f) {
            val vk = Online.lastIds.getOrNull(who.toInt())?.let { Online.position(it) }?.getOrNull(9)?.toInt() ?: -1
            if (vk == 1 || vk == 2) {
                if (riderVel.size > 60) riderVel.clear()
                val rv = riderVel.getOrPut(id) { floatArrayOf(x, y, 0f) }
                val sp = hypot(x - rv[0], y - rv[1]) / frameDt.coerceAtLeast(1e-3f)
                rv[0] = x; rv[1] = y; rv[2] += (sp.coerceAtMost(1500f) - rv[2]) * min(1f, frameDt * 5f)
                if (drawVehicle3D(c, vk, "r$id", x, y, sx, sy, yaw, rv[2], ch, 1f, false, dir)) return true
            }
        }
        val pxPerM = 62.5f * scale
        val weapon = weaponModels.getOrElse(wIdx) { "" }
        // 3B durum bayrakları (yalnız yerel oyuncu): roketatar boş (ön başlık namluda yok), demir kademe alet
        var flags = 0
        if (who == -1f) {
            if (weapon == "rpg" && hud[Native.MAG] < 0.5f) flags = flags or 1
            if ((animI == 17 && hud[Native.TOOL_AXE] >= 2f) || (animI == 18 && hud[Native.TOOL_PICK] >= 2f)) flags = flags or 2
        }
        val fr = Model3D.actor(id, Model3D.CHARS[ch], weapon, animI, t, yaw, pxPerM, Settings.current.model3d, flags,
            if (who == -1f) Skins.wings else "") ?: return false
        val k = pxPerM / fr.pxPerM
        val skin = when { who == -1f -> Skins.char; who >= 0f -> Online.lastSkins.getOrNull(who.toInt()) ?: ""; else -> "" }
        val paint = when {
            flash == 1f -> flashPaint
            flash == 2f -> { tintPaint.colorFilter = PorterDuffColorFilter(0x66FFD34A, PorterDuff.Mode.SRC_ATOP); tintPaint }
            skin.isNotEmpty() -> skinPaint(skin, false)
            else -> bmpPaint
        }
        if (skin.isNotEmpty() && Settings.current.particles) {
            val tc = Skins.trail(skin)
            if (tc != 0 && Math.random() < 0.18) particles += Particle(x + (Math.random().toFloat() - 0.5f) * 50f, y, 20f + Math.random().toFloat() * 90f,
                (Math.random().toFloat() - 0.5f) * 30f, 0f, 35f + Math.random().toFloat() * 30f, 0.9f, 0.9f, tc, 3.5f, 3)
        }
        drawShadow3d(c, fr, sx, sy, k, pxPerM * 0.40f)
        // koşarken / yürürken ayak tozu: adımda iki küçük bulut, yüzeyin renginde (asfaltta çok ince)
        if (Settings.current.particles && (animI == 1 || animI == 2 || animI == 6 || animI == 8)) {
            if (footT.size > 60) footT.clear()
            val left = (footT[id] ?: 0f) - frameDt
            if (left <= 0f) {
                val run = animI == 2
                footT[id] = if (run) 0.14f else 0.30f
                val dv = Math.toRadians(dir * 45.0)
                val fdx = sin(dv).toFloat(); val fdy = cos(dv).toFloat()
                repeat(if (run) 2 else 1) {
                    val sd = Math.random().toFloat() - 0.5f
                    particles += Particle(x + sd * 12f - fdx * 8f, y + 4f - fdy * 4f, 1f, -fdx * (16f + Math.random().toFloat() * 26f) + sd * 24f, -fdy * 8f,
                        12f + Math.random().toFloat() * 14f, 0.5f + Math.random().toFloat() * 0.25f, 0.75f, dustColor(surface()), if (run) 4.4f else 2.8f, 1)
                }
            } else footT[id] = left
        }
        if (who == -1f) {
            // takla: her 35 ms'de bir art görüntü bırak, eskileri sönerek çiz
            if (animI == 13 && time - ghostT > 0.035f) { ghosts += Ghost(x, y, "", false, 0.32f, fr); ghostT = time }
            for (g in ghosts) {
                val gf = g.fr ?: continue
                ghostPaint.alpha = (g.life / 0.32f * 150).toInt().coerceIn(0, 255)
                drawFrame3d(c, gf, wx(g.x), wy(g.y), 62.5f * scale / gf.pxPerM, ghostPaint)
            }
        }
        drawFrame3d(c, fr, sx, sy, k, paint)
        // efekt noktaları (kare koordinatı → ekran dünyası)
        val p = fr.pts
        fun ex(i: Int) = camX + (sx + (p[i] - fr.ax) * k - sw / 2f) / scale
        fun ey(i: Int) = camY + (sy + (p[i + 1] - fr.ay) * k - sh / 2f) / scale
        val wk = p[12].toInt()
        if (wk != 0) {
            fx3d.actor(id, wk, ex(0), ey(0), ex(2), ey(2), p[10], y, animI == 4 || animI == 17 || animI == 18, frameDt, 62.5f)
        }
        if (who == -1f) { me3dT = time; me3dWeapon = wk; me3dMx = ex(4); me3dMy = ey(4); me3dRx = ex(13); me3dRy = ey(13); me3dStage = p[15].toInt() }
        return true
    }

    private fun drawFrame3d(c: Canvas, fr: Model3D.Frame, sx: Float, sy: Float, k: Float, paint: Paint) {
        val l = sx - fr.ax * k; val tp = sy - fr.ay * k
        if (abs(k - 1f) < 0.01f) {
            // ölçek 1: tam piksele oturt (bulanıklık yok)
            c.drawBitmap(fr.bmp, kotlin.math.round(l), kotlin.math.round(tp), paint)
        } else {
            dst3d.set(l, tp, l + fr.bmp.width * k, tp + fr.bmp.height * k)
            c.drawBitmap(fr.bmp, null, dst3d, paint)
        }
    }

    // ------------------------------------------------------------------ V6 NPC'ler
    // rehber (tür 6) şimdilik asker görseliyle
    // V11 komutan (tür 8): kendi görseli gelene dek asker görseliyle ("npc/commander" atlasa girince o kullanılır)
    private val npc6Names = arrayOf("soldier", "merchant", "quest", "doctor", "mechanic", "trainer", "soldier", "wizard", "commander")
    private val npc6Plate = arrayOf(null, "Tüccar Marco" to "Tüccar", "Yaşlı Silas" to "Görevci", "Doktor Elena" to "Doktor",
        "Tamirci Viktor" to "Tamirci", "Eğitmen Jack" to "Köpek Eğitmeni", "Rehber" to "Öğretici", "Büyücü Aldric" to "Işınlanma",
        "Komutan Diana" to "Komutan")
    /** Görev hedefleri (ekran kenarı oku) ve etkin rehber adımı (öğretici okları) */
    private data class QuestTarget(val x: Float, val y: Float, val label: String, val guide: Boolean)
    private val questTargets = ArrayList<QuestTarget>()
    @Volatile var guideStep = ""
        private set
    /** NPC başı görev işareti: ch → "!" (görev var) / "?" (teslim) */
    private val questMarks = HashMap<Int, String>()
    private var questPollT = 0f

    // ---- kişisel üs sandıkları (çanta işçisi): Rust "chests" görünümünden yarım saniyede bir
    private class ChestMark(val x: Float, val y: Float, val id: String, val used: Int, val cap: Int)
    private val chestMarks = ArrayList<ChestMark>()
    private var chestPollT = 0f
    @Volatile private var nearChest = -1

    private fun pollChests() {
        val j = GameSession.ui("chests")
        val l = j.optJSONArray("list")
        val out = ArrayList<ChestMark>()
        for (i in 0 until (l?.length() ?: 0)) {
            val o = l!!.getJSONObject(i)
            out += ChestMark(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.optString("id"), o.optInt("used"), o.optInt("cap"))
        }
        synchronized(chestMarks) { chestMarks.clear(); chestMarks.addAll(out) }
        nearChest = j.optInt("near", -1)
    }

    /** Sandıklar: sandık görseli (ahşap / askerî), yakındaysa doluluk plakası. */
    private fun drawChests(c: Canvas) {
        synchronized(chestMarks) {
            for ((i, m) in chestMarks.withIndex()) {
                val sx = wx(m.x); val sy = wy(m.y)
                if (sx < -200 || sy < -200 || sx > sw + 200 || sy > sh + 300) continue
                // V8 sandık görselleri (ahşap / demir / askerî); yanındayken kapağı açık
                val base = when (m.id) { "chest_wood" -> "s/chest_wood"; "chest_iron" -> "s/chest_iron"; else -> "s/chest_mil" }
                val sp = atlas[if (i == nearChest) base + "_open" else base] ?: atlas[base]
                    ?: atlas[if (m.id == "chest_wood") "s/crate" else "s/crate_mil"] ?: itemSprites.getOrNull(itemIds.indexOf(m.id))
                if (sp != null) {
                    castShadow(c, sx, sy, 56 * scale) { p -> atlas.draw(c, sp, sx, sy, scale, p) }
                    atlas.draw(c, sp, sx, sy, scale, pixelPaint)
                }
                if (i == nearChest) drawText(c, "Sandık ${m.used}/${m.cap}", sx - 34 * density, sy - 70 * scale, 12 * density, 0xFFFFD34A.toInt())
            }
        }
    }
    /** dokunarak menü açmak için uzak oyuncuların ekran kutuları */
    private class PlayerBox(val l: Float, val t: Float, val r: Float, val b: Float, val uid: String, val name: String)
    private val playerBoxes = ArrayList<PlayerBox>()
    /** dokunarak üretim açmak için tezgâhların ekran kutuları: sol, üst, sağ, alt, oyuncuya uzaklık */
    private val benchBoxes = ArrayList<FloatArray>()
    /** dokunarak konuşmak için NPC'lerin ekran kutuları: ch, sol, üst, sağ, alt */
    private val npcBoxes = ArrayList<FloatArray>()
    private val npc6Bmp = HashMap<String, android.graphics.Bitmap?>()
    private val meshW = 4
    private val meshH = 10
    private val meshVerts = FloatArray((meshW + 1) * (meshH + 1) * 2)
    /** Yakındaki konuşulabilir NPC (karakter kodu 5-9), her karede çizim sırasında güncellenir. */
    private var nearNpc = -1
    private var nearNpcD = Float.MAX_VALUE

    /**
     * Piksel NPC: tek görsel, çalışma anında ızgara (mesh) ile canlandırılır — göğüs nefesle genişler ve kalkar,
     * üst gövde hafifçe salınır; ek animasyon karesi yok (APK'ya yük yok). Görseller sağa bakar; oyuncu soldaysa aynalanır.
     */
    private fun drawNpc6(c: Canvas, ch: Int, x: Float, y: Float, sx: Float, sy: Float, dir: Int, t: Float): Boolean {
        val name = npc6Names.getOrNull(ch - 4) ?: return false
        // büyücü görseli yoksa görevci görseli mor tonla
        val key = when {
            name == "wizard" && atlas["npc/wizard"] == null -> "npc/quest"
            name == "commander" && atlas["npc/commander"] == null -> "npc/soldier"
            else -> "npc/$name"
        }
        // V13 Gezgin Tüccar Marco: 5 yönlü bekleme animasyonu (batı yanı doğunun aynası)
        val marco = if (ch == 5) {
            val f = frameOf(atlas.anims("n/marco", atlas.zombieAnims), "idle", t + ch * 0.37f)
            val (d5, fl) = if (dir >= 5) (8 - dir) to true else dir to false
            atlas["n/marco/idle_${d5}_$f"]?.let { it to fl }
        } else null
        val w: Float; val top: Float
        val px0 = hud[Native.PX]; val py0 = hud[Native.PY]
        val near = ch >= 5 && kotlin.math.hypot(px0 - x, py0 - y) < 170f
        val f3 = drawNpc3D(c, ch, x, y, sx, sy, dir, t, near)
        if (f3 != null) {
            w = f3[0]; top = f3[1]
        } else if (marco != null) {
            val (msp, fl) = marco
            val k = msp.k * scale
            w = msp.w * k; top = sy - msp.ay * k
            castShadow(c, sx, sy, w * 0.6f) { p -> atlas.draw(c, msp, sx, sy, scale, p, fl) }
            atlas.draw(c, msp, sx, sy, scale, pixelPaint, fl)
        } else {
            val sp = atlas[key] ?: return false
            val bmp = npc6Bmp.getOrPut(key) { atlas.extract(key) } ?: return false
            val k = sp.k * scale
            w = sp.w * k; val h = sp.h * k
            val left = sx - sp.ax * k; top = sy - sp.ay * k
            val flip = dir in 5..7
            val ph = ch * 1.7f
            val breath = sin(t * 2.1f + ph)
            val sway = sin(t * 1.05f + ph * 0.6f)
            var i = 0
            for (j in 0..meshH) {
                val v = j.toFloat() / meshH
                val up = ((0.62f - v) / 0.62f).coerceIn(0f, 1f)
                for (q in 0..meshW) {
                    val u = q.toFloat() / meshW
                    var vx = left + u * w + (u - 0.5f) * w * 0.014f * breath * up + sway * w * 0.012f * (1 - v) * (1 - v)
                    val vy = top + v * h - h * 0.006f * (breath + 1f) * up
                    if (flip) vx = 2 * sx - vx
                    meshVerts[i++] = vx; meshVerts[i++] = vy
                }
            }
            castShadow(c, sx, sy, w * 0.6f) { p -> c.drawBitmapMesh(bmp, meshW, meshH, meshVerts, 0, null, 0, p) }
            c.drawBitmapMesh(bmp, meshW, meshH, meshVerts, 0, null, 0, if (ch == 11 && key == "npc/quest") wizardPaint else pixelPaint)
        }
        // büyücü: çevresinde yükselen mavi-mor kıvılcımlar
        if (ch == 11 && Settings.current.particles && Math.random() < 0.25)
            particles += Particle(x + ((Math.random() - 0.5) * 70).toFloat(), y - 20f, 10f, 0f, 0f, 90f, 1.1f, 1.1f,
                if (Math.random() < 0.5) 0xFF8AD8FF.toInt() else 0xFFC890FF.toInt(), 3.5f, 3)
        val px = hud[Native.PX]; val py = hud[Native.PY]
        val d = kotlin.math.hypot(px - x, py - y)
        if (ch >= 5 && d < 170f && d < nearNpcD) { nearNpc = ch; nearNpcD = d }
        if (ch >= 5) npcBoxes += floatArrayOf(ch.toFloat(), sx - w * 0.4f, top, sx + w * 0.4f, sy, d)
        npc6Plate.getOrNull(ch - 4)?.let { (n, sub) -> plates += Plate(sx, top - 4 * density, n, 0xFFFFD34A.toInt(), -1f, false, sub) }
        if (!clean) questMarks[ch]?.let { m ->
            // başın üstünde zıplayan görev işareti
            val by = top - 34 * density + sin(time * 4f) * 4 * density
            fill.color = 0xFF15140F.toInt(); c.drawRect(sx - 11 * density, by - 13 * density, sx + 11 * density, by + 11 * density, fill)
            fill.color = if (m == "?") 0xFFFFD34A.toInt() else 0xFFFF9A2A.toInt()
            c.drawRect(sx - 9 * density, by - 11 * density, sx + 9 * density, by + 9 * density, fill)
            drawText(c, m, sx, by + 6 * density, 17 * density, 0xFF15140F.toInt(), true, fontBold)
        }
        return true
    }

    /**
     * 3B NPC: oyuncu yaklaşınca ona döner ve konuşma animasyonuna geçer (baş sallama, gövde jesti), uzakta nefes alır.
     * Döndürür: [genişlik, üst] (isim plakası ve dokunma kutusu için) ya da model yoksa null (piksel NPC çizilir).
     */
    private fun drawNpc3D(c: Canvas, ch: Int, x: Float, y: Float, sx: Float, sy: Float, dir: Int, t: Float, near: Boolean): FloatArray? {
        if (!Model3D.ready || Settings.current.model3d < 0) return null
        val model = Model3D.NPCS.getOrNull(ch - 4) ?: return null
        val id = "n$ch@${x.toInt()},${y.toInt()}"
        val target = if (near) Math.toDegrees(kotlin.math.atan2((hud[Native.PX] - x).toDouble(), (hud[Native.PY] - y).toDouble())).toFloat() else dir * 45f
        val prev = yaw3d[id]
        val yaw = if (prev == null) target else {
            val d = ((target - prev) % 360f + 540f) % 360f - 180f
            prev + d.coerceIn(-240f * frameDt, 240f * frameDt)
        }
        if (yaw3d.size > 80) yaw3d.clear()
        yaw3d[id] = (yaw % 360f + 360f) % 360f
        val pxPerM = 62.5f * scale
        val anim = if (near) Model3D.A_TALK else 0
        val fr = Model3D.actor(id, model, "", anim, t + ch * 0.9f, yaw, pxPerM, Settings.current.model3d) ?: return null
        val k = pxPerM / fr.pxPerM
        drawShadow3d(c, fr, sx, sy, k, pxPerM * 0.40f)
        drawFrame3d(c, fr, sx, sy, k, bmpPaint)
        return floatArrayOf(fr.bmp.width * k * 0.8f, sy - fr.ay * k)
    }

    // ------------------------------------------------------------------ V8 devlet üsleri
    private val wizardPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false
        colorFilter = android.graphics.LightingColorFilter(0xFFB8A8FF.toInt(), 0x00180A30) }
    private val friendPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false
        colorFilter = android.graphics.LightingColorFilter(0xFFC8DCFF.toInt(), 0x00081428) }
    private val deadPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    @Volatile private var baseNear: JSONObject? = null
    private var baseNearT = 0f

    /** V10 asker karelerinde namlu ucu (sağa bakan görselde genişlik/yükseklik oranı) */
    private val soldierTip = mapOf(
        "npc/soldier_aim_s" to (0.99f to 0.2f), "npc/soldier_fire" to (0.8f to 0.23f), "npc/soldier_crouch" to (0.99f to 0.27f),
        "npc/sniper_aim_s" to (0.99f to 0.26f), "npc/sniper_kneel" to (0.99f to 0.24f), "npc/sniper_recoil" to (0.8f to 0.27f))

    /**
     * Devlet üssü askeri: asker görseli ızgarayla nefes alır, yürürken sallanır, ateşte geri teper; elde çizimle tüfek
     * (keskin nişancıda dürbünlü uzun namlu) nişan açısına döner. Keskin nişancı nişan alırken kırmızı lazer çizer.
     * Kayıt: [10, tür(+2 kule), x, y, açı, durum, t, efekt(1 vuruş, 2 ölümsüz), can, seviye, düşman+lazerMesafe*2, ölüm/nişan].
     */
    private fun drawSoldier(c: Canvas, o: Int) {
        val kf = drawBuf[o + 1].toInt(); val kind = kf % 4; val sniper = kind == 1; val tower = kf >= 4
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]; val face = drawBuf[o + 4]
        val state = drawBuf[o + 5].toInt(); val t = drawBuf[o + 6]; val fxf = drawBuf[o + 7].toInt()
        val hp = drawBuf[o + 8]; val lv = drawBuf[o + 9].toInt()
        val hv = drawBuf[o + 10].toInt(); val hostile = hv and 1 == 1; val aimD = (hv shr 1).toFloat()
        val extra = drawBuf[o + 11]
        val sx = wx(x); var sy = wy(y)
        if (tower) sy -= 235f * scale
        if (sx < -300 || sx > sw + 300 || sy < -300 || sy > sh + 400) return
        val dirX0 = cos(face); val dirY0 = sin(face)
        if ((kind == 2 || kind == 3) && drawBot(c, kind, x, y, sx, sy, face, state, t, fxf, hp, lv, hostile, extra)) return
        if ((kind == 0 || kind == 1) && drawSoldier3D(c, sniper, tower, x, y, sx, sy, face, state, t, fxf, hp, lv, hostile, aimD, extra)) return
        val who = if (sniper) "sniper" else "soldier"
        // V10 yan görünüm pozları (sağa bakar): devriye, nişan, ateş, kulede çömelme, şarjör, dürbün, vurulma, düşüş, ölü.
        // Yukarı bakarken V8 sırt, kameraya doğru nişanda V8 ön karesi.
        val key = when {
            state == 4 -> when {
                extra < 0.2f -> "npc/${who}_hit"
                !sniper && extra < 0.5f -> "npc/soldier_falling"
                else -> "npc/${who}_dead"
            }
            fxf == 1 && state != 3 -> "npc/${who}_hit"
            dirY0 < -0.55f && state != 0 -> "npc/${who}_back"
            dirY0 > 0.75f && state >= 2 -> "npc/${who}_aim"
            state == 3 -> if (sniper) "npc/sniper_recoil" else if (tower) "npc/soldier_crouch" else "npc/soldier_fire"
            state == 2 -> if (sniper) (if (tower) "npc/sniper_kneel" else "npc/sniper_aim_s") else if (tower) "npc/soldier_crouch" else "npc/soldier_aim_s"
            state == 1 -> if (sniper) "npc/sniper_idle" else if ((t * 4f).toInt() % 2 == 0) "npc/soldier_walk1" else "npc/soldier_walk2"
            sniper -> if ((t / 5f).toInt() % 3 == 2) "npc/sniper_bolt" else "npc/sniper_scan"
            else -> if ((t / 6f).toInt() % 4 == 3) "npc/soldier_reload" else "npc/soldier_walk1"
        }.let { if (atlas[it] != null) it else if (atlas["npc/${who}_idle"] != null) "npc/${who}_idle" else "npc/soldier" }
        val v8 = key != "npc/soldier"
        val tip = soldierTip[key]
        val sp = atlas[key] ?: run { placeholder(c, sx, sy, if (hostile) 0xFF8A3A2A.toInt() else 0xFF3A6AAA.toInt()); return }
        val bmp = npc6Bmp.getOrPut(key) { atlas.extract(key) } ?: return
        val k = sp.k * scale
        val w = sp.w * k; val h = sp.h * k
        val dirX = cos(face); val dirY = sin(face)
        // nişan karesi sağa bakar; arka karede tüfek sola dönük
        val flip = if (key.endsWith("_back")) dirX > 0f else dirX < 0f
        val dead = state == 4
        c.save()
        var ox = 0f; var oy = 0f
        if (state == 3) ox -= dirX * 5f * scale
        if (state == 1) oy -= kotlin.math.abs(sin(t * 9f)) * 4f * scale
        if (dead) {
            val f = (extra / 0.45f).coerceIn(0f, 1f)
            if (key.endsWith("_idle") || key == "npc/soldier") c.rotate((if (flip) 1f else -1f) * 84f * f, sx, sy)
            deadPaint.alpha = (255 * (1f - ((extra - 3f) / 3f).coerceIn(0f, 1f))).toInt()
        }
        val left = sx + ox - sp.ax * k; val top = sy + oy - sp.ay * k
        val breath = sin(t * 2.3f + x * 0.01f)
        var i = 0
        for (j in 0..meshH) {
            val v = j.toFloat() / meshH
            val up = ((0.62f - v) / 0.62f).coerceIn(0f, 1f)
            for (q in 0..meshW) {
                val u = q.toFloat() / meshW
                var vx = left + u * w + (u - 0.5f) * w * 0.012f * breath * up
                val vy = top + v * h - h * 0.005f * (breath + 1f) * up
                if (flip) vx = 2 * (sx + ox) - vx
                meshVerts[i++] = vx; meshVerts[i++] = vy
            }
        }
        val paint = when { dead -> deadPaint; fxf == 1 -> flashPaint; !hostile -> friendPaint; else -> pixelPaint }
        if (!dead) castShadow(c, sx + ox, sy + oy, w * 0.55f) { p -> c.drawBitmapMesh(bmp, meshW, meshH, meshVerts, 0, null, 0, p) }
        c.drawBitmapMesh(bmp, meshW, meshH, meshVerts, 0, null, 0, paint)
        // tüfek: omuzdan nişan yönüne (V8 karelerde tüfek çizimin içinde; yalnız namlu ucu hesaplanır)
        if (!dead) {
            val shx = sx + ox + (if (flip) -1f else 1f) * w * 0.08f
            val shy = top + h * 0.45f
            val len = (if (sniper) 66f else 46f) * scale
            // V10 karelerde namlu ucu görselden (sağa bakan karede oran); aynalanınca simetriği
            val ex = if (tip != null) { val tx = left + tip.first * w; if (flip) 2 * (sx + ox) - tx else tx } else shx + dirX * len
            val ey = if (tip != null) top + tip.second * h else shy + dirY * len * 0.55f
            if (!v8) {
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.color = 0xFF15140F.toInt(); stroke.strokeWidth = 6.5f * scale; c.drawLine(shx, shy, ex, ey, stroke)
            stroke.color = if (sniper) 0xFF5A5040.toInt() else 0xFF3A3C3E.toInt(); stroke.strokeWidth = 3.5f * scale; c.drawLine(shx, shy, ex, ey, stroke)
            if (sniper) {
                val mx = shx + dirX * len * 0.45f; val my = shy + dirY * len * 0.25f
                fill.color = 0xFF101010.toInt(); c.drawRect(mx - 6 * scale, my - 6 * scale, mx + 6 * scale, my - 1 * scale, fill)
            }
            stroke.strokeCap = Paint.Cap.BUTT
            }
            if (state == 3 && key != "npc/soldier_fire") {
                fill.color = 0xFFFFE08A.toInt(); c.drawCircle(ex + dirX * 6 * scale, ey + dirY * 3 * scale, 6f * scale, fill)
                fill.color = 0x80FFB040.toInt(); c.drawCircle(ex + dirX * 6 * scale, ey + dirY * 3 * scale, 12f * scale, fill)
            }
            // keskin nişancı lazeri: nişan doldukça kalınlaşır, uçta yanıp sönen nokta
            if (sniper && state == 2 && aimD > 0f) {
                val lx = ex + dirX * aimD * scale; val ly = ey + dirY * aimD * scale
                stroke.color = alphaC(0xFFFF2020.toInt(), 0.35f + 0.5f * extra); stroke.strokeWidth = (1f + 2f * extra) * density
                c.drawLine(ex, ey, lx, ly, stroke)
                fill.color = alphaC(0xFFFF3030.toInt(), 0.6f + 0.4f * sin(time * 25f)); c.drawCircle(lx, ly, (3f + 4f * extra) * density, fill)
            }
        }
        c.restore()
        if (!dead) {
            if (fxf == 2) drawFxSprite(c, "shield", x, y - 62 - (if (tower) 235f else 0f), 175f, time)
            val label = "Sv $lv " + if (sniper) "Keskin Nişancı" else "M4 Askeri"
            plates += Plate(sx, top - 4 * density, label, if (hostile) 0xFFFF7A6A.toInt() else 0xFF8AD8FF.toInt(), hp, false, null)
        }
    }

    /**
     * 3B devlet askeri / keskin nişancı: devriyede yürür, nişanda dizini kırıp öne eğilir, ateşte geri teper, ölünce düşer.
     * Namlu alevi ve keskin nişancı lazeri göğüs hizasından nişan yönüne çizilir. Model yoksa false (piksel asker).
     */
    private fun drawSoldier3D(c: Canvas, sniper: Boolean, tower: Boolean, x: Float, y: Float, sx: Float, sy: Float, face: Float, state: Int,
                              t: Float, fxf: Int, hp: Float, lv: Int, hostile: Boolean, aimD: Float, extra: Float): Boolean {
        if (!Model3D.ready) return false
        val model = if (sniper) "npc_sniper" else "npc_soldier"
        val dirX = cos(face); val dirY = sin(face)
        val yaw = Math.toDegrees(kotlin.math.atan2(dirX.toDouble(), dirY.toDouble())).toFloat()
        val dead = state == 4
        val (anim, at) = when {
            dead -> 11 to extra.coerceAtMost(0.9f)
            fxf == 1 && state < 2 -> 10 to 0.08f
            state == 3 -> Model3D.A_SHOOT to t
            state == 2 -> Model3D.A_AIM to 0f
            state == 1 -> 1 to t
            else -> 0 to t
        }
        val pxPerM = 62.5f * scale
        val id = "g${x.toInt()},${y.toInt()}"
        val fr = Model3D.actor(id, model, "", anim, at, yaw, pxPerM, Settings.current.model3d) ?: return false
        val k = pxPerM / fr.pxPerM
        val paint = when {
            dead -> { deadPaint.alpha = (255 * (1f - ((extra - 3f) / 3f).coerceIn(0f, 1f))).toInt(); deadPaint }
            fxf == 1 -> flashPaint
            else -> bmpPaint
        }
        if (!dead) drawShadow3d(c, fr, sx, sy, k, pxPerM * 0.40f)
        drawFrame3d(c, fr, sx, sy, k, paint)
        val top = sy - fr.ay * k
        if (!dead) {
            // göğüs hizası (baş noktasının 0.35 m altı), nişan yönünde 0.55 m ileri: namlu ucu
            val hx = sx + (fr.pts[8] - fr.ax) * k; val hy = sy + (fr.pts[9] - fr.ay) * k
            val ex = hx + dirX * 0.55f * pxPerM; val ey = hy + 0.35f * pxPerM + dirY * 0.30f * pxPerM
            if (state == 3 && (t % 0.4f) < 0.09f) {
                fill.color = 0xFFFFE08A.toInt(); c.drawCircle(ex, ey, 6f * scale, fill)
                fill.color = 0x80FFB040.toInt(); c.drawCircle(ex, ey, 12f * scale, fill)
            }
            if (sniper && state == 2 && aimD > 0f) {
                val lx = ex + dirX * aimD * scale; val ly = ey + dirY * aimD * scale
                stroke.color = alphaC(0xFFFF2020.toInt(), 0.35f + 0.5f * extra); stroke.strokeWidth = (1f + 2f * extra) * density
                c.drawLine(ex, ey, lx, ly, stroke)
                fill.color = alphaC(0xFFFF3030.toInt(), 0.6f + 0.4f * sin(time * 25f)); c.drawCircle(lx, ly, (3f + 4f * extra) * density, fill)
            }
            if (fxf == 2) drawFxSprite(c, "shield", x, y - 62 - (if (tower) 235f else 0f), 175f, time)
            val label = "Sv $lv " + if (sniper) "Keskin Nişancı" else "M4 Askeri"
            plates += Plate(sx, top - 4 * density, label, if (hostile) 0xFFFF7A6A.toInt() else 0xFF8AD8FF.toInt(), hp, false, null)
        }
        return true
    }

    /**
     * V13 Cephe botları (kalkanlı / alev makineli asker): 5 yönlü animasyon (batı yanı doğunun aynası), ölüm güney/doğu.
     * Durum: 0 bekleme, 1 yürüme, 2-3 nişan/ateş, 4 ölü (extra = ölüm süresi).
     */
    private fun drawBot(c: Canvas, kind: Int, x: Float, y: Float, sx: Float, sy: Float, face: Float, state: Int, t: Float, fxf: Int,
                        hp: Float, lv: Int, hostile: Boolean, extra: Float): Boolean {
        val name = if (kind == 2) "shieldbot" else "flamebot"
        if (!atlas.has("z/$name/idle_0_0")) return false
        val anim = when (state) { 4 -> "death"; 2, 3 -> "attack"; 1 -> "walk"; else -> "idle" }
        val dir = ((Math.round((90.0 - Math.toDegrees(face.toDouble())) / 45.0).toInt() % 8) + 8) % 8
        var d5 = dir; var flip = false
        if (anim == "death") { d5 = when (dir) { 2, 3, 5, 6 -> 2; else -> 0 }; flip = dir == 5 || dir == 6 }
        else if (dir >= 5) { d5 = 8 - dir; flip = true }
        val f = frameOf(atlas.anims("z/$name", atlas.zombieAnims), anim, if (state == 4) extra else t)
        val sp = atlas[spriteKey("z/$name", anim, d5, f)] ?: atlas[spriteKey("z/$name", "idle", d5, 0)] ?: return false
        val dead = state == 4
        if (!dead) castShadow(c, sx, sy, sp.w * sp.k * scale * 0.55f) { p -> atlas.draw(c, sp, sx, sy, scale, p, flip) }
        val paint = when {
            dead -> { deadPaint.alpha = (255 * (1f - ((extra - 3f) / 3f).coerceIn(0f, 1f))).toInt(); deadPaint }
            fxf == 1 -> flashPaint
            !hostile -> friendPaint
            else -> pixelPaint
        }
        atlas.draw(c, sp, sx, sy, scale, paint, flip)
        if (!dead) {
            if (fxf == 2) drawFxSprite(c, "shield", x, y - 62, 175f, time)
            val top = sy - sp.ay * sp.k * scale
            val label = "Sv $lv " + if (kind == 2) "Kalkanlı Asker" else "Alev Makineli Asker"
            plates += Plate(sx, top - 4 * density, label, if (hostile) 0xFFFF7A6A.toInt() else 0xFF8AD8FF.toInt(), hp, false, null)
            // alev makinesinin pilot alevi
            if (kind == 3 && Settings.current.particles && Math.random() < 0.3)
                particles += Particle(x + cos(face) * 40f, y, 60f, 0f, 0f, 30f, 0.4f, 0.4f, 0xFFFFA040.toInt(), 3f, 4)
        }
        return true
    }

    private var heliDustT = 0f

    /** Takviye helikopteri: zeminde gölge, gövde + kuyruk + kızaklar, dönen pervane; alçalınca toz kaldırır. */
    private fun drawHeli(c: Canvas, o: Int) {
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]; val alt = drawBuf[o + 4]; val ang = drawBuf[o + 5]; val t = drawBuf[o + 6]
        val gx = wx(x); val gy = wy(y)
        if (gx < -800 || gx > sw + 800 || gy < -900 || gy > sh + 600) return
        val s = scale
        val sh2 = (1f - alt / 700f).coerceIn(0.35f, 1f)
        groundBlob(c, gx, gy, 90 * s * sh2, 26 * s * sh2, 0.35f * sh2)
        val hy = gy - alt * s
        val body = atlas["x/heli"]
        if (body != null) {
            // V8 gövde görseli (burnu sola); sağa giderken aynalanır, hafif yatış
            val right = cos(ang) > 0f
            c.save(); c.rotate((if (right) 1f else -1f) * 4f * sin(t * 1.3f), gx, hy)
            atlas.draw(c, body, gx, hy, s, pixelPaint, right)
            c.restore()
        } else {
        c.save(); c.translate(gx, hy); c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        val r = RectF()
        fill.color = 0xFF1A1C14.toInt(); r.set(-142 * s, -9 * s, -50 * s, 9 * s); c.drawRoundRect(r, 6 * s, 6 * s, fill)
        fill.color = 0xFF3E4628.toInt(); r.set(-140 * s, -6 * s, -52 * s, 6 * s); c.drawRoundRect(r, 4 * s, 4 * s, fill)
        fill.color = 0xFF2A301C.toInt(); r.set(-148 * s, -18 * s, -132 * s, 18 * s); c.drawRoundRect(r, 3 * s, 3 * s, fill)
        fill.color = 0xFF15160F.toInt(); r.set(-70 * s, -30 * s, 58 * s, 30 * s); c.drawRoundRect(r, 26 * s, 26 * s, fill)
        fill.color = 0xFF4A5530.toInt(); r.set(-66 * s, -26 * s, 54 * s, 26 * s); c.drawRoundRect(r, 22 * s, 22 * s, fill)
        fill.color = 0xFF5E6A3C.toInt(); r.set(-60 * s, -22 * s, 40 * s, -6 * s); c.drawRoundRect(r, 10 * s, 10 * s, fill)
        fill.color = 0xFF8AB8CC.toInt(); r.set(22 * s, -18 * s, 50 * s, 18 * s); c.drawRoundRect(r, 14 * s, 14 * s, fill)
        fill.color = 0x60FFFFFF; r.set(28 * s, -14 * s, 40 * s, -4 * s); c.drawRoundRect(r, 4 * s, 4 * s, fill)
        stroke.color = 0xFF101010.toInt(); stroke.strokeWidth = 4 * s
        c.drawLine(-46 * s, -36 * s, 36 * s, -36 * s, stroke); c.drawLine(-46 * s, 36 * s, 36 * s, 36 * s, stroke)
        // kuyruk pervanesi
        val ta = t * 40f
        stroke.color = 0xA0202020.toInt(); stroke.strokeWidth = 3 * s
        c.drawLine(-140 * s + cos(ta) * 14 * s, sin(ta) * 14 * s + 0f, -140 * s - cos(ta) * 14 * s, -sin(ta) * 14 * s, stroke)
        c.restore()
        }
        // ana pervane: bulanık disk + dört kanat
        fill.color = 0x22000000; c.drawCircle(gx, hy - 4 * s, 120 * s, fill)
        stroke.color = 0xB0181818.toInt(); stroke.strokeWidth = 5 * s
        val ra = t * 22f
        for (q in 0 until 4) {
            val a = ra + q * (PI.toFloat() / 2f)
            c.drawLine(gx, hy - 4 * s, gx + cos(a) * 118 * s, hy - 4 * s + sin(a) * 46 * s, stroke)
        }
        fill.color = 0xFF202020.toInt(); c.drawCircle(gx, hy - 4 * s, 7 * s, fill)
        if (alt < 150f && Settings.current.particles && Math.random() < 0.12)
            burst(x + ((Math.random() - 0.5) * 200).toFloat(), y, 1, 0xFFA89878.toInt(), 160f, 4f, 0, 0.7f, 30f)
        // iniş tozu: pervane rüzgârı iki yana yerden toz kaldırır (seyrek, küçük; eski dar kare büyütülünce kutu gibiydi)
        heliDustT -= 1f / 60f
        if (alt < 110f && heliDustT <= 0f) {
            heliDustT = 0.8f
            val side = if (Math.random() < 0.5) -1f else 1f
            fx("dust", x + side * (60f + (Math.random() * 50).toFloat()), y - 6, 150f)
        }
    }

    /**
     * Üs bayrağı (merkezde ele geçirme alanı): direk + dalgalanan bez (devlet haki/yıldız, klan rengi, kendi klanın
     * yeşil çerçeve); zeminde alan halkası ve ele geçirme ilerlemesi.
     */
    private fun drawFlag(c: Canvas, o: Int) {
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]; val rgb = drawBuf[o + 4].toInt(); val who = drawBuf[o + 5].toInt()
        val cap = drawBuf[o + 7]
        val sx = wx(x); val sy = wy(y)
        if (sx < -400 || sx > sw + 400 || sy < -500 || sy > sh + 300) return
        val s = scale
        val rr = 3f * 64f * s
        c.save(); c.scale(1f, 0.5f, sx, sy)
        stroke.color = alphaC(if (who == 2) 0xFF60FF90.toInt() else 0xFFFFFFFF.toInt(), 0.25f + 0.1f * sin(time * 2f))
        stroke.strokeWidth = 2.5f * density
        stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10 * density, 8 * density), time * 20f)
        c.drawCircle(sx, sy, rr, stroke)
        stroke.pathEffect = null
        if (cap > 0f && atlas.fx["capture"] == null) {
            stroke.color = 0xFFFFD34A.toInt(); stroke.strokeWidth = 6 * density
            c.drawArc(sx - rr, sy - rr, sx + rr, sy + rr, -90f, 360f * cap.coerceIn(0f, 1f), false, stroke)
        }
        c.restore()
        // V8 ele geçirme halkası: ilerlemeye göre dolan altın halka kareleri
        if (cap > 0f) drawFxSprite(c, "capture", x, y, rr * 2.1f / scale, 0f, fixedFrame = (cap.coerceIn(0f, 1f) * 5.99f).toInt())
        val poleH = 170f * s
        stroke.color = 0xFF2A2622.toInt(); stroke.strokeWidth = 5 * s; c.drawLine(sx, sy, sx, sy - poleH, stroke)
        stroke.color = 0xFF9A948A.toInt(); stroke.strokeWidth = 2.5f * s; c.drawLine(sx, sy, sx, sy - poleH, stroke)
        val col = when (who) { 0 -> 0xFF4E5A2E.toInt(); else -> (0xFF000000.toInt() or (rgb and 0xFFFFFF)).let { if (rgb == 0) 0xFFB04030.toInt() else it } }
        path.reset()
        val fw = 78f * s; val fh = 46f * s; val top = sy - poleH
        val n = 8
        for (q in 0..n) {
            val u = q / n.toFloat()
            val wave = sin(time * 5f - u * 6f) * 6f * s * u
            if (q == 0) path.moveTo(sx, top + wave) else path.lineTo(sx + u * fw, top + wave)
        }
        for (q in n downTo 0) {
            val u = q / n.toFloat()
            val wave = sin(time * 5f - u * 6f) * 6f * s * u
            path.lineTo(sx + u * fw, top + fh + wave)
        }
        path.close()
        fill.color = col; c.drawPath(path, fill)
        stroke.color = if (who == 2) 0xFF60FF90.toInt() else 0xFF15140F.toInt(); stroke.strokeWidth = 2 * s; c.drawPath(path, stroke)
        if (who == 0) {
            val mx = sx + fw * 0.45f; val my = top + fh * 0.5f + sin(time * 5f - 2.7f) * 3f * s
            drawText(c, "★", mx, my + 8 * s, 22 * s, 0xFFF0F0E0.toInt(), true, fontBold)
        }
    }

    /** Üsse yaklaşınca üst ortada bilgi kartı: sahip, kalan asker, ödül ve süresi, ele geçirme çubuğu. */
    private fun drawBaseHud(c: Canvas) {
        val b = baseNear ?: return
        if (!b.has("id")) return
        val d = density
        val cx = sw / 2f; val top = 58 * d
        val w = 300 * d; val h = if (b.optDouble("cap", 0.0) > 0) 82 * d else 62 * d
        val r = RectF(cx - w / 2, top, cx + w / 2, top + h)
        fill.color = 0xD0151410.toInt(); c.drawRoundRect(r, 6 * d, 6 * d, fill)
        stroke.color = if (b.optBoolean("mine")) 0xFF60FF90.toInt() else 0xFFB04A3A.toInt(); stroke.strokeWidth = 2 * d
        c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        val owner = b.optString("owner")
        drawText(c, "${b.optString("name")} · Sv ${b.optInt("level")} · " + if (b.optBoolean("mine")) "Klanının Üssü" else owner,
            cx, top + 18 * d, 13 * d, if (b.optBoolean("mine")) 0xFF8AF0A0.toInt() else 0xFFFFD34A.toInt(), true, fontBold)
        val alive = b.optInt("alive"); val max = b.optInt("max", 15)
        var line2 = "Asker $alive/$max"
        if (b.optInt("pending") > 0) line2 += " · Helikopter Geliyor"
        val left = b.optDouble("left", -1.0)
        if (left >= 0) line2 += " · Ödül: ${b.optString("reward")} ×${b.optInt("n", 1)} ${(left / 60).toInt()}:${"%02d".format((left % 60).toInt())}"
        drawText(c, line2, cx, top + 38 * d, 11.5f * d, 0xFFE8E2D0.toInt(), true, fontBold)
        val hint = when {
            b.optBoolean("mine") -> "Askerler Sana Dost; Üssü Savun"
            alive > 0 -> "Askerleri Temizle, Sonra Bayrağın Dibinde Dur"
            !b.optBoolean("inclan") -> "Ele Geçirmek İçin Bir Klana Katıl"
            b.optBoolean("sent") -> "Karargâha Bildirildi…"
            else -> "Bayrağın Dibinde 10 Sn Kal"
        }
        drawText(c, hint, cx, top + 55 * d, 11 * d, 0xFFB8B0A0.toInt(), true, fontBold)
        val cap = b.optDouble("cap", 0.0).toFloat()
        if (cap > 0f) {
            val br = RectF(cx - w / 2 + 12 * d, top + 64 * d, cx + w / 2 - 12 * d, top + 74 * d)
            fill.color = 0xFF2A2620.toInt(); c.drawRect(br, fill)
            fill.color = 0xFFFFD34A.toInt(); c.drawRect(br.left, br.top, br.left + br.width() * cap.coerceIn(0f, 1f), br.bottom, fill)
            drawText(c, "Ele Geçiriliyor %${(cap * 100).toInt().coerceAtMost(100)}", cx, br.bottom - 1 * d, 9.5f * d, 0xFF15140F.toInt(), true, fontBold)
        }
    }

    /** Savaşçı Sv10 karanlık aura: zeminde dönen mor halka + yükselen kıvılcımlar */
    private fun drawAura(c: Canvas, sx: Float, sy: Float, tier: Int) {
        val pal = AuraFx.PALETTES[tier.coerceIn(0, 3)]
        val rx = (150f + 22f * tier) * scale
        AuraFx.draw(c, sx, sy, rx, time, pal, density, 0.80f + 0.07f * tier, Settings.current.fx)
        if (!Settings.current.particles) return
        val px = hud[Native.PX]; val py = hud[Native.PY]
        val world = rx / scale
        // alev dilleri mührün çevresinden yükselir (kademe arttıkça daha çok), kor parıltıları arada
        val flames = 0.14f + 0.06f * tier
        if (Math.random() < flames) {
            val (ox, oy) = AuraFx.ringPoint(world * 1.1f)
            particles += Particle(px + ox, py + oy, 0f, 0f, 0f, 42f + 18f * tier + Math.random().toFloat() * 20f, 0.8f, 0.8f, pal.main, 4.6f + 1.2f * tier, 6)
        }
        if (Math.random() < 0.30f) {
            val (ox, oy) = AuraFx.ringPoint(world)
            particles += Particle(px + ox, py + oy, 4f, (Math.random().toFloat() - 0.5f) * 14f, 0f, 55f + Math.random().toFloat() * 70f, 1.1f, 1.1f, pal.hot, 3.2f, 3)
        }
        if (tier >= 2 && Math.random() < 0.20f) {
            val (ox, oy) = AuraFx.ringPoint(world * 0.8f)
            particles += Particle(px + ox, py + oy, 6f, (Math.random().toFloat() - 0.5f) * 40f, 0f, 90f + Math.random().toFloat() * 80f, 0.8f, 0.8f, 0xFFFFE07A.toInt(), 2.4f, 4)
        }
    }

    // yakın dövüş karesi: vuruş anı (swing olayı) 2. ve 3. kare arasına oturur, sonrası olaydan sayılır
    private var swingT = -9f
    private fun meleeFrame(t: Float, wIdx: Int): Int {
        val period = if (weaponSprites.getOrElse(wIdx) { "" } == "knife") 1f / 2.8f else 1f / 1.5f
        val since = time - swingT
        val post = period * 0.6f
        val fr = if (since in 0f..post) 2.5f + since / post * 3.5f
        else { val ct = t % period; if (ct < period * 0.4f) ct / (period * 0.4f) * 2.5f else 2.5f + (ct - period * 0.4f) / post * 3.5f }
        return fr.toInt().coerceIn(0, 5)
    }

    // yürüme/koşu adımı kat edilen yola göre ilerler: hız ne olursa olsun ayak kaymaz (döngü başına adım boyu)
    private var strideX = Float.NaN; private var strideY = 0f; private var stridePh = 0f
    private fun strideFrame(x: Float, y: Float, anim: String, n: Int = 8): Int {
        if (!strideX.isNaN()) {
            val d = kotlin.math.hypot(x - strideX, y - strideY)
            if (d < 120f) stridePh += d / (if (anim == "run") 110f else 64f)
        }
        strideX = x; strideY = y
        return ((stridePh % 1f) * n).toInt().coerceIn(0, n - 1)
    }

    // köpek: ölçülen hıza göre tırıs/dörtnal, adım yine yola bağlı
    private var petX = Float.NaN; private var petY = 0f; private var petPh = 0f; private var petV = 0f; private var petT = 0f
    private fun petGait(x: Float, y: Float, anims: Map<String, IntArray>): Pair<String, Int> {
        val dt = (time - petT).coerceIn(0.001f, 0.2f)
        var d = 0f
        if (!petX.isNaN()) { d = kotlin.math.hypot(x - petX, y - petY); if (d > 150f) d = 0f }
        petV += (d / dt - petV) * 0.25f
        petX = x; petY = y; petT = time
        val run = petV > 4.2f * 62.5f
        petPh += d / (if (run) 125f else 75f)
        val g = if (run) "run" else "walk"
        val n = anims[g]?.get(0) ?: 8
        return g to ((petPh % 1f) * n).toInt().coerceIn(0, n - 1)
    }

    /** Köpek yardımcı: tek görünüm (fularlı çoban köpeği, animasyonlu), "Köpek" etiketiyle. Seviye görünümü değiştirmez. */
    private fun drawPet(c: Canvas, o: Int) {
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val dir = drawBuf[o + 4].toInt()
        var anim = zAnimNames.getOrElse(drawBuf[o + 5].toInt()) { "idle" }
        val t = drawBuf[o + 6]
        val lv = drawBuf[o + 9].toInt()
        val pre = "p/dog0"
        val pa = atlas.anims(pre, atlas.zombieAnims)
        val gait = petGait(x, y, pa)
        val sx = wx(x); val sy = wy(y)
        if (sx < -300 || sx > sw + 300 || sy < -200 || sy > sh + 400) return
        var f = frameOf(pa, anim, t)
        if (anim == "walk") { anim = gait.first; f = gait.second }
        val vd = viewDir(pre, anim, dir, f)
        val flip = viewFlip
        val sp = atlas[spriteKey(pre, anim, vd, f)] ?: atlas[spriteKey(pre, "idle", 0, 0)] ?: atlas[spriteKey("z/dog", "idle", 0, 0)]
        if (sp == null) { placeholder(c, sx, sy, 0xFFC08A40.toInt()); return }
        val paint = if (drawBuf[o + 7] > 0) flashPaint else pixelPaint
        atlas.draw(c, sp, sx, sy, scale, paint, flip)
        plates += Plate(sx, sy - sp.ay * sp.k * scale + 2 * density, "Köpek Sv ${drawBuf[o + 9].toInt()}", 0xFF9BE07A.toInt(), drawBuf[o + 8], false, null)
    }

    private val zombiePixel = HashMap<String, Boolean>()

    private val magmaArt by lazy { atlas.has("z/magma/idle_0_0") }

    private fun drawZombie(c: Canvas, o: Int) {
        val code = drawBuf[o + 1].toInt()
        val kind = code % 8
        val species = code / 8
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val dir = drawBuf[o + 4].toInt()
        val anim = zAnimNames.getOrElse(drawBuf[o + 5].toInt()) { "idle" }
        val t = drawBuf[o + 6]
        val flash = drawBuf[o + 7]
        val hp = drawBuf[o + 8]
        val lvl = drawBuf[o + 9].toInt()
        val alpha = drawBuf[o + 10]
        val special = drawBuf[o + 11].toInt()
        val enraged = special and 1 == 1
        val maxHp = special shr 2
        val sx = wx(x); val sy = wy(y)
        if (sx < -400 || sx > sw + 400 || sy < -200 || sy > sh + 700) return
        // V13: Titan'ın Magma İskelet görseli (yürüme 8 yön, diğerleri 5 yön + ayna)
        val sid = if (species > 0) speciesId.getOrNull(species - 1) else if (kind == 7 && magmaArt) "magma" else null
        val name = sid ?: zombieSprite.getOrElse(kind) { "walker" }
        val f = frameOf(atlas.anims("z/$name", atlas.zombieAnims), anim, t)
        // piksel zombiler (köpek + V7 örgü iskeletli türler): tek görünüm (sola bakar), sağa bakarken aynalanır
        val pix = sid == null && zombiePixel.getOrPut(name) { !atlas.has("z/$name/idle_1_0") && atlas.has("z/$name/idle_0_0") }
        var zdir = if (pix) viewDir("z/$name", anim, dir, f) else dir
        var zflip = pix && viewFlip
        if (sid != null) {
            // V13 bölge yaratıkları 5 yön (G, GD, D, KD, K) çizildi: batı yanı doğunun aynası; ölüm yalnız G ve D
            if (anim == "death") {
                zdir = when (dir) { 2, 3 -> 2; 5, 6 -> 2; else -> 0 }
                zflip = dir == 5 || dir == 6
            } else if (dir >= 5 && !atlas.has(spriteKey("z/$name", anim, dir, 0))) { zdir = 8 - dir; zflip = true }
        }
        var sp = atlas[spriteKey("z/$name", anim, zdir, f)]
        if (sp == null) sp = atlas[spriteKey("z/$name", "idle", zdir, 0)]
        if (sp == null) sp = atlas[spriteKey("z/walker", anim, dir, f)]
        if (sp == null) { placeholder(c, sx, sy, zombieColor.getOrElse(kind) { -1 }); return }
        if (alpha > 0.3f && anim != "death") castShadow(c, sx, sy, sp.w * sp.k * scale * 0.5f, alpha) { p -> atlas.draw(c, sp, sx, sy, scale, p, zflip) }
        // tür aurası: yuvarlak leke değil, ayaktan yükselen dilli alev/sis (toksik: zehir yeşili, karanlık: mor, boss: kor)
        if ((kind == 4 || kind == 3 || kind == 7) && alpha > 0.3f && Settings.current.particles) {
            val col = when (kind) { 4 -> 0xFFB070FF; 3 -> 0xFF9AFF40; else -> if (enraged) 0xFFFF4A30 else 0xFFFF8A30 }.toInt()
            val rate = (if (kind == 7) 0.55f else 0.30f) * (sp.w * sp.k * scale / (120f * scale)).coerceIn(0.6f, 2.2f)
            if (Math.random() < rate) {
                val side = (Math.random().toFloat() - 0.5f) * sp.w * sp.k * 0.7f
                particles += Particle(x + side, y + 4f, 0f, 0f, 0f, 55f + Math.random().toFloat() * 40f, 0.85f, 0.85f, col, if (kind == 7) 8f else 5.5f, 6)
            }
            drawTexRing(c, sx, sy, sp.w * sp.k * scale * (0.5f + 0.06f * sin(time * 3f)), col, if (kind == 7) 0.55f else 0.4f)
        }
        val paint = when {
            flash > 0 -> flashPaint
            alpha < 0.99f -> { alphaPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255); alphaPaint }
            enraged -> { tintPaint.colorFilter = PorterDuffColorFilter(0x55FF2010, PorterDuff.Mode.SRC_ATOP); tintPaint }
            pix -> pixelPaint
            else -> bmpPaint
        }
        atlas.draw(c, sp, sx, sy, scale, paint, zflip)
        if (alpha > 0.35f && anim != "death") {
            val boss = kind == 7
            // sıradan zombiler: yalnızca hasar aldıysa can barı; isim yalnızca seçkin (tank, gizemli) ve boss'ta
            val elite = kind == 5 || kind == 6
            // yaratık plakası: adı üstte, can çubuğu altta; seviye rozeti ve "can / en çok" çubuğun içinde
            val cur = if (hp >= 0f && maxHp > 0) (hp * maxHp).roundToInt().coerceAtLeast(if (hp > 0f) 1 else 0) else -1
            val label = when {
                sid != null && species > 0 -> speciesName.getOrElse(species - 1) { "" }
                else -> zombieName.getOrElse(kind) { "" }
            }
            plates += Plate(sx, sy - sp.ay * sp.k * scale + 4 * density, label, zombieColor.getOrElse(kind) { -1 }, if (hp >= 0f) hp else 1f, boss, null,
                small = !boss && !elite, lvl = lvl.coerceAtLeast(1), hpCur = cur, hpMax = maxHp)
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
        groundBlob(c, sx, sy, sz * 0.4f, 4 * density, 0.3f)
        fill.shader = RadialGradient(sx, sy - sz * 0.5f + bob, sz * 0.9f, (col and 0x00FFFFFF) or 0x70000000, 0, Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy - sz * 0.5f + bob, sz * 0.9f, fill); fill.shader = null
        if (rarity >= 2) {
            stroke.color = col; stroke.strokeWidth = 2 * density; stroke.alpha = (160 + 90 * sin(age * 5f)).toInt().coerceIn(0, 255)
            c.drawCircle(sx, sy - sz * 0.5f + bob, sz * 0.62f, stroke); stroke.alpha = 255
        }
        val sp = itemSprites.getOrNull(item)
        rect.set(sx - sz / 2, sy - sz + bob, sx + sz / 2, sy + bob)
        if (sp != null) atlas.drawFit(c, sp, rect, pixelPaint) else { fill.color = col; c.drawCircle(sx, sy - sz / 2 + bob, sz / 3, fill) }
        // nadir eşyalarda yükselen ışık zerreleri
        if (rarity >= 2 && Settings.current.particles && Math.random() < 0.08)
            particles += Particle(x + ((Math.random() - 0.5) * 30).toFloat(), y, 12f, 0f, 0f, 45f, 0.9f, 0.9f, col, 3f, 3)
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
                // roket / dron füzesi: zeytin gövde, sivri başlık, kuyruk kanatçıkları; arkasında sıcak egzoz alevi ve kalıcı duman izi
                val big = if (kind == 1) 1f else 0.7f
                val L = 30f * scale * big
                val Wd = 6.2f * scale * big
                val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                // egzoz alevi (toplamalı, titrek)
                val fl = FxTex.flames[((time * 40f).toInt() + (x * 0.1f).toInt()) and 3]
                val flick = 0.85f + 0.3f * Math.random().toFloat()
                drawTex(c, fl, sx - dx * L * 0.52f, sy - dy * L * 0.52f, Wd * 3.4f * flick, deg + 90f + 180f, texPaint(0xFFFF9A30.toInt(), true), 0.95f, 1.0f, true)
                drawTex(c, fl, sx - dx * L * 0.52f, sy - dy * L * 0.52f, Wd * 1.9f * flick, deg + 90f + 180f, texPaint(0xFFFFFFFF.toInt(), true), 0.9f, 1.0f, true)
                c.save(); c.translate(sx, sy); c.rotate(deg)
                // kuyruk kanatçıkları
                fill.color = 0xFF3C4430.toInt()
                path.reset(); path.moveTo(-L * 0.50f, 0f); path.lineTo(-L * 0.62f, -Wd * 1.5f); path.lineTo(-L * 0.28f, -Wd * 0.5f); path.close(); c.drawPath(path, fill)
                path.reset(); path.moveTo(-L * 0.50f, 0f); path.lineTo(-L * 0.62f, Wd * 1.5f); path.lineTo(-L * 0.28f, Wd * 0.5f); path.close(); c.drawPath(path, fill)
                // gövde (arkada çelik boru, önde zeytin başlık)
                fill.color = 0xFF6A6E66.toInt(); c.drawRoundRect(-L * 0.50f, -Wd * 0.42f, -L * 0.12f, Wd * 0.42f, 2f, 2f, fill)
                fill.color = 0xFF5B6B3A.toInt(); c.drawRoundRect(-L * 0.14f, -Wd * 0.68f, L * 0.22f, Wd * 0.68f, Wd * 0.4f, Wd * 0.4f, fill)
                path.reset(); path.moveTo(L * 0.18f, -Wd * 0.68f); path.quadTo(L * 0.40f, -Wd * 0.50f, L * 0.58f, 0f); path.quadTo(L * 0.40f, Wd * 0.50f, L * 0.18f, Wd * 0.68f); path.close()
                fill.color = 0xFF74874A.toInt(); c.drawPath(path, fill)
                // ışık: üst yüzeyde açık şerit, alt yüzeyde koyu
                fill.color = 0x55FFFFFF; c.drawRect(-L * 0.12f, -Wd * 0.62f, L * 0.30f, -Wd * 0.30f, fill)
                fill.color = 0x55000000; c.drawRect(-L * 0.12f, Wd * 0.32f, L * 0.30f, Wd * 0.62f, fill)
                // sarı başlık bandı
                fill.color = 0xFFE0B040.toInt(); c.drawRect(L * 0.05f, -Wd * 0.68f, L * 0.09f, Wd * 0.68f, fill)
                c.restore()
                if (Settings.current.particles) fx3d.rocketTrail(x - dx * L * 0.45f / scale, y - 70f - dy * L * 0.45f / scale, dx, dy, 62.5f, y)
                flashes += Light(x, y - 70f, 150f, 0xFFFF9A40.toInt(), 0.05f, 0.05f)
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
        // V8: yandan çizilmiş motor ve açık cip (yön: sağa/sola çevrilir, dikey giderken son yön korunur)
        // V11: 8 yönlü çizim varsa (x8/moto/0..7, x8/car/0..7; docs/15 istemleri) araç gittiği yöne döner, yan görünüş kullanılmaz
        // V12.2: gezgin tüccarın kamyoneti (suv) için ayrı 8 yön (x8/truck, docs/19) varsa o
        var fxDir = dir
        // 3B araç (motosiklet: Nightstalker + sürücü, araba: Hellhound); kare yoksa çizim
        val d3 = (kind == 1 || kind == 2) && drawVehicle3D(c, kind, "v" + drawBuf[o + 10].toInt(), x, y, sx, sy, drawBuf[o + 5], speed, driver, hp, wreck, dir)
        if (!d3) {
            val d8 = (if (kind == 3) atlas["x8/truck/$dir"] else null) ?: if (kind >= 1) atlas["x8/${if (kind == 1) "moto" else "car"}/$dir"] else null
            val v8 = if (d8 != null) null else if (kind == 1) atlas["x/moto"] else if (kind >= 2) atlas["x/car"] else null
            val sp = d8 ?: v8 ?: atlas["v/$id/$dir"] ?: atlas["s/wreck0"]
            if (sp == null) { placeholder(c, sx, sy, 0xFFB03030.toInt()); return }
            // kendi aracın: Elmas Sandığı araç kaplaması
            val mine = driver >= 0 && hypot(x - hud[Native.PX], y - hud[Native.PY]) < 8f && Skins.vehicle.isNotEmpty()
            val paint = if (wreck) { tintPaint.colorFilter = PorterDuffColorFilter(0x90201810.toInt(), PorterDuff.Mode.SRC_ATOP); tintPaint }
                else if (mine) skinPaint(Skins.vehicle, false) else bmpPaint
            if (mine && speed > 40 && Settings.current.particles) {
                val tc = Skins.trail(Skins.vehicle)
                if (tc != 0 && Math.random() < 0.5) particles += Particle(x + (Math.random().toFloat() - 0.5f) * 60f, y, 10f, (Math.random().toFloat() - 0.5f) * 40f, 0f,
                    20f, 0.6f, 0.6f, tc, 3f, 3)
            }
            val vib = if (speed > 5 && !wreck) sin(time * 50f) * 0.6f * density else 0f
            // efektlerin (egzoz, toz, iz, far) yönü: yan görünüşlü araçta görselin baktığı yan (doğu/batı)
            if (v8 != null) {
                // motorun resmi sağa, cipin resmi sola bakar
                val right = when (dir) { in 1..3 -> true; in 5..7 -> false; else -> vehFace[driver] ?: (dir == 0) }
                fxDir = if (right) 2 else 6
                if (driver >= 0) vehFace[driver] = right
                val flip = if (kind == 1) !right else right
                val sgn = if (right) 1f else -1f
                val shw = sp.w * sp.k * scale * 0.46f
                groundBlob(c, sx, sy - scale, shw, 8 * scale, 0.3f)
                // hafif öne eğilme (hızlanırken) ve yol sarsıntısı
                val tilt = if (wreck) 0f else (speed / 600f).coerceIn(0f, 1f) * 1.5f * sgn
                c.save(); if (tilt != 0f) c.rotate(tilt, sx, sy)
                atlas.draw(c, sp, sx, sy + vib, scale, paint, flip)
                if (driver >= 0) drawSeated(c, driver, kind, right, sx, sy + vib)
                c.restore()
                if (hp < 0.999f && !wreck) bar(c, sx, sy - sp.ay * sp.k * scale - 6 * density, 56 * density, hp, 0xFF60D0FF.toInt())
                if (driver >= 0 && speed > 20 && !wreck) surfaceFx(x, y, fxDir, speed, kind)
            } else {
                // binicili iki tekerlekliler: kahraman eyerde oturur, hızla pedal çevirir; önden/arkadan bakışta araç önde
                val rider = driver >= 0 && kind <= 1
                val riderFront = rider && (dir == 0 || dir == 4)
                if (!riderFront) atlas.draw(c, sp, sx, sy + vib, scale, paint)
                if (rider) drawRider(c, driver, kind, dir, sx, sy + vib, speed)
                if (riderFront) atlas.draw(c, sp, sx, sy + vib, scale, paint)
                if (hp < 0.999f && !wreck) bar(c, sx, sy - sp.ay * sp.k * scale - 6 * density, 56 * density, hp, 0xFF60D0FF.toInt())
                if (driver >= 0 && speed > 20 && !wreck) surfaceFx(x, y, dir, speed, kind)
            }
        }
        // Araç arkası: tek efekt kaynağı. Tekerlek tozu + iz = surfaceFx (gövde + iz); egzoz yalnız ivmelenirken (ayrı iş);
        // far ışığı ayrı katman. Eski `road_*`, `dust` ve `exhaust` sprite'ları aynı işi ikinci kez yapıyordu (docs/22 §3.2).
        if (driver >= 0 && speed > 30 && !wreck && kind >= 1 && Settings.current.particles) {
            val acc = vehState["v" + drawBuf[o + 10].toInt()]?.acc ?: 0f
            if (acc > 110f && Math.random() < 0.4) {
                val bx = -sin(fxDir * PI.toFloat() / 4); val by = -cos(fxDir * PI.toFloat() / 4)
                val back = if (kind >= 2) 120f else 55f
                particles += Particle(x + bx * back, y + by * back * 0.7f, if (kind >= 2) 22f else 18f, bx * 55f, by * 30f, 36f, 0.55f, 0.55f,
                    0xFF9AA2AA.toInt(), 4.2f, 1)
            }
        }
        if (driver >= 0 && !wreck && 1f - hud[Native.LIGHT] > 0.05f)
            lightSources += floatArrayOf(x + sin(fxDir * PI.toFloat() / 4) * 160, y + cos(fxDir * PI.toFloat() / 4) * 120 - 30, 380f, 1f)
    }

    // ------------------------------------------------------------------ 3B araçlar
    private class VehState(var wheel: Float = 0f, var lastSpeed: Float = 0f, var tilt: Float = 0f, var acc: Float = 0f)
    private val vehState = HashMap<String, VehState>()

    /**
     * 3B araç: tekerlekler hızla döner, hızlanırken arka çöker / frende burun iner, yolda hafif sarsılır; motosiklette
     * sürücü eyerde. Kare yoksa false (çizim araç görseline düşer).
     */
    private fun drawVehicle3D(c: Canvas, kind: Int, id: String, x: Float, y: Float, sx: Float, sy: Float, angle: Float, speed: Float,
                              driver: Int, hp: Float, wreck: Boolean, dir: Int): Boolean {
        if (!Model3D.ready) return false
        val name = if (kind == 1) "nightstalker" else "hellhound"
        if (vehState.size > 40) vehState.clear()
        val st = vehState.getOrPut(id) { VehState(lastSpeed = speed) }
        val dt = frameDt.coerceAtLeast(1e-3f)
        val r = if (kind == 1) 0.33f else 0.41f
        if (!wreck) st.wheel = (st.wheel + Math.toDegrees((speed / 62.5f / r * dt).toDouble()).toFloat()) % 360f
        val acc = (speed - st.lastSpeed) / dt
        st.acc = acc
        st.lastSpeed = speed
        // ivme (dünya birimi/sn²) → gövde eğimi: hızlanınca arka çöker (−), frende arka kalkar (+)
        st.tilt += ((-acc / 62.5f * 0.5f).coerceIn(-4f, 4f) - st.tilt) * min(1f, dt * 6f)
        val bounce = if (wreck || speed < 5f) 0f else sin(time * 23f + x * 0.01f) * 0.012f * (speed / 500f).coerceIn(0f, 1f)
        val rider = if (kind == 1 && driver >= 0) Model3D.CHARS.getOrElse(driver) { "" } else ""
        val pxPerM = 62.5f * scale
        val fr = Model3D.vehicle(id, name, rider, angle, st.wheel, bounce, st.tilt, 0f, pxPerM, Settings.current.model3d) ?: return false
        val k = pxPerM / fr.pxPerM
        // gölge: kare siluetinin kendisi (şasi, tekerlek, sürücü); altında aracın boyuna yönünde koyu temas lekesi
        val L = if (kind == 1) 2.3f else 4.4f; val W = if (kind == 1) 0.8f else 2.1f
        val a = Math.toRadians(angle.toDouble()).toFloat()
        val fx = sin(a); val fy = cos(a) * 0.375f
        val flen = hypot(fx, fy); val plen = hypot(cos(a), sin(a) * 0.375f)
        drawShadow3d(c, fr, sx, sy, k, 0f, 1f)
        c.save(); c.translate(sx, sy); c.rotate(Math.toDegrees(atan2(fy, fx).toDouble()).toFloat())
        val hl = L * pxPerM * flen * 0.46f; val hw = W * pxPerM * plen * 0.40f + 3f * scale
        fill.color = alphaC(0xFF000000.toInt(), 0.30f)
        rect.set(-hl, -hw, hl, hw); c.drawRoundRect(rect, hw, hw, fill)
        c.restore()
        val mine = driver >= 0 && hypot(x - hud[Native.PX], y - hud[Native.PY]) < 8f && Skins.vehicle.isNotEmpty()
        val paint = when {
            wreck -> { tintPaint.colorFilter = PorterDuffColorFilter(0x90201810.toInt(), PorterDuff.Mode.SRC_ATOP); tintPaint }
            mine -> skinPaint(Skins.vehicle, false)
            else -> bmpPaint
        }
        drawFrame3d(c, fr, sx, sy, k, paint)
        if (hp < 0.999f && !wreck) bar(c, sx, sy - fr.ay * k - 6 * density, 56 * density, hp, 0xFF60D0FF.toInt())
        if (driver >= 0 && speed > 20 && !wreck) surfaceFx(x, y, dir, speed, kind)
        // Hellhound sert hızlanınca egzozdan nitro alevi
        if (kind == 2 && driver >= 0 && acc > 250f && speed > 60f && Math.random() < 0.6) {
            val p = fr.pts
            val ex = camX + (sx + (p[4] - fr.ax) * k - sw / 2f) / scale
            val ey = camY + (sy + (p[5] - fr.ay) * k - sh / 2f) / scale
            fx3d.muzzle(ex, ey, -fx / flen, -fy / flen, Fx3D.RPG, 62.5f, y)
        }
        return true
    }

    // ------------------------------------------------------------------ yüzeye göre araç izi ve tozu
    /** zemin izi (dünya koordinatı, açı, ömür); yumuşak zeminde (kum, kar, çamur) ve asfaltta frende kalır */
    private class Track(val x: Float, val y: Float, val a: Float, val color: Int, var life: Float, val max: Float, val w: Float)
    private val tracks = ArrayList<Track>()
    private var trackT = 0f

    /** Sürülen yerin yüzeyi: 0 asfalt, 1 toprak/orman, 2 kum, 3 kar, 4 çamur, 5 kül */
    private fun surface(): Int {
        val z = zones.getOrElse(hud[Native.ZONE].toInt()) { "" }
        return when {
            z.contains("Çöl") -> 2
            z.contains("Donmuş") -> 3
            z.contains("Bataklık") -> 4
            z.contains("Yanık") -> 5
            z.contains("Orman") || z.contains("Kamp") || z.contains("Karakol") || z.contains("Cephe") -> 1
            else -> 0
        }
    }

    private fun surfaceFx(x: Float, y: Float, dir: Int, speed: Float, kind: Int) {
        val surf = surface()
        val a = dir * PI.toFloat() / 4
        val bx = -sin(a); val by = -cos(a)
        val back = if (kind >= 2) 95f else 45f
        val wide = if (kind >= 2) 34f else 0f
        val k = (speed / 400f).coerceIn(0.2f, 1f)
        // teker izi: 0,06 sn'de bir iki (araba) ya da bir (motor) iz
        trackT += 1f / 60f
        if (trackT > 0.06f && (surf >= 2 || surf == 1 || speed > 300)) {
            trackT = 0f
            val col = when (surf) { 2 -> 0x55806040; 3 -> 0x66A0B4C8; 4 -> 0x66302818; 5 -> 0x55201A18; 1 -> 0x40403020; else -> 0x45101010 }
            val life = if (surf >= 2) 9f else 4f
            for (side in if (kind >= 2) intArrayOf(-1, 1) else intArrayOf(0)) {
                val tx = x + bx * back + by * side * wide * 0.7f
                val ty = y + by * back * 0.7f - bx * side * wide * 0.5f
                tracks += Track(tx, ty, a, col, life, life, if (kind >= 2) 9f else 6f)
            }
            if (tracks.size > 600) tracks.subList(0, tracks.size - 600).clear()
        }
        if (!Settings.current.particles) return
        // Tekerlek tozu: tek yayıcı. Yüzeye göre renk ve miktar; hız arttıkça sıklaşır. Bulutlar dokulu, kırık parçalar yerçekimli.
        val dust = dustColor(surf)
        val rate = when {
            surf == 0 -> if (speed > 300f) 0.6f else 0f
            else -> (0.8f + speed / 260f).coerceAtMost(3.2f)
        } * (if (kind >= 2) 1f else 0.8f)
        dustAcc += rate * frameDt * 30f
        while (dustAcc >= 1f) {
            dustAcc -= 1f
            val side = if (Math.random() < 0.5) -1f else 1f
            val ox = x + bx * back + by * side * wide * 0.7f
            val oy = y + by * back * 0.7f - bx * side * wide * 0.5f
            val r1 = Math.random().toFloat(); val r2 = Math.random().toFloat() - 0.5f; val r3 = Math.random().toFloat() - 0.5f
            particles += Particle(ox + r3 * 14f, oy + r2 * 8f, 3f + r1 * 8f,
                bx * (45f + 70f * r1) + by * r2 * 120f, by * (28f + 40f * r1) * 0.7f - bx * r2 * 70f, 24f + 38f * r1,
                0.8f + 0.45f * r1, 1.2f, dust, (if (kind >= 2) 7.5f else 6f) * (0.7f + 0.6f * r1), 1)
            // yumuşak zeminde fırlayan parçalar (kum tanesi, çamur topağı, taş kırığı, kar topağı)
            if (surf != 0 && Math.random() < 0.55) {
                val shade = if (surf == 3) 0xFFD8E4F4.toInt() else dust
                particles += Particle(ox, oy, 5f, bx * (30f + 90f * r1) + by * r3 * 160f, by * (20f + 40f * r1) - bx * r3 * 90f, 90f + 120f * r1,
                    0.5f + 0.3f * r1, 0.8f, shade, 2.4f + 1.4f * r1, 0)
            }
        }
    }

    private var dustAcc = 0f

    /** Yüzeyin toz rengi: kum, kar, çamur, kül, toprak; asfaltta soluk gri */
    private fun dustColor(surf: Int): Int = when (surf) {
        2 -> 0xFFD8B878.toInt()
        3 -> 0xFFEAF2FF.toInt()
        4 -> 0xFF5A6038.toInt()
        5 -> 0xFF54504C.toInt()
        1 -> 0xFF8A6A44.toInt()
        else -> 0xFFB8BCC4.toInt()
    }

    private val ZONE_HARBOR = 15  // world::ZONES_ALL sırası

    /** Kan gölü: öldürülen zombinin altında yayılır, 12 sn'de söner (zemin katmanı, her şeyin altında). */
    private class Pool(val x: Float, val y: Float, val size: Float, var t: Float = 0f)
    private val pools = ArrayList<Pool>()

    private fun drawPools(c: Canvas, dt: Float) {
        val it = pools.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.t += dt
            if (p.t > 12f) { it.remove(); continue }
            drawFxSprite(c, "blood_pool", p.x, p.y, p.size, p.t, alpha = (255 * ((12f - p.t) / 3f).coerceIn(0f, 1f) * 0.85f).toInt())
        }
    }

    private fun drawTracks(c: Canvas, dt: Float) {
        if (tracks.isEmpty()) return
        val it = tracks.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.life -= dt
            if (t.life <= 0f) { it.remove(); continue }
            val sx = wx(t.x); val sy = wy(t.y)
            if (sx < -50 || sx > sw + 50 || sy < -50 || sy > sh + 50) continue
            val k = (t.life / t.max).coerceIn(0f, 1f)
            val l = 11f * scale; val w = t.w * scale * 0.5f
            c.save(); c.rotate(-Math.toDegrees(t.a.toDouble()).toFloat(), sx, sy)
            // iz: koyu çukur + iki yanda çıkıntı (sıkışmış zemin) + ortada tırtıl çentikleri
            fill.color = alphaC(t.color, k)
            rect.set(sx - w, sy - l, sx + w, sy + l); c.drawRoundRect(rect, w, w, fill)
            fill.color = alphaC(0xFFFFFFFF.toInt(), k * 0.10f)
            c.drawRect(sx - w, sy - l, sx - w * 0.55f, sy + l, fill)          // yan çıkıntıda ışık
            fill.color = alphaC(0xFF000000.toInt(), k * 0.20f)
            var yy = sy - l + 2f * scale
            while (yy < sy + l - scale) { c.drawRect(sx - w * 0.7f, yy, sx + w * 0.7f, yy + scale, fill); yy += 3.4f * scale }
            c.restore()
        }
    }

    // eyer konumu (araç çapasına göre dünya birimi), yön 0..4; 5..7 aynası
    private val seatMoto = arrayOf(floatArrayOf(0f, -40f), floatArrayOf(-14f, -44f), floatArrayOf(-22f, -48f), floatArrayOf(-14f, -48f), floatArrayOf(0f, -42f))

    private fun drawRider(c: Canvas, driver: Int, kind: Int, dir: Int, sx: Float, sy: Float, speed: Float) {
        val actor = "h/" + heroIds.getOrElse(driver) { "kaan" }
        val f = if (speed > 5f) ((time * (5f + speed * 0.02f)).toInt() and 3) else 0
        val flip = dir in 1..3
        val key = spriteKey(actor, "ride", 0, f)
        val hs = atlas[key] ?: return
        val d5 = if (dir <= 4) dir else 8 - dir
        val tbl = seatMoto
        val ox = tbl[d5][0] * (if (dir <= 4) 1f else -1f); val oy = tbl[d5][1]
        val seat = atlas.attach(actor, key, "seat")
        val ax = sx + ox * scale - (seat?.get(0) ?: 0f) * scale * (if (flip) -1f else 1f)
        val ay = sy + oy * scale - (seat?.get(1) ?: -45f) * scale
        atlas.draw(c, hs, ax, ay, scale, pixelPaint, flip)
    }

    /** sürücü başına son bakış yönü (dikey giderken araç ters dönmesin) */
    private val vehFace = HashMap<Int, Boolean>()

    /**
     * V8 motor/cip sürücüsü: kalça noktası eyere oturur. Cipte gövde kapısının altı kırpılır (yalnız gövde ve baş görünür).
     * Ölçüler araç resminin kaynak pikselinde (çapaya göre), x resmin baktığı yönde.
     */
    private fun drawSeated(c: Canvas, driver: Int, kind: Int, right: Boolean, sx: Float, sy: Float) {
        val actor = "h/" + heroIds.getOrElse(driver) { "kaan" }
        val hs = atlas[spriteKey(actor, "ride", 0, 0)] ?: return
        val car = kind >= 2
        val vs = (if (car) atlas["x/car"] else atlas["x/moto"]) ?: return
        // eyer (kaynak px, çapaya göre): motor resmi sağa → arka (sol) tarafta; cip resmi sola → ön koltuk
        val seatX = if (car) 110f else 64f; val seatY = if (car) 52f else 37f
        val dx = (seatX - vs.ax) * vs.k * (if (car) -1f else 1f)  // resmin "ileri" eksenine göre
        val hx = sx + dx * (if (right) 1f else -1f) * scale
        val hy = sy + (seatY - vs.ay) * vs.k * scale
        // binici resmi sola bakar; kalça kaynakta (47, 115) civarı
        val hipX = (47f - hs.ax) * hs.k; val hipY = (115f - hs.ay) * hs.k
        val flip = right
        val ax = hx - hipX * scale * (if (flip) -1f else 1f)
        val ay = hy - hipY * scale
        if (car) {
            c.save(); c.clipRect(0f, 0f, sw.toFloat(), sy + (60f - vs.ay) * vs.k * scale)
            atlas.draw(c, hs, ax, ay, scale, pixelPaint, flip)
            c.restore()
        } else atlas.draw(c, hs, ax, ay, scale, pixelPaint, flip)
    }

    private fun drawDrone(c: Canvas, o: Int) {
        val x = drawBuf[o + 2]; val y = drawBuf[o + 3]
        val t = drawBuf[o + 6]
        val sx = wx(x); val sy = wy(y)
        val hover = sin(t * 3f) * 6 * scale
        val lift = 150 * scale
        groundBlob(c, sx, sy, 26 * scale, 8 * scale, 0.25f)
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
                drawFxSprite(c, "toxic", x, y + 10, drawBuf[o + 7] * 0.9f, age, alpha = (200 * fade).toInt())
                for (k in 0..4) {
                    val a = age * 0.6f + k * 1.3f
                    val cx = sx + cos(a) * r * 0.35f; val cy = sy - 40 * scale + sin(a * 1.3f) * r * 0.2f
                    fill.shader = RadialGradient(cx, cy, r * 0.7f, alphaC(0xFF90E040.toInt(), 0.35f * fade), 0, Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, r * 0.7f, fill)
                }
                fill.shader = null
            }
            2 -> { // şifa alanı
                drawFxSprite(c, "heal", x, y + 8, drawBuf[o + 7] * 0.7f, age, alpha = (230 * fade).toInt())
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
    private val addPixPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val fxPixPaint = Paint()

    /** Testler: efekti oyuncunun yanında tetikler */
    internal fun previewEffect(name: String, dx: Float = 130f, dy: Float = -20f) {
        val x = hud[Native.PX] + dx; val y = hud[Native.PY] + dy
        when (name) {
            "explode" -> fx3d.explode(x, y - 30f, 130f, false)
            "bomb" -> fx3d.explode(x, y - 40f, 320f, true)
            "levelup" -> fx3d.levelUp(hud[Native.PX], hud[Native.PY], 62.5f)
            else -> fx(name, x, y, if (name == "toxic" || name == "fire") 110f else 140f, 25f, false, -1f)
        }
    }

    private fun fx(name: String, x: Float, y: Float, size: Float, rot: Float = 0f, follow: Boolean = false, dur: Float = -1f, frame: Int = -1) {
        // V16: efektler kodla (dokulu parçacık, hilal/halka dalgaları, ateş topu); atlas efekt sprite'ları yalnız bilinmeyen adlar için
        val st = Settings.current
        if (!st.particles && name !in setOf("levelup", "teleport", "bolt")) return
        val ex = if (follow) hud[Native.PX] else x
        val ey = if (follow) hud[Native.PY] - (if (name == "levelup") 0f else 30f) else y
        if (fx3d.named(name, ex, ey, size, rot, dur, follow)) return
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
        val pixel = m.size > 4 && m[4] > 0f
        val paint = if (m[3] > 0f) (if (pixel) addPixPaint else addPaint) else if (pixel) fxPixPaint else alphaPaint
        paint.alpha = alpha
        val sx = wx(wxp); val sy = wy(wyp)
        val k = size * scale / sp.w
        if (rot != 0f) { c.save(); c.rotate(rot, sx, sy) }
        atlas.draw(c, sp, sx, sy, k, paint)
        if (rot != 0f) c.restore()
        paint.alpha = 255
    }

    /** İksir etkileri: süre HUD'dan gelir; bitince ya da araçtayken çizilmez. */
    private fun drawBuffs(c: Canvas) {
        if (hud[Native.IN_VEHICLE] > 0.5f || hud[Native.DEAD] > 0.5f) return
        val px = hud[Native.PX]; val py = hud[Native.PY] - 60
        val hl = hud[Native.HEAL_T]
        if (hl > 0f) drawFxSprite(c, "heal", px, py + 64, 62f, time, alpha = (230 * (hl / 1.2f).coerceIn(0f, 1f)).toInt())
        // hız: bedeni saran camgöbeği rüzgâr girdabı; güç: ayak altında alev halkası (V7 piksel efektler, döngü)
        fun ring(left: Float, name: String, y: Float, size: Float, ph: Float) {
            if (left <= 0f) return
            val a = (left / 1.2f).coerceIn(0f, 1f)
            drawFxSprite(c, name, px, y, size, time + ph, alpha = (235 * a).toInt())
        }
        ring(hud[Native.POWER_T], "fire_ring", py + 62, 150f, 0f)
        ring(hud[Native.SPEED_T], "wind_ring", py + 10, 135f, 0.3f)
    }

    private fun drawFx(c: Canvas) {
        drawBuffs(c)
        for (f in fxs) {
            if (f.follow) { f.x = hud[Native.PX]; f.y = hud[Native.PY] - 60 }
            val fade = if (f.dur > 0) ((f.dur - f.t) / 0.4f).coerceIn(0f, 1f) else 1f
            val op = if (f.name == "toxic") 0.72f else 1f  // zehir bulutu yarı saydam: altındaki zombi görünsün
            drawFxSprite(c, f.name, f.x, f.y, f.size, f.t, f.rot, (255 * fade * op).toInt(), f.frame)
        }
    }

    // ------------------------------------------------------------------ doku parçacıkları
    private val texPaints = HashMap<Long, Paint>()
    private val texRect = RectF()

    /** Renk çarpımlı doku boyası (renk başına bir kez; alpha çizimde ayarlanır) */
    private fun texPaint(color: Int, additive: Boolean): Paint {
        val key = (color.toLong() and 0xFFFFFFL) or (if (additive) 1L shl 40 else 0L)
        return texPaints.getOrPut(key) {
            Paint().apply {
                isFilterBitmap = false; isAntiAlias = false
                colorFilter = android.graphics.LightingColorFilter(color or 0xFF000000.toInt(), 0)
                if (additive) xfermode = addMode
            }
        }
    }

    /** Dokuyu (sx, sy) merkezli [w] px genişlikte çiz; [squash] dikey ezme (yere yatık bulutlar), [anchorBottom] tabanı sabitle (alev) */
    /** Ayakta yere yatık, dokulu halka (toplamalı): düz daire yerine titrek, dithered kenarlı halka */
    private fun drawTexRing(c: Canvas, sx: Float, sy: Float, rx: Float, color: Int, a: Float) {
        drawTex(c, FxTex.ring, sx, sy, rx * 2f, 0f, texPaint(color, true), a, 0.5f)
    }

    private fun drawTex(c: Canvas, bmp: Bitmap, sx: Float, sy: Float, w: Float, rot: Float, paint: Paint, alpha: Float,
                        squash: Float = 1f, anchorBottom: Boolean = false) {
        if (alpha <= 0.01f || w < 1f) return
        paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        val h = w * bmp.height / bmp.width * squash
        c.save()
        c.translate(sx, sy)
        if (rot != 0f) c.rotate(rot)
        if (anchorBottom) texRect.set(-w / 2f, -h, w / 2f, 0f) else texRect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        c.drawBitmap(bmp, null, texRect, paint)
        c.restore()
    }

    private fun drawParticles(c: Canvas) {
        val u = scale * density
        for (p in particles) {
            val k = (p.life / p.max).coerceIn(0f, 1f)
            val sx = wx(p.x); val sy = wy(p.y) - p.z * scale
            if (sx < -120 || sx > sw + 120 || sy < -160 || sy > sh + 120) continue
            val sd = p.seed
            when (p.kind) {
                0 -> { // kırık parça: yerçekimiyle döner, sonda söner
                    val spin = (p.max - p.life) * (sd - 0.5f) * 900f
                    drawTex(c, FxTex.chunks[(sd * 5f).toInt() % 5], sx, sy, p.size * u * 1.9f, spin, texPaint(p.color, false), min(1f, k * 3f))
                }
                1 -> { // toz / duman bulutu: çabuk belirir, büyüyerek ve dönerek söner; yere yatık (perspektif)
                    val a = min(1f, (1f - k) * 7f) * Math.pow(k.toDouble(), 0.8).toFloat() * 0.85f
                    val w = p.size * u * (1.6f + 2.4f * (1f - k))
                    drawTex(c, FxTex.puffs[(sd * 8f).toInt() % 8], sx, sy, w, sd * 360f + (1f - k) * 40f * (if (sd > 0.5f) 1f else -1f),
                        texPaint(p.color, false), a, 0.84f)
                }
                3 -> { // parıltı: dört köşeli yıldız, titreşir, toplamalı
                    val tw = 0.55f + 0.45f * sin(p.life * 17f + sd * 6.3f)
                    drawTex(c, FxTex.sparkle, sx, sy, p.size * u * 3.4f, 0f, texPaint(p.color, true), k * tw)
                }
                4 -> { // piksel kıvılcım: kare, hafif yalpalar, yer yer söner; sarı → turuncu → kor kırmızı
                    if (sin(p.life * 37f + p.x) > -0.55f) {
                        val col = when { k > 0.66f -> 0xFFFFE07A.toInt(); k > 0.33f -> 0xFFFF9A2E.toInt(); else -> 0xFFC8401E.toInt() }
                        fill.color = alphaC(col, min(1f, k * 1.6f))
                        val h = p.size * u * 0.6f
                        val dx = sin(p.life * 5f + p.y * 0.05f) * 3f * scale
                        c.drawRect(sx + dx - h, sy - h, sx + dx + h, sy + h, fill)
                    }
                }
                6 -> { // alev dili: tabandan doğar, yükselirken incelir ve söner (toplamalı, iki katman: renk + sıcak öz)
                    val grow = Math.sin(Math.PI * (1.0 - k)).toFloat().coerceAtLeast(0.05f)
                    val w = p.size * u * (0.5f + 0.9f * grow)
                    val fl = FxTex.flames[(sd * 6f).toInt() % 6]
                    val a = Math.pow(k.toDouble(), 0.6).toFloat() * min(1f, (1f - k) * 6f)
                    drawTex(c, fl, sx, sy, w, 0f, texPaint(p.color, true), a, 1f, true)
                    drawTex(c, fl, sx, sy, w * 0.62f, 0f, texPaint(0xFFFFFFFF.toInt(), true), a * 0.8f, 0.85f, true)
                }
            }
        }
    }

    /**
     * Düşen bomba: hedefte büyüyen kırmızı halka ve gölge, gökten inen kanatçıklı bomba (düştükçe hızlanır).
     * Görsel gelirse (atlas "s/air_bomb") onu kullanır; yoksa kodla çizilir.
     */
    private fun drawBombs(c: Canvas) {
        for (b in bombs) {
            val f = (b.t / b.dur).coerceIn(0f, 1f)
            val sx = wx(b.x); val sy = wy(b.y)
            // hedef halkası (yanıp söner) ve büyüyen gölge
            val pulse = 0.5f + 0.5f * sin(time * 14f)
            stroke.color = alphaC(0xFFFF3020.toInt(), 0.5f + 0.5f * pulse); stroke.strokeWidth = 3f * density
            val rr = (260f - 120f * f) * scale
            c.drawOval(sx - rr, sy - rr * 0.45f, sx + rr, sy + rr * 0.45f, stroke)
            fill.color = alphaC(0xFF000000.toInt(), 0.15f + 0.4f * f)
            val sr = (20f + 70f * f) * scale
            c.drawOval(sx - sr, sy - sr * 0.4f, sx + sr, sy + sr * 0.4f, fill)
            // bomba: yükseklik f² ile azalır (ivmelenme)
            val alt = (1f - f * f) * 1400f * scale
            val by = sy - alt - 40f * scale
            // bombardıman uçağının gölgesi yerde hedefin üstünden geçer (ilk yarıda)
            atlas["s/bomber_shadow"]?.let { sh ->
                val pf = b.t / 1.6f
                if (pf < 1f) {
                    alphaPaint.alpha = (110 * sin(pf * PI.toFloat())).toInt().coerceIn(0, 255)
                    atlas.draw(c, sh, sx + (pf - 0.35f) * 1500f * scale, sy + (pf - 0.35f) * 300f * scale, scale, alphaPaint)
                    alphaPaint.alpha = 255
                }
            }
            val sp = atlas["s/air_bomb"]
            if (sp != null) {
                // düşüş ıslığı: bombanın arkasında beyaz hava izi
                if (f > 0.15f) drawFxSprite(c, "bomb_whistle", b.x, b.y - alt / scale - 40f - 120f, 120f, b.t)
                atlas.draw(c, sp, sx, by + 40f * scale, scale, pixelPaint); continue
            }
            val w = 16f * scale; val h = 44f * scale
            fill.color = 0xFF3A3F2E.toInt(); c.drawOval(sx - w, by - h, sx + w, by + h * 0.6f, fill)
            fill.color = 0xFF5A6244.toInt(); c.drawOval(sx - w * 0.55f, by - h * 0.9f, sx + w * 0.2f, by + h * 0.2f, fill)
            fill.color = 0xFFC83020.toInt(); c.drawRect(sx - w, by - h * 0.25f, sx + w, by - h * 0.1f, fill)
            fill.color = 0xFF2A2E22.toInt()
            for (k in listOf(-1f, 1f)) c.drawRect(sx + k * w * 0.2f - (if (k < 0) w * 0.9f else 0f), by - h * 1.25f, sx + k * w * 0.2f + (if (k > 0) w * 0.9f else 0f), by - h * 0.95f, fill)
        }
    }

    private fun drawSpears(c: Canvas) {
        for (s in spears) {
            val f = (s.t / s.dur).coerceIn(0f, 1f)
            val arc = 160f * 4f * f * (1f - f)
            val x = s.x1 + (s.x2 - s.x1) * f; val y = s.y1 + (s.y2 - s.y1) * f - arc
            // yönelim: yayın türevi
            val dx = s.x2 - s.x1; val dy = (s.y2 - s.y1) - 160f * 4f * (1f - 2f * f)
            val l = max(1f, kotlin.math.hypot(dx, dy))
            val ux = dx / l; val uy = dy / l
            val len = 70f
            val sx = wx(x); val sy = wy(y)
            val tx = sx + ux * len * 0.5f * scale; val ty = sy + uy * len * 0.5f * scale
            val bx = sx - ux * len * 0.5f * scale; val by = sy - uy * len * 0.5f * scale
            stroke.color = 0xFF6A4424.toInt(); stroke.strokeWidth = 3.2f * density
            c.drawLine(bx, by, tx, ty, stroke)
            stroke.color = 0xFF3A2414.toInt(); stroke.strokeWidth = 1.2f * density
            c.drawLine(bx, by, tx, ty, stroke)
            // demir uç
            val px = -uy; val py = ux; val h = 9f * scale; val w = 4f * scale
            val path = android.graphics.Path()
            path.moveTo(tx + ux * h, ty + uy * h); path.lineTo(tx + px * w, ty + py * w); path.lineTo(tx - px * w, ty - py * w); path.close()
            fill.color = 0xFFD8DDE2.toInt(); c.drawPath(path, fill)
        }
    }

    private fun drawBolts(c: Canvas) {
        for (b in bolts) {
            val a = (b.life / 0.45f).coerceIn(0f, 1f)
            for (pass in 0..1) {
                stroke.color = if (pass == 0) alphaC(0xFF7FB0FF.toInt(), a * 0.55f) else alphaC(0xFFFFFFFF.toInt(), a)
                stroke.strokeWidth = (if (pass == 0) 12f else 4f) * density
                for (k in 0 until 9) c.drawLine(wx(b.pts[k * 2]), wy(b.pts[k * 2 + 1]), wx(b.pts[k * 2 + 2]), wy(b.pts[k * 2 + 3]), stroke)
            }
        }
    }

    private fun drawTracers(c: Canvas) {
        val sp = atlas["fx/tracer/0"]
        for (t in tracers) {
            val k = t.life / 0.12f
            val x1 = wx(t.x1); val y1 = wy(t.y1); val x2 = wx(t.x2); val y2 = wy(t.y2)
            stroke.color = alphaC(t.color, k * (if (sp != null) 0.35f else 1f)); stroke.strokeWidth = t.width * density
            c.drawLine(x1, y1, x2, y2, stroke)
            if (sp == null) continue
            // V8 iz mermisi: parlak baş namludan hedefe uçar, kuyruk geriye uzanır
            val p = ((1f - k) * 1.4f).coerceIn(0f, 1f)
            val hx = x1 + (x2 - x1) * p; val hy = y1 + (y2 - y1) * p
            val deg = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat() + 180f
            val len = (if (t.width > 2f) 150f else 100f) * scale
            c.save(); c.rotate(deg, hx, hy)
            atlas.draw(c, sp, hx, hy, len / (sp.w * sp.k), fxPixPaint)
            c.restore()
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
        if (Settings.current.fx > 0) for (f in flashes) glow(f.x, f.y, f.r, f.color, f.life / f.max)
        lightSources.clear()
        rect.set(0f, 0f, sw.toFloat(), sh.toFloat())
        c.drawBitmap(lightBmp!!, null, rect, multiply)
    }

    /** Konuşma balonları: isim → (metin, bitiş zamanı) */
    private val bubbles = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Float>>()
    private val plateRects = ArrayList<RectF>()

    /** Yazılan sohbet mesajını konuşanın kafasının üstünde 5 sn gösterir. */
    fun bubble(name: String, text: String) { bubbles[name] = text.take(140) to time + (4f + text.length * 0.05f).coerceAtMost(9f) }

    private fun drawPlates(c: Canvas) {
        plateRects.clear()
        val d = density
        // kameraya yakın olanlar önce; kendi karakterin her zaman ilk. İsimler asla yerinden kaydırılmaz: başkasınınkiyle
        // çakışan isim (kalabalıkta) saydamlaşır, kendi ismin ve boss'unki hep tam görünür.
        val cx = sw / 2f; val cy = sh / 2f
        plates.sortWith(compareBy({ if (it.self) 0 else 1 }, { hypot(it.x - cx, it.y - cy) }))
        val myLevel = GameSession.hud()[Native.LEVEL].toInt()
        var named = 0
        for (p in plates) {
            if (p.lvl > 0) { drawCreaturePlate(c, p, myLevel); continue }
            val ts = if (p.boss) 15 * d else if (p.small) 9.5f * d else 11.5f * d
            val y = p.y - (if (p.hp >= 0) 10 * d else 2 * d)
            if (p.hp >= 0) bar(c, p.x, p.y - 3 * d, if (p.boss) 120 * d else 52 * d, p.hp, if (p.boss) 0xFFFF4A3A.toInt() else 0xFFE04848.toInt())
            if (p.text.isEmpty()) continue
            if (named >= 12 && !p.boss && !p.self) continue
            val w = text.apply { typeface = fontBold; textSize = ts }.measureText(p.text) / 2 + 3 * d
            val lines = 1 + (if (p.sub != null) 1 else 0) + (if (p.title != null) 1 else 0)
            val r = RectF(p.x - w, y - ts * lines - 2 * d, p.x + w, y + 3 * d)
            val crowded = !p.self && !p.boss && plateRects.any { RectF.intersects(it, r) }
            plateRects += r
            named++
            if (crowded) c.saveLayerAlpha(r.left - 40 * d, r.top - 30 * d, r.right + 40 * d, r.bottom + 4 * d, 80)
            drawText(c, p.text, p.x, y, ts, p.color, center = true)
            var top = y - ts
            if (p.sub != null) { drawText(c, p.sub, p.x, y - ts * 1.1f, ts * 0.85f, p.subColor, center = true); top -= ts }
            if (p.title != null && !Settings.current.hideTitles) { drawTitle(c, p.title, p.titleRank, p.x, top - 3 * d, ts * 0.85f); top -= ts * 1.25f }
            if (crowded) c.restore()
            // konuşma balonu (oyuncular): koyu yarı saydam balon, konturuz beyaz yazı, en çok 3 satır
            bubbles[p.text]?.let { (msg, until) ->
                if (until < time) { bubbles.remove(p.text); return@let }
                drawBubble(c, msg, p.x, top - 4 * d, until - time)
            }
        }
    }

    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    /** Sohbet balonu: [bottom] kuyruğun ucu. Yazı konturuz, satırlara bölünür; son yarım saniyede söner. */
    private fun drawBubble(c: Canvas, msg: String, x: Float, bottom: Float, left: Float) {
        val d = density
        val ts = 13f * d
        bubbleText.typeface = fontBold; bubbleText.textSize = ts
        val maxW = 210f * d
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in msg.split(' ')) {
            val tryLine = if (line.isEmpty()) word else "$line $word"
            if (bubbleText.measureText(tryLine) <= maxW || line.isEmpty()) { line.clear(); line.append(tryLine) }
            else { lines += line.toString(); line = StringBuilder(word) }
            if (lines.size == 3) break
        }
        if (lines.size < 3 && line.isNotEmpty()) lines += line.toString()
        if (lines.size == 3 && lines.joinToString(" ").length < msg.length) lines[2] = lines[2].trimEnd().take(28) + "…"
        val w = lines.maxOf { bubbleText.measureText(it) } / 2 + 9 * d
        val lh = ts * 1.22f
        val h = lh * lines.size + 9 * d
        val a = (left / 0.5f).coerceIn(0f, 1f)
        val bb = RectF(x - w, bottom - 7 * d - h, x + w, bottom - 7 * d)
        fill.color = Color.argb((235 * a).toInt(), 18, 20, 26); c.drawRoundRect(bb, 7 * d, 7 * d, fill)
        path.reset(); path.moveTo(x - 6 * d, bb.bottom - 1); path.lineTo(x + 6 * d, bb.bottom - 1); path.lineTo(x, bottom); path.close()
        c.drawPath(path, fill)
        stroke.color = Color.argb((150 * a).toInt(), 255, 255, 255); stroke.strokeWidth = 1.2f * d
        c.drawRoundRect(bb, 7 * d, 7 * d, stroke)
        bubbleText.alpha = (255 * a).toInt(); bubbleText.textAlign = Paint.Align.CENTER
        lines.forEachIndexed { i, l -> c.drawText(l, x, bb.top + 4.5f * d + lh * (i + 1) - lh * 0.22f, bubbleText) }
    }

    /**
     * Yaratık plakası: üstte adı (türün rengi), altında can çubuğu. Çubuğun solunda kalkan biçimli seviye rozeti: rengi
     * seviye farkını söyler (gri: XP vermez, yeşil: kolay, sarı: denk, turuncu: zor, kırmızı: çok zor). Can çubuğun içinde.
     */
    private fun drawCreaturePlate(c: Canvas, p: Plate, myLevel: Int) {
        val d = density
        val bw = if (p.boss) 150 * d else if (p.small) 66 * d else 84 * d
        val bh = if (p.boss) 13 * d else 10 * d
        val by = p.y - 2 * d
        val left = p.x - bw / 2
        // kalabalıkta üst üste binen plaka yerinden oynamaz, saydamlaşır
        val area = RectF(left - bh, by - bh * 2.2f, left + bw, by + bh)
        val crowded = !p.boss && plateRects.any { RectF.intersects(it, area) }
        plateRects += area
        if (crowded) c.saveLayerAlpha(area.left - 4 * d, area.top - 14 * d, area.right + 4 * d, area.bottom + 6 * d, 70)
        drawCreaturePlateBody(c, p, myLevel, bw, bh, by, left)
        if (crowded) c.restore()
    }

    private fun drawCreaturePlateBody(c: Canvas, p: Plate, myLevel: Int, bw: Float, bh: Float, by: Float, left: Float) {
        val d = density
        // çubuk
        rect.set(left - 1.5f * d, by - bh / 2 - 1.5f * d, left + bw + 1.5f * d, by + bh / 2 + 1.5f * d)
        fill.color = 0xE0101216.toInt(); c.drawRoundRect(rect, 3 * d, 3 * d, fill)
        val col = if (p.boss) 0xFFFF4A3A.toInt() else 0xFFD8403C.toInt()
        rect.set(left, by - bh / 2, left + bw * p.hp.coerceIn(0f, 1f), by + bh / 2)
        if (rect.width() > 0) {
            fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, lighten(col, 1.4f), col, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, 2 * d, 2 * d, fill); fill.shader = null
        }
        if (p.hpCur >= 0) drawText(c, "${fmtShort(p.hpCur)} / ${fmtShort(p.hpMax)}", p.x + 7 * d, by + bh * 0.36f, bh * 0.92f, 0xFFFFFFFF.toInt(), center = true)
        // seviye rozeti (kalkan)
        val diff = p.lvl - myLevel
        val lc = when {
            myLevel - p.lvl > XP_LEVEL_GAP -> 0xFF8A8F96.toInt()
            diff <= -3 -> 0xFF5CC85A.toInt()
            diff <= 2 -> 0xFFF2C94A.toInt()
            diff <= 5 -> 0xFFF08A3A.toInt()
            else -> 0xFFE8433A.toInt()
        }
        val r = bh * 0.72f + 1 * d
        val bx = left - 1 * d
        path.reset()
        path.moveTo(bx - r, by - r); path.lineTo(bx + r, by - r); path.lineTo(bx + r, by + r * 0.25f)
        path.quadTo(bx + r, by + r * 0.8f, bx, by + r * 1.25f); path.quadTo(bx - r, by + r * 0.8f, bx - r, by + r * 0.25f); path.close()
        fill.color = 0xF0161A20.toInt(); c.drawPath(path, fill)
        stroke.color = lc; stroke.strokeWidth = 1.8f * d; c.drawPath(path, stroke)
        drawText(c, p.lvl.toString(), bx, by + r * 0.38f, if (p.lvl >= 100) r * 0.95f else r * 1.15f, lc, center = true)
        // ad
        if (p.text.isNotEmpty()) drawText(c, p.text, p.x, by - bh / 2 - 4 * d, if (p.boss) 14 * d else 10.5f * d, p.color, center = true)
    }

    private fun fmtShort(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
        n >= 10_000 -> "${n / 1000}B"
        else -> n.toString()
    }

    private val glintPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val plateSrc = android.graphics.Rect()
    private val plateDst = RectF()

    /**
     * Oyuncu başındaki unvan: arka plaka yok (oyunu kapatmasın), küçük ve okunaklı yazı.
     * 1. altın, 2. gümüş, 3. bronz: metal geçişli yazı, üstünden kayan ışık, çevresinde göz kırpan piksel yıldızlar;
     * 1.'nin önünde küçük taç. 4-10 sade açık mavi.
     */
    private fun drawTitle(c: Canvas, raw: String, rank: Int, x: Float, y: Float, size: Float) {
        val t = I18n.t(raw)
        val d = density
        val ts = size * 0.92f
        if (rank !in 1..3) { drawText(c, t, x, y, ts, 0xFFAED6FF.toInt(), center = true); return }
        val (hi, mid, lo) = when (rank) {
            1 -> Triple(0xFFFFF4B0.toInt(), 0xFFFFC83A.toInt(), 0xFFC07A10.toInt())
            2 -> Triple(0xFFFFFFFF.toInt(), 0xFFD6DEE8.toInt(), 0xFF7A8696.toInt())
            else -> Triple(0xFFFFD8B0.toInt(), 0xFFE0904A.toInt(), 0xFF8A4A1E.toInt())
        }
        text.typeface = fontBold; text.textSize = ts
        val tw = text.measureText(t)
        val left = x - tw / 2; val top = y - ts
        // ince plaka: koyu zemin, sıra renginde ince kenar ve üst ışık çizgisi (kanatlı kurdele kaldırıldı; sıralama
        // plakası görselleri gelince onlar çizilecek, istem: docs/22 Bölüm 6)
        val pw = tw / 2 + 10 * d; val pcy = y - ts * 0.36f; val ph = ts * 0.78f
        tutOval.set(x - pw, pcy - ph, x + pw, pcy + ph * 0.92f)
        fill.color = 0xC8100E0C.toInt(); c.drawRoundRect(tutOval, 3 * d, 3 * d, fill)
        stroke.color = alphaC(mid, 0.85f); stroke.strokeWidth = 1.1f * d; c.drawRoundRect(tutOval, 3 * d, 3 * d, stroke)
        stroke.color = alphaC(hi, 0.35f); stroke.strokeWidth = 0.8f * d
        c.drawLine(tutOval.left + 4 * d, tutOval.top + 1.6f * d, tutOval.right - 4 * d, tutOval.top + 1.6f * d, stroke)
        // koyu kontur
        textStroke.typeface = fontBold; textStroke.textSize = ts; textStroke.strokeWidth = ts * 0.2f
        textStroke.textAlign = Paint.Align.CENTER; textStroke.alpha = 230
        c.drawText(t, x, y, textStroke)
        // metal geçiş + kayan ışık bandı
        val u = left + ((time * 0.45f + rank * 0.3f) % 1.6f) * (tw + 80 * d) - 40 * d
        text.shader = LinearGradient(0f, top, 0f, y, intArrayOf(hi, mid, lo), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        text.textAlign = Paint.Align.CENTER
        c.drawText(t, x, y, text)
        text.shader = LinearGradient(u - 18 * d, top, u + 18 * d, y, intArrayOf(0x00FFFFFF, 0xB0FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawText(t, x, y, text)
        text.shader = null; text.textAlign = Paint.Align.LEFT
        // 1.: küçük piksel taç
        if (rank == 1) {
            val cx = left - 9 * d; val cy = y - ts * 0.42f; val k = ts * 0.09f
            fill.color = 0xFF2A1A08.toInt(); c.drawRect(cx - 5 * k - k, cy - 3 * k - k, cx + 5 * k + k, cy + 3 * k + k, fill)
            fill.color = mid
            c.drawRect(cx - 5 * k, cy, cx + 5 * k, cy + 3 * k, fill)
            for (i in -1..1) c.drawRect(cx + i * 4 * k - k, cy - 3 * k, cx + i * 4 * k + k, cy, fill)
            fill.color = 0xFFE03A3A.toInt(); c.drawRect(cx - k, cy + k, cx + k, cy + 2 * k, fill)
        }
        // plakanın kenarlarında doğup sönen dört köşeli yıldız parıltıları (ilk 3; 1.'de daha çok)
        val n = 6 - rank * 1
        for (i in 0 until n) {
            val life = (time * (0.55f + i * 0.07f) + i * 0.618f) % 1f
            val a = sin(life * PI.toFloat())
            if (a < 0.08f) continue
            val side = ((i * 0.37f + 0.11f + (time * 0.05f).toInt() * 0.29f) % 1f)
            val px = tutOval.left + side * tutOval.width()
            val py = if (i % 2 == 0) tutOval.top else tutOval.bottom
            val r = (2.2f + 2.6f * a) * d * (if (rank == 1) 1.15f else 1f)
            path.reset()
            path.moveTo(px, py - r); path.quadTo(px, py, px + r, py); path.quadTo(px, py, px, py + r)
            path.quadTo(px, py, px - r, py); path.quadTo(px, py, px, py - r); path.close()
            fill.color = alphaC(hi, a); c.drawPath(path, fill)
            fill.color = alphaC(0xFFFFFFFF.toInt(), a * 0.9f); c.drawCircle(px, py, r * 0.18f, fill)
        }
    }

    /** Kaplama boyası: kaplama başına önbellekli renk matrisi filtresi (piksel kahramanda bulanıksız) */
    private val skinPaints = HashMap<String, Paint>()
    private fun skinPaint(id: String, pixel: Boolean): Paint = skinPaints.getOrPut(id + pixel) {
        Paint(if (pixel) 0 else Paint.FILTER_BITMAP_FLAG).apply {
            Skins.matrix(id)?.let { colorFilter = android.graphics.ColorMatrixColorFilter(it) }
        }
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun drawFloaters(c: Canvas) {
        for (f in floaters) {
            val k = (f.life / f.max)
            val pop = if (k > 0.85f) 1f + (k - 0.85f) * 3f else 1f
            drawText(c, f.text, wx(f.x), wy(f.y), f.size * density * pop * 0.75f, alphaC(f.color, min(1f, k * 2.5f)), center = true)
        }
    }

    private fun drawText(c: Canvas, raw: String, x: Float, y: Float, size: Float, color: Int, center: Boolean = false, font: Typeface = fontBold, tr: Boolean = true) {
        val s = if (tr) I18n.t(raw) else raw
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
        groundBlob(c, x, y, 20 * scale, 6 * scale, 0.3f)
        fill.color = col; c.drawRoundRect(x - 14 * scale, y - 100 * scale, x + 14 * scale, y, 12 * scale, 12 * scale, fill)
    }

    // ------------------------------------------------------------------ HUD
    private val portraitCache = HashMap<Int, Sprite?>()

    private fun drawHud(c: Canvas) {
        val d = density
        val hp = hud[Native.HP]; val maxHp = max(1f, hud[Native.MAXHP])
        // kırmızı vinyet (hasar / düşük can)
        val low = ((0.3f - hp / maxHp) / 0.3f).coerceIn(0f, 1f) * (0.5f + 0.5f * sin(time * 5f))
        if (whiteFlash > 0.01f) { fill.color = alphaC(0xFFE8F0FF.toInt(), whiteFlash * 0.6f); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), fill) }
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
        val zi = hud[Native.ZONE].toInt()
        val zone = if (zi == zoneLabelOf && zoneLabel.isNotEmpty()) zoneLabel else zones.getOrElse(zi) { "" }
        drawText(c, if (serverTitle.isNotEmpty()) "$serverTitle => $zone" else zone, sw / 2f, 26 * d, 15 * d, 0xFFFFE9A8.toInt(), true, fontDisplay)
        if (hud[Native.PVP] > 0f) {
            val a = 0.65f + 0.35f * kotlin.math.sin(time * 4f)
            drawText(c, "⚔ PvP Bölgesi · Oyuncular Saldırabilir", sw / 2f, 44 * d, 12 * d, alphaC(0xFFFF5A4A.toInt(), a), true)
        }
        // boss barı (üst orta bağlamsal panel)
        if (hud[Native.BOSS_HP] >= 0 && hud[Native.BOSS_MAX] > 0) {
            val w = sw * 0.42f
            drawText(c, "TİTAN", sw / 2f, 64 * d, 16 * d, 0xFFFF6A50.toInt(), true, fontDisplay)
            hudBar(c, sw / 2f - w / 2, 70 * d, w, 12 * d, hud[Native.BOSS_HP] / hud[Native.BOSS_MAX], 0xFFD02A20.toInt(),
                "${hud[Native.BOSS_HP].roundToInt()}", 0f)
        }
        // ---- sağ üst: radar (altın ve elmas Compose şeridinde: GameScreen.CurrencyBar)
        val rr = 64 * d
        val rcx = sw - 16 * d - rr; val rcy = 16 * d + rr
        drawRadar(c, rcx, rcy, rr)
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
                drawText(c, line, 16 * d, y, 12.5f * d, alphaC(0xFFEEF2F0.toInt(), min(1f, life)), tr = false)
                y -= 17 * d
            }
        }
        // ---- afiş: üst ortadaki bağlamsal panelin (boss, üs kartı, etkinlik) altında
        val boss = hud[Native.BOSS_HP] >= 0 && hud[Native.BOSS_MAX] > 0
        val base = baseNear?.has("id") == true
        HudLayout.topBusy = boss || base
        slotBottom = when {
            base -> 58 * d + (if (baseNear?.optDouble("cap", 0.0) ?: 0.0 > 0.0) 82 else 62) * d
            boss -> 84 * d
            HudLayout.eventShown -> 100 * d
            else -> 50 * d
        }
        if (bannerT > 0) {
            val k = (bannerT / BANNER_DUR).coerceIn(0f, 1f)
            val a = if (k > 0.85f) (1f - k) / 0.15f else if (k < 0.2f) k / 0.2f else 1f
            val y = max(sh * 0.26f, slotBottom + 46 * d)
            fill.shader = LinearGradient(sw * 0.2f, 0f, sw * 0.8f, 0f, intArrayOf(0, alphaC(0xCC0A0E16.toInt(), a), 0), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, y - 44 * d, sw.toFloat(), y + 26 * d, fill); fill.shader = null
            drawText(c, banner, sw / 2f, y, 30 * d * (if (k > 0.85f) 1f + (k - 0.85f) else 1f), alphaC(bannerColor, a), true, fontDisplay)
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

    /** Mini harita: dünya görüntüsünden (binalar, ağaçlar) oyuncu çevresi + canlı noktalar. Dokununca tam harita. */
    private val mapSrc = Rect()
    private val mapDst = RectF()
    private val mapClip = Path()
    private var radarCx = 0f; private var radarCy = 0f; private var radarR = 0f
    private val mapSrcF = RectF()
    private fun drawRadar(c: Canvas, cx: Float, cy: Float, r: Float) {
        val d = density
        radarCx = cx; radarCy = cy; radarR = r
        val px = hud[Native.PX]; val py = hud[Native.PY]
        val range = 1500f
        val bmp = com.zenvex.online.ui.WorldMapImage.bitmap()
        c.save()
        mapClip.reset(); mapClip.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(mapClip)
        fill.color = 0xFF2A3A20.toInt(); c.drawCircle(cx, cy, r, fill)
        val rm = RegionMap.snap?.takeIf { it.area == groundArea }
        if (rm != null) {
            // V15: bölgenin gerçek küçültülmüş görüntüsü
            mapSrcF.set(rm.u(px - range), rm.v(py - range), rm.u(px + range), rm.v(py + range))
            mapSrcF.round(mapSrc)
            mapDst.set(cx - r, cy - r, cx + r, cy + r)
            c.drawBitmap(rm.bmp, mapSrc, mapDst, bmpPaint)
        } else if (bmp != null) {
            val k = bmp.width / mapPx
            val sr = range * k
            mapSrc.set((px * k - sr).toInt(), (py * k - sr).toInt(), (px * k + sr).toInt(), (py * k + sr).toInt())
            mapDst.set(cx - r, cy - r, cx + r, cy + r)
            c.drawBitmap(bmp, mapSrc, mapDst, pixelPaint)
        }
        // bölge dışı (başka harita) karanlık
        areas.getOrNull(com.zenvex.online.ui.areaIndexAt(areas, px, py))?.let { a ->
            c.save()
            for (q in a.rects) {
                rect.set(cx + (q[0] - px) / range * r, cy + (q[1] - py) / range * r, cx + (q[2] - px) / range * r, cy + (q[3] - py) / range * r)
                c.clipOutRect(rect)
            }
            fill.color = 0xE60C0A12.toInt(); c.drawCircle(cx, cy, r, fill)
            c.restore()
        }
        // kenar karartması
        fill.shader = RadialGradient(cx, cy, r, intArrayOf(0, 0, 0x90000000.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null
        for (i in 0 until radarN) {
            val x = radarBuf[i * 3]; val y = radarBuf[i * 3 + 1]; val t = radarBuf[i * 3 + 2].toInt()
            var dx = (x - px) / range; var dy = (y - py) / range
            val l = hypot(dx, dy)
            if (l > 1f) { if (t == 0) continue; dx /= l; dy /= l }
            if (t == 7 && l > 1f) continue
            val col = when (t) { 0 -> 0xFFFF3A30.toInt(); 1 -> 0xFFFF1010.toInt(); 2 -> 0xFF60D0FF.toInt(); 3 -> 0xFFFFD34A.toInt(); 5 -> 0xFFFF9A2A.toInt(); 6 -> 0xFFC8B878.toInt(); 7 -> 0xFFFF7A50.toInt(); else -> 0xFF7FFFE0.toInt() }
            val sx = cx + dx * r * 0.96f; val sy = cy + dy * r * 0.96f
            if (t == 6) {
                fill.color = 0xFF000000.toInt(); c.drawRect(sx - 5 * d, sy - 5 * d, sx + 5 * d, sy + 5 * d, fill)
                fill.color = col; c.drawRect(sx - 4 * d, sy - 4 * d, sx + 4 * d, sy + 4 * d, fill)
                continue
            }
            val rad = (if (t == 1) 4.5f else if (t == 0 || t == 7) 2.6f else 3.4f) * d
            fill.color = 0xFF000000.toInt(); c.drawCircle(sx, sy, rad + 1 * d, fill)
            fill.color = col; c.drawCircle(sx, sy, rad, fill)
            if (t == 5) drawText(c, "!", sx, sy - 5 * d, 12 * d, 0xFFFF9A2A.toInt(), true, fontBold)
        }
        c.restore()
        // oyuncu oku (bakış yönü)
        c.save(); c.rotate(dirAngle(hud[Native.PX], hud[Native.PY]), cx, cy)
        path.reset()
        path.moveTo(cx, cy - 7 * d); path.lineTo(cx + 5 * d, cy + 5 * d); path.lineTo(cx, cy + 2.5f * d); path.lineTo(cx - 5 * d, cy + 5 * d); path.close()
        fill.color = Color.WHITE; c.drawPath(path, fill)
        stroke.color = 0xFF15140F.toInt(); stroke.strokeWidth = 1.2f * d; c.drawPath(path, stroke)
        c.restore()
        // piksel çerçeve
        stroke.color = 0xFF15140F.toInt(); stroke.strokeWidth = 4 * d; c.drawCircle(cx, cy, r, stroke)
        stroke.color = 0xFFB89A5A.toInt(); stroke.strokeWidth = 2 * d; c.drawCircle(cx, cy, r - 1 * d, stroke)
        // kuzey işareti
        drawText(c, "K", cx, cy - r + 11 * d, 10 * d, 0xFFFFE6A0.toInt(), true, fontBold)
    }

    private var lastPx = 0f; private var lastPy = 0f; private var faceAng = 0f
    /** Oyuncunun hareket yönünden ok açısı (derece, yukarı = 0) */
    private fun dirAngle(x: Float, y: Float): Float {
        val dx = x - lastPx; val dy = y - lastPy
        if (dx * dx + dy * dy > 4f) faceAng = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        lastPx = x; lastPy = y
        return faceAng
    }

    // ------------------------------------------------------------------ öğretici
    // Doğal yönlendirme: hedefte yerde nabız gibi atan yumuşak halka + üstünde küçük zıplayan işaret, ekran dışındaysa
    // kenarda küçük yön göstergesi; rehber Nova yapılacak işi kısa bir konuşma balonuyla söyler ve balon kendiliğinden söner.
    // Adımlar gerçek eylemi bekler (yürüdün mü, saldırdın mı, sayaç ilerledi mi).
    @Volatile private var guideN = 0
    @Volatile private var guideGoal = 0
    @Volatile private var guideReady = false
    @Volatile private var guideOffer = false
    @Volatile private var guideGiver = 0
    private var tutMoved = false
    private var tutAttacked = false
    private var tutKey = ""
    private var tutT = 0f
    private var tutLast = 0f
    private val tutOval = RectF()

    /** Yerde iki dalga halinde genişleyip sönen halka (izometrik elips) */
    private fun groundRing(c: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        for (k in 0..1) {
            val ph = (time * 0.8f + k * 0.5f) % 1f
            val rr = rad * (0.5f + 0.7f * ph)
            stroke.color = alphaC(color, 0.6f * (1f - ph)); stroke.strokeWidth = 2f * density
            tutOval.set(x - rr, y - rr * 0.42f, x + rr, y + rr * 0.42f); c.drawOval(tutOval, stroke)
        }
        fill.color = alphaC(color, 0.10f + 0.05f * sin(time * 3f))
        tutOval.set(x - rad * 0.5f, y - rad * 0.21f, x + rad * 0.5f, y + rad * 0.21f); c.drawOval(tutOval, fill)
    }

    /** Küçük ok başı: ucu (x, y), [ang] yönünü gösterir */
    private fun chevron(c: Canvas, x: Float, y: Float, ang: Float, size: Float, color: Int, a: Float) {
        c.save(); c.rotate(Math.toDegrees(ang.toDouble()).toFloat(), x, y)
        path.reset()
        path.moveTo(x, y); path.lineTo(x - size, y - size * 0.75f); path.lineTo(x - size * 0.55f, y); path.lineTo(x - size, y + size * 0.75f); path.close()
        fill.color = alphaC(color, a); c.drawPath(path, fill)
        stroke.color = alphaC(0xFF15140F.toInt(), a * 0.7f); stroke.strokeWidth = 1.4f * density; c.drawPath(path, stroke)
        c.restore()
    }

    /** Yuvarlak köşeli yarı saydam küçük etiket */
    private fun pill(c: Canvas, s: String, x: Float, y: Float, color: Int, a: Float = 1f) {
        val d = density
        val w = text.apply { typeface = fontBold; textSize = 12 * d }.measureText(s) + 16 * d
        fill.color = alphaC(0xB0101010.toInt(), a); tutOval.set(x - w / 2, y - 10 * d, x + w / 2, y + 9 * d)
        c.drawRoundRect(tutOval, 9 * d, 9 * d, fill)
        drawText(c, s, x, y + 4 * d, 12 * d, alphaC(color, a), true, fontBold)
    }

    /** Ekrandaki bir tuşun çevresinde sönerek genişleyen ince halka */
    private fun hintRing(c: Canvas, x: Float, y: Float, r: Float) {
        for (k in 0..1) {
            val ph = (time * 1.1f + k * 0.5f) % 1f
            stroke.color = alphaC(0xFFFFE6A0.toInt(), 0.55f * (1f - ph)); stroke.strokeWidth = 2.5f * density
            c.drawCircle(x, y, r * (1f + 0.3f * ph), stroke)
        }
        // işaret eden el: parmak ucu halkanın merkezine, hafifçe dokunur gibi iner kalkar
        com.zenvex.online.ui.UiArt.bitmap(context, "tut_hand")?.let { b ->
            val h = 46 * density; val w = h * b.width / b.height
            val bob = (0.5f + 0.5f * sin(time * 5f)) * 8 * density
            tutOval.set(x - w * 0.32f, y + bob, x - w * 0.32f + w, y + bob + h)
            c.drawBitmap(b, null, tutOval, pixelPaint)
        }
    }

    /**
     * Rehberin konuşma balonu: üst ortada, bölge adı ve bağlamsal panelin (boss/üs/etkinlik) altında; sözcük sözcük sarılır,
     * 9 sn sonra söner. Duyuru afişi (yeni görev, bölge) aynı yere düşer: afiş varken balon bekler ve süresi işlemez —
     * üst ortada aynı anda tek mesaj.
     */
    private fun guideBubble(c: Canvas, raw: String) {
        val msg = I18n.t(raw)
        val d = density
        val a = (tutT / 0.35f).coerceIn(0f, 1f) * ((9.5f - tutT) / 0.6f).coerceIn(0f, 1f)
        if (a <= 0.01f) return
        val size = 13.5f * d
        text.typeface = fontBold; text.textSize = size
        val maxW = min(sw * 0.44f, 420 * d)
        val lines = ArrayList<String>()
        var cur = ""
        for (w in msg.split(' ')) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (text.measureText(t) > maxW && cur.isNotEmpty()) { lines += cur; cur = w } else cur = t
        }
        if (cur.isNotEmpty()) lines += cur
        val lh = size * 1.3f
        val bw = maxW + 24 * d
        val bh = lines.size * lh + 34 * d
        val diana = guideStep == "g8" || guideStep == "g9" || (guideOffer && guideGiver == 8)
        // Komutan Diana konuşurken balonun solunda portresi
        val port = if (diana) com.zenvex.online.ui.UiArt.bitmap(context, "commander_portrait") else null
        val pw = if (port != null) max(bh, 64 * d) * port.width / port.height else 0f
        val x0 = sw / 2f - bw / 2 + pw / 2; val y0 = slotBottom + 6 * d - (1f - a) * 10 * d
        if (port != null) {
            val ph = max(bh, 64 * d)
            tutOval.set(x0 - pw - 4 * d, y0, x0 - 4 * d, y0 + ph)
            fill.color = alphaC(0xD8141310.toInt(), a); c.drawRoundRect(tutOval, 6 * d, 6 * d, fill)
            pixelPaint.alpha = (255 * a).toInt(); c.drawBitmap(port, null, tutOval, pixelPaint); pixelPaint.alpha = 255
            stroke.color = alphaC(0xFF6A5A3A.toInt(), a); stroke.strokeWidth = 1.5f * d; c.drawRoundRect(tutOval, 6 * d, 6 * d, stroke)
        }
        fill.color = alphaC(0xD8141310.toInt(), a); tutOval.set(x0, y0, x0 + bw, y0 + bh); c.drawRoundRect(tutOval, 8 * d, 8 * d, fill)
        stroke.color = alphaC(0xFF6A5A3A.toInt(), a); stroke.strokeWidth = 1.5f * d; c.drawRoundRect(tutOval, 8 * d, 8 * d, stroke)
        drawText(c, if (diana) "Komutan Diana" else "İlk Adımlar", x0 + 12 * d, y0 + 17 * d, 12 * d, alphaC(0xFFFFD34A.toInt(), a), false, fontBold)
        for ((i, l) in lines.withIndex()) drawText(c, l, x0 + 12 * d, y0 + 22 * d + (i + 1) * lh - 4 * d, size, alphaC(0xFFF2EEE4.toInt(), a), false, fontBold)
    }

    private fun drawTutorial(c: Canvas) {
        if (hud[Native.DEAD] > 0.5f) return
        val d = density
        val dt = (time - tutLast).coerceIn(0f, 0.1f); tutLast = time
        if (moveStick.active && kotlin.math.hypot(moveStick.vx, moveStick.vy) > 0.3f) tutMoved = true
        if (atkStick.active) tutAttacked = true
        // rehber adımı → yapılacak iş (kısa, doğal cümle) ve gösterilecek kontrol
        val prog = if (guideGoal > 1) " ($guideN/$guideGoal)" else ""
        var ringAt: Triple<Float, Float, Float>? = null
        val msg: String? = when {
            // Nova'nın adımları kendiliğinden teslim edilir; yalnız Komutan Diana'nın adımlarında dönmek gerekir
            guideReady -> "Görev tamam! Komutan Diana'ya dön, ödülün hazır."
            guideStep == "g1" && !tutMoved -> { ringAt = Triple(moveStick.homeX, moveStick.homeY, stickR); "Sol tarafta parmağını kaydırarak yürü. Sonuna kadar itersen koşarsın; koşmak seni yormaz." }
            guideStep == "g1" && !tutAttacked -> { ringAt = Triple(atkStick.homeX, atkStick.homeY, stickR); "Sağdaki çemberi zombiye doğru çevir; o yöne vurursun." }
            guideStep == "g1" -> "Kamptan çık ve 3 zombi indir$prog. Vuruşlar dayanıklılık harcar, sarı çubuğa göz kulak ol."
            guideStep == "g2" -> { if (btnAbility.visible) ringAt = Triple(btnAbility.r.centerX(), btnAbility.r.centerY(), btnAbility.r.width() / 2); "Takla tuşuna bas: baktığın yöne yuvarlanırsın ve o an hasar almazsın$prog." }
            guideStep == "g3" -> "Yerde parlayan eşyaların üstünden geç, kendiliğinden çantana girer$prog."
            guideStep == "g4" -> "Çantayı aç, Üretim bölümünden Bandaj'ı seç ve üret."
            guideStep == "g4b" -> "Çanta → Üret → Silah Ve Alet sekmesinden Taş Balta'yı üret. Malzemeyi ben verdim."
            guideStep == "g4a" -> "Bir ağaca yürü ve vurmaya devam et: 500 canı var, balta her vuruşta 50 götürür."
            guideStep == "g5" -> "Çantada bir eşyaya basılı tut ve soldaki ceplerden birine sürükle."
            guideStep == "g6" -> "Gezgin Tüccar Marco'nun kamyonetini bul (haritada işaretli) ve onunla konuş."
            guideStep == "g6b" -> "Ateşin doğusundaki mor cüppeli Büyücü Aldric'le konuş."
            guideStep == "g9" && guideN == 0 -> "Askerleri indir, sonra ortadaki bayrak halkasında 10 saniye dur."
            guideStep == "g7" -> "5. seviyeye ulaş: Yaşlı Silas'ın görevlerini yap, ormanda avlan."
            else -> null
        }
        val key = "$guideStep|$tutMoved|$tutAttacked|$guideReady|$guideOffer|$guideN"
        // afiş üst ortadayken balon bekler (süresi de işlemez): aynı yerde iki yazı üst üste binmez
        if (key != tutKey) { tutKey = key; tutT = 0f } else if (bannerT <= 0f) tutT += dt
        ringAt?.let { (x, y, r) -> hintRing(c, x, y, r) }
        if (msg != null && bannerT <= 0f) guideBubble(c, msg)
        // görev hedefi: ekrandaysa yerde halka + küçük işaret, ekran dışındaysa kenarda yön göstergesi
        val tgt = questTargets.firstOrNull { it.guide } ?: questTargets.firstOrNull() ?: return
        val px = hud[Native.PX]; val py = hud[Native.PY]
        val gx = wx(tgt.x); val gy = wy(tgt.y)
        val m = 56 * d
        val dist = (kotlin.math.hypot(tgt.x - px, tgt.y - py) / 62.5f).toInt()
        val gold = 0xFFFFD34A.toInt()
        // göstergeler HUD köşelerine (portre, harita, joystickler) binmesin: güvenli iç dikdörtgen
        val sl = 150 * d; val sr = sw - 180 * d; val st = 118 * d; val sb = sh - 120 * d
        if (gx in m..(sw - m) && gy in m..(sh - m)) {
            groundRing(c, gx, gy, 46f * scale, gold)
            // hedef oyuncunun dibindeyken (12 m) yazı yok: oyuncu adıyla çakışmasın, yerdeki halka yeter
            if (dist > 12) {
                val top = gy - 130 * scale - (4 + sin(time * 4f) * 4) * d
                // hedefin üstü HUD'a denk geliyorsa yazı ve ok gösterilmez (yerdeki halka yeter)
                if (top - 22 * d > st && gx in sl..sr) {
                    chevron(c, gx, top, (PI / 2).toFloat(), 10 * d, gold, 0.95f)
                    pill(c, "${tgt.label} · $dist m", gx, top - 22 * d, 0xFFFFF0C0.toInt(), 0.9f)
                }
            }
        } else {
            // ekran dışındaki hedef: oyuncunun çevresinde dönen küçük ok ve mesafe (ad yalnız yakınken)
            val pxs = wx(px); val pys = wy(py) - 40 * scale
            val ang = atan2(gy - pys, gx - pxs)
            val rr = 92 * d
            val ex = pxs + kotlin.math.cos(ang) * rr; val ey = pys + kotlin.math.sin(ang) * rr
            val a = 0.65f + 0.35f * sin(time * 4f)
            chevron(c, ex, ey, ang, 11 * d, gold, a)
            val label = if (dist <= 60) "${tgt.label} · $dist m" else "$dist m"
            pill(c, label, ex - kotlin.math.cos(ang) * 24 * d, ey - kotlin.math.sin(ang) * 18 * d + 4 * d, 0xFFFFF0C0.toInt(), 0.75f)
        }
    }

    // ------------------------------------------------------------------ kontroller
    private fun drawControls(c: Canvas) {
        val s = Settings.current
        val op = s.joystickOpacity
        for (st in listOf(moveStick, atkStick)) {
            val isAtk = st === atkStick
            if (isAtk && inVehicleNow()) continue // araçta saldırı yok
            fill.shader = RadialGradient(st.cx, st.cy, stickR, intArrayOf(alphaC(0xFF000000.toInt(), 0.1f * op), alphaC(0xFF000000.toInt(), 0.35f * op)), null, Shader.TileMode.CLAMP)
            c.drawCircle(st.cx, st.cy, stickR, fill); fill.shader = null
            stroke.color = alphaC(if (isAtk) 0xFFFF7A5A.toInt() else 0xFFFFFFFF.toInt(), 0.55f * op); stroke.strokeWidth = 2.5f * density
            c.drawCircle(st.cx, st.cy, stickR, stroke)
            val kr = stickR * 0.45f
            fill.shader = RadialGradient(st.x - kr * 0.3f, st.y - kr * 0.3f, kr * 1.4f,
                alphaC(if (isAtk) 0xFFFF9A7A.toInt() else 0xFFF4F6F8.toInt(), 0.95f * op), alphaC(if (isAtk) 0xFFB03A28.toInt() else 0xFF8A96A4.toInt(), 0.9f * op), Shader.TileMode.CLAMP)
            c.drawCircle(st.x, st.y, kr, fill); fill.shader = null
            if (isAtk) {
                // saldırı topuzu: katana izi tuş görseli (yoksa vektör kılıç)
                val art = btnArt("attack")
                if (art != null) {
                    artPaint.alpha = (255 * op.coerceAtLeast(0.6f)).toInt(); artPaint.colorFilter = null
                    val ar = kr * 1.12f
                    artRect.set(st.x - ar, st.y - ar, st.x + ar, st.y + ar)
                    c.drawBitmap(art, null, artRect, artPaint)
                } else if (!st.active) drawIcon(c, "attack", st.x, st.y, kr * 0.9f, alphaC(0xFFFFFFFF.toInt(), op))
            }
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
        btnInteract.visible = !inVehicleNow() && (hud[Native.NEAR_MERCHANT] > 0 || hud[Native.NEAR_BENCH] > 0 || nearNpc >= 5 || nearChest >= 0)
        nearCache = Online.nearest(220f)
        val nr = nearCache
        btnTrade.visible = nr != null && nr.dist < 170f && hud[Native.IN_VEHICLE] < 0.5f && rideUid == null
        btnRide.visible = rideUid != null || (nr != null && nr.vehicle >= 2 && hud[Native.IN_VEHICLE] < 0.5f)
        btnReload.visible = hud[Native.AMMO] >= 0
        val cd = hud[Native.ABILITY_CD]
        if (cd > abilityCdMax || cd <= 0f) abilityCdMax = max(cd, 0.1f)
        // silah değiştirme tuşu yok (silah çantadan kuşanılır); araçtayken takla ve saldırı kullanılamaz → gizli
        btnWeapon.visible = false
        btnAbility.visible = !inVehicleNow()
        if (btnAbility.visible) roundBtn(c, btnAbility, "roll", if (cd > 0) "${cd.roundToInt()}" else null, 0xFF5A3A8A.toInt(), cd > 0)
        if (btnReload.visible) drawReloadBtn(c)
        if (btnVehicle.visible) roundBtn(c, btnVehicle, if (hud[Native.IN_VEHICLE] > 0) "exit" else "car", if (hud[Native.IN_VEHICLE] > 0) "Araçtan İn" else "Araca Bin", 0xFF2E8A5A.toInt(), false)
        if (btnTrade.visible) roundBtn(c, btnTrade, "trade", null, 0xFF2E7AA0.toInt(), false)
        if (btnRide.visible) roundBtn(c, btnRide, if (rideUid != null) "exit" else "car", if (rideUid != null) "İn" else "Yolcu Ol", 0xFF8A5AC0.toInt(), false)
        if (btnInteract.visible) {
            if (nearNpc >= 5 || hud[Native.NEAR_MERCHANT] > 0) roundBtn(c, btnInteract, "chat", "Konuş", 0xFFC08A20.toInt(), false)
            else if (nearChest >= 0 && hud[Native.NEAR_BENCH] <= 0) roundBtn(c, btnInteract, "bag", "Sandık", 0xFF8A6A3A.toInt(), false)
            else if (hud[Native.NEAR_BENCH] > 0) roundBtn(c, btnInteract, "craft", "Tezgâh", 0xFFC08A20.toInt(), false)
            else roundBtn(c, btnInteract, "craft", null, 0xFFC08A20.toInt(), false)
        }
    }

    /**
     * Şarjör tuşu: şarjör + dönen ok simgesi, altında şarjördeki mermi; şarjör boşken kırmızı nabız halkası,
     * değiştirilirken simgenin çevresinde dönen yay.
     */
    private fun drawReloadBtn(c: Canvas) {
        val b = btnReload
        val d = density
        val cx = b.r.centerX(); val cy = b.r.centerY(); val r = b.r.width() / 2 * (if (b.pressed >= 0) 0.92f else 1f)
        val reloading = hud[Native.RELOADING] > 0
        val mag = hud[Native.MAG].toInt(); val empty = mag <= 0 && !reloading
        fill.color = 0x59000000; c.drawCircle(cx, cy + 2 * d, r, fill)
        fill.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.5f, alphaC(0xFF4A5A6A.toInt(), 0.92f), alphaC(0xFF141A22.toInt(), 0.92f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null
        stroke.color = if (empty) 0xFFFF5A4A.toInt() else 0xFF8A949E.toInt(); stroke.strokeWidth = 3f * d
        c.drawCircle(cx, cy, r, stroke)
        if (empty) {
            val ph = (time * 1.6f) % 1f
            stroke.color = alphaC(0xFFFF5A4A.toInt(), 0.7f * (1f - ph)); stroke.strokeWidth = 2.5f * d
            c.drawCircle(cx, cy, r * (1f + 0.35f * ph), stroke)
        }
        // şarjör: ortada, hafif kavisli gövde, üstte pirinç mermi ucu
        val s = r * 0.62f
        c.save(); c.rotate(12f, cx, cy)
        fill.color = alphaC(0xFFE8ECEF.toInt(), if (reloading) 0.6f else 1f)
        rect.set(cx - s * 0.30f, cy - s * 0.42f, cx + s * 0.30f, cy + s * 0.62f); c.drawRoundRect(rect, s * 0.12f, s * 0.12f, fill)
        fill.color = 0xFF27303A.toInt()
        for (k in 0..2) { val ly = cy - s * 0.12f + k * s * 0.24f; c.drawRect(cx - s * 0.18f, ly, cx + s * 0.18f, ly + s * 0.07f, fill) }
        fill.color = alphaC(0xFFFFC860.toInt(), if (reloading) 0.6f else 1f)
        rect.set(cx - s * 0.13f, cy - s * 0.68f, cx + s * 0.13f, cy - s * 0.38f); c.drawRoundRect(rect, s * 0.13f, s * 0.13f, fill)
        c.restore()
        // şarjörü saran dönüş oku: değiştirilirken döner
        val ar = r * 0.74f
        c.save(); if (reloading) c.rotate(time * 360f, cx, cy)
        stroke.color = 0xFF7AF4FF.toInt(); stroke.strokeWidth = r * 0.11f; stroke.strokeCap = Paint.Cap.ROUND
        rect.set(cx - ar, cy - ar, cx + ar, cy + ar); c.drawArc(rect, -60f, 290f, false, stroke)
        val ea = Math.toRadians(-60.0)
        val ex = cx + cos(ea).toFloat() * ar; val ey = cy + sin(ea).toFloat() * ar
        val tx = -sin(ea).toFloat(); val ty = cos(ea).toFloat()   // yay yönünde teğet
        val nx = cos(ea).toFloat(); val ny = sin(ea).toFloat()
        val hs = r * 0.24f
        path.reset()
        path.moveTo(ex + tx * hs, ey + ty * hs)
        path.lineTo(ex - tx * hs * 0.4f + nx * hs * 0.8f, ey - ty * hs * 0.4f + ny * hs * 0.8f)
        path.lineTo(ex - tx * hs * 0.4f - nx * hs * 0.8f, ey - ty * hs * 0.4f - ny * hs * 0.8f); path.close()
        fill.color = 0xFF7AF4FF.toInt(); c.drawPath(path, fill)
        c.restore(); stroke.strokeCap = Paint.Cap.BUTT
        // şarjördeki mermi (tuşun altında, okunur boy)
        drawText(c, if (reloading) "…" else mag.toString(), cx, cy + r + 14 * d, 13 * d, if (empty) 0xFFFF7A6A.toInt() else 0xFFF2EEE4.toInt(), true, fontBold)
    }

    private fun roundBtn(c: Canvas, b: Btn, icon: String, label: String?, color: Int, dim: Boolean, small: Boolean = false) {
        val cx = b.r.centerX(); val cy = b.r.centerY(); val r = b.r.width() / 2 * (if (b.pressed >= 0) 0.92f else 1f)
        val op = if (small) 0.92f else Settings.current.joystickOpacity.coerceAtLeast(0.55f)
        fill.color = alphaC(0xFF000000.toInt(), 0.35f * op); c.drawCircle(cx, cy + 2 * density, r, fill)
        val art = btnArt(icon)
        if (art != null) {
            // piksel tuş görseli (camgöbeği halkalı metal); basılıyken küçülür, beklemedeyken kararır
            artPaint.alpha = (255 * op).toInt()
            artPaint.colorFilter = if (dim) dimFilter else null
            artRect.set(cx - r, cy - r, cx + r, cy + r)
            c.drawBitmap(art, null, artRect, artPaint)
            if (b.flash > 0f || b.pressed >= 0) {
                fill.color = alphaC(0xFF7AF4FF.toInt(), 0.18f + b.flash * 0.35f)
                c.drawCircle(cx, cy, r * 0.8f, fill)
            }
        } else {
            fill.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.5f, alphaC(lighten(color, 1.6f), op), alphaC(color, op), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, fill); fill.shader = null
            stroke.color = alphaC(0xFFFFFFFF.toInt(), 0.35f * op + b.flash * 0.5f); stroke.strokeWidth = 2 * density
            c.drawCircle(cx, cy, r, stroke)
            // V11 piksel simgesi varsa (takas…) onu, yoksa vektör simge
            val px = if (icon == "trade") com.zenvex.online.ui.UiArt.bitmap(context, "ic_trade") else null
            if (px != null) {
                artPaint.alpha = if (dim) 110 else 255; artPaint.colorFilter = null
                val hs = r * 0.62f
                artRect.set(cx - hs, cy - hs, cx + hs, cy + hs)
                c.drawBitmap(px, null, artRect, artPaint)
            } else drawIcon(c, icon, cx, cy - (if (label != null && !dim) r * 0.12f else 0f), r * 0.55f, alphaC(Color.WHITE, if (dim) 0.4f else 1f))
        }
        if (icon == "roll" && dim) {
            fill.color = 0x80000000.toInt()
            val cd = hud[Native.ABILITY_CD]
            c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * (cd / abilityCdMax).coerceIn(0f, 1f), true, fill)
        }
        if (label != null) {
            // görselli tuşta yazı tuşun altına (simgeyi kapatmasın); bekleme sayacı tuşun ortasında
            if (art != null && !dim) drawText(c, label, cx, cy + r + 13 * density, 12 * density, Color.WHITE, true)
            else drawText(c, label, cx, if (dim) cy + r * 0.2f else cy + r * 0.62f, r * (if (dim) 0.62f else 0.36f), Color.WHITE, true)
        }
    }

    // HUD tuş görselleri (assets/ui/btn_*.png); bulunamazsa vektör ikon çizilir
    private val artPaint = Paint().apply { isFilterBitmap = false }
    private val artRect = RectF()
    private val dimFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(0.45f, 0.45f, 0.45f, 1f) })
    /** Araçta (sürücü ya da yolcu) */
    private fun inVehicleNow() = hud[Native.IN_VEHICLE] > 0 || rideUid != null

    private fun btnArt(icon: String): Bitmap? = when (icon) {
        "roll" -> "btn_roll"; "weapon" -> "btn_swap"; "car" -> "btn_drive"; "exit" -> "btn_exit"
        "chat", "craft" -> "btn_hand"; "attack" -> "btn_attack"; else -> null
    }?.let { com.zenvex.online.ui.UiArt.bitmap(context, it) }

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
            "weapon" -> { // çapraz bıçak ve katana (silah değiştir)
                for (side in listOf(-1f, 1f)) {
                    c.save(); c.rotate(45f * side, cx, cy)
                    val long = if (side > 0) 0.95f else 0.6f
                    path.reset()
                    path.moveTo(cx - s * 0.09f, cy + s * 0.2f); path.lineTo(cx - s * 0.09f, cy - s * long + s * 0.15f)
                    path.lineTo(cx, cy - s * long); path.lineTo(cx + s * 0.09f, cy - s * long + s * 0.15f); path.lineTo(cx + s * 0.09f, cy + s * 0.2f); path.close()
                    c.drawPath(path, fill)
                    c.drawRect(cx - s * 0.25f, cy + s * 0.2f, cx + s * 0.25f, cy + s * 0.3f, fill)  // siper
                    c.drawRect(cx - s * 0.07f, cy + s * 0.3f, cx + s * 0.07f, cy + s * 0.7f, fill)  // kabza
                    c.restore()
                }
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
                val x = e.getX(i) * touchK; val y = e.getY(i) * touchK
                // inşa modu: dokunuşlar önizlemeyi taşır (joystick ve tuşlar beklemede)
                if (buildTouch(x, y, id)) return true
                // mini haritaya dokunma: tam harita
                if (radarR > 0 && hypot(x - radarCx, y - radarCy) < radarR) { Audio.play("click", 0.7f, bus = Audio.Bus.Ui); ui.onOpen("map"); return true }
                // başka oyuncuya dokunma: oyuncu menüsü
                synchronized(playerBoxes) { playerBoxes.firstOrNull { x in it.l..it.r && y in it.t..it.b } }?.let { pb ->
                    Audio.play("click", 0.7f, bus = Audio.Bus.Ui)
                    ui.onOpen("player:${pb.uid}|${pb.name}")
                    return true
                }
                // tezgâha dokunma: üretim (yakındaysa)
                benchBoxes.firstOrNull { x in it[0]..it[2] && y in (it[1] - 40 * density)..it[3] }?.let { b ->
                    if (b[4] < 330f) ui.onOpen("craft_bench") else ui.onToast("Tezgâha Yaklaş")
                    return true
                }
                // NPC'ye dokunma: konuşma (yakındaysa)
                npcBoxes.firstOrNull { x in it[1]..it[3] && y in it[2]..it[4] }?.let { b ->
                    if (b[5] < 300f) ui.onOpen("npc:${b[0].toInt() - 4}") else ui.onToast("Konuşmak İçin Yaklaş")
                    return true
                }
                for (b in buttons) {
                    if (!b.visible) continue
                    val r = b.r.width() / 2 * 1.2f
                    if (hypot(x - b.r.centerX(), y - b.r.centerY()) < r) { b.pressed = id; b.flash = 1f; press(b); return true }
                }
                val leftSide = x < sw / 2f
                val moveSide = if (s.leftHanded) !leftSide else leftSide
                val st = if (moveSide) moveStick else atkStick
                if (!st.active && y > sh * 0.28f && !(st === atkStick && inVehicleNow())) {
                    st.pointer = id
                    if (s.moveDynamic || st === atkStick) { st.cx = x; st.cy = y } else { st.cx = st.homeX; st.cy = st.homeY }
                    if (st === atkStick && !s.moveDynamic) { st.cx = st.homeX; st.cy = st.homeY }
                    moveStickTo(st, x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i)
                if (buildSlot >= 0 && buildPtr == id) moveBuild(e.getX(i) * touchK, e.getY(i) * touchK)
                if (moveStick.pointer == id) moveStickTo(moveStick, e.getX(i) * touchK, e.getY(i) * touchK)
                if (atkStick.pointer == id) moveStickTo(atkStick, e.getX(i) * touchK, e.getY(i) * touchK)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = e.getPointerId(e.actionIndex)
                if (buildPtr == id || e.actionMasked == MotionEvent.ACTION_CANCEL) buildPtr = -1
                for (st in listOf(moveStick, atkStick)) if (st.pointer == id || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    st.pointer = -1; st.vx = 0f; st.vy = 0f; st.cx = st.homeX; st.cy = st.homeY; st.x = st.cx; st.y = st.cy
                }
                for (b in buttons) if (b.pressed == id || e.actionMasked == MotionEvent.ACTION_CANCEL) b.pressed = -1
                if (e.actionMasked == MotionEvent.ACTION_UP) for (b in buttons) b.pressed = -1
            }
        }
        return true
    }

    // ------------------------------------------------------------------ V15 inşa modu (tezgâh kurma)
    /** kurulacak tezgâhın çanta yuvası (-1: inşa modu kapalı); önizleme parmağı izler, ✓ kurar, ✕ vazgeçer */
    @Volatile private var buildSlot = -1
        set(v) { field = v; building.value = v >= 0 }
    /** inşa modu açık mı (Compose HUD bu sırada gizlenir) */
    val building = androidx.compose.runtime.mutableStateOf(false)
    private var buildX = 0f
    private var buildY = 0f
    private var buildOk = false
    private var buildWhy = ""
    private var buildPtr = -1
    private val buildYes = RectF()
    private val buildNo = RectF()
    private val buildPaint = Paint().apply { alpha = 170; isFilterBitmap = false }

    fun startBuild(slot: Int) {
        buildX = hud[Native.PX] + 70f; buildY = hud[Native.PY] + 10f
        buildSlot = slot
        checkBuild()
    }

    private fun checkBuild() {
        val r = GameSession.cmdNoSave("bench_spot", "x" to buildX, "y" to buildY)
        buildOk = r.ok; buildWhy = r.msg
    }

    private fun moveBuild(x: Float, y: Float) {
        // önizleme parmağın biraz üstünde (parmak tezgâhı örtmesin)
        buildX = (x - sw / 2f) / scale + camX
        buildY = (y - 36 * density - sh / 2f) / scale + camY
        checkBuild()
    }

    /** true: dokunuş inşa modunda kullanıldı */
    private fun buildTouch(x: Float, y: Float, id: Int): Boolean {
        if (buildSlot < 0) return false
        when {
            buildYes.contains(x, y) -> {
                if (!buildOk) { ui.onToast(buildWhy.ifEmpty { "Buraya kurulamaz" }); return true }
                val r = GameSession.cmd("place_bench", "slot" to buildSlot, "x" to buildX, "y" to buildY)
                ui.onToast(r.msg)
                if (r.ok) { buildSlot = -1; Audio.play("craft"); fx("dust", buildX, buildY, 140f); shake(2f, 0.1f) }
            }
            buildNo.contains(x, y) -> { buildSlot = -1; Audio.play("click", 0.7f, bus = Audio.Bus.Ui) }
            else -> { buildPtr = id; moveBuild(x, y) }
        }
        return true
    }

    private fun drawBuild(c: Canvas) {
        if (buildSlot < 0) return
        val d = density
        val col = if (buildOk) 0xFF5BE38A.toInt() else 0xFFFF5A4A.toInt()
        // menzil halkası (6 karo)
        val rr = 6 * 64f * scale
        stroke.color = alphaC(0xFFFFFFFF.toInt(), 0.35f); stroke.strokeWidth = 2 * d
        c.drawCircle(wx(hud[Native.PX]), wy(hud[Native.PY]), rr, stroke)
        // yerde renkli halka + yarı saydam tezgâh
        val gx = wx(buildX); val gy = wy(buildY)
        val pulse = 0.75f + 0.25f * sin(time * 5f)
        fill.color = alphaC(col, 0.28f * pulse); c.drawOval(gx - 46 * d, gy - 16 * d, gx + 46 * d, gy + 16 * d, fill)
        stroke.color = alphaC(col, 0.9f); stroke.strokeWidth = 2.5f * d
        c.drawOval(gx - 46 * d, gy - 16 * d, gx + 46 * d, gy + 16 * d, stroke)
        atlas["s/workbench"]?.let { atlas.draw(c, it, gx, gy, scale, buildPaint) }
        // üst bilgi ve ✓ / ✕
        // bilgi ve tuşlar alt ortada (inşa modunda Compose HUD gizli; joystick'lerle çakışmaz)
        val bx = sw / 2f; val by = sh - 64 * d; val br = 28 * d
        fill.color = 0xC0141210.toInt()
        rect.set(bx - 130 * d, by - br - 46 * d, bx + 130 * d, by + br + 10 * d); c.drawRoundRect(rect, 8 * d, 8 * d, fill)
        drawText(c, if (buildOk) "Tezgâhı buraya kur?" else buildWhy.ifEmpty { "Buraya kurulamaz" }, bx, by - br - 24 * d, 14 * d, col, center = true)
        drawText(c, "Yeri seçmek için dokun ya da sürükle", bx, by - br - 8 * d, 10 * d, 0xFFE8E0C8.toInt(), center = true)
        buildYes.set(bx + 16 * d, by - br, bx + 16 * d + 2 * br, by + br)
        buildNo.set(bx - 16 * d - 2 * br, by - br, bx - 16 * d, by + br)
        for ((r, ok) in listOf(buildYes to true, buildNo to false)) {
            fill.color = if (ok) alphaC(if (buildOk) 0xFF2E8A4A.toInt() else 0xFF4A4A40.toInt(), 0.95f) else 0xF08A2A22.toInt()
            c.drawCircle(r.centerX(), r.centerY(), br, fill)
            stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = 2 * d; c.drawCircle(r.centerX(), r.centerY(), br, stroke)
            drawText(c, if (ok) "✓" else "✕", r.centerX(), r.centerY() + 9 * d, 26 * d, 0xFFFFFFFF.toInt(), center = true)
        }
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
            var mm = (((m - dz) / (1 - dz)) * sens).coerceAtMost(1f)
            if (st === moveStick) {
                // çekirdek 0,6 üstünde koşar; ayardaki eşik bu noktaya eşlenir (altı yürüme, üstü koşma)
                val rt = s.runThreshold.coerceIn(0.3f, 0.95f)
                mm = if (mm <= rt) mm * 0.58f / rt else 0.6f + (mm - rt) / (1f - rt) * 0.4f
            }
            val k = mm / m
            vx *= k; vy *= k
        }
        st.vx = vx; st.vy = vy
    }

    private fun press(b: Btn) {
        Audio.play("click", 0.6f, bus = Audio.Bus.Ui)
        vibrate(15, 90)
        when (b.id) {
            // bekleme süresi tuşun üstünde gösterilir; ayrıca bildirim çıkmaz
            "ability" -> { val r = GameSession.cmd("ability"); if (!r.ok && r.msg.isNotEmpty() && !r.msg.startsWith("Bekleme")) ui.onToast(r.msg) }
            "weapon" -> { val r = GameSession.cmd("cycle_weapon"); if (r.msg.isNotEmpty()) ui.onToast(r.msg) }
            "reload" -> GameSession.cmd("reload")
            "vehicle" -> {
                val wasIn = hud[Native.IN_VEHICLE] > 0.5f
                val r = GameSession.cmd("vehicle")
                if (r.ok) {
                    // binme / inme geçişi: kapı tozu, kısa titreşim, motor ya da kapı sesi
                    val px = hud[Native.PX]; val py = hud[Native.PY]
                    fx("dust", px, py, 160f); burst(px, py, 12, 0xFF9A8A70.toInt(), 180f, 4f)
                    vibrate(25, 120); shake(3f, 0.12f)
                    Audio.play(if (wasIn) "crate" else "engine", 0.7f, if (wasIn) 1.3f else 1.15f)
                }
                if (r.msg.isNotEmpty()) ui.onToast(r.msg)
            }
            "interact" -> when {
                nearNpc >= 5 -> ui.onOpen("npc:${nearNpc - 4}")
                hud[Native.NEAR_MERCHANT] > 0 -> ui.onOpen("npc:1")
                nearChest >= 0 && hud[Native.NEAR_BENCH] <= 0 -> ui.onOpen("bag")
                // V15 (R8): tezgâhın yanında tezgâh tarifleri açılır (önceden el üretimi açılıyordu)
                hud[Native.NEAR_BENCH] > 0 -> ui.onOpen("craft_bench")
                else -> ui.onOpen("craft")
            }
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

    /** Bölge (dokular) değişti: parçalar atılır, yarıda kalan arka plan çizimleri dönemle geçersizleşir */
    fun clearChunks() {
        groundEpoch++
        groundQ.clear()
        synchronized(chunks) { chunks.clear(); groundPending.clear() }
    }
}
