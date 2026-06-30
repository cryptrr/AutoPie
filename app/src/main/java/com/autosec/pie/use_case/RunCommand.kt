package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandResult
import com.autopi.utils.isValidUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class RunCommand() {
    suspend operator fun invoke(item: CommandModel, inputText: String?, inputFiles: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<CommandResult> {
        val inputDir = inputFiles.firstOrNull()?.let { File(it) }

        val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)

        Timber.d("inputText: $inputText, inputFiles: $inputFiles")

        when {

            inputDir?.isDirectory == true -> {
                Timber.d("directory detected")
                return  useCases.runCommandForDirectory(item,inputDir, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, inputText, inputFiles, commandExtraInputs, result.success ,processId)
                }
            }
            inputText.isValidUrl() -> {
                Timber.d("Is a valid url")
                return useCases.runCommandForUrl(item, inputText!!, inputFiles, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, inputText, inputFiles, commandExtraInputs, result.success ,processId)
                }
            }

            inputFiles.isNotEmpty() -> {
                Timber.d("file uris not empty")
                return useCases.runCommandForFiles(item, inputText, inputFiles, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, inputText, inputFiles, commandExtraInputs, result.success ,processId)
                }
            }

            inputText?.isNotEmpty() == true -> {
                Timber.d("text is present")

                return useCases.runCommandForText(item, inputText, inputFiles, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, inputText, inputFiles, commandExtraInputs, result.success ,processId)
                }
            }

            //Standalone Command Runner
            else -> {
                Timber.d("No text or files present")

                return useCases.runStandaloneCommand(item, commandExtraInputs, processId).onEach { result ->
                    useCases.addCommandToHistory(item, inputText, inputFiles, commandExtraInputs, result.success ,processId)
                }
            }
        }
    }
}
