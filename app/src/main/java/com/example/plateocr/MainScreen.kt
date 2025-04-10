package com.example.plateocr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Import Material 3 components
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.compose.runtime.remember
import androidx.lifecycle.LifecycleOwner

@Composable
fun MainScreen(
    onStartCamera: (PreviewView) -> Unit,
    onCaptureClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onBackupDbClick: () -> Unit,
    onRestoreDbClick: () -> Unit,
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
    onDismissExistingLabel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = remember { context as LifecycleOwner }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview - Takes full background
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { previewView ->
            onStartCamera(previewView)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Buttons Row - Will stay at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(
                    onClick = onCaptureClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text("Capture Plate")
                }
                Button(
                    onClick = onExportCsvClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text("Export CSV")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Second Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(
                    onClick = onBackupDbClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text("Backup DB")
                }
                Button(
                    onClick = onRestoreDbClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text("Restore DB")
                }
            }
        }



        if (detectedText.isNotEmpty()) {
            Text("Detected: $detectedText")
        }

        if (showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = onDismissConfirmation,
                title = { Text("Confirm Plate Number") },
                text = {
                    OutlinedTextField(
                        value = correctedText,
                        onValueChange = onCorrectedTextChange,
                        label = { Text("Plate Number") }
                    )
                },
                confirmButton = {
                    Button(onClick = { onConfirmText(correctedText) }) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    Button(onClick = onDismissConfirmation) {
                        Text("Edit")
                    }
                }
            )
        }

        if (showLabelDialog) {
            AlertDialog(
                onDismissRequest = onDismissLabel,
                title = { Text("Add Label (Optional)") },
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

        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = onDismissSuccess,
                title = { Text("Success") },
                text = { Text("Plate information saved successfully.") },
                confirmButton = {
                    Button(onClick = onDismissSuccess) {
                        Text("OK")
                    }
                }
            )
        }

        if (showExistingLabelDialog) {
            AlertDialog(
                onDismissRequest = onDismissExistingLabel,
                title = { Text("Plate Already Exists") },
                text = { Text("The plate number '$correctedText' already exists with the label: '$existingPlateLabel'.") },
                confirmButton = {
                    Button(onClick = onDismissExistingLabel) {
                        Text("OK")
                    }
                }
            )
        }
    }
}