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
import java.util.Base64

/**
 * Looks for opening up too early or shooting the slide faults by measuring the change in body
 * angle during the early drive.
 */
class EarlyDriveBodyAngle(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Early Drive"
    override val description = "Looks for opening up too early or shooting the slide faults by " +
        "measuring the change in body angle during the early drive."
    override val strokeHistoryUnit = "Δ°"
    private val acceptableDeltaRange = -5.0..17.0

    private var preOpenAngle = 0.0
    private var preOpenBitmap: Bitmap? = null
    private var preOpenHip: Point? = null
    private var preOpenShoulder: Point? = null
    private var catchTimeOfBadStroke = 0L
    private var preOpenTimeOfBadStroke = 0L

    init {
        registerWithCoach()
    }

    override fun clear() {
        super.clear()
        preOpenAngle = 0.0
        preOpenBitmap = null
        preOpenHip = null
        preOpenShoulder = null
    }

    override fun getFaultInitialMessage(): String {
        return "You are opening up too early. Push with the legs at the catch don't pull with the arms. Maintain your catch angle until your hands pass your shins"
    }

    override fun getFaultReminderMessage(): String {
        return "Focus on maintaining your catch angle during the early drive. Hands and seat should move in unison until hands pass the shins"
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.DRIVE_UPDATE) {
            if (rower.currentBodyAngle != null && rower.catchFinishPct < 30) {
                // Top of slide where hands should move with seat and body angle stays the same
                preOpenAngle = rower.currentBodyAngle!!
                preOpenBitmap = rower.currentBitmap
                preOpenHip = rower.currentHip
                preOpenShoulder = rower.currentShoulder
                Log.w(TAG, "preOpenAngle $preOpenAngle")
            }
        }
        if (event == Coach.Event.FINISH) {
            // Take the delta between catch angle and current body angle.
            val delta = preOpenAngle - rower.catchBodyAngle!!
            Log.w(TAG, "delta $delta")
            preOpenAngle = rower.catchBodyAngle!!
            Log.w(TAG, "finish  preOpenAngle $preOpenAngle")

            if (rower.strokeCount < 2) {
                return
            }
            strokeHistory.add(delta.toFloat())

            if (delta in acceptableDeltaRange) {
                goodStroke()
            } else {
                badStroke()
                catchTimeOfBadStroke = rower.catchShoulder?.time ?: 0
                preOpenTimeOfBadStroke = preOpenShoulder?.time ?: 0
            }
        }
    }

    override fun updateDisplay() {
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT || goodConsecutiveStrokes < 3) {
            if (rower.catchFinishPct < 40 && rower.phase != Rower.Phase.RECOVERY) {
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
        if (frame.strokePosPct < 30 && frame.phase != Rower.Phase.RECOVERY) {
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
            <h2>Not Maintaining Body Angle During the Initial Drive</h2>
            <p>You should use only the legs during the early part of the drive. 
            Your forward body angle should not change until your hands pass your shins.</p>
        """
    }

    override fun faultReportImageRow(): String {
        Log.i(TAG, "faultReportImageRow()")
        val catch = rower.frames.find { it.time == catchTimeOfBadStroke }
        if (catch == null) {
            Log.w(TAG, "catch frame with time $catchTimeOfBadStroke not found")
            return ""
        }
        val preOpen = rower.frames.find { it.time == preOpenTimeOfBadStroke }
        if (preOpen == null) {
            Log.w(TAG, "preopen frame with time $preOpenTimeOfBadStroke not found")
            return ""
        }
        val time = DateUtils.formatElapsedTime(
            (catch.time - rower.startTime!!) /
                1000
        )
        val message = if (strokeHistory.last() > 0) "Opening up too early" else "Shooting the slide"
        var html =
            """
                <tr>
                    <td colspan="100">
                        $time stroke # ${catch.strokeCount} $message
                    </td>
                </tr>
                <tr>
            """

        listOf(catch, preOpen).forEach { frame ->
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
        private const val TAG = "EarlyDriveAngle"
    }
}
