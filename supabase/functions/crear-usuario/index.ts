// Edge Function: crear-usuario
// Permite que un ADMINISTRADOR cree cuentas desde la app. Usa la clave de servicio
// (que nunca sale de Supabase) y comprueba antes que quien llama sea administrador.
//
// Despliegue: supabase functions deploy crear-usuario --no-verify-jwt
// (la verificación del token se hace aquí dentro).

import { createClient } from "npm:@supabase/supabase-js@2";

const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...cors },
  });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  // 1. ¿Quién llama?
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return json({ error: "Falta la sesión" }, 401);
  const { data: u, error: eu } = await admin.auth.getUser(token);
  if (eu || !u.user) return json({ error: "Sesión inválida o vencida" }, 401);

  const { data: quien } = await admin
    .from("perfil").select("id_rol, activo").eq("id_perfil", u.user.id).maybeSingle();
  if (!quien || !quien.activo || quien.id_rol !== 1) {
    return json({ error: "Solo un administrador puede crear usuarios" }, 403);
  }

  // 2. Datos del nuevo usuario
  let b: Record<string, unknown>;
  try { b = await req.json(); } catch { return json({ error: "JSON inválido" }, 400); }

  const email = String(b.email ?? "").trim().toLowerCase();
  const password = String(b.password ?? "");
  const nombre = String(b.nombre ?? "").trim();
  const cargo = String(b.cargo ?? "").trim();
  const idRol = Number(b.id_rol);

  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return json({ error: "Correo no válido" }, 400);
  if (password.length < 12) return json({ error: "La contraseña debe tener al menos 12 caracteres" }, 400);
  if (nombre.length < 2 || nombre.length > 80) return json({ error: "Escribe el nombre (2 a 80 letras)" }, 400);
  if (cargo.length > 80) return json({ error: "El cargo es demasiado largo" }, 400);
  if (![1, 2, 3, 4].includes(idRol)) return json({ error: "Rol no válido" }, 400);

  // 3. Crear la cuenta (el trigger crea el perfil con rol Reemplazante)
  const { data: nuevo, error: ec } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { nombre },
  });
  if (ec || !nuevo.user) {
    const msg = (ec?.message ?? "").toLowerCase();
    if (msg.includes("already") || msg.includes("registered")) {
      return json({ error: "Ese correo ya tiene una cuenta" }, 409);
    }
    return json({ error: "No se pudo crear la cuenta" }, 400);
  }

  // 4. Dejar nombre, cargo y rol definitivos
  const { error: ep } = await admin.from("perfil")
    .update({ nombre, cargo: cargo || null, id_rol: idRol, activo: true })
    .eq("id_perfil", nuevo.user.id);
  if (ep) {
    await admin.auth.admin.deleteUser(nuevo.user.id); // no dejar cuentas a medias
    return json({ error: "No se pudo guardar el perfil" }, 500);
  }

  await admin.from("bitacora").insert({
    id_perfil: u.user.id,
    accion: "CREA_USUARIO",
    entidad: "perfil",
    detalle: { id_perfil: nuevo.user.id, email, id_rol: idRol },
  });

  return json({ ok: true, id: nuevo.user.id }, 200);
});
