package com.autosec.pie.autopieapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity
data class CommandHistoryEntity(
    @PrimaryKey val id: String,
    val commandModelId: String,
    val exec: String,
    val commandExtraInputs: List<CommandExtraInputEntity>,
    val success: Boolean,
    val currentLink: String?,
    val fileUris: List<String>?,
    val processId: Int
)

data class CommandExtraInputEntity(
    @PrimaryKey val id: String,
    val name: String,
    val default: String,
    val value: String,
    val type: String,
    val defaultBoolean: Boolean,
    val description: String
)

object RoomTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromCommandExtraInputList(value: List<CommandExtraInputEntity>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toCommandExtraInputList(value: String?): List<CommandExtraInputEntity>? {
        val type = object : TypeToken<List<CommandExtraInputEntity>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}

fun CommandExtraInput.toEntity(): CommandExtraInputEntity {
    return CommandExtraInputEntity(
        id = this.id,
        name = this.name,
        default = this.default,
        value = this.value,
        type = this.type,
        defaultBoolean = this.defaultBoolean,
        description = this.description
    )
}

fun CommandExtraInputEntity.toInputs(): CommandExtraInput {
    return CommandExtraInput(
        id = this.id,
        name = this.name,
        default = this.default,
        value = this.value,
        type = this.type,
        defaultBoolean = this.defaultBoolean,
        description = this.description
    )
}