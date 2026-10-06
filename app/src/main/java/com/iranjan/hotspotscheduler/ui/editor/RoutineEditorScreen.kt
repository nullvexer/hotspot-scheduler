package com.iranjan.hotspotscheduler.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

@Composable
fun RoutineEditorScreen(
    viewModel: RoutineEditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onDone: () -> Unit
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val startHour by viewModel.startHour.collectAsStateWithLifecycle()
    val startMinute by viewModel.startMinute.collectAsStateWithLifecycle()
    val endHour by viewModel.endHour.collectAsStateWithLifecycle()
    val endMinute by viewModel.endMinute.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val hotspotTarget by viewModel.hotspotTarget.collectAsStateWithLifecycle()
    val dataTarget by viewModel.dataTarget.collectAsStateWithLifecycle()
    val hotspotPassword by viewModel.hotspotPassword.collectAsStateWithLifecycle()
    val priority by viewModel.priority.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val durationMinutes by viewModel.durationMinutes.collectAsStateWithLifecycle()
    val effectiveStatePreview by viewModel.effectiveStatePreview.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val validationErrors by viewModel.validationErrors.collectAsStateWithLifecycle()

    val showPassword by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onDone) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = if (isEditing) "EDIT ROUTINE" else "NEW ROUTINE",
                        fontSize = 20.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.save(); onDone() }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Check, contentDescription = "Save")
                    }
                }

                error?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = err,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Effective State Preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "EFFECTIVE STATE PREVIEW",
                            fontSize = 11.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        Text(
                            text = effectiveStatePreview,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                        Text(
                            text = "Duration: ${durationMinutes / 60}h ${durationMinutes % 60}m",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Form Fields
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { viewModel.onNameChange(it) },
                        label = { Text("Routine Name") },
                        placeholder = { Text("Morning Hotspot") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Time Range
                    TimeRangeRow(
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        onStartChange = { h, m -> viewModel.onStartTimeChange(h, m) },
                        onEndChange = { h, m -> viewModel.onEndTimeChange(h, m) }
                    )

                    // Days
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "DAYS", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 1.sp)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                java.time.DayOfWeek.values().forEach { day ->
                                    val selected = day in days
                                    androidx.compose.material3.FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.onDayToggle(day) },
                                        label = { Text(text = day.name.substring(0, 3).uppercase(), fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Network Targets
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "NETWORK TARGETS", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 1.sp)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

                            TargetRow(
                                label = "MOBILE DATA",
                                icon = androidx.compose.material.icons.Icons.Filled.SignalCellular4g,
                                target = dataTarget,
                                onChange = { viewModel.onDataTargetChange(it) }
                            )
                            TargetRow(
                                label = "WIFI HOTSPOT",
                                icon = androidx.compose.material.icons.Icons.Filled.WifiTethering,
                                target = hotspotTarget,
                                onChange = { viewModel.onHotspotTargetChange(it) }
                            )
                        }
                    }

                    // Hotspot Password (only shown if hotspot is ON)
                    (hotspotTarget as? com.iranjan.hotspotscheduler.domain.model.Target.Set)?.let { target ->
                        if (target.on) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "HOTSPOT PASSWORD", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 1.sp)
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
                                    OutlinedTextField(
                                        value = hotspotPassword,
                                        onValueChange = { viewModel.onPasswordChange(it) },
                                        label = { Text("Password (8-63 ASCII, empty = keep current)") },
                                        placeholder = { Text("••••••••") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = if (showPassword) androidx.compose.material3.TextFieldDefaults.VisualTransformation.None else androidx.compose.material3.PasswordVisualTransformation()
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        androidx.compose.material3.TextButton(onClick = { showPassword = !showPassword }) {
                                            Text(text = if (showPassword) "HIDE" else "SHOW")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Priority
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "PRIORITY", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 1.sp)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (0..5).forEach { p ->
                                    val selected = priority == p
                                    androidx.compose.material3.FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.onPriorityChange(p) },
                                        label = { Text(text = "P$p", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Save Button
                    Button(
                        onClick = { viewModel.save(); onDone() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = if (isEditing) "SAVE CHANGES" else "CREATE ROUTINE", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeRangeRow(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartChange: (Int, Int) -> Unit,
    onEndChange: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "TIME WINDOW", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, letterSpacing = 1.sp)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimePicker(
                    label = "START",
                    hour = startHour,
                    minute = startMinute,
                    onChange = onStartChange
                )
                TimePicker(
                    label = "END",
                    hour = endHour,
                    minute = endMinute,
                    onChange = onEndChange
                )
            }
        }
    }
}

@Composable
fun TimePicker(label: String, hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Hour
            androidx.compose.material3.Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(48.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onChange((hour + 23) % 24, minute) }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp, contentDescription = "Up")
                    }
                    Text(text = "%02d".format(hour), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IconButton(onClick = { onChange((hour + 1) % 24, minute) }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, contentDescription = "Down")
                    }
                }
            }
            Text(text = ":", fontSize = 20.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            // Minute
            androidx.compose.material3.Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(48.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onChange(hour, (minute + 55) % 60) }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp, contentDescription = "Up")
                    }
                    Text(text = "%02d".format(minute), fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IconButton(onClick = { onChange(hour, (minute + 5) % 60) }) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, contentDescription = "Down")
                    }
                }
            }
        }
    }
}

@Composable
fun TargetRow(label: String, icon: androidx.compose.graphics.vector.ImageVector, target: com.iranjan.hotspotscheduler.domain.model.Target, onChange: (com.iranjan.hotspotscheduler.domain.model.Target) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Box(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp).align(Alignment.Center))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(
                text = when (target) {
                    is com.iranjan.hotspotscheduler.domain.model.Target.Set -> if (target.on) "ON" else "OFF"
                    else "UNCHANGED"
                },
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.SegmentedButton(
            selected = when (target) {
                is com.iranjan.hotspotscheduler.domain.model.Target.Set -> if (target.on) 0 else 1
                else 2
            },
            onClick = { index ->
                onChange(
                    when (index) {
                        0 -> com.iranjan.hotspotscheduler.domain.model.Target.Set(true)
                        1 -> com.iranjan.hotspotscheduler.domain.model.Target.Set(false)
                        else -> com.iranjan.hotspotscheduler.domain.model.Target.LeaveAlone
                    }
                )
            }
        ) {
            androidx.compose.material3.SegmentedButtonItem(
                modifier = Modifier.width(72.dp),
                label = { Text(text = "ON", fontSize = 11.sp) }
            )
            androidx.compose.material3.SegmentedButtonItem(
                modifier = Modifier.width(72.dp),
                label = { Text(text = "OFF", fontSize = 11.sp) }
            )
            androidx.compose.material3.SegmentedButtonItem(
                modifier = Modifier.width(96.dp),
                label = { Text(text = "UNCHANGED", fontSize = 11.sp) }
            )
        }
    }
}