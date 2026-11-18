package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WorkspaceDataBundle(
    val workspace: Workspace,
    val employees: List<EmployeeRecord> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val leaves: List<LeaveRequest> = emptyList(),
    val payroll: List<PayrollRecord> = emptyList(),
    val onboarding: List<OnboardingTask> = emptyList(),
    val serviceItems: List<ServiceItem> = emptyList(),
    val channelPosts: List<ChannelPost> = emptyList(),
    val performance: List<PerformanceItem> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val hasPendingSync: Boolean = false
)

sealed interface HrmsRootState {
    data object Loading : HrmsRootState
    data object NeedsWorkspaceSetup : HrmsRootState
    data class Ready(
        val userProfile: UserPrivateProfile?,
        val bundle: WorkspaceDataBundle
    ) : HrmsRootState
    data class Error(val message: String) : HrmsRootState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HrmsViewModel(
    private val repository: HrmsRepository,
    val currentUserId: String,
    val userDisplayName: String,
    val userEmail: String
) : ViewModel() {

    private val workspaceId = "ws_$currentUserId"

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _mfaUnlocked = MutableStateFlow(false)
    val mfaUnlocked: StateFlow<Boolean> = _mfaUnlocked.asStateFlow()

    private fun <T> Flow<T>.unwrapFirestoreErrors(label: String): Flow<T> = this.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        throw rootError
    }

    private val coreSubDataFlow: Flow<WorkspaceDataBundle?> = repository.observeWorkspace(workspaceId)
        .unwrapFirestoreErrors("workspace")
        .flatMapLatest { workspace ->
            if (workspace == null) {
                flowOf(null)
            } else {
                val part1 = combine(
                    repository.observeEmployees(workspaceId).unwrapFirestoreErrors("employees"),
                    repository.observeAttendance(workspaceId).unwrapFirestoreErrors("attendance"),
                    repository.observeLeaveRequests(workspaceId).unwrapFirestoreErrors("leaves"),
                    repository.observePayroll(workspaceId).unwrapFirestoreErrors("payroll"),
                    repository.observeOnboarding(workspaceId).unwrapFirestoreErrors("onboarding")
                ) { emp, att, lv, pay, onb ->
                    FiveTuple(emp, att, lv, pay, onb)
                }

                val part2 = combine(
                    repository.observeServiceItems(workspaceId).unwrapFirestoreErrors("serviceItems"),
                    repository.observeChannelPosts(workspaceId).unwrapFirestoreErrors("channelPosts"),
                    repository.observePerformance(workspaceId).unwrapFirestoreErrors("performance"),
                    repository.observeNotifications(workspaceId).unwrapFirestoreErrors("notifications")
                ) { srv, posts, perf, notifs ->
                    FourTuple(srv, posts, perf, notifs)
                }

                combine(part1, part2) { p1, p2 ->
                    val pending = p1.first.pendingWrites || p1.second.pendingWrites ||
                        p1.third.pendingWrites || p1.fourth.pendingWrites || p1.fifth.pendingWrites ||
                        p2.first.pendingWrites || p2.second.pendingWrites || p2.third.pendingWrites ||
                        p2.fourth.pendingWrites
                    WorkspaceDataBundle(
                        workspace = workspace,
                        employees = p1.first.items.sortedBy { it.employeeCode },
                        attendance = p1.second.items.sortedByDescending { it.createdAt?.seconds ?: 0L },
                        leaves = p1.third.items.sortedByDescending { it.createdAt?.seconds ?: 0L },
                        payroll = p1.fourth.items.sortedBy { it.employeeCode },
                        onboarding = p1.fifth.items.sortedBy { it.completed },
                        serviceItems = p2.first.items.sortedByDescending { it.createdAt?.seconds ?: 0L },
                        channelPosts = p2.second.items.sortedByDescending { it.createdAt?.seconds ?: 0L },
                        performance = p2.third.items.sortedBy { it.itemKind },
                        notifications = p2.fourth.items.sortedByDescending { it.createdAt?.seconds ?: 0L },
                        hasPendingSync = pending
                    )
                }
            }
        }

    val rootState: StateFlow<HrmsRootState> = combine(
        repository.observeUserProfile(currentUserId).unwrapFirestoreErrors("userProfile"),
        coreSubDataFlow
    ) { profile, bundle ->
        if (bundle == null) {
            HrmsRootState.NeedsWorkspaceSetup
        } else {
            HrmsRootState.Ready(profile, bundle)
        }
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(HrmsRootState.Error(root.message ?: "Failed to load workspace data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = HrmsRootState.Loading
        )

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun unlockMfaSession() {
        _mfaUnlocked.value = true
    }

    fun setupWorkspace(companyName: String, role: HrRole, populateFifty: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.initializeWorkspace(
                companyName = companyName,
                role = role,
                fullName = userDisplayName,
                email = userEmail,
                populateFiftyEmployees = populateFifty
            )
            _isBusy.value = false
            res.onFailure { _snackbarMessage.value = it.localizedMessage ?: "Workspace setup failed" }
                .onSuccess {
                    _snackbarMessage.value = if (populateFifty) {
                        "Initialized 50-employee workspace"
                    } else {
                        "Workspace created"
                    }
                }
        }
    }

    fun checkIn(
        mode: VerificationMode,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        selfieCaptured: Boolean,
        shiftWindow: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.checkIn(
                wsId = workspaceId,
                employeeName = userDisplayName.ifBlank { "Employee" },
                department = "People",
                mode = mode,
                locationLabel = locationLabel,
                latitude = latitude,
                longitude = longitude,
                selfieCaptured = selfieCaptured,
                shiftWindow = shiftWindow
            )
            _isBusy.value = false
            res.onSuccess { _snackbarMessage.value = "Checked in (${mode.label})" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Check-in failed" }
        }
    }

    fun checkOut(record: AttendanceRecord) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.checkOut(workspaceId, record.id, record.checkInTime)
            _isBusy.value = false
            res.onSuccess { _snackbarMessage.value = "Checked out for today" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Check-out failed" }
        }
    }

    fun submitLeave(
        leaveType: LeaveType,
        startDate: String,
        endDate: String,
        daysCount: Long,
        reason: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitLeaveRequest(
                wsId = workspaceId,
                employeeName = userDisplayName.ifBlank { "Employee" },
                department = "People",
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                daysCount = daysCount,
                reason = reason
            )
            _isBusy.value = false
            res.onSuccess { _snackbarMessage.value = "Leave request submitted" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to submit leave" }
        }
    }

    fun decideLeave(leaveId: String, approved: Boolean) {
        viewModelScope.launch {
            val res = repository.decideLeaveRequest(workspaceId, leaveId, approved, userDisplayName)
            res.onSuccess {
                _snackbarMessage.value = if (approved) "Leave approved" else "Leave declined"
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Approval failed"
            }
        }
    }

    fun addEmployee(
        currentCount: Int,
        fullName: String,
        jobTitle: String,
        department: String,
        role: HrRole,
        workEmail: String,
        managerName: String,
        workMode: WorkMode,
        shiftWindow: String,
        salary: Double
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addEmployee(
                workspaceId, currentCount, fullName, jobTitle, department, role,
                workEmail, managerName, workMode, shiftWindow, salary
            )
            _isBusy.value = false
            res.onSuccess { _snackbarMessage.value = "$fullName added to roster" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not add employee" }
        }
    }

    fun updateEmployeeShift(employeeId: String, shiftWindow: String, status: String, workMode: String) {
        viewModelScope.launch {
            val res = repository.updateEmployeeShiftAndStatus(workspaceId, employeeId, shiftWindow, status, workMode)
            res.onSuccess { _snackbarMessage.value = "Employee schedule updated" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun toggleOnboarding(task: OnboardingTask) {
        viewModelScope.launch {
            val res = repository.toggleOnboardingTask(workspaceId, task.id, !task.completed)
            res.onFailure { _snackbarMessage.value = it.localizedMessage ?: "Checklist update failed" }
        }
    }

    fun addOnboardingTask(employeeName: String, flowType: String, title: String, category: String, dueDate: String) {
        viewModelScope.launch {
            val res = repository.addOnboardingTask(workspaceId, employeeName, flowType, title, category, dueDate)
            res.onSuccess { _snackbarMessage.value = "Checklist task added" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to add task" }
        }
    }

    fun runPayroll(employees: List<EmployeeRecord>, attendance: List<AttendanceRecord>, periodLabel: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.runMonthlyPayroll(workspaceId, employees, attendance, periodLabel)
            _isBusy.value = false
            res.onSuccess { count -> _snackbarMessage.value = "Payroll calculated for $count employees" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Payroll run failed" }
        }
    }

    fun markPayrollPaid(payslipId: String) {
        viewModelScope.launch {
            val res = repository.markPayrollPaid(workspaceId, payslipId)
            res.onSuccess { _snackbarMessage.value = "Payslip marked PAID" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun submitServiceItem(
        kind: String,
        title: String,
        category: String,
        description: String,
        amount: Double,
        attachmentLabel: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitServiceItem(
                workspaceId, userDisplayName.ifBlank { "Employee" },
                kind, title, category, description, amount, attachmentLabel
            )
            _isBusy.value = false
            res.onSuccess {
                _snackbarMessage.value = if (kind == "EXPENSE") "Expense claim submitted" else "HR ticket raised"
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Submission failed"
            }
        }
    }

    fun updateServiceStatus(itemId: String, status: String) {
        viewModelScope.launch {
            val res = repository.updateServiceItemStatus(workspaceId, itemId, status)
            res.onSuccess { _snackbarMessage.value = "Updated status to $status" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun postToChannel(channelId: String, postType: String, body: String, roleLabel: String) {
        viewModelScope.launch {
            val res = repository.postChannelMessage(
                workspaceId, channelId, userDisplayName.ifBlank { "Team Member" },
                roleLabel, postType, body
            )
            res.onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not post message" }
        }
    }

    fun acknowledgePost(post: ChannelPost) {
        viewModelScope.launch {
            repository.acknowledgePost(workspaceId, post.id, post.ackCount)
        }
    }

    fun addPerformanceItem(kind: String, title: String, assignee: String, subtitle: String, rating: Long) {
        viewModelScope.launch {
            val res = repository.addPerformanceItem(workspaceId, kind, title, assignee, subtitle, rating)
            res.onSuccess { _snackbarMessage.value = "Saved to Performance & Learning" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not save item" }
        }
    }

    fun updatePerformanceProgress(perfId: String, progress: Long) {
        viewModelScope.launch {
            repository.updatePerformanceProgress(workspaceId, perfId, progress)
        }
    }

    fun savePrivateProfile(
        fullName: String,
        phone: String,
        address: String,
        emergency: String,
        bankLast4: String,
        taxStatus: String,
        mfaEnabled: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updatePrivateProfile(fullName, phone, address, emergency, bankLast4, taxStatus, mfaEnabled)
            _isBusy.value = false
            res.onSuccess { _snackbarMessage.value = "Personal details saved securely" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Save failed" }
        }
    }

    fun updateWorkspaceIntegrations(
        role: HrRole? = null,
        slackWebhook: String? = null,
        biometricBadge: Boolean? = null,
        calendarSync: Boolean? = null,
        accountingFormat: String? = null
    ) {
        viewModelScope.launch {
            val res = repository.updateWorkspaceSettings(
                workspaceId, role, slackWebhook, biometricBadge, calendarSync, accountingFormat
            )
            res.onSuccess { _snackbarMessage.value = "Workspace preferences updated" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun markNotificationsRead(notifications: List<AppNotification>) {
        val unreadIds = notifications.filter { it.unread }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            repository.markAllNotificationsRead(workspaceId, unreadIds)
        }
    }

    private data class FiveTuple<A, B, C, D, E>(
        val first: A, val second: B, val third: C, val fourth: D, val fifth: E
    )

    private data class FourTuple<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )

    private companion object {
        const val TAG = "HrmsViewModel"
    }
}
