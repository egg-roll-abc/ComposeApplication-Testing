package com.shx.composeapplication.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shx.composeapplication.data.entity.AccountRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountRecordDao {

    @Query(
        """
        SELECT * FROM account_records
        WHERE createdAt >= :startMillis AND createdAt < :endMillis
        ORDER BY createdAt DESC
        """
    )
    fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<AccountRecordEntity>>

    @Query("SELECT * FROM account_records WHERE id = :id")
    suspend fun getById(id: Long): AccountRecordEntity?

    @Insert
    suspend fun insert(record: AccountRecordEntity): Long

    @Update
    suspend fun update(record: AccountRecordEntity)

    @Delete
    suspend fun delete(record: AccountRecordEntity)

    @Query("SELECT * FROM account_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AccountRecordEntity>>

    @Query("DELETE FROM account_records")
    suspend fun deleteAll()
}
