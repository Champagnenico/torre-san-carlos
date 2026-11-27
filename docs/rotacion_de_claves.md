# Rotación de tokens y claves

Procedimiento para cambiar el **token del nodo** y la **clave de Resend** (y, de paso, el token interno `ESCALAR_TOKEN`, que se guarda en la base de datos y es fácil de olvidar).

> **Regla de oro:** los valores reales nunca se escriben en el repositorio, ni en código, ni en docs, ni en capturas, ni en mensajes de commit o PR. Se guardan solo en Supabase, en Resend y en el gestor de contraseñas del equipo.

Última revisión: 27-11-2026. El registro de cada rotación está en [`checklist_rotacion.md`](checklist_rotacion.md).

---

## 1. Qué secretos existen y dónde viven

| Secreto | Quién lo usa | Dónde se cambia | Dónde más hay que ponerlo |
|---|---|---|---|
| `TOKEN_NODO_TORRE` (token del nodo) | Edge Function `recibir-lectura` (cabecera `x-token`) | Supabase → Edge Functions → Secrets | `#define TOKEN_NODO` en `firmware/nodo_torre/nodo_torre.ino` (solo en el PC, nunca commit) y volver a subir el programa al ESP32 |
| `RESEND_API_KEY` (clave de correo) | Edge Function `enviar-aviso` | 1) Resend → API Keys (crear la nueva). 2) Supabase → Edge Functions → Secrets | Ningún otro lugar |
| `ESCALAR_TOKEN` (token interno) | Edge Functions `escalar-alertas` y `enviar-aviso` (cabecera `x-cron-token`) | Supabase → Edge Functions → Secrets | **Dentro de la base de datos**: el job `escalar-alertas` (`cron.sql`) y la función `fn_enviar_aviso_correo` (`avisos_correo.sql`) llevan el mismo valor |

Otras claves, que normalmente no se rotan:

- **Clave publishable de Supabase** (`sb_publishable_...`, en `Config.kt`, `web/index.html` y `docs/pruebas_roles.ps1`): es pública por diseño; la seguridad la dan las políticas RLS. Si se rota, hay que cambiarla en esos 3 archivos y publicar una app y una web nuevas.
- **`SUPABASE_SERVICE_ROLE_KEY`**: la inyecta Supabase en las Edge Functions; no está en el repo. Solo se rota si se sospecha que se filtró (Supabase → Project Settings → API Keys).
- **Clave del Wi-Fi** (`WIFI_PASS`): vive solo en el firmware local.

## 2. Resultado de la revisión del repositorio (27-11-2026)

Se revisó el código de la rama `main` (zip descargado de GitHub) buscando `token`, `secret`, `api_key`, `password`, `bearer`, `service_role`, `resend`, JWT (`eyJ...`), claves `re_...` / `sb_secret_...` y cadenas largas de alta entropía, además del texto de los dos `.docx` de `docs/`.

| Hallazgo | Estado |
|---|---|
| Token del nodo | Solo el marcador `PEGAR_TOKEN_DEL_NODO` en el firmware. Sin valor real. |
| Clave de Resend | No aparece en ningún archivo (solo `Deno.env.get("RESEND_API_KEY")`). |
| `ESCALAR_TOKEN` | Solo el marcador `<TOKEN>` en `cron.sql` y `avisos_correo.sql`. |
| `service_role` / `sb_secret_` | Solo `Deno.env.get(...)` y GRANTs de SQL. Ningún valor. |
| Clave Wi-Fi | Solo el marcador `TU_CLAVE_WIFI`. |
| `sb_publishable_...` | Aparece 3 veces. Es pública por diseño; no requiere acción. |
| Contraseñas de usuarios | No hay; los scripts `.ps1` las piden por consola. |

**Limitación importante:** el zip no incluye la carpeta `.git`, así que **el historial no se pudo revisar desde aquí**. El informe de la Evaluación 3 también declara que el token del nodo y la clave de correo "se mostraron en pruebas", por lo que hay que asumirlos expuestos y rotarlos igual. Para revisar el historial, ejecutar en un clon completo:

```bash
git clone --mirror https://github.com/Champagnenico/torre-san-carlos.git
cd torre-san-carlos.git

# Opción A (recomendada): escáner de secretos sobre todo el historial
gitleaks detect --source . --log-opts="--all" -v
# o: trufflehog git file://$PWD --only-verified

# Opción B: búsqueda manual en todos los commits y ramas
git log --all -p -S"TOKEN_NODO" --oneline
git log --all -p -G"re_[A-Za-z0-9]{16,}" --oneline
git log --all -p -G"sb_secret_|service_role.{0,5}eyJ" --oneline
git log --all -p -G"x-cron-token.{0,40}'[^<]" --oneline
```

Si aparece un valor real en algún commit: **rotarlo primero** (secciones 3 a 5) y recién después pensar en limpiar el historial (`git filter-repo`). Limpiar el historial no sirve si el secreto sigue vigente, porque ya pudo ser copiado.

## 3. Rotar el token del nodo (`TOKEN_NODO_TORRE`)

**Cuándo:** cada 90 días, después de demostraciones, cuando alguien deja el equipo o si el token se mostró o filtró.

**Ojo con el corte:** `recibir-lectura` acepta un solo token a la vez. Desde que se cambia el secreto hasta que el ESP32 se reprograma, el nodo recibe 401. El firmware trata los 4xx como rechazo definitivo y **descarta esas mediciones** (no las reintenta). Por eso conviene dejar el PC con el Arduino IDE listo y hacer los pasos 3 y 4 seguidos (unos minutos), y no durante una alerta abierta.

1. **Generar el token nuevo** (largo y aleatorio), en tu PC:
   ```bash
   openssl rand -hex 32
   ```
   Guardarlo de inmediato en el gestor de contraseñas del equipo. No pegarlo en chats, issues ni commits.
2. **Dejar listo el firmware:** abrir `firmware/nodo_torre/nodo_torre.ino` en el Arduino IDE y conectar el ESP32 por USB.
3. **Cambiar el secreto en Supabase:** Dashboard → *Edge Functions* → *Secrets* → editar `TOKEN_NODO_TORRE` con el valor nuevo. Alternativa por CLI:
   ```bash
   supabase secrets set TOKEN_NODO_TORRE=<valor-nuevo>
   ```
4. **Cambiar el token en el nodo:** reemplazar el valor de `#define TOKEN_NODO` y subir el programa. **No hacer commit de ese archivo con el valor real** (ver sección 6).
5. **Verificar** (dejar `docs/pruebas_api.ps1` como referencia):
   - Monitor Serial a 115200: debe mostrar `POST ... -> 201` y la marca `OK` en la pantalla.
   - Con el token **viejo** la función debe responder 401 (EF-03). Con el nuevo, 201 o 200.
   - Si sigue respondiendo 401 con el token nuevo, volver a desplegar la función: `supabase functions deploy recibir-lectura`.
6. **Cerrar:** borrar el token viejo del gestor de contraseñas, anotar la rotación en [`checklist_rotacion.md`](checklist_rotacion.md) (fecha y quién; **sin el valor**).

## 4. Rotar la clave de Resend (`RESEND_API_KEY`)

**Cuándo:** cada 90 días, después de demostraciones, o de inmediato si se filtró.

A diferencia del nodo, aquí **no hay corte**: se crea la nueva, se activa, y recién entonces se borra la vieja.

1. **Crear la clave nueva:** Resend → *API Keys* → *Create API Key*. Permiso **Sending access** (no *Full access*) y, si hay dominio verificado, restringida a ese dominio. Resend muestra el valor **una sola vez**: copiarlo directo al gestor de contraseñas.
2. **Cambiar el secreto en Supabase:** Dashboard → *Edge Functions* → *Secrets* → editar `RESEND_API_KEY`. Por CLI:
   ```bash
   supabase secrets set RESEND_API_KEY=<valor-nuevo>
   ```
3. **Verificar el envío:** provocar un aviso de prueba (por ejemplo, una alerta de prueba en el entorno de desarrollo) y confirmar que llega el correo. También se puede llamar a `enviar-aviso` con un `id_notificacion` existente:
   ```bash
   curl -X POST "https://<proyecto>.supabase.co/functions/v1/enviar-aviso" \
     -H "x-cron-token: $ESCALAR_TOKEN" -H "Content-Type: application/json" \
     -d '{"id_notificacion": <id>}'
   ```
   Respuesta esperada: `{"ok":true,...}`. Un 502 "el servicio de correo rechazó el envío" indica una clave mal pegada.
4. **Revocar la clave vieja:** Resend → *API Keys* → eliminar la anterior. Revisar en *Logs* de Resend que no haya envíos que no reconozcas.
5. **Cerrar:** borrar la clave vieja del gestor y registrar la rotación en el checklist (fecha y quién; **sin el valor**).

## 5. Rotar el token interno (`ESCALAR_TOKEN`)

No fue pedido explícitamente, pero tiene el mismo riesgo: autoriza llamar a `escalar-alertas` y `enviar-aviso`, y su valor queda **copiado dentro de la base de datos**, así que cambiar solo el secreto rompe el escalamiento y los correos (401).

1. Generar el valor nuevo (`openssl rand -hex 32`) y guardarlo en el gestor.
2. Cambiar el secreto `ESCALAR_TOKEN` en Supabase (Secrets).
3. En el **SQL Editor** de Supabase, pegar el contenido de `supabase/cron.sql` y `supabase/avisos_correo.sql` y reemplazar `<TOKEN>` por el valor nuevo **solo en el editor**. Antes de ejecutar `cron.sql`, correr `select cron.unschedule('escalar-alertas');` para no duplicar el job. `avisos_correo.sql` ya usa `create or replace`, y recrea el trigger.
4. **No guardar** esos archivos con el valor real y borrar el snippet del SQL Editor cuando termines (el historial de consultas del editor también guarda el texto).
5. Verificar: sin cabecera responde 401; con el token nuevo, 200. Revisar que el job siga activo con `select jobname, schedule, active from cron.job;`.
6. Registrar en el checklist.

## 6. Cómo evitar volver a subir un secreto

- No editar los `#define` del firmware con valores reales dentro del repo. Usar un `config_local.h` (como ya sugiere el encabezado del `.ino`) y mantenerlo fuera de Git; el `.gitignore` ya lo excluye.
- No guardar `cron.sql` ni `avisos_correo.sql` con el `<TOKEN>` reemplazado.
- Si un secreto llega a un commit, aunque sea en una rama o un PR: **rotarlo de inmediato** (esta guía) y avisar al equipo. Borrar el commit no revoca nada.
- Activar en GitHub *Settings → Code security → Secret scanning* y *Push protection* si el plan del repo lo permite.
