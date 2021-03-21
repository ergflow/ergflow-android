package org.ergflow.rubric

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateUtils
import org.ergflow.Coach
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.cos

/**
 * Checks for proper forward body angle at the catch.
 */
class CatchAngle(coach: Coach) : BaseFaultChecker(coach) {

    override val strokeHistoryUnit = "°"
    private var maxAngle = 80

    override val title = "Catch Angle"
    override val description = "Checks for proper forward body angle at the catch."
    private var catchTimeOfBadStroke = 0L

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun getFaultInitialMessage(): String {
        return "Your catch angle is ${strokeHistory.last().toInt()}. Bend at the hips during the " +
            "recovery and make sure your " +
            "shoulders are in front of the hips at the catch."
    }

    override fun getFaultReminderMessage(): String {
        return "Focus on bending at the hips and maximizing stroke length at the catch."
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.CATCH) {
            rower.catchBodyAngle?.apply {
                // Catch angles outside of (30, 150) range are probably detection errors
                if (this > 30 && this < 150) {
                    strokeHistory.add(this.toFloat())
                    if (this <= maxAngle) {
                        goodStroke()
                    } else {
                        badStroke()
                        catchTimeOfBadStroke = rower.catchShoulder?.time ?: 0
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
                coach.targetPositions[0],
                display.green,
            )
        }
    }

    override fun faultReportDescription(): String {
        return """
            <h2>Improper Catch Angle</h2>
            <p>You should have a forward leaning body angle at the catch. 
             Bend just at the hips and not with the back.</p>
        """
    }

    override fun faultReportImageRow(): String {
        val out = ByteArrayOutputStream()
        val frame = rower.frames.find { it.time == catchTimeOfBadStroke }
        val copy = frame?.bitmap?.copy(frame.bitmap.config, true) ?: return ""
        val canvas = Canvas(copy)
        coach.display.drawLines(canvas, frame.points)
        val bodyLength = rower.averageBodyLength ?: 0f
        val a = (bodyLength * cos(Math.toRadians(70.0))).toFloat()
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
            </td></tr>
            <tr><td>
                <img src="data:image/jpg;base64, $imageData"/>
            </td></tr>
        """
    }

    override fun clear() {
        super.clear()
        maxAngle = 80
    }
}
