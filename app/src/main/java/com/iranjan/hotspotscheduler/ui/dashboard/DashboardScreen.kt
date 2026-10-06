package com.iranjan.hotspotscheduler.ui.dashboard

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.background
import androidx.compose.foundation.layout.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.components.DashboardComponents
import com.iranjan.hotspotscheduler.ui.components.NextBoundaryCard
import com.iranjan.hotspotscheduler.ui.components.SignalPath
import com.iranjan.hotspotscheduler.ui.components.StatusCard
import com.iranjan.hotspotscheduler.ui.components.TimeRail
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToRoutines: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val state by viewModel.dashboardState.collectAsStateWithLifecycle()

    HotspotSchedulerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
        ) {
            androidx.compose.foundation.layout.Column(
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
                    Column {
                        Text(
                            text = "Hotspot Scheduler",
                            fontSize = 28.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "vNext  •  Abstract Automation Control Plane",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onNavigateToSetup) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Settings,
                                contentDescription = "Setup",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onNavigateToDiagnostics) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.MedicalInformation,
                                contentDescription = "Diagnostics",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onNavigateToRoutines) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.AccessTime,
                                contentDescription = "Routines",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // System Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "HOTSPOT",
                        value = when (state.hotspotState) { true -> "ON"; false -> "OFF"; null -> "?" },
                        icon = androidx.compose.material.icons.Icons.Filled.WifiTethering,
                        color = if (state.hotspotState == true) androidx.compose.material3.MaterialTheme.colorScheme.primary
                        else androidx.compose.material3.MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "MOBILE DATA",
                        value = when (state.dataState) { true -> "ON"; false -> "OFF"; null -> "?" },
                        icon = androidx.compose.material.icons.Icons.Filled.SignalCellular4g,
                        color = if (state.dataState == true) androidx.compose.material3.MaterialTheme.colorScheme.primary
                        else androidx.compose.material3.MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Signal Path Visualization
                SignalPath(
                    fromLabel = "MOBILE DATA",
                    fromState = state.dataState == true,
                    toLabel = "HOTSPOT",
                    toState = state.hotspotState == true
                )

                // Next Automation
                NextBoundaryCard(
                    nextBoundary = state.nextBoundary,
                    routineName = state.nextRoutineName
                )

                // Time Rail
                TimeRail(
                    routines = state.routines,
                    now = java.time.Instant.now(),
                    zone = java.time.ZoneId.systemDefault()
                )

                // Quick Actions
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.runHotspotTest(true) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(text = "HOTSPOT ON", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    Button(
                        onClick = { viewModel.runHotspotTest(false) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Text(text = "HOTSPOT OFF", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.runDataTest(true) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(text = "DATA ON", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    Button(
                        onClick = { viewModel.runDataTest(false) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Text(text = "DATA OFF", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }

                // Full test buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.runFullTest(true, true) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        enabled = !state.isRunning,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = "BOTH ON", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.runFullTest(false, false) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        enabled = !state.isRunning,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(text = "BOTH OFF", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }

                // System Status Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SYSTEM STATUS",
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                StatusRow("Automation", if (state.masterEnabled) "ENABLED" else "DISABLED", state.masterEnabled)
                                StatusRow("Scheduler", if (state.paused) "PAUSED" else "ACTIVE", !state.paused)
                                StatusRow("Cap", if (state.capHit) "HIT" else "OK", !state.capHit)
                            }
                            Column {
                                StatusRow("Suppressed", if (state.suppressed) "YES" else "NO", !state.suppressed)
                                StatusRow("Running", if (state.isRunning) "YES" else "NO", !state.isRunning)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            color = if (ok) androidx.compose.material3.MaterialTheme.colorScheme.primary
            else androidx.compose.material3.MaterialTheme.colorScheme.error,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}