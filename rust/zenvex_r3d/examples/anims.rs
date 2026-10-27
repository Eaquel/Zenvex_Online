//! Animasyon önizleme sayfası:
//! cargo run -p zenvex_r3d --example anims --release -- <m3d> <çıktı.png> <karakter> <silah|-> <anim,anim,...> [yaw] [kare]
//! Her satır bir animasyon, sütunlar zaman içinde eşit aralıklı kareler.
use std::path::Path;
use zenvex_r3d::anim::{self, a, In, Style, Wk};
use zenvex_r3d::model::{Model, Texture};
use zenvex_r3d::raster::Light;
use zenvex_r3d::rig::{self, Skeleton};
use zenvex_r3d::{render, transform_model, Item, Settings};

fn load(dir: &Path, name: &str) -> Model {
    let mut m = Model::parse(&std::fs::read(dir.join(format!("{name}.zxm"))).unwrap()).unwrap();
    let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
    m.tex = Some(Texture::from_rgba(im.width() as usize, im.height() as usize, im.as_raw()));
    m
}

fn anim_id(s: &str) -> (u8, f32) {
    // (kimlik, önizlenecek süre)
    match s {
        "idle" => (a::IDLE, 3.0),
        "walk" => (a::WALK, 0.9),
        "run" => (a::RUN, 0.7),
        "punch" => (a::PUNCH, 0.58),
        "melee" => (a::MELEE, 0.7),
        "melee2" => (a::MELEE, 1.4),
        "fire" => (a::FIRE, 0.3),
        "reload" => (a::RELOAD, 1.6),
        "roll" => (a::ROLL, 0.32),
        "death" => (a::DEATH, 0.9),
        "knock" => (a::KNOCK, 0.6),
        "getup" => (a::GETUP, 0.5),
        "hurt" => (a::HURT, 0.18),
        "ride" => (a::RIDE, 1.0),
        "chop" => (a::CHOP, 0.9),
        "mine" => (a::MINE, 0.9),
        "pickup" => (a::PICKUP, 1.0),
        "drink" => (a::DRINK, 1.0),
        "cheer" => (a::CHEER, 1.0),
        "cast" => (a::CAST, 1.0),
        _ => (a::IDLE, 1.0),
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let dir = Path::new(&args[1]);
    let ch = &args[3];
    let m = load(dir, ch);
    let sk = Skeleton::of(&m);
    let anims: Vec<&str> = args[5].split(',').collect();
    let yaw: f32 = args.get(6).and_then(|s| s.parse().ok()).unwrap_or(35.0);
    let nf: usize = args.get(7).and_then(|s| s.parse().ok()).unwrap_or(8);
    let mut cache: std::collections::HashMap<String, Model> = Default::default();
    let base_w = if args[4] == "-" { Wk::None } else { Wk::from_model(&args[4]) };
    let light = Light::default();
    let mut rows: Vec<Vec<zenvex_r3d::Image>> = Vec::new();
    let t0 = std::time::Instant::now();
    let mut n = 0;
    for an in &anims {
        let (id, dur) = anim_id(an);
        let mut row = Vec::new();
        for f in 0..nf {
            let t = dur * f as f32 / nf as f32;
            let inp = In { anim: id, t, period: if an.starts_with("melee") { 0.7 } else if *an == "fire" { 0.3 } else { 1.6 }, weapon: base_w, style: Style::of(ch), wback: 0.0 };
            let mut inp = inp;
            let shown_name = anim::key_of(&inp, 1.0).3.model();
            if !shown_name.is_empty() && !cache.contains_key(shown_name) {
                cache.insert(shown_name.to_string(), load(dir, shown_name));
            }
            if let Some(wm) = cache.get(shown_name) {
                inp.wback = -wm.meta.back;
            }
            let out = anim::evaluate(&sk, &inp);
            let (mut p, mut nn) = (Vec::new(), Vec::new());
            rig::skin(&m, &out.world, &mut p, &mut nn);
            let (mut wp, mut wn) = (Vec::new(), Vec::new());
            let mut items = vec![Item { model: &m, pos: &p, nrm: &nn, range: None, id: 1, glow: 0.0 }];
            let wmodel = cache.get(shown_name);
            if let (Some(xf), Some(wm)) = (out.weapon, wmodel) {
                transform_model(wm, &xf, &mut wp, &mut wn);
                let rng = match (out.mag_hidden, wm.part("mag")) {
                    (true, Some(pd)) => Some((0, pd.start as usize)),
                    _ => None,
                };
                items.push(Item { model: wm, pos: &wp, nrm: &wn, range: rng, id: 2, glow: out.shown.glow() });
            }
            let (mut mp, mut mn) = (Vec::new(), Vec::new());
            if let (Some(xf), Some(wm)) = (out.mag, wmodel) {
                if let Some(pd) = wm.part("mag") {
                    let piv = zenvex_r3d::math::V3::from(pd.pivot);
                    let xf2 = zenvex_r3d::math::Xf::new(xf.r, xf.t - xf.r.apply(piv));
                    transform_model(wm, &xf2, &mut mp, &mut mn);
                    items.push(Item { model: wm, pos: &mp, nrm: &mn, range: Some((pd.start as usize, pd.count as usize)), id: 3, glow: 0.0 });
                }
            }
            let s = Settings { yaw, pitch: 22.0, scale: 95.0, ssaa: false, outline_px: 1 };
            row.push(render(&items, &s, &light));
            n += 1;
        }
        rows.push(row);
    }
    eprintln!("{} kare, ortalama {:?}", n, t0.elapsed() / n as u32);
    let cell_w = rows.iter().flatten().map(|i| i.w).max().unwrap() + 6;
    // çapanın altına taşan (zeminin altı) en büyük kısım: hücrede yer ayır, zemin çizgisi çiz
    let below = rows.iter().flatten().map(|i| i.h as i32 - i.ay as i32).max().unwrap().max(0) as usize;
    let above = rows.iter().flatten().map(|i| i.ay as usize).max().unwrap();
    let cell_h = above + below + 12;
    eprintln!("zemin altı en çok {below} px");
    let mut out = image::RgbaImage::from_pixel((cell_w * nf) as u32, (cell_h * rows.len()) as u32, image::Rgba([72, 92, 72, 255]));
    for (r, row) in rows.iter().enumerate() {
        for (c, im) in row.iter().enumerate() {
            // çapa (ayak) hücrede aynı yerde
            let ox = (c * cell_w + cell_w / 2) as i32 - im.ax as i32;
            let gy = (r * cell_h + above + 6) as u32;
            for x in (c * cell_w) as u32..((c + 1) * cell_w) as u32 {
                out.put_pixel(x, gy, image::Rgba([40, 52, 40, 255]));
            }
            let oy = gy as i32 - im.ay as i32;
            for yy in 0..im.h {
                for xx in 0..im.w {
                    let px = im.rgba[yy * im.w + xx];
                    let (x, y) = (ox + xx as i32, oy + yy as i32);
                    if px[3] > 0 && x >= 0 && y >= 0 && (x as u32) < out.width() && (y as u32) < out.height() {
                        out.put_pixel(x as u32, y as u32, image::Rgba(px));
                    }
                }
            }
        }
    }
    out.save(&args[2]).unwrap();
}
