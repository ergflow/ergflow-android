package org.ergflow.rubric

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.ergflow.Coach
import java.io.File
import java.io.FileWriter

/**
 * Base fault checker class.
 */
abstract class BaseFaultChecker(final override val coach: Coach) : FaultChecker {

    override var status = FaultChecker.Status.GOOD
    override val strokeHistory: MutableList<Float> = mutableListOf()
    override var initialMessageSent = false
    override var faultValueByStroke: MutableMap<Int, Float> = mutableMapOf()
    override var totalGoodStrokes = 0
    override var goodConsecutiveStrokes = 3
    override var badConsecutiveStrokes = 0
    override var numberOfFaultyStrokes = 0
    override var timeOfLastMessage = 0L
    private var numberOfStrokesReported = 0
    var lastReportedStroke = -1
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(coach.context)!!

    val rower = coach.rower

    override fun getTotalMark(): FaultChecker.Mark {
        return FaultChecker.Mark(totalGoodStrokes, strokeHistory.size)
    }

    fun registerWithCoach() {
        coach.listeners.add(this::onEvent)
    }

    abstract fun onEvent(event: Coach.Event)

    fun goodStroke() {
        totalGoodStrokes++
        goodConsecutiveStrokes++
        badConsecutiveStrokes = 0
    }

    /**
     * Report bad stroke.
     *
     * @return true if a fault message is queued
     */
    fun badStroke(): Boolean {
        badConsecutiveStrokes++
        goodConsecutiveStrokes = 0
        if (badConsecutiveStrokes > 3) {
            numberOfFaultyStrokes++
        }
        return false
    }

    open fun updateFaultReport(dir: File) {
        val faultReportDescription = faultReportDescription()
        if (faultReportDescription.isBlank() || numberOfStrokesReported >= 10) {
            return
        }
        val file = File(dir, "$title.html")
        Log.i(TAG, "updating fault report ${file.absolutePath}")
        FileWriter(file, true).use { out ->
            if (faultValueByStroke.size == 1) {
                // This is the first fault for this fault checker
                out.append(faultReportDescription)
            }
            out.append("<div style=\"page-break-inside:avoid\">")
            out.append(faultReportImageRow())
            out.append("</div>")
            numberOfStrokesReported++
        }
    }

    open fun faultReportImageRow(): String {
        return ""
    }

    open fun faultReportDescription(): String {
        return ""
    }

    override fun clear() {
        faultValueByStroke.clear()
        numberOfFaultyStrokes = 0
        strokeHistory.clear()
        totalGoodStrokes = 0
        initialMessageSent = false
        status = FaultChecker.Status.GOOD
        goodConsecutiveStrokes = 3
        numberOfStrokesReported = 0
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "BaseFaultChecker"
    }
}
