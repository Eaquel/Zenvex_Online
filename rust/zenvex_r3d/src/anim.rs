//! Animasyonlar: (oyun animasyon durumu, süre/faz, silah türü, karakter tarzı) → iskelet pozu + silah yerleşimi.
//!
//! Gövde uzayı: Z yukarı, ön = −Y, karakterin sağı = −X. Açılar derece.
//!  - uyluk/kol "öne" = ileri kaldırma; diz/dirsek = bükme; "yana" = gövdeden dışa açma
//!  - gövde (chest) x = öne eğilme, z = sola dönme (yukarıdan bakınca saat yönünün tersi)
//!
//! El hedefleri dinlenme pozundaki gövde uzayında verilir ve göğüs dönmesiyle birlikte döner (IK).

use crate::math::{lerp, v3, Xf, M3, V3};
use crate::rig::*;

/// Oyun çekirdeğindeki kahraman animasyon kimlikleri (zenvex_core::game::A_*)
pub mod a {
    pub const IDLE: u8 = 0;
    pub const WALK: u8 = 1;
    pub const RUN: u8 = 2;
    pub const PUNCH: u8 = 3;
    pub const MELEE: u8 = 4;
    pub const PISTOL_IDLE: u8 = 5;
    pub const PISTOL_WALK: u8 = 6;
    pub const RIFLE_IDLE: u8 = 7;
    pub const RIFLE_WALK: u8 = 8;
    pub const CAST: u8 = 9;
    pub const HURT: u8 = 10;
    pub const DEATH: u8 = 11;
    pub const RIDE: u8 = 12;
    pub const ROLL: u8 = 13;
    pub const KNOCK: u8 = 14;
    pub const GETUP: u8 = 15;
    pub const PICKUP: u8 = 16;
    pub const CHOP: u8 = 17;
    pub const MINE: u8 = 18;
    pub const OPEN: u8 = 19;
    pub const DRINK: u8 = 20;
    pub const CHEER: u8 = 21;
    pub const POINT: u8 = 22;
    /// yalnız 3B: ateşli silah ateşi ve şarjör değiştirme
    pub const FIRE: u8 = 40;
    pub const RELOAD: u8 = 41;
}

/// Silah türleri (3B model adlarıyla)
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Wk {
    None,
    Knife,
    Hatchet,
    Handaxe,
    Club,
    Effigy,
    Katana,
    Claw,
    BloodScythe,
    IceScythe,
    Hammer,
    StormAxe,
    Pickaxe,
    Chainsaw,
    Pistol,
    Uzi,
    Ak,
    Sniper,
    Rpg,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Grip {
    None,
    /// tek el
    One,
    /// iki el, sol el sapın altında
    Two,
    /// tüfek: sol el namlu altında
    Rifle,
    /// tabanca: sol el destekte
    Pistol,
    /// pençe: sağ ele giyilir
    Claw,
    /// omuzda (RPG)
    Shoulder,
}

impl Wk {
    pub const ALL: [Wk; 19] = [
        Wk::None,
        Wk::Knife,
        Wk::Hatchet,
        Wk::Handaxe,
        Wk::Club,
        Wk::Effigy,
        Wk::Katana,
        Wk::Claw,
        Wk::BloodScythe,
        Wk::IceScythe,
        Wk::Hammer,
        Wk::StormAxe,
        Wk::Pickaxe,
        Wk::Chainsaw,
        Wk::Pistol,
        Wk::Uzi,
        Wk::Ak,
        Wk::Sniper,
        Wk::Rpg,
    ];

    /// 3B model dosya adı (assets/m3d/<ad>.zxm)
    pub fn model(self) -> &'static str {
        match self {
            Wk::None => "",
            Wk::Knife => "knife",
            Wk::Hatchet => "hatchet",
            Wk::Handaxe => "handaxe",
            Wk::Club => "club",
            Wk::Effigy => "effigy",
            Wk::Katana => "katana",
            Wk::Claw => "void_claw",
            Wk::BloodScythe => "blood_scythe",
            Wk::IceScythe => "ice_scythe",
            Wk::Hammer => "magma_hammer",
            Wk::StormAxe => "storm_axe",
            Wk::Pickaxe => "pickaxe",
            Wk::Chainsaw => "chainsaw",
            Wk::Pistol => "pistol",
            Wk::Uzi => "uzi",
            Wk::Ak => "ak47",
            Wk::Sniper => "sniper",
            Wk::Rpg => "rpg",
        }
    }

    pub fn from_model(s: &str) -> Wk {
        Wk::ALL.iter().copied().find(|w| w.model() == s).unwrap_or(Wk::None)
    }

    pub fn grip(self) -> Grip {
        match self {
            Wk::None => Grip::None,
            Wk::Knife | Wk::Hatchet | Wk::Handaxe | Wk::Club | Wk::Effigy | Wk::Katana => Grip::One,
            Wk::Claw => Grip::Claw,
            Wk::BloodScythe | Wk::IceScythe | Wk::Hammer | Wk::StormAxe | Wk::Pickaxe | Wk::Chainsaw => Grip::Two,
            Wk::Pistol => Grip::Pistol,
            Wk::Uzi | Wk::Ak | Wk::Sniper => Grip::Rifle,
            Wk::Rpg => Grip::Shoulder,
        }
    }

    pub fn ranged(self) -> bool {
        matches!(self, Wk::Pistol | Wk::Uzi | Wk::Ak | Wk::Sniper | Wk::Rpg)
    }

    /// silahın kendi ışıması (kor, buz, neon) — çizimde parlaklık
    pub fn glow(self) -> f32 {
        match self {
            Wk::Hammer => 0.18,
            Wk::IceScythe => 0.12,
            Wk::Claw => 0.10,
            Wk::Chainsaw | Wk::Uzi => 0.08,
            Wk::StormAxe => 0.06,
            _ => 0.0,
        }
    }
}

/// Karakter tarzı (hareket karakteri)
#[derive(Clone, Copy, Debug, Default)]
pub struct Style {
    /// ağır gövde (Vex): geniş duruş, ağır adım
    pub heavy: f32,
    /// kambur (Morvath)
    pub hunch: f32,
    /// rahat/kendinden emin (Lyra)
    pub swagger: f32,
}

impl Style {
    pub fn of(name: &str) -> Style {
        match name {
            "vex" => Style { heavy: 1.0, ..Default::default() },
            "morvath" => Style { hunch: 1.0, ..Default::default() },
            "lyra" => Style { swagger: 1.0, ..Default::default() },
            _ => Style::default(),
        }
    }
}

/// Bir anahtar kare: tüm alanlar doğrusal karıştırılabilir
#[derive(Clone, Copy, Debug, Default)]
pub struct K {
    pub hips: V3,
    pub off: V3,
    pub chest: V3,
    pub head: V3,
    pub thigh_r: V3,
    pub shin_r: f32,
    pub thigh_l: V3,
    pub shin_l: f32,
    /// FK kol: x öne, y yana, z burulma; dirsek bükümü
    pub arm_r: V3,
    pub elbow_r: f32,
    pub arm_l: V3,
    pub elbow_l: f32,
    /// IK ağırlığı (≥ 0.5 → avuç hedefe)
    pub ik_r: f32,
    pub hand_r: V3,
    pub ik_l: f32,
    pub hand_l: V3,
    /// silah ileri/yukarı yönleri (gövde uzayı)
    pub wdir: V3,
    pub wup: V3,
    /// tüm gövde dönmesi (takla, düşme) ve dönme merkezinin yüksekliği
    pub body: V3,
    pub body_z: f32,
}

impl K {
    pub fn mix(&self, o: &K, t: f32) -> K {
        let l3 = |a: V3, b: V3| a.lerp(b, t);
        let l = |a: f32, b: f32| lerp(a, b, t);
        K {
            hips: l3(self.hips, o.hips),
            off: l3(self.off, o.off),
            chest: l3(self.chest, o.chest),
            head: l3(self.head, o.head),
            thigh_r: l3(self.thigh_r, o.thigh_r),
            shin_r: l(self.shin_r, o.shin_r),
            thigh_l: l3(self.thigh_l, o.thigh_l),
            shin_l: l(self.shin_l, o.shin_l),
            arm_r: l3(self.arm_r, o.arm_r),
            elbow_r: l(self.elbow_r, o.elbow_r),
            arm_l: l3(self.arm_l, o.arm_l),
            elbow_l: l(self.elbow_l, o.elbow_l),
            ik_r: l(self.ik_r, o.ik_r),
            hand_r: l3(self.hand_r, o.hand_r),
            ik_l: l(self.ik_l, o.ik_l),
            hand_l: l3(self.hand_l, o.hand_l),
            wdir: l3(self.wdir, o.wdir),
            wup: l3(self.wup, o.wup),
            body: l3(self.body, o.body),
            body_z: l(self.body_z, o.body_z),
        }
    }
}

/// Anahtar kare dizisinde yumuşak ara değer; `keys` fazına göre sıralı, faz ∈ [0, 1]
pub fn track(keys: &[(f32, K)], ph: f32) -> K {
    if ph <= keys[0].0 {
        return keys[0].1;
    }
    for w in keys.windows(2) {
        let (t0, k0) = &w[0];
        let (t1, k1) = &w[1];
        if ph <= *t1 {
            let u = ((ph - t0) / (t1 - t0).max(1e-6)).clamp(0.0, 1.0);
            let e = u * u * (3.0 - 2.0 * u);
            return k0.mix(k1, e);
        }
    }
    keys[keys.len() - 1].1
}

/// Animasyon girdisi
#[derive(Clone, Copy, Debug)]
pub struct In {
    pub anim: u8,
    /// animasyonun başından beri geçen süre (sn)
    pub t: f32,
    /// saldırı döngüsü süresi (sn); vuruş anı döngünün %40'ı (çekirdekle aynı)
    pub period: f32,
    pub weapon: Wk,
    pub style: Style,
    /// silahın tutma noktasının gerisinde kalan sap uzunluğu (m; iki elli tutuşta sol elin yeri)
    pub wback: f32,
    /// durum bayrakları: [`F_EMPTY`], [`F_IRON`]
    pub flags: u32,
    /// "mag" parçasının pivotu (silah uzayı): roketatarda başlığın oturduğu yer
    pub pivot: V3,
}

/// roketatar boş: ön başlık (roket) namluda yok
pub const F_EMPTY: u32 = 1;
/// toplama aleti demir kademesinde (balta → savaş baltası modeli)
pub const F_IRON: u32 = 2;

impl In {
    pub fn new(anim: u8, t: f32, period: f32, weapon: Wk, style: Style) -> In {
        In { anim, t, period, weapon, style, wback: 0.0, flags: 0, pivot: V3::ZERO }
    }
}

/// Şarjör / roket değiştirme durumu
#[derive(Clone, Copy, Debug, Default)]
pub struct Rl {
    /// değiştirilen parça sol elde
    pub hand: bool,
    /// parça silahta yok (elde, havada ya da henüz takılmadı)
    pub hide: bool,
    /// ≥ 0: parça namlu ağzından bu kadar metre dışarıda, sol el onu iter
    pub slide: f32,
    /// sol el silahtan ayrık (anahtar karedeki hedefe gider)
    pub free_left: bool,
}

/// Bir karenin tüm çıktıları
pub struct Key {
    pub k: K,
    pub trail: f32,
    pub muzzle: f32,
    pub shown: Wk,
    pub rl: Option<Rl>,
    /// yakın dövüş kombosunun hangi vuruşu (0, 1, 2)
    pub stage: u8,
}

/// Çıktı: poz + silah yerleşimi + efekt bilgisi
pub struct Out {
    pub pose: Pose,
    pub world: [Xf; NBONES],
    /// silah modelinin dünya (model uzayı) dönüşümü
    pub weapon: Option<Xf>,
    /// şarjör silahtan ayrık: sol elde (dönüşüm) — None ise silahta
    pub mag: Option<Xf>,
    pub mag_hidden: bool,
    /// savurma izi gücü (0..1): efektler silah ucunun izini çizer
    pub trail: f32,
    /// kombonun hangi vuruşu (0, 1, 2)
    pub stage: u8,
    /// namlu alevi (0..1)
    pub muzzle: f32,
    /// bu animasyonda gösterilecek silah (toplama aletleri silahın yerine geçer)
    pub shown: Wk,
}

const TAU: f32 = std::f32::consts::TAU;

fn v(x: f32, y: f32, z: f32) -> V3 {
    v3(x, y, z)
}

/// Dinlenme duruşu (kollar yanda, hafif dirsek)
fn rest(st: &Style) -> K {
    K {
        thigh_r: v(0.0, 3.0 + 5.0 * st.heavy, 0.0),
        thigh_l: v(0.0, 3.0 + 5.0 * st.heavy, 0.0),
        shin_r: 4.0 + 6.0 * st.heavy,
        shin_l: 4.0 + 6.0 * st.heavy,
        arm_r: v(4.0, 6.0, 0.0),
        arm_l: v(4.0, 6.0, 0.0),
        elbow_r: 14.0,
        elbow_l: 14.0,
        chest: v(4.0 + 14.0 * st.hunch, 0.0, 0.0),
        head: v(-2.0 - 8.0 * st.hunch, 0.0, 0.0),
        off: v(0.0, 0.0, -0.01 - 0.03 * st.heavy),
        wdir: v(0.0, -0.85, -0.5),
        wup: v(0.0, -0.5, 0.85),
        ..Default::default()
    }
}

/// Silahlı hazır duruş (yürüme/beklemede eldeki silah)
fn ready(k: &mut K, wk: Wk, s: f32) {
    match wk.grip() {
        Grip::None => {}
        Grip::One => {
            k.ik_r = 1.0;
            k.hand_r = v(-0.30, -0.22, 0.98) * s;
            k.wdir = v(-0.15, -0.75, if wk == Wk::Katana { -0.35 } else { 0.55 });
            k.wup = v(0.0, 0.3, 1.0);
            if wk == Wk::Katana {
                k.wup = v(-0.2, -0.4, 0.9);
            }
        }
        Grip::Claw => {
            k.ik_r = 1.0;
            k.hand_r = v(-0.30, -0.20, 0.98) * s;
            k.wdir = v(-0.1, -1.0, 0.05);
            k.wup = v(0.0, 0.0, 1.0);
        }
        Grip::Two => {
            k.ik_r = 1.0;
            k.ik_l = 1.0;
            if matches!(wk, Wk::BloodScythe | Wk::IceScythe) {
                // tırpan: sağ yanda dik, ağız yukarıda ve geride (yüzü kapatmaz)
                k.hand_r = v(-0.32, -0.16, 1.02) * s;
                k.wdir = v(-0.12, 0.22, 1.0);
                k.wup = v(0.0, 1.0, -0.2);
            } else {
                // balyoz/balta/kazma/testere: sağ omuza yaslı, baş omzun arkasında
                k.hand_r = v(-0.16, -0.26, 1.06) * s;
                k.wdir = v(-0.40, 0.55, 0.75);
                k.wup = v(0.0, 0.8, -0.6);
            }
        }
        Grip::Rifle | Grip::Pistol => {
            k.ik_r = 1.0;
            k.ik_l = 1.0;
            let pist = wk.grip() == Grip::Pistol;
            k.hand_r = v(-0.12, -0.28 - if pist { 0.12 } else { 0.0 }, if pist { 1.14 } else { 1.12 }) * s;
            k.wdir = v(0.0, -1.0, if pist { 0.0 } else { -0.12 });
            k.wup = v(0.0, 0.0, 1.0);
            k.chest.z += if pist { 6.0 } else { 12.0 };
        }
        Grip::Shoulder => {
            k.ik_r = 1.0;
            k.ik_l = 1.0;
            k.hand_r = v(-0.14, -0.20, 1.28) * s;
            k.wdir = v(0.0, -1.0, 0.04);
            k.wup = v(0.0, 0.0, 1.0);
            k.chest.z += 14.0;
        }
    }
}

fn idle(st: &Style, t: f32, wk: Wk, s: f32) -> K {
    let mut k = rest(st);
    let b = (t * TAU * 0.32).sin();
    k.chest.x += 1.6 * b;
    k.head.x -= 1.0 * b;
    k.off.z += -0.006 * (1.0 + b);
    k.arm_r.x += 2.0 * b;
    k.arm_l.x += 2.0 * b;
    k.elbow_r += 3.0 * b;
    k.elbow_l += 3.0 * b;
    // 0.16 Hz: 6.25 sn — nefesle (0.32 Hz) aynı turda kapanır, önbellekteki boşta döngüsü dikişsiz
    k.hips.z = 2.0 * (t * TAU * 0.16).sin();
    if st.swagger > 0.0 {
        k.hips.y = 4.0;
        k.thigh_l.y += 6.0;
        k.shin_l += 8.0;
    }
    ready(&mut k, wk, s);
    if matches!(wk, Wk::BloodScythe | Wk::IceScythe) {
        // dik duran tırpan: sap hafifçe sallanır, rüzgârlı bir ağız
        k.wdir = v(-0.12 + 0.05 * (t * TAU * 0.32).sin(), 0.22, 1.0).norm();
    }
    if k.ik_r > 0.5 {
        k.hand_r = k.hand_r + v(0.0, 0.0, 0.008 * b);
        k.hand_l = k.hand_l + v(0.0, 0.0, 0.008 * b);
    }
    k
}

/// Yürüme / koşu döngüsü (faz = adım döngüsü)
fn stride(st: &Style, t: f32, run: bool, wk: Wk, s: f32) -> K {
    let mut k = rest(st);
    let freq = if run { 1.45 } else { 1.12 } * (1.0 - 0.15 * st.heavy);
    let p = t * freq;
    let sn = (p * TAU).sin();
    let cs = (p * TAU).cos();
    let (sw, kn, arm, el, bob, lean) =
        if run { (40.0, 82.0, 46.0, 78.0, 0.045, 14.0) } else { (26.0, 42.0, 24.0, 20.0, 0.022, 4.0) };
    let sw = sw * (1.0 - 0.2 * st.heavy);
    k.thigh_r.x = sw * sn;
    k.thigh_l.x = -sw * sn;
    k.shin_r = 6.0 + kn * cs.max(0.0).powf(1.2);
    k.shin_l = 6.0 + kn * (-cs).max(0.0).powf(1.2);
    k.off.z += -bob * sn.abs() - 0.02 * st.heavy;
    k.off.x = 0.012 * sn * (1.0 + st.heavy);
    k.hips.z = 7.0 * sn;
    k.hips.y = -3.0 * cs * (1.0 + st.heavy);
    k.chest.z = -9.0 * sn;
    k.chest.x = lean + 10.0 * st.hunch;
    k.head.x = -lean * 0.5;
    k.arm_r.x = -arm * sn;
    k.arm_l.x = arm * sn;
    k.arm_r.y = 8.0 + 6.0 * st.heavy;
    k.arm_l.y = 8.0 + 6.0 * st.heavy;
    k.elbow_r = el + 10.0 * (-sn).max(0.0);
    k.elbow_l = el + 10.0 * sn.max(0.0);
    ready(&mut k, wk, s);
    if matches!(wk, Wk::BloodScythe | Wk::IceScythe) {
        // hareket halinde tırpan omuza yatar: ağız arkada ve yukarıda, sap gövdeyle sallanır
        k.hand_r = h(-0.24, -0.20, 1.12, s);
        k.wdir = v(-0.28 + 0.06 * sn, 0.50, 0.82);
        k.wup = v(0.0, 0.8, -0.5);
    }
    if k.ik_r > 0.5 {
        // silahlıyken kol sabit, gövdeyle sallanır
        let bobv = v(0.0, 0.0, -bob * 0.5 * sn.abs());
        k.hand_r = k.hand_r + bobv;
        k.hand_l = k.hand_l + bobv;
        k.chest.z *= 0.4;
        if wk.ranged() {
            k.chest.z += if wk.grip() == Grip::Pistol { 6.0 } else { 12.0 };
        }
    }
    k
}

/// Silahsız yumruk (sol direkt + sağ kroşe), faz 0..1 tek yumruk; `alt` sırayı değiştirir
fn punch(st: &Style, ph: f32, alt: bool) -> K {
    let mut g = rest(st);
    // gard
    g.arm_r = v(60.0, 18.0, 0.0);
    g.elbow_r = 115.0;
    g.arm_l = v(60.0, 18.0, 0.0);
    g.elbow_l = 115.0;
    g.chest.x = 8.0;
    g.thigh_r.x = -8.0;
    g.thigh_l.x = 10.0;
    g.shin_l = 14.0;
    g.shin_r = 10.0;
    let mut hit = g;
    if alt {
        hit.arm_r = v(88.0, 6.0, 0.0);
        hit.elbow_r = 6.0;
        hit.chest.z = 24.0;
    } else {
        hit.arm_l = v(88.0, 6.0, 0.0);
        hit.elbow_l = 6.0;
        hit.chest.z = -20.0;
    }
    hit.off.y = -0.05;
    hit.chest.x = 12.0;
    track(&[(0.0, g), (0.35, hit), (0.55, hit), (1.0, g)], ph)
}

/// Anahtar kare üretici: `r` üzerine değişiklik uygular
fn with(r: &K, f: impl FnOnce(&mut K)) -> K {
    let mut k = *r;
    f(&mut k);
    k
}

/// El hedefi (metre × boy ölçeği)
fn h(x: f32, y: f32, z: f32, s: f32) -> V3 {
    v(x * s, y * s, z * s)
}

/// Öne atılım: kök öne, ön bacak bükük, arka bacak gergin; `crouch` gövdeyi alçaltır
fn lunge(k: &mut K, d: f32, crouch: f32) {
    k.off.y = -d;
    k.off.z -= crouch;
    k.thigh_r.x = 22.0 + 90.0 * crouch;
    k.shin_r = 26.0 + 110.0 * crouch;
    k.thigh_l.x = -18.0 - 20.0 * crouch;
    k.shin_l = 14.0 + 30.0 * crouch;
}

/// Dönerek yan adım: ağırlık arka ayakta, dizler bükük (bükülme sırasında)
fn coil(k: &mut K, crouch: f32) {
    k.off.z -= crouch;
    k.thigh_r.x = 28.0 + 80.0 * crouch;
    k.shin_r = 34.0 + 100.0 * crouch;
    k.thigh_l.x = 16.0 + 70.0 * crouch;
    k.shin_l = 30.0 + 90.0 * crouch;
}

/// Bir vuruşun anahtar kareleri: (faz, poz) dizisi, iz penceresi, kombo sırası
struct Mel {
    keys: Vec<(f32, K)>,
    trail: (f32, f32),
}

/// Yakın dövüş saldırısı; faz 0..1, vuruş anı 0.40. Her silahın kombosu farklı sayıda vuruştan oluşur.
fn melee_keys(r: &K, wk: Wk, s: f32, stage: u32) -> Mel {
    let r = *r;
    match wk {
        // ---------------------------------------------------------------- tırpan: üç vuruşluk kombo
        Wk::BloodScythe | Wk::IceScythe => match stage % 3 {
            0 => {
                // geniş biçme: sağ arkadan sol öne, gövde ve kalça ayrı dönerek kırbaç gibi
                let k1 = with(&r, |k| {
                    k.ik_l = 1.0;
                    k.hand_r = h(-0.46, 0.12, 1.05, s);
                    k.hand_l = h(-0.18, 0.16, 0.95, s);
                    k.wdir = v(-0.85, 0.50, 0.15);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = 50.0;
                    k.chest.x = 6.0;
                    k.hips.z = 18.0;
                    coil(k, 0.05);
                });
                let k2 = with(&k1, |k| {
                    k.hand_r = h(-0.54, 0.20, 1.00, s);
                    k.hand_l = h(-0.20, 0.20, 0.92, s);
                    k.wdir = v(-0.96, 0.26, 0.10);
                    k.chest.z = 60.0;
                    k.hips.z = 28.0;
                    coil(k, 0.09);
                });
                let k3 = with(&r, |k| {
                    k.ik_l = 1.0;
                    k.hand_r = h(-0.08, -0.62, 1.02, s);
                    k.hand_l = h(-0.10, -0.36, 0.94, s);
                    k.wdir = v(0.35, -0.93, 0.05);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = -6.0;
                    k.hips.z = -4.0;
                    lunge(k, 0.15, 0.04);
                });
                let k4 = with(&k3, |k| {
                    k.hand_r = h(0.26, -0.28, 1.00, s);
                    k.hand_l = h(0.10, -0.14, 0.92, s);
                    k.wdir = v(0.95, -0.15, 0.08);
                    k.chest.z = -54.0;
                    k.hips.z = -30.0;
                    lunge(k, 0.13, 0.04);
                });
                let k5 = with(&k4, |k| {
                    k.hand_r = h(0.34, -0.12, 1.02, s);
                    k.wdir = v(0.85, 0.30, 0.10);
                    k.chest.z = -46.0;
                });
                Mel { keys: vec![(0.0, r), (0.15, k1), (0.27, k2), (0.40, k3), (0.54, k4), (0.72, k5), (1.0, r)], trail: (0.30, 0.62) }
            }
            1 => {
                // yükselen ters kesik: sol alttan sağ üste, kavisli yukarı çekiş
                let k1 = with(&r, |k| {
                    k.ik_l = 1.0;
                    k.hand_r = h(0.20, -0.40, 0.62, s);
                    k.hand_l = h(0.04, -0.24, 0.66, s);
                    k.wdir = v(0.85, -0.45, -0.30);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = -50.0;
                    k.chest.x = 16.0;
                    k.hips.z = -22.0;
                    coil(k, 0.12);
                });
                let k2 = with(&r, |k| {
                    k.ik_l = 1.0;
                    k.hand_r = h(-0.26, -0.62, 1.24, s);
                    k.hand_l = h(-0.12, -0.38, 1.08, s);
                    k.wdir = v(-0.35, -0.92, 0.38);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = 6.0;
                    k.chest.x = 4.0;
                    k.hips.z = 4.0;
                    lunge(k, 0.16, 0.02);
                });
                let k3 = with(&k2, |k| {
                    k.hand_r = h(-0.46, -0.10, 1.52, s);
                    k.hand_l = h(-0.24, -0.06, 1.38, s);
                    k.wdir = v(-0.85, -0.20, 0.55);
                    k.chest.z = 52.0;
                    k.chest.x = -8.0;
                    k.hips.z = 26.0;
                });
                let k4 = with(&k3, |k| {
                    k.hand_r = h(-0.50, 0.05, 1.40, s);
                    k.wdir = v(-0.90, 0.20, 0.35);
                    k.chest.z = 44.0;
                });
                Mel { keys: vec![(0.0, r), (0.17, k1), (0.40, k2), (0.56, k3), (0.76, k4), (1.0, r)], trail: (0.26, 0.60) }
            }
            _ => {
                // fırıldak: yarım çömelip tam bir tur dönerek tırpanı yatay savurur
                let spin = |a: f32, fl: f32| {
                    with(&r, move |k| {
                        k.ik_l = 1.0;
                        k.hand_r = h(-0.54, -0.18, 1.16, s);
                        k.hand_l = h(-0.20, -0.14, 1.04, s);
                        k.wdir = v(-1.0, 0.04, 0.08);
                        k.wup = v(0.0, 0.0, 1.0);
                        k.body = v(0.0, 0.0, a);
                        k.chest.x = 6.0;
                        k.chest.y = fl;
                        k.off.z = 0.07 * (fl.abs() / 14.0);
                        k.thigh_r.x = 18.0;
                        k.thigh_l.x = -16.0;
                        k.shin_r = 30.0;
                        k.shin_l = 40.0;
                    })
                };
                let k0 = with(&spin(-26.0, 0.0), |k| coil(k, 0.10));
                let kmid = spin(120.0, 12.0);
                let kmid2 = spin(250.0, 14.0);
                let kend = with(&spin(360.0, 6.0), |k| {
                    lunge(k, 0.10, 0.05);
                    k.hand_r = h(-0.20, -0.58, 1.06, s);
                    k.hand_l = h(-0.12, -0.34, 0.96, s);
                    k.wdir = v(0.45, -0.88, 0.05);
                    k.chest.y = 0.0;
                });
                Mel { keys: vec![(0.0, r), (0.16, k0), (0.30, kmid), (0.48, kmid2), (0.66, kend), (0.82, with(&kend, |_| {})), (1.0, r)], trail: (0.18, 0.70) }
            }
        },
        // ---------------------------------------------------------------- balyoz, fırtına baltası, kazma, testere (iki el)
        Wk::Hammer => {
            // gövde ağırlığıyla baş üstünden yere çakma; çarpmada gövde sarsılır
            let up = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.10, 0.12, 1.62, s);
                k.hand_l = h(0.04, 0.18, 1.40, s);
                k.wdir = v(-0.05, 0.85, 0.52);
                k.wup = v(0.0, 0.52, -0.85);
                k.chest.x = -18.0;
                k.chest.z = 8.0;
                k.off.z = 0.02;
            });
            let slam = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.04, -0.66, 0.90, s);
                k.hand_l = h(0.04, -0.50, 0.84, s);
                k.wdir = v(0.0, -0.90, -0.42);
                k.wup = v(0.0, -0.42, 0.90);
                k.chest.x = 34.0;
                k.chest.z = -4.0;
                lunge(k, 0.12, 0.10);
            });
            let rebound = with(&slam, |k| {
                k.hand_r = h(-0.05, -0.62, 0.98, s);
                k.chest.x = 26.0;
                k.off.z += 0.03;
            });
            Mel { keys: vec![(0.0, r), (0.26, up), (0.30, up), (0.40, slam), (0.47, rebound), (0.66, slam), (1.0, r)], trail: (0.30, 0.46) }
        }
        Wk::StormAxe => {
            // çapraz şimşek kesik: sağ omuzdan sol kalçaya
            let k1 = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.30, 0.04, 1.66, s);
                k.hand_l = h(-0.08, 0.10, 1.46, s);
                k.wdir = v(-0.35, 0.50, 0.80);
                k.wup = v(0.0, 0.8, -0.6);
                k.chest.x = -10.0;
                k.chest.z = 28.0;
                k.hips.z = 14.0;
                coil(k, 0.05);
            });
            let k2 = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.02, -0.60, 1.00, s);
                k.hand_l = h(0.06, -0.44, 0.92, s);
                k.wdir = v(0.30, -0.80, -0.52);
                k.wup = v(0.0, -0.6, 0.8);
                k.chest.x = 28.0;
                k.chest.z = -16.0;
                lunge(k, 0.13, 0.05);
            });
            let k3 = with(&k2, |k| {
                k.hand_r = h(0.16, -0.40, 0.72, s);
                k.hand_l = h(0.12, -0.28, 0.70, s);
                k.wdir = v(0.55, -0.40, -0.72);
                k.chest.z = -34.0;
            });
            Mel { keys: vec![(0.0, r), (0.24, k1), (0.40, k2), (0.56, k3), (1.0, r)], trail: (0.28, 0.60) }
        }
        Wk::Pickaxe => {
            // iki elle baş üstünden dik iniş: sivri uç hedefe saplanır, sonra geri çekilir
            let up = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.12, 0.16, 1.70, s);
                k.hand_l = h(0.02, 0.22, 1.48, s);
                k.wdir = v(-0.06, 0.58, 0.82);
                k.wup = v(0.0, 0.82, -0.58);
                k.chest.x = -20.0;
                k.chest.z = 10.0;
                k.off.z = 0.03;
            });
            let hit = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.06, -0.62, 0.92, s);
                k.hand_l = h(0.03, -0.46, 0.86, s);
                k.wdir = v(0.0, -0.72, -0.69);
                k.wup = v(0.0, -0.69, 0.72);
                k.chest.x = 32.0;
                k.chest.z = -6.0;
                lunge(k, 0.11, 0.09);
            });
            let pull = with(&hit, |k| {
                k.hand_r = h(-0.08, -0.52, 1.02, s);
                k.hand_l = h(0.02, -0.38, 0.96, s);
                k.chest.x = 22.0;
            });
            Mel { keys: vec![(0.0, r), (0.26, up), (0.31, up), (0.40, hit), (0.50, hit), (0.64, pull), (1.0, r)], trail: (0.32, 0.45) }
        }
        Wk::Chainsaw => {
            // testere iki yönlü yatay kesik; motor titremesi anahtar kareler arasında eklenir
            let a = with(&r, |k| {
                k.ik_l = 1.0;
                k.hand_r = h(-0.34, -0.46, 1.10, s);
                k.hand_l = h(-0.06, -0.30, 1.04, s);
                k.wdir = v(-0.30, -0.95, 0.0);
                k.wup = v(0.0, 0.0, 1.0);
                k.chest.z = 26.0;
                k.chest.x = 12.0;
                lunge(k, 0.08, 0.05);
            });
            let b = with(&a, |k| {
                k.hand_r = h(0.10, -0.62, 1.06, s);
                k.hand_l = h(0.10, -0.44, 1.02, s);
                k.wdir = v(0.30, -0.95, -0.05);
                k.chest.z = -22.0;
                lunge(k, 0.14, 0.05);
            });
            let rev = if stage % 2 == 0 { (a, b) } else { (b, a) };
            let (a, b) = rev;
            Mel { keys: vec![(0.0, r), (0.18, a), (0.40, a), (0.58, b), (0.80, b), (1.0, r)], trail: (0.26, 0.74) }
        }
        // ---------------------------------------------------------------- tek elle: bıçak, balta, tokmak, katana, pençe
        Wk::Knife => {
            if stage % 2 == 0 {
                // derin atılımla sapla, ters elden yatay kesik
                let k1 = with(&r, |k| {
                    k.hand_r = h(-0.30, 0.12, 1.20, s);
                    k.wdir = v(-0.1, -0.9, 0.35);
                    k.chest.z = 26.0;
                    k.off.y = 0.04;
                });
                let k2 = with(&r, |k| {
                    k.ik_r = 1.0;
                    k.hand_r = h(-0.06, -0.74, 1.16, s);
                    k.wdir = v(0.05, -1.0, -0.05);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = -22.0;
                    k.chest.x = 10.0;
                    lunge(k, 0.17, 0.0);
                });
                let k3 = with(&k2, |k| k.hand_r = h(-0.12, -0.56, 1.10, s));
                Mel { keys: vec![(0.0, r), (0.24, k1), (0.40, k2), (0.58, k3), (1.0, r)], trail: (0.26, 0.62) }
            } else {
                let k1 = with(&r, |k| {
                    k.hand_r = h(0.12, -0.24, 1.32, s);
                    k.wdir = v(0.85, -0.35, 0.35);
                    k.wup = v(0.0, 0.3, 1.0);
                    k.chest.z = -26.0;
                });
                let k2 = with(&r, |k| {
                    k.ik_r = 1.0;
                    k.hand_r = h(-0.46, -0.48, 1.06, s);
                    k.wdir = v(-0.85, -0.5, -0.1);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = 28.0;
                    lunge(k, 0.11, 0.0);
                });
                let k3 = with(&k2, |k| {
                    k.hand_r = h(-0.50, -0.30, 1.00, s);
                    k.wdir = v(-0.7, 0.2, -0.3);
                });
                Mel { keys: vec![(0.0, r), (0.24, k1), (0.40, k2), (0.58, k3), (1.0, r)], trail: (0.26, 0.62) }
            }
        }
        Wk::Katana => {
            // iaido: omuzdan yatay kesik, geri dönüşte ters kesik
            let flip = stage % 2 == 1;
            let sx = if flip { -1.0 } else { 1.0 };
            let k1 = with(&r, |k| {
                k.hand_r = h(-0.34 * sx, -0.05, 1.30, s);
                k.wdir = v(-0.6 * sx, 0.5, 0.6);
                k.wup = v(0.6 * sx, 0.3, 0.4);
                k.chest.z = 30.0 * sx;
                k.ik_l = 1.0;
                k.hand_l = h(-0.18 * sx, -0.10, 1.20, s);
            });
            let k2 = with(&r, |k| {
                k.ik_r = 1.0;
                k.hand_r = h(-0.02 * sx, -0.58, 1.10, s);
                k.wdir = v(0.85 * sx, -0.55, -0.05);
                k.wup = v(0.0, 0.2, 1.0);
                k.chest.z = -18.0 * sx;
                lunge(k, 0.10, 0.0);
            });
            let k3 = with(&k2, |k| {
                k.hand_r = h(0.30 * sx, -0.30, 0.92, s);
                k.wdir = v(0.6 * sx, 0.5, -0.45);
                k.chest.z = -34.0 * sx;
            });
            Mel { keys: vec![(0.0, r), (0.24, k1), (0.40, k2), (0.58, k3), (1.0, r)], trail: (0.26, 0.62) }
        }
        Wk::Claw => {
            let k1 = with(&r, |k| {
                k.hand_r = h(-0.40, 0.0, 1.45, s);
                k.wdir = v(-0.3, -0.2, 1.0);
                k.wup = v(0.0, -1.0, 0.0);
                k.chest.z = 22.0;
            });
            let k2 = with(&r, |k| {
                k.ik_r = 1.0;
                k.hand_r = h(-0.05, -0.55, 1.05, s);
                k.wdir = v(0.4, -0.9, -0.2);
                k.wup = v(0.0, 0.0, 1.0);
                k.chest.z = -12.0;
                lunge(k, 0.09, 0.0);
            });
            let k3 = with(&k2, |k| {
                k.hand_r = h(0.22, -0.30, 0.80, s);
                k.wdir = v(0.6, -0.4, -0.7);
                k.chest.z = -26.0;
            });
            Mel { keys: vec![(0.0, r), (0.24, k1), (0.40, k2), (0.58, k3), (1.0, r)], trail: (0.26, 0.62) }
        }
        Wk::Hatchet | Wk::Handaxe | Wk::Club | Wk::Effigy => {
            // ağır silah daha yüksekten, daha çok gövdeyle; balta iki yönlü hızlı kombo
            let heavy = if matches!(wk, Wk::Handaxe | Wk::Club | Wk::Effigy) { 1.0 } else { 0.0 };
            if stage % 2 == 0 {
                let k1 = with(&r, |k| {
                    k.hand_r = h(-0.30, 0.10, 1.62 + 0.10 * heavy, s);
                    k.wdir = v(-0.2, 0.6, 0.8);
                    k.wup = v(0.0, 1.0, -0.4);
                    k.chest.z = 20.0 + 6.0 * heavy;
                    k.chest.x = -6.0 - 6.0 * heavy;
                    coil(k, 0.03 + 0.03 * heavy);
                });
                let k2 = with(&r, |k| {
                    k.ik_r = 1.0;
                    k.hand_r = h(-0.10, -0.60, 1.02, s);
                    k.wdir = v(0.15, -0.85, -0.5);
                    k.wup = v(0.0, -0.5, 0.9);
                    k.chest.z = -10.0;
                    k.chest.x = 16.0 + 6.0 * heavy;
                    lunge(k, 0.09 + 0.03 * heavy, 0.03);
                });
                let k3 = with(&k2, |k| {
                    k.hand_r = h(0.10, -0.40, 0.78, s);
                    k.wdir = v(0.3, -0.4, -0.9);
                });
                Mel { keys: vec![(0.0, r), (0.22, k1), (0.40, k2), (0.56, k3), (1.0, r)], trail: (0.28, 0.58) }
            } else {
                let k1 = with(&r, |k| {
                    k.hand_r = h(0.22, -0.30, 1.22, s);
                    k.wdir = v(0.8, -0.4, 0.2);
                    k.wup = v(0.0, 0.2, 1.0);
                    k.chest.z = -34.0;
                    coil(k, 0.03);
                });
                let k2 = with(&r, |k| {
                    k.ik_r = 1.0;
                    k.hand_r = h(-0.38, -0.52, 1.08, s);
                    k.wdir = v(-0.85, -0.45, 0.0);
                    k.wup = v(0.0, 0.0, 1.0);
                    k.chest.z = 30.0;
                    lunge(k, 0.09, 0.02);
                });
                let k3 = with(&k2, |k| {
                    k.hand_r = h(-0.52, -0.20, 1.04, s);
                    k.wdir = v(-0.80, 0.10, 0.1);
                    k.chest.z = 42.0 + 8.0 * heavy;
                });
                Mel { keys: vec![(0.0, r), (0.22, k1), (0.40, k2), (0.56, k3), (1.0, r)], trail: (0.28, 0.58) }
            }
        }
        _ => Mel { keys: vec![(0.0, r), (1.0, r)], trail: (0.0, 0.0) },
    }
}

/// Yakın dövüşte kombo düzeni: kaç vuruşluk bir tur
pub fn combo_len(wk: Wk) -> u32 {
    match wk {
        Wk::BloodScythe | Wk::IceScythe => 3,
        _ => 2,
    }
}

fn melee(st: &Style, ph: f32, wk: Wk, s: f32, combo: u32) -> (K, f32, u8) {
    let mut r = rest(st);
    ready(&mut r, wk, s);
    let stage = combo % combo_len(wk);
    let m = melee_keys(&r, wk, s, stage);
    let mut k = track(&m.keys, ph);
    let (a, b) = m.trail;
    let trail = if b > a && ph > a && ph < b { (std::f32::consts::PI * (ph - a) / (b - a)).sin().powf(0.55) } else { 0.0 };
    if wk == Wk::Chainsaw && ph > 0.12 && ph < 0.86 {
        // motor titremesi: eller ve gövde ince ince sarsılır
        let j = (ph * 520.0).sin() * 0.006 * s;
        k.hand_r = k.hand_r + v(j, -j * 0.6, j * 0.5);
        k.hand_l = k.hand_l + v(-j, j * 0.5, 0.0);
        k.chest.x += (ph * 410.0).sin() * 0.7;
    }
    (k, trail, stage as u8)
}

/// Ateş: geri tepme (faz 0..1), namlu alevi ilk %20; roketatarda omuz çöker, boru yukarı fırlar
fn fire(st: &Style, ph: f32, wk: Wk, s: f32) -> (K, f32) {
    let mut r = rest(st);
    ready(&mut r, wk, s);
    aim(&mut r, wk, s);
    let mut kick = r;
    let rk = match wk {
        Wk::Sniper | Wk::Rpg => 1.0,
        Wk::Ak => 0.6,
        _ => 0.4,
    };
    kick.hand_r = kick.hand_r + v(0.0, 0.07 * rk, 0.03 * rk);
    kick.wdir = (kick.wdir + v(0.0, 0.0, 0.25 * rk)).norm();
    kick.chest.x -= 6.0 * rk;
    kick.off.y = 0.03 * rk;
    if wk == Wk::Rpg {
        // arka alev basıncı: gövde geriye, dizler çöker, boru omuzda yukarı sıçrar
        kick.hand_r = r.hand_r + v(0.0, 0.14, 0.05) * s;
        kick.wdir = v(0.0, -0.92, 0.38);
        kick.chest.x = r.chest.x - 11.0;
        kick.off.y = 0.07;
        kick.off.z = -0.05;
        kick.thigh_r.x += 12.0;
        kick.shin_r += 14.0;
        kick.head.x -= 6.0;
    }
    let k = track(&[(0.0, r), (0.10, kick), (0.28, kick), (0.7, r), (1.0, r)], ph);
    (k, if ph < 0.2 { 1.0 - ph / 0.2 } else { 0.0 })
}

/// Nişan: tüfek omza kalkar (dipçik omuzda, yanak dipçikte), tabanca iki elle öne uzanır
fn aim(k: &mut K, wk: Wk, s: f32) {
    match wk.grip() {
        Grip::Rifle => {
            k.hand_r = v(-0.11, -0.24, 1.33) * s;
            k.wdir = v(0.0, -1.0, 0.0);
            k.head.x += 7.0;
            k.head.z += 5.0;
            k.chest.z += 4.0;
        }
        Grip::Pistol => {
            k.hand_r = v(-0.07, -0.50, 1.36) * s;
            k.wdir = v(0.0, -1.0, 0.02);
            k.chest.z -= 2.0;
        }
        _ => {}
    }
}

/// Şarjör değiştirme: silah yana eğilir, sol el şarjörü çıkarıp yenisini takar (faz 0..1)
fn reload(st: &Style, ph: f32, wk: Wk, s: f32) -> (K, Rl) {
    if wk == Wk::Rpg {
        return reload_rpg(st, ph, s);
    }
    let mut r = rest(st);
    ready(&mut r, wk, s);
    let mut tilt = r;
    tilt.wdir = v(0.25, -0.8, 0.35);
    tilt.wup = v(0.7, 0.0, 0.7);
    tilt.hand_r = r.hand_r + v(0.08, 0.10, 0.04) * s;
    tilt.chest.x += 6.0;
    tilt.head.x = 16.0;
    let mut out = tilt;
    out.ik_l = 1.0;
    out.hand_l = v(0.22, -0.10, 0.95) * s;
    let keys = [(0.0, r), (0.18, tilt), (0.42, out), (0.70, tilt), (0.9, r), (1.0, r)];
    let k = track(&keys, ph);
    // sol el 0.25-0.75 arası şarjörü taşır; silahta şarjör 0.25-0.72 arası yok
    let hand = (0.25..0.75).contains(&ph);
    (k, Rl { hand, hide: (0.25..0.72).contains(&ph), slide: -1.0, free_left: hand })
}

/// Roketatar: boru öne-aşağı eğilir, sol el sırttan yeni roketi alır, namlu ağzından içeri iter, boru omuza döner
fn reload_rpg(st: &Style, ph: f32, s: f32) -> (K, Rl) {
    let mut r = rest(st);
    ready(&mut r, Wk::Rpg, s);
    let low = with(&r, |k| {
        k.hand_r = h(-0.20, -0.16, 1.14, s);
        k.wdir = v(0.08, -0.84, -0.22);
        k.chest.x += 8.0;
        k.chest.z -= 6.0;
        k.head.x = 10.0;
    });
    let back = with(&low, |k| {
        k.ik_l = 1.0;
        k.hand_l = h(0.24, 0.26, 1.36, s);
        k.chest.z = low.chest.z + 14.0;
        k.chest.x = low.chest.x - 4.0;
    });
    let carry = with(&low, |k| {
        k.ik_l = 1.0;
        k.hand_l = h(0.08, -0.52, 0.96, s);
        k.wdir = v(0.06, -0.92, -0.10);
        k.chest.z = low.chest.z - 4.0;
    });
    let push = with(&carry, |k| {
        k.hand_l = h(0.04, -0.64, 1.00, s);
        k.wdir = v(0.04, -0.98, -0.04);
    });
    let keys = [(0.0, r), (0.16, low), (0.36, back), (0.44, back), (0.62, carry), (0.80, push), (0.90, low), (1.0, r)];
    let k = track(&keys, ph);
    let slide = if (0.66..0.88).contains(&ph) {
        let u = ((ph - 0.66) / 0.22).clamp(0.0, 1.0);
        0.26 * (1.0 - smooth01(u))
    } else {
        -1.0
    };
    let rl = Rl { hand: (0.38..0.66).contains(&ph), hide: ph < 0.88, slide, free_left: (0.16..0.88).contains(&ph) };
    (k, rl)
}

/// Takla: öne yuvarlanma (gövde X etrafında 360°), faz 0..1. Gövde top gibi toplanır, dönme merkezi
/// toplanmış gövdenin ortası; baş dizlere, kollar bacaklara sarılır.
fn roll(st: &Style, ph: f32) -> K {
    let mut k = rest(st);
    let tuck = smooth01((ph / 0.22).min(1.0)) * smooth01(((1.0 - ph) / 0.22).min(1.0));
    k.body = v(-360.0 * smooth01(ph), 0.0, 0.0);
    k.body_z = 0.42;
    k.off.z = -0.50 * tuck;
    k.off.y = -0.25 * ph;
    k.thigh_r.x = 118.0 * tuck;
    k.thigh_l.x = 112.0 * tuck;
    k.shin_r = 140.0 * tuck;
    k.shin_l = 136.0 * tuck;
    k.chest.x = 62.0 * tuck;
    k.head.x = 38.0 * tuck;
    k.arm_r = v(78.0 * tuck, 8.0, 0.0);
    k.arm_l = v(78.0 * tuck, 8.0, 0.0);
    k.elbow_r = 120.0 * tuck;
    k.elbow_l = 120.0 * tuck;
    k
}

fn smooth01(x: f32) -> f32 {
    let t = x.clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

/// Sırtüstü düşüş (ölüm, yere serilme): faz 0..1
fn fall(st: &Style, ph: f32, dead: bool) -> K {
    let r = rest(st);
    let mut buckle = r;
    buckle.shin_r = 60.0;
    buckle.shin_l = 40.0;
    buckle.thigh_r.x = 30.0;
    buckle.thigh_l.x = 20.0;
    buckle.off.z = -0.30;
    buckle.chest.x = -16.0;
    buckle.head.x = -20.0;
    buckle.arm_r = v(-30.0, 30.0, 0.0);
    buckle.arm_l = v(-20.0, 35.0, 0.0);
    buckle.body = v(18.0, 0.0, 0.0);
    let mut down = r;
    down.body = v(88.0, 0.0, if dead { 12.0 } else { 0.0 });
    down.body_z = 0.0;
    down.off.z = 0.10;
    down.off.y = 0.25;
    down.arm_r = v(-10.0, 60.0, 0.0);
    down.arm_l = v(10.0, 45.0, 0.0);
    down.elbow_r = 30.0;
    down.elbow_l = 50.0;
    down.thigh_r.x = 18.0;
    down.thigh_r.y = 10.0;
    down.thigh_l.y = 14.0;
    down.shin_r = 30.0;
    down.head.z = if dead { 35.0 } else { 0.0 };
    track(&[(0.0, r), (0.35, buckle), (0.8, down), (1.0, down)], ph)
}

fn getup(st: &Style, ph: f32) -> K {
    let r = rest(st);
    let down = fall(st, 1.0, false);
    let mut kneel = r;
    kneel.off.z = -0.42;
    kneel.thigh_r.x = 90.0;
    kneel.shin_r = 95.0;
    kneel.thigh_l.x = -10.0;
    kneel.shin_l = 110.0;
    kneel.chest.x = 25.0;
    kneel.arm_l = v(30.0, 20.0, 0.0);
    kneel.elbow_l = 40.0;
    track(&[(0.0, down), (0.45, kneel), (1.0, r)], ph)
}

fn hurt(st: &Style, ph: f32, wk: Wk, s: f32) -> K {
    let mut r = rest(st);
    ready(&mut r, wk, s);
    let mut h = r;
    h.chest.x -= 16.0;
    h.head.x -= 18.0;
    h.chest.z += 10.0;
    h.off.y = 0.06;
    h.arm_l = v(25.0, 30.0, 0.0);
    h.elbow_l = 60.0;
    track(&[(0.0, r), (0.3, h), (1.0, r)], ph)
}

/// Motosiklet sürüşü: oturur, eller gidonda
fn ride(st: &Style, t: f32, s: f32) -> K {
    let mut k = rest(st);
    k.thigh_r = v(82.0, 14.0, 0.0);
    k.thigh_l = v(82.0, 14.0, 0.0);
    k.shin_r = 95.0;
    k.shin_l = 95.0;
    k.off.z = -0.38;
    k.chest.x = 26.0;
    k.head.x = -18.0;
    k.ik_r = 1.0;
    k.ik_l = 1.0;
    let w = 0.01 * (t * 9.0).sin();
    k.hand_r = v(-0.26, -0.50, 0.98 + w) * s;
    k.hand_l = v(0.26, -0.50, 0.98 - w) * s;
    k
}

fn gather(st: &Style, ph: f32, anim: u8, s: f32, flags: u32) -> (K, f32, Wk) {
    let mut r = rest(st);
    match anim {
        a::CHOP => {
            // odun kesme: omuz üstünden çapraz iniş, gövdeye saplanıp sapı hafifçe çevirip çekme
            let wk = if flags & F_IRON != 0 { Wk::Handaxe } else { Wk::Hatchet };
            ready(&mut r, wk, s);
            let k1 = with(&r, |k| {
                k.hand_r = h(-0.34, 0.12, 1.70, s);
                k.wdir = v(-0.2, 0.65, 0.75);
                k.wup = v(0.0, 1.0, -0.3);
                k.chest.z = 24.0;
                k.chest.x = -8.0;
                coil(k, 0.05);
            });
            let k2 = with(&r, |k| {
                k.hand_r = h(-0.08, -0.64, 0.98, s);
                k.wdir = v(0.18, -0.85, -0.50);
                k.wup = v(0.0, -0.5, 0.9);
                k.chest.z = -10.0;
                k.chest.x = 20.0;
                lunge(k, 0.10, 0.04);
            });
            let k3 = with(&k2, |k| {
                k.hand_r = h(-0.10, -0.60, 0.94, s);
                k.wdir = v(0.12, -0.86, -0.50);
                k.chest.x = 22.0;
            });
            let k4 = with(&k2, |k| {
                k.hand_r = h(-0.14, -0.46, 1.08, s);
                k.wdir = v(0.0, -0.80, -0.20);
                k.chest.x = 12.0;
            });
            let k = track(&[(0.0, r), (0.20, k1), (0.40, k2), (0.50, k3), (0.64, k4), (1.0, r)], ph);
            (k, if (0.30..0.46).contains(&ph) { 0.5 } else { 0.0 }, wk)
        }
        a::MINE => {
            let wk = Wk::Pickaxe;
            let (k, tr, _) = melee(st, ph, wk, s, 0);
            (k, tr * 0.5, wk)
        }
        a::PICKUP | a::OPEN => {
            let mut low = r;
            low.off.z = -0.32;
            low.thigh_r.x = 70.0;
            low.shin_r = 90.0;
            low.thigh_l.x = 20.0;
            low.shin_l = 70.0;
            low.chest.x = 38.0;
            low.ik_r = 1.0;
            low.hand_r = v(-0.18, -0.48, if anim == a::OPEN { 0.55 } else { 0.25 }) * s;
            (track(&[(0.0, r), (0.4, low), (0.65, low), (1.0, r)], ph), 0.0, Wk::None)
        }
        a::DRINK => {
            let mut d = r;
            d.ik_r = 1.0;
            d.hand_r = v(-0.04, -0.18, 1.55) * s;
            d.head.x = -22.0;
            (track(&[(0.0, r), (0.3, d), (0.75, d), (1.0, r)], ph), 0.0, Wk::None)
        }
        a::CHEER => {
            let mut c = r;
            c.arm_r = v(170.0, 15.0, 0.0);
            c.arm_l = v(170.0, 15.0, 0.0);
            c.elbow_r = 10.0;
            c.elbow_l = 10.0;
            c.off.z = 0.06 * (ph * TAU * 2.0).sin().abs();
            (c, 0.0, Wk::None)
        }
        a::POINT => {
            let mut p = r;
            p.arm_r = v(85.0, 10.0, 0.0);
            p.elbow_r = 4.0;
            p.chest.z = 10.0;
            (track(&[(0.0, r), (0.3, p), (1.0, p)], ph), 0.0, Wk::None)
        }
        _ => {
            // büyü (cast): iki el öne
            let mut c = r;
            c.ik_r = 1.0;
            c.ik_l = 1.0;
            c.hand_r = v(-0.14, -0.55, 1.25) * s;
            c.hand_l = v(0.14, -0.55, 1.25) * s;
            c.chest.x = -6.0;
            (track(&[(0.0, r), (0.3, c), (0.8, c), (1.0, r)], ph), 0.0, Wk::None)
        }
    }
}

/// Animasyon durumundan anahtar kareyi üretir
pub fn key_of(inp: &In, s: f32) -> Key {
    let st = &inp.style;
    let wk = inp.weapon;
    let t = inp.t;
    let ph_once = |dur: f32| (t / dur).clamp(0.0, 1.0);
    let plain = |k: K, shown: Wk| Key { k, trail: 0.0, muzzle: 0.0, shown, rl: None, stage: 0 };
    match inp.anim {
        a::IDLE | a::PISTOL_IDLE | a::RIFLE_IDLE => plain(idle(st, t, wk, s), wk),
        a::WALK | a::PISTOL_WALK | a::RIFLE_WALK => plain(stride(st, t, false, wk, s), wk),
        a::RUN => plain(stride(st, t, true, wk, s), wk),
        a::PUNCH => {
            // çekirdek: 4/14 sn'de bir yumruk, sırayla sol/sağ
            let half = 4.0 / 14.0;
            let n = (t / half) as u32;
            plain(punch(st, (t % half) / half, n % 2 == 1), Wk::None)
        }
        a::MELEE => {
            let per = inp.period.max(0.2);
            let n = (t / per) as u32;
            let (k, trail, stage) = melee(st, (t % per) / per, wk, s, n);
            Key { k, trail, muzzle: 0.0, shown: wk, rl: None, stage }
        }
        a::FIRE => {
            let per = inp.period.max(0.08);
            let (k, m) = fire(st, (t % per) / per, wk, s);
            Key { k, trail: 0.0, muzzle: m, shown: wk, rl: None, stage: 0 }
        }
        a::RELOAD => {
            let (k, rl) = reload(st, (t / inp.period.max(0.3)).fract(), wk, s);
            Key { k, trail: 0.0, muzzle: 0.0, shown: wk, rl: Some(rl), stage: 0 }
        }
        a::ROLL => plain(roll(st, ph_once(0.32)), Wk::None),
        a::DEATH => plain(fall(st, ph_once(0.9), true), Wk::None),
        a::KNOCK => plain(fall(st, ph_once(0.6), false), Wk::None),
        a::GETUP => plain(getup(st, ph_once(0.5)), Wk::None),
        a::HURT => plain(hurt(st, ph_once(0.18), wk, s), wk),
        a::RIDE => plain(ride(st, t, s), Wk::None),
        a::CHOP | a::MINE => {
            let (k, trail, w) = gather(st, (t / 0.9).fract(), inp.anim, s, inp.flags);
            Key { k, trail, muzzle: 0.0, shown: w, rl: None, stage: 0 }
        }
        x => {
            let (k, trail, w) = gather(st, ph_once(1.0), x, s, inp.flags);
            Key { k, trail, muzzle: 0.0, shown: w, rl: None, stage: 0 }
        }
    }
}

/// Anahtar kareyi iskelet pozuna çevirir, kolları IK ile çözer, silahı yerleştirir
pub fn evaluate(sk: &Skeleton, inp: &In) -> Out {
    let s = sk.height / 1.86;
    let Key { k, trail, muzzle, shown, rl, stage } = key_of(inp, s);
    let mut pose = Pose::default();
    let lr = |d: V3| M3::xyz(d.x, d.y, d.z);
    pose.local[PELVIS] = M3::rz(k.hips.z) * M3::ry(k.hips.y) * M3::rx(k.hips.x);
    pose.local[SPINE] = M3::rz(k.chest.z) * M3::ry(k.chest.y) * M3::rx(k.chest.x);
    pose.local[HEAD] = lr(v3(k.head.x, k.head.y, k.head.z));
    // bacak: öne kaldırma = Rx(−), dışa açma: sağda +Y, solda −Y; diz = Rx(+)
    pose.local[THIGH_R] = M3::rz(k.thigh_r.z) * M3::ry(k.thigh_r.y) * M3::rx(-k.thigh_r.x);
    pose.local[THIGH_L] = M3::rz(-k.thigh_l.z) * M3::ry(-k.thigh_l.y) * M3::rx(-k.thigh_l.x);
    pose.local[SHIN_R] = M3::rx(k.shin_r);
    pose.local[SHIN_L] = M3::rx(k.shin_l);
    pose.local[UPPER_R] = M3::rz(k.arm_r.z) * M3::ry(k.arm_r.y) * M3::rx(-k.arm_r.x);
    pose.local[UPPER_L] = M3::rz(-k.arm_l.z) * M3::ry(-k.arm_l.y) * M3::rx(-k.arm_l.x);
    pose.local[FORE_R] = M3::rx(-k.elbow_r);
    pose.local[FORE_L] = M3::rx(-k.elbow_l);
    pose.root_t = k.off * s;
    pose.body = M3::xyz(k.body.x, k.body.y, k.body.z);
    pose.body_pivot = v3(0.0, 0.0, k.body_z * s);
    let grip = shown.grip();
    // IK: hedefler göğüs (spine) uzayında
    let xf0 = sk.world(&pose);
    let chest = xf0[SPINE];
    let pole_r = v3(-0.7, 0.45, -0.35);
    let pole_l = v3(0.7, 0.45, -0.35);
    if k.ik_r >= 0.5 {
        let tr = chest.apply(k.hand_r);
        sk.solve_arm(&mut pose, UPPER_R, FORE_R, sk.palm_r, tr, chest.r.apply(pole_r));
    }
    let wb = chest.r * M3::basis(k.wdir, k.wup);
    // iki elli tutuşlarda sol el silahın sapında/namlusunda; değiştirilen parça elde ya da sol el serbestken anahtar kareyi izler
    let free_left = rl.map(|r| r.free_left).unwrap_or(false);
    let on_weapon = shown != Wk::None && k.ik_r >= 0.5 && matches!(grip, Grip::Two | Grip::Rifle | Grip::Pistol | Grip::Shoulder) && !free_left;
    // silahın dünya yerleşimi (sağ avuç + yön): parçanın kayması bunun üzerinden
    let wxf = if shown != Wk::None {
        let pr = if k.ik_r >= 0.5 {
            let xf1 = sk.world(&pose);
            xf1[HAND_R].apply(sk.palm_r)
        } else {
            sk.world(&pose)[HAND_R].apply(sk.palm_r)
        };
        let b = if k.ik_r >= 0.5 { wb } else { sk.world(&pose)[HAND_R].r * M3::basis(k.wdir, k.wup) };
        Some(Xf::new(b, pr))
    } else {
        None
    };
    let sliding = rl.filter(|r| r.slide >= 0.0 && wxf.is_some());
    if on_weapon {
        let xf1 = sk.world(&pose);
        let pr = xf1[HAND_R].apply(sk.palm_r);
        let fwd = wb.col(0);
        let up = wb.col(2);
        let back = if inp.wback > 0.05 { inp.wback } else { 0.4 };
        let tl = match grip {
            Grip::Two => pr - fwd * (0.30 * s).min(back * 0.85),
            Grip::Rifle => pr + fwd * (0.28 * s) - up * 0.03,
            Grip::Shoulder => pr + fwd * (0.30 * s) - up * 0.06,
            _ => pr + fwd * (0.04 * s) - up * 0.06,
        };
        sk.solve_arm(&mut pose, UPPER_L, FORE_L, sk.palm_l, tl, chest.r.apply(pole_l));
    } else if let (Some(sl), Some(wx)) = (sliding, wxf) {
        // yeni roketi namlu ağzından iten el: roketin gövdesinde, ağırlık merkezinin biraz gerisinde
        let tl = wx.apply(inp.pivot + v3(sl.slide - 0.06, 0.0, 0.05));
        sk.solve_arm(&mut pose, UPPER_L, FORE_L, sk.palm_l, tl, chest.r.apply(pole_l));
    } else if k.ik_l >= 0.5 {
        let tl = chest.apply(k.hand_l);
        sk.solve_arm(&mut pose, UPPER_L, FORE_L, sk.palm_l, tl, chest.r.apply(pole_l));
    }
    let world = sk.world(&pose);
    let mut weapon = None;
    let mut mag = None;
    let mut mag_hidden = false;
    if shown != Wk::None {
        let pr = world[HAND_R].apply(sk.palm_r);
        let b = if k.ik_r >= 0.5 { wb } else { world[HAND_R].r * M3::basis(k.wdir, k.wup) };
        let wx = Xf::new(b, pr);
        weapon = Some(wx);
        // roketatar boşken başlık namluda yok (atıştan yeniden doldurmanın sonuna dek)
        mag_hidden = match rl {
            Some(r) => r.hide,
            None => shown == Wk::Rpg && inp.flags & F_EMPTY != 0,
        };
        if let Some(r) = rl {
            if r.hand {
                let pl = world[HAND_L].apply(sk.palm_l);
                // roket namlu eksenine paralel taşınır; şarjör elin yönünde
                let m = if shown == Wk::Rpg { wx.r } else { world[HAND_L].r * M3::basis(v3(0.0, -0.3, 1.0), v3(0.0, -1.0, -0.3)) };
                mag = Some(Xf::new(m, pl));
            } else if r.slide >= 0.0 {
                mag = Some(Xf::new(wx.r, wx.apply(inp.pivot + v3(r.slide, 0.0, 0.0))));
            }
        }
    }
    Out { pose, world, weapon, mag, mag_hidden, trail, stage, muzzle, shown }
}
