package com.autosec.pie.autopieapp.domain.model

import com.autosec.pie.autopieapp.data.CommandType
import kotlinx.serialization.Serializable


@Serializable
data class GenericResponseDTO<T>(
    val ok: Boolean,
    val data: T,
)

@Serializable
data class CloudCommandsListDto(
    val kind: String,
    val items: List<CloudCommandModel>,
    val cursor: String,
    val hasNext: Boolean
)

@Serializable
data class CloudCommandModel(
    override val id: String,
    override val type: CommandType,
    override val name: String,
    override val directory: String,
    override val command: String,
    override val packageUniqueName: String,
    override val deleteSourceFile: Boolean? = false,
    override val description: String,
    val extrasRequired : Boolean? = false,
    override val extras: List<CloudCommandExtra>? = null
) : CloudCommandInterface



interface CloudCommandInterface {
    val id: String
    val type: CommandType
    val name: String
    val directory: String
    val command: String
    val description: String
    val packageUniqueName: String
    val deleteSourceFile: Boolean?
    val extras: List<CloudCommandExtra>?
}

@Serializable
data class CloudCommandExtra(
    val id: String,
    val name: String = "",
    val type: String = "",
    val default: String = "",
    val description: String = "",
    val defaultBoolean: Boolean = true,
    val selectableOptions: List<String> = emptyList()
)

