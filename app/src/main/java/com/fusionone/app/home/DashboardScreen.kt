package com.fusionone.app.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fusionone.app.core.database.ScanHistoryEntity
import com.fusionone.app.core.network.MatchDto
import com.fusionone.app.ui.theme.AccentGold
import com.fusionone.app.ui.theme.CardDarkGray
import com.fusionone.app.ui.theme.DangerRed
import com.fusionone.app.ui.theme.PrimaryTeal
import com.fusionone.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    onOpenScanner: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recentScans by viewModel.recentScans.collectAsState()
    val securityScore by viewModel.securityScore.collectAsState()
    val liveMatches by viewModel.liveMatches.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = AccentGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("FusionOne", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Default.Notifications, contentDescription = "Recent activity")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Person, contentDescription = "Settings")
            }
        }

        Column {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Security overview — live now", color = PrimaryTeal, style = MaterialTheme.typography.bodyMedium)
        }

        // Link/Image Scanner card
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardDarkGray)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AccentGold)
                    Spacer(Modifier.width(8.dp))
                    Text("Link/Image Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Scan links or photos for phishing, malware, and hidden payloads",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onOpenScanner,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Scan Now", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Live Football Matches card
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardDarkGray)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = AccentGold)
                    Spacer(Modifier.width(8.dp))
                    Text("Live Football Matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                when (val state = liveMatches) {
                    is LiveMatchesState.Loading -> CircularProgressIndicator(color = PrimaryTeal, modifier = Modifier.size(20.dp))
                    is LiveMatchesState.Error -> Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    is LiveMatchesState.Success -> {
                        if (state.matches.isEmpty()) {
                            Text("No live matches right now across the top 5 leagues.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.matches.forEach { match -> LiveMatchRow(match, onOpenLive) }
                        }
                    }
                }
            }
        }

        // Security Score card
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardDarkGray)) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentGold)
                    Spacer(Modifier.width(8.dp))
                    Text("Security Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (securityScore == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("No scans yet — run your first scan to see your score", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    SecurityScoreGauge(score = securityScore!!)
                    Text(
                        if (securityScore!! >= 70) "Your recent scans look secure." else "Some recent scans flagged risks — check History for details.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Recent Scans card
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardDarkGray)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Scans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenHistory) { Text("See all") }
                }
                if (recentScans.isEmpty()) {
                    Text("No scans yet.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    recentScans.take(3).forEach { entry -> RecentScanRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LiveMatchRow(match: MatchDto, onOpenLive: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .background(PrimaryTeal.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("LIVE", color = PrimaryTeal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${match.homeTeam.shortName ?: match.homeTeam.name} vs ${match.awayTeam.shortName ?: match.awayTeam.name}", maxLines = 1)
        }
        Text(
            "${match.score.fullTime.home ?: 0}-${match.score.fullTime.away ?: 0}",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        // Honest labeling: this opens match details/stats in the Live tab, not a video stream —
        // there's no free, legal way to pull an arbitrary live broadcast for these fixtures.
        IconButton(onClick = onOpenLive) {
            Icon(Icons.Default.OpenInNew, contentDescription = "View match details", tint = AccentGold)
        }
    }
}

@Composable
private fun SecurityScoreGauge(score: Int) {
    val color = when {
        score >= 70 -> PrimaryTeal
        score >= 40 -> AccentGold
        else -> DangerRed
    }
    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val sweep = 360f * (score / 100f)
            drawArc(
                color = CardDarkGray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("/100", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(
                if (score >= 70) "Secure" else if (score >= 40) "Caution" else "At risk",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RecentScanRow(entry: ScanHistoryEntity) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (entry.verdict) {
            "SAFE" -> Icons.Default.CheckCircle to PrimaryTeal
            "WARNING" -> Icons.Default.Warning to AccentGold
            else -> Icons.Default.Warning to DangerRed
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.displayDomain, maxLines = 1)
            Text(relativeTime(entry.scannedAtEpochMillis), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            if (entry.verdict == "SAFE") "Safe" else if (entry.verdict == "WARNING") "Warning" else "Threat Blocked",
            color = tint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun relativeTime(epochMillis: Long): String {
    val diffMs = System.currentTimeMillis() - epochMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    }
}
