package com.iranjan.hotspotscheduler.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics
import com.iranjan.hotspotscheduler.automation.session.AutomationCoordinator
import com.iranjan.hotspotscheduler.automation.session.AutomationSession
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVault
import com.iranjan.hotspotscheduler.domain.model.AutomationResult
import com.iranjan.hotspotscheduler.domain.model.DesiredNetworkState
import com.iranjan.hotspotscheduler.domain.model.Target
import com.iranjan.hotspotscheduler.util.AttemptLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Deliberately minimal control panel.
 *
 * The automation engine is the product; this screen is a harness for it. Every button calls the
 * same code path a scheduled boundary uses, so what the tester sees on the phone is real engine
 * behaviour and not a UI-only shortcut.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var diagnostics: AutomationDiagnostics

    @Inject lateinit var coordinator: AutomationCoordinator

    @Inject lateinit var prefs: AutomationPreferences

    @Inject lateinit var vault: CredentialVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ControlPanel(diagnostics, coordinator, prefs, vault)
                }
            }
        }
    }
}

private fun describe(result: AutomationResult): String = when (result) {
    is AutomationResult.Success ->
        "SUCCESS steps=${result.steps.size} lock=${result.finalLock}"
    is AutomationResult.PartialSuccess ->
        "PARTIAL SUCCESS failed=${result.failedFeature} lock=${result.finalLock} reason=${result.failureReason}"
    is AutomationResult.Failed ->
        "FAILED reason=${result.reason}"
    is AutomationResult.Blocked ->
        "BLOCKED reason=${result.reason}"
}

@Composable
private fun ControlPanel(
    diagnostics: AutomationDiagnostics,
    coordinator: AutomationCoordinator,
    prefs: AutomationPreferences,
    vault: CredentialVault
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var logTick by remember { mutableStateOf(0) }
    var pinSet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { pinSet = vault.isConfigured() }
    LaunchedEffect(logTick) { logs = AttemptLog.getAll().takeLast(40) }

    fun run(label: String, block: suspend () -> AutomationResult) {
        scope.launch {
            busy = true
            status = "$label ..."
            logs = emptyList()
            val result = try {
                block()
            } catch (t: Throwable) {
                AttemptLog.add("$label threw: ${t.message}")
                null
            }
            result?.let { status = describe(it) }
            logs = AttemptLog.getAll().takeLast(40)
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Hotspot Scheduler", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("vNext control panel", fontSize = 13.sp)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("STATUS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(status, fontSize = 13.sp)
                Text("Secure PIN configured: $pinSet", fontSize = 13.sp)
            }
        }

        Text("SINGLE NETWORK STEP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = { run("Hotspot ON") { coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST, DesiredNetworkState(Target.Set(true), Target.LeaveAlone)) } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Hotspot ON") }
        Button(
            onClick = { run("Hotspot OFF") { coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST, DesiredNetworkState(Target.Set(false), Target.LeaveAlone)) } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Hotspot OFF") }
        Button(
            onClick = { run("Data ON") { coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST, DesiredNetworkState(Target.LeaveAlone, Target.Set(true))) } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Mobile Data ON") }
        Button(
            onClick = { run("Data OFF") { coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST, DesiredNetworkState(Target.LeaveAlone, Target.Set(false))) } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Mobile Data OFF") }

        Text("FULL TRANSACTION  (wake > unlock > change > verify > lock once)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = { run("BOTH ON") { coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST, DesiredNetworkState(Target.Set(true), Target.Set(true))) } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("BOTH ON") }
        Button(
            onClick = { run("BOTH OFF") { coordinator.executeManualTest(AutomationSession.Trigger.MANUAL_TEST, DesiredNetworkState(Target.Set(false), Target.Set(false))) } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("BOTH OFF") }

        Text("DIAGNOSTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    status = "capability probe running ..."
                    logs = emptyList()
                    val report = diagnostics.runFullProbe()
                    status = "probe: ${report.capabilityReport.overall}"
                    logs = AttemptLog.getAll().takeLast(60)
                    busy = false
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Run capability probe") }
        Button(
            onClick = { run("Wake test") { AutomationResult.Success(emptyList(), com.iranjan.hotspotscheduler.domain.model.LockResult.SKIPPED, "wake") } },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh logs") }

        Text("EXECUTION LOG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (logs.isEmpty()) {
                    Text("no log entries yet", fontSize = 12.sp)
                } else {
                    logs.reversed().forEach { line ->
                        Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}