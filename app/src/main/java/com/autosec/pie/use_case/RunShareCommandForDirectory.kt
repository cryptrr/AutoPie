package com.autosec.pie.use_case

import android.os.Environment
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandResult
import com.autosec.pie.autopieapp.data.ExecAndCommand
import com.autosec.pie.autopieapp.data.ExecType
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

class RunShareCommandForDirectory(private val processManagerService: ProcessManagerService){

    suspend operator fun invoke(
        item: CommandModel,
        inputDir: File,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ): Flow<CommandResult> {

        return flow {

            Timber.d("runShareCommandForDirectory")


            val currentItems = inputDir.listFiles()!!


            currentItems.map { path ->


                val execFilePath =
                    Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                val cwdPath = Path(Environment.getExternalStorageDirectory().absolutePath, item.path).absolutePathString()


                val (execType,fullExecPath, resultCommand) = when{
                    File(item.exec).isAbsolute -> {
                        Timber.d("Using package absolute path")
                        ExecAndCommand( ExecType.ABSOLUTE_PATH,item.exec,"\"${item.command}\"")
                    }
                    File(execFilePath).exists() -> {
                        Timber.d("Using autopie package")
                        //For packages installed inside autosec/bin
                        ExecAndCommand( ExecType.AUTOPIE_PACKAGE,execFilePath,"\"${item.command}\"")
                    }
                    else -> {
                        //Base case fallback to terminals installed packages such as busybox packages.
                        Timber.d("Using shell installed program")
                        ExecAndCommand( ExecType.SHELL_INSTALLED,item.exec, item.command)
                    }
                }



                val useQuotes = execType != ExecType.SHELL_INSTALLED
                val isShellScript = Utils.isShellScript(File(fullExecPath))
                val usePython = Utils.isZipFile(File(fullExecPath))

                val inputParsedData = mutableListOf<InputParsedData>().also {
                    it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.map{item -> "\"$item\""}.joinToString(" ")))
                    it.add(InputParsedData(name = "INPUT_FILE", value = if(useQuotes) "\"${path.absolutePath}\"" else path.absolutePath))
                    it.add(InputParsedData(name = "FILENAME", value = if(useQuotes) "\"${path.name}\"" else path.name))
                    it.add(InputParsedData(name = "DIRECTORY", value = if(useQuotes) "\"${path.parent}\"" else path.parent))
                    it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(useQuotes) "\"${path.nameWithoutExtension}\"" else path.nameWithoutExtension))
                    it.add(InputParsedData(name = "FILE_PATH", value = if(useQuotes) "\"${(path.parent ?: "")}\"" else path.parent ?: ""))
                    it.add(InputParsedData(name = "FILE_EXT", value = if(useQuotes) "\"${path.extension}\"" else path.extension))
                    it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                }

                val result = processManagerService.runCommandForShareWithEnv2(item, fullExecPath, resultCommand, cwdPath,
                    inputParsedData,commandExtraInputs,processId, usePython, isShellScript)


                if (result.success) {

                    if (item.deleteSourceFile == true) {
                        processManagerService.deleteFile(path.absolutePath)
                    }

                } else {
                    //autoPieNotification.sendNotification("Command Failed", "${item.name} $inputDir")
                }

                emit(result)

            }

        }

    }

}