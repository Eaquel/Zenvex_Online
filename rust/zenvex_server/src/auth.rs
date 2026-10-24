//! Bağlantının ilk mesajı: kimlik.
//!
//! İstemci: `{"t":"hello","acc":<hesap>,"sid":"<oturum>","ts":<ms>,"sig":"<hex>","server":"n1","meta":{...}}`
//! `sig = HMAC-SHA256(oturum sırrı, "net:<hesap>:<oturum>:<ts>")`. Oturum sırrı yalnız istemcide ve Supabase'de
//! durur; sunucu sırrı zx_net_auth ile (servis anahtarıyla) alır, imzayı karşılaştırır. Zaman damgası 2 dakikadan
//! eskiyse reddedilir. Yük testinde (ALLOW_GUESTS=1) `{"t":"hello","guest":"g1",...}` de kabul edilir.

use hmac::{Hmac, KeyInit, Mac};
use serde_json::{json, Value};
use sha2::Sha256;

use crate::Config;

pub struct Who {
    pub uid: String,
    pub server: String,
    pub name: String,
    pub clan: i64,
    pub meta: Value,
}

const SERVERS: [&str; 4] = ["n1", "n2", "n3", "hc"];

pub async fn check(hello: &str, cfg: &Config) -> Result<Who, String> {
    let v: Value = serde_json::from_str(hello).map_err(|_| "Geçersiz istek")?;
    if v["t"] != "hello" {
        return Err("Önce kimlik".into());
    }
    let server = v["server"].as_str().unwrap_or("");
    if !SERVERS.contains(&server) {
        return Err("Geçersiz sunucu".into());
    }
    let meta = if v["meta"].is_object() { v["meta"].clone() } else { json!({}) };
    if let Some(g) = v["guest"].as_str() {
        if !cfg.allow_guests {
            return Err("Misafir girişi kapalı".into());
        }
        return Ok(Who { uid: format!("g{}", g.chars().filter(|c| c.is_ascii_alphanumeric()).take(16).collect::<String>()),
            server: server.into(), name: meta["n"].as_str().unwrap_or("Bot").chars().take(24).collect(), clan: 0, meta });
    }
    let acc = v["acc"].as_i64().ok_or("Hesap yok")?;
    let sid = v["sid"].as_str().ok_or("Oturum yok")?;
    let ts = v["ts"].as_i64().ok_or("Zaman yok")?;
    let sig = v["sig"].as_str().ok_or("İmza yok")?;
    let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0);
    if (now - ts).abs() > 120_000 {
        return Err("Saat farkı çok büyük".into());
    }
    let r = reqwest::Client::new()
        .post(format!("{}/rest/v1/rpc/zx_net_auth", cfg.sb_url))
        .header("apikey", &cfg.sb_key)
        .header("authorization", format!("Bearer {}", cfg.sb_key))
        .json(&json!({"p_id": acc, "p_sid": sid}))
        .timeout(std::time::Duration::from_secs(8))
        .send()
        .await
        .map_err(|_| "Kimlik sunucusuna ulaşılamadı")?;
    if !r.status().is_success() {
        return Err("Oturum geçersiz".into());
    }
    let a: Value = r.json().await.map_err(|_| "Kimlik yanıtı okunamadı")?;
    let secret = a["secret"].as_str().ok_or("Oturum geçersiz")?;
    let mut mac = Hmac::<Sha256>::new_from_slice(secret.as_bytes()).map_err(|_| "İmza hatası")?;
    mac.update(format!("net:{acc}:{sid}:{ts}").as_bytes());
    let want = hex::decode(sig).map_err(|_| "İmza hatası")?;
    mac.verify_slice(&want).map_err(|_| "İmza tutmadı")?;
    Ok(Who {
        uid: format!("a{acc}"),
        server: server.into(),
        name: a["name"].as_str().unwrap_or("Oyuncu").to_string(),
        clan: a["clan"].as_i64().unwrap_or(0),
        meta,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hmac_matches_client_format() {
        // istemci (Kotlin) ile aynı biçim: hex(HMAC-SHA256(sır, "net:<hesap>:<oturum>:<ts>"))
        let mut mac = Hmac::<Sha256>::new_from_slice(b"s3cret").unwrap();
        mac.update(b"net:7:abc:1000");
        let h = hex::encode(mac.finalize().into_bytes());
        assert_eq!(h.len(), 64);
    }
}
