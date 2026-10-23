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
pub const FRONT: usize = 6;
pub const HOME: usize = 7;
/// Titan'ın uyanabildiği bölgeler (başlangıç ormanı, cephe ve kişisel üs hariç)
pub const TITAN_AREAS: &[usize] = &[1, 2, 3, 4, 5];
/// Titan dilimi (sn)
pub const TITAN_SLOT: f64 = 900.0;

const W: f32 = world::WORLD_TILES as f32;
const O: f32 = world::OLD_TILES as f32;
const N: f32 = world::MAP_TILES as f32;
/// şehir suru (WALL_Y + 1 karo) iki bölgeyi ayırır
const WALL: f32 = world::WALL_Y as f32 + 1.0;

pub const AREAS: &[Area] = &[
    Area { id: "forest", name: "Başlangıç Ormanı", rects: &[(0.0, WALL, O, O)], level: 1, rec: (1, 15), cost: 0,
        zones: &[Zone::Camp, Zone::Forest, Zone::DeepForest, Zone::Swamp],
        desc: "Kamp, sahra hastanesi ve köpek barınağı burada. Kampın çevresi sakin; doğuya ve bataklığa gittikçe zombiler sertleşir." },
    Area { id: "city", name: "Yıkık Şehir", rects: &[(0.0, 0.0, O, WALL)], level: 10, rec: (10, 50), cost: 30,
        zones: &[Zone::SafeCity, Zone::Suburb, Zone::Downtown, Zone::Industrial, Zone::BossArena],
        desc: "Güvenli bölgeden banliyöye, merkeze ve sanayiye. Elektronik, dişli, pil ve paslı anahtar burada." },
    Area { id: "burnt", name: "Yanık Orman", rects: &[(O, 0.0, W, O)], level: 15, rec: (15, 25), cost: 45,
        zones: &[Zone::BurntForest],
        desc: "Yangından geriye kül ve kömürleşmiş ağaçlar kaldı. Koşucular ve karanlık zombiler dumanın içinde avlanır." },
    Area { id: "desert", name: "Çöl Otoyolu", rects: &[(O / 2.0, O, O, W)], level: 20, rec: (20, 30), cost: 60,
        zones: &[Zone::Desert],
        desc: "Kumla örtülmüş otoyol ve terk edilmiş araçlar. Tank zombiler ve kuduz köpek sürüleri dolaşır." },
    Area { id: "bswamp", name: "Kara Bataklık", rects: &[(0.0, O, O / 2.0, W)], level: 25, rec: (25, 35), cost: 75,
        zones: &[Zone::BlackSwamp],
        desc: "Zehirli sis ve kara su. Toksik zombilerin yuvası; gizemli maskeliler de görülmüş." },
    Area { id: "harbor", name: "Donmuş Liman", rects: &[(O, O, W, W)], level: 40, rec: (40, 50), cost: 100,
        zones: &[Zone::Harbor],
        desc: "Buzla kaplı rıhtım ve konteynerler. Dünyanın en tehlikeli zombileri burada." },
    Area { id: "front", name: "Cephe Hattı", rects: &[(W, 0.0, N, N), (0.0, W, N, N)], level: 10, rec: (28, 50), cost: 40,
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
        "harbor" => post("harbor_post"),
        "front" => world::front_spawn(),
        "home" => world::home_spawn(),
        _ => world::camp_spawn(),
    }
}

/// Bölgedeki yaratıklar: (tür, en düşük seviye, en yüksek seviye), ağırlığa göre sıralı (Titan ayrıca: titan_area)
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
    }
    out.sort_by(|a, b| b.3.cmp(&a.3));
    out.into_iter().map(|(k, lo, hi, _)| (k, lo, hi)).collect()
}

/// Bölgede çıkan eşyalar: önce bölgeye özel malzemeler (data::region_items), sonra temel malzemeler ve nadir hazır eşyalar
pub fn drops(i: usize) -> Vec<&'static str> {
    let mut out: Vec<&'static str> = Vec::new();
    let mut add = |v: &[&'static str]| {
        for id in v {
            if !out.contains(id) {
                out.push(id);
            }
        }
    };
    for z in AREAS[i].zones {
        let ids: Vec<&'static str> = crate::data::region_items(*z).iter().map(|e| e.0).collect();
        add(&ids);
    }
    let kinds = creatures(i);
    if !kinds.is_empty() {
        add(&["bone", "cloth"]);
    }
    for (k, _, _) in kinds {
        add(match k {
            ZKind::Dog => &["leather"],
            ZKind::Tank => &["scrap"],
            ZKind::Dark => &["neck_dark"],
            ZKind::Mystery => &["ring_hunter", "neck_amulet"],
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
                if i == self.titan.1 && !self.titan_dead {
                    let d = &ZOMBIES[ZKind::Titan as usize];
                    mobs.insert(0, json!({ "name": format!("{} (Şimdi Burada!)", d.name), "sprite": format!("z/{}/idle_0_0", d.sprite), "lo": 50, "hi": 50, "color": d.color }));
                }
                if i == FRONT {
                    mobs.push(json!({ "name": "Üs Askeri (Bot)", "sprite": "npc/soldier", "lo": 28, "hi": 50, "color": 0xFFC8D0A0u32 }));
                    mobs.push(json!({ "name": "Keskin Nişancı (Bot)", "sprite": "npc/soldier", "lo": 28, "hi": 50, "color": 0xFFE0C080u32 }));
                }
                json!({
                    "id": a.id, "name": a.name, "desc": a.desc, "i": i,
                    "rects": a.rects.iter().map(|&(x0, y0, x1, y1)| json!([x0 * TILE, y0 * TILE, x1 * TILE, y1 * TILE])).collect::<Vec<_>>(),
                    "hub": [hx, hy], "level": a.level, "rec": [a.rec.0, a.rec.1], "cost": a.cost,
                    "here": i == here, "open": lv >= a.level, "safe": a.zones.iter().all(|z| z.safe()),
                    "mobs": mobs, "drops": drops(i), "titan": i == self.titan.1 && !self.titan_dead,
                })
            })
            .collect();
        let (_, ta, tx, ty) = self.titan;
        json!({ "areas": list, "here": here, "gold": self.progress.gold, "cd": self.wiz_cd.max(0.0), "near": self.near_wizard(),
            "titan": { "area": ta, "name": AREAS.get(ta).map(|a| a.name), "x": tx, "y": ty, "left": self.titan_left(), "dead": self.titan_dead },
            // gezgin tüccar kamyonetleri (haritada işaret)
            "merchant": self.caravans.iter().map(|c| { let p = self.caravan_park(c);
                json!({ "x": c.x, "y": c.y, "parked": c.parked, "area": c.area, "park": [p.0, p.1] }) }).collect::<Vec<_>>() })
    }

    /// Titan zaman dilimi (15 dk): sunucu saati (üs saati) geldiyse onunla (her oyuncuda aynı), yoksa oyun süresiyle
    pub fn titan_slot(&self) -> i64 {
        if self.base_clock > 0.0 {
            (self.base_clock / (TITAN_SLOT * 1000.0)) as i64
        } else {
            (self.time as f64 / TITAN_SLOT) as i64
        }
    }

    /// Dilimin Titan'ı: bölge ve yer (tohum + dilimden; her istemcide aynı). Güvenli olmayan, boş bir yer seçilir.
    pub fn titan_spot(&self, slot: i64) -> (usize, f32, f32) {
        let mut rng = crate::rng::Rng::new(self.world.seed ^ 0x7174_A400 ^ (slot as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15));
        let a = TITAN_AREAS[rng.below(TITAN_AREAS.len() as u32) as usize];
        let mut scratch = Vec::new();
        for _ in 0..60 {
            let r = AREAS[a].rects[0];
            let tx = rng.range(r.0 + 20.0, r.2 - 20.0);
            let ty = rng.range(r.1 + 20.0, r.3 - 20.0);
            let (x, y) = (tx * TILE, ty * TILE);
            let z = self.world.zone(x, y);
            if !z.safe() && z != Zone::Wall && area_at(x, y) == a && !self.world.blocked(x, y, 40.0, &mut scratch) {
                return (a, x, y);
            }
        }
        let (hx, hy) = hub(a);
        (a, hx + 30.0 * TILE, hy)
    }

    /// V12.1: Titan her 15 dakikada başka bir bölgede uyanır (sabit arena yok). Dilim değişince eskisi kaybolur ve
    /// herkese duyurulur; oyuncu yaklaşınca doğar; o dilimde öldürülürse bir sonrakine kadar gelmez.
    pub(crate) fn update_titan(&mut self, px: f32, py: f32) {
        let slot = self.titan_slot();
        if slot != self.titan.0 {
            let (a, x, y) = self.titan_spot(slot);
            self.titan = (slot, a, x, y);
            self.titan_dead = false;
            self.zombies.retain(|z| !z.boss);
            self.msg(format!("TİTAN {} bölgesinde uyandı! 15 dakika orada kalacak.", AREAS[a].name));
        }
        let (_, _, bx, by) = self.titan;
        let alive = self.zombies.iter().any(|z| z.boss && z.state != crate::game::ZState::Dead);
        if !alive && !self.titan_dead && (bx - px).hypot(by - py) < 1600.0 {
            self.spawn_zombie_at(ZKind::Titan, 50, bx, by);
            self.events.push(crate::game::Event::Boss { name: "TİTAN".into() });
        }
    }

    /// Titan'ın kalan süresi (sn, dilim sonuna kadar)
    pub fn titan_left(&self) -> f32 {
        if self.base_clock > 0.0 {
            (TITAN_SLOT - (self.base_clock / 1000.0) % TITAN_SLOT) as f32
        } else {
            (TITAN_SLOT - self.time as f64 % TITAN_SLOT) as f32
        }
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
    fn titan_moves_every_slot_and_stays_dead_when_killed() {
        let mut g = crate::game::Game::new(7, crate::Progress::default(), 0);
        let mut seen = std::collections::HashSet::new();
        for slot in 0..40 {
            let (a, x, y) = g.titan_spot(slot);
            assert!(TITAN_AREAS.contains(&a));
            assert_eq!(area_at(x, y), a);
            assert!(!g.world.zone(x, y).safe());
            assert_eq!(g.titan_spot(slot), (a, x, y), "aynı dilim aynı yer");
            seen.insert(a);
        }
        assert!(seen.len() >= 4, "Titan hep aynı bölgelerde: {seen:?}");
        // sunucu saatiyle dilim; yaklaşınca doğar
        g.base_clock = 1_800_000_000_000.0;
        g.update_titan(0.0, 0.0);
        let (_, _, bx, by) = g.titan;
        g.update_titan(bx, by + 300.0);
        assert!(g.zombies.iter().any(|z| z.boss));
        // öldürüldü: dilim bitene kadar gelmez
        g.zombies.clear();
        g.titan_dead = true;
        g.update_titan(bx, by + 300.0);
        assert!(!g.zombies.iter().any(|z| z.boss));
        // yeni dilim: başka yer ve yeniden uyanır
        g.base_clock += TITAN_SLOT * 1000.0;
        g.update_titan(0.0, 0.0);
        assert!(!g.titan_dead);
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
