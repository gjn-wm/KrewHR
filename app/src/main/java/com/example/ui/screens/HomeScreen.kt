package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.*
import com.example.ui.WorkspaceDataBundle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.OfflineSyncPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.utils.HrmsPlatformUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    bundle: WorkspaceDataBundle,
    userName: String,
    isBusy: Boolean,
    onQuickCheckIn: () -> Unit,
    onQuickCheckOut: (AttendanceRecord) -> Unit,
    onApproveLeave: (String, Boolean) -> Unit,
    onApproveExpense: (String, String) -> Unit,
    onNavigateTab: (Int) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val role = safeEnum(bundle.workspace.userRole, HrRole.HR_ADMIN)
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val activePunch = bundle.attendance.firstOrNull {
        it.dateLabel == todayDate && it.status == "CHECKED_IN" &&
            it.employeeName.equals(userName, ignoreCase = true)
    } ?: bundle.attendance.firstOrNull { it.dateLabel == todayDate && it.status == "CHECKED_IN" }

    val pendingLeaves = bundle.leaves.filter { it.status == "PENDING" }
    val pendingExpenses = bundle.serviceItems.filter { it.status == "PENDING" && it.kind == "EXPENSE" }
    val unreadNotifs = bundle.notifications.count { it.unread }
    val announcements = bundle.channelPosts.filter { it.postType == "ANNOUNCEMENT" }.take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Editorial Workspace Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bundle.workspace.companyName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hi, ${userName.substringBefore(" ").ifBlank { "Team" }}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OfflineSyncPill(hasPendingSync = bundle.hasPendingSync)
                    BadgedBox(
                        badge = {
                            if (unreadNotifs > 0) {
                                Badge { Text("$unreadNotifs") }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.testTag("notifications_button")
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Open notifications")
                        }
                    }
                }
            }
        }

        // 2. Hero Attendance Punch Card with visual backdrop
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = TealPrimary,
                shadowElevation = 4.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_workplace_hero_1794985564660),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.14f
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(TealPrimary.copy(alpha = 0.94f), TealPrimaryDark.copy(alpha = 0.88f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                label = if (activePunch != null) "ON SHIFT • ${activePunch.checkInTime}" else "SHIFT 09:00 – 18:00",
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            )
                            StatusPill(
                                label = role.label,
                                containerColor = AmberLight,
                                contentColor = SlateInk
                            )
                        }
                        Text(
                            text = if (activePunch != null) {
                                "Clocked in via ${safeEnum(activePunch.verificationMode, VerificationMode.ONE_TAP).label}"
                            } else {
                                "Ready to start today's shift?"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = if (activePunch != null) {
                                "${activePunch.locationLabel.ifBlank { "Workspace Verified" }} • Synced to cloud"
                            } else {
                                "Use 1-tap punch here, or open Time & Leave for GPS geofence or selfie verification."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (activePunch != null) onQuickCheckOut(activePunch) else onQuickCheckIn()
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activePunch != null) AmberLight else Color.White,
                                    contentColor = SlateInk
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("home_attendance_button")
                            ) {
                                Icon(
                                    if (activePunch != null) Icons.Filled.Logout else Icons.Filled.TouchApp,
                                    contentDescription = "Clock toggle",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (activePunch != null) "Check Out" else "1-Tap Check In",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = { onNavigateTab(2) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = "GPS or Selfie", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GPS / Selfie")
                            }
                        }
                    }
                }
            }
        }

        // 3. 2x2 KPI Summary
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard(
                        title = "Team Headcount",
                        value = "${bundle.employees.size}/50",
                        subtitle = "${bundle.employees.count { it.status == "ACTIVE" }} active today",
                        icon = Icons.Filled.Groups,
                        accentColor = TealPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(1) }
                    )
                    KpiMetricCard(
                        title = "Checked In",
                        value = "${bundle.attendance.count { it.status == "CHECKED_IN" }}",
                        subtitle = "${bundle.attendance.size} total logs",
                        icon = Icons.Filled.Schedule,
                        accentColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(2) }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard(
                        title = "Pending Approvals",
                        value = "${pendingLeaves.size + pendingExpenses.size}",
                        subtitle = "${pendingLeaves.size} leave • ${pendingExpenses.size} expense",
                        icon = Icons.Filled.FactCheck,
                        accentColor = AmberAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(2) }
                    )
                    KpiMetricCard(
                        title = "Open Tasks",
                        value = "${bundle.onboarding.count { !it.completed }}",
                        subtitle = "Onboarding & exits",
                        icon = Icons.Filled.Checklist,
                        accentColor = SageSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTab(1) }
                    )
                }
            }
        }

        // 4. Fast Approvals Inbox
        item {
            SectionHeader(
                title = if (role == HrRole.EMPLOYEE) "My Pending Requests" else "Fast Approvals Inbox",
                actionLabel = "View All",
                onActionClick = { onNavigateTab(2) }
            )
        }

        if (pendingLeaves.isEmpty() && pendingExpenses.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.DoneAll,
                    title = "Inbox Zero",
                    subtitle = "All leave requests and expense claims are up to date."
                )
            }
        } else {
            items(pendingLeaves.take(3), key = { "lv_${it.id}" }) { leave ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(leave.employeeName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${safeEnum(leave.leaveType, LeaveType.ANNUAL).label} • ${leave.startDate} (${leave.daysCount}d)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill("LEAVE", AmberContainer, OnAmberContainer)
                        }
                        Text(leave.reason, style = MaterialTheme.typography.bodyMedium)
                        if (role != HrRole.EMPLOYEE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onApproveLeave(leave.id, true) },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("approve_leave_${leave.id}")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve")
                                }
                                OutlinedButton(
                                    onClick = { onApproveLeave(leave.id, false) },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("decline_leave_${leave.id}")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Decline", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Decline")
                                }
                            }
                        }
                    }
                }
            }

            items(pendingExpenses.take(2), key = { "exp_${it.id}" }) { exp ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${exp.requesterName} • ${exp.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                HrmsPlatformUtils.formatCurrency(exp.amount),
                                style = MonoMetricStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (role != HrRole.EMPLOYEE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onApproveExpense(exp.id, "APPROVED") },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text("Approve Claim")
                                }
                                OutlinedButton(
                                    onClick = { onApproveExpense(exp.id, "REJECTED") },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Recent Company Announcements
        item {
            SectionHeader(
                title = "Company Announcements",
                actionLabel = "Channels",
                onActionClick = { onNavigateTab(3) }
            )
        }

        items(announcements, key = { it.id }) { post ->
            Surface(
                onClick = { onNavigateTab(3) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${post.channelId}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${post.ackCount} acks", style = MonoCaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(post.body, style = MaterialTheme.typography.bodyMedium)
                    Text("${post.authorName} • ${post.authorRole}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
