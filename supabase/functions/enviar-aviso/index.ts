// Edge Function: enviar-aviso
// La llama un trigger de la base cada vez que se inserta una fila en `notificacion`
// (alerta nueva, escalamiento o repetición). Busca el correo del contacto y le
// envía el aviso por correo con Resend.
//
// Secretos: ESCALAR_TOKEN (el mismo de escalar-alertas) y RESEND_API_KEY.
// Despliegue: supabase functions deploy enviar-aviso --no-verify-jwt

import { createClient } from "npm:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);
const TOKEN = Deno.env.get("ESCALAR_TOKEN") ?? "";
const RESEND = Deno.env.get("RESEND_API_KEY") ?? "";
const REMITENTE = Deno.env.get("CORREO_REMITENTE") ?? "Torre de agua <onboarding@resend.dev>";

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

const TITULO: Record<string, string> = {
  CRITICA: "CRÍTICO",
  ATENCION: "Atención",
  LLENA: "Torre llena",
};
const INSTRUCCION: Record<string, string> = {
  CRITICA: "Encienda el bombeo ahora.",
  ATENCION: "Prepare el bombeo: el nivel está bajando.",
  LLENA: "Detenga el bombeo.",
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, error: "método no permitido" }, 405);
  if (!TOKEN) return json({ ok: false, error: "falta ESCALAR_TOKEN" }, 500);
  if (!tokenValido(req.headers.get("x-cron-token") ?? "")) return json({ ok: false, error: "no autorizado" }, 401);
  if (!RESEND) return json({ ok: false, error: "falta RESEND_API_KEY" }, 500);

  let id: number;
  try { id = Number((await req.json()).id_notificacion); } catch { return json({ ok: false, error: "JSON inválido" }, 400); }
  if (!Number.isInteger(id)) return json({ ok: false, error: "id_notificacion inválido" }, 400);

  const { data: n } = await supabase.from("notificacion")
    .select("id_notificacion, id_perfil, orden, alerta(tipo, abierta_en, lectura(nivel_pct, volumen_l))")
    .eq("id_notificacion", id).maybeSingle();
  if (!n) return json({ ok: false, error: "notificación no existe" }, 404);

  const a = Array.isArray(n.alerta) ? n.alerta[0] : n.alerta;
  const l = a ? (Array.isArray(a.lectura) ? a.lectura[0] : a.lectura) : null;
  const tipo = String(a?.tipo ?? "CRITICA");
  const nivel = l?.nivel_pct != null ? Math.round(Number(l.nivel_pct)) : null;

  const { data: u } = await supabase.auth.admin.getUserById(n.id_perfil);
  const correo = u?.user?.email;
  if (!correo) return json({ ok: false, error: "el contacto no tiene correo" }, 404);

  const asunto = `${TITULO[tipo] ?? tipo}: torre de agua${nivel != null ? ` en ${nivel} %` : ""}`;
  const hora = a?.abierta_en
    ? new Date(a.abierta_en).toLocaleString("es-CL", { timeZone: "America/Santiago" })
    : "";
  const texto =
    `${asunto}\n\n${INSTRUCCION[tipo] ?? ""}\n\n` +
    (hora ? `Alerta abierta: ${hora}\n` : "") +
    (n.orden > 1 ? `Aviso escalado (contacto ${n.orden}): nadie ha confirmado.\n` : "") +
    `\nAbre la app y pulsa «Recibido» para confirmar.\n— Fundo San Carlos`;

  const r = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { "Authorization": `Bearer ${RESEND}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: REMITENTE, to: [correo], subject: asunto, text: texto }),
  });
  if (!r.ok) {
    const detalle = (await r.text()).slice(0, 300);
    return json({ ok: false, error: "el servicio de correo rechazó el envío", detalle }, 502);
  }
  return json({ ok: true, enviado_a: correo }, 200);
});
