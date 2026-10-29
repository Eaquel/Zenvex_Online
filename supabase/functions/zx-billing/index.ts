// Google Play satın alma doğrulaması (mağaza). İstemci satın alma bitince buraya yollar:
//   { "id": <hesap>, "key": "<anahtar>", "server": "n1", "product": "gems_220", "token": "<purchaseToken>" }
// Google Play Developer API ile satın alma sorgulanır (ödendi mi, bu uygulamanın mı, hangi sipariş); geçerliyse
// zx_store_verified içerik postasını gönderir. Aynı sipariş iki kez işlenmez (veritabanında order_id tekil).
// Gerekli gizli: GOOGLE_PLAY_SA = Play Console'a bağlı hizmet hesabının JSON anahtarı (Play Console → Kullanıcılar ve
// izinler → hizmet hesabına "Finansal veriler / siparişler" izni).
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const PACKAGE = "com.zenvex.online";
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

function b64url(data: ArrayBuffer | Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// hizmet hesabı JWT'si → OAuth erişim jetonu (androidpublisher kapsamı)
async function accessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${header}.${claim}.${b64url(sig)}` }),
  });
  if (!r.ok) throw new Error("Google yetkilendirmesi başarısız");
  return (await r.json()).access_token;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Yalnızca POST" }, 405);
  let body: { id?: number; key?: string; server?: string; product?: string; token?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Geçersiz istek" }, 400);
  }
  const { id, key, server, product, token } = body;
  // kimlik 0 (yönetici hesabı) geçerli: "!id" onu eksik sayıyordu
  if (id == null || !key || !product || !token || !/^[a-z0-9_]{2,40}$/.test(product)) return json({ error: "Eksik bilgi" }, 400);
  const saRaw = Deno.env.get("GOOGLE_PLAY_SA");
  if (!saRaw) return json({ error: "Ödeme doğrulaması henüz yapılandırılmadı" }, 503);

  let purchase: { purchaseState?: number; orderId?: string; consumptionState?: number };
  try {
    const at = await accessToken(JSON.parse(saRaw));
    const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}/purchases/products/${product}/tokens/${encodeURIComponent(token)}`;
    const r = await fetch(url, { headers: { Authorization: `Bearer ${at}` } });
    if (!r.ok) return json({ error: "Satın alma Google'da bulunamadı" }, 402);
    purchase = await r.json();
  } catch (e) {
    return json({ error: (e as Error).message }, 502);
  }
  // 0 = ödendi, 1 = iptal, 2 = beklemede (ör. nakit ödeme)
  if (purchase.purchaseState !== 0) return json({ error: purchase.purchaseState === 2 ? "Ödeme bekleniyor" : "Ödeme tamamlanmadı" }, 402);
  const order = purchase.orderId || `token:${token.slice(0, 60)}`;

  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = await sb.rpc("zx_store_verified", {
    p_id: id, p_key: key, p_server: server ?? "n1", p_product: product, p_order: order, p_token: token,
  });
  if (error) return json({ error: error.message }, 400);
  return json(data);
});
