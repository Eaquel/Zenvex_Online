package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenvex.online.game.Account
import com.zenvex.online.game.Online
import org.json.JSONObject

/** Şikâyet nedenleri (sunucuya metin olarak gider) */
private val REPORT_REASONS = listOf("Hile", "Küfür / Hakaret", "Spam / Reklam", "Uygunsuz İsim", "Dolandırıcılık", "Diğer")

/**
 * Oyunda başka bir oyuncuya dokununca: solda oyuncu kartı (resim, çerçeve, ad, seviye, klan, unvan), sağda eylemler:
 * Fısıltı (yalnız ona giden mesaj), Takas Teklif Et, Arkadaş Ekle, Profili Gör, Şikâyet Et.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerMenu(uid: String, name: String, close: () -> Unit, trade: () -> Unit, profile: () -> Unit) {
    var p by remember(uid) { mutableStateOf<JSONObject?>(null) }
    var mode by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(uid) { Account.viewProfile(name) { r -> r.onSuccess { p = it } } }
    val target = uid.removePrefix("a").toLongOrNull()
    val quiet = remember { MutableInteractionSource() }
    // dışarı dokunmak kapatır değil: yalnız X (yanlışlıkla kapanmasın); dokunuş oyuna geçmez
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(quiet, null) {}.imePadding(), contentAlignment = Alignment.CenterEnd) {
        Row(Modifier.padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- oyuncu kartı
            PxPanel(Modifier.width(250.dp), color = Color(0xF0201F1C), pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PT(name, 18.sp, Px.Name, Modifier.weight(1f))
                    PxIcon("close", size = 32.dp) { close() }
                }
                Spacer(Modifier.height(8.dp))
                val o = p
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    ProfileAvatar(o?.optInt("avatar") ?: 1, o?.optString("frame") ?: "", 110.dp, photo = o?.optString("photo")?.takeIf { it != "null" } ?: "")
                }
                Spacer(Modifier.height(6.dp))
                if (o == null) PT("Yükleniyor…", 12.sp, Px.Sub, Modifier.align(Alignment.CenterHorizontally))
                else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        PT("Sv ${o.optInt("level", 1)}", 15.sp, Px.Level)
                        Spacer(Modifier.width(10.dp))
                        PT("${fmtL(o.optLong("kills"))} Öldürme", 13.sp, Color(0xFFFF8A7A))
                    }
                    o.optJSONObject("clan")?.let { c -> PT("${c.optString("name")} · ${c.optString("rank_name")}", 12.sp, Px.Clan, Modifier.align(Alignment.CenterHorizontally)) }
                    o.optJSONObject("title")?.let { Spacer(Modifier.height(4.dp)); Box(Modifier.align(Alignment.CenterHorizontally)) { TitleChip(it, 11f) } }
                }
            }
            // ---- eylemler
            PxPanel(Modifier.width(250.dp), color = Color(0xF0201F1C), pad = 10.dp) {
                when (mode) {
                    "whisper" -> {
                        PT("$name Adlı Oyuncuya Fısılda", 13.sp, Px.Clan)
                        Spacer(Modifier.height(6.dp))
                        Field(msg, { if (it.length <= 140) msg = it }, "Mesajın…", Modifier.fillMaxWidth()) {
                            Online.whisper(uid, name, msg)?.let { Toaster.show(it, false) } ?: run { msg = ""; mode = "" }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PxButton("Gönder", Modifier.weight(1f), color = Px.Green, height = 40.dp, size = 14.sp) {
                                Online.whisper(uid, name, msg)?.let { Toaster.show(it, false) } ?: run { Toaster.show("Fısıltı Gönderildi"); msg = ""; mode = "" }
                            }
                            PxButton("Geri", color = Px.Dark, height = 40.dp, size = 14.sp) { mode = "" }
                        }
                    }
                    "report" -> {
                        // V15 (R41c): neden seç + kısa açıklama (yönetici bildirimde iki profili yan yana görür)
                        PT("Neden Şikâyet Ediyorsun?", 13.sp, Px.Clan)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (r in REPORT_REASONS) {
                                val on = reason == r
                                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (on) Color(0xFFB0402E) else Color(0xCC5A2420))
                                    .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(3.dp))
                                    .clickable { reason = r }.padding(horizontal = 9.dp, vertical = 7.dp)) { PT(r, 12.sp) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Field(note, { if (it.length <= 200) note = it }, "Ne oldu? (İsteğe bağlı, en çok 200 harf)", Modifier.fillMaxWidth())
                        PT("${note.length}/200", 10.sp, Px.Sub)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PxButton("Şikâyet Et", Modifier.weight(1f), color = Px.Red, height = 38.dp, size = 13.sp, enabled = reason.isNotEmpty()) {
                                if (target == null) { Toaster.show("Misafir Oyuncu Şikâyet Edilemez", false); return@PxButton }
                                Account.report(target, reason, note) { e -> if (e != null) Toaster.show(e, false) else Toaster.show("Şikâyetin Yöneticilere İletildi") }
                                mode = ""; reason = ""; note = ""
                            }
                            PxButton("Geri", color = Px.Dark, height = 38.dp, size = 13.sp) { mode = "" }
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PxButton("Fısıltı", Modifier.fillMaxWidth(), color = Px.Green, icon = "chat", height = 44.dp, size = 15.sp) { mode = "whisper" }
                        PxButton("Takas Teklif Et", Modifier.fillMaxWidth(), color = Px.Green, icon = "trade", height = 44.dp, size = 15.sp) { trade() }
                        PxButton("Arkadaş Ekle", Modifier.fillMaxWidth(), color = Px.Blue, icon = "people", height = 44.dp, size = 15.sp) {
                            Account.friendRequest(name) { r -> r.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) } }
                        }
                        PxButton("Profili Gör", Modifier.fillMaxWidth(), color = Px.Dark, icon = "profile", height = 44.dp, size = 15.sp) { profile() }
                        PxButton("Şikâyet Et", Modifier.fillMaxWidth(), color = Px.Red, icon = "info", height = 44.dp, size = 15.sp) { mode = "report" }
                    }
                }
            }
        }
    }
}
