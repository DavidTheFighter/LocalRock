package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Expected values are from the capture of the official app on an S8 Pro Ultra (2026-10-11). */
class RoomCleanModesTest {

    private val captured = listOf(
        RoomCleanMode(segment = 22, fanPower = 105, waterBoxMode = 201, mopMode = 300),
        RoomCleanMode(segment = 25, fanPower = 102, waterBoxMode = 202, mopMode = 300),
        RoomCleanMode(segment = 17, fanPower = 102, waterBoxMode = 200, mopMode = 300),
    )

    @Test
    fun customize_data_matches_the_capture() {
        assertEquals(
            """[{"fan_power":105,"mop_mode":300,"segment":22,"water_box_mode":201},""" +
                """{"fan_power":102,"mop_mode":300,"segment":25,"water_box_mode":202},""" +
                """{"fan_power":102,"mop_mode":300,"segment":17,"water_box_mode":200}]""",
            customizeCleanModeData(captured).toString(),
        )
    }

    @Test
    fun customize_answer_parses_to_the_rows_that_were_sent() {
        val answer = Json.parseToJsonElement(
            """[{"segment":22,"fan_power":105,"water_box_mode":201,"mop_mode":300},""" +
                """{"segment":25,"fan_power":102,"water_box_mode":202,"mop_mode":300},""" +
                """{"segment":17,"fan_power":102,"water_box_mode":200,"mop_mode":300}]"""
        )
        assertEquals(captured, parseCustomizeCleanMode(answer))
    }

    @Test
    fun empty_or_malformed_answers_parse_to_no_rows() {
        assertEquals(emptyList(), parseCustomizeCleanMode(Json.parseToJsonElement("[]")))
        assertEquals(emptyList(), parseCustomizeCleanMode(Json.parseToJsonElement("""["ok"]""")))
        assertEquals(emptyList(), parseCustomizeCleanMode(Json.parseToJsonElement("""[{"segment":22}]""")))
        assertEquals(emptyList(), parseCustomizeCleanMode(null))
    }

    @Test
    fun a_room_without_suction_and_water_is_refused() {
        assertFailsWith<MapEditException> {
            customizeCleanModeData(listOf(RoomCleanMode(16, VacuumFanPower.OFF, WaterBoxMode.OFF, MopRoute.STANDARD)))
        }
        assertFailsWith<MapEditException> { customizeCleanModeData(emptyList()) }
    }

    @Test
    fun customize_mode_is_set_when_any_value_is_custom() {
        // Status after the official app's Customize tab: 106/204/302.
        assertTrue(isCustomizeModeSet(106, 204, 302))
        assertTrue(isCustomizeModeSet(VacuumFanPower.CUSTOM, WaterBoxMode.LOW, MopRoute.STANDARD))
        assertFalse(isCustomizeModeSet(VacuumFanPower.BALANCED, WaterBoxMode.LOW, MopRoute.STANDARD))
        assertFalse(isCustomizeModeSet(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART))
        assertFalse(isCustomizeModeSet(null, null, null))
    }

    @Test
    fun every_room_gets_an_entry_stored_or_default() {
        val modes = roomCleanModesFor(listOf(16, 22), captured)
        assertEquals(listOf(defaultRoomCleanMode(16), captured[0]), modes)
        // 102/201/300, what the official app set when it left Customize.
        assertEquals(RoomCleanMode(16, 102, 201, 300), defaultRoomCleanMode(16))
    }

    @Test
    fun stored_check_ignores_order_extra_rows_and_water_slide_codes() {
        assertTrue(roomCleanModesStored(captured, captured.reversed()))
        assertTrue(roomCleanModesStored(captured.take(2), captured))
        assertFalse(roomCleanModesStored(captured, captured.take(2)))
        assertFalse(roomCleanModesStored(captured, captured.map { it.copy(fanPower = 103) }))
        val sent = listOf(RoomCleanMode(16, 102, WaterBoxMode.MEDIUM, 300))
        assertTrue(roomCleanModesStored(sent, listOf(RoomCleanMode(16, 102, 235, 300))))
    }
}
