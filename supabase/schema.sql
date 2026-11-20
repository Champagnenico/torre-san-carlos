-- =====================================================================
-- Proyecto Integrado (TIHI43) - Nivel de la torre de agua
-- Fundo Agrícola San Carlos, La Unión
-- Esquema de Supabase (PostgreSQL): 14 tablas, 4 triggers, RLS y Realtime
--
-- Cómo usarlo: Supabase -> SQL Editor -> pegar todo y ejecutar (Run).
-- Después ejecutar seed.sql.
-- Se puede volver a ejecutar: borra y recrea las tablas del proyecto.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Limpieza (solo objetos de este proyecto)
-- ---------------------------------------------------------------------
drop trigger if exists trg_nuevo_usuario on auth.users;

drop table if exists bitacora            cascade;
drop table if exists cambio_umbral       cascade;
drop table if exists confirmacion        cascade;
drop table if exists notificacion        cascade;
drop table if exists alerta              cascade;
drop table if exists contacto_escalamiento cascade;
drop table if exists umbral              cascade;
drop table if exists evento_dispositivo  cascade;
drop table if exists lectura             cascade;
drop table if exists calibracion         cascade;
drop table if exists nodo                cascade;
drop table if exists deposito            cascade;
drop table if exists perfil              cascade;
drop table if exists rol                 cascade;

drop function if exists rol_actual()                 cascade;
drop function if exists fn_bitacora_inmutable()      cascade;
drop function if exists fn_crear_perfil()            cascade;
drop function if exists fn_registrar_cambio_umbral() cascade;
drop function if exists fn_registrar_confirmacion()  cascade;

-- ---------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------

-- 1. rol
create table rol (
  id_rol      smallint primary key,
  nombre      varchar(20) not null unique
              check (nombre in ('ADMINISTRADOR','OPERADOR','MANTENCION','REEMPLAZANTE')),
  descripcion varchar(120) not null
);

-- 2. perfil (1 a 1 con auth.users)
create table perfil (
  id_perfil     uuid primary key references auth.users (id) on delete cascade,
  nombre        varchar(80) not null,
  cargo         varchar(80),
  id_rol        smallint not null default 4 references rol (id_rol),
  activo        boolean not null default true,
  vigente_hasta timestamptz,                      -- fecha de término (reemplazantes)
  creado_en     timestamptz not null default now()
);

-- 3. deposito (la torre; el pozo de purines queda previsto)
create table deposito (
  id_deposito smallint generated always as identity primary key,
  nombre      varchar(80) not null unique,
  tipo        varchar(20) not null default 'TORRE'
              check (tipo in ('TORRE','POZO_PURINES')),
  capacidad_l integer not null check (capacidad_l > 0),
  creado_en   timestamptz not null default now()
);

-- 4. nodo (equipo ESP32 instalado)
create table nodo (
  id_nodo     integer generated always as identity primary key,
  id_deposito smallint not null references deposito (id_deposito),
  dev_eui     varchar(40) not null unique,        -- ej. nodo-torre-01
  descripcion varchar(120),
  activo      boolean not null default true,
  creado_en   timestamptz not null default now()
);

-- 5. calibracion (distancia con la torre vacía y llena, medidas en terreno)
create table calibracion (
  id_calibracion integer generated always as identity primary key,
  id_nodo        integer not null references nodo (id_nodo),
  dist_vacia_mm  integer not null check (dist_vacia_mm between 20 and 4000),
  dist_llena_mm  integer not null check (dist_llena_mm between 20 and 4000),
  vigente_desde  timestamptz not null default now(),
  check (dist_vacia_mm > dist_llena_mm),
  unique (id_nodo, vigente_desde)
);

-- 6. lectura (cada medición)
create table lectura (
  id_lectura   integer generated always as identity primary key,
  id_nodo      integer not null references nodo (id_nodo),
  ts_lectura   timestamptz not null,
  seq          integer not null check (seq between 0 and 65535),
  distancia_mm integer not null check (distancia_mm between 20 and 4000),
  nivel_pct    numeric(5,2) not null check (nivel_pct between 0 and 100),
  volumen_l    integer not null check (volumen_l >= 0),
  validez      varchar(10) not null check (validez in ('VALIDO','DUDOSO')),
  estado       varchar(10) check (estado in ('NORMAL','LLENA','ATENCION','CRITICA')),
  activos      jsonb,                             -- umbrales activos tras evaluar (histéresis)
  mensaje      jsonb,                             -- mensaje crudo del nodo, tal como llegó
  unique (id_nodo, ts_lectura)
);
create index idx_lectura_nodo_ts on lectura (id_nodo, ts_lectura desc);

-- 7. umbral (límites por depósito)
create table umbral (
  id_umbral     integer generated always as identity primary key,
  id_deposito   smallint not null references deposito (id_deposito),
  tipo          varchar(10) not null check (tipo in ('LLENA','ATENCION','CRITICA')),
  operador      varchar(2)  not null check (operador in ('<','<=','>','>=')),
  valor_pct     numeric(5,2) not null check (valor_pct between 0 and 100),
  histeresis_pct numeric(4,2) not null default 2 check (histeresis_pct between 0 and 20),
  unique (id_deposito, tipo)
);

-- 8. contacto_escalamiento (quién avisa por cada depósito y en qué orden)
create table contacto_escalamiento (
  id_deposito smallint not null references deposito (id_deposito),
  id_perfil   uuid     not null references perfil (id_perfil) on delete cascade,
  orden       smallint not null check (orden between 1 and 3),
  primary key (id_deposito, id_perfil),
  unique (id_deposito, orden)
);

-- 9. alerta
create table alerta (
  id_alerta      integer generated always as identity primary key,
  id_deposito    smallint not null references deposito (id_deposito),
  id_lectura     integer  not null references lectura (id_lectura),
  tipo           varchar(10) not null check (tipo in ('LLENA','ATENCION','CRITICA')),
  abierta_en     timestamptz not null default now(),
  cerrada_en     timestamptz,
  contacto_actual smallint not null default 1 check (contacto_actual between 1 and 3),
  ultimo_aviso_en timestamptz not null default now()
);
create index idx_alerta_abiertas on alerta (id_deposito) where cerrada_en is null;

-- 10. notificacion (aviso a cada contacto)
create table notificacion (
  id_notificacion integer generated always as identity primary key,
  id_alerta       integer not null references alerta (id_alerta),
  id_perfil       uuid    not null references perfil (id_perfil),
  orden           smallint not null check (orden between 1 and 3),
  enviada_en      timestamptz not null default now(),
  leida_en        timestamptz
);
create index idx_notificacion_perfil on notificacion (id_perfil, enviada_en desc);

-- 11. confirmacion (una por alerta: «Recibido»)
create table confirmacion (
  id_confirmacion integer generated always as identity primary key,
  id_alerta       integer not null unique references alerta (id_alerta),
  id_perfil       uuid    not null references perfil (id_perfil),
  confirmada_en   timestamptz not null default now()
);

-- 12. cambio_umbral (quién cambió qué límite)
create table cambio_umbral (
  id_cambio      integer generated always as identity primary key,
  id_umbral      integer not null references umbral (id_umbral),
  id_perfil      uuid references perfil (id_perfil),
  valor_anterior numeric(5,2) not null,
  valor_nuevo    numeric(5,2) not null,
  cambiado_en    timestamptz not null default now()
);

-- 13. evento_dispositivo (rechazos, fallas, calibraciones)
create table evento_dispositivo (
  id_evento    integer generated always as identity primary key,
  id_nodo      integer references nodo (id_nodo),   -- null si el nodo no está registrado
  tipo         varchar(20) not null
               check (tipo in ('RECHAZO','FALLA_SENSOR','PERDIDA_WIFI','CALIBRACION')),
  detalle      jsonb not null,
  ocurrido_en  timestamptz not null default now()
);

-- 14. bitacora (registro inalterable)
create table bitacora (
  id_bitacora integer generated always as identity primary key,
  id_perfil   uuid references perfil (id_perfil) on delete set null,
  accion      varchar(40) not null,
  entidad     varchar(40) not null,
  detalle     jsonb,
  creado_en   timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- 2. Funciones y triggers (4)
-- ---------------------------------------------------------------------

-- Trigger 1: la bitácora no se puede modificar ni borrar
create function fn_bitacora_inmutable() returns trigger
language plpgsql as $$
begin
  raise exception 'La bitácora no se puede modificar ni borrar';
end;
$$;

create trigger trg_bitacora_inmutable
  before update or delete on bitacora
  for each row execute function fn_bitacora_inmutable();

-- Trigger 2: cada cuenta nueva de Auth recibe su perfil con el rol más restringido
create function fn_crear_perfil() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.perfil (id_perfil, nombre, id_rol)
  values (
    new.id,
    coalesce(nullif(new.raw_user_meta_data ->> 'nombre', ''), split_part(new.email, '@', 1)),
    4                                              -- REEMPLAZANTE
  );
  return new;
end;
$$;

create trigger trg_nuevo_usuario
  after insert on auth.users
  for each row execute function fn_crear_perfil();

-- Trigger 3: cada cambio de un umbral queda registrado con quién lo hizo
create function fn_registrar_cambio_umbral() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.valor_pct is distinct from old.valor_pct then
    insert into public.cambio_umbral (id_umbral, id_perfil, valor_anterior, valor_nuevo)
    values (new.id_umbral, auth.uid(), old.valor_pct, new.valor_pct);
    insert into public.bitacora (id_perfil, accion, entidad, detalle)
    values (auth.uid(), 'CAMBIO_UMBRAL', 'umbral',
            jsonb_build_object('id_umbral', new.id_umbral, 'tipo', new.tipo,
                               'antes', old.valor_pct, 'despues', new.valor_pct));
  end if;
  return new;
end;
$$;

create trigger trg_cambio_umbral
  after update on umbral
  for each row execute function fn_registrar_cambio_umbral();

-- Trigger 4: cada confirmación queda registrada con quién la hizo
create function fn_registrar_confirmacion() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.bitacora (id_perfil, accion, entidad, detalle)
  values (new.id_perfil, 'CONFIRMA_ALERTA', 'alerta',
          jsonb_build_object('id_alerta', new.id_alerta));
  return new;
end;
$$;

create trigger trg_confirmacion
  after insert on confirmacion
  for each row execute function fn_registrar_confirmacion();

-- ---------------------------------------------------------------------
-- 3. Seguridad por filas (RLS) en las 14 tablas
-- ---------------------------------------------------------------------

-- Rol de la cuenta actual; ignora cuentas inactivas o vencidas (devuelve null)
create function rol_actual() returns text
language sql stable security definer set search_path = '' as $$
  select r.nombre
  from public.perfil p
  join public.rol r on r.id_rol = p.id_rol
  where p.id_perfil = auth.uid()
    and p.activo
    and (p.vigente_hasta is null or p.vigente_hasta > now());
$$;

revoke all on function rol_actual() from public, anon;
grant execute on function rol_actual() to authenticated, service_role;

alter table rol                   enable row level security;
alter table perfil                enable row level security;
alter table deposito              enable row level security;
alter table nodo                  enable row level security;
alter table calibracion           enable row level security;
alter table lectura               enable row level security;
alter table umbral                enable row level security;
alter table contacto_escalamiento enable row level security;
alter table alerta                enable row level security;
alter table notificacion          enable row level security;
alter table confirmacion          enable row level security;
alter table cambio_umbral         enable row level security;
alter table evento_dispositivo    enable row level security;
alter table bitacora              enable row level security;

-- Permisos de tabla: nada para anon; lo mínimo para authenticated.
-- Solo service_role (las Edge Functions) escribe mediciones, alertas y avisos.
revoke all on all tables in schema public from anon, authenticated;

grant select on rol, deposito, lectura, umbral, alerta, notificacion,
                perfil, nodo, calibracion, evento_dispositivo,
                contacto_escalamiento, cambio_umbral, bitacora, confirmacion
      to authenticated;
grant insert on confirmacion to authenticated;
grant update (valor_pct, histeresis_pct) on umbral to authenticated;
grant update (nombre, cargo, id_rol, activo, vigente_hasta) on perfil to authenticated;
grant insert, update, delete on contacto_escalamiento to authenticated;
grant update (leida_en) on notificacion to authenticated;
grant all on all tables in schema public to service_role;
grant usage, select on all sequences in schema public to service_role;

-- Matriz de autorización:
--   Ver nivel / historial ........ ADMINISTRADOR, OPERADOR, MANTENCION, REEMPLAZANTE
--   Confirmar alertas ............ ADMINISTRADOR, OPERADOR, REEMPLAZANTE
--   Detalle técnico de equipos ... ADMINISTRADOR, MANTENCION
--   Editar umbrales / usuarios ... ADMINISTRADOR

-- Ver nivel y historial (todos los roles vigentes)
create policy ver_deposito on deposito for select to authenticated
  using (rol_actual() is not null);
create policy ver_lectura on lectura for select to authenticated
  using (rol_actual() is not null);
create policy ver_alerta on alerta for select to authenticated
  using (rol_actual() is not null);
create policy ver_umbral on umbral for select to authenticated
  using (rol_actual() is not null);
create policy ver_rol on rol for select to authenticated
  using (rol_actual() is not null);

-- Detalle técnico de equipos
create policy ver_nodo on nodo for select to authenticated
  using (rol_actual() in ('ADMINISTRADOR','MANTENCION'));
create policy ver_calibracion on calibracion for select to authenticated
  using (rol_actual() in ('ADMINISTRADOR','MANTENCION'));
create policy ver_evento on evento_dispositivo for select to authenticated
  using (rol_actual() in ('ADMINISTRADOR','MANTENCION'));

-- Perfiles: cada persona ve el suyo; el Administrador gestiona todos
create policy ver_perfil_propio on perfil for select to authenticated
  using (id_perfil = auth.uid() and rol_actual() is not null);
create policy admin_ver_perfiles on perfil for select to authenticated
  using (rol_actual() = 'ADMINISTRADOR');
create policy admin_edita_perfiles on perfil for update to authenticated
  using (rol_actual() = 'ADMINISTRADOR')
  with check (rol_actual() = 'ADMINISTRADOR');

-- Contactos de escalamiento: los gestiona el Administrador
create policy admin_contactos on contacto_escalamiento for all to authenticated
  using (rol_actual() = 'ADMINISTRADOR')
  with check (rol_actual() = 'ADMINISTRADOR');

-- Umbrales: solo el Administrador los edita
create policy admin_edita_umbral on umbral for update to authenticated
  using (rol_actual() = 'ADMINISTRADOR')
  with check (rol_actual() = 'ADMINISTRADOR');

-- Avisos: cada persona ve y marca como leídos los suyos
create policy ver_notificacion_propia on notificacion for select to authenticated
  using (id_perfil = auth.uid() and rol_actual() is not null);
create policy admin_ver_notificaciones on notificacion for select to authenticated
  using (rol_actual() = 'ADMINISTRADOR');
create policy marcar_leida_propia on notificacion for update to authenticated
  using (id_perfil = auth.uid() and rol_actual() is not null)
  with check (id_perfil = auth.uid());

-- Confirmaciones: ver (todos los roles vigentes) y crear solo a nombre propio
create policy ver_confirmacion on confirmacion for select to authenticated
  using (rol_actual() is not null);
create policy confirmar_alerta on confirmacion for insert to authenticated
  with check (id_perfil = auth.uid()
              and rol_actual() in ('ADMINISTRADOR','OPERADOR','REEMPLAZANTE'));

-- Registros de auditoría: solo el Administrador los consulta
create policy admin_ver_cambios on cambio_umbral for select to authenticated
  using (rol_actual() = 'ADMINISTRADOR');
create policy admin_ver_bitacora on bitacora for select to authenticated
  using (rol_actual() = 'ADMINISTRADOR');

-- Sin política de escritura para lectura, alerta, evento_dispositivo,
-- calibracion, nodo, deposito y bitacora: solo service_role (Edge Functions)
-- y los triggers pueden escribir ahí.

-- ---------------------------------------------------------------------
-- 4. Realtime: la app recibe alertas, avisos y mediciones en vivo
-- ---------------------------------------------------------------------
do $$
declare t text;
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    foreach t in array array['lectura','alerta','notificacion'] loop
      begin
        execute format('alter publication supabase_realtime add table public.%I', t);
      exception when duplicate_object then null;
      end;
    end loop;
  end if;
end $$;

-- ---------------------------------------------------------------------
-- 5. Comprobación: las 14 tablas deben aparecer con rls_activo = true
-- ---------------------------------------------------------------------
select c.relname as tabla, c.relrowsecurity as rls_activo
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by c.relname;
