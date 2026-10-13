package com.onedebrid.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.local.dao.DownloadDao
import com.onedebrid.app.data.local.entity.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            // Remove from database
            downloadDao.deleteDownload(download.id)
            
            // Remove the actual file from local storage if it exists
            download.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}