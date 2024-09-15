package com.example.contextmonitorapp


import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.contextmonitorapp.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build

import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.View

import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
//import com.google.android.filament.View
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log
import kotlin.math.min


class MainActivity : AppCompatActivity(), SensorEventListener {


    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null  // Handle nullable accelerometer

    private val accelValuesX = mutableListOf<Float>()
    private val accelValuesY = mutableListOf<Float>()
    private val accelValuesZ = mutableListOf<Float>()


    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null  // VideoCapture for video recording
//    private var isRecording = false
    private  var heartRate: Int = 0
    private  var respiratoryRate: Int = 0
//    private var countdownTimer: CountDownTimer? = null

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Check if the device has an accelerometer
        if (accelerometer == null) {
            Toast.makeText(this, "No accelerometer found on this device", Toast.LENGTH_LONG).show()
        } else {
            binding.buttonRespiratoryRate.setOnClickListener {
                startRespiratoryRateSensing()
            }
        }

//        binding.buttonSave.setOnClickListener {
//            Toast.makeText(this, "Respiratory Rate saved!", Toast.LENGTH_SHORT).show()
//        }

//        binding.buttonSave.setOnClickListener { onSaveButtonClick() }

        // heart rate
        enableEdgeToEdge()
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up heart rate button functionality
        binding.buttonHeartRate.setOnClickListener {
            startVideoRecording()
        }

        binding.buttonNext.setOnClickListener {
            val intent = Intent(this, SymptomsActivity::class.java).apply{
                putExtra("HEART_RATE", heartRate.toFloat())
                putExtra("RESPIRATORY_RATE", respiratoryRate.toFloat())
            }

            startActivity(intent)
        }


    }

    private fun startRespiratoryRateSensing() {
        binding.textViewStatus.text = "Recoring Respiratory Rate..." // Show calculating status
        startCountdownTimer()
        // Register accelerometer listener
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelValuesX.add(event.values[0])
            accelValuesY.add(event.values[1])
            accelValuesZ.add(event.values[2])


        }
    }

    private fun stopRespiratoryRateSensing() {
        // Unregister the accelerometer listener to stop data collection
        sensorManager.unregisterListener(this)

        // Calculate the respiratory rate using collected data
        if (accelValuesY.isNotEmpty()) {
            respiratoryRate = respiratoryRateCalculator(accelValuesX, accelValuesY, accelValuesZ)

            binding.textViewStatus.text = "Respiratory Rate: $respiratoryRate"
            binding.buttonNext.visibility = View.VISIBLE

        } else {
            binding.textViewStatus.text = "No data collected for respiratory rate."
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No need to handle accuracy changes
    }

    private fun respiratoryRateCalculator(
        accelValuesX: MutableList<Float>,
        accelValuesY: MutableList<Float>,
        accelValuesZ: MutableList<Float>,
    ): Int {
        var previousValue = 10f
        var k = 0
        for (i in 11 until accelValuesY.size) {
            val currentValue = sqrt(
                accelValuesZ[i].toDouble().pow(2.0) +
                        accelValuesX[i].toDouble().pow(2.0) +
                        accelValuesY[i].toDouble().pow(2.0)
            ).toFloat()
            if (abs(previousValue - currentValue) > 0.15) {
                k++
            }
            previousValue = currentValue
        }
        val ret = k.toDouble() / 45.0
        return (ret * 30).toInt()
    }

    //heart rate

    private lateinit var cameraControl: CameraControl
    private var activeRecording: Recording? = null
    private var countdownTimer: CountDownTimer? = null


    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera provider error: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
//    private lateinit var cameraControl: CameraControl

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()
        val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        // Set up video capture use case
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            cameraControl = camera.cameraControl
        } catch (exc: Exception) {
            Log.e("MainActivity", "Use case binding failed", exc)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startVideoRecording() {
        val videoCapture = this.videoCapture ?: return

        // Ensure there is no active recording
//        val curRecording = recording
        if (activeRecording != null) {
            Log.e("MainActivity", "A recording is already in progress.")

            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        // Enable flash
        cameraControl.enableTorch(true)

        // Create a file for saving the recording
        val videoFile = File(getExternalFilesDir(null), "heart_rate_video.mp4")
        val outputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // Start the recording


        activeRecording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // Recording has started
                        binding.textViewStatus.text = "Recording started"
                        startCountdownTimer() // Start the timer here
                    }

                    is VideoRecordEvent.Finalize -> {
                        // Recording has finalized


                        if (!recordEvent.hasError()) {
                            // Print the URI to the log
                            val uri = recordEvent.outputResults.outputUri
//                            val path = getRealPathFromURI(uri) // Function to convert URI to actual file path
                            binding.textViewStatus.text = "Calculating..."
                            lifecycleScope.launch {

                                heartRate = heartRateCalculator(recordEvent.outputResults.outputUri, contentResolver)
                                binding.textViewStatus.text = "Heart Rate: $heartRate"

                            }
                        } else {
                            activeRecording?.close()
                            activeRecording = null
                        }
                        // Disable flash after recording
                        cameraControl.enableTorch(false)
                        activeRecording = null
                    }
                }
            }
    }

    private fun startCountdownTimer() {
        countdownTimer = object : CountDownTimer(45000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update timer display
                val secondsRemaining = millisUntilFinished / 1000
                binding.textViewTimer.text = "Time remaining: $secondsRemaining sec"
            }

            override fun onFinish() {
                // Stop recording after 45 seconds
                binding.textViewTimer.text = "Recording finished!"
                stopVideoRecording()
                stopRespiratoryRateSensing()
            }

        }.start()
    }

    private fun stopVideoRecording() {
        // Ensure there is an active recording
        val recording = activeRecording ?: return
        // Stop the active recording
        recording.stop()
        countdownTimer?.cancel() // Cancel the timer if recording is manually stopped
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun calculateHeartRate(uri: Uri) {
        lifecycleScope.launch {
            val heartRate = heartRateCalculator(uri, contentResolver)
            binding.textViewHeartRate.text = "Heart Rate: $heartRate bpm"
        }
    }

//    private fun onSaveButtonClick() {
//        // Handle save button click
//        Toast.makeText(this, "Recording saved!", Toast.LENGTH_SHORT).show()
//    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    // Helper function for heart rate calculation
    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun heartRateCalculator(uri: Uri, contentResolver: ContentResolver): Int {
        return withContext(Dispatchers.IO) {


            val result: Int
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(uri, proj, null, null, null)
            val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor?.moveToFirst()
            val path = cursor?.getString(columnIndex ?: 0)
            cursor?.close()

            val sdCardPath = path?.replace("/storage/emulated/0", "/sdcard")
//            val path = getRealPathFromURI(uri)

            Log.d("HeartRateCalculator", "EXTRACTED SDCARD Video Path: $sdCardPath")
            Log.d("HeartRateCalculator", "EXTRACTED Video Path: $path")

            val retriever = MediaMetadataRetriever()
            val frameList = ArrayList<Bitmap>()
            try {

                val file = File(path)
                if (!file.exists()) {
                    Log.e("HeartRateCalculator", "File does not exist at path: $path")
                }

                retriever.setDataSource(path)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                val frameDuration = min(duration!!.toInt(), 425)
                var i = 10
                while (i < frameDuration) {
                    val bitmap = retriever.getFrameAtIndex(i)
                    bitmap?.let { frameList.add(it) }
                    i += 15
                }
            } catch (e: Exception) {
                Log.e("HeartRateCalculator", "Error extracting frames: ${e.message}", e)
            } finally {
                retriever.release()
                if (frameList.isEmpty()) {
                    Log.e("MainActivity", "No frames extracted from the video.")
                    return@withContext 0 // Return 0 or any default value if no frames are extracted
                }
                var redBucket: Long
                var pixelCount: Long = 0
                val a = mutableListOf<Long>()
                for (i in frameList) {
                    redBucket = 0
                    for (y in 350 until 450) {
                        for (x in 350 until 450) {
                            val c: Int = i.getPixel(x, y)
                            pixelCount++
                            redBucket += Color.red(c) + Color.blue(c) + Color.green(c)
                        }
                    }
                    a.add(redBucket)
                }
                if (a.size < 5) {
                    Log.e("MainActivity", "Not enough data points for heart rate calculation.")
                    return@withContext 0 // Return 0
                }
                val b = mutableListOf<Long>()
                for (i in 0 until a.lastIndex - 5) {
                    val temp = (a.elementAt(i) + a.elementAt(i + 1) + a.elementAt(i + 2) + a.elementAt(i + 3) + a.elementAt(i + 4)) / 4
                    b.add(temp)
                }
                var x = b.elementAt(0)
                var count = 0
                for (i in 1 until b.lastIndex) {
                    val p = b.elementAt(i)
                    if ((p - x) > 3500) {
                        count += 1
                    }
                    x = b.elementAt(i)
                }
                val rate = ((count.toFloat()) * 60).toInt()
                result = (rate / 4)
            }
            result
        }
    }

}





