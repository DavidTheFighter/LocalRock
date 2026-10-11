package com.kodraliu.localrock.shared.notify

import com.kodraliu.localrock.shared.vacuum.VacuumSession
import com.kodraliu.localrock.shared.vacuum.createSecurityData
import com.kodraliu.localrock.shared.vacuum.getRoomMapping
import com.kodraliu.localrock.shared.vacuum.map.parseB01Map
import com.kodraliu.localrock.shared.vacuum.map.parseLegacyMap
import com.kodraliu.localrock.shared.vacuum.map.roomAtMm
import com.kodraliu.localrock.shared.vacuum.parseRoomMapping

/**
 * Works out which room a robot is in. The robot never says: it has to be read off the map, as the
 * room under the robot's position, and the room's name then comes from the home's room list.
 */
internal class RoomLocator(
    private val session: VacuumSession,
    private val rriotKey: () -> String,
    private val homeRoomName: (roomId: Long) -> String?,
) {
    /** The robot's segment-to-room table. It does not change during a job, so it is read once. */
    private var segmentToRoomId: Map<Int, Long> = emptyMap()

    /** Null when the map has no robot position, or the robot is not on any room's floor. */
    suspend fun currentRoom(): String? {
        val bytes = session.fetchMap(createSecurityData(rriotKey()), timeoutMs = MAP_TIMEOUT_MS).data
        val map = runCatching { parseB01Map(bytes) }.getOrNull()
            ?: runCatching { parseLegacyMap(bytes) }.getOrNull()
            ?: return null
        val room = map.robotMm?.let { map.roomAtMm(it) } ?: return null
        if (segmentToRoomId.isEmpty()) {
            segmentToRoomId = parseRoomMapping(session.getRoomMapping().result)
        }
        return segmentToRoomId[room.id]?.let(homeRoomName) ?: room.name
    }

    private companion object {
        const val MAP_TIMEOUT_MS = 20_000L
    }
}
