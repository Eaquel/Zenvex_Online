// Zenvex Online herkese açık yasal sayfalar. Metinlerin TEK kaynağı content/legal.json: uygulama aynı dosyayı paketler,
// böylece oyun içi ve web metinleri birebir aynıdır. Sayfalar: ?p=home | privacy | terms | presale | sales | content |
// imprint | promo. Supabase varsayılan alan adında HTML'i düz metne çevirdiği için sayfalar okunaklı düz metin olarak sunulur.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import legal from "./content/legal.json" with { type: "json" };

const BASE = "https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal";

type Page = { id: string; title: string; paras: string[] };
const data = legal as { version: number; updated: string; operator: Record<string, string>; notice: string[]; pages: Page[] };

function fill(s: string): string {
  return s.replace(/\{\{([A-Z_]+)\}\}/g, (_, k: string) => {
    if (k === "TARIH") return data.updated;
    if (k === "URL") return BASE;
    return data.operator[k] ?? "";
  });
}

function render(p: Page): string {
  const title = `ZENVEX ONLINE — ${p.title.toLocaleUpperCase("tr")}`;
  const out = [title, "=".repeat(title.length), ""];
  for (const raw of p.paras) {
    const s = fill(raw);
    // "1. Başlık" satırları büyük harfle ve öncesinde boşlukla
    if (/^\d+\.\s/.test(s) && s.length < 60) out.push("", s.toLocaleUpperCase("tr"));
    else out.push(s.startsWith("-") ? "  " + s : s);
  }
  out.push("", `Metin sürümü: ${data.version}`, "");
  return out.join("\n");
}

function home(): string {
  const lines = [
    "ZENVEX ONLINE",
    "=============",
    "",
    "Zenvex Online; kıyamet sonrası bir şehirde geçen, çevrimiçi bir 2.5D piksel sanat zombi hayatta kalma oyunudur.",
    "Kapüşonlu savaşçıyla zombilerle savaş, malzeme topla, üret, köpeğini eğit ve diğer oyuncularla birlikte hayatta kal.",
    "",
    "YASAL METİNLER",
  ];
  for (const p of data.pages) lines.push(`- ${p.title}: ${BASE}?p=${p.id}`);
  lines.push("", "İLETİŞİM", fill("{{UNVAN}} · {{ADRES}} · {{EPOSTA}}"), "");
  return lines.join("\n");
}

Deno.serve((req: Request) => {
  const url = new URL(req.url);
  const id = url.searchParams.get("p") ?? "home";
  // uygulama ve araçlar için ham veri: ?format=json
  if (url.searchParams.get("format") === "json") {
    return new Response(JSON.stringify(data), {
      headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=600" },
    });
  }
  const page = data.pages.find((p) => p.id === id);
  return new Response(page ? render(page) : home(), {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=600" },
  });
});
