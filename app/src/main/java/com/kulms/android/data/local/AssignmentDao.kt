package com.kulms.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kulms.android.data.model.Assignment
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
        // Preserve checked state
        val checkedKeys = getAll().filter { it.isChecked }.map { it.compositeKey }.toSet()
        deleteAll()
        insertAll(assignments.map {
            if (checkedKeys.contains(it.compositeKey)) it.copy(isChecked = true) else it
        })
    }
}
