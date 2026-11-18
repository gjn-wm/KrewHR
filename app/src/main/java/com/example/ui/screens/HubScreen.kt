package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.WorkspaceDataBundle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.utils.HrmsPlatformUtils

enum class HubSection {
    MENU,
    PAYROLL,
    SELF_SERVICE,
    PERFORMANCE,
    ANALYTICS,
    SECURITY_INTEGRATIONS
}

@Composable
fun HubScreen(
    bundle: WorkspaceDataBundle,
    userProfile: UserPrivateProfile?,
    mfaUnlocked: Boolean,
    isBusy: Boolean,
    onUnlockMfa: () -> Unit,
    onRunPayroll: (String) -> Unit,
    onMarkPayrollPaid: (String) -> Unit,
    onSubmitServiceItem: (String, String, String, String, Double, String) -> Unit,
    onUpdateServiceStatus: (String, String) -> Unit,
    onSavePrivateProfile: (String, String, String, String, String, String, Boolean) -> Unit,
    onAddPerformanceItem: (String, String, String, String, Long) -> Unit,
    onUpdatePerformanceProgress: (String, Long) -> Unit,
    onUpdateWorkspaceIntegrations: (HrRole?, String?, Boolean?, Boolean?, String?) -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var activeSection by remember { mutableStateOf(HubSection.MENU) }
    val context = LocalContext.current
    val mfaRequired = userProfile?.mfaLockEnabled == true && !mfaUnlocked

    val mfaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onUnlockMfa()
            activeSection = HubSection.PAYROLL
        } else {
            onShowMessage("MFA verification cancelled")
        }
    }

    if (activeSection != HubSection.MENU) {
        BackHandler { activeSection = HubSection.MENU }
    }

    when (activeSection) {
        HubSection.MENU -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("People Operations Hub", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Payroll, self-service portal, performance, analytics & integrations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    HubMenuCard(
                        icon = Icons.Filled.Payments,
                        title = "Payroll & Benefits",
                        subtitle = "Automated salary runs, PDF payslips, tax & benefits",
                        badge = "${bundle.payroll.size} slips",
                        accent = TealPrimary,
                        testTag = "hub_payroll_card",
                        onClick = {
                            if (mfaRequired) {
                                val intent = HrmsPlatformUtils.createDeviceMfaIntent(context)
                                if (intent != null) mfaLauncher.launch(intent)
                                else {
                                    onUnlockMfa()
                                    activeSection = HubSection.PAYROLL
                                }
                            } else {
                                activeSection = HubSection.PAYROLL
                            }
                        }
                    )
                }

                item {
                    HubMenuCard(
                        icon = Icons.Filled.ReceiptLong,
                        title = "Self-Service Portal",
                        subtitle = "Expense claims with receipt picker, HR tickets & private PII",
                        badge = "${bundle.serviceItems.count { it.status == "PENDING" }} open",
                        accent = AmberAccent,
                        testTag = "hub_self_service_card",
                        onClick = { activeSection = HubSection.SELF_SERVICE }
                    )
                }

                item {
                    HubMenuCard(
                        icon = Icons.Filled.TrendingUp,
                        title = "Performance & Learning",
                        subtitle = "OKR goal tracking, peer appraisals & compliance training",
                        badge = "${bundle.performance.size} active",
                        accent = EmeraldSuccess,
                        testTag = "hub_performance_card",
                        onClick = { activeSection = HubSection.PERFORMANCE }
                    )
                }

                item {
                    HubMenuCard(
                        icon = Icons.Filled.BarChart,
                        title = "Reporting & HR Analytics",
                        subtitle = "Attendance trends, 50-seat headcount, retention & payroll",
                        badge = "Live",
                        accent = SageSecondary,
                        testTag = "hub_analytics_card",
                        onClick = { activeSection = HubSection.ANALYTICS }
                    )
                }

                item {
                    HubMenuCard(
                        icon = Icons.Filled.Security,
                        title = "Security, Roles & Integrations",
                        subtitle = "RBAC permissions, MFA lock, Calendar sync, CSV accounting",
                        badge = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN).label,
                        accent = SlateInk,
                        testTag = "hub_security_card",
                        onClick = { activeSection = HubSection.SECURITY_INTEGRATIONS }
                    )
                }
            }
        }

        HubSection.PAYROLL -> {
            PayrollSectionScreen(
                bundle = bundle,
                isBusy = isBusy,
                onBack = { activeSection = HubSection.MENU },
                onRunPayroll = onRunPayroll,
                onMarkPaid = onMarkPayrollPaid,
                onDownloadPayslip = { payslip ->
                    HrmsPlatformUtils.generateAndSharePayslipPdf(context, payslip, bundle.workspace.companyName)
                        .onFailure { onShowMessage("Could not generate PDF: ${it.localizedMessage}") }
                }
            )
        }

        HubSection.SELF_SERVICE -> {
            SelfServiceSectionScreen(
                bundle = bundle,
                userProfile = userProfile,
                isBusy = isBusy,
                onBack = { activeSection = HubSection.MENU },
                onSubmitServiceItem = onSubmitServiceItem,
                onUpdateServiceStatus = onUpdateServiceStatus,
                onSavePrivateProfile = onSavePrivateProfile
            )
        }

        HubSection.PERFORMANCE -> {
            PerformanceLearningScreen(
                bundle = bundle,
                onBack = { activeSection = HubSection.MENU },
                onAddItem = onAddPerformanceItem,
                onUpdateProgress = onUpdatePerformanceProgress
            )
        }

        HubSection.ANALYTICS -> {
            AnalyticsDashboardScreen(
                bundle = bundle,
                onBack = { activeSection = HubSection.MENU }
            )
        }

        HubSection.SECURITY_INTEGRATIONS -> {
            SecurityIntegrationsScreen(
                bundle = bundle,
                userProfile = userProfile,
                onBack = { activeSection = HubSection.MENU },
                onSavePrivateProfile = onSavePrivateProfile,
                onUpdateWorkspaceIntegrations = onUpdateWorkspaceIntegrations,
                onSignOut = onSignOut,
                onShowMessage = onShowMessage
            )
        }
    }
}

@Composable
private fun HubMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.12f)
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.padding(12.dp).size(26.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(badge, SoftSurfaceVariant, SlateMuted)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("subscreen_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PayrollSectionScreen(
    bundle: WorkspaceDataBundle,
    isBusy: Boolean,
    onBack: () -> Unit,
    onRunPayroll: (String) -> Unit,
    onMarkPaid: (String) -> Unit,
    onDownloadPayslip: (PayrollRecord) -> Unit
) {
    val role = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN)
    val totalNet = bundle.payroll.sumOf { it.netPay }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar("Payroll & Benefits", onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = TealPrimary,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Nov 2026 Automated Payroll", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "Calculated from ${bundle.employees.size} employee salaries, ${bundle.attendance.size} attendance logs, 21% statutory tax & $280 benefits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        if (bundle.payroll.isNotEmpty()) {
                            Text(
                                "Total Net Run: ${HrmsPlatformUtils.formatCurrency(totalNet)}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = AmberLight
                            )
                        }
                        if (role == HrRole.HR_ADMIN) {
                            Button(
                                onClick = { onRunPayroll("Nov 2026") },
                                enabled = !isBusy && bundle.employees.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SlateInk),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_payroll_button")
                            ) {
                                Icon(Icons.Filled.Calculate, contentDescription = "Calculate Payroll")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Run Payroll Calculation (${bundle.employees.size} Staff)")
                            }
                        }
                    }
                }
            }

            // Benefits & Statutory Cards
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.HealthAndSafety, contentDescription = "Health Plan", tint = EmeraldSuccess)
                            Text("Health & Dental", style = MaterialTheme.typography.titleSmall)
                            Text("Platinum PPO • 85% employer covered", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Savings, contentDescription = "Retirement", tint = AmberAccent)
                            Text("401(k) / PF Match", style = MaterialTheme.typography.titleSmall)
                            Text("4% instant company match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Text("Employee Payslips (${bundle.payroll.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (bundle.payroll.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Receipt,
                        title = "No payslips generated yet",
                        subtitle = "Tap Run Payroll Calculation above to compute salaries & tax forms."
                    )
                }
            } else {
                items(bundle.payroll, key = { it.id }) { slip ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${slip.employeeName} (${slip.employeeCode})", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${slip.department} • ${slip.daysWorked} days worked • Tax -${HrmsPlatformUtils.formatCurrency(slip.taxDeduction)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    HrmsPlatformUtils.formatCurrency(slip.netPay),
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { onDownloadPayslip(slip) },
                                    modifier = Modifier.weight(1f).height(42.dp).testTag("download_payslip_${slip.employeeCode}")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF Payslip", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("PDF Payslip")
                                }
                                if (slip.status == "SCHEDULED" && role == HrRole.HR_ADMIN) {
                                    Button(
                                        onClick = { onMarkPaid(slip.id) },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) {
                                        Text("Mark Paid")
                                    }
                                } else {
                                    StatusPill(slip.status, EmeraldContainer, EmeraldSuccess, modifier = Modifier.align(Alignment.CenterVertically))
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
private fun SelfServiceSectionScreen(
    bundle: WorkspaceDataBundle,
    userProfile: UserPrivateProfile?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSubmitServiceItem: (String, String, String, String, Double, String) -> Unit,
    onUpdateServiceStatus: (String, String) -> Unit,
    onSavePrivateProfile: (String, String, String, String, String, String, Boolean) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) } // 0 = Expenses & Tickets, 1 = Private PII Details
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showTicketDialog by remember { mutableStateOf(false) }
    val role = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN)

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar("Self-Service Portal", onBack)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("Expenses & Tickets") }
            SegmentedButton(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("My Private Details") }
        }

        if (subTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showExpenseDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("new_expense_button")
                        ) {
                            Icon(Icons.Filled.AttachMoney, contentDescription = "Claim expense", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Claim Expense")
                        }
                        FilledTonalButton(
                            onClick = { showTicketDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("new_ticket_button")
                        ) {
                            Icon(Icons.Filled.SupportAgent, contentDescription = "Raise ticket", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Raise Ticket")
                        }
                    }
                }

                items(bundle.serviceItems, key = { it.id }) { item ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (item.kind == "EXPENSE") {
                                    Text(HrmsPlatformUtils.formatCurrency(item.amount), style = MonoMetricStyle, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    StatusPill(item.status, SoftSurfaceVariant, SlateMuted)
                                }
                            }
                            Text("${item.requesterName} • ${item.category} • ${item.kind}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.description.isNotBlank()) {
                                Text(item.description, style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.hasAttachment && item.attachmentLabel.isNotBlank()) {
                                Text("Receipt attached: ${item.attachmentLabel}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                            }
                            if (role != HrRole.EMPLOYEE && item.status !in listOf("APPROVED", "REJECTED", "RESOLVED")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.kind == "EXPENSE") {
                                        Button(onClick = { onUpdateServiceStatus(item.id, "APPROVED") }, modifier = Modifier.weight(1f)) { Text("Approve") }
                                        OutlinedButton(onClick = { onUpdateServiceStatus(item.id, "REJECTED") }, modifier = Modifier.weight(1f)) { Text("Reject") }
                                    } else {
                                        Button(onClick = { onUpdateServiceStatus(item.id, "RESOLVED") }, modifier = Modifier.fillMaxWidth()) { Text("Resolve Ticket") }
                                    }
                                }
                            } else {
                                StatusPill(
                                    item.status,
                                    if (item.status in listOf("APPROVED", "RESOLVED")) EmeraldContainer else AmberContainer,
                                    if (item.status in listOf("APPROVED", "RESOLVED")) EmeraldSuccess else OnAmberContainer
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Private PII Profile Form (isolated to /users/{uid})
            var fullName by remember(userProfile) { mutableStateOf(userProfile?.fullName ?: "") }
            var phone by remember(userProfile) { mutableStateOf(userProfile?.phone ?: "") }
            var address by remember(userProfile) { mutableStateOf(userProfile?.homeAddress ?: "") }
            var emergency by remember(userProfile) { mutableStateOf(userProfile?.emergencyContact ?: "") }
            var bankLast4 by remember(userProfile) { mutableStateOf(userProfile?.bankAccountLast4 ?: "4821") }
            var taxStatus by remember(userProfile) { mutableStateOf(userProfile?.taxFilingStatus ?: "SINGLE") }
            val mfaEnabled = userProfile?.mfaLockEnabled ?: false

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = SageContainer.copy(alpha = 0.6f)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = "Encrypted Vault", tint = OnSageContainer)
                            Text("Isolated PII Vault: Only your authenticated UID can read or edit these fields.", style = MaterialTheme.typography.bodySmall, color = OnSageContainer)
                        }
                    }
                }
                item { OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Legal Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Personal Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Home Address") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = emergency, onValueChange = { emergency = it }, label = { Text("Emergency Contact") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = bankLast4, onValueChange = { bankLast4 = it.take(4) }, label = { Text("Direct Deposit Last 4") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("SINGLE", "MARRIED", "HEAD_OF_HOUSEHOLD")) { status ->
                            FilterChip(selected = taxStatus == status, onClick = { taxStatus = status }, label = { Text(status.replace("_", " ")) })
                        }
                    }
                }
                item {
                    Button(
                        onClick = { onSavePrivateProfile(fullName, phone, address, emergency, bankLast4, taxStatus, mfaEnabled) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_private_pii_button")
                    ) {
                        Text("Save Private Details")
                    }
                }
            }
        }
    }

    if (showExpenseDialog) {
        SubmitExpenseDialog(
            onDismiss = { showExpenseDialog = false },
            onSubmit = { title, cat, desc, amt, receipt ->
                onSubmitServiceItem("EXPENSE", title, cat, desc, amt, receipt)
                showExpenseDialog = false
            }
        )
    }

    if (showTicketDialog) {
        SubmitTicketDialog(
            onDismiss = { showTicketDialog = false },
            onSubmit = { title, cat, desc ->
                onSubmitServiceItem("TICKET", title, cat, desc, 0.0, "")
                showTicketDialog = false
            }
        )
    }
}

@Composable
private fun SubmitExpenseDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, Double, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Travel") }
    var amountText by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var attachmentLabel by remember { mutableStateOf("") }

    // Play-policy compliant Android Photo Picker (zero storage permissions!)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                attachmentLabel = uri.lastPathSegment ?: "receipt_photo.jpg"
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Expense Claim") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Expense Title") }, singleLine = true)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount ($)") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Notes") })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Travel", "Meals", "Software", "Equipment")) { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach receipt")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (attachmentLabel.isBlank()) "Attach Receipt (Photo Picker)" else "Attached: $attachmentLabel")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    onSubmit(title, category, desc, amt, attachmentLabel)
                },
                enabled = title.trim().length >= 2 && (amountText.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SubmitTicketDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("IT Support") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raise HR / IT Ticket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Subject") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Details") }, minLines = 2)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("IT Support", "Payroll", "Benefits", "Workplace")) { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(title, category, desc) },
                enabled = title.trim().length >= 2
            ) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
