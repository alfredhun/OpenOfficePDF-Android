package com.openofficepdf.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.database.dao.DocumentDao
import com.openofficepdf.core.database.entity.DocumentEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val documentDao: DocumentDao
) : ViewModel() {

    val recentDocuments: StateFlow<List<DocumentEntity>> = documentDao.getAllRecentDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRecentDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentDao.delete(doc)
        }
    }
}
