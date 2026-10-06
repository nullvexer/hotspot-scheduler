package com.iranjan.hotspotscheduler.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iranjan.hotspotscheduler.ui.AppNav
import com.iranjan.hotspotscheduler.ui.theme.HotspotSchedulerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HotspotSchedulerTheme {
                AppNav()
            }
        }
    }
}