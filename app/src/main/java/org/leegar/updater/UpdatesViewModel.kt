package org.leegar.updater

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.leegar.updater.data.ChangelogState
import org.leegar.updater.data.ProjectInfo
import org.leegar.updater.data.Update
import org.leegar.updater.updatescheck.UpdatesCheckState

private const val TAG = "UpdatesViewModel"

class UpdatesViewModel(
    application: Application,
) : AndroidViewModel(application) {

    data class UiState(
        val updates: List<Update> = emptyList(),
        val projectInfo: ProjectInfo = ProjectInfo(),
        val isCheckingForUpdates: Boolean = false,
        val isOnline: Boolean = true,
        val lastCheckedTimestamp: Long = 0L,
        val hasUpdateCheckFailed: Boolean = false,
        val lastError: String? = null,
        val downloadError: String? = null,
        val changelogs: Map<String, ChangelogState> = emptyMap(),
    ) {
        val checkState: UpdatesCheckState
            get() = when {
                isCheckingForUpdates -> UpdatesCheckState.Checking
                !isOnline -> UpdatesCheckState.NoInternet
                hasUpdateCheckFailed -> UpdatesCheckState.Error
                else -> UpdatesCheckState.Idle
            }

        val canCheckForUpdates: Boolean
            get() = isOnline && !isCheckingForUpdates
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val updaterApplication = getApplication<UpdaterApplication>()
    private val repository = updaterApplication.updatesRepository
    private val appStateRepository = updaterApplication.appStateRepository
    private val changelogRepository = updaterApplication.changelogRepository
    private val networkMonitor = updaterApplication.networkMonitor

    private val changelogJobs = HashMap<String, Job>()

    init {
        viewModelScope.launch {
            appStateRepository.lastCheckedTimestampFlow.collect { ts ->
                _uiState.update { it.copy(lastCheckedTimestamp = ts) }
            }
        }

        viewModelScope.launch {
            appStateRepository.projectInfoFlow.collect { projectInfo ->
                _uiState.update { it.copy(projectInfo = projectInfo) }
            }
        }

        viewModelScope.launch {
            repository.observeLocalUpdates().collect { updates ->
                val cached = changelogRepository.loadCachedMap(updates)
                _uiState.update { state ->
                    val mergedChangelogs = cached + state.changelogs
                    state.copy(
                        updates = updates,
                        changelogs = mergedChangelogs,
                    )
                }
            }
        }

        viewModelScope.launch {
            networkMonitor.networkState
                .distinctUntilChangedBy { it.isOnline }
                .collect { networkState ->
                    _uiState.update { it.copy(isOnline = networkState.isOnline) }
                }
        }

        // First-ever launch auto-check: only if the app has never checked before and has no cached updates
        viewModelScope.launch {
            val hasChecked = appStateRepository.hasEverCheckedFlow.first()
            val local = repository.observeLocalUpdates().first()
            if (!hasChecked && local.isEmpty()) {
                networkMonitor.networkState.first { it.isOnline }
                fetchUpdates()
            }
        }
    }

    fun fetchUpdates() {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    hasUpdateCheckFailed = false,
                    lastError = null,
                )
            }
            try {
                val fetchedAt = repository.fetchUpdates()
                if (fetchedAt != null) {
                    appStateRepository.setLastCheckedTimestamp(fetchedAt)
                }
                val refreshedLogs = changelogRepository.loadCachedMap(_uiState.value.updates)
                _uiState.update { state ->
                    state.copy(
                        isCheckingForUpdates = false,
                        changelogs = state.changelogs + refreshedLogs,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch updates", e)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false,
                        hasUpdateCheckFailed = true,
                        lastError = e.message ?: getApplication<UpdaterApplication>().getString(R.string.err_network),
                    )
                }
            }
        }
    }

    fun loadChangelog(update: Update, forceRefresh: Boolean = false) {
        val id = update.downloadId
        val existing = _uiState.value.changelogs[id]
        if (!forceRefresh && (existing is ChangelogState.Loaded || changelogJobs[id]?.isActive == true)) {
            return
        }

        val previousLoaded = existing as? ChangelogState.Loaded
        changelogJobs[id]?.cancel()
        changelogJobs[id] = viewModelScope.launch {
            _uiState.update { state ->
                val pendingState = if (previousLoaded != null) {
                    previousLoaded.copy(isRefreshing = true)
                } else {
                    ChangelogState.Loading
                }
                state.copy(changelogs = state.changelogs + (id to pendingState))
            }
            val result = try {
                ChangelogState.Loaded(
                    markdown = changelogRepository.fetchChangelog(update, forceRefresh),
                    isRefreshing = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load changelog for $id", e)
                previousLoaded?.copy(isRefreshing = false) ?: ChangelogState.Error(e.message)
            }
            _uiState.update { state ->
                state.copy(changelogs = state.changelogs + (id to result))
            }
        }
    }

    fun reportDownloadError(message: String?) {
        val clean = message?.trim()?.takeIf { it.isNotEmpty() } ?: return
        _uiState.update { it.copy(downloadError = clean) }
    }

    fun clearDownloadError() {
        _uiState.update { it.copy(downloadError = null, hasUpdateCheckFailed = false, lastError = null) }
    }

    fun clearChangelogCache() {
        changelogRepository.clearCache()
        _uiState.update { it.copy(changelogs = emptyMap()) }
    }
}