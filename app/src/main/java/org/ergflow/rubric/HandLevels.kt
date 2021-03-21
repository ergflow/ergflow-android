package org.ergflow.rubric

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateUtils
import android.util.Log
import org.ergflow.Coach
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.cos
import kotlin.math.max

class BadHandLevelFrame(val time: Long, val delta: Float)

/**
 * Checks for consistent hand levels by measuring difference in
 * actual heights with expected heights.
 */
class HandLevels(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Hand levels"
    override val description = "Checks for consistent hand levels by measuring difference in " +
        "actual heights with expected heights"

    private var allowedMaxDeviation = 5
    override val strokeHistoryUnit = "Δ"
    private var leftX: Float? = null
    private var rightX: Float? = null
    private var topY: Float? = null
    private var bottomY: Float? = null
    private var currentDelta = 0f
    private var catchTimeOfBadStroke = 0L

    // Frames with worst hand levels in buckets by strokePct
    private val worstFrameBuckets = mutableMapOf(
        0..16 to BadHandLevelFrame(0, -1f),
        17..32 to BadHandLevelFrame(0, -1f),
        33..48 to BadHandLevelFrame(0, -1f),
        49..64 to BadHandLevelFrame(0, -1f),
        65..80 to BadHandLevelFrame(0, -1f),
        81..100 to BadHandLevelFrame(0, -1f),
    )

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun getFaultInitialMessage(): String {
        return "Try to maintain constant hand levels. Hands out before the knees come up and " +
            "don't dip at the catch or finish. Keep your hands in the green rectangle."
    }

    override fun getFaultReminderMessage(): String {
        return "Focus on maintaining proper hand levels"
    }

    override fun getFixedMessage(): String {
        return ""
    }

    override fun onEvent(event: Coach.Event) {

        when (event) {

            Coach.Event.CATCH -> {
                worstFrameBuckets.keys.forEach { range ->
                    worstFrameBuckets[range] = BadHandLevelFrame(0, -1f)
                }
                val finishWrist = rower.finishWrist!!
                val yHip = rower.hipHeights.truncatedAverage() ?: return
                val bodyLength = rower.averageBodyLength ?: return
                leftX = 10f
                rightX = finishWrist.x.toFloat()
                // Top is around nipple height measured as 80% of body length at layback where
                // layback is 20 degrees past vertical
                topY = yHip - 0.8f * bodyLength * cos(0.35).toFloat()
                bottomY = 130f

                strokeHistory.add(currentDelta)
                if (currentDelta <= allowedMaxDeviation) {
                    goodStroke()
                } else {
                    badStroke()
                    catchTimeOfBadStroke = rower.catchShoulder?.time ?: 0
                }
                currentDelta = 0f
            }

            else -> {
                if (topY != null && bottomY != null) {

                    var y = rower.currentWrist?.y?.toFloat() ?: return
                    // The hand is 5px lower than wrist at catch and same or higher than wrist at
                    // the finish
                    y += (5 - 0.05f * rower.catchFinishPct)

                    val deltaTop = topY!! - y
                    val deltaBottom = y - bottomY!!
                    currentDelta = max(max(deltaTop, deltaBottom), currentDelta)

                    // Gather worst frames in buckets for reporting so that we don't have to show
                    // every frame in the report.
                    worstFrameBuckets.keys.find { rower.strokePct in it }?.also { range ->
                        if (worstFrameBuckets[range]!!.delta <= currentDelta) {
                            worstFrameBuckets[range] = BadHandLevelFrame(
                                rower.currentWrist!!.time,
                                currentDelta
                            )
                        }
                    }
                }
            }
        }
    }

    override fun updateDisplay() {
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT || goodConsecutiveStrokes < 3) {

            val topLeft = Pair(leftX ?: return, topY ?: return)
            val topRight = Pair(rightX ?: return, topY ?: return)
            val bottomLeft = Pair(leftX ?: return, bottomY ?: return)
            val bottomRight = Pair(rightX ?: return, bottomY ?: return)

            coach.display.drawLine(
                topLeft.first,
                topLeft.second,
                topRight.first,
                topRight.second,
                coach.display.green,
            )
            coach.display.drawLine(
                topRight.first,
                topRight.second,
                bottomRight.first,
                bottomRight.second,
                coach.display.green
            )
            coach.display.drawLine(
                bottomRight.first,
                bottomRight.second,
                bottomLeft.first,
                bottomLeft.second,
                coach.display.green
            )
            coach.display.drawLine(
                bottomLeft.first,
                bottomLeft.second,
                topLeft.first,
                topLeft.second,
                coach.display.green
            )
        }
    }

    private fun annotateImage(canvas: Canvas) {
        val topLeft = Pair(leftX ?: return, topY ?: return)
        val topRight = Pair(rightX ?: return, topY ?: return)
        val bottomLeft = Pair(leftX ?: return, bottomY ?: return)
        val bottomRight = Pair(rightX ?: return, bottomY ?: return)

        val green = Paint().apply {
            color = Color.GREEN
            strokeWidth = 3f
        }
        canvas.drawLine(
            topLeft.first,
            topLeft.second,
            topRight.first,
            topRight.second,
            green,
        )
        canvas.drawLine(
            topRight.first,
            topRight.second,
            bottomRight.first,
            bottomRight.second,
            green
        )
        canvas.drawLine(
            bottomRight.first,
            bottomRight.second,
            bottomLeft.first,
            bottomLeft.second,
            green
        )
        canvas.drawLine(
            bottomLeft.first,
            bottomLeft.second,
            topLeft.first,
            topLeft.second,
            green
        )
    }

    override fun clear() {
        super.clear()
        leftX = null
        rightX = null
        topY = null
        bottomY = null
        currentDelta = 0f
        worstFrameBuckets.keys.forEach { range ->
            worstFrameBuckets[range] = BadHandLevelFrame(0, -1f)
        }
    }

    override fun faultReportDescription(): String {
        return """
            <h2>Not Maintaining Proper Hand Levels</h2>
            <p>${getFaultInitialMessage()}</p>
        """
    }

    override fun faultReportImageRow(): String {
        Log.i(TAG, "faultReportImageRow()")
        val frame1 = rower.frames.find { it.time == catchTimeOfBadStroke }
        if (frame1 == null) {
            Log.w(TAG, "frame with time $catchTimeOfBadStroke not found")
            return ""
        }
        val time = DateUtils.formatElapsedTime(
            (frame1.time - rower.startTime!!) /
                1000
        )
        var html =
            """
                <tr>
                    <th>
                        $time stroke # ${frame1.strokeCount}
                    </th>
                </tr>
                <tr>
            """

        Log.i(TAG, "fault image header row: $html")

        val badHandTimestamps = worstFrameBuckets.values
            .map { badHandLevelFrame -> badHandLevelFrame.time }

        Log.i(TAG, "hand level fault sample badHandTimestamps: $badHandTimestamps")
        val sampleFrames = rower.frames.filter { frame -> frame.time in badHandTimestamps }
        Log.i(TAG, "Hand level fault sampleFrames.size: ${sampleFrames.size}")

        sampleFrames
            .forEach { frame ->
                Log.i(TAG, "Adding frame with time ${frame.time} to fault report")
                val copy = frame.bitmap!!.copy(frame.bitmap.config, true)
                val canvas = Canvas(copy)
                coach.display.drawLines(canvas, frame.points)
                annotateImage(canvas)
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
        private const val TAG = "HandLevels"
    }
}
