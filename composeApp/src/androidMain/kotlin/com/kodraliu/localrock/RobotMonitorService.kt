package com.kodraliu.localrock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kodraliu.localrock.shared.notify.RobotNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the process, and with it the connection to the server, alive while the app is closed so
 * robot alerts still arrive. It holds no logic of its own: the watching is done by the
 * AppContainer's monitor, which runs for as long as the process does.
 */
class RobotMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as LocalRockApp).container
        scope.launch {
            // A process the system brought back for this service has no screen to load the robots.
            if (container.deviceRepository.devices.value.isEmpty()) {
                runCatching { container.deviceRepository.refresh() }
                    .onFailure { println("[VacLocal] monitor service: could not load robots: ${it.message}") }
            }
        }
        scope.launch {
            // Signing out or switching the setting off ends the watch wherever that happens.
            container.backgroundWatchWanted.first { !it }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RobotNotifications.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            // The service's own notification is also where a running clean is shown, so it is
            // built from whatever is running at this moment rather than from fixed text.
            ServiceCompat.startForeground(
                this,
                RobotNotifications.WATCH_ID,
                RobotNotifications.watchNotification(this),
                type,
            )
            RobotNotifications.watchRunning = true
        } catch (e: Exception) {
            // Android refuses a foreground start from the background (a restart after the process
            // was killed). The app starts the service again the next time it is opened.
            println("[VacLocal] monitor service could not go foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // From here on an update would bring the notification back with no service behind it.
        RobotNotifications.watchRunning = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Start or stop the service to match [wanted]. Starting needs the app to be on screen. */
        fun sync(context: Context, wanted: Boolean) {
            val intent = Intent(context, RobotMonitorService::class.java)
            if (wanted) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { println("[VacLocal] monitor service not started: ${it.message}") }
            } else {
                context.stopService(intent)
            }
        }
    }
}
