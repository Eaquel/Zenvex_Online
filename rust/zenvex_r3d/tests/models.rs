//! Oyundaki tüm 3B modeller (android/app/src/main/assets/m3d) yüklenir; her karakter her silahla her animasyonu
//! çizer, her araç sürücüsüyle döner. Boş ya da dev boyutlu kare, NaN çapa ya da panik olmamalı.
use std::path::PathBuf;
use zenvex_r3d::anim::a;
use zenvex_r3d::service::{ActorReq, Service, VehicleReq};

const CHARS: [&str; 4] = ["kael", "lyra", "vex", "morvath"];
const WEAPONS: [&str; 19] = [
    "", "knife", "hatchet", "handaxe", "club", "effigy", "katana", "void_claw", "blood_scythe", "ice_scythe", "magma_hammer", "storm_axe",
    "pickaxe", "chainsaw", "pistol", "uzi", "ak47", "sniper", "rpg",
];

fn service() -> Option<Service> {
    let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../android/app/src/main/assets/m3d");
    if !dir.exists() {
        return None;
    }
    let mut s = Service::new();
    for name in CHARS.iter().chain(WEAPONS.iter().skip(1)).chain(["hellhound", "nightstalker"].iter()) {
        let zxm = std::fs::read(dir.join(format!("{name}.zxm"))).unwrap();
        let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
        s.load(name, &zxm, im.width() as usize, im.height() as usize, im.as_raw()).unwrap();
    }
    Some(s)
}

#[test]
fn every_character_weapon_and_animation_renders() {
    let Some(mut s) = service() else { return };
    let anims = [
        a::IDLE, a::WALK, a::RUN, a::PUNCH, a::MELEE, a::RIFLE_IDLE, a::CAST, a::HURT, a::DEATH, a::RIDE, a::ROLL, a::KNOCK, a::GETUP,
        a::PICKUP, a::CHOP, a::MINE, a::OPEN, a::DRINK, a::CHEER, a::POINT, a::FIRE, a::RELOAD,
    ];
    for ch in CHARS {
        for w in WEAPONS {
            for &anim in &anims {
                for t in [0.0, 0.37, 0.81] {
                    let r = ActorReq { ch, weapon: w, anim, t, period: 1.0, yaw: 135.0, pitch: 22.0, scale: 60.0, ssaa: false, flags: 0 };
                    let (img, p) = s.actor(&r).unwrap_or_else(|| panic!("{ch} {w} {anim}"));
                    assert!(img.w > 10 && img.h > 10 && img.w < 400 && img.h < 400, "{ch} {w} {anim} {t}: {}x{}", img.w, img.h);
                    assert!(img.ax.is_finite() && img.ay.is_finite() && p.tip[0].is_finite() && p.muzzle[1].is_finite());
                    assert!(img.rgba.iter().filter(|c| c[3] > 0).count() > 200, "{ch} {w} {anim}: boş kare");
                }
            }
        }
    }
}

#[test]
fn melee_trail_and_muzzle_flash_are_reported() {
    let Some(mut s) = service() else { return };
    let trail = (0..16)
        .map(|i| {
            let r = ActorReq { ch: "kael", weapon: "katana", anim: a::MELEE, t: i as f32 / 16.0, period: 1.0, yaw: 0.0, pitch: 22.0, scale: 60.0, ssaa: false, flags: 0 };
            s.actor(&r).unwrap().1.trail
        })
        .fold(0f32, f32::max);
    assert!(trail > 0.5, "katana savuruşunda iz yok: {trail}");
    let r = ActorReq { ch: "lyra", weapon: "ak47", anim: a::FIRE, t: 0.0, period: 1.0, yaw: 0.0, pitch: 22.0, scale: 60.0, ssaa: false, flags: 0 };
    let (_, p) = s.actor(&r).unwrap();
    assert!(p.flash > 0.9 && p.weapon == 16, "namlu alevi: {} silah {}", p.flash, p.weapon);
}

#[test]
fn vehicles_render_with_rider_and_spinning_wheels() {
    let Some(mut s) = service() else { return };
    for (name, rider) in [("hellhound", ""), ("nightstalker", "kael"), ("nightstalker", "vex")] {
        for yaw in [0.0, 45.0, 90.0, 200.0] {
            for wheel in [0.0, 45.0, 135.0] {
                let r = VehicleReq { name, yaw, pitch: 22.0, scale: 50.0, wheel, bounce: 0.01, tilt: 2.0, rider, rider_t: 0.0, ssaa: false };
                let (img, _) = s.vehicle(&r).unwrap();
                assert!(img.w > 20 && img.h > 20 && img.w < 400 && img.h < 400, "{name} {yaw}: {}x{}", img.w, img.h);
            }
        }
    }
}
