//! V12 bölgeler: dünya ayrı haritalara bölündü. Bölgeler arasında yürünmez (görünmez sınır, ötesi karanlık);
//! bölgeden bölgeye yalnız Büyücü Aldric ışınlar (her bölgenin giriş karakolunda bir büyücü durur).
//! Harita ekranı yalnız bulunulan bölgeyi gösterir; büyücünün haritasında bölgeye dokununca yaratıklar, ganimet,
//! önerilen seviye ve ücret yazar.
use crate::data::{ZKind, ZOMBIES};
use crate::world::{self, Zone, TILE};
use serde_json::{json, Value};

pub struct Area {
    pub id: &'static str,
    pub name: &'static str,
    /// karo cinsinden dikdörtgenler (x0, y0, x1, y1); birleşimi bölgedir
    pub rects: &'static [(f32, f32, f32, f32)],
    /// büyücünün ışınlaması için gereken seviye
    pub level: u32,
    /// önerilen seviye aralığı
    pub rec: (u32, u32),
    pub cost: u32,
    pub zones: &'static [Zone],
    pub desc: &'static str,
}

pub const FOREST: usize = 0;
pub const CITY: usize = 1;
pub const FRONT: usize = 7;
pub const HOME: usize = 8;

const W: f32 = world::WORLD_TILES as f32;
const O: f32 = world::OLD_TILES as f32;
const N: f32 = world::MAP_TILES as f32;
/// şehir suru (WALL_Y + 1 karo) iki bölgeyi ayırır
const WALL: f32 = world::WALL_Y as f32 + 1.0;

pub const AREAS: &[Area] = &[
    Area { id: "forest", name: "Başlangıç Ormanı", rects: &[(0.0, WALL, O, O)], level: 1, rec: (1, 15), cost: 0,
        zones: &[Zone::Camp, Zone::Forest, Zone::DeepForest, Zone::Swamp],
        desc: "Kamp, sahra hastanesi ve köpek barınağı burada. Kampın çevresi sakin; doğuya ve bataklığa gittikçe zombiler sertleşir." },
    Area { id: "city", name: "Yıkık Şehir", rects: &[(0.0, 0.0, O, WALL)], level: 10, rec: (10, 50), cost: 15,
        zones: &[Zone::SafeCity, Zone::Suburb, Zone::Downtown, Zone::Industrial, Zone::BossArena],
        desc: "Güvenli bölgeden banliyöye, merkeze ve sanayiye. Kuzeydoğudaki arenada Titan uyur." },
    Area { id: "burnt", name: "Yanık Orman", rects: &[(O, W / 4.0, W, O)], level: 15, rec: (15, 25), cost: 15,
        zones: &[Zone::BurntForest],
        desc: "Yangından geriye kül ve kömürleşmiş ağaçlar kaldı. Koşucular ve karanlık zombiler dumanın içinde avlanır." },
    Area { id: "desert", name: "Çöl Otoyolu", rects: &[(O / 2.0, O, O, W)], level: 20, rec: (20, 30), cost: 15,
        zones: &[Zone::Desert],
        desc: "Kumla örtülmüş otoyol ve terk edilmiş araçlar. Tank zombiler ve kuduz köpek sürüleri dolaşır." },
    Area { id: "bswamp", name: "Kara Bataklık", rects: &[(0.0, O, O / 2.0, W)], level: 25, rec: (25, 35), cost: 15,
        zones: &[Zone::BlackSwamp],
        desc: "Zehirli sis ve kara su. Toksik zombilerin yuvası; gizemli maskeliler de görülmüş." },
    Area { id: "military", name: "Askeri Bölge", rects: &[(O, 0.0, W, W / 4.0)], level: 35, rec: (35, 50), cost: 15,
        zones: &[Zone::Military],
        desc: "Terk edilmiş kışla, radar ve cephanelikler. Askerî sandıklar zengin, zombiler çok güçlü." },
    Area { id: "harbor", name: "Donmuş Liman", rects: &[(O, O, W, W)], level: 40, rec: (40, 50), cost: 15,
        zones: &[Zone::Harbor],
        desc: "Buzla kaplı rıhtım ve konteynerler. Dünyanın en tehlikeli zombileri burada." },
    Area { id: "front", name: "Cephe Hattı", rects: &[(W, 0.0, N, N), (0.0, W, N, N)], level: 10, rec: (28, 50), cost: 15,
        zones: &[Zone::Front],
        desc: "Devlet ve klan üslerinin kapıştığı savaş alanı. Zombi yok: üsleri askerler (botlar) korur. Klanınla üs ele geçir." },
    Area { id: "home", name: "Kişisel Üssüm", rects: &[(world::HOME_X0 as f32, world::HOME_Y0 as f32, world::HOME_X1 as f32 + 1.0, world::HOME_Y1 as f32 + 1.0)],
        level: 1, rec: (1, 50), cost: 0, zones: &[Zone::Home],
        desc: "Yalnız senin görebildiğin, duvarla çevrili güvenli üs. Sandıklarını burada kurarsın." },
];

/// Dünya pikselinin bölgesi (AREAS sırası)
pub fn area_at(x: f32, y: f32) -> usize {
    let (tx, ty) = (x / TILE, y / TILE);
    let inside = |a: &Area| a.rects.iter().any(|&(x0, y0, x1, y1)| tx >= x0 && tx < x1 && ty >= y0 && ty < y1);
    if inside(&AREAS[HOME]) {
        return HOME;
    }
    if tx >= W || ty >= W {
        return FRONT;
    }
    AREAS[..FRONT].iter().position(inside).unwrap_or(FOREST)
}

/// Bölge sınırı görünmez duvardır: (ox, oy) bölgesinden çıkan hareket eksen eksen geri alınır (sınır boyunca kayılır).
/// Sınıra takıldıysa true.
pub fn clamp(ox: f32, oy: f32, x: &mut f32, y: &mut f32) -> bool {
    let a = area_at(ox, oy);
    if area_at(*x, *y) == a {
        return false;
    }
    if area_at(*x, oy) == a {
        *y = oy;
    } else if area_at(ox, *y) == a {
        *x = ox;
    } else {
        *x = ox;
        *y = oy;
    }
    true
}

/// Bölgenin giriş noktası (büyücü ışınlaması ve ölünce doğma): bölgenin güvenli karakolu
pub fn hub(i: usize) -> (f32, f32) {
    let post = |id: &str| {
        let l = world::landmark(id);
        (l.tx * TILE, (l.ty + 2.5) * TILE)
    };
    match AREAS.get(i).map(|a| a.id).unwrap_or("forest") {
        "city" => world::city_spawn(),
        "burnt" => post("burnt_post"),
        "desert" => post("desert_post"),
        "bswamp" => post("bswamp_post"),
        "military" => post("military_post"),
        "harbor" => post("harbor_post"),
        "front" => world::front_spawn(),
        "home" => world::home_spawn(),
        _ => world::camp_spawn(),
    }
}

/// Bölgedeki yaratıklar: (tür, en düşük seviye, en yüksek seviye), ağırlığa göre sıralı
pub fn creatures(i: usize) -> Vec<(ZKind, u32, u32)> {
    let mut out: Vec<(ZKind, u32, u32, u32)> = Vec::new();
    for z in AREAS[i].zones {
        let (lo, hi) = z.level_range();
        for &(k, w) in z.spawns() {
            match out.iter_mut().find(|e| e.0 == k) {
                Some(e) => {
                    e.1 = e.1.min(lo);
                    e.2 = e.2.max(hi);
                    e.3 += w;
                }
                None => out.push((k, lo, hi, w)),
            }
        }
        if *z == Zone::BossArena {
            out.push((ZKind::Titan, 50, 50, 0));
        }
    }
    out.sort_by(|a, b| b.3.cmp(&a.3));
    out.into_iter().map(|(k, lo, hi, _)| (k, lo, hi)).collect()
}

/// Bölgede zombilerden ve sandıklardan çıkan başlıca eşyalar (combat.rs ganimet tablolarıyla aynı)
pub fn drops(i: usize) -> Vec<&'static str> {
    let mut out: Vec<&'static str> = Vec::new();
    let mut add = |v: &[&'static str]| {
        for id in v {
            if !out.contains(id) {
                out.push(id);
            }
        }
    };
    // bölgeye özel (seçkinlerde daha sık)
    for z in AREAS[i].zones {
        add(match z {
            Zone::Harbor => &["frozen_fish", "ice_shard"],
            Zone::BurntForest => &["scorched_resin", "charcoal"],
            Zone::Desert => &["scorpion_venom", "dried_hide"],
            Zone::BlackSwamp => &["swamp_moss", "bog_iron"],
            Zone::Military => &["circuit_board", "powder_pouch"],
            Zone::Downtown | Zone::Suburb | Zone::Industrial => &["gold_watch", "rusty_key"],
            _ => &[],
        });
    }
    let kinds = creatures(i);
    if !kinds.is_empty() {
        add(&["tooth", "bone", "cloth"]);
    }
    for (k, _, _) in kinds {
        add(match k {
            ZKind::Dog => &["leather"],
            ZKind::Toxic => &["toxic"],
            ZKind::Dark => &["dark_crystal", "neck_dark"],
            ZKind::Tank => &["scrap", "gear"],
            ZKind::Mystery => &["electronics", "ring_hunter", "neck_amulet"],
            ZKind::Titan => &["titan_shard", "ring_titan", "w_katana"],
            _ => &[],
        });
    }
    out
}

impl crate::game::Game {
    /// Büyücü haritası: tüm bölgeler (sınırlar, giriş noktası, kilit, yaratıklar, ganimet)
    pub fn areas_json(&self) -> Value {
        let here = area_at(self.player.x, self.player.y);
        let lv = self.progress.level;
        let list: Vec<Value> = AREAS
            .iter()
            .enumerate()
            .map(|(i, a)| {
                let (hx, hy) = hub(i);
                let mut mobs: Vec<Value> = creatures(i)
                    .iter()
                    .map(|&(k, lo, hi)| {
                        let d = &ZOMBIES[k as usize];
                        json!({ "name": d.name, "sprite": format!("z/{}/idle_0_0", d.sprite), "lo": lo, "hi": hi, "color": d.color })
                    })
                    .collect();
                if i == FRONT {
                    mobs.push(json!({ "name": "Üs Askeri (Bot)", "sprite": "npc/soldier", "lo": 28, "hi": 50, "color": 0xFFC8D0A0u32 }));
                    mobs.push(json!({ "name": "Keskin Nişancı (Bot)", "sprite": "npc/soldier", "lo": 28, "hi": 50, "color": 0xFFE0C080u32 }));
                }
                json!({
                    "id": a.id, "name": a.name, "desc": a.desc, "i": i,
                    "rects": a.rects.iter().map(|&(x0, y0, x1, y1)| json!([x0 * TILE, y0 * TILE, x1 * TILE, y1 * TILE])).collect::<Vec<_>>(),
                    "hub": [hx, hy], "level": a.level, "rec": [a.rec.0, a.rec.1], "cost": a.cost,
                    "here": i == here, "open": lv >= a.level, "safe": a.zones.iter().all(|z| z.safe()),
                    "mobs": mobs, "drops": drops(i),
                })
            })
            .collect();
        json!({ "areas": list, "here": here, "gold": self.progress.gold, "cd": self.wiz_cd.max(0.0), "near": self.near_wizard() })
    }

    /// Oyuncu bir büyücünün yanında mı (büyücü haritası yalnız onunla konuşurken açılır)
    pub fn near_wizard(&self) -> bool {
        let (px, py) = (self.player.x, self.player.y);
        self.npcs.iter().any(|n| n.kind == crate::story::N_WIZARD && (n.x - px).hypot(n.y - py) < 6.0 * crate::data::PX_PER_M)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hubs_are_safe_and_inside_their_area() {
        let g = crate::game::Game::new(7, crate::Progress::default(), 0);
        for (i, a) in AREAS.iter().enumerate() {
            let (x, y) = hub(i);
            assert_eq!(area_at(x, y), i, "{} girişi kendi bölgesinde değil", a.id);
            assert!(g.world.zone(x, y).safe(), "{} girişi güvenli değil", a.id);
            // her bölgenin girişinde büyücü var
            assert!(
                g.npcs.iter().any(|n| n.kind == crate::story::N_WIZARD && area_at(n.x, n.y) == i && (n.x - x).hypot(n.y - y) < 20.0 * TILE),
                "{} girişinde büyücü yok",
                a.id
            );
        }
    }

    #[test]
    fn areas_cover_the_map() {
        for ty in (0..world::MAP_TILES).step_by(7) {
            for tx in (0..world::MAP_TILES).step_by(7) {
                let i = area_at(tx as f32 * TILE + 1.0, ty as f32 * TILE + 1.0);
                assert!(i < AREAS.len());
            }
        }
        assert_eq!(area_at(world::CAMP.0, world::CAMP.1), FOREST);
        assert_eq!(area_at(world::BOSS.0, world::BOSS.1), CITY);
        for b in crate::bases::base_defs() {
            let (x, y) = b.pos();
            assert_eq!(area_at(x, y), FRONT, "üs {} Cephe Hattı'nda değil", b.id);
        }
    }

    #[test]
    fn front_has_no_zombies_and_every_area_lists_info() {
        assert!(creatures(FRONT).is_empty());
        assert!(Zone::Front.spawns().is_empty());
        for i in 0..AREAS.len() {
            if i != FRONT && i != HOME {
                assert!(!creatures(i).is_empty(), "{}", AREAS[i].id);
                assert!(!drops(i).is_empty(), "{}", AREAS[i].id);
            }
        }
        for i in 0..AREAS.len() {
            for id in drops(i) {
                assert!(crate::data::resolve_item_id(id).is_some(), "bilinmeyen eşya {id}");
            }
        }
    }
}
