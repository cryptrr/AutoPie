package com.autosec.pie.use_case

import android.os.Environment
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.ExecAndCommand
import com.autosec.pie.autopieapp.data.ExecType
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.utils.Utils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File

class RunStandaloneCommand(private val processManagerService: ProcessManagerService){
    operator fun invoke(item: CommandModel, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<Pair<Boolean, String>> {

        return flow {
            Timber.d("RunStandaloneCommand")

            val execFilePath =
                Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

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

            val inputParsedData = mutableListOf<InputParsedData>().also {
                it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
            }


            val isShellScript = Utils.isShellScript(File(fullExecPath))
            val usePython = Utils.isZipFile(File(fullExecPath))


            Timber.d("Command to run: ${item.exec} $resultCommand")


            val success = processManagerService.runCommandForShareWithEnv(item, fullExecPath, resultCommand, item.path,inputParsedData,commandExtraInputs,processId, usePython, isShellScript)

            emit(Pair(success, item.name))
        }
    }

}