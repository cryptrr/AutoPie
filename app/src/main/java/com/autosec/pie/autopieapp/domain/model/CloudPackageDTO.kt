package com.autosec.pie.autopieapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CloudPackageListDTO(
    val kind: String,
    val items: List<CloudPackageModel>,
    val cursor: String,
    val hasNext: Boolean
)

@Serializable
data class CloudPackageModel(
    val id: String,
    val createdAt: String,
    val updatedAt: String,
    val name: String,
    val uniqueName: String,
    val isFeatured: Boolean,
    val categories:List<String>,
    val description: String,
    val url: String,
    val isLocal: Boolean,
    val version: String,
    val isActive: Boolean,
)