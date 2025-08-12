package com.autosec.pie.autopieapp.data.dbService

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.RoomTypeConverters
import com.autosec.pie.autopieapp.data.UserTagEntity

@Database(entities = [CommandHistoryEntity::class, UserTagEntity::class], version = 4)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun userTagsDao(): UserTagsDao
}

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM CommandHistoryEntity")
    fun getAll(): List<CommandHistoryEntity>

    @Query("SELECT * FROM CommandHistoryEntity WHERE commandModelId IS :commandName ORDER BY id DESC")
    fun getAllWithName(commandName: String): List<CommandHistoryEntity>

    @Query("""
        SELECT exec
            FROM (
                SELECT *
                FROM CommandHistoryEntity
                WHERE exec IS NOT NULL
                ORDER BY id DESC
            )
        GROUP BY exec
        LIMIT :count;
    """)
    fun getLatestUsedPackages(count: Int): List<String>

    @Query("SELECT * FROM CommandHistoryEntity WHERE id LIKE :id LIMIT 1")
    fun findById(id: String): CommandHistoryEntity

    @Insert
    fun insertAll(vararg commandHistory: CommandHistoryEntity)

    @Delete
    fun delete(commandHistory: CommandHistoryEntity)
}

@Dao
interface UserTagsDao {
    @Query("SELECT * FROM UserTagEntity")
    fun getAll(): List<UserTagEntity>

    @Insert
    fun insertAll(tags: List<UserTagEntity>)

    @Delete
    fun delete(tag: UserTagEntity)
}

