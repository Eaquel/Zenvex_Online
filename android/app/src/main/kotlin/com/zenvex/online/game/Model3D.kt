package com.zenvex.online.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.roundToInt

/**
 * 3B karakter, silah ve araç kareleri (rust/zenvex_r3d). Modeller assets/m3d'den bir kez yüklenir; kareler arka plan
 * iş parçacığında çizilip önbelleğe alınır. Oyun bir kare ister: önbellekte varsa hemen verilir, yoksa çizim kuyruğa
 * girer ve o aktörün son karesi gösterilir (bir kare gecikme görünmez).
 *
 * Önbellek anahtarı nicelenmiş durumdur (animasyon fazı, 32 yön, ölçek basamağı): döngüsel hareketler bir turdan sonra
 * yeniden çizilmez.
 */
object Model3D {
    /** oyundaki kahraman sırası (data.rs CHARACTERS) → 3B model */
    val CHARS = arrayOf("kael", "lyra", "vex", "morvath")

    /** assets/m3d altındaki tüm modeller (tools/model3d/build.py çıktısı) */
    val MODELS = arrayOf(
        "kael", "lyra", "vex", "morvath",
        "knife", "hatchet", "handaxe", "club", "effigy", "katana", "void_claw", "blood_scythe", "ice_scythe", "magma_hammer",
        "storm_axe", "pickaxe", "chainsaw", "pistol", "uzi", "ak47", "sniper", "rpg",
        "hellhound", "nightstalker",
        "npc_marco", "npc_silas", "npc_elena", "npc_viktor", "npc_jack", "npc_aldric", "npc_diana", "npc_soldier", "npc_sniper",
        "wings_ash", "wings_dragon", "wings_titan", "wings_s1",
    )

    /** NPC karakter kodu (çekirdek draw.rs: 4 kule askeri, 5 tüccar … 12 komutan) → 3B model */
    val NPCS = arrayOf("npc_soldier", "npc_marco", "npc_silas", "npc_elena", "npc_viktor", "npc_jack", "npc_soldier", "npc_aldric", "npc_diana")

    /** NPC animasyonları (zenvex_r3d anim::a) */
    const val A_TALK = 23
    const val A_AIM = 24
    const val A_SHOOT = 25

    /** silah eşyası → 3B model (listede yoksa "w_" sonrası ad) */
    private val ITEM_MODEL = mapOf(
        "w_bat" to "club", "w_baton" to "club", "w_glock" to "pistol", "w_m4" to "ak47",
        "ev_blood_weapon" to "blood_scythe", "ev_titan_weapon" to "magma_hammer", "ev_base_weapon" to "storm_axe",
        "ev_scrap_weapon" to "chainsaw", "ev_pack_weapon" to "void_claw", "ev_breath_weapon" to "ice_scythe",
        "t_axe_stone" to "hatchet", "t_axe_iron" to "handaxe", "t_pick_stone" to "pickaxe", "t_pick_iron" to "pickaxe",
    )

    /** yakın dövüş kombosundaki vuruş sayısı (zenvex_r3d anim::combo_len): tırpan ve tek elli silahlar 3, ağır silahlar 2 */
    fun comboLen(weapon: String): Int = when {
        weapon.endsWith("scythe") -> 3
        weapon in setOf("knife", "katana", "void_claw", "hatchet", "handaxe", "club", "effigy") -> 3
        else -> 2
    }

    class Frame(val bmp: Bitmap, val ax: Float, val ay: Float, val pxPerM: Float, val pts: FloatArray)

    @Volatile var ready = false
        private set
    private val names: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** yüklenmek üzere kuyruğa alınmış modeller */
    private val loading: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var appCtx: Context? = null

    private class Req(val key: String, val actor: String, val vehicle: Boolean, val a: String, val b: String, val p: FloatArray)

    private val queue = LinkedBlockingDeque<Req>()
    private val pending = HashSet<String>()
    /** önbellek bellek bütçesi (bayt): dolunca en uzun süredir kullanılmayan kare atılır */
    private var budget = 40L shl 20
    /** düşük bellekli cihaz: önbellek küçük, 1024'ten büyük model dokuları yarı boyutta yüklenir */
    private var lowRam = false
    private var bytes = 0L
    private val cache = LinkedHashMap<String, Frame>(512, 0.75f, true)
    /** aktör başına son kare (yeni kare hazır olana dek bu çizilir) */
    private val last = HashMap<String, Frame>()
    private var thread: Thread? = null

    /** son yükleme hatası (tanı) */
    @Volatile var lastError: Throwable? = null
        private set

    /** yüklenen modeller (tanı ve testler) */
    fun loaded(): Set<String> = names.toSet()

    /** Silah eşyasının 3B modeli ("" yumruk) */
    fun itemModel(item: String): String = when {
        item.isEmpty() -> ""
        else -> ITEM_MODEL[item] ?: item.removePrefix("w_")
    }

    fun init(ctx: Context) {
        if (thread != null) return
        val app = ctx.applicationContext
        // düşük bellekli cihazda daha küçük önbellek
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mc = am?.memoryClass ?: 256
        lowRam = am?.isLowRamDevice == true || mc < 192
        // bol bellekli cihazda büyük kare önbelleği: dönüşlerde ve yürüyüşte aynı kare tekrar tekrar çizilmez (işlemci ısınmaz)
        budget = when { lowRam -> 20L shl 20; mc >= 384 -> 72L shl 20; else -> 40L shl 20 }
        thread = Thread({ loop(app) }, "zx-3d").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1; start() }
    }

    /**
     * Açılışta yalnız kahramanlar ve NPC'ler yüklenir (her yerde görünürler); silahlar, kanatlar ve araçlar ilk kez
     * istendiklerinde arka planda yüklenir. Eskiden 37 modelin tamamı (dokularıyla ~100 MB yerel bellek) baştan
     * yükleniyordu; bir oturumda çoğu silah, kanat ve araç hiç görünmez (1 GB cihazlarda bellek baskısı ve geç açılış).
     */
    private fun loop(ctx: Context) {
        appCtx = ctx
        if (!loadModels(ctx, (CHARS + NPCS).distinct())) return
        while (true) {
            val r = try { queue.takeFirst() } catch (_: InterruptedException) { return }
            if (r.key.startsWith("load|")) { loadModels(ctx, listOf(r.a)); loading -= r.a; continue }
            // isteğin modelleri (kanatlı karakter "kael+wings_s1", silah ya da sürücü) yüklü değilse önce yükle
            for (n in r.a.split('+') + r.b) if (n.isNotEmpty() && n !in names && n in MODELS) { loadModels(ctx, listOf(n)); loading -= n }
            render(r)
        }
    }

    /** Model yüklü değilse arka planda yüklenmek üzere sıraya alır (çizim isteklerinden önce) */
    private fun ensure(name: String) {
        if (name.isEmpty() || name in names || name !in MODELS || sync) return
        if (loading.add(name)) queue.addFirst(Req("load|$name", "", false, name, "", FloatArray(0)))
    }

    private fun loadAll(ctx: Context): Boolean = loadModels(ctx, MODELS.toList())

    private fun loadModels(ctx: Context, list: List<String>): Boolean {
        try {
            val am = ctx.assets
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
            for (name in list) {
                if (name in names) continue
                val zxm = runCatching { am.open("m3d/$name.zxm").use { it.readBytes() } }.getOrNull() ?: continue
                if (lowRam) {
                    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    runCatching { am.open("m3d/$name.webp").use { BitmapFactory.decodeStream(it, null, b) } }
                    opts.inSampleSize = if (b.outWidth > 1024) 2 else 1
                }
                val tex = runCatching { am.open("m3d/$name.webp").use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
                val px = IntArray((tex?.width ?: 0) * (tex?.height ?: 0))
                tex?.getPixels(px, 0, tex.width, 0, 0, tex.width, tex.height)
                if (Native.r3dLoad(name, zxm, tex?.width ?: 0, tex?.height ?: 0, px) == 1) names += name
                tex?.recycle()
                // normal haritası (orijinal yüksek ayrıntılı yüzeyin kıvrımları); düşük bellekte yüklenmez
                if (!lowRam) {
                    opts.inSampleSize = 1
                    val nm = runCatching { am.open("m3d/${name}_n.webp").use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
                    if (nm != null) {
                        val np = IntArray(nm.width * nm.height)
                        nm.getPixels(np, 0, nm.width, 0, 0, nm.width, nm.height)
                        Native.r3dNormal(name, nm.width, nm.height, np)
                        nm.recycle()
                    }
                }
            }
            ready = true
            return true
        } catch (e: Throwable) {
            Log.w("zenvex", "3B modeller yüklenemedi", e)
            lastError = e
            return false
        }
    }

    private var out = IntArray(256 * 320)
    private val info = FloatArray(32)

    /** Bir isteği çiz ve önbelleğe koy (çizim iş parçacığında; testlerde çağıranın iş parçacığında) */
    private fun render(r: Req) {
        var n = if (r.vehicle) Native.r3dVehicle(r.a, r.b, r.p, out, info) else Native.r3dActor(r.a, r.b, r.p, out, info)
        if (n < 0) {
            out = IntArray(-n + 1024)
            n = if (r.vehicle) Native.r3dVehicle(r.a, r.b, r.p, out, info) else Native.r3dActor(r.a, r.b, r.p, out, info)
        }
        if (n > 0) {
            val w = info[0].toInt(); val h = info[1].toInt()
            val bmp = Bitmap.createBitmap(out, 0, w, w, h, Bitmap.Config.ARGB_8888)
            val scale = if (r.vehicle) r.p[2] else r.p[5]
            val fr = Frame(bmp, info[2], info[3], scale, info.copyOfRange(4, 4 + 16))
            synchronized(cache) {
                cache.put(r.key, fr)?.let { bytes -= it.bmp.byteCount }
                bytes += bmp.byteCount
                val it = cache.entries.iterator()
                while (bytes > budget && it.hasNext()) {
                    val e = it.next()
                    if (e.key == r.key) continue
                    bytes -= e.value.bmp.byteCount
                    it.remove()
                }
                if (r.actor.isNotEmpty()) last[r.actor] = fr
                pending -= r.key
            }
        } else synchronized(cache) { pending -= r.key }
    }

    /** Testler (emülatörsüz ekran görüntüsü): modelleri hemen yükle, kareleri istendiği anda çiz */
    @Volatile var sync = false
        private set

    @Synchronized fun initSync(ctx: Context) {
        sync = true
        if (!ready) loadAll(ctx.applicationContext)
    }

    private fun request(key: String, actor: String, vehicle: Boolean, a: String, b: String, p: FloatArray): Frame? {
        synchronized(cache) {
            if (last.size > 48) last.keys.retainAll { it == "me" }
            cache[key]?.let { last[actor] = it; return it }
            if (sync) {
                render(Req(key, actor, vehicle, a, b, p))
                return cache[key] ?: last[actor]
            }
            if (key !in pending) {
                pending += key
                // en yeni istek önce (eski, artık gösterilmeyecek istekler kuyrukta beklemesin)
                queue.addFirst(Req(key, actor, vehicle, a, b, p))
                trim()
            }
            return last[actor]
        }
    }

    /** Önceden çizim: döngünün sıradaki kareleri boşta kalan zamanda (kuyruğun sonuna) */
    private fun prefetch(key: String, vehicle: Boolean, a: String, b: String, p: FloatArray) {
        if (sync) return
        synchronized(cache) {
            if (key in cache || key in pending) return
            pending += key
            queue.addLast(Req(key, "", vehicle, a, b, p))
            trim()
        }
    }

    private fun trim() {
        // atılan bir yükleme isteği de unutulur (sonraki istekte yeniden sıraya girer)
        while (queue.size > 32) queue.pollLast()?.let { pending -= it.key; if (it.key.startsWith("load|")) loading -= it.a }
    }

    /** Aktör artık görünmüyor (uzak oyuncu ayrıldı): son karesini bırak */
    fun forget(actor: String) = synchronized(cache) { last.remove(actor) }

    /** Sistem belleği azaldı: kare önbelleği boşaltılır (görünen aktörlerin son karesi kalır, yeniden çizilir) */
    fun dropCache() = synchronized(cache) { cache.clear(); bytes = 0L }

    /**
     * Kenar yumuşatma çarpanı (3B Karakter Kalitesi): Düşük 1×, Orta 2×, Yüksek 3× (k×k çizilip süzülür).
     * Büyük vitrin çizimlerinde (> 200 px/m) 2× yeter: çizgiler zaten ince, 3× iç tampon gereksiz büyür.
     */
    fun aa(quality: Int, sc: Float = 0f): Int = if (sc > 200f) 2 else (quality + 1).coerceIn(1, 3)

    /** Yön (derece) 32 basamağa */
    fun yawBucket(deg: Float): Int = ((deg / 11.25f).roundToInt() % 32 + 32) % 32

    /** Ekran ölçeği (metre başına piksel) basamağa; çok büyük yakınlaştırmada üst sınır (hız) */
    fun scaleBucket(pxPerM: Float, quality: Int): Float {
        // 3: vitrin (karakter ekranı, lobi, portre) — kutu boyunda çizilir; eskiden 230'da kesilip büyütülüyordu (bulanık)
        val cap = when (quality) { 0 -> 90f; 1 -> 120f; 2 -> 160f; else -> 380f }
        return ((pxPerM.coerceIn(30f, cap) / 6f).roundToInt() * 6f)
    }

    /**
     * Animasyon saatini önbelleğe uygun nicele: döngülerde faz, tek seferlerde kare.
     * anim: çekirdek A_* (yakın dövüşte t döngü sayısıdır)
     */
    fun quantize(anim: Int, t: Float, ch: String, stages: Int = 2): Float {
        val (cycle, steps) = loopOf(anim, ch)
        if (cycle > 0f) {
            val ph = (t / cycle) - kotlin.math.floor(t / cycle)
            return ((ph * steps).roundToInt() % steps) * cycle / steps
        }
        return when (anim) {
            // yakın dövüş: saat döngü sayısı; sol/sağ sırası (çift/tek) + 16 basamak
            4 -> { val n = kotlin.math.floor(t); (n.toInt() % stages) + ((t - n) * 16).roundToInt().coerceAtMost(15) / 16f }
            // ateş / şarjör: saat faz (0..1)
            40 -> ((t * 8f).toInt().coerceAtMost(7)) / 8f
            41 -> ((t * 24f).toInt().coerceAtMost(23)) / 24f
            13, 10 -> (t * 30f).roundToInt() / 30f
            else -> (t * 15f).roundToInt() / 15f
        }
    }

    /** Döngüsel animasyonun süresi ve basamak sayısı (döngü değilse 0) */
    private fun loopOf(anim: Int, ch: String): Pair<Float, Int> {
        val heavy = if (ch == "vex") 0.15f else 0f
        return when (anim) {
            // boşta: nefes 0.32 Hz + kalça 0.16 Hz → 6.25 sn'de dikişsiz döner
            0, 5, 7 -> 6.25f to 60
            1, 6, 8 -> 1f / (1.12f * (1f - heavy)) to 16
            2 -> 1f / (1.45f * (1f - heavy)) to 14
            3 -> 12f / 14f to 12  // üç yumruk: sol düz, sağ çapraz, aperkat
            12 -> 0.698f to 10
            17, 18 -> 0.9f to 14
            A_TALK -> 3.125f to 30
            A_SHOOT -> 0.4f to 8
            else -> 0f to 0
        }
    }

    /**
     * Karakter karesi (actor: aktörün kimliği — yerel "me", uzak oyuncu kimliği). wings: sırttaki kanat modeli ("" yok;
     * yüklenmemişse kanatsız çizilir).
     */
    fun actor(actor: String, ch0: String, weapon: String, anim: Int, t: Float, yaw: Float, pxPerM: Float, quality: Int, flags: Int = 0,
              wings: String = ""): Frame? {
        if (!ready) return null
        if (ch0 !in names) { ensure(ch0); return null }
        if (wings.isNotEmpty() && wings !in names) ensure(wings)
        val ch = if (wings.isNotEmpty() && wings in names) "$ch0+$wings" else ch0
        // kombo uzunluğu zenvex_r3d anim::combo_len ile aynı olmalı (önbellek anahtarı vuruş sırasını buna göre sarar)
        val stages = comboLen(weapon)
        val tq = quantize(anim, t, ch0, stages)
        val yb = yawBucket(yaw)
        val sc = scaleBucket(pxPerM, quality)
        val ssaa = aa(quality, sc)
        fun key(tt: Float) = "a|$ch|$weapon|$anim|$tt|$yb|$sc|$ssaa|$flags"
        fun params(tt: Float) = floatArrayOf(anim.toFloat(), tt, 1f, yb * 11.25f, 22f, sc, ssaa.toFloat(), flags.toFloat())
        val fr = request(key(tq), actor, false, ch, weapon, params(tq))
        // döngüde sıradaki iki kare (yürürken/koşarken ilk turda takılma olmasın)
        val (cycle, steps) = loopOf(anim, ch0)
        if (cycle > 0f) for (i in 1..2) {
            val tn = quantize(anim, tq + i * cycle / steps, ch0, stages)
            prefetch(key(tn), false, ch, weapon, params(tn))
        }
        return fr
    }

    /**
     * Araç karesi. V20: dönüşte akıcılık için yön başına az kare: sarsıntı çizimde 2B kaydırmadır (eskiden anahtardaydı
     * ve her karede değiştiği için dönerken neredeyse hiç kare önbellekte olmuyor, eski yön gösterilip sıçrıyordu),
     * tekerlek 4 evre, eğim 3 basamak. [turn]: dönüş yönü (−1/0/1); o yöndeki komşu yönler önceden çizilir.
     */
    fun vehicle(actor: String, name: String, rider: String, yaw: Float, wheel: Float, @Suppress("UNUSED_PARAMETER") bounce: Float, tilt: Float,
                riderT: Float, pxPerM: Float, quality: Int, turn: Int = 0): Frame? {
        if (!ready) return null
        if (name !in names) { ensure(name); return null }
        val yb = yawBucket(yaw)
        val sc = scaleBucket(pxPerM, quality)
        val wq = ((wheel / 90f).roundToInt() % 4) * 90f
        val tq = (tilt / 3f).roundToInt().coerceIn(-1, 1) * 3f
        val ssaa = aa(quality, sc)
        fun key(b: Int) = "v|$name|$rider|$b|$sc|$wq|$tq|$ssaa"
        fun params(b: Int) = floatArrayOf(b * 11.25f, 22f, sc, wq, 0f, tq, 0f, ssaa.toFloat())
        val fr = request(key(yb), actor, true, name, rider, params(yb))
        // dönerken gidilen yöndeki iki yön, düz giderken iki yan yön önceden (önbellekte hazır olsun)
        val ahead = if (turn != 0) listOf(turn, turn * 2) else listOf(1, -1)
        for (d in ahead) { val b = ((yb + d) % 32 + 32) % 32; prefetch(key(b), true, name, rider, params(b)) }
        return fr
    }
}
