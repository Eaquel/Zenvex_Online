package com.zenvex.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Arkadaşlar ve özel mesajlar (sohbetin "Özel" sekmesi). Yalnız arkadaşlar arasında yazışılır. */
@Composable
fun FriendsPanel(modifier: Modifier = Modifier) {
    if (!Account.loggedIn) { PT("Arkadaşlar İçin Hesap Gerekli.", 14.sp, Px.Sub, modifier); return }
    var data by remember { mutableStateOf(JSONObject()) }
    var tick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var add by remember { mutableStateOf("") }
    LaunchedEffect(tick) { Account.friends { r -> r.onSuccess { data = it } } }
    LaunchedEffect(Unit) { while (true) { delay(8000); tick++ } }
    val res: (Result<String>) -> Unit = { r -> r.onSuccess { Toaster.show(it) }.onFailure { Toaster.show(it.message ?: "", false) }; tick++ }
    val o = open
    if (o != null) { DmThread(o, modifier) { open = null; tick++ }; return }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(add, { add = it.take(16) }, "Oyuncu Adı", Modifier.weight(1f)) { Account.friendRequest(add, res); add = "" }
            Spacer(Modifier.width(6.dp))
            PxButton("Ekle", height = 44.dp, size = 14.sp, icon = "plus") { Account.friendRequest(add, res); add = "" }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val inc = data.optJSONArray("incoming") ?: JSONArray()
            if (inc.length() > 0) PT("Arkadaşlık İstekleri", 13.sp, Px.Clan)
            for (i in 0 until inc.length()) {
                val f = inc.getJSONObject(i)
                Row(Modifier.fillMaxWidth().background(Color(0x40B08A2A)).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(f.optInt("avatar"), "", 30.dp, account = f.optLong("id", -1))
                    Spacer(Modifier.width(6.dp))
                    PT("${f.optString("name")} · Sv ${f.optInt("level")}", 14.sp, modifier = Modifier.weight(1f))
                    PxButton("Kabul", height = 30.dp, size = 12.sp) { Account.friendRespond(f.optLong("id"), true, res) }
                    Spacer(Modifier.width(4.dp))
                    PxButton("Ret", height = 30.dp, size = 12.sp, color = Px.Dark) { Account.friendRespond(f.optLong("id"), false, res) }
                }
            }
            val fr = data.optJSONArray("friends") ?: JSONArray()
            PT("Arkadaşlar (${fr.length()})", 13.sp, Px.Clan)
            if (fr.length() == 0) PT("Henüz Arkadaşın Yok. Adıyla Ekleyebilirsin.", 12.sp, Px.Sub)
            for (i in 0 until fr.length()) {
                val f = fr.getJSONObject(i)
                Row(Modifier.fillMaxWidth().background(Px.Row).clickable { open = f }.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(f.optInt("avatar"), f.optString("frame"), 30.dp, account = f.optLong("id", -1))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (f.optBoolean("online")) Px.Name else Px.Sub))
                    Spacer(Modifier.width(6.dp))
                    PT(f.optString("name"), 14.sp, modifier = Modifier.weight(1f))
                    PT("Sv ${f.optInt("level")}", 12.sp, Px.Level)
                    if (f.optInt("unread") > 0) Box(Modifier.padding(start = 6.dp).size(18.dp).clip(CircleShape).background(Color(0xFFE02828)), contentAlignment = Alignment.Center) {
                        PT("${f.optInt("unread")}", 10.sp)
                    }
                }
            }
            val out = data.optJSONArray("outgoing") ?: JSONArray()
            for (i in 0 until out.length()) PT("⌛ ${out.getJSONObject(i).optString("name")} · İstek Bekliyor", 11.sp, Px.Sub)
        }
    }
}

@Composable
private fun DmThread(f: JSONObject, modifier: Modifier, back: () -> Unit) {
    val msgs = remember { mutableStateListOf<JSONObject>() }
    var last by remember { mutableLongStateOf(0L) }
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    val id = f.optLong("id")
    fun pull() = Account.dmList(id, last) { r ->
        r.onSuccess { a ->
            for (i in 0 until a.length()) { val m = a.getJSONObject(i); msgs += m; last = maxOf(last, m.optLong("id")) }
        }
    }
    LaunchedEffect(id) { while (true) { pull(); delay(3000) } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) state.animateScrollToItem(msgs.size - 1) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PxIcon("back", size = 34.dp) { back() }
            Spacer(Modifier.width(8.dp))
            ProfileAvatar(f.optInt("avatar"), f.optString("frame"), 30.dp, account = f.optLong("id", -1))
            Spacer(Modifier.width(6.dp))
            PT(f.optString("name"), 16.sp, Px.Name, Modifier.weight(1f))
            PxIcon("trash", size = 34.dp, tint = Color(0xFFE07060)) { confirm = true }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(msgs) { m ->
                val mine = m.optBoolean("mine")
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(Modifier.widthIn(max = 300.dp).background(if (mine) Color(0xC02E4A2A) else Color(0xC0302E28)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                        PT(m.optString("text"), 14.sp, maxLines = 6)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var wait by remember { mutableLongStateOf(0L) }
            LaunchedEffect(wait) { if (wait > 0) { delay(200); wait = Online.chatWait() } }
            fun go() {
                if (Online.chatLocked()) { Toaster.show("Sohbet ${Online.CHAT_LEVEL}. Seviyede Açılır", false); return }
                if (Online.chatWait() > 0) { Toaster.show("${(Online.chatWait() + 999) / 1000} Sn Bekle", false); return }
                val t = text.trim(); if (t.isEmpty()) return
                text = ""
                Account.dmSend(id, t) { r -> r.onSuccess { pull() }.onFailure { Toaster.show(it.message ?: "", false) } }
                Online.markSent(); wait = Online.chatWait()
            }
            Field(text, { if (it.length <= 200) text = it }, "Özel Mesaj…", Modifier.weight(1f)) { go() }
            Spacer(Modifier.width(6.dp))
            if (wait > 0) Box(Modifier.size(44.dp).pxPanel(Px.PanelDark), contentAlignment = Alignment.Center) { PT("${(wait + 999) / 1000}", 16.sp, Px.Sub) }
            else PxButton("", height = 44.dp, icon = "play", color = Px.Blue) { go() }
        }
    }
    if (confirm) PxDialog("Arkadaşlıktan Çıkar?", { confirm = false }) {
        PxButton("Çıkar", Modifier.fillMaxWidth(), Px.Red, height = 40.dp) {
            Account.friendRemove(id) { confirm = false; back() }
        }
    }
}
