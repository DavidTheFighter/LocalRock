package com.kodraliu.localrock.shared.vacuum.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomAtMmTest {

    // 4 x 2 cells; row 0 is the bottom of the map. Left half is segment 16, right half segment 17,
    // with a wall cell in the bottom-right corner. The grid starts at cell (100, 200) of the robot's
    // coordinate space, and a cell is 50 mm.
    private val map = ParsedMap(
        width = 4,
        height = 2,
        grid = byteArrayOf(
            16, 16, 17, 127,
            16, 16, 17, 17,
        ),
        rooms = listOf(ParsedMapRoom(16, "Room 16"), ParsedMapRoom(17, "Room 17")),
        resolution = 0.05f,
        pixelOffsetLeft = 100,
        pixelOffsetTop = 200,
    )

    private fun at(cellX: Int, cellY: Int) = ParsedMapPoint(x = cellX * 50 + 25, y = cellY * 50 + 25)

    @Test
    fun finds_the_room_under_the_robot() {
        assertEquals(16, map.roomAtMm(at(100, 200))?.id)
        assertEquals(16, map.roomAtMm(at(101, 201))?.id)
        assertEquals(17, map.roomAtMm(at(102, 200))?.id)
        assertEquals(17, map.roomAtMm(at(103, 201))?.id)
    }

    @Test
    fun a_wall_or_a_point_off_the_map_is_no_room() {
        assertNull(map.roomAtMm(at(103, 200)))
        assertNull(map.roomAtMm(at(99, 200)))
        assertNull(map.roomAtMm(at(100, 202)))
    }
}
