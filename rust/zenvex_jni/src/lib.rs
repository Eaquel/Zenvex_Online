//! Kotlin `com.zenvex.online.game.Native` nesnesinin JNI uygulaması.
//! Oyun nesnesi `Box<Game>` olarak tutulur, Kotlin'e `Long` tutamaç olarak verilir.
#![allow(non_snake_case)]

use jni::objects::{JByteArray, JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jfloat, jint, jlong, jstring};
use jni::JNIEnv;
use std::cell::RefCell;
use zenvex_core::game::{Remote, STRIDE};
use zenvex_core::{Game, Input};

/// Uzak oyuncu kaydı: x, y, yön, anim, anim_t, karakter, ekipman0..2, araç
pub const REMOTE_STRIDE: usize = 10;
/// Yerel oyuncu kaydı (çevrimiçi yayın): aynı sıra
pub const LOCAL_STRIDE: usize = 10;

thread_local! {
    static DRAW_BUF: RefCell<Vec<f32>> = RefCell::new(vec![0.0; STRIDE * 6000]);
    static PIX_BUF: RefCell<Vec<u32>> = const { RefCell::new(Vec::new()) };
}

fn game<'a>(h: jlong) -> Option<&'a mut Game> {
    if h == 0 {
        None
    } else {
        // SAFETY: tutamaç `create` ile oluşturuldu ve `destroy` çağrılana kadar geçerli;
        // Kotlin tarafı tüm çağrıları tek bir kilit altında yapar.
        Some(unsafe { &mut *(h as *mut Game) })
    }
}

fn jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn rstr(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|s| s.into()).unwrap_or_default()
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_create<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    seed: jlong,
    save: JString<'l>,
    day: jint,
) -> jlong {
    let save = rstr(&mut env, &save);
    let progress = if save.is_empty() {
        zenvex_core::Progress::default()
    } else {
        zenvex_core::progress_from_json(&save)
    };
    Box::into_raw(Box::new(Game::new(
        seed as u64,
        progress,
        day.max(0) as u32,
    ))) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_destroy<'l>(
    _env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) {
    if h != 0 {
        // SAFETY: `create` tarafından Box::into_raw ile üretildi, yalnızca bir kez serbest bırakılır.
        drop(unsafe { Box::from_raw(h as *mut Game) });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_worldJson<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> jstring {
    let s = game(h)
        .map(|g| zenvex_core::world_json(g))
        .unwrap_or_default();
    jstr(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_uiJson<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    kind: JString<'l>,
) -> jstring {
    let kind = rstr(&mut env, &kind);
    let s = game(h)
        .map(|g| g.ui_json(&kind))
        .unwrap_or_else(|| "{}".into());
    jstr(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_command<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    cmd: JString<'l>,
) -> jstring {
    let cmd = rstr(&mut env, &cmd);
    let s = game(h)
        .map(|g| g.command(&cmd))
        .unwrap_or_else(|| "{\"ok\":false,\"msg\":\"oyun yok\"}".into());
    jstr(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_step<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    dt: jfloat,
    input: JFloatArray<'l>,
) {
    let mut v = [0.0f32; 5];
    if env.get_float_array_region(&input, 0, &mut v).is_err() {
        return;
    }
    if let Some(g) = game(h) {
        g.step(
            dt,
            Input {
                move_x: v[0],
                move_y: v[1],
                atk_x: v[2],
                atk_y: v[3],
                attacking: v[4] > 0.5,
            },
        );
    }
}

/// view: [x, y, w, h, bitki_yoğunluğu]
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_drawList<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    view: JFloatArray<'l>,
    out: JFloatArray<'l>,
) -> jint {
    let Some(g) = game(h) else { return 0 };
    let mut v = [0.0f32; 5];
    if env.get_float_array_region(&view, 0, &mut v).is_err() {
        return 0;
    }
    let cap = env.get_array_length(&out).unwrap_or(0) as usize;
    DRAW_BUF.with(|b| {
        let mut buf = b.borrow_mut();
        let len = (cap.min(buf.len()) / STRIDE) * STRIDE;
        let n = g.draw_list(v[0], v[1], v[2], v[3], &mut buf[..len], v[4]);
        let _ = env.set_float_array_region(&out, 0, &buf[..n * STRIDE]);
        n as jint
    })
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_hud<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) {
    if let Some(g) = game(h) {
        let _ = env.set_float_array_region(&out, 0, &g.hud());
    }
}

/// Radar kayıtları (x, y, tür) üçlüleri; dönen değer kayıt sayısı.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_radar<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) -> jint {
    let Some(g) = game(h) else { return 0 };
    let cap = env.get_array_length(&out).unwrap_or(0) as usize;
    let mut buf = vec![0.0f32; cap];
    let n = g.radar(&mut buf);
    let _ = env.set_float_array_region(&out, 0, &buf[..n * 3]);
    n as jint
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_events<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> jstring {
    let s = game(h)
        .map(|g| g.take_events_json())
        .unwrap_or_else(|| "[]".into());
    jstr(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_saveJson<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> jstring {
    let s = game(h)
        .map(|g| {
            g.sync_pos();
            zenvex_core::progress_to_json(&g.progress)
        })
        .unwrap_or_default();
    jstr(&mut env, &s)
}

/// Zemin dokuları: `size`x`size` ARGB, world::ground sırasıyla art arda.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_setTextures<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    size: jint,
    pixels: JIntArray<'l>,
) {
    let Some(g) = game(h) else { return };
    let size = size.max(1) as usize;
    let len = env.get_array_length(&pixels).unwrap_or(0) as usize;
    let mut all = vec![0i32; len];
    if env.get_int_array_region(&pixels, 0, &mut all).is_err() {
        return;
    }
    let per = size * size;
    g.textures.size = size;
    g.textures.tex = all
        .chunks(per)
        .filter(|c| c.len() == per)
        .map(|c| c.iter().map(|&p| p as u32).collect())
        .collect();
}

/// V13 bölge dokuları (ana, ikinci, yol, boşluk; `size`x`size` ARGB art arda) ve ait oldukları bölge.
/// Ortak dokularla aynı boyda olmalı; ışınlanınca yenisi gönderilir.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_setRegionTextures<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    area: jint,
    size: jint,
    pixels: JIntArray<'l>,
) {
    let Some(g) = game(h) else { return };
    let size = size.max(1) as usize;
    if g.textures.size != size {
        return;
    }
    let len = env.get_array_length(&pixels).unwrap_or(0) as usize;
    let mut all = vec![0i32; len];
    if env.get_int_array_region(&pixels, 0, &mut all).is_err() {
        return;
    }
    let base = zenvex_core::world::ground::BASE_COUNT;
    g.textures.tex.truncate(base);
    let per = size * size;
    for c in all.chunks(per).take(4) {
        g.textures.tex.push(if c.len() == per { c.iter().map(|&p| p as u32).collect() } else { Vec::new() });
    }
    g.textures.area = if area >= 0 { Some(area as usize) } else { None };
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_renderChunk<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    x0: jfloat,
    y0: jfloat,
    size: jint,
    out: JIntArray<'l>,
) -> jint {
    let Some(g) = game(h) else { return 0 };
    if !g.textures.ready() {
        return 0;
    }
    let size = size.max(1) as usize;
    PIX_BUF.with(|b| {
        let mut buf = b.borrow_mut();
        buf.resize(size * size, 0);
        zenvex_core::ground::render_chunk(&g.world, &g.textures, x0, y0, size, &mut buf);
        // SAFETY: u32 ve i32 aynı boyut ve hizalamaya sahip.
        let ints: &[i32] =
            unsafe { std::slice::from_raw_parts(buf.as_ptr() as *const i32, buf.len()) };
        let _ = env.set_int_array_region(&out, 0, ints);
        1
    })
}

#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_minimap<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    n: jint,
    out: JIntArray<'l>,
) {
    let Some(g) = game(h) else { return };
    let n = n.max(1) as usize;
    let mut buf = vec![0u32; n * n];
    zenvex_core::minimap(g, n, &mut buf);
    let ints: Vec<i32> = buf.iter().map(|&p| p as i32).collect();
    let _ = env.set_int_array_region(&out, 0, &ints);
}

/// Uzak oyuncular: REMOTE_STRIDE'lık kayıtlar.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_setRemotes<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    data: JFloatArray<'l>,
    count: jint,
) {
    let Some(g) = game(h) else { return };
    let n = count.max(0) as usize;
    let mut v = vec![0.0f32; n * REMOTE_STRIDE];
    if n > 0 && env.get_float_array_region(&data, 0, &mut v).is_err() {
        return;
    }
    g.remotes = v
        .chunks(REMOTE_STRIDE)
        .map(|r| Remote {
            x: r[0],
            y: r[1],
            dir: r[2] as u8 & 7,
            anim: r[3] as u8,
            anim_t: r[4],
            character: r[5] as u8,
            equip: [r[6], r[7], r[8]],
            vehicle: r[9],
        })
        .collect();
}

/// Yerel oyuncu durumu (çevrimiçi yayın için), LOCAL_STRIDE uzunluğunda.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_localState<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) {
    let Some(g) = game(h) else { return };
    let p = &g.player;
    let eq = g.equip_codes();
    let veh = p.vehicle.map_or(-1.0, |i| g.vehicles[i].kind as f32);
    let (anim, clock) = g.anim_state();
    let rec: [f32; LOCAL_STRIDE] = [
        p.x,
        p.y,
        p.dir as f32,
        anim as f32,
        clock,
        g.progress.character as f32,
        eq[0],
        eq[1],
        eq[2],
        veh,
    ];
    let _ = env.set_float_array_region(&out, 0, &rec);
}

/// Ev sahibi: zombi anlık görüntüsü (net::NET_STRIDE kayıtlar); kayıt sayısı döner.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_netSnapshot<'l>(env: JNIEnv<'l>, _c: JClass<'l>, h: jlong, out: JFloatArray<'l>) -> jint {
    let Some(g) = game(h) else { return 0 };
    let cap = env.get_array_length(&out).unwrap_or(0) as usize;
    let mut buf = vec![0.0f32; cap];
    let n = g.net_snapshot(&mut buf);
    let _ = env.set_float_array_region(&out, 0, &buf[..n * zenvex_core::net::NET_STRIDE]);
    n as jint
}

/// İstemci: ev sahibinin görüntüsünü uygula.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_netApply<'l>(env: JNIEnv<'l>, _c: JClass<'l>, h: jlong, data: JFloatArray<'l>, count: jint) {
    let Some(g) = game(h) else { return };
    let n = count.max(0) as usize * zenvex_core::net::NET_STRIDE;
    let mut v = vec![0.0f32; n];
    if n > 0 && env.get_float_array_region(&data, 0, &mut v).is_err() {
        return;
    }
    g.net_apply(&v);
}

/// V15 gerçek harita: bölgenin zemini oyunun kendi dokularıyla küçük ölçekte (`w`x`hh`, piksel başına `step` dünya pikseli).
/// Bölge dokuları yüklü değilse 0 döner (çağıran sonra yeniden dener).
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_renderRegion<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    x0: jfloat,
    y0: jfloat,
    step: jfloat,
    w: jint,
    hh: jint,
    out: JIntArray<'l>,
) -> jint {
    let Some(g) = game(h) else { return 0 };
    if !g.textures.ready() {
        return 0;
    }
    let (w, hh) = (w.max(1) as usize, hh.max(1) as usize);
    let mut buf = vec![0u32; w * hh];
    zenvex_core::ground::render_scaled(&g.world, &g.textures, x0, y0, step.max(0.1), w, hh, &mut buf);
    let ints: Vec<i32> = buf.iter().map(|&p| p as i32).collect();
    let _ = env.set_int_array_region(&out, 0, &ints);
    1
}

/// V15 gerçek harita: dikdörtgendeki sabit nesneler (kırılmamış) — kayıt başına [sprite, x, y, tür]; yazılan kayıt sayısı döner.
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_regionStatics<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    x0: jfloat,
    y0: jfloat,
    x1: jfloat,
    y1: jfloat,
    out: JFloatArray<'l>,
) -> jint {
    let Some(g) = game(h) else { return 0 };
    let cap = env.get_array_length(&out).unwrap_or(0).max(0) as usize / 4;
    let mut v: Vec<f32> = Vec::with_capacity(cap.min(g.world.statics.len()) * 4);
    for s in &g.world.statics {
        if v.len() / 4 >= cap {
            break;
        }
        if s.broken || s.x < x0 || s.x >= x1 || s.y < y0 || s.y >= y1 {
            continue;
        }
        v.extend_from_slice(&[s.sprite as f32, s.x, s.y, s.kind as u8 as f32]);
    }
    let _ = env.set_float_array_region(&out, 0, &v);
    (v.len() / 4) as jint
}

// ============================================================================ 3B çizici
// Kotlin `Model3D` yalnız kendi iş parçacığından çağırır; yine de kilitle korunur.
static R3D: std::sync::Mutex<Option<zenvex_r3d::service::Service>> = std::sync::Mutex::new(None);

fn r3d<T>(f: impl FnOnce(&mut zenvex_r3d::service::Service) -> T) -> T {
    let mut g = R3D.lock().unwrap_or_else(|e| e.into_inner());
    f(g.get_or_insert_with(zenvex_r3d::service::Service::new))
}

/// Model yükler: .zxm baytları + doku (ARGB tamsayı, Bitmap.getPixels düzeni)
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dLoad<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    name: JString<'l>,
    zxm: JByteArray<'l>,
    tw: jint,
    th: jint,
    argb: JIntArray<'l>,
) -> jint {
    let name = rstr(&mut env, &name);
    let bytes = env.convert_byte_array(&zxm).unwrap_or_default();
    let n = (tw.max(0) * th.max(0)) as usize;
    let mut px = vec![0i32; n];
    if n > 0 && env.get_int_array_region(&argb, 0, &mut px).is_err() {
        return 0;
    }
    let mut rgba = Vec::with_capacity(n * 4);
    for p in px {
        let p = p as u32;
        rgba.extend_from_slice(&[(p >> 16) as u8, (p >> 8) as u8, p as u8, (p >> 24) as u8]);
    }
    match r3d(|s| s.load(&name, &bytes, tw as usize, th as usize, &rgba)) {
        Ok(()) => 1,
        Err(_) => 0,
    }
}

/// Modelin normal haritası (ARGB). dönüş 1: yüklendi
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dNormal<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    name: JString<'l>,
    tw: jint,
    th: jint,
    argb: JIntArray<'l>,
) -> jint {
    let name = rstr(&mut env, &name);
    let n = (tw.max(0) * th.max(0)) as usize;
    let mut px = vec![0i32; n];
    if n == 0 || env.get_int_array_region(&argb, 0, &mut px).is_err() {
        return 0;
    }
    let mut rgba = Vec::with_capacity(n * 4);
    for p in px {
        let p = p as u32;
        rgba.extend_from_slice(&[(p >> 16) as u8, (p >> 8) as u8, p as u8, 255]);
    }
    r3d(|s| s.load_normal(&name, tw as usize, th as usize, &rgba)) as jint
}

fn put_image(env: &mut JNIEnv, img: &zenvex_r3d::Image, pts: &zenvex_r3d::service::Points, out: &JIntArray, info: &JFloatArray) -> jint {
    let cap = env.get_array_length(out).unwrap_or(0) as usize;
    let n = img.w * img.h;
    if n > cap {
        // çağıran daha büyük dizi ayırsın: gereken boyutu bildir
        let _ = env.set_float_array_region(info, 0, &[img.w as f32, img.h as f32]);
        return -(n as jint);
    }
    let px: Vec<i32> = img.rgba.iter().map(|c| ((c[3] as u32) << 24 | (c[0] as u32) << 16 | (c[1] as u32) << 8 | c[2] as u32) as i32).collect();
    let _ = env.set_int_array_region(out, 0, &px);
    let mut inf = [0f32; 4 + zenvex_r3d::service::Points::LEN];
    inf[0] = img.w as f32;
    inf[1] = img.h as f32;
    inf[2] = img.ax;
    inf[3] = img.ay;
    pts.write(&mut inf[4..]);
    let _ = env.set_float_array_region(info, 0, &inf);
    n as jint
}

/// Karakter karesi. p: [anim, t, period, yaw, pitch, scale, ssaa (kenar yumuşatma çarpanı 1-3), flags]; dönüş: piksel sayısı (eksi: dizi küçük)
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dActor<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    ch: JString<'l>,
    weapon: JString<'l>,
    p: JFloatArray<'l>,
    out: JIntArray<'l>,
    info: JFloatArray<'l>,
) -> jint {
    let ch = rstr(&mut env, &ch);
    let weapon = rstr(&mut env, &weapon);
    let mut a = [0f32; 8];
    if env.get_float_array_region(&p, 0, &mut a).is_err() {
        return 0;
    }
    let req = zenvex_r3d::service::ActorReq {
        ch: &ch,
        weapon: &weapon,
        anim: a[0] as u8,
        t: a[1],
        period: a[2],
        yaw: a[3],
        pitch: a[4],
        scale: a[5],
        ssaa: (a[6].round() as i32).clamp(1, 3) as u8,
        flags: a[7] as u32,
    };
    match r3d(|s| s.actor(&req)) {
        Some((img, pts)) => put_image(&mut env, &img, &pts, &out, &info),
        None => 0,
    }
}

/// Araç karesi. p: [yaw, pitch, scale, wheel, bounce, tilt, rider_t, ssaa (1-3)]
#[no_mangle]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dVehicle<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    name: JString<'l>,
    rider: JString<'l>,
    p: JFloatArray<'l>,
    out: JIntArray<'l>,
    info: JFloatArray<'l>,
) -> jint {
    let name = rstr(&mut env, &name);
    let rider = rstr(&mut env, &rider);
    let mut a = [0f32; 8];
    if env.get_float_array_region(&p, 0, &mut a).is_err() {
        return 0;
    }
    let req = zenvex_r3d::service::VehicleReq {
        name: &name,
        yaw: a[0],
        pitch: a[1],
        scale: a[2],
        wheel: a[3],
        bounce: a[4],
        tilt: a[5],
        rider: &rider,
        rider_t: a[6],
        ssaa: (a[7].round() as i32).clamp(1, 3) as u8,
    };
    match r3d(|s| s.vehicle(&req)) {
        Some((img, pts)) => put_image(&mut env, &img, &pts, &out, &info),
        None => 0,
    }
}
