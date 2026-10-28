//! V2 oyun durumu ve ana döngü.
#[allow(unused_imports)]
use crate::Hyp;
use crate::data::{self, WeaponClass, ZKind, PX_PER_M, WEAPONS};
use crate::ground::Textures;
use crate::progress::{Progress, CITY_LEVEL};
use crate::rng::Rng;
use crate::world::{self, World};
use serde::Serialize;
use std::f32::consts::{FRAC_PI_4, TAU};

// ---- kahraman animasyonları (assetgen/character2.HERO_ANIMS sırası)
pub const A_IDLE: u8 = 0;
pub const A_WALK: u8 = 1;
pub const A_RUN: u8 = 2;
pub const A_PUNCH: u8 = 3;
pub const A_MELEE: u8 = 4;
pub const A_PISTOL_IDLE: u8 = 5;
pub const A_PISTOL_WALK: u8 = 6;
pub const A_RIFLE_IDLE: u8 = 7;
pub const A_RIFLE_WALK: u8 = 8;
pub const A_CAST: u8 = 9;
pub const A_HURT: u8 = 10;
pub const A_DEATH: u8 = 11;
pub const A_RIDE: u8 = 12;
pub const A_ROLL: u8 = 13;
pub const A_KNOCK: u8 = 14;
pub const A_GETUP: u8 = 15;
// V10 elle çizilmiş etkileşim pozları (oyuncu dururken oynar; hareket ya da saldırı keser)
pub const A_PICKUP: u8 = 16;
pub const A_CHOP: u8 = 17;
/// Ağaç kesme ve kaya kırma animasyonunun döngüsü (sn) ve aletin gövdeye indiği an (döngü oranı); zenvex_r3d ile aynı
pub const GATHER_CYCLE: f32 = 0.9;
pub const GATHER_IMPACT: f32 = 0.40;
pub const A_MINE: u8 = 18;
pub const A_OPEN: u8 = 19;
pub const A_DRINK: u8 = 20;
pub const A_CHEER: u8 = 21;
pub const A_POINT: u8 = 22;
/// yalnız çizim/ağ (3B): ayakta ateş (geri tepme) ve şarjör değiştirme; saat faz (0..1)
pub const A_FIRE: u8 = 40;
pub const A_RELOAD: u8 = 41;
// zombi animasyonları
pub const Z_IDLE: u8 = 0;
pub const Z_WALK: u8 = 1;
pub const Z_ATTACK: u8 = 2;
pub const Z_HURT: u8 = 3;
pub const Z_DEATH: u8 = 4;
/// V13 Magma Titan: iki elle yere vurma ve ateş püskürtme
pub const Z_SLAM: u8 = 5;
pub const Z_BREATH: u8 = 6;

// ---- çizim listesi
pub const STRIDE: usize = 12;
/// takla süresi (sn) ve karanlık aura yarıçapı (px)
pub const ROLL_TIME: f32 = 0.32;
/// Oyuncu hızları (px/sn): yürüme 1.25 m/sn, koşu 3.1 m/sn
pub const WALK_SPEED: f32 = 1.25 * PX_PER_M;
pub const RUN_SPEED: f32 = 3.1 * PX_PER_M;
/// Dayanıklılık bedelleri: yumruk, kılıç/bıçak savurma, takla (yürüme/koşu bedava)
pub const ST_PUNCH: f32 = 5.0;
pub const ST_SWING: f32 = 7.0;
pub const ST_ROLL: f32 = 18.0;
pub const AURA_R: f32 = 190.0;
pub const D_STATIC: f32 = 0.0;
pub const D_HUMAN: f32 = 1.0;
pub const D_ZOMBIE: f32 = 2.0;
pub const D_DROP: f32 = 3.0;
pub const D_PROJ: f32 = 4.0;
pub const D_VEHICLE: f32 = 5.0;
pub const D_DRONE: f32 = 6.0;
pub const D_AREA: f32 = 7.0;

pub fn m(v: f32) -> f32 {
    v * PX_PER_M
}

pub fn dir_from_vec(x: f32, y: f32) -> u8 {
    let mut a = x.atan2(y);
    if a < 0.0 {
        a += TAU;
    }
    ((a / FRAC_PI_4).round() as i32).rem_euclid(8) as u8
}

pub fn dir_vec(d: u8) -> (f32, f32) {
    let a = d as f32 * FRAC_PI_4;
    (a.sin(), a.cos())
}

#[derive(Clone, Copy, Debug, Default)]
pub struct Input {
    pub move_x: f32,
    pub move_y: f32,
    pub atk_x: f32,
    pub atk_y: f32,
    pub attacking: bool,
}

#[derive(Serialize, Clone, Debug)]
#[serde(tag = "t")]
pub enum Event {
    #[serde(rename = "roll")]
    Roll { x: f32, y: f32 },
    #[serde(rename = "dodge")]
    Dodge { x: f32, y: f32 },
    #[serde(rename = "knock")]
    Knockdown,
    #[serde(rename = "potion")]
    Potion { kind: u8 },
    #[serde(rename = "dmg")]
    Damage { x: f32, y: f32, v: i32, crit: bool },
    #[serde(rename = "pdmg")]
    PlayerDamage { x: f32, y: f32, v: i32 },
    #[serde(rename = "xp")]
    Xp { x: f32, y: f32, v: u32 },
    #[serde(rename = "gold")]
    Gold { x: f32, y: f32, v: u32 },
    #[serde(rename = "pickup")]
    Pickup {
        x: f32,
        y: f32,
        name: String,
        count: u32,
        rarity: u8,
    },
    #[serde(rename = "invfull")]
    InvFull,
    #[serde(rename = "lvl")]
    LevelUp { level: u32, points: u32 },
    #[serde(rename = "clvl")]
    CharLevel { level: u32 },
    #[serde(rename = "hit")]
    Hit { x: f32, y: f32, kind: u8 },
    /// PvP: yerel oyuncu uzak oyuncuya (remotes[k]) vurdu; istemci hedefe iletir
    #[serde(rename = "pvp")]
    PvpHit { k: u32, v: f32, crit: bool },
    #[serde(rename = "treefall")]
    TreeFall { x: f32, y: f32 },
    #[serde(rename = "kill")]
    Kill { x: f32, y: f32, z: u8 },
    #[serde(rename = "died")]
    Died,
    #[serde(rename = "swing")]
    Swing,
    #[serde(rename = "shoot")]
    Shoot { x: f32, y: f32, a: f32, w: u8 },
    #[serde(rename = "reload")]
    Reload,
    #[serde(rename = "noammo")]
    NoAmmo,
    #[serde(rename = "explode")]
    Explode { x: f32, y: f32, r: f32 },
    #[serde(rename = "groan")]
    Groan { x: f32, y: f32, z: u8 },
    #[serde(rename = "teleport")]
    Teleport { x: f32, y: f32 },
    #[serde(rename = "toxic")]
    ToxicCloud { x: f32, y: f32 },
    #[serde(rename = "slam")]
    Slam { x: f32, y: f32, r: f32 },
    /// V13 Magma Titan saldırı efekti: a = 0 pençe, 1 yere vurma, 2 ateş püskürtme (tx, ty: hedef)
    #[serde(rename = "magma")]
    Magma { x: f32, y: f32, a: u8, tx: f32, ty: f32 },
    #[serde(rename = "boss")]
    Boss { name: String },
    #[serde(rename = "quest")]
    Quest { title: String },
    #[serde(rename = "zone")]
    ZoneEnter {
        name: String,
        lo: u32,
        hi: u32,
        safe: bool,
    },
    #[serde(rename = "locked")]
    CityLocked,
    /// kule askeri mızrak fırlattı (t: uçuş süresi)
    #[serde(rename = "spear")]
    Spear { x1: f32, y1: f32, x2: f32, y2: f32, dur: f32 },
    /// güvenli bölgeye yaklaşan yaratığa tanrıların yıldırımı
    #[serde(rename = "bolt")]
    Lightning { x: f32, y: f32 },
    #[serde(rename = "doghit")]
    DogHit { x: f32, y: f32, v: i32 },
    #[serde(rename = "drone")]
    DroneShot { x: f32, y: f32 },
    #[serde(rename = "shield")]
    Shield { v: i32 },
    #[serde(rename = "heal")]
    Heal { x: f32, y: f32, v: i32 },
    #[serde(rename = "rage")]
    Rage,
    #[serde(rename = "cloak")]
    Cloak,
    #[serde(rename = "crate")]
    Crate { x: f32, y: f32 },
    #[serde(rename = "car_hit")]
    CarHit { x: f32, y: f32 },
    #[serde(rename = "enter")]
    EnterVehicle { v: u8 },
    #[serde(rename = "exit")]
    ExitVehicle,
    #[serde(rename = "msg")]
    Msg { text: String },
    #[serde(rename = "lastbreath")]
    LastBreath,
    #[serde(rename = "step")]
    Step,
    /// ev sahibi: uzak oyuncuya zombi hasarı (i = setRemotes sırası)
    #[serde(rename = "rdmg")]
    RemoteDamage { i: usize, v: i32, poison: bool },
    /// istemci: zombiye vuruş → ev sahibine gönderilir
    #[serde(rename = "zhit")]
    ZHit { id: u32, dmg: f32, kx: f32, ky: f32, stun: bool },
    /// ev sahibi: uzak oyuncunun öldürdüğü zombi → ödül o oyuncuya
    #[serde(rename = "nkill")]
    NetKill { k: i32, kind: u8, level: u32, x: f32, y: f32 },
    /// V8 devlet üssü: asker atışı (k: 0 M4, 1 keskin nişancı)
    #[serde(rename = "botshot")]
    BotShot { x1: f32, y1: f32, x2: f32, y2: f32, hit: bool, k: u8 },
    #[serde(rename = "botdown")]
    BotDown { x: f32, y: f32 },
    /// yeni inen asker 5 sn ölümsüz
    #[serde(rename = "immune")]
    Immune { x: f32, y: f32 },
    /// takviye helikopteri yola çıktı (piste iner)
    #[serde(rename = "heli")]
    Heli { x: f32, y: f32 },
    /// V11 eğitim üssüne hava saldırısı: bomba dur sn içinde (x, y)'ye düşer
    #[serde(rename = "bomb")]
    Bomb { x: f32, y: f32, dur: f32 },
    /// ele geçirme süresi doldu: Kotlin sunucuya bildirir
    #[serde(rename = "basecap")]
    BaseCapture { id: u32 },
}

#[derive(Clone, Debug, Default)]
pub struct Player {
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub anim: u8,
    pub anim_t: f32,
    pub hp: f32,
    pub stamina: f32,
    pub stamina_delay: f32,
    pub shield: f32,
    pub shield_t: f32,
    pub rage_t: f32,
    /// Gölge'nin Kan Yemini: can çalma +%15, saldırı hızı +%30
    pub oath_t: f32,
    pub cloak_t: f32,
    pub cloak_cd: f32,
    pub energy_t: f32,
    pub food_t: f32,
    pub poison_t: f32,
    pub heal_t: f32,
    pub heal_rate: f32,
    pub atk_cd: f32,
    pub atk_active: bool,
    pub hit_done: bool,
    pub reload_t: f32,
    pub ability_cd: f32,
    pub cast_t: f32,
    pub vehicle: Option<usize>,
    pub dead: bool,
    pub combat_t: f32,
    pub last_breath_cd: f32,
    pub hurt_t: f32,
    /// sendeleme bekleme süresi: art arda vuruşlar (kurşun yağmuru) oyuncuyu kilitlemesin
    pub flinch_cd: f32,
    /// sürekli hasar (zehir birikintisi) için biriken hasar yazısı
    pub dot_acc: f32,
    pub dot_t: f32,
    pub flash_t: f32,
    pub moving: bool,
    pub step_t: f32,
    pub noammo_t: f32,
    pub locked_t: f32,
    pub zone: u8,
    pub god: bool,
    pub one_hit: bool,
    pub speed_cheat: f32,
    // V4
    pub roll_t: f32,
    /// yakın dövüş döngü süresi (sn); çizim/ağ için animasyon süresi döngü sayısına çevrilir
    pub atk_period: f32,
    pub roll_dx: f32,
    pub roll_dy: f32,
    /// takladan sonra hasar almama
    pub iframe_t: f32,
    pub speed_t: f32,
    pub power_t: f32,
    /// iksir beklemeleri: can, hız, güç, panzehir
    pub pot_cd: [f32; 4],
    pub aura_t: f32,
    pub inf_ammo: bool,
    pub no_cd: bool,
    pub invis: bool,
    /// Titan dalgasıyla yere düştü (kalkma animasyonu için)
    pub knocked: bool,
    /// etkileşim pozu (toplama, içme, sevinç...) ve kalan süresi
    pub pose: u8,
    pub pose_t: f32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ZState {
    Wander,
    Notice,
    Chase,
    Attack,
    Hurt,
    Flee,
    Dead,
}

#[derive(Clone, Debug)]
pub struct Zombie {
    /// ağ kimliği (ev sahibi atar)
    pub id: u32,
    /// istemcide ara değerleme hedefi: son anlık görüntü, iki görüntü arası hız ve geldiği an (oyun saati)
    pub snap_x: f32,
    pub snap_y: f32,
    pub snap_vx: f32,
    pub snap_vy: f32,
    pub snap_at: f32,
    pub kind: ZKind,
    pub level: u32,
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub state: ZState,
    pub anim: u8,
    pub anim_t: f32,
    pub hp: f32,
    pub max_hp: f32,
    pub tx: f32,
    pub ty: f32,
    pub timer: f32,
    pub attack_cd: f32,
    pub hit_done: bool,
    pub flash_t: f32,
    pub bar_t: f32,
    pub kx: f32,
    pub ky: f32,
    pub alpha: f32,
    pub special_cd: f32,
    pub puddle_t: f32,
    pub decoy: bool,
    pub cloned: bool,
    pub summoned: bool,
    pub enraged: bool,
    pub boss: bool,
    /// doğduğu yer: bölgesinden fazla uzaklaşınca buraya döner
    pub home_x: f32,
    pub home_y: f32,
    /// eve dönüyor (bu sırada kimseyi görmez, canı dolar)
    pub ret: bool,
    /// köpek ısırdıysa bir süre köpeğe odaklanır
    pub dog_aggro: f32,
    /// V13 bölge yaratığı (data::SPECIES indeksi + 1; 0 klasik zombi)
    pub species: u8,
}

#[derive(Clone, Debug)]
pub struct Drop {
    pub item: u16,
    pub count: u32,
    /// tekil ekipmanın dayanıklılığı (binde; yere bırakıp almak canı yenilemez)
    pub dur: u16,
    pub x: f32,
    pub y: f32,
    pub age: f32,
    pub pop: f32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ProjKind {
    Bullet = 0,
    Rocket = 1,
    DroneBullet = 2,
    Acid = 3,
    DroneRocket = 4,
}

#[derive(Clone, Debug)]
pub struct Projectile {
    pub kind: ProjKind,
    pub x: f32,
    pub y: f32,
    pub vx: f32,
    pub vy: f32,
    pub dmg: f32,
    pub pierce: u8,
    pub life: f32,
    pub splash: f32,
    pub hostile: bool,
    pub hit: Vec<usize>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AreaKind {
    ToxicPuddle = 0,
    PoisonCloud = 1,
    Heal = 2,
    ShieldFx = 3,
    Shockwave = 4,
    Explosion = 5,
}

#[derive(Clone, Debug)]
pub struct Area {
    pub kind: AreaKind,
    pub x: f32,
    pub y: f32,
    pub r: f32,
    pub life: f32,
    pub max_life: f32,
    pub dps: f32,
    pub hit_done: bool,
}

#[derive(Clone, Debug)]
pub struct Vehicle {
    pub kind: usize,
    pub x: f32,
    pub y: f32,
    pub angle: f32,
    pub speed: f32,
    pub hp: f32,
    pub fuel: f32,
    pub driver: bool,
    pub wrecked: bool,
    /// oyuncunun kendi aracı (anahtarla çağrılan; ikinci kez çağrılınca yeni araç doğmaz, bu yanına gelir)
    pub owned: bool,
}

#[derive(Clone, Debug)]
pub struct Npc {
    pub kind: u8, // 0 kule askeri, 1 tüccar, 2 görevci, 3 doktor, 4 tamirci, 5 köpek eğitmeni, 6 rehber
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub cd: f32,
    pub anim_t: f32,
    /// kule askeri: havadaki mızrağın çarpmasına kalan süre ve hedef zombi kimliği
    pub hit_t: f32,
    pub hit_id: u32,
}

#[derive(Clone, Debug, Default)]
pub struct Drone {
    pub x: f32,
    pub y: f32,
    pub cd: f32,
    pub t: f32,
}

#[derive(Clone, Debug, Default)]
pub struct Remote {
    pub x: f32,
    pub y: f32,
    pub dir: u8,
    pub anim: u8,
    pub anim_t: f32,
    pub character: u8,
    pub equip: [f32; 3],
    pub vehicle: f32,
}

pub struct Game {
    /** köpeğin vuruşu sırasında doğru: öldürme köpek sayacına da yazılır */
    pub dog_hit: bool,
    /** Hile komutları yalnız sunucunun yönetici dediği oturumda açılır (testlerde açık) */
    pub cheats_ok: bool,
    pub world: World,
    pub progress: Progress,
    pub player: Player,
    pub zombies: Vec<Zombie>,
    pub drops: Vec<Drop>,
    pub projectiles: Vec<Projectile>,
    pub areas: Vec<Area>,
    pub vehicles: Vec<Vehicle>,
    pub npcs: Vec<Npc>,
    pub drone: Drone,
    pub remotes: Vec<Remote>,
    /// PvP: uzak oyuncu düşman mı (başka klan / klansız); istemci her saniye günceller
    pub remote_hostile: Vec<bool>,
    /// PvP: öldürülen oyuncu sayısı bu oturumda istemciye bildirilir (sayaç progress.ev.pvp_kills)
    pub pvp_hit_t: f32,
    /// klan savaşı: üssün sahibi klandan biri ele geçirme halkasındaysa (istemci bildirir)
    pub capture_blocked: bool,
    /// 0 tek oyunculu, 1 ev sahibi (zombileri o çalıştırır), 2 istemci
    pub net: u8,
    pub next_zid: u32,
    /// ev sahibinde uzak oyuncu vuruşunun sahibi (-1 = yerel)
    pub kill_credit: i32,
    /// başka oyuncunun aracında yolcu
    pub passenger: bool,
    /// Hardcore sunucu: daha sert zombiler, daha çok XP, ölünce çanta yere düşer
    pub hardcore: bool,
    pub dog: Option<crate::pet::Dog>,
    /// hile: zaman ölçeği
    pub time_scale: f32,
    /// klan güçlendirmeleri (sunucudan): hasar, tecrübe, hız seviyeleri
    pub clan_boost: [u8; 3],
    pub events: Vec<Event>,
    pub time: f32,
    pub day_t: f32,
    pub boss_t: f32,
    /// V12.1 Titan: (dilim, bölge, x, y) ve bu dilimde öldürüldü mü
    pub titan: (i64, usize, f32, f32),
    pub titan_dead: bool,
    /// "seviyene uygun zombi avla" uyarısının son gösterildiği an (dakikada en çok bir kez)
    pub xp_warn_t: f32,
    pub textures: Textures,
    pub rng: Rng,
    pub scratch: Vec<u32>,
    /// zombi itme ızgarası (her karede yeniden kurulur, bellek yeniden kullanılır): hücre başlangıçları ve sıralı zombi dizinleri
    pub sep_start: Vec<u32>,
    pub sep_order: Vec<u32>,
    pub sep_cell: Vec<u32>,
    pub spawn_t: f32,
    pub draw_tmp: Vec<(f32, [f32; STRIDE])>,
    /// çizim sırası: (sıralanabilir derinlik anahtarı << 32 | dizin); kayıtların kendisi taşınmaz
    pub draw_idx: Vec<u64>,
    pub day_index: u32,
    /// V8 devlet üsleri (sunucu durumu + yerel garnizon)
    pub bases: Vec<crate::bases::BaseState>,
    pub soldiers: Vec<crate::bases::Soldier>,
    pub helis: Vec<crate::bases::Heli>,
    /// oyuncunun klanı (0 = yok); klan üssünün askerleri üyelere dost
    pub my_clan: i64,
    /// sunucu saati (ms), üs ödül sürelerini saymak için
    pub base_clock: f64,
    /// büyücü ışınlanma bekleme süresi
    pub wiz_cd: f32,
    /// V12.1: dışarıda kurulan geçici tezgâh (x, y, kalan sn)
    pub temp_benches: Vec<(f32, f32, f32)>,
    /// V12.2: bölgelerin gezgin tüccar kamyonetleri
    pub caravans: Vec<crate::caravan::Caravan>,
    /// V11 eğitim üssü öğreticisi: 0 kapalı, 1 çatışma, 2 bomba düşüyor, 3 patladı (bkz. training.rs)
    pub train: u8,
    pub train_t: f32,
}

impl Game {
    pub fn new(seed: u64, mut progress: Progress, day_index: u32) -> Game {
        progress.character = 0;
        let world = World::generate(seed);
        progress.inv.migrate();
        progress.story.migrate();
        progress.relayout_inv();
        progress.ensure_dailies(day_index);
        // V12.1: oyundan kalkan ara malzemeler karşılığına dönüşür
        progress.retire_items();
        // V18: VIP kalktı; kanatlar yalnız yöneticinin taktığıdır
        progress.wings = progress.wings_grant.clone();
        let refund = progress.sell_off_retired();
        let _ = progress.quest_event(data::QuestKind::Level, 0);
        let (x, y) = world::camp_spawn();
        let player = Player {
            x,
            y,
            hp: progress.max_hp(),
            stamina: progress.max_stamina(),
            speed_cheat: 1.0,
            ..Default::default()
        };
        // V11: dünyada sahipsiz araç yok; araç Tamirci Viktor'a parçalar verilerek üretilir, anahtarla çağrılır
        let vehicles: Vec<Vehicle> = Vec::new();
        let mut npcs: Vec<Npc> = world
            .snipers
            .iter()
            .map(|&(x, y)| Npc {
                kind: 0,
                x,
                y,
                dir: 0,
                cd: 0.0,
                anim_t: 0.0,
                hit_t: 0.0,
                hit_id: 0,
            })
            .collect();
        // NPC'ler: ormandaki kampta tüccar ve görevci; doktor sahra hastanesinde, tamirci benzinlikte,
        // köpek eğitmeni barınakta. Şehirdeki pazarda hepsi tüccarın çevresinde.
        let mut scratch = Vec::new();
        for &(mx, my) in &world.merchants {
            let forest = world.zone(mx, my) == world::Zone::Camp;
            // tüccar sabit değil: kendi kamyonetiyle dolaşır (caravan.rs)
            let mut spots: Vec<(u8, f32, f32)> = vec![(2, mx + m(2.6), my + m(1.2))];
            if forest {
                // büyücü kampta, ateşin doğusunda: kişisel üsse ve keşfedilen karakollara ışınlar
                spots.push((7, mx + m(9.0), my + m(2.4)));
                for (kind, id, ox, oy) in [(3u8, "clinic", 0.0, 0.6), (4, "gas", 0.0, 2.2), (5, "kennel", 0.0, 0.8)] {
                    let (lx, ly) = world::landmark(id).pos();
                    spots.push((kind, lx + m(ox), ly + m(oy)));
                }
            } else {
                // V15 (R10): her NPC güvenli bölgenin kendi köşesinde (doktor batıda, tamirci doğuda, eğitmen kuzeyde);
                // köşe güvenli değilse yakına çekilir
                for (kind, far, near) in [(3u8, (-15.0f32, 1.0f32), (-2.6f32, 1.2f32)), (4, (15.0, 1.0), (4.8, 2.8)), (5, (0.0, -9.0), (-4.8, 2.8))] {
                    let pick = [far, (far.0 * 0.6, far.1 * 0.6), (far.0 * 0.35, far.1 * 0.35), near]
                        .into_iter()
                        .find(|&(ox, oy)| world.zone(mx + m(ox), my + m(oy)).safe())
                        .unwrap_or(near);
                    spots.push((kind, mx + m(pick.0), my + m(pick.1)));
                }
            }
            for (kind, mut x, mut y) in spots {
                if kind != 1 {
                    world.resolve(&mut x, &mut y, m(0.4), true, &mut scratch);
                }
                npcs.push(Npc { kind, x, y, dir: 0, cd: 0.0, anim_t: (kind as f32) * 0.7, hit_t: 0.0, hit_id: 0 });
            }
        }
        // kişisel üsteki büyücü (dönüş kapısının yanında)
        {
            let (hx, hy) = world::home_spawn();
            npcs.push(Npc { kind: 7, x: hx + m(2.2), y: hy - m(9.5), dir: 0, cd: 0.0, anim_t: 1.3, hit_t: 0.0, hit_id: 0 });
        }
        // şehir güvenli bölgesinde Komutan Diana (Sv 10 şehir öğreticisi)
        {
            let (cx, cy) = world::city_spawn();
            let (mut x, mut y) = (cx + m(5.0), cy - m(3.0));
            world.resolve(&mut x, &mut y, m(0.4), true, &mut scratch);
            npcs.push(Npc { kind: crate::story::N_COMMANDER, x, y, dir: 0, cd: 0.0, anim_t: 0.6, hit_t: 0.0, hit_id: 0 });
        }
        // Cephe Karargâhı'ndaki büyücü (geri dönüş); tüccar ve diğerleri world.merchants ile gelir
        {
            let (fx, fy) = world::front_spawn();
            npcs.push(Npc { kind: 7, x: fx + m(6.0), y: fy - m(0.5), dir: 0, cd: 0.0, anim_t: 0.9, hit_t: 0.0, hit_id: 0 });
        }
        // V12.2: gezgin tüccar Marco — kişisel üs dışındaki her bölgede bir kamyonet; tüccar NPC'si park edince görünür
        let mut caravans = Vec::new();
        for i in 0..crate::areas::AREAS.len() {
            if i == crate::areas::HOME {
                continue;
            }
            caravans.push(crate::caravan::Caravan::build(&world, i, npcs.len()));
            npcs.push(Npc { kind: crate::story::N_MERCHANT, x: -10_000.0, y: -10_000.0, dir: 0, cd: 0.0, anim_t: 0.2 * i as f32, hit_t: 0.0, hit_id: 0 });
        }
        // V12: her bölgenin giriş karakolunda bir büyücü (bölgeler arası tek yol)
        for (i, a) in crate::areas::AREAS.iter().enumerate() {
            if matches!(a.id, "forest" | "front" | "home") {
                continue;
            }
            let (hx, hy) = crate::areas::hub(i);
            let (mut x, mut y) = if a.id == "city" { (hx - m(5.0), hy - m(3.0)) } else { (hx + m(3.5), hy - m(1.5)) };
            world.resolve(&mut x, &mut y, m(0.4), true, &mut scratch);
            npcs.push(Npc { kind: 7, x, y, dir: 0, cd: 0.0, anim_t: 0.3 + i as f32 * 0.4, hit_t: 0.0, hit_id: 0 });
            // V12.2: görevci Silas her bölgenin güvenli girişinde (görevler oradan alınır); şehirde pazarda zaten var
            if a.id != "city" {
                let (mut x, mut y) = (hx - m(3.5), hy - m(1.5));
                world.resolve(&mut x, &mut y, m(0.4), true, &mut scratch);
                npcs.push(Npc { kind: crate::story::N_QUEST, x, y, dir: 0, cd: 0.0, anim_t: 0.9 + i as f32 * 0.3, hit_t: 0.0, hit_id: 0 });
            }
        }
        let mut g = Game {
            world,
            progress,
            player,
            zombies: Vec::new(),
            drops: Vec::new(),
            projectiles: Vec::new(),
            areas: Vec::new(),
            vehicles,
            npcs,
            drone: Drone {
                x,
                y,
                ..Default::default()
            },
            remotes: Vec::new(),
            remote_hostile: Vec::new(),
            pvp_hit_t: 0.0,
            capture_blocked: false,
            net: 0,
            next_zid: 1,
            kill_credit: -1,
            passenger: false,
            hardcore: false,
            dog: None,
            time_scale: 1.0,
            cheats_ok: cfg!(test),
            dog_hit: false,
            clan_boost: [0; 3],
            events: Vec::new(),
            time: 0.0,
            day_t: 0.3,
            boss_t: 5.0,
            textures: Textures::default(),
            rng: Rng::new(seed ^ 0x5EED),
            scratch: Vec::new(),
            sep_start: Vec::new(),
            sep_order: Vec::new(),
            sep_cell: Vec::new(),
            spawn_t: 0.0,
            draw_tmp: Vec::new(),
            draw_idx: Vec::new(),
            day_index,
            bases: Vec::new(),
            soldiers: Vec::new(),
            helis: Vec::new(),
            my_clan: 0,
            base_clock: 0.0,
            wiz_cd: 0.0,
            temp_benches: Vec::new(),
            caravans,
            titan: (i64::MIN, 0, 0.0, 0.0),
            titan_dead: false,
            xp_warn_t: -999.0,
            train: 0,
            train_t: 0.0,
        };
        // kervanlar ilk karede yerinde olsun (harita ve dükkân hemen doğru konumu görsün)
        g.update_caravans();
        g.restore_pos();
        if refund > 0 {
            g.msg(format!("Eski eşyaların altına çevrildi: +{refund} altın"));
        }
        g
    }

    /// Kayda yazılacak konum: ölüyken ya da araçta yolcuyken değil (doğuşa döner)
    pub fn sync_pos(&mut self) {
        self.progress.last_pos = if self.player.dead || self.passenger { None } else { Some((self.player.x, self.player.y)) };
    }

    /// Kayıttaki konumdan devam: bölgeye girme seviyesi yetiyorsa ve nokta dünyanın içindeyse (engel varsa yanına itilir)
    fn restore_pos(&mut self) {
        let Some((x, y)) = self.progress.last_pos else { return };
        if !x.is_finite() || !y.is_finite() || x < 0.0 || y < 0.0 || x >= world::MAP_PX || y >= world::MAP_PX {
            return;
        }
        // V15: eski kayıttaki konum kare bölgelerin dışında kaldıysa (boşluk) giriş noktasında başlar
        let Some(area) = crate::areas::area_exact(x, y) else { return };
        if area != crate::areas::HOME && self.progress.level < crate::areas::AREAS[area].level {
            return;
        }
        let (mut px, mut py) = (x, y);
        let city = self.progress.level >= CITY_LEVEL;
        self.world.resolve(&mut px, &mut py, 14.0, city, &mut self.scratch);
        if crate::areas::area_exact(px, py) != Some(area) {
            return;
        }
        self.player.x = px;
        self.player.y = py;
        self.drone.x = px;
        self.drone.y = py;
    }

    pub fn msg(&mut self, t: impl Into<String>) {
        self.events.push(Event::Msg { text: t.into() });
    }

    /// Ölünce doğma: kişisel üs seçildiyse orada, yoksa öldüğü bölgenin giriş karakolunda (V12; bölgeler ayrı harita).
    /// `city`: eski kayıtlar için şehirde doğma isteği (şehir bölgesinde zaten öyle).
    pub fn respawn(&mut self, city: bool) {
        let area = crate::areas::area_at(self.player.x, self.player.y);
        let (x, y) = if self.progress.home_spawn {
            world::home_spawn()
        } else if area != crate::areas::HOME && self.progress.level >= crate::areas::AREAS[area].level {
            crate::areas::hub(area)
        } else if city && self.progress.level >= CITY_LEVEL {
            world::city_spawn()
        } else {
            world::camp_spawn()
        };
        let max_hp = self.progress.max_hp();
        let max_st = self.progress.max_stamina();
        let p = &mut self.player;
        p.x = x;
        p.y = y;
        p.dead = false;
        p.hp = max_hp;
        p.stamina = max_st;
        p.anim = A_IDLE;
        p.anim_t = 0.0;
        p.vehicle = None;
        p.poison_t = 0.0;
        for v in self.vehicles.iter_mut() {
            v.driver = false;
        }
        self.zombies.retain(|z| (z.x - x).hyp(z.y - y) > 900.0);
    }

    /// V18: gece/gündüz döngüsü kalktı; her zaman gündüz (`day_t` yalnız eski kayıtlar ve hile için duruyor)
    pub fn light(&self) -> f32 {
        1.0
    }

    pub fn is_night(&self) -> bool {
        false
    }

    /// Eldeki silah (kırıksa yumruk: onarana dek iş görmez)
    pub fn weapon(&self) -> &'static data::WeaponDef {
        if self.progress.eq.broken("weapon") {
            return &WEAPONS[0];
        }
        &WEAPONS[data::weapon_for(&self.progress.eq.weapon)]
    }

    pub fn step(&mut self, dt: f32, input: Input) {
        let dt = dt.clamp(0.0, 0.05) * self.time_scale;
        self.time += dt;
        self.day_t += dt / 960.0;
        self.progress.play_time += dt as f64;
        if !self.player.dead && !crate::world::ZONES_ALL[self.player.zone as usize].safe() {
            self.progress.ev.danger_secs += dt as f64;
        }
        self.tick_timers(dt);
        self.story_tick();
        if self.passenger {
            // yolcu: konumu ağ katmanı verir
            self.player.anim_t += dt;
        } else if self.player.vehicle.is_some() {
            self.update_vehicle_drive(dt, input);
        } else {
            self.update_player(dt, input);
        }
        self.update_zone();
        self.update_dog(dt);
        self.update_zombies(dt);
        self.update_projectiles(dt);
        self.update_areas(dt);
        self.update_npcs(dt);
        self.update_drops(dt);
        self.update_statics(dt);
        self.update_spawning(dt);
        self.update_bases(dt);
        self.update_training(dt);
        self.wiz_cd = (self.wiz_cd - dt).max(0.0);
        self.update_benches(dt);
        self.update_caravans();
    }

    fn update_zone(&mut self) {
        // keşif: oyuncunun çevresindeki bulutlar kalkar
        if !self.passenger || self.time.fract() < 0.05 {
            let (x, y) = (self.player.x, self.player.y);
            self.progress.reveal(x, y, 2);
        }
        let z = self.world.zone(self.player.x, self.player.y);
        let zi = world::ZONES_ALL.iter().position(|q| *q == z).unwrap_or(0) as u8;
        if zi != self.player.zone {
            self.player.zone = zi;
            let (lo, hi) = z.level_range();
            // karakollarda genel "Karakol" yerine yerin adı (Cephe Karargâhı, Kül Karakolu…)
            let name = if z == world::Zone::Outpost {
                let (px, py) = (self.player.x, self.player.y);
                world::LANDMARKS
                    .iter()
                    .filter(|l| l.safe)
                    .min_by(|a, b| {
                        let (ax, ay) = a.pos();
                        let (bx, by) = b.pos();
                        (ax - px).hyp(ay - py).total_cmp(&(bx - px).hyp(by - py))
                    })
                    .map(|l| format!("{} (Güvenli)", l.name))
                    .unwrap_or_else(|| z.name().into())
            } else {
                z.name().into()
            };
            self.events.push(Event::ZoneEnter {
                name,
                lo,
                hi,
                safe: z.safe(),
            });
            if z.city() && !self.progress.city_entered {
                self.progress.city_entered = true;
                let done = self.progress.quest_event(data::QuestKind::EnterCity, 1);
                self.quests_done(done);
            }
        }
    }

    /// Etkileşim pozu: yalnız yaya, ayakta ve boşta iken (hareket/saldırı/araç pozu keser).
    pub fn play_pose(&mut self, a: u8, dur: f32) {
        let p = &self.player;
        if p.dead || p.vehicle.is_some() || self.passenger || p.knocked || p.roll_t > 0.0 || p.hurt_t > 0.0 || p.atk_active || p.moving {
            return;
        }
        self.player.pose = a;
        self.player.pose_t = dur;
    }

    pub fn quests_done(&mut self, done: Vec<String>) {
        for t in done {
            self.events.push(Event::Quest { title: t });
        }
    }

    /// Süreli etkiler (iksir, skill, bekleme) her durumda işler: yürürken, araçta ya da yolcuyken.
    fn tick_timers(&mut self, dt: f32) {
        let p = &mut self.player;
        p.flash_t = (p.flash_t - dt).max(0.0);
        p.last_breath_cd = (p.last_breath_cd - dt).max(0.0);
        p.combat_t += dt;
        p.ability_cd = (p.ability_cd - dt).max(0.0);
        p.cloak_cd = (p.cloak_cd - dt).max(0.0);
        p.noammo_t = (p.noammo_t - dt).max(0.0);
        p.locked_t = (p.locked_t - dt).max(0.0);
        for t in [
            &mut p.rage_t,
            &mut p.oath_t,
            &mut p.cloak_t,
            &mut p.energy_t,
            &mut p.shield_t,
            &mut p.cast_t,
            &mut p.iframe_t,
            &mut p.speed_t,
            &mut p.power_t,
        ] {
            *t = (*t - dt).max(0.0);
        }
        for c in p.pot_cd.iter_mut() {
            *c = (*c - dt).max(0.0);
        }
        if p.no_cd {
            p.ability_cd = 0.0;
            p.pot_cd = [0.0; 4];
        }
        if p.heal_t > 0.0 {
            p.heal_t = (p.heal_t - dt).max(0.0);
            if !p.dead {
                p.hp = (p.hp + p.heal_rate * dt).min(self.progress.max_hp());
            }
        }
    }

    // ------------------------------------------------------------------ oyuncu
    fn update_player(&mut self, dt: f32, input: Input) {
        let max_hp = self.progress.max_hp();
        let max_st = self.progress.max_stamina();
        let regen = self.progress.regen_mult();
        {
            let p = &mut self.player;
            p.anim_t += dt;
            if p.shield_t <= 0.0 {
                p.shield = 0.0;
            }
            if p.dead {
                return;
            }
            if p.food_t > 0.0 {
                p.food_t -= dt;
                p.hp = (p.hp + 3.0 * dt).min(max_hp);
            }
            if p.poison_t > 0.0 {
                p.poison_t -= dt;
                if !p.god {
                    p.hp -= 3.0 * dt;
                }
            }
            if p.combat_t > 5.0 && p.hp < max_hp {
                p.hp = (p.hp + 2.0 * regen * dt).min(max_hp);
            }
            p.hp = p.hp.min(max_hp);
            p.stamina_delay -= dt;
            if p.stamina_delay <= 0.0 || p.energy_t > 0.0 {
                p.stamina = (p.stamina + 18.0 * dt).min(max_st);
            }
        }
        if self.player.hp <= 0.0 && !self.player.dead {
            self.kill_player();
            return;
        }
        self.update_aura(dt);
        if self.player.roll_t > 0.0 {
            self.update_roll(dt);
            self.pickup_drops();
            return;
        }
        if self.player.knocked {
            // yere düşme → kalkma
            self.player.hurt_t -= dt;
            let a = if self.player.hurt_t > 0.5 { A_KNOCK } else { A_GETUP };
            if self.player.anim != a {
                self.player.anim = a;
                self.player.anim_t = 0.0;
            }
            if self.player.hurt_t <= 0.0 {
                self.player.knocked = false;
                self.player.hurt_t = 0.0;
            }
            return;
        }
        self.player.flinch_cd -= dt;
        self.player.dot_t -= dt;
        if self.player.hurt_t > 0.0 {
            self.player.hurt_t -= dt;
            if self.player.anim != A_HURT {
                self.player.anim = A_HURT;
                self.player.anim_t = 0.0;
            }
            return;
        }
        if self.player.cast_t > 0.0 {
            return;
        }
        if self.player.pose_t > 0.0 {
            self.player.pose_t -= dt;
            if input.attacking || input.move_x.hyp(input.move_y) > 0.08 {
                self.player.pose_t = 0.0;
            } else {
                if self.player.anim != self.player.pose {
                    self.player.anim = self.player.pose;
                    self.player.anim_t = 0.0;
                }
                self.player.moving = false;
                self.pickup_drops();
                return;
            }
        }
        let w = self.weapon();
        let ranged = matches!(
            w.class,
            WeaponClass::Pistol | WeaponClass::Rifle | WeaponClass::Sniper | WeaponClass::Heavy
        );
        let (aim_idle, aim_walk) = match w.class {
            WeaponClass::Pistol => (A_PISTOL_IDLE, A_PISTOL_WALK),
            WeaponClass::Fist | WeaponClass::Melee => (A_IDLE, A_WALK),
            _ => (A_RIFLE_IDLE, A_RIFLE_WALK),
        };
        // ---- saldırı
        let atk_len = input.atk_x.hyp(input.atk_y);
        let wants = input.attacking && atk_len > 0.2;
        if wants {
            self.player.dir = dir_from_vec(input.atk_x, input.atk_y);
            self.player.combat_t = self.player.combat_t.min(2.0);
        }
        self.player.atk_cd = (self.player.atk_cd - dt).max(0.0);
        if self.player.reload_t > 0.0 {
            self.player.reload_t -= dt;
            if self.player.reload_t <= 0.0 {
                self.finish_reload();
            }
        }
        let aim = (
            input.atk_x / atk_len.max(1e-3),
            input.atk_y / atk_len.max(1e-3),
        );
        match w.class {
            WeaponClass::Fist => self.update_fist(dt, wants),
            WeaponClass::Melee => self.update_melee(dt, wants, w),
            _ => {
                if wants && self.player.atk_cd <= 0.0 && self.player.reload_t <= 0.0 {
                    self.fire(w, aim);
                }
                self.player.atk_active = wants;
            }
        }
        // ---- hareket
        let mlen = input.move_x.hyp(input.move_y).min(1.0);
        let attacking = self.player.atk_active;
        let mut moving = false;
        if mlen > 0.08 {
            let (nx, ny) = (input.move_x / mlen, input.move_y / mlen);
            // yürümek ve koşmak dayanıklılık harcamaz; yalnız saldırı ve takla harcar
            let run = mlen > 0.6 && !attacking;
            let mut speed = if run {
                RUN_SPEED * self.progress.run_mult()
            } else {
                WALK_SPEED * (0.45 + 0.55 * (mlen / 0.6).min(1.0))
            };
            speed *= self.progress.move_mult() * self.player.speed_cheat * (1.0 + 0.02 * self.clan_boost[2] as f32);
            if self.player.speed_t > 0.0 {
                speed *= 1.25;
            }
            if self.player.cloak_t > 0.0 {
                speed *= 1.2;
            }
            if self.player.energy_t > 0.0 {
                speed *= 1.2;
            }
            if attacking {
                speed *= if ranged { 0.65 } else { 0.45 };
            }
            let gate_open = self.progress.level >= CITY_LEVEL;
            let (mut x, mut y) = (
                self.player.x + nx * speed * dt,
                self.player.y + ny * speed * dt,
            );
            let (ox, oy) = (self.player.x, self.player.y);
            // V12: başka bölgeye yürünmez; sınıra dayanınca büyücü hatırlatılır
            let beyond = crate::areas::area_at(x, y);
            let here = crate::areas::area_at(ox, oy);
            self.world
                .resolve_move(ox, oy, &mut x, &mut y, 14.0, gate_open, &mut self.scratch);
            if beyond != here && self.player.locked_t <= 0.0 {
                self.player.locked_t = 4.0;
                let name = crate::areas::AREAS[beyond].name;
                self.msg(format!("Bölge sınırı: {name} başka bir harita. Bölgeler arası Büyücü Aldric ışınlar."));
            }
            self.player.x = x;
            self.player.y = y;
            moving = true;
            self.player.step_t -= dt * if run { 1.6 } else { 1.0 };
            if self.player.step_t <= 0.0 {
                self.player.step_t = 0.42;
                self.events.push(Event::Step);
            }
            if !attacking {
                self.player.dir = dir_from_vec(nx, ny);
            }
            let a = if attacking && !ranged {
                self.player.anim
            } else if ranged && (attacking || self.player.combat_t < 1.5) {
                aim_walk
            } else if run {
                A_RUN
            } else {
                A_WALK
            };
            if self.player.anim != a {
                self.player.anim = a;
                self.player.anim_t = 0.0;
            }
        } else if !attacking || ranged {
            let a = if ranged && (attacking || self.player.combat_t < 1.5) {
                aim_idle
            } else if !ranged && attacking {
                self.player.anim
            } else {
                A_IDLE
            };
            if self.player.anim != a {
                self.player.anim = a;
                self.player.anim_t = 0.0;
            }
        }
        self.player.moving = moving;
        self.pickup_drops();
    }

    fn update_fist(&mut self, dt: f32, wants: bool) {
        const FPS: f32 = 14.0;
        const HALF: f32 = 4.0 / FPS;
        let p = &mut self.player;
        if wants && !p.atk_active {
            p.atk_active = true;
            p.hit_done = false;
            if p.anim != A_PUNCH {
                p.anim = A_PUNCH;
                p.anim_t = 0.0;
            }
        }
        if !p.atk_active {
            return;
        }
        let tired = p.stamina < 4.0;
        let speed = if tired { 0.6 } else { 1.0 };
        p.anim_t += dt * (speed - 1.0);
        let half_t = p.anim_t % HALF;
        let mut swing = false;
        if !p.hit_done && half_t >= 1.5 / FPS {
            p.hit_done = true;
            swing = true;
        }
        if half_t + dt * speed >= HALF - 1e-4 {
            p.hit_done = false;
            if !wants {
                p.atk_active = false;
            }
        }
        if swing {
            self.spend_stamina(ST_PUNCH);
            self.events.push(Event::Swing);
            let dmg = self.progress.fist_damage();
            self.melee_hit(dmg, m(1.4), 0.75, 0.0);
        }
    }

    fn update_melee(&mut self, _dt: f32, wants: bool, w: &'static data::WeaponDef) {
        let b = self.progress.bonus();
        let (dmg, reach) = (w.damage * (1.0 + b.blade_pct), m(w.range_m) * (1.0 + b.reach));
        // yorgunken savurma yavaşlar
        let tired = if self.player.stamina < ST_SWING { 1.6 } else { 1.0 };
        let p = &mut self.player;
        // V19: ağaç kesme / kaya kırma (A_CHOP / A_MINE) animasyonu kendi döngüsüyle oynar (r3d: 0,9 sn, balta gövdeye
        // 0,40'ta iner); vuruş da o anda iner. Eskiden silahın saldırı hızıyla vuruluyordu: tek savuruşta 2-3 hasar.
        let gathering = matches!(p.anim, A_CHOP | A_MINE);
        let period = if gathering {
            GATHER_CYCLE * tired
        } else {
            tired / (w.rate * (1.0 + b.atk_speed + if p.oath_t > 0.0 { 0.3 } else { 0.0 }))
        };
        p.atk_period = period;
        if wants && !p.atk_active {
            p.atk_active = true;
            p.hit_done = false;
            if !gathering {
                p.anim = A_MELEE;
                p.anim_t = 0.0;
            }
        }
        if !p.atk_active {
            return;
        }
        if !gathering && p.anim != A_MELEE {
            p.anim = A_MELEE;
            p.anim_t = 0.0;
        }
        let ct = p.anim_t % period;
        if !p.hit_done && ct >= period * if gathering { GATHER_IMPACT } else { 0.4 } {
            p.hit_done = true;
            self.spend_stamina(ST_SWING * if w.range_m > 1.6 { 1.3 } else { 0.75 });
            self.events.push(Event::Swing);
            self.melee_hit(dmg, reach, if w.pierce > 0 { 1.2 } else { 0.9 }, w.stun);
        }
        let p = &mut self.player;
        if ct + 0.02 >= period * 0.98 {
            p.hit_done = false;
            if !wants {
                p.atk_active = false;
                p.anim = A_IDLE;
            }
        }
    }

    /// Çizim ve ağ için animasyon ve saati: yakın dövüşte döngü sayısı (3B saldırı hareketi vuruş anına oturur);
    /// ateşli silahla ayaktayken ateş ve şarjör değiştirme ayrı animasyon, saat faz (0..1)
    pub fn anim_state(&self) -> (u8, f32) {
        let p = &self.player;
        if p.anim == A_MELEE && p.atk_period > 0.01 {
            return (A_MELEE, p.anim_t / p.atk_period);
        }
        let aiming = matches!(p.anim, A_PISTOL_IDLE | A_RIFLE_IDLE | A_PISTOL_WALK | A_RIFLE_WALK);
        if aiming && !p.moving && !p.dead && p.vehicle.is_none() {
            let w = self.weapon();
            if p.reload_t > 0.0 && w.reload > 0.0 {
                return (A_RELOAD, (1.0 - p.reload_t / w.reload).clamp(0.0, 0.999));
            }
            if p.atk_cd > 0.0 && p.atk_period > 0.01 {
                return (A_FIRE, (1.0 - p.atk_cd / p.atk_period).clamp(0.0, 0.999));
            }
        }
        (p.anim, p.anim_t)
    }

    pub fn damage_mult(&self) -> f32 {
        // klan hasar güçlendirmesi: seviye başı %2
        let mut k = self.progress.damage_mult() * (1.0 + 0.02 * self.clan_boost[0] as f32);
        if self.player.power_t > 0.0 {
            k *= 1.2;
        }
        if self.player.rage_t > 0.0 {
            k *= 1.4;
        }
        if self.player.cloak_t > 0.0 {
            k *= 1.25;
        }
        k
    }

    fn kill_player(&mut self) {
        let p = &mut self.player;
        p.hp = 0.0;
        p.dead = true;
        p.anim = A_DEATH;
        p.anim_t = 0.0;
        p.atk_active = false;
        self.progress.deaths += 1;
        self.events.push(Event::Died);
        // ölümde elindeki silah sarsılır (%1); çantalar kırılmaz
        self.wear_eq("weapon", 10);
        if self.hardcore {
            // çantadaki her şey ölüm yerine düşer, altının yarısı gider
            let (x, y) = (self.player.x, self.player.y);
            let slots: Vec<_> = self.progress.inv.slots.iter_mut().filter_map(|s| s.take()).collect();
            for (i, s) in slots.into_iter().enumerate() {
                let a = i as f32 * 2.4;
                self.spawn_drop(s.item as usize, s.count as u32, x + a.cos() * 40.0, y + a.sin() * 30.0);
                if let Some(d) = self.drops.last_mut() {
                    d.dur = s.dur;
                }
            }
            self.progress.gold /= 2;
            self.msg("Hardcore: çantan ölüm yerine düştü, altının yarısı gitti");
        }
    }

    /// Oyuncuyu (ve sürdüğü aracı bırakarak) verilen noktaya taşır.
    pub fn teleport(&mut self, x: f32, y: f32) -> (f32, f32) {
        let (mut x, mut y) = (x.clamp(100.0, world::MAP_PX - 100.0), y.clamp(100.0, world::MAP_PX - 100.0));
        self.world.resolve(&mut x, &mut y, 14.0, true, &mut self.scratch);
        if let Some(vi) = self.player.vehicle.take() {
            self.vehicles[vi].driver = false;
        }
        self.player.x = x;
        self.player.y = y;
        (x, y)
    }

    /// Patlama hasarı: skill ağacındaki patlama koruması uygulanır.
    pub fn damage_player_blast(&mut self, amount: f32) {
        let k = 1.0 - self.progress.bonus().blast_res;
        self.damage_player(amount * k);
    }

    /// Oyuncu şu an hasar almıyor mu (takla, takla sonrası, ölümsüzlük)
    pub fn player_invulnerable(&self) -> bool {
        self.player.dead || self.player.god || self.player.cloak_t > 0.0 || self.player.roll_t > 0.0 || self.player.iframe_t > 0.0
    }

    pub fn damage_player(&mut self, amount: f32) {
        self.damage_player_ex(amount, false);
    }

    /// Sürekli hasar (zehir birikintisi, bulut): sendeletmez, hasar yazısı yarım saniyede bir toplu çıkar.
    pub fn damage_player_dot(&mut self, amount: f32) {
        self.damage_player_ex(amount, true);
    }

    fn damage_player_ex(&mut self, amount: f32, dot: bool) {
        if self.player_invulnerable() {
            return;
        }
        let b = self.progress.bonus();
        if b.dodge > 0.0 && self.rng.chance(b.dodge) {
            let (x, y) = (self.player.x, self.player.y);
            self.events.push(Event::Dodge { x, y: y - 110.0 });
            return;
        }
        if b.reflect > 0.0 {
            // en yakın zombiye yansıt
            let (px, py) = (self.player.x, self.player.y);
            let near = self
                .zombies
                .iter()
                .enumerate()
                .filter(|(_, z)| z.state != ZState::Dead && (z.x - px).hyp(z.y - py) < 140.0)
                .min_by(|a, b2| {
                    let da = (a.1.x - px).hyp(a.1.y - py);
                    let db = (b2.1.x - px).hyp(b2.1.y - py);
                    da.partial_cmp(&db).unwrap_or(std::cmp::Ordering::Equal)
                })
                .map(|(i, _)| i);
            if let Some(i) = near {
                self.hurt_zombie(i, amount * b.reflect * 4.0, false, 0.0, 0.0, false, true);
            }
        }
        let z = self.world.zone(self.player.x, self.player.y);
        if z.safe() {
            return;
        }
        // araçtayken hasarın çoğunu araç alır (araba hepsini, motosiklet %70'ini); araç canı 0'da patlar
        let mut amount = amount;
        if let Some(vi) = self.player.vehicle {
            if let Some(v) = self.vehicles.get_mut(vi) {
                let share = crate::data::veh_slot(v.kind).map_or(1.0, |s| crate::data::VEH_ABSORB[s]);
                v.hp -= amount * share;
                amount *= 1.0 - share;
                if amount <= 0.0 {
                    return;
                }
            }
        }
        let armor = self.progress.armor();
        let amount = if self.hardcore { amount * 1.5 } else { amount };
        let mut dmg = amount * 100.0 / (100.0 + armor) * (1.0 - self.progress.damage_resist());
        let p = &mut self.player;
        p.combat_t = 0.0;
        if p.shield > 0.0 {
            let absorbed = p.shield.min(dmg);
            p.shield -= absorbed;
            dmg -= absorbed;
        }
        if dmg <= 0.0 {
            return;
        }
        p.hp -= dmg;
        let (x, y) = (p.x, p.y);
        if !dot {
            // her darbede zırh, yüzük ve kolye binde 1 aşınır
            for k in ["helmet", "jacket", "pants", "boots", "ring", "necklace"] {
                self.wear_eq(k, 1);
            }
        }
        let p = &mut self.player;
        if dot {
            p.dot_acc += dmg;
            if p.dot_t <= 0.0 {
                p.dot_t = 0.5;
                p.flash_t = 0.12;
                let v = p.dot_acc.round().max(1.0) as i32;
                p.dot_acc = 0.0;
                self.events.push(Event::PlayerDamage { x, y: y - 110.0, v });
            }
        } else {
            p.flash_t = 0.12;
            self.events.push(Event::PlayerDamage {
                x,
                y: y - 110.0,
                v: dmg.round().max(1.0) as i32,
            });
        }
        let max_hp = self.progress.max_hp();
        let has_cloak = self.progress.eq.cloak == "cloak";
        let p = &mut self.player;
        if has_cloak && p.cloak_cd <= 0.0 && p.hp > 0.0 && p.hp < max_hp * 0.2 {
            p.cloak_t = 3.0;
            p.cloak_cd = 60.0;
            self.events.push(Event::Cloak);
        }
        let p = &mut self.player;
        if p.hp <= 0.0 {
            if self.progress.has_last_breath() && p.last_breath_cd <= 0.0 {
                p.hp = 1.0;
                p.last_breath_cd = 90.0;
                self.events.push(Event::LastBreath);
                return;
            }
            self.kill_player();
        } else if !dot && !p.atk_active && p.flinch_cd <= 0.0 && dmg >= max_hp * 0.04 {
            // kısa sendeleme: en fazla 1,2 sn'de bir ve yalnız kayda değer vuruşta
            p.hurt_t = 0.18;
            p.flinch_cd = 1.2;
        }
    }

    // ------------------------------------------------------------------ takla ve aura (V4 Savaşçı)
    pub fn start_roll(&mut self) -> Result<String, String> {
        if self.player.roll_t > 0.0 || self.player.hurt_t > 0.3 {
            return Err(String::new());
        }
        let cost = ST_ROLL * self.progress.stamina_cost_mult();
        if self.player.stamina < cost && !self.player.no_cd {
            return Err("Takla İçin Çok Yorgunsun".into());
        }
        self.spend_stamina(ST_ROLL);
        let (dx, dy) = dir_vec(self.player.dir);
        let p = &mut self.player;
        p.roll_t = ROLL_TIME;
        p.roll_dx = dx;
        p.roll_dy = dy;
        p.atk_active = false;
        p.hurt_t = 0.0;
        p.ability_cd = if p.no_cd { 0.0 } else { self.progress.roll_cd() };
        p.anim = A_ROLL;
        p.anim_t = 0.0;
        let (x, y) = (p.x, p.y);
        self.events.push(Event::Roll { x, y });
        self.story_event(crate::story::Ev::Roll);
        Ok(String::new())
    }

    /// Dayanıklılık harcar (beceri indirimi uygulanır); kısa süre dolum durur.
    fn spend_stamina(&mut self, base: f32) {
        let cost = base * self.progress.stamina_cost_mult();
        self.player.stamina = (self.player.stamina - cost).max(0.0);
        self.player.stamina_delay = 0.9;
    }

    fn update_roll(&mut self, dt: f32) {
        let gate_open = self.progress.level >= CITY_LEVEL;
        let speed = m(3.0) / ROLL_TIME; // 3 metre
        let (mut x, mut y) = (self.player.x + self.player.roll_dx * speed * dt, self.player.y + self.player.roll_dy * speed * dt);
        self.world.resolve(&mut x, &mut y, 14.0, gate_open, &mut self.scratch);
        crate::areas::clamp(self.player.x, self.player.y, &mut x, &mut y);
        let p = &mut self.player;
        p.x = x;
        p.y = y;
        p.anim_t += dt;
        p.roll_t -= dt;
        if p.roll_t <= 0.0 {
            p.roll_t = 0.0;
            p.iframe_t = self.progress.bonus().iframe;
            p.anim = A_IDLE;
            p.anim_t = 0.0;
        }
    }

    /// Savaşçı Sv10: yakındaki düşmanlara saniyede 20 hasar
    fn update_aura(&mut self, dt: f32) {
        if !self.progress.bonus().aura || self.player.dead {
            return;
        }
        self.player.aura_t -= dt;
        if self.player.aura_t > 0.0 {
            return;
        }
        self.player.aura_t = 1.0;
        let (px, py) = (self.player.x, self.player.y);
        let hits: Vec<usize> = self
            .zombies
            .iter()
            .enumerate()
            .filter(|(_, z)| z.state != ZState::Dead && (z.x - px).hyp(z.y - py) < AURA_R)
            .map(|(i, _)| i)
            .collect();
        for i in hits.into_iter().rev() {
            if i < self.zombies.len() {
                self.hurt_zombie(i, 20.0, false, 0.0, 0.0, false, true);
            }
        }
    }

    // ------------------------------------------------------------------ yetenekler
    pub fn use_ability(&mut self) -> Result<String, String> {
        if self.player.dead || self.player.vehicle.is_some() {
            return Err("Şu an kullanılamaz".into());
        }
        if self.player.ability_cd > 0.0 {
            return Err(format!("Bekleme: {:.0} sn", self.player.ability_cd));
        }
        let lvl = self.progress.char_level[self.progress.character.min(3)] as f32;
        match self.progress.character {
            // İz Sürücü: Gölge Atılımı — takla gibi atılır, 3 sn vuruşlar sert
            1 => {
                self.start_roll()?;
                self.player.rage_t = 3.0;
                self.player.ability_cd = if self.player.no_cd { 0.0 } else { 7.0 * (1.0 - self.progress.bonus().roll_cd).max(0.3) };
                self.events.push(Event::Rage);
                Ok("Gölge Atılımı!".into())
            }
            // Muhafız: Kalkan Duvarı — canın %30'u kalkan, çevredekiler geri savrulur
            2 => {
                let v = self.progress.max_hp() * 0.30;
                self.player.shield = v;
                self.player.shield_t = 4.0;
                self.player.ability_cd = if self.player.no_cd { 0.0 } else { 20.0 };
                self.player.cast_t = 0.4;
                self.player.anim = A_CAST;
                self.player.anim_t = 0.0;
                let (x, y) = (self.player.x, self.player.y);
                let near: Vec<usize> = self.zombies.iter().enumerate()
                    .filter(|(_, z)| z.hp > 0.0 && (z.x - x).hyp(z.y - y) < m(3.5)).map(|(i, _)| i).collect();
                for i in near.into_iter().rev() {
                    self.hurt_zombie(i, 15.0 + 5.0 * lvl, false, x, y, true, true);
                }
                self.areas.push(Area { kind: AreaKind::ShieldFx, x, y, r: m(3.5), life: 0.8, max_life: 0.8, dps: 0.0, hit_done: true });
                self.events.push(Event::Shield { v: v as i32 });
                Ok(format!("Kalkan Duvarı: {v:.0}"))
            }
            // Gölge: Kan Yemini — 6 sn can çalma ve saldırı hızı
            3 => {
                self.player.oath_t = 6.0;
                self.player.ability_cd = if self.player.no_cd { 0.0 } else { 25.0 };
                self.events.push(Event::Rage);
                Ok("Kan Yemini!".into())
            }
            _ => self.start_roll(),
        }
    }

    pub fn spawn_drop(&mut self, item: usize, count: u32, x: f32, y: f32) {
        if count == 0 {
            return;
        }
        let a = self.rng.range(0.0, TAU);
        let r = self.rng.range(10.0, 40.0);
        self.drops.push(Drop {
            item: item as u16,
            count,
            dur: crate::inventory::DUR_FULL,
            x: x + a.cos() * r,
            y: y + a.sin() * r * 0.6,
            age: 0.0,
            pop: 0.0,
        });
    }

    pub fn spawn_drop_id(&mut self, id: &str, count: u32, x: f32, y: f32) {
        if let Some(i) = data::item_index(id) {
            self.spawn_drop(i, count, x, y);
        }
    }

    fn pickup_drops(&mut self) {
        let (px, py) = (self.player.x, self.player.y);
        let mut full = false;
        let mut i = 0;
        while i < self.drops.len() {
            let d = &self.drops[i];
            if d.age < 0.35 || (d.x - px).hyp(d.y - py) > 40.0 {
                i += 1;
                continue;
            }
            let item = d.item as usize;
            let def = &data::ITEMS[item];
            let (dx, dy, cnt, dur) = (d.x, d.y, d.count, d.dur);
            if def.id == "coin" {
                self.progress.add_gold(cnt);
                self.events.push(Event::Gold {
                    x: dx,
                    y: dy - 60.0,
                    v: cnt,
                });
                let done = self.progress.quest_event(data::QuestKind::Gold, cnt);
                self.quests_done(done);
                self.drops.swap_remove(i);
                continue;
            }
            if def.id == "gem" {
                self.progress.gems += cnt;
                self.drops.swap_remove(i);
                continue;
            }
            let left = if crate::inventory::durable(item) {
                self.progress.inv.put(crate::inventory::Slot { item: item as u16, count: cnt.min(u16::MAX as u32) as u16, dur })
            } else {
                self.progress.inv.add(item, cnt)
            };
            if left < cnt {
                self.play_pose(A_PICKUP, 0.3);
                self.story_event(crate::story::Ev::Loot);
                self.events.push(Event::Pickup {
                    x: dx,
                    y: dy - 60.0,
                    name: def.name.into(),
                    count: cnt - left,
                    rarity: def.rarity as u8,
                });
            }
            if left == 0 {
                self.drops.swap_remove(i);
            } else {
                self.drops[i].count = left;
                full = true;
                i += 1;
            }
        }
        if full && self.player.noammo_t <= 0.0 {
            self.player.noammo_t = 3.0;
            self.events.push(Event::InvFull);
        }
    }

    fn update_drops(&mut self, dt: f32) {
        for d in self.drops.iter_mut() {
            d.age += dt;
            d.pop = (d.pop + dt * 3.0).min(1.0);
        }
        self.drops.retain(|d| d.age < 150.0);
    }

    // ------------------------------------------------------------------ statikler
    fn update_statics(&mut self, dt: f32) {
        let (px, py) = (self.player.x, self.player.y);
        for s in self.world.statics.iter_mut() {
            if s.shake > 0.0 {
                s.shake = (s.shake - dt).max(0.0);
            }
            if s.broken {
                s.respawn -= dt;
                if s.respawn <= 0.0 && (s.x - px).hyp(s.y - py) > 700.0 {
                    s.broken = false;
                    s.hp = s.max_hp;
                }
            }
        }
    }

    fn update_npcs(&mut self, dt: f32) {
        for ni in 0..self.npcs.len() {
            let npc = &mut self.npcs[ni];
            npc.anim_t += dt;
            npc.cd -= dt;
            if npc.kind != 0 {
                let (dx, dy) = (self.player.x - npc.x, self.player.y - npc.y);
                if dx.hyp(dy) < 500.0 {
                    npc.dir = dir_from_vec(dx, dy);
                }
                continue;
            }
            // havadaki mızrak hedefe vardı
            if npc.hit_t > 0.0 {
                npc.hit_t -= dt;
                if npc.hit_t <= 0.0 {
                    let id = npc.hit_id;
                    if let Some(i) = self.zombies.iter().position(|z| z.id == id && z.state != ZState::Dead) {
                        let dmg = 60.0 + self.zombies[i].max_hp * 0.25;
                        let (kx, ky) = dir_vec(self.npcs[ni].dir);
                        self.hurt_zombie(i, dmg, false, kx, ky, false, false);
                    }
                }
                continue;
            }
            if npc.cd > 0.0 {
                continue;
            }
            let (nx, ny) = (npc.x, npc.y);
            let range = m(14.0);
            let mut best: Option<(usize, f32)> = None;
            for (i, z) in self.zombies.iter().enumerate() {
                if z.state == ZState::Dead || z.boss {
                    continue;
                }
                let d = (z.x - nx).hyp(z.y - ny);
                if d < range && best.is_none_or(|(_, bd)| d < bd) {
                    best = Some((i, d));
                }
            }
            if let Some((i, d)) = best {
                let (zx, zy, id) = (self.zombies[i].x, self.zombies[i].y, self.zombies[i].id);
                let t = (d / m(16.0)).clamp(0.2, 0.75);
                let npc = &mut self.npcs[ni];
                npc.dir = dir_from_vec(zx - nx, zy - ny);
                npc.cd = 1.6;
                npc.hit_t = t;
                npc.hit_id = id;
                self.events.push(Event::Spear { x1: nx, y1: ny - 300.0, x2: zx, y2: zy - 50.0, dur: t });
            }
        }
    }
}

#[cfg(test)]
mod tests {
    #[allow(unused_imports)]
    use crate::Hyp;
    use super::*;

    #[test]
    fn dirs_round_trip() {
        for d in 0..8u8 {
            let (x, y) = dir_vec(d);
            assert_eq!(dir_from_vec(x, y), d);
        }
    }
}

#[cfg(test)]
mod resume_tests {
    #[allow(unused_imports)]
    use crate::Hyp;
    use super::*;

    /// Uygulama kapanıp açılınca (ya da arka plandan sonra yeniden girince) kayıttaki konumdan devam edilir
    #[test]
    fn position_survives_save_load() {
        let mut g = Game::new(5, Progress::default(), 1);
        let (sx, sy) = world::camp_spawn();
        g.player.x = sx + 400.0;
        g.player.y = sy + 300.0;
        g.sync_pos();
        let saved = crate::progress_to_json(&g.progress);
        let p: Progress = serde_json::from_str(&saved).unwrap();
        let h = Game::new(5, p, 1);
        assert!((h.player.x - (sx + 400.0)).abs() < 40.0 && (h.player.y - (sy + 300.0)).abs() < 40.0, "kaldığı yerden başlamalı");
        // girme seviyesi yetmeyen bölgeye kaydedilmiş konum: doğuştan başlar
        let mut p2: Progress = serde_json::from_str(&saved).unwrap();
        let (hx, hy) = crate::areas::hub(5);
        p2.last_pos = Some((hx, hy));
        let k = Game::new(5, p2, 1);
        assert!((k.player.x - sx).abs() < 1.0 && (k.player.y - sy).abs() < 1.0);
    }
}
