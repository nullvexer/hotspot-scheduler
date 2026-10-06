package com.iranjan.hotspotscheduler.ui.components

import androidx.compose.foundation.layout.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = value,
                        fontSize = 24.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun SignalPath(
    fromLabel: String,
    fromState: Boolean,
    toLabel: String,
    toState: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // From node
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (fromState) androidx.compose.material3.MaterialTheme.colorScheme.primary
                            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (fromState) "●" else "○",
                        fontSize = 32.sp,
                        color = if (fromState) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.TextAlign.Center,
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = fromLabel,
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.TextAlign.Center
                )
            }

            // Arrow
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(32.dp)
            )

            // To node
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (toState) androidx.compose.material3.MaterialTheme.colorScheme.primary
                            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (toState) "●" else "○",
                        fontSize = 32.sp,
                        color = if (toState) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.TextAlign.Center,
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = toLabel,
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TimeRail(
    routines: List<com.iranjan.hotspotscheduler.domain.model.Routine>,
    now: java.time.Instant,
    zone: java.time.ZoneId,
    modifier: Modifier = Modifier
) {
    val currentMinutes = now.atZone(zone).toLocalTime().hour * 60 + now.atZone(zone).toLocalTime().minute
    val railWidth = 300.dp // Will be constrained by parent

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "24-HOUR TIMELINE",
            fontSize = 11.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = 1.2.sp
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))

        // Time markers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            (0..23 step 3).forEach { hour ->
                Text(
                    text = "%02d:00".format(hour),
                    fontSize = 9.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // Current time indicator
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))

        // Routine bars
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                androidx.compose.material3.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.material3.Box(
                            modifier = Modifier
                                .width(0.dp)
                                .weight(leftPercent)
                        )
                        androidx.compose.material3.Box(
                            modifier = Modifier
                                .width(0.dp)
                                .weight(widthPercent)
                                .fillMaxHeight()
                                .background(
                                    androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
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

@Composable
fun NextBoundaryCard(
    nextBoundary: com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator.Boundary?,
    routineName: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NEXT AUTOMATION",
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                    nextBoundary?.let { boundary ->
                        val time = java.time.Instant.ofEpochMilli(boundary.atMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                        val action = if (boundary.isStart) "ACTIVATION" else "DEACTIVATION"
                        Text(
                            text = "${com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator.formatTime(time)}  •  $action",
                            fontSize = 22.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        routineName?.let { name ->
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 2.dp))
                            Text(
                                text = name,
                                fontSize = 13.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    } ?: Text(
                        text = "No scheduled boundaries",
                        fontSize = 22.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}