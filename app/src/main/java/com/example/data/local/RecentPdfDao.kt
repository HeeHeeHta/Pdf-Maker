package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY createdTimestamp DESC")
    fun getAllRecentPdfs(): Flow<List<RecentPdfEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentPdf(pdf: RecentPdfEntity): Long

    @Query("DELETE FROM recent_pdfs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recent_pdfs WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM recent_pdfs")
    suspend fun clearAll()
}
