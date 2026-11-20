package cl.inacap.torreagua.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import cl.inacap.torreagua.AppViewModel
import kotlinx.coroutines.delay

private enum class Pestana(val titulo: String, val icono: String) {
    ESTADO("Estado", "⌂"),
    ALERTAS("Alertas", "!"),
    HISTORIAL("Historial", "≈"),
    EQUIPOS("Equipos", "⚙"),
    CUENTA("Cuenta", "☰"),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val ui = vm.ui
    when {
        !ui.logueado -> LoginScreen(ui) { correo, clave -> vm.iniciarSesion(correo.trim(), clave) }
        ui.sinAcceso -> SinAccesoScreen { vm.cerrarSesion() }
        else -> Principal(vm)
    }
}

@Composable
private fun Principal(vm: AppViewModel) {
    val ui = vm.ui
    val perfil = ui.perfil
    var actual by rememberSaveable { mutableStateOf(Pestana.ESTADO) }

    val pestanas = Pestana.values().filter { it != Pestana.EQUIPOS || perfil?.veEquipos == true }
    if (actual !in pestanas) actual = Pestana.ESTADO

    // El aviso (éxito o error) se descarta solo a los 6 s.
    LaunchedEffect(ui.aviso) {
        if (ui.aviso != null) {
            delay(6_000)
            vm.descartarAviso()
        }
    }

    val abiertas = ui.alertas.count { it.cerradaEn == null }
    val ancho = esAncho()

    val aviso = ui.aviso
    var ultimoAviso by remember { mutableStateOf(aviso) }
    if (aviso != null) ultimoAviso = aviso

    fun etiquetaDe(t: Pestana) = if (t == Pestana.CUENTA && perfil?.esAdmin == true) "Administrar" else t.titulo

    @Composable
    fun Icono(t: Pestana) {
        if (t == Pestana.ALERTAS && abiertas > 0) {
            BadgedBox(badge = { Badge { Text(abiertas.toString()) } }) { Text(t.icono, fontSize = 22.sp) }
        } else {
            Text(t.icono, fontSize = 22.sp)
        }
    }

    Scaffold(
        containerColor = Fondo,
        topBar = { Encabezado() },
        bottomBar = {
            if (!ancho) {
                NavigationBar(containerColor = Tarjeta) {
                    pestanas.forEach { t ->
                        NavigationBarItem(
                            selected = actual == t,
                            onClick = { actual = t },
                            icon = { Icono(t) },
                            label = { Text(etiquetaDe(t)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Tarjeta,
                                selectedTextColor = VerdeOscuro,
                                indicatorColor = VerdeOscuro,
                            ),
                        )
                    }
                }
            }
        },
    ) { margen: PaddingValues ->
        Row(Modifier.padding(margen).fillMaxSize()) {
            if (ancho) {
                NavigationRail(containerColor = Tarjeta, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    pestanas.forEach { t ->
                        NavigationRailItem(
                            selected = actual == t,
                            onClick = { actual = t },
                            icon = { Icono(t) },
                            label = { Text(etiquetaDe(t)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Tarjeta,
                                selectedTextColor = VerdeOscuro,
                                indicatorColor = VerdeOscuro,
                            ),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = if (ancho && actual == Pestana.ESTADO) 1000.dp else 760.dp).fillMaxHeight()) {
                    AnimatedVisibility(
                        visible = aviso != null,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                    ) {
                        ultimoAviso?.let { a ->
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                                Banner(a.texto, a.exito)
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = actual,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                        modifier = Modifier.weight(1f),
                        label = "pestana",
                    ) { pantalla ->
                        val m = Modifier.fillMaxSize()
                        when (pantalla) {
                            Pestana.ESTADO -> EstadoScreen(ui, m)
                            Pestana.ALERTAS -> AlertasScreen(
                                ui = ui,
                                puedeConfirmar = perfil?.puedeConfirmar == true,
                                esAdmin = perfil?.esAdmin == true,
                                onConfirmar = { vm.confirmar(it) },
                                modifier = m,
                            )
                            Pestana.HISTORIAL -> HistorialScreen(ui, { vm.cambiarRango(it) }, m)
                            Pestana.EQUIPOS -> EquiposScreen(ui, m)
                            Pestana.CUENTA -> CuentaScreen(
                                ui = ui,
                                onGuardar = { l, a, c -> vm.guardarUmbrales(l, a, c) },
                                onCrearUsuario = { c, k, n, g, r -> vm.crearUsuario(c, k, n, g, r) },
                                onCargarUsuarios = { vm.cargarUsuarios() },
                                onCambiarActivo = { id, a -> vm.cambiarActivo(id, a) },
                                onSalir = { vm.cerrarSesion() },
                                modifier = m,
                            )
                        }
                    }
                }
            }
        }
    }
}
