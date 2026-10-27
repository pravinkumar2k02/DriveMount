package com.drivemount.app.ui.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.data.db.UploadEntity
import com.drivemount.app.data.repository.DriveRepository
import com.drivemount.app.upload.UploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for inspecting and managing the background file upload queue.
 */
@HiltViewModel
class UploadsViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val uploadManager: UploadManager,
) : ViewModel() {

    val uploads: StateFlow<List<UploadEntity>> = driveRepository.getAllUploads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun pauseUpload(uploadId: String) {
        uploadManager.pauseUpload(uploadId)
    }

    fun resumeUpload(uploadId: String) {
        uploadManager.resumeUpload(uploadId)
    }

    fun cancelUpload(uploadId: String) {
        uploadManager.cancelUpload(uploadId)
    }
}

