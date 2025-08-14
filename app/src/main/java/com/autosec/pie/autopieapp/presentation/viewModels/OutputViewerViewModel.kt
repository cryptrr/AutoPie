package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.data.services.ForegroundService
import com.autosec.pie.use_case.AutoPieUseCases
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import androidx.core.net.toUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

class OutputViewerViewModel(private val application1: Application) : ViewModel() {



    val main: MainViewModel by inject(MainViewModel::class.java)
    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
    val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)




    private val _logContent = MutableStateFlow<String>("")
    val logContent: StateFlow<String> = _logContent


    private var logJob: Job? = null


    val currentLogPath = mutableStateOf<String?>(null)
    val currentCommandName = mutableStateOf<String>("")

    init {
        try {
            viewModelScope.launch {
                main.eventFlow.collect{
                    when(it){

                        else -> {}
                    }
                }
            }
        }catch (e:Exception){
            Timber.e(e)
        }
    }

    fun getOutputFromFile(path: String){

        path?.let{
            viewModelScope.launch(dispatchers.io){
                try {
                    val inputStream = application1.contentResolver.openInputStream(it.toUri())
                    val contents = inputStream?.bufferedReader().use { it?.readText() }
                    //Timber.d(contents)
                    _logContent.value = contents ?: ""
                }catch (e: Exception){
                    Timber.e(e)
                }

            }
        }

    }


    fun streamFile(path: String) {
        Timber.d("Trying to read log at $path")

        logJob?.cancel() // stop previous stream if running
        logJob = viewModelScope.launch(dispatchers.io) {

            try {
                val file = File(path)
                file.bufferedReader().use { reader -> // closes automatically when this block ends
                    while (isActive) { // ensures loop stops when coroutine is cancelled
                        val line = reader.readLine()
                        if (line != null) {
                            _logContent.update { it + "\n" + line }
                        } else {
                            delay(200)
                        }
                    }
                }

            }catch (e: Exception){
                Timber.e(e)
            }
        }
    }

    fun closeLogStream() {
        logJob?.cancel()
        logJob = null
    }


}