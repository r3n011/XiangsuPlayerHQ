package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.TranscodeCacheDao
import com.theveloper.pixelplay.data.database.TranscodeCacheEntity
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.TranscodeCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TranscodeCacheViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transcodeCacheDao: TranscodeCacheDao
) : ViewModel() {

    private val _cacheEntries = MutableStateFlow<List<TranscodeCacheEntity>>(emptyList())
    val cacheEntries: StateFlow<List<TranscodeCacheEntity>> = _cacheEntries.asStateFlow()

    private val _totalSizeBytes = MutableStateFlow(0L)
    val totalSizeBytes: StateFlow<Long> = _totalSizeBytes.asStateFlow()

    val ttlKey: StateFlow<String> = userPreferencesRepository.transcodeCacheTtlFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "3_days"
        )

    init {
        // 完整初始化缓存管理器，确保 context 和 DAO 都已就绪
        TranscodeCacheManager.init(context, transcodeCacheDao)
        observeCacheEntries()
        refreshSize()
    }

    private fun observeCacheEntries() {
        // 当数据库变化时自动收集最新数据
        viewModelScope.launch {
            runCatching {
                transcodeCacheDao.getAllCaches()
                    .catch { emit(emptyList()) }
                    .collect { entries ->
                        _cacheEntries.value = entries
                        _totalSizeBytes.value = entries.sumOf { it.fileSizeBytes }
                    }
            }
        }
    }

    fun refreshSize() {
        viewModelScope.launch {
            runCatching {
                _totalSizeBytes.value = transcodeCacheDao.getTotalCacheSize() ?: 0L
            }
        }
    }

    fun cleanExpiredNow() {
        viewModelScope.launch {
            runCatching {
                val ttlMs = userPreferencesRepository.getTranscodeCacheTtl()
                TranscodeCacheManager.cleanExpiredByTtl(ttlMs)
                refreshSize()
            }
        }
    }

    fun setTtl(ttlKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.setTranscodeCacheTtl(ttlKey)
        }
    }

    fun deleteEntry(cacheKey: String) {
        TranscodeCacheManager.deleteCacheEntry(cacheKey)
    }

    fun clearAll() {
        TranscodeCacheManager.clearAllCache()
    }
}
