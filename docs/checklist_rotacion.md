# Checklist de rotación de tokens y claves

Procedimiento en [`rotacion_de_claves.md`](rotacion_de_claves.md). **No escribir aquí los valores de los tokens ni de las claves**, solo fechas y responsables.

Periodicidad sugerida: cada 90 días, después de cada demostración, al salir alguien del equipo, o de inmediato si hay sospecha de filtración.

## Revisión del repositorio

- [x] Código de `main` sin tokens ni claves reales. Revisado el 27-11-2026 (ver sección 2 del procedimiento).
- [ ] Historial completo de Git revisado con `gitleaks` o `trufflehog`. Fecha: __-__-____ · Responsable: __________ · ¿Hallazgos?: sí / no
- [ ] Secret scanning y push protection activados en GitHub. Fecha: __-__-____

## Rotación pendiente (ambos se mostraron en pruebas)

| Secreto | Última rotación | Responsable | Próxima rotación |
|---|---|---|---|
| Token del nodo (`TOKEN_NODO_TORRE`) | __-__-____ | __________ | 90 días después |
| Clave de Resend (`RESEND_API_KEY`) | __-__-____ | __________ | 90 días después |
| Token interno (`ESCALAR_TOKEN`) | __-__-____ | __________ | 90 días después |

## Pasos por rotación (copiar este bloque en cada rotación)

### Rotación del __-__-____ (responsable: __________)

**Token del nodo**
- [ ] Token nuevo generado y guardado en el gestor de contraseñas
- [ ] Secreto `TOKEN_NODO_TORRE` actualizado en Supabase
- [ ] `TOKEN_NODO` actualizado en el ESP32 y programa vuelto a subir
- [ ] Nodo envía con marca `OK` (201/200) y el token viejo da 401
- [ ] Token viejo eliminado del gestor
- [ ] Ningún archivo con el valor real quedó en un commit

**Clave de Resend**
- [ ] Clave nueva creada en Resend (*Sending access*)
- [ ] Secreto `RESEND_API_KEY` actualizado en Supabase
- [ ] Correo de prueba recibido
- [ ] Clave vieja eliminada en Resend y logs revisados
- [ ] Clave vieja eliminada del gestor

**Token interno**
- [ ] Secreto `ESCALAR_TOKEN` actualizado en Supabase
- [ ] `cron.sql` y `avisos_correo.sql` ejecutados con el valor nuevo (solo en el SQL Editor)
- [ ] Snippet del SQL Editor borrado
- [ ] `escalar-alertas` y `enviar-aviso` responden 200 con el token nuevo

**Cierre**
- [ ] Rotación anotada en la tabla de arriba
- [ ] Equipo avisado (Nicolás, Daniel, Cristóbal)
