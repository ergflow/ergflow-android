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

class Layback(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Layback Position"
    override val description = "Checks for proper layback position at the finish."

    override val strokeHistoryUnit = "°"
    var minFinishAngle = 100
    var maxFinishAngle = 139
    var currentFaultType = ""
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

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.FINISH) {
            rower.finishBodyAngle?.apply {
                // Catch angles outside of (30, 160) range are probably detection errors
                if (this > 30 && this < 160) {
                    strokeHistory.add(this.toFloat())
                    if (this >= minFinishAngle && this <= maxFinishAngle) {
                        goodStroke()
                    } else {
                        badStroke()
                        finishTimeOfBadStroke = rower.finishWrist?.time ?: 0
                        badBodyAngle = this
                    }
                    when {
                        rower.legDeviationPercent > 15 -> {
                            // probably bad hip detection so ignore
                        }
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
            <tr><td>
                $time stroke # ${frame.strokeCount} 
                Ideal finish body angle is 110 degrees. Yours is ${badBodyAngle.toInt()}
            </td></tr>
            <tr><td>
                <img src="data:image/jpg;base64, $imageData"/>
            </td></tr>
        """
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "Layback"
    }
}
