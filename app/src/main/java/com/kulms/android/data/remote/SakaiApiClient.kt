package com.kulms.android.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kulms.android.data.model.Assignment
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
        } catch (e: Exception) {
            Log.e(TAG, "fetchQuizzes($siteId) error", e)
            emptyList()
        }
    }

    // MARK: - Individual Assignment Detail

    private suspend fun fetchAssignmentItem(entityId: String): RawAssignmentItem? {
        return try {
            val text = WebViewFetcher.fetch("/direct/assignment/item/$entityId.json")
            gson.fromJson(text, RawAssignmentItem::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "fetchAssignmentItem($entityId) error", e)
            null
        }
    }

    // MARK: - Fetch All

    data class CourseResult(
        val course: Site,
        val assignments: List<AssignmentWithDetail>,
        val quizzes: List<RawQuiz>
    )

    data class AssignmentWithDetail(
        val raw: RawAssignment,
        val submissions: List<RawSubmission>
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

                    // Fetch individual assignment details
                    val enriched = rawAssignments.map { raw ->
                        async {
                            val entityId = raw.assignmentId
                            if (!entityId.isNullOrEmpty()) {
                                val item = fetchAssignmentItem(entityId)
                                AssignmentWithDetail(raw, item?.submissions ?: emptyList())
                            } else {
                                AssignmentWithDetail(raw, emptyList())
                            }
                        }
                    }.awaitAll()

                    synchronized(this@coroutineScope) {
                        completed++
                        onProgress?.invoke(completed, courses.size)
                    }

                    CourseResult(course, enriched, rawQuizzes)
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
        val grade: String?
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
        val dueDate: FlexibleTimestamp?,
        val retractDate: FlexibleTimestamp?,
        val submitted: Boolean?,
        val publishedAssessmentId: Long?
    )

    data class RawAssignmentItem(
        val submissions: List<RawSubmission>?
    )

    data class RawSubmission(
        val graded: Boolean?,
        val grade: String?,
        val userSubmission: Boolean?,
        val submitted: Boolean?,
        val draft: Boolean?,
        val status: String?,
        val dateSubmittedEpochSeconds: Long?
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

            // Process assignments
            for (detail in result.assignments) {
                val raw = detail.raw
                val deadline = raw.dueTime?.epochMillis
                    ?: raw.dueDate?.epochMillis
                    ?: raw.closeTime?.epochMillis

                // Determine status from individual API first
                var status = ""
                var grade = ""
                val submission = detail.submissions.firstOrNull()
                if (submission != null) {
                    if (submission.graded == true) {
                        status = "評定済"
                        grade = submission.grade ?: ""
                    } else if (submission.submitted == true && submission.draft != true) {
                        status = "提出済"
                    } else if (!submission.status.isNullOrEmpty() && submission.status != "未開始") {
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

                val assignUrl = when {
                    raw.entityURL?.startsWith("http") == true -> raw.entityURL
                    raw.entityURL != null -> "${WebViewFetcher.BASE_URL}${raw.entityURL}"
                    else -> "${WebViewFetcher.BASE_URL}/portal/site/${course.id}"
                }

                assignments.add(
                    Assignment(
                        compositeKey = "${course.id}:assignment:${raw.title ?: ""}",
                        courseId = course.id,
                        courseName = course.title,
                        title = raw.title ?: "",
                        url = assignUrl,
                        deadline = deadline,
                        status = status,
                        grade = grade,
                        isChecked = false,
                        cachedAt = now,
                        itemType = "assignment",
                        entityId = raw.assignmentId ?: ""
                    )
                )
            }

            // Process quizzes
            for (quiz in result.quizzes) {
                val deadline = quiz.dueDate?.epochMillis ?: quiz.retractDate?.epochMillis

                var status = ""
                if (quiz.submitted == true) status = "提出済"

                assignments.add(
                    Assignment(
                        compositeKey = "${course.id}:quiz:${quiz.title ?: ""}",
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
                        entityId = quiz.publishedAssessmentId?.toString() ?: ""
                    )
                )
            }
        }

        Log.d(TAG, "buildAssignments: ${assignments.size} total (${assignments.count { it.itemType == "quiz" }} quizzes)")
        return assignments
    }
}
