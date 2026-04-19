package com.radian0523.kulms_plus_for_android.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.radian0523.kulms_plus_for_android.data.model.Assignment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object SakaiApiClient {
    private const val TAG = "SakaiApiClient"
    private val gson = Gson()
    private val concurrentLimit = Semaphore(4)

    // MARK: - Session

    suspend fun checkSession(): Boolean {
        return try {
            val text = WebViewFetcher.fetch("/direct/site.json?_limit=1")
            val collection = gson.fromJson(text, SiteCollection::class.java)
            val valid = (collection?.siteCollection?.size ?: 0) > 0
            Log.d(TAG, "checkSession: ${collection?.siteCollection?.size ?: 0} sites -> $valid")
            valid
        } catch (e: Exception) {
            Log.e(TAG, "checkSession error", e)
            false
        }
    }

    // MARK: - Courses

    suspend fun fetchCourses(): List<Site> {
        val text = WebViewFetcher.fetch("/direct/site.json?_limit=200")
        val collection = gson.fromJson(text, SiteCollection::class.java)
        Log.d(TAG, "fetchCourses: ${collection.siteCollection.size} sites total")
        val filtered = collection.siteCollection.filter {
            it.type == "course" || it.type == "project"
        }
        Log.d(TAG, "fetchCourses: ${filtered.size} courses after filter")
        return filtered
    }

    // MARK: - Assignments

    private suspend fun fetchAssignments(siteId: String): List<RawAssignment> {
        return try {
            val text = WebViewFetcher.fetch("/direct/assignment/site/$siteId.json")
            val collection = gson.fromJson(text, AssignmentCollection::class.java)
            Log.d(TAG, "fetchAssignments($siteId): ${collection.assignmentCollection.size} items")
            collection.assignmentCollection
        } catch (e: SessionExpiredException) {
            throw e // セッション切れは上位に伝播
        } catch (e: Exception) {
            Log.e(TAG, "fetchAssignments($siteId) error", e)
            emptyList()
        }
    }

    // MARK: - Quizzes

    private suspend fun fetchQuizzes(siteId: String): List<RawQuiz> {
        return try {
            val text = WebViewFetcher.fetch("/direct/sam_pub/context/$siteId.json")
            val collection = gson.fromJson(text, QuizCollection::class.java)
            Log.d(TAG, "fetchQuizzes($siteId): ${collection.samPubCollection.size} items")
            collection.samPubCollection
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchQuizzes($siteId) error", e)
            emptyList()
        }
    }

    // MARK: - Assignment Tool URL (via pages.json)

    private suspend fun fetchAssignmentToolUrl(siteId: String): String? {
        return try {
            val text = WebViewFetcher.fetch("/direct/site/$siteId/pages.json")
            val pages = gson.fromJson(text, Array<PageEntry>::class.java) ?: emptyArray()
            for (page in pages) {
                for (tool in page.tools ?: emptyList()) {
                    if (tool.toolId == "sakai.assignment.grades" && tool.id != null) {
                        return "${WebViewFetcher.BASE_URL}/portal/site/$siteId/tool/${tool.id}"
                    }
                }
            }
            null
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchAssignmentToolUrl($siteId) error", e)
            null
        }
    }

    // MARK: - Individual Assignment Detail

    private suspend fun fetchAssignmentItem(entityId: String): RawAssignmentItem? {
        return try {
            val text = WebViewFetcher.fetch("/direct/assignment/item/$entityId.json")
            gson.fromJson(text, RawAssignmentItem::class.java)
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchAssignmentItem($entityId) error", e)
            null
        }
    }

    // MARK: - Fetch All

    data class CourseResult(
        val course: Site,
        val assignments: List<AssignmentWithDetail>,
        val quizzes: List<RawQuiz>,
        val assignmentToolUrl: String? = null
    )

    data class AssignmentWithDetail(
        val raw: RawAssignment,
        val submissions: List<RawSubmission>,
        val itemAllowResubmission: Boolean? = null
    )

    // pages.json models for fetching assignment tool URL
    data class PageEntry(
        val tools: List<ToolEntry>?
    )
    data class ToolEntry(
        val id: String?,
        val toolId: String?
    )

    suspend fun fetchAllAssignments(
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): List<CourseResult> = coroutineScope {
        val courses = fetchCourses()
        Log.d(TAG, "fetchAllAssignments: ${courses.size} courses found")
        var completed = 0

        courses.map { course ->
            async {
                concurrentLimit.withPermit {
                    val rawAssignments = fetchAssignments(course.id)
                    val rawQuizzes = fetchQuizzes(course.id)

                    // Fetch assignment tool URL via pages.json
                    val toolUrl = if (rawAssignments.isNotEmpty()) {
                        fetchAssignmentToolUrl(course.id)
                    } else null

                    // Fetch individual assignment details
                    val enriched = rawAssignments.map { raw ->
                        async {
                            val entityId = raw.assignmentId
                            if (!entityId.isNullOrEmpty()) {
                                val item = fetchAssignmentItem(entityId)
                                AssignmentWithDetail(raw, item?.submissions ?: emptyList(), item?.allowResubmission)
                            } else {
                                AssignmentWithDetail(raw, emptyList())
                            }
                        }
                    }.awaitAll()

                    synchronized(this@coroutineScope) {
                        completed++
                        onProgress?.invoke(completed, courses.size)
                    }

                    CourseResult(course, enriched, rawQuizzes, toolUrl)
                }
            }
        }.awaitAll()
    }

    // MARK: - API Response Models

    data class SiteCollection(
        @SerializedName("site_collection") val siteCollection: List<Site>
    )

    data class Site(
        val id: String,
        val title: String,
        val type: String?
    )

    data class AssignmentCollection(
        @SerializedName("assignment_collection") val assignmentCollection: List<RawAssignment>
    )

    data class RawAssignment(
        val title: String?,
        val entityURL: String?,
        val dueTime: FlexibleTimestamp?,
        val dueDate: FlexibleTimestamp?,
        val closeTime: FlexibleTimestamp?,
        val submitted: Boolean?,
        val submissionStatus: String?,
        val gradeDisplay: String?,
        val grade: String?,
        val allowResubmission: Boolean?
    ) {
        /** Extract assignment ID from entityURL (e.g. "/direct/assignment/a/{uuid}") */
        val assignmentId: String?
            get() = entityURL?.split("/")?.lastOrNull()
    }

    data class QuizCollection(
        @SerializedName("sam_pub_collection") val samPubCollection: List<RawQuiz>
    )

    data class RawQuiz(
        val title: String?,
        val startDate: FlexibleTimestamp?,
        val dueDate: FlexibleTimestamp?,
        val retractDate: FlexibleTimestamp?,
        val submitted: Boolean?,
        val publishedAssessmentId: Long?
    )

    data class RawAssignmentItem(
        val submissions: List<RawSubmission>?,
        val allowResubmission: Boolean?
    )

    data class RawSubmission(
        val graded: Boolean?,
        val grade: String?,
        val userSubmission: Boolean?,
        val submitted: Boolean?,
        val draft: Boolean?,
        val returned: Boolean?,
        val status: String?,
        val dateSubmittedEpochSeconds: Long?,
        val properties: Map<String, Any?>?
    )

    /**
     * Flexible timestamp that handles Sakai's multiple formats:
     * - Number (epoch milliseconds)
     * - Object with "epochSecond" key
     * - Object with "time" key (epoch milliseconds)
     * - String of epoch milliseconds
     */
    data class FlexibleTimestamp(
        val time: Long?,
        val epochSecond: Long?
    ) {
        val epochMillis: Long?
            get() = when {
                epochSecond != null -> epochSecond * 1000
                time != null -> time
                else -> null
            }
    }

    // MARK: - Build Assignment objects

    fun buildAssignments(results: List<CourseResult>): List<Assignment> {
        val now = System.currentTimeMillis()
        val assignments = mutableListOf<Assignment>()

        for (result in results) {
            val course = result.course
            val courseAssignUrl = result.assignmentToolUrl
                ?: "${WebViewFetcher.BASE_URL}/portal/site/${course.id}"

            // Process assignments
            for (detail in result.assignments) {
                val raw = detail.raw
                val deadline = raw.dueTime?.epochMillis
                    ?: raw.dueDate?.epochMillis
                    ?: raw.closeTime?.epochMillis

                // Determine status from individual API first
                // Sakai status文字列はフローチャート（時間比較含む）に基づく正確な判定。
                // Booleanフィールド（graded, returned等）は前回の提出状態が残るため
                // 単体では状態#11（返却後に作業中）等を正しく判定できない。
                // 参照: kulms-extension/docs/sakai-submission-states.md
                var status = ""
                var grade = ""
                val submission = detail.submissions.firstOrNull()
                if (submission != null) {
                    val subStatus = (submission.status ?: "").lowercase()
                    val statusIndicatesSubmitted =
                        subStatus.contains("提出済") || subStatus.contains("submitted") ||
                        subStatus.contains("再提出") || subStatus.contains("resubmitted") ||
                        subStatus.contains("評定済") || subStatus.contains("graded") ||
                        subStatus.contains("採点済") ||
                        subStatus.contains("返却") || subStatus.contains("returned")

                    if (statusIndicatesSubmitted) {
                        if (submission.graded == true && submission.returned == true) {
                            status = "評定済"
                        } else {
                            status = "提出済"
                        }
                        grade = submission.grade ?: ""
                    } else if (submission.userSubmission == true && submission.draft != true && submission.status.isNullOrEmpty()) {
                        status = "提出済"
                        if (submission.graded == true) grade = submission.grade ?: ""
                    } else if (!submission.status.isNullOrEmpty() && submission.status != "未開始" && submission.status.lowercase() != "not started") {
                        status = submission.status
                    }
                }
                // Fallback to list API data
                if (status.isEmpty()) {
                    if (raw.submitted == true) {
                        status = "提出済"
                    } else if (!raw.submissionStatus.isNullOrEmpty()) {
                        status = raw.submissionStatus
                    }
                }
                if (grade.isEmpty()) {
                    grade = raw.gradeDisplay ?: raw.grade ?: ""
                }

                // allowResubmission: check both list API and individual API
                val allowResubFlag = raw.allowResubmission == true || detail.itemAllowResubmission == true
                val allowResub = if (!allowResubFlag) {
                    false
                } else {
                    val remain = submission?.properties?.get("allow_resubmit_number")
                    remain != "0" && remain != 0
                }

                // compositeKey: entityId-based (matching extension's getCheckedKey)
                val entityId = raw.assignmentId ?: ""
                val compositeKey = if (entityId.isNotEmpty()) entityId else "${course.id}:${raw.title ?: ""}"

                assignments.add(
                    Assignment(
                        compositeKey = compositeKey,
                        courseId = course.id,
                        courseName = course.title,
                        title = raw.title ?: "",
                        url = courseAssignUrl,
                        deadline = deadline,
                        status = status,
                        grade = grade,
                        isChecked = false,
                        cachedAt = now,
                        itemType = "assignment",
                        entityId = entityId,
                        closeTime = raw.closeTime?.epochMillis,
                        allowResubmission = allowResub
                    )
                )
            }

            // Process quizzes (未公開クイズを除外: startDate が未来のものは非表示)
            for (quiz in result.quizzes) {
                val startMs = quiz.startDate?.epochMillis
                if (startMs != null && startMs > now) continue

                val deadline = quiz.dueDate?.epochMillis ?: quiz.retractDate?.epochMillis

                val status = ""

                // compositeKey: entityId-based (matching extension's getCheckedKey)
                val entityId = quiz.publishedAssessmentId?.toString() ?: ""
                val compositeKey = if (entityId.isNotEmpty()) entityId else "${course.id}:${quiz.title ?: ""}"

                assignments.add(
                    Assignment(
                        compositeKey = compositeKey,
                        courseId = course.id,
                        courseName = course.title,
                        title = quiz.title ?: "",
                        url = "${WebViewFetcher.BASE_URL}/portal/site/${course.id}",
                        deadline = deadline,
                        status = status,
                        grade = "",
                        isChecked = false,
                        cachedAt = now,
                        itemType = "quiz",
                        entityId = entityId,
                        closeTime = quiz.retractDate?.epochMillis
                    )
                )
            }
        }

        Log.d(TAG, "buildAssignments: ${assignments.size} total (${assignments.count { it.itemType == "quiz" }} quizzes)")
        return assignments
    }
}
