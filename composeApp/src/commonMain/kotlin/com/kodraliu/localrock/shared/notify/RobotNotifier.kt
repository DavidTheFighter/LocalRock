package com.kodraliu.localrock.shared.notify

/** Shows a [RobotNotification] as a system notification, for when the app is not on screen. */
interface RobotNotifier {
    fun post(notification: RobotNotification)

    /**
     * Show, or bring up to date, the standing display of a running clean job. Where the platform
     * already keeps a notification up for the background watch, the job is shown in that one.
     */
    fun showProgress(progress: RobotProgress)

    /** Take that display down again. */
    fun clearProgress(duid: String)
}

expect fun platformRobotNotifier(): RobotNotifier

/**
 * Whether this platform lets the app keep its connection to the server open while it is closed.
 * Android does, through a foreground service. iOS suspends the app, and the only timely ways
 * around that are Apple's push service or an entitlement Apple has to grant.
 */
expect val backgroundAlertsSupported: Boolean
