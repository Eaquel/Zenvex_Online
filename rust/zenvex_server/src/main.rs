//! Zenvex Online oyun sunucusu (V15).
//!
//! Supabase Realtime'ın yerini alan, kendi makinemizde çalışan WebSocket sunucusu. Hesap, kayıt, klan, posta yine
//! Supabase'de; bu sunucu yalnız canlı oyunu taşır:
//!   - Oda = (oyun sunucusu, bölge). Oyuncu yalnız bulunduğu bölgenin odasındadır.
//!   - Konumlar sunucuda toplanır, saniyede 10 kez tek paket halinde ve yalnız yakındaki oyunculara gider (ilgi alanı).
//!   - Hedefli mesajlar (vuruş, hasar, PvP, takas, fısıltı) yalnız alıcısına iletilir; herkese yayılmaz.
//!   - Zombileri bölgedeki bir oyuncu (ev sahibi) yönetir; ev sahibini sunucu seçer ve ayrılınca hemen devreder.
//!   - Kimlik: istemci Supabase oturum numarasını ve oturum sırrıyla imzalanmış bir zaman damgası yollar; sunucu
//!     oturumu Supabase'den (zx_net_auth) doğrular.
//!
//! Ortam değişkenleri: BIND (0.0.0.0:7777), HEALTH_BIND (127.0.0.1:7778), SB_URL, SB_SERVICE_KEY,
//! ALLOW_GUESTS (yük testi için 1), INTEREST_PX (3000).
//!
//! Protokol: metin çerçeveleri, JSON. Bkz. docs/teknik/OYUN_SUNUCUSU.md.

mod auth;
mod hub;

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{mpsc, Mutex};
use tokio_tungstenite::tungstenite::Message;

use hub::{Hub, Outbox};

pub struct Config {
    pub sb_url: String,
    pub sb_key: String,
    pub allow_guests: bool,
    pub interest_px: f32,
}

fn env(k: &str, d: &str) -> String {
    std::env::var(k).ok().filter(|v| !v.is_empty()).unwrap_or_else(|| d.to_string())
}

#[tokio::main]
async fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.get(1).map(String::as_str) == Some("loadtest") {
        hub::loadtest::run(&args[2..]).await;
        return;
    }
    let cfg = Arc::new(Config {
        sb_url: env("SB_URL", "https://blvstsvmvmdydyrnecpo.supabase.co"),
        sb_key: env("SB_SERVICE_KEY", ""),
        allow_guests: env("ALLOW_GUESTS", "0") == "1",
        interest_px: env("INTEREST_PX", "3000").parse().unwrap_or(3000.0),
    });
    if cfg.sb_key.is_empty() && !cfg.allow_guests {
        eprintln!("SB_SERVICE_KEY yok: oturumlar doğrulanamaz. (Yalnız yük testi için ALLOW_GUESTS=1)");
        std::process::exit(2);
    }
    let bind = env("BIND", "0.0.0.0:7777");
    let hub = Arc::new(Mutex::new(Hub::new(cfg.interest_px)));
    // saniyede 10 kez: konum paketleri, ev sahibi seçimi, sessiz bağlantıların düşürülmesi
    {
        let hub = hub.clone();
        tokio::spawn(async move {
            let mut t = tokio::time::interval(Duration::from_millis(100));
            t.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
            loop {
                t.tick().await;
                hub.lock().await.tick();
            }
        });
    }
    // durum: http://HEALTH_BIND/ (oda, oyuncu, mesaj sayıları; yalnız makinenin içinden)
    {
        let hub = hub.clone();
        let hb = env("HEALTH_BIND", "127.0.0.1:7778");
        tokio::spawn(async move {
            let Ok(l) = TcpListener::bind(&hb).await else { return };
            loop {
                let Ok((mut s, _)) = l.accept().await else { continue };
                let body = hub.lock().await.stats().to_string();
                tokio::spawn(async move {
                    use tokio::io::AsyncWriteExt;
                    let r = format!(
                        "HTTP/1.1 200 OK\r\ncontent-type: application/json\r\ncontent-length: {}\r\nconnection: close\r\n\r\n{}",
                        body.len(),
                        body
                    );
                    let _ = s.write_all(r.as_bytes()).await;
                });
            }
        });
    }
    let listener = TcpListener::bind(&bind).await.expect("bağlanılamadı");
    eprintln!("zenvex_server {bind} dinliyor");
    loop {
        let Ok((stream, addr)) = listener.accept().await else { continue };
        let _ = stream.set_nodelay(true);
        tokio::spawn(conn(stream, addr, hub.clone(), cfg.clone()));
    }
}

async fn conn(stream: TcpStream, addr: SocketAddr, hub: Arc<Mutex<Hub>>, cfg: Arc<Config>) {
    let Ok(ws) = tokio_tungstenite::accept_async(stream).await else { return };
    let (mut sink, mut src) = ws.split();
    // ilk mesaj kimlik olmalı (10 sn içinde)
    let hello = match tokio::time::timeout(Duration::from_secs(10), src.next()).await {
        Ok(Some(Ok(Message::Text(t)))) => t.to_string(),
        _ => return,
    };
    let who = match auth::check(&hello, &cfg).await {
        Ok(w) => w,
        Err(e) => {
            let _ = sink.send(Message::text(serde_json::json!({"t": "err", "msg": e}).to_string())).await;
            return;
        }
    };
    let (tx, mut rx) = mpsc::unbounded_channel::<Outbox>();
    let id = hub.lock().await.join(who, tx);
    // gönderici: hub'ın kuyruğundan sokete
    let writer = tokio::spawn(async move {
        while let Some(m) = rx.recv().await {
            let msg = match m {
                Outbox::Text(t) => Message::text(t),
                Outbox::Close => break,
            };
            if sink.send(msg).await.is_err() {
                break;
            }
        }
        let _ = sink.close().await;
    });
    while let Ok(Some(Ok(m))) = tokio::time::timeout(Duration::from_secs(45), src.next()).await {
        match m {
            Message::Text(t) => {
                if t.len() > 32 * 1024 {
                    continue;
                }
                hub.lock().await.on_message(id, &t);
            }
            Message::Close(_) => break,
            _ => {}
        }
    }
    hub.lock().await.leave(id);
    writer.abort();
    let _ = addr;
}
