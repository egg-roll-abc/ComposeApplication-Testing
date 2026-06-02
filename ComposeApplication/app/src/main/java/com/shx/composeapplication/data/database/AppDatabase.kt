package com.shx.composeapplication.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shx.composeapplication.data.dao.AccountRecordDao
import com.shx.composeapplication.data.entity.AccountRecordEntity

@Database(
    entities = [AccountRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountRecordDao(): AccountRecordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "accounting_demo.db"
                ).build().also { instance = it }
            }
    }
}
