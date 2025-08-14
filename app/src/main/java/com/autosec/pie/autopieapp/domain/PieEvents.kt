package com.autosec.pie.autopieapp.domain

import com.autosec.pie.autopieapp.data.CommandModel

sealed class ViewModelEvent {
    data object InstallingPython : ViewModelEvent()
    data object InstalledPythonSuccessfully : ViewModelEvent()
    data object OpenEditCommandSheet : ViewModelEvent()
    data object OpenCloudCommandDetails : ViewModelEvent()
    data object OpenCloudPackageDetails : ViewModelEvent()
    data class OpenCommandDetails(var card: CommandModel) : ViewModelEvent()
    data class OpenCommandHistory(var card: CommandModel) : ViewModelEvent()

    data object RefreshCommandsList : ViewModelEvent()
    data object ObserversConfigChanged : ViewModelEvent()
    data object SharesConfigChanged : ViewModelEvent()
    data object CronConfigChanged : ViewModelEvent()
    data object CloseShareReceiverSheet : ViewModelEvent()
    data class CancelProcess(val processId: Int) : ViewModelEvent()
    data object StopAutoPie : ViewModelEvent()
    data class CommandCompleted(val processId: Int) : ViewModelEvent()
    data class CommandFailed(val processId: Int, val msg: String = "") : ViewModelEvent()
    data object AuthTokenExpired: ViewModelEvent()

}