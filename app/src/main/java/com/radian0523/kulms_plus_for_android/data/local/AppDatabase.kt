package com.radian0523.kulms_plus_for_android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.radian0523.kulms_plus_for_android.data.model.Assignment

@Database(entities = [Assignment::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assignmentDao(): AssignmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assignments ADD COLUMN allowResubmission INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kulms.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
