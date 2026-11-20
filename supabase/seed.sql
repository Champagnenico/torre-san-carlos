-- =====================================================================
-- Datos base. Ejecutar DESPUÉS de schema.sql (SQL Editor -> Run).
-- =====================================================================

insert into rol (id_rol, nombre, descripcion) values
  (1, 'ADMINISTRADOR', 'Gestiona usuarios y umbrales; ve todo (encargado del fundo)'),
  (2, 'OPERADOR',      'Ve el nivel y confirma alertas (operador de maquinaria, lecheros)'),
  (3, 'MANTENCION',    'Ve el nivel y el detalle técnico de los equipos (mecánico)'),
  (4, 'REEMPLAZANTE',  'Ve el nivel y confirma alertas; cuenta con fecha de término');

insert into deposito (nombre, tipo, capacidad_l) values
  ('Torre de agua', 'TORRE', 30000);

insert into nodo (id_deposito, dev_eui, descripcion)
select id_deposito, 'nodo-torre-01', 'ESP32 + HC-SR04 en la parte alta de la torre'
from deposito where nombre = 'Torre de agua';

-- CALIBRACIÓN PROVISIONAL: reemplazar con las distancias medidas en terreno
-- (A8/A13): distancia del sensor al agua con la torre vacía y con la torre llena.
insert into calibracion (id_nodo, dist_vacia_mm, dist_llena_mm)
select id_nodo, 3500, 300 from nodo where dev_eui = 'nodo-torre-01';

-- UMBRALES PROVISIONALES (el informe indica que faltan confirmar con el cliente).
-- Atención: nivel < 30 %. Crítico: nivel < 15 %. Llena: nivel > 95 %.
insert into umbral (id_deposito, tipo, operador, valor_pct, histeresis_pct)
select id_deposito, v.tipo, v.operador, v.valor, 2
from deposito,
     (values ('LLENA','>',95.0), ('ATENCION','<',30.0), ('CRITICA','<',15.0))
       as v(tipo, operador, valor)
where nombre = 'Torre de agua';

-- ---------------------------------------------------------------------
-- DESPUÉS: crear los usuarios en Authentication -> Users (Add user) y
-- asignarles su rol y cargo. Cada cuenta nueva parte como REEMPLAZANTE.
-- Reemplaza los correos por los reales:
--
--   update perfil set id_rol = 1, cargo = 'Encargado del fundo'
--   where id_perfil = (select id from auth.users where email = 'admin@correo.cl');
--
--   update perfil set id_rol = 2, cargo = 'Operador de maquinaria'
--   where id_perfil = (select id from auth.users where email = 'operador@correo.cl');
--
--   update perfil set id_rol = 3, cargo = 'Mecánico'
--   where id_perfil = (select id from auth.users where email = 'mecanico@correo.cl');
--
-- Contactos de escalamiento (orden 1 = primer aviso, 2 y 3 si nadie confirma):
--
--   insert into contacto_escalamiento (id_deposito, id_perfil, orden)
--   select d.id_deposito, u.id, 1
--   from deposito d, auth.users u
--   where d.nombre = 'Torre de agua' and u.email = 'operador@correo.cl';
--   -- repetir con orden 2 y 3 para los siguientes contactos
-- ---------------------------------------------------------------------
