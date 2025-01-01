package com.autosec.pie.domain.model

data class FileUploadGenericDto(
    val filename: String,
    val binaryData: ByteArray,
    val contentType: String,
    val tags: String = "",
    val description: String = "",
    val isPrivate: Boolean = false
) {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileUploadGenericDto

        if (filename != other.filename) return false
        if (!binaryData.contentEquals(other.binaryData)) return false
        if (contentType != other.contentType) return false
        if (tags != other.tags) return false
        if (description != other.description) return false
        if (isPrivate != other.isPrivate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + binaryData.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + isPrivate.hashCode()
        return result
    }
}