package com.zenvex.online.game

import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

/** 3B modellerin hepsi yüklenir ve her karakter + araç gerçekten çizilir (libzenvex.so gerekir). */
class Model3DTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test fun allModelsRender() {
        Assume.assumeTrue("libzenvex.so yok", runCatching { Native.hashCode(); true }.getOrDefault(false))
        Model3D.initSync(paparazzi.context)
        val all = Model3D.loaded()
        println("3B modeller: ${all.sorted()}")
        Model3D.lastError?.printStackTrace(System.out)
        for (ch in Model3D.CHARS) {
            val fr = Model3D.actor("t_$ch", ch, "katana", 0, 0f, 30f, 90f, 1)
            println("$ch → ${fr?.bmp?.width}x${fr?.bmp?.height}")
            assertTrue("$ch çizilmedi", fr != null)
        }
        for (v in listOf("hellhound", "nightstalker")) {
            val fr = Model3D.vehicle("t_$v", v, if (v == "nightstalker") "kael" else "", 30f, 0f, 0f, 0f, 0f, 70f, 1)
            println("$v → ${fr?.bmp?.width}x${fr?.bmp?.height}")
            assertTrue("$v çizilmedi", fr != null)
        }
        assertTrue("eksik model: $all", all.size >= 24)
    }
}
