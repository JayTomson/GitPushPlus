package com.example.data

import androidx.room.Dao
import androidx.room.Database
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

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<GitProject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: GitProject)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Int)
}

@Database(entities = [GitProject::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
