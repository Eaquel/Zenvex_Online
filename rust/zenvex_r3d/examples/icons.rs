//! Silah simgeleri: her silah modeli çapraz (sap sol altta, uç sağ üstte) önden çizilir.
//! cargo run -p zenvex_r3d --example icons --release -- <m3d_klasörü> <çıktı_klasörü> [ad:dönme ...]
//! Dönme (derece): silahın kendi ekseni etrafında; yassı yüzü (bıçak ağzı, namlu yanı) kameraya çevirir.
use std::path::Path;
use zenvex_r3d::math::{v3, M3, Xf};
use zenvex_r3d::model::{Model, Texture};
use zenvex_r3d::raster::Light;
use zenvex_r3d::{render, transform_model, Item, Settings};

const WEAPONS: &[&str] = &[
    "knife", "hatchet", "handaxe", "club", "effigy", "katana", "void_claw", "blood_scythe", "ice_scythe", "magma_hammer", "storm_axe",
    "pickaxe", "chainsaw", "pistol", "uzi", "ak47", "sniper", "rpg",
];

fn load(dir: &Path, name: &str) -> Model {
    let mut m = Model::parse(&std::fs::read(dir.join(format!("{name}.zxm"))).unwrap()).unwrap();
    let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
    m.tex = Some(Texture::from_rgba(im.width() as usize, im.height() as usize, im.as_raw()));
    m
}

fn glow(name: &str) -> f32 {
    match name {
        "magma_hammer" => 0.18,
        "ice_scythe" => 0.12,
        "void_claw" => 0.10,
        "chainsaw" | "uzi" => 0.08,
        "storm_axe" => 0.06,
        _ => 0.0,
    }
}

fn main() {
    let a: Vec<String> = std::env::args().collect();
    let dir = Path::new(&a[1]);
    let out = Path::new(&a[2]);
    std::fs::create_dir_all(out).unwrap();
    let mut roll: std::collections::HashMap<String, f32> = Default::default();
    for x in &a[3..] {
        if let Some((n, r)) = x.split_once(':') {
            roll.insert(n.to_string(), r.parse().unwrap_or(0.0));
        }
    }
    let light = Light::default();
    for &name in WEAPONS {
        let m = load(dir, name);
        let r = roll.get(name).copied().unwrap_or(0.0);
        // ateşli silahlar yatay (namlu sağa), diğerleri çapraz (uç sağ üst)
        let gun = matches!(name, "pistol" | "uzi" | "ak47" | "sniper" | "rpg");
        let ang: f32 = if gun { 0.0 } else { 45.0 };
        let fwd = v3(ang.to_radians().cos(), 0.0, ang.to_radians().sin());
        let up0 = v3(-ang.to_radians().sin(), 0.0, ang.to_radians().cos());
        let up = M3::axis_angle(fwd, r.to_radians()).apply(up0);
        let b = M3::basis(fwd, up);
        let (mut p, mut n) = (Vec::new(), Vec::new());
        transform_model(&m, &Xf::new(b, v3(0.0, 0.0, 0.0)), &mut p, &mut n);
        // en uzun kenar ~300 px (ssaa sonrası); sonra paketleyici hücreye sığdırır
        let (mut x0, mut x1, mut z0, mut z1) = (f32::MAX, f32::MIN, f32::MAX, f32::MIN);
        for q in &p {
            x0 = x0.min(q.x);
            x1 = x1.max(q.x);
            z0 = z0.min(q.z);
            z1 = z1.max(q.z);
        }
        let ext = (x1 - x0).max(z1 - z0).max(0.05);
        let s = Settings { yaw: 0.0, pitch: 0.0, scale: 300.0 / ext, ssaa: true, outline_px: 1 };
        let items = [Item { model: &m, pos: &p, nrm: &n, range: None, id: 1, glow: glow(name) }];
        let img = render(&items, &s, &light);
        let mut im = image::RgbaImage::new(img.w as u32, img.h as u32);
        for (i, px) in img.rgba.iter().enumerate() {
            im.put_pixel((i % img.w) as u32, (i / img.w) as u32, image::Rgba(*px));
        }
        im.save(out.join(format!("{name}.png"))).unwrap();
        println!("{name}: {}x{}", img.w, img.h);
    }
}
