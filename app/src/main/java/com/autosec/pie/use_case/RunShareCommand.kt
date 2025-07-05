package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.services.JsonService
import com.autosec.pie.autopieapp.domain.ViewModelError
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

        when {
            inputDir?.isDirectory == true -> {
                return  useCases.runShareCommandForDirectory(item,inputDir, commandExtraInputs, processId)
            }
            currentLink.isValidUrl() -> {
                return useCases.runShareCommandForUrl(item, currentLink!!, fileUris, commandExtraInputs, processId)
            }

            fileUris.isNotEmpty() -> {
                return useCases.runShareCommandForFiles(item, currentLink, fileUris, commandExtraInputs, processId)
            }

            else -> {
                throw ViewModelError.Unknown
            }
        }
    }
}
