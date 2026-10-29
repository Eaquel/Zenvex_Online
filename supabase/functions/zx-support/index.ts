// Destek fotoğrafları (gizli "support" kovası). İki işlem:
//   { "op": "photo", "id", "key", "ticket", "idx", "ext": "jpg|png|webp", "data": "<base64>" }
//     → zx_support_photo_path yolu onaylar (talep sahibi, sıra, süre), fotoğraf servis anahtarıyla yüklenir. En çok 10 MB.
//   { "op": "sign", "id", "key", "ticket" }
//     → yönetici için talebin fotoğraflarına 1 saatlik imzalı bağlantılar.
//   { "op": "purge" } (yalnız servis anahtarıyla: Authorization: Bearer <service_role>)
//     → saklama süresi (2 yıl) dolmuş ya da hesabı anonimleşmiş taleplerin fotoğraflarını depodan siler (KVKK).
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const MAX_BYTES = 10 * 1024 * 1024;
const MIME: Record<string, string> = { jpg: "image/jpeg", png: "image/png", webp: "image/webp" };
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

function fromB64(s: string): Uint8Array {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Yalnızca POST" }, 405);
  let b: { op?: string; id?: number; key?: string; ticket?: number; idx?: number; ext?: string; data?: string };
  try {
    b = await req.json();
  } catch {
    return json({ error: "Geçersiz istek" }, 400);
  }
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, service);

  if (b.op === "purge") {
    if (req.headers.get("Authorization") !== `Bearer ${service}`) return json({ error: "Yetkisiz" }, 401);
    const { data: list, error } = await sb.rpc("zx_support_purge_list");
    if (error) return json({ error: error.message }, 500);
    let removed = 0;
    for (const t of (list ?? []) as { ticket: number; paths: string[] }[]) {
      const rm = await sb.storage.from("support").remove(t.paths);
      if (rm.error) continue;
      removed += t.paths.length;
      await sb.rpc("zx_support_purge_done", { p_ticket: t.ticket });
    }
    return json({ ok: true, removed });
  }

  if (!b.id || !b.key || !b.ticket) return json({ error: "Eksik bilgi" }, 400);

  if (b.op === "photo") {
    const ext = (b.ext ?? "").toLowerCase();
    if (!MIME[ext] || typeof b.data !== "string") return json({ error: "Desteklenmeyen biçim" }, 400);
    // base64 boyu kabaca 4/3: çözmeden önce sınır
    if (b.data.length > Math.ceil(MAX_BYTES * 4 / 3) + 8) return json({ error: "Fotoğraf 10 MB'den büyük" }, 413);
    let bytes: Uint8Array;
    try {
      bytes = fromB64(b.data);
    } catch {
      return json({ error: "Bozuk fotoğraf" }, 400);
    }
    if (bytes.length > MAX_BYTES) return json({ error: "Fotoğraf 10 MB'den büyük" }, 413);
    const { data: path, error } = await sb.rpc("zx_support_photo_path", {
      p_id: b.id, p_key: b.key, p_ticket: b.ticket, p_idx: b.idx ?? -1, p_ext: ext,
    });
    if (error || !path) return json({ error: error?.message ?? "Yol onaylanmadı" }, 400);
    const up = await sb.storage.from("support").upload(path as string, bytes, { contentType: MIME[ext], upsert: false });
    if (up.error) return json({ error: up.error.message }, 500);
    return json({ ok: true, path });
  }

  if (b.op === "sign") {
    const { data: paths, error } = await sb.rpc("zx_admin_support_paths", { p_id: b.id, p_key: b.key, p_ticket: b.ticket });
    if (error) return json({ error: error.message }, 403);
    const list = (paths as string[] | null) ?? [];
    if (list.length === 0) return json({ urls: [] });
    const s = await sb.storage.from("support").createSignedUrls(list, 3600);
    if (s.error) return json({ error: s.error.message }, 500);
    return json({ urls: (s.data ?? []).map((x) => x.signedUrl).filter(Boolean) });
  }

  return json({ error: "Bilinmeyen işlem" }, 400);
});
