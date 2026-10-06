package com.iranjan.hotspotscheduler.ui.diagnostics

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit
) {
    val fullReport by viewModel.fullReport.collectAsStateWithLifecycle()
    val liveTestResult by viewModel.liveTestResult.collectAsStateWithLifecycle()
    val capabilityReport by viewModel.capabilityReport.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    HotspotSchedulerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "AUTOMATION HEALTH",
                        fontSize = 20.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Clear logs")
                    }
                }

                // Capability Panel
                capabilityReport?.let { report ->
                    CapabilityPanel(report = report)
                }

                // Live Test Actions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "LIVE ACTIONS", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.runWakeTest() }, modifier = Modifier.weight(1f), enabled = !isRunning) { Text("WAKE") }
                            Button(onClick = { viewModel.runUnlockTest() }, modifier = Modifier.weight(1f), enabled = !isRunning) { Text("UNLOCK") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.runHotspotTest(true) }, modifier = Modifier.weight(1f), enabled = !isRunning,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)) { Text("HOTSPOT ON") }
                            Button(onClick = { viewModel.runHotspotTest(false) }, modifier = Modifier.weight(1f), enabled = !isRunning,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) { Text("HOTSPOT OFF") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.runDataTest(true) }, modifier = Modifier.weight(1f), enabled = !isRunning,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)) { Text("DATA ON") }
                            Button(onClick = { viewModel.runDataTest(false) }, modifier = Modifier.weight(1f), enabled = !isRunning,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) { Text("DATA OFF") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.runFullTransaction(true, true) }, modifier = Modifier.weight(1f).fillMaxWidth(), enabled = !isRunning,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)) { Text("FULL TEST: BOTH ON", fontWeight = FontWeight.Bold) }
                            Button(onClick = { viewModel.runFullTransaction(false, false) }, modifier = Modifier.weight(1f).fillMaxWidth(), enabled = !isRunning,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) { Text("FULL TEST: BOTH OFF", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // Live Test Result
                liveTestResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = when {
                                result.result is com.iranjan.hotspotscheduler.domain.model.AutomationResult.Success -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                result.result is com.iranjan.hotspotscheduler.domain.model.AutomationResult.PartialSuccess -> androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                                else -> androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "LAST TEST: ${result.testName}", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                            Text(text = "${result.result}  •  ${result.durationMs}ms", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Full Diagnostic Report
                fullReport?.let { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "FULL DIAGNOSTIC REPORT", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))

                            DiagnosticSection("CAPABILITIES", report.capabilityReport.allCapabilities.map { "${it.name}: ${it.status} - ${it.detail}" })
                            DiagnosticSection("ACCESSIBILITY DUMP", listOf(report.accessibilityDump))
                            DiagnosticSection("SCREEN STATE", listOf(report.screenState))
                            DiagnosticSection("KEYGUARD STATE", listOf(report.keyguardState))
                            DiagnosticSection("HOTSPOT STATE", listOf(report.hotspotState))
                            DiagnosticSection("MOBILE DATA STATE", listOf(report.mobileDataState))
                        }
                    }
                }

                // Execution Logs
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "EXECUTION LOGS (${logs.size})", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                            Text(text = "newest first", fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                        ) {
                            items(logs.reversed()) { log ->
                                Text(
                                    text = log,
                                    fontSize = 10.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityPanel(report: com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.Report) {
    val capabilities = report.allCapabilities

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = when (report.overall) {
                com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.OverallStatus.READY -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.OverallStatus.DEGRADED -> androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                else -> androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM STATUS: ${report.overall}",
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = when (report.overall) {
                        com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.OverallStatus.READY -> androidx.compose.material.icons.Icons.Filled.CheckCircle
                        com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.OverallStatus.DEGRADED -> androidx.compose.material.icons.Icons.Filled.Warning
                        else -> androidx.compose.material.icons.Icons.Filled.Error
                    },
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(capabilities) { cap ->
                    CapabilityChip(capability = cap)
                }
            }
        }
    }
}

@Composable
fun CapabilityChip(capability: com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.Capability) {
    val color = when (capability.status) {
        com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.Status.READY -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe.Status.DEGRADED -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
        else -> androidx.compose.material3.MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.width(140.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(100.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Box(
                modifier = Modifier.size(8.dp)
                    .background(color, shape = RoundedCornerShape(4.dp))
            )
            Text(
                text = capability.name,
                fontSize = 10.sp,
                color = color,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DiagnosticSection(title: String, lines: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        lines.forEach { line ->
            Text(
                text = line,
                fontSize = 10.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}