package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class GitProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val repoOwner: String,
    val repoName: String,
    val defaultBranch: String = "main",
    val localFolderPath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_commits")
data class LocalCommit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val commitMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val changesJson: String // Serialized list of CommittedFileChange
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<GitProject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: GitProject)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Int)

    // Local commits methods
    @Query("SELECT * FROM local_commits WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getLocalCommitsForProject(projectId: Int): Flow<List<LocalCommit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalCommit(commit: LocalCommit)

    @Query("DELETE FROM local_commits WHERE projectId = :projectId")
    suspend fun clearLocalCommitsForProject(projectId: Int)

    @Delete
    suspend fun deleteLocalCommit(commit: LocalCommit)
}

@Database(entities = [GitProject::class, LocalCommit::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
