package com.autosec.pie.use_case

import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandResult
import com.autosec.pie.utils.isValidUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class RunShareCommand() {
    suspend operator fun invoke(item: CommandModel, currentLink: String?, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<CommandResult> {
        val inputDir = fileUris.firstOrNull()?.let { File(it) }

        val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

        Timber.d("currentLink: $currentLink, fileUris: $fileUris")

        when {
            inputDir?.isDirectory == true -> {
                Timber.d("directory detected")
                return  useCases.runShareCommandForDirectory(item,inputDir, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, currentLink, fileUris, commandExtraInputs, result.success ,processId)
                }
            }
            currentLink.isValidUrl() -> {
                Timber.d("Is a valid url")
                return useCases.runShareCommandForUrl(item, currentLink!!, fileUris, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, currentLink, fileUris, commandExtraInputs, result.success ,processId)
                }
            }

            fileUris.isNotEmpty() -> {
                Timber.d("file uris not empty")
                return useCases.runShareCommandForFiles(item, currentLink, fileUris, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, currentLink, fileUris, commandExtraInputs, result.success ,processId)
                }
            }

            currentLink?.isNotEmpty() == true -> {
                Timber.d("text is present")

                return useCases.runShareCommandForText(item, currentLink, fileUris, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, currentLink, fileUris, commandExtraInputs, result.success ,processId)
                }
            }

            //Standalone Command Runner
            else -> {
                Timber.d("No text or files present")

                return useCases.runStandaloneCommand(item, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, currentLink, fileUris, commandExtraInputs, result.success ,processId)
                }
            }
        }
    }
}
