-- Cada fila nueva en `notificacion` dispara el envío de un correo (Edge Function enviar-aviso).
-- Reemplaza <TOKEN> por el valor del secreto ESCALAR_TOKEN. Ejecutar una sola vez.
create or replace function fn_enviar_aviso_correo() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url     := 'https://tzjziqjrxgquzkjbldml.supabase.co/functions/v1/enviar-aviso',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-token', '<TOKEN>'),
    body    := jsonb_build_object('id_notificacion', new.id_notificacion)
  );
  return new;
end;
$$;

drop trigger if exists trg_aviso_correo on notificacion;
create trigger trg_aviso_correo
  after insert on notificacion
  for each row execute function fn_enviar_aviso_correo();
