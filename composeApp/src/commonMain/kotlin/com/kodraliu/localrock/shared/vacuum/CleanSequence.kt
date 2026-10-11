package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/*
 * Room cleaning order, "Cleaning sequence" in the official app. Copied from a capture of the
 * official app on an S8 Pro Ultra (2026-10-11): set_clean_sequence in the need_retry form with the
 * rooms in cleaning order, without start_edit_map / end_edit_map, and get_clean_sequence answering
 * with the same plain list. A robot with no order set answers `[]`. The list may name only some of
 * the rooms: `[17, 25]` was stored as sent, and the full clean that followed started in room 17.
 *
 * app_segment_clean has no order of its own that the robot follows (issue #8), so the order the
 * user picked rooms in is stored as the robot's sequence before the rooms are cleaned.
 */

/** `[23, 26, 19, 16, 22, 25, 17]` */
internal fun cleanSequenceData(order: List<Int>): JsonArray = JsonArray(order.map { JsonPrimitive(it) })

/** The get_clean_sequence answer; empty when the robot has no order or the shape is unexpected. */
internal fun parseCleanSequence(result: JsonElement?): List<Int> {
    val list = result as? JsonArray ?: return emptyList()
    return list.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
}

/**
 * The sequence to store so the robot cleans [picked] in that order: the picked rooms first, then
 * the rest of [stored] as it was. Rooms in neither stay out of the sequence, as they were. Null
 * when nothing has to change: fewer than two rooms, or [stored] already has the picked rooms in
 * that order.
 */
internal fun cleanSequenceFor(picked: List<Int>, stored: List<Int>): List<Int>? {
    val order = picked.distinct()
    if (order.size < 2) return null
    if (stored.filter { it in order } == order) return null
    return (order + stored).distinct()
}

suspend fun VacuumSession.getCleanSequence(): V1Response =
    sendCommand("get_clean_sequence").requireOk("get_clean_sequence")

suspend fun VacuumSession.setCleanSequence(order: List<Int>): V1Response =
    sendData("set_clean_sequence", cleanSequenceData(order))
