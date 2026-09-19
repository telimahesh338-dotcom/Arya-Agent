package com.arya.ui.console

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arya.agent.AgentBroker
import com.arya.agent.AgentEngine
import com.arya.agent.AgentRunStatus
import com.arya.android.CapabilityManager
import com.arya.android.DeviceCapabilities
import com.arya.security.RiskLevel
import com.arya.ui.theme.*

@Composable
fun AgentConsoleScreen(
    engine: AgentEngine,
    capabilityManager: CapabilityManager,
    onLaunchMediaProjection: () -> Unit
) {
    val context = LocalContext.current
    val broker = engine.broker

    val runStatus by broker.status.collectAsState()
    val telemetry by broker.telemetry.collectAsState()
    val capabilities by capabilityManager.capabilities.collectAsState()
    val confirmationRequest by broker.confirmationRequest.collectAsState()

    var goalText by remember { mutableStateOf("") }
    var steeringText by remember { mutableStateOf("") }
    val logMessages = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        broker.logStream.collect { msg ->
            logMessages.add(0, msg)
            if (logMessages.size > 200) logMessages.removeLast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ARYA",
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatusBadge(runStatus)
                    }
                },
                actions = {
                    if (runStatus == AgentRunStatus.RUNNING || runStatus == AgentRunStatus.COUNTDOWN) {
                        IconButton(onClick = { engine.stop() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Emergency Stop", tint = ErrorRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
                .padding(16.dp)
        ) {
            // 1. Preflight Capability Readiness
            if (!capabilities.isReadyForAutonomousOperation) {
                ReadinessBanner(capabilities) {
                    capabilityManager.refreshCapabilities()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. Goal Input & Action Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it },
                        label = { Text("What should ARYA do?") },
                        placeholder = { Text("e.g. Open Settings and check Wi-Fi") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = runStatus == AgentRunStatus.IDLE || runStatus == AgentRunStatus.COMPLETED || runStatus == AgentRunStatus.FAILED,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkCard,
                            focusedLabelColor = CyanPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (goalText.isNotBlank()) {
                                    engine.startTask(goalText, delaySeconds = 0)
                                }
                            },
                            enabled = goalText.isNotBlank() && (runStatus == AgentRunStatus.IDLE || runStatus == AgentRunStatus.COMPLETED || runStatus == AgentRunStatus.FAILED),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Execute Now", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (goalText.isNotBlank()) {
                                    engine.startTask(goalText, delaySeconds = 5)
                                }
                            },
                            enabled = goalText.isNotBlank() && (runStatus == AgentRunStatus.IDLE || runStatus == AgentRunStatus.COMPLETED || runStatus == AgentRunStatus.FAILED),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start (5s Delay)", color = CyanPrimary)
                        }
                    }

                    // Steering input during live run
                    if (runStatus == AgentRunStatus.RUNNING || runStatus == AgentRunStatus.PAUSED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = steeringText,
                                onValueChange = { steeringText = it },
                                placeholder = { Text("Inject real-time guidance...") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                if (steeringText.isNotBlank()) {
                                    broker.injectSteering(steeringText)
                                    steeringText = ""
                                }
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Steer", tint = CyanPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Telemetry Overview
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active App", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(telemetry.activePackage.ifBlank { "None" }, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Column {
                        Text("Progress", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("Step ${telemetry.stepCount} / ${telemetry.maxSteps}", fontWeight = FontWeight.SemiBold, color = CyanPrimary)
                    }
                    Column {
                        Text("State", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(telemetry.statusMessage, fontWeight = FontWeight.Normal, color = TextPrimary, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Live Execution Audit Log
            Text("EXECUTION TRACE", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                items(logMessages) { msg ->
                    Text(
                        text = "> $msg",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (msg.contains("STOP") || msg.contains("Error") || msg.contains("Failed")) ErrorRed
                        else if (msg.contains("approved") || msg.contains("completed")) SuccessGreen
                        else TextSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    // 5. Human Risk Confirmation Modal
    confirmationRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { /* Require explicit action */ },
            title = {
                Text(
                    text = "CONFIRM HIGH-RISK ACTION",
                    color = if (req.risk.level == RiskLevel.CRITICAL) ErrorRed else WarningAmber,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(req.risk.reason, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Target Action: ${req.action}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { broker.resolveConfirmation(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Approve", color = DarkBackground)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { broker.resolveConfirmation(false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Text("Deny Action")
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun StatusBadge(status: AgentRunStatus) {
    val (bgColor, textColor) = when (status) {
        AgentRunStatus.IDLE -> DarkCard to TextSecondary
        AgentRunStatus.COUNTDOWN -> WarningAmber to DarkBackground
        AgentRunStatus.RUNNING -> SuccessGreen to DarkBackground
        AgentRunStatus.PAUSED -> WarningAmber to DarkBackground
        AgentRunStatus.AWAITING_CONFIRMATION -> WarningAmber to DarkBackground
        AgentRunStatus.AWAITING_USER_INPUT -> CyanPrimary to DarkBackground
        AgentRunStatus.COMPLETED -> CyanPrimary to DarkBackground
        AgentRunStatus.FAILED, AgentRunStatus.STOPPED -> ErrorRed to TextPrimary
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status.name,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ReadinessBanner(capabilities: DeviceCapabilities, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "System Readiness Checklist",
                fontWeight = FontWeight.Bold,
                color = WarningAmber,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (!capabilities.hasAccessibility) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accessibility Service", color = ErrorRed, fontSize = 13.sp)
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) {
                        Text("Enable", color = CyanPrimary)
                    }
                }
            }

            if (!capabilities.hasOverlayPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Draw Over Other Apps", color = ErrorRed, fontSize = 13.sp)
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }) {
                        Text("Grant", color = CyanPrimary)
                    }
                }
            }
        }
    }
}
