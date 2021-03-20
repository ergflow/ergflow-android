package org.ergflow.rubric

import org.ergflow.Coach
import kotlin.math.roundToInt

interface FaultChecker {

    enum class Status {
        GOOD,
        FAULT_MESSAGE_SENT,
        IGNORE_AND_MOVE_ON,
    }

    companion object {
        val FAULT_STATES: List<Status> = listOf(
            Status.FAULT_MESSAGE_SENT,
            Status.IGNORE_AND_MOVE_ON,
        )
    }

    class Mark(val goodStrokes: Int, val totalStrokes: Int) {
        val percent =
            if (totalStrokes == 0) 100 else (100 * goodStrokes / totalStrokes.toFloat()).roundToInt()
    }

    val title: String
    val description: String
    val coach: Coach
    var status: Status
    val strokeHistory: MutableList<Float>
    val strokeHistoryUnit: String
    var totalGoodStrokes: Int
    var remindAfter: Int
    var minStrokesBeforeResending: Int
    var initialMessageSent: Boolean
    var goodConsecutiveStrokes: Int
    var badConsecutiveStrokes: Int
    var numberOfFaultyStrokes: Int
    var timeOfLastMessage: Long

    /**
     * Fault map key by stroke count when fault happened and strokeHistory value.
     */
    var faults: MutableMap<Int, Float>

    fun getFaultInitialMessage(): String

    fun getFaultReminderMessage(): String

    fun getFixedMessage(): String

    fun getTotalMark(): Mark

    /**
     * Called for every frame so that the fault checker can update the display.
     */
    fun updateDisplay() {}

    fun clear()
}
