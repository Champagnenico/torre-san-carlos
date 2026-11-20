package cl.inacap.torreagua

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.inacap.torreagua.ui.AppRoot
import cl.inacap.torreagua.ui.TemaTorre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Avisos al celular: permiso (Android 13+) y servicio de monitoreo.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Actualiza los datos cada pocos segundos mientras la app está visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    MonitorService.iniciarSiHaySesion(this@MainActivity)
                    vm.refrescar()
                    delay(Config.REFRESCO_MS)
                }
            }
        }

        setContent {
            TemaTorre {
                AppRoot(vm)
            }
        }
    }
}
