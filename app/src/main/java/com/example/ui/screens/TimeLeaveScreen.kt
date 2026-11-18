package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.WorkspaceDataBundle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.OfflineSyncPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.utils.HrmsPlatformUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLeaveScreen(
    bundle: WorkspaceDataBundle,
    userName: String,
    isBusy: Boolean,
    onCheckIn: (VerificationMode, String, Double, Double, Boolean, String) -> Unit,
    onCheckOut: (AttendanceRecord) -> Unit,
    onSubmitLeave: (LeaveType, String, String, Long, String) -> Unit,
    onDecideLeave: (String, Boolean) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val role = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN)
    var subTab by remember { mutableIntStateOf(0) } // 0 = Attendance & Shifts, 1 = Leave & Approvals
    var selectedMode by remember { mutableStateOf(VerificationMode.ONE_TAP) }
    var lastSelfieUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSelfieUri by remember { mutableStateOf<Uri?>(null) }
    var showLeaveSheet by remember { mutableStateOf(false) }

    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val activePunch = bundle.attendance.firstOrNull {
        it.dateLabel == todayDate && it.status == "CHECKED_IN" &&
            it.employeeName.equals(userName, ignoreCase = true)
    } ?: bundle.attendance.firstOrNull { it.dateLabel == todayDate && it.status == "CHECKED_IN" }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingSelfieUri
        if (success && uri != null) {
            lastSelfieUri = uri
            onCheckIn(VerificationMode.SELFIE, "Selfie Verified Check-In", 0.0, 0.0, true, "09:00 - 18:00")
        } else {
            onShowMessage("Selfie verification cancelled")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = HrmsPlatformUtils.createSelfieTempUri(context)
            pendingSelfieUri = uri
            takePictureLauncher.launch(uri)
        } else {
            onShowMessage("Camera permission is needed for selfie check-in")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val loc = HrmsPlatformUtils.fetchLastCoarseLocation(context)
            val lat = loc?.latitude ?: 37.7749
            val lon = loc?.longitude ?: -122.4194
            val label = if (loc != null) {
                "GPS (${String.format(Locale.US, "%.3f, %.3f", lat, lon)})"
            } else {
                "GPS Verified (Field)"
            }
            onCheckIn(VerificationMode.GPS, label, lat, lon, false, "09:00 - 18:00")
        } else {
            onShowMessage("Location permission denied; use One-Tap or grant location access.")
        }
    }

    fun triggerCheckIn() {
        when (selectedMode) {
            VerificationMode.ONE_TAP -> {
                onCheckIn(VerificationMode.ONE_TAP, "Mobile One-Tap", 0.0, 0.0, false, "09:00 - 18:00")
            }
            VerificationMode.GPS -> {
                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    val loc = HrmsPlatformUtils.fetchLastCoarseLocation(context)
                    val lat = loc?.latitude ?: 37.7749
                    val lon = loc?.longitude ?: -122.4194
                    val label = if (loc != null) "GPS (${String.format(Locale.US, "%.3f, %.3f", lat, lon)})" else "GPS Verified (Field)"
                    onCheckIn(VerificationMode.GPS, label, lat, lon, false, "09:00 - 18:00")
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            VerificationMode.SELFIE -> {
                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasCam) {
                    val uri = HrmsPlatformUtils.createSelfieTempUri(context)
                    pendingSelfieUri = uri
                    takePictureLauncher.launch(uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time & Leave", style = MaterialTheme.typography.headlineMedium)
                OfflineSyncPill(hasPendingSync = bundle.hasPendingSync)
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("tab_attendance")
                ) {
                    Text("Attendance & Shifts")
                }
                SegmentedButton(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("tab_leave")
                ) {
                    val pendingCount = bundle.leaves.count { it.status == "PENDING" }
                    Text(if (pendingCount > 0) "Leave ($pendingCount)" else "Leave")
                }
            }
        }

        if (subTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Verification Mode", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                VerificationMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = selectedMode == mode,
                                        onClick = { selectedMode = mode },
                                        label = { Text(mode.label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (lastSelfieUri != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(
                                        model = lastSelfieUri,
                                        contentDescription = "Captured attendance selfie",
                                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text("Latest selfie verification attached", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                                }
                            }

                            Button(
                                onClick = {
                                    if (activePunch != null) onCheckOut(activePunch) else triggerCheckIn()
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("attendance_punch_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    if (activePunch != null) Icons.Filled.Logout else Icons.Filled.Fingerprint,
                                    contentDescription = "Punch"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (activePunch != null) "Check Out (In since ${activePunch.checkInTime})"
                                    else "Check In with ${selectedMode.label}"
                                )
                            }
                        }
                    }
                }

                // Weekly Shift Schedule Strip
                item {
                    Text("Weekly Shift Schedule", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val days = listOf("Mon" to "09-18 HQ", "Tue" to "09-18 HQ", "Wed" to "09-18 Remote", "Thu" to "09-18 HQ", "Fri" to "09-17 Flex")
                        items(days) { (day, shift) ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(shift, style = MonoCaptionStyle)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Live Attendance Logs (${bundle.attendance.size})", style = MaterialTheme.typography.titleMedium)
                }

                if (bundle.attendance.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Schedule,
                            title = "No attendance logs yet",
                            subtitle = "Tap Check In above to record your shift with offline sync."
                        )
                    }
                } else {
                    items(bundle.attendance, key = { it.id }) { rec ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(rec.employeeName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${rec.dateLabel} • In ${rec.checkInTime}" +
                                            if (rec.checkOutTime.isNotBlank()) " → Out ${rec.checkOutTime} (${rec.hoursWorked}h)" else "",
                                        style = MonoCaptionStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${safeEnum(rec.verificationMode, VerificationMode.ONE_TAP).label} • ${rec.locationLabel.ifBlank { rec.department }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val checkedOut = rec.status == "CHECKED_OUT"
                                StatusPill(
                                    label = if (checkedOut) "DONE" else "ACTIVE",
                                    containerColor = if (checkedOut) SoftSurfaceVariant else EmeraldContainer,
                                    contentColor = if (checkedOut) SlateMuted else EmeraldSuccess
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Leave & Approvals Tab
            val approvedDays = bundle.leaves.filter { it.status == "APPROVED" }.sumOf { it.daysCount }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LeaveBalanceCard("Annual", "${(18 - approvedDays).coerceAtLeast(0)}d", Modifier.weight(1f))
                        LeaveBalanceCard("Sick", "10d", Modifier.weight(1f))
                        LeaveBalanceCard("WFH Flex", "6d", Modifier.weight(1f))
                    }
                }

                item {
                    Button(
                        onClick = { showLeaveSheet = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("request_leave_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.EventAvailable, contentDescription = "Request leave")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Request Time Off / WFH")
                    }
                }

                if (bundle.leaves.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.BeachAccess,
                            title = "No leave requests",
                            subtitle = "Submit an Annual, Sick, or WFH request above."
                        )
                    }
                } else {
                    items(bundle.leaves, key = { it.id }) { lv ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lv.employeeName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${safeEnum(lv.leaveType, LeaveType.ANNUAL).label} • ${lv.startDate} to ${lv.endDate} (${lv.daysCount}d)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val (bg, fg) = when (lv.status) {
                                        "APPROVED" -> EmeraldContainer to EmeraldSuccess
                                        "REJECTED" -> RoseContainer to RoseError
                                        else -> AmberContainer to OnAmberContainer
                                    }
                                    StatusPill(lv.status, bg, fg)
                                }
                                Text(lv.reason, style = MaterialTheme.typography.bodyMedium)
                                if (lv.status == "PENDING" && role != HrRole.EMPLOYEE) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { onDecideLeave(lv.id, true) },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) { Text("Approve") }
                                        OutlinedButton(
                                            onClick = { onDecideLeave(lv.id, false) },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) { Text("Decline") }
                                    }
                                } else if (lv.reviewedBy.isNotBlank()) {
                                    Text("Reviewed by ${lv.reviewedBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLeaveSheet) {
        RequestLeaveSheet(
            onDismiss = { showLeaveSheet = false },
            onSubmit = { type, start, end, days, reason ->
                onSubmitLeave(type, start, end, days, reason)
                showLeaveSheet = false
            }
        )
    }
}

@Composable
private fun LeaveBalanceCard(label: String, remaining: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(remaining, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestLeaveSheet(
    onDismiss: () -> Unit,
    onSubmit: (LeaveType, String, String, Long, String) -> Unit
) {
    var leaveType by remember { mutableStateOf(LeaveType.ANNUAL) }
    var startDate by remember { mutableStateOf("2026-11-24") }
    var endDate by remember { mutableStateOf("2026-11-25") }
    var daysCount by remember { mutableStateOf("2") }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Request Leave / WFH", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LeaveType.entries) { type ->
                    FilterChip(
                        selected = leaveType == type,
                        onClick = { leaveType = type },
                        label = { Text(type.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = "Pick start date")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("End Date") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = daysCount,
                onValueChange = { daysCount = it.filter { ch -> ch.isDigit() } },
                label = { Text("Total Days") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason / Handover Note") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("leave_reason_input")
            )
            Button(
                onClick = {
                    val days = (daysCount.toLongOrNull() ?: 1L).coerceIn(1L, 60L)
                    onSubmit(leaveType, startDate, endDate, days, reason)
                },
                enabled = reason.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_leave_button")
            ) {
                Text("Submit Request")
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val formatted = fmt.format(Date(millis))
                        startDate = formatted
                        endDate = formatted
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
