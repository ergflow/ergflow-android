package org.ergflow.rubric

import org.ergflow.Coach
import kotlin.math.roundToInt

/**
 * Fault checker interface.
 */
interface FaultChecker {

    /**
     * Current status of the rower with respect to the implemented fault checker.
     */
    enum class Status {
        GOOD,
        FAULT_MESSAGE_SENT,
        IGNORE_AND_MOVE_ON,
    }

    /**
     * Object used to communicate overall status of the implemented fault for the current session.
     *
     * @param goodStrokes - number strokes that did not have a fault detected by the implemented FaultChecker
     * @param totalStrokes - total strokes tested by the implemented FaultChecker
     */
    class Mark(val goodStrokes: Int, val totalStrokes: Int) {
        val percent =
            if (totalStrokes == 0) 100 else (100 * goodStrokes / totalStrokes.toFloat()).roundToInt()
    }

    /** Fault title shown in report **/
    val title: String

    /** Fault description shown in report **/
    val description: String

    /** Fault status **/
    var status: Status

    /** Parent coach **/
    val coach: Coach

    /**
     * Each FaultChecker tests one scalar value such as body angle, or a y value difference in hand
     * levels compared to expected, etc...
     * This is the history of every value that is tested during the session for the implemented
     * fault checker.
     **/
    val strokeHistory: MutableList<Float>

    /**
     * The unit (e.g., "°") for the scalar that is tested.
     */
    val strokeHistoryUnit: String

    /**
     * Total of strokes tested by the implemented fault checker that did not have the tested fault
     * detected.
     */
    var totalGoodStrokes: Int

    /** Initial fault message sent. **/
    var initialMessageSent: Boolean

    /**
     * Number of current consecutive strokes without having implemented fault checker
     * detecting a fault.
     **/
    var goodConsecutiveStrokes: Int

    /**
     * Number of current consecutive strokes not having implemented fault detected.
     **/
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
