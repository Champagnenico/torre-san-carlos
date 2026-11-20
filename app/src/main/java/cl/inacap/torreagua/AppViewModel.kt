package cl.inacap.torreagua

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.inacap.torreagua.data.Alerta
import cl.inacap.torreagua.data.ApiException
import cl.inacap.torreagua.data.Bitacora
import cl.inacap.torreagua.data.Equipo
import cl.inacap.torreagua.data.EventoDisp
import cl.inacap.torreagua.data.Nivel
import cl.inacap.torreagua.data.Perfil
import cl.inacap.torreagua.data.Punto
import cl.inacap.torreagua.data.SesionStore
import cl.inacap.torreagua.data.SupabaseApi
import cl.inacap.torreagua.data.Umbral
import cl.inacap.torreagua.data.Usuario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class Aviso(val texto: String, val exito: Boolean)

data class UiState(
    val logueado: Boolean = false,
    val cargandoLogin: Boolean = false,
    val errorLogin: String? = null,
    val perfil: Perfil? = null,
    val sinAcceso: Boolean = false,
    val cargoDatos: Boolean = false,
    val nivel: Nivel? = null,
    val alertas: List<Alerta> = emptyList(),
    val umbrales: List<Umbral> = emptyList(),
    val puntos: List<Punto> = emptyList(),
    val rangoHoras: Int = 24,
    val equipos: List<Equipo> = emptyList(),
    val eventos: List<EventoDisp> = emptyList(),
    val bitacora: List<Bitacora> = emptyList(),
    val aviso: Aviso? = null,
    val errorRed: String? = null,
    val ahora: Long = System.currentTimeMillis(),
    val guardando: Boolean = false,
    val creandoUsuario: Boolean = false,
    val usuarioCreado: Int = 0,
    val usuarios: List<Usuario> = emptyList(),
    val cambiandoUsuario: String? = null,
)

private class Paquete(
    val perfil: Perfil?,
    val nivel: Nivel?,
    val alertas: List<Alerta>,
    val umbrales: List<Umbral>,
    val puntos: List<Punto>,
    val equipos: List<Equipo>,
    val eventos: List<EventoDisp>,
    val bitacora: List<Bitacora>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SupabaseApi.instancia(app)

    var ui by mutableStateOf(UiState(logueado = api.sesion != null))
        private set

    // ------------------------------------------------------------- Sesión

    fun iniciarSesion(email: String, clave: String) {
        if (email.isBlank() || clave.isBlank()) {
            ui = ui.copy(errorLogin = "Escribe tu correo y tu contraseña")
            return
        }
        ui = ui.copy(cargandoLogin = true, errorLogin = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.iniciarSesion(email.trim(), clave) }
                ui = ui.copy(logueado = true, cargandoLogin = false, perfil = null, sinAcceso = false)
                refrescar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val msg = if (e.codigo in 400..422) "Correo o contraseña incorrectos" else e.message
                ui = ui.copy(cargandoLogin = false, errorLogin = msg)
            } catch (e: IOException) {
                ui = ui.copy(cargandoLogin = false, errorLogin = "Sin conexión a internet. Revisa tu red.")
            } catch (e: Exception) {
                ui = ui.copy(cargandoLogin = false, errorLogin = "No se pudo iniciar sesión")
            }
        }
    }

    fun cerrarSesion(mensaje: String? = null) {
        ui = UiState(logueado = false, errorLogin = mensaje)
        viewModelScope.launch(Dispatchers.IO) { api.cerrarSesion() }
    }

    // ------------------------------------------------------------- Datos

    /** Llamada periódica desde la Activity mientras la app está visible. */
    suspend fun refrescar() {
        if (!ui.logueado) return
        val perfilActual = ui.perfil
        val horas = ui.rangoHoras
        try {
            val p = withContext(Dispatchers.IO) { cargarTodo(perfilActual, horas) }
            if (p.perfil == null) {
                ui = ui.copy(sinAcceso = true, errorRed = null)
                return
            }
            ui = ui.copy(
                perfil = p.perfil, sinAcceso = false, cargoDatos = true,
                nivel = p.nivel, alertas = p.alertas, umbrales = p.umbrales, puntos = p.puntos,
                equipos = p.equipos, eventos = p.eventos, bitacora = p.bitacora,
                errorRed = null, ahora = System.currentTimeMillis(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            alFallar(e)
        }
    }

    private fun cargarTodo(perfilActual: Perfil?, horas: Int): Paquete {
        val perfil = perfilActual ?: api.perfil()
        if (perfil == null) return Paquete(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return Paquete(
            perfil = perfil,
            nivel = api.nivelActual(),
            alertas = api.alertas(),
            umbrales = api.umbrales(),
            puntos = api.historial(horas),
            equipos = if (perfil.veEquipos) api.equipos() else emptyList(),
            eventos = if (perfil.veEquipos) api.eventos() else emptyList(),
            bitacora = if (perfil.esAdmin) api.bitacora() else emptyList(),
        )
    }

    private fun alFallar(e: Exception) {
        if (e is ApiException && e.codigo == 401) {
            cerrarSesion("Tu sesión expiró. Vuelve a iniciar sesión.")
            return
        }
        val msg = when (e) {
            is IOException -> "Sin conexión con el servidor. Se reintentará solo."
            is ApiException -> e.message ?: "Error del servidor"
            else -> "No se pudieron leer los datos"
        }
        ui = ui.copy(errorRed = msg)
    }

    fun cambiarRango(horas: Int) {
        ui = ui.copy(rangoHoras = horas)
        viewModelScope.launch {
            try {
                val puntos = withContext(Dispatchers.IO) { api.historial(horas) }
                ui = ui.copy(puntos = puntos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                alFallar(e)
            }
        }
    }

    // ----------------------------------------------------------- Acciones

    fun confirmar(idAlerta: Int) {
        viewModelScope.launch {
            try {
                val nueva = withContext(Dispatchers.IO) { api.confirmarAlerta(idAlerta) }
                ui = ui.copy(
                    aviso = Aviso(if (nueva) "Alerta confirmada: «Recibido»" else "Esa alerta ya estaba confirmada", true)
                )
                refrescar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.codigo == 401) {
                    alFallar(e)
                } else {
                    ui = ui.copy(aviso = Aviso("No se pudo confirmar: ${e.message}", false))
                }
            } catch (e: Exception) {
                ui = ui.copy(aviso = Aviso("No se pudo confirmar. Revisa tu conexión.", false))
            }
        }
    }

    fun guardarUmbrales(llena: Double, atencion: Double, critico: Double) {
        if (llena !in 0.0..100.0 || atencion !in 0.0..100.0 || critico !in 0.0..100.0) {
            ui = ui.copy(aviso = Aviso("Los valores deben estar entre 0 y 100", false))
            return
        }
        if (!(critico < atencion && atencion < llena)) {
            ui = ui.copy(aviso = Aviso("Debe cumplirse: crítico < atención < llena", false))
            return
        }
        ui = ui.copy(guardando = true, aviso = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.guardarUmbral("LLENA", llena)
                    api.guardarUmbral("ATENCION", atencion)
                    api.guardarUmbral("CRITICA", critico)
                }
                ui = ui.copy(guardando = false, aviso = Aviso("Umbral guardado con éxito", true))
                refrescar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.codigo == 401) {
                    alFallar(e)
                } else {
                    ui = ui.copy(guardando = false, aviso = Aviso("No se pudo guardar: ${e.message}", false))
                }
            } catch (e: Exception) {
                ui = ui.copy(guardando = false, aviso = Aviso("No se pudo guardar. Revisa tu conexión.", false))
            }
        }
    }

    fun crearUsuario(email: String, clave: String, nombre: String, cargo: String, idRol: Int) {
        if (email.isBlank() || nombre.isBlank() || clave.isBlank()) {
            ui = ui.copy(aviso = Aviso("Completa nombre, correo y contraseña", false))
            return
        }
        if (clave.length < 8) {
            ui = ui.copy(aviso = Aviso("La contraseña debe tener al menos 8 caracteres", false))
            return
        }
        ui = ui.copy(creandoUsuario = true, aviso = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.crearUsuario(email.trim(), clave, nombre.trim(), cargo.trim(), idRol)
                }
                ui = ui.copy(
                    creandoUsuario = false,
                    usuarioCreado = ui.usuarioCreado + 1,
                    aviso = Aviso("Usuario creado: ${email.trim()}", true),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.codigo == 401) {
                    alFallar(e)
                } else {
                    ui = ui.copy(creandoUsuario = false, aviso = Aviso("No se pudo crear: ${e.message}", false))
                }
            } catch (e: Exception) {
                ui = ui.copy(creandoUsuario = false, aviso = Aviso("No se pudo crear. Revisa tu conexión.", false))
            }
        }
    }

    fun cargarUsuarios() {
        if (ui.perfil?.esAdmin != true) return
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) { api.usuarios() }
                ui = ui.copy(usuarios = lista)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.codigo == 401) alFallar(e)
            } catch (e: Exception) {
                // La lista se vuelve a pedir al abrir la pantalla; no se muestra un error aparte.
            }
        }
    }

    fun cambiarActivo(id: String, activo: Boolean) {
        val u = ui.usuarios.firstOrNull { it.id == id } ?: return
        if (u.esYo) {
            ui = ui.copy(aviso = Aviso("No puedes desactivar tu propia cuenta", false))
            return
        }
        ui = ui.copy(cambiandoUsuario = id, aviso = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.cambiarActivo(id, activo) }
                val lista = withContext(Dispatchers.IO) { api.usuarios() }
                ui = ui.copy(
                    cambiandoUsuario = null,
                    usuarios = lista,
                    aviso = Aviso(if (activo) "Cuenta activada" else "Cuenta desactivada", true),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.codigo == 401) {
                    alFallar(e)
                } else {
                    ui = ui.copy(cambiandoUsuario = null, aviso = Aviso("No se pudo cambiar: ${e.message}", false))
                }
            } catch (e: Exception) {
                ui = ui.copy(cambiandoUsuario = null, aviso = Aviso("No se pudo cambiar. Revisa tu conexión.", false))
            }
        }
    }

    fun descartarAviso() {
        ui = ui.copy(aviso = null)
    }
}
