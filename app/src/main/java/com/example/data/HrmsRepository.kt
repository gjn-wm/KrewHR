package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HrmsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext)
    )

    companion object {
        private fun createConfiguredFirestore(appContext: Context): FirebaseFirestore {
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(databaseId)
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
                instance.firestoreSettings = settings
            } catch (_: IllegalStateException) {
                // Settings already initialized on this instance
            }
            return instance
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun workspaceIdFor(uid: String): String = "ws_$uid"

    private fun <T> observeCollection(
        workspaceId: String,
        collectionName: String,
        clazz: Class<T>,
        pathLabel: String
    ): Flow<SyncSnapshot<T>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("workspaces").document(workspaceId).collection(collectionName)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    SyncSnapshot(
                        items = list,
                        pendingWrites = snapshot.metadata.hasPendingWrites(),
                        fromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, pathLabel)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserPrivateProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Profile access restricted to authenticated owner." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserPrivateProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorkspace(workspaceId: String): Flow<Workspace?> = flow {
        val uid = requireUserId()
        val path = "workspaces/$workspaceId"
        emitAll(
            db.collection("workspaces").document(workspaceId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeEmployees(wsId: String): Flow<SyncSnapshot<EmployeeRecord>> =
        observeCollection(wsId, "employees", EmployeeRecord::class.java, "workspaces/$wsId/employees")

    fun observeAttendance(wsId: String): Flow<SyncSnapshot<AttendanceRecord>> =
        observeCollection(wsId, "attendance", AttendanceRecord::class.java, "workspaces/$wsId/attendance")

    fun observeLeaveRequests(wsId: String): Flow<SyncSnapshot<LeaveRequest>> =
        observeCollection(wsId, "leave_requests", LeaveRequest::class.java, "workspaces/$wsId/leave_requests")

    fun observePayroll(wsId: String): Flow<SyncSnapshot<PayrollRecord>> =
        observeCollection(wsId, "payroll_runs", PayrollRecord::class.java, "workspaces/$wsId/payroll_runs")

    fun observeOnboarding(wsId: String): Flow<SyncSnapshot<OnboardingTask>> =
        observeCollection(wsId, "onboarding_tasks", OnboardingTask::class.java, "workspaces/$wsId/onboarding_tasks")

    fun observeServiceItems(wsId: String): Flow<SyncSnapshot<ServiceItem>> =
        observeCollection(wsId, "service_items", ServiceItem::class.java, "workspaces/$wsId/service_items")

    fun observeChannelPosts(wsId: String): Flow<SyncSnapshot<ChannelPost>> =
        observeCollection(wsId, "channel_posts", ChannelPost::class.java, "workspaces/$wsId/channel_posts")

    fun observePerformance(wsId: String): Flow<SyncSnapshot<PerformanceItem>> =
        observeCollection(wsId, "performance_items", PerformanceItem::class.java, "workspaces/$wsId/performance_items")

    fun observeNotifications(wsId: String): Flow<SyncSnapshot<AppNotification>> =
        observeCollection(wsId, "notifications", AppNotification::class.java, "workspaces/$wsId/notifications")

    suspend fun initializeWorkspace(
        companyName: String,
        role: HrRole,
        fullName: String,
        email: String,
        populateFiftyEmployees: Boolean
    ): Result<String> {
        val cleanCompany = companyName.trim().take(80)
        if (cleanCompany.length < 2) return Result.failure(IllegalArgumentException("Company name must be at least 2 characters."))
        val safeName = fullName.trim().ifBlank { "Workspace Lead" }.take(80)
        val safeEmail = email.trim().ifBlank { "member@krewhr.io" }.take(120)

        var path = "workspaces"
        return try {
            val uid = requireUserId()
            val wsId = workspaceIdFor(uid)
            path = "workspaces/$wsId"

            val userPayload = mapOf(
                "userId" to uid,
                "fullName" to safeName,
                "workEmail" to safeEmail,
                "phone" to "",
                "homeAddress" to "",
                "emergencyContact" to "",
                "bankAccountLast4" to "4821",
                "taxFilingStatus" to "SINGLE",
                "mfaLockEnabled" to false,
                "activeWorkspaceId" to wsId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val seatCount = if (populateFiftyEmployees) 50L else 1L
            val wsPayload = mapOf(
                "ownerUid" to uid,
                "memberUids" to listOf(uid),
                "companyName" to cleanCompany,
                "seatLimit" to 50L,
                "activeSeatCount" to seatCount,
                "userRole" to role.name,
                "slackWebhookUrl" to "",
                "biometricBadgeEnabled" to true,
                "calendarSyncEnabled" to true,
                "accountingFormat" to "CSV",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val rootBatch = db.batch()
            rootBatch.set(db.collection("users").document(uid), userPayload)
            rootBatch.set(db.collection("workspaces").document(wsId), wsPayload)
            rootBatch.commit().await()

            val subBatch = db.batch()
            val wsRef = db.collection("workspaces").document(wsId)
            if (populateFiftyEmployees) {
                HrmsSeedData.buildFiftyEmployeeRoster(uid, safeName, safeEmail).forEach { (id, emp) ->
                    subBatch.set(wsRef.collection("employees").document(id), empToMap(emp))
                }
                HrmsSeedData.buildInitialAttendance(uid, safeName).forEach { (id, att) ->
                    subBatch.set(wsRef.collection("attendance").document(id), attendanceToMap(att))
                }
                HrmsSeedData.buildInitialLeaves(uid).forEach { (id, lv) ->
                    subBatch.set(wsRef.collection("leave_requests").document(id), leaveToMap(lv))
                }
                HrmsSeedData.buildInitialOnboarding(uid).forEach { (id, task) ->
                    subBatch.set(wsRef.collection("onboarding_tasks").document(id), onboardingToMap(task))
                }
                HrmsSeedData.buildInitialServiceItems(uid).forEach { (id, srv) ->
                    subBatch.set(wsRef.collection("service_items").document(id), serviceToMap(srv))
                }
                HrmsSeedData.buildInitialChannelPosts(uid, safeName).forEach { (id, post) ->
                    subBatch.set(wsRef.collection("channel_posts").document(id), postToMap(post))
                }
                HrmsSeedData.buildInitialPerformance(uid, safeName).forEach { (id, perf) ->
                    subBatch.set(wsRef.collection("performance_items").document(id), perfToMap(perf))
                }
                HrmsSeedData.buildInitialNotifications(uid).forEach { (id, notif) ->
                    subBatch.set(wsRef.collection("notifications").document(id), notifToMap(notif))
                }
            } else {
                val selfEmp = EmployeeRecord(
                    ownerUid = uid,
                    memberUids = listOf(uid),
                    employeeCode = "KRW-001",
                    fullName = safeName,
                    jobTitle = "Workspace Lead",
                    department = "People",
                    role = role.name,
                    workEmail = safeEmail,
                    workPhone = "",
                    managerName = "Board",
                    workMode = WorkMode.HYBRID.name,
                    shiftWindow = "09:00 - 18:00",
                    status = "ACTIVE",
                    baseMonthlySalary = 9000.0,
                    annualLeaveBalance = 18L,
                    sickLeaveBalance = 10L,
                    documents = listOf("Offer Letter", "Handbook Ack")
                )
                subBatch.set(wsRef.collection("employees").document("emp_001"), empToMap(selfEmp))
            }
            subBatch.commit().await()
            Result.success(wsId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivateProfile(
        fullName: String,
        phone: String,
        homeAddress: String,
        emergencyContact: String,
        bankLast4: String,
        taxFilingStatus: String,
        mfaLockEnabled: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "fullName" to fullName.trim().take(80).ifBlank { "Employee" },
                    "phone" to phone.trim().take(30),
                    "homeAddress" to homeAddress.trim().take(200),
                    "emergencyContact" to emergencyContact.trim().take(120),
                    "bankAccountLast4" to bankLast4.trim().take(10),
                    "taxFilingStatus" to taxFilingStatus,
                    "mfaLockEnabled" to mfaLockEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWorkspaceSettings(
        wsId: String,
        userRole: HrRole? = null,
        slackWebhookUrl: String? = null,
        biometricBadgeEnabled: Boolean? = null,
        calendarSyncEnabled: Boolean? = null,
        accountingFormat: String? = null
    ): Result<Unit> {
        val path = "workspaces/$wsId"
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRole?.let { updates["userRole"] = it.name }
            slackWebhookUrl?.let { updates["slackWebhookUrl"] = it.trim().take(200) }
            biometricBadgeEnabled?.let { updates["biometricBadgeEnabled"] = it }
            calendarSyncEnabled?.let { updates["calendarSyncEnabled"] = it }
            accountingFormat?.let { updates["accountingFormat"] = it }
            db.collection("workspaces").document(wsId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addEmployee(
        wsId: String,
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
    ): Result<Unit> {
        if (currentCount >= 50) return Result.failure(IllegalStateException("Workspace seat limit (50/50) reached."))
        val path = "workspaces/$wsId/employees"
        return try {
            val uid = requireUserId()
            val code = "KRW-${(currentCount + 1).toString().padStart(3, '0')}"
            val emp = EmployeeRecord(
                ownerUid = uid,
                memberUids = listOf(uid),
                employeeCode = code,
                fullName = fullName.trim().take(80),
                jobTitle = jobTitle.trim().take(80),
                department = department,
                role = role.name,
                workEmail = workEmail.trim().take(120),
                workPhone = "",
                managerName = managerName.trim().take(80),
                workMode = workMode.name,
                shiftWindow = shiftWindow.trim().take(40).ifBlank { "09:00 - 18:00" },
                status = "ACTIVE",
                baseMonthlySalary = salary.coerceIn(0.0, 1000000.0),
                annualLeaveBalance = 15L,
                sickLeaveBalance = 10L,
                documents = listOf("Offer Letter", "NDA & IP Agreement")
            )
            val docRef = db.collection("workspaces").document(wsId).collection("employees").document()
            docRef.set(empToMap(emp)).await()
            db.collection("workspaces").document(wsId)
                .update(mapOf("activeSeatCount" to (currentCount + 1).toLong().coerceAtMost(50L), "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateEmployeeShiftAndStatus(
        wsId: String,
        employeeId: String,
        shiftWindow: String,
        status: String,
        workMode: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/employees/$employeeId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "shiftWindow" to shiftWindow.trim().take(40),
                    "status" to status,
                    "workMode" to workMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun checkIn(
        wsId: String,
        employeeName: String,
        department: String,
        mode: VerificationMode,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        selfieCaptured: Boolean,
        shiftWindow: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/attendance"
        return try {
            val uid = requireUserId()
            val now = Date()
            val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
            val timeLabel = SimpleDateFormat("HH:mm", Locale.US).format(now)
            val record = AttendanceRecord(
                ownerUid = uid,
                memberUids = listOf(uid),
                employeeName = employeeName.trim().take(80).ifBlank { "Employee" },
                department = department.ifBlank { "People" },
                dateLabel = dateLabel,
                checkInTime = timeLabel,
                checkOutTime = "",
                verificationMode = mode.name,
                locationLabel = locationLabel.take(120),
                latitude = latitude.coerceIn(-90.0, 90.0),
                longitude = longitude.coerceIn(-180.0, 180.0),
                selfieCaptured = selfieCaptured,
                shiftWindow = shiftWindow.ifBlank { "09:00 - 18:00" },
                hoursWorked = 0.0,
                status = "CHECKED_IN"
            )
            db.collection("workspaces").document(wsId).collection("attendance")
                .document()
                .set(attendanceToMap(record))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun checkOut(wsId: String, recordId: String, checkInTime: String): Result<Unit> {
        val path = "workspaces/$wsId/attendance/$recordId"
        return try {
            requireUserId()
            val nowTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            val computedHours = estimateHoursWorked(checkInTime, nowTime)
            db.document(path).update(
                mapOf(
                    "checkOutTime" to nowTime,
                    "hoursWorked" to computedHours,
                    "status" to "CHECKED_OUT",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun estimateHoursWorked(inTime: String, outTime: String): Double {
        return try {
            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            val d1 = fmt.parse(inTime)
            val d2 = fmt.parse(outTime)
            if (d1 != null && d2 != null) {
                val diff = ((d2.time - d1.time) / 3600000.0).coerceIn(0.1, 16.0)
                kotlin.math.round(diff * 100.0) / 100.0
            } else 8.0
        } catch (_: Exception) {
            8.0
        }
    }

    suspend fun submitLeaveRequest(
        wsId: String,
        employeeName: String,
        department: String,
        leaveType: LeaveType,
        startDate: String,
        endDate: String,
        daysCount: Long,
        reason: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/leave_requests"
        return try {
            val uid = requireUserId()
            val req = LeaveRequest(
                ownerUid = uid,
                memberUids = listOf(uid),
                employeeName = employeeName.trim().take(80).ifBlank { "Employee" },
                department = department.ifBlank { "People" },
                leaveType = leaveType.name,
                startDate = startDate.trim().take(20),
                endDate = endDate.trim().take(20),
                daysCount = daysCount.coerceIn(1L, 60L),
                reason = reason.trim().take(300).ifBlank { "Time off request" },
                status = "PENDING",
                reviewedBy = ""
            )
            db.collection("workspaces").document(wsId).collection("leave_requests")
                .document()
                .set(leaveToMap(req))
                .await()
            addNotificationInternal(
                wsId,
                uid,
                "New Leave Request",
                "${req.employeeName} requested ${req.daysCount}d ${leaveType.label}.",
                "APPROVAL"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun decideLeaveRequest(
        wsId: String,
        leaveId: String,
        approved: Boolean,
        reviewerName: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/leave_requests/$leaveId"
        return try {
            requireUserId()
            val nextStatus = if (approved) "APPROVED" else "REJECTED"
            db.document(path).update(
                mapOf(
                    "status" to nextStatus,
                    "reviewedBy" to reviewerName.trim().take(80).ifBlank { "Manager" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun runMonthlyPayroll(
        wsId: String,
        employees: List<EmployeeRecord>,
        attendanceRecords: List<AttendanceRecord>,
        periodLabel: String
    ): Result<Int> {
        val path = "workspaces/$wsId/payroll_runs"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val payrollCol = db.collection("workspaces").document(wsId).collection("payroll_runs")
            val activeEmps = employees.filter { it.status != "OFFBOARDING" }.take(50)
            activeEmps.forEach { emp ->
                val loggedDays = attendanceRecords.count { it.employeeName == emp.fullName }.toLong()
                val workedDays = (20L + loggedDays).coerceAtMost(23L)
                val overtime = if (loggedDays > 0) 180.0 else 0.0
                val tax = kotlin.math.round(emp.baseMonthlySalary * 0.21 * 100.0) / 100.0
                val benefits = 280.0
                val net = (emp.baseMonthlySalary + overtime - tax - benefits).coerceAtLeast(0.0)
                val docId = "pay_${emp.employeeCode.lowercase().replace("-", "_")}_${periodLabel.lowercase().replace(" ", "_")}"
                val rec = PayrollRecord(
                    ownerUid = uid,
                    memberUids = listOf(uid),
                    employeeName = emp.fullName,
                    employeeCode = emp.employeeCode,
                    department = emp.department,
                    periodLabel = periodLabel.take(40),
                    basePay = emp.baseMonthlySalary,
                    overtimePay = overtime,
                    taxDeduction = tax,
                    benefitsDeduction = benefits,
                    netPay = net,
                    daysWorked = workedDays,
                    status = "SCHEDULED"
                )
                batch.set(payrollCol.document(docId), payrollToMap(rec))
            }
            batch.commit().await()
            addNotificationInternal(
                wsId,
                uid,
                "Payroll Calculated ($periodLabel)",
                "Automated payslips generated for ${activeEmps.size} employees.",
                "PAYROLL"
            )
            Result.success(activeEmps.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markPayrollPaid(wsId: String, payslipId: String): Result<Unit> {
        val path = "workspaces/$wsId/payroll_runs/$payslipId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "status" to "PAID",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleOnboardingTask(wsId: String, taskId: String, completed: Boolean): Result<Unit> {
        val path = "workspaces/$wsId/onboarding_tasks/$taskId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addOnboardingTask(
        wsId: String,
        employeeName: String,
        flowType: String,
        title: String,
        category: String,
        dueDate: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/onboarding_tasks"
        return try {
            val uid = requireUserId()
            val task = OnboardingTask(
                ownerUid = uid,
                memberUids = listOf(uid),
                employeeName = employeeName.trim().take(80).ifBlank { "New Hire" },
                flowType = flowType,
                title = title.trim().take(120),
                category = category,
                dueDate = dueDate.trim().take(20).ifBlank { "2026-11-30" },
                completed = false
            )
            db.collection("workspaces").document(wsId).collection("onboarding_tasks")
                .document()
                .set(onboardingToMap(task))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitServiceItem(
        wsId: String,
        requesterName: String,
        kind: String,
        title: String,
        category: String,
        description: String,
        amount: Double,
        attachmentLabel: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/service_items"
        return try {
            val uid = requireUserId()
            val item = ServiceItem(
                ownerUid = uid,
                memberUids = listOf(uid),
                requesterName = requesterName.trim().take(80).ifBlank { "Employee" },
                kind = kind,
                title = title.trim().take(120),
                category = category.trim().take(40),
                description = description.trim().take(400),
                amount = if (kind == "EXPENSE") amount.coerceIn(0.0, 100000.0) else 0.0,
                hasAttachment = attachmentLabel.isNotBlank(),
                attachmentLabel = attachmentLabel.trim().take(120),
                status = "PENDING"
            )
            db.collection("workspaces").document(wsId).collection("service_items")
                .document()
                .set(serviceToMap(item))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateServiceItemStatus(wsId: String, itemId: String, status: String): Result<Unit> {
        val path = "workspaces/$wsId/service_items/$itemId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun postChannelMessage(
        wsId: String,
        channelId: String,
        authorName: String,
        authorRole: String,
        postType: String,
        body: String
    ): Result<Unit> {
        val path = "workspaces/$wsId/channel_posts"
        return try {
            val uid = requireUserId()
            val post = ChannelPost(
                ownerUid = uid,
                memberUids = listOf(uid),
                channelId = channelId,
                authorName = authorName.trim().take(80).ifBlank { "Team Member" },
                authorRole = authorRole.trim().take(40).ifBlank { "Employee" },
                postType = postType,
                body = body.trim().take(500),
                ackCount = 0L
            )
            db.collection("workspaces").document(wsId).collection("channel_posts")
                .document()
                .set(postToMap(post))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgePost(wsId: String, postId: String, currentCount: Long): Result<Unit> {
        val path = "workspaces/$wsId/channel_posts/$postId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "ackCount" to (currentCount + 1L).coerceAtMost(10000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addPerformanceItem(
        wsId: String,
        itemKind: String,
        title: String,
        assigneeName: String,
        subtitle: String,
        rating: Long
    ): Result<Unit> {
        val path = "workspaces/$wsId/performance_items"
        return try {
            val uid = requireUserId()
            val item = PerformanceItem(
                ownerUid = uid,
                memberUids = listOf(uid),
                itemKind = itemKind,
                title = title.trim().take(120),
                assigneeName = assigneeName.trim().take(80).ifBlank { "Team" },
                subtitle = subtitle.trim().take(300),
                progressPercent = if (itemKind == "FEEDBACK") 100L else 0L,
                rating = rating.coerceIn(0L, 5L),
                status = if (itemKind == "FEEDBACK") "COMPLETED" else "ON_TRACK"
            )
            db.collection("workspaces").document(wsId).collection("performance_items")
                .document()
                .set(perfToMap(item))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePerformanceProgress(wsId: String, perfId: String, progress: Long): Result<Unit> {
        val path = "workspaces/$wsId/performance_items/$perfId"
        return try {
            requireUserId()
            val clamped = progress.coerceIn(0L, 100L)
            val status = if (clamped >= 100L) "COMPLETED" else "ON_TRACK"
            db.document(path).update(
                mapOf(
                    "progressPercent" to clamped,
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(wsId: String, unreadIds: List<String>): Result<Unit> {
        val path = "workspaces/$wsId/notifications"
        return try {
            requireUserId()
            if (unreadIds.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            unreadIds.forEach { id ->
                batch.update(
                    db.collection("workspaces").document(wsId).collection("notifications").document(id),
                    mapOf("unread" to false, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun addNotificationInternal(
        wsId: String,
        uid: String,
        title: String,
        message: String,
        category: String
    ) {
        try {
            val payload = mapOf(
                "ownerUid" to uid,
                "memberUids" to listOf(uid),
                "title" to title.take(100),
                "message" to message.take(250),
                "category" to category,
                "unread" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("workspaces").document(wsId).collection("notifications")
                .document()
                .set(payload)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "workspaces/$wsId/notifications")
        }
    }

    // --- Serialization Helpers (Never include @DocumentId "id", filter nulls, always use serverTimestamp) ---
    private fun empToMap(e: EmployeeRecord): Map<String, Any> = mapOf(
        "ownerUid" to e.ownerUid,
        "memberUids" to e.memberUids,
        "employeeCode" to e.employeeCode,
        "fullName" to e.fullName,
        "jobTitle" to e.jobTitle,
        "department" to e.department,
        "role" to e.role,
        "workEmail" to e.workEmail,
        "workPhone" to e.workPhone,
        "managerName" to e.managerName,
        "workMode" to e.workMode,
        "shiftWindow" to e.shiftWindow,
        "status" to e.status,
        "baseMonthlySalary" to e.baseMonthlySalary,
        "annualLeaveBalance" to e.annualLeaveBalance,
        "sickLeaveBalance" to e.sickLeaveBalance,
        "documents" to e.documents,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun attendanceToMap(a: AttendanceRecord): Map<String, Any> = mapOf(
        "ownerUid" to a.ownerUid,
        "memberUids" to a.memberUids,
        "employeeName" to a.employeeName,
        "department" to a.department,
        "dateLabel" to a.dateLabel,
        "checkInTime" to a.checkInTime,
        "checkOutTime" to a.checkOutTime,
        "verificationMode" to a.verificationMode,
        "locationLabel" to a.locationLabel,
        "latitude" to a.latitude,
        "longitude" to a.longitude,
        "selfieCaptured" to a.selfieCaptured,
        "shiftWindow" to a.shiftWindow,
        "hoursWorked" to a.hoursWorked,
        "status" to a.status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun leaveToMap(l: LeaveRequest): Map<String, Any> = mapOf(
        "ownerUid" to l.ownerUid,
        "memberUids" to l.memberUids,
        "employeeName" to l.employeeName,
        "department" to l.department,
        "leaveType" to l.leaveType,
        "startDate" to l.startDate,
        "endDate" to l.endDate,
        "daysCount" to l.daysCount,
        "reason" to l.reason,
        "status" to l.status,
        "reviewedBy" to l.reviewedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun payrollToMap(p: PayrollRecord): Map<String, Any> = mapOf(
        "ownerUid" to p.ownerUid,
        "memberUids" to p.memberUids,
        "employeeName" to p.employeeName,
        "employeeCode" to p.employeeCode,
        "department" to p.department,
        "periodLabel" to p.periodLabel,
        "basePay" to p.basePay,
        "overtimePay" to p.overtimePay,
        "taxDeduction" to p.taxDeduction,
        "benefitsDeduction" to p.benefitsDeduction,
        "netPay" to p.netPay,
        "daysWorked" to p.daysWorked,
        "status" to p.status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun onboardingToMap(t: OnboardingTask): Map<String, Any> = mapOf(
        "ownerUid" to t.ownerUid,
        "memberUids" to t.memberUids,
        "employeeName" to t.employeeName,
        "flowType" to t.flowType,
        "title" to t.title,
        "category" to t.category,
        "dueDate" to t.dueDate,
        "completed" to t.completed,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun serviceToMap(s: ServiceItem): Map<String, Any> = mapOf(
        "ownerUid" to s.ownerUid,
        "memberUids" to s.memberUids,
        "requesterName" to s.requesterName,
        "kind" to s.kind,
        "title" to s.title,
        "category" to s.category,
        "description" to s.description,
        "amount" to s.amount,
        "hasAttachment" to s.hasAttachment,
        "attachmentLabel" to s.attachmentLabel,
        "status" to s.status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun postToMap(p: ChannelPost): Map<String, Any> = mapOf(
        "ownerUid" to p.ownerUid,
        "memberUids" to p.memberUids,
        "channelId" to p.channelId,
        "authorName" to p.authorName,
        "authorRole" to p.authorRole,
        "postType" to p.postType,
        "body" to p.body,
        "ackCount" to p.ackCount,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun perfToMap(p: PerformanceItem): Map<String, Any> = mapOf(
        "ownerUid" to p.ownerUid,
        "memberUids" to p.memberUids,
        "itemKind" to p.itemKind,
        "title" to p.title,
        "assigneeName" to p.assigneeName,
        "subtitle" to p.subtitle,
        "progressPercent" to p.progressPercent,
        "rating" to p.rating,
        "status" to p.status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun notifToMap(n: AppNotification): Map<String, Any> = mapOf(
        "ownerUid" to n.ownerUid,
        "memberUids" to n.memberUids,
        "title" to n.title,
        "message" to n.message,
        "category" to n.category,
        "unread" to n.unread,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
