package cl.inacap.torreagua.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.torreagua.UiState
import cl.inacap.torreagua.data.Alerta
import cl.inacap.torreagua.data.Fechas

private fun colorAlerta(tipo: String): Color = when (tipo) {
    "CRITICA" -> RojoCritico
    "ATENCION" -> Amarillo
    else -> AzulLlena
}

private fun tituloAlerta(a: Alerta): String {
    val pct = a.nivelPct?.let { entero(it) + " %" } ?: ""
    return when (a.tipo) {
        "CRITICA" -> "Crítico: torre de agua en $pct"
        "ATENCION" -> "Atención: torre de agua en $pct"
        else -> "Torre llena: $pct"
    }
}

private fun instruccion(tipo: String): String = when (tipo) {
    "CRITICA" -> "Encienda el bombeo ahora."
    "ATENCION" -> "Prepare el bombeo: el nivel está bajando."
    else -> "Detenga el bombeo."
}

@Composable
fun AlertasScreen(
    ui: UiState,
    puedeConfirmar: Boolean,
    esAdmin: Boolean,
    onConfirmar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val abiertas = ui.alertas.filter { it.cerradaEn == null }
    val anteriores = ui.alertas.filter { it.cerradaEn != null }.take(15)

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingPantalla)
    ) {
        Titulo("Alertas")
        Espacio()

        if (abiertas.isEmpty()) {
            TarjetaBlanca {
                EtiquetaEstado(EstadoUi.NORMAL)
                Espacio(8.dp)
                Text("No hay alertas abiertas.", style = MaterialTheme.typography.bodyLarge)
            }
        }

        abiertas.forEach { a ->
            TarjetaAlerta(a, ui.ahora, puedeConfirmar, onConfirmar)
            Espacio()
        }

        if (anteriores.isNotEmpty()) {
            Espacio(8.dp)
            Text("Anteriores", style = MaterialTheme.typography.titleMedium)
            Espacio(8.dp)
            TarjetaBlanca {
                anteriores.forEachIndexed { i, a ->
                    if (i > 0) Espacio(10.dp)
                    Text(tituloAlerta(a), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    val conf = if (a.confirmadaEn != null) "confirmada ${Fechas.fechaHora(a.confirmadaEn)}" else "sin confirmar"
                    Text(
                        "${Fechas.fechaHora(a.abiertaEn)} → ${Fechas.fechaHora(a.cerradaEn ?: a.abiertaEn)} · $conf",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario,
                    )
                }
            }
        }

        if (esAdmin && ui.bitacora.isNotEmpty()) {
            Espacio(16.dp)
            Text("Bitácora", style = MaterialTheme.typography.titleMedium)
            Espacio(8.dp)
            TarjetaBlanca {
                ui.bitacora.take(15).forEachIndexed { i, b ->
                    if (i > 0) Espacio(10.dp)
                    Text(b.texto, fontSize = 16.sp)
                    Text(Fechas.fechaHora(b.ts), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
            }
        }
    }
}

@Composable
private fun TarjetaAlerta(a: Alerta, ahora: Long, puedeConfirmar: Boolean, onConfirmar: (Int) -> Unit) {
    val minutos = ((ahora - a.abiertaEn) / 60_000L).coerceAtLeast(0)
    val confirmada = a.confirmadaEn != null
    TarjetaBlanca {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(colorAlerta(a.tipo), RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tituloAlerta(a), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(instruccion(a.tipo), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Desde las ${Fechas.hora(a.abiertaEn)} ($minutos min)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                )
            }
        }
        Espacio()
        if (confirmada) {
            Banner("Recibido a las ${Fechas.hora(a.confirmadaEn!!)}", exito = true)
        } else if (puedeConfirmar) {
            Button(
                onClick = { onConfirmar(a.id) },
                colors = ButtonDefaults.buttonColors(containerColor = VerdeRecibido, contentColor = TextoPrincipal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
            ) {
                Text("Recibido", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "Tu cargo no puede confirmar alertas.",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
            )
        }
    }
}
