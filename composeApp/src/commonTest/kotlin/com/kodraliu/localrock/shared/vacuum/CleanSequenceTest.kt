package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Expected values are from the capture of the official app on an S8 Pro Ultra (2026-10-11). */
class CleanSequenceTest {

    private val captured = listOf(23, 26, 19, 16, 22, 25, 17)

    @Test
    fun sequence_data_matches_the_capture() {
        assertEquals("[23,26,19,16,22,25,17]", cleanSequenceData(captured).toString())
        // The robot also stored a list naming two of its rooms.
        assertEquals("[17,25]", cleanSequenceData(listOf(17, 25)).toString())
    }

    @Test
    fun sequence_answer_parses_to_the_room_order() {
        assertEquals(captured, parseCleanSequence(Json.parseToJsonElement("[23,26,19,16,22,25,17]")))
        // A robot with no order set.
        assertEquals(emptyList(), parseCleanSequence(Json.parseToJsonElement("[]")))
        assertEquals(emptyList(), parseCleanSequence(Json.parseToJsonElement("""{"status":1}""")))
        assertEquals(emptyList(), parseCleanSequence(null))
    }

    @Test
    fun picked_rooms_go_first_and_the_rest_keep_their_order() {
        assertEquals(
            listOf(17, 23, 26, 19, 16, 22, 25),
            cleanSequenceFor(picked = listOf(17, 23), stored = captured),
        )
    }

    @Test
    fun rooms_outside_the_pick_and_the_stored_order_stay_out() {
        assertEquals(listOf(22, 16), cleanSequenceFor(picked = listOf(22, 16), stored = emptyList()))
        assertEquals(listOf(25, 17), cleanSequenceFor(picked = listOf(25, 17), stored = listOf(17, 25)))
        assertEquals(listOf(16, 17, 25), cleanSequenceFor(picked = listOf(16, 17), stored = listOf(17, 25)))
    }

    @Test
    fun nothing_is_written_when_the_order_already_holds_or_does_not_matter() {
        assertNull(cleanSequenceFor(picked = listOf(26, 16, 17), stored = captured))
        assertNull(cleanSequenceFor(picked = listOf(17), stored = captured))
        assertNull(cleanSequenceFor(picked = listOf(17, 17), stored = captured))
        assertNull(cleanSequenceFor(picked = emptyList(), stored = captured))
    }
}
