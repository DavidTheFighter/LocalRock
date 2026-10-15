package com.kodraliu.localrock.shared.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kodraliu.localrock.MainActivity
import com.kodraliu.localrock.R
import com.kodraliu.localrock.shared.protocol.appContextOrNull

internal object RobotNotifications {
    const val CHANNEL_ALERTS = "robot_alerts"
    const val CHANNEL_FINISHED = "robot_finished"
    const val CHANNEL_WATCHING = "robot_watching"

    /** Intent extra naming the robot a tapped notification should open. */
    const val EXTRA_OPEN_DUID = "open_duid"

    /** The background watch's own notification, which doubles as the progress display. */
    const val WATCH_ID = 1

    /** Set by the service. Its notification must only be touched while it is the one showing it. */
    @Volatile
    var watchRunning = false

    /** Clean jobs running right now, by robot, in the order they started. */
    private val jobs = LinkedHashMap<String, RobotProgress>()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ALERTS, "Robot needs attention", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Errors, a stuck robot, and dock problems" },
                NotificationChannel(CHANNEL_FINISHED, "Cleaning finished", NotificationManager.IMPORTANCE_DEFAULT),
                // Low importance: during a clean it changes every few seconds and must never make a sound.
                NotificationChannel(CHANNEL_WATCHING, "Background watch and cleaning progress", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while LocalRock watches your robots; follows a clean while one runs" },
            )
        )
    }

    /** Opens the app, on [duid]'s screen when one is given. */
    fun openAppIntent(context: Context, duid: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (duid != null) intent.putExtra(EXTRA_OPEN_DUID, duid)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Record a robot's running job, or its end with null, and bring the notification up to date. */
    fun setJob(context: Context, duid: String, progress: RobotProgress?) {
        synchronized(jobs) {
            if (progress == null) jobs.remove(duid) else jobs[duid] = progress
        }
        if (watchRunning) show(context, WATCH_ID, watchNotification(context))
    }

    /**
     * The one standing notification. With nothing running it only says the watch is on; during a
     * clean it shows that job instead, so there are never two notifications saying related things.
     */
    fun watchNotification(context: Context): Notification {
        val running = synchronized(jobs) { jobs.values.toList() }
        val builder = NotificationCompat.Builder(context, CHANNEL_WATCHING)
            .setSmallIcon(R.drawable.ic_stat_robot)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        when (running.size) {
            0 -> builder
                .setContentTitle("Watching your robots")
                .setContentText("Alerts arrive while your phone can reach your server.")
                .setContentIntent(openAppIntent(context, duid = null, requestCode = WATCH_ID))
            1 -> {
                val job = running.single()
                builder
                    .setContentTitle(job.title)
                    .setContentText(job.detail)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(openAppIntent(context, job.duid, requestCode = WATCH_ID))
                job.percent?.let { builder.setProgress(100, it, false) }
            }
            else -> {
                val lines = NotificationCompat.InboxStyle()
                for (job in running) lines.addLine(job.title + (job.percent?.let { " · $it%" } ?: ""))
                builder
                    .setContentTitle("${running.size} robots cleaning")
                    .setContentText(running.first().title)
                    .setStyle(lines)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(openAppIntent(context, duid = null, requestCode = WATCH_ID))
            }
        }
        return builder.build()
    }

    fun show(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // The permission was taken away after alerts were switched on.
            println("[VacLocal] notification not shown: ${e.message}")
        }
    }
}

private object AndroidRobotNotifier : RobotNotifier {
    override fun post(notification: RobotNotification) {
        val context = appContextOrNull() ?: return
        RobotNotifications.ensureChannels(context)
        // One slot per robot and kind, so a newer alert replaces the one it supersedes.
        val id = "${notification.duid}-${notification.alert}".hashCode()
        val built = NotificationCompat.Builder(
            context,
            if (notification.alert) RobotNotifications.CHANNEL_ALERTS else RobotNotifications.CHANNEL_FINISHED,
        )
            .setSmallIcon(R.drawable.ic_stat_robot)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setPriority(if (notification.alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (notification.alert) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(RobotNotifications.openAppIntent(context, notification.duid, id))
            .setAutoCancel(true)
            .build()
        RobotNotifications.show(context, id, built)
    }

    override fun showProgress(progress: RobotProgress) {
        val context = appContextOrNull() ?: return
        RobotNotifications.setJob(context, progress.duid, progress)
    }

    override fun clearProgress(duid: String) {
        val context = appContextOrNull() ?: return
        RobotNotifications.setJob(context, duid, null)
    }
}

actual fun platformRobotNotifier(): RobotNotifier = AndroidRobotNotifier

actual val backgroundAlertsSupported: Boolean = true
