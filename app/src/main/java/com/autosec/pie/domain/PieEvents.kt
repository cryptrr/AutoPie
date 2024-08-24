package com.autosec.pie.domain

sealed class ViewModelEvent {
    data object InstallingPython : ViewModelEvent()
    data object InstalledPythonSuccessfully : ViewModelEvent()
    data object OpenEditCommandSheet : ViewModelEvent()
    data object RefreshCommandsList : ViewModelEvent()
    data object CloseShareReceiverSheet : ViewModelEvent()
    data object CommandCompleted : ViewModelEvent()
}