// Google ile giriş: istemciden gelen Google ID jetonunu doğrular, Zenvex hesabını bulur/açar ya da var olan hesaba bağlar.
// İstemci kimliği: WEB_CLIENT_ID (Google Cloud → OAuth "Web application"); jetonun "aud" alanı buna eşit olmalı.
// İstek: { "id_token": "...", "action": "login" }  ya da  { "id_token": "...", "action": "link", "id": 1000001, "key": "ABC..." }
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const WEB_CLIENT_ID = "145695347343-afg8kme2oe2ir1hlie4f36pi1f7ilf3m.apps.googleusercontent.com";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Yalnızca POST" }, 405);
  // OAuth "Web application" istemci kimliği gizli değildir; ortam değişkeniyle değiştirilebilir
  const clientId = Deno.env.get("GOOGLE_WEB_CLIENT_ID") || WEB_CLIENT_ID;

  let body: { id_token?: string; action?: string; id?: number; key?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Geçersiz istek" }, 400);
  }
  const token = body.id_token ?? "";
  if (token.length < 100) return json({ error: "Google jetonu eksik" }, 400);

  // Google'ın jeton doğrulama ucu imzayı ve süreyi kontrol eder; biz alıcıyı ve vereni denetleriz
  const r = await fetch("https://oauth2.googleapis.com/tokeninfo?id_token=" + encodeURIComponent(token));
  if (!r.ok) return json({ error: "Google doğrulaması başarısız" }, 401);
  const t = await r.json();
  if (t.aud !== clientId) return json({ error: "Jeton bu uygulama için değil" }, 401);
  if (t.iss !== "accounts.google.com" && t.iss !== "https://accounts.google.com") return json({ error: "Geçersiz jeton" }, 401);
  if (Number(t.exp) * 1000 < Date.now()) return json({ error: "Jetonun süresi dolmuş" }, 401);
  if (String(t.email_verified) !== "true") return json({ error: "Google e-postası doğrulanmamış" }, 401);

  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = body.action === "link"
    ? await sb.rpc("zx_google_link", { p_id: body.id, p_key: body.key, p_sub: t.sub, p_email: t.email })
    : await sb.rpc("zx_google_login", { p_sub: t.sub, p_email: t.email, p_name: t.name ?? t.given_name ?? "" });
  if (error) return json({ error: error.message }, 400);
  return json(data);
});
