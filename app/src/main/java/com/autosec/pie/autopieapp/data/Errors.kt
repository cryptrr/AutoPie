package com.autopi.autopieapp.data

sealed class AutoPieError(msg: String) : Exception(msg) {
    class UnsafeCommandException(msg: String): AutoPieError(msg)
}