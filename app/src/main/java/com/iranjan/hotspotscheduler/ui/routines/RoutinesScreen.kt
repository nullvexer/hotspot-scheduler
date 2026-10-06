package com.iranjan.hotspotscheduler.ui.routines

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
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNewRoutine: () -> Unit,
    onEditRoutine: (com.iranjan.hotspotscheduler.domain.model.Routine) -> Unit
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()

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
                    Text(
                        text = "ROUTINES",
                        fontSize = 28.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Button(onClick = onNewRoutine) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Add, contentDescription = "Add")
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text(text = "NEW ROUTINE", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }

                // Time Rail Overview
                TimeRailOverview(routines = routines)

                // Routine List
                if (routines.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "No routines yet",
                                    fontSize = 18.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Create your first automation schedule",
                                    fontSize = 14.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        routines.forEach { routine ->
                            RoutineCard(routine = routine, onClick = { onEditRoutine(routine) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeRailOverview(routines: List<com.iranjan.hotspotscheduler.domain.model.Routine>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "24-HOUR OVERVIEW",
                    fontSize = 11.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (0..23 step 4).forEach { hour ->
                        Text(
                            text = "%02d:00".format(hour),
                            fontSize = 9.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                routines.forEach { routine ->
                    val startMin = routine.startTime.hour * 60 + routine.startTime.minute
                    val endMin = routine.endTime.hour * 60 + routine.endTime.minute
                    val isOvernight = routine.endTime.isBefore(routine.startTime)

                    val leftPercent = startMin / 1440f
                    val widthPercent = if (isOvernight) {
                        (1440 - startMin + endMin) / 1440f
                    } else {
                        (endMin - startMin) / 1440f
                    }

                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(6.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    androidx.compose.material3.Box(
                                        modifier = Modifier
                                            .width(0.dp)
                                            .weight(leftPercent)
                                    )
                                    androidx.compose.material3.Box(
                                        modifier = Modifier
                                            .width(0.dp)
                                            .weight(
                                                if (isOvernight) (1440 - routine.startTime.hour * 60 - routine.startTime.minute + routine.endTime.hour * 60 + routine.endTime.minute) / 1440f
                                                else (routine.endTime.hour * 60 + routine.endTime.minute - routine.startTime.hour * 60 - routine.startTime.minute) / 1440f
                                            )
                                            .fillMaxHeight()
                                            .background(
                                                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = routine.name,
                                            fontSize = 11.sp,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
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
}

@Composable
fun RoutineCard(
    routine: com.iranjan.hotspotscheduler.domain.model.Routine,
    onClick: () -> Unit
) {
    val daysText = routine.daysOfWeek.toSortedList().joinToString(", ") { it.name.substring(0, 3) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (routine.enabled) androidx.compose.material3.MaterialTheme.colorScheme.surface
            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = routine.name,
                            fontSize = 16.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        androidx.compose.material3.Switch(
                            checked = routine.enabled,
                            onCheckedChange = { /* handled by click */ },
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "${routine.startTime} – ${routine.endTime}",
                            fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = daysText,
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (routine.hotspotTarget is com.iranjan.hotspotscheduler.domain.model.Target.Set) {
                                val on = (routine.hotspotTarget as com.iranjan.hotspotscheduler.domain.model.Target.Set).on
                                Badge(
                                    text = if (on) "HOTSPOT ON" else "HOTSPOT OFF",
                                    color = if (on) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                    else androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                                )
                            }
                            if (routine.mobileDataTarget is com.iranjan.hotspotscheduler.domain.model.Target.Set) {
                                val on = (routine.mobileDataTarget as com.iranjan.hotspotscheduler.domain.model.Target.Set).on
                                Badge(
                                    text = if (on) "DATA ON" else "DATA OFF",
                                    color = if (on) androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                                    else androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    androidx.compose.material3.Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(color, shape = RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}