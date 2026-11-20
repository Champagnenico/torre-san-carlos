-- =====================================================================
-- Pruebas de integridad (BD-01 a BD-09) y de seguridad por filas (RLS-01 a RLS-07)
-- Ejecutar DESPUÉS de schema.sql y seed.sql, en el SQL Editor de Supabase.
-- Todo ocurre dentro de una transacción que termina en ROLLBACK:
-- no deja datos de prueba. La tabla final muestra el resultado de cada prueba
-- (tomar la captura de esa tabla para el informe).
-- =====================================================================
begin;

create temp table resultados (
  id        text,
  prueba    text,
  esperado  text,
  obtenido  text,
  ok        boolean
) on commit drop;

-- Ejecuta una sentencia, opcionalmente como otro rol/usuario, y devuelve qué pasó.
create function pg_temp.intentar(p_sql text, p_rol text default null, p_uid uuid default null)
returns text language plpgsql as $$
declare n integer; res text;
begin
  if p_rol is not null then
    perform set_config('request.jwt.claims',
      case when p_uid is null then '{}' else json_build_object('sub', p_uid, 'role', p_rol)::text end,
      true);
    execute format('set local role %I', p_rol);
  end if;
  begin
    execute p_sql;
    get diagnostics n = row_count;
    res := 'ACEPTADA (' || n || ' filas)';
  exception when others then
    res := 'RECHAZADA: ' || sqlerrm;
  end;
  if p_rol is not null then execute 'reset role'; end if;
  return res;
end;
$$;

create function pg_temp.registrar(p_id text, p_prueba text, p_esperado text, p_obtenido text)
returns void language sql as $$
  insert into resultados values (p_id, p_prueba, p_esperado, p_obtenido,
                                 p_obtenido like p_esperado || '%');
$$;

-- ---------------------------------------------------------------------
-- Datos de apoyo (se descartan con el ROLLBACK)
-- ---------------------------------------------------------------------
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a1', 'admin@prueba.cl'),
  ('00000000-0000-0000-0000-0000000000a2', 'operador@prueba.cl'),
  ('00000000-0000-0000-0000-0000000000a3', 'mecanico@prueba.cl'),
  ('00000000-0000-0000-0000-0000000000a4', 'vencido@prueba.cl');

update perfil set id_rol = 1 where id_perfil = '00000000-0000-0000-0000-0000000000a1';
update perfil set id_rol = 2 where id_perfil = '00000000-0000-0000-0000-0000000000a2';
update perfil set id_rol = 3 where id_perfil = '00000000-0000-0000-0000-0000000000a3';
update perfil set id_rol = 4, vigente_hasta = now() - interval '1 day'
  where id_perfil = '00000000-0000-0000-0000-0000000000a4';

insert into lectura (id_nodo, ts_lectura, seq, distancia_mm, nivel_pct, volumen_l, validez, estado)
select id_nodo, now(), 1, 3000, 15.6, 4680, 'VALIDO', 'ATENCION' from nodo limit 1;

insert into alerta (id_deposito, id_lectura, tipo)
select d.id_deposito, l.id_lectura, 'ATENCION' from deposito d, lectura l limit 1;

insert into bitacora (accion, entidad) values ('PRUEBA', 'prueba');

-- ---------------------------------------------------------------------
-- Integridad de datos
-- ---------------------------------------------------------------------
select pg_temp.registrar('BD-01', 'Medición de un nodo inexistente (clave foránea)', 'RECHAZADA',
  pg_temp.intentar($q$ insert into lectura (id_nodo, ts_lectura, seq, distancia_mm, nivel_pct, volumen_l, validez)
                       values (99999, now(), 1, 1000, 50, 15000, 'VALIDO') $q$));

select pg_temp.registrar('BD-02', 'Perfil con rol inexistente (clave foránea)', 'RECHAZADA',
  pg_temp.intentar($q$ update perfil set id_rol = 99
                       where id_perfil = '00000000-0000-0000-0000-0000000000a2' $q$));

select pg_temp.registrar('BD-03', 'Nivel fuera de 0 a 100 % (CHECK)', 'RECHAZADA',
  pg_temp.intentar($q$ insert into lectura (id_nodo, ts_lectura, seq, distancia_mm, nivel_pct, volumen_l, validez)
                       select id_nodo, now() + interval '1 hour', 2, 1000, 150, 1, 'VALIDO' from nodo limit 1 $q$));

select pg_temp.registrar('BD-04', 'Umbral con operador inválido (CHECK)', 'RECHAZADA',
  pg_temp.intentar($q$ update umbral set operador = '!=' where tipo = 'CRITICA' $q$));

select pg_temp.registrar('BD-05', 'Dos confirmaciones de la misma alerta (UNIQUE)', 'RECHAZADA',
  pg_temp.intentar($q$
    insert into confirmacion (id_alerta, id_perfil)
      select id_alerta, '00000000-0000-0000-0000-0000000000a2' from alerta limit 1;
    insert into confirmacion (id_alerta, id_perfil)
      select id_alerta, '00000000-0000-0000-0000-0000000000a1' from alerta limit 1; $q$));

select pg_temp.registrar('BD-06', 'Dispositivo con identificador duplicado (UNIQUE)', 'RECHAZADA',
  pg_temp.intentar($q$ insert into nodo (id_deposito, dev_eui)
                       select id_deposito, 'nodo-torre-01' from deposito limit 1 $q$));

select pg_temp.registrar('BD-07', 'Modificar la bitácora (trigger)', 'RECHAZADA: La bitácora no se puede modificar ni borrar',
  pg_temp.intentar($q$ update bitacora set accion = 'ALTERADA' $q$));

select pg_temp.registrar('BD-08', 'Borrar la bitácora (trigger)', 'RECHAZADA: La bitácora no se puede modificar ni borrar',
  pg_temp.intentar($q$ delete from bitacora $q$));

select pg_temp.registrar('BD-09', 'Borrar un depósito con nodos (clave foránea)', 'RECHAZADA',
  pg_temp.intentar($q$ delete from deposito $q$));

-- ---------------------------------------------------------------------
-- Seguridad por filas (MT-16): una cuenta de cada rol
-- ---------------------------------------------------------------------
select pg_temp.registrar('RLS-01', 'Operador intenta editar un umbral', 'ACEPTADA (0 filas)',
  pg_temp.intentar($q$ update umbral set valor_pct = 5 where tipo = 'CRITICA' $q$,
                   'authenticated', '00000000-0000-0000-0000-0000000000a2'));

select pg_temp.registrar('RLS-02', 'Administrador edita un umbral', 'ACEPTADA (1 filas)',
  pg_temp.intentar($q$ update umbral set valor_pct = 12 where tipo = 'CRITICA' $q$,
                   'authenticated', '00000000-0000-0000-0000-0000000000a1'));

select pg_temp.registrar('RLS-03', 'El cambio de umbral quedó registrado en cambio_umbral', 'ACEPTADA (1 filas)',
  pg_temp.intentar($q$ select 1 from cambio_umbral
                       where valor_anterior = 15 and valor_nuevo = 12
                         and id_perfil = '00000000-0000-0000-0000-0000000000a1' $q$));

select pg_temp.registrar('RLS-04', 'Mantención intenta confirmar una alerta', 'RECHAZADA',
  pg_temp.intentar($q$ insert into confirmacion (id_alerta, id_perfil)
                       select id_alerta, '00000000-0000-0000-0000-0000000000a3' from alerta limit 1 $q$,
                   'authenticated', '00000000-0000-0000-0000-0000000000a3'));

select pg_temp.registrar('RLS-05', 'Operador intenta ver el detalle de los nodos', 'ACEPTADA (0 filas)',
  pg_temp.intentar($q$ select * from nodo $q$,
                   'authenticated', '00000000-0000-0000-0000-0000000000a2'));

select pg_temp.registrar('RLS-06', 'Operador intenta insertar una medición', 'RECHAZADA',
  pg_temp.intentar($q$ insert into lectura (id_nodo, ts_lectura, seq, distancia_mm, nivel_pct, volumen_l, validez)
                       select id_nodo, now() + interval '2 hour', 3, 1000, 50, 15000, 'VALIDO' from nodo limit 1 $q$,
                   'authenticated', '00000000-0000-0000-0000-0000000000a2'));

select pg_temp.registrar('RLS-07', 'Reemplazante vencido intenta ver las mediciones', 'ACEPTADA (0 filas)',
  pg_temp.intentar($q$ select * from lectura $q$,
                   'authenticated', '00000000-0000-0000-0000-0000000000a4'));

select pg_temp.registrar('RLS-08', 'Visitante sin sesión (anon) intenta ver las mediciones', 'RECHAZADA',
  pg_temp.intentar($q$ select * from lectura $q$, 'anon'));

-- ---------------------------------------------------------------------
-- Resultado (tomar captura de esta tabla)
-- ---------------------------------------------------------------------
select id, prueba, case when ok then 'APROBADA' else 'FALLÓ' end as resultado, obtenido
from resultados order by id;

rollback;
