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

@Database(entities = [CommandHistoryEntity::class], version = 2)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
}

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM CommandHistoryEntity")
    fun getAll(): List<CommandHistoryEntity>

    @Query("SELECT * FROM CommandHistoryEntity WHERE commandModelId IS :commandName")
    fun getAllWithName(commandName: String): List<CommandHistoryEntity>

    @Query("SELECT * FROM CommandHistoryEntity WHERE id LIKE :id LIMIT 1")
    fun findById(id: String): CommandHistoryEntity

    @Insert
    fun insertAll(vararg commandHistory: CommandHistoryEntity)

    @Delete
    fun delete(commandHistory: CommandHistoryEntity)
}