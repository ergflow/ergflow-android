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

/**
 * Checks for proper forward shin angle at the catch.
 */
class ShinAngle(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Shin Angle"
    override val description = "Checks for proper forward shin angle at the catch."
    override val strokeHistoryUnit = "°"
    private var maxAngle = 110
    private var previousAngle = 0
    private var catchTimeOfBadStroke = 0L
    private var badAngle = 0.0

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun clear() {
        super.clear()
        previousAngle = 0
    }

    override fun getFaultInitialMessage(): String {
        return "Your shin angle at the catch is $previousAngle. Ideally the shins should be vertical at the catch."
    }

    override fun getFaultReminderMessage(): String {
        return "Focus on getting your shins as close as possible to vertical at the catch"
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun getTotalMark(): FaultChecker.Mark {
        return FaultChecker.Mark(totalGoodStrokes, strokeHistory.size)
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.CATCH) {
            rower.catchShinAngle?.apply {
                // Catch angles outside of (30, 180) range are probably detection errors
                if (this > 30 && this < 130) {
                    strokeHistory.add(this.toFloat())
                    if (this <= maxAngle) {
                        goodStroke()
                    } else {
                        badStroke()
                        catchTimeOfBadStroke = rower.catchShoulder?.time ?: 0
                        badAngle = this
                    }
                    previousAngle = this.toInt()
                }
            }
        }
    }

    override fun updateDisplay() {
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT || goodConsecutiveStrokes < 3) {
            coach.display.showTarget(
                listOf(BodyPart.LEFT_ANKLE, BodyPart.LEFT_KNEE),
                coach.targetPositions[0],
                coach.display.green,
            )
        }
    }

    override fun faultReportDescription(): String {
        return """
            <h2>Improper Shin Angle at the Catch</h2>
            <p>Ideally, the shins should be vertical at the catch.</p>
        """
    }

    override fun faultReportImageRow(): String {
        val out = ByteArrayOutputStream()
        val frame = rower.frames.find { it.time == catchTimeOfBadStroke }
        val copy = frame?.bitmap?.copy(frame.bitmap.config, true) ?: return ""
        val canvas = Canvas(copy)
        coach.display.drawLines(canvas, frame.points)
        val ankle = frame.points[BodyPart.LEFT_ANKLE] ?: return ""
        val knee = frame.points[BodyPart.LEFT_KNEE] ?: return ""
        canvas.drawLine(
            ankle.x.toFloat(),
            ankle.y.toFloat(),
            ankle.x.toFloat(),
            knee.y.toFloat(),
            Paint().apply {
                color = Color.GREEN
                strokeWidth = 3f
            }
        )
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
}
