package org.ergflow

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.ergflow.rubric.BaseFaultChecker
import org.ergflow.rubric.CatchAngle
import org.ergflow.rubric.EarlyDriveBodyAngle
import org.ergflow.rubric.FaultChecker
import org.ergflow.rubric.HandLevels
import org.ergflow.rubric.HandsOut
import org.ergflow.rubric.Layback
import org.ergflow.rubric.LungingAtCatch
import org.ergflow.rubric.RushingTheSlide
import org.ergflow.rubric.ShinAngle
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.KeyPoint
import java.io.BufferedReader

/**
 * Class that watches the rower during the various phases of the stroke and looks for faults.
 */
class Coach(val context: Context, val rower: Rower) {

    /**
     * A target position is a list of expected positions for a specific segment of the
     * stroke.
     */
    class TargetPosition {
        var keyPoints = listOf<KeyPoint>()
    }

    /**
     * Events sent to the fault checker listeners.
     */
    enum class Event {
        CATCH, DRIVE_UPDATE, FINISH, RECOVERY_UPDATE
    }

    val display = Display(this)

    private var textToSpeech: TextToSpeech? = null
    var targetPositions = listOf<TargetPosition>()

    private var previousDrivePoints = mutableListOf<Map<BodyPart, Point>>()
    private var previousRecoveryPoints = mutableListOf<Map<BodyPart, Point>>()
    var listeners = mutableListOf<(Event) -> Unit>()

    // Instantiate fault checkers
    val rushing = RushingTheSlide(this)
    val layback = Layback(this)
    val catch = CatchAngle(this)
    val shins = ShinAngle(this)
    val earlyDriveBodyAngle = EarlyDriveBodyAngle(this)
    val lungingAtCatch = LungingAtCatch(this)
    val handLevels = HandLevels(this)
    val handsOut = HandsOut(this)
    val faultCheckers: List<BaseFaultChecker> = listOf(
        handLevels,
        handsOut,
        rushing,
        layback,
        catch,
        shins,
        earlyDriveBodyAngle,
        lungingAtCatch,
    )

    val report = Report(rower, faultCheckers, context)
    private var timeOfLastCatch = 0L
    private var timeOfLastFinish = 0L

    init {
        val json = context.assets.open("targetPositions.json").bufferedReader()
            .use(BufferedReader::readText)
        val jsonType = object : TypeToken<List<TargetPosition>>() {}.type
        targetPositions = Gson().fromJson(json, jsonType)
        textToSpeech = TextToSpeech(context) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech?.setPitch(1f)
            }
        }
    }

    /**
     * This method is called by the stroke analyzer for every pose estimation.
     */
    fun onUpdate() {
        when (rower.phase) {
            Rower.Phase.CATCH -> {
                if (System.currentTimeMillis() - timeOfLastCatch > 500) {
                    onCatch()
                    handleFaults()
                    timeOfLastCatch = System.currentTimeMillis()
                }
            }
            Rower.Phase.DRIVE -> {
                onDriveUpdate()
                previousDrivePoints.add(rower.currentPoints.toMap())
            }
            Rower.Phase.FINISH -> {
                if (System.currentTimeMillis() - timeOfLastFinish > 500) {
                    onFinish()
                    previousRecoveryPoints.add(rower.currentPoints.toMap())
                    timeOfLastFinish = System.currentTimeMillis()
                }
            }
            Rower.Phase.RECOVERY -> {
                onRecoveryUpdate()
                previousRecoveryPoints.add(rower.currentPoints.toMap())
            }
        }
    }

    /**
     * This method is called by the stroke analyzer at the end of the stroke.
     */
    fun onEndOfStroke() {
        Log.i(TAG, "end of stroke")
        if (rower.isRowing) {
            report.saveFaultImages()
        }
        clearStrokeData()
    }

    private fun handleFaults() {
        if (!rower.isRowing) {
            return
        }

        // Revisit the IGNORE_AND_MOVE_ON faults after a while by setting status to GOOD
        faultCheckers.filter {
            it.status == FaultChecker.Status.IGNORE_AND_MOVE_ON &&
                System.currentTimeMillis() - it.timeOfLastMessage > UNIGNORE_TIME_INTERVAL_MS
        }.forEach { it.status = FaultChecker.Status.GOOD }

        // Send a message for anything with 3 or more bad strokes in a row but only if
        // not marked as ignore
        faultCheckers.firstOrNull {
            it.badConsecutiveStrokes > 3 &&
                it.status != FaultChecker.Status.IGNORE_AND_MOVE_ON
        }?.apply {
            rower.strokeFaults.add(title)
            if (System.currentTimeMillis() - timeOfLastMessage < REMINDER_TIME_INTERVAL_MS) {
                return@apply
            }
            timeOfLastMessage = System.currentTimeMillis()
            if (initialMessageSent) {
                say(getFaultReminderMessage())
                status = FaultChecker.Status.IGNORE_AND_MOVE_ON
            } else {
                say(getFaultInitialMessage())
                initialMessageSent = true
                status = FaultChecker.Status.FAULT_MESSAGE_SENT
            }
        }

        // Send fixed message and update statuses for fixed faults
        faultCheckers.filter { it.badConsecutiveStrokes == 0 && it.status != FaultChecker.Status.GOOD }
            .forEach {
                it.status = FaultChecker.Status.GOOD
                say(it.getFixedMessage())
            }
    }

    private fun say(message: String) {
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        if (message.length > maxLength) {
            Log.w(
                TAG,
                "Message length ${message.length} > TextToSpeech.getMaxSpeechInputLength() $maxLength"
            )
        }
        display.showToast(message)
        Log.i(TAG, message.take(maxLength))
        textToSpeech?.speak(
            message.take(maxLength),
            TextToSpeech.QUEUE_ADD,
            null,
            "${System.currentTimeMillis()}"
        ).also { Log.d(TAG, "speak response: $it") }
    }

    private fun onCatch() {
        listeners.forEach { listener -> listener(Event.CATCH) }
    }

    private fun onDriveUpdate() {
        listeners.forEach { listener -> listener(Event.DRIVE_UPDATE) }
    }

    private fun onFinish() {
        listeners.forEach { listener -> listener(Event.FINISH) }
    }

    private fun onRecoveryUpdate() {
        listeners.forEach { listener -> listener(Event.RECOVERY_UPDATE) }
    }

    private fun clearStrokeData() {
        previousDrivePoints.clear()
        previousRecoveryPoints.clear()
        rower.strokeFaults.clear()
    }

    fun saveStats() {
        Log.i(TAG, "saveStats generateReport")
        report.generateReport()
    }

    fun reset() {
        faultCheckers.forEach { it.clear() }
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "Coach"

        /**
         * The amount of time to wait before sending the second message if a fault is not fixed.
         */
        const val REMINDER_TIME_INTERVAL_MS = 30_000L

        /**
         * If fault is not corrected after the reminder then ignore until after this time.
         */
        const val UNIGNORE_TIME_INTERVAL_MS = 60_000L
    }
}
