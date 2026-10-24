package com.zenvex.online.game

import android.content.Context
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.zenvex.online.ui.titleTr
import java.util.Locale

/**
 * Çok dil. Kaynak dil Türkçe ve anahtar metnin kendisidir (tools/i18n/extract.py toplar); her dilin
 * assets/i18n/<dil>.json dosyası "Türkçe metin" → "çeviri" eşlemesidir.
 *
 * Ekrana gelen metin önce birebir aranır; bulunamazsa değişkenli şablonlarla ("Seviye {0} Gerekli") eşleştirilir ve
 * yakalanan parçalar (ör. eşya adı) da ayrıca çevrilir. Böylece arayüz, Rust çekirdeği ve sunucudan gelen metinler
 * çağrı yerlerinde değişiklik gerekmeden çevrilir. Sonuçlar küçük bir önbellekte tutulur (oyun ekranı her kare çizer).
 */
object I18n {
    /** desteklenen diller: kod → kendi dilindeki adı */
    val LANGS = linkedMapOf(
        "tr" to "Türkçe", "en" to "English", "es" to "Español", "pt" to "Português (Brasil)", "ru" to "Русский",
        "de" to "Deutsch", "fr" to "Français", "it" to "Italiano", "id" to "Bahasa Indonesia", "ar" to "العربية",
    )

    /** Compose durumu: dil değişince ekrandaki tüm yazılar yeniden çizilir */
    var lang by androidx.compose.runtime.mutableStateOf("tr")
        private set
    /** Türkçe dışındaki dillerde "Her Kelime Büyük" dönüşümü yapılmaz (çeviriler doğru yazımla gelir) */
    val turkish get() = lang == "tr"
    val rtl get() = lang == "ar"

    private class Tpl(val re: Regex, val out: String, val anchor: String, val weight: Int)

    @Volatile private var exact: Map<String, String> = emptyMap()
    @Volatile private var templates: List<Tpl> = emptyList()
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4000
    }

    /** cihaz dili destekleniyorsa o, değilse İngilizce (Türkçe cihazda Türkçe) */
    fun deviceDefault(): String {
        val l = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (l in LANGS) l else "en"
    }

    fun load(ctx: Context, code: String) {
        val c = if (code in LANGS) code else "en"
        val map = if (c == "tr") emptyMap() else runCatching {
            ctx.assets.open("i18n/$c.json").bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }.getOrDefault(emptyMap())
        install(c, map)
    }

    /** testler ve yükleyici için: eşlemeyi doğrudan kurar */
    fun install(code: String, map: Map<String, String>) {
        val ex = HashMap<String, String>(map.size * 2)
        val tp = ArrayList<Tpl>()
        for ((k, v) in map) {
            if (v.isEmpty()) continue
            if (PH.containsMatchIn(k)) template(k, v)?.let { tp += it } else ex[k] = v
        }
        tp.sortByDescending { it.weight }
        synchronized(cache) { cache.clear() }
        exact = ex; templates = tp; lower = null; lang = code
    }

    fun parse(json: String): Map<String, String> {
        val o = JSONObject(json)
        val m = HashMap<String, String>(o.length() * 2)
        for (k in o.keys()) m[k] = o.optString(k)
        return m
    }

    private val PH = Regex("\\{(\\d+)\\}")

    private fun template(k: String, v: String): Tpl? {
        val parts = PH.split(k)
        val literal = parts.joinToString("")
        if (literal.count { it.isLetter() } < 2) return null
        val sb = StringBuilder("^")
        val m = PH.findAll(k).toList()
        for ((i, p) in parts.withIndex()) {
            sb.append(Regex.escape(p))
            if (i < m.size) sb.append("(.+?)")
        }
        sb.append("$")
        // çeviride {n} → n. yakalanan grup; sırası dile göre değişebilir
        val idx = m.map { it.groupValues[1].toInt() }
        var out = v
        // önce geçici işaretlere, sonra grup numaralarına çevir (yer değiştirmede karışmasın)
        for ((g, n) in idx.withIndex()) out = out.replace("{$n}", "\u0000$g\u0000")
        return Tpl(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), out, parts.maxBy { it.length }.trim(), literal.length)
    }

    /** arayüz yazısı: çeviri; Türkçede (ya da çevirisi yoksa) tek satır yazılar Her Kelime Büyük */
    fun show(s: String, single: Boolean): String {
        val r = t(s)
        return if (single && (lang == "tr" || r == s)) r.titleTr() else r
    }

    /** metni etkin dile çevirir; Türkçede ve bilinmeyen metinde aynen döner */
    fun t(s: String): String {
        if (lang == "tr" || s.isEmpty()) return s
        if (s.none { it.isLetter() }) return s
        synchronized(cache) { cache[s] }?.let { return it }
        val r = resolve(s, 0)
        synchronized(cache) { cache[s] = r }
        return r
    }

    private fun resolve(s: String, depth: Int): String {
        // baştaki/sondaki boşluk ve satır sonları korunur
        val a = s.indexOfFirst { !it.isWhitespace() }
        if (a < 0) return s
        val b = s.indexOfLast { !it.isWhitespace() }
        val core = s.substring(a, b + 1)
        val r = lookup(core, depth) ?: (if (core.contains('\n')) core.split('\n').joinToString("\n") { resolve(it, depth) } else null)
            ?: lookupLoose(core, depth) ?: core
        return s.substring(0, a) + r + s.substring(b + 1)
    }

    private fun lookup(core: String, depth: Int): String? {
        exact[core]?.let { return it }
        if (depth > 2) return null
        for (t in templates) {
            if (t.anchor.isNotEmpty() && !core.contains(t.anchor)) continue
            val m = t.re.matchEntire(core) ?: continue
            var out = t.out
            for (g in 1 until m.groupValues.size) {
                val part = m.groupValues[g]
                out = out.replace("\u0000${g - 1}\u0000", if (part.any { it.isLetter() }) resolve(part, depth + 1) else part)
            }
            return out
        }
        return null
    }

    /** "A · B" ve "A: B" gibi birleşik metinler parça parça çevrilir */
    private fun lookupLoose(core: String, depth: Int): String? {
        if (depth > 2) return null
        for (sep in SEPS) {
            if (!core.contains(sep)) continue
            val parts = core.split(sep)
            var any = false
            val out = parts.joinToString(sep) { p ->
                val r = resolve(p, depth + 1)
                if (r != p) any = true
                r
            }
            if (any) return out
        }
        // tek kelimenin büyük/küçük harf farkı (PT tek satırı Her Kelime Büyük yazar)
        exactCase(core)?.let { return it }
        return null
    }

    private val SEPS = listOf(" · ", " — ", " | ", ": ", " - ", ", ")

    @Volatile private var lower: Map<String, String>? = null
    private fun exactCase(core: String): String? {
        val l = lower ?: exact.entries.associate { it.key.lowercase(TR) to it.value }.also { lower = it }
        return l[core.lowercase(TR)]
    }

    private val TR = Locale.forLanguageTag("tr-TR")
}

/** kısaltma: "metin".tr() */
fun String.tr(): String = I18n.t(this)
