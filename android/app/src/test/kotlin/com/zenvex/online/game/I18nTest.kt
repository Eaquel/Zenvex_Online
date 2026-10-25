package com.zenvex.online.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nTest {
    private val dir = File("src/main/assets/i18n")
    private fun load(code: String) = I18n.parse(File(dir, "$code.json").readText())
    private val ph = Regex("\\{(\\d+)\\}")

    @Test fun everyLanguageCoversTheCatalogWithMatchingPlaceholders() {
        val catalog = org.json.JSONArray(File(dir, "catalog.json").readText()).let { a -> List(a.length()) { a.getString(it) } }
        for (code in I18n.LANGS.keys - "tr") {
            val m = load(code)
            val missing = catalog.filter { it !in m }
            assertTrue("$code eksik: ${missing.take(5)}", missing.isEmpty())
            for (k in catalog) {
                val a = ph.findAll(k).map { it.value }.sorted().toList()
                val b = ph.findAll(m.getValue(k)).map { it.value }.sorted().toList()
                assertEquals("$code yer tutucu: $k", a, b)
            }
        }
    }

    @Test fun translatesExactAndTemplatedStrings() {
        I18n.install("en", load("en"))
        assertEquals("Settings", I18n.t("Ayarlar"))
        assertEquals("Level 7 required", I18n.t("Seviye 7 gerekli"))
        assertEquals("You killed Bob!", I18n.t("Bob oyuncusunu öldürdün!"))
        assertEquals("  Settings\n", I18n.t("  Ayarlar\n"))
        assertEquals("bilinmeyen metin xyz", I18n.t("bilinmeyen metin xyz"))
        I18n.install("tr", emptyMap())
        assertEquals("Ayarlar", I18n.t("Ayarlar"))
    }

    @Test fun arabicIsRtlAndOthersAreNot() {
        I18n.install("ar", load("ar"))
        assertTrue(I18n.rtl)
        assertNotEquals("Ayarlar", I18n.t("Ayarlar"))
        I18n.install("de", load("de"))
        assertTrue(!I18n.rtl)
        I18n.install("tr", emptyMap())
    }
}
