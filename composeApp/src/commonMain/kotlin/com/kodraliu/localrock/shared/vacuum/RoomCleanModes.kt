package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * Per-room cleaning settings, "Customize" in the official app. Copied from a capture of the
 * official app on an S8 Pro Ultra (2026-10-11):
 * - the mode is switched on with set_clean_motor_mode and the three custom codes (106/204/302),
 *   which status reports from then on, and left with any ordinary set_clean_motor_mode;
 * - the rooms are written with set_customize_clean_mode in the need_retry form, between
 *   start_edit_map and end_edit_map, and read with get_customize_clean_mode;
 * - the run itself was an ordinary app_start.
 *
 * The app wrote three of the map's eight rooms and the robot returned exactly those three, so it
 * is not known whether a write replaces the list or adds to it. Every room on the map is sent to
 * stay clear of that. This robot has no pass count per room.
 */

/** One room's settings, with codes from [VacuumFanPower], [WaterBoxMode] and [MopRoute]. */
data class RoomCleanMode(val segment: Int, val fanPower: Int, val waterBoxMode: Int, val mopMode: Int)

/** Per-room settings are on if any of the three values is its custom code. */
fun isCustomizeModeSet(fanPower: Int?, waterBoxMode: Int?, mopMode: Int?): Boolean =
    fanPower == VacuumFanPower.CUSTOM || waterBoxMode == WaterBoxMode.CUSTOM || mopMode == MopRoute.CUSTOM

/** What a room without stored settings starts from: the values the official app left Customize with. */
fun defaultRoomCleanMode(segment: Int): RoomCleanMode =
    RoomCleanMode(segment, VacuumFanPower.BALANCED, WaterBoxMode.LOW, MopRoute.STANDARD)

/** One entry per room in [segments], the stored one where the robot has it. */
fun roomCleanModesFor(segments: List<Int>, stored: List<RoomCleanMode>): List<RoomCleanMode> {
    val bySegment = stored.associateBy { it.segment }
    return segments.map { bySegment[it] ?: defaultRoomCleanMode(it) }
}

/** `[{"fan_power", "mop_mode", "segment", "water_box_mode"}, ...]`, keys in the captured order. */
internal fun customizeCleanModeData(modes: List<RoomCleanMode>): JsonArray {
    if (modes.isEmpty()) throw MapEditException("There are no rooms to save settings for")
    if (modes.any { it.fanPower == VacuumFanPower.OFF && it.waterBoxMode == WaterBoxMode.OFF }) {
        throw MapEditException("Every room needs suction or water")
    }
    return JsonArray(modes.map { m ->
        JsonObject(mapOf(
            "fan_power" to JsonPrimitive(m.fanPower),
            "mop_mode" to JsonPrimitive(m.mopMode),
            "segment" to JsonPrimitive(m.segment),
            "water_box_mode" to JsonPrimitive(m.waterBoxMode),
        ))
    })
}

/** The get_customize_clean_mode answer; rows missing a value are skipped. */
internal fun parseCustomizeCleanMode(result: JsonElement?): List<RoomCleanMode> {
    val list = result as? JsonArray ?: return emptyList()
    return list.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        fun int(key: String) = runCatching { o[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }.getOrNull()
        RoomCleanMode(
            segment = int("segment") ?: return@mapNotNull null,
            fanPower = int("fan_power") ?: return@mapNotNull null,
            waterBoxMode = int("water_box_mode") ?: return@mapNotNull null,
            mopMode = int("mop_mode") ?: return@mapNotNull null,
        )
    }
}

/** True when the robot holds [sent], whatever else it holds and in whatever order. */
internal fun roomCleanModesStored(sent: List<RoomCleanMode>, stored: List<RoomCleanMode>): Boolean {
    fun normalized(m: RoomCleanMode) = m.copy(waterBoxMode = WaterBoxMode.normalize(m.waterBoxMode) ?: m.waterBoxMode)
    val held = stored.map(::normalized).toSet()
    return sent.all { normalized(it) in held }
}

suspend fun VacuumSession.getCustomizeCleanMode(): V1Response =
    sendCommand("get_customize_clean_mode").requireOk("get_customize_clean_mode")

suspend fun VacuumSession.setCustomizeCleanMode(modes: List<RoomCleanMode>): V1Response =
    sendData("set_customize_clean_mode", customizeCleanModeData(modes))
