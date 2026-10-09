package com.fusionone.app.feature.urlscanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.fusionone.app.feature.urlscanner.model.UrlScanResult
import com.fusionone.app.feature.urlscanner.model.Verdict
import com.fusionone.app.ui.theme.DangerRed
import com.fusionone.app.ui.theme.PrimaryTeal
import com.fusionone.app.ui.theme.SafeGreen
import com.fusionone.app.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlScannerScreen(
    sharedUrl: String? = null,
    onNavigateHome: () -> Unit = {},
    viewModel: UrlScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val urlInput by viewModel.urlInput.collectAsState()

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.onUrlInputChanged(sharedUrl)
            viewModel.scan(sharedUrl)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("URL Security Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = viewModel::onUrlInputChanged,
                    label = { Text("Paste a URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = viewModel::scanCurrentInput,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan URL", fontWeight = FontWeight.Bold)
                }
            }
        }

        when (val state = uiState) {
            is ScanUiState.Idle -> EmptyHint()
            is ScanUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryTeal)
            }
            is ScanUiState.Error -> Text(state.message, color = DangerRed)
            is ScanUiState.Success -> ScanResultCard(state.result)
        }
    }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        "Paste a URL above, or share a link into FusionOne from your browser or messaging app.",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ScanResultCard(result: UrlScanResult) {
    val (verdictColor, verdictLabel) = when (result.verdict) {
        Verdict.SAFE -> SafeGreen to "SAFE"
        Verdict.WARNING -> WarningAmber to "WARNING"
        Verdict.DANGER -> DangerRed to "DANGER"
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.15f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (result.verdict) {
                                Verdict.SAFE -> Icons.Default.CheckCircle
                                Verdict.WARNING -> Icons.Default.Warning
                                Verdict.DANGER -> Icons.Default.Dangerous
                            },
                            contentDescription = null,
                            tint = verdictColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(verdictLabel, style = MaterialTheme.typography.titleLarge, color = verdictColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${result.reputationScore}/100", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(result.domain, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        item { InfoSection("Threat Explanation", result.threatExplanations) }

        item {
            InfoSection(
                "Connection & Certificate",
                listOf(
                    "SSL/TLS: ${if (result.sslValid) "Valid" else "Invalid or missing"}" +
                        (result.sslIssuer?.let { " — issued by $it" } ?: ""),
                    result.sslDaysUntilExpiry?.let { "Certificate expires in $it day(s)" } ?: "Certificate expiry unknown",
                    "IP address: ${result.ipAddress ?: "Could not resolve"}"
                )
            )
        }

        item {
            InfoSection(
                "Domain Info",
                listOf(
                    "Domain age: ${result.domainAgeDays?.let { "$it days" } ?: "Unknown"}",
                    "Registrar: ${result.whoisRegistrar ?: "Unknown"}",
                    if (result.isTyposquat) "⚠ Resembles brand: ${result.resemblesBrand}" else "No brand impersonation detected"
                )
            )
        }

        item { InfoSection("Redirect Chain (${result.redirectChain.size} hop${if (result.redirectChain.size != 1) "s" else ""})", result.redirectChain) }
    }
}

@Composable
private fun InfoSection(title: String, lines: List<String>) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            lines.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
