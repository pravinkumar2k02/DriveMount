package com.drivemount.app.ui.accounts

import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drivemount.app.data.db.AccountEntity
import com.drivemount.app.util.Constants
import com.drivemount.app.util.toReadableSize

/**
 * Screen displaying the list of mounted Google accounts, their storage quota,
 * and controls to mount, unmount, or view files in the Android system Files app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToUploads: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DriveMount Accounts") },
                actions = {
                    IconButton(onClick = onNavigateToUploads) {
                        Icon(
                            imageVector = Icons.Filled.CloudQueue,
                            contentDescription = "Upload Queue"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.addAccount(context) }
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "No Google Drive accounts mounted",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToSignIn) {
                        Text("Sign In with Google")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(accounts, key = { it.accountId }) { account ->
                    AccountCard(
                        account = account,
                        onRefreshQuota = { viewModel.refreshQuota(account.accountId) },
                        onDeleteClick = { accountToDelete = account },
                        onOpenInFiles = {
                            val uri = DocumentsContract.buildRootUri(Constants.AUTHORITY, account.accountId)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, DocumentsContract.Root.MIME_TYPE_ITEM)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to general storage roots
                                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = DocumentsContract.buildRootsUri(Constants.AUTHORITY)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallbackIntent)
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val uri = DocumentsContract.buildRootsUri(Constants.AUTHORITY)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = uri
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Filled.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open All Mounts in Files App")
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Unmount Account") },
            text = { Text("Are you sure you want to unmount ${account.displayName.ifBlank { account.accountId }}? Local offline cached blocks will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeAccount(account.accountId)
                        accountToDelete = null
                    }
                ) {
                    Text("Unmount", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AccountCard(
    account: AccountEntity,
    onRefreshQuota: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenInFiles: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!account.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = account.photoUrl,
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName.ifBlank { "Google Account" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = account.accountId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Unmount account",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Storage quota
            val usedBytes = account.storageUsed
            val totalBytes = account.storageLimit.coerceAtLeast(1L)
            val progress = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage: ${usedBytes.toReadableSize()} of ${totalBytes.toReadableSize()}",
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = onRefreshQuota, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Quota",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenInFiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in Files App")
            }
        }
    }
}

