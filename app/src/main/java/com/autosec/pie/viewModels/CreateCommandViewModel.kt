package com.autosec.pie.viewModels

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import org.koin.java.KoinJavaComponent

class CreateCommandViewModel(application: Application) : AndroidViewModel(application) {

    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    val newCommandName = mutableStateOf("")
    val execFile = mutableStateOf("")
    val command = mutableStateOf("")
    val directory = mutableStateOf("")
    val deleteSource = mutableStateOf(false)


}