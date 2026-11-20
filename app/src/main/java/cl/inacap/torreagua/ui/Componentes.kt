package cl.inacap.torreagua.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.inacap.torreagua.Config
import cl.inacap.torreagua.data.Nivel
import java.text.NumberFormat
import java.util.Locale

/** Estados con color y palabra (nunca solo color), igual que el semáforo del nodo. */
enum class EstadoUi(val texto: String, val fondo: Color, val letra: Color) {
    NORMAL("Normal", VerdeNormal, Color.White),
    ATENCION("Atención", Amarillo, TextoPrincipal),
    CRITICA("Crítico", RojoCritico, Color.White),
    LLENA("Llena", AzulLlena, Color.White),
    SIN_CONEXION("Sensor sin conexión", GrisSinConexion, Color.White),
}

fun estadoUi(n: Nivel?, ahora: Long): EstadoUi {
    if (n == null) return EstadoUi.SIN_CONEXION
    if (ahora - n.ts > Config.SIN_CONEXION_MIN * 60_000L) return EstadoUi.SIN_CONEXION
    return when (n.estado) {
        "CRITICA" -> EstadoUi.CRITICA
        "ATENCION" -> EstadoUi.ATENCION
        "LLENA" -> EstadoUi.LLENA
        else -> EstadoUi.NORMAL
    }
}

private val formatoMiles: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CL"))

fun miles(v: Int): String = formatoMiles.format(v)
fun entero(v: Double): String = Math.round(v).toString()

// ------------------------------------------------------------------ Encabezado

@Composable
fun Encabezado(subtitulo: String = "Nivel de la torre de agua") {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VerdeOscuro)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("Fundo San Carlos", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(subtitulo, color = Color(0xFFD5E6DA), style = MaterialTheme.typography.bodySmall)
    }
}

// --------------------------------------------------------------------- Tarjeta

@Composable
fun TarjetaBlanca(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Tarjeta),
        border = BorderStroke(1.dp, Borde),
    ) {
        Column(Modifier.padding(16.dp), content = contenido)
    }
}

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(texto, style = MaterialTheme.typography.titleLarge, color = TextoPrincipal, modifier = modifier)
}

// ------------------------------------------------------------------ Etiqueta

@Composable
fun EtiquetaEstado(e: EstadoUi, modifier: Modifier = Modifier) {
    Row(
        modifier
            .latido(e == EstadoUi.CRITICA)
            .background(e.fondo, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e == EstadoUi.SIN_CONEXION) {
            Text("▲", color = e.letra, fontSize = 16.sp)
            Spacer(Modifier.size(6.dp))
        }
        Text(e.texto, color = e.letra, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

// ------------------------------------------------------------ Dibujo de la torre

@Composable
fun IconoTorre(pct: Double, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(pct) { anim.animateTo(pct.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val tanqueAlto = h * 0.68f
        val tanqueAncho = w * 0.8f
        val izq = (w - tanqueAncho) / 2f
        val relleno = tanqueAlto * (anim.value / 100f).coerceIn(0f, 1f)

        drawRoundRect(
            color = Color(0xFFDCE7F2),
            topLeft = Offset(izq, 0f),
            size = Size(tanqueAncho, tanqueAlto),
            cornerRadius = CornerRadius(10f, 10f),
        )
        drawRect(color = color, topLeft = Offset(izq, tanqueAlto - relleno), size = Size(tanqueAncho, relleno))
        drawRoundRect(
            color = TextoPrincipal,
            topLeft = Offset(izq, 0f),
            size = Size(tanqueAncho, tanqueAlto),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 5f),
        )
        // patas
        drawLine(TextoPrincipal, Offset(izq + tanqueAncho * 0.15f, tanqueAlto), Offset(izq, h), strokeWidth = 6f, cap = StrokeCap.Round)
        drawLine(TextoPrincipal, Offset(w / 2f, tanqueAlto), Offset(w / 2f, h), strokeWidth = 6f, cap = StrokeCap.Round)
        drawLine(TextoPrincipal, Offset(izq + tanqueAncho * 0.85f, tanqueAlto), Offset(izq + tanqueAncho, h), strokeWidth = 6f, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------- Pantalla y LED del nodo (réplica)

@Composable
fun PantallaLcd(linea1: String, linea2: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LcdVerde, RoundedCornerShape(8.dp))
            .border(3.dp, Color(0xFF3F4F1C), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(linea1, color = LcdTexto, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(linea2, color = LcdTexto, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
fun SemaforoLed(e: EstadoUi) {
    val verde = e == EstadoUi.NORMAL || e == EstadoUi.LLENA
    val amarillo = e == EstadoUi.ATENCION
    val rojo = e == EstadoUi.CRITICA
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Led(verde, Color(0xFF2ECC71))
        Led(amarillo, Color(0xFFF1C40F))
        Led(rojo, Color(0xFFE74C3C))
        Text("verde · amarillo · rojo", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
    }
}

@Composable
private fun Led(encendido: Boolean, color: Color, tam: Dp = 26.dp) {
    Box(
        Modifier
            .size(tam)
            .parpadeo(encendido)
            .background(if (encendido) color else Color(0xFF555B58), CircleShape)
    )
}

fun lineasLcd(n: Nivel?, e: EstadoUi): Pair<String, String> {
    if (n == null || e == EstadoUi.SIN_CONEXION) return "Sin lectura" to "Sensor sin conex."
    val l1 = "Torre: ${entero(n.nivelPct)} %"
    val l2 = when (e) {
        EstadoUi.NORMAL -> "Normal"
        EstadoUi.ATENCION -> "ATENCION"
        EstadoUi.CRITICA -> "CRITICO: bombear"
        EstadoUi.LLENA -> "LLENA: detener"
        EstadoUi.SIN_CONEXION -> ""
    }
    return l1 to l2
}

@Composable
fun Espacio(alto: Dp = 12.dp) = Spacer(Modifier.height(alto))

@Composable
fun Banner(texto: String, exito: Boolean) {
    val fondo = if (exito) Color(0xFFDFF5E6) else Color(0xFFFBE3E3)
    val borde = if (exito) VerdeNormal else RojoCritico
    Text(
        texto,
        color = TextoPrincipal,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo, RoundedCornerShape(12.dp))
            .border(2.dp, borde, RoundedCornerShape(12.dp))
            .padding(14.dp),
    )
}

val PaddingPantalla = PaddingValues(horizontal = 16.dp, vertical = 16.dp)


// ------------------------------------------------------------- Animaciones y adaptación

/** Pantalla ancha (tablet o celular horizontal): menú lateral y tarjetas en dos columnas. */
@Composable
fun esAncho(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** Aparece suavemente (fundido + deslizamiento) al entrar a la pantalla; no se repite al actualizar los datos. */
@Composable
fun Aparece(indice: Int = 0, contenido: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(indice * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 8 },
    ) { contenido() }
}

/** Número que cuenta desde 0 hasta el valor (y sigue suavemente cuando cambia). */
@Composable
fun rememberContador(valor: Double): Int {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(valor) { anim.animateTo(valor.toFloat(), tween(1000, easing = FastOutSlowInEasing)) }
    return Math.round(anim.value)
}

/** Latido: agranda y achica levemente (se usa en lo crítico). */
@Composable
fun Modifier.latido(activo: Boolean): Modifier {
    if (!activo) return this
    val t = rememberInfiniteTransition(label = "latido")
    val escala by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "escala")
    return this.graphicsLayer { scaleX = escala; scaleY = escala }
}

/** Parpadeo suave de luminosidad (LED encendido, barra de alerta sin confirmar). */
@Composable
fun Modifier.parpadeo(activo: Boolean): Modifier {
    if (!activo) return this
    val t = rememberInfiniteTransition(label = "parpadeo")
    val a by t.animateFloat(1f, 0.55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alfa")
    return this.graphicsLayer { alpha = a }
}
