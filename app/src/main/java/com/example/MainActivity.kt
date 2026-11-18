package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.HrmsRepository
import com.example.data.VerificationMode
import com.example.ui.HrmsRootState
import com.example.ui.HrmsViewModel
import com.example.ui.LocalPreviewViewModel
import com.example.ui.components.NotificationsSheet
import com.example.ui.screens.*
import com.example.ui.theme.KrewHrTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KrewHrTheme {
                AppNavigation()
            }
        }
    }
}

private data class NavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_TABS = listOf(
    NavTab("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    NavTab("People", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_people"),
    NavTab("Time", Icons.Filled.Schedule, Icons.Outlined.Schedule, "nav_time"),
    NavTab("Channels", Icons.Filled.Forum, Icons.Outlined.Forum, "nav_channels"),
    NavTab("Hub", Icons.Filled.GridView, Icons.Outlined.GridView, "nav_hub")
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var localPreviewActive by remember { mutableStateOf(false) }
    val user = currentUser

    if (user != null) {
        key(user.uid) {
            KrewHrSignedInShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Workspace Lead",
                userEmail = user.email ?: ""
            )
        }
    } else if (localPreviewActive) {
        KrewHrPreviewShell(onExitPreview = { localPreviewActive = false })
    } else {
        AuthScreen(
            onAuthSuccess = {},
            onSkipSignIn = { localPreviewActive = true }
        )
    }
}

@Composable
private fun KrewHrPreviewShell(onExitPreview: () -> Unit) {
    val viewModel: LocalPreviewViewModel = viewModel(key = "local_preview")
    val bundle by viewModel.bundle.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val mfaUnlocked by viewModel.mfaUnlocked.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showNotifications by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        NAV_TABS.forEachIndexed { index, tab ->
                            val selected = selectedTab == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail) {
                    NavigationRail {
                        Spacer(modifier = Modifier.height(12.dp))
                        NAV_TABS.forEachIndexed { index, tab ->
                            val selected = selectedTab == index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                bundle = bundle,
                                userName = viewModel.previewName,
                                isBusy = false,
                                onQuickCheckIn = {
                                    viewModel.checkIn(VerificationMode.ONE_TAP, "Mobile 1-Tap Check-In", 0.0, 0.0, false, "09:00 - 18:00")
                                },
                                onQuickCheckOut = { rec -> viewModel.checkOut(rec) },
                                onApproveLeave = { id, ok -> viewModel.decideLeave(id, ok) },
                                onApproveExpense = { id, st -> viewModel.updateServiceStatus(id, st) },
                                onNavigateTab = { selectedTab = it },
                                onOpenNotifications = { showNotifications = true }
                            )

                            1 -> PeopleScreen(
                                bundle = bundle,
                                onAddEmployee = { name, title, dept, role, email, mgr, mode, shift, salary ->
                                    viewModel.addEmployee(name, title, dept, role, email, mgr, mode, shift, salary)
                                },
                                onUpdateEmployee = { id, shift, status, mode ->
                                    viewModel.updateEmployeeShift(id, shift, status, mode)
                                },
                                onToggleTask = { viewModel.toggleOnboarding(it) },
                                onAddTask = { empName, flow, title, cat, due ->
                                    viewModel.addOnboardingTask(empName, flow, title, cat, due)
                                }
                            )

                            2 -> TimeLeaveScreen(
                                bundle = bundle,
                                userName = viewModel.previewName,
                                isBusy = false,
                                onCheckIn = { mode, loc, lat, lon, selfie, shift ->
                                    viewModel.checkIn(mode, loc, lat, lon, selfie, shift)
                                },
                                onCheckOut = { rec -> viewModel.checkOut(rec) },
                                onSubmitLeave = { type, start, end, days, reason ->
                                    viewModel.submitLeave(type, start, end, days, reason)
                                },
                                onDecideLeave = { id, ok -> viewModel.decideLeave(id, ok) },
                                onShowMessage = { viewModel.showMessage(it) }
                            )

                            3 -> ChannelsScreen(
                                bundle = bundle,
                                onPostMessage = { channelId, postType, body, roleLabel ->
                                    viewModel.postToChannel(channelId, postType, body, roleLabel)
                                },
                                onAcknowledgePost = { post -> viewModel.acknowledgePost(post) }
                            )

                            4 -> HubScreen(
                                bundle = bundle,
                                userProfile = profile,
                                mfaUnlocked = mfaUnlocked,
                                isBusy = false,
                                onUnlockMfa = { viewModel.unlockMfaSession() },
                                onRunPayroll = { period -> viewModel.runPayroll(period) },
                                onMarkPayrollPaid = { id -> viewModel.markPayrollPaid(id) },
                                onSubmitServiceItem = { kind, title, cat, desc, amt, att ->
                                    viewModel.submitServiceItem(kind, title, cat, desc, amt, att)
                                },
                                onUpdateServiceStatus = { id, st -> viewModel.updateServiceStatus(id, st) },
                                onSavePrivateProfile = { name, phone, addr, emg, bank, tax, mfa ->
                                    viewModel.savePrivateProfile(name, phone, addr, emg, bank, tax, mfa)
                                },
                                onAddPerformanceItem = { kind, title, assignee, sub, rating ->
                                    viewModel.addPerformanceItem(kind, title, assignee, sub, rating)
                                },
                                onUpdatePerformanceProgress = { id, progress ->
                                    viewModel.updatePerformanceProgress(id, progress)
                                },
                                onUpdateWorkspaceIntegrations = { role, slack, bio, cal, fmt ->
                                    viewModel.updateWorkspaceIntegrations(role, slack, bio, cal, fmt)
                                },
                                onSignOut = onExitPreview,
                                onShowMessage = { viewModel.showMessage(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        NotificationsSheet(
            notifications = bundle.notifications,
            onMarkAllRead = { viewModel.markNotificationsRead() },
            onDismiss = { showNotifications = false }
        )
    }
}

@Composable
private fun KrewHrSignedInShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: HrmsViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                HrmsViewModel(
                    repository = HrmsRepository(app),
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val mfaUnlocked by viewModel.mfaUnlocked.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showNotifications by remember { mutableStateOf(false) }

    when (val state = rootState) {
        is HrmsRootState.Loading -> {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading KrewHR workspace...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        is HrmsRootState.Error -> {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                        Text("Workspace Sync Error", style = MaterialTheme.typography.titleLarge)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { signOutUser(credentialManager, scope) }) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }

        is HrmsRootState.NeedsWorkspaceSetup -> {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    WorkspaceSetupScreen(
                        userName = userDisplayName,
                        userEmail = userEmail,
                        isBusy = isBusy,
                        onInitialize = { company, role, populate50 ->
                            viewModel.setupWorkspace(company, role, populate50)
                        },
                        onSignOut = { signOutUser(credentialManager, scope) }
                    )
                }
            }
        }

        is HrmsRootState.Ready -> {
            val bundle = state.bundle
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 680.dp

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                NAV_TABS.forEachIndexed { index, tab ->
                                    val selected = selectedTab == index
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Icon(
                                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail {
                                Spacer(modifier = Modifier.height(12.dp))
                                NAV_TABS.forEachIndexed { index, tab ->
                                    val selected = selectedTab == index
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Icon(
                                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 840.dp)
                                    .fillMaxHeight()
                                    .align(Alignment.TopCenter)
                            ) {
                                when (selectedTab) {
                                    0 -> HomeScreen(
                                        bundle = bundle,
                                        userName = userDisplayName,
                                        isBusy = isBusy,
                                        onQuickCheckIn = {
                                            viewModel.checkIn(
                                                VerificationMode.ONE_TAP,
                                                "Mobile 1-Tap Check-In",
                                                0.0,
                                                0.0,
                                                false,
                                                "09:00 - 18:00"
                                            )
                                        },
                                        onQuickCheckOut = { rec -> viewModel.checkOut(rec) },
                                        onApproveLeave = { id, ok -> viewModel.decideLeave(id, ok) },
                                        onApproveExpense = { id, st -> viewModel.updateServiceStatus(id, st) },
                                        onNavigateTab = { selectedTab = it },
                                        onOpenNotifications = { showNotifications = true }
                                    )

                                    1 -> PeopleScreen(
                                        bundle = bundle,
                                        onAddEmployee = { name, title, dept, role, email, mgr, mode, shift, salary ->
                                            viewModel.addEmployee(bundle.employees.size, name, title, dept, role, email, mgr, mode, shift, salary)
                                        },
                                        onUpdateEmployee = { id, shift, status, mode ->
                                            viewModel.updateEmployeeShift(id, shift, status, mode)
                                        },
                                        onToggleTask = { viewModel.toggleOnboarding(it) },
                                        onAddTask = { empName, flow, title, cat, due ->
                                            viewModel.addOnboardingTask(empName, flow, title, cat, due)
                                        }
                                    )

                                    2 -> TimeLeaveScreen(
                                        bundle = bundle,
                                        userName = userDisplayName,
                                        isBusy = isBusy,
                                        onCheckIn = { mode, loc, lat, lon, selfie, shift ->
                                            viewModel.checkIn(mode, loc, lat, lon, selfie, shift)
                                        },
                                        onCheckOut = { rec -> viewModel.checkOut(rec) },
                                        onSubmitLeave = { type, start, end, days, reason ->
                                            viewModel.submitLeave(type, start, end, days, reason)
                                        },
                                        onDecideLeave = { id, ok -> viewModel.decideLeave(id, ok) },
                                        onShowMessage = { viewModel.showMessage(it) }
                                    )

                                    3 -> ChannelsScreen(
                                        bundle = bundle,
                                        onPostMessage = { channelId, postType, body, roleLabel ->
                                            viewModel.postToChannel(channelId, postType, body, roleLabel)
                                        },
                                        onAcknowledgePost = { post -> viewModel.acknowledgePost(post) }
                                    )

                                    4 -> HubScreen(
                                        bundle = bundle,
                                        userProfile = state.userProfile,
                                        mfaUnlocked = mfaUnlocked,
                                        isBusy = isBusy,
                                        onUnlockMfa = { viewModel.unlockMfaSession() },
                                        onRunPayroll = { period ->
                                            viewModel.runPayroll(bundle.employees, bundle.attendance, period)
                                        },
                                        onMarkPayrollPaid = { id -> viewModel.markPayrollPaid(id) },
                                        onSubmitServiceItem = { kind, title, cat, desc, amt, att ->
                                            viewModel.submitServiceItem(kind, title, cat, desc, amt, att)
                                        },
                                        onUpdateServiceStatus = { id, st -> viewModel.updateServiceStatus(id, st) },
                                        onSavePrivateProfile = { name, phone, addr, emg, bank, tax, mfa ->
                                            viewModel.savePrivateProfile(name, phone, addr, emg, bank, tax, mfa)
                                        },
                                        onAddPerformanceItem = { kind, title, assignee, sub, rating ->
                                            viewModel.addPerformanceItem(kind, title, assignee, sub, rating)
                                        },
                                        onUpdatePerformanceProgress = { id, progress ->
                                            viewModel.updatePerformanceProgress(id, progress)
                                        },
                                        onUpdateWorkspaceIntegrations = { role, slack, bio, cal, fmt ->
                                            viewModel.updateWorkspaceIntegrations(role, slack, bio, cal, fmt)
                                        },
                                        onSignOut = { signOutUser(credentialManager, scope) },
                                        onShowMessage = { viewModel.showMessage(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showNotifications) {
                NotificationsSheet(
                    notifications = bundle.notifications,
                    onMarkAllRead = { viewModel.markNotificationsRead(bundle.notifications) },
                    onDismiss = { showNotifications = false }
                )
            }
        }
    }
}
