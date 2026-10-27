package com.drivemount.app.ui.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivemount.app.data.db.UploadEntity
import com.drivemount.app.util.toReadableSize

/**
 * Screen displaying the active, queued, and completed background uploads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UploadsViewModel = hiltViewModel(),
) {
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Queue") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uploads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No active uploads in queue",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uploads, key = { it.uploadId }) { upload ->
                    UploadItemCard(
                        upload = upload,
                        onPause = { viewModel.pauseUpload(upload.uploadId) },
                        onResume = { viewModel.resumeUpload(upload.uploadId) },
                        onCancel = { viewModel.cancelUpload(upload.uploadId) }
                    )
                }
            }
        }
    }
}

@Composable
fun UploadItemCard(
    upload: UploadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = upload.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                UploadStatusChip(status = upload.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (upload.totalSize > 0) {
                (upload.uploadedBytes.toFloat() / upload.totalSize.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${upload.uploadedBytes.toReadableSize()} of ${upload.totalSize.toReadableSize()} (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (upload.status == UploadEntity.STATUS_IN_PROGRESS) {
                        IconButton(onClick = onPause) {
                            Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause")
                        }
                    } else if (upload.status == UploadEntity.STATUS_PAUSED || upload.status == UploadEntity.STATUS_FAILED) {
                        IconButton(onClick = onResume) {
                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Resume")
                        }
                    }

                    if (upload.status != UploadEntity.STATUS_COMPLETED) {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (!upload.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = upload.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun UploadStatusChip(status: String) {
    val (label, chipColor) = when (status) {
        UploadEntity.STATUS_IN_PROGRESS -> "Uploading" to Color(0xFF1976D2)
        UploadEntity.STATUS_PAUSED -> "Paused" to Color(0xFFF57C00)
        UploadEntity.STATUS_COMPLETED -> "Done" to Color(0xFF388E3C)
        UploadEntity.STATUS_FAILED -> "Failed" to Color(0xFFD32F2F)
        else -> "Pending" to Color.Gray
    }

    SuggestionChip(
        onClick = {},
        label = { Text(label, color = chipColor) },
        colors = SuggestionChipDefaults.suggestionChipColors()
    )
}

