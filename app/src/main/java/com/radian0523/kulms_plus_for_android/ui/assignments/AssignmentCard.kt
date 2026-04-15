package com.radian0523.kulms_plus_for_android.ui.assignments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radian0523.kulms_plus_for_android.data.model.Assignment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignmentCard(
    assignment: Assignment
) {
    val context = LocalContext.current
    val urgencyColor = parseColor(assignment.urgency.colorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Card body
        Column(modifier = Modifier.weight(1f)) {
            // Course name pill + quiz badge
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = assignment.courseName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = urgencyColor,
                    modifier = Modifier
                        .background(urgencyColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                if (assignment.itemType == "quiz") {
                    Text(
                        text = "テスト",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF9C27B0),
                        modifier = Modifier
                            .background(Color(0xFF9C27B0).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Title (tappable)
            Text(
                text = assignment.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(assignment.url))
                        context.startActivity(intent)
                    }
            )

            // Deadline + remaining
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = assignment.deadlineText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (assignment.remainingText.isNotEmpty()) {
                    val remainingColor = when (assignment.urgency) {
                        Assignment.Urgency.OVERDUE, Assignment.Urgency.DANGER -> Color(0xFFE85555)
                        Assignment.Urgency.WARNING -> Color(0xFFD7AA57)
                        Assignment.Urgency.SUCCESS -> Color(0xFF62B665)
                        Assignment.Urgency.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = assignment.remainingText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = remainingColor
                    )
                }
            }

            // Status badge
            if (assignment.isSubmitted) {
                val statusLabel = when {
                    assignment.status.contains("評定済") || assignment.status.lowercase().contains("graded") || assignment.status.contains("採点済") -> "評定済"
                    assignment.status.contains("提出済") || assignment.status.lowercase().contains("submitted") -> "提出済"
                    else -> assignment.status
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

fun parseColor(hex: String): Color {
    val colorString = hex.removePrefix("#")
    return Color(android.graphics.Color.parseColor("#$colorString"))
}
