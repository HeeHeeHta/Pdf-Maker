package com.example.data.repository

import com.example.data.local.RecentPdfDao
import com.example.data.local.RecentPdfEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class RecentPdfRepository(private val dao: RecentPdfDao) {
    val recentPdfs: Flow<List<RecentPdfEntity>> = dao.getAllRecentPdfs()

    suspend fun addRecentPdf(
        filePath: String,
        fileName: String,
        pageCount: Int,
        fileSize: Long
    ): Long {
        val entity = RecentPdfEntity(
            filePath = filePath,
            fileName = fileName,
            pageCount = pageCount,
            fileSize = fileSize
        )
        return dao.insertRecentPdf(entity)
    }

    suspend fun deleteRecentPdf(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteByFilePath(filePath: String) {
        dao.deleteByFilePath(filePath)
    }

    // Cleans up entities whose files no longer exist on disk
    suspend fun syncWithFileSystem(recentList: List<RecentPdfEntity>) {
        recentList.forEach { entity ->
            val file = File(entity.filePath)
            if (!file.exists()) {
                dao.deleteById(entity.id)
            }
        }
    }
}
