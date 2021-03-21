package org.ergflow.rubric

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.format.DateUtils
import android.util.Log
import org.ergflow.Coach
import org.ergflow.Frame
import org.ergflow.Rower
import org.ergflow.Stats
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_HIP
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_KNEE
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_WRIST
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Check that knees stay down as hands pass during
 * the recovery. The measured value is the difference in knee height at the finish
 * compared to knee height when hands pass the thighs.
 */
class HandsOut(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Hands Out"
    override val description = "Check that knees stay down as hands pass during " +
        "the recovery. The measured value is the difference in knee height at the finish " +
        "compared to knee height when hands pass the thighs."
    override val strokeHistoryUnit = "Δ"
    private var maxKneeDelta = 25 // will be set later relative to body length
    private val maxKneeDeltaBodyRatio = 0.15
    private var kneeDeltaBeforeHandsPass: Int? = null
    private var midThighX = 210
    private var handsMidThighTimestamp = 0L
    private var badHandsMidThighTimestamp = 0L
    private var badFinishTimestamp = 0L

    override fun getFaultInitialMessage(): String {
        return "Hands out at the finish and keep your knees down until your hands pass them."
    }

    override fun getFaultReminderMessage(): String {
        return "Focus on getting you hands out, rocking over at the hips, and keeping knees down until the hands pass."
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun getTotalMark(): FaultChecker.Mark {
        return FaultChecker.Mark(totalGoodStrokes, strokeHistory.size)
    }

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun onEvent(event: Coach.Event) {
        when (event) {

            Coach.Event.CATCH -> {
                rower.averageBodyLength?.let {
                    // set max delta relative to body length
                    maxKneeDelta = (it * maxKneeDeltaBodyRatio).roundToInt()
                }
                val ankle = rower.fixedAnkle
                val shinLength = rower.averageShinLength ?: return
                midThighX = (ankle.x + shinLength * 1.2).roundToInt()

                kneeDeltaBeforeHandsPass?.let { delta ->
                    strokeHistory.add(delta.toFloat())
                    if (delta <= maxKneeDelta) {
                        goodStroke()
                    } else {
                        badStroke()
                        badHandsMidThighTimestamp = handsMidThighTimestamp
                        badFinishTimestamp = rower.timeOfLatestFinish
                    }
                }
            }

            Coach.Event.RECOVERY_UPDATE -> {

                // only interested in hands before they pass mid thigh
                if (rower.currentWrist!!.x <= midThighX) return

                // ignore frames that have shin length different than average
                val currentShinLength = rower.currentShinLength ?: return
                val aveShinLength = rower.averageShinLength ?: return
                if (abs(aveShinLength - currentShinLength) > maxKneeDelta / 2) {
                    return
                }

                if (rower.legDeviationPercent > 15) {
                    // ignore frames with bad detection
                    return
                }
                handsMidThighTimestamp = rower.currentKnee?.time ?: 0L
                val yFinishKnee = rower.finishKneeHeights.truncatedAverage() ?: return
                kneeDeltaBeforeHandsPass = yFinishKnee.toInt() - rower.currentKnee!!.y
            }

            else -> {
                // ignore other phases
            }
        }
    }

    override fun updateDisplay() {
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT || goodConsecutiveStrokes < 3) {
            if (rower.currentWrist?.x ?: 0 > midThighX && rower.phase != Rower.Phase.DRIVE) {
                val currentKnee = rower.currentKnee!!

                // draw arrow pointing down from knee
                val topOfArrow = currentKnee.y.toFloat()
                val bottomOfArrow = topOfArrow + 15
                coach.display.drawLine(
                    currentKnee.x.toFloat(),
                    bottomOfArrow,
                    currentKnee.x.toFloat(),
                    topOfArrow,
                    coach.display.yellow,
                )
                coach.display.drawLine(
                    currentKnee.x.toFloat(),
                    bottomOfArrow,
                    currentKnee.x.toFloat() + 5,
                    bottomOfArrow - 5,
                    coach.display.yellow,
                )
                coach.display.drawLine(
                    currentKnee.x.toFloat(),
                    bottomOfArrow,
                    currentKnee.x.toFloat() - 5,
                    bottomOfArrow - 5,
                    coach.display.yellow,
                )
            }
        }
    }

    private fun annotateImage(canvas: Canvas, frame: Frame) {
        val yAveHip =
            Stats.truncatedAverage(rower.frames.map { it.points[LEFT_HIP]!!.y }, 5)
                ?: rower.finishKnee!!.y
        val currentWrist = frame.points.getValue(LEFT_WRIST)
        if (currentWrist.x > midThighX) {
            val currentKnee = frame.points.getValue(LEFT_KNEE)
            canvas.drawLine(
                currentKnee.x.toFloat(),
                yAveHip.toFloat(),
                currentKnee.x.toFloat(),
                currentKnee.y.toFloat(),
                coach.display.smallYellow,
            )
        }
    }

    override fun clear() {
        super.clear()
        kneeDeltaBeforeHandsPass = null
        midThighX = 250
    }

    override fun faultReportDescription(): String {
        return """
            <h2>Knees up too early</h2>
            <p>${getFaultInitialMessage()}</p>
        """
    }

    override fun faultReportImageRow(): String {
        Log.i(TAG, "faultReportImageRow()")
        val finish = rower.frames.find { it.time == badFinishTimestamp }
        if (finish == null) {
            Log.w(TAG, "finish frame with time $badFinishTimestamp not found")
            return ""
        }
        val midThighRecovery = rower.frames.find { it.time == badHandsMidThighTimestamp }
        if (midThighRecovery == null) {
            Log.w(
                TAG,
                "midThighRecovery frame with time $badHandsMidThighTimestamp not found"
            )
            return ""
        }
        val time = DateUtils.formatElapsedTime(
            (finish.time - rower.startTime!!) /
                1000
        )
        var html =
            """
                <tr><td colspan="2">$time stroke # ${finish.strokeCount}</td></tr>
                <tr><td>finish</td><td>recovery</td></tr>
                <tr>
            """

        Log.i(TAG, "fault image header row: $html")

        listOf(finish, midThighRecovery).forEach { frame ->
            Log.i(TAG, "Adding frame with time ${frame.time} to fault report")
            val copy = frame.bitmap!!.copy(frame.bitmap.config, true)
            val canvas = Canvas(copy)
            coach.display.drawLines(canvas, frame.points)
            annotateImage(canvas, frame)
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
        private const val TAG = "HandsOut"
    }
}
