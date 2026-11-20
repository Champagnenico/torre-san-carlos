package cl.inacap.torreagua

/** Datos de conexión con Supabase. La clave "publishable" es pública por diseño
 *  (la seguridad la dan las políticas RLS). NUNCA pongas aquí la clave secret ni service_role. */
object Config {
    const val SUPABASE_URL = "https://tzjziqjrxgquzkjbldml.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_JRIYQnzJUw_NCfu7hDuhLA_lQ5rH5Yq"

    const val REFRESCO_MS = 3_000L      // cada cuánto se actualizan los datos
    const val SIN_CONEXION_MIN = 30L     // minutos sin mediciones => "Sensor sin conexión"
}
