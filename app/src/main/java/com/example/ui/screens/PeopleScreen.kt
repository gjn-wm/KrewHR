package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.WorkspaceDataBundle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.utils.HrmsPlatformUtils

private val DEPARTMENTS = listOf("All", "Engineering", "Product", "Design", "Sales", "Operations", "People")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    bundle: WorkspaceDataBundle,
    onAddEmployee: (
        fullName: String, jobTitle: String, department: String, role: HrRole,
        email: String, manager: String, mode: WorkMode, shift: String, salary: Double
    ) -> Unit,
    onUpdateEmployee: (id: String, shift: String, status: String, mode: String) -> Unit,
    onToggleTask: (OnboardingTask) -> Unit,
    onAddTask: (empName: String, flowType: String, title: String, category: String, dueDate: String) -> Unit
) {
    val context = LocalContext.current
    val currentUserRole = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN)
    var subTab by remember { mutableIntStateOf(0) } // 0 = Directory, 1 = Onboarding/Offboarding
    var searchQuery by remember { mutableStateOf("") }
    var selectedDept by remember { mutableStateOf("All") }
    var selectedEmployee by remember { mutableStateOf<EmployeeRecord?>(null) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }

    var flowFilter by remember { mutableStateOf("ALL") }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val filteredEmployees = remember(bundle.employees, searchQuery, selectedDept) {
        bundle.employees.filter { emp ->
            val matchesDept = selectedDept == "All" || emp.department == selectedDept
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                emp.fullName.contains(q, ignoreCase = true) ||
                emp.jobTitle.contains(q, ignoreCase = true) ||
                emp.employeeCode.contains(q, ignoreCase = true)
            matchesDept && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + Sub-tab selector
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
                Column {
                    Text("People & Lifecycle", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${bundle.employees.size} of ${bundle.workspace.seatLimit} active workspace seats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (currentUserRole == HrRole.HR_ADMIN) {
                    FilledTonalButton(
                        onClick = {
                            if (subTab == 0) showAddEmployeeDialog = true else showAddTaskDialog = true
                        },
                        modifier = Modifier.testTag("people_add_action_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (subTab == 0) "Add Staff" else "Add Task")
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("tab_directory")
                ) {
                    Text("Directory (${bundle.employees.size})")
                }
                SegmentedButton(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("tab_onboarding")
                ) {
                    Text("Onboard & Exit (${bundle.onboarding.size})")
                }
            }
        }

        if (subTab == 0) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name, title, or ID (e.g. KRW-009)...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search directory") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("directory_search_input")
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DEPARTMENTS) { dept ->
                    FilterChip(
                        selected = selectedDept == dept,
                        onClick = { selectedDept = dept },
                        label = { Text(dept) }
                    )
                }
            }

            if (filteredEmployees.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.PersonSearch,
                        title = "No matching employees",
                        subtitle = "Try another department filter or add a new team member."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEmployees, key = { it.id }) { emp ->
                        EmployeeCard(
                            employee = emp,
                            onClick = { selectedEmployee = emp }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        } else {
            // Onboarding & Offboarding Checklists
            val tasks = bundle.onboarding.filter {
                flowFilter == "ALL" || it.flowType == flowFilter
            }
            val doneCount = tasks.count { it.completed }
            val progress = if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size

            Column(modifier = Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("ALL" to "All Checklists", "ONBOARDING" to "Onboarding", "OFFBOARDING" to "Offboarding")) { (key, label) ->
                        FilterChip(
                            selected = flowFilter == key,
                            onClick = { flowFilter = key },
                            label = { Text(label) }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Checklist Completion", style = MaterialTheme.typography.titleSmall)
                            Text("$doneCount / ${tasks.size} completed", style = MonoMetricStyle)
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTask(task) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = task.completed,
                                    onCheckedChange = { onToggleTask(task) },
                                    modifier = Modifier.testTag("task_checkbox_${task.id}")
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${task.employeeName} • Due ${task.dueDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    label = if (task.flowType == "ONBOARDING") "ONBOARD" else "EXIT",
                                    containerColor = if (task.flowType == "ONBOARDING") SageContainer else AmberContainer,
                                    contentColor = if (task.flowType == "ONBOARDING") OnSageContainer else OnAmberContainer
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Employee Detail Bottom Sheet
    selectedEmployee?.let { emp ->
        EmployeeDetailSheet(
            employee = emp,
            canEdit = currentUserRole == HrRole.HR_ADMIN,
            onDismiss = { selectedEmployee = null },
            onEmailClick = {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${emp.workEmail}"))
                context.startActivity(intent)
            },
            onCallClick = {
                if (emp.workPhone.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${emp.workPhone}"))
                    context.startActivity(intent)
                }
            },
            onSaveChanges = { shift, status, mode ->
                onUpdateEmployee(emp.id, shift, status, mode)
                selectedEmployee = null
            }
        )
    }

    if (showAddEmployeeDialog) {
        AddEmployeeDialog(
            onDismiss = { showAddEmployeeDialog = false },
            onConfirm = { name, title, dept, role, email, mgr, mode, shift, salary ->
                onAddEmployee(name, title, dept, role, email, mgr, mode, shift, salary)
                showAddEmployeeDialog = false
            }
        )
    }

    if (showAddTaskDialog) {
        AddOnboardingTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { empName, flow, title, cat, due ->
                onAddTask(empName, flow, title, cat, due)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
private fun EmployeeCard(employee: EmployeeRecord, onClick: () -> Unit) {
    val initials = employee.fullName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "KR" }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("employee_card_${employee.employeeCode}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(TealPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, style = MaterialTheme.typography.titleMedium, color = OnTealPrimaryContainer, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(employee.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(employee.employeeCode, style = MonoCaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${employee.jobTitle} • ${employee.department}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (employee.managerName.isNotBlank()) {
                    Text("Reports to ${employee.managerName} • ${employee.shiftWindow}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            val (bg, fg) = when (employee.status) {
                "ON_LEAVE" -> AmberContainer to OnAmberContainer
                "OFFBOARDING" -> RoseContainer to RoseError
                else -> EmeraldContainer to EmeraldSuccess
            }
            StatusPill(employee.status.replace("_", " "), bg, fg)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeDetailSheet(
    employee: EmployeeRecord,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEmailClick: () -> Unit,
    onCallClick: () -> Unit,
    onSaveChanges: (shift: String, status: String, mode: String) -> Unit
) {
    var shift by remember(employee.id) { mutableStateOf(employee.shiftWindow) }
    var status by remember(employee.id) { mutableStateOf(employee.status) }
    var mode by remember(employee.id) { mutableStateOf(employee.workMode) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(employee.fullName, style = MaterialTheme.typography.headlineSmall)
                    Text("${employee.jobTitle} • ${employee.department} (${employee.employeeCode})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(safeEnum(employee.role, HrRole.EMPLOYEE).label, TealPrimaryContainer, OnTealPrimaryContainer)
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onEmailClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Email, contentDescription = "Email", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Email")
                }
                FilledTonalButton(onClick = onCallClick, enabled = employee.workPhone.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call")
                }
            }

            Text("Reporting Line: ${employee.managerName.ifBlank { "Executive" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Base Monthly Comp: ${HrmsPlatformUtils.formatCurrency(employee.baseMonthlySalary)} • Annual Leave: ${employee.annualLeaveBalance}d", style = MaterialTheme.typography.bodyMedium)

            Text("Employee Documents", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                employee.documents.take(3).forEach { doc ->
                    AssistChip(onClick = {}, label = { Text(doc) }, leadingIcon = { Icon(Icons.Filled.Description, contentDescription = doc, modifier = Modifier.size(16.dp)) })
                }
            }

            if (canEdit) {
                HorizontalDivider()
                Text("HR Admin Controls", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = shift,
                    onValueChange = { shift = it },
                    label = { Text("Assigned Shift Window") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ACTIVE", "ON_LEAVE", "OFFBOARDING").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.replace("_", " ")) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m.name,
                            onClick = { mode = m.name },
                            label = { Text(m.label) }
                        )
                    }
                }
                Button(
                    onClick = { onSaveChanges(shift, status, mode) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save Employee Record")
                }
            }
        }
    }
}

@Composable
private fun AddEmployeeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, HrRole, String, String, WorkMode, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("Engineering") }
    var email by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf("Marcus Chen") }
    var salaryText by remember { mutableStateOf("8200") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Job Title") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Work Email") }, singleLine = true)
                OutlinedTextField(value = manager, onValueChange = { manager = it }, label = { Text("Manager Name") }, singleLine = true)
                OutlinedTextField(value = salaryText, onValueChange = { salaryText = it }, label = { Text("Monthly Salary ($)") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DEPARTMENTS.drop(1)) { d ->
                        FilterChip(selected = dept == d, onClick = { dept = d }, label = { Text(d) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sal = salaryText.toDoubleOrNull() ?: 7500.0
                    onConfirm(name, title, dept, HrRole.EMPLOYEE, email, manager, WorkMode.HYBRID, "09:00 - 18:00", sal)
                },
                enabled = name.trim().length >= 2 && title.trim().length >= 2 && email.contains("@")
            ) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddOnboardingTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var empName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("ONBOARDING") }
    var category by remember { mutableStateOf("IT_ACCESS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Checklist Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = empName, onValueChange = { empName = it }, label = { Text("Employee Name") }, singleLine = true)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Checklist Task") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = flow == "ONBOARDING", onClick = { flow = "ONBOARDING" }, label = { Text("Onboarding") })
                    FilterChip(selected = flow == "OFFBOARDING", onClick = { flow = "OFFBOARDING" }, label = { Text("Offboarding") })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("IT_ACCESS", "POLICY", "BENEFITS", "EXIT")) { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(empName, flow, title, category, "2026-11-30") },
                enabled = empName.trim().length >= 2 && title.trim().length >= 2
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
