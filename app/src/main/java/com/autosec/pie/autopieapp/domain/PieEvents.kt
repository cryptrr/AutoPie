package com.autosec.pie.autopieapp.domain

import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.JobType

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
    data class CommandStarted(val processId: Int,val command: CommandModel, val logFile: String, val input: String, val jobType: JobType) : ViewModelEvent()
    data class CommandCompleted(val processId: Int,val command: CommandModel, val logFile: String) : ViewModelEvent()
    data class CommandFailed(val processId: Int,val command: CommandModel, val logFile: String) : ViewModelEvent()
    data object AuthTokenExpired: ViewModelEvent()

}