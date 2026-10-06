package com.iranjan.hotspotscheduler.ui.calibration

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
fun CalibrationScreen(
    viewModel: CalibrationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onDone: () -> Unit
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isCalibrating by viewModel.isCalibrating.collectAsStateWithLifecycle()
    val dump by viewModel.dump.collectAsStateWithLifecycle()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDone) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = "CALIBRATION",
                        fontSize = 20.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.runFullProbe() }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = status, fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

                        if (!isCalibrating) {
                            Button(
                                onClick = { viewModel.startCalibration() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                            ) {
                                Text(text = "START CALIBRATION", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(onClick = { viewModel.captureCalibration() }, modifier = Modifier.weight(1f)) {
                                    Text(text = "CAPTURE DUMP", fontWeight = FontWeight.Medium)
                                }
                                Button(onClick = { viewModel.confirmCalibration() }, modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)) {
                                    Text(text = "CONFIRM", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                dump.ifNotNull().ifNotEmpty() {
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
                                Text(text = "CAPTURED UI DUMP", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                                IconButton(onClick = { viewModel.clearCalibration() }) {
                                    Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Clear")
                                }
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(300.dp)
                            ) {
                                items(dump!!.lines()) { line ->
                                    Text(
                                        text = line,
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
}