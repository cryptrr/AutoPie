package com.autosec.pie.use_case

import android.os.Environment
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandResult
import com.autosec.pie.autopieapp.data.ExecAndCommand
import com.autosec.pie.autopieapp.data.ExecType
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.JobType
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.toCommandResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class RunCommandForUrl(private val processManagerService: ProcessManagerService){
    suspend operator fun invoke(item: CommandModel, currentLink: String, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<CommandResult> {

        return flow {
            Timber.d("runCommandForUrl")


            val inputUrl = URL(currentLink)

            val host = inputUrl.host

            val filename = inputUrl.file

            val path = Path(Environment.getExternalStorageDirectory().absolutePath, item.path).absolutePathString()

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

            val useQuotes = execType != ExecType.SHELL_INSTALLED
            val isShellScript = Utils.isShellScript(File(fullExecPath))
            val usePython = Utils.isZipFile(File(fullExecPath))


            val inputParsedData = mutableListOf<InputParsedData>().also {
                it.add(InputParsedData(name = "INPUT_FILE", value = if(useQuotes) "\"$currentLink\"" else currentLink))
                it.add(InputParsedData(name = "INPUT_URL", value = if(useQuotes) "\"$currentLink\"" else currentLink))
                it.add(InputParsedData(name = "HOST", value = if(useQuotes) "\"$host\"" else host))
                it.add(InputParsedData(name = "FILENAME", value = if(useQuotes) "\"$filename\"" else filename))
                it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
            }


            Timber.d("Command to run: ${item.exec} ${resultCommand}")


            val processResult = processManagerService.runCommandForShareWithEnv2(item, fullExecPath, resultCommand, path ,inputParsedData,commandExtraInputs,currentLink,processId, JobType.URL,usePython, isShellScript)

            val result = processResult.toCommandResult(JobType.URL, currentLink)

            emit(result)
        }
    }

}
