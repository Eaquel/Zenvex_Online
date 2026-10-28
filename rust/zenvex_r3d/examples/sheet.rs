//! Servis üzerinden önizleme sayfası (NPC'ler, kanatlı kahramanlar):
//! cargo run -p zenvex_r3d --example sheet --release -- <m3d> <çıktı.png> <satır;satır;...>
//! satır: karakter[+kanat]:anim:yaw:t,t,t[:silah]   (anim kimliği sayı; ör. "npc_aldric:23:20:0,0.8,1.6")
use std::path::Path;
use zenvex_r3d::service::{ActorReq, Service};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let dir = Path::new(&args[1]);
    let mut s = Service::new();
    for e in std::fs::read_dir(dir).unwrap().flatten() {
        let p = e.path();
        if p.extension().is_some_and(|x| x == "zxm") {
            let name = p.file_stem().unwrap().to_string_lossy().to_string();
            let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
            s.load(&name, &std::fs::read(&p).unwrap(), im.width() as usize, im.height() as usize, im.as_raw()).unwrap();
        }
    }
    let mut rows = Vec::new();
    for line in args[3].split(';') {
        let f: Vec<&str> = line.split(':').collect();
        let (ch, anim, yaw) = (f[0], f[1].parse::<u8>().unwrap(), f[2].parse::<f32>().unwrap());
        let mut row = Vec::new();
        for t in f[3].split(',') {
            let r = ActorReq { ch, weapon: f.get(4).copied().unwrap_or(""), anim, t: t.parse().unwrap(), period: 0.4, yaw, pitch: 22.0, scale: 90.0, ssaa: true, flags: 0 };
            row.push(s.actor(&r).unwrap_or_else(|| panic!("{ch}")).0);
        }
        rows.push(row);
    }
    let cols = rows.iter().map(|r| r.len()).max().unwrap();
    let cw = rows.iter().flatten().map(|i| i.w).max().unwrap() + 6;
    let above = rows.iter().flatten().map(|i| i.ay as usize).max().unwrap();
    let below = rows.iter().flatten().map(|i| i.h as i32 - i.ay as i32).max().unwrap().max(0) as usize;
    let chh = above + below + 12;
    let mut out = image::RgbaImage::from_pixel((cw * cols) as u32, (chh * rows.len()) as u32, image::Rgba([72, 92, 72, 255]));
    for (r, row) in rows.iter().enumerate() {
        for (c, im) in row.iter().enumerate() {
            let ox = (c * cw + cw / 2) as i32 - im.ax as i32;
            let oy = (r * chh + above + 6) as i32 - im.ay as i32;
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
