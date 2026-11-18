package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HrRole

@Composable
fun WorkspaceSetupScreen(
    userName: String,
    userEmail: String,
    isBusy: Boolean,
    onInitialize: (companyName: String, role: HrRole, populateFifty: Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var companyName by remember { mutableStateOf("KrewTech Solutions") }
    var selectedRole by remember { mutableStateOf(HrRole.HR_ADMIN) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Welcome, ${userName.ifBlank { "Lead" }}", style = MaterialTheme.typography.headlineSmall)
                    Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSignOut) {
                    Text("Sign Out")
                }
            }

            Image(
                painter = painterResource(id = R.drawable.img_onboarding_welcome_1794985564056),
                contentDescription = "Workspace onboarding illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Configure Your 50-Seat Organization", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Set up your private Cloud Firestore workspace. Launch immediately with a 50-employee organizational roster across 6 departments, or start with an empty roster.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company / Organization Name") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = "Company") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("company_name_input")
                    )

                    Text("Your Primary Role", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HrRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Button(
                        onClick = { onInitialize(companyName, selectedRole, true) },
                        enabled = !isBusy && companyName.trim().length >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("launch_50_workspace_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.RocketLaunch, contentDescription = "Launch")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Launch 50-Employee Workspace")
                        }
                    }

                    OutlinedButton(
                        onClick = { onInitialize(companyName, selectedRole, false) },
                        enabled = !isBusy && companyName.trim().length >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("launch_empty_workspace_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = "Blank Workspace")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Empty & Add Manually")
                    }
                }
            }
        }
    }
}
