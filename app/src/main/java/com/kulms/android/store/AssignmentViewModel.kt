package com.kulms.android.store

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kulms.android.data.local.AppDatabase
import com.kulms.android.data.model.Assignment
import com.kulms.android.data.remote.SakaiApiClient
import com.kulms.android.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssignmentViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).assignmentDao()

    private val _assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val assignments: StateFlow<List<Assignment>> = _assignments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(true)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastRefreshed = MutableStateFlow<Long?>(null)
    val lastRefreshed: StateFlow<Long?> = _lastRefreshed.asStateFlow()

    private val cacheTTL = 30 * 60 * 1000L // 30 min

    companion object {
        private const val TAG = "AssignmentVM"
    }

    // MARK: - Grouped sections

    data class GroupedSection(
        val id: String,
        val label: String,
        val colorHex: String,
        val assignments: List<Assignment>
    )

    fun groupedAssignments(): List<GroupedSection> {
        val all = _assignments.value
        val active = all.filter { !it.isSubmitted && !it.isChecked }
        val submitted = all.filter { it.isSubmitted && !it.isChecked }
        val checked = all.filter { it.isChecked }

        val sorted = active.sortedBy { it.deadline ?: Long.MAX_VALUE }

        val danger = sorted.filter {
            it.urgency == Assignment.Urgency.OVERDUE || it.urgency == Assignment.Urgency.DANGER
        }
        val warning = sorted.filter { it.urgency == Assignment.Urgency.WARNING }
        val success = sorted.filter { it.urgency == Assignment.Urgency.SUCCESS }
        val other = sorted.filter { it.urgency == Assignment.Urgency.OTHER }

        val sections = mutableListOf<GroupedSection>()
        if (danger.isNotEmpty()) sections.add(GroupedSection("danger", "緊急", "#e85555", danger))
        if (warning.isNotEmpty()) sections.add(GroupedSection("warning", "5日以内", "#d7aa57", warning))
        if (success.isNotEmpty()) sections.add(GroupedSection("success", "14日以内", "#62b665", success))
        if (other.isNotEmpty()) sections.add(GroupedSection("other", "その他", "#777777", other))
        if (submitted.isNotEmpty()) sections.add(GroupedSection("submitted", "提出済み", "#777777", submitted))
        if (checked.isNotEmpty()) sections.add(GroupedSection("checked", "完了済み", "#777777", checked))
        return sections
    }

    // MARK: - Load cached (startup, no network)

    fun loadCached() {
        if (_assignments.value.isNotEmpty()) return
        viewModelScope.launch {
            val cached = dao.getAll()
            if (cached.isNotEmpty()) {
                _assignments.value = cached
                _lastRefreshed.value = cached.firstOrNull()?.cachedAt
            }
        }
    }

    // MARK: - Fetch (network)

    fun fetchAll(forceRefresh: Boolean = false) {
        if (_isLoading.value) return

        // Check cache TTL
        if (!forceRefresh) {
            val last = _lastRefreshed.value
            if (last != null && System.currentTimeMillis() - last < cacheTTL && _assignments.value.isNotEmpty()) {
                return
            }
        }

        _isLoading.value = true
        _errorMessage.value = null
        _progress.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "fetchAll: checking session...")
                val sessionValid = SakaiApiClient.checkSession()
                Log.d(TAG, "fetchAll: session valid = $sessionValid")
                if (!sessionValid) {
                    _isLoggedIn.value = false
                    _isLoading.value = false
                    return@launch
                }

                Log.d(TAG, "fetchAll: fetching assignments...")
                val results = SakaiApiClient.fetchAllAssignments { completed, total ->
                    _progress.value = Pair(completed, total)
                }

                val newAssignments = SakaiApiClient.buildAssignments(results)

                // Save to database (preserves checked state)
                dao.replaceAll(newAssignments)
                _assignments.value = dao.getAll()
                _lastRefreshed.value = System.currentTimeMillis()
                _progress.value = null

                // Schedule notifications
                NotificationHelper.scheduleNotifications(getApplication(), newAssignments)

            } catch (e: Exception) {
                Log.e(TAG, "fetchAll error", e)
                _errorMessage.value = e.localizedMessage ?: "エラーが発生しました"
            }
            _isLoading.value = false
        }
    }

    // MARK: - Toggle check

    fun toggleChecked(assignment: Assignment) {
        viewModelScope.launch {
            val newChecked = !assignment.isChecked
            dao.updateChecked(assignment.compositeKey, newChecked)
            _assignments.value = _assignments.value.map {
                if (it.compositeKey == assignment.compositeKey) it.copy(isChecked = newChecked) else it
            }
        }
    }

    // MARK: - Login state

    fun setLoggedIn(loggedIn: Boolean) {
        _isLoggedIn.value = loggedIn
    }

    // MARK: - Logout

    fun logout() {
        viewModelScope.launch {
            dao.deleteAll()
            _assignments.value = emptyList()
            _lastRefreshed.value = null
            _isLoggedIn.value = false
            com.kulms.android.data.remote.WebViewFetcher.clearData()
        }
    }

    // MARK: - Last refreshed text

    val lastRefreshedText: String
        get() {
            val last = _lastRefreshed.value ?: return ""
            val ago = ((System.currentTimeMillis() - last) / 60000).toInt()
            return if (ago < 1) "最終更新: たった今" else "最終更新: ${ago}分前"
        }
}
