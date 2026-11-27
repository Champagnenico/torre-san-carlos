# Torre San Carlos: monitoreo IoT del nivel de la torre de agua

Proyecto Integrado (TIHI43), INACAP, Ingeniería Informática.
Cliente: Fundo Agrícola San Carlos, La Unión. Profesor: Javier Elgueta.
Integrantes: Nicolás Vera, Daniel Bracho, Cristóbal González.

Un nodo ESP32 con sensor ultrasónico HC-SR04 mide el nivel de la torre de 30.000 L y envía los datos por Wi-Fi a Supabase. Una app Android (y una versión web para iPhone) muestra el nivel y avisa cuando hay alertas.

## Estructura
| Carpeta | Contenido |
|---|---|
| `app/` | App Android (Kotlin, Jetpack Compose, OkHttp) |
| `supabase/` | Esquema SQL, RLS, vista, cron y Edge Functions |
| `firmware/nodo_torre/` | Programa del ESP32 (Arduino IDE) |
| `web/` | Versión web instalable (PWA) |
| `docs/` | Informe de la Evaluación 3 y plan de pruebas |

## Cómo ponerlo en marcha
1. **Supabase**: ejecutar en orden `schema.sql`, `seed.sql`, `vista_nivel.sql`; crear las Edge Functions de `supabase/functions/`; definir los secretos `TOKEN_NODO_TORRE`, `ESCALAR_TOKEN` y `RESEND_API_KEY`; ejecutar `cron.sql` y `avisos_correo.sql` (reemplazando `<TOKEN>`).
2. **Nodo**: abrir `firmware/nodo_torre/nodo_torre.ino`, completar Wi-Fi (2,4 GHz) y token, subir a un ESP32 Dev Module.
3. **App**: abrir en Android Studio, poner la URL y la clave *publishable* en `Config.kt` y ejecutar.
4. **Web**: publicar la carpeta `web/` en Netlify.

## Seguridad
No subir claves secretas ni el token del nodo. En la app solo va la clave publicable. Procedimiento de rotación en [`docs/rotacion_de_claves.md`](docs/rotacion_de_claves.md) y registro en [`docs/checklist_rotacion.md`](docs/checklist_rotacion.md).
