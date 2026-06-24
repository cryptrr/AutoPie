package com.autopi.use_case

import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandResult
import com.autopi.autopieapp.data.ExecAndCommand
import com.autopi.autopieapp.data.ExecType
import com.autopi.autopieapp.data.InputParsedData
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.utils.Utils
import com.autopi.utils.toCommandResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.net.URL

class RunCommandForUrl(private val processManagerService: ProcessManagerService) {
    suspend operator fun invoke(
        item: CommandModel,
        currentLink: String,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ): Flow<CommandResult> {

        return flow {
            Timber.d("runCommandForUrl")


            val inputUrl = URL(currentLink)

            val host = inputUrl.host

            val filename = inputUrl.file

            val path = processManagerService.getCommandWorkingDirectory(item.path)

            val execFilePath = processManagerService.getAutoPiePackagePath(item.exec)

            val (execType, fullExecPath, resultCommand) = when {
                File(item.exec).isAbsolute -> {
                    Timber.d("Using package absolute path")
                    ExecAndCommand(ExecType.ABSOLUTE_PATH, item.exec, "\"${item.command}\"")
                }

                File(execFilePath).isFile -> {
                    Timber.d("Using autopie package")
                    //For packages installed inside autosec/bin
                    ExecAndCommand(ExecType.AUTOPIE_PACKAGE, execFilePath, "\"${item.command}\"")
                }

                else -> {
                    //Base case fallback to terminals installed packages such as busybox packages.
                    Timber.d("Using shell installed program")
                    ExecAndCommand(ExecType.SHELL_INSTALLED, item.exec, item.command)
                }
            }

            val useQuotes = execType != ExecType.SHELL_INSTALLED
            val isShellScript = Utils.isShellScript(File(fullExecPath))
            val usePython = Utils.isZipFile(File(fullExecPath))


            val inputParsedData = mutableListOf<InputParsedData>().also {
                it.add(
                    InputParsedData(
                        name = "INPUT_FILE",
                        value = if (useQuotes) "\"$currentLink\"" else currentLink
                    )
                )
                it.add(
                    InputParsedData(
                        name = "INPUT_URL",
                        value = if (useQuotes) "\"$currentLink\"" else currentLink
                    )
                )
                it.add(InputParsedData(name = "HOST", value = if (useQuotes) "\"$host\"" else host))
                it.add(
                    InputParsedData(
                        name = "FILENAME",
                        value = if (useQuotes) "\"$filename\"" else filename
                    )
                )
                it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
            }

            val quotedCommandExtraInputs = commandExtraInputs.map {
                it
            }



            Timber.d("Command to run: ${item.exec} ${resultCommand}")



            val processResult = if(item.command.startsWith("#@INTERACTIVE")){
                processManagerService.runCommandInTermuxShell(
                    item,
                    fullExecPath,
                    resultCommand,
                    path,
                    inputParsedData,
                    if (execType == ExecType.SHELL_INSTALLED) quotedCommandExtraInputs else commandExtraInputs,
                    currentLink,
                    processId,
                    JobType.URL,
                    usePython,
                    isShellScript
                )
            }else{
                processManagerService.runCommandForShareWithEnv2(
                    item,
                    fullExecPath,
                    resultCommand,
                    path,
                    inputParsedData,
                    if (execType == ExecType.SHELL_INSTALLED) quotedCommandExtraInputs else commandExtraInputs,
                    currentLink,
                    processId,
                    JobType.URL,
                    usePython,
                    isShellScript
                )
            }

            val result = processResult.toCommandResult(JobType.URL, currentLink)

            emit(result)
        }
    }

}
