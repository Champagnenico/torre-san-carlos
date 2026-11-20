package cl.inacap.torreagua.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.inacap.torreagua.UiState
import cl.inacap.torreagua.data.Fechas

@Composable
fun EquiposScreen(ui: UiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingPantalla)
    ) {
        Titulo("Equipos")
        Espacio()

        if (ui.equipos.isEmpty()) {
            Aparece(0) {
                TarjetaBlanca {
                    Text("No hay equipos registrados.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        ui.equipos.forEachIndexed { idx, e ->
            Aparece(idx) { TarjetaBlanca {
                Text("Nodo de la torre", style = MaterialTheme.typography.titleMedium)
                Espacio(4.dp)
                Text("Identificador: ${e.devEui}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (e.activo) "Estado: activo" else "Estado: desactivado",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (e.distVaciaMm != null && e.distLlenaMm != null) {
                    Espacio(4.dp)
                    Text("Calibración", style = MaterialTheme.typography.titleMedium)
                    Text("Torre vacía: ${e.distVaciaMm} mm", style = MaterialTheme.typography.bodyMedium)
                    Text("Torre llena: ${e.distLlenaMm} mm", style = MaterialTheme.typography.bodyMedium)
                }
            } }
            Espacio()
        }

        Text("Eventos recientes", style = MaterialTheme.typography.titleMedium)
        Espacio(8.dp)
        Aparece(ui.equipos.size + 1) { TarjetaBlanca {
            if (ui.eventos.isEmpty()) {
                Text("Sin eventos registrados.", style = MaterialTheme.typography.bodyLarge)
            }
            ui.eventos.forEachIndexed { i, ev ->
                if (i > 0) Espacio(10.dp)
                Text(ev.tipo.replace('_', ' '), fontWeight = FontWeight.Bold)
                if (ev.motivo.isNotBlank()) Text(ev.motivo, style = MaterialTheme.typography.bodyMedium)
                Text(Fechas.fechaHora(ev.ts), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        } }
    }
}
