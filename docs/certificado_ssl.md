\# Validación de certificado SSL en el nodo ESP32



\## Origen del certificado

Para reemplazar `setInsecure()` en `firmware/nodo\_torre/nodo\_torre.ino`, se extrajo el certificado de autoridad emisora \*\*WE1\*\* (emitido por \*\*Google Trust Services — GTS Root R4\*\*) directamente desde la conexión HTTPS del servidor de Supabase (`https://tzjziqjrxgquzkjbldml.supabase.co`) mediante el visor de certificados del navegador en formato PEM (Base64).



\## Caducidad y mantenimiento

\- \*\*Vigencia del certificado WE1:\*\* válido hasta el \*\*20 de febrero de 2029 (20/02/2029, 11:00 GMT-3)\*\*.

\- Los certificados raíz e intermedios tienen fecha de caducidad. Antes de esa fecha (o si el proveedor cambia su autoridad certificadora), será necesario exportar el nuevo certificado y actualizar la constante `SUPABASE\_CA\_CERT` en el firmware del ESP32 para evitar fallos de conexión TLS.



\## Verificación en el nodo real

Se cargó el firmware actualizado con `setCACert(SUPABASE\_CA\_CERT)` en el ESP32 y se verificó en el Monitor Serie el envío correcto de mediciones a `recibir-lectura` con respuesta \*\*HTTP 201\*\* (ver evidencia en `docs/evidencia\_ssl.png`).

