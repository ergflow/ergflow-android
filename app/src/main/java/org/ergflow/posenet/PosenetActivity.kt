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

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings.PluginState
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import org.ergflow.Report
import org.ergflow.StrokeAnalyzer
import org.ergflow.posenet.databinding.TfePnActivityPosenetBinding
import org.ergflow.ui.ItemArrayAdapter
import org.tensorflow.lite.examples.posenet.lib.BodyPart
import org.tensorflow.lite.examples.posenet.lib.Person
import org.tensorflow.lite.examples.posenet.lib.Posenet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs

const val WEBVIEW_URL = "org.ergflow.WebView.Url"
const val REPORT_TIMESTAMP = "org.ergflow.WebView.ReportTimestamp"
const val CACHED_REPORT = "org.ergflow.WebView.CachedReport"

class PosenetActivity :
    Fragment(),
    ActivityCompat.OnRequestPermissionsResultCallback {

    /** Threshold for confidence score. */
    private val minConfidence = 0.5

    /** Paint class holds the style and color information to draw geometries,text and bitmaps. */
    private var paint = Paint()

    /** A shape for extracting frame data.   */
    private val PREVIEW_WIDTH = 640
    private val PREVIEW_HEIGHT = 480

    /** An object for the Posenet library.    */
    private lateinit var posenet: Posenet

    /** ID of the current [CameraDevice].   */
    private var cameraId: String? = null

    /** True if camera is on port side otherwise false*/
    private var cameraOnRight = true

    /** ID of the front [CameraDevice].   */
    private var frontCameraId: String? = null

    /** ID of the back [CameraDevice].   */
    private var backCameraId: String? = null

    private var _binding: TfePnActivityPosenetBinding? = null

    /** View binding (only valid between onCreateView and onDestroyView).*/
    private val binding get() = _binding!!

    /** A [SurfaceView] for camera preview.   */
    private var surfaceView: SurfaceView? = null

    /** A [CameraCaptureSession] for camera preview.   */
    private var captureSession: CameraCaptureSession? = null

    /** A reference to the opened [CameraDevice].    */
    private var cameraDevice: CameraDevice? = null

    /** The [android.util.Size] of camera preview.  */
    private var previewSize: Size? = null

    /** The [android.util.Size.getWidth] of camera preview. */
    private var previewWidth = 0

    /** The [android.util.Size.getHeight] of camera preview.  */
    private var previewHeight = 0

    /** An IntArray to save image data in ARGB8888 format  */
    private lateinit var rgbBytes: IntArray

    /** A ByteArray to save image data in YUV format  */
    private var yuvBytes = arrayOfNulls<ByteArray>(3)

    /** An additional thread for running tasks that shouldn't block the UI.   */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background.    */
    private var backgroundHandler: Handler? = null

    /** An [ImageReader] that handles preview frame capture.   */
    private var imageReader: ImageReader? = null

    /** [CaptureRequest.Builder] for the camera preview   */
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    /** [CaptureRequest] generated by [.previewRequestBuilder   */
    private var previewRequest: CaptureRequest? = null

    /** A [Semaphore] to prevent the app from exiting before closing the camera.    */
    private val cameraOpenCloseLock = Semaphore(1)

    /** Whether the current camera device supports Flash or not.    */
    private var flashSupported = false

    /** Orientation of the camera sensor.   */
    private var sensorOrientation: Int? = null

    /** Abstract interface to someone holding a display surface.    */
    private var surfaceHolder: SurfaceHolder? = null

    private var strokeAnalyzer: StrokeAnalyzer? = null

    var itemArrayAdater: ItemArrayAdapter? = null

    /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.   */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@PosenetActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@PosenetActivity.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@PosenetActivity.activity?.finish()
        }
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    fun onToolbarItem(item: MenuItem) {
        when (item.itemId) {
            R.id.item_front_cam -> {
                closeCamera()
                stopBackgroundThread()
                cameraId = frontCameraId
                startBackgroundThread()
                openCamera()
            }
            R.id.item_back_cam -> {
                closeCamera()
                stopBackgroundThread()
                cameraId = backCameraId
                startBackgroundThread()
                openCamera()
            }
            R.id.item_cam_on_left -> {
                cameraOnRight = false
            }
            R.id.item_cam_on_right -> {
                cameraOnRight = true
            }
            R.id.item_save_report -> {
                saveReport()
            }
            else -> {
            }
        }
    }

    private fun saveReport() {
        strokeAnalyzer?.rower?.duration?.let {
            if (it < 15_000) {
                showToast("No session to report on")
                return
            }
        }
        val report = strokeAnalyzer?.coach?.report
        report?.apply {
            generateReport()
            if (cachedReportPath == null) {
                showToast("Nothing to save yet")
                return
            }
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(WEBVIEW_URL, cachedReportPath)
                val timeStamp: String = report?.rower?.let {
                    SimpleDateFormat("yyyy-MM-dd_HH.mm.ss",
                        Locale.getDefault()).format(Date(it.startTime ?:
                            System.currentTimeMillis()))
                } ?: ""
                putExtra(REPORT_TIMESTAMP, timeStamp)
                putExtra(CACHED_REPORT, report.cachedReportPath)
            }

            startActivityForResult(intent, 1)
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = TfePnActivityPosenetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        surfaceView = binding.surfaceView
        surfaceView!!.setZOrderOnTop(true)
        surfaceHolder = surfaceView!!.holder
        surfaceHolder?.setFormat(PixelFormat.TRANSLUCENT)
        itemArrayAdater = ItemArrayAdapter(context, R.layout.item_layout)
        val state = binding.listView.onSaveInstanceState()
        binding.listView.adapter = itemArrayAdater
        binding.listView.onRestoreInstanceState(state)
        binding.listView.isNestedScrollingEnabled = true
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    override fun onStart() {
        super.onStart()
        openCamera()
        posenet = Posenet(this.context!!)
        strokeAnalyzer = StrokeAnalyzer(this.context!!, ::saveReport)
        strokeAnalyzer!!.display.itemArrayAdapter = itemArrayAdater
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onDestroy() {
        strokeAnalyzer?.rower?.duration?.let {
            if (it > 15_000) {
                saveReport()
            }
        }
        super.onDestroy()
        posenet.close()
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted(grantResults)) {
                ErrorDialog.newInstance(getString(R.string.tfe_pn_request_permission))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray) = grantResults.all {
        it == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sets up member variables related to camera.
     */
    private fun setUpCameraOutputs() {
        val activity = activity ?: return
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = cameraId
                    continue
                }

                if (cameraDirection != null && cameraDirection != CameraCharacteristics.LENS_FACING_FRONT) {
                    // Only front and back cameras currently supported
                    continue
                }

                frontCameraId = cameraId

                previewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

                imageReader = ImageReader.newInstance(PREVIEW_WIDTH,
                    PREVIEW_HEIGHT,
                    ImageFormat.YUV_420_888, /*maxImages*/
                    2)

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

                previewHeight = previewSize!!.height
                previewWidth = previewSize!!.width

                // Initialize the storage bitmaps once when the resolution is known.
                rgbBytes = IntArray(previewWidth * previewHeight)

                // Check if the flash is supported.
                flashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                if (this.cameraId == null) {
                    this.cameraId = cameraId
                }

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.tfe_pn_camera_error))
                .show(childFragmentManager, FRAGMENT_DIALOG)
        }
    }

    /**
     * Opens the camera specified by [PosenetActivity.cameraId].
     */
    private fun openCamera() {
        val permissionCamera =
            context?.checkPermission(Manifest.permission.CAMERA, Process.myPid(), Process.myUid())
                ?: PackageManager.PERMISSION_GRANTED
        if (permissionCamera != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        }
        val activity = this.activity ?: return
        setUpCameraOutputs()
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        if (captureSession == null) {
            return
        }

        try {
            cameraOpenCloseLock.acquire()
            captureSession!!.close()
            captureSession = null
            cameraDevice!!.close()
            cameraDevice = null
            imageReader!!.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("imageAvailableListener").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    /** Fill the yuvBytes with data from image planes.   */
    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Row stride is the total number of bytes occupied in memory by a row of an image.
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i]!!)
        }
    }

    /** A [OnImageAvailableListener] to receive frames as they are available.  */
    private var imageAvailableListener = object : OnImageAvailableListener {
        override fun onImageAvailable(imageReader: ImageReader) {
            // We need wait until we have some size from onPreviewSizeChosen
            if (previewWidth == 0 || previewHeight == 0) {
                return
            }

            val image = imageReader.acquireLatestImage() ?: return
            fillBytes(image.planes, yuvBytes)

            ImageUtils.convertYUV420ToARGB8888(yuvBytes[0]!!,
                yuvBytes[1]!!,
                yuvBytes[2]!!,
                previewWidth,
                previewHeight,
                /*yRowStride=*/
                image.planes[0].rowStride,
                /*uvRowStride=*/
                image.planes[1].rowStride,
                /*uvPixelStride=*/
                image.planes[1].pixelStride,
                rgbBytes)

            // Create bitmap from int array
            val imageBitmap =
                Bitmap.createBitmap(rgbBytes, previewWidth, previewHeight, Bitmap.Config.ARGB_8888)

            image.close()

            processImage(imageBitmap)
            imageBitmap.recycle()
        }
    }

    /** Crop Bitmap to maintain aspect ratio of model input.   */
    private fun cropBitmap(bitmap: Bitmap): Bitmap {
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width
        val modelInputRatio = MODEL_HEIGHT.toFloat() / MODEL_WIDTH
        var croppedBitmap = bitmap

        // Acceptable difference between the modelInputRatio and bitmapRatio to skip cropping.
        val maxDifference = 1e-5

        // Checks if the bitmap has similar aspect ratio as the required model input.
        when {
            abs(modelInputRatio - bitmapRatio) < maxDifference -> return croppedBitmap
            modelInputRatio < bitmapRatio -> {
                // New image is taller so we are height constrained.
                val cropHeight = bitmap.height - (bitmap.width.toFloat() / modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(bitmap,
                    0,
                    (cropHeight / 2).toInt(),
                    bitmap.width,
                    (bitmap.height - cropHeight).toInt())
            }
            else -> {
                val cropWidth = bitmap.width - (bitmap.height.toFloat() * modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(bitmap,
                    (cropWidth / 2).toInt(),
                    0,
                    (bitmap.width - cropWidth).toInt(),
                    bitmap.height)
            }
        }
        return flip(croppedBitmap)
    }

    fun flip(bInput: Bitmap): Bitmap {
        if (cameraOnRight) {
            val matrix = Matrix()
            matrix.preScale(-1.0f, 1.0f)
            return Bitmap.createBitmap(bInput, 0, 0, bInput.width, bInput.height, matrix, true)
        }
        return bInput
    }

    /** Set the paint color and size.    */
    private fun setPaint() {
        paint.color = Color.RED
        paint.textSize = 80.0f
        paint.strokeWidth = 8.0f
    }

    /** Draw bitmap on Canvas.   */
    private fun draw(canvas: Canvas, person: Person, bitmap: Bitmap) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        // Draw `bitmap` and `person` in square canvas.
        val screenWidth: Int
        val screenHeight: Int
        val left: Int
        val top: Int
        if (canvas.height > canvas.width) {
            screenWidth = canvas.width
            screenHeight = canvas.width
            left = 0
            top = (canvas.height - canvas.width) / 2
        } else {
            screenWidth = canvas.height
            screenHeight = canvas.height
            left = 0
            top = 0
        }
        val right: Int = left + screenWidth
        val bottom: Int = top + screenHeight

        setPaint()
        canvas.drawBitmap(bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(left, top, right, bottom),
            paint)

        if (person.score >= minConfidence) {
            strokeAnalyzer?.analyzeFrame(person, bitmap)
        }
        strokeAnalyzer?.updateDisplay(canvas)

        // Draw!
        surfaceHolder!!.unlockCanvasAndPost(canvas)
    }

    /** Process image using Posenet library.   */
    private fun processImage(bitmap: Bitmap) {
        // Crop bitmap.
        val croppedBitmap = cropBitmap(bitmap)

        // Created scaled version of bitmap for model input.
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MODEL_WIDTH, MODEL_HEIGHT, true)

        // Perform inference.
        val person = posenet.estimateSinglePose(scaledBitmap)
        surfaceHolder?.lockCanvas()?.let {
            draw(it, person, scaledBitmap)
        }
        croppedBitmap.recycle()
        scaledBitmap.recycle()
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            // We capture images from preview in YUV format.
            imageReader = ImageReader.newInstance(previewSize!!.width,
                previewSize!!.height,
                ImageFormat.YUV_420_888,
                2)
            imageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

            // This is the surface we need to record images for processing.
            val recordingSurface = imageReader!!.surface

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(recordingSurface)

            // Here, we create a CameraCaptureSession for camera preview.

            val callback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    // The camera is already closed
                    if (cameraDevice == null) return

                    // When the session is ready, we start displaying the preview.
                    captureSession = cameraCaptureSession
                    try {
                        // Auto focus should be continuous for camera preview.
                        previewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        // Flash is automatically enabled when necessary.
                        setAutoFlash(previewRequestBuilder!!)

                        // Finally, we start displaying the camera preview.
                        previewRequest = previewRequestBuilder!!.build()
                        captureSession!!.setRepeatingRequest(previewRequest!!, null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, e.toString())
                    }
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    showToast("Failed")
                }
            }
            val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(recordingSurface)),
                Executors.newCachedThreadPool(),
                callback)

            cameraDevice!!.createCaptureSession(sessionConfiguration)
//        cameraDevice!!.createCaptureSession(
//            listOf(recordingSurface),
//            callback,
//            null,
//            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (flashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        }
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity).setMessage(arguments!!.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ -> activity!!.finish() }.create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }

    companion object {

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private const val FRAGMENT_DIALOG = "dialog"

        /** List of body joints that should be connected.    */
        val bodyJoints = listOf(
            Pair(BodyPart.LEFT_WRIST, BodyPart.LEFT_ELBOW),
            Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_SHOULDER),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
            Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
            Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
            Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
        )

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Tag for the [Log].
         */
        private const val TAG = "PosenetActivity"
    }
}
