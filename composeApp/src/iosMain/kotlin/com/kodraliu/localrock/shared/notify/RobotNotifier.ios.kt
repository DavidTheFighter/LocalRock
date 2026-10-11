package com.kodraliu.localrock.shared.notify

import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

// Not compiled on the Windows machine this was written on; verify with an Xcode build.

/**
 * Only reached while the app is in the background, which on iOS is the short spell before it is
 * suspended. While the app is on screen the alert is shown in the app instead.
 */
private object IosRobotNotifier : RobotNotifier {
    override fun post(notification: RobotNotification) {
        val content = UNMutableNotificationContent()
        content.setTitle(notification.title)
        content.setBody(notification.body)
        content.setSound(UNNotificationSound.defaultSound)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "${notification.duid}-${if (notification.alert) "alert" else "finished"}",
            content = content,
            trigger = null,
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, withCompletionHandler = null)
    }

    // iOS has no standing notification to keep up to date. Its equivalent is a Live Activity,
    // which needs a widget extension and cannot be refreshed locally once the app is suspended.
    override fun showProgress(progress: RobotProgress) = Unit

    override fun clearProgress(duid: String) = Unit
}

actual fun platformRobotNotifier(): RobotNotifier = IosRobotNotifier

actual val backgroundAlertsSupported: Boolean = false
