package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.containsValidHttpUrl
import com.autosec.pie.utils.containsValidUrl
import com.autosec.pie.utils.extractFirstUrl
import com.autosec.pie.utils.isValidUrl
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class RunShareCommand() {
    suspend operator fun invoke(item: CommandModel, currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<Pair<Boolean, String>> {
        val inputDir = fileUris.firstOrNull()?.let { File(it) }

        val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

        Timber.d("currentLink: $currentLink, fileUris: $fileUris")

        when {
            inputDir?.isDirectory == true -> {
                Timber.d("directory detected")
                return  useCases.runShareCommandForDirectory(item,inputDir, commandExtraInputs, processId)
            }
            currentLink.isValidUrl() -> {
                Timber.d("Is a valid url")
                return useCases.runShareCommandForUrl(item, currentLink!!, fileUris, commandExtraInputs, processId)
            }

            fileUris.isNotEmpty() -> {
                Timber.d("file uris not empty")
                return useCases.runShareCommandForFiles(item, currentLink, fileUris, commandExtraInputs, processId)
            }

            currentLink?.isNotEmpty() == true -> {
                Timber.d("text is present")

                return useCases.runShareCommandForText(item, currentLink, fileUris, commandExtraInputs, processId)
            }

            //Standalone Command Runner
            else -> {
                Timber.d("No text or files present")

                return useCases.runStandaloneCommand(item, commandExtraInputs, processId)
            }
        }
    }
}
