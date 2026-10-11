package com.kodraliu.localrock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.notify.RobotNotifications
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        container = (application as LocalRockApp).container
        openRobotFrom(intent)

        // Android only allows a foreground service to be started while the app is on screen, so
        // the background watch is brought in line with the setting here rather than from the setting.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.backgroundWatchWanted.collect { wanted ->
                    RobotMonitorService.sync(this@MainActivity, wanted)
                }
            }
        }

        setContent { App(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRobotFrom(intent)
    }

    /** A tapped robot notification carries the robot it is about. */
    private fun openRobotFrom(intent: Intent?) {
        val duid = intent?.getStringExtra(RobotNotifications.EXTRA_OPEN_DUID) ?: return
        intent.removeExtra(RobotNotifications.EXTRA_OPEN_DUID)
        container.requestOpenDevice(duid)
    }
}
