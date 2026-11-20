package cl.inacap.torreagua.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colores tomados del prototipo web v4
val VerdeOscuro = Color(0xFF1F4D2E)
val Fondo = Color(0xFFE8EEEA)
val Tarjeta = Color(0xFFFFFFFF)
val VerdeNormal = Color(0xFF1E7B3A)
val RojoCritico = Color(0xFFC62F2F)
val Amarillo = Color(0xFFF2B705)
val AzulLlena = Color(0xFF1F5FA8)
val GrisSinConexion = Color(0xFF5F6B66)
val VerdeRecibido = Color(0xFF3DDC84)
val LcdVerde = Color(0xFF9BB84A)
val LcdTexto = Color(0xFF1F2A12)
val TextoPrincipal = Color(0xFF14201A)
val TextoSecundario = Color(0xFF4A5A52)
val Borde = Color(0xFFC9D6CE)

private val Esquema = lightColorScheme(
    primary = VerdeOscuro,
    onPrimary = Color.White,
    background = Fondo,
    onBackground = TextoPrincipal,
    surface = Tarjeta,
    onSurface = TextoPrincipal,
    error = RojoCritico,
)

// Tipografía grande (base de 18 sp) como en el prototipo
private val Tipos = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun TemaTorre(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, typography = Tipos, content = contenido)
}
