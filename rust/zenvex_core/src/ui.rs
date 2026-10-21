//! Arayüz köprüsü: Kotlin ekranları için JSON görünümleri ve JSON komutları (geliştirici hileleri dahil).
use crate::data::{
    self, ItemKind, CHARACTERS, COSMETICS, ITEMS, MERCHANT, PASS_REWARDS, RECIPES,
};
use crate::game::*;
use crate::progress::{self, SKILLS};
use crate::world;
use serde_json::{json, Value};

const NEAR: f32 = 150.0;
pub const DOCTOR_PRICE: u32 = 20;
pub const MECHANIC_PRICE: u32 = 30;

fn item_json(id: &str) -> Value {
    match data::item_index(id) {
        Some(i) if !id.is_empty() => {
            let d = &ITEMS[i];
            json!({ "i": i, "id": d.id, "name": d.name, "kind": d.kind, "rarity": d.rarity as u8, "desc": d.desc,
                    "armor": d.armor, "bonus": d.bonus, "value": d.value })
        }
        _ => Value::Null,
    }
}

fn reply(ok: bool, msg: impl Into<String>) -> String {
    json!({ "ok": ok, "msg": msg.into() }).to_string()
}

fn skin_weapon(cos: &str) -> Option<&'static str> {
    Some(match cos {
        "skin_glock_camo" => "w_glock",
        "skin_ak_gold" => "w_ak47",
        "skin_m4_neon" => "w_m4",
        "skin_sniper_titan" => "w_sniper",
        "skin_rpg_gold" => "w_rpg",
        _ => return None,
    })
}

/// Bandaj ve ilk yardım çantası arası bekleme (sn)
const HEAL_ITEM_CD: f32 = 4.0;

impl Game {
    pub fn near_bench(&self) -> bool {
        let (px, py) = (self.player.x, self.player.y);
        self.world
            .benches
            .iter()
            .any(|&(x, y)| (x - px).hypot(y - py) < NEAR)
    }

    pub fn near_merchant(&self) -> bool {
        let (px, py) = (self.player.x, self.player.y);
        self.world
            .merchants
            .iter()
            .any(|&(x, y)| (x - px).hypot(y - py) < NEAR)
    }

    /// Kamp doktoru: 20 altına canı doldurur, zehri temizler.
    pub(crate) fn npc_doctor(&mut self) -> Result<String, String> {
        if !self.near_npc(3) {
            return Err("Doktor sahra hastanesinde".into());
        }
        let max = self.progress.max_hp();
        if self.player.hp >= max - 0.5 && self.player.poison_t <= 0.0 {
            return Err("Sağlığın yerinde".into());
        }
        if self.progress.gold < DOCTOR_PRICE {
            return Err(format!("{DOCTOR_PRICE} altın gerekli"));
        }
        self.progress.gold -= DOCTOR_PRICE;
        self.player.hp = max;
        self.player.poison_t = 0.0;
        Ok(format!("Doktor yaralarını sardı (-{DOCTOR_PRICE} altın)"))
    }

    /// Kamp tamircisi: 12 m içindeki araçları onarır ve yakıtı 100'e tamamlar (30 altın).
    pub(crate) fn npc_mechanic(&mut self) -> Result<String, String> {
        if !self.near_npc(4) {
            return Err("Tamirci benzin istasyonunda".into());
        }
        let (px, py) = (self.player.x, self.player.y);
        let r = crate::game::m(12.0);
        let near: Vec<usize> = (0..self.vehicles.len())
            .filter(|&i| {
                let v = &self.vehicles[i];
                (v.x - px).hypot(v.y - py) < r && (v.hp < data::VEHICLES[v.kind].hp || v.fuel < 100.0 || v.wrecked)
            })
            .collect();
        if near.is_empty() {
            return Err("Yakında tamir edilecek araç yok".into());
        }
        if self.progress.gold < MECHANIC_PRICE {
            return Err(format!("{MECHANIC_PRICE} altın gerekli"));
        }
        self.progress.gold -= MECHANIC_PRICE;
        for i in near.iter().copied() {
            let v = &mut self.vehicles[i];
            v.hp = data::VEHICLES[v.kind].hp;
            v.fuel = 100.0;
            v.wrecked = false;
        }
        Ok(format!("{} araç onarıldı (-{MECHANIC_PRICE} altın)", near.len()))
    }

    /// Kamp NPC'leri tüccarın çevresinde ~6 m içinde durur.
    /// Belirli türde bir NPC'ye yakın mı (konuşma mesafesi)
    pub(crate) fn near_npc(&self, kind: u8) -> bool {
        let (px, py) = (self.player.x, self.player.y);
        self.npcs.iter().any(|n| n.kind == kind && (n.x - px).hypot(n.y - py) < crate::game::m(4.0))
    }

    fn buy_price(&self, base: u32) -> u32 {
        ((base as f32) * (1.0 - self.progress.trade_bonus()))
            .ceil()
            .max(1.0) as u32
    }

    fn sell_price(&self, item: usize) -> u32 {
        ((ITEMS[item].value as f32) * (0.5 + self.progress.trade_bonus())).floor() as u32
    }

    // ------------------------------------------------------------------ görünümler
    pub fn ui_json(&self, kind: &str) -> String {
        let g = &self.progress;
        let v = match kind {
            "tree" => {
                let t = crate::rpg::tree();
                let nodes: Vec<Value> = t.iter().map(|n| {
                    let owned = n.id == 0 || g.tree.contains(&n.id);
                    let avail = !owned && n.links.iter().any(|&l| l == 0 || g.tree.contains(&l));
                    json!({ "id": n.id, "x": n.x, "y": n.y, "b": n.branch, "k": n.kind, "s": format!("{:?}", n.stat), "name": n.name, "desc": n.desc,
                            "links": n.links, "own": owned, "av": avail, "cost": g.tree_cost(n.id), "req": g.tree_req_level(n.id) })
                }).collect();
                json!({ "points": g.tree_points(), "gems": g.gems, "credit": g.tree_credit, "spent": g.tree_spent, "level": g.level,
                        "reset_cost": g.tree_reset_cost(), "branches": crate::rpg::BRANCHES,
                        "nodes": nodes, "perks": crate::rpg::WARRIOR_PERKS, "char_level": g.char_level[0],
                        "char_xp": g.char_xp[0], "char_next": data::char_xp_to_next(g.char_level[0]) })
            }
            "dog" => {
                let lv = g.dog_lv;
                let stats: Vec<Value> = (0..3).map(|i| {
                    let val = match i { 0 => crate::rpg::dog_damage(lv[i]), 1 => crate::rpg::dog_hp(lv[i]), _ => crate::rpg::dog_regen(lv[i]) };
                    json!({ "name": crate::rpg::DOG_STATS[i], "lv": lv[i], "max": crate::rpg::DOG_MAX, "val": val,
                            "cost": crate::rpg::dog_upgrade_cost(lv[i]) })
                }).collect();
                let d = self.dog.as_ref();
                json!({ "owned": g.dog_owned, "price": crate::rpg::DOG_PRICE, "gold": g.gold, "near": self.near_npc(5),
                        "stats": stats, "hp": d.map_or(0.0, |d| d.hp), "dead": d.map_or(0.0, |d| d.dead_t) })
            }
            "cheat" => {
                let items: Vec<Value> = ITEMS.iter().filter(|d| d.kind != ItemKind::Currency && data::item_in_game(d.id))
                    .map(|d| json!([d.id, d.name, format!("{:?}", d.kind), d.rarity as u8])).collect();
                json!({ "god": self.player.god, "onehit": self.player.one_hit, "speed": self.player.speed_cheat,
                        "net": self.net, "level": g.level, "gold": g.gold, "items": items,
                        "infammo": self.player.inf_ammo, "nocd": self.player.no_cd, "invis": self.player.invis, "timescale": self.time_scale,
                        "x": (self.player.x / world::TILE) as i32, "y": (self.player.y / world::TILE) as i32 })
            }
            "profile" => {
                let c = g.character;
                json!({
                    "name": g.name, "level": g.level, "xp": g.xp, "xp_next": progress::xp_to_next(g.level), "points": g.tree_points(),
                    "gold": g.gold, "gems": g.gems, "character": c, "char_name": CHARACTERS[c].name, "char_class": CHARACTERS[c].class,
                    "char_level": g.char_level[c], "frame": g.frame, "tag": g.tag,
                    "xp_total": (1..g.level).map(|l| progress::xp_to_next(l) as u64).sum::<u64>() + g.xp as u64,
                    "tag_name": COSMETICS.iter().find(|x| x.id == g.tag).map_or("", |x| x.name),
                    "costume": g.costume[c], "pass_tier": g.pass_tier(), "pass_normal": g.pass.normal, "pass_elite": g.pass.elite,
                    "city": g.level >= progress::CITY_LEVEL, "zone": world::ZONES_ALL[self.player.zone as usize].name(),
                    "stats": { "kills": g.kills, "deaths": g.deaths, "trees": g.trees, "crates": g.crates, "crafted": g.crafted,
                               "boss": g.boss_kills, "gold_earned": g.gold_earned, "time": g.play_time as u64 },
                    // etkinlik puanı için sayaçlar (zombi türüne göre öldürme dahil)
                    "ev": { "kills_by": g.kills_by, "boss": g.boss_kills, "soldiers": g.ev.soldier_kills, "caps": g.ev.base_caps,
                            "dog": g.ev.dog_kills, "danger": g.ev.danger_secs as u64, "pvp": g.ev.pvp_kills, "trees": g.trees, "crates": g.crates, "crafted": g.crafted },
                    "drone_targets": g.drone_targets,
                })
            }
            "bases" => self.bases_json(),
            "base_near" => self.base_near_json(),
            "fog" => {
                let n = crate::progress::FOG_N;
                let rows: Vec<String> = g.fog_rows();
                json!({ "n": n, "rows": rows })
            }
            "inventory" => {
                let slots: Vec<Value> = g
                    .inv
                    .slots
                    .iter()
                    .map(|s| match s {
                        Some(s) => {
                            let id = ITEMS[s.item as usize].id;
                            let mut v = item_json(id);
                            v["count"] = json!(s.count);
                            if let Some((left, max)) = self.item_cd(id) {
                                v["cd"] = json!(left);
                                v["cdmax"] = json!(max);
                            }
                            v
                        }
                        None => Value::Null,
                    })
                    .collect();
                let e = &g.eq;
                let mut eq = serde_json::Map::new();
                for k in crate::progress::Equipment::KEYS {
                    eq.insert(k.into(), item_json(e.get(k).map_or("", |s| s.as_str())));
                }
                // 4 bölme sekmesi: açık olanlar (aralık) + kapalı olanlar (hangi çanta gerekir)
                const NEED: [&str; 4] = ["", "Basit Çanta", "Polis Çantası", "Askerî Çanta"];
                const NAMES: [&str; 4] = ["Ana Çanta", "Bölme 2", "Bölme 3", "Bölme 4"];
                let pages: Vec<Value> = (0..crate::inventory::PAGES)
                    .map(|p| match g.inv.page_range(p) {
                        Some(r) => json!({ "start": r.start, "len": r.len(), "used": g.inv.page_used(p), "name": NAMES[p], "open": true }),
                        None => json!({ "start": 0, "len": 0, "used": 0, "name": NAMES[p], "open": false, "need": NEED[p] }),
                    })
                    .collect();
                let used = g.inv.slots.iter().skip(crate::inventory::POCKETS).filter(|s| s.is_some()).count();
                json!({
                    "slots": slots, "cap": g.inv.slots.len().saturating_sub(crate::inventory::POCKETS), "used": used,
                    "pockets": crate::inventory::POCKETS, "pages": pages, "eq": eq,
                    "armor": g.armor(), "max_hp": g.max_hp(), "dmg": g.damage_mult(), "speed": g.move_mult(), "crit": g.crit_chance(),
                    "gold": g.gold, "gems": g.gems, "in_game_hp": self.player.hp,
                    "chest": self.near_chest().map_or(-1, |i| i as i64), "home": self.at_home(),
                })
            }
            "craft" => {
                let bench = self.near_bench();
                let list: Vec<Value> = RECIPES
                    .iter()
                    .enumerate()
                    .map(|(i, r)| {
                        let inputs: Vec<Value> = r
                            .inputs
                            .iter()
                            .map(|(id, n)| json!({ "id": id, "name": data::item(id).name, "need": n, "have": g.inv.count_id(id) }))
                            .collect();
                        let has = r.inputs.iter().all(|(id, n)| g.inv.count_id(id) >= *n as u32);
                        json!({ "i": i, "out": item_json(r.out), "count": r.count, "inputs": inputs, "bench": r.bench,
                                "can": has && (!r.bench || bench) })
                    })
                    .collect();
                json!({ "near_bench": bench, "recipes": list })
            }
            "merchant" => {
                let buy: Vec<Value> = MERCHANT
                    .iter()
                    .enumerate()
                    .map(|(i, (id, price, req))| {
                        json!({ "i": i, "item": item_json(id), "price": self.buy_price(*price), "req": req,
                                "locked": g.level < *req, "afford": g.gold >= self.buy_price(*price) })
                    })
                    .collect();
                let sell: Vec<Value> = g
                    .inv
                    .slots
                    .iter()
                    .enumerate()
                    .filter_map(|(i, s)| {
                        let s = (*s)?;
                        let d = &ITEMS[s.item as usize];
                        if d.kind == ItemKind::Currency || d.kind == ItemKind::Cloak {
                            return None;
                        }
                        Some(json!({ "slot": i, "item": item_json(d.id), "count": s.count, "price": self.sell_price(s.item as usize) }))
                    })
                    .collect();
                json!({ "gold": g.gold, "near": self.near_merchant(), "buy": buy, "sell": sell, "bonus": g.trade_bonus() })
            }
            "skills" => {
                let list: Vec<Value> = SKILLS
                    .iter()
                    .map(|s| {
                        json!({ "id": s.id, "branch": format!("{:?}", s.branch), "name": s.name, "desc": s.desc, "max": s.max,
                                "req": s.req_level, "icon": s.icon, "lvl": g.skills[s.id], "can": g.can_upgrade(s.id).is_ok(),
                                "why": g.can_upgrade(s.id).err().unwrap_or("") })
                    })
                    .collect();
                json!({ "points": g.points, "level": g.level, "reset_cost": 200 * g.level, "skills": list })
            }
            "characters" => {
                let list: Vec<Value> = CHARACTERS
                    .iter()
                    .enumerate()
                    .map(|(i, c)| {
                        json!({ "i": i, "id": c.id, "name": c.name, "class": c.class, "desc": c.desc, "ability": c.ability,
                                "ability_desc": c.ability_desc, "unlocked": g.unlocked[i], "level": g.char_level[i],
                                "xp": g.char_xp[i], "xp_next": data::char_xp_to_next(g.char_level[i]), "max": progress::MAX_CHAR_LEVEL,
                                "gold": c.unlock_gold, "req": c.unlock_level, "selected": g.character == i,
                                "can_unlock": !g.unlocked[i] && g.level >= c.unlock_level && g.gold >= c.unlock_gold,
                                "costume": g.costume[i] })
                    })
                    .collect();
                json!({ "chars": list, "gold": g.gold, "level": g.level })
            }
            "quests" => {
                let main = data::MAIN_QUESTS.get(g.quests.main).map(|q| {
                    let prog = if q.kind == data::QuestKind::Level { g.level } else { g.quests.main_progress };
                    json!({ "title": q.title, "goal": q.goal, "progress": prog.min(q.goal), "gold": q.gold, "xp": q.xp, "pass_xp": q.pass_xp,
                            "index": g.quests.main + 1, "total": data::MAIN_QUESTS.len() })
                });
                let daily: Vec<Value> = g
                    .quests
                    .daily
                    .iter()
                    .filter_map(|(id, prog, done)| {
                        let q = data::DAILY_POOL.iter().find(|q| q.id == id)?;
                        Some(json!({ "title": q.title, "goal": q.goal, "progress": (*prog).min(q.goal), "done": done,
                                     "gold": q.gold, "xp": q.xp, "pass_xp": q.pass_xp }))
                    })
                    .collect();
                json!({ "main": main, "daily": daily, "story": self.story_json() })
            }
            "pass" => {
                let tier = g.pass_tier();
                let rewards: Vec<Value> = PASS_REWARDS
                    .iter()
                    .map(|(t, free, prem)| {
                        json!({ "tier": t, "free": reward_json(free), "premium": reward_json(prem),
                                "free_claimed": g.pass.claimed_free.contains(t), "premium_claimed": g.pass.claimed_premium.contains(t),
                                "reached": *t <= tier })
                    })
                    .collect();
                json!({ "season": "Sezon 1: Tanrıların Düşüşü", "tier": tier, "xp": g.pass.xp % data::PASS_XP_PER_TIER,
                        "xp_per_tier": data::PASS_XP_PER_TIER, "tiers": data::PASS_TIERS, "normal": g.pass.normal, "elite": g.pass.elite,
                        "price_normal": data::PASS_PRICE_NORMAL, "price_elite": data::PASS_PRICE_ELITE, "rewards": rewards })
            }
            "cosmetics" => {
                let list: Vec<Value> = COSMETICS
                    .iter()
                    .map(|c| {
                        let owned = g.cosmetics.iter().any(|o| o == c.id);
                        let equipped = g.frame == c.id || g.tag == c.id || g.costume.iter().any(|x| x == c.id)
                            || g.skins.iter().any(|(_, s)| s == c.id);
                        json!({ "id": c.id, "name": c.name, "kind": c.kind, "owned": owned, "equipped": equipped })
                    })
                    .collect();
                json!({ "items": list, "gems": g.gems })
            }
            k if k == "chests" || k.starts_with("chest:") => self.chests_json(k).unwrap_or(Value::Null),
            _ => json!({ "error": "bilinmeyen görünüm" }),
        };
        v.to_string()
    }

    // ------------------------------------------------------------------ komutlar
    fn travel(&mut self, to: &str) -> Result<String, String> {
        let Some(t) = world::TRAVEL.iter().find(|t| t.id == to) else {
            return Err("Bilinmeyen bölge".into());
        };
        if self.player.dead {
            return Err("Önce yeniden doğ".into());
        }
        if self.progress.level < t.level {
            return Err(format!("{} için seviye {} gerekli", t.name, t.level));
        }
        if self.player.combat_t < 5.0 {
            return Err("Çatışmadayken seyahat edemezsin".into());
        }
        if self.progress.gold < t.cost {
            return Err(format!("{} altın gerekli", t.cost));
        }
        self.progress.gold -= t.cost;
        let (x, y) = t.pos();
        self.teleport(x, y);
        self.zombies.retain(|z| (z.x - x).hypot(z.y - y) > 700.0 || z.boss);
        Ok(format!("{} bölgesine seyahat edildi", t.name))
    }

    pub fn command(&mut self, s: &str) -> String {
        let v: Value = serde_json::from_str(s).unwrap_or(Value::Null);
        let cmd = v["cmd"].as_str().unwrap_or("");
        let int = |k: &str| v[k].as_u64().unwrap_or(0) as usize;
        // çanta komutlarından önce düzen güncel olsun (seviye atlayınca ana çanta büyür)
        if matches!(cmd, "equip" | "unequip" | "use" | "drop" | "move" | "split" | "move_page" | "sort" | "chest_take" | "chest_all") {
            self.progress.relayout_inv();
        }
        if let Some(r) = self.chest_command(cmd, &v) {
            return match r {
                Ok(m) => reply(true, m),
                Err(m) => reply(false, m),
            };
        }
        let res: Result<String, String> = match cmd {
            "equip" => self.progress.equip_slot(int("slot")).map(|n| format!("{n} kuşanıldı")).map_err(String::from),
            "unequip" => self.progress.unequip(v["slot"].as_str().unwrap_or("")).map(|_| "Çıkarıldı".into()).map_err(String::from),
            "use" => self.use_slot(int("slot")),
            "drop" => self.drop_slot(int("slot"), v["n"].as_u64().map(|n| n as u32)),
            "split" => self
                .progress
                .inv
                .split(int("slot"), v["n"].as_u64().unwrap_or(1) as u32)
                .map(|_| "Yığın bölündü".into())
                .map_err(String::from),
            "move_page" => self.progress.inv.move_to_page(int("from"), int("page")).map(|_| String::new()).map_err(String::from),
            "move" => {
                let to = int("to");
                let r = self.progress.inv.move_slot(int("from"), to).map(|_| String::new()).map_err(String::from);
                if r.is_ok() && crate::inventory::Inventory::is_pocket(to) {
                    self.story_event(crate::story::Ev::Pocket);
                }
                r
            }
            // klan deposuna verilen altın / eşya (sunucu kabul ettikten sonra çağrılır)
            "spend_gold" => {
                let n = int("n") as u32;
                if self.progress.gold < n {
                    Err("Yetersiz altın".into())
                } else {
                    self.progress.gold -= n;
                    Ok(String::new())
                }
            }
            "remove_item" => {
                let (slot, n) = (int("slot"), int("n") as u16);
                match self.progress.inv.slots.get_mut(slot) {
                    Some(Some(s)) if s.count >= n => {
                        s.count -= n;
                        if s.count == 0 {
                            self.progress.inv.slots[slot] = None;
                        }
                        Ok(String::new())
                    }
                    _ => Err("Eşya bulunamadı".into()),
                }
            }
            // yönetici işlemi (sunucu kuyruğundan): eşya sil, çantayı boşalt, altını ayarla
            "admin_action" => {
                let a = &v["a"];
                match a["t"].as_str().unwrap_or("") {
                    "remove_item" => {
                        // adet yoksa o eşyanın tamamı, varsa yığınlardan o kadar
                        let it = a["item"].as_u64().unwrap_or(u64::MAX) as usize;
                        let mut left = a["n"].as_u64().unwrap_or(u64::MAX);
                        for s in self.progress.inv.slots.iter_mut() {
                            if left == 0 {
                                break;
                            }
                            if let Some(x) = s {
                                if x.item as usize == it {
                                    if (x.count as u64) <= left {
                                        left -= x.count as u64;
                                        *s = None;
                                    } else {
                                        x.count -= left as u16;
                                        left = 0;
                                    }
                                }
                            }
                        }
                        Ok("Yönetici bir eşyanı kaldırdı".into())
                    }
                    "set_gems" => {
                        self.progress.gems = a["v"].as_u64().unwrap_or(0).min(u32::MAX as u64) as u32;
                        Ok("Yönetici elmasını ayarladı".into())
                    }
                    "clear_bag" => {
                        for s in self.progress.inv.slots.iter_mut() {
                            *s = None;
                        }
                        Ok("Yönetici çantanı boşalttı".into())
                    }
                    "set_gold" => {
                        self.progress.gold = a["v"].as_u64().unwrap_or(0).min(u32::MAX as u64) as u32;
                        Ok("Yönetici altınını ayarladı".into())
                    }
                    _ => Ok(String::new()),
                }
            }
            "clan_boost" => {
                for (i, k) in ["dmg", "xp", "speed"].iter().enumerate() {
                    self.clan_boost[i] = v[*k].as_u64().unwrap_or(0).min(5) as u8;
                }
                Ok(String::new())
            }
            "talk" => {
                let k = int("npc") as u8;
                if !self.near_npc(k) && k != 0 {
                    Err("Çok uzaktasın".into())
                } else {
                    Ok(self.talk(k, v["opt"].as_str().unwrap_or("")).to_string())
                }
            }
            "sort" => {
                match v["page"].as_u64() {
                    Some(p) => self.progress.inv.sort_page(p as usize),
                    None => self.progress.inv.sort(),
                }
                Ok("Sıralandı".into())
            }
            "craft" => self.craft(int("i"), v["n"].as_u64().unwrap_or(1).clamp(1, 50) as u32),
            "buy" => self.buy(int("i"), v["n"].as_u64().unwrap_or(1).clamp(1, 240) as u32),
            "sell" => self.sell(int("slot"), v["n"].as_u64().map(|n| n as u32)),
            "upgrade_skill" => self.progress.upgrade(int("id")).map(|_| "Yetenek geliştirildi".into()).map_err(String::from),
            "reset_skills" => self.progress.reset_skills().map(|_| "Yetenekler sıfırlandı".into()).map_err(String::from),
            "select_char" => self.select_char(int("i")),
            "unlock_char" => self.unlock_char(int("i")),
            "claim_pass" => self.claim_pass(v["tier"].as_u64().unwrap_or(0) as u32, v["premium"].as_bool().unwrap_or(false)),
            "buy_pass" => Err("Ödeme sistemi (Google Play Faturalandırma) henüz bağlı değil. Test için geliştirici menüsü: \"pass buy\"".into()),
            "equip_cosmetic" => self.equip_cosmetic(v["id"].as_str().unwrap_or("")),
            "set_name" => {
                let n: String = v["name"].as_str().unwrap_or("").trim().chars().filter(|c| !c.is_control()).take(16).collect();
                if n.chars().count() < 3 {
                    Err("İsim en az 3 harf olmalı".into())
                } else {
                    self.progress.name = n;
                    Ok("İsim değişti".into())
                }
            }
            "ability" => self.use_ability(),
            "vehicle" => self.toggle_vehicle(),
            "cycle_weapon" => self.cycle_weapon(),
            "reload" => {
                let w = self.weapon();
                if w.ammo.is_empty() {
                    Err("Bu silah dolmaz".into())
                } else {
                    self.start_reload(w);
                    Ok(String::new())
                }
            }
            "drone_targets" => {
                for k in 0..3 {
                    self.progress.drone_targets[k] = v["targets"][k].as_bool().unwrap_or(true);
                }
                Ok("Dron hedefleri kaydedildi".into())
            }
            "respawn" => {
                self.respawn(v["city"].as_bool().unwrap_or(false));
                Ok("Yeniden doğdun".into())
            }
            "cheat" if !self.cheats_ok => Err("Bu işlem için yönetici yetkisi gerekli".into()),
            "cheat" => self.cheat(v["text"].as_str().unwrap_or("")),
            // oyuncunun kendi isteğiyle sıfırlama (Ayarlar → Hesap); sunucudaki kayıt da sıfırlanır
            "reset_progress" => {
                self.progress = progress::Progress::default();
                self.respawn(false);
                Ok("Profil sıfırlandı".into())
            }
            // süresi dolan etkinlik ödülü: çantadan, sandıklardan ve kuşanılandan kalkar
            "expire_item" => {
                let id = v["id"].as_str().unwrap_or("").to_string();
                if let Some(idx) = crate::data::item_index(&id) {
                    for s in self.progress.inv.slots.iter_mut() {
                        if s.is_some_and(|x| x.item as usize == idx) {
                            *s = None;
                        }
                    }
                    for c in self.progress.chests.iter_mut() {
                        for s in c.slots.iter_mut() {
                            if s.is_some_and(|x| x.item as usize == idx) {
                                *s = None;
                            }
                        }
                    }
                    for k in crate::progress::Equipment::KEYS {
                        if let Some(e) = self.progress.eq.get_mut(k) {
                            if *e == id {
                                e.clear();
                            }
                        }
                    }
                    Ok(format!("{} süresi doldu", crate::data::item(&id).name))
                } else {
                    Err("Eşya yok".into())
                }
            }
            "set_admin" => {
                self.cheats_ok = v["on"].as_bool().unwrap_or(false);
                Ok(String::new())
            }
            "tree_unlock" => self.progress.tree_unlock(v["id"].as_u64().unwrap_or(0) as u16),
            "tree_reset" => self.progress.tree_reset(),
            "dog_buy" => self.dog_buy(),
            "doctor" => self.npc_doctor(),
            "mechanic" => self.npc_mechanic(),
            "dog_up" => self.dog_upgrade(int("stat")),
            "set_rules" => {
                self.hardcore = v["hardcore"].as_bool().unwrap_or(false);
                Ok(String::new())
            }
            "travel" => self.travel(v["to"].as_str().unwrap_or("")),
            // V8 devlet üsleri: sunucu durumu (Kotlin her 30 sn ve harita açılınca) / ele geçirme reddi
            "bases" => {
                let data: Value = match &v["data"] {
                    Value::String(t) => serde_json::from_str(t).unwrap_or(Value::Null),
                    o => o.clone(),
                };
                Ok(format!("{}", self.apply_bases(&data)))
            }
            "base_cap_fail" => {
                self.base_capture_failed(int("id"));
                Ok(String::new())
            }
            "mail_claim" => {
                let g = v["gold"].as_u64().unwrap_or(0) as u32;
                self.progress.add_gold(g);
                let mut got = Vec::new();
                if g > 0 {
                    got.push(format!("{g} altın"));
                }
                let gems = v["gems"].as_u64().unwrap_or(0) as u32;
                if gems > 0 {
                    self.progress.gems = self.progress.gems.saturating_add(gems);
                    got.push(format!("{gems} elmas"));
                }
                let xp = v["xp"].as_u64().unwrap_or(0) as u32;
                if xp > 0 {
                    let (x, y) = (self.player.x, self.player.y);
                    self.give_xp(x, y, xp);
                    got.push(format!("{xp} XP"));
                }
                for it in v["items"].as_array().cloned().unwrap_or_default() {
                    let id = it["id"].as_str().unwrap_or("");
                    let n = it["n"].as_u64().unwrap_or(1) as u32;
                    if crate::data::item_index(id).is_none() || n == 0 {
                        continue;
                    }
                    let left = self.progress.inv.add_id(id, n);
                    if left > 0 {
                        let (x, y) = (self.player.x, self.player.y);
                        self.spawn_drop_id(id, left, x, y);
                    }
                    got.push(format!("{n} {}", crate::data::item(id).name));
                }
                Ok(format!("Alındı: {}", got.join(", ")))
            }
            "net_mode" => {
                self.set_net(v["mode"].as_u64().unwrap_or(0) as u8);
                Ok(String::new())
            }
            "net_hit" => {
                let f = |k: &str| v[k].as_f64().unwrap_or(0.0) as f32;
                self.net_hit(v["id"].as_u64().unwrap_or(0) as u32, f("dmg"), f("kx"), f("ky"), v["stun"].as_bool().unwrap_or(false),
                             v["killer"].as_i64().unwrap_or(-1) as i32);
                Ok(String::new())
            }
            "net_kill" => {
                let f = |k: &str| v[k].as_f64().unwrap_or(0.0) as f32;
                self.net_kill(v["kind"].as_u64().unwrap_or(0) as u8, v["level"].as_u64().unwrap_or(1) as u32, f("x"), f("y"));
                Ok(String::new())
            }
            // PvP: düşman oyuncu vuruşu; döner: uygulandı mı
            "pvp_dmg" => {
                if self.pvp_damage(v["v"].as_f64().unwrap_or(0.0) as f32) {
                    Ok("hit".into())
                } else {
                    Err("PvP kapalı".into())
                }
            }
            "pvp_kill" => {
                self.progress.ev.pvp_kills += 1;
                let (x, y) = (self.player.x, self.player.y);
                self.give_xp(x, y, 120);
                Ok("Oyuncu öldürdün".into())
            }
            "capture_blocked" => {
                self.capture_blocked = v["on"].as_bool().unwrap_or(false);
                Ok(String::new())
            }
            "pvp_hostile" => {
                self.remote_hostile = v["h"].as_array().map(|a| a.iter().map(|b| b.as_bool().unwrap_or(false)).collect()).unwrap_or_default();
                Ok(String::new())
            }
            "net_pdmg" => {
                self.net_damage(v["v"].as_f64().unwrap_or(0.0) as f32, v["poison"].as_bool().unwrap_or(false));
                Ok(String::new())
            }
            "ride" => {
                let f = |k: &str| v[k].as_f64().unwrap_or(0.0) as f32;
                self.ride(v["on"].as_bool().unwrap_or(false), f("x"), f("y"));
                Ok(String::new())
            }
            "trade_take" => {
                // takas: verilen eşyayı envanterden çıkar
                let id = v["id"].as_str().unwrap_or("");
                let n = v["n"].as_u64().unwrap_or(1) as u32;
                if crate::data::item_bound(id) {
                    Err("Etkinlik ödülleri takas edilemez".into())
                } else if id == "coin" {
                    if self.progress.gold >= n {
                        self.progress.gold -= n;
                        Ok("ok".into())
                    } else {
                        Err("Yeterli altın yok".into())
                    }
                } else if self.progress.inv.remove_id(id, n) {
                    Ok("ok".into())
                } else {
                    Err("Eşya yok".into())
                }
            }
            "trade_give" => {
                // takas: karşı taraftan gelen eşyayı ekle
                let id = v["id"].as_str().unwrap_or("");
                let n = v["n"].as_u64().unwrap_or(1) as u32;
                if id == "coin" {
                    self.progress.add_gold(n);
                    Ok(format!("+{n} altın"))
                } else if crate::data::item_index(id).is_some() {
                    let left = self.progress.inv.add_id(id, n);
                    if left > 0 {
                        let (x, y) = (self.player.x, self.player.y);
                        self.spawn_drop_id(id, left, x, y);
                    }
                    Ok(format!("+{n} {}", crate::data::item(id).name))
                } else {
                    Err("Geçersiz eşya".into())
                }
            }
            _ => Err(format!("Bilinmeyen komut: {cmd}")),
        };
        match res {
            Ok(m) => reply(true, m),
            Err(m) => reply(false, m),
        }
    }

    /// Eşyanın bekleme süresi (kalan, toplam); beklemesi olmayan eşyada None.
    pub fn item_cd(&self, id: &str) -> Option<(f32, f32)> {
        let p = &self.player.pot_cd;
        match id {
            "potion_heal" => Some((p[0], 30.0)),
            "potion_speed" => Some((p[1], 60.0)),
            "potion_power" => Some((p[2], 60.0)),
            "bandage" | "medkit" => Some((p[3], HEAL_ITEM_CD)),
            _ => None,
        }
    }

    pub fn use_slot(&mut self, slot: usize) -> Result<String, String> {
        let s = self
            .progress
            .inv
            .slots
            .get(slot)
            .copied()
            .flatten()
            .ok_or("Boş slot")?;
        let d = &ITEMS[s.item as usize];
        match d.kind {
            ItemKind::Weapon
            | ItemKind::Helmet
            | ItemKind::Jacket
            | ItemKind::Boots
            | ItemKind::Bag
            | ItemKind::Pants
            | ItemKind::Ring
            | ItemKind::Necklace => {
                return self
                    .progress
                    .equip_slot(slot)
                    .map(|n| format!("{n} kuşanıldı"))
                    .map_err(String::from);
            }
            ItemKind::Chest => return self.place_chest(slot),
            ItemKind::Consumable => {}
            _ if d.id == "fuel" => {
                let vi = self.player.vehicle.ok_or("Önce bir araca bin")?;
                self.vehicles[vi].fuel = (self.vehicles[vi].fuel + 50.0).min(100.0);
            }
            _ => return Err("Bu eşya kullanılamaz".into()),
        }
        if self.player.dead {
            return Err("Ölüyken olmaz".into());
        }
        let max_hp = self.progress.max_hp();
        let p = &mut self.player;
        match d.id {
            "bandage" | "medkit" => {
                if p.hp >= max_hp {
                    return Err("Canın zaten dolu".into());
                }
                if p.pot_cd[3] > 0.0 {
                    return Err(format!("Bekleme: {:.0} sn", p.pot_cd[3]));
                }
                p.pot_cd[3] = if p.no_cd { 0.0 } else { HEAL_ITEM_CD };
                p.hp = (p.hp + d.bonus).min(max_hp);
                let (x, y) = (p.x, p.y);
                self.events.push(Event::Heal {
                    x,
                    y,
                    v: d.bonus as i32,
                });
            }
            "food" => p.food_t = 10.0,
            "energy" => {
                p.stamina = self.progress.max_stamina();
                p.energy_t = 10.0;
            }
            "antidote" => {
                if p.poison_t <= 0.0 {
                    return Err("Zehirlenmedin".into());
                }
                p.poison_t = 0.0;
            }
            "potion_heal" | "potion_speed" | "potion_power" => {
                let (k, cd) = match d.id { "potion_heal" => (0usize, 30.0), "potion_speed" => (1, 60.0), _ => (2, 60.0) };
                if p.pot_cd[k] > 0.0 {
                    return Err(format!("Bekleme: {:.0} sn", p.pot_cd[k]));
                }
                p.pot_cd[k] = if p.no_cd { 0.0 } else { cd };
                match k {
                    0 => {
                        p.heal_t = 10.0;
                        p.heal_rate = 5.0;
                    }
                    1 => p.speed_t = 30.0,
                    _ => p.power_t = 20.0,
                }
                self.events.push(Event::Potion { kind: k as u8 });
            }
            "fuel" => {}
            _ => return Err("Bu eşya kullanılamaz".into()),
        }
        self.progress.inv.remove(s.item as usize, 1);
        self.story_event(crate::story::Ev::Use(d.id));
        Ok(format!("{} kullanıldı", d.name))
    }

    /// Yere bırak: n = None → yığının tamamı, aksi halde n tane.
    fn drop_slot(&mut self, slot: usize, n: Option<u32>) -> Result<String, String> {
        if let Some(Some(x)) = self.progress.inv.slots.get(slot) {
            if crate::data::item_bound(ITEMS[x.item as usize].id) {
                return Err("Etkinlik ödülleri yere bırakılamaz".into());
            }
        }
        let s = self.progress.inv.take_n(slot, n).ok_or("Boş slot")?;
        let (x, y) = (self.player.x, self.player.y + 30.0);
        self.spawn_drop(s.item as usize, s.count as u32, x, y);
        if let Some(d) = self.drops.last_mut() {
            d.age = -1.5; // hemen geri toplanmasın
        }
        Ok(if s.count > 1 { format!("{} ×{} yere bırakıldı", ITEMS[s.item as usize].name, s.count) } else { format!("{} yere bırakıldı", ITEMS[s.item as usize].name) })
    }

    fn craft(&mut self, i: usize, n: u32) -> Result<String, String> {
        let r = RECIPES.get(i).ok_or("Tarif yok")?;
        if r.bench && !self.near_bench() {
            return Err("Bu tarif için çalışma tezgâhı gerekli".into());
        }
        let out = data::item_index(r.out).ok_or("Tarif hatalı")?;
        let mut made = 0;
        for _ in 0..n {
            if !r
                .inputs
                .iter()
                .all(|(id, k)| self.progress.inv.count_id(id) >= *k as u32)
            {
                break;
            }
            if !self.progress.inv.can_fit(out, r.count as u32) {
                if made == 0 {
                    return Err("Envanterde yer yok".into());
                }
                break;
            }
            for (id, k) in r.inputs {
                self.progress.inv.remove_id(id, *k as u32);
            }
            self.progress.inv.add(out, r.count as u32);
            made += 1;
        }
        if made == 0 {
            return Err("Malzeme yetersiz".into());
        }
        self.progress.crafted += made;
        for _ in 0..made {
            self.story_event(crate::story::Ev::Craft(ITEMS[out].id));
        }
        let done = self.progress.quest_event(data::QuestKind::Craft, made);
        self.quests_done(done);
        Ok(format!(
            "{}× {} üretildi",
            made * r.count as u32,
            ITEMS[out].name
        ))
    }

    fn buy(&mut self, i: usize, n: u32) -> Result<String, String> {
        let &(id, base, req) = MERCHANT.get(i).ok_or("Ürün yok")?;
        if self.progress.level < req {
            return Err(format!("Seviye {req} gerekli"));
        }
        let item = data::item_index(id).ok_or("Ürün hatalı")?;
        let n = if ITEMS[item].stack == 1 { 1 } else { n };
        let cost = self.buy_price(base) * n;
        if self.progress.gold < cost {
            return Err("Yeterli altının yok".into());
        }
        if !self.progress.inv.can_fit(item, n) {
            return Err("Envanterde yer yok".into());
        }
        self.progress.gold -= cost;
        self.progress.inv.add(item, n);
        Ok(format!(
            "{n}× {} satın alındı (-{cost} altın)",
            ITEMS[item].name
        ))
    }

    fn sell(&mut self, slot: usize, n: Option<u32>) -> Result<String, String> {
        let s = self
            .progress
            .inv
            .slots
            .get(slot)
            .copied()
            .flatten()
            .ok_or("Boş slot")?;
        let item = s.item as usize;
        let n = n.unwrap_or(s.count as u32).clamp(1, s.count as u32);
        let price = self.sell_price(item) * n;
        if let Some(Some(sl)) = self.progress.inv.slots.get_mut(slot) {
            sl.count -= n as u16;
            if sl.count == 0 {
                self.progress.inv.slots[slot] = None;
            }
        }
        self.progress.add_gold(price);
        Ok(format!(
            "{n}× {} satıldı (+{price} altın)",
            ITEMS[item].name
        ))
    }

    fn select_char(&mut self, i: usize) -> Result<String, String> {
        if i >= CHARACTERS.len() || !self.progress.unlocked[i] {
            return Err("Bu karakter kilitli".into());
        }
        self.progress.character = i;
        self.player.ability_cd = self.player.ability_cd.max(3.0);
        Ok(format!("{} seçildi", CHARACTERS[i].name))
    }

    fn unlock_char(&mut self, i: usize) -> Result<String, String> {
        let c = CHARACTERS.get(i).ok_or("Karakter yok")?;
        if self.progress.unlocked[i] {
            return Err("Zaten açık".into());
        }
        if self.progress.level < c.unlock_level {
            return Err(format!("Seviye {} gerekli", c.unlock_level));
        }
        if self.progress.gold < c.unlock_gold {
            return Err(format!("{} altın gerekli", c.unlock_gold));
        }
        self.progress.gold -= c.unlock_gold;
        self.progress.unlocked[i] = true;
        Ok(format!("{} açıldı!", c.name))
    }

    fn claim_pass(&mut self, tier: u32, premium: bool) -> Result<String, String> {
        let &(t, free, prem) = PASS_REWARDS
            .iter()
            .find(|r| r.0 == tier)
            .ok_or("Kademe yok")?;
        if t > self.progress.pass_tier() {
            return Err("Bu kademeye henüz ulaşmadın".into());
        }
        let p = &mut self.progress;
        if premium {
            if !(p.pass.normal || p.pass.elite) {
                return Err("Elit ödüller için Sezon Bileti gerekli".into());
            }
            if p.pass.claimed_premium.contains(&t) {
                return Err("Zaten alındı".into());
            }
            p.pass.claimed_premium.push(t);
            Ok(p.grant(prem))
        } else {
            if p.pass.claimed_free.contains(&t) {
                return Err("Zaten alındı".into());
            }
            p.pass.claimed_free.push(t);
            Ok(p.grant(free))
        }
    }

    fn activate_pass(&mut self, elite: bool) {
        let p = &mut self.progress;
        p.pass.normal = true;
        if elite && !p.pass.elite {
            p.pass.elite = true;
            p.pass.xp += 10 * data::PASS_XP_PER_TIER;
            p.grant("cos:tag_legend");
            p.grant("cos:frame_s1");
        }
    }

    fn equip_cosmetic(&mut self, id: &str) -> Result<String, String> {
        let p = &mut self.progress;
        let c = COSMETICS
            .iter()
            .find(|c| c.id == id)
            .ok_or("Kozmetik yok")?;
        if !p.cosmetics.iter().any(|o| o == id) {
            return Err("Bu kozmetiğe sahip değilsin".into());
        }
        let toggle = |slot: &mut String| {
            if slot == id {
                slot.clear();
                false
            } else {
                *slot = id.to_string();
                true
            }
        };
        let on = match c.kind {
            "frame" => toggle(&mut p.frame),
            "tag" => toggle(&mut p.tag),
            "costume" => {
                let ci = CHARACTERS
                    .iter()
                    .position(|ch| id.starts_with(ch.id))
                    .unwrap_or(0);
                toggle(&mut p.costume[ci])
            }
            _ => {
                let w = skin_weapon(id).ok_or("Kozmetik hatalı")?;
                match p.skins.iter().position(|(sw, _)| sw == w) {
                    Some(k) if p.skins[k].1 == id => {
                        p.skins.remove(k);
                        false
                    }
                    Some(k) => {
                        p.skins[k].1 = id.to_string();
                        true
                    }
                    None => {
                        p.skins.push((w.to_string(), id.to_string()));
                        true
                    }
                }
            }
        };
        Ok(if on {
            format!("{} kuşanıldı", c.name)
        } else {
            format!("{} çıkarıldı", c.name)
        })
    }

    /// Envanterdeki bir sonraki silaha geç (sıra: WEAPONS tablosu, sonunda yumruk).
    fn cycle_weapon(&mut self) -> Result<String, String> {
        let cur = data::weapon_for(&self.progress.eq.weapon);
        let owned: Vec<(usize, usize)> = self
            .progress
            .inv
            .slots
            .iter()
            .enumerate()
            .filter_map(|(i, s)| {
                let s = (*s)?;
                let d = &ITEMS[s.item as usize];
                (d.kind == ItemKind::Weapon).then(|| (data::weapon_for(d.id), i))
            })
            .collect();
        let next = owned
            .iter()
            .filter(|(w, _)| *w > cur)
            .min_by_key(|(w, _)| *w);
        match next {
            Some(&(_, slot)) => self
                .progress
                .equip_slot(slot)
                .map(|n| n.to_string())
                .map_err(String::from),
            None if cur != 0 => match self.progress.unequip("weapon") {
                Ok(()) => Ok("Yumruk".into()),
                Err(e) => Err(e.into()),
            },
            None => match owned.iter().min_by_key(|(w, _)| *w) {
                Some(&(_, slot)) => self
                    .progress
                    .equip_slot(slot)
                    .map(|n| n.to_string())
                    .map_err(String::from),
                None => Err("Başka silahın yok".into()),
            },
        }
    }

    // ------------------------------------------------------------------ geliştirici menüsü
    pub fn cheat(&mut self, text: &str) -> Result<String, String> {
        let parts: Vec<&str> = text.split_whitespace().collect();
        let arg = |i: usize| parts.get(i).copied().unwrap_or("");
        let num = |i: usize, d: f32| {
            parts
                .get(i)
                .and_then(|s| s.replace(',', ".").parse::<f32>().ok())
                .unwrap_or(d)
        };
        let (px, py) = (self.player.x, self.player.y);
        match arg(0) {
            "gold" | "altin" => {
                self.progress.add_gold(num(1, 10000.0) as u32);
                Ok(format!("Altın: {}", self.progress.gold))
            }
            "gem" | "elmas" => {
                self.progress.gems += num(1, 1000.0) as u32;
                Ok(format!("Elmas: {}", self.progress.gems))
            }
            "item" | "esya" => {
                let id = arg(1);
                if data::item_index(id).is_none() || !data::item_in_game(id) {
                    let ids: Vec<&str> = ITEMS.iter().filter(|i| data::item_in_game(i.id)).map(|i| i.id).collect();
                    return Err(format!("Eşya kimlikleri: {}", ids.join(", ")));
                }
                Ok(self.progress.grant(&format!("{id}:{}", num(2, 1.0) as u32)))
            }
            "items" | "malzeme" => {
                for d in ITEMS.iter().filter(|d| d.kind == ItemKind::Material) {
                    self.progress.inv.add_id(d.id, 20);
                }
                Ok("Tüm malzemelerden 20'şer eklendi".into())
            }
            "weapons" | "silahlar" => {
                for id in ["w_knife", "w_katana"] {
                    self.progress.inv.add_id(id, 1);
                }
                Ok("Bıçak ve katana eklendi".into())
            }
            "level" | "seviye" => {
                let target = (num(1, 10.0) as u32).clamp(1, progress::MAX_LEVEL);
                let p = &mut self.progress;
                while p.level < target {
                    let need = progress::xp_to_next(p.level) - p.xp.min(progress::xp_to_next(p.level));
                    p.add_xp(need.max(1));
                }
                Ok(format!("Seviye {}", p.level))
            }
            "xp" => {
                self.progress.add_xp(num(1, 1000.0) as u32);
                Ok(format!("Seviye {}", self.progress.level))
            }
            "charlevel" | "karakter" => {
                let c = self.progress.character;
                self.progress.char_level[c] = (num(1, 10.0) as u32).clamp(1, progress::MAX_CHAR_LEVEL);
                self.progress.char_xp[c] = 0;
                Ok(format!("Karakter seviyesi {}", self.progress.char_level[c]))
            }
            "god" | "olumsuz" => {
                self.player.god = !self.player.god;
                Ok(format!("Ölümsüzlük {}", if self.player.god { "AÇIK" } else { "KAPALI" }))
            }
            "onehit" | "tekvurus" => {
                self.player.one_hit = !self.player.one_hit;
                Ok(format!("Tek vuruş {}", if self.player.one_hit { "AÇIK" } else { "KAPALI" }))
            }
            "speed" | "hiz" => {
                self.player.speed_cheat = num(1, 2.0).clamp(0.2, 6.0);
                Ok(format!("Hız ×{}", self.player.speed_cheat))
            }
            "time" | "saat" => {
                let h = num(1, 12.0).clamp(0.0, 24.0);
                self.day_t = self.day_t.floor() + h / 24.0;
                Ok(format!("Saat {h:.0}:00"))
            }
            "night" | "gece" => {
                self.day_t = self.day_t.floor() + 0.0;
                Ok("Gece oldu".into())
            }
            "day" | "gunduz" => {
                self.day_t = self.day_t.floor() + 0.5;
                Ok("Gündüz oldu".into())
            }
            "heal" | "can" => {
                self.player.hp = self.progress.max_hp();
                self.player.stamina = self.progress.max_stamina();
                self.player.poison_t = 0.0;
                self.player.ability_cd = 0.0;
                Ok("Can ve dayanıklılık doldu".into())
            }
            "spawn" | "zombi" | "boss" | "killall" | "temizle" if self.net == 2 => {
                Err("Çevrimiçi katılımcısın: zombileri ev sahibi yönetir, bu hile yalnızca ev sahibinde ya da tek başınayken çalışır".into())
            }
            "spawn" | "zombi" => {
                let kind = match arg(1) {
                    "runner" | "kosucu" => data::ZKind::Runner,
                    "dog" | "kopek" => data::ZKind::Dog,
                    "toxic" | "toksik" => data::ZKind::Toxic,
                    "dark" | "karanlik" => data::ZKind::Dark,
                    "tank" => data::ZKind::Tank,
                    "mystery" | "gizemli" => data::ZKind::Mystery,
                    "titan" | "boss" => data::ZKind::Titan,
                    _ => data::ZKind::Walker,
                };
                let n = (num(2, 1.0) as u32).clamp(1, 30);
                let lvl = (num(3, self.progress.level as f32) as u32).clamp(1, 60);
                for k in 0..n {
                    let a = k as f32 * 0.7 + self.rng.range(0.0, 0.5);
                    self.spawn_zombie_at(kind, lvl, px + a.cos() * 320.0, py + a.sin() * 320.0);
                }
                Ok(format!("{n}× {} doğdu", data::ZOMBIES[kind as usize].name))
            }
            "boss" => {
                self.spawn_zombie_at(data::ZKind::Titan, 50, px + 420.0, py - 200.0);
                self.events.push(Event::Boss { name: "TİTAN".into() });
                Ok("TİTAN çağrıldı!".into())
            }
            "killall" | "temizle" => {
                let n = self.zombies.len();
                self.zombies.clear();
                Ok(format!("{n} zombi silindi"))
            }
            "tp" | "isinla" => {
                let (x, y) = match arg(1) {
                    "city" | "sehir" => world::city_spawn(),
                    "camp" | "kamp" => world::camp_spawn(),
                    "boss" | "titan" => (world::BOSS.0 - 600.0, world::BOSS.1 + 400.0),
                    "swamp" | "bataklik" => (50.0 * world::TILE, 300.0 * world::TILE),
                    "downtown" | "merkez" => (50.0 * world::TILE, 60.0 * world::TILE),
                    "deep" | "derin" => (300.0 * world::TILE, 232.0 * world::TILE),
                    "burnt" | "kul" => (500.0 * world::TILE, 280.0 * world::TILE),
                    "desert" | "col" => (250.0 * world::TILE, 520.0 * world::TILE),
                    "bswamp" | "karabataklik" => (90.0 * world::TILE, 520.0 * world::TILE),
                    "military" | "askeri" => (520.0 * world::TILE, 90.0 * world::TILE),
                    "harbor" | "liman" => (520.0 * world::TILE, 520.0 * world::TILE),
                    "home" | "us" => world::home_spawn(),
                    "base" | "uss" => {
                        let b = &crate::bases::base_defs()[(num(2, 1.0) as usize).clamp(1, crate::bases::BASE_N) - 1];
                        (b.tx * world::TILE, (b.ty + crate::bases::BASE_HALF + 5.0) * world::TILE)
                    }
                    _ => (num(1, px / world::TILE) * world::TILE, num(2, py / world::TILE) * world::TILE),
                };
                let (x, y) = self.teleport(x, y);
                Ok(format!("Işınlandın ({:.0}, {:.0})", x / world::TILE, y / world::TILE))
            }
            "unlock" | "ac" => {
                self.progress.unlocked = [true; 4];
                for c in COSMETICS {
                    if !self.progress.cosmetics.iter().any(|o| o == c.id) {
                        self.progress.cosmetics.push(c.id.to_string());
                    }
                }
                Ok("Tüm karakterler ve kozmetikler açıldı".into())
            }
            "pass" => match arg(1) {
                "buy" | "al" => {
                    self.activate_pass(arg(2) == "elite" || arg(2) == "yuksek");
                    Ok("Sezon Bileti etkinleştirildi (test)".into())
                }
                _ => {
                    let t = (num(1, 30.0) as u32).min(data::PASS_TIERS);
                    self.progress.pass.xp = t * data::PASS_XP_PER_TIER;
                    Ok(format!("Sezon Bileti kademesi {t}"))
                }
            },
            "vehicle" | "arac" => {
                // bisiklet kaldırıldı: varsayılan motor
                let k = match arg(1) {
                    "car" | "araba" => 2,
                    "suv" => 3,
                    _ => 1,
                };
                let (mut x, mut y) = (px + 120.0, py);
                self.world.resolve(&mut x, &mut y, data::VEHICLES[k].radius, true, &mut self.scratch);
                self.vehicles.push(Vehicle { kind: k, x, y, angle: 0.0, speed: 0.0, hp: data::VEHICLES[k].hp, fuel: 100.0, driver: false, wrecked: false });
                Ok(format!("{} getirildi", data::VEHICLES[k].name))
            }
            "points" | "puan" => {
                self.progress.tree_credit += num(1, 10.0) as u32 * 10;
                Ok(format!("Ağaç kredisi: {}", self.progress.tree_credit))
            }
            "fuel" | "yakit" => {
                for v in self.vehicles.iter_mut() {
                    v.fuel = 100.0;
                    v.hp = data::VEHICLES[v.kind].hp;
                    v.wrecked = false;
                }
                Ok("Tüm araçların yakıtı ve canı doldu".into())
            }
            "clearbag" | "cantabosalt" => {
                for sl in self.progress.inv.slots.iter_mut() {
                    *sl = None;
                }
                Ok("Çanta boşaltıldı".into())
            }
            "nocd" | "beklemesiz" => {
                self.player.no_cd = !self.player.no_cd;
                Ok(format!("Beklemesiz takla/iksir {}", if self.player.no_cd { "AÇIK" } else { "KAPALI" }))
            }
            "invis" | "gorunmez" => {
                self.player.invis = !self.player.invis;
                Ok(format!("Zombilere görünmezlik {}", if self.player.invis { "AÇIK" } else { "KAPALI" }))
            }
            "timescale" | "zaman" => {
                self.time_scale = num(1, 1.0).clamp(0.1, 4.0);
                Ok(format!("Zaman ×{}", self.time_scale))
            }
            "dog" | "kopek" => {
                self.progress.dog_owned = true;
                let l = (num(1, 10.0) as u8).min(crate::rpg::DOG_MAX);
                self.progress.dog_lv = [l; 3];
                self.dog = None;
                Ok(format!("Köpek seviye {l}"))
            }
            "potions" | "iksirler" => {
                for id in ["potion_heal", "potion_speed", "potion_power"] {
                    self.progress.inv.add_id(id, 5);
                }
                Ok("Her iksirden 5 eklendi".into())
            }
            "reset" | "sifirla" => {
                self.progress = progress::Progress::default();
                self.respawn(false);
                Ok("Profil sıfırlandı".into())
            }
            _ => Err("Komutlar: gold N, gem N, item id N, items, weapons, level N, xp N, charlevel N, \
                      god, onehit, speed X, time H, day, night, heal, spawn tür N lvl, boss, killall, tp city|camp|boss|swamp|downtown|deep|burnt|desert|bswamp|military|harbor|home|base N|x y, \
                      unlock, pass N, pass buy [elite], vehicle moto|car|suv, points N, fuel, clearbag, nocd, invis, timescale X, dog N, potions, reset"
                .into()),
        }
    }
}

fn reward_json(r: &str) -> Value {
    let (k, v) = r.split_once(':').unwrap_or((r, "1"));
    match k {
        "gold" => {
            json!({ "type": "gold", "id": "coin", "name": "Altın", "count": v.parse::<u32>().unwrap_or(0), "rarity": 0 })
        }
        "gem" => {
            json!({ "type": "gem", "id": "gem", "name": "Elmas", "count": v.parse::<u32>().unwrap_or(0), "rarity": 2 })
        }
        "cos" => {
            let c = COSMETICS.iter().find(|c| c.id == v);
            json!({ "type": "cos", "id": v, "name": c.map_or(v, |c| c.name), "kind": c.map_or("", |c| c.kind), "count": 1, "rarity": 3 })
        }
        id => {
            let d = data::item(id);
            json!({ "type": "item", "id": id, "name": d.name, "count": v.parse::<u32>().unwrap_or(1), "rarity": d.rarity as u8 })
        }
    }
}

#[cfg(test)]
mod cheat_tests {
    use crate::game::Game;
    use crate::progress::Progress;

    /// Hile panelindeki her hızlı komut hatasız çalışmalı.
    #[test]
    fn all_panel_cheats_work() {
        let mut g = Game::new(7, Progress::default(), 1);
        let cmds = [
            "gold 10000", "gem 500", "level 10", "level 50", "xp 5000", "charlevel 10", "items", "weapons", "points 10", "nocd", "nocd", "invis", "invis", "timescale 2", "timescale 1", "dog 5", "potions",
            "item w_katana 1", "item medkit 5", "god", "god", "onehit", "onehit",
            "speed 2", "speed 1", "heal", "day", "night", "time 18", "spawn walker 3", "spawn runner 2", "spawn dog 2",
            "spawn toxic 2", "spawn dark 2", "spawn tank 1", "spawn mystery 1", "boss", "killall", "vehicle moto",
            "vehicle car", "vehicle suv", "fuel", "tp camp", "tp city", "tp swamp", "tp deep", "tp downtown", "tp boss", "tp 100 100",
            "unlock", "pass 30", "pass buy", "pass buy elite", "clearbag", "reset",
        ];
        for c in cmds {
            let r = g.cheat(c);
            assert!(r.is_ok(), "{c}: {r:?}");
            for _ in 0..3 {
                g.step(1.0 / 30.0, crate::game::Input::default());
            }
        }
        assert!(g.cheat("bilinmeyen").is_err());
        // çevrimiçi katılımcıda zombi hileleri açıkça reddedilir
        g.net = 2;
        assert!(g.cheat("spawn tank 1").is_err());
        assert!(g.cheat("gold 5").is_ok());
        let v: serde_json::Value = serde_json::from_str(&g.ui_json("cheat")).unwrap();
        assert!(v["items"].as_array().unwrap().len() > 25, "yalnız oyundaki eşyalar listelenir");
    }
}

#[cfg(test)]
mod v4_tests {
    use crate::game::{Game, Input};
    use crate::progress::Progress;

    fn tick(g: &mut Game, n: usize) {
        for _ in 0..n {
            g.step(1.0 / 30.0, Input::default());
        }
    }

    /// Arayüz ekran görüntüsü testleri için örnek görünüm verisi (ZX_DUMP_UI=dizin ile çalıştırılınca yazar).
    #[test]
    fn dump_ui_samples() {
        let Ok(dir) = std::env::var("ZX_DUMP_UI") else { return };
        let mut g = Game::new(7, Progress::default(), 1);
        g.cheat("level 30").unwrap();
        // birkaç dalda ilerlemiş bir ağaç
        for _ in 0..3 {
            for id in 1..crate::rpg::tree().len() as u16 {
                let n = &crate::rpg::tree()[id as usize];
                if (n.branch == 0 || n.branch == 2) && n.x.abs() + n.y.abs() < 9.0 {
                    let _ = g.command(&format!(r#"{{"cmd":"tree_unlock","id":{id}}}"#));
                }
            }
        }
        // çanta: ceplerde iksirler (biri beklemede), çantada karışık eşyalar
        g.cheat("potions").unwrap();
        g.cheat("item medkit 6").unwrap();
        g.cheat("item wood 30").unwrap();
        g.cheat("item cloth 12").unwrap();
        g.cheat("item w_katana 1").unwrap();
        for (k, id) in ["potion_heal", "potion_speed", "medkit"].iter().enumerate() {
            let from = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == *id)).unwrap();
            g.progress.inv.move_slot(from, k).unwrap();
        }
        g.use_slot(1).unwrap();
        // V8 ekipman: kask, ceket, bot, çanta (2 ek bölme), yüzük, kolye; ek bölmede eşya; yanında sandık
        for id in ["helm_police", "jacket_basic", "boots_basic", "bag_police", "ring_hunter", "neck_bone"] {
            g.cheat(&format!("item {id} 1")).unwrap();
            let s = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == id)).unwrap();
            g.use_slot(s).unwrap();
        }
        g.cheat("item stone 22").unwrap();
        let st = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "stone")).unwrap();
        g.progress.inv.move_to_page(st, 1).unwrap();
        g.cheat("item chest_iron 1").unwrap();
        let cs = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "chest_iron")).unwrap();
        let (px, py) = (g.player.x, g.player.y + 40.0);
        let ci = g.progress.place_chest(cs, px, py).unwrap();
        g.cheat("item scrap 40").unwrap();
        let sc = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "scrap")).unwrap();
        g.progress.chest_put(ci, sc, Some(25)).unwrap();
        g.cheat("item ring_copper 1").unwrap();
        let rc = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "ring_copper")).unwrap();
        g.progress.chest_put(ci, rc, None).unwrap();
        std::fs::write(format!("{dir}/chest.json"), g.ui_json(&format!("chest:{ci}"))).unwrap();
        g.step(1.0 / 30.0, crate::game::Input::default());
        // harita: başlangıçtan kuzeye bir yol keşfedilmiş
        let (sx, sy) = (g.player.x, g.player.y);
        for k in 0..12 {
            g.progress.reveal(sx, sy - k as f32 * 700.0, 2);
        }
        std::fs::write(format!("{dir}/world.json"), crate::world_json(&g)).unwrap();
        // V8 devlet üsleri: ilk 6 üs keşfedilmiş; biri bir klanın, biri oyuncunun klanının
        for b in crate::bases::base_defs().iter().take(6) {
            let (bx, by) = b.pos();
            g.progress.reveal(bx, by, 2);
        }
        let now = 1_800_000_000_000.0;
        g.apply_bases(&serde_json::json!({"now": now, "clan": 7, "bases": [
            {"id": 0, "owner": 0, "reward": "titan_shard", "n": 1, "end": now + 754_000.0},
            {"id": 1, "owner": 3, "name": "Kurtlar", "color": 0xC04040, "reward": "potion_heal", "n": 2, "end": now + 312_000.0, "cap": now - 900_000.0},
            {"id": 2, "owner": 7, "name": "Benim Klanım", "color": 0x40A0E0, "reward": "medkit", "n": 3, "end": now + 61_000.0, "cap": now - 60_000.0},
        ]}));
        std::fs::write(format!("{dir}/bases.json"), g.ui_json("bases")).unwrap();
        std::fs::write(format!("{dir}/player.json"), serde_json::json!({"x": sx, "y": sy}).to_string()).unwrap();
        // konuşma ve görev örnekleri
        let mut h = Game::new(5, Progress::default(), 1);
        let n = h.npcs.iter().rev().find(|n| n.kind == crate::story::N_GUIDE).unwrap();
        let (nx, ny) = (n.x, n.y + 60.0);
        h.player.x = nx;
        h.player.y = ny;
        std::fs::write(format!("{dir}/talk.json"), h.talk(crate::story::N_GUIDE, "").to_string()).unwrap();
        h.talk(crate::story::N_GUIDE, "accept");
        h.story_event(crate::story::Ev::Kill { kind: crate::data::ZKind::Walker, x: 0.0, y: 0.0 });
        std::fs::write(format!("{dir}/quests.json"), h.ui_json("quests")).unwrap();
        for v in ["tree", "dog", "bag", "inventory", "fog"] {
            std::fs::write(format!("{dir}/{v}.json"), g.ui_json(v)).unwrap();
        }
    }

    #[test]
    fn camp_npcs_serve() {
        let mut g = Game::new(7, Progress::default(), 1);
        assert!(g.npcs.iter().any(|n| n.kind == 3) && g.npcs.iter().any(|n| n.kind == 5), "kamp NPC'leri");
        g.progress.gold = 100;
        g.player.hp = 10.0;
        let goto = |g: &mut Game, kind: u8| {
            let n = g.npcs.iter().rev().find(|n| n.kind == kind).unwrap();
            let (x, y) = (n.x, n.y + 60.0);
            g.player.x = x;
            g.player.y = y;
        };
        goto(&mut g, 3);
        assert!(g.command(r#"{"cmd":"doctor"}"#).contains("ok"));
        assert!(g.player.hp >= g.progress.max_hp() - 0.5);
        assert_eq!(g.progress.gold, 80);
        goto(&mut g, 4);
        g.cheat("vehicle moto").unwrap();
        g.vehicles.last_mut().unwrap().fuel = 10.0;
        g.command(r#"{"cmd":"mechanic"}"#);
        assert_eq!(g.vehicles.last().unwrap().fuel, 100.0);
        assert_eq!(g.progress.gold, 50);
    }

    #[test]
    fn roll_moves_and_protects() {
        let mut g = Game::new(7, Progress::default(), 1);
        g.cheat("tp deep").unwrap();
        g.player.dir = 2;
        let (x0, y0) = (g.player.x, g.player.y);
        assert!(g.use_ability().is_ok());
        assert!(g.player_invulnerable());
        let hp = g.player.hp;
        g.damage_player(50.0);
        assert_eq!(g.player.hp, hp, "takla sırasında hasar alınmaz");
        tick(&mut g, 15);
        assert!((g.player.x - x0).hypot(g.player.y - y0) > 60.0, "takla oyuncuyu taşımalı");
        assert!(g.player.ability_cd > 3.0, "takla 5 sn bekler");
        assert!(g.use_ability().is_err());
    }

    #[test]
    fn potions_and_dog() {
        let mut g = Game::new(7, Progress::default(), 1);
        g.cheat("potions").unwrap();
        let slot = |g: &Game, id: &str| {
            g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == id)).unwrap()
        };
        let s = slot(&g, "potion_speed");
        assert!(g.use_slot(s).is_ok());
        assert!(g.player.speed_t > 0.0);
        let s = slot(&g, "potion_speed");
        assert!(g.use_slot(s).is_err(), "bekleme süresi");
        // köpek: kamptaki eğitmen dışında alınamaz, hileyle verilir ve oyuncuyu takip eder
        g.progress.gold = 10_000;
        g.cheat("tp deep").unwrap();
        assert!(g.dog_buy().is_err());
        g.cheat("dog 3").unwrap();
        tick(&mut g, 60);
        let d = g.dog.as_ref().expect("köpek doğmalı");
        assert!((d.x - g.player.x).hypot(d.y - g.player.y) < 400.0);
        g.cheat("spawn walker 2 1").unwrap();
        tick(&mut g, 200);
        assert!(g.progress.kills > 0 || g.zombies.iter().any(|z| z.hp < z.max_hp), "köpek saldırmalı");
    }

    #[test]
    fn buffs_tick_and_dog_fights() {
        let mut g = Game::new(9, Progress::default(), 1);
        g.cheat("potions").unwrap();
        let s = g.progress.inv.slots.iter().position(|s| s.is_some_and(|s| crate::data::ITEMS[s.item as usize].id == "potion_speed")).unwrap();
        g.use_slot(s).unwrap();
        // süre her durumda işler ve biter
        tick(&mut g, 31 * 30);
        assert_eq!(g.player.speed_t, 0.0);
        assert!(g.hud()[32] == 0.0);
        // zombiler köpeğe de saldırır
        g.cheat("tp deep").unwrap();
        g.cheat("god").ok();
        g.player.god = false;
        g.cheat("dog 1").unwrap();
        tick(&mut g, 30);
        let max = g.dog.as_ref().unwrap().hp;
        g.player.invis = true; // oyuncu görünmez: hedef yalnız köpek
        let (dx, dy) = (g.dog.as_ref().unwrap().x, g.dog.as_ref().unwrap().y);
        for k in 0..3 {
            g.spawn_zombie_at(crate::data::ZKind::Walker, 3, dx + 60.0 + k as f32 * 20.0, dy);
        }
        let mut hit = false;
        for _ in 0..300 {
            tick(&mut g, 1);
            if g.dog.as_ref().is_some_and(|d| d.hp < max || d.dead_t > 0.0) {
                hit = true;
                break;
            }
        }
        assert!(hit, "zombiler köpekle dövüşmeli");
    }

    #[test]
    fn zombies_leash_home() {
        let mut g = Game::new(11, Progress::default(), 1);
        g.cheat("tp deep").unwrap();
        g.zombies.clear();
        let (px, py) = (g.player.x, g.player.y);
        g.spawn_zombie_at(crate::data::ZKind::Walker, 1, px + 200.0, py);
        let z = &mut g.zombies[0];
        z.state = crate::game::ZState::Chase;
        // evinden çok uzağa sürüklenmiş gibi
        z.home_x = px + 200.0 + crate::data::PX_PER_M * 20.0;
        tick(&mut g, 2);
        assert!(g.zombies[0].ret, "bölge sınırını aşan yaratık geri dönmeli");
    }

    #[test]
    fn walking_and_running_cost_no_stamina_but_attacks_do() {
        let mut g = Game::new(9, Progress::default(), 1);
        let full = g.progress.max_stamina();
        let run = Input { move_x: 1.0, ..Default::default() };
        for _ in 0..90 {
            g.step(1.0 / 30.0, run);
        }
        assert_eq!(g.player.stamina, full, "koşmak dayanıklılık harcamamalı");
        assert!(g.player.moving);
        g.player.ability_cd = 0.0;
        g.start_roll().unwrap();
        assert!(g.player.stamina < full, "takla harcamalı");
        let after_roll = g.player.stamina;
        for _ in 0..12 {
            g.step(1.0 / 30.0, Input::default());
        }
        let atk = Input { attacking: true, atk_x: 1.0, ..Default::default() };
        for _ in 0..20 {
            g.step(1.0 / 30.0, atk);
        }
        assert!(g.player.stamina < after_roll, "yumruk harcamalı");
    }

    #[test]
    fn bicycle_is_gone() {
        let g = Game::new(3, Progress::default(), 1);
        assert!(g.vehicles.iter().all(|v| v.kind != crate::data::REMOVED_VEHICLE));
        assert!(g.world.vehicle_spawns.iter().all(|v| v.0 != crate::data::REMOVED_VEHICLE));
        let mut g = g;
        g.cheat("vehicle bike").unwrap();
        assert_ne!(g.vehicles.last().unwrap().kind, crate::data::REMOVED_VEHICLE);
        let c: serde_json::Value = serde_json::from_str(&g.ui_json("cheat")).unwrap();
        assert!(c["items"].as_array().unwrap().iter().all(|i| i[0] != "w_sniper" && !i[0].as_str().unwrap().starts_with("ammo_")));
    }
}
