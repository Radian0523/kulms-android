package com.radian0523.kulms_plus_for_android.store

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radian0523.kulms_plus_for_android.data.local.AppDatabase
import com.radian0523.kulms_plus_for_android.data.model.Assignment
import com.radian0523.kulms_plus_for_android.data.remote.SakaiApiClient
import com.radian0523.kulms_plus_for_android.data.remote.SessionExpiredException
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssignmentViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).assignmentDao()
    private val prefs = application.getSharedPreferences("kulms_settings", android.content.Context.MODE_PRIVATE)

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

    val autoComplete: Boolean
        get() = prefs.getBoolean("autoComplete", true)

    fun setAutoComplete(enabled: Boolean) {
        prefs.edit().putBoolean("autoComplete", enabled).apply()
    }

    fun groupedAssignments(): List<GroupedSection> {
        val all = _assignments.value
        val auto = autoComplete
        // Hide overdue + completed (submitted or checked)
        val visible = all.filter { a ->
            val isCompleted = a.isChecked || (auto && a.isSubmitted)
            !(a.urgency == Assignment.Urgency.OVERDUE && isCompleted)
        }

        val active = visible.filter { !(it.isChecked || (auto && it.isSubmitted)) }
        val completed = visible.filter { it.isChecked || (auto && it.isSubmitted) }

        val sorted = active.sortedBy { it.deadline ?: Long.MAX_VALUE }

        val overdue = sorted.filter { it.urgency == Assignment.Urgency.OVERDUE }
        val danger = sorted.filter { it.urgency == Assignment.Urgency.DANGER }
        val warning = sorted.filter { it.urgency == Assignment.Urgency.WARNING }
        val success = sorted.filter { it.urgency == Assignment.Urgency.SUCCESS }
        val other = sorted.filter { it.urgency == Assignment.Urgency.OTHER }

        val sections = mutableListOf<GroupedSection>()
        if (overdue.isNotEmpty()) sections.add(GroupedSection("overdue", "遅延提出", "#e85555", overdue))
        if (danger.isNotEmpty()) sections.add(GroupedSection("danger", "緊急", "#e85555", danger))
        if (warning.isNotEmpty()) sections.add(GroupedSection("warning", "5日以内", "#d7aa57", warning))
        if (success.isNotEmpty()) sections.add(GroupedSection("success", "14日以内", "#62b665", success))
        if (other.isNotEmpty()) sections.add(GroupedSection("other", "その他", "#777777", other))
        if (completed.isNotEmpty()) sections.add(GroupedSection("completed", "完了済み", "#777777", completed))
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

            } catch (e: SessionExpiredException) {
                // セッション切れ: キャッシュを保護し、既存データを維持する
                Log.w(TAG, "fetchAll: session expired mid-fetch, preserving cache")
                _isLoggedIn.value = false
            } catch (e: Exception) {
                Log.e(TAG, "fetchAll error", e)
                _errorMessage.value = e.localizedMessage ?: "エラーが発生しました"
            }
            _isLoading.value = false
        }
    }

    // MARK: - Reschedule notifications (after settings change)

    fun rescheduleNotifications() {
        val current = _assignments.value
        if (current.isNotEmpty()) {
            NotificationHelper.scheduleNotifications(getApplication(), current)
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
            com.radian0523.kulms_plus_for_android.data.remote.WebViewFetcher.clearData()
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
