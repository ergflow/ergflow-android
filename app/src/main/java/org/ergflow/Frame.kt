package org.ergflow

import android.graphics.Bitmap
import org.tensorflow.lite.examples.posenet.lib.BodyPart

/**
 * Individual frame bitmap and associated metadata.
 */
class Frame(val bitmap: Bitmap?) {

    var time = 0L
    var timeSinceCatchMs = 0L
    var strokeCount = 0
    var points = mapOf<BodyPart, Point>()
    var strokeRate = 0
    var phase = Rower.Phase.DRIVE
    var strokePosPct = 0
    var bodyAngle = 0.0
}
