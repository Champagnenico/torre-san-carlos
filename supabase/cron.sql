-- Escalamiento cada minuto: pg_cron llama a la Edge Function escalar-alertas.
-- 1) Reemplaza <TOKEN> por el mismo valor del secreto ESCALAR_TOKEN.
-- 2) Ejecútalo una sola vez en el SQL Editor de Supabase.
create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.schedule(
  'escalar-alertas',
  '* * * * *',
  $$
  select net.http_post(
    url     := 'https://tzjziqjrxgquzkjbldml.supabase.co/functions/v1/escalar-alertas',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-token', '<TOKEN>'),
    body    := '{}'::jsonb
  );
  $$
);
