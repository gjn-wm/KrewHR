package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SyncSnapshot<T>(
    val items: List<T> = emptyList(),
    val pendingWrites: Boolean = false,
    val fromCache: Boolean = false
)

enum class HrRole(val label: String) {
    HR_ADMIN("HR Admin"),
    MANAGER("Manager"),
    EMPLOYEE("Employee")
}

enum class WorkMode(val label: String) {
    HYBRID("Hybrid"),
    REMOTE("Remote"),
    ONSITE("Onsite")
}

enum class VerificationMode(val label: String) {
    ONE_TAP("One-Tap"),
    GPS("GPS Geofence"),
    SELFIE("Selfie Verify")
}

enum class LeaveType(val label: String) {
    ANNUAL("Annual Leave"),
    SICK("Sick Leave"),
    PARENTAL("Parental"),
    WFH("Remote / WFH")
}

inline fun <reified T : Enum<T>> safeEnum(raw: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default

@Keep
@IgnoreExtraProperties
data class UserPrivateProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val workEmail: String = "",
    val phone: String = "",
    val homeAddress: String = "",
    val emergencyContact: String = "",
    val bankAccountLast4: String = "",
    val taxFilingStatus: String = "SINGLE",
    val mfaLockEnabled: Boolean = false,
    val activeWorkspaceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Workspace(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val companyName: String = "",
    val seatLimit: Long = 50L,
    val activeSeatCount: Long = 0L,
    val userRole: String = HrRole.HR_ADMIN.name,
    val slackWebhookUrl: String = "",
    val biometricBadgeEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = true,
    val accountingFormat: String = "CSV",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EmployeeRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val employeeCode: String = "",
    val fullName: String = "",
    val jobTitle: String = "",
    val department: String = "Engineering",
    val role: String = HrRole.EMPLOYEE.name,
    val workEmail: String = "",
    val workPhone: String = "",
    val managerName: String = "",
    val workMode: String = WorkMode.HYBRID.name,
    val shiftWindow: String = "09:00 - 18:00",
    val status: String = "ACTIVE",
    val baseMonthlySalary: Double = 6500.0,
    val annualLeaveBalance: Long = 15L,
    val sickLeaveBalance: Long = 10L,
    val documents: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val employeeName: String = "",
    val department: String = "",
    val dateLabel: String = "",
    val checkInTime: String = "",
    val checkOutTime: String = "",
    val verificationMode: String = VerificationMode.ONE_TAP.name,
    val locationLabel: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val selfieCaptured: Boolean = false,
    val shiftWindow: String = "09:00 - 18:00",
    val hoursWorked: Double = 0.0,
    val status: String = "CHECKED_IN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LeaveRequest(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val employeeName: String = "",
    val department: String = "",
    val leaveType: String = LeaveType.ANNUAL.name,
    val startDate: String = "",
    val endDate: String = "",
    val daysCount: Long = 1L,
    val reason: String = "",
    val status: String = "PENDING",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PayrollRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val employeeName: String = "",
    val employeeCode: String = "",
    val department: String = "",
    val periodLabel: String = "",
    val basePay: Double = 0.0,
    val overtimePay: Double = 0.0,
    val taxDeduction: Double = 0.0,
    val benefitsDeduction: Double = 0.0,
    val netPay: Double = 0.0,
    val daysWorked: Long = 20L,
    val status: String = "SCHEDULED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnboardingTask(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val employeeName: String = "",
    val flowType: String = "ONBOARDING",
    val title: String = "",
    val category: String = "IT_ACCESS",
    val dueDate: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ServiceItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val requesterName: String = "",
    val kind: String = "EXPENSE",
    val title: String = "",
    val category: String = "Travel",
    val description: String = "",
    val amount: Double = 0.0,
    val hasAttachment: Boolean = false,
    val attachmentLabel: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChannelPost(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val channelId: String = "all-hands",
    val authorName: String = "",
    val authorRole: String = "",
    val postType: String = "DISCUSSION",
    val body: String = "",
    val ackCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PerformanceItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val itemKind: String = "GOAL",
    val title: String = "",
    val assigneeName: String = "",
    val subtitle: String = "",
    val progressPercent: Long = 0L,
    val rating: Long = 0L,
    val status: String = "ON_TRACK",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val title: String = "",
    val message: String = "",
    val category: String = "SYSTEM",
    val unread: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
