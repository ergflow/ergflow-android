package org.ergflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_ANKLE
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_ELBOW
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_HIP
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_KNEE
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_SHOULDER
import org.tensorflow.lite.examples.posenet.lib.BodyPart.LEFT_WRIST
import org.tensorflow.lite.examples.posenet.lib.KeyPoint
import org.tensorflow.lite.examples.posenet.lib.Person
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * This class receives posnet Person detection results in realtime. It analyzes the detected
 * points and updates the Rower class and notifies the Coach class.
 * It also updates the display to show current rowing stats and
 */
class StrokeAnalyzer(private val context: Context) {

    val rower = Rower()
    val coach = Coach(context, rower)
    val display = coach.display
    private var frameCount = 0

    private val poses = mutableMapOf<Long, Person>()
    private val poseShelfLife = 5000L
    private val frameShelfLife = 10_000L

    private var handUpFrameCount = 0
    private var consecutiveValidStrokes = 0
    private var consecutiveInvalidStrokes = 0

    private var shinLengths = LimitedList(30, 10)
    private var thighLengths = LimitedList(30, 10)
    private var bodyLengths = LimitedList(30, 10)
    private var upperArmLengths = LimitedList(30, 10)
    private var forearmLengths = LimitedList(30, 10)

    private var previousWrist: Point? = null
    private var newCatch: Point? = null
    private var newFinish: Point? = null

    private fun reset() {
        frameCount = 0
        handUpFrameCount = 0
        consecutiveValidStrokes = 0
        consecutiveInvalidStrokes = 0
        shinLengths.list.clear()
        thighLengths.list.clear()
        bodyLengths.list.clear()
        upperArmLengths.list.clear()
        forearmLengths.list.clear()
    }

    fun analyzeFrame(person: Person, bitmap: Bitmap) {
        rower.currentBitmap = bitmap
        val time = System.currentTimeMillis()
        poses[time] = person

        // prune old entries from the maps
        poses.keys.removeAll(poses.keys.filter { time - it > poseShelfLife })

        person.keyPoints.forEach { keyPoint: KeyPoint ->
            rower.statsMap[keyPoint.bodyPart]?.addPosition(keyPoint.position, time)
            rower.currentPoints[keyPoint.bodyPart] =
                Point(keyPoint.position.x, keyPoint.position.y, time)
        }

        analyze()
        addFrame(bitmap, time)
    }

    fun updateDisplay(
        canvas: Canvas,
    ) {
        display.canvas = canvas
        if (!rower.isRowing) {
            display.showErgOverlay()
        }
        display.showDetection()
        display.showRowerStats()
        display.showFaults()
    }

    /**
     * Analyze the current frame.
     *
     * @return true if detected rower state is valid
     */
    private fun analyze(): Boolean {

        updateCatchAndFinishValues()

        if (isRowerStateValid()) {

            frameCount++

            rower.currentShinAngle = getAngle(rower.currentAnkle, rower.currentKnee)
            rower.currentBodyAngle = getAngle(rower.currentHip, rower.currentShoulder)

            val driveLength = rower.finishShoulder!!.x - rower.catchShoulder!!.x
            if (driveLength < 0) {
                Log.w(TAG, "Invalid driveLength $driveLength")
            }

            rower.currentShinLength = rower.currentDistance(LEFT_ANKLE, LEFT_KNEE).toFloat()
            shinLengths.add(rower.currentShinLength)
            rower.currentThighLength = rower.currentDistance(LEFT_KNEE, LEFT_HIP).toFloat()
            thighLengths.add(rower.currentThighLength)
            rower.currentBodyLength = rower.currentDistance(
                LEFT_HIP,
                LEFT_SHOULDER
            ).toFloat()
            bodyLengths.add(rower.currentBodyLength)
            rower.currentUpperArmLength = rower.currentDistance(
                LEFT_ELBOW,
                LEFT_SHOULDER
            ).toFloat()
            upperArmLengths.add(rower.currentUpperArmLength)
            rower.currentForearmLength = rower.currentDistance(
                LEFT_WRIST,
                LEFT_ELBOW
            ).toFloat()
            forearmLengths.add(rower.currentForearmLength)
            fixTyrannosaurusArms()
            rower.hipHeights.add(rower.currentHip!!.y.toFloat())
            rower.armDeviationPercent = armDeviationPercent()
            rower.legDeviationPercent = legDeviationPercent()
            if (rower.armDeviationPercent > 10) {
                Log.w(TAG, "Arm length deviation is ${rower.armDeviationPercent}%")
            }
            if (rower.legDeviationPercent > 10) {
                Log.w(TAG, "Leg length deviation is ${rower.armDeviationPercent}%")
            }

            // signal pause by hand above the head (wrist above shoulder)
            if (rower.currentWrist!!.y < rower.currentShoulder!!.y - 10) {
                if (rower.isRowing && ++handUpFrameCount > 15) {
                    if (rower.isRowing) {
                        Log.w(TAG, "Pause")
                        rower.isRowing = false
                        return false
                    }
                } else if (++handUpFrameCount > 150) {
                    Log.w(TAG, "reset")
                    display.showToast("Resetting session")
                    coach.saveStats()
                    rower.reset()
                    coach.reset()
                    reset()
                    return false
                }
            } else {
                handUpFrameCount = 0
            }

            val prevStrokePosPct = rower.catchFinishPct
            rower.catchFinishPct =
                if (driveLength == 0) 0 else (
                    100.0f * (
                        rower.currentShoulder!!.x - rower.catchShoulder!!.x
                        ) / driveLength
                    ).roundToInt()
            if (rower.catchFinishPct > 110) {
                Log.w(TAG, "rower.strokePosPct=${rower.catchFinishPct}")
            }
            Log.d(
                TAG,
                "rower.currentBodyAngle=${rower.currentBodyAngle} " +
                    "currentHip.time=${rower .currentHip?.time} " +
                    "catchShoulder.time=${rower .catchShoulder?.time} " +
                    "rower.catchFinishPct=${rower.catchFinishPct} " +
                    "rower.currentShoulder!!.x=${rower.currentShoulder!!.x} " +
                    "rower.catchShoulder!!.x=${rower.catchShoulder!!.x} " +
                    "rower.finishShoulder!!.x=${rower.finishShoulder!!.x} " +
                    "driveLength=$driveLength " +
                    "rower.strokeCount.=${rower.strokeCount} "
            )
            val now = System.currentTimeMillis()
            when {

                // -ve stroke position % is likely a bad value
                prevStrokePosPct < 0 -> {
                    Log.i(TAG, "$prevStrokePosPct stroke position % is likely a bad value")
                    return false
                }

                // When at top of slide and stroke % is increasing
                prevStrokePosPct < 20 && prevStrokePosPct < rower.catchFinishPct -> {

                    // Previous phase was recovery and now stroke position is increasing so must be
                    // at the catch
                    if (rower.phase == Rower.Phase.RECOVERY) {
                        if (!catch()) {
                            Log.i(TAG, "Unable to process catch")
                            return false
                        }
                    } else {
                        if (rower.isRowing && now - rower.catchTimePreviousToFinish > 9000) {
                            Log.w(
                                TAG,
                                "PAUSE!! rower.catchTimePreviousToFinish " +
                                    "(${rower.catchTimePreviousToFinish}) was " +
                                    "${now - rower.catchTimePreviousToFinish} ms ago"
                            )
                            rower.isRowing = false
                        }
                        rower.phase = Rower.Phase.DRIVE
                        Log.d(TAG, "phase = ${rower.phase} $frameCount")
                    }
                }

                // When at end of slide and stroke position % is decreasing
                prevStrokePosPct > 70 && prevStrokePosPct > rower.catchFinishPct -> {

                    // Previous phase was drive and now stroke position is decreasing so must be
                    // at the finish
                    if (rower.phase == Rower.Phase.DRIVE) {

                        finish()
                    } else {
                        rower.phase = Rower.Phase.RECOVERY
                        Log.d(TAG, "phase = ${rower.phase} $frameCount")
                    }
                }

                else -> {
                    if (rower.phase == Rower.Phase.CATCH) rower.phase = Rower.Phase.DRIVE
                    if (rower.phase == Rower.Phase.FINISH) rower.phase = Rower.Phase.RECOVERY
                }
            }
            previousWrist = rower.currentWrist
            if (rower.isRowing) {
                coach.onUpdate()
            }
            return true
        } else {
            Log.w(TAG, "rower state missing some required values")
            return false
        }
    }

    private fun catch(): Boolean {
        val now = System.currentTimeMillis()
        coach.onEndOfStroke()
        rower.phase = Rower.Phase.CATCH
        if (rower.strokeCount > 0) {
            rower.statsMap[LEFT_SHOULDER]?.apply {
                localMaxXList.clear()
                maxXList.clear()
            }
        }
        newCatchValues()
        rower.statsMap[LEFT_WRIST]?.clear()
        val catchTime = rower.catchTimes[rower.strokeCount]!!
        if (rower.catchTimes.size > 1) {
            val previousCatchTime = rower.catchTimes[rower.strokeCount - 1] ?: return false
            val strokeMs = catchTime - previousCatchTime
            rower.strokeRate = (60f / (strokeMs / 1000f)).roundToInt()
            Log.i(TAG, "strokeRate=${rower.strokeRate} catchTime=$catchTime previousCatchTime=$previousCatchTime")
            rower.finishWrist?.also { finish ->
                rower.driveMs = finish.time - previousCatchTime
                val recoveryMs = strokeMs - rower.driveMs
                rower.slideRatio = rower.driveMs / recoveryMs.toFloat()
            }
        }
        rower.strokeRate?.let {
            if (rower.startTime == null && it > 15) {
                rower.startTime = rower.catchTimes[rower.strokeCount - 1] ?: now
            }
        }
        rower.strokeCount++

        Log.i(TAG, "Catch!! strokePosPct=${rower.catchFinishPct}")
        Log.i(
            TAG,
            "Catch time rower.catchShoulder?.time=${
            rower.catchShoulder?.time
            } now=$now"
        )
        Log.i(TAG, "Time since last catch = ${now - catchTime}")
        Log.i(TAG, "There were $frameCount frames in the last stroke")

        rower.catchFinishPct = 0
        Log.i(TAG, "Stroke # ${rower.strokeCount}")

        if (isStrokeValid()) {
            consecutiveValidStrokes++
            consecutiveInvalidStrokes = 0
        } else {
            consecutiveInvalidStrokes++
            consecutiveValidStrokes = 0
        }
        if (rower.isRowing) {
            if (consecutiveInvalidStrokes > 3) {
                rower.isRowing = false
                rower.endTime = now
                coach.saveStats()
            }
        } else {
            if (consecutiveValidStrokes > 2) {
                rower.isRowing = true
                rower.endTime = null
            }
        }
        frameCount = 0
        return true
    }

    private fun finish() {
        rower.phase = Rower.Phase.FINISH
        Log.w(TAG, "phase = ${rower.phase} frameCount $frameCount")
        rower.catchTimePreviousToFinish = rower.catchShoulder?.time ?: 0

        if (rower.strokeCount > 0) {
            rower.statsMap[LEFT_SHOULDER]?.apply {
                localMinXList.clear()
                minXList.clear()
            }
        }
        newFinishValues()
        rower.timeOfLatestFinish = rower.finishWrist?.time ?: System.currentTimeMillis()
        Log.i(TAG, "Finish wrist time ${rower.finishWrist?.time} rower.timeOfLatestFinish ${rower.timeOfLatestFinish}")

        rower.finishKnee?.let { rower.finishKneeHeights.add(it.y.toFloat()) }

        rower.averageShinLength = shinLengths.truncatedAverage()
        rower.averageThighLength = thighLengths.truncatedAverage()
        rower.averageBodyLength = bodyLengths.truncatedAverage()
        rower.averageUpperArmLength = upperArmLengths.truncatedAverage()
        rower.averageForearmLength = forearmLengths.truncatedAverage()
    }

    private fun newCatchValues() {
        newCatch?.also { catch ->
            Log.i(TAG, "new catch.time=${catch.time}")
            rower.catchTimes[rower.strokeCount] = catch.time
            poses[catch.time]?.apply {
                getPoint(LEFT_SHOULDER)?.apply {
                    rower.catchShoulder = Point(position.x, position.y, catch.time)
                }
                getPoint(LEFT_ELBOW)?.apply {
                    rower.catchElbow = Point(position.x, position.y, catch.time)
                }
                getPoint(LEFT_WRIST)?.apply {
                    rower.catchWrist = Point(position.x, position.y, catch.time)
                }
                getPoint(LEFT_KNEE)?.apply {
                    rower.catchKnee = Point(position.x, position.y, catch.time)
                }
                getPoint(LEFT_HIP)?.apply {
                    rower.catchHip = Point(position.x, position.y, catch.time)
                }
                getPoint(BodyPart.LEFT_EAR)?.apply {
                    rower.catchEar = Point(position.x, position.y, catch.time)
                }
                rower.catchBodyAngle = getAngle(rower.catchHip, rower.catchShoulder)
                Log.d(
                    TAG,
                    "rower.catchBodyAngle = ${rower.catchBodyAngle} time=${
                    rower.catchHip?.time
                    }"
                )
                rower.catchShinAngle = getAngle(rower.fixedAnkle, rower.catchKnee)
            }
        }
    }

    /**
     * Wrist detection is often wrong near the catch. If forearm length is off from average then
     * coerce the wrist position to where they should be given the elbow position.
     */
    private fun fixTyrannosaurusArms() {
        val averageForearm = rower.averageForearmLength ?: return
        val currentForearm = rower.currentForearmLength ?: return
        val elbow = rower.currentElbow ?: return

        if (abs(averageForearm - currentForearm) < 0.2 * averageForearm) {
            // If length is within 20% of average then don't do any correction
            return
        }
        Log.w(
            TAG,
            "Fixing wrist position. " +
                "averageForearm=$averageForearm currentForearm=$currentForearm"
        )
        // Move wrist to previous height and aveForearmLength distance from the elbow
        val yPrevious = previousWrist?.y ?: 115
        val xDelta = sqrt(averageForearm.pow(2) - (elbow.y - yPrevious).toFloat().pow(2))
        val expectedWrist = Point((elbow.x - xDelta).toInt(), yPrevious, elbow.time)
        rower.currentPoints[LEFT_WRIST] = expectedWrist
        rower.currentForearmLength = averageForearm
    }

    /**
     * Compare forearm and upper arm lengths for this frame against the average lengths.
     * Large deviations from average are an indication that there is an error in detection.
     *
     * @return deviation from as a percent or 100 if lengths are missing
     */
    private fun armDeviationPercent(): Int {
        if (rower.strokeCount < 5) {
            // only start applying the test after 5 strokes
            return 0
        }
        val aveForearm = rower.averageForearmLength ?: return 100
        val aveUpperArm = rower.averageUpperArmLength ?: return 100
        if (aveForearm == 0f || aveUpperArm == 0f) {
            return 100
        }
        val currentForearm = rower.currentForearmLength ?: return 100
        val currentUpperArm = rower.currentUpperArmLength ?: return 100
        val forearmDelta = abs(aveForearm - currentForearm)
        val upperArmDelta = abs(aveUpperArm - currentUpperArm)
        return (max(forearmDelta / aveForearm, upperArmDelta / aveUpperArm) * 100).toInt()
    }

    /**
     * Compare shin and thigh lengths for this frame against the average lengths.
     * Large deviations from average are an indication that there is an error in detection.
     *
     * @return deviation from as a percent or 100 if lengths are missing
     */
    private fun legDeviationPercent(): Int {
        if (rower.strokeCount < 5) {
            // only start applying the test after 5 strokes
            return 0
        }
        val aveShin = rower.averageShinLength ?: return 100
        val aveThigh = rower.averageThighLength ?: return 100
        if (aveShin == 0f || aveThigh == 0f) {
            return 100
        }
        val currentShin = rower.currentShinLength ?: return 100
        val currentThigh = rower.currentThighLength ?: return 100
        val shinDelta = abs(aveShin - currentShin)
        val thighDelta = abs(aveThigh - currentThigh)
        return (max(shinDelta / aveShin, thighDelta / aveThigh) * 100).toInt()
    }

    private fun addFrame(bitmap: Bitmap, time: Long) {
        if (bitmap == rower.frames.lastOrNull()?.bitmap) {
            Log.w(TAG, "dup bitmap")
            return
        }
        val frame = Frame(bitmap.copy(bitmap.config, false)).apply {
            this.time = time
            if (rower.phase != Rower.Phase.CATCH) {
                val catchTime = rower.catchShoulder?.time ?: time
                timeSinceCatchMs = time - catchTime
            }
            strokeCount = rower.strokeCount
            points = rower.currentPoints.toMap()
            strokeRate = rower.strokeRate ?: 0
            phase = rower.phase
            strokePosPct = rower.catchFinishPct
            bodyAngle = rower.currentBodyAngle ?: 0.0
        }
        Log.d(TAG, "adding frame at ${frame.time}")
        rower.frames.add(frame)

        val expired = rower.frames.filter {
            System.currentTimeMillis() - it.time > frameShelfLife
        }
        if (expired.isNotEmpty()) {
            Log.d(
                TAG,
                "removing ${expired.size} expired frames " +
                    "starting with frame at ${expired.first().time} and " +
                    "ending at ${expired.last().time}"
            )
        }
        rower.frames.removeAll(expired)
    }

    /**
     * Not all pose estimations are correct. Check if the values are within possible ranges.
     */
    private fun isStrokeValid(): Boolean {
        if (rower.strokeRate !in 5..50) {
            Log.w(TAG, "invalid stroke rate ${rower.strokeRate}")
            return false
        }
        if (rower.catchBodyAngle!! !in 25.0..135.0) {
            Log.w(TAG, "invalid catchBodyAngle ${rower.catchBodyAngle}")
            return false
        }
        if (rower.catchShinAngle!! !in 25.0..180.0) {
            Log.w(TAG, "invalid catchShinAngle ${rower.catchShinAngle}")
            return false
        }
        if (rower.finishBodyAngle!! !in 25.0..150.0) {
            Log.w(TAG, "invalid finishBodyAngle ${rower.finishBodyAngle}")
            return false
        }
        val timeOfLastCatch = rower.currentShoulder?.time ?: System.currentTimeMillis()
        val timeSinceLastCatch = System.currentTimeMillis() - timeOfLastCatch
        if (timeSinceLastCatch > 5000) {
            Log.w(TAG, "invalid time since last catch $timeSinceLastCatch")
            return false
        }
        val wristTravel = rower.finishWrist!!.x - rower.catchWrist!!.x
        if (wristTravel < 50) {
            Log.w(TAG, "invalid wristTravel $wristTravel")
            return false
        }

        return true
    }

    private fun updateCatchAndFinishValues() {

        if (rower.phase == Rower.Phase.RECOVERY || !rower.isRowing) {
            val previousShoulderCatchTime = newCatch?.time
            Log.i(TAG, "previousShoulderCatchTime=$previousShoulderCatchTime")
            newCatch = rower.statsMap[LEFT_SHOULDER]?.getMinXPoint()
            newCatch?.apply {
                // Sometimes after pausing the newCatch does not update so we need to clear it
                if (System.currentTimeMillis() - time > 5_000) {
                    rower.statsMap[LEFT_SHOULDER]?.apply {
                        localMaxXList.clear()
                        maxXList.clear()
                    }
                }
            }
            if (rower.strokeCount == 0 || frameCount > 100) {
                newCatchValues()
            }
        }

        if (rower.phase == Rower.Phase.DRIVE || !rower.isRowing) {
            newFinish = rower.statsMap[LEFT_WRIST]?.getMaxXPoint()
            newFinish?.apply {
                // Sometimes after pausing the newFinish does not update so we need to clear it
                if (System.currentTimeMillis() - time > 5_000) {
                    rower.statsMap[LEFT_SHOULDER]?.apply {
                        localMinXList.clear()
                        minXList.clear()
                    }
                }
            }
            if (rower.strokeCount == 0 || frameCount > 100) {
                newFinishValues()
            }
        }

        // Ankle detection can be bad. Ankle should not move much so replace it with the fixed
        // hard coded one
        rower.currentPoints[LEFT_ANKLE] = rower.fixedAnkle
    }

    private fun newFinishValues() {
        newFinish?.also { finish ->
            poses[finish.time]?.apply {
                Log.i(TAG, "newFinish time ${finish.time}")
                getPoint(LEFT_WRIST)?.apply {
                    rower.finishWrist = Point(position.x, position.y, finish.time)
                }
                getPoint(LEFT_SHOULDER)?.apply {
                    rower.finishShoulder = Point(position.x, position.y, finish.time)
                }
                getPoint(LEFT_ELBOW)?.apply {
                    rower.finishElbow = Point(position.x, position.y, finish.time)
                }
                getPoint(LEFT_KNEE)?.apply {
                    rower.finishKnee = Point(position.x, position.y, finish.time)
                }
                getPoint(LEFT_HIP)?.apply {
                    rower.finishHip = Point(position.x, position.y, finish.time)
                }
            }
            rower.finishBodyAngle = getAngle(rower.finishHip, rower.finishShoulder)
        }
    }

    private fun isRowerStateValid(): Boolean {

        if (rower.catchWrist == null) {
            Log.w(TAG, "catchWrist is null")
            return false
        }
        if (rower.catchElbow == null) {
            Log.w(TAG, "catchElbow is null")
            return false
        }
        if (rower.catchShoulder == null) {
            Log.w(TAG, "catchShoulder is null")
            return false
        }
        if (rower.catchKnee == null) {
            Log.w(TAG, "catchKnee is null")
            return false
        }
        if (rower.catchHip == null) {
            Log.w(TAG, "catchHip is null")
            return false
        }
        if (rower.finishWrist == null) {
            Log.w(TAG, "finishWrist is null")
            return false
        }
        if (rower.finishElbow == null) {
            Log.w(TAG, "finishElbow is null")
            return false
        }
        if (rower.finishHip == null) {
            Log.w(TAG, "finishHip is null")
            return false
        }
        if (rower.finishShoulder == null) {
            Log.w(TAG, "finishShoulder is null")
            return false
        }
        if (rower.currentWrist == null) {
            Log.w(TAG, "currentWrist is null")
            return false
        }
        if (rower.currentElbow == null) {
            Log.w(TAG, "currentElbow is null")
            return false
        }
        if (rower.currentHip == null) {
            Log.w(TAG, "currentHip is null")
            return false
        }
        if (rower.currentAnkle == null) {
            Log.w(TAG, "currentAnkle is null")
            return false
        }
        if (rower.currentKnee == null) {
            Log.w(TAG, "currentKnee is null")
            return false
        }
        if (rower.currentShoulder == null) {
            Log.w(TAG, "currentShoulder is null")
            return false
        }

        return true
    }

    private fun getAngle(point1: Point?, point2: Point?): Double? {
        if (point1 == null || point2 == null) {
            return null
        }
        var angle =
            Math.toDegrees(atan2(point1.y.toDouble() - point2.y, point1.x.toDouble() - point2.x))

        if (angle < 0) {
            angle += 360f
        }

        return angle
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "StrokeAnalyzer"
    }
}
