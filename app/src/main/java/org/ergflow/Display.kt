package org.ergflow

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateUtils
import androidx.core.content.res.ResourcesCompat
import org.ergflow.posenet.MODEL_HEIGHT
import org.ergflow.posenet.MODEL_WIDTH
import org.ergflow.posenet.PosenetActivity
import org.ergflow.posenet.R
import org.ergflow.rubric.FaultChecker
import org.ergflow.ui.ItemArrayAdapter
import org.ergflow.ui.ItemArrayAdapter.Item
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.Position
import kotlin.math.roundToInt

class Display(val coach: Coach) {

//    lateinit var toolBarText: TextView

    private val errorColor = coach.context.resources.getColor(R.color.dracula_red, null)
    private val warnColor = coach.context.resources.getColor(R.color.dracula_yellow, null)
    private val goodColor = coach.context.resources.getColor(R.color.dracula_green, null)
    private val titleRowColor = coach.context.resources.getColor(R.color.dracula_selection, null)

    val yellow = Paint().apply {
        color = Color.YELLOW
    }
    val smallYellow = Paint().apply {
        color = Color.YELLOW
        textSize = 16f
        strokeWidth = 3f
    }
    val blackBorder = Paint().apply {
        color = Color.BLACK
        textSize = 16f
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val green = Paint().apply {
        color = Color.GREEN
    }
    val red = Paint().apply {

        color = Color.RED
    }
    val blue = Paint().apply {

        color = Color.BLUE
        strokeWidth = 8f
    }
    val smallRed = Paint().apply {
        color = Color.RED
        textSize = 16f
        strokeWidth = 3f
    }
    val orange = Paint().apply {

        color = Color.parseColor("#ff6f00")
    }
    val white = Paint().apply {
        color = Color.WHITE
    }
    private val orangeWide = Paint().apply {
        color = Color.parseColor("#D93F00")
        strokeWidth = 30f
    }

    val displayableBodyParts = listOf(
        BodyPart.LEFT_ANKLE,
        BodyPart.LEFT_KNEE,
        BodyPart.LEFT_HIP,
        BodyPart.LEFT_SHOULDER,
        BodyPart.LEFT_ELBOW,
        BodyPart.LEFT_WRIST,
    )

    var itemArrayAdapter: ItemArrayAdapter? = null

    private val rower = coach.rower
    private var widthRatio: Float = 1.0f
    private var left: Int = 0
    private var x = 0.0f
    private var heightRatio: Float = 1.0f
    private var top: Int = 0

    private val targetShinLength: Double by lazy {
        val catch = coach.targetPositions[0].keyPoints
        rower.distance(
            catch.find { it.bodyPart == BodyPart.LEFT_ANKLE }!!,
            catch.find { it.bodyPart == BodyPart.LEFT_KNEE }!!,
        )
    }

    private val targetThighLength: Double by lazy {
        val catch = coach.targetPositions[0].keyPoints
        rower.distance(
            catch.find { it.bodyPart == BodyPart.LEFT_KNEE }!!,
            catch.find { it.bodyPart == BodyPart.LEFT_HIP }!!,
        )
    }

    private val targetBodyLength: Double by lazy {
        val catch = coach.targetPositions[0].keyPoints
        rower.distance(
            catch.find { it.bodyPart == BodyPart.LEFT_HIP }!!,
            catch.find { it.bodyPart == BodyPart.LEFT_SHOULDER }!!,
        )
    }

    var canvas: Canvas? = null
        set(canvas) {
            field = canvas
            this.left = canvas!!.height
            this.x = left.toFloat() + 50
            this.widthRatio = widthRatio(canvas)
            this.heightRatio = heightRatio(canvas)
            val textSize = 12f * heightRatio
            val strokeWidth = 2f * heightRatio
            yellow.textSize = textSize
            yellow.strokeWidth = strokeWidth
            green.textSize = textSize
            green.strokeWidth = strokeWidth
            red.textSize = textSize
            red.strokeWidth = strokeWidth
            white.textSize = textSize
            white.strokeWidth = strokeWidth
            orange.textSize = textSize
            orange.strokeWidth = strokeWidth
        }

    private fun heightRatio(canvas: Canvas?) = canvas!!.height.toFloat() / MODEL_HEIGHT

    private fun widthRatio(canvas: Canvas?) =
        // Not a typo. Need to use height here otherwise points don't line up
        canvas!!.height.toFloat() / MODEL_WIDTH

    var row = 0

    fun drawText(canvas: Canvas, row: Int, text: String) {
        canvas.drawText(
            text,
            10f,
            15 + row * 20f,
            blackBorder,
        )
        canvas.drawText(
            text,
            10f,
            15 + row * 20f,
            smallYellow,
        )
    }

    fun showRowerStats() {

        row = 0
        if (rower.isRowing) {
            val time = DateUtils.formatElapsedTime(rower.duration / 1000)
            var spm = ""
            if (rower.slideRatio != null) {
                spm += "\nStroke Rate: ${rower.strokeRate}\n"
            }
            drawListItem("time", "\nDuration: $time\n", "", spm, null)
        } else {
            drawListItem("time", "\nDuration: 00:00\n", "", "\nStroke Rate:  0\n", null)
        }
        drawListItem("scoreTitle", "Test", "Last Stroke", "Score", null, titleRowColor)
        drawScore("catch angle", coach.catch)
        drawScore("finish angle", coach.layback)
        drawScore("early drive", coach.earlyDriveBodyAngle)
        drawScore("lunging at catch", coach.lungingAtCatch)
        drawScore("slide ratio", coach.rushing)
        drawScore("shin angle", coach.shins)
        drawScore("hand levels", coach.handLevels)
        drawScore("hands out", coach.handsOut)
        var totalGood = 0
        var total = 0
        coach.faultCheckers.forEach {
            val totalMark = it.getTotalMark()
            totalGood += totalMark.goodStrokes
            total += totalMark.totalStrokes
        }
        val overallTotal = FaultChecker.Mark(totalGood, total)
        drawListItem("total", "total", "", "${overallTotal.percent}%", null, titleRowColor)
    }

    private fun drawScore(title: String, faultChecker: FaultChecker) {
        var colour: Int? = goodColor
        if (!rower.isRowing) {
            colour = null
        } else if (faultChecker.status == FaultChecker.Status.GOOD) {
            if (faultChecker.badConsecutiveStrokes > 0) {
                colour = warnColor
            }
        } else {
            colour = errorColor
        }
        val mark = faultChecker.getTotalMark()
        val middle = String.format("%.1f", faultChecker.strokeHistory.lastOrNull() ?: 0f) + faultChecker.strokeHistoryUnit
        val right = "(${mark.goodStrokes}/${mark.totalStrokes}) ${mark.percent}%"
        drawListItem(title, title, middle, right, colour)
    }

    private fun drawListItem(
        key: String,
        left: String,
        middle: String,
        right: String,
        textColor: Int?,
    ) {
        coach.context.mainExecutor.execute {
            itemArrayAdapter?.addOrUpdate(Item(key, left, middle, right, textColor, null))
        }
    }

    private fun drawListItem(
        key: String,
        left: String,
        middle: String,
        right: String,
        textColor: Int?,
        backgroundColor: Int?,
    ) {
        coach.context.mainExecutor.execute {
            itemArrayAdapter?.addOrUpdate(Item(key, left, middle, right, textColor, backgroundColor))
        }
    }

    private fun drawLines(
        keyPoints: MutableMap<BodyPart, Position>,
        paint: Paint,
        scaleFactorX: Float,
        offsetX: Float,
        scaleFactorY: Float,
        offsetY: Float,
    ) {
        drawLines(keyPoints, paint, scaleFactorX, offsetX, scaleFactorY, offsetY, canvas!!)
    }

    private fun drawLines(
        keyPoints: MutableMap<BodyPart, Position>,
        paint: Paint,
        scaleFactorX: Float,
        offsetX: Float,
        scaleFactorY: Float,
        offsetY: Float,
        canvas: Canvas,
    ) {

        for (line in PosenetActivity.bodyJoints) {
            if (keyPoints.containsKey(line.first) && keyPoints.containsKey(line.second)) {
                canvas.drawLine(
                    (keyPoints[line.first]!!.x.toFloat() * scaleFactorX + offsetX) * widthRatio(canvas),
                    (keyPoints[line.first]!!.y.toFloat() * scaleFactorY - offsetY) * heightRatio(canvas) + top,
                    (keyPoints[line.second]!!.x.toFloat() * scaleFactorX + offsetX) * widthRatio(canvas),
                    (keyPoints[line.second]!!.y.toFloat() * scaleFactorY - offsetY) * heightRatio(canvas) + top,
                    paint
                )
            }
        }
    }

    fun drawLines(canvas: Canvas, points: Map<BodyPart, Point>) {

        for (line in PosenetActivity.bodyJoints) {
            if (points.containsKey(line.first) && points.containsKey(line.second)) {
                canvas.drawLine(
                    (points.getValue(line.first).x.toFloat()),
                    (points.getValue(line.first).y.toFloat()),
                    (points.getValue(line.second).x.toFloat()),
                    (points.getValue(line.second).y.toFloat()),
                    smallRed
                )
            }
        }
    }

    fun showFaults() {
        if (rower.isRowing) {
            // If a verbal message is sent then only update the display matching that message
            coach.faultCheckers.find {
                // if more than three bad strokes in a row then verbal message sent and status
                // is changed
                it.status != FaultChecker.Status.GOOD
            }?.apply {
                updateDisplay()
                return
            }
            // Otherwise, update display for first fault with more than one bad stroke
            coach.faultCheckers.find {
                it.badConsecutiveStrokes > 0
            }?.apply {
                updateDisplay()
            }
        }
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        canvas?.drawLine(
            x1 * widthRatio,
            y1 * heightRatio + top,
            x2 * widthRatio,
            y2 * heightRatio + top,
            paint,
        )
    }

    fun showTarget(
        bodyParts: List<BodyPart>,
        targetPosition: Coach.TargetPosition,
        paint: Paint,
    ) {
        showTarget(bodyParts, targetPosition, paint, canvas!!)
    }

    fun showTarget(
        bodyParts: List<BodyPart>,
        targetPosition: Coach.TargetPosition,
        paint: Paint,
        canvas: Canvas,
    ) {

        val ankle = rower.fixedAnkle
        val bodyLength = rower.averageBodyLength ?: return
        val shinLength = rower.averageShinLength ?: return
        val thighLength = rower.averageThighLength ?: return
        val targetAnkle =
            targetPosition.keyPoints.find { it.bodyPart == BodyPart.LEFT_ANKLE }!!.position
        // Use shin + thigh length to estimate x scale factor
        val scaleFactorX = (shinLength + thighLength) / (targetShinLength + targetThighLength)

        // Use the body length to estimate y scale factor
        val scaleFactorY = bodyLength / targetBodyLength

        // Ankles are fixed on non-dynamic ergs so use that to find the offsets
        // Target position catch is at x=0 so offset is number of pixels to move target right to
        // match actual
        val offsetX = ankle.x - targetAnkle.x * scaleFactorX

        // Target ankle is at bottom (y=257) so offset is number of pixels to move up to match actual
        val offsetY = (257 * scaleFactorY - ankle.y)

        val targetKeyPoints = mutableMapOf<BodyPart, Position>()
        for (keyPoint in targetPosition.keyPoints) {
            if (keyPoint.bodyPart in bodyParts) {
                targetKeyPoints[keyPoint.bodyPart] = keyPoint.position
            }
        }
        drawLines(
            targetKeyPoints,
            paint,
            scaleFactorX.toFloat(),
            offsetX.toFloat(),
            scaleFactorY.toFloat(),
            offsetY.toFloat(),
            canvas,
        )
    }

    fun showTargetPositions() {

        val ankle = rower.fixedAnkle
        val shoulder = rower.currentPoints[BodyPart.LEFT_SHOULDER]!!
        val currentTime = shoulder.time

        val targetDriveMs = 1200L
        val driveScaleFactor = rower.driveMs / targetDriveMs.toDouble()
        // use 1904 to fix target stroke rate at 22
//        val driveScaleFactor = 1904 / targetDriveMs.toDouble()
        val segment: Int
        // There are 36 target segments for every 80ms of the target stroke
        // Segments 0-15 are the drive and 16-35 are the recovery
        val numberOfDriveSegments = 16
        val numberOfRecoverySegments = 20

        // Slide ratio (drive/recovery) depends on stroke rate. For a rate of 20 the ratio is 0.6
        // For a rate of 36 ratio is 0.9. We will use the following formula:
        val targetSlideRatio = (rower.strokeRate!! + 12) / 53.toFloat()

        segment = if (rower.catchTimePreviousToFinish == rower.catchShoulder?.time ?: 0) {
            // Recovery
            val targetRecoveryMs = rower.driveMs / targetSlideRatio
            val adjustedCurrentRecoveryMs = (currentTime - rower.timeOfLatestFinish) * driveScaleFactor
            (numberOfRecoverySegments * adjustedCurrentRecoveryMs / targetRecoveryMs).roundToInt() + numberOfDriveSegments
        } else {
            // Drive
            val catchTime = rower.catchShoulder?.time ?: 0
            val adjustedCurrentDriveMs = (currentTime - catchTime) * driveScaleFactor
            (numberOfDriveSegments.toDouble() * adjustedCurrentDriveMs / rower.driveMs).roundToInt()
        }
        if (segment < 0 || segment >= coach.targetPositions.size) {
            println(
                "Unable to draw segment $segment because there are only ${
                coach.targetPositions.size
                }"
            )
            return
        }

        val desiredPosition = coach.targetPositions[segment]
        val desiredKeyPoints = mutableMapOf<BodyPart, Position>()
        for (keyPoint in desiredPosition.keyPoints) {
            desiredKeyPoints[keyPoint.bodyPart] = keyPoint.position
        }

        // Use the hip at finish and ankle of the desired and actual points to figure out the
        // offset and scale used to size and align desired position with actual
        val targetFinishHip =
            coach.targetPositions[15].keyPoints.find { it.bodyPart == BodyPart.LEFT_HIP }!!.position
        val targetFinishShoulder =
            coach.targetPositions[15].keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }!!.position
        val targetAnkle =
            desiredPosition.keyPoints.find { it.bodyPart == BodyPart.LEFT_ANKLE }!!.position

        // Use the x distance between hips at finish and ankles to estimate x scale factor
        val scaleFactorX =
            (rower.finishHip!!.x - ankle.x) / (targetFinishHip.x - targetAnkle.x).toFloat()

        // Use the y distance between shoulder at finish and ankles to estimate y scale factor
        val scaleFactorY =
            (ankle.y - rower.finishShoulder!!.y) / (targetAnkle.y - targetFinishShoulder.y).toFloat()

        // Ankles are fixed on non-dynamic ergs so use that to find the offsets
        // Target position catch is at x=0 so offset is number of pixels to move target right to
        // match actual
        val offsetX = ankle.x - targetAnkle.x * scaleFactorX

        // Target ankle is at bottom (y=257) so offset is number of pixels to move up to match actual
        val offsetY = (MODEL_HEIGHT * scaleFactorY - ankle.y)

        drawLines(desiredKeyPoints, green, scaleFactorX, offsetX, scaleFactorY, offsetY)
    }

    fun showDetection() {

        val points = coach.rower.currentPoints
        drawLinesAndPoints(points, blue)
    }

    fun drawLinesAndPoints(points: Map<BodyPart, Point>, paint: Paint) {
        // Draw lines
        PosenetActivity.bodyJoints.forEach { line ->
            val first = points[line.first] ?: return
            val second = points[line.second] ?: return
            canvas?.drawLine(
                first.x.toFloat() * widthRatio,
                first.y.toFloat() * heightRatio + top,
                second.x.toFloat() * widthRatio,
                second.y.toFloat() * heightRatio + top,
                paint,
            )
        }
        // Draw key points
        points.filter { entry -> entry.key in displayableBodyParts }.values.forEach { point ->
            val adjustedX: Float = point.x.toFloat() * widthRatio + 0
            val adjustedY: Float = point.y.toFloat() * heightRatio + top
            canvas?.drawCircle(adjustedX, adjustedY, 10.0f, paint)
        }
    }

    fun showErgOverlay() {
        val drawableErg = ResourcesCompat.getDrawable(coach.context.resources, R.drawable
            .erg_overlay, null)
        drawableErg?.setBounds(0, 0, canvas!!.width, canvas!!.height)
        drawableErg?.draw(canvas!!)
    }
}
