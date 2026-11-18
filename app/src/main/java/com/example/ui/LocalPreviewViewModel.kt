package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Pure in-memory ViewModel used ONLY when the user explicitly skips Google Sign-In
 * on emulators/devices without a Google account. Never touches FirebaseFirestore.
 */
class LocalPreviewViewModel : ViewModel() {

    private val previewUid = "local_preview_user"
    val previewName = "Alex Rivera"

    private val _userProfile = MutableStateFlow(
        UserPrivateProfile(
            id = previewUid,
            userId = previewUid,
            fullName = previewName,
            workEmail = "alex.rivera@krewhr.io",
            phone = "+1-415-555-0100",
            homeAddress = "450 Market St, San Francisco, CA",
            emergencyContact = "Jordan Rivera (+1-415-555-0199)",
            bankAccountLast4 = "4821",
            taxFilingStatus = "SINGLE",
            mfaLockEnabled = false,
            activeWorkspaceId = "local_ws"
        )
    )
    val userProfile: StateFlow<UserPrivateProfile> = _userProfile.asStateFlow()

    private val _bundle = MutableStateFlow(buildInitialBundle())
    val bundle: StateFlow<WorkspaceDataBundle> = _bundle.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _mfaUnlocked = MutableStateFlow(false)
    val mfaUnlocked: StateFlow<Boolean> = _mfaUnlocked.asStateFlow()

    private fun buildInitialBundle(): WorkspaceDataBundle {
        val employees = HrmsSeedData.buildFiftyEmployeeRoster(previewUid, previewName, "alex.rivera@krewhr.io")
            .map { (id, rec) -> rec.copy(id = id) }
        val attendance = HrmsSeedData.buildInitialAttendance(previewUid, previewName)
            .map { (id, rec) -> rec.copy(id = id) }
        val leaves = HrmsSeedData.buildInitialLeaves(previewUid)
            .map { (id, rec) -> rec.copy(id = id) }
        val onboarding = HrmsSeedData.buildInitialOnboarding(previewUid)
            .map { (id, rec) -> rec.copy(id = id) }
        val serviceItems = HrmsSeedData.buildInitialServiceItems(previewUid)
            .map { (id, rec) -> rec.copy(id = id) }
        val posts = HrmsSeedData.buildInitialChannelPosts(previewUid, previewName)
            .map { (id, rec) -> rec.copy(id = id) }
        val perf = HrmsSeedData.buildInitialPerformance(previewUid, previewName)
            .map { (id, rec) -> rec.copy(id = id) }
        val notifs = HrmsSeedData.buildInitialNotifications(previewUid)
            .map { (id, rec) -> rec.copy(id = id) }

        return WorkspaceDataBundle(
            workspace = Workspace(
                id = "local_ws",
                ownerUid = previewUid,
                memberUids = listOf(previewUid),
                companyName = "KrewTech Solutions (Preview)",
                seatLimit = 50L,
                activeSeatCount = employees.size.toLong(),
                userRole = HrRole.HR_ADMIN.name,
                biometricBadgeEnabled = true,
                calendarSyncEnabled = true,
                accountingFormat = "CSV"
            ),
            employees = employees,
            attendance = attendance,
            leaves = leaves,
            payroll = emptyList(),
            onboarding = onboarding,
            serviceItems = serviceItems,
            channelPosts = posts,
            performance = perf,
            notifications = notifs,
            hasPendingSync = true
        )
    }

    fun clearSnackbar() { _snackbarMessage.value = null }
    fun showMessage(msg: String) { _snackbarMessage.value = msg }
    fun unlockMfaSession() { _mfaUnlocked.value = true }

    fun checkIn(
        mode: VerificationMode,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        selfieCaptured: Boolean,
        shiftWindow: String
    ) {
        val now = Date()
        val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val timeLabel = SimpleDateFormat("HH:mm", Locale.US).format(now)
        val newRec = AttendanceRecord(
            id = "att_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            employeeName = previewName,
            department = "People",
            dateLabel = dateLabel,
            checkInTime = timeLabel,
            verificationMode = mode.name,
            locationLabel = locationLabel,
            latitude = latitude,
            longitude = longitude,
            selfieCaptured = selfieCaptured,
            shiftWindow = shiftWindow,
            hoursWorked = 0.0,
            status = "CHECKED_IN"
        )
        _bundle.update { it.copy(attendance = listOf(newRec) + it.attendance) }
        _snackbarMessage.value = "Checked in (${mode.label})"
    }

    fun checkOut(record: AttendanceRecord) {
        val nowTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        _bundle.update { b ->
            b.copy(
                attendance = b.attendance.map {
                    if (it.id == record.id) it.copy(checkOutTime = nowTime, hoursWorked = 8.0, status = "CHECKED_OUT") else it
                }
            )
        }
        _snackbarMessage.value = "Checked out for today"
    }

    fun submitLeave(type: LeaveType, startDate: String, endDate: String, daysCount: Long, reason: String) {
        val lv = LeaveRequest(
            id = "lv_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            employeeName = previewName,
            department = "People",
            leaveType = type.name,
            startDate = startDate,
            endDate = endDate,
            daysCount = daysCount,
            reason = reason.ifBlank { "Time off request" },
            status = "PENDING"
        )
        _bundle.update { it.copy(leaves = listOf(lv) + it.leaves) }
        _snackbarMessage.value = "Leave request submitted"
    }

    fun decideLeave(leaveId: String, approved: Boolean) {
        val next = if (approved) "APPROVED" else "REJECTED"
        _bundle.update { b ->
            b.copy(leaves = b.leaves.map { if (it.id == leaveId) it.copy(status = next, reviewedBy = previewName) else it })
        }
        _snackbarMessage.value = if (approved) "Leave approved" else "Leave declined"
    }

    fun addEmployee(
        fullName: String, jobTitle: String, department: String, role: HrRole,
        email: String, manager: String, mode: WorkMode, shift: String, salary: Double
    ) {
        val current = _bundle.value.employees.size
        if (current >= 50) {
            _snackbarMessage.value = "Workspace seat limit (50/50) reached."
            return
        }
        val code = "KRW-${(current + 1).toString().padStart(3, '0')}"
        val emp = EmployeeRecord(
            id = "emp_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            employeeCode = code,
            fullName = fullName.trim(),
            jobTitle = jobTitle.trim(),
            department = department,
            role = role.name,
            workEmail = email.trim(),
            managerName = manager.trim(),
            workMode = mode.name,
            shiftWindow = shift.ifBlank { "09:00 - 18:00" },
            status = "ACTIVE",
            baseMonthlySalary = salary,
            annualLeaveBalance = 15L,
            sickLeaveBalance = 10L,
            documents = listOf("Offer Letter", "NDA & IP Agreement")
        )
        _bundle.update {
            it.copy(
                employees = it.employees + emp,
                workspace = it.workspace.copy(activeSeatCount = (it.employees.size + 1).toLong())
            )
        }
        _snackbarMessage.value = "$fullName added to roster"
    }

    fun updateEmployeeShift(employeeId: String, shift: String, status: String, workMode: String) {
        _bundle.update { b ->
            b.copy(employees = b.employees.map {
                if (it.id == employeeId) it.copy(shiftWindow = shift, status = status, workMode = workMode) else it
            })
        }
        _snackbarMessage.value = "Employee schedule updated"
    }

    fun toggleOnboarding(task: OnboardingTask) {
        _bundle.update { b ->
            b.copy(onboarding = b.onboarding.map { if (it.id == task.id) it.copy(completed = !it.completed) else it })
        }
    }

    fun addOnboardingTask(empName: String, flow: String, title: String, category: String, dueDate: String) {
        val task = OnboardingTask(
            id = "task_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            employeeName = empName,
            flowType = flow,
            title = title,
            category = category,
            dueDate = dueDate,
            completed = false
        )
        _bundle.update { it.copy(onboarding = listOf(task) + it.onboarding) }
        _snackbarMessage.value = "Checklist task added"
    }

    fun runPayroll(periodLabel: String) {
        val b = _bundle.value
        val activeEmps = b.employees.filter { it.status != "OFFBOARDING" }.take(50)
        val slips = activeEmps.map { emp ->
            val logged = b.attendance.count { it.employeeName == emp.fullName }.toLong()
            val worked = (20L + logged).coerceAtMost(23L)
            val overtime = if (logged > 0) 180.0 else 0.0
            val tax = kotlin.math.round(emp.baseMonthlySalary * 0.21 * 100.0) / 100.0
            val benefits = 280.0
            val net = (emp.baseMonthlySalary + overtime - tax - benefits).coerceAtLeast(0.0)
            PayrollRecord(
                id = "pay_${emp.employeeCode}",
                ownerUid = previewUid,
                memberUids = listOf(previewUid),
                employeeName = emp.fullName,
                employeeCode = emp.employeeCode,
                department = emp.department,
                periodLabel = periodLabel,
                basePay = emp.baseMonthlySalary,
                overtimePay = overtime,
                taxDeduction = tax,
                benefitsDeduction = benefits,
                netPay = net,
                daysWorked = worked,
                status = "SCHEDULED"
            )
        }
        _bundle.update { it.copy(payroll = slips) }
        _snackbarMessage.value = "Payroll calculated for ${slips.size} employees"
    }

    fun markPayrollPaid(payslipId: String) {
        _bundle.update { b ->
            b.copy(payroll = b.payroll.map { if (it.id == payslipId) it.copy(status = "PAID") else it })
        }
        _snackbarMessage.value = "Payslip marked PAID"
    }

    fun submitServiceItem(kind: String, title: String, category: String, desc: String, amount: Double, attachment: String) {
        val item = ServiceItem(
            id = "srv_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            requesterName = previewName,
            kind = kind,
            title = title,
            category = category,
            description = desc,
            amount = if (kind == "EXPENSE") amount else 0.0,
            hasAttachment = attachment.isNotBlank(),
            attachmentLabel = attachment,
            status = "PENDING"
        )
        _bundle.update { it.copy(serviceItems = listOf(item) + it.serviceItems) }
        _snackbarMessage.value = if (kind == "EXPENSE") "Expense claim submitted" else "HR ticket raised"
    }

    fun updateServiceStatus(itemId: String, status: String) {
        _bundle.update { b ->
            b.copy(serviceItems = b.serviceItems.map { if (it.id == itemId) it.copy(status = status) else it })
        }
        _snackbarMessage.value = "Updated status to $status"
    }

    fun postToChannel(channelId: String, postType: String, body: String, roleLabel: String) {
        val post = ChannelPost(
            id = "post_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            channelId = channelId,
            authorName = previewName,
            authorRole = roleLabel,
            postType = postType,
            body = body,
            ackCount = 0L
        )
        _bundle.update { it.copy(channelPosts = listOf(post) + it.channelPosts) }
    }

    fun acknowledgePost(post: ChannelPost) {
        _bundle.update { b ->
            b.copy(channelPosts = b.channelPosts.map { if (it.id == post.id) it.copy(ackCount = it.ackCount + 1L) else it })
        }
    }

    fun addPerformanceItem(kind: String, title: String, assignee: String, subtitle: String, rating: Long) {
        val item = PerformanceItem(
            id = "perf_${UUID.randomUUID().toString().take(8)}",
            ownerUid = previewUid,
            memberUids = listOf(previewUid),
            itemKind = kind,
            title = title,
            assigneeName = assignee,
            subtitle = subtitle,
            progressPercent = if (kind == "FEEDBACK") 100L else 0L,
            rating = rating,
            status = if (kind == "FEEDBACK") "COMPLETED" else "ON_TRACK"
        )
        _bundle.update { it.copy(performance = listOf(item) + it.performance) }
        _snackbarMessage.value = "Saved to Performance & Learning"
    }

    fun updatePerformanceProgress(perfId: String, progress: Long) {
        val clamped = progress.coerceIn(0L, 100L)
        _bundle.update { b ->
            b.copy(performance = b.performance.map {
                if (it.id == perfId) it.copy(progressPercent = clamped, status = if (clamped >= 100L) "COMPLETED" else "ON_TRACK") else it
            })
        }
    }

    fun savePrivateProfile(fullName: String, phone: String, addr: String, emergency: String, bank: String, tax: String, mfa: Boolean) {
        _userProfile.update {
            it.copy(
                fullName = fullName,
                phone = phone,
                homeAddress = addr,
                emergencyContact = emergency,
                bankAccountLast4 = bank,
                taxFilingStatus = tax,
                mfaLockEnabled = mfa
            )
        }
        _snackbarMessage.value = "Personal details saved (Preview)"
    }

    fun updateWorkspaceIntegrations(role: HrRole?, slack: String?, bio: Boolean?, cal: Boolean?, fmt: String?) {
        _bundle.update { b ->
            b.copy(
                workspace = b.workspace.copy(
                    userRole = role?.name ?: b.workspace.userRole,
                    slackWebhookUrl = slack ?: b.workspace.slackWebhookUrl,
                    biometricBadgeEnabled = bio ?: b.workspace.biometricBadgeEnabled,
                    calendarSyncEnabled = cal ?: b.workspace.calendarSyncEnabled,
                    accountingFormat = fmt ?: b.workspace.accountingFormat
                )
            )
        }
        _snackbarMessage.value = "Workspace preferences updated"
    }

    fun markNotificationsRead() {
        _bundle.update { b ->
            b.copy(notifications = b.notifications.map { it.copy(unread = false) })
        }
    }
}
