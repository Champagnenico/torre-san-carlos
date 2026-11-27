# Validación SSL del nodo (certificado raíz de Supabase)

Issue: #5. Antes el nodo usaba `client.setInsecure()`, que cifra pero **no comprueba
con quién se habla**: cualquiera en la red podría hacerse pasar por Supabase y
leer el token del nodo. Ahora usa `client.setCACert(ROOT_CA_SUPABASE)`.

## De dónde se sacó el certificado

> Completar al subir el cambio (rellenar con lo que se vea en la prueba real):

* Fecha en que se obtuvo: `__/__/2026`
* Quién lo sacó: Cristóbal González
* Servidor consultado: `tzjziqjrxgquzkjbldml.supabase.co:443`
* Cadena que entregó el servidor: `supabase.co → (intermedio) → (raíz)`  ← anotar lo que muestre el comando
* Certificado raíz usado: `GTS Root R4` (Google Trust Services)
* Descargado de: https://pki.goog/repository/ (sitio oficial de Google Trust Services)
* Huella SHA-256 del certificado pegado: `__:__:__...`  ← salida de `openssl x509 -noout -fingerprint -sha256`
* Vence el: `__/__/20__`  ← salida de `openssl x509 -noout -enddate`

### Cómo se obtiene (Git Bash o Linux/Mac, con `openssl`)

1. Ver qué cadena entrega Supabase hoy y quién la firma:

   ```bash
   openssl s_client -connect tzjziqjrxgquzkjbldml.supabase.co:443 \
     -servername tzjziqjrxgquzkjbldml.supabase.co -showcerts </dev/null 2>/dev/null \
     | grep -E "^ *[0-9] s:|^ *i:"
   ```

   Hay que anotar quién es el emisor del último certificado de la cadena. A la fecha de
   esta guía, fuentes externas indican `supabase.co → WE1 → GTS Root R4`, pero
   **hay que verificarlo con el comando**, porque Supabase puede cambiar de autoridad
   certificadora (Let's Encrypt, Google Trust Services, SSL.com).

2. Descargar el certificado raíz correspondiente desde el sitio oficial de la autoridad
   (para Google: https://pki.goog/repository/ → "GTS Root R4", formato PEM; para
   Let's Encrypt: https://letsencrypt.org/certificates/ → "ISRG Root X1").
   **No** sacarlo de un foro ni de otro repositorio.

3. Comprobar que es el correcto:

   ```bash
   openssl x509 -in gtsr4.pem -noout -subject -issuer -enddate -fingerprint -sha256
   ```

   * `subject` e `issuer` deben ser iguales (raíz autofirmada).
   * Para GTS Root R4 la fecha de término debe ser **2036**. Si dice **2028** es la
     versión cruzada con GlobalSign, que caduca antes: no usar esa.

4. Pegar el bloque `-----BEGIN CERTIFICATE-----` ... `-----END CERTIFICATE-----`
   dentro de `firmware/nodo_torre/certificado_raiz.h`. Se recomienda dejar también
   `ISRG Root X1` (Let's Encrypt) pegado a continuación, como respaldo por si
   Supabase cambia de autoridad. Pegar **solo certificados raíz**, nunca el
   certificado del servidor ni el intermedio: esos se renuevan y romperían el nodo.

## Los certificados raíz caducan

* Un certificado raíz no dura para siempre (los de Google vencen en 2036, el de
  Let's Encrypt `ISRG Root X1` en 2035; verificar siempre con `-enddate`).
* El nodo lleva el certificado **guardado dentro del firmware**. Cuando la raíz
  caduque, o si Supabase cambia de autoridad certificadora, el nodo dejará de poder
  enviar lecturas (la pantalla mostrará `OFF`/`ERR` y el Monitor Serial dirá
  `Fallo de conexion: ... certificate verify failed`) hasta que se vuelva a
  grabar el firmware con el certificado nuevo.
* Qué hacer: repetir los pasos de arriba, actualizar `certificado_raiz.h` y esta
  ficha, y volver a subir el programa al ESP32.
* Recomendación: poner un recordatorio en el calendario **6 meses antes** de la
  fecha de vencimiento anotada arriba. Como el nodo está en terreno, un cambio de
  certificado sin avisar significa perder lecturas hasta que alguien vaya a grabarlo.

## Hora (NTP)

Para validar un certificado hay que conocer la fecha actual. El ESP32 no tiene reloj
propio, así que `nodo_torre.ino` la pide por Internet (NTP: `pool.ntp.org`) antes de
enviar. Si el nodo no logra la hora, no envía y reintenta (Monitor Serial:
`Sin hora NTP todavia`). Si la red del fundo bloquea NTP (puerto UDP 123), no
funcionará el envío.

## Wi-Fi y token: no se suben

La clave de Wi-Fi y el token del nodo van en `firmware/nodo_torre/config_local.h`,
que está en `.gitignore`. En el repositorio solo está `config_local.example.h` con
valores de mentira. Antes de cada commit revisar con `git status` que
`config_local.h` **no** aparezca.

## Resultado de la prueba en el nodo real

> Completar después de probar:

* [ ] Monitor Serial muestra `Hora sincronizada`/envíos con `-> 201` (o `200`)
* [ ] La pantalla muestra `OK` en la esquina
* [ ] Las lecturas aparecen en la app / tabla `lectura` de Supabase
* [ ] Prueba negativa: con un certificado cualquiera mal pegado, el nodo **no** envía
  (se ve `certificate verify failed`). Esto confirma que ya no es inseguro.
