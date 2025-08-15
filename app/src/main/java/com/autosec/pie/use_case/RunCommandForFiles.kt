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
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class RunCommandForFiles(private val processManagerService: ProcessManagerService){
    operator fun invoke(
        item: CommandModel,
        currentLink: String?,
        fileUris: List<String>,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        processId: Int
    ) : Flow<CommandResult> {
        Timber.d("runCommandForFiles")


        return flow{
            val currentItems = fileUris

            if (item.command.contains("{INPUT_FILES}")) {

                Timber.d("Multiple Input files detected")


                val replacedString = item.command


                Timber.d("Replaced String $replacedString")

                val execFilePath =
                    Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec
                val path = Path(Environment.getExternalStorageDirectory().absolutePath, item.path).absolutePathString()


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

                val inputFiles = if(usePython){
                    currentItems.joinToString(" "){"'${it}'"}.replace("''","'").replace("'", "\'")
                }else{
                    currentItems.joinToString(" ")
                }

                val parsedPath = Path(currentItems.firstOrNull() ?: "")

                val inputParsedData = mutableListOf<InputParsedData>().also {
                    it.add(InputParsedData(name = "INPUT_FILES", value = "$inputFiles"))
                    it.add(InputParsedData(name = "INPUT_FILE", value = if(useQuotes) "\"${parsedPath.absolutePathString()}\"" else parsedPath.absolutePathString()))
                    it.add(InputParsedData(name = "FILENAME", value = if(useQuotes) "\"${parsedPath.fileName}\"" else "${parsedPath.fileName}"))
                    it.add(InputParsedData(name = "DIRECTORY", value = if(useQuotes) "\"${parsedPath.parent}\"" else "${parsedPath.parent}"))
                    it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(useQuotes) "\"${parsedPath.nameWithoutExtension}\"" else parsedPath.nameWithoutExtension))
                    it.add(InputParsedData(name = "FILE_EXT", value =  if(useQuotes) "\"${parsedPath.extension}\"" else parsedPath.extension))
                    it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                }

                Timber.d("fullExecPath : $fullExecPath")
                Timber.d("Use Python : $usePython")


                Timber.d("Result Command: $resultCommand")

                val processResult = processManagerService.runCommandForShareWithEnv2(item, fullExecPath, resultCommand, path,
                    inputParsedData,commandExtraInputs,fileUris.toString(),processId,  JobType.FILES,usePython, isShellScript)

                val result = processResult.toCommandResult(JobType.FILES, fileUris.toString())

                emit(result)


            } else {

                Timber.d("Single input file")

                currentItems.map { path ->

                    val replacedString = item.command

                    val parsedPath = Path(path)

                    Timber.d("Parsed path: $parsedPath")


                    val execFilePath =
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

                    val dirPath = Path(Environment.getExternalStorageDirectory().absolutePath, item.path).absolutePathString()


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
                        it.add(InputParsedData(name = "INPUT_FILES", value = currentItems.map {item -> "\"$item\"" }.joinToString(" ")))
                        it.add(InputParsedData(name = "INPUT_FILE", value = if(useQuotes) "\"${parsedPath.absolutePathString()}\"" else parsedPath.absolutePathString()))
                        it.add(InputParsedData(name = "FILENAME", value = if(useQuotes) "\"${parsedPath.fileName}\"" else "${parsedPath.fileName}"))
                        it.add(InputParsedData(name = "DIRECTORY", value = if(useQuotes) "\"${parsedPath.parent}\"" else "${parsedPath.parent}"))
                        it.add(InputParsedData(name = "FILENAME_NO_EXT", value = if(useQuotes) "\"${parsedPath.nameWithoutExtension}\"" else parsedPath.nameWithoutExtension))
                        it.add(InputParsedData(name = "FILE_EXT", value =  if(useQuotes) "\"${parsedPath.extension}\"" else parsedPath.extension))
                        it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
                    }


                    Timber.d("Replaced String $replacedString")


                    val processResult = processManagerService.runCommandForShareWithEnv2(item, fullExecPath, resultCommand,dirPath,
                        inputParsedData,commandExtraInputs,fileUris.toString(),processId,  JobType.FILE,usePython, isShellScript)

                    val result = processResult.toCommandResult(JobType.FILE, path)

                    emit(result)

                }

            }
        }
    }
}