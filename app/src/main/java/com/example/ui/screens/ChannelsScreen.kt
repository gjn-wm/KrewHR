package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ChannelPost
import com.example.data.HrRole
import com.example.data.safeEnum
import com.example.ui.WorkspaceDataBundle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

private val CHANNELS = listOf(
    "all-hands" to "#all-hands",
    "engineering" to "#engineering",
    "product-design" to "#product-design",
    "operations" to "#operations",
    "sales" to "#sales",
    "people-ops" to "#people-ops"
)

@Composable
fun ChannelsScreen(
    bundle: WorkspaceDataBundle,
    onPostMessage: (channelId: String, postType: String, body: String, roleLabel: String) -> Unit,
    onAcknowledgePost: (ChannelPost) -> Unit
) {
    var selectedChannel by remember { mutableStateOf("all-hands") }
    var messageText by remember { mutableStateOf("") }
    var isAnnouncement by remember { mutableStateOf(false) }

    val roleLabel = safeEnum(bundle.workspace.userRole, HrRole.EMPLOYEE).label
    val channelPosts = remember(bundle.channelPosts, selectedChannel) {
        bundle.channelPosts.filter { it.channelId == selectedChannel }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Department Channels & Forums", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Cross-team announcements, department threads, and shared HR processes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CHANNELS) { (id, label) ->
                FilterChip(
                    selected = selectedChannel == id,
                    onClick = { selectedChannel = id },
                    label = { Text(label) },
                    leadingIcon = {
                        if (id == "all-hands") {
                            Icon(Icons.Filled.Campaign, contentDescription = label, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.testTag("channel_chip_$id")
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (channelPosts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Forum,
                        title = "Quiet in #$selectedChannel",
                        subtitle = "Start a cross-team thread or post an announcement below."
                    )
                }
            } else {
                items(channelPosts, key = { it.id }) { post ->
                    val isPinned = post.postType == "ANNOUNCEMENT"
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isPinned) TealPrimaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(post.authorName, style = MaterialTheme.typography.titleSmall)
                                    Text(post.authorRole, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(
                                    label = if (isPinned) "ANNOUNCEMENT" else "THREAD",
                                    containerColor = if (isPinned) AmberContainer else SoftSurfaceVariant,
                                    contentColor = if (isPinned) OnAmberContainer else SlateMuted
                                )
                            }
                            Text(post.body, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick = { onAcknowledgePost(post) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Filled.ThumbUp, contentDescription = "Acknowledge", modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${post.ackCount}", style = MonoCaptionStyle)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAnnouncement,
                        onClick = { isAnnouncement = !isAnnouncement },
                        label = { Text(if (isAnnouncement) "Tag: Announcement" else "Tag: Discussion") },
                        leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = "Announcement tag", modifier = Modifier.size(16.dp)) }
                    )
                    Text("Posting to #$selectedChannel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Share an update in #$selectedChannel...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("channel_message_input")
                    )
                    FilledIconButton(
                        onClick = {
                            val trimmed = messageText.trim()
                            if (trimmed.isNotEmpty()) {
                                onPostMessage(
                                    selectedChannel,
                                    if (isAnnouncement) "ANNOUNCEMENT" else "DISCUSSION",
                                    trimmed,
                                    roleLabel
                                )
                                messageText = ""
                            }
                        },
                        enabled = messageText.trim().isNotEmpty(),
                        modifier = Modifier.size(50.dp).testTag("send_channel_post_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post message")
                    }
                }
            }
        }
    }
}
