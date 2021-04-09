package org.ergflow.rubric

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateUtils
import android.util.Log
import org.ergflow.Coach
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Checks for proper layback position at the finish.
 */
class Layback(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Layback Position"
    override val description = "Checks for proper layback position at the finish."

    override val strokeHistoryUnit = "°"
    private var minFinishAngle = 95
    private var maxFinishAngle = 139
    private var currentFaultType = ""
    private var finishTimeOfBadStroke = 0L
    private var badBodyAngle = 0.0

    override fun getFaultInitialMessage(): String {
        return "$currentFaultType Ideal finish body angle is 110 degrees. Yours is ${strokeHistory
            .last().toInt()}. Try to match the angle of the green line at the finish"
    }

    override fun getFaultReminderMessage(): String {
        return "$currentFaultType Ideal finish body angle is 110 degrees. Yours is ${strokeHistory
            .last().toInt()}"
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun getThresholdInfo(): String {
        return "[$minFinishAngle, $maxFinishAngle] $strokeHistoryUnit"
    }

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.FINISH) {
            rower.finishBodyAngle?.apply {
                if (detectionLooksOk()) {
                    strokeHistory.add(this.toFloat())
                    if (this >= minFinishAngle && this <= maxFinishAngle) {
                        goodStroke()
                    } else {
                        badStroke()
                        finishTimeOfBadStroke = rower.timeOfLatestFinish
                        badBodyAngle = this
                    }
                    when {
                        this < minFinishAngle -> {
                            currentFaultType = "Not enough layback at the finish. "
                        }
                        this > maxFinishAngle -> {
                            currentFaultType = "Too much layback at the finish. "
                        }
                    }
                }
            }
        }
    }

    /**
     * Extra heuristics to ignore errors in detection of the finish position.
     */
    private fun detectionLooksOk(): Boolean {
        val finishAngle = rower.finishBodyAngle ?: return false
        val elbow = rower.finishElbow ?: return false
        val shoulder = rower.finishShoulder ?: return false

        // Assume error in detection if finish angle out of this range
        if (finishAngle !in 60.0..160.0) {
            Log.w(
                TAG,
                "Stroke ${rower.strokeCount}: Invalid detection for finish angle $finishAngle"
            )
            return false
        }

        // Assume error in detecting finish if elbow is not past body.
        // If elbow is in front of body then humerus angle will be greater than body angle.
        val humerusAngle = rower.angle(elbow, shoulder)
        if (humerusAngle > finishAngle + 10) {
            Log.w(
                TAG,
                "Stroke ${rower.strokeCount}: Invalid detection " +
                    "humerus angle $humerusAngle finish angle $finishAngle"
            )
            return false
        }

        return true
    }

    override fun updateDisplay() {
        val display = coach.display
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT || goodConsecutiveStrokes < 1) {
            display.showTarget(
                listOf(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
                coach.targetPositions[15],
                display.green,
            )
        }
    }

    override fun clear() {
        super.clear()
        currentFaultType = ""
    }

    override fun faultReportDescription(): String {
        return """
            <h2>Improper Layback Position</h2>
            <p>Ideal finish body angle is around 110 degrees. You want to maximize stroke length at 
            the finish with a good layback position and bending at the hips without slouching. Too 
            much layback will fatigue your abs and not help with performance.  
            </p>
        """
    }

    override fun faultReportImageRow(): String {
        val out = ByteArrayOutputStream()
        val frame = rower.frames.find { it.time == finishTimeOfBadStroke }
        frame?.also {
            if (it.strokeCount == lastReportedStroke) {
                return ""
            }
            lastReportedStroke = it.strokeCount
        }
        val copy = frame?.bitmap?.copy(frame.bitmap.config, true)
        if (copy == null) {
            Log.w(TAG, "finishTimeOfBadStroke not found: $finishTimeOfBadStroke")
            return ""
        }
        val canvas = Canvas(copy)
        coach.display.drawLines(canvas, frame.points)
        val bodyLength = rower.averageBodyLength ?: 0f
        val a = (bodyLength * cos(Math.toRadians(110.0))).toFloat()
        frame.points[BodyPart.LEFT_HIP]?.apply {
            canvas.drawLine(
                x.toFloat(),
                y.toFloat(),
                x - a,
                y.toFloat() - bodyLength,
                Paint().apply {
                    color = Color.GREEN
                    strokeWidth = 3f
                }
            )
        }
        copy.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val imageData = Base64.getEncoder().encodeToString(out.toByteArray())
        val time = DateUtils.formatElapsedTime(
            (frame.time - rower.startTime!!) /
                1000
        )
        return """
            <table>
            <tr><td>
                $time stroke # ${frame.strokeCount} 
                Ideal finish body angle is 110 degrees. Yours is ${badBodyAngle.roundToInt()}
            </td></tr>
            <tr><td>
                <img src="data:image/jpg;base64, $imageData"/>
            </td></tr>
            </table>
        """
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "Layback"
    }
}
