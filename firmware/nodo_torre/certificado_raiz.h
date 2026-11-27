// Certificado raíz con el que el nodo valida el servidor de Supabase (HTTPS).
// Es un certificado PÚBLICO: se puede subir a GitHub.
//
// Cómo obtenerlo, de dónde salió y cuándo caduca: docs/certificado_ssl.md
//
// IMPORTANTE: reemplazar el bloque de abajo por el PEM real (solo hace falta
// pegar el bloque BEGIN/END CERTIFICATE completo, tal cual, sin comillas: es un
// "raw string"). Se pueden pegar varios certificados seguidos (por ejemplo
// GTS Root R4 + ISRG Root X1 como respaldo).

const char* ROOT_CA_SUPABASE = R"EOF_CERT(
-----BEGIN CERTIFICATE-----
PEGAR_AQUI_EL_CERTIFICADO_RAIZ_GTS_ROOT_R4
-----END CERTIFICATE-----
)EOF_CERT";
