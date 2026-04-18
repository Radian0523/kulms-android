package com.radian0523.kulms_plus_for_android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.concurrent.TimeUnit

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey val compositeKey: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val url: String,
    val deadline: Long?,       // epoch millis, null = no deadline
    val status: String,
    val grade: String,
    val isChecked: Boolean,
    val cachedAt: Long,        // epoch millis
    val itemType: String,      // "assignment" or "quiz"
    val entityId: String,
    val closeTime: Long? = null // epoch millis, Accept Until
) {
    enum class Urgency(val sortOrder: Int, val label: String, val colorHex: String) {
        OVERDUE(0, "期限切れ", "#e85555"),
        DANGER(1, "緊急", "#e85555"),
        WARNING(2, "5日以内", "#d7aa57"),
        SUCCESS(3, "14日以内", "#62b665"),
        OTHER(4, "その他", "#777777");
    }

    val urgency: Urgency
        get() {
            val dl = deadline ?: return Urgency.OTHER
            val diff = dl - System.currentTimeMillis()
            return when {
                diff < 0 -> Urgency.OVERDUE
                diff < TimeUnit.HOURS.toMillis(24) -> Urgency.DANGER
                diff < TimeUnit.DAYS.toMillis(5) -> Urgency.WARNING
                diff < TimeUnit.DAYS.toMillis(14) -> Urgency.SUCCESS
                else -> Urgency.OTHER
            }
        }

    val isSubmitted: Boolean
        get() {
            val s = status.lowercase()
            return s.contains("提出済") || s.contains("submitted")
                    || s.contains("再提出") || s.contains("resubmitted")
                    || s.contains("評定済") || s.contains("graded") || s.contains("採点済")
                    || s.contains("返却") || s.contains("returned")
        }

    val remainingText: String
        get() {
            val dl = deadline ?: return ""
            val now = System.currentTimeMillis()
            val diff = dl - now
            if (diff < 0) {
                // 締切過ぎ: closeTime が未過ぎなら再提出受付期間
                if (closeTime != null && closeTime > now) return "再提出受付期間"
                return "期限切れ"
            }
            val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
            val hours = (TimeUnit.MILLISECONDS.toHours(diff) % 24).toInt()
            val mins = (TimeUnit.MILLISECONDS.toMinutes(diff) % 60).toInt()
            if (days > 0) return "残り${days}日${hours}時間${mins}分"
            if (hours > 0) return "残り${hours}時間${mins}分"
            return "残り${mins}分"
        }

    val deadlineText: String
        get() {
            val dl = deadline ?: return "-"
            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.JAPAN)
            return sdf.format(java.util.Date(dl))
        }
}
