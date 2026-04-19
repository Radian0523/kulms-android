package com.radian0523.kulms_plus_for_android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.radian0523.kulms_plus_for_android.data.model.Assignment
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments ORDER BY deadline ASC")
    fun observeAll(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments ORDER BY deadline ASC")
    suspend fun getAll(): List<Assignment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assignments: List<Assignment>)

    @Query("DELETE FROM assignments")
    suspend fun deleteAll()

    @Query("UPDATE assignments SET isChecked = :checked WHERE compositeKey = :key")
    suspend fun updateChecked(key: String, checked: Boolean)

    @Transaction
    suspend fun replaceAll(assignments: List<Assignment>) {
        // Preserve checked state (with legacy key fallback for compositeKey migration)
        val existing = getAll().filter { it.isChecked }
        val checkedKeys = existing.map { it.compositeKey }.toSet()
        // Build legacy key set: old format was "courseId:itemType:title"
        val legacyCheckedKeys = existing.map { "${it.courseId}:${it.itemType}:${it.title}" }.toSet()
        deleteAll()
        insertAll(assignments.map {
            val isChecked = checkedKeys.contains(it.compositeKey)
                    || legacyCheckedKeys.contains("${it.courseId}:${it.itemType}:${it.title}")
            if (isChecked) it.copy(isChecked = true) else it
        })
    }
}
