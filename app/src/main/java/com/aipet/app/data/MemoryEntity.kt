package com.aipet.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "user_memory")
data class UserMemory(
    @PrimaryKey val id: String = "owner",
    val name: String,
    val faceEmbedding: String
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM user_memory WHERE id = 'owner' LIMIT 1")
    suspend fun getOwner(): UserMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOwner(userMemory: UserMemory)
}
