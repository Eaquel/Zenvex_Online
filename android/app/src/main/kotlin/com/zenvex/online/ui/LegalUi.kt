package com.zenvex.online.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.GameSession
import com.zenvex.online.game.Legal
import com.zenvex.online.game.Store
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ayarlar > Yasal'daki metinler (sıra ekrandaki sıradır) */
private val PAGE_ORDER = listOf("privacy", "terms", "presale", "sales", "content", "promo", "imprint")
/** düğmeye sığan kısa adlar */
private val SHORT = mapOf("privacy" to "KVKK Aydınlatma Metni", "content" to "İçerik ve Telif Bildirimi", "promo" to "Ticari İleti Onay Metni")

/**
 * Yasal pencereler (MainActivity kökünde, her ekranın üstünde):
 *  - ilk açılış aydınlatması (KVKK m.10) — onaylanmadan oyun ve cihaz kimliği gönderimi başlamaz,
 *  - güncellenen Kullanım Şartları'nın yeniden onayı,
 *  - satın alma öncesi bilgilendirme ve onay (6502 m.48, Mesafeli Sözleşmeler Yönetmeliği m.5 ve m.15/1-ğ),
 *  - tam ekran yasal metin okuyucu.
 */
@Composable
fun LegalHost() {
    // hesap değişince (giriş, kayıt, çıkış) onaylanan şartlar sürümü ve bildirim tercihi sunucudan alınır
    LaunchedEffect(Account.id.value) { if (Account.loggedIn) Legal.sync() else Legal.termsNeeded.value = false }
    when {
        !Legal.noticeSeen.value -> NoticeDialog()
        Legal.termsNeeded.value && Account.loggedIn -> TermsUpdateDialog()
    }
    Store.confirm.value?.let { PurchaseConfirm(it) }
    Legal.open.value?.let { id -> LegalReader(id) { Legal.open.value = null } }
}

// =========================================================================== küçük parçalar
@Composable
fun LegalCheck(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(if (checked) Px.Green else Color(0xFF17160F))
            .border(2.dp, if (checked) Px.GreenHi else Px.Sub, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            if (checked) Icon("check", 16.dp, Color.White)
        }
        Spacer(Modifier.width(8.dp))
        PT(text, 12.sp, Px.Text, Modifier.weight(1f), maxLines = 8)
    }
}

/** Metne bağlantı: dokununca tam ekran okuyucuda açılır */
@Composable
fun LegalLink(id: String, label: String? = null) {
    val title = label ?: Legal.page(id)?.title ?: id
    PT(title, 12.sp, Color(0xFF8CC2FF), Modifier.clip(RoundedCornerShape(3.dp)).clickable { Legal.open.value = id }.padding(horizontal = 2.dp, vertical = 3.dp), maxLines = 2)
}

@Composable
private fun LegalPara(raw: String, dark: Boolean = false) {
    val text = if (dark) Color(0xFF2B2A24) else Px.Text
    // arayüz metinleri çevrilir (madde işareti ayrı); yasal metinler (legal.json) Türkçe kalır: bağlayıcı dil Türkçedir
    val tr = com.zenvex.online.game.I18n.t(raw)
    val p = when {
        !raw.startsWith("- ") -> tr
        tr != raw -> if (tr.startsWith("- ")) tr else "- $tr"
        else -> "- " + com.zenvex.online.game.I18n.t(raw.removePrefix("- "))
    }
    when {
        Regex("^\\d+\\.\\s").containsMatchIn(p) && p.length < 60 ->
            PT(p, 15.sp, if (dark) Color(0xFF6A4A1A) else Px.Clan, Modifier.padding(top = 10.dp, bottom = 4.dp), maxLines = 2)
        p.startsWith("- ") -> Row(Modifier.padding(start = 6.dp, bottom = 4.dp)) {
            PT("•", 13.sp, text)
            Spacer(Modifier.width(6.dp))
            PT(p.removePrefix("- "), 13.sp, text, Modifier.weight(1f), maxLines = 40)
        }
        else -> PT(p, 13.sp, text, Modifier.padding(bottom = 8.dp), maxLines = 60)
    }
}

/** Tam ekran yasal metin okuyucu: başlıklar ve maddeler biçimli, kaydırılır; herkese açık bağlantı paylaşılabilir. */
@Composable
fun LegalReader(id: String, close: () -> Unit) {
    val ctx = LocalContext.current
    val page = Legal.page(id) ?: run { close(); return }
    Dim {
        Column(Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.94f).clip(RoundedCornerShape(10.dp)).background(Color(0xF7F4EFE2))
            .border(2.dp, Color(0xFF8A7A5A), RoundedCornerShape(10.dp)).clickable(remember { MutableInteractionSource() }, null) {}) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF2B2A24)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("shield", 20.dp, Px.Clan)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    PT("Zenvex Online · Metin Sürümü ${Legal.version}", 11.sp, Px.Sub, maxLines = 2)
                    PT(page.title, 16.sp, Px.Text, maxLines = 2)
                }
                PxButton("Bağlantıyı Paylaş", height = 32.dp, size = 12.sp, color = Px.Dark, icon = "globe") {
                    runCatching {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Legal.url(id)), null))
                    }
                }
                Spacer(Modifier.width(6.dp))
                PxButton("Kapat", height = 32.dp, size = 12.sp, color = Px.Red) { close() }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 14.dp)) {
                if (com.zenvex.online.game.I18n.lang != "tr") PT("Bu metnin bağlayıcı dili Türkçedir.", 12.sp, Color(0xFF6A4A1A), Modifier.padding(bottom = 8.dp), maxLines = 2)
                for (p in page.paras) LegalPara(p, dark = true)
            }
        }
    }
}

@Composable
private fun LegalDialogFrame(title: String, icon: String = "shield", content: @Composable ColumnScope.() -> Unit) {
    Dim {
        PxPanel(Modifier.fillMaxWidth(0.84f).fillMaxHeight(0.92f).clickable(remember { MutableInteractionSource() }, null) {}, pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, 22.dp, Px.Clan)
                Spacer(Modifier.width(8.dp))
                PT(title, 17.sp, Px.Clan, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

// =========================================================================== ilk açılış aydınlatması
@Composable
private fun NoticeDialog() {
    LegalDialogFrame("Kişisel Verilerinin İşlenmesi Hakkında Bilgilendirme") {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            for (p in Legal.notice) LegalPara(p)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegalLink("privacy", "Aydınlatma Metninin Tamamı")
            LegalLink("terms")
            LegalLink("imprint")
        }
        Spacer(Modifier.height(4.dp))
        PxButton("Okudum, Anladım", Modifier.fillMaxWidth(), height = 40.dp, size = 15.sp, icon = "check") { Legal.ackNotice() }
    }
}

// =========================================================================== şartlar güncellendi
@Composable
private fun TermsUpdateDialog() {
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LegalDialogFrame("Kullanım Şartları Güncellendi") {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            LegalPara("Kullanım Şartları, Aydınlatma Metni ve satın alma metinlerimiz güncellendi (metin sürümü ${Legal.version}). Oyuna devam etmeden önce güncel Kullanım Şartları'nı okuyup onaylaman gerekiyor.")
            LegalPara("Kabul etmezsen hesabından çıkış yapılır; hesabın ve ilerlemen silinmez. Hesabının silinmesini istersen Ayarlar > Yasal ekranını kullanabilirsin.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { LegalLink("terms"); LegalLink("privacy"); LegalLink("presale") }
            LegalCheck(ok, "Kullanım Şartları'nı okudum ve kabul ediyorum.") { ok = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PxButton("Kabul Etmiyorum, Çıkış Yap", Modifier.weight(1f), color = Px.Dark, height = 40.dp, size = 13.sp) {
                GameSession.save(cloud = true); com.zenvex.online.game.Session.end(); Account.logout()
                Legal.termsNeeded.value = false
                GameSession.reload { Toaster.show("Hesaptan Çıkıldı") }
            }
            PxButton(if (busy) "Bekle…" else "Onayla ve Devam Et", Modifier.weight(1f), enabled = ok && !busy, height = 40.dp, size = 14.sp) {
                busy = true
                Legal.acceptTerms { e -> busy = false; if (e != null) Toaster.show(e, false) }
            }
        }
    }
}

// =========================================================================== satın alma öncesi bilgilendirme
@Composable
private fun PurchaseConfirm(id: String) {
    val p = Store.product(id) ?: JSONObject().put("id", id).put("title", id)
    val meta = p.optJSONObject("meta") ?: JSONObject()
    val kind = p.optString("kind")
    val price = Store.price(id, p.optDouble("price"))
    var ok by remember(id) { mutableStateOf(false) }
    val close = { Store.confirm.value = null }
    LegalDialogFrame("Satın Alma Onayı", "crate") {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // sol: ürün, içerik, fiyat, teslim
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                PT(p.optString("title"), 20.sp, Px.Orange, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                val lines = buildList {
                    val gems = p.optLong("gems")
                    if (gems > 0) add("$gems Elmas")
                    if (gems > 0 && kind == "gems" && p.optBoolean("first")) add("İlk alım: 2 katı, toplam ${gems * 2} Elmas")
                    if (p.optLong("gold") > 0) add("${p.optLong("gold")} Altın")
                    val items = p.optJSONArray("items")
                    if (items != null && items.length() > 0) add("${items.length()} çeşit eşya (aşağıda)")
                    when (kind) {
                        "card" -> {
                            val days = meta.optInt("days")
                            add("$days gün boyunca her gün ${meta.optInt("daily_gems")} Elmas + ${meta.optLong("daily_gold")} Altın")
                            if (meta.has("daily_items")) add("Her gün ayrıca 1 karakter kartı")
                            add("Günlük ödül Mağaza > Kartlar'dan alınır; alınmayan ödüller 3 güne kadar birikir. $days gün sonunda kart kendiliğinden biter.")
                        }
                        "pass" -> add("Geçerli sezonun bileti: oynadığın sunucudaki (${GameSession.server.value.title}) karakterinde açılır, sezon bitince sona erer.")
                        "pack" -> add("Seviye paketi: yalnız teklif süresi içinde ve bir kez alınabilir.")
                    }
                }
                for (l in lines) LegalPara("- $l")
                val items = p.optJSONArray("items")
                if (items != null && items.length() > 0) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until minOf(items.length(), 6)) {
                        val it = items.getJSONObject(i)
                        ItemSlot(it.optString("id"), it.optInt("n"), size = 40.dp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT("Toplam fiyat:", 14.sp, Px.Sub)
                    Spacer(Modifier.width(6.dp))
                    PT(price, 18.sp, Px.Clan)
                }
                PT("Vergiler dahildir. Ek masraf yoktur; kesin tutar Google Play ödeme ekranında gösterilir.", 11.sp, Px.Sub, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                LegalPara("- Tek seferlik satın alma: abonelik değildir, otomatik yenilenmez, tekrar ücret alınmaz.")
                LegalPara("- Teslim: ödeme onaylanınca hemen; elmas, altın ve eşyalar postana, bilet karakterine, kart Mağaza > Kartlar'a.")
                LegalPara("- Ödeme Google Play ile alınır; kart bilgilerin bize ulaşmaz.")
                LegalPara("- Satıcı: ${Legal.operator["UNVAN"].orEmpty()}")
            }
            // sağ: cayma hakkı, metinler, onay
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0x334A2A20)).border(2.dp, Color(0xFFB0573F), RoundedCornerShape(4.dp))
                    .padding(8.dp)) {
                    PT("Cayma Hakkı Yoktur", 14.sp, Color(0xFFFF9A7A))
                    PT("Bu ürün onayından hemen sonra hesabına teslim edilen dijital içeriktir. Mesafeli Sözleşmeler Yönetmeliği m.15/1-ğ uyarınca cayma hakkı bulunmaz.",
                        12.sp, Px.Text, maxLines = 6)
                    PT("İçerik teslim edilmez ya da ayıplı olursa 6502 sayılı Kanun'daki hakların saklıdır (Ayarlar > Destek > Ödeme).", 12.sp, Px.Text, maxLines = 5)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { LegalLink("presale"); LegalLink("sales") }
                LegalCheck(ok, "Ön Bilgilendirme Formu'nu ve Mesafeli Satış Sözleşmesi'ni okudum, onaylıyorum. İçeriğin hemen teslim edilmesini ve bu nedenle cayma hakkımın olmadığını kabul ediyorum.") { ok = it }
                PT("18 yaşından küçüksen satın almadan önce velinden izin al.", 11.sp, Px.Sub, maxLines = 2)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PxButton("Vazgeç", Modifier.weight(1f), color = Px.Dark, height = 40.dp, size = 14.sp) { close() }
            PxButton("Siparişi Onayla · Ödeme Yükümlülüğü Doğurur", Modifier.weight(1.6f), enabled = ok, height = 40.dp, size = 13.sp) { Store.launch(id) }
        }
    }
}

// =========================================================================== Ayarlar > Yasal
private val DATE = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"))

/** Yasal metinler, satın alma geçmişi, KVKK veri özeti, hesap silme talebi; iki sütun, kaydırma yok. */
@Composable
fun ColumnScope.LegalPanel() {
    var dialog by remember { mutableStateOf<String?>(null) }
    var deleteAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Account.id.value) {
        if (Account.loggedIn) Account.consentState { r -> r.onSuccess { o -> deleteAt = if (o.isNull("delete_requested_at")) null else o.optLong("delete_requested_at") } }
    }
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PT("Yasal Metinler", 14.sp, Px.Clan)
            Spacer(Modifier.height(4.dp))
            for (row in PAGE_ORDER.mapNotNull { Legal.page(it) }.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (pg in row) PxButton(SHORT[pg.id] ?: pg.title, Modifier.weight(1f), color = Px.Dark, height = 34.dp, size = 11.sp) { Legal.open.value = pg.id }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(5.dp))
            }
            PT("Metin sürümü ${Legal.version} · Oyun ${appVersion()}", 11.sp, Px.Sub, maxLines = 2)
            PT("Yazı tipleri: Outfit, Big Shoulders, Pixelify Sans (SIL Open Font License).", 10.sp, Px.Sub, maxLines = 2)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PT("Hesabım ve Verilerim (KVKK m.11)", 14.sp, Px.Clan, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            if (!Account.loggedIn) {
                PT("Satın alma geçmişi ve veri özeti için hesabına gir.", 12.sp, Px.Sub, maxLines = 3)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PxButton("Satın Almalarım", Modifier.weight(1f), color = Px.Blue, height = 34.dp, size = 12.sp, icon = "crate") { dialog = "purchases" }
                PxButton("Verilerim", Modifier.weight(1f), color = Px.Blue, height = 34.dp, size = 12.sp, icon = "eye") { dialog = "data" }
            }
            Spacer(Modifier.height(5.dp))
            PT("Bilgi, düzeltme, silme ve itiraz başvuruları için Ayarlar > Destek ekranında \"KVKK\" konusunu seç; en geç 30 gün içinde ücretsiz yanıtlanır.", 11.sp, Px.Sub, maxLines = 4)
            Spacer(Modifier.height(6.dp))
            val at = deleteAt
            if (at != null) {
                PT("Silme talebin var: ${DATE.format(Date(at + 30L * 86_400_000))} tarihinde hesabın anonimleştirilecek.", 12.sp, Color(0xFFFF9A7A), maxLines = 3)
                Spacer(Modifier.height(4.dp))
                PxButton("Silme Talebimi İptal Et", Modifier.fillMaxWidth(), color = Px.Dark, height = 36.dp, size = 12.sp) {
                    Account.cancelDelete { e -> Toaster.show(e ?: "Silme Talebin İptal Edildi", e == null); if (e == null) deleteAt = null }
                }
            } else {
                var ask by remember { mutableStateOf(false) }
                PxButton(if (ask) "Emin Misin? 30 Gün Sonra Hesabın Silinir" else "Hesabımın Silinmesini İste", Modifier.fillMaxWidth(), color = Px.Red, height = 36.dp, size = 12.sp) {
                    if (!ask) { ask = true; return@PxButton }
                    ask = false
                    Account.requestDelete { e -> Toaster.show(e ?: "Silme Talebin Alındı", e == null); if (e == null) deleteAt = System.currentTimeMillis() }
                }
                PT("30 gün içinde iptal edebilirsin. Sonra ad, e-posta, fotoğraf, mesajlar ve oyun kaydın silinir; satın alma kayıtları yasal süre boyunca anonim saklanır.", 10.sp, Px.Sub, maxLines = 4)
            }
        }
    }
    when (dialog) {
        "purchases" -> PurchasesDialog { dialog = null }
        "data" -> MyDataDialog { dialog = null }
    }
}

@Composable
private fun PurchasesDialog(close: () -> Unit) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Account.myPurchases { r -> r.onSuccess { list = it }.onFailure { err = it.message.orEmpty() } } }
    PxDialog("Satın Almalarım", close, width = 640.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
            val l = list
            when {
                err.isNotEmpty() -> PT(err, 12.sp, Color(0xFFFF7A6A), maxLines = 3)
                l == null -> PT("Yükleniyor…", 12.sp, Px.Sub)
                l.length() == 0 -> PT("Henüz bir satın alman yok.", 12.sp, Px.Sub)
                else -> for (i in 0 until l.length()) {
                    val o = l.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Px.PanelDark).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            PT(o.optString("title") + if (o.optBoolean("test")) " (test)" else "", 13.sp, Px.Text, maxLines = 2)
                            PT("Sipariş: ${o.optString("order")} · ${DATE.format(Date(o.optLong("at")))}", 10.sp, Px.Sub, maxLines = 2)
                        }
                        PT("₺" + "%.2f".format(o.optDouble("price")).replace('.', ','), 14.sp, Px.Clan)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        PT("Kesin tutar Google Play makbuzundadır. Teslimle ilgili sorunlar için Ayarlar > Destek > Ödeme.", 11.sp, Px.Sub, maxLines = 2)
    }
}

@Composable
private fun MyDataDialog(close: () -> Unit) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Account.myData { r -> r.onSuccess { data = it }.onFailure { err = it.message.orEmpty() } } }
    PxDialog("Sunucudaki Verilerim", close, width = 680.dp) {
        PT("KVKK m.11 kapsamında sunucuda seninle ilgili tutulan verilerin özeti. Cihazındaki oyun ayarları yalnız telefonunda durur.", 11.sp, Px.Sub, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).background(Color(0xCC0E0D0A)).verticalScroll(rememberScrollState()).padding(8.dp)) {
            val d = data
            when {
                err.isNotEmpty() -> PT(err, 12.sp, Color(0xFFFF7A6A), maxLines = 3)
                d == null -> PT("Yükleniyor…", 12.sp, Px.Sub)
                else -> androidx.compose.material3.Text(d.toString(2), color = Px.Text,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp))
            }
        }
        Spacer(Modifier.height(6.dp))
        PxButton("Paylaş / Kaydet", Modifier.fillMaxWidth(), color = Px.Blue, height = 36.dp, size = 13.sp, enabled = data != null, icon = "globe") {
            val d = data ?: return@PxButton
            runCatching {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_SUBJECT, "Zenvex Online verilerim").putExtra(Intent.EXTRA_TEXT, d.toString(2)), null))
            }
        }
    }
}

// =========================================================================== şikâyet ve fotoğraf bildirimi
/** Profilden şikâyet: hukuka aykırı içerik ve telif bildirimleri de buradan yapılır (5651 m.9, 5846 Ek m.4). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(target: Long, name: String, close: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    PxDialog("Şikâyet Et: $name", close, width = 620.dp) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (r in REPORT_REASONS) {
                val on = reason == r
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) Color(0xFFB0402E) else Color(0xCC5A2420))
                    .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(3.dp))
                    .clickable { reason = r }.padding(horizontal = 9.dp, vertical = 7.dp)) { PT(r, 12.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Field(note, { if (it.length <= 200) note = it }, "Ne oldu? Telif bildiriminde eserin sana ait olduğunu belirt (en çok 200 harf)", Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            PT("${note.length}/200", 10.sp, Px.Sub, Modifier.weight(1f))
            LegalLink("content", "Bildirim Usulü")
        }
        Spacer(Modifier.height(4.dp))
        PxButton("Şikâyet Et", Modifier.fillMaxWidth(), color = Px.Red, height = 38.dp, size = 13.sp, enabled = reason.isNotEmpty()) {
            Account.report(target, reason, note) { e -> if (e != null) Toaster.show(e, false) else Toaster.show("Şikâyetin Yöneticilere İletildi") }
            close()
        }
    }
}

/** Profil fotoğrafı seçmeden önce: herkese açık olduğu ve hak sahipliği sorumluluğu (5846, kişilik hakları, KVKK). */
@Composable
fun PhotoNoticeDialog(close: () -> Unit, proceed: () -> Unit) {
    var ok by remember { mutableStateOf(false) }
    PxDialog("Profil Fotoğrafı", close, width = 560.dp) {
        LegalPara("- Profil fotoğrafın tüm oyunculara görünür.")
        LegalPara("- Yalnız sana ait ya da kullanma hakkına sahip olduğun bir fotoğraf yükle. Başkasının fotoğrafını izinsiz, telifli görselleri (film, oyun, sanatçı görselleri vb.) ya da müstehcen, şiddet içeren, nefret söylemi içeren görselleri yükleme.")
        LegalPara("- Fotoğrafında kimlik, adres, telefon gibi kişisel bilgiler görünmesin.")
        LegalPara("- Kurallara aykırı fotoğraflar bildirim üzerine kaldırılır; tekrarında hesap yaptırımı uygulanır.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { LegalLink("terms"); LegalLink("privacy") }
        LegalCheck(ok, "Bu fotoğrafı yükleme hakkına sahibim ve kuralları kabul ediyorum.") { ok = it }
        Spacer(Modifier.height(4.dp))
        PxButton("Fotoğraf Seç", Modifier.fillMaxWidth(), color = Px.Blue, height = 38.dp, size = 13.sp, enabled = ok) { close(); proceed() }
    }
}

/** Hesap açma / girme ekranındaki zorunlu onay (aydınlatma ayrı bir bilgilendirmedir, onaya bağlı değildir). */
@Composable
fun AccountTerms(checked: Boolean, onChange: (Boolean) -> Unit) {
    LegalCheck(checked, "Kullanım Şartları'nı okudum ve kabul ediyorum.") { onChange(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { LegalLink("terms"); LegalLink("privacy", "Aydınlatma Metni") }
}
