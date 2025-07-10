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

class RunShareCommandForDirectory(private val processManagerService: ProcessManagerService){

    suspend operator fun invoke(
        item: CommandModel,
        inputDir: File,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ): Flow<Pair<Boolean, String>> {

        return flow {

            Timber.d("runShareCommandForDirectory")


            val currentItems = inputDir.listFiles()!!


            currentItems.map { path ->

                val resultString = "\"${item.command}\""

                val inputParsedData = mutableListOf<InputParsedData>().also {
                    it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.map{item -> "\"$item\""}.joinToString(" ")))
                    it.add(InputParsedData(name = "INPUT_FILE", value = "\"${path.absolutePath}\""))
                    it.add(InputParsedData(name = "FILENAME", value = "\"${path.name}\""))
                    it.add(InputParsedData(name = "DIRECTORY", value = "\"${path.parent}\""))
                    it.add(InputParsedData(name = "FILENAME_NO_EXT", value = "\"${path.nameWithoutExtension}\""))
                    it.add(InputParsedData(name = "FILE_PATH", value = "\"${(path.parent ?: "")}\""))
                    it.add(InputParsedData(name = "FILE_EXT", value = "\"${path.extension}\""))
                    it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                }

                val quotedCommandExtraInputs = commandExtraInputs.map{ it.copy(value = "\"${it.value}\"") }

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

                val success = processManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                    inputParsedData,quotedCommandExtraInputs,processId, usePython, isShellScript)



                if (success) {

                    if (item.deleteSourceFile == true) {
                        processManagerService.deleteFile(path.absolutePath)
                    }

                } else {
                    //autoPieNotification.sendNotification("Command Failed", "${item.name} $inputDir")
                }

                emit(Pair(success, path.name))

            }

        }

    }

}