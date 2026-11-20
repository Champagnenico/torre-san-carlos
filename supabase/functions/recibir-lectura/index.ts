// Edge Function: recibir-lectura
// Recibe la medición del nodo ESP32, la valida, calcula nivel y litros con la
// calibración guardada en la base, guarda la lectura (con el mensaje crudo en
// jsonb) y abre/cierra alertas. Versión simplificada del dominio del informe.
//
// Despliegue:
//   supabase secrets set TOKEN_NODO_TORRE=<token largo y secreto>
//   supabase functions deploy recibir-lectura --no-verify-jwt
//
// (SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY ya existen dentro de las Edge Functions.)

import { createClient } from "npm:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);
const TOKEN = Deno.env.get("TOKEN_NODO_TORRE") ?? "";

const SALTO_MAX_PCT_15MIN = 15; // salto de nivel imposible en 15 min => lectura dudosa
const SEVERIDAD: Record<string, number> = { ATENCION: 1, CRITICA: 2 };

const json = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

// Comparación en tiempo constante del token
function tokenValido(recibido: string): boolean {
  const a = new TextEncoder().encode(recibido);
  const b = new TextEncoder().encode(TOKEN);
  let d = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) d |= (a[i] ?? 0) ^ (b[i] ?? 0);
  return TOKEN.length > 0 && d === 0;
}

async function rechazar(id_nodo: number | null, motivo: string, mensaje: unknown, status = 400) {
  await supabase.from("evento_dispositivo").insert({
    id_nodo, tipo: "RECHAZO", detalle: { motivo, mensaje },
  });
  return json({ ok: false, error: motivo }, status);
}

function cumple(op: string, nivel: number, valor: number): boolean {
  switch (op) {
    case "<": return nivel < valor;
    case "<=": return nivel <= valor;
    case ">": return nivel > valor;
    case ">=": return nivel >= valor;
    default: return false;
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "método no permitido" }, 405);
  if (!tokenValido(req.headers.get("x-token") ?? "")) return json({ error: "no autorizado" }, 401);

  // 1. Validar el mensaje
  let msg: any;
  try { msg = await req.json(); } catch { return rechazar(null, "JSON inválido", null); }

  const entero = (v: unknown, min: number, max: number) =>
    Number.isInteger(v) && (v as number) >= min && (v as number) <= max;

  if (typeof msg?.dev_eui !== "string" || msg.dev_eui.length < 1 || msg.dev_eui.length > 40)
    return rechazar(null, "dev_eui inválido", msg);

  const { data: nodo } = await supabase
    .from("nodo").select("id_nodo, id_deposito, activo").eq("dev_eui", msg.dev_eui).maybeSingle();
  if (!nodo || !nodo.activo) return rechazar(null, "nodo no registrado", msg, 403);

  if (!entero(msg.seq, 0, 65535)) return rechazar(nodo.id_nodo, "seq fuera de rango", msg);
  if (!entero(msg.distancia_mm, 20, 4000)) return rechazar(nodo.id_nodo, "distancia fuera del rango del sensor", msg);
  const edadMin = msg.edad_min === undefined ? 0 : msg.edad_min;
  if (!entero(edadMin, 0, 10080)) return rechazar(nodo.id_nodo, "edad_min inválida", msg);

  // 2. Calibración y depósito
  const [{ data: cal }, { data: dep }] = await Promise.all([
    supabase.from("calibracion").select("dist_vacia_mm, dist_llena_mm")
      .eq("id_nodo", nodo.id_nodo).order("vigente_desde", { ascending: false }).limit(1).maybeSingle(),
    supabase.from("deposito").select("capacidad_l").eq("id_deposito", nodo.id_deposito).single(),
  ]);
  if (!cal || !dep) return rechazar(nodo.id_nodo, "sin calibración o depósito", msg, 500);

  // 3. Nivel y litros
  const bruto = ((cal.dist_vacia_mm - msg.distancia_mm) / (cal.dist_vacia_mm - cal.dist_llena_mm)) * 100;
  const nivel = Math.round(Math.min(100, Math.max(0, bruto)) * 100) / 100;
  const volumen = Math.round((nivel / 100) * dep.capacidad_l);
  const ts = new Date(Date.now() - edadMin * 60_000);

  // 4. Validez: salto imposible respecto de la última lectura válida
  let validez = "VALIDO";
  const { data: prev } = await supabase.from("lectura").select("nivel_pct, ts_lectura")
    .eq("id_nodo", nodo.id_nodo).eq("validez", "VALIDO").lt("ts_lectura", ts.toISOString())
    .order("ts_lectura", { ascending: false }).limit(1).maybeSingle();
  if (prev) {
    const min = (ts.getTime() - new Date(prev.ts_lectura).getTime()) / 60_000;
    const permitido = SALTO_MAX_PCT_15MIN * Math.max(1, min / 15);
    if (Math.abs(nivel - Number(prev.nivel_pct)) > permitido) validez = "DUDOSO";
  }

  // 5. Estado según los umbrales (solo si la lectura es válida)
  const { data: umbrales } = await supabase.from("umbral").select("tipo, operador, valor_pct, histeresis_pct")
    .eq("id_deposito", nodo.id_deposito);
  const u = Object.fromEntries((umbrales ?? []).map((x) => [x.tipo, x]));

  let estado: string | null = null;
  if (validez === "VALIDO") {
    estado = "NORMAL";
    if (u.LLENA && cumple(u.LLENA.operador, nivel, Number(u.LLENA.valor_pct))) estado = "LLENA";
    else if (u.CRITICA && cumple(u.CRITICA.operador, nivel, Number(u.CRITICA.valor_pct))) estado = "CRITICA";
    else if (u.ATENCION && cumple(u.ATENCION.operador, nivel, Number(u.ATENCION.valor_pct))) estado = "ATENCION";
  }

  // 6. Guardar la lectura (el mensaje crudo queda en jsonb)
  const { data: lectura, error: errIns } = await supabase.from("lectura").insert({
    id_nodo: nodo.id_nodo, ts_lectura: ts.toISOString(), seq: msg.seq,
    distancia_mm: msg.distancia_mm, nivel_pct: nivel, volumen_l: volumen,
    validez, estado,
    activos: { estado },
    mensaje: { ...msg, recibido_en: new Date().toISOString() },
  }).select("id_lectura").single();

  if (errIns) {
    if (errIns.code === "23505") return json({ ok: true, duplicada: true }, 200); // misma marca de tiempo
    return json({ ok: false, error: "no se pudo guardar" }, 500);
  }
  if (validez === "DUDOSO") return json({ ok: true, validez, nivel_pct: nivel }, 201);

  // 7. Alertas: abrir si empeora y cerrar al volver a la zona normal (con histéresis)
  const { data: abiertas } = await supabase.from("alerta").select("id_alerta, tipo")
    .eq("id_deposito", nodo.id_deposito).is("cerrada_en", null);
  const abier = abiertas ?? [];

  const ahora = new Date().toISOString();
  const cerrar = async (ids: number[]) => {
    if (ids.length) await supabase.from("alerta").update({ cerrada_en: ahora }).in("id_alerta", ids);
  };

  const hAt = Number(u.ATENCION?.histeresis_pct ?? 2);
  if (u.ATENCION && nivel >= Number(u.ATENCION.valor_pct) + hAt)
    await cerrar(abier.filter((a) => a.tipo !== "LLENA").map((a) => a.id_alerta));
  const hLl = Number(u.LLENA?.histeresis_pct ?? 2);
  if (u.LLENA && nivel <= Number(u.LLENA.valor_pct) - hLl)
    await cerrar(abier.filter((a) => a.tipo === "LLENA").map((a) => a.id_alerta));

  if (estado && estado !== "NORMAL") {
    const yaAbierta = abier.some((a) => a.tipo === estado);
    const peorAbierta = Math.max(0, ...abier.map((a) => SEVERIDAD[a.tipo] ?? 0));
    const empeora = estado === "LLENA" || (SEVERIDAD[estado] ?? 0) > peorAbierta;
    if (!yaAbierta && empeora) {
      const { data: alerta } = await supabase.from("alerta").insert({
        id_deposito: nodo.id_deposito, id_lectura: lectura.id_lectura, tipo: estado,
      }).select("id_alerta").single();
      const { data: contacto } = await supabase.from("contacto_escalamiento").select("id_perfil")
        .eq("id_deposito", nodo.id_deposito).eq("orden", 1).maybeSingle();
      if (alerta && contacto)
        await supabase.from("notificacion").insert({
          id_alerta: alerta.id_alerta, id_perfil: contacto.id_perfil, orden: 1,
        });
    }
  }

  return json({ ok: true, validez, nivel_pct: nivel, volumen_l: volumen, estado }, 201);
});
