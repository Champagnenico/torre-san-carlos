package cl.inacap.torreagua.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ApiException(val codigo: Int, mensaje: String) : Exception(mensaje)

data class Sesion(
    val accessToken: String,
    val refreshToken: String,
    val expiraEnSeg: Long,
    val userId: String,
    val email: String,
)

data class Perfil(val nombre: String, val cargo: String?, val idRol: Int, val email: String) {
    val rol: String
        get() = when (idRol) {
            1 -> "Administrador"
            2 -> "Operador"
            3 -> "Mantención"
            else -> "Reemplazante"
        }
    val esAdmin get() = idRol == 1
    val puedeConfirmar get() = idRol == 1 || idRol == 2 || idRol == 4
    val veEquipos get() = idRol == 1 || idRol == 3
}

data class Nivel(
    val deposito: String,
    val capacidadL: Int,
    val ts: Long,
    val distanciaMm: Int,
    val nivelPct: Double,
    val volumenL: Int,
    val estado: String?,
)

data class Alerta(
    val id: Int,
    val tipo: String,
    val abiertaEn: Long,
    val cerradaEn: Long?,
    val nivelPct: Double?,
    val confirmadaEn: Long?,
)

data class Umbral(val tipo: String, val operador: String, val valor: Double, val histeresis: Double)

data class Punto(val ts: Long, val nivel: Double)

data class Equipo(val devEui: String, val activo: Boolean, val distVaciaMm: Int?, val distLlenaMm: Int?)

data class EventoDisp(val tipo: String, val motivo: String, val ts: Long)

data class Bitacora(val texto: String, val ts: Long)

object Fechas {
    private fun utc(patron: String) =
        SimpleDateFormat(patron, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** Supabase entrega las fechas en UTC: "2026-11-19T02:46:51.123+00:00". */
    fun parse(s: String): Long = utc("yyyy-MM-dd'T'HH:mm:ss").parse(s.substring(0, 19))?.time ?: 0L

    fun iso(ms: Long): String = utc("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date(ms))

    /** Hora local del teléfono, ej. "04:05 a. m." */
    fun hora(ms: Long): String =
        SimpleDateFormat("hh:mm a", Locale("es", "CL")).format(Date(ms))

    fun fechaHora(ms: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale("es", "CL")).format(Date(ms))
}
