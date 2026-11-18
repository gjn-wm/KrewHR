package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.WorkspaceDataBundle
import com.example.ui.components.AttendanceTrendChart
import com.example.ui.components.DepartmentHeadcountDonut
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.utils.HrmsPlatformUtils

@Composable
fun PerformanceLearningScreen(
    bundle: WorkspaceDataBundle,
    onBack: () -> Unit,
    onAddItem: (String, String, String, String, Long) -> Unit,
    onUpdateProgress: (String, Long) -> Unit
) {
    var filterKind by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = bundle.performance.filter { filterKind == "ALL" || it.itemKind == filterKind }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar("Performance & Learning", onBack)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("ALL" to "All", "GOAL" to "OKRs", "FEEDBACK" to "Appraisals", "TRAINING" to "Training")) { (k, label) ->
                    FilterChip(selected = filterKind == k, onClick = { filterKind = k }, label = { Text(label) })
                }
            }
            FilledTonalIconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_performance_button")) {
                Icon(Icons.Filled.Add, contentDescription = "Add goal or feedback")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { item ->
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
                            StatusPill(item.itemKind, TealPrimaryContainer, OnTealPrimaryContainer)
                            Text("${item.progressPercent}%", style = MonoMetricStyle, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("${item.assigneeName} • ${item.subtitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (item.itemKind != "FEEDBACK") {
                            Slider(
                                value = item.progressPercent.toFloat(),
                                onValueChange = {},
                                onValueChangeFinished = {
                                    val next = ((item.progressPercent + 20L) % 120L).coerceIn(0L, 100L)
                                    onUpdateProgress(item.id, next)
                                },
                                valueRange = 0f..100f
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onUpdateProgress(item.id, (item.progressPercent + 20L).coerceAtMost(100L)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+20% Progress")
                                }
                                if (item.progressPercent < 100L) {
                                    FilledTonalButton(
                                        onClick = { onUpdateProgress(item.id, 100L) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Complete")
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(item.rating.toInt().coerceIn(1, 5)) {
                                    Icon(Icons.Filled.Star, contentDescription = "Star", tint = AmberLight, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Appraisal Rating ${item.rating}/5", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var kind by remember { mutableStateOf("GOAL") }
        var title by remember { mutableStateOf("") }
        var assignee by remember { mutableStateOf("Engineering") }
        var subtitle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Goal / Feedback / Training") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("GOAL", "FEEDBACK", "TRAINING").forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) })
                        }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = assignee, onValueChange = { assignee = it }, label = { Text("Owner / Team") }, singleLine = true)
                    OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Notes / Appraisal Summary") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddItem(kind, title, assignee, subtitle, 5L)
                        showAddDialog = false
                    },
                    enabled = title.trim().length >= 2
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AnalyticsDashboardScreen(
    bundle: WorkspaceDataBundle,
    onBack: () -> Unit
) {
    val deptColors = mapOf(
        "Engineering" to TealPrimary,
        "Product" to AmberAccent,
        "Design" to EmeraldSuccess,
        "Sales" to Color(0xFF6366F1),
        "Operations" to SageSecondary,
        "People" to Color(0xFFEC4899)
    )
    val deptSlices = bundle.employees
        .groupBy { it.department }
        .map { (dept, list) -> Triple(dept, list.size, deptColors[dept] ?: TealPrimary) }
        .sortedByDescending { it.second }

    val totalEmployees = bundle.employees.size.coerceAtLeast(1)
    val activeEmployees = bundle.employees.count { it.status == "ACTIVE" }
    val offboardingCount = bundle.employees.count { it.status == "OFFBOARDING" }
    val retentionRate = (((totalEmployees - offboardingCount).toFloat() / totalEmployees) * 100).toInt()
    val monthlyPayrollEstimate = bundle.employees.sumOf { it.baseMonthlySalary }
    val todayCheckedInRate = (bundle.attendance.count { it.status == "CHECKED_IN" }.toFloat() / totalEmployees).coerceIn(0.15f, 1f)

    val attendanceTrend = listOf(
        "Mon" to 0.94f,
        "Tue" to 0.96f,
        "Wed" to 0.92f,
        "Thu" to 0.90f,
        "Fri" to 0.88f,
        "Today" to todayCheckedInRate.coerceAtLeast(0.76f)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar("Reporting & HR Analytics", onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard(
                        title = "Retention Rate",
                        value = "$retentionRate%",
                        subtitle = "$offboardingCount offboarding",
                        icon = Icons.Filled.Verified,
                        accentColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Monthly Payroll",
                        value = "$${(monthlyPayrollEstimate / 1000).toInt()}k",
                        subtitle = "$activeEmployees active staff",
                        icon = Icons.Filled.Payments,
                        accentColor = TealPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Department Headcount (${bundle.employees.size}/50)", style = MaterialTheme.typography.titleMedium)
                        DepartmentHeadcountDonut(slices = deptSlices, totalCount = bundle.employees.size)
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Weekly Attendance Trend", style = MaterialTheme.typography.titleMedium)
                        AttendanceTrendChart(dailyRates = attendanceTrend)
                    }
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Workforce Snapshot", style = MaterialTheme.typography.titleMedium)
                        val hybrid = bundle.employees.count { it.workMode == "HYBRID" }
                        val remote = bundle.employees.count { it.workMode == "REMOTE" }
                        val onsite = bundle.employees.count { it.workMode == "ONSITE" }
                        Text("Hybrid: $hybrid  •  Remote: $remote  •  Onsite: $onsite", style = MonoMetricStyle, color = MaterialTheme.colorScheme.primary)
                        Text("Approved Leave Days: ${bundle.leaves.filter { it.status == "APPROVED" }.sumOf { it.daysCount }}d  •  Pending Expenses: ${HrmsPlatformUtils.formatCurrency(bundle.serviceItems.filter { it.kind == "EXPENSE" && it.status == "PENDING" }.sumOf { it.amount })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityIntegrationsScreen(
    bundle: WorkspaceDataBundle,
    userProfile: UserPrivateProfile?,
    onBack: () -> Unit,
    onSavePrivateProfile: (String, String, String, String, String, String, Boolean) -> Unit,
    onUpdateWorkspaceIntegrations: (HrRole?, String?, Boolean?, Boolean?, String?) -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val currentRole = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN)
    var slackUrl by remember(bundle.workspace.slackWebhookUrl) { mutableStateOf(bundle.workspace.slackWebhookUrl) }
    val mfaEnabled = userProfile?.mfaLockEnabled ?: false

    val mfaVerifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && userProfile != null) {
            onSavePrivateProfile(
                userProfile.fullName, userProfile.phone, userProfile.homeAddress,
                userProfile.emergencyContact, userProfile.bankAccountLast4,
                userProfile.taxFilingStatus, !mfaEnabled
            )
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                val csv = HrmsPlatformUtils.buildAccountingCsv(bundle.payroll, bundle.serviceItems)
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                onShowMessage("Accounting CSV exported")
            } catch (e: Exception) {
                onShowMessage("Export failed: ${e.localizedMessage}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar("Security & Integrations", onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Role-Based Access Control
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Role-Based Access Control (RBAC)", style = MaterialTheme.typography.titleMedium)
                        Text("Switch role permissions preview for your workspace session:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            HrRole.entries.forEach { role ->
                                FilterChip(
                                    selected = currentRole == role,
                                    onClick = { onUpdateWorkspaceIntegrations(role, null, null, null, null) },
                                    label = { Text(role.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            when (currentRole) {
                                HrRole.HR_ADMIN -> "Full access: Payroll runs, 50-seat roster edits, approvals, analytics & security."
                                HrRole.MANAGER -> "Team lead access: 1-tap leave & expense approvals, shift monitoring, channels."
                                HrRole.EMPLOYEE -> "Self-service access: Attendance punch, payslips, leave requests, channels & learning."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Device MFA & Biometric Hardware Lock
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Security & Biometric Hardware", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Device Credential MFA for Payroll", style = MaterialTheme.typography.titleSmall)
                                Text("Require device PIN/Biometric before opening Payroll", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = mfaEnabled,
                                onCheckedChange = {
                                    val intent = HrmsPlatformUtils.createDeviceMfaIntent(context)
                                    if (intent != null) {
                                        mfaVerifyLauncher.launch(intent)
                                    } else if (userProfile != null) {
                                        onSavePrivateProfile(
                                            userProfile.fullName, userProfile.phone, userProfile.homeAddress,
                                            userProfile.emergencyContact, userProfile.bankAccountLast4,
                                            userProfile.taxFilingStatus, !mfaEnabled
                                        )
                                    }
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Gate Hardware Sync", style = MaterialTheme.typography.titleSmall)
                                Text("Pair office badge & mobile check-in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = bundle.workspace.biometricBadgeEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateWorkspaceIntegrations(null, null, enabled, null, null)
                                }
                            )
                        }
                    }
                }
            }

            // 3. Calendar, Chat & Accounting Integrations
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connected Tools & Exports", style = MaterialTheme.typography.titleMedium)

                        FilledTonalButton(
                            onClick = {
                                try {
                                    HrmsPlatformUtils.openCalendarShiftSync(
                                        context,
                                        "${bundle.workspace.companyName} Shift (09:00-18:00)",
                                        "Synced from KrewHR Workforce Schedule"
                                    )
                                } catch (_: Exception) {
                                    onShowMessage("No calendar app available on this device")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sync_calendar_button")
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Calendar Sync")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Shift to Android Calendar")
                        }

                        FilledTonalButton(
                            onClick = { csvExportLauncher.launch("krewhr_accounting_nov2026.csv") },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_accounting_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Accounting CSV Export")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Accounting Ledger (.CSV)")
                        }

                        OutlinedTextField(
                            value = slackUrl,
                            onValueChange = { slackUrl = it },
                            label = { Text("Chat / Slack Webhook URL") },
                            placeholder = { Text("https://hooks.slack.com/services/...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onUpdateWorkspaceIntegrations(null, slackUrl, null, null, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Chat Webhook")
                        }
                    }
                }
            }

            // 4. Sign Out
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of KrewHR")
                }
            }
        }
    }
}
