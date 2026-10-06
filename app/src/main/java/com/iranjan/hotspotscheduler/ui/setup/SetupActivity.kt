package com.iranjan.hotspotscheduler.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HotspotSchedulerTheme {
                SetupScreen(
                    onNavigateBack = { finish() },
                    onNavigateToCalibration = { /* handled by nav */ }
                )
            }
        }
    }
}