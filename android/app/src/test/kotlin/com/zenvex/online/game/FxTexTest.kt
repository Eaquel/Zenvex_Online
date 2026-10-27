package com.zenvex.online.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** Efekt dokularının kontrol sayfası (build/game-shots/fx_tex.png): toz, duman, alev, kıvılcım, parça, leke, halka. */
class FxTexTest {
    @get:Rule val paparazzi = Paparazzi()

    private fun paint(color: Int, add: Boolean = false) = Paint().apply {
        isFilterBitmap = false; isAntiAlias = false
        colorFilter = LightingColorFilter(color or 0xFF000000.toInt(), 0)
        if (add) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    @Test fun sheet() {
        val W = 1400; val H = 640
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF3A5A2C.toInt())
        val r = RectF()
        fun draw(b: Bitmap, x: Float, y: Float, s: Float, p: Paint) {
            r.set(x - s / 2, y - s * b.height / b.width / 2, x + s / 2, y + s * b.height / b.width / 2)
            c.drawBitmap(b, null, r, p)
        }
        // toz (kum, toprak, kar, kül) ve duman
        val dust = intArrayOf(0xFFD8B878.toInt(), 0xFF8A6A44.toInt(), 0xFFEAF2FF.toInt(), 0xFF4A4644.toInt(), 0xFF9AA2AA.toInt(), 0xFFC8C0B0.toInt())
        for (i in 0 until 8) draw(FxTex.puffs[i], 70f + i * 130f, 70f, 120f, paint(dust[i % dust.size]))
        // alevler: turuncu, mor, mavi (toplamalı)
        c.drawRect(0f, 150f, W.toFloat(), 330f, Paint().apply { color = 0xFF0A0810.toInt() })
        val fl = intArrayOf(0xFFFF8A20.toInt(), 0xFFB070FF.toInt(), 0xFF40C8FF.toInt(), 0xFFFFC850.toInt(), 0xFFFF4A6A.toInt(), 0xFF70FF9A.toInt())
        for (i in 0 until 6) { draw(FxTex.flames[i], 80f + i * 105f, 240f, 100f, paint(fl[i], true)); draw(FxTex.flames[i], 80f + i * 105f, 250f, 70f, paint(0xFFFFFFFF.toInt(), true)) }
        for (i in 0 until 5) draw(FxTex.sparkle, 760f + i * 60f, 190f, 14f * (i + 1), paint(0xFFFFE07A.toInt(), true))
        // kırık parçalar
        for (i in 0 until 5) draw(FxTex.chunks[i], 780f + i * 55f, 280f, 40f, paint(intArrayOf(0xFF8A8A80.toInt(), 0xFF7A5A3A.toInt(), 0xFF7A1A14.toInt(), 0xFF9AA2AA.toInt(), 0xFFB09060.toInt())[i]))
        // lekeler ve halkalar
        for (i in 0 until 5) draw(FxTex.splats[i], 90f + i * 150f, 450f, 130f, paint(0xFF7A1018.toInt()))
        for (i in 0 until 3) draw(FxTex.splats[i], 90f + i * 150f, 570f, 80f, paint(0xFF2A2A22.toInt()))
        draw(FxTex.ring, 960f, 460f, 200f, paint(0xFFFFE0A0.toInt(), true))
        draw(FxTex.ring, 1200f, 460f, 150f, paint(0xFFB070FF.toInt(), true))
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "fx_tex.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
