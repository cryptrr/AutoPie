package com.autosec.pie.data


data class ShareInputs(
    val currentLink: String? = "",
    val fileUris: List<String> = emptyList()
)