package cl.inacap.torreagua

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.inacap.torreagua.data.Alerta
import cl.inacap.torreagua.data.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: consulta las alertas en Supabase cada pocos segundos y las
 * muestra como notificaciones del celular, aunque la app esté cerrada.
 * Usa solo Supabase (sin Firebase). Se detiene solo al cerrar sesión.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trabajo: Job? = null
    private val ultimoAviso = HashMap<Int, Long>()   // id de alerta -> última vez que se avisó

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanales()
        val nota = notaFija()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_FIJA, nota, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID_FIJA, nota)
        }
        if (trabajo?.isActive != true) {
            trabajo = scope.launch { bucle() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun bucle() {
        val api = SupabaseApi.instancia(this)
        while (scope.isActive) {
            if (api.sesion == null) {          // se cerró sesión: ya no hay nada que avisar
                stopSelf()
                return
            }
            try {
                revisar(api.alertas())
            } catch (e: Exception) {
                // sin internet o sesión caducada: se reintenta en el próximo ciclo
            }
            delay(INTERVALO_MS)
        }
    }

    private fun revisar(alertas: List<Alerta>) {
        val ahora = System.currentTimeMillis()
        for (a in alertas) {
            if (a.cerradaEn != null || a.confirmadaEn != null) {
                ultimoAviso.remove(a.id)
                continue
            }
            val previo = ultimoAviso[a.id]
            val repetir = a.tipo == "CRITICA" && previo != null && ahora - previo >= REPETIR_CRITICA_MS
            if (previo == null || repetir) {
                avisar(a)
                ultimoAviso[a.id] = ahora
            }
        }
    }

    private fun avisar(a: Alerta) {
        val pct = a.nivelPct?.let { "%.0f %%".format(it) } ?: "—"
        val (titulo, texto) = when (a.tipo) {
            "CRITICA" -> "Nivel crítico de la torre" to "La torre está en $pct. Hay que bombear agua."
            "ATENCION" -> "Nivel bajo en la torre" to "La torre está en $pct. Conviene preparar el bombeo."
            "LLENA" -> "Torre llena" to "La torre llegó a $pct. Detener el bombeo."
            else -> "Alerta de la torre" to "Nivel actual: $pct."
        }
        val nota = NotificationCompat.Builder(this, CANAL_ALERTAS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(abrirApp())
            .build()
        try {
            NotificationManagerCompat.from(this).notify(1000 + a.id, nota)
        } catch (e: SecurityException) {
            // el usuario no dio permiso de notificaciones
        }
    }

    private fun abrirApp(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notaFija(): Notification =
        NotificationCompat.Builder(this, CANAL_FIJA)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Torre de agua")
            .setContentText("Monitoreo activo: te avisaremos si el nivel necesita atención.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(abrirApp())
            .build()

    private fun crearCanales() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_FIJA, "Monitoreo", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ALERTAS, "Alertas de nivel", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        private const val CANAL_FIJA = "monitoreo"
        private const val CANAL_ALERTAS = "alertas"
        private const val ID_FIJA = 1
        private const val INTERVALO_MS = 10_000L
        private const val REPETIR_CRITICA_MS = 15 * 60_000L

        fun iniciarSiHaySesion(context: Context) {
            if (SupabaseApi.instancia(context).sesion == null) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            } catch (e: Exception) {
                // el sistema puede negarlo si la app está en segundo plano; se reintenta al abrirla
            }
        }
    }
}
