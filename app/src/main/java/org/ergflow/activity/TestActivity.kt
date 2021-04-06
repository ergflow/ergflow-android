package org.ergflow.activity

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
import org.ergflow.activity.databinding.EfActivityTestBinding
import org.ergflow.activity.ui.ItemArrayAdapter
import org.ergflow.activity.ui.ItemArrayAdapter.Item
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.Posenet

class TestActivity : AppCompatActivity() {

    private lateinit var binding: EfActivityTestBinding

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
        binding = EfActivityTestBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val sampleImageView = binding.image
        val drawnImage = ResourcesCompat.getDrawable(resources, R.drawable.murray, null)
        val imageBitmap = drawableToBitmap(drawnImage!!)
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

        val itemArrayAdapter = ItemArrayAdapter(applicationContext, R.layout.ef_item_layout)
        val strokeAnalyzer = StrokeAnalyzer(this.applicationContext)
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
        binding.listView.adapter = itemArrayAdapter
        binding.listView.onRestoreInstanceState(state)

        itemArrayAdapter.addOrUpdate(
            Item(
                "a",
                "This is a very long item with lots and lots of text in it. Not sure if it will wrap around!",
                "",
                "",
                null,
                null
            )
        )
        itemArrayAdapter.addOrUpdate(
            Item(
                "b",
                "asdfasf",
                "asdfsdf",
                "123",
                Color.parseColor("#D93F00"),
                null
            )
        ) // orange
        itemArrayAdapter.addOrUpdate(Item("c", "dup", "asdfsqwrqrdf", "456", null, null))
        itemArrayAdapter.addOrUpdate(
            Item(
                "c",
                "xczvx",
                "asdfsqwrqrdf",
                "654",
                Color.parseColor("#80001F"),
                null
            )
        ) // red
        itemArrayAdapter.addOrUpdate(
            Item(
                "e",
                "zbvbcv",
                "asdfsqwrqrdf",
                "78946532",
                Color.parseColor("#607000"),
                null
            )
        )
    }
}
