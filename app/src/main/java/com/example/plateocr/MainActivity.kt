package com.example.plateocr

import java.io.IOException
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.plateocr.database.AppDatabase
import com.example.plateocr.database.Plate
import com.example.plateocr.database.PlateDao
import com.example.plateocr.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.CameraSelector
import android.graphics.BitmapFactory
import com.google.mlkit.vision.text.TextRecognition


import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap


import android.provider.MediaStore


class MainActivity : ComponentActivity() {
    // State variables
    private var showConfirmationDialog by mutableStateOf(false)
    private var showLabelDialog by mutableStateOf(false)
    private var showSuccessDialog by mutableStateOf(false)
    private var showExistingLabelDialog by mutableStateOf(false)
    private var detectedText by mutableStateOf("")
    private var correctedText by mutableStateOf("")
    private var labelText by mutableStateOf("")
    private var existingPlateLabel by mutableStateOf("")

    // Camera and permissions
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private lateinit var fileProviderAuthority: String
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requiredPermissions: Array<String>

    // Database
    private lateinit var database: AppDatabase
    private lateinit var plateDao: PlateDao

    override fun onResume() {
        super.onResume()

        if (this::permissionLauncher.isInitialized && checkPermissions()) {
            initializeContent()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize Context-dependent properties
        fileProviderAuthority = "$packageName.fileprovider"
        requiredPermissions = buildRequiredPermissions()
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                initializeContent()
            } else {
                showPermissionRationaleDialog()
            }
        }

        database = AppDatabase.getDatabase(this)
        plateDao = database.plateDao()

        if (checkPermissions()) {
            initializeContent()
        } else {
            requestPermissions()
        }
    }

    private fun buildRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun checkPermissions(): Boolean {
        val result = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        Log.d("Permissions", "checkPermissions result: $result")
        return result
    }

    private fun shouldShowRationale(): Boolean {
        return requiredPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Needed")
            .setMessage("This app needs camera and storage permissions to function correctly. Please grant these permissions in the app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Intent to open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent) // Consider using a launcher for result if needed
                // Maybe finish the activity or disable functionality until permissions are granted
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Features may be limited or unavailable without permissions", Toast.LENGTH_LONG).show()
                // Decide what to do here. Maybe initialize with limited functionality
                // or show a message indicating limitations.
                // Calling initializeContent() might still lead to crashes later
                // if permissions are strictly required.
                // finish() // Option: Close the app if permissions are essential
            }
            .setCancelable(false)
            .show()
    }

    // Also, modify the requestPermissions logic slightly
    private fun requestPermissions() {
        // Check if rationale should be shown OR if permissions were denied permanently
        // Note: A more robust check involves tracking if a request was already made.
        // This simplified version handles the common case.
        if (shouldShowRationale()) {
            showPermissionRationaleDialog()
        } else {
            // If rationale shouldn't be shown (either first time, or denied with "don't ask again")
            // just launch the request. If denied with "don't ask again", it won't pop up,
            // and the result callback will indicate denial.
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun initializeContent() {
        setContent {
            MaterialTheme {
                MainScreen(
                    onStartCamera = { initializeCamera(it) },
                    onCaptureClick = { takePicture() },
                    onExportCsvClick = { exportPlatesToCsv() },
                    onBackupDbClick = { backupDatabase() },
                    onRestoreDbClick = { restoreDatabase() },
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
                    onDismissConfirmation = { showConfirmationDialog = false },
                    onDismissLabel = { showLabelDialog = false },
                    onDismissSuccess = { showSuccessDialog = false },
                    onDismissExistingLabel = { showExistingLabelDialog = false }
                )
            }
        }
    }

    private fun initializeCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

            } catch(exc: Exception) {
                Log.e("Camera", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun getFileFromUri(uri: Uri): File? {
        return try {
            // Check if the URI points to an external file
            if (uri.scheme == "content") {
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                        val filePath = it.getString(columnIndex)
                        File(filePath)
                    } else {
                        null
                    }
                }
            } else {
                // If it's a file URI, convert directly to File
                File(uri.path)
            }
        } catch (e: Exception) {
            Log.e("Camera", "Error getting file from URI", e)
            null
        }
    }

    private fun takePicture() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PlateDetector")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: run {
                        Log.e("Camera", "Saved URI is null")
                        return
                    }

                    Toast.makeText(baseContext, "Photo capture succeeded", Toast.LENGTH_SHORT).show()
                    processImage(savedUri)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(baseContext, "Photo capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun getImageContentUri(file: File): Uri? {
        return try {
            val contentResolver = applicationContext.contentResolver
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)

            contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (columnIndex >= 0) {
                        val id = cursor.getLong(columnIndex)
                        Uri.withAppendedPath(contentUri, id.toString())
                    } else {
                        Log.e("Camera", "Column _ID not found in MediaStore query")
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("Camera", "Error getting content URI", e)
            null
        }
    }

    // Process image and do OCR, then check database
    private fun processImage(uri: Uri) {
        try {
            // Use contentResolver to directly get the image
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }

            if (bitmap != null) {
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
            } else {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                Log.e("ProcessImage", "Bitmap is null")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
            Log.e("ProcessImage", "Exception while processing image from URI", e)
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
            val existingPlate = withContext(Dispatchers.IO) {
                plateDao.getPlateByNumber(plateNumber)
            }
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
                    label = labelText,
                    timestamp = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    plateDao.insert(plate)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Saved successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Add a new ActivityResultLauncher for creating documents
    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv") // MIME type
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val plates = plateDao.getAllPlates()
                    if (plates.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "No plates to export", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Write to the URI obtained from the user
                    contentResolver.openOutputStream(targetUri)?.use { fos ->
                        fos.write("Number,Label,Timestamp\n".toByteArray())
                        plates.forEach { plate ->
                            val line = "\"${plate.number}\",\"${plate.label}\",\"${plate.timestamp}\"\n"
                            fos.write(line.toByteArray())
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "CSV exported successfully", Toast.LENGTH_SHORT).show()
                        }
                    } ?: throw IOException("Could not open output stream")

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("ExportCSV", "Error exporting CSV", e)
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "Export cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPlatesToCsv() {
        val suggestedName = "plates_${System.currentTimeMillis()}.csv"
        // Launch the SAF file creation intent
        createCsvLauncher.launch(suggestedName)
        // The rest of the logic moves inside the launcher's callback
    }

    // Add a new ActivityResultLauncher for creating documents
    private val createDbBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-sqlite3") // Or "application/octet-stream"
    ) { uri: Uri? ->
        uri?.let { backupUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val dbFile = getDatabasePath("plate_database")
                    if (!dbFile.exists()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Database file not found", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    contentResolver.openOutputStream(backupUri)?.use { outputStream ->
                        dbFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: throw IOException("Could not open output stream for backup")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Backup created successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("BackupDB", "Error creating backup", e)
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "Backup cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupDatabase() {
        val suggestedName = "plate_db_backup_${System.currentTimeMillis()}.db"
        // Launch the SAF file creation intent
        createDbBackupLauncher.launch(suggestedName)
        // The rest of the logic moves inside the launcher's callback
    }

    private val restoreDbLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        database.close()
                        val dbFile = getDatabasePath("plate_database")
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(dbFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        database = AppDatabase.getDatabase(this@MainActivity)
                        plateDao = database.plateDao()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Database restored successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun restoreDatabase() {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"  // Allow all file types so user can choose any file
        }.also { restoreDbLauncher.launch(it) }
    }


    override fun onDestroy() {
        super.onDestroy()
        database.close()
    }
}