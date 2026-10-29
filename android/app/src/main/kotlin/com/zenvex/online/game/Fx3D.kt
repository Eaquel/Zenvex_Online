package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 3B silahların kodla çizilen efektleri: savurma izi (silah ucunun gerçek yolu, Catmull-Rom ile yumuşatılmış şerit),
 * silahın elementine göre sürekli efekt (magma koru, buz sisi ve kar, kan damlası, boşluk dumanı, yıldırım, testere
 * kıvılcımı, ruh alevi), namlu alevi + kovan, vuruş patlaması.
 *
 * Koordinatlar "ekran dünyası": X dünya x'i, Y = zemin y'si − yükseklik (dünya birimi). Kamera yalnız kaydığı için
 * çizimde wx/wy dönüşümü yeterli.
 */
class Fx3D {
    /** Wk sırası (rust/zenvex_r3d/src/anim.rs Wk::ALL) */
    companion object {
        const val NONE = 0; const val KNIFE = 1; const val HATCHET = 2; const val HANDAXE = 3; const val CLUB = 4
        const val EFFIGY = 5; const val KATANA = 6; const val CLAW = 7; const val BLOOD = 8; const val ICE = 9
        const val MAGMA = 10; const val STORM = 11; const val PICKAXE = 12; const val CHAINSAW = 13
        const val PISTOL = 14; const val UZI = 15; const val AK = 16; const val SNIPER = 17; const val RPG = 18

        /** savurma izinin rengi (dış) ve çekirdeği (iç, parlak) */
        fun trailColor(w: Int): Int = when (w) {
            KNIFE -> 0xFFDDEBFF.toInt()
            HATCHET, HANDAXE, PICKAXE -> 0xFFFFE6C2.toInt()
            CLUB -> 0xFFF0D8B0.toInt()
            EFFIGY -> 0xFF6CFF9C.toInt()
            KATANA -> 0xFFA8E6FF.toInt()
            CLAW -> 0xFFB15CFF.toInt()
            BLOOD -> 0xFFFF1F35.toInt()
            ICE -> 0xFF7FE6FF.toInt()
            MAGMA -> 0xFFFF6A12.toInt()
            STORM -> 0xFF6FB2FF.toInt()
            CHAINSAW -> 0xFFC8FF3A.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

        fun coreColor(w: Int): Int = when (w) {
            CLAW -> 0xFFEAD2FF.toInt()
            BLOOD -> 0xFFFFB0B8.toInt()
            ICE -> 0xFFF0FDFF.toInt()
            MAGMA -> 0xFFFFE27A.toInt()
            STORM -> 0xFFE8F4FF.toInt()
            CHAINSAW -> 0xFFF4FFC8.toInt()
            EFFIGY -> 0xFFDCFFE6.toInt()
            else -> 0xFFFFFFFF.toInt()
        }

        /** ağır silahların izi daha geniş ve uzun ömürlü */
        private fun trailLife(w: Int) = when (w) { MAGMA, STORM, BLOOD, ICE -> 0.22f; KNIFE -> 0.12f; else -> 0.17f }
    }

    // ------------------------------------------------------------------ savurma izi
    private class Sample(val tx: Float, val ty: Float, val bx: Float, val by: Float, val s: Float, var age: Float)
    private class Trail(var weapon: Int) {
        val pts = ArrayList<Sample>()
        var seen = 0f
    }

    private val trails = HashMap<String, Trail>()

    // ------------------------------------------------------------------ parçacıklar
    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float,
        val color: Int, val size: Float, val kind: Int, ground: Float, var rot: Float = 0f,
    ) {
        /** çıkış noktası: kırıntının yatay yayılması kaynağın boyuyla sınırlanır */
        val x0 = x
        /** V19: yere düşen kırıntılar tek bir çizgiye dizilmesin: her biri kaynağın derinliği içinde kendi noktasına iner */
        val ground = ground + if (K.debris(kind)) (kotlin.random.Random.nextFloat() - 0.5f) * size * 9f else 0f
    }

    /** efekt yoğunluğu (0..1): yeni parçacıklar buna göre seyreltilir */
    var density = 1f
    private inner class PList : ArrayList<P>(512) {
        override fun add(element: P): Boolean {
            if (density < 1f && rnd.nextFloat() > density) return false
            return super.add(element)
        }
    }
    private val ps = PList()
    /** yere düşen damlalardan doğan lekeler (her karede yeni liste ayrılmasın) */
    private val splatTmp = ArrayList<P>()
    private val rnd = Random(7)

    private object K {
        /** yere düşüp duran kırıntılar (yayılması kaynağın boyuyla sınırlı) */
        fun debris(k: Int) = k == CHUNK || k == SPLINTER || k == SHARD || k == SPARK
        const val GLOW = 0       // ışık noktası (toplamalı)
        const val EMBER = 1      // kor: yükselir, titrer, sarı→turuncu→kırmızı
        const val MIST = 2       // sis/duman: büyüyerek söner
        const val FLAKE = 3      // kar tanesi: yavaşça süzülür, döner
        const val DROP = 4       // kan damlası: düşer, yere değince leke
        const val SPLAT = 5      // yerde leke
        const val WISP = 6       // boşluk/ruh dumanı: kıvrılarak yükselir
        const val SPARK = 7      // kıvılcım çizgisi: hızla uçar, yerçekimi
        const val SHELL = 8      // kovan: döner, yere seker
        const val RING = 9       // genişleyen halka
        const val SHARD = 10     // buz kırığı
        const val CHUNK = 11     // kaya / kabuk parçası: döner, yere seker
        const val LEAF = 12      // yaprak: sallanarak düşer
        const val SPLINTER = 13  // kıymık: ince çizgi, yerçekimi
        const val BLAST = 14     // arka alev: sıcak gaz dili, hızla söner
        const val STAR = 15      // dört köşeli parıltı: titrer, yavaşça yükselir
        const val BUBBLE = 16    // zehir kabarcığı: yükselir, büyür, patlar
        const val FIRE = 17      // ateş topu parçası: beyaz-sarı → turuncu → kızıl → koyu duman
    }

    private class Arc(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var life: Float, val max: Float, val color: Int, val seed: Int)
    private val arcs = ArrayList<Arc>()

    private class Flash(val x: Float, val y: Float, val dx: Float, val dy: Float, val size: Float, var life: Float, val max: Float, val color: Int, val seed: Int)
    private val flashes = ArrayList<Flash>()

    private val emit = HashMap<String, Float>()

    // ------------------------------------------------------------------ çizim araçları
    private val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val norm = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val addLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val path = Path()
    private val rect = RectF()
    private val glowBmp: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x22FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(32f, 32f, 32f, p)
    }
    private val glowPaints = HashMap<Int, Paint>()
    private fun glowPaint(color: Int, alpha: Float): Paint {
        val p = glowPaints.getOrPut(color or 0xFF000000.toInt()) {
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                colorFilter = LightingColorFilter(color and 0xFFFFFF, 0)
            }
        }
        p.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return p
    }

    var enabled = true

    // doku çizimi (FxTex): renk çarpımlı boya önbelleği
    private val texPaints = HashMap<Long, Paint>()
    private fun tex(color: Int, additive: Boolean): Paint = texPaints.getOrPut((color.toLong() and 0xFFFFFFL) or (if (additive) 1L shl 40 else 0L)) {
        Paint().apply {
            isFilterBitmap = false; isAntiAlias = false
            colorFilter = LightingColorFilter(color or 0xFF000000.toInt(), 0)
            if (additive) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    private fun drawTex(c: Canvas, b: Bitmap, x: Float, y: Float, w: Float, rot: Float, p: Paint, a: Float, squash: Float = 1f, bottom: Boolean = false) {
        if (a <= 0.01f || w < 1f) return
        p.alpha = (a * 255f).toInt().coerceIn(0, 255)
        val h = w * b.height / b.width * squash
        c.save(); c.translate(x, y); if (rot != 0f) c.rotate(rot)
        if (bottom) rect.set(-w / 2f, -h, w / 2f, 0f) else rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        c.drawBitmap(b, null, rect, p)
        c.restore()
    }

    // ------------------------------------------------------------------ girişler
    /**
     * Bir aktörün bu karedeki silah durumu. tip/base: silah ucu ve gövdesi (ekran dünyası), ground: aktörün zemin y'si,
     * strength: savurma izi gücü (0 = savurmuyor), attacking: saldırı animasyonunda mı.
     */
    fun actor(id: String, weapon: Int, tipX: Float, tipY: Float, baseX: Float, baseY: Float, strength: Float, ground: Float,
              attacking: Boolean, dt: Float, unit: Float) {
        val tr = trails.getOrPut(id) { Trail(weapon) }
        tr.seen = 0f
        if (tr.weapon != weapon) { tr.weapon = weapon; tr.pts.clear() }
        if (weapon in 1..CHAINSAW && strength > 0.02f) {
            val last = tr.pts.lastOrNull()
            // aynı (önbellekten tekrar gelen) kareyi iki kez ekleme
            if (last == null || abs(last.tx - tipX) + abs(last.ty - tipY) > 0.5f) {
                tr.pts += Sample(tipX, tipY, baseX, baseY, strength, 0f)
                if (tr.pts.size > 14) tr.pts.removeAt(0)
            }
        }
        if (!enabled) return
        ambient(id, weapon, tipX, tipY, baseX, baseY, ground, attacking, strength, dt, unit)
    }

    /** Namlu alevi: (x, y) namlu ucu, (dx, dy) atış yönü (birim), weapon: Wk */
    fun muzzle(x: Float, y: Float, dx: Float, dy: Float, weapon: Int, unit: Float, ground: Float) {
        val big = weapon == SNIPER || weapon == RPG
        val size = unit * (if (big) 0.95f else if (weapon == PISTOL) 0.5f else 0.7f)
        flashes += Flash(x, y, dx, dy, size, 0.075f, 0.075f, if (weapon == RPG) 0xFFFF8A30.toInt() else 0xFFFFC860.toInt(), rnd.nextInt())
        if (!enabled) return
        // namlu dumanı
        repeat(if (big) 5 else 2) {
            ps += P(x + dx * unit * 0.1f, y + dy * unit * 0.1f, dx * unit * (0.4f + rnd.nextFloat() * 0.6f) + rnd.nextFloat() * 8f - 4f,
                dy * unit * 0.5f - unit * 0.25f, 0.5f + rnd.nextFloat() * 0.4f, 0.9f, 0xFF9A948C.toInt(), unit * (0.10f + rnd.nextFloat() * 0.08f), K.MIST, ground)
        }
        // kıvılcımlar
        repeat(if (big) 7 else 3) {
            val a = rnd.nextFloat() * 0.8f - 0.4f
            val c = cos(a); val s = sin(a)
            val vx = (dx * c - dy * s); val vy = (dx * s + dy * c)
            val sp = unit * (5f + rnd.nextFloat() * 6f)
            ps += P(x, y, vx * sp, vy * sp, 0.12f + rnd.nextFloat() * 0.1f, 0.22f, 0xFFFFD27A.toInt(), unit * 0.03f, K.SPARK, ground + unit * 0.5f)
        }
        // kovan: sağa-yukarı fırlar, döner, yere seker (roketatar ve tabancada küçük/yok)
        if (weapon != RPG) {
            val side = if (dx >= 0f) 1f else -1f
            ps += P(x - dx * unit * 0.35f, y - dy * unit * 0.35f, (-dy * 1.2f + side * 0.6f) * unit * 2.2f, -unit * (2.4f + rnd.nextFloat()),
                0.9f, 0.9f, 0xFFE0B04A.toInt(), unit * (if (weapon == SNIPER) 0.05f else 0.035f), K.SHELL, ground + rnd.nextFloat() * unit * 0.3f, rnd.nextFloat() * 6f)
        }
    }

    /** Vuruş anı: silah ucunda elemente göre patlama */
    fun impact(x: Float, y: Float, weapon: Int, unit: Float, ground: Float) {
        if (!enabled || weapon !in 1..CHAINSAW) return
        val col = trailColor(weapon)
        flashes += Flash(x, y, 0f, 0f, unit * 0.55f, 0.12f, 0.12f, col, rnd.nextInt())
        ps += P(x, y, 0f, 0f, 0.28f, 0.28f, col, unit * 0.65f, K.RING, ground)
        val n = when (weapon) { MAGMA, STORM -> 14; BLOOD, ICE, CLAW -> 11; else -> 6 }
        repeat(n) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val sp = unit * (2.5f + rnd.nextFloat() * 4.5f)
            val vx = cos(a) * sp; val vy = sin(a) * sp * 0.6f - unit * 1.5f
            when (weapon) {
                MAGMA -> ps += P(x, y, vx, vy, 0.5f + rnd.nextFloat() * 0.4f, 0.9f, col, unit * 0.05f, K.EMBER, ground)
                ICE -> ps += P(x, y, vx, vy, 0.45f + rnd.nextFloat() * 0.3f, 0.75f, 0xFFDDF8FF.toInt(), unit * 0.06f, K.SHARD, ground, rnd.nextFloat() * 6f)
                BLOOD -> ps += P(x, y, vx, vy, 0.9f, 0.9f, 0xFFB0101E.toInt(), unit * 0.045f, K.DROP, ground + rnd.nextFloat() * unit * 0.3f)
                CLAW, EFFIGY -> ps += P(x, y, vx * 0.5f, vy * 0.5f - unit, 0.6f, 0.6f, col, unit * 0.12f, K.WISP, ground, rnd.nextFloat() * 6f)
                else -> ps += P(x, y, vx, vy, 0.18f + rnd.nextFloat() * 0.12f, 0.3f, coreColor(weapon), unit * 0.03f, K.SPARK, ground)
            }
        }
        if (weapon == STORM) repeat(3) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            arcs += Arc(x, y, x + cos(a) * unit * 1.2f, y + sin(a) * unit * 0.8f, 0.14f, 0.14f, col, rnd.nextInt())
        }
    }

    // ------------------------------------------------------------------ savurma dalgaları (tırpan, katana, balyoz)
    /** kind: 0 yere yatık hilal, 1 yükselen hilal, 2 genişleyen halka, 3 dönen hilal (fırıldak) */
    private class Wave(val x: Float, val y: Float, val dx: Float, val dy: Float, val kind: Int, val color: Int, val core: Int,
                       val size: Float, var life: Float, val max: Float, val span: Float, val ground: Float)
    private val waves = ArrayList<Wave>()

    /** Vuruş anında silahın elementine göre hilal dalgası + parçacık. (x, y): oyuncu ayağı (dünya), (dx, dy) saldırı yönü */
    fun slash(x: Float, y: Float, dx: Float, dy: Float, weapon: Int, stage: Int, unit: Float) {
        if (!enabled) return
        val l = hypot(dx, dy).coerceAtLeast(1e-3f); val ux = dx / l; val uy = dy / l
        val hip = y - unit * 0.9f
        val col = trailColor(weapon); val core = coreColor(weapon)
        when (weapon) {
            BLOOD, ICE -> {
                when (stage % 3) {
                    0 -> waves += Wave(x, hip, ux, uy, 0, col, core, unit * 2.1f, 0.34f, 0.34f, 1.15f, y)
                    1 -> waves += Wave(x, hip, ux, uy, 1, col, core, unit * 2.0f, 0.34f, 0.34f, 1.05f, y)
                    else -> {
                        waves += Wave(x, y, 0f, 0f, 2, col, core, unit * 3.4f, 0.55f, 0.55f, 0f, y)
                        waves += Wave(x, hip, ux, uy, 3, col, core, unit * 1.9f, 0.5f, 0.5f, 0.9f, y)
                        waves += Wave(x, hip, -ux, -uy, 3, col, core, unit * 1.9f, 0.5f, 0.5f, 0.9f, y)
                    }
                }
                val n = if (stage % 3 == 2) 16 else 9
                repeat(n) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat()
                    val sp = unit * (2f + rnd.nextFloat() * 4f)
                    val px = x + ux * unit * 1.2f; val py = hip + uy * unit * 0.6f
                    if (weapon == BLOOD) ps += P(px, py, cos(a) * sp + ux * unit * 3f, sin(a) * sp * 0.6f - unit * 1.2f, 0.8f, 0.8f, 0xFFB0101E.toInt(), unit * 0.05f, K.DROP, y + rnd.nextFloat() * unit * 0.4f)
                    else ps += P(px, py, cos(a) * sp + ux * unit * 3f, sin(a) * sp * 0.6f - unit * 1.2f, 0.6f, 0.75f, 0xFFDDF8FF.toInt(), unit * 0.07f, K.SHARD, y, rnd.nextFloat() * 6f)
                }
            }
            // V18: her vuruş aşamasının izi hareketine uyar (üçüncü aşama bitirici)
            KATANA -> if (stage % 3 == 2) {
                // baş üstünden dik iniş: iki yay üst üste (çapraz parlama)
                waves += Wave(x, hip, ux, uy, 0, col, core, unit * 2.0f, 0.22f, 0.22f, 0.8f, y)
                waves += Wave(x, hip - unit * 0.5f, ux, uy, 1, col, core, unit * 1.7f, 0.2f, 0.2f, 0.8f, y)
            } else waves += Wave(x, hip, ux, uy, if (stage % 3 == 0) 0 else 1, col, core, unit * 2.3f, 0.22f, 0.22f, 0.8f, y)
            HATCHET, HANDAXE, CLUB, EFFIGY -> if (stage % 3 == 2) {
                // bitirici çakma: yerde şok halkası ve kırıntılar
                val cx = x + ux * unit * 1.2f; val cy = y + uy * unit * 0.7f
                waves += Wave(cx, cy, 0f, 0f, 2, col, core, unit * 1.8f, 0.34f, 0.34f, 0f, cy)
                repeat(5) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = unit * (2f + rnd.nextFloat() * 3f)
                    ps += P(cx, cy - unit * 0.2f, cos(a) * sp, sin(a) * sp * 0.5f - unit * (2.5f + rnd.nextFloat() * 1.5f), 0.9f, 0.9f, 0xFF5A4A40.toInt(), unit * 0.07f, K.CHUNK, cy, rnd.nextFloat() * 6f)
                }
            } else waves += Wave(x, hip, ux, uy, if (stage % 3 == 0) 1 else 0, col, core, unit * 1.2f, 0.16f, 0.16f, 0.9f, y)
            // V19: her savuruşa tek iz (bitiricide biraz daha uzun ve derin); eskiden bitirici iki iz bırakıyordu
            KNIFE -> waves += if (stage % 3 == 2) Wave(x + ux * unit * 0.2f, hip + uy * unit * 0.1f, ux, uy, 0, col, core, unit * 1.05f, 0.18f, 0.18f, 0.75f, y)
                else Wave(x, hip, ux, uy, if (stage % 3 == 0) 0 else 1, col, core, unit * 0.9f, 0.16f, 0.16f, 0.7f, y)
            CLAW -> repeat(3) { i -> waves += Wave(x, hip + (if (stage % 3 == 2) (1 - i) else (i - 1)) * unit * 0.22f, ux, uy, 0, col, core, unit * 1.5f, 0.24f, 0.24f, 0.5f, y) }
            STORM -> waves += Wave(x, hip, ux, uy, stage % 2, col, core, unit * 2.0f, 0.26f, 0.26f, 1.0f, y)
            MAGMA -> {
                // balyoz yere çarpar: şok halkası, kaya parçaları, kor
                val cx = x + ux * unit * 1.5f; val cy = y + uy * unit * 0.9f
                waves += Wave(cx, cy, 0f, 0f, 2, col, core, unit * 3.0f, 0.5f, 0.5f, 0f, cy)
                repeat(9) {
                    val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = unit * (2.5f + rnd.nextFloat() * 4f)
                    ps += P(cx, cy - unit * 0.3f, cos(a) * sp, sin(a) * sp * 0.5f - unit * (3f + rnd.nextFloat() * 2f), 1.1f, 1.1f, 0xFF4A3A34.toInt(), unit * 0.10f, K.CHUNK, cy, rnd.nextFloat() * 6f)
                }
            }
        }
    }

    /** Ağaca balta: kabuk parçaları, kıymıklar, düşen yapraklar, toz. (x, y): gövdenin vurulan noktası */
    fun chopHit(x: Float, y: Float, unit: Float, ground: Float, dirX: Float) {
        if (!enabled) return
        val side = if (dirX >= 0f) -1f else 1f
        repeat(7) {
            val sp = unit * (1.6f + rnd.nextFloat() * 3f)
            ps += P(x, y, side * sp * (0.4f + rnd.nextFloat()) + (rnd.nextFloat() - 0.5f) * 30f, -unit * (1.4f + rnd.nextFloat() * 2.2f), 0.9f + rnd.nextFloat() * 0.4f, 1.1f,
                if (rnd.nextFloat() < 0.5f) 0xFF7A5230.toInt() else 0xFF9A6C40.toInt(), unit * (0.07f + rnd.nextFloat() * 0.05f), K.CHUNK, ground, rnd.nextFloat() * 6f)
        }
        repeat(8) {
            val sp = unit * (3f + rnd.nextFloat() * 5f)
            ps += P(x, y, side * sp * (0.3f + rnd.nextFloat() * 0.8f), -unit * (1f + rnd.nextFloat() * 2.5f), 0.5f + rnd.nextFloat() * 0.3f, 0.8f, 0xFFD9B27A.toInt(), unit * 0.028f, K.SPLINTER, ground, rnd.nextFloat() * 6f)
        }
        repeat(3) {
            // yapraklar tacın altından (gövdenin ~2-3 m üstü) iner, gövdenin dibine yakın konar
            ps += P(x + (rnd.nextFloat() - 0.5f) * unit * 1.2f, y - unit * (2.0f + rnd.nextFloat() * 1.2f), (rnd.nextFloat() - 0.5f) * 16f, unit * 0.4f, 2.0f + rnd.nextFloat(), 2.6f,
                if (rnd.nextFloat() < 0.5f) 0xFF5C9A3A.toInt() else 0xFF8AB040.toInt(), unit * 0.07f, K.LEAF, ground, rnd.nextFloat() * 6f)
        }
        ps += P(x, y + unit * 0.1f, 0f, -unit * 0.2f, 0.5f, 0.5f, 0xFFB8A484.toInt(), unit * 0.16f, K.MIST, ground)
    }

    /**
     * Ağaç devrilince: yapraklar tacın yüksekliğinden (gövdenin 2-4 m üstü) sallanarak gövdenin çevresine iner ve
     * yerde kısa süre kalıp söner; toz bulutu gövdenin dibinde. Eski radyal yeşil patlama yaprakları etrafa saçıyordu.
     */
    fun treeFall(x: Float, y: Float, unit: Float) {
        if (!enabled) return
        repeat(14) {
            val ox = (rnd.nextFloat() - 0.5f) * unit * 2.2f
            ps += P(x + ox, y - unit * (2.2f + rnd.nextFloat() * 1.8f), (rnd.nextFloat() - 0.5f) * 20f, unit * 0.3f, 2.2f + rnd.nextFloat() * 0.8f, 3.0f,
                if (rnd.nextFloat() < 0.5f) 0xFF5C9A3A.toInt() else 0xFF8AB040.toInt(), unit * 0.07f, K.LEAF, y + ox * 0.15f, rnd.nextFloat() * 6f)
        }
        repeat(3) { k ->
            ps += P(x + (k - 1) * unit * 0.5f, y, (k - 1) * unit * 0.4f, -unit * 0.15f, 0.9f, 0.9f, 0xFFB8A484.toInt(), unit * 0.3f, K.MIST, y)
        }
    }

    /** Kayaya kazma: gri parçalar, parlak kıvılcımlar, toz ve çarpma ışığı */
    fun mineHit(x: Float, y: Float, unit: Float, ground: Float, dirX: Float) {
        if (!enabled) return
        val side = if (dirX >= 0f) -1f else 1f
        flashes += Flash(x, y, 0f, 0f, unit * 0.5f, 0.1f, 0.1f, 0xFFFFD080.toInt(), rnd.nextInt())
        repeat(8) {
            val sp = unit * (1.8f + rnd.nextFloat() * 3.4f)
            ps += P(x, y, side * sp * (0.2f + rnd.nextFloat()) + (rnd.nextFloat() - 0.5f) * 40f, -unit * (1.6f + rnd.nextFloat() * 2.6f), 1f, 1.2f,
                if (rnd.nextFloat() < 0.5f) 0xFF7E7A72.toInt() else 0xFF5C5852.toInt(), unit * (0.06f + rnd.nextFloat() * 0.06f), K.CHUNK, ground, rnd.nextFloat() * 6f)
        }
        repeat(13) {
            val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = unit * (3f + rnd.nextFloat() * 6f)
            ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.6f - unit * 1.2f, 0.22f + rnd.nextFloat() * 0.2f, 0.4f, if (rnd.nextFloat() < 0.4f) 0xFFFFF0B0.toInt() else 0xFFFFB84A.toInt(), unit * 0.03f, K.SPARK, ground)
        }
        repeat(2) { ps += P(x + (rnd.nextFloat() - 0.5f) * 20f, y, (rnd.nextFloat() - 0.5f) * 30f, -unit * 0.3f, 0.6f, 0.6f, 0xFF9A968E.toInt(), unit * 0.15f, K.MIST, ground) }
        ps += P(x, y, 0f, 0f, 0.2f, 0.2f, 0xFFFFC870.toInt(), unit * 0.4f, K.RING, ground)
    }

    /** Roketatar arka alevi: namlunun arkasından yayılan sıcak gaz konisi, duman ve kıvılcım. (dx, dy) atış yönü */
    fun backblast(x: Float, y: Float, dx: Float, dy: Float, unit: Float, ground: Float) {
        flashes += Flash(x, y, -dx, -dy, unit * 0.85f, 0.09f, 0.09f, 0xFFFFA040.toInt(), rnd.nextInt())
        if (!enabled) return
        repeat(7) {
            val a = (rnd.nextFloat() - 0.5f) * 0.9f
            val c = cos(a); val sn = sin(a)
            val bx = -dx * c + dy * sn; val by = -dy * c - dx * sn
            val sp = unit * (2.5f + rnd.nextFloat() * 3.5f)
            ps += P(x, y, bx * sp, by * sp * 0.7f, 0.22f + rnd.nextFloat() * 0.14f, 0.36f, 0xFFFFB050.toInt(), unit * (0.13f + rnd.nextFloat() * 0.1f), K.BLAST, ground)
        }
        repeat(6) {
            val a = (rnd.nextFloat() - 0.5f) * 1.1f
            val c = cos(a); val sn = sin(a)
            val bx = -dx * c + dy * sn; val by = -dy * c - dx * sn
            val sp = unit * (1.2f + rnd.nextFloat() * 2.4f)
            ps += P(x, y, bx * sp, by * sp * 0.5f - unit * 0.2f, 0.9f + rnd.nextFloat() * 0.6f, 1.5f, 0xFF8E8880.toInt(), unit * (0.13f + rnd.nextFloat() * 0.1f), K.MIST, ground)
        }
        repeat(8) {
            val a = (rnd.nextFloat() - 0.5f) * 1.4f
            val c = cos(a); val sn = sin(a)
            val bx = -dx * c + dy * sn; val by = -dy * c - dx * sn
            val sp = unit * (4f + rnd.nextFloat() * 6f)
            ps += P(x, y, bx * sp, by * sp * 0.6f, 0.15f + rnd.nextFloat() * 0.15f, 0.3f, 0xFFFFD27A.toInt(), unit * 0.026f, K.SPARK, ground + unit * 0.5f)
        }
    }

    /** Uçan roketin arkasındaki iz (her karede çağrılır): sıcak egzoz ve kalıcı duman */
    fun rocketTrail(x: Float, y: Float, dx: Float, dy: Float, unit: Float, ground: Float) {
        if (!enabled || ps.size > 470) return
        ps += P(x, y, -dx * unit * 0.8f + (rnd.nextFloat() - 0.5f) * 12f, -dy * unit * 0.8f + (rnd.nextFloat() - 0.5f) * 12f, 1.1f + rnd.nextFloat() * 0.5f, 1.5f, 0xFFA8A29A.toInt(), unit * (0.14f + rnd.nextFloat() * 0.08f), K.MIST, ground)
        if (rnd.nextFloat() < 0.8f) ps += P(x, y, -dx * unit * 3f, -dy * unit * 3f + (rnd.nextFloat() - 0.5f) * 30f, 0.12f, 0.2f, 0xFFFFC060.toInt(), unit * 0.03f, K.SPARK, ground + unit * 2f)
    }

    // ------------------------------------------------------------------ efekt kitaplığı (eski sprite efektlerinin kodla karşılığı)
    /** Süreli yayıcı: x, y dünya (follow ise oyuncuya göre), kind: fire, toxic, rage, breath */
    private class Emit(val kind: String, var x: Float, var y: Float, val size: Float, val rot: Float, var life: Float, val max: Float, val follow: Boolean, var acc: Float = 0f)
    private val emits = ArrayList<Emit>()
    var followX = 0f
    var followY = 0f

    private fun puffColor(a: Int, b: Int, k: Float): Int {
        val r = ((a shr 16 and 255) * (1 - k) + (b shr 16 and 255) * k).toInt()
        val g = ((a shr 8 and 255) * (1 - k) + (b shr 8 and 255) * k).toInt()
        val bl = ((a and 255) * (1 - k) + (b and 255) * k).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl
    }

    /**
     * Ada göre efekt üretir (GameView.fx çağrılarının kodla karşılığı). size: sprite çapı (dünya pikseli), rot: derece.
     * Dönüş: bilinen bir efekt muydu.
     */
    fun named(name: String, x: Float, y: Float, size: Float, rot: Float, dur: Float, follow: Boolean): Boolean {
        val g = y + size * 0.28f
        val u = size
        when (name) {
            "dust" -> dust(x, y, size, g)
            "smoke" -> repeat(6) { ps += P(x + (rnd.nextFloat() - 0.5f) * u * 0.4f, y, (rnd.nextFloat() - 0.5f) * 14f, -u * (0.25f + rnd.nextFloat() * 0.3f), 1.4f + rnd.nextFloat() * 1.2f, 2.4f, 0xFF4A4642.toInt(), u * (0.14f + rnd.nextFloat() * 0.1f), K.MIST, g) }
            "sparkle" -> repeat(6) { ps += P(x + (rnd.nextFloat() - 0.5f) * u * 0.7f, y + (rnd.nextFloat() - 0.5f) * u * 0.5f, (rnd.nextFloat() - 0.5f) * 20f, -u * 0.25f, 0.8f + rnd.nextFloat() * 0.7f, 1.5f, 0xFFFFE27A.toInt(), u * (0.05f + rnd.nextFloat() * 0.04f), K.STAR, g, rnd.nextFloat() * 6f) }
            "spark" -> {
                flashes += Flash(x, y, 0f, 0f, u * 0.5f, 0.07f, 0.07f, 0xFFFFE0A0.toInt(), rnd.nextInt())
                repeat(9) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = u * (2f + rnd.nextFloat() * 3.5f)
                    ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.7f - u, 0.14f + rnd.nextFloat() * 0.16f, 0.3f, if (rnd.nextFloat() < 0.4f) 0xFFFFFFFF.toInt() else 0xFFFFC060.toInt(), 62.5f * 0.03f, K.SPARK, g) }
            }
            "crit" -> {
                flashes += Flash(x, y, 0f, 0f, u * 0.9f, 0.14f, 0.14f, 0xFFFFD34A.toInt(), rnd.nextInt())
                repeat(12) { i -> val a = i * 2f * PI.toFloat() / 12f + rnd.nextFloat() * 0.2f; val sp = u * (4f + rnd.nextFloat() * 4f)
                    ps += P(x, y, cos(a) * sp, sin(a) * sp, 0.18f + rnd.nextFloat() * 0.1f, 0.28f, 0xFFFFD34A.toInt(), 62.5f * 0.028f, K.SPARK, 1e9f) }
                repeat(3) { ps += P(x + (rnd.nextFloat() - 0.5f) * u * 0.5f, y - rnd.nextFloat() * u * 0.3f, 0f, -u * 0.5f, 0.5f, 0.5f, 0xFFFFF0B0.toInt(), u * 0.12f, K.STAR, g, rnd.nextFloat() * 6f) }
            }
            "blood" -> {
                repeat(11) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = u * (1.5f + rnd.nextFloat() * 3.2f)
                    ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.6f - u * 1.4f, 0.9f, 0.9f, 0xFFA8101C.toInt(), 62.5f * (0.03f + rnd.nextFloat() * 0.02f), K.DROP, g + rnd.nextFloat() * u * 0.2f) }
                ps += P(x, g, 0f, 0f, 0.5f, 0.5f, 0xFF8A0C16.toInt(), u * 0.2f, K.MIST, g)
            }
            "frost" -> {
                ps += P(x, g, 0f, 0f, 0.4f, 0.4f, 0xFFB8F0FF.toInt(), u * 0.8f, K.RING, g)
                repeat(8) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = u * (1.5f + rnd.nextFloat() * 2.5f)
                    ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.6f - u, 0.6f, 0.7f, 0xFFDDF8FF.toInt(), u * 0.07f, K.SHARD, g, rnd.nextFloat() * 6f) }
                repeat(4) { ps += P(x + (rnd.nextFloat() - 0.5f) * u * 0.6f, y, 0f, -u * 0.2f, 0.8f, 0.8f, 0xFFE8FBFF.toInt(), u * 0.06f, K.STAR, g, rnd.nextFloat() * 6f) }
            }
            "shockwave" -> waves += Wave(x, g, 0f, 0f, 2, 0xFFFFE0B0.toInt(), 0xFFFFFFFF.toInt(), u * 0.5f, 0.5f, 0.5f, 0f, g)
            "slash" -> {
                val a = Math.toRadians(rot.toDouble()); waves += Wave(x - cos(a).toFloat() * u * 0.2f, y, cos(a).toFloat(), sin(a).toFloat(), 0, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), u * 0.8f, 0.16f, 0.16f, 0.8f, g)
            }
            "muzzle" -> {
                val a = Math.toRadians(rot.toDouble()); muzzle(x, y, cos(a).toFloat(), sin(a).toFloat(), AK, 62.5f, g)
            }
            "levelup" -> levelUp(x, y, u * 0.6f)
            "level_ring" -> {}
            "teleport" -> teleport(x, y, u)
            "bolt" -> bolt(x, y, u)
            "magma_claw" -> {
                val a = Math.toRadians(rot.toDouble()); val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
                repeat(3) { i -> waves += Wave(x, y + (i - 1) * u * 0.07f, dx, dy, 0, 0xFFFF6A12.toInt(), 0xFFFFE27A.toInt(), u * 0.7f, 0.26f, 0.26f, 0.55f, g) }
                repeat(10) { ps += P(x + dx * u * 0.3f, y + dy * u * 0.2f, dx * u * 2f + (rnd.nextFloat() - 0.5f) * 80f, (rnd.nextFloat() - 0.5f) * 80f - u * 0.5f, 0.5f + rnd.nextFloat() * 0.4f, 0.9f, 0xFFFF8A20.toInt(), u * 0.03f, K.EMBER, g) }
            }
            "magma_slam" -> {
                waves += Wave(x, g, 0f, 0f, 2, 0xFFFF6A12.toInt(), 0xFFFFE27A.toInt(), u * 0.5f, 0.7f, 0.7f, 0f, g)
                waves += Wave(x, g, 0f, 0f, 2, 0xFFFFB040.toInt(), 0xFFFFFFFF.toInt(), u * 0.32f, 0.5f, 0.5f, 0f, g)
                repeat(14) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = u * (1.2f + rnd.nextFloat() * 2.2f)
                    ps += P(x, g - u * 0.05f, cos(a) * sp, sin(a) * sp * 0.4f - u * (2f + rnd.nextFloat() * 2f), 1.2f, 1.2f, 0xFF3A2A24.toInt(), u * (0.03f + rnd.nextFloat() * 0.03f), K.CHUNK, g, rnd.nextFloat() * 6f) }
                repeat(24) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = u * (0.8f + rnd.nextFloat() * 2f)
                    ps += P(x, g, cos(a) * sp, sin(a) * sp * 0.4f - u * 1.5f, 0.8f + rnd.nextFloat() * 0.7f, 1.5f, 0xFFFF8A20.toInt(), u * 0.012f, K.EMBER, g) }
                repeat(10) { val a = it * 2f * PI.toFloat() / 10f; ps += P(x + cos(a) * u * 0.2f, g, cos(a) * u * 0.6f, sin(a) * u * 0.25f, 0.9f, 0.9f, 0xFFFF7A1A.toInt(), u * 0.06f, K.FIRE, g) }
            }
            "magma_breath" -> emits += Emit("breath", x, y, u, rot, if (dur > 0) dur else 0.9f, if (dur > 0) dur else 0.9f, false)
            "fire" -> emits += Emit("fire", x, y, u, rot, if (dur > 0) dur else 2.6f, if (dur > 0) dur else 2.6f, follow)
            "toxic" -> {
                emits += Emit("toxic", x, y, u, rot, if (dur > 0) dur else 5f, if (dur > 0) dur else 5f, follow)
                waves += Wave(x, g, 0f, 0f, 2, 0xFF7CFF30.toInt(), 0xFFD8FF90.toInt(), u * 0.5f, 0.6f, 0.6f, 0f, g)
            }
            "rage" -> emits += Emit("rage", x, y, u, rot, if (dur > 0) dur else 5f, if (dur > 0) dur else 5f, true)
            else -> return false
        }
        return true
    }

    private fun dust(x: Float, y: Float, u: Float, g: Float) {
        repeat(5) { ps += P(x + (rnd.nextFloat() - 0.5f) * u * 0.5f, y + u * 0.05f, (rnd.nextFloat() - 0.5f) * u * 0.8f, -u * (0.05f + rnd.nextFloat() * 0.15f), 0.7f + rnd.nextFloat() * 0.5f, 1.2f, 0xFFB8A484.toInt(), u * (0.10f + rnd.nextFloat() * 0.08f), K.MIST, g) }
        repeat(3) { ps += P(x, y, (rnd.nextFloat() - 0.5f) * u * 2f, -u * (0.6f + rnd.nextFloat()), 0.7f, 0.9f, 0xFF8E7C62.toInt(), u * 0.035f, K.CHUNK, g, rnd.nextFloat() * 6f) }
    }

    /** Patlama: ışık · gövde (ateş topu, halka, kıvılcım, enkaz) · iz (yanık leke, duman sütunu, yerde alev). r: patlama yarıçapı (dünya) */
    fun explode(x: Float, y: Float, r: Float, big: Boolean) {
        val g = y + r * 0.25f
        // ışık
        flashes += Flash(x, y - r * 0.2f, 0f, 0f, r * (if (big) 2.2f else 1.5f), 0.14f, 0.14f, 0xFFFFD890.toInt(), rnd.nextInt())
        if (!enabled) return
        // duman ve enkaz önce eklenir: ateş topu (toplamalı) üstlerine biner
        repeat(if (big) 14 else 9) { ps += P(x + (rnd.nextFloat() - 0.5f) * r * 0.8f, y - r * 0.1f, (rnd.nextFloat() - 0.5f) * 30f, -r * (0.5f + rnd.nextFloat() * 0.7f), 1.6f + rnd.nextFloat() * 1.8f, 3.4f, 0xFF3C3834.toInt(), r * (0.22f + rnd.nextFloat() * 0.14f), K.MIST, g) }
        repeat(if (big) 16 else 9) {
            val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = r * (1.2f + rnd.nextFloat() * 2f)
            ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.5f - r * (1.6f + rnd.nextFloat() * 1.4f), 1.2f, 1.2f, 0xFF3A322C.toInt(), r * (0.05f + rnd.nextFloat() * 0.05f), K.CHUNK, g, rnd.nextFloat() * 6f)
        }
        // gövde: ateş topu parçaları dışa doğru yayılır, yükselir ve soğur
        val n = if (big) 22 else 13
        repeat(n) {
            val a = rnd.nextFloat() * 2f * PI.toFloat(); val rr = rnd.nextFloat()
            val sp = r * (0.5f + rr * 1.6f)
            ps += P(x + cos(a) * r * 0.15f * rr, y - r * 0.1f + sin(a) * r * 0.08f * rr, cos(a) * sp, sin(a) * sp * 0.55f - r * 0.5f, 0.55f + rnd.nextFloat() * 0.45f, 1.0f, 0xFFFFE090.toInt(), r * (0.28f + rnd.nextFloat() * 0.22f), K.FIRE, g)
        }
        waves += Wave(x, g, 0f, 0f, 2, 0xFFFFC070.toInt(), 0xFFFFFFFF.toInt(), r * (if (big) 3.4f else 2.6f), 0.5f, 0.5f, 0f, g)
        repeat(if (big) 36 else 22) {
            val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = r * (2f + rnd.nextFloat() * 3f)
            ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.6f - r * 1.2f, 0.4f + rnd.nextFloat() * 0.5f, 0.9f, if (rnd.nextFloat() < 0.5f) 0xFFFFD060.toInt() else 0xFFFF7A20.toInt(), 62.5f * 0.02f, K.SPARK, g)
        }
        // iz: yanık leke (yere kalıcı) ve kısa süre yanan zemin
        ps += P(x, g, 0f, 0f, 9f, 9f, 0xFF16120E.toInt(), r * 0.55f, K.SPLAT, g)
        emits += Emit("fire", x, g - r * 0.1f, r * 0.5f, 0f, 1.6f, 1.6f, false)
    }

    /** Seviye atlama: yerden yükselen halka, ışık sütunu ve spiral yıldızlar. (x, y): oyuncu ayağı */
    fun levelUp(x: Float, y: Float, u: Float) {
        val g = y
        waves += Wave(x, g, 0f, 0f, 2, 0xFFFFD34A.toInt(), 0xFFFFFFFF.toInt(), u * 1.6f, 0.9f, 0.9f, 0f, g)
        waves += Wave(x, g, 0f, 0f, 2, 0xFFFFF0B0.toInt(), 0xFFFFFFFF.toInt(), u * 1.0f, 0.7f, 0.7f, 0f, g)
        waves += Wave(x, g, 0f, 0f, 4, 0xFFFFD34A.toInt(), 0xFFFFFFFF.toInt(), u * 0.7f, 1.0f, 1.0f, 0f, g)
        flashes += Flash(x, g - u * 0.6f, 0f, 0f, u * 1.4f, 0.16f, 0.16f, 0xFFFFE890.toInt(), rnd.nextInt())
        emits += Emit("levelup", x, g, u, 0f, 1.1f, 1.1f, true)
    }

    /** Işınlanma: mor rün halkası, ışık sütunu ve kıvılcımlar */
    fun teleport(x: Float, y: Float, u: Float) {
        val g = y + u * 0.3f
        waves += Wave(x, g, 0f, 0f, 2, 0xFFB070FF.toInt(), 0xFFF0E0FF.toInt(), u * 0.9f, 0.7f, 0.7f, 0f, g)
        waves += Wave(x, g, 0f, 0f, 2, 0xFFD8B0FF.toInt(), 0xFFFFFFFF.toInt(), u * 0.55f, 0.5f, 0.5f, 0f, g)
        waves += Wave(x, g, 0f, 0f, 4, 0xFFB070FF.toInt(), 0xFFFFFFFF.toInt(), u * 0.42f, 0.8f, 0.8f, 0f, g)
        flashes += Flash(x, y, 0f, 0f, u * 0.9f, 0.14f, 0.14f, 0xFFD8B0FF.toInt(), rnd.nextInt())
        repeat(16) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val rr = u * (0.15f + rnd.nextFloat() * 0.25f)
            ps += P(x + cos(a) * rr, g + sin(a) * rr * 0.4f, 0f, -u * (0.8f + rnd.nextFloat() * 1.4f), 0.8f + rnd.nextFloat() * 0.5f, 1.2f, if (rnd.nextFloat() < 0.5f) 0xFFD8B0FF.toInt() else 0xFFFFFFFF.toInt(), u * 0.045f, K.STAR, g, rnd.nextFloat() * 6f) }
    }

    /** Gökten yıldırım: çatallı iki çizgi, çarpma ışığı, halka ve kıvılcım. (x, y): düştüğü nokta */
    fun bolt(x: Float, y: Float, u: Float) {
        val top = y - u * 3.4f
        var px = x + (rnd.nextFloat() - 0.5f) * u * 0.8f; var py = top
        val segs = 6
        for (i in 1..segs) {
            val nx = if (i == segs) x else x + (rnd.nextFloat() - 0.5f) * u * 0.7f * (1f - i / segs.toFloat() * 0.8f)
            val ny = top + (y - top) * i / segs
            arcs += Arc(px, py, nx, ny, 0.32f, 0.32f, 0xFFB8D8FF.toInt(), rnd.nextInt())
            if (i == 3) arcs += Arc(nx, ny, nx + (rnd.nextFloat() - 0.5f) * u * 1.2f, ny + u * 0.9f, 0.16f, 0.16f, 0xFF8CC4FF.toInt(), rnd.nextInt())
            px = nx; py = ny
        }
        flashes += Flash(x, y, 0f, 0f, u * 1.6f, 0.2f, 0.2f, 0xFFD8EAFF.toInt(), rnd.nextInt())
        waves += Wave(x, y + u * 0.1f, 0f, 0f, 2, 0xFF8CC4FF.toInt(), 0xFFFFFFFF.toInt(), u * 0.9f, 0.4f, 0.4f, 0f, y + u * 0.1f)
        repeat(14) { val a = rnd.nextFloat() * 2f * PI.toFloat(); val sp = u * (2f + rnd.nextFloat() * 3.5f)
            ps += P(x, y, cos(a) * sp, sin(a) * sp * 0.5f - u, 0.25f + rnd.nextFloat() * 0.25f, 0.5f, 0xFFDDEEFF.toInt(), u * 0.03f, K.SPARK, y + u * 0.15f) }
    }

    private fun updateEmits(dt: Float) {
        val it = emits.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.life -= dt
            if (e.life <= 0f) { it.remove(); continue }
            if (e.follow) { e.x = followX; e.y = followY }
            val k = e.life / e.max
            val g = e.y + e.size * 0.28f
            when (e.kind) {
                "fire" -> {
                    e.acc += dt * 26f * min(1f, k * 2f)
                    while (e.acc >= 1f) { e.acc -= 1f
                        ps += P(e.x + (rnd.nextFloat() - 0.5f) * e.size * 0.7f, e.y + e.size * 0.1f, 0f, -e.size * (0.7f + rnd.nextFloat() * 0.9f), 0.45f + rnd.nextFloat() * 0.35f, 0.8f, 0xFFFF8A2A.toInt(), e.size * (0.12f + rnd.nextFloat() * 0.1f), K.WISP, g, rnd.nextFloat() * 6f)
                        if (rnd.nextFloat() < 0.15f) ps += P(e.x + (rnd.nextFloat() - 0.5f) * e.size * 0.5f, e.y, (rnd.nextFloat() - 0.5f) * 30f, -e.size * 1.2f, 0.7f, 0.9f, 0xFFFF9A30.toInt(), e.size * 0.018f, K.EMBER, g)
                        if (rnd.nextFloat() < 0.12f) ps += P(e.x, e.y - e.size * 0.3f, 0f, -e.size * 0.5f, 1.2f, 1.4f, 0xFF4A4440.toInt(), e.size * 0.16f, K.MIST, g)
                    }
                }
                "toxic" -> {
                    e.acc += dt * 12f * min(1f, k * 3f)
                    while (e.acc >= 1f) { e.acc -= 1f
                        val a = rnd.nextFloat() * 2f * PI.toFloat(); val rr = e.size * 0.42f * sqrt(rnd.nextFloat())
                        ps += P(e.x + cos(a) * rr, e.y + sin(a) * rr * 0.4f, 0f, -e.size * (0.12f + rnd.nextFloat() * 0.2f), 0.9f + rnd.nextFloat() * 0.8f, 1.5f, 0xFF9AFF40.toInt(), e.size * (0.03f + rnd.nextFloat() * 0.04f), K.BUBBLE, g, rnd.nextFloat() * 6f)
                        if (rnd.nextFloat() < 0.35f) ps += P(e.x + cos(a) * rr, e.y + sin(a) * rr * 0.4f, 0f, -e.size * 0.1f, 1.4f, 1.6f, 0xFF6CC030.toInt(), e.size * 0.2f, K.MIST, g)
                    }
                }
                "rage" -> {
                    e.acc += dt * 22f
                    while (e.acc >= 1f) { e.acc -= 1f
                        val a = rnd.nextFloat() * 2f * PI.toFloat(); val rr = e.size * 0.16f
                        ps += P(e.x + cos(a) * rr, e.y + sin(a) * rr * 0.4f + e.size * 0.12f, 0f, -e.size * (0.55f + rnd.nextFloat() * 0.5f), 0.5f + rnd.nextFloat() * 0.3f, 0.8f, 0xFFFF3A20.toInt(), e.size * (0.06f + rnd.nextFloat() * 0.04f), K.WISP, g, rnd.nextFloat() * 6f)
                    }
                    if ((e.max - e.life) % 0.9f < dt) waves += Wave(e.x, g, 0f, 0f, 2, 0xFFFF3A20.toInt(), 0xFFFFB090.toInt(), e.size * 0.5f, 0.45f, 0.45f, 0f, g)
                }
                "breath" -> {
                    val a = Math.toRadians(e.rot.toDouble()); val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
                    e.acc += dt * 70f
                    while (e.acc >= 1f) { e.acc -= 1f
                        val sp = e.size * (0.9f + rnd.nextFloat() * 1.3f); val spread = (rnd.nextFloat() - 0.5f) * 0.55f
                        val c2 = cos(spread); val s2 = sin(spread)
                        val vx = (dx * c2 - dy * s2) * sp; val vy = (dx * s2 + dy * c2) * sp * 0.6f
                        ps += P(e.x, e.y, vx, vy - e.size * 0.1f, 0.35f + rnd.nextFloat() * 0.25f, 0.6f, 0xFFFF8A2A.toInt(), e.size * (0.06f + rnd.nextFloat() * 0.06f), K.FIRE, g)
                        if (rnd.nextFloat() < 0.3f) ps += P(e.x, e.y, vx * 1.3f, vy * 1.3f - e.size * 0.2f, 0.5f, 0.6f, 0xFFFFD060.toInt(), e.size * 0.012f, K.EMBER, g)
                    }
                }
                "levelup" -> {
                    // spiral: yukarı akan iki kol yıldız
                    e.acc += dt * 40f
                    while (e.acc >= 1f) { e.acc -= 1f
                        val ph = (e.max - e.life) * 7f + (if (rnd.nextBoolean()) PI.toFloat() else 0f)
                        val rr = e.size * 0.7f * (0.5f + 0.5f * k)
                        ps += P(e.x + cos(ph) * rr, e.y - e.size * 0.1f + sin(ph) * rr * 0.4f, 0f, -e.size * (1.4f + rnd.nextFloat() * 0.8f), 0.6f + rnd.nextFloat() * 0.4f, 0.9f, if (rnd.nextFloat() < 0.5f) 0xFFFFE27A.toInt() else 0xFFFFFFFF.toInt(), e.size * (0.04f + rnd.nextFloat() * 0.035f), K.STAR, g, rnd.nextFloat() * 6f)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ sürekli element efektleri
    private fun ambient(id: String, w: Int, tx: Float, ty: Float, bx: Float, by: Float, ground: Float, attacking: Boolean,
                        strength: Float, dt: Float, unit: Float) {
        val rate = when (w) {
            MAGMA -> 22f; ICE -> 14f; BLOOD -> 4f; CLAW -> 12f; STORM -> 5f; EFFIGY -> 9f
            CHAINSAW -> if (attacking) 40f else 3f
            else -> 0f
        } * (1f + strength * 1.5f)
        if (rate <= 0f || ps.size > 480) return
        var acc = (emit[id] ?: 0f) + rate * dt
        while (acc >= 1f) {
            acc -= 1f
            // silah boyunca bir nokta (uca yakın ağırlıklı)
            val r = 0.45f + 0.55f * rnd.nextFloat()
            val x = bx + (tx - bx) * r; val y = by + (ty - by) * r
            when (w) {
                MAGMA -> {
                    ps += P(x, y, rnd.nextFloat() * 14f - 7f, -unit * (0.6f + rnd.nextFloat() * 0.9f), 0.6f + rnd.nextFloat() * 0.5f, 1.1f,
                        0xFFFF8A20.toInt(), unit * (0.025f + rnd.nextFloat() * 0.025f), K.EMBER, ground)
                    if (rnd.nextFloat() < 0.25f) ps += P(x, y, 0f, -unit * 0.4f, 0.9f, 0.9f, 0xFF3A2A22.toInt(), unit * 0.09f, K.MIST, ground)
                }
                ICE -> {
                    if (rnd.nextFloat() < 0.55f) ps += P(x, y, rnd.nextFloat() * 10f - 5f, unit * 0.25f, 1.2f, 1.2f, 0xFFEFFBFF.toInt(),
                        unit * (0.025f + rnd.nextFloat() * 0.02f), K.FLAKE, ground, rnd.nextFloat() * 6f)
                    else ps += P(x, y, rnd.nextFloat() * 8f - 4f, unit * 0.12f, 0.9f, 0.9f, 0xFFB8F0FF.toInt(), unit * 0.1f, K.MIST, ground)
                }
                BLOOD -> ps += P(tx + (bx - tx) * rnd.nextFloat() * 0.3f, ty, 0f, unit * 0.3f, 1.2f, 1.2f, 0xFF9A0A18.toInt(), unit * 0.03f, K.DROP, ground)
                CLAW -> ps += P(x, y, rnd.nextFloat() * 10f - 5f, -unit * 0.5f, 0.8f, 0.8f,
                    if (rnd.nextFloat() < 0.6f) 0xFF9C4CFF.toInt() else 0xFF2A0F45.toInt(), unit * 0.07f, K.WISP, ground, rnd.nextFloat() * 6f)
                EFFIGY -> ps += P(x, y, rnd.nextFloat() * 8f - 4f, -unit * 0.6f, 0.7f, 0.7f, 0xFF5CFF8E.toInt(), unit * 0.06f, K.WISP, ground, rnd.nextFloat() * 6f)
                STORM -> {
                    // bıçak boyunca titreyen kısa yıldırım ve mavi kıvılcım
                    val r2 = 0.4f + 0.6f * rnd.nextFloat()
                    arcs += Arc(x, y, bx + (tx - bx) * r2 + rnd.nextFloat() * unit * 0.3f - unit * 0.15f,
                        by + (ty - by) * r2 - rnd.nextFloat() * unit * 0.2f, 0.09f, 0.09f, 0xFF8CC4FF.toInt(), rnd.nextInt())
                    ps += P(x, y, rnd.nextFloat() * 40f - 20f, -rnd.nextFloat() * 40f, 0.15f, 0.15f, 0xFFDDEEFF.toInt(), unit * 0.02f, K.SPARK, ground)
                }
                CHAINSAW -> {
                    if (attacking) {
                        val a = rnd.nextFloat() * 1.4f - 0.7f
                        val dx = (tx - bx); val dy = (ty - by); val l = hypot(dx, dy).coerceAtLeast(1f)
                        val c = cos(a); val s = sin(a)
                        val vx = (dx * c - dy * s) / l; val vy = (dx * s + dy * c) / l
                        val sp = unit * (3f + rnd.nextFloat() * 4f)
                        ps += P(x, y, vx * sp, vy * sp - unit, 0.2f + rnd.nextFloat() * 0.15f, 0.35f, 0xFFFFE070.toInt(), unit * 0.025f, K.SPARK, ground)
                    } else ps += P(bx, by, rnd.nextFloat() * 6f - 3f, -unit * 0.5f, 0.8f, 0.8f, 0xFF6A6A66.toInt(), unit * 0.06f, K.MIST, ground)
                }
            }
        }
        emit[id] = acc
    }

    // ------------------------------------------------------------------ güncelleme
    fun update(dt: Float, unit: Float) {
        for (t in trails.values) {
            t.seen += dt
            val life = trailLife(t.weapon)
            for (s in t.pts) s.age += dt
            t.pts.removeAll { it.age > life }
        }
        trails.values.removeAll { it.seen > 2f && it.pts.isEmpty() }
        if (emit.size > 64) emit.clear()
        val g = unit * 9.8f
        val it = ps.iterator()
        val splats = splatTmp.apply { clear() }
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            when (p.kind) {
                K.EMBER -> { p.vx += (rnd.nextFloat() - 0.5f) * 60f * dt; p.vy -= unit * 0.6f * dt; p.vx *= 1f - 1.5f * dt }
                K.MIST -> { p.vx *= 1f - 2f * dt; p.vy *= 1f - 2f * dt }
                K.FLAKE -> { p.vx = sin(p.life * 4f + p.rot) * 10f; p.rot += dt * 3f }
                K.WISP -> { p.rot += dt * 5f; p.vx = cos(p.rot) * unit * 0.35f; p.vy *= 1f - 0.8f * dt }
                K.DROP, K.SPARK, K.SHELL, K.SHARD, K.CHUNK, K.SPLINTER -> {
                    p.vy += g * dt * (if (p.kind == K.SPARK || p.kind == K.SPLINTER) 0.6f else 1f)
                    if (p.kind == K.SHELL || p.kind == K.SHARD) p.rot += dt * 14f
                    if (p.kind == K.CHUNK) p.rot += dt * (6f + p.vx * 0.01f)
                }
                K.LEAF -> { p.vx = sin(p.life * 3.2f + p.rot) * unit * 0.9f; p.vy = unit * (0.9f + 0.3f * sin(p.life * 5f + p.rot)); p.rot += dt * 4f }
                K.BLAST -> { p.vx *= 1f - 4f * dt; p.vy *= 1f - 4f * dt }
                K.STAR -> { p.vy -= unit * 0.15f * dt; p.vx *= 1f - 1.2f * dt; p.vy *= 1f - 0.8f * dt; p.rot += dt * 2f }
                K.BUBBLE -> { p.vx = sin(p.life * 5f + p.rot) * unit * 0.25f; p.vy -= unit * 0.35f * dt }
                K.FIRE -> { p.vx *= 1f - 2.4f * dt; p.vy = p.vy * (1f - 2.2f * dt) - unit * 0.9f * dt }
            }
            p.x += p.vx * dt; p.y += p.vy * dt
            // kırıntı kaynağın genişliğinden (ağaç gövdesi, kaya, zombi ~1 m) öteye savrulmaz: sınıra gelince yatay hız kesilir
            if (K.debris(p.kind) && abs(p.x - p.x0) > max(p.size * 10f, unit * 0.75f)) p.vx = 0f
            if (p.y >= p.ground && p.vy > 0f) {
                when (p.kind) {
                    K.DROP -> { it.remove(); splats += P(p.x, p.ground, 0f, 0f, 2.5f, 2.5f, 0xFF5A0610.toInt(), p.size * 2.2f, K.SPLAT, p.ground) }
                    K.SHELL -> { p.y = p.ground; p.vy = -p.vy * 0.35f; p.vx *= 0.5f; if (abs(p.vy) < unit * 0.4f) { p.vy = 0f; p.vx = 0f } }
                    // yere değen kırıntı kaymaz: sekmesi kısa, yatay hızı çoğu kesilir
                    K.SPARK, K.SHARD, K.SPLINTER -> { p.y = p.ground; p.vy = -p.vy * 0.25f; p.vx *= 0.2f; if (abs(p.vy) < unit * 0.4f) { p.vy = 0f; p.vx = 0f } }
                    K.CHUNK -> { p.y = p.ground; p.vy = -p.vy * 0.38f; p.vx *= 0.3f; if (abs(p.vy) < unit * 0.5f) { p.vy = 0f; p.vx = 0f } }
                    K.LEAF -> { p.y = p.ground; p.vy = 0f; p.vx = 0f }
                }
            }
        }
        ps += splats
        updateEmits(dt)
        arcs.removeAll { it.life -= dt; it.life <= 0f }
        waves.removeAll { it.life -= dt; it.life <= 0f }
        flashes.removeAll { it.life -= dt; it.life <= 0f }
    }

    fun clear() { trails.clear(); ps.clear(); arcs.clear(); flashes.clear(); emit.clear(); waves.clear(); emits.clear() }

    // ------------------------------------------------------------------ çizim
    /** Zemindeki izler (kan lekesi): aktörlerden önce */
    fun drawGround(c: Canvas, camX: Float, camY: Float, scale: Float, sw: Float, sh: Float) {
        for (p in ps) {
            if (p.kind != K.SPLAT) continue
            val sx = (p.x - camX) * scale + sw / 2f; val sy = (p.y - camY) * scale + sh / 2f
            val k = (p.life / p.max).coerceIn(0f, 1f)
            val sd = ((p.x * 3.7f + p.y * 1.9f).toInt() and 0x7FFF) % 5
            drawTex(c, FxTex.splats[sd], sx, sy, p.size * scale * 3.6f, (sd * 70).toFloat(), tex(p.color, false), min(1f, k * 2f) * 0.9f, 0.55f)
        }
    }

    /** İzler, parçacıklar, yıldırımlar, namlu alevleri: aktörlerden sonra */
    fun draw(c: Canvas, camX: Float, camY: Float, scale: Float, sw: Float, sh: Float) {
        fun X(x: Float) = (x - camX) * scale + sw / 2f
        fun Y(y: Float) = (y - camY) * scale + sh / 2f
        for (t in trails.values) drawTrail(c, t, ::X, ::Y, scale)
        for (w in waves) drawWave(c, w, ::X, ::Y, scale)
        for (p in ps) {
            if (p.kind == K.SPLAT) continue
            val k = (p.life / p.max).coerceIn(0f, 1f)
            val sx = X(p.x); val sy = Y(p.y)
            if (sx < -50 || sy < -50 || sx > sw + 50 || sy > sh + 50) continue
            val r = p.size * scale
            when (p.kind) {
                K.GLOW -> c.drawBitmap(glowBmp, null, rect.apply { set(sx - r, sy - r, sx + r, sy + r) }, glowPaint(p.color, k))
                K.EMBER -> {
                    // titreyen kor: sarı → turuncu → koyu kırmızı, etrafında hale
                    if (sin(p.life * 41f + p.x) > -0.6f) {
                        val col = when { k > 0.6f -> 0xFFFFE27A.toInt(); k > 0.3f -> 0xFFFF8A20.toInt(); else -> 0xFFB8301A.toInt() }
                        c.drawBitmap(glowBmp, null, rect.apply { set(sx - r * 3, sy - r * 3, sx + r * 3, sy + r * 3) }, glowPaint(col, k * 0.55f))
                        norm.color = withAlpha(col, min(1f, k * 1.8f))
                        c.drawRect(sx - r * 0.6f, sy - r * 0.6f, sx + r * 0.6f, sy + r * 0.6f, norm)
                    }
                }
                K.MIST -> {
                    val sd = ((p.x * 7.3f + p.y * 3.1f).toInt() and 0x7FFF) % 8
                    drawTex(c, FxTex.puffs[sd], sx, sy, r * (3.4f - k * 1.2f), p.x * 9f + (1f - k) * 35f, tex(p.color, false), min(1f, (1f - k) * 6f) * k * 0.8f, 0.85f)
                }
                K.FLAKE -> {
                    line.color = withAlpha(p.color, min(1f, k * 2f)); line.strokeWidth = r * 0.35f
                    for (i in 0 until 3) {
                        val a = p.rot + i * PI.toFloat() / 3f
                        val ex = cos(a) * r; val ey = sin(a) * r
                        c.drawLine(sx - ex, sy - ey, sx + ex, sy + ey, line)
                    }
                }
                K.DROP -> {
                    norm.color = withAlpha(p.color, min(1f, k * 3f))
                    rect.set(sx - r * 0.7f, sy - r * 1.4f, sx + r * 0.7f, sy + r * 0.7f)
                    c.drawOval(rect, norm)
                }
                K.WISP -> {
                    // ruh/boşluk alevi: tabandan doğan, kıvrılan alev dili (toplamalı) + koyu duman izi
                    val fl = FxTex.flames[((p.x * 5.1f).toInt() and 0x7FFF) % 6]
                    val grow = sin(PI.toFloat() * (1f - k)).coerceAtLeast(0.05f)
                    drawTex(c, fl, sx + sin(p.rot) * r * 0.4f, sy, r * (1.2f + 1.5f * grow), 0f, tex(p.color, true), k * 0.9f, 1f, true)
                    drawTex(c, fl, sx + sin(p.rot) * r * 0.4f, sy, r * (0.7f + 0.9f * grow), 0f, tex(0xFFFFFFFF.toInt(), true), k * 0.6f, 0.9f, true)
                }
                K.SPARK -> {
                    addLine.color = withAlpha(p.color, min(1f, k * 2f)); addLine.strokeWidth = r * 1.2f
                    val l = 0.022f
                    c.drawLine(sx, sy, sx - p.vx * l * scale, sy - p.vy * l * scale, addLine)
                }
                K.SHELL -> {
                    c.save(); c.rotate(Math.toDegrees(p.rot.toDouble()).toFloat(), sx, sy)
                    norm.color = withAlpha(p.color, min(1f, k * 3f))
                    c.drawRect(sx - r, sy - r * 0.45f, sx + r, sy + r * 0.45f, norm)
                    c.restore()
                }
                K.SHARD -> {
                    c.save(); c.rotate(Math.toDegrees(p.rot.toDouble()).toFloat(), sx, sy)
                    path.reset(); path.moveTo(sx, sy - r); path.lineTo(sx + r * 0.45f, sy); path.lineTo(sx, sy + r); path.lineTo(sx - r * 0.45f, sy); path.close()
                    norm.color = withAlpha(p.color, min(1f, k * 2f)); c.drawPath(path, norm)
                    c.restore()
                }
                K.STAR -> {
                    val tw = 0.55f + 0.45f * sin(p.life * 18f + p.rot * 3f)
                    drawTex(c, FxTex.sparkle, sx, sy, r * 2.6f, 0f, tex(p.color, true), min(1f, k * 2.2f) * tw, 1f)
                }
                K.BUBBLE -> {
                    // kabarcık: ince halka + iç parıltı; ömrünün sonunda şişip patlar
                    val grow = 1f + (1f - k) * 0.5f
                    val rr = r * grow
                    norm.style = Paint.Style.STROKE; norm.strokeWidth = max(1.3f, rr * 0.16f); norm.color = withAlpha(p.color, min(1f, k * 3f) * 0.9f)
                    c.drawCircle(sx, sy, rr, norm)
                    norm.style = Paint.Style.FILL; norm.color = withAlpha(p.color, min(1f, k * 3f) * 0.18f); c.drawCircle(sx, sy, rr, norm)
                    norm.color = withAlpha(0xFFFFFFFF.toInt(), min(1f, k * 3f) * 0.8f); c.drawCircle(sx - rr * 0.35f, sy - rr * 0.35f, max(1f, rr * 0.18f), norm)
                }
                K.FIRE -> {
                    val t0 = 1f - k
                    val grow = 0.6f + 0.9f * sqrt(t0)
                    val col = when { k > 0.72f -> 0xFFFFF0B0.toInt(); k > 0.48f -> 0xFFFFB040.toInt(); k > 0.28f -> 0xFFE0541A.toInt(); else -> 0xFF4A2418.toInt() }
                    val sd = ((p.x * 2.7f + p.y * 1.3f).toInt() and 0x7FFF) % 8
                    val additive = k > 0.3f
                    drawTex(c, FxTex.puffs[sd], sx, sy, r * 2.6f * grow, p.x * 5f + t0 * 40f, tex(col, additive), if (additive) min(1f, k * 1.6f) else k * 2.2f, 0.9f)
                    if (k > 0.5f) drawTex(c, FxTex.puffs[(sd + 3) % 8], sx, sy, r * 1.5f * grow, -p.y * 4f, tex(0xFFFFFFFF.toInt(), true), (k - 0.5f) * 1.2f, 0.9f)
                }
                K.CHUNK -> {
                    val sd = ((p.x * 3.1f + p.y).toInt() and 0x7FFF) % 5
                    drawTex(c, FxTex.chunks[sd], sx, sy, r * 2.4f, Math.toDegrees(p.rot.toDouble()).toFloat(), tex(p.color, false), min(1f, k * 3f), 1f)
                }
                K.SPLINTER -> {
                    line.color = withAlpha(p.color, min(1f, k * 3f)); line.strokeWidth = max(1.2f, r * 0.9f)
                    val l = 0.03f
                    c.drawLine(sx, sy, sx - p.vx * l * scale, sy - p.vy * l * scale, line)
                }
                K.LEAF -> {
                    c.save(); c.rotate(Math.toDegrees(p.rot.toDouble()).toFloat(), sx, sy)
                    norm.color = withAlpha(p.color, min(1f, k * 2f))
                    path.reset(); path.moveTo(sx - r, sy); path.quadTo(sx, sy - r * 0.8f, sx + r, sy); path.quadTo(sx, sy + r * 0.8f, sx - r, sy); path.close()
                    c.drawPath(path, norm)
                    c.restore()
                }
                K.BLAST -> {
                    val fl = FxTex.flames[((p.x * 5.1f).toInt() and 0x7FFF) % 6]
                    val ang = Math.toDegrees(kotlin.math.atan2(p.vy.toDouble(), p.vx.toDouble())).toFloat() + 90f
                    drawTex(c, fl, sx, sy, r * (1.6f + 1.2f * (1f - k)), ang, tex(p.color, true), k * 0.9f, 1f, true)
                    drawTex(c, fl, sx, sy, r * (0.9f + 0.6f * (1f - k)), ang, tex(0xFFFFFFFF.toInt(), true), k * 0.7f, 0.9f, true)
                }
                K.RING -> {
                    // şok halkası: dithered kalın halka, genişledikçe incelip söner; yere yatık
                    drawTex(c, FxTex.ring, sx, sy, r * 2f * (1.4f - k) * 1.1f, 0f, tex(p.color, true), k * 0.9f, 0.55f)
                }
            }
        }
        for (a in arcs) drawArc(c, a, X(a.x1), Y(a.y1), X(a.x2), Y(a.y2), scale)
        for (f in flashes) drawFlash(c, f, X(f.x), Y(f.y), scale)
    }

    /** Hilal dalgası: iki bıçaklı dilimli hilal (geniş soluk + dar parlak) ve parlak dış kenar; halka türü doku halkasıdır */
    private fun drawWave(c: Canvas, w: Wave, X: (Float) -> Float, Y: (Float) -> Float, scale: Float) {
        val k = (w.life / w.max).coerceIn(0f, 1f)
        val age = 1f - k
        if (w.kind == 4) {
            // ışık sütunu: yerden yükselen dar gradyan şerit (alt parlak, üst söner), yanlarda sızan parıltı
            val bx = X(w.x); val by = Y(w.ground)
            val hh = w.size * scale * 5.5f * min(1f, age * 4f + 0.15f); val ww = w.size * scale * (0.8f - 0.45f * age)
            val a = if (age < 0.15f) age / 0.15f else k * k
            for ((wk, ak) in arrayOf(1.0f to 0.14f, 0.7f to 0.22f, 0.45f to 0.34f, 0.2f to 0.6f)) {
                add.shader = android.graphics.LinearGradient(0f, by - hh, 0f, by, intArrayOf(withAlpha(w.color, 0f), withAlpha(w.color, ak * a), withAlpha(w.core, ak * 1.4f * a)), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(bx - ww * wk, by - hh, bx + ww * wk, by, add)
            }
            add.shader = null
            return
        }
        if (w.kind == 2) {
            drawTex(c, FxTex.ring, X(w.x), Y(w.ground), w.size * scale * (0.5f + 1.4f * age) * 2f * 0.5f, 0f, tex(w.color, true), k * 0.85f, 0.55f)
            drawTex(c, FxTex.ring, X(w.x), Y(w.ground), w.size * scale * (0.35f + 1.0f * age) * 2f * 0.5f, 0f, tex(w.core, true), k * 0.5f, 0.55f)
            return
        }
        var dx = w.dx; var dy = w.dy
        if (w.kind == 3) {
            // dönen hilal: merkez sabit, yön hızla döner
            val a = kotlin.math.atan2(dy, dx) + age * 11f
            dx = cos(a); dy = sin(a)
        }
        val travel = if (w.kind == 3) 0f else w.size * (0.35f + 0.65f * age)
        val cx = w.x + dx * travel; val cy = w.y + dy * travel * 0.6f
        val R = w.size * (if (w.kind == 3) 0.6f else 0.55f + 0.45f * age)
        val thick = R * (0.46f - 0.26f * age)
        val span = w.span
        val n = 16
        val ox = FloatArray(n + 1); val oy = FloatArray(n + 1); val ix = FloatArray(n + 1); val iy = FloatArray(n + 1)
        val lift = if (w.kind == 1) -0.55f else 0f
        for (i in 0..n) {
            val t = i * 2f / n - 1f
            val a = t * span
            val u = R * cos(a); val v = R * sin(a)
            val th = thick * Math.pow((1f - t * t).toDouble(), 0.8).toFloat()
            val ui = (R - th) * cos(a); val vi = (R - th) * sin(a)
            // yere yatık: dünya düzleminde (y ekseni 0.55 ezik); yükselen: ekranda yukarı eğik
            fun mx(uu: Float, vv: Float) = X(cx + uu * dx - vv * dy)
            fun my(uu: Float, vv: Float) = Y(cy + (uu * dy + vv * dx) * 0.55f + lift * uu)
            ox[i] = mx(u, v); oy[i] = my(u, v); ix[i] = mx(ui, vi); iy[i] = my(ui, vi)
        }
        for (layer in 0..1) {
            path.reset(); path.moveTo(ox[0], oy[0])
            for (i in 1..n) path.lineTo(ox[i], oy[i])
            val shrink = if (layer == 0) 0f else 0.5f
            for (i in n downTo 0) path.lineTo(ix[i] + (ox[i] - ix[i]) * shrink, iy[i] + (oy[i] - iy[i]) * shrink)
            path.close()
            add.color = withAlpha(if (layer == 0) w.color else w.core, k * k * (if (layer == 0) 0.55f else 0.8f))
            c.drawPath(path, add)
        }
        addLine.color = withAlpha(w.core, k * 0.95f); addLine.strokeWidth = (1.4f + 2.2f * k) * scale * 0.9f
        path.reset(); path.moveTo(ox[0], oy[0]); for (i in 1..n) path.lineTo(ox[i], oy[i]); c.drawPath(path, addLine)
        // uçlarda parıltı
        for (e in intArrayOf(0, n)) {
            val gr = 9f * scale * k
            c.drawBitmap(glowBmp, null, rect.apply { set(ox[e] - gr, oy[e] - gr, ox[e] + gr, oy[e] + gr) }, glowPaint(w.core, k))
        }
    }

    /** Silah ucunun yolu: Catmull-Rom ile alt bölünmüş şerit; dışta renkli hale, uç kenarda parlak çekirdek */
    private fun drawTrail(c: Canvas, t: Trail, X: (Float) -> Float, Y: (Float) -> Float, scale: Float) {
        val n = t.pts.size
        if (n < 2) return
        val life = trailLife(t.weapon)
        val col = trailColor(t.weapon); val core = coreColor(t.weapon)
        val sub = 4
        // alt bölünmüş noktalar
        val m = (n - 1) * sub + 1
        val tx = FloatArray(m); val ty = FloatArray(m); val bx = FloatArray(m); val by = FloatArray(m); val al = FloatArray(m)
        var j = 0
        for (i in 0 until n - 1) {
            val p0 = t.pts[maxOf(0, i - 1)]; val p1 = t.pts[i]; val p2 = t.pts[i + 1]; val p3 = t.pts[minOf(n - 1, i + 2)]
            for (s in 0 until sub) {
                val u = s.toFloat() / sub
                tx[j] = X(cr(p0.tx, p1.tx, p2.tx, p3.tx, u)); ty[j] = Y(cr(p0.ty, p1.ty, p2.ty, p3.ty, u))
                bx[j] = X(cr(p0.bx, p1.bx, p2.bx, p3.bx, u)); by[j] = Y(cr(p0.by, p1.by, p2.by, p3.by, u))
                val age = p1.age + (p2.age - p1.age) * u
                val st = p1.s + (p2.s - p1.s) * u
                al[j] = (1f - age / life).coerceIn(0f, 1f).let { it * it } * st.coerceIn(0f, 1f)
                j++
            }
        }
        val last = t.pts[n - 1]
        tx[j] = X(last.tx); ty[j] = Y(last.ty); bx[j] = X(last.bx); by[j] = Y(last.by)
        al[j] = (1f - last.age / life).coerceIn(0f, 1f).let { it * it } * last.s.coerceIn(0f, 1f)
        // dış şerit: gövdeden uca, uca yakın daha opak (iki kat: geniş soluk + dar parlak)
        for (layer in 0..1) {
            val inner = if (layer == 0) 0.25f else 0.62f
            val paint = if (layer == 0) norm else add
            for (i in 0 until m - 1) {
                val a = (al[i] + al[i + 1]) * 0.5f
                if (a <= 0.01f) continue
                path.reset()
                path.moveTo(tx[i], ty[i]); path.lineTo(tx[i + 1], ty[i + 1])
                path.lineTo(bx[i + 1] + (tx[i + 1] - bx[i + 1]) * inner, by[i + 1] + (ty[i + 1] - by[i + 1]) * inner)
                path.lineTo(bx[i] + (tx[i] - bx[i]) * inner, by[i] + (ty[i] - by[i]) * inner)
                path.close()
                paint.color = withAlpha(col, a * (if (layer == 0) 0.30f else 0.55f))
                c.drawPath(path, paint)
            }
        }
        // uç kenar: parlak çekirdek çizgisi
        for (i in 0 until m - 1) {
            val a = (al[i] + al[i + 1]) * 0.5f
            if (a <= 0.01f) continue
            addLine.color = withAlpha(core, a * 0.9f); addLine.strokeWidth = (1.2f + 2.6f * a) * scale * 0.9f
            c.drawLine(tx[i], ty[i], tx[i + 1], ty[i + 1], addLine)
        }
        // en uçta parıltı
        if (al[m - 1] > 0.2f) {
            val r = 10f * scale * al[m - 1]
            c.drawBitmap(glowBmp, null, rect.apply { set(tx[m - 1] - r, ty[m - 1] - r, tx[m - 1] + r, ty[m - 1] + r) }, glowPaint(core, al[m - 1]))
        }
    }

    /** Kırık çizgi yıldırım: iki katman (geniş mavi hale + ince beyaz çekirdek), her karede yeniden titrer */
    private fun drawArc(c: Canvas, a: Arc, x1: Float, y1: Float, x2: Float, y2: Float, scale: Float) {
        val k = (a.life / a.max).coerceIn(0f, 1f)
        val r = Random(a.seed + (a.life * 60f).toInt())
        val segs = 7
        val dx = x2 - x1; val dy = y2 - y1
        val l = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / l; val ny = dx / l
        path.reset(); path.moveTo(x1, y1)
        for (i in 1 until segs) {
            val u = i.toFloat() / segs
            val off = (r.nextFloat() - 0.5f) * l * 0.35f * sin(u * PI.toFloat())
            path.lineTo(x1 + dx * u + nx * off, y1 + dy * u + ny * off)
        }
        path.lineTo(x2, y2)
        addLine.color = withAlpha(a.color, k * 0.5f); addLine.strokeWidth = 4.5f * scale * 0.6f
        c.drawPath(path, addLine)
        addLine.color = withAlpha(0xFFFFFFFF.toInt(), k); addLine.strokeWidth = 1.4f * scale * 0.6f
        c.drawPath(path, addLine)
        val g = 7f * scale
        c.drawBitmap(glowBmp, null, rect.apply { set(x2 - g, y2 - g, x2 + g, y2 + g) }, glowPaint(a.color, k * 0.7f))
    }

    /** Namlu alevi: atış yönünde uzun dil + yan dilcikler + sıcak hale (yön yoksa yuvarlak patlama) */
    private fun drawFlash(c: Canvas, f: Flash, sx: Float, sy: Float, scale: Float) {
        val k = (f.life / f.max).coerceIn(0f, 1f)
        val s = f.size * scale
        val halo = s * (1.2f + (1f - k) * 0.6f)
        c.drawBitmap(glowBmp, null, rect.apply { set(sx - halo, sy - halo, sx + halo, sy + halo) }, glowPaint(f.color, k))
        if (f.dx == 0f && f.dy == 0f) {
            c.drawBitmap(glowBmp, null, rect.apply { set(sx - s * 0.5f, sy - s * 0.5f, sx + s * 0.5f, sy + s * 0.5f) }, glowPaint(0xFFFFFFFF.toInt(), k))
            return
        }
        val r = Random(f.seed)
        val ang = Math.toDegrees(kotlin.math.atan2(f.dy, f.dx).toDouble()).toFloat()
        c.save(); c.translate(sx, sy); c.rotate(ang)
        fun tongue(len: Float, wid: Float, rot: Float, color: Int, alpha: Float) {
            c.save(); c.rotate(rot)
            path.reset(); path.moveTo(0f, -wid); path.quadTo(len * 0.45f, -wid * 0.9f, len, 0f); path.quadTo(len * 0.45f, wid * 0.9f, 0f, wid); path.close()
            add.color = withAlpha(color, alpha); c.drawPath(path, add)
            c.restore()
        }
        val L = s * (1.3f + r.nextFloat() * 0.5f)
        tongue(L, s * 0.24f, 0f, f.color, k)
        tongue(L * 0.65f, s * 0.12f, 0f, 0xFFFFFFFF.toInt(), k)
        tongue(L * 0.45f, s * 0.12f, 38f + r.nextFloat() * 12f, f.color, k * 0.8f)
        tongue(L * 0.45f, s * 0.12f, -38f - r.nextFloat() * 12f, f.color, k * 0.8f)
        c.restore()
    }

    private fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t; val t3 = t2 * t
        return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
    }

    private fun withAlpha(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((Color.alpha(c) * a.coerceIn(0f, 1f)).toInt() shl 24)
}
