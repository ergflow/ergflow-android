package org.ergflow.rubric

import org.ergflow.Coach
import org.ergflow.Rower

class RushingTheSlide(coach: Coach) : BaseFaultChecker(coach) {

    override val title = "Slide Ratio"
    override val description = "Checks that recovery duration is longer than drive duration."

    override val strokeHistoryUnit = " D/R"

    override fun getFaultInitialMessage(): String {
        return "Slow down the slide. Recovery should take longer than the drive"
    }

    override fun getFaultReminderMessage(): String {
        return "Keep working on slowing down the slide during the recovery."
    }

    override fun getFixedMessage(): String {
        return ""
    }

    private val maxSlideRatio = 1f

    init {
        coach.listeners.add(this::onEvent)
    }

    override fun onEvent(event: Coach.Event) {
        if (event == Coach.Event.CATCH) {
            rower.slideRatio?.apply {
                // assume ratios outside the range (.2, 2) are ignorable errors in detection
                if (this > .2 && this < 2) {
                    strokeHistory.add(this)
                    if (this <= maxSlideRatio) {
                        goodStroke()
                    } else {
                        badStroke()
                    }
                }
            }
        }
    }

    override fun updateDisplay() {
        if (status == FaultChecker.Status.FAULT_MESSAGE_SENT && rower.phase != Rower.Phase.DRIVE) {
            coach.display.showTargetPositions()
        }
    }
}
