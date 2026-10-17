//! Kotlin `online.zenvex.game.Native` nesnesinin JNI uygulaması.
//! Oyun nesnesi `Box<Game>` olarak tutulur, Kotlin'e `Long` tutamaç olarak verilir.
#![allow(non_snake_case)]

use jni::objects::{JClass, JFloatArray, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::cell::RefCell;
use zenvex_core::{game::STRIDE, Game, Input};

thread_local! {
    static DRAW_BUF: RefCell<Vec<f32>> = RefCell::new(vec![0.0; STRIDE * 6000]);
}

fn game<'a>(h: jlong) -> Option<&'a mut Game> {
    if h == 0 {
        None
    } else {
        // SAFETY: tutamaç `create` ile oluşturuldu ve `destroy` çağrılana kadar geçerli;
        // Kotlin tarafı tüm çağrıları tek bir iş parçacığından (oyun döngüsü kilidi altında) yapar.
        Some(unsafe { &mut *(h as *mut Game) })
    }
}

fn jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_create<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    seed: jlong,
    save: JString<'l>,
) -> jlong {
    let save: String = env.get_string(&save).map(|s| s.into()).unwrap_or_default();
    let progress = zenvex_core::progress::Progress::default();
    let progress = if save.is_empty() {
        progress
    } else {
        zenvex_core::progress_from_json(&save)
    };
    Box::into_raw(Box::new(Game::new(seed as u64, progress))) as jlong
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_destroy<'l>(
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
pub extern "system" fn Java_online_zenvex_game_Native_worldJson<'l>(
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
pub extern "system" fn Java_online_zenvex_game_Native_step<'l>(
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
pub extern "system" fn Java_online_zenvex_game_Native_drawList<'l>(
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
        let len = cap.min(buf.len());
        let n = g.draw_list(v[0], v[1], v[2], v[3], &mut buf[..len], v[4]);
        let _ = env.set_float_array_region(&out, 0, &buf[..n * STRIDE]);
        n as jint
    })
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_hud<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) {
    if let Some(g) = game(h) {
        let _ = env.set_float_array_region(&out, 0, &g.hud());
    }
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_radar<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) -> jint {
    let Some(g) = game(h) else { return 0 };
    let cap = env.get_array_length(&out).unwrap_or(0) as usize;
    let mut buf = vec![0.0f32; cap];
    let n = g.radar(&mut buf);
    let _ = env.set_float_array_region(&out, 0, &buf[..n * 2]);
    n as jint
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_events<'l>(
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
pub extern "system" fn Java_online_zenvex_game_Native_saveJson<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> jstring {
    let s = game(h)
        .map(|g| zenvex_core::progress_to_json(&g.progress))
        .unwrap_or_default();
    jstr(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_skillsJson<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> jstring {
    let s = game(h)
        .map(|g| zenvex_core::skills_json(&g.progress))
        .unwrap_or_default();
    jstr(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_upgradeSkill<'l>(
    _env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
    id: jint,
) -> jboolean {
    let Some(g) = game(h) else { return JNI_FALSE };
    if id < 0 || g.progress.upgrade(id as usize).is_err() {
        return JNI_FALSE;
    }
    g.player.hp = g.player.hp.min(g.progress.max_hp());
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_online_zenvex_game_Native_respawn<'l>(
    _env: JNIEnv<'l>,
    _c: JClass<'l>,
    h: jlong,
) {
    if let Some(g) = game(h) {
        g.respawn();
    }
}
