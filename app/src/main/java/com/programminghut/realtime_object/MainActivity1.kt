package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class MainActivity1 : AppCompatActivity() {

    lateinit var labels: List<String>
    var colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY,
        Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1
    lateinit var textToSpeech: TextToSpeech
    var detectionResults = mutableListOf<DetectionResult>()

    data class DetectionResult(val label: String, val score: Float, val rect: RectF)

    // Define a distance threshold in pixels (you can adjust this value)
    private val CLOSE_DISTANCE_THRESHOLD = 30 // Threshold in pixels

    private var canSpeakWarning = true // Flag to control warning speech
    private val warningHandler = Handler() // Handler for managing delays
    private var warningsEnabled = true // Flag to control warning enable/disable

    private lateinit var toggleButton: Button // Button to toggle warnings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.US
            }
        }

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        toggleButton = findViewById(R.id.toggleButton) // Initialize the toggle button

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                detectionResults.clear() // Clear previous results
                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x = 0
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        val label = labels[classes[index].toInt()]
                        val rect = RectF(
                            locations[x + 1] * w,
                            locations[x] * h,
                            locations[x + 3] * w,
                            locations[x + 2] * h
                        )
                        detectionResults.add(DetectionResult(label, fl, rect))

                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(rect, paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText("$label ${fl.toString()}", rect.left, rect.top, paint)

                        // Check if the detected object is too close
                        checkProximity(label, rect)
                    }
                }

                imageView.setImageBitmap(mutable)
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Set touch listener on imageView
        imageView.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                for (result in detectionResults) {
                    if (result.rect.contains(x, y)) {
                        // Determine position
                        val centerX = (result.rect.left + result.rect.right) / 2
                        val screenWidth = imageView.width.toFloat()
                        val position = when {
                            centerX < screenWidth / 3 -> "in the left"
                            centerX < 2 * screenWidth / 3 -> "at the center"
                            else -> "in the right"
                        }
                        speak(result.label, position)  // Pass the position to speak method
                        break
                    }
                }
            }
            true
        }

        // Set up the toggle button listener
        toggleButton.setOnClickListener {
            warningsEnabled = !warningsEnabled // Toggle warning state
            val buttonText = if (warningsEnabled) "Disable Warnings" else "Enable Warnings"
            toggleButton.text = buttonText // Update button text
            Toast.makeText(this, "Warnings are now ${if (warningsEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkProximity(label: String, rect: RectF) {
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2

        // Assuming that the user's position is approximately at the center of the screen
        val userX = imageView.width / 2
        val userY = imageView.height / 2

        // Calculate distance (in pixels) between user and object
        val distance = Math.sqrt(
            ((userX - centerX) * (userX - centerX) + (userY - centerY) * (userY - centerY)).toDouble()
        ).toFloat()

        // Check if the object is too close and warnings are enabled
        if (distance < CLOSE_DISTANCE_THRESHOLD && canSpeakWarning && warningsEnabled) {
            canSpeakWarning = false // Prevent further warnings until delay is over
            textToSpeech.speak("$label is in front of you", TextToSpeech.QUEUE_FLUSH, null, null)

            // Set a delay before allowing another warning
            warningHandler.postDelayed({
                canSpeakWarning = true // Re-enable warnings after delay
            }, 2000) // 2000 ms = 2 second delay
        }
    }

    private fun speak(text: String, position: String) {
        textToSpeech.speak("$text is $position", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        textToSpeech.shutdown() // Shutdown TTS
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {}

            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    fun get_permission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }
}
