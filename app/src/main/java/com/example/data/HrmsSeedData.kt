package com.example.data

object HrmsSeedData {

    private data class RosterTemplate(
        val name: String,
        val title: String,
        val dept: String,
        val role: String,
        val manager: String,
        val mode: String,
        val shift: String,
        val status: String,
        val salary: Double
    )

    fun buildFiftyEmployeeRoster(
        ownerUid: String,
        ownerName: String,
        ownerEmail: String
    ): List<Pair<String, EmployeeRecord>> {
        val domain = ownerEmail.substringAfter("@", "krewhr.io").ifBlank { "krewhr.io" }
        val templates = listOf(
            RosterTemplate(ownerName.ifBlank { "Alex Rivera" }, "Head of People & Ops", "People", "HR_ADMIN", "Board / CEO", "HYBRID", "09:00 - 18:00", "ACTIVE", 11500.0),
            RosterTemplate("Maya Lin", "Chief Technology Officer", "Engineering", "HR_ADMIN", "Board / CEO", "HYBRID", "09:00 - 18:00", "ACTIVE", 13800.0),
            RosterTemplate("Marcus Chen", "VP of Engineering", "Engineering", "MANAGER", "Maya Lin", "HYBRID", "09:00 - 18:00", "ACTIVE", 12200.0),
            RosterTemplate("Elena Rostova", "Director of Product", "Product", "MANAGER", "Maya Lin", "HYBRID", "09:30 - 18:30", "ACTIVE", 11800.0),
            RosterTemplate("Devon Brooks", "Head of Design", "Design", "MANAGER", "Elena Rostova", "REMOTE", "09:00 - 18:00", "ACTIVE", 10900.0),
            RosterTemplate("Priya Nair", "VP of Revenue", "Sales", "MANAGER", ownerName.ifBlank { "Alex Rivera" }, "HYBRID", "08:30 - 17:30", "ACTIVE", 11600.0),
            RosterTemplate("Jordan Hayes", "Operations Lead", "Operations", "MANAGER", ownerName.ifBlank { "Alex Rivera" }, "ONSITE", "08:00 - 17:00", "ACTIVE", 9400.0),
            RosterTemplate("Sofia Alvarez", "People Partner", "People", "HR_ADMIN", ownerName.ifBlank { "Alex Rivera" }, "HYBRID", "09:00 - 18:00", "ACTIVE", 8200.0),
            RosterTemplate("Liam O'Connor", "Staff Android Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "REMOTE", "09:00 - 18:00", "ACTIVE", 10400.0),
            RosterTemplate("Aisha Patel", "Senior Backend Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 9800.0),
            RosterTemplate("Noah Takahashi", "Platform Architect", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:30 - 18:30", "ACTIVE", 10600.0),
            RosterTemplate("Chloe Bennett", "Frontend Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "REMOTE", "09:00 - 18:00", "ACTIVE", 8400.0),
            RosterTemplate("Ethan Morales", "QA Automation Lead", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 8100.0),
            RosterTemplate("Zara Haddad", "Site Reliability Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "REMOTE", "10:00 - 19:00", "ON_LEAVE", 9600.0),
            RosterTemplate("Lucas Meyer", "iOS Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 9200.0),
            RosterTemplate("Hannah Kim", "Security Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 9900.0),
            RosterTemplate("Oliver Grant", "Data Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "REMOTE", "09:00 - 18:00", "ACTIVE", 9100.0),
            RosterTemplate("Nadia Kowalski", "Mobile Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 8700.0),
            RosterTemplate("Caleb Foster", "Cloud Infra Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "REMOTE", "08:30 - 17:30", "ACTIVE", 9300.0),
            RosterTemplate("Mei-Ling Zhou", "Full Stack Engineer", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 8800.0),
            RosterTemplate("Tariq Al-Mansoor", "API Integrations Eng", "Engineering", "EMPLOYEE", "Marcus Chen", "REMOTE", "09:30 - 18:30", "ACTIVE", 8600.0),
            RosterTemplate("Sienna Reed", "Junior Software Eng", "Engineering", "EMPLOYEE", "Marcus Chen", "ONSITE", "09:00 - 18:00", "ACTIVE", 6900.0),
            RosterTemplate("Victor Silva", "DevOps Specialist", "Engineering", "EMPLOYEE", "Marcus Chen", "HYBRID", "09:00 - 18:00", "ACTIVE", 8900.0),
            RosterTemplate("Grace Liu", "Senior Product Manager", "Product", "EMPLOYEE", "Elena Rostova", "HYBRID", "09:00 - 18:00", "ACTIVE", 9700.0),
            RosterTemplate("Daniel Park", "Product Manager", "Product", "EMPLOYEE", "Elena Rostova", "HYBRID", "09:00 - 18:00", "ACTIVE", 8600.0),
            RosterTemplate("Amara Okafor", "Product Analyst", "Product", "EMPLOYEE", "Elena Rostova", "REMOTE", "09:00 - 18:00", "ACTIVE", 7600.0),
            RosterTemplate("Owen Murphy", "Technical PM", "Product", "EMPLOYEE", "Elena Rostova", "HYBRID", "09:30 - 18:30", "ACTIVE", 9100.0),
            RosterTemplate("Layla Mansour", "Growth PM", "Product", "EMPLOYEE", "Elena Rostova", "REMOTE", "09:00 - 18:00", "ON_LEAVE", 8500.0),
            RosterTemplate("Julian Cruz", "Product Operations", "Product", "EMPLOYEE", "Elena Rostova", "HYBRID", "09:00 - 18:00", "ACTIVE", 7400.0),
            RosterTemplate("Clara Johansson", "Staff Product Designer", "Design", "EMPLOYEE", "Devon Brooks", "REMOTE", "09:00 - 18:00", "ACTIVE", 9500.0),
            RosterTemplate("Mateo Rossi", "UX Researcher", "Design", "EMPLOYEE", "Devon Brooks", "HYBRID", "09:30 - 18:30", "ACTIVE", 8100.0),
            RosterTemplate("Ruby Thornton", "Design Systems Lead", "Design", "EMPLOYEE", "Devon Brooks", "REMOTE", "09:00 - 18:00", "ACTIVE", 8900.0),
            RosterTemplate("Kieran Walsh", "Brand Designer", "Design", "EMPLOYEE", "Devon Brooks", "HYBRID", "09:00 - 18:00", "ACTIVE", 7500.0),
            RosterTemplate("Nora Sato", "UI Motion Designer", "Design", "EMPLOYEE", "Devon Brooks", "REMOTE", "10:00 - 19:00", "ACTIVE", 7800.0),
            RosterTemplate("Sebastian Weber", "Enterprise AE", "Sales", "EMPLOYEE", "Priya Nair", "HYBRID", "08:30 - 17:30", "ACTIVE", 8800.0),
            RosterTemplate("Camille Laurent", "Account Executive", "Sales", "EMPLOYEE", "Priya Nair", "HYBRID", "08:30 - 17:30", "ACTIVE", 7900.0),
            RosterTemplate("Dylan Cooper", "Solutions Engineer", "Sales", "EMPLOYEE", "Priya Nair", "REMOTE", "09:00 - 18:00", "ACTIVE", 8600.0),
            RosterTemplate("Fatima Zahra", "Customer Success Lead", "Sales", "MANAGER", "Priya Nair", "HYBRID", "09:00 - 18:00", "ACTIVE", 8400.0),
            RosterTemplate("Connor Price", "Account Manager", "Sales", "EMPLOYEE", "Fatima Zahra", "REMOTE", "09:00 - 18:00", "ACTIVE", 7200.0),
            RosterTemplate("Eva Novak", "SDR Team Lead", "Sales", "EMPLOYEE", "Priya Nair", "ONSITE", "08:30 - 17:30", "ACTIVE", 6800.0),
            RosterTemplate("Leo Fernandez", "Sales Enablement", "Sales", "EMPLOYEE", "Priya Nair", "HYBRID", "09:00 - 18:00", "ACTIVE", 7100.0),
            RosterTemplate("Harper Shaw", "Partnerships Manager", "Sales", "EMPLOYEE", "Priya Nair", "REMOTE", "09:00 - 18:00", "OFFBOARDING", 8000.0),
            RosterTemplate("Isaac Mensah", "Finance Controller", "Operations", "MANAGER", "Jordan Hayes", "HYBRID", "08:30 - 17:30", "ACTIVE", 9200.0),
            RosterTemplate("Tessa Ward", "IT & MDM Admin", "Operations", "EMPLOYEE", "Jordan Hayes", "ONSITE", "08:00 - 17:00", "ACTIVE", 7600.0),
            RosterTemplate("Samir Gupta", "Payroll Specialist", "Operations", "EMPLOYEE", "Isaac Mensah", "HYBRID", "09:00 - 18:00", "ACTIVE", 7400.0),
            RosterTemplate("Mila Petrov", "Workplace Coordinator", "Operations", "EMPLOYEE", "Jordan Hayes", "ONSITE", "08:00 - 17:00", "ACTIVE", 6200.0),
            RosterTemplate("Gavin Cole", "Procurement Analyst", "Operations", "EMPLOYEE", "Isaac Mensah", "HYBRID", "09:00 - 18:00", "ACTIVE", 7000.0),
            RosterTemplate("Fiona Gallagher", "Legal & Compliance", "Operations", "EMPLOYEE", "Jordan Hayes", "REMOTE", "09:00 - 18:00", "ACTIVE", 9400.0),
            RosterTemplate("Arjun Mehta", "Talent Acquisition", "People", "EMPLOYEE", "Sofia Alvarez", "HYBRID", "09:00 - 18:00", "ACTIVE", 7300.0),
            RosterTemplate("Brooke Taylor", "L&D Specialist", "People", "EMPLOYEE", "Sofia Alvarez", "REMOTE", "09:00 - 18:00", "ACTIVE", 7100.0)
        )

        return templates.mapIndexed { idx, t ->
            val num = (idx + 1).toString().padStart(3, '0')
            val empId = "emp_$num"
            val emailHandle = t.name.lowercase().replace("[^a-z0-9]".toRegex(), ".").replace("..", ".").trim('.')
            val email = if (idx == 0 && ownerEmail.isNotBlank()) ownerEmail else "$emailHandle@$domain"
            empId to EmployeeRecord(
                ownerUid = ownerUid,
                memberUids = listOf(ownerUid),
                employeeCode = "KRW-$num",
                fullName = t.name,
                jobTitle = t.title,
                department = t.dept,
                role = t.role,
                workEmail = email,
                workPhone = "+1-415-555-${(1000 + idx)}",
                managerName = t.manager,
                workMode = t.mode,
                shiftWindow = t.shift,
                status = t.status,
                baseMonthlySalary = t.salary,
                annualLeaveBalance = (12L + (idx % 8)).coerceAtMost(20L),
                sickLeaveBalance = (7L + (idx % 4)).coerceAtMost(12L),
                documents = listOf("Offer Letter", "NDA & IP Agreement", "Tax W-4 Form", "Handbook Ack")
            )
        }
    }

    fun buildInitialAttendance(ownerUid: String, ownerName: String): List<Pair<String, AttendanceRecord>> {
        val sampleStaff = listOf(
            Triple("Marcus Chen", "Engineering", "08:56"),
            Triple("Elena Rostova", "Product", "09:04"),
            Triple("Devon Brooks", "Design", "09:11"),
            Triple("Priya Nair", "Sales", "08:48"),
            Triple("Jordan Hayes", "Operations", "08:02"),
            Triple("Liam O'Connor", "Engineering", "09:00")
        )
        return sampleStaff.mapIndexed { index, (name, dept, time) ->
            "att_init_${index + 1}" to AttendanceRecord(
                ownerUid = ownerUid,
                memberUids = listOf(ownerUid),
                employeeName = name,
                department = dept,
                dateLabel = "2026-11-17",
                checkInTime = time,
                checkOutTime = "",
                verificationMode = if (index % 2 == 0) VerificationMode.GPS.name else VerificationMode.ONE_TAP.name,
                locationLabel = if (index % 2 == 0) "HQ Campus Geofence" else "Remote Verified",
                latitude = 37.7749,
                longitude = -122.4194,
                selfieCaptured = false,
                shiftWindow = "09:00 - 18:00",
                hoursWorked = 5.5,
                status = "CHECKED_IN"
            )
        }
    }

    fun buildInitialLeaves(ownerUid: String): List<Pair<String, LeaveRequest>> = listOf(
        "lv_init_1" to LeaveRequest(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            employeeName = "Zara Haddad",
            department = "Engineering",
            leaveType = LeaveType.ANNUAL.name,
            startDate = "2026-11-23",
            endDate = "2026-11-25",
            daysCount = 3L,
            reason = "Thanksgiving week travel with family",
            status = "PENDING",
            reviewedBy = ""
        ),
        "lv_init_2" to LeaveRequest(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            employeeName = "Layla Mansour",
            department = "Product",
            leaveType = LeaveType.WFH.name,
            startDate = "2026-11-19",
            endDate = "2026-11-20",
            daysCount = 2L,
            reason = "Focused Q4 roadmap sprint from home",
            status = "PENDING",
            reviewedBy = ""
        ),
        "lv_init_3" to LeaveRequest(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            employeeName = "Daniel Park",
            department = "Product",
            leaveType = LeaveType.SICK.name,
            startDate = "2026-11-12",
            endDate = "2026-11-12",
            daysCount = 1L,
            reason = "Dental recovery",
            status = "APPROVED",
            reviewedBy = "Elena Rostova"
        )
    )

    fun buildInitialOnboarding(ownerUid: String): List<Pair<String, OnboardingTask>> = listOf(
        "onb_1" to OnboardingTask(ownerUid = ownerUid, memberUids = listOf(ownerUid), employeeName = "Sienna Reed", flowType = "ONBOARDING", title = "Provision MacBook Pro & MDM enrollment", category = "IT_ACCESS", dueDate = "2026-11-19", completed = true),
        "onb_2" to OnboardingTask(ownerUid = ownerUid, memberUids = listOf(ownerUid), employeeName = "Sienna Reed", flowType = "ONBOARDING", title = "Sign Employee Handbook & Security Policy", category = "POLICY", dueDate = "2026-11-20", completed = false),
        "onb_3" to OnboardingTask(ownerUid = ownerUid, memberUids = listOf(ownerUid), employeeName = "Sienna Reed", flowType = "ONBOARDING", title = "Complete Health & 401k Benefits Enrollment", category = "BENEFITS", dueDate = "2026-11-22", completed = false),
        "off_1" to OnboardingTask(ownerUid = ownerUid, memberUids = listOf(ownerUid), employeeName = "Harper Shaw", flowType = "OFFBOARDING", title = "Schedule Exit Interview & Knowledge Transfer", category = "EXIT", dueDate = "2026-11-21", completed = true),
        "off_2" to OnboardingTask(ownerUid = ownerUid, memberUids = listOf(ownerUid), employeeName = "Harper Shaw", flowType = "OFFBOARDING", title = "Revoke SSO, GitHub & CRM Tokens", category = "IT_ACCESS", dueDate = "2026-11-28", completed = false),
        "off_3" to OnboardingTask(ownerUid = ownerUid, memberUids = listOf(ownerUid), employeeName = "Harper Shaw", flowType = "OFFBOARDING", title = "Return Hardware Kit & Badge", category = "EXIT", dueDate = "2026-11-29", completed = false)
    )

    fun buildInitialServiceItems(ownerUid: String): List<Pair<String, ServiceItem>> = listOf(
        "srv_1" to ServiceItem(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            requesterName = "Liam O'Connor",
            kind = "EXPENSE",
            title = "Droidcon Travel & Hotel",
            category = "Travel",
            description = "2-night conference stay and rail pass",
            amount = 485.50,
            hasAttachment = true,
            attachmentLabel = "receipt_droidcon.jpg",
            status = "PENDING"
        ),
        "srv_2" to ServiceItem(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            requesterName = "Clara Johansson",
            kind = "EXPENSE",
            title = "Ergonomic Monitor Arm",
            category = "Equipment",
            description = "Home office stipend reimbursement",
            amount = 165.00,
            hasAttachment = false,
            attachmentLabel = "",
            status = "PENDING"
        ),
        "srv_3" to ServiceItem(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            requesterName = "Chloe Bennett",
            kind = "TICKET",
            title = "Update Tax Withholding W-4",
            category = "Payroll",
            description = "Need updated state tax withholding form before Nov 30 payroll",
            amount = 0.0,
            hasAttachment = false,
            attachmentLabel = "",
            status = "IN_PROGRESS"
        )
    )

    fun buildInitialChannelPosts(ownerUid: String, ownerName: String): List<Pair<String, ChannelPost>> = listOf(
        "post_1" to ChannelPost(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            channelId = "all-hands",
            authorName = ownerName.ifBlank { "Alex Rivera" },
            authorRole = "HR Admin",
            postType = "ANNOUNCEMENT",
            body = "Welcome to KrewHR! Q4 Benefits Open Enrollment closes Nov 25. Please review your selections in Hub > Payroll & Benefits.",
            ackCount = 34L
        ),
        "post_2" to ChannelPost(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            channelId = "engineering",
            authorName = "Marcus Chen",
            authorRole = "VP Engineering",
            postType = "DISCUSSION",
            body = "Release v4.2 freeze starts Thursday at 4 PM. Drop any migration notes in #engineering before EOD.",
            ackCount = 14L
        ),
        "post_3" to ChannelPost(
            ownerUid = ownerUid,
            memberUids = listOf(ownerUid),
            channelId = "people-ops",
            authorName = "Sofia Alvarez",
            authorRole = "People Partner",
            postType = "ANNOUNCEMENT",
            body = "New hybrid shift guidelines and GPS check-in options are live for field & remote staff.",
            ackCount = 21L
        )
    )

    fun buildInitialPerformance(ownerUid: String, ownerName: String): List<Pair<String, PerformanceItem>> = listOf(
        "perf_1" to PerformanceItem(ownerUid = ownerUid, memberUids = listOf(ownerUid), itemKind = "GOAL", title = "Reduce Mobile App Cold Start Under 1.1s", assigneeName = "Engineering (15 staff)", subtitle = "Q4 Core Architecture OKR", progressPercent = 78L, rating = 4L, status = "ON_TRACK"),
        "perf_2" to PerformanceItem(ownerUid = ownerUid, memberUids = listOf(ownerUid), itemKind = "GOAL", title = "Achieve 98% On-Time Onboarding Completion", assigneeName = "People Ops", subtitle = "50-employee readiness benchmark", progressPercent = 92L, rating = 5L, status = "ON_TRACK"),
        "perf_3" to PerformanceItem(ownerUid = ownerUid, memberUids = listOf(ownerUid), itemKind = "FEEDBACK", title = "Q4 Peer Review: Marcus Chen", assigneeName = ownerName.ifBlank { "Alex Rivera" }, subtitle = "Consistently unblocks cross-team Android & backend sprints with calm clarity.", progressPercent = 100L, rating = 5L, status = "COMPLETED"),
        "perf_4" to PerformanceItem(ownerUid = ownerUid, memberUids = listOf(ownerUid), itemKind = "TRAINING", title = "SOC2 & Workplace Data Privacy 2026", assigneeName = "All 50 Employees", subtitle = "Required annual compliance module (25 mins)", progressPercent = 60L, rating = 4L, status = "ON_TRACK"),
        "perf_5" to PerformanceItem(ownerUid = ownerUid, memberUids = listOf(ownerUid), itemKind = "TRAINING", title = "Inclusive Manager Coaching Workshop", assigneeName = "Managers & Leads", subtitle = "Structured 1:1 feedback & appraisal calibration", progressPercent = 100L, rating = 5L, status = "COMPLETED")
    )

    fun buildInitialNotifications(ownerUid: String): List<Pair<String, AppNotification>> = listOf(
        "notif_1" to AppNotification(ownerUid = ownerUid, memberUids = listOf(ownerUid), title = "2 Leave Requests Pending", message = "Zara Haddad and Layla Mansour await manager approval.", category = "APPROVAL", unread = true),
        "notif_2" to AppNotification(ownerUid = ownerUid, memberUids = listOf(ownerUid), title = "2 Expense Claims Submitted", message = "$650.50 in new reimbursements ready for review.", category = "APPROVAL", unread = true),
        "notif_3" to AppNotification(ownerUid = ownerUid, memberUids = listOf(ownerUid), title = "Nov 2026 Payroll Ready", message = "Run automated payroll calculation across 50 active employees.", category = "PAYROLL", unread = false)
    )
}
