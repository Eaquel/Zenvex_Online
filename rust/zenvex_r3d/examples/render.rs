//! Masaüstü önizleme: cargo run -p zenvex_r3d --example render --release -- <m3d_klasörü> <çıktı.png> [karakter] [silah]
use std::path::Path;
use zenvex_r3d::math::{v3, M3, Xf};
use zenvex_r3d::model::{Model, Texture};
use zenvex_r3d::raster::Light;
use zenvex_r3d::rig::{self, Pose, Skeleton};
use zenvex_r3d::{render, transform_model, Item, Settings};

fn load(dir: &Path, name: &str) -> Model {
    let mut m = Model::parse(&std::fs::read(dir.join(format!("{name}.zxm"))).unwrap()).unwrap();
    let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
    m.tex = Some(Texture::from_rgba(im.width() as usize, im.height() as usize, im.as_raw()));
    m
}

fn main() {
    let a: Vec<String> = std::env::args().collect();
    let dir = Path::new(&a[1]);
    let ch = a.get(3).map(|s| s.as_str()).unwrap_or("kael");
    let wp = a.get(4).map(|s| s.as_str());
    let m = load(dir, ch);
    let w = wp.map(|n| load(dir, n));
    let sk = Skeleton::of(&m);
    let pose = Pose::default();
    let xf = sk.world(&pose);
    let (mut p, mut n) = (Vec::new(), Vec::new());
    rig::skin(&m, &xf, &mut p, &mut n);
    let (mut wpv, mut wn) = (Vec::new(), Vec::new());
    if let Some(wm) = &w {
        // silah sağ avuçta, ucu öne-aşağı
        let palm = xf[rig::HAND_R].apply(sk.palm_r);
        let b = M3::basis(v3(0.0, -0.8, -0.5), v3(0.0, -0.5, 0.8));
        transform_model(wm, &Xf::new(b, palm), &mut wpv, &mut wn);
    }
    let light = Light::default();
    let mut imgs = Vec::new();
    let t0 = std::time::Instant::now();
    for d in 0..8 {
        let s = Settings { yaw: d as f32 * 45.0, pitch: 22.0, scale: 120.0, ssaa: false, outline_px: 1 };
        let mut items = vec![Item { model: &m, pos: &p, nrm: &n, range: None, id: 1, glow: 0.0 }];
        if let Some(wm) = &w {
            items.push(Item { model: wm, pos: &wpv, nrm: &wn, range: None, id: 2, glow: 0.0 });
        }
        imgs.push(render(&items, &s, &light));
    }
    println!("8 kare: {:?}", t0.elapsed());
    let t1 = std::time::Instant::now();
    for _ in 0..50 {
        let s = Settings { yaw: 30.0, pitch: 22.0, scale: 120.0, ssaa: false, outline_px: 1 };
        let mut items = vec![Item { model: &m, pos: &p, nrm: &n, range: None, id: 1, glow: 0.0 }];
        if let Some(wm) = &w {
            items.push(Item { model: wm, pos: &wpv, nrm: &wn, range: None, id: 2, glow: 0.0 });
        }
        std::hint::black_box(render(&items, &s, &light));
    }
    let t2 = std::time::Instant::now();
    for _ in 0..50 {
        rig::skin(&m, &sk.world(&pose), &mut p, &mut n);
    }
    println!("çizim/kare: {:?}  deri/kare: {:?}", t1.elapsed() / 50, t2.elapsed() / 50);
    let cw = imgs.iter().map(|i| i.w).sum::<usize>() + 8 * 10;
    let chh = imgs.iter().map(|i| i.h).max().unwrap();
    let mut out = image::RgbaImage::from_pixel(cw as u32, chh as u32, image::Rgba([70, 90, 70, 255]));
    let mut x = 0;
    for im in &imgs {
        for yy in 0..im.h {
            for xx in 0..im.w {
                let c = im.rgba[yy * im.w + xx];
                if c[3] > 0 {
                    out.put_pixel((x + xx) as u32, (chh - im.h + yy) as u32, image::Rgba(c));
                }
            }
        }
        x += im.w + 10;
    }
    out.save(&a[2]).unwrap();
}
