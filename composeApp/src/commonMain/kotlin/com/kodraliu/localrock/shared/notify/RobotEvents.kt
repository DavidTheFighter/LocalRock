package com.kodraliu.localrock.shared.notify

import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus

/** Something about a robot that is worth interrupting someone for. */
sealed interface RobotEvent {
    /** The robot raised an error: stuck, brush blocked, and so on. */
    data class Error(val code: Int) : RobotEvent

    /** A clean run ended with the robot back on its dock. */
    data class CleanFinished(val areaMm2: Long?, val seconds: Long?) : RobotEvent

    /** The dock needs attention: water empty, dirty tank full, and so on. */
    data class DockAlert(val code: Int) : RobotEvent
}

/**
 * Turns a stream of status snapshots into [RobotEvent]s. Edge-triggered: a condition is reported
 * when it appears, not for as long as it lasts. A field the robot has not reported yet is unknown
 * rather than "fine", so whatever is already wrong when watching starts is the baseline and is
 * not announced.
 */
class RobotEventDetector {
    private var last = VacuumStatus()

    /**
     * True from the moment a clean run is seen until it ends, through the returning, washing and
     * recharging in between. This is the span a progress display should cover.
     */
    var runInProgress = false
        private set

    fun update(next: VacuumStatus): List<RobotEvent> {
        val prev = last
        last = next
        val events = mutableListOf<RobotEvent>()

        val error = next.errorCode
        if (error != null && error != 0 && prev.errorCode != null && prev.errorCode != error) {
            events += RobotEvent.Error(error)
        }

        val dock = next.dockErrorStatus
        if (dock != null && dock != 0 && prev.dockErrorStatus != null && prev.dockErrorStatus != dock) {
            events += RobotEvent.DockAlert(dock)
        }

        if (next.state in CLEANING_STATES) {
            runInProgress = true
        } else if (runInProgress && next.jobOver && next.state in RESTING_STATES) {
            runInProgress = false
            // Stopped out on the floor, or carried home with an error, is over but not finished.
            if (next.state in DOCKED_STATES && (next.errorCode ?: 0) == 0) {
                events += RobotEvent.CleanFinished(next.cleanArea, next.cleanTime)
            }
        }
        return events
    }

    private companion object {
        /**
         * Only these end a run. Returning, washing the mop and emptying the bin all happen in the
         * middle of one as well.
         */
        val DOCKED_STATES = setOf(VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE)
        val RESTING_STATES = DOCKED_STATES + setOf(VacuumStateCodes.IDLE, VacuumStateCodes.NOT_CHARGING)
    }
}

internal val CLEANING_STATES = setOf(
    VacuumStateCodes.CLEANING,
    VacuumStateCodes.SPOT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING,
    VacuumStateCodes.SEGMENT_CLEANING,
)

/**
 * in_cleaning stays non-zero while the robot recharges or washes its mop partway through a job,
 * which is what tells a break from the end. A robot that does not report it is taken at its word
 * once it is charging.
 */
private val VacuumStatus.jobOver: Boolean get() = (inCleaning ?: 0) == 0

/** What is shown to the user for one event, wherever it ends up being shown. */
data class RobotNotification(
    val duid: String,
    val title: String,
    val body: String,
    /** True for something that needs a person, false for good news. */
    val alert: Boolean,
)

fun RobotEvent.toNotification(duid: String, deviceName: String): RobotNotification = when (this) {
    is RobotEvent.Error -> RobotNotification(duid, deviceName, VacuumErrorCodes.describe(code), alert = true)
    is RobotEvent.DockAlert -> RobotNotification(duid, deviceName, DockErrorCodes.describe(code), alert = true)
    is RobotEvent.CleanFinished -> RobotNotification(duid, deviceName, finishedText(areaMm2, seconds), alert = false)
}

private fun finishedText(areaMm2: Long?, seconds: Long?): String {
    val parts = listOfNotNull(formatArea(areaMm2), formatMinutes(seconds))
    return if (parts.isEmpty()) "Cleaning finished" else "Cleaning finished: " + parts.joinToString(" in ")
}

/** A running clean job, for a display that stays up and is updated as the job goes on. */
data class RobotProgress(
    val duid: String,
    /** The robot and what it is doing, for example "S8: Cleaning in Kitchen". */
    val title: String,
    /** The numbers, for example "42% · 12.5 m² · 18 min · Battery 64%". */
    val detail: String,
    /** 0..100, or null when the robot does not report how far along it is. */
    val percent: Int?,
)

/** [room] is where the robot is now, when that is known; it is only shown while it is cleaning. */
fun progressOf(duid: String, deviceName: String, status: VacuumStatus, room: String?): RobotProgress {
    val error = status.errorCode ?: 0
    val activity = when {
        error != 0 -> VacuumErrorCodes.describe(error)
        else -> status.state?.let { VacuumStateCodes.describe(it) } ?: "Working"
    }
    val where = room?.takeIf { error == 0 && status.state in CLEANING_STATES }
    val percent = status.cleanPercent?.coerceIn(0, 100)
    val detail = listOfNotNull(
        percent?.let { "$it%" },
        formatArea(status.cleanArea),
        formatMinutes(status.cleanTime),
        status.battery?.let { "Battery $it%" },
    ).joinToString(" · ")
    return RobotProgress(
        duid = duid,
        title = "$deviceName: $activity" + (where?.let { " in $it" } ?: ""),
        detail = detail,
        percent = percent,
    )
}

/** mm² to "12.5 m²"; null when there is nothing to show yet. */
private fun formatArea(areaMm2: Long?): String? {
    if (areaMm2 == null || areaMm2 <= 0) return null
    val tenths = (areaMm2 + 50_000) / 100_000
    return "${tenths / 10}.${tenths % 10} m²"
}

private fun formatMinutes(seconds: Long?): String? =
    if (seconds == null || seconds <= 0) null else "${(seconds + 30) / 60} min"
