package cl.inacap.torreagua.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cl.inacap.torreagua.UiState
import cl.inacap.torreagua.data.Fechas
import cl.inacap.torreagua.data.Punto

@Composable
fun HistorialScreen(ui: UiState, onRango: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingPantalla)
    ) {
        Titulo("Historial")
        Espacio()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Día" to 24, "Semana" to 168, "Mes" to 720).forEach { (texto, horas) ->
                FilterChip(
                    selected = ui.rangoHoras == horas,
                    onClick = { onRango(horas) },
                    label = { Text(texto) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VerdeOscuro,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        Espacio()

        Aparece(0) { TarjetaBlanca {
            Text("Nivel de la torre (%)", style = MaterialTheme.typography.titleMedium)
            Espacio(8.dp)
            if (ui.puntos.size < 2) {
                Text(
                    "Aún no hay suficientes mediciones en este período.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextoSecundario,
                )
            } else {
                val llena = ui.umbrales.firstOrNull { it.tipo == "LLENA" }?.valor
                val atencion = ui.umbrales.firstOrNull { it.tipo == "ATENCION" }?.valor
                val critica = ui.umbrales.firstOrNull { it.tipo == "CRITICA" }?.valor
                Grafico(ui.puntos, llena, atencion, critica, ui.rangoHoras)
                Espacio(8.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fechas.fechaHora(ui.puntos.first().ts), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                    Text(Fechas.fechaHora(ui.puntos.last().ts), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                Espacio(8.dp)
                Leyenda(llena, atencion, critica)
            }
        } }

        if (ui.puntos.isNotEmpty()) {
            Espacio()
            Aparece(1) { TarjetaBlanca {
                val min = ui.puntos.minOf { it.nivel }
                val max = ui.puntos.maxOf { it.nivel }
                Text("Mínimo del período: ${entero(min)} %", style = MaterialTheme.typography.bodyLarge)
                Text("Máximo del período: ${entero(max)} %", style = MaterialTheme.typography.bodyLarge)
                Text("Mediciones: ${ui.puntos.size}", style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            } }
        }
    }
}

@Composable
private fun Leyenda(llena: Double?, atencion: Double?, critica: Double?) {
    Column {
        if (llena != null) Text("--- Llena ${entero(llena)} %", style = MaterialTheme.typography.bodySmall, color = AzulLlena)
        if (atencion != null) Text("--- Atención ${entero(atencion)} %", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        if (critica != null) Text("--- Crítico ${entero(critica)} %", style = MaterialTheme.typography.bodySmall, color = RojoCritico)
    }
}

@Composable
private fun Grafico(puntos: List<Punto>, llena: Double?, atencion: Double?, critica: Double?, clave: Int) {
    val progreso = remember(clave) { Animatable(0f) }
    LaunchedEffect(clave) { progreso.animateTo(1f, tween(1200)) }
    Row(Modifier.fillMaxWidth().height(220.dp)) {
        Column(
            Modifier.width(40.dp).height(220.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("100", "75", "50", "25", "0").forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        }
        Canvas(Modifier.weight(1f).height(220.dp)) {
            val w = size.width
            val h = size.height
            fun y(v: Double): Float = (h * (1f - (v.coerceIn(0.0, 100.0) / 100.0))).toFloat()

            listOf(0.0, 25.0, 50.0, 75.0, 100.0).forEach {
                drawLine(Borde, Offset(0f, y(it)), Offset(w, y(it)), strokeWidth = 1f)
            }
            val guion = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            llena?.let { drawLine(AzulLlena, Offset(0f, y(it)), Offset(w, y(it)), strokeWidth = 3f, pathEffect = guion) }
            atencion?.let { drawLine(Amarillo, Offset(0f, y(it)), Offset(w, y(it)), strokeWidth = 3f, pathEffect = guion) }
            critica?.let { drawLine(RojoCritico, Offset(0f, y(it)), Offset(w, y(it)), strokeWidth = 3f, pathEffect = guion) }

            val t0 = puntos.first().ts
            val t1 = puntos.last().ts
            val dt = (t1 - t0).coerceAtLeast(1L).toFloat()
            val ruta = Path()
            puntos.forEachIndexed { i, p ->
                val x = w * ((p.ts - t0).toFloat() / dt)
                val yy = y(p.nivel)
                if (i == 0) ruta.moveTo(x, yy) else ruta.lineTo(x, yy)
            }
            clipRect(right = w * progreso.value) {
                drawPath(ruta, VerdeOscuro, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }
    }
}
