package com.kodraliu.localrock.shared.notify

import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.vacuum.VacuumSession
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.applyDpsPush
import com.kodraliu.localrock.shared.vacuum.getStatus
import com.kodraliu.localrock.shared.vacuum.merge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/**
 * Watches robots for as long as the process lives, whichever screen is open. It keeps its own
 * light session per robot: the robot's pushes give state and error changes at once, and a status
 * poll covers what is never pushed (the dock, how far a job is, and whether it is over).
 *
 * It reports two things: [onEvent] for moments worth an alert, and [onProgress] with the state of
 * a running clean job each time it is looked at, or null once there is no job.
 */
class RobotMonitor(
    private val sessionFor: (Device) -> VacuumSession,
    private val ensureConnected: suspend () -> Unit,
    private val onEvent: (Device, RobotEvent) -> Unit,
    private val onProgress: (Device, RobotProgress?) -> Unit,
    /** Whether anyone is looking at the progress; finding the room costs a map download each time. */
    private val wantsRoom: () -> Boolean,
    private val rriotKey: () -> String,
    private val homeRoomName: (roomId: Long) -> String?,
    private val idlePollMs: Long = 30_000L,
    private val activePollMs: Long = 10_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var watching: Map<String, String?> = emptyMap()

    /** Watch exactly these robots; an empty list stops watching. */
    fun watch(devices: List<Device>) {
        val wanted = devices.associate { it.duid to it.localKey }
        if (wanted == watching) return
        watching = wanted
        job?.cancel()
        job = if (devices.isEmpty()) null else scope.launch {
            for (device in devices) launch { watchOne(device) }
        }
    }

    private suspend fun watchOne(device: Device) {
        connect()
        val session = sessionFor(device)
        val locator = RoomLocator(session, rriotKey, homeRoomName)
        val detector = RobotEventDetector()
        val lock = Mutex()
        var status = VacuumStatus()
        var room: String? = null

        suspend fun advance(change: (VacuumStatus) -> VacuumStatus) {
            val (events, progress) = lock.withLock {
                status = change(status)
                val events = detector.update(status)
                if (!detector.runInProgress) room = null
                events to if (detector.runInProgress) progressOf(device.duid, device.name, status, room) else null
            }
            onProgress(device, progress)
            for (event in events) {
                println("[VacLocal] monitor ${device.duid}: $event")
                onEvent(device, event)
            }
        }

        try {
            session.start()
            coroutineScope {
                launch {
                    // The session hands out everything pushed so far each time; only what changed
                    // is news. Replaying the rest would bring back an error the robot has cleared.
                    var seen = emptyMap<Int, JsonElement>()
                    session.dpsUpdates.collect { all ->
                        val changed = all.filter { (key, value) -> seen[key] != value }
                        seen = all
                        if (changed.isNotEmpty()) advance { it.applyDpsPush(changed) }
                    }
                }
                var tick = 0
                while (isActive) {
                    runCatching { session.ensureSubscribed() }
                    val fresh = quietly { session.getStatus() }
                    if (fresh != null) advance { it.merge(fresh) }

                    val cleaning = lock.withLock { detector.runInProgress && status.state in CLEANING_STATES }
                    if (cleaning && wantsRoom() && tick % ROOM_EVERY_N_POLLS == 0) {
                        val found = quietly { locator.currentRoom() }
                        // A failed lookup keeps the last answer: the robot has not left the room
                        // just because one map download timed out.
                        if (found != null) {
                            lock.withLock { room = found }
                            advance { it }
                        }
                    }
                    tick++
                    delay(if (lock.withLock { detector.runInProgress }) activePollMs else idlePollMs)
                }
            }
        } finally {
            withContext(NonCancellable) { session.close() }
            onProgress(device, null)
        }
    }

    /** Null when the robot does not answer or the request fails; watching goes on either way. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private suspend fun connect() {
        while (true) {
            try {
                ensureConnected()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("[VacLocal] monitor: cannot connect yet (${e.message})")
                delay(CONNECT_RETRY_MS)
            }
        }
    }

    private companion object {
        const val CONNECT_RETRY_MS = 30_000L

        /** The room is looked up on every second poll of a running job, so about every 20 s. */
        const val ROOM_EVERY_N_POLLS = 2
    }
}
