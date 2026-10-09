package com.fusionone.app.feature.photoanalysis

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fusionone.app.core.util.PayloadRisk
import com.fusionone.app.ui.theme.DangerRed
import com.fusionone.app.ui.theme.PrimaryTeal
import com.fusionone.app.ui.theme.SafeGreen
import com.fusionone.app.ui.theme.WarningAmber
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoAnalysisScreen(
    onNavigateHome: () -> Unit = {},
    viewModel: PhotoAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedImageUriString by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUriString = uri.toString()
            viewModel.analyze(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Analysis", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Checks for hidden payloads smuggled inside the image file and extracts the GPS location where the photo was taken.",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            onClick = { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
        ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Photo", fontWeight = FontWeight.Bold)
        }

        selectedImageUriString?.let { uriStr ->
            AsyncImage(
                model = uriStr,
                contentDescription = "Selected photo",
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }

        when (val state = uiState) {
            is PhotoUiState.Idle -> Text("No photo selected yet.")
            is PhotoUiState.Loading -> CircularProgressIndicator(color = PrimaryTeal)
            is PhotoUiState.Error -> Text(state.message, color = DangerRed)
            is PhotoUiState.Success -> PhotoResultContent(state.result)
        }
    }
    }
}

@Composable
private fun PhotoResultContent(result: PhotoAnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        val (color, label) = when (result.payload.risk) {
            PayloadRisk.CLEAN -> SafeGreen to "No hidden payload detected"
            PayloadRisk.SUSPICIOUS -> WarningAmber to "Suspicious data found"
            PayloadRisk.DANGEROUS -> DangerRed to "Payload indicators found"
        }

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (result.payload.risk == PayloadRisk.CLEAN) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = color
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("File size: ${result.payload.fileSizeBytes} bytes")
                if (result.payload.trailingBytesAfterImageEnd > 0) {
                    Text("Bytes appended after image end marker: ${result.payload.trailingBytesAfterImageEnd}")
                    result.payload.trailingDataEntropy?.let { Text("Entropy of appended data: ${"%.2f".format(it)} bits/byte") }
                }
                if (result.payload.findings.isEmpty()) {
                    Text("No embedded executables, scripts, or appended data found.")
                } else {
                    Spacer(Modifier.height(8.dp))
                    result.payload.findings.forEach { finding ->
                        Text("• ${finding.description}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Location Taken", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (result.location.hasGps) {
                    Text("Coordinates: ${result.location.latitude}, ${result.location.longitude}")
                    result.location.placeName?.let { Text("Place: $it") }
                    result.location.altitudeMeters?.let { Text("Altitude: ${"%.1f".format(it)} m") }
                } else {
                    Text("No GPS data embedded in this photo (location may have been stripped, or the device/app didn't record it).")
                }
                result.location.dateTimeOriginal?.let { Text("Taken: $it") }
                if (result.location.cameraMake != null || result.location.cameraModel != null) {
                    Text("Camera: ${listOfNotNull(result.location.cameraMake, result.location.cameraModel).joinToString(" ")}")
                }
            }
        }
    }
}
