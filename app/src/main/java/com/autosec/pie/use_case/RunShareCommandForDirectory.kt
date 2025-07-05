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

class RunShareCommandForDirectory {

    suspend operator fun invoke(
        item: CommandModel,
        inputDir: File,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ): Flow<Pair<Boolean, String>> {

        return flow {

            Timber.d("runShareCommandForDirectory")


            val currentItems = inputDir.listFiles()!!

            val startTime = System.currentTimeMillis()


            currentItems.map { path ->

                val resultString = "\"${item.command}\""

                val inputParsedData = mutableListOf<InputParsedData>().also {
                    it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.joinToString(" ")))
                    it.add(InputParsedData(name = "INPUT_FILE", value = "'${path.absolutePath}'"))
                    it.add(InputParsedData(name = "FILENAME", value = "'${path.name}'"))
                    it.add(InputParsedData(name = "DIRECTORY", value = "'${path.parent}'"))
                    it.add(InputParsedData(name = "FILENAME_NO_EXT", value = "'${path.nameWithoutExtension}'"))
                    it.add(InputParsedData(name = "FILE_PATH", value = "'${(path.parent ?: "")}'"))
                    it.add(InputParsedData(name = "FILE_EXT", value = "'${path.extension}'"))
                    it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                }


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

                val usePython = !Utils.isShellScript(File(fullExecPath))

                val success = ProcessManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,
                    inputParsedData,commandExtraInputs,processId, usePython)



                if (success) {

                    if (item.deleteSourceFile == true) {
                        ProcessManagerService.deleteFile(path.absolutePath)
                    }

                } else {
                    //autoPieNotification.sendNotification("Command Failed", "${item.name} $inputDir")
                }

                emit(Pair(success, path.name))

            }

        }

    }

}