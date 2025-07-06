package com.autosec.pie.use_case

import android.os.Environment
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.utils.Utils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class RunShareCommandForFiles(private val processManagerService: ProcessManagerService){
    suspend operator fun invoke(
        item: CommandModel,
        currentLink: String?,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ) : Flow<Pair<Boolean, String>> {
        Timber.d("runShareCommandForFiles")


        return flow{
            val currentItems = fileUris

            if (item.command.contains("{INPUT_FILES}")) {

                Timber.d("Multiple Input files detected")


                val replacedString = item.command



                Timber.d("Replaced String $replacedString")

                val execFilePath =
                    Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                val fullExecPath = when{
                    File(item.exec).isAbsolute -> {
                        item.exec
                    }
                    File(execFilePath).exists() -> {
                        //For packages installed inside autosec/bin
                        execFilePath
                    }
                    else -> {
                        //Base case fallback to terminal installed packages such as busybox packages.
                        item.exec
                    }
                }

                val isShellScript = Utils.isShellScript(File(fullExecPath))
                val usePython = Utils.isZipFile(File(fullExecPath))

                val inputFiles = if(usePython){
                    currentItems.joinToString(" "){"'${it}'"}.replace("''","'").replace("'", "\'")
                }else{
                    currentItems.joinToString(" ")
                }

                val parsedPath = Path(currentItems.firstOrNull() ?: "")

                val inputParsedData = mutableListOf<InputParsedData>().also {
                    it.add(InputParsedData(name = "INPUT_FILES", value = "$inputFiles"))
                    it.add(InputParsedData(name = "INPUT_FILE", value = if(usePython) "\"${parsedPath.absolutePathString()}\"" else parsedPath.absolutePathString()))
                    it.add(InputParsedData(name = "FILENAME", value = if(usePython) "\"${parsedPath.fileName}\"" else "${parsedPath.fileName}"))
                    it.add(InputParsedData(name = "DIRECTORY", value = if(usePython) "\"${parsedPath.parent}\"" else "${parsedPath.parent}"))
                    it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(usePython) "\"${parsedPath.nameWithoutExtension}\"" else parsedPath.nameWithoutExtension))
                    it.add(InputParsedData(name = "FILE_EXT", value =  if(usePython) "\"${parsedPath.extension}\"" else parsedPath.extension))
                    it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                }

                Timber.d("fullExecPath : $fullExecPath")
                Timber.d("Use Python : $usePython")

                val resultString = if(usePython) "\"${replacedString}\"" else replacedString

                Timber.d("Result Command: $resultString")

                val success = processManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                    inputParsedData,commandExtraInputs,processId, usePython, isShellScript)


                emit(Pair(success, fileUris.toString()))


            } else {

                Timber.d("Single input file")

                currentItems.map { path ->

                    val replacedString = item.command

                    val parsedPath = Path(path)

                    Timber.d("Parsed path: $parsedPath")


                    val execFilePath =
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                    val fullExecPath = when{
                        File(item.exec).isAbsolute -> {
                            item.exec
                        }
                        File(execFilePath).exists() -> {
                            //For packages installed inside autosec/bin
                            execFilePath
                        }
                        else -> {
                            //Base case fallback to terminal installed packages such as busybox packages.
                            item.exec
                        }
                    }

                    val isShellScript = Utils.isShellScript(File(fullExecPath))
                    val usePython = Utils.isZipFile(File(fullExecPath))

                    val inputParsedData = mutableListOf<InputParsedData>().also {
                        it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.map {item -> "\"$item\"" }.joinToString(" ")))
                        it.add(InputParsedData(name = "INPUT_FILE", value = if(usePython) "\"${parsedPath.absolutePathString()}\"" else parsedPath.absolutePathString()))
                        it.add(InputParsedData(name = "FILENAME", value = if(usePython) "\"${parsedPath.fileName}\"" else "${parsedPath.fileName}"))
                        it.add(InputParsedData(name = "DIRECTORY", value = if(usePython) "\"${parsedPath.parent}\"" else "${parsedPath.parent}"))
                        it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(usePython) "\"${parsedPath.nameWithoutExtension}\"" else parsedPath.nameWithoutExtension))
                        it.add(InputParsedData(name = "FILE_EXT", value =  if(usePython) "\"${parsedPath.extension}\"" else parsedPath.extension))
                        it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                    }


                    Timber.d("Replaced String $replacedString")

                    val resultString = "\"${replacedString}\""

                    val success = processManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                        inputParsedData,commandExtraInputs,processId, usePython, isShellScript)


                    emit(Pair(success, path))

                }

            }
        }
    }
}