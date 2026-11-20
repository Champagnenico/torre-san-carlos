package cl.inacap.torreagua.data

import android.content.Context
import cl.inacap.torreagua.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Guarda la sesión en el teléfono (SharedPreferences privado de la app). */
class SesionStore(context: Context) {
    private val prefs = context.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    fun leer(): Sesion? {
        val at = prefs.getString("at", null) ?: return null
        val rt = prefs.getString("rt", null) ?: return null
        val uid = prefs.getString("uid", null) ?: return null
        return Sesion(at, rt, prefs.getLong("exp", 0L), uid, prefs.getString("email", "") ?: "")
    }

    fun guardar(s: Sesion) {
        prefs.edit()
            .putString("at", s.accessToken)
            .putString("rt", s.refreshToken)
            .putLong("exp", s.expiraEnSeg)
            .putString("uid", s.userId)
            .putString("email", s.email)
            .apply()
    }

    fun borrar() {
        prefs.edit().clear().apply()
    }
}

private fun JSONObject.texto(k: String): String? = if (isNull(k)) null else getString(k)

/**
 * Cliente de Supabase por HTTP (Auth + PostgREST). Todas las funciones son BLOQUEANTES:
 * hay que llamarlas desde un hilo de fondo (Dispatchers.IO).
 */
class SupabaseApi(private val store: SesionStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var sesion: Sesion? = store.leer()
        private set

    companion object {
        @Volatile
        private var unica: SupabaseApi? = null

        /** Una sola instancia por proceso: la app y el servicio de avisos comparten la sesión. */
        fun instancia(context: Context): SupabaseApi =
            unica ?: synchronized(this) {
                unica ?: SupabaseApi(SesionStore(context.applicationContext)).also { unica = it }
            }
    }

    // ------------------------------------------------------------------ Auth

    fun iniciarSesion(email: String, clave: String): Sesion {
        val cuerpo = JSONObject().put("email", email).put("password", clave).toString()
        val resp = solicitar("POST", "/auth/v1/token?grant_type=password", cuerpo, conSesion = false)
        return guardarDesde(JSONObject(resp))
    }

    private fun refrescarToken(): Sesion {
        val rt = sesion?.refreshToken ?: throw ApiException(401, "Sin sesión")
        val cuerpo = JSONObject().put("refresh_token", rt).toString()
        try {
            val resp = solicitar("POST", "/auth/v1/token?grant_type=refresh_token", cuerpo, conSesion = false)
            return guardarDesde(JSONObject(resp))
        } catch (e: ApiException) {
            if (e.codigo in 400..499) throw ApiException(401, "Tu sesión expiró")
            throw e
        }
    }

    private fun guardarDesde(resp: JSONObject): Sesion {
        val user = resp.getJSONObject("user")
        val s = Sesion(
            accessToken = resp.getString("access_token"),
            refreshToken = resp.getString("refresh_token"),
            expiraEnSeg = System.currentTimeMillis() / 1000 + resp.optLong("expires_in", 3600),
            userId = user.getString("id"),
            email = user.optString("email", ""),
        )
        store.guardar(s)
        sesion = s
        return s
    }

    fun cerrarSesion() {
        val token = sesion?.accessToken
        store.borrar()
        sesion = null
        if (token != null) {
            try {  // aviso al servidor; si falla no importa
                val req = Request.Builder()
                    .url(Config.SUPABASE_URL + "/auth/v1/logout")
                    .header("apikey", Config.SUPABASE_KEY)
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(jsonType))
                    .build()
                http.newCall(req).execute().close()
            } catch (e: Exception) {
            }
        }
    }

    @Synchronized
    private fun sesionVigente(): Sesion {
        val s = sesion ?: throw ApiException(401, "Sin sesión")
        if (s.expiraEnSeg - 60 > System.currentTimeMillis() / 1000) return s
        return refrescarToken()
    }

    // ----------------------------------------------------------------- HTTP

    private fun solicitar(
        metodo: String,
        ruta: String,
        cuerpo: String? = null,
        conSesion: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val b = Request.Builder()
            .url(Config.SUPABASE_URL + ruta)
            .header("apikey", Config.SUPABASE_KEY)
            .header("Accept", "application/json")
        if (conSesion) b.header("Authorization", "Bearer " + sesionVigente().accessToken)
        for ((k, v) in extra) b.header(k, v)
        val rb = cuerpo?.toRequestBody(jsonType)
        b.method(metodo, rb)
        http.newCall(b.build()).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, mensajeDeError(txt, r.code))
            return txt
        }
    }

    private fun mensajeDeError(txt: String, codigo: Int): String {
        try {
            val o = JSONObject(txt)
            for (k in listOf("message", "msg", "error_description", "error")) {
                if (!o.isNull(k)) return o.getString(k)
            }
        } catch (e: Exception) {
        }
        return "Error $codigo"
    }

    private fun get(ruta: String): JSONArray = JSONArray(solicitar("GET", "/rest/v1/$ruta"))

    // ---------------------------------------------------------------- Datos

    /** null si la cuenta existe pero no tiene acceso (inactiva, vencida o sin perfil). */
    fun perfil(): Perfil? {
        val s = sesionVigente()
        val arr = get("perfil?select=nombre,cargo,id_rol&id_perfil=eq.${s.userId}")
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return Perfil(o.getString("nombre"), o.texto("cargo"), o.getInt("id_rol"), s.email)
    }

    fun nivelActual(): Nivel? {
        val arr = get("v_nivel_actual?select=*")
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return Nivel(
            deposito = o.getString("nombre"),
            capacidadL = o.getInt("capacidad_l"),
            ts = Fechas.parse(o.getString("ts_lectura")),
            distanciaMm = o.getInt("distancia_mm"),
            nivelPct = o.getDouble("nivel_pct"),
            volumenL = o.getInt("volumen_l"),
            estado = o.texto("estado"),
        )
    }

    fun alertas(): List<Alerta> {
        val arr = get(
            "alerta?select=id_alerta,tipo,abierta_en,cerrada_en,lectura(nivel_pct),confirmacion(confirmada_en)" +
                "&order=abierta_en.desc&limit=20"
        )
        val lista = ArrayList<Alerta>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val lect = o.optJSONObject("lectura")
            val c = o.opt("confirmacion")
            val conf: Long? = when (c) {
                is JSONObject -> Fechas.parse(c.getString("confirmada_en"))
                is JSONArray ->
                    if (c.length() > 0) Fechas.parse(c.getJSONObject(0).getString("confirmada_en")) else null
                else -> null
            }
            lista.add(
                Alerta(
                    id = o.getInt("id_alerta"),
                    tipo = o.getString("tipo"),
                    abiertaEn = Fechas.parse(o.getString("abierta_en")),
                    cerradaEn = o.texto("cerrada_en")?.let { Fechas.parse(it) },
                    nivelPct = if (lect != null && !lect.isNull("nivel_pct")) lect.getDouble("nivel_pct") else null,
                    confirmadaEn = conf,
                )
            )
        }
        return lista
    }

    fun umbrales(): List<Umbral> {
        val arr = get("umbral?select=tipo,operador,valor_pct,histeresis_pct")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Umbral(o.getString("tipo"), o.getString("operador"), o.getDouble("valor_pct"), o.getDouble("histeresis_pct"))
        }
    }

    /** Mediciones válidas de las últimas [horas] horas (hasta 3.000 puntos). */
    fun historial(horas: Int): List<Punto> {
        val desde = Fechas.iso(System.currentTimeMillis() - horas * 3_600_000L)
        val puntos = ArrayList<Punto>()
        for (pagina in 0 until 3) {
            val arr = get(
                "lectura?select=ts_lectura,nivel_pct&validez=eq.VALIDO&ts_lectura=gte.$desde" +
                    "&order=ts_lectura.asc&limit=1000&offset=${pagina * 1000}"
            )
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                puntos.add(Punto(Fechas.parse(o.getString("ts_lectura")), o.getDouble("nivel_pct")))
            }
            if (arr.length() < 1000) break
        }
        return puntos
    }

    /** Devuelve true si la alerta se confirmó (o ya estaba confirmada). */
    fun confirmarAlerta(idAlerta: Int): Boolean {
        val uid = sesionVigente().userId
        val cuerpo = JSONObject().put("id_alerta", idAlerta).put("id_perfil", uid).toString()
        try {
            solicitar("POST", "/rest/v1/confirmacion", cuerpo, extra = mapOf("Prefer" to "return=minimal"))
        } catch (e: ApiException) {
            if (e.codigo == 409) return false  // ya estaba confirmada
            throw e
        }
        return true
    }

    /** Crea una cuenta nueva mediante la Edge Function (solo Administrador). */
    fun crearUsuario(email: String, clave: String, nombre: String, cargo: String, idRol: Int) {
        val cuerpo = JSONObject()
            .put("email", email)
            .put("password", clave)
            .put("nombre", nombre)
            .put("cargo", cargo)
            .put("id_rol", idRol)
            .toString()
        solicitar("POST", "/functions/v1/crear-usuario", cuerpo)
    }

    /** Lista las cuentas (solo el Administrador las ve; las políticas RLS lo exigen). */
    fun usuarios(): List<Usuario> {
        val yo = sesionVigente().userId
        val arr = get("perfil?select=id_perfil,nombre,cargo,id_rol,activo&order=nombre.asc")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val id = o.getString("id_perfil")
            Usuario(id, o.getString("nombre"), o.texto("cargo"), o.getInt("id_rol"), o.getBoolean("activo"), id == yo)
        }
    }

    /** Desactiva o reactiva una cuenta. Una cuenta desactivada deja de ver el sistema. */
    fun cambiarActivo(idPerfil: String, activo: Boolean) {
        val cuerpo = JSONObject().put("activo", activo).toString()
        val resp = solicitar(
            "PATCH", "/rest/v1/perfil?id_perfil=eq.$idPerfil", cuerpo,
            extra = mapOf("Prefer" to "return=representation"),
        )
        if (JSONArray(resp).length() == 0) throw ApiException(403, "No tienes permiso para cambiar usuarios")
    }

    /** Cambia el valor de un umbral (solo el Administrador; las políticas RLS lo exigen). */
    fun guardarUmbral(tipo: String, valor: Double) {
        val cuerpo = JSONObject().put("valor_pct", valor).toString()
        val resp = solicitar(
            "PATCH", "/rest/v1/umbral?tipo=eq.$tipo", cuerpo,
            extra = mapOf("Prefer" to "return=representation"),
        )
        if (JSONArray(resp).length() == 0) throw ApiException(403, "No tienes permiso para cambiar los umbrales")
    }

    fun equipos(): List<Equipo> {
        val nodos = get("nodo?select=dev_eui,activo")
        val cal = get("calibracion?select=dist_vacia_mm,dist_llena_mm&order=vigente_desde.desc&limit=1")
        val c = if (cal.length() > 0) cal.getJSONObject(0) else null
        return (0 until nodos.length()).map {
            val o = nodos.getJSONObject(it)
            Equipo(o.getString("dev_eui"), o.getBoolean("activo"), c?.getInt("dist_vacia_mm"), c?.getInt("dist_llena_mm"))
        }
    }

    fun eventos(): List<EventoDisp> {
        val arr = get("evento_dispositivo?select=tipo,detalle,ocurrido_en&order=ocurrido_en.desc&limit=5")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val det = o.optJSONObject("detalle")
            EventoDisp(o.getString("tipo"), det?.optString("motivo", "") ?: "", Fechas.parse(o.getString("ocurrido_en")))
        }
    }

    fun bitacora(): List<Bitacora> {
        val arr = get("bitacora?select=accion,detalle,creado_en&order=creado_en.desc&limit=15")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val d = o.optJSONObject("detalle")
            val texto = when (o.getString("accion")) {
                "CAMBIO_UMBRAL" ->
                    "Umbral ${d?.optString("tipo", "")?.lowercase()} cambiado de ${d?.opt("antes")} % a ${d?.opt("despues")} %"
                "CONFIRMA_ALERTA" -> "Alerta ${d?.opt("id_alerta")} confirmada"
                "CREA_USUARIO", "CREAR_USUARIO" -> {
                    val rol = when (d?.optInt("id_rol", 0)) {
                        1 -> "Administrador"
                        2 -> "Operador"
                        3 -> "Mantención"
                        4 -> "Reemplazante"
                        else -> ""
                    }
                    "Usuario creado: ${d?.optString("email", "")}" + if (rol.isNotEmpty()) " ($rol)" else ""
                }
                "DESACTIVA_USUARIO" -> "Cuenta desactivada: ${d?.optString("nombre", "")}"
                "ACTIVA_USUARIO" -> "Cuenta activada: ${d?.optString("nombre", "")}"
                else -> o.getString("accion").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            }
            Bitacora(texto, Fechas.parse(o.getString("creado_en")))
        }
    }
}
