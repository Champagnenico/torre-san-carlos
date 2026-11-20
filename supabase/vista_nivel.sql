-- Vista para la app: última medición válida de cada depósito, con porcentaje y litros.
-- Corre con permisos del dueño (así los roles sin acceso a la tabla nodo igual ven el nivel),
-- pero solo devuelve filas a cuentas vigentes, mediante rol_actual().
create or replace view v_nivel_actual as
select distinct on (d.id_deposito)
       d.id_deposito,
       d.nombre,
       d.capacidad_l,
       l.ts_lectura,
       l.distancia_mm,
       l.nivel_pct,
       l.volumen_l,
       l.estado,
       (l.ts_lectura < now() - interval '30 minutes') as sin_conexion
from deposito d
join nodo n on n.id_deposito = d.id_deposito
join lectura l on l.id_nodo = n.id_nodo and l.validez = 'VALIDO'
where rol_actual() is not null
order by d.id_deposito, l.ts_lectura desc;

revoke all on v_nivel_actual from public, anon;
grant select on v_nivel_actual to authenticated;
