package com.autosec.pie.autopieapp.presentation.viewModels

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.autosec.pie.autopieapp.data.InstalledPackageModel
import com.autosec.pie.use_case.AutoPieUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import kotlin.getValue

class InstalledPackagesViewModel(private val application: Application) : AndroidViewModel(application) {

    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

    var installedPackages = MutableStateFlow<List<InstalledPackageModel>>(emptyList())

    var filteredPackages = MutableStateFlow<List<InstalledPackageModel>>(emptyList())

    var searchQuery = mutableStateOf("")

    init {
        getPackages()
    }


    fun getPackages(){
        val packages = readPackages()

        val packagesModels = packages.map { InstalledPackageModel(name = it.name, path = it.path, version = "0.2.1", hasUpdate = false) }

        installedPackages.update {
            packagesModels
        }
        filteredPackages.update {
            packagesModels
        }
    }

    fun search(query: String) {

        Timber.d("Searching ${query}")

        filteredPackages.update {
            installedPackages.value.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

    }

    private fun readPackages(): List<File> {

        try {

            val packages = useCases.getInstalledPackages(application.filesDir)

            return packages

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