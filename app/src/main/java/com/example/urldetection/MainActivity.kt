package com.example.urldetection

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import android.media.MediaPlayer

import com.amazonaws.services.rekognition.model.TextDetection
import com.amazonaws.services.rekognition.model.DetectTextResult
import com.amazonaws.services.rekognition.model.DetectTextRequest
import com.amazonaws.services.rekognition.AmazonRekognition

// ここわからん
/*
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder
import com.amazonaws.services.rekognition.model.AmazonRekognitionException
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.AmazonClientException
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
 */

import com.amazonaws.services.rekognition.model.Image
import com.amazonaws.util.IOUtils

// 画像をBitmapからBase64エンコードするために使う
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.util.Base64
import com.amplifyframework.AmplifyException
import com.amplifyframework.core.Amplify
import com.amplifyframework.predictions.models.TextFormatType
import com.amplifyframework.predictions.result.IdentifyTextResult
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.predictions.PredictionsCategory
import com.amplifyframework.predictions.aws.AWSPredictionsPlugin
import com.amplifyframework.predictions.models.LanguageType


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private val myApp: MyAmplifyApp? = null
    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        val myApp = this.application as MyAmplifyApp
/*
        // amplify初期化
        try {
//            Amplify.addPlugin(AWSDataStorePlugin())
//            Amplify.addPlugin(AWSApiPlugin())
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSPredictionsPlugin())
            Amplify.configure(applicationContext)
            Log.i(TAG, "Initialized Amplify")
        } catch (error: AmplifyException) {
            Log.e(TAG, "Could not initialize Amplify", error)
        }
  */
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        // ここで画像ファイルを定義する。
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    /*
                    // 画像のuriを文字列に変換し、先頭からfile://を削除する
                    val imageUri: String = savedUri.toString().removePrefix("file://")
                    Log.d(TAG, imageUri)
                    */

                    // 画像をbitmapへ
                    // Fileスキームからパスを取得
                    val imageUriString: String? = photoFile.path
                    // FileスキームのURIからパスを取得
                    // val imageUriString: String? = savedUri.getPath()
                    Log.d(TAG, imageUriString.toString())
                    val imageBit: Bitmap = BitmapFactory.decodeFile(imageUriString)
                    if (myApp != null) {
                        myApp.detectText(imageBit)
                    } else {
                        Log.d(TAG, "動いてない")
                    }

                }

            })
        /*
        // 画像のbit化
        val byteArrayOutputStream = ByteArrayOutputStream()
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
        val imageString: String = Base64.encodeToString(imageBytes, Base64.DEFAULT)
         */

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
/*
    // amplify利用
    fun detectText(image: Bitmap) {
        Amplify.Predictions.identify(
            TextFormatType.PLAIN, image,
            { result ->
                val identifyResult = result as IdentifyTextResult
                Log.i("MyAmplifyApp", "${identifyResult?.fullText}")
            },
            { Log.e("MyAmplifyApp", "Identify text failed", it) }
        )
    }

 */
/*
    object DetectLabelsLocalFile {
        @Throws(java.lang.Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val photo = "input.jpg"
            var imageBytes: ByteBuffer?
            FileInputStream(File(photo)).use { inputStream ->
                imageBytes =
                    ByteBuffer.wrap(IOUtils.toByteArray(inputStream))
            }
            val credential: AWSCredentials = ProfileCredentialsProvider("default").getCredentials()
            // val rekognitionClient: AmazonRekognition = AmazonRekognitionClientBuilder.standard()
            val rekognitionClient: AmazonRekognition = AmazonRekognitionClient(credential);
            val request = DetectLabelsRequest()
                .withImage(
                    Image()
                        .withBytes(imageBytes)
                )
                .withMaxLabels(10)
                .withMinConfidence(77f)
            try {
                val result = rekognitionClient.detectLabels(request)
                val labels = result.labels
                println("Detected labels for $photo")
                for (label in labels) {
                    println(label.name + ": " + label.confidence.toString())
                }
            } catch (e: AmazonRekognitionException) {
                e.printStackTrace()
            }
        }
    }

 */
}
