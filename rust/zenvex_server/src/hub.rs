//! Bağlı oyuncular, odalar ve mesaj yönlendirme.
//!
//! İstemci → sunucu (`t` alanı):
//!   track  {meta}                 presence bilgisi (ad, klan, unvan, kaplama) değişti
//!   area   {a}                    bölge değişti (oda değişir)
//!   p      {s:[...]}              kendi durumun (konum, yön, animasyon...); sunucu toplar, 10 Hz dağıtır
//!   z      {d:[...]}              ev sahibinin zombi listesi → odadaki diğerlerine
//!   hit    {...}                  zombi vuruşu → odanın ev sahibine
//!   kill pd pvp pvpk treq tr w {to,...}  hedefli: yalnız alıcıya
//!   c      {ch,m,...}             sohbet: genel → aynı sunucudaki herkes, klan → aynı klan
//!   ping   {i}                    → pong {i}
//! Sunucu → istemci: welcome, pres (presence: joins/leaves), P (konum paketi: [[uid, s...]]), host, ve yukarıdaki
//! iletilen mesajlar (u alanı sunucu tarafından gerçek gönderenle değiştirilir).

use std::collections::HashMap;
use std::time::{Duration, Instant};

use serde_json::{json, Map, Value};
use tokio::sync::mpsc::UnboundedSender;

use crate::auth::Who;

pub enum Outbox {
    Text(String),
    Close,
}

struct Client {
    uid: String,
    server: String,
    name: String,
    clan: i64,
    meta: Value,
    tx: UnboundedSender<Outbox>,
    area: i32,
    state: Vec<f64>,
    dirty: bool,
    last_msg: Instant,
    window: (Instant, u32),
    last_chat: Instant,
}

#[derive(Default)]
struct Room {
    members: Vec<u64>,
    host: Option<u64>,
}

pub struct Hub {
    clients: HashMap<u64, Client>,
    by_uid: HashMap<String, u64>,
    rooms: HashMap<(String, i32), Room>,
    next: u64,
    interest: f32,
    ticks: u64,
    msgs_in: u64,
    msgs_out: u64,
    started: Instant,
}

/// Bir istemcinin saniyede gönderebileceği en çok mesaj (fazlası sessizce düşer)
const MSG_PER_SEC: u32 = 40;
const CHAT_GAP: Duration = Duration::from_millis(2500);
/// Konumu değişmeyenler de bu kadar karede bir yeniden gönderilir (yeni katılanlar görsün)
const FULL_EVERY: u64 = 20;

impl Hub {
    pub fn new(interest: f32) -> Hub {
        Hub { clients: HashMap::new(), by_uid: HashMap::new(), rooms: HashMap::new(), next: 1, interest, ticks: 0, msgs_in: 0, msgs_out: 0,
            started: Instant::now() }
    }

    fn send(&mut self, id: u64, text: &str) {
        if let Some(c) = self.clients.get(&id) {
            if c.tx.send(Outbox::Text(text.to_string())).is_ok() {
                self.msgs_out += 1;
            }
        }
    }

    fn presence_of(c: &Client) -> Value {
        let mut m = c.meta.clone();
        if !m.is_object() {
            m = json!({});
        }
        if m.get("n").and_then(Value::as_str).unwrap_or("").is_empty() {
            m["n"] = json!(c.name);
        }
        json!({"metas": [m]})
    }

    pub fn join(&mut self, who: Who, tx: UnboundedSender<Outbox>) -> u64 {
        // aynı hesap başka bağlantıdaysa eskisi kapanır
        if let Some(old) = self.by_uid.get(&who.uid).copied() {
            if let Some(c) = self.clients.get(&old) {
                let _ = c.tx.send(Outbox::Close);
            }
            self.leave(old);
        }
        let id = self.next;
        self.next += 1;
        let now = Instant::now();
        let c = Client { uid: who.uid.clone(), server: who.server, name: who.name, clan: who.clan, meta: who.meta, tx, area: -1, state: Vec::new(),
            dirty: false, last_msg: now, window: (now, 0), last_chat: now - CHAT_GAP };
        self.by_uid.insert(who.uid.clone(), id);
        self.clients.insert(id, c);
        self.send(id, &json!({"t": "welcome", "you": who.uid}).to_string());
        // aynı sunucudakilerin tam listesi, diğerlerine katılma
        let server = self.clients[&id].server.clone();
        let mut joins = Map::new();
        for (k, c) in &self.clients {
            if *k != id && c.server == server {
                joins.insert(c.uid.clone(), Self::presence_of(c));
            }
        }
        self.send(id, &json!({"t": "pres", "full": true, "joins": joins, "leaves": {}}).to_string());
        self.presence_join(id);
        id
    }

    fn presence_join(&mut self, id: u64) {
        let Some(c) = self.clients.get(&id) else { return };
        let mut joins = Map::new();
        joins.insert(c.uid.clone(), Self::presence_of(c));
        let msg = json!({"t": "pres", "full": false, "joins": joins, "leaves": {}}).to_string();
        let server = c.server.clone();
        let ids: Vec<u64> = self.clients.iter().filter(|(k, o)| **k != id && o.server == server).map(|(k, _)| *k).collect();
        for k in ids {
            self.send(k, &msg);
        }
    }

    pub fn leave(&mut self, id: u64) {
        let Some(c) = self.clients.remove(&id) else { return };
        if self.by_uid.get(&c.uid) == Some(&id) {
            self.by_uid.remove(&c.uid);
        }
        self.room_remove(id, &c.server, c.area);
        let mut leaves = Map::new();
        leaves.insert(c.uid.clone(), json!({}));
        let msg = json!({"t": "pres", "full": false, "joins": {}, "leaves": leaves}).to_string();
        let ids: Vec<u64> = self.clients.iter().filter(|(_, o)| o.server == c.server).map(|(k, _)| *k).collect();
        for k in ids {
            self.send(k, &msg);
        }
    }

    fn room_remove(&mut self, id: u64, server: &str, area: i32) {
        if area < 0 {
            return;
        }
        let key = (server.to_string(), area);
        if let Some(r) = self.rooms.get_mut(&key) {
            r.members.retain(|m| *m != id);
            if r.members.is_empty() {
                self.rooms.remove(&key);
                return;
            }
        }
        self.elect(&key);
    }

    /// Ev sahibi: odadaki en küçük kimlik (istemcilerin eski seçimiyle aynı kural); değişince odaya duyurulur
    fn elect(&mut self, key: &(String, i32)) {
        let Some(r) = self.rooms.get(key) else { return };
        let best = r.members.iter().copied().min_by(|a, b| self.clients[a].uid.cmp(&self.clients[b].uid));
        if best == r.host {
            return;
        }
        let members = r.members.clone();
        if let Some(r) = self.rooms.get_mut(key) {
            r.host = best;
        }
        let h = best.map(|b| self.clients[&b].uid.clone()).unwrap_or_default();
        let msg = json!({"t": "host", "h": h}).to_string();
        for m in members {
            self.send(m, &msg);
        }
    }

    fn set_area(&mut self, id: u64, area: i32) {
        let Some(c) = self.clients.get_mut(&id) else { return };
        if c.area == area {
            return;
        }
        let (server, old) = (c.server.clone(), c.area);
        c.area = area;
        c.state.clear();
        c.dirty = false;
        self.room_remove(id, &server, old);
        if area < 0 {
            return;
        }
        let key = (server, area);
        self.rooms.entry(key.clone()).or_default().members.push(id);
        let host = self.rooms[&key].host;
        self.elect(&key);
        // seçim değişmediyse yeni gelen mevcut ev sahibini yine de öğrensin
        if self.rooms[&key].host == host {
            let h = host.map(|b| self.clients[&b].uid.clone()).unwrap_or_default();
            self.send(id, &json!({"t": "host", "h": h}).to_string());
        }
    }

    pub fn on_message(&mut self, id: u64, text: &str) {
        self.msgs_in += 1;
        let now = Instant::now();
        let Some(c) = self.clients.get_mut(&id) else { return };
        c.last_msg = now;
        if now.duration_since(c.window.0) >= Duration::from_secs(1) {
            c.window = (now, 0);
        }
        c.window.1 += 1;
        if c.window.1 > MSG_PER_SEC {
            return;
        }
        let Ok(mut v) = serde_json::from_str::<Value>(text) else { return };
        let t = v["t"].as_str().unwrap_or("").to_string();
        let (uid, server, area, name) = (c.uid.clone(), c.server.clone(), c.area, c.name.clone());
        match t.as_str() {
            "ping" => self.send(id, &json!({"t": "pong", "i": v["i"]}).to_string()),
            "track" => {
                if let Some(c) = self.clients.get_mut(&id) {
                    c.meta = if v["meta"].is_object() { v["meta"].clone() } else { json!({}) };
                    if let Some(n) = c.meta["n"].as_str() {
                        c.name = n.chars().take(24).collect();
                    }
                }
                self.presence_join(id);
            }
            "area" => self.set_area(id, v["a"].as_i64().unwrap_or(-1).clamp(-1, 64) as i32),
            "p" => {
                if let (Some(c), Some(s)) = (self.clients.get_mut(&id), v["s"].as_array()) {
                    c.state = s.iter().take(16).map(|x| x.as_f64().unwrap_or(0.0)).collect();
                    c.dirty = true;
                }
            }
            "z" => {
                let key = (server, area);
                let Some(r) = self.rooms.get(&key) else { return };
                if r.host != Some(id) {
                    return;
                }
                v["h"] = json!(uid);
                let msg = v.to_string();
                for m in r.members.clone() {
                    if m != id {
                        self.send(m, &msg);
                    }
                }
            }
            "hit" => {
                let key = (server, area);
                let Some(h) = self.rooms.get(&key).and_then(|r| r.host) else { return };
                if h != id {
                    v["u"] = json!(uid);
                    self.send(h, &v.to_string());
                }
            }
            "kill" | "pd" | "pvp" | "pvpk" | "treq" | "tr" | "w" => {
                let Some(to) = v["to"].as_str().and_then(|u| self.by_uid.get(u)).copied() else { return };
                let Some(o) = self.clients.get(&to) else { return };
                if o.server != server || (matches!(t.as_str(), "kill" | "pd" | "pvp" | "pvpk") && o.area != area) {
                    return;
                }
                v["u"] = json!(uid);
                if matches!(t.as_str(), "w" | "treq" | "pvp" | "pvpk") {
                    v["n"] = json!(name);
                }
                self.send(to, &v.to_string());
            }
            "c" => {
                let Some(c) = self.clients.get_mut(&id) else { return };
                if now.duration_since(c.last_chat) < CHAT_GAP {
                    return;
                }
                c.last_chat = now;
                let clan = c.clan;
                let ch = v["ch"].as_str().unwrap_or("global").to_string();
                let cid = v["cid"].as_i64().unwrap_or(0);
                if ch == "clan" && (cid == 0 || (clan != 0 && cid != clan)) {
                    return;
                }
                v["u"] = json!(uid);
                let msg = v.to_string();
                let ids: Vec<u64> = self.clients.iter().filter(|(k, o)| **k != id && o.server == server).map(|(k, _)| *k).collect();
                for k in ids {
                    self.send(k, &msg);
                }
            }
            _ => {}
        }
    }

    /// 10 Hz: odalara konum paketleri (yalnız ilgi alanındakiler), sessiz bağlantıları kapat
    pub fn tick(&mut self) {
        self.ticks += 1;
        let full = self.ticks % FULL_EVERY == 0;
        let r2 = self.interest * self.interest;
        let mut out: Vec<(u64, String)> = Vec::new();
        for room in self.rooms.values() {
            if room.members.len() < 2 {
                continue;
            }
            for &to in &room.members {
                let me = &self.clients[&to];
                let (mx, my) = (me.state.first().copied().unwrap_or(0.0) as f32, me.state.get(1).copied().unwrap_or(0.0) as f32);
                let mut list = Vec::new();
                for &from in &room.members {
                    if from == to {
                        continue;
                    }
                    let o = &self.clients[&from];
                    if o.state.len() < 2 || !(o.dirty || full) {
                        continue;
                    }
                    let (dx, dy) = (o.state[0] as f32 - mx, o.state[1] as f32 - my);
                    if dx * dx + dy * dy > r2 {
                        continue;
                    }
                    let mut e = vec![json!(o.uid)];
                    e.extend(o.state.iter().map(|x| json!((x * 10.0).round() / 10.0)));
                    list.push(Value::Array(e));
                }
                if !list.is_empty() {
                    out.push((to, json!({"t": "P", "l": list}).to_string()));
                }
            }
        }
        for c in self.clients.values_mut() {
            c.dirty = false;
        }
        for (to, msg) in out {
            self.send(to, &msg);
        }
        if self.ticks % 50 == 0 {
            let now = Instant::now();
            let dead: Vec<u64> = self.clients.iter().filter(|(_, c)| now.duration_since(c.last_msg) > Duration::from_secs(40)).map(|(k, _)| *k).collect();
            for k in dead {
                if let Some(c) = self.clients.get(&k) {
                    let _ = c.tx.send(Outbox::Close);
                }
                self.leave(k);
            }
        }
    }

    pub fn stats(&self) -> Value {
        let mut rooms = Map::new();
        for ((s, a), r) in &self.rooms {
            rooms.insert(format!("{s}:{a}"), json!(r.members.len()));
        }
        let up = self.started.elapsed().as_secs_f64().max(1.0);
        json!({"players": self.clients.len(), "rooms": rooms, "msgs_in_per_s": (self.msgs_in as f64 / up).round(),
            "msgs_out_per_s": (self.msgs_out as f64 / up).round(), "uptime_s": up.round()})
    }
}

pub mod loadtest {
    //! Yük testi: `zenvex_server loadtest ws://127.0.0.1:7777 <bot> <saniye>` (sunucu ALLOW_GUESTS=1 ile çalışmalı).
    //! Botlar aynı bölgede rastgele yürür (3,3 Hz konum), her saniye ping ölçer; sonunda ortalama ve en kötü ping,
    //! bot başına alınan mesaj yazılır.
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::sync::Arc;
    use std::time::{Duration, Instant};

    use futures_util::{SinkExt, StreamExt};
    use serde_json::{json, Value};
    use tokio_tungstenite::tungstenite::Message;

    pub async fn run(args: &[String]) {
        let url = args.first().cloned().unwrap_or_else(|| "ws://127.0.0.1:7777".into());
        let n: usize = args.get(1).and_then(|s| s.parse().ok()).unwrap_or(10);
        let secs: u64 = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(30);
        let recv = Arc::new(AtomicU64::new(0));
        let ping_sum = Arc::new(AtomicU64::new(0));
        let ping_n = Arc::new(AtomicU64::new(0));
        let ping_max = Arc::new(AtomicU64::new(0));
        let mut tasks = Vec::new();
        for i in 0..n {
            let (url, recv, ps, pn, pm) = (url.clone(), recv.clone(), ping_sum.clone(), ping_n.clone(), ping_max.clone());
            tasks.push(tokio::spawn(async move {
                let Ok((ws, _)) = tokio_tungstenite::connect_async(url.as_str()).await else { return };
                let (mut tx, mut rx) = ws.split();
                let _ = tx.send(Message::text(json!({"t": "hello", "guest": format!("bot{i}"), "server": "n1", "meta": {"n": format!("Bot {i}")}}).to_string())).await;
                let _ = tx.send(Message::text(json!({"t": "area", "a": (i % 2) as i64}).to_string())).await;
                let start = Instant::now();
                let sent_at = Arc::new(std::sync::Mutex::new(std::collections::HashMap::<u64, Instant>::new()));
                let sa = sent_at.clone();
                let reader = tokio::spawn(async move {
                    while let Some(Ok(Message::Text(t))) = rx.next().await {
                        recv.fetch_add(1, Ordering::Relaxed);
                        if let Ok(v) = serde_json::from_str::<Value>(&t) {
                            if v["t"] == "pong" {
                                if let Some(at) = sa.lock().unwrap().remove(&v["i"].as_u64().unwrap_or(0)) {
                                    let ms = at.elapsed().as_millis() as u64;
                                    ps.fetch_add(ms, Ordering::Relaxed);
                                    pn.fetch_add(1, Ordering::Relaxed);
                                    pm.fetch_max(ms, Ordering::Relaxed);
                                }
                            }
                        }
                    }
                });
                let (mut x, mut y) = (2000.0 + i as f64 * 37.0, 2000.0);
                let mut k = 0u64;
                while start.elapsed() < Duration::from_secs(secs) {
                    tokio::time::sleep(Duration::from_millis(300)).await;
                    x += ((k * 7 + i as u64) % 11) as f64 - 5.0;
                    y += ((k * 5 + i as u64) % 9) as f64 - 4.0;
                    k += 1;
                    let s = json!({"t": "p", "s": [x, y, 0, 1, 0, 0, 0, 0, 0, 0]}).to_string();
                    if tx.send(Message::text(s)).await.is_err() {
                        break;
                    }
                    if k % 3 == 0 {
                        sent_at.lock().unwrap().insert(k, Instant::now());
                        let _ = tx.send(Message::text(json!({"t": "ping", "i": k}).to_string())).await;
                    }
                }
                let _ = tx.close().await;
                reader.abort();
            }));
        }
        for t in tasks {
            let _ = t.await;
        }
        let pn = ping_n.load(Ordering::Relaxed).max(1);
        println!(
            "bot {n}, {secs} sn: ort. ping {} ms, en kötü {} ms, bot başına {:.1} mesaj/sn",
            ping_sum.load(Ordering::Relaxed) / pn,
            ping_max.load(Ordering::Relaxed),
            recv.load(Ordering::Relaxed) as f64 / n as f64 / secs as f64
        );
    }
}
