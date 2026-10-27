//! Küçük 3B matematik: vektör, 3×3 dönme matrisi, afin dönüşüm.

use std::ops::{Add, Mul, Neg, Sub};

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct V3 {
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

pub const fn v3(x: f32, y: f32, z: f32) -> V3 {
    V3 { x, y, z }
}

impl V3 {
    pub const ZERO: V3 = v3(0.0, 0.0, 0.0);
    pub fn dot(self, o: V3) -> f32 {
        self.x * o.x + self.y * o.y + self.z * o.z
    }
    pub fn cross(self, o: V3) -> V3 {
        v3(self.y * o.z - self.z * o.y, self.z * o.x - self.x * o.z, self.x * o.y - self.y * o.x)
    }
    pub fn len(self) -> f32 {
        self.dot(self).sqrt()
    }
    pub fn norm(self) -> V3 {
        let l = self.len();
        if l > 1e-9 {
            self * (1.0 / l)
        } else {
            self
        }
    }
    pub fn lerp(self, o: V3, t: f32) -> V3 {
        self + (o - self) * t
    }
    pub fn from(a: [f32; 3]) -> V3 {
        v3(a[0], a[1], a[2])
    }
}

impl Add for V3 {
    type Output = V3;
    fn add(self, o: V3) -> V3 {
        v3(self.x + o.x, self.y + o.y, self.z + o.z)
    }
}
impl Sub for V3 {
    type Output = V3;
    fn sub(self, o: V3) -> V3 {
        v3(self.x - o.x, self.y - o.y, self.z - o.z)
    }
}
impl Mul<f32> for V3 {
    type Output = V3;
    fn mul(self, s: f32) -> V3 {
        v3(self.x * s, self.y * s, self.z * s)
    }
}
impl Neg for V3 {
    type Output = V3;
    fn neg(self) -> V3 {
        v3(-self.x, -self.y, -self.z)
    }
}

/// Satır düzenli 3×3 matris (m[r][c]); `apply` sütun vektörüyle çarpar.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct M3 {
    pub m: [[f32; 3]; 3],
}

impl Default for M3 {
    fn default() -> Self {
        M3::IDENTITY
    }
}

impl M3 {
    pub const IDENTITY: M3 = M3 { m: [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]] };

    pub fn rx(deg: f32) -> M3 {
        let (s, c) = deg.to_radians().sin_cos();
        M3 { m: [[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]] }
    }
    pub fn ry(deg: f32) -> M3 {
        let (s, c) = deg.to_radians().sin_cos();
        M3 { m: [[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]] }
    }
    pub fn rz(deg: f32) -> M3 {
        let (s, c) = deg.to_radians().sin_cos();
        M3 { m: [[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]] }
    }
    /// Paketteki motorla aynı sıra: Rz · Ry · Rx (derece)
    pub fn xyz(rx: f32, ry: f32, rz: f32) -> M3 {
        M3::rz(rz) * M3::ry(ry) * M3::rx(rx)
    }
    /// Eksen-açı (radyan, eksen birim)
    pub fn axis_angle(a: V3, rad: f32) -> M3 {
        let (s, c) = rad.sin_cos();
        let t = 1.0 - c;
        M3 {
            m: [
                [t * a.x * a.x + c, t * a.x * a.y - s * a.z, t * a.x * a.z + s * a.y],
                [t * a.x * a.y + s * a.z, t * a.y * a.y + c, t * a.y * a.z - s * a.x],
                [t * a.x * a.z - s * a.y, t * a.y * a.z + s * a.x, t * a.z * a.z + c],
            ],
        }
    }
    /// `from` birim yönünü `to` birim yönüne çeviren en kısa dönme
    pub fn from_to(from: V3, to: V3) -> M3 {
        let a = from.norm();
        let b = to.norm();
        let c = a.dot(b);
        if c > 0.999_999 {
            return M3::IDENTITY;
        }
        if c < -0.999_999 {
            let o = if a.x.abs() < 0.9 { v3(1.0, 0.0, 0.0) } else { v3(0.0, 1.0, 0.0) };
            return M3::axis_angle(a.cross(o).norm(), std::f32::consts::PI);
        }
        let ax = a.cross(b);
        M3::axis_angle(ax.norm(), ax.len().atan2(c))
    }
    /// Sütunları u, v, w olan matris (yerel X→u, Y→v, Z→w)
    pub fn cols(u: V3, v: V3, w: V3) -> M3 {
        M3 { m: [[u.x, v.x, w.x], [u.y, v.y, w.y], [u.z, v.z, w.z]] }
    }
    /// İleri yön `fwd` ve yaklaşık yukarı `up` ile dik taban (X = ileri, Z = yukarı)
    pub fn basis(fwd: V3, up: V3) -> M3 {
        let u = fwd.norm();
        let w = (up - u * up.dot(u)).norm();
        let v = w.cross(u).norm();
        M3::cols(u, v, w)
    }
    pub fn apply(&self, p: V3) -> V3 {
        let m = &self.m;
        v3(
            m[0][0] * p.x + m[0][1] * p.y + m[0][2] * p.z,
            m[1][0] * p.x + m[1][1] * p.y + m[1][2] * p.z,
            m[2][0] * p.x + m[2][1] * p.y + m[2][2] * p.z,
        )
    }
    pub fn transpose(&self) -> M3 {
        let m = &self.m;
        M3 { m: [[m[0][0], m[1][0], m[2][0]], [m[0][1], m[1][1], m[2][1]], [m[0][2], m[1][2], m[2][2]]] }
    }
    pub fn col(&self, c: usize) -> V3 {
        v3(self.m[0][c], self.m[1][c], self.m[2][c])
    }
}

impl Mul for M3 {
    type Output = M3;
    fn mul(self, o: M3) -> M3 {
        let mut r = [[0.0f32; 3]; 3];
        for (i, row) in r.iter_mut().enumerate() {
            for (j, cell) in row.iter_mut().enumerate() {
                *cell = self.m[i][0] * o.m[0][j] + self.m[i][1] * o.m[1][j] + self.m[i][2] * o.m[2][j];
            }
        }
        M3 { m: r }
    }
}

/// Afin dönüşüm: p' = r · p + t
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Xf {
    pub r: M3,
    pub t: V3,
}

impl Default for Xf {
    fn default() -> Self {
        Xf::IDENTITY
    }
}

impl Xf {
    pub const IDENTITY: Xf = Xf { r: M3::IDENTITY, t: V3::ZERO };
    pub fn new(r: M3, t: V3) -> Xf {
        Xf { r, t }
    }
    pub fn apply(&self, p: V3) -> V3 {
        self.r.apply(p) + self.t
    }
    pub fn then(&self, o: &Xf) -> Xf {
        // önce self, sonra o
        Xf { r: o.r * self.r, t: o.r.apply(self.t) + o.t }
    }
}

pub fn smoothstep(e0: f32, e1: f32, x: f32) -> f32 {
    let t = ((x - e0) / (e1 - e0)).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

pub fn lerp(a: f32, b: f32, t: f32) -> f32 {
    a + (b - a) * t
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn from_to_rotates() {
        let r = M3::from_to(v3(1.0, 0.0, 0.0), v3(0.0, 0.0, 1.0));
        let p = r.apply(v3(1.0, 0.0, 0.0));
        assert!((p - v3(0.0, 0.0, 1.0)).len() < 1e-5);
    }

    #[test]
    fn basis_is_orthonormal() {
        let b = M3::basis(v3(0.3, -0.9, 0.2), v3(0.0, 0.0, 1.0));
        let (u, v, w) = (b.col(0), b.col(1), b.col(2));
        assert!((u.len() - 1.0).abs() < 1e-5 && u.dot(v).abs() < 1e-5 && u.dot(w).abs() < 1e-5 && v.dot(w).abs() < 1e-5);
        assert!((u.cross(v) - w).len() < 1e-4);
    }
}
