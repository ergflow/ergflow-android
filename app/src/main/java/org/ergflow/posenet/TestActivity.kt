/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ergflow.posenet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import org.ergflow.Point
import org.ergflow.StrokeAnalyzer
import org.ergflow.posenet.databinding.TfePnActivityTestBinding
import org.ergflow.ui.ItemArrayAdapter
import org.ergflow.ui.ItemArrayAdapter.Item
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.Posenet

class TestActivity : AppCompatActivity() {

    private lateinit var binding: TfePnActivityTestBinding

    /** Returns a resized bitmap of the drawable image.    */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(257, 257, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, canvas.width, canvas.height)

        drawable.draw(canvas)
        return bitmap
    }

    /** Calls the Posenet library functions.    */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TfePnActivityTestBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val sampleImageView = binding.image
        val drawedImage = ResourcesCompat.getDrawable(resources, R.drawable.murray, null)
        val imageBitmap = drawableToBitmap(drawedImage!!)
        sampleImageView.setImageBitmap(imageBitmap)
        val posenet = Posenet(this.applicationContext)
        val person = posenet.estimateSinglePose(imageBitmap)

        // Draw the keyPoints over the image.
        val paint = Paint()
        paint.color = Color.RED
        val size = 2.0f

        val mutableBitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val actualKeyPoints = mutableMapOf<BodyPart, Point>()
        for (keyPoint in person.keyPoints) {
            if (keyPoint.bodyPart.name.startsWith("LEFT")) {
                canvas.drawCircle(
                    keyPoint.position.x.toFloat(),
                    keyPoint.position.y.toFloat(),
                    size,
                    paint
                )
                actualKeyPoints[keyPoint.bodyPart] =
                    Point(keyPoint.position.x, keyPoint.position.y, System.currentTimeMillis())
            }
        }

        val itemArrayAdater = ItemArrayAdapter(applicationContext, R.layout.item_layout)
        val strokeAnalyzer = StrokeAnalyzer(this.applicationContext) {}
        strokeAnalyzer.analyzeFrame(person, mutableBitmap)
        strokeAnalyzer.updateDisplay(canvas)
        val rower = strokeAnalyzer.rower
        rower.finishHip = actualKeyPoints[BodyPart.LEFT_HIP]
        rower.finishShoulder = actualKeyPoints[BodyPart.LEFT_SHOULDER]
        rower.driveMs = 1200L
        rower.strokeRate = 20

        sampleImageView.adjustViewBounds = true
        sampleImageView.setImageBitmap(mutableBitmap)

        val state = binding.listView.onSaveInstanceState()
        binding.listView.adapter = itemArrayAdater
        binding.listView.onRestoreInstanceState(state)

        itemArrayAdater.addOrUpdate(
            Item(
                "a",
                "This is a very long item with lots and lots of" + " text in it. Not sure if it will wrap around!",
                "",
                "",
                null,
                null
            )
        )
        itemArrayAdater.addOrUpdate(
            Item(
                "b",
                "asdfasf",
                "asdfsdf",
                "123",
                Color.parseColor("#D93F00"),
                null
            )
        ) // orange
        itemArrayAdater.addOrUpdate(Item("c", "dup", "asdfsqwrqrdf", "456", null, null))
        itemArrayAdater.addOrUpdate(
            Item(
                "c",
                "xczvx",
                "asdfsqwrqrdf",
                "654",
                Color.parseColor("#80001F"),
                null
            )
        ) // red
        itemArrayAdater.addOrUpdate(
            Item(
                "e",
                "zbvbcv",
                "asdfsqwrqrdf",
                "78946532",
                Color.parseColor("#607000"),
                null
            )
        ) // green
//        val tableRight = binding.tableRight
//        strokeAnalyzer?.rower?.apply {
//            runOnUiThread {
//                addRow(
//                    "Test",
//                    "Overall Mark",
//                    "Last 10 Strokes",
//                    "Last Stroke",
//                    true
//                )
//                addRow(
//                    "Stroke Rate",
//                    rower.strokeRate.toString(),
//                    rower.strokeRate.toString(),
//                    "asfasd",
//                    false
//                )
//            }
//        }
    }

//    fun addRow(
//        lable: String,
//        overallMark: String,
//        lastTenMark: String,
//        lastStroke: String,
//        headerRow: Boolean
//    ) {
//        val table = binding.tableRight
//        table.addView(
//            TableRow(applicationContext).apply {
//                layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT)
//                addView(
//                    TextView(context).apply {
//                        text = lable
//                        setTextColor(Color.WHITE)
//                        setPadding(10, 0, 0, 0)
//                        if (headerRow) paintFlags = Paint.UNDERLINE_TEXT_FLAG
//                    }
//                )
//                addView(
//                    TextView(context).apply {
//                        text = overallMark
//                        setTextColor(Color.WHITE)
//                        setPadding(10, 0, 0, 0)
//                        if (headerRow) paintFlags = Paint.UNDERLINE_TEXT_FLAG
//                    }
//                )
//                addView(
//                    TextView(context).apply {
//
//                        text = lastTenMark
//                        setTextColor(Color.WHITE)
//                        setPadding(10, 0, 0, 0)
//                        if (headerRow) paintFlags = Paint.UNDERLINE_TEXT_FLAG
//                    }
//                )
//                addView(
//                    TextView(context).apply {
//                        text = lastStroke
//                        setTextColor(Color.WHITE)
//                        setPadding(10, 0, 0, 0)
//                        if (headerRow) paintFlags = Paint.UNDERLINE_TEXT_FLAG
//                    }
//                )
//            }
//        )
//    }
}
