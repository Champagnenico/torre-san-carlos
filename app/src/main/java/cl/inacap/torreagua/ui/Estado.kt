package cl.inacap.torreagua.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.torreagua.UiState

@Composable
fun EstadoScreen(ui: UiState, modifier: Modifier = Modifier) {
    val n = ui.nivel
    val estado = estadoUi(n, ui.ahora)
    val ancho = esAncho()

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingPantalla)
    ) {
        Titulo("Estado ahora")
        Espacio()

        if (ui.errorRed != null) {
            Banner(ui.errorRed, exito = false)
            Espacio()
        }

        if (ancho) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { Aparece(0) { TarjetaNivel(ui, estado) } }
                Box(Modifier.weight(1f)) { Aparece(1) { TarjetaNodo(ui, estado) } }
            }
        } else {
            Aparece(0) { TarjetaNivel(ui, estado) }
            Espacio(16.dp)
            Aparece(1) { TarjetaNodo(ui, estado) }
        }
    }
}

@Composable
private fun TarjetaNivel(ui: UiState, estado: EstadoUi) {
    val n = ui.nivel
    TarjetaBlanca {
        if (n == null) {
            Text("Torre de agua", style = MaterialTheme.typography.titleMedium)
            Espacio(8.dp)
            if (!ui.cargoDatos) {
                Text("Cargando…", style = MaterialTheme.typography.bodyLarge, color = TextoSecundario)
            } else {
                EtiquetaEstado(EstadoUi.SIN_CONEXION)
                Espacio(8.dp)
                Text(
                    "Todavía no hay mediciones. Cuando el nodo envíe la primera, aparecerá aquí.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                IconoTorre(
                    pct = n.nivelPct,
                    color = estado.fondo,
                    modifier = Modifier
                        .width(96.dp)
                        .height(130.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.deposito, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(rememberContador(n.nivelPct).toString(), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text(" %", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text("≈ ${miles(n.volumenL)} de ${miles(n.capacidadL)} L", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    EtiquetaEstado(estado)
                }
            }
            Spacer(Modifier.height(12.dp))
            val hace = ((ui.ahora - n.ts) / 60_000L).coerceAtLeast(0)
            Text(
                "Última lectura: ${cl.inacap.torreagua.data.Fechas.hora(n.ts)} · hace $hace min",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
            )
            if (estado == EstadoUi.SIN_CONEXION) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "El valor mostrado es el último que llegó; no hay mediciones recientes. " +
                        "Esto no significa que la torre esté en 0 %.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TarjetaNodo(ui: UiState, estado: EstadoUi) {
    TarjetaBlanca {
        Text("Así lo ve el nodo en la torre", style = MaterialTheme.typography.titleMedium)
        Espacio()
        val (l1, l2) = lineasLcd(ui.nivel, estado)
        PantallaLcd(l1, l2)
        Espacio()
        SemaforoLed(estado)
        Espacio(8.dp)
        Text(
            "Pantalla LCD y tres LED del nodo: avisan en el lugar aunque no haya internet.",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
        )
    }
}
