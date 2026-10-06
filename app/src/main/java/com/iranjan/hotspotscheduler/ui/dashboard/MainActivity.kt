package com.iranjan.hotspotscheduler.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DashboardScreen() }
    }
}

@Composable
fun DashboardScreen() {
    HotspotSchedulerTheme {
        Surface {
            Text(text = "Hotspot Scheduler vNext - Dashboard Placeholder", style = androidx.compose.material3.Typography().headlineMedium)
        }
    }
}