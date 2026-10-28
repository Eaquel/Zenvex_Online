//! Araç önizlemesi: cargo run -p zenvex_r3d --example vehicles --release -- <m3d> <çıktı.png> <araç> [sürücü]
//! Satırlar: 8 yön; son satır tekerlek dönüşü + eğim + sarsıntı.
use std::path::Path;
use zenvex_r3d::service::{Service, VehicleReq};

fn main() {
    let a: Vec<String> = std::env::args().collect();
    let dir = Path::new(&a[1]);
    let name = a[3].as_str();
    let rider = a.get(4).map(|s| s.as_str()).unwrap_or("");
    let mut sv = Service::new();
    for n in [name, rider] {
        if n.is_empty() {
            continue;
        }
        let zxm = std::fs::read(dir.join(format!("{n}.zxm"))).unwrap();
        let im = image::open(dir.join(format!("{n}.webp"))).unwrap().to_rgba8();
        sv.load(n, &zxm, im.width() as usize, im.height() as usize, im.as_raw()).unwrap();
        if let Ok(nm) = image::open(dir.join(format!("{n}_n.webp"))) {
            let nm = nm.to_rgba8();
            sv.load_normal(n, nm.width() as usize, nm.height() as usize, nm.as_raw());
        }
    }
    let mut imgs = Vec::new();
    let t0 = std::time::Instant::now();
    for d in 0..8 {
        let r = VehicleReq { name, yaw: d as f32 * 45.0, pitch: 22.0, scale: 70.0, wheel: 0.0, bounce: 0.0, tilt: 0.0, rider, rider_t: 0.0, ssaa: 2 };
        imgs.push(sv.vehicle(&r).unwrap().0);
    }
    for f in 0..8 {
        let r = VehicleReq {
            name,
            yaw: 60.0,
            pitch: 22.0,
            scale: 70.0,
            wheel: f as f32 * 30.0,
            bounce: 0.02 * (f as f32 * 0.8).sin(),
            tilt: -4.0 + f as f32,
            rider,
            rider_t: f as f32 * 0.087,
            ssaa: 2,
        };
        imgs.push(sv.vehicle(&r).unwrap().0);
    }
    eprintln!("{} kare, ort {:?}", imgs.len(), t0.elapsed() / imgs.len() as u32);
    let cw = imgs.iter().map(|i| i.w).max().unwrap() + 8;
    let below = imgs.iter().map(|i| i.h as i32 - i.ay as i32).max().unwrap().max(0) as usize;
    let above = imgs.iter().map(|i| i.ay as usize).max().unwrap();
    let ch = above + below + 12;
    let mut out = image::RgbaImage::from_pixel((cw * 8) as u32, (ch * 2) as u32, image::Rgba([72, 92, 72, 255]));
    for (k, im) in imgs.iter().enumerate() {
        let (c, r) = (k % 8, k / 8);
        let gx = (c * cw + cw / 2) as i32;
        let gy = (r * ch + above + 6) as i32;
        for x in (c * cw) as u32..((c + 1) * cw) as u32 {
            out.put_pixel(x, gy as u32, image::Rgba([40, 52, 40, 255]));
        }
        for yy in 0..im.h {
            for xx in 0..im.w {
                let px = im.rgba[yy * im.w + xx];
                let (x, y) = (gx - im.ax as i32 + xx as i32, gy - im.ay as i32 + yy as i32);
                if px[3] > 0 && x >= 0 && y >= 0 && (x as u32) < out.width() && (y as u32) < out.height() {
                    out.put_pixel(x as u32, y as u32, image::Rgba(px));
                }
            }
        }
    }
    out.save(&a[2]).unwrap();
}
