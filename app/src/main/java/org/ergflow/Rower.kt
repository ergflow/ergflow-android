package org.ergflow

import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.KeyPoint
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Class representing the state of the rower. This class is updated in realtime by the
 * StrokeAnalyzer class. The pose estimations are just estimations. Any anomaly can be either an
 * error in detection (which happens a lot) or it could be the rower having a bad stroke.
 * <p>
 * Any given state can be invalid. When using this class you need to validate that things are not
 * null etc. If it is invalid then wait for the next detection and try again.
 */
class Rower {

    /**
     * Phase of the stroke.
     */
    enum class Phase { CATCH, DRIVE, FINISH, RECOVERY }

    /**
     * Indicates if the rower is currently rowing
     */
    var isRowing = false

    /**
     * Timestamp of when the stroke analyzer detected that the rower started rowing or null if
     * rower has not started yet.
     */
    var startTime: Long? = null

    /**
     * Timestamp of when the stroke analyzer detected that the rower stopped rowing or null if
     * rower is still rowing.
     */
    var endTime: Long? = null

    /**
     * Duration in ms.
     */
    val duration: Long get() {
        return startTime?.let {
            val end = endTime ?: System.currentTimeMillis()
            return end - it
        } ?: 0
    }

    /**
     * Number of strokes since startTime
     */
    var strokeCount = 0

    /**
     * Number of strokes that had at least one fault reported
     */
    var errorStrokeCount = 0

    /**
     * Map of catch times keyed by stoke number
     */
    var catchTimes = mutableMapOf<Int, Long>()

    /**
     * Map of min and max values keyed by body part. The Stats class values give min and max
     * points for each stroke or average medians. It is used by the stroke analyzer to find catch
     * and finish positions.
     */
    val statsMap = mapOf(
        Pair(BodyPart.LEFT_WRIST, Stats()),
        Pair(BodyPart.LEFT_ELBOW, Stats()),
        Pair(BodyPart.LEFT_SHOULDER, Stats()),
        Pair(BodyPart.LEFT_HIP, Stats()),
        Pair(BodyPart.LEFT_KNEE, Stats()),
        Pair(BodyPart.LEFT_ANKLE, Stats()),
        Pair(BodyPart.LEFT_EAR, Stats()),
    )

    /**
     * Current body part positions. These values are from the posenet estimations of the last
     * video frame.
     */
    val currentPoints = mutableMapOf<BodyPart, Point>()

    /**
     * Position of wrist at the last catch.
     */
    var catchWrist: Point? = null

    /**
     * Position of elbow at the last catch.
     */
    var catchElbow: Point? = null

    /**
     * Position of hip at the last catch.
     */
    var catchHip: Point? = null

    /**
     * Position of ear at the last catch.
     */
    var catchEar: Point? = null

    /**
     * Hard coded position of ankle.
     */
    var fixedAnkle = Point(82, 167, 0)

    /**
     * Position of knee at the last catch.
     */
    var catchKnee: Point? = null

    /**
     * Position of shoulder at the last catch.
     */
    var catchShoulder: Point? = null

    /**
     * Position of wrist at the last finish.
     */
    var finishWrist: Point? = null

    /**
     * Position of elbow at the last finish.
     */
    var finishElbow: Point? = null

    /**
     * Position of hip at the last finish.
     */
    var finishHip: Point? = null

    /**
     * Position of knee at the last finish.
     */
    var finishKnee: Point? = null

    /**
     * Position of shoulder at the last finish.
     */
    var finishShoulder: Point? = null
    /**
     * List of frame bitmaps and associated metadata for the current stroke.
     */
    var frames = mutableListOf<Frame>()

    /**
     * Current bitmap of rower
     */
    var currentBitmap: Bitmap? = null

    /**
     *  Current wrist position.
     */
    val currentWrist get() = currentPoints[BodyPart.LEFT_WRIST]

    /**
     *  Current wrist position.
     */
    val currentElbow get() = currentPoints[BodyPart.LEFT_ELBOW]

    /**
     * Current hip position.
     */
    val currentHip get() = currentPoints[BodyPart.LEFT_HIP]

    /**
     * Current ankle position.
     */
    val currentAnkle get() = currentPoints[BodyPart.LEFT_ANKLE]

    /**
     * Current knee position.
     */
    val currentKnee get() = currentPoints[BodyPart.LEFT_KNEE]

    /**
     * Current shoulder position.
     */
    val currentShoulder get() = currentPoints[BodyPart.LEFT_SHOULDER]

    /**
     * Current shoulder position.
     */
    val currentEar get() = currentPoints[BodyPart.LEFT_EAR]

    /**
     * Current stroke rate
     */
    var strokeRate: Int? = null
        set(value) {
            field = value
            value?.apply {
                if (this in 12..40) {
                    strokeRateAccumulator += this
                    strokeRateCounter++
                }
            }
        }

    private var strokeRateAccumulator: Int = 0
    private var strokeRateCounter: Int = 0

    /**
     * Average stroke rate
     */
    val averageStrokeRate: Int
        get() = if (strokeRateCounter == 0) 0 else strokeRateAccumulator / strokeRateCounter

    /**
     * Current slide ratio measured as (drive duration) / (recovery duration).
     */
    var slideRatio: Float? = null

    /**
     * Current phase of the stroke.
     */
    var phase: Phase = Phase.DRIVE

    /**
     * Current position between catch and finish as a percent where 0% is the catch and 100% is
     * the finish. It is measured from last catch position to current position. For example, 50%
     * can be either half way during the drive phase or half way during the recovery phase.
     */
    var catchFinishPct = 0

    /**
     * Estimated stroke percent where 0 is the catch, 50 is finish, and approaches 100 during the
     * end of the recovery when approaching the next catch.
     */
    val strokePct
        get() = when (phase) {
            Phase.CATCH -> 0
            Phase.DRIVE -> catchFinishPct / 2
            Phase.FINISH -> 50
            Phase.RECOVERY -> 50 + (100 - catchFinishPct) / 2
        }

    /**
     * Current body angle in degrees. It is measured at the hip such that sitting upright is 90°,
     * leaning forward is < 90°, and leaning backwards is > 90°.
     */
    var currentBodyAngle: Double? = null

    /**
     * Current shin angle in degrees. It is measured such that vertical is  90°,
     * leaning forward is < 90°, and leaning backwards is > 90°.
     */
    var currentShinAngle: Double? = null

    /**
     * Body angle at the catch in degrees. It is measured at the hip such that sitting upright is
     * 90°, leaning forward is < 90°, and leaning backwards is > 90°.
     */
    var catchBodyAngle: Double? = null

    /**
     * Body angle at the finish in degrees. It is measured at the hip such that sitting upright is
     * 90°, leaning forward is < 90°, and leaning backwards is > 90°.
     */
    var finishBodyAngle: Double? = null

    /**
     * Shin angle at the catch in degrees. It is measured such that vertical is  90°,
     * leaning forward is < 90°, and leaning backwards is > 90°.
     */
    var catchShinAngle: Double? = null

    /**
     * Shin length in pixels for the last 30 frames.
     */
    var averageShinLength: Float? = null

    /**
     * Thigh length in pixels for the last 30 frames.
     */
    var averageThighLength: Float? = null

    /**
     * Body length in pixels for the last 30 frames.
     */
    var averageBodyLength: Float? = null

    /**
     * Upper arm length in pixels for the last 30 frames.
     */
    var averageUpperArmLength: Float? = null

    /**
     * Forearm length in pixels for the last 30 frames.
     */
    var averageForearmLength: Float? = null

    /**
     * Shin length in pixels.
     */
    var currentShinLength: Float? = null

    /**
     * Thigh length in pixels.
     */
    var currentThighLength: Float? = null

    /**
     * Body length in pixels.
     */
    var currentBodyLength: Float? = null

    /**
     * Upper arm length in pixels.
     */
    var currentUpperArmLength: Float? = null

    /**
     * Forearm length in pixels.
     */
    var currentForearmLength: Float? = null

    /**
     * The duration in milliseconds of the drive phase of the previous stroke.
     */
    var driveMs = 0L

    /**
     * The time of when the last finish was detected (ms since midnight, January 1, 1970 UTC)
     */
    var timeOfLatestFinish = 0L

    /**
     * List of faults queued during this stroke.
     */
    val strokeFaults = mutableSetOf<String>()

    /**
     * Hip y values for the last 30 frames
     */
    val hipHeights = LimitedList(30, 10)

    /**
     * Finish knee y values for the last 10 strokes
     */
    val finishKneeHeights = LimitedList(10, 6)

    /**
     * Compare forearm and upper arm lengths for this frame against the average lengths.
     * Large deviations from average are an indication that there is an error in detection.
     *
     * Deviation from average as a percent or 100 if lengths are missing.
     */
    var armDeviationPercent = 0

    /**
     * Compare shin and thigh lengths for this frame against the average lengths.
     * Large deviations from average are an indication that there is an error in detection.
     *
     * Deviation from average as a percent or 100 if lengths are missing.
     */
    var legDeviationPercent = 0

    /**
     * Time of last catch that gets updated at finish. After finish this is the same as current
     * catch time.
     */
    var catchTimePreviousToFinish = 0L

    /**
     * Calculate angle of the line between two points.
     *
     * @param p1 point 1
     * @param p2 point 2
     * @return angle in degrees
     */
    fun angle(p1: Point, p2: Point): Double {
        var angle = Math.toDegrees(atan2(p1.y.toDouble() - p2.y, p1.x.toDouble() - p2.x))

        if (angle < 0) {
            angle += 360f
        }
        return angle
    }

    /**
     * Calculate distance of the line between two body parts.
     *
     * @param p1 body part 1
     * @param p2 body part 2
     * @return angle in degrees
     */
    fun currentDistance(p1: BodyPart, p2: BodyPart): Double {
        val point1 = currentPoints[p1]!!
        val point2 = currentPoints[p2]!!
        return distance(point1, point2)
    }

    /**
     * Calculate the distance between two points.
     *
     * @param p1 point 1
     * @param p2 point 2
     * @return distance in pixels
     */
    private fun distance(p1: Point, p2: Point): Double {
        return hypot(p2.x - p1.x.toDouble(), p2.y - p1.y.toDouble())
    }

    /**
     * Calculate the distance between two points.
     *
     * @param p1 point 1
     * @param p2 point 2
     * @return distance in pixels
     */
    fun distance(p1: KeyPoint, p2: KeyPoint): Double {
        return hypot(
            p2.position.x - p1.position.x.toDouble(),
            p2.position.y - p1.position.y.toDouble()
        )
    }

    /**
     * Clear all values.
     */
    fun reset() {
        Log.i(TAG, "${System.currentTimeMillis()} Resetting rower")
        isRowing = false
        startTime = null
        endTime = null
        strokeCount = 0
        statsMap.forEach { (_, stats) -> stats.clear() }
        currentPoints.clear()
        catchWrist = null
        catchElbow = null
        catchHip = null
        catchEar = null
        catchKnee = null
        catchShoulder = null
        finishWrist = null
        finishElbow = null
        finishHip = null
        finishKnee = null
        finishShoulder = null
        frames.clear()
        currentBitmap = null
        strokeRate = null
        strokeRateAccumulator = 0
        strokeRateCounter = 0
        slideRatio = null
        phase = Phase.DRIVE
        catchFinishPct = 0
        currentBodyAngle = null
        currentShinAngle = null
        catchBodyAngle = null
        finishBodyAngle = null
        catchShinAngle = null
        averageThighLength = null
        averageBodyLength = null
        averageForearmLength = null
        averageUpperArmLength = null
        currentShinLength = null
        currentThighLength = null
        currentBodyLength = null
        currentUpperArmLength = null
        currentForearmLength = null
        driveMs = 0L
        timeOfLatestFinish = 0L
        strokeFaults.clear()
        hipHeights.list.clear()
        finishKneeHeights.list.clear()
        armDeviationPercent = 0
        legDeviationPercent = 0
        catchTimes.clear()
        errorStrokeCount = 0
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "Rower"
    }
}
