package com.zenvex.online.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/**
 * Canlı sahnenin GPU gölgelendiricileri (AGSL, Android 13+): gerçekçi gökyüzü (saçılma, hacimli bulut, ışık huzmeleri),
 * gürültüyle akan alev (kara cisim renk rampası), kıvrılarak yükselen duman ve sürüklenen sis. Daha eski cihazlarda ve
 * gölgelendirici derlenemezse sahne eski yol çizimine döner (bkz. [SceneShaders.create]).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SceneShaders private constructor() {
    private val sky = RuntimeShader(NOISE + SKY)
    private val fires = List(8) { RuntimeShader(NOISE + FIRE) }
    private val smokes = List(8) { RuntimeShader(NOISE + SMOKE) }
    private val fogs = List(4) { RuntimeShader(NOISE + FOG) }
    private val skyBrush = ShaderBrush(sky)
    private val fireBrushes = fires.map { ShaderBrush(it) }
    private val smokeBrushes = smokes.map { ShaderBrush(it) }
    private val fogBrushes = fogs.map { ShaderBrush(it) }

    fun sky(
        w: Float, h: Float, t: Float, horizon: Float, sunX: Float, sunY: Float, sunR: Float, breath: Float, flash: Float, rays: Boolean,
        top: Color, mid: Color, hor: Color, sun: Color,
    ): Brush {
        sky.setFloatUniform("iRes", w, h)
        sky.setFloatUniform("iTime", t)
        sky.setFloatUniform("horizon", horizon)
        sky.setFloatUniform("sun", sunX, sunY)
        sky.setFloatUniform("sunR", sunR)
        sky.setFloatUniform("breath", breath)
        sky.setFloatUniform("flash", flash)
        sky.setFloatUniform("rays", if (rays) 1f else 0f)
        sky.setColorUniform("cTop", top.toArgb())
        sky.setColorUniform("cMid", mid.toArgb())
        sky.setColorUniform("cHor", hor.toArgb())
        sky.setColorUniform("cSun", sun.toArgb())
        return skyBrush
    }

    /** alev: (x, y) taban ortası, hw yarı genişlik, hh yükseklik (piksel) */
    fun fire(i: Int, x: Float, y: Float, hw: Float, hh: Float, t: Float, seed: Float, tint: Color): Brush {
        val s = fires[i % fires.size]
        s.setFloatUniform("origin", x, y)
        s.setFloatUniform("dim", hw, hh)
        s.setFloatUniform("iTime", t)
        s.setFloatUniform("seed", seed)
        s.setColorUniform("tint", tint.toArgb())
        return fireBrushes[i % fires.size]
    }

    /** duman sütunu: (x, y) taban, u genişlik birimi, hh yükseklik, drift sağa kayma (u cinsinden) */
    fun smoke(i: Int, x: Float, y: Float, u: Float, hh: Float, drift: Float, t: Float, seed: Float, smoke: Color, glow: Color): Brush {
        val s = smokes[i % smokes.size]
        s.setFloatUniform("origin", x, y)
        s.setFloatUniform("dim", u, hh)
        s.setFloatUniform("drift", drift)
        s.setFloatUniform("iTime", t)
        s.setFloatUniform("seed", seed)
        s.setColorUniform("smokeCol", smoke.toArgb())
        s.setColorUniform("glowCol", glow.toArgb())
        return smokeBrushes[i % smokes.size]
    }

    /** yatay sis bandı: y0..y1 arası, strength yoğunluk */
    fun fog(i: Int, y0: Float, y1: Float, h: Float, t: Float, seed: Float, strength: Float, col: Color): Brush {
        val s = fogs[i % fogs.size]
        s.setFloatUniform("band", y0, y1)
        s.setFloatUniform("iH", h)
        s.setFloatUniform("iTime", t)
        s.setFloatUniform("seed", seed)
        s.setFloatUniform("strength", strength)
        s.setColorUniform("fogCol", col.toArgb())
        return fogBrushes[i % fogs.size]
    }

    companion object {
        private var failed = false
        private var shared: Any? = null

        /** Android 13+ ve derleme başarılıysa tek ortak örnek; aksi halde null (yol çizimine dönülür). */
        fun create(): SceneShaders? {
            if (failed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            (shared as? SceneShaders)?.let { return it }
            return runCatching { SceneShaders() }.onFailure { failed = true }.getOrNull()?.also { shared = it }
        }

        private const val NOISE = """
float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}
"""

        private const val SKY = """
uniform float2 iRes;
uniform float iTime;
uniform float horizon;
uniform float2 sun;
uniform float sunR;
uniform float breath;
uniform float flash;
uniform float rays;
layout(color) uniform half4 cTop;
layout(color) uniform half4 cMid;
layout(color) uniform half4 cHor;
layout(color) uniform half4 cSun;

half4 main(float2 fc) {
    float H = iRes.y;
    float y = fc.y / horizon;
    float3 top = float3(cTop.rgb);
    float3 mid = float3(cMid.rgb);
    float3 hor = float3(cHor.rgb);
    float3 sc = float3(cSun.rgb);
    // atmosfer: zenit koyu, ufka doğru sıcak saçılma
    float3 col = mix(top, mix(mid, hor, 0.08 * breath), smoothstep(0.0, 0.55, y));
    col = mix(col, mix(mid, hor, 0.55 + 0.1 * breath), smoothstep(0.45, 0.86, y));
    col = mix(col, mix(hor, sc, 0.25), smoothstep(0.82, 1.1, y));
    float2 d = (fc - sun) / H;
    float r = length(d);
    // Mie saçılması: güneşin etrafında geniş + dar hale
    col += sc * (exp(-r * 4.5) * 0.42 + exp(-r * 16.0) * 0.45);
    // ufuk bandı güneşin altında daha parlak
    col += sc * exp(-abs(fc.x - sun.x) / iRes.x * 2.6) * smoothstep(0.6, 1.0, y) * 0.22;
    // yıldızlar (yalnız üst gök)
    float2 sg = fc / (H * 0.011);
    float2 si = floor(sg);
    float sh = hash(si);
    if (sh > 0.968) {
        float2 sp = fract(sg) - 0.5 - (float2(hash(si + 3.1), hash(si + 7.7)) - 0.5) * 0.6;
        float tw = 0.45 + 0.55 * sin(iTime * (1.0 + sh * 3.0) + sh * 40.0);
        col += smoothstep(0.13, 0.0, length(sp)) * tw * (1.0 - smoothstep(0.08, 0.6, y)) * 0.75;
    }
    // bulutlar: alan bükümlü fbm, gün batımında yassı katmanlar; güneşe bakan kenarlar aydınlanır
    float2 p = fc / H;
    float2 cp = p * float2(1.25, 4.6) + float2(iTime * 0.008, 0.0);
    float2 warp = float2(fbm(cp * 0.6 + float2(iTime * 0.012, 0.0)), fbm(cp * 0.6 + float2(5.2, 1.3)));
    float2 q = cp + warp * 1.1;
    float n = fbm(q);
    float band = smoothstep(0.04, 0.32, y) * (1.0 - smoothstep(0.78, 1.0, y));
    float dens = smoothstep(0.40, 0.74, n) * band;
    float2 toSun = normalize(sun - fc + float2(0.001, 0.001)) * 0.05;
    float n2 = fbm(q + toSun * float2(1.25, 4.6));
    float lit = clamp((n - n2) * 5.0 + 0.45, 0.0, 1.0);
    float near = exp(-r * 2.0);
    float3 cDark = mix(top, mid, 0.55) * 0.72;
    float3 cLit = mix(mid, sc, 0.5 + 0.45 * near);
    float3 cc = mix(cDark, cLit, lit * (0.3 + 0.7 * near));
    cc += sc * pow(lit, 3.0) * near * 0.55;
    col = mix(col, cc, dens * 0.88);
    // ışık huzmeleri: bulut boşluklarından aşağı yelpaze
    if (rays > 0.5) {
        float ang = atan(d.y, d.x);
        float rr = fbm(float2(ang * 7.0, iTime * 0.04));
        rr = smoothstep(0.48, 0.78, rr) * exp(-r * 1.5) * smoothstep(-0.05, 0.12, d.y);
        col += sc * rr * 0.24 * (1.0 - dens * 0.7);
    }
    // güneş diski ince bulutların ardında
    float disc = 1.0 - smoothstep(sunR * 0.92, sunR, length(fc - sun));
    col = mix(col, mix(sc, float3(1.0, 0.98, 0.92), 0.4), disc * (1.0 - dens * 0.75));
    col += flash * 0.2 * (1.0 - y * 0.5);
    col += (hash(fc + iTime) - 0.5) * (2.0 / 255.0);
    return half4(half3(col), 1.0);
}
"""

        private const val FIRE = """
uniform float2 origin;
uniform float2 dim;
uniform float iTime;
uniform float seed;
layout(color) uniform half4 tint;

half4 main(float2 fc) {
    float2 q = float2((fc.x - origin.x) / dim.x, (origin.y - fc.y) / dim.y);
    if (q.y < -0.2 || q.y > 1.1 || abs(q.x) > 1.3) return half4(0.0);
    float y = max(q.y, 0.0);
    float n = fbm(float2(q.x * 2.0 + seed, q.y * 2.4 - iTime * 2.3));
    float n2 = noise(float2(q.x * 5.5 - seed, q.y * 6.5 - iTime * 4.6));
    float sway = (n - 0.5) * 0.85 * y + sin(iTime * 1.7 + seed + y * 3.0) * 0.07 * y;
    float w = mix(0.95, 0.10, pow(y, 0.75));
    float d = abs(q.x - sway) / w;
    float heat = (1.0 - d) * 1.35 - y * 0.9 + (n2 - 0.5) * 0.7 * y + (n - 0.5) * 0.55;
    // üst kısım ayrık dillere kopar
    heat -= smoothstep(0.35, 1.0, y) * (1.0 - n2) * 0.55;
    heat *= smoothstep(-0.2, 0.06, q.y);
    heat = clamp(heat, 0.0, 1.0);
    float3 tc = float3(tint.rgb);
    // kara cisim rampası: koyu kızıl → tema rengi → beyaza yakın sarı çekirdek
    float3 c = tc * tc * 0.75 * smoothstep(0.0, 0.25, heat);
    c = mix(c, tc, smoothstep(0.2, 0.55, heat));
    c = mix(c, mix(tc, float3(1.0, 0.96, 0.82), 0.85), smoothstep(0.55, 0.92, heat));
    float a = smoothstep(0.02, 0.3, heat);
    return half4(half3(c * a), a);
}
"""

        private const val SMOKE = """
uniform float2 origin;
uniform float2 dim;
uniform float drift;
uniform float iTime;
uniform float seed;
layout(color) uniform half4 smokeCol;
layout(color) uniform half4 glowCol;

half4 main(float2 fc) {
    float2 q = float2((fc.x - origin.x) / dim.x, (origin.y - fc.y) / dim.y);
    if (q.y < 0.0 || q.y > 1.0) return half4(0.0);
    float y = q.y;
    float cx = drift * pow(y, 1.5) + sin(y * 4.0 + iTime * 0.3 + seed) * 0.12 * y;
    float w = 0.22 + y * 1.1;
    float dx = (q.x - cx) / w;
    float prof = exp(-dx * dx * 2.4);
    float n = fbm(float2(q.x * 0.9 + seed - iTime * 0.03, y * 2.6 - iTime * 0.2));
    float n2 = fbm(float2(q.x * 2.2 - seed, y * 5.5 - iTime * 0.34) + n * 1.5);
    float dens = prof * smoothstep(0.28, 0.72, n * 0.55 + n2 * 0.55);
    dens *= smoothstep(0.0, 0.07, y) * (1.0 - smoothstep(0.5, 1.0, y));
    dens = clamp(dens * 1.6, 0.0, 0.92);
    float3 c = mix(float3(glowCol.rgb), float3(smokeCol.rgb), smoothstep(0.0, 0.28, y));
    c *= 0.7 + 0.6 * n2;
    return half4(half3(c * dens), dens);
}
"""

        private const val FOG = """
uniform float2 band;
uniform float iH;
uniform float iTime;
uniform float seed;
uniform float strength;
layout(color) uniform half4 fogCol;

half4 main(float2 fc) {
    float v = (fc.y - band.x) / (band.y - band.x);
    if (v < 0.0 || v > 1.0) return half4(0.0);
    float prof = smoothstep(0.0, 0.55, v) * (1.0 - smoothstep(0.7, 1.0, v));
    float2 p = fc / iH;
    float n = fbm(float2(p.x * 1.6 + iTime * 0.025 + seed, p.y * 7.0 + seed));
    float n2 = fbm(float2(p.x * 3.4 - iTime * 0.04, p.y * 11.0 - seed));
    float a = prof * clamp(0.25 + n * 0.9 + (n2 - 0.5) * 0.5, 0.0, 1.0) * strength;
    return half4(half3(float3(fogCol.rgb) * a), a);
}
"""
    }
}
