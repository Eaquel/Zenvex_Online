//! Arayüz köprüsü: Kotlin ekranları için JSON görünümleri ve JSON komutları (geliştirici hileleri dahil).
use crate::data::{
    self, ItemKind, CHARACTERS, COSMETICS, ITEMS, MERCHANT, PASS_REWARDS, RECIPES, WEAPONS,
};
use crate::game::*;
use crate::progress::{self, SKILLS};
use crate::world;
use serde_json::{json, Value};

const NEAR: f32 = 150.0;

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
            "profile" => {
                let c = g.character;
                json!({
                    "name": g.name, "level": g.level, "xp": g.xp, "xp_next": progress::xp_to_next(g.level), "points": g.points,
                    "gold": g.gold, "gems": g.gems, "character": c, "char_name": CHARACTERS[c].name, "char_class": CHARACTERS[c].class,
                    "char_level": g.char_level[c], "frame": g.frame, "tag": g.tag,
                    "tag_name": COSMETICS.iter().find(|x| x.id == g.tag).map_or("", |x| x.name),
                    "costume": g.costume[c], "pass_tier": g.pass_tier(), "pass_normal": g.pass.normal, "pass_elite": g.pass.elite,
                    "city": g.level >= progress::CITY_LEVEL, "zone": world::ZONES_ALL[self.player.zone as usize].name(),
                    "stats": { "kills": g.kills, "deaths": g.deaths, "trees": g.trees, "crates": g.crates, "crafted": g.crafted,
                               "boss": g.boss_kills, "gold_earned": g.gold_earned, "time": g.play_time as u64 },
                    "drone_targets": g.drone_targets,
                })
            }
            "inventory" => {
                let slots: Vec<Value> = g
                    .inv
                    .slots
                    .iter()
                    .map(|s| match s {
                        Some(s) => {
                            let mut v = item_json(ITEMS[s.item as usize].id);
                            v["count"] = json!(s.count);
                            v
                        }
                        None => Value::Null,
                    })
                    .collect();
                let e = &g.eq;
                json!({
                    "slots": slots, "cap": g.inventory_cap(),
                    "eq": { "weapon": item_json(&e.weapon), "helmet": item_json(&e.helmet), "jacket": item_json(&e.jacket),
                            "boots": item_json(&e.boots), "bag": item_json(&e.bag), "cloak": item_json(&e.cloak) },
                    "armor": g.armor(), "max_hp": g.max_hp(), "dmg": g.damage_mult(), "speed": g.move_mult(),
                    "gold": g.gold, "gems": g.gems, "in_game_hp": self.player.hp,
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
                json!({ "main": main, "daily": daily })
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
            _ => json!({ "error": "bilinmeyen görünüm" }),
        };
        v.to_string()
    }

    // ------------------------------------------------------------------ komutlar
    pub fn command(&mut self, s: &str) -> String {
        let v: Value = serde_json::from_str(s).unwrap_or(Value::Null);
        let cmd = v["cmd"].as_str().unwrap_or("");
        let int = |k: &str| v[k].as_u64().unwrap_or(0) as usize;
        let res: Result<String, String> = match cmd {
            "equip" => self.progress.equip_slot(int("slot")).map(|n| format!("{n} kuşanıldı")).map_err(String::from),
            "unequip" => self.progress.unequip(v["slot"].as_str().unwrap_or("")).map(|_| "Çıkarıldı".into()).map_err(String::from),
            "use" => self.use_slot(int("slot")),
            "drop" => self.drop_slot(int("slot")),
            "sort" => {
                self.progress.inv.sort();
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
            "cheat" => self.cheat(v["text"].as_str().unwrap_or("")),
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
                if id == "coin" {
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

    fn use_slot(&mut self, slot: usize) -> Result<String, String> {
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
            | ItemKind::Cloak => {
                return self
                    .progress
                    .equip_slot(slot)
                    .map(|n| format!("{n} kuşanıldı"))
                    .map_err(String::from);
            }
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
            "fuel" => {}
            _ => return Err("Bu eşya kullanılamaz".into()),
        }
        self.progress.inv.remove(s.item as usize, 1);
        Ok(format!("{} kullanıldı", d.name))
    }

    fn drop_slot(&mut self, slot: usize) -> Result<String, String> {
        let s = self.progress.inv.take_slot(slot).ok_or("Boş slot")?;
        let (x, y) = (self.player.x, self.player.y + 30.0);
        self.spawn_drop(s.item as usize, s.count as u32, x, y);
        if let Some(d) = self.drops.last_mut() {
            d.age = -1.5; // hemen geri toplanmasın
        }
        Ok(format!("{} yere bırakıldı", ITEMS[s.item as usize].name))
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
                return Err("Premium ödüller için Sezon Pass gerekli".into());
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
                if data::item_index(id).is_none() {
                    let ids: Vec<&str> = ITEMS.iter().map(|i| i.id).collect();
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
                for w in &WEAPONS[1..] {
                    self.progress.inv.add_id(w.item, 1);
                    if !w.ammo.is_empty() {
                        self.progress.inv.add_id(w.ammo, 60);
                    }
                }
                Ok("Tüm silahlar ve mermiler eklendi".into())
            }
            "set" => {
                let t = match arg(1) {
                    "police" | "polis" => "police",
                    "military" | "asker" => "military",
                    "titan" => "titan",
                    _ => "basic",
                };
                for k in ["helm", "jacket", "boots", "bag"] {
                    let id = format!("{k}_{t}");
                    if data::item_index(&id).is_some() {
                        self.progress.inv.add_id(&id, 1);
                    }
                }
                Ok(format!("{t} seti eklendi"))
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
                    "deep" | "derin" => (300.0 * world::TILE, 250.0 * world::TILE),
                    _ => (num(1, px / world::TILE) * world::TILE, num(2, py / world::TILE) * world::TILE),
                };
                let (mut x, mut y) = (x.clamp(100.0, world::MAP_PX - 100.0), y.clamp(100.0, world::MAP_PX - 100.0));
                self.world.resolve(&mut x, &mut y, 14.0, true, &mut self.scratch);
                if let Some(vi) = self.player.vehicle.take() {
                    self.vehicles[vi].driver = false;
                }
                self.player.x = x;
                self.player.y = y;
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
                    Ok("Pass etkinleştirildi (test)".into())
                }
                _ => {
                    let t = (num(1, 30.0) as u32).min(data::PASS_TIERS);
                    self.progress.pass.xp = t * data::PASS_XP_PER_TIER;
                    Ok(format!("Pass kademesi {t}"))
                }
            },
            "vehicle" | "arac" => {
                let k = match arg(1) {
                    "moto" | "motor" => 1,
                    "car" | "araba" => 2,
                    "suv" => 3,
                    _ => 0,
                };
                let (mut x, mut y) = (px + 120.0, py);
                self.world.resolve(&mut x, &mut y, data::VEHICLES[k].radius, true, &mut self.scratch);
                self.vehicles.push(Vehicle { kind: k, x, y, angle: 0.0, speed: 0.0, hp: data::VEHICLES[k].hp, fuel: 100.0, driver: false, wrecked: false });
                Ok(format!("{} getirildi", data::VEHICLES[k].name))
            }
            "reset" | "sifirla" => {
                self.progress = progress::Progress::default();
                self.respawn(false);
                Ok("Profil sıfırlandı".into())
            }
            _ => Err("Komutlar: gold N, gem N, item id N, items, weapons, set basic|police|military|titan, level N, xp N, charlevel N, \
                      god, onehit, speed X, time H, day, night, heal, spawn tür N lvl, boss, killall, tp city|camp|boss|swamp|downtown|deep|x y, \
                      unlock, pass N, pass buy [elite], vehicle bike|moto|car|suv, reset"
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
