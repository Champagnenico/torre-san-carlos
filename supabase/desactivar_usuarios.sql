-- Desactivar y reactivar usuarios (HU-07)
-- El Administrador cambia perfil.activo desde la app (la política admin_edita_perfiles ya lo permite).
-- Este script agrega dos protecciones:
--   1. Nadie puede desactivar su propia cuenta.
--   2. Cada cambio de activo queda en la bitácora (DESACTIVA_USUARIO / ACTIVA_USUARIO).
-- Una cuenta desactivada deja de pasar rol_actual(), por lo que RLS le oculta todos los datos.

create or replace function fn_proteger_perfil() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if old.activo and not new.activo and new.id_perfil = auth.uid() then
    raise exception 'No puedes desactivar tu propia cuenta';
  end if;
  return new;
end $$;

create or replace function fn_bitacora_perfil_activo() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.bitacora (id_perfil, accion, entidad, detalle)
  values (auth.uid(),
          case when new.activo then 'ACTIVA_USUARIO' else 'DESACTIVA_USUARIO' end,
          'perfil',
          jsonb_build_object('id_perfil', new.id_perfil, 'nombre', new.nombre, 'id_rol', new.id_rol));
  return new;
end $$;

drop trigger if exists trg_proteger_perfil on public.perfil;
create trigger trg_proteger_perfil
  before update of activo on public.perfil
  for each row when (old.activo is distinct from new.activo)
  execute function fn_proteger_perfil();

drop trigger if exists trg_bitacora_perfil_activo on public.perfil;
create trigger trg_bitacora_perfil_activo
  after update of activo on public.perfil
  for each row when (old.activo is distinct from new.activo)
  execute function fn_bitacora_perfil_activo();
