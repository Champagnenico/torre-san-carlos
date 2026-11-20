package cl.inacap.torreagua.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.torreagua.UiState

@Composable
fun LoginScreen(ui: UiState, onEntrar: (String, String) -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fondo)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Encabezado("Inicia sesión para ver el nivel")
        Column(Modifier.padding(16.dp)) {
            Titulo("Iniciar sesión")
            Espacio()
            Aparece(0) { TarjetaBlanca {
                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui.errorLogin != null) {
                    Spacer(Modifier.height(12.dp))
                    Banner(ui.errorLogin, exito = false)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onEntrar(correo, clave) },
                    enabled = !ui.cargandoLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeOscuro, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (ui.cargandoLogin) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.height(24.dp))
                    } else {
                        Text("Entrar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } }
            Spacer(Modifier.height(12.dp))
            Text(
                "El acceso a este sistema es administrado por el Fundo Agrícola San Carlos. Las cuentas son creadas exclusivamente por el administrador; si requiere acceso, le solicitamos dirigirse a él.",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
            )
        }
    }
}

@Composable
fun SinAccesoScreen(onSalir: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fondo)) {
        Encabezado()
        Column(Modifier.padding(16.dp)) {
            Titulo("Sin acceso")
            Espacio()
            TarjetaBlanca {
                Text(
                    "Tu cuenta existe, pero todavía no tiene permisos para ver el sistema " +
                        "(puede estar inactiva o vencida). Avisa al administrador.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onSalir, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}
