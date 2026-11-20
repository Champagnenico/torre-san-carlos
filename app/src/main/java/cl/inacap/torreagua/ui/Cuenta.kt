package cl.inacap.torreagua.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.torreagua.UiState

private fun txt(v: Double?): String = if (v == null) "" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
fun CuentaScreen(
    ui: UiState,
    onGuardar: (Double, Double, Double) -> Unit,
    onCrearUsuario: (String, String, String, String, Int) -> Unit,
    onSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = ui.perfil
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingPantalla)
    ) {
        Titulo(if (p?.esAdmin == true) "Administrar" else "Mi cuenta")
        Espacio()

        if (p != null) {
            Aparece(0) {
                TarjetaBlanca {
                    Text(p.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(p.cargo ?: "", style = MaterialTheme.typography.bodyLarge)
                    Espacio(4.dp)
                    Text("Rol: ${p.rol}", style = MaterialTheme.typography.bodyMedium)
                    Text(p.email, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
                }
            }
            Espacio()
        }

        if (p?.esAdmin == true) {
            Aparece(1) { UmbralesEditor(ui, onGuardar) }
            Espacio()
            Aparece(2) { NuevoUsuario(ui, onCrearUsuario) }
            Espacio()
        }

        OutlinedButton(onClick = onSalir, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun UmbralesEditor(ui: UiState, onGuardar: (Double, Double, Double) -> Unit) {
    val actualLlena = ui.umbrales.firstOrNull { it.tipo == "LLENA" }?.valor
    val actualAtencion = ui.umbrales.firstOrNull { it.tipo == "ATENCION" }?.valor
    val actualCritica = ui.umbrales.firstOrNull { it.tipo == "CRITICA" }?.valor

    var llena by remember(actualLlena) { mutableStateOf(txt(actualLlena)) }
    var atencion by remember(actualAtencion) { mutableStateOf(txt(actualAtencion)) }
    var critica by remember(actualCritica) { mutableStateOf(txt(actualCritica)) }

    TarjetaBlanca {
        Text("Umbrales de alerta (%)", style = MaterialTheme.typography.titleMedium)
        Espacio(4.dp)
        Text(
            "Crítico < Atención < Llena. Cada cambio queda en la bitácora.",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
        )
        Espacio()
        CampoNumero("Llena (detener bombeo)", llena) { llena = it }
        Espacio(8.dp)
        CampoNumero("Atención", atencion) { atencion = it }
        Espacio(8.dp)
        CampoNumero("Crítico (encender bombeo)", critica) { critica = it }
        Espacio()
        Button(
            onClick = {
                val l = llena.replace(',', '.').toDoubleOrNull()
                val a = atencion.replace(',', '.').toDoubleOrNull()
                val c = critica.replace(',', '.').toDoubleOrNull()
                if (l != null && a != null && c != null) onGuardar(l, a, c) else onGuardar(-1.0, -1.0, -1.0)
            },
            enabled = !ui.guardando,
            colors = ButtonDefaults.buttonColors(containerColor = VerdeOscuro, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (ui.guardando) "Guardando…" else "Guardar umbrales")
        }
    }
}

@Composable
private fun CampoNumero(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NuevoUsuario(ui: UiState, onCrear: (String, String, String, String, Int) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var cargo by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf(2) }

    // Al crear con éxito se limpia el formulario.
    LaunchedEffect(ui.usuarioCreado) {
        if (ui.usuarioCreado > 0) {
            nombre = ""; cargo = ""; correo = ""; clave = ""; rol = 2
        }
    }

    TarjetaBlanca {
        Text("Crear usuario", style = MaterialTheme.typography.titleMedium)
        Espacio(4.dp)
        Text(
            "La persona entra con este correo y contraseña. Dile la contraseña en persona.",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario,
        )
        Espacio()
        OutlinedTextField(
            value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Espacio(8.dp)
        OutlinedTextField(
            value = cargo, onValueChange = { cargo = it }, label = { Text("Cargo (ej. Capataz)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Espacio(8.dp)
        OutlinedTextField(
            value = correo, onValueChange = { correo = it }, label = { Text("Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Espacio(8.dp)
        OutlinedTextField(
            value = clave, onValueChange = { clave = it }, label = { Text("Contraseña (mínimo 8)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Espacio()
        Text("Rol", style = MaterialTheme.typography.bodyMedium)
        Espacio(4.dp)
        listOf(listOf(1 to "Administrador", 2 to "Operador"), listOf(3 to "Mantención", 4 to "Reemplazante")).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { (id, texto) ->
                    FilterChip(
                        selected = rol == id,
                        onClick = { rol = id },
                        label = { Text(texto) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VerdeOscuro,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }
        Espacio()
        Button(
            onClick = { onCrear(correo, clave, nombre, cargo, rol) },
            enabled = !ui.creandoUsuario,
            colors = ButtonDefaults.buttonColors(containerColor = VerdeOscuro, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (ui.creandoUsuario) "Creando…" else "Crear usuario")
        }
    }
}
