package org.ergflow.rubric

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateUtils
import android.util.Log
import org.ergflow.Coach
import org.ergflow.Frame
import org.ergflow.Point
import org.ergflow.Rower
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.abs

/**
 * Measures the change in body angle during the recovery before the
 * catch. Catch body angle should be established early in the recovery and should not change
 * at the catch.
 */
class LungingAtCatch(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Not Lunging at the Catch"
    override val description = "Measures the change in body angle during the recovery before the " +
        "catch. Catch body angle should be established early in the recovery and should not " +
        "change at the catch."
    override val strokeHistoryUnit = "Δ°"
    private val acceptableDeltaRange = -10.0..15.0

    private var preLungeAngle: Double? = null
    private var preLungeBitmap: Bitmap? = null
    private var preLungeHip: Point? = null
    private var preLungeShoulder: Point? = null
    private var preLungeEar: Point? = null
    private var catchTimeOfBadStroke = 0L
    private var preLungeTimeOfBadStroke = 0L

    init {
        registerWithCoach()
    }

    override fun clear() {
        super.clear()
        preLungeAngle = null
        preLungeBitmap = null
        preLungeHip = null
        preLungeShoulder = null
        preLungeEar = null
    }

    override fun getFaultInitialMessage(): String {
        return "You are lunging at the catch. Establish your catch angle early in the recovery " +
            "and maintain it until the catch."
    }

    override fun getFaultReminderMessage(): String {
        return "Focus on establishing your catch angle after the finish and maintaining it during" +
            " the catch."
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.RECOVERY_UPDATE) {
            if (rower.currentBodyAngle != null && rower.catchFinishPct > 20) {
                // Top of slide where hands should move with seat and body angle stays the same
                preLungeAngle = rower.currentBodyAngle!!
                preLungeBitmap = rower.currentBitmap
                preLungeHip = rower.currentHip
                preLungeShoulder = rower.currentShoulder
                preLungeEar = rower.currentEar
                Log.w(TAG, "preLungeAngle $preLungeAngle")
            }
        }
        if (event == Coach.Event.FINISH) {
            // Take the delta between catch angle and current body angle.
            val catchAngle = rower.catchBodyAngle!!
            var delta = (preLungeAngle?: catchAngle) - catchAngle
            Log.w(TAG, "delta $delta")
            preLungeAngle = rower.catchBodyAngle!!
            Log.w(TAG, "finish  preLungeAngle $preLungeAngle")

            if (delta !in acceptableDeltaRange) {
                // Often the delta is caused by shoulder detection error.
                // If delta is bad then check hip to ear angle delta.
                // If hip to ear delta is less than hip to shoulder then use that.
                if (preLungeHip != null && preLungeEar != null) {
                    val delta2 = rower.angle(preLungeHip!!, preLungeEar!!) -
                            rower.angle(rower.catchHip!!, rower.catchEar!!)
                    if (abs(delta2) < abs(delta)) {
                        delta = delta2
                    }
                }
            }

            strokeHistory.add(delta.toFloat())

            if (delta in acceptableDeltaRange) {
                goodStroke()
            } else {
                badStroke()
                catchTimeOfBadStroke = rower.catchShoulder?.time ?: 0
                preLungeTimeOfBadStroke = preLungeShoulder?.time ?: 0
            }

            preLungeAngle = 0.0
            preLungeBitmap = null
            preLungeHip = null
            preLungeShoulder = null
            preLungeEar = null
        }
    }

    override fun updateDisplay() {
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT || goodConsecutiveStrokes < 3) {
            if (rower.catchFinishPct < 50 &&
                (rower.phase == Rower.Phase.RECOVERY || rower.phase == Rower.Phase.CATCH)
            ) {
                val catchDeltax = rower.currentHip!!.x - rower.catchHip!!.x
                coach.display.drawLine(
                    (rower.catchShoulder!!.x + catchDeltax).toFloat(),
                    rower.currentShoulder!!.y.toFloat(),
                    rower.currentHip!!.x.toFloat(),
                    rower.currentHip!!.y.toFloat(),
                    coach.display.green,
                )
            }
        }
    }

    private fun annotateImage(
        canvas: Canvas,
        frame: Frame,
        catchBodyAngle: Double,
        catchHip: Point,
        catchShoulder: Point
    ) {

        val hip = frame.points[BodyPart.LEFT_HIP]!!
        val shoulder = frame.points[BodyPart.LEFT_SHOULDER]!!
        canvas.drawText(
            "${frame.bodyAngle.toInt()}° ${(frame.bodyAngle - catchBodyAngle).toInt()}Δ",
            (hip.x + 5).toFloat(),
            hip.y.toFloat(),
            coach.display.blackBorder,
        )
        canvas.drawText(
            "${frame.bodyAngle.toInt()}° ${(frame.bodyAngle - catchBodyAngle).toInt()}Δ",
            (hip.x + 5).toFloat(),
            hip.y.toFloat(),
            coach.display.smallYellow,
        )
        if (frame.strokePosPct < 30 && frame.phase != Rower.Phase.DRIVE) {
            val catchDeltax = hip.x - catchHip.x
            canvas.drawLine(
                (catchShoulder.x + catchDeltax).toFloat(),
                shoulder.y.toFloat(),
                hip.x.toFloat(),
                hip.y.toFloat(),
                Paint().apply {
                    color = Color.GREEN
                    strokeWidth = 3f
                },
            )
        }
    }

    override fun faultReportDescription(): String {
        return """
            <h2>lunging at the catch</h2>
            <p>${getFaultInitialMessage()}</p>
        """
    }

    override fun faultReportImageRow(): String {
        Log.i(TAG, "faultReportImageRow()")
        val catch = rower.frames.find { it.time == catchTimeOfBadStroke }
        if (catch == null) {
            Log.w(TAG, "catch frame with time $catchTimeOfBadStroke not found")
            return ""
        }
        val preLunge = rower.frames.find { it.time == preLungeTimeOfBadStroke }
        if (preLunge == null) {
            Log.w(TAG, "preLunge frame with time $preLungeTimeOfBadStroke not found")
            return ""
        }
        val time = DateUtils.formatElapsedTime(
            (catch.time - rower.startTime!!) /
                1000
        )
        val message = if (strokeHistory.last() > 0) "Lunging at the catch" else
            "Lunging at catch"
        var html =
            """
                <tr>
                    <td colspan="100">
                        $time stroke # ${catch.strokeCount} $message
                    </td>
                </tr>
                <tr>
                    <td>before catch</td><td>catch</td>
                </tr>
                <tr>
            """

        listOf(preLunge, catch).forEach { frame ->
            val copy = frame.bitmap!!.copy(frame.bitmap.config, true)
            val canvas = Canvas(copy)
            coach.display.drawLines(canvas, frame.points)
            annotateImage(
                canvas,
                frame,
                catch.bodyAngle,
                catch.points[BodyPart.LEFT_HIP]!!,
                catch.points[BodyPart.LEFT_SHOULDER]!!
            )
            val out = ByteArrayOutputStream()
            copy.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val imageData = Base64.getEncoder().encodeToString(out.toByteArray())
            html +=
                """
                    <td>
                        <img src="data:image/jpg;base64, $imageData"/>
                    </td>
                """
        }

        html += "</tr>"
        return html
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "LungingAtCatch"
    }
}
