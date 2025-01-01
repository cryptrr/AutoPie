package com.autosec.pie.domain

sealed class ViewModelEvent {
    data object InstallingPython : ViewModelEvent()
    data object InstalledPythonSuccessfully : ViewModelEvent()
    data object OpenEditCommandSheet : ViewModelEvent()
    data object OpenCloudCommandDetails : ViewModelEvent()
    data object OpenCloudPackageDetails : ViewModelEvent()

    data object RefreshCommandsList : ViewModelEvent()
    data object ObserversConfigChanged : ViewModelEvent()
    data object SharesConfigChanged : ViewModelEvent()
    data object CronConfigChanged : ViewModelEvent()
    data object CloseShareReceiverSheet : ViewModelEvent()
    data class CancelProcess(val processId: Int) : ViewModelEvent()
    data class CommandCompleted(val processId: Int) : ViewModelEvent()
    data object AuthTokenExpired: ViewModelEvent()

}