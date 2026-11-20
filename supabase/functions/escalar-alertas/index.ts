// Edge Function: escalar-alertas
// La llama pg_cron cada minuto. Para cada alerta abierta y sin «Recibido»:
//   - a los 10 min sin confirmar avisa al siguiente contacto (orden 2, luego 3);
//   - si ya no quedan contactos y la alerta es CRITICA, la repite cada 15 min.
// «Recibido» (una fila en confirmacion) detiene el escalamiento.
//
// Despliegue:
//   supabase secrets set ESCALAR_TOKEN=<token largo y secreto>
//   supabase functions deploy escalar-alertas --no-verify-jwt

import { createClient } from "npm:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);
const TOKEN = Deno.env.get("ESCALAR_TOKEN") ?? "";

const MIN_ESCALAR = 10;  // minutos sin confirmar => siguiente contacto
const MIN_REPETIR = 15;  // minutos entre repeticiones de una alerta CRITICA

const json = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function tokenValido(recibido: string): boolean {
  const a = new TextEncoder().encode(recibido);
  const b = new TextEncoder().encode(TOKEN);
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, error: "método no permitido" }, 405);
  if (!TOKEN) return json({ ok: false, error: "falta el secreto ESCALAR_TOKEN" }, 500);
  if (!tokenValido(req.headers.get("x-cron-token") ?? "")) return json({ ok: false, error: "no autorizado" }, 401);

  const { data: abiertas, error } = await supabase
    .from("alerta")
    .select("id_alerta, id_deposito, tipo, contacto_actual, ultimo_aviso_en, confirmacion(id_confirmacion)")
    .is("cerrada_en", null);
  if (error) return json({ ok: false, error: "no se pudieron leer las alertas" }, 500);

  const ahora = Date.now();
  let escaladas = 0;
  let repetidas = 0;

  for (const a of abiertas ?? []) {
    const conf = a.confirmacion as unknown;
    const confirmada = Array.isArray(conf) ? conf.length > 0 : !!conf;
    if (confirmada) continue;

    const min = (ahora - new Date(a.ultimo_aviso_en).getTime()) / 60000;
    const sig = Number(a.contacto_actual) + 1;

    // ¿hay un contacto siguiente?
    let siguiente: { id_perfil: string } | null = null;
    if (sig <= 3 && min >= MIN_ESCALAR) {
      const { data } = await supabase.from("contacto_escalamiento").select("id_perfil")
        .eq("id_deposito", a.id_deposito).eq("orden", sig).maybeSingle();
      siguiente = data;
    }

    if (siguiente) {
      const { error: e1 } = await supabase.from("notificacion").insert({
        id_alerta: a.id_alerta, id_perfil: siguiente.id_perfil, orden: sig,
      });
      if (!e1) {
        await supabase.from("alerta").update({
          contacto_actual: sig, ultimo_aviso_en: new Date().toISOString(),
        }).eq("id_alerta", a.id_alerta);
        escaladas++;
      }
    } else if (a.tipo === "CRITICA" && min >= MIN_REPETIR) {
      // sin más contactos a quienes escalar: repetir al contacto actual
      const { data: actual } = await supabase.from("contacto_escalamiento").select("id_perfil")
        .eq("id_deposito", a.id_deposito).eq("orden", a.contacto_actual).maybeSingle();
      if (actual) {
        const { error: e2 } = await supabase.from("notificacion").insert({
          id_alerta: a.id_alerta, id_perfil: actual.id_perfil, orden: a.contacto_actual,
        });
        if (!e2) {
          await supabase.from("alerta").update({ ultimo_aviso_en: new Date().toISOString() })
            .eq("id_alerta", a.id_alerta);
          repetidas++;
        }
      }
    }
  }

  return json({ ok: true, revisadas: (abiertas ?? []).length, escaladas, repetidas }, 200);
});
