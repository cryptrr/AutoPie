package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.autosec.pie.autopieapp.data.InstalledPackageModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.io.File

class InstalledPackagesViewModel(application: Application) : AndroidViewModel(application) {

    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    var installedPackages = MutableStateFlow<List<InstalledPackageModel>>(emptyList())

    init {
        getPackages()
    }


    fun getPackages(){
        val packages = readPackages()

        val packagesModels = packages.map { InstalledPackageModel(name = it.name, path = it.path, version = "0.2.1", hasUpdate = false) }

        installedPackages.update {
            packagesModels
        }
    }

    private fun readPackages(): List<File> {

        try {
            val folderPath = Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin"

            return getFilesInFolder(folderPath)
        } catch (e: Exception) {
            Timber.e(e)
            return emptyList()
        }
    }

    private fun getFilesInFolder(folderPath: String): List<File> {
        val directory = File(folderPath)
        return if (directory.isDirectory) {
            directory.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

}