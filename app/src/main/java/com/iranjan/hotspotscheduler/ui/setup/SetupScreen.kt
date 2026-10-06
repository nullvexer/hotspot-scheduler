package com.iranjan.hotspotscheduler.ui.setup

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCalibration: () -> Unit
) {
    val pinConfigured by viewModel.pinConfigured.collectAsStateWithLifecycle()
    val autoUnlockEnabled by viewModel.autoUnlockEnabled.collectAsStateWithLifecycle()
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val pinConfirm by viewModel.pinConfirm.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()

    val showPin by remember { mutableStateOf(false) }
    val showConfirm by remember { mutableStateOf(false) }

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
                        text = "SYSTEM CONSOLE",
                        fontSize = 20.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onNavigateToCalibration) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Tune, contentDescription = "Calibrate")
                    }
                }

                // Capability Probe Button
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.runCapabilityProbe() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.MedicalInformation, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(text = "RUN CAPABILITY PROBE", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // PIN Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "SECURE PIN", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                                Text(text = if (pinConfigured) "CONFIGURED" else "NOT SET", fontSize = 18.sp, color = if (pinConfigured) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                            if (!pinConfigured) {
                                Button(onClick = { /* show pin dialog */ }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)) {
                                    Text(text = "SET PIN", fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Switch(
                                        checked = autoUnlockEnabled,
                                        onCheckedChange = { viewModel.setAutoUnlockEnabled(it) }
                                    )
                                    Text(text = "Auto-unlock", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { viewModel.removePin() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) {
                                        Text(text = "REMOVE PIN", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                    if (!pinConfigured) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { viewModel.onPinChange(it) },
                                label = { Text("PIN (4-16 digits)") },
                                placeholder = { Text("Enter PIN") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showPin) TextFieldDefaults.VisualTransformation.None else androidx.compose.material3.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPin = !showPin }) {
                                        Icon(imageVector = if (showPin) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility, contentDescription = "Toggle visibility")
                                    }
                                }
                            )
                            OutlinedTextField(
                                value = pinConfirm,
                                onValueChange = { viewModel.onPinConfirmChange(it) },
                                label = { Text("Confirm PIN") },
                                placeholder = { Text("Confirm PIN") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showConfirm) TextFieldDefaults.VisualTransformation.None else androidx.compose.material3.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showConfirm = !showConfirm }) {
                                        Icon(imageVector = if (showConfirm) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility, contentDescription = "Toggle visibility")
                                    }
                                }
                            )
                            pinError?.let { err ->
                                Text(text = err, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                            Button(
                                onClick = { viewModel.savePin() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                            ) {
                                Text(text = "SAVE PIN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // System Permissions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "SYSTEM PERMISSIONS", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

                        PermissionRow(
                            label = "Accessibility",
                            description = "Required for UI automation",
                            actionLabel = "OPEN SETTINGS",
                            onAction = { viewModel.openAccessibilitySettings() }
                        )
                        PermissionRow(
                            label = "Battery Optimization",
                            description = "Must be unrestricted for background execution",
                            actionLabel = "OPEN SETTINGS",
                            onAction = { viewModel.openBatteryOptimizationSettings() }
                        )
                        PermissionRow(
                            label = "Exact Alarms",
                            description = "Required for precise schedule timing",
                            actionLabel = "OPEN SETTINGS",
                            onAction = { viewModel.openExactAlarmSettings() }
                        )
                        PermissionRow(
                            label = "Display Over Apps",
                            description = "May be needed for overlay features",
                            actionLabel = "OPEN SETTINGS",
                            onAction = { viewModel.openOverlaySettings() }
                        )
                        PermissionRow(
                            label = "Notifications",
                            description = "Alerts for automation status",
                            actionLabel = "OPEN SETTINGS",
                            onAction = { viewModel.openNotificationSettings() }
                        )
                        PermissionRow(
                            label = "Screen Lock",
                            description = "Must be configured for auto-unlock",
                            actionLabel = "OPEN SETTINGS",
                            onAction = { viewModel.openSecuritySettings() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(label: String, description: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = label, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(text = description, fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAction) { Text(text = actionLabel, fontSize = 12.sp) }
        }
    }
}