package com.example.plateocr

import android.Manifest
import android.content.pm.PackageManager

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext

import com.example.plateocr.database.AppDatabase
import com.example.plateocr.database.PlateDao
import com.example.plateocr.database.Plate
import com.google.mlkit.vision.text.Text

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.content.Intent

import android.app.Activity
import android.provider.OpenableColumns
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var database: AppDatabase
    private lateinit var plateDao: PlateDao

    private val EXPORT_CSV_REQUEST_CODE = 1001  // For CSV export
    private val REQUEST_CODE_PERMISSIONS = 10   // You already have this one

    // State for dialogs
    private var showConfirmationDialog by mutableStateOf(false)
    private var showLabelDialog by mutableStateOf(false)
    private var showSuccessDialog by mutableStateOf(false)
    private var showExistingLabelDialog by mutableStateOf(false)
    private var detectedText by mutableStateOf("")
    private var correctedText by mutableStateOf("")
    private var labelText by mutableStateOf("")
    private var existingPlateLabel by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission if not granted
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        database = AppDatabase.getDatabase(this)
        plateDao = database.plateDao()

        setContent {
            MainScreen(
                onCaptureClick = { if (allPermissionsGranted()) takePicture() else Toast.makeText(this, "Camera permission not granted.", Toast.LENGTH_SHORT).show() },
                showConfirmationDialog = showConfirmationDialog,
                showLabelDialog = showLabelDialog,
                showSuccessDialog = showSuccessDialog,
                showExistingLabelDialog = showExistingLabelDialog,
                detectedText = detectedText,
                correctedText = correctedText,
                onCorrectedTextChange = { correctedText = it },
                labelText = labelText,
                onLabelTextChange = { labelText = it },
                existingPlateLabel = existingPlateLabel,
                onConfirmText = { confirmedText ->
                    correctedText = confirmedText
                    showConfirmationDialog = false
                    checkIfPlateExists(confirmedText)
                },
                onConfirmLabel = { label ->
                    labelText = label
                    savePlateToDatabase()
                    showLabelDialog = false
                    showSuccessDialog = true
                },
                onDismissConfirmation = {
                    showConfirmationDialog = false
                },
                onDismissLabel = {
                    showLabelDialog = false
                },
                onDismissSuccess = {
                    showSuccessDialog = false
                },
                onDismissExistingLabel = {
                    showExistingLabelDialog = false
                },
                onExportCsvClick = { exportPlatesToCsv() }
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Start the camera
    internal fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            imageCapture = ImageCapture.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("Camera", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


// Modified takePicture function
private fun takePicture() {
    val imageCapture = imageCapture ?: return

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+) approach using MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "plate_${System.currentTimeMillis()}")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val contentResolver = applicationContext.contentResolver
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IOException("Failed to create new MediaStore record.")

            // Create output options using the file descriptor approach
            val outputStream = contentResolver.openOutputStream(uri)
                ?: throw IOException("Failed to get output stream")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputStream).build()

            imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        // Update IS_PENDING flag
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(uri, contentValues, null, null)

                        // Process the image
                        val file = uriToFile(uri)
                        processImage(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Camera", "Error capturing image: ${exception.message}")
                        Toast.makeText(
                            this@MainActivity,
                            "Error capturing image",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Clean up the failed entry
                        contentResolver.delete(uri, null, null)
                    }
                }
            )
        } else {
            // Pre-Android 10 approach using direct file access
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM + "/Camera"
            )
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            val file = File(picturesDir, "plate_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        // Notify gallery
                        sendBroadcast(
                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                                data = Uri.fromFile(file)
                            }
                        )
                        processImage(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Camera", "Error capturing image: ${exception.message}")
                        Toast.makeText(
                            this@MainActivity,
                            "Error capturing image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    } catch (e: Exception) {
        Log.e("Camera", "Error setting up capture: ${e.message}")
        Toast.makeText(
            this@MainActivity,
            "Error setting up capture: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
    private fun uriToFile(uri: Uri): File {
        return when {
            // For Android Q and above, use the URI directly with ContentResolver
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile("plate_temp", ".jpg", cacheDir)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }
            // For older versions, get the file path from the URI
            else -> {
                val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
                val cursor = contentResolver.query(uri, filePathColumn, null, null, null)
                cursor?.use {
                    it.moveToFirst()
                    val columnIndex = it.getColumnIndex(filePathColumn[0])
                    val filePath = it.getString(columnIndex)
                    File(filePath)
                } ?: throw IOException("Could not get file path from URI")
            }
        }
    }


    // Process image and do OCR, then check database
    private fun processImage(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val image = InputImage.fromBitmap(bitmap, 0)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val biggestText = findBiggestText(visionText)
                if (biggestText.isNotEmpty()) {
                    detectedText = biggestText
                    correctedText = biggestText
                    showConfirmationDialog = true
                } else {
                    Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Text recognition failed: ${e.message}")
                Toast.makeText(this, "Text recognition failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun findBiggestText(visionText: Text): String {
        var biggestText = ""
        var maxArea = 0

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val boundingBox = element.boundingBox
                    if (boundingBox != null) {
                        val area = boundingBox.width() * boundingBox.height()
                        if (area > maxArea) {
                            maxArea = area
                            biggestText = element.text
                        }
                    }
                }
            }
        }
        return biggestText.trim() // Trim whitespace
    }

    private fun checkIfPlateExists(plateNumber: String) {
        lifecycleScope.launch {
            val existingPlate = plateDao.getPlateByNumber(plateNumber)
            withContext(Dispatchers.Main) {
                if (existingPlate != null) {
                    existingPlateLabel = existingPlate.label
                    showExistingLabelDialog = true
                } else {
                    showLabelDialog = true
                }
            }
        }
    }

    private fun savePlateToDatabase() {
        lifecycleScope.launch {
            try {
                val plate = Plate(
                    number = correctedText,
                    label = labelText
                )
                plateDao.insert(plate)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Saved successfully with label '$labelText'", Toast.LENGTH_SHORT).show()
                    showSuccessDialog = false // Hide success dialog after a short delay or user interaction
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportPlatesToCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "plate_records.csv")
        }
        startActivityForResult(intent, EXPORT_CSV_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)  // Important!

        if (requestCode == EXPORT_CSV_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val plates = plateDao.getAllPlates()
                        contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write("Number,Label\n".toByteArray())
                            plates.forEach { plate ->
                                stream.write("\"${plate.number}\",\"${plate.label}\"\n".toByteArray())
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Export successful!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).apply {
                // Add other permissions if needed
            }.toTypedArray()
    }
}

@Composable
fun MainScreen(
    onCaptureClick: () -> Unit,
    showConfirmationDialog: Boolean,
    showLabelDialog: Boolean,
    showSuccessDialog: Boolean,
    showExistingLabelDialog: Boolean,
    detectedText: String,
    correctedText: String,
    onCorrectedTextChange: (String) -> Unit,
    labelText: String,
    onLabelTextChange: (String) -> Unit,
    existingPlateLabel: String,
    onConfirmText: (String) -> Unit,
    onConfirmLabel: (String) -> Unit,
    onDismissConfirmation: () -> Unit,
    onDismissLabel: () -> Unit,
    onDismissSuccess: () -> Unit,
    onDismissExistingLabel: () -> Unit,
    onExportCsvClick: () -> Unit
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Text("Capture a License Plate", color = Color.Black)

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth().height(300.dp)
        )

        Button(onClick = onCaptureClick) {
            Text("Capture Photo")
        }

        Button(onClick = onExportCsvClick) {
            Text("Export Plates to CSV")
        }
    }

    // Confirmation Dialog
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text("Is this the correct plate number?") },
            text = {
                Column {
                    Text("Detected: $detectedText")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctedText,
                        onValueChange = onCorrectedTextChange,
                        label = { Text("Correct if needed") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onConfirmText(correctedText) }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                Button(onClick = onDismissConfirmation) {
                    Text("Retake")
                }
            }
        )
    }

    // Label Dialog
    if (showLabelDialog) {
        AlertDialog(
            onDismissRequest = onDismissLabel,
            title = { Text("Add a label for this plate") },
            text = {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = onLabelTextChange,
                    label = { Text("Label") }
                )
            },
            confirmButton = {
                Button(onClick = { onConfirmLabel(labelText) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = onDismissLabel) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = onDismissSuccess,
            title = { Text("Success!") },
            text = { Text("Plate number '${correctedText}' saved with label: $labelText") },
            confirmButton = {
                Button(onClick = onDismissSuccess) {
                    Text("OK")
                }
            }
        )
    }

    // Existing Label Dialog
    if (showExistingLabelDialog) {
        AlertDialog(
            onDismissRequest = onDismissExistingLabel,
            title = { Text("Plate already exists!") },
            text = { Text("Plate number '${correctedText}' already exists with label: $existingPlateLabel") },
            confirmButton = {
                Button(onClick = onDismissExistingLabel) {
                    Text("OK")
                }
            }
        )
    }

    // Pass the PreviewView to startCamera
    LaunchedEffect(Unit) {
        (context as MainActivity).startCamera(previewView)
    }
}