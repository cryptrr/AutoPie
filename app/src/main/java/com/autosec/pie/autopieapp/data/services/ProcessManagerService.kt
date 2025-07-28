package com.autosec.pie.autopieapp.data.services

import android.app.Application
import android.os.Environment
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.AutoPieError
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandInterface
import com.autosec.pie.autopieapp.data.CommandResult
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.JobType
import com.autosec.pie.autopieapp.data.ProcessResult
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.utils.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createSymbolicLinkPointingTo

class ProcessManagerService(private val main: MainViewModel, private val dispatchers: DispatcherProvider, private val activity: Application){

    private var shell: Shell? = null

    private var mcpShell: Shell? = null

    private var shells = HashMap<Int, Shell>()

    init {
        main.viewModelScope.launch {
            main.eventFlow.collect {
                when (it) {
                    is ViewModelEvent.CancelProcess -> {
                        CoroutineScope(dispatchers.io).launch {
                            Timber.d("Received processId in event: ${it.processId}")
                            Timber.d("Shells List: ${shells.keys}")

                            val runningShell = shells[it.processId]

                            if (runningShell != null) {
                                runningShell.process.destroyForcibly()

                                Timber.d("Process terminated: ${it.processId}")
                                shells.remove(it.processId)
                            } else {
                                Timber.d("processId not match")
                            }

                            Timber.d("Shells List After: ${shells.keys}")
                        }

                    }

                    else -> {}
                }
            }
        }
    }


    private fun initShell() {
        val shellPath = File(activity.filesDir, "sh").absolutePath

        shell = Shell(
            shellPath,
        )

        Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        val setEnvResult =
            shell?.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        Timber.d(setEnvResult?.output())

    }

    private fun initMCPShell(modulePath: String, host: String, port: String) {
        val shellPath = File(activity.filesDir, "sh").absolutePath

        val envMap = HashMap<String, String>()
        envMap["MCP_SERVER_HOST"] = host
        envMap["MCP_SERVER_PORT"] = port
        envMap["DYNAMIC_MODULES_DIR"] = modulePath

        mcpShell = Shell(
            shellPath,
            envMap
        )

        Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        val setEnvResult =
            mcpShell?.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        Timber.d(setEnvResult?.output())

    }

    private fun getNewShell(): Shell {
        val shellPath = File(activity.filesDir, "sh").absolutePath

        val newShell = Shell(
            shellPath,
        )

        Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        val setEnvResult =
            newShell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        Timber.d(setEnvResult?.output())

        return newShell

    }

    private fun getShell(
        inputParsedData: List<InputParsedData>,
        commandObject: CommandInterface,
        commandExtraInputs: List<CommandExtraInput> = emptyList()
    ): Shell {
        val shellPath = File(activity.filesDir, "sh").absolutePath

        val envMap = HashMap<String, String>()

        for (inputData in inputParsedData) {
            envMap[inputData.name] = inputData.value
        }

        //This is for when "Extra Inputs" are not passed in ie when the CommandExtrasBottomSheet is not opened or edited.
        if (commandExtraInputs.isEmpty()) {
            for (extra in commandObject.extras ?: emptyList()) {
                //Timber.d("Setting extra to defaults: ${extra.name}=${extra.default}")

                //Check if the field is not string type. Then don't do all of this shit with the file paths.
                if(extra.type != "STRING"){
                    envMap[extra.name] = extra.default
                }
                //TEMP FIX for multi user envs where fully qualified paths for extras don't work
                else if(extra.name.endsWith("FILE") || extra.name.endsWith("FOLDER")){
                    if(Path(extra.default).isAbsolute){
                        envMap[extra.name] = extra.default
                    }else{
                        val fullPath = File(Environment.getExternalStorageDirectory(), extra.default).absolutePath
                        envMap[extra.name] = fullPath
                    }
                }
                else if(extra.name.endsWith("FILES")){
                    envMap[extra.name] = extra.default.split(",").map {
                        if(Path(extra.default).isAbsolute){
                            it
                        }else{
                            File(Environment.getExternalStorageDirectory(), it).absolutePath
                        }
                    }.joinToString(",")
                }
                else{
                    envMap[extra.name] = extra.default
                }
            }
        }
        //This is when the command extra inputs are passed. That is when the CommandExtrasBottomSheet is opened, all the extras including the defaults are passed as commandExtraInputs
        else {
            for (extra in commandExtraInputs) {

                if(extra.type != "STRING"){
                    envMap[extra.name] = extra.value
                }
                else if(extra.name.endsWith("FILE") || extra.name.endsWith("FOLDER")){
                    if(Path(extra.default).isAbsolute){
                        envMap[extra.name] = extra.value
                    }else{
                        val fullPath = File(Environment.getExternalStorageDirectory(), extra.value).absolutePath
                        envMap[extra.name] = fullPath
                    }
                }
                else if(extra.name.endsWith("FILES")){
                    envMap[extra.name] = extra.value.split(",").map {
                        if(Path(extra.value).isAbsolute){
                            it
                        }else{
                            File(Environment.getExternalStorageDirectory(), it).absolutePath
                        }
                    }.joinToString(",")
                }
                else{
                    envMap[extra.name] = extra.value
                }
            }
        }

        //Adding the command at last to get the env included result command
        envMap["resultCommand"] = commandObject.command

        Timber.d("ENV MAP: $envMap")

        val shell = Shell(
            shellPath,
            envMap
        )

        Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        val setEnvResult =
            shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        return shell

    }

    fun checkShell(): Boolean {
        try {
            val shellPath = File(activity.filesDir, "sh").absolutePath

            val shell = Shell(
                shellPath,
            )

            val result =
                shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath}")

            shell.shutdown()

            return result.isSuccess
        } catch (e: Exception) {
            return false
        }
    }


    private fun checkForUnsafeCommands(commandObject: CommandInterface, command: String) {
        val unsafePatterns = listOf(
            "rm\\s+-rf\\s+/",
            ":\\(\\)\\{ :\\|: & \\};:",   // Fork bomb
            "dd\\s+if=/dev/zero\\s+of=/dev/sda",
            "chmod\\s+-R\\s+777\\s+/",
            "mkfs\\.ext4\\s+/dev/sda",
            "wget .* -O \\| sh",
            "mv\\s+/.+\\s+/dev/null",
            "echo .* > /proc/sysrq-trigger",
            "iptables\\s+-F",
            "killall\\s+-9\\s+.*",
            "\\breboot\\b",
            "shutdown\\s+-h\\s+now",
            "ln\\s+-s\\s+/bin/busybox\\s+/dev/null",
            "find / -exec rm -rf \\{\\} \\\\;",
            "cp\\s+/bin/busybox\\s+/dev/sda"
        )

        val fullCommand = "${commandObject.exec} ${command}".trim()

        Timber.d("Full Command: $fullCommand")

        val unsafeRegexes = unsafePatterns.map { Regex(it) }

        if (unsafeRegexes.any { it.containsMatchIn(fullCommand) }) {
            throw AutoPieError.UnsafeCommandException("Unsafe command detected: $fullCommand")
        }
    }

    fun runCommandWithEnv(
        commandObject: CommandInterface,
        exec: String,
        command: String,
        cwd: String,
        inputParsedData: List<InputParsedData> = emptyList(),
        usePython: Boolean = true
    ): Boolean {
        try {

            checkForUnsafeCommands(commandObject, command)

            val shell = getShell(inputParsedData, commandObject, emptyList())

            val checkEnvResult = shell.run("cd ${cwd}")

            Timber.d(checkEnvResult.output())

            val fullCommand = if (usePython) "python3.10 $exec $command" else "sh $exec $command"

            Timber.d(fullCommand)

            val result = shell.run(fullCommand)

            val output = result.output()

            Timber.d(output)

            shell.shutdown()

            return result.isSuccess


        }
        catch(e: AutoPieError){
            throw e
        }
        catch (e: Exception) {
            Timber.e(e.toString())

        }

        return false

    }

    fun runCommandForShareWithEnv(
        commandObject: CommandInterface,
        exec: String,
        command: String,
        cwd: String,
        inputParsedData: List<InputParsedData> = emptyList(),
        commandExtraInputs: List<CommandExtraInput>,
        processId: Int,
        usePython: Boolean = true,
        isShellScript: Boolean = false
    ): Boolean {

        try {

            checkForUnsafeCommands(commandObject, command)

            val shell = getShell(inputParsedData, commandObject, commandExtraInputs)

            Timber.d("Received processId in Command Start: $processId")
            shells.set(processId, shell)

            val cwdSuccess = shell.run("cd ${cwd}")

            if(!cwdSuccess.isSuccess){
                Timber.e("CWD unsuccessful ${cwdSuccess.output}")
            }else{
                Timber.d("current working directory is $cwd")
            }

            //val fullCommand = if(usePython) "python3.10 $exec $command" else "sh $exec $command"
            val fullCommand = when {
                usePython -> "python3.10 $exec $command"
                isShellScript -> "sh $exec $command"
                else -> "$exec $command"
            }

            Timber.d("FULL COMMAND: $fullCommand")

            Timber.d("Env dump: ${shell.environment}")

            val result = shell.run(fullCommand)

            Timber.d("Exit Code ${result.exitCode}")

            val checkPwd = shell.run("pwd")


            val output = result.output()

            Timber.d(output)

            Timber.d("Command Run: ${result.details.command}")


            shell.shutdown()
            shells.remove(processId)

            return result.isSuccess


        }
        catch (e: Exception) {
            Timber.e(e.toString())
            throw e
        }
    }

    fun runCommandForShareWithEnv2(
        commandObject: CommandInterface,
        exec: String,
        command: String,
        cwd: String,
        inputParsedData: List<InputParsedData> = emptyList(),
        commandExtraInputs: List<CommandExtraInput>,
        processId: Int,
        usePython: Boolean = true,
        isShellScript: Boolean = false
    ): ProcessResult {

        try {

            checkForUnsafeCommands(commandObject, command)

            val shell = getShell(inputParsedData, commandObject, commandExtraInputs)

            Timber.d("Received processId in Command Start: $processId")
            shells.set(processId, shell)

            val cwdSuccess = shell.run("cd ${cwd}")

            if(!cwdSuccess.isSuccess){
                Timber.e("CWD unsuccessful ${cwdSuccess.output}")
            }else{
                Timber.d("current working directory is $cwd")
            }

            val fullCommand = when {
                usePython -> "python3.10 $exec $command"
                isShellScript -> "sh $exec $command"
                else -> "$exec $command"
            }

            Timber.d("FULL COMMAND: $fullCommand")

            Timber.d("Env dump: ${shell.environment}")

            val result = shell.run(fullCommand)

            Timber.d("Exit Code ${result.exitCode}")

            val checkPwd = shell.run("pwd")


            val output = result.output()

            Timber.d(output)

            Timber.d("Command Run: ${result.details.command}")


            shell.shutdown()
            shells.remove(processId)


            return ProcessResult(commandObject.name, processId ,result.isSuccess, output)


        }
        catch (e: Exception) {
            Timber.e(e.toString())
            throw e
        }
    }


    fun createTerminalShell(): com.jaredrummler.ktsh.Shell? {

        try {
            val shellPath = File(activity.filesDir, "sh").absolutePath

            val shell = com.jaredrummler.ktsh.Shell(
                shellPath,
            )

            Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)

            shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

            shell.run("cd ${activity.filesDir.absolutePath}")

            return shell
        } catch (e: Exception) {
            Timber.e(e.toString())

            return null
        }
    }


    fun downloadFileWithPython(url: String, fullFilePath: String): Boolean {

        Timber.d("Downloading file with python")

        try {

            if (shell?.isAlive() != true) initShell()

            val command =
                "python3.10 -c \"import urllib.request; url = '${url}'; output_file = '${fullFilePath}'; urllib.request.urlretrieve(url, output_file); print(f'Downloaded {url} to {output_file}')\""

            Timber.d(command)

            val result = shell!!.run(command)


            Timber.d(shell!!.isRunning().toString())
            Timber.d(result.output())

            return result.isSuccess

        } catch (e: Exception) {
            Timber.e(e.toString())
            return false
        }

    }

    fun installPip(): Boolean {
        Timber.d("Installing pip")

        try {
            if (shell?.isAlive() != true) initShell()
            val command =
                "python3.10 -m ensurepip"
            Timber.d(command)

            val result = shell!!.run(command)
            Timber.d(shell!!.isRunning().toString())
            Timber.d(result.output())

            return result.isSuccess
        } catch (e: Exception) {
            Timber.e(e.toString())
            return false
        }
    }

    fun linkPython3(): Boolean {
        Timber.d("Linking python3.10 to python3")

        try {

            val targetFile = Path(activity.filesDir.absolutePath, "build/usr/bin/python3.10")
            val symbolicLink = Path(activity.filesDir.absolutePath, "build/usr/bin/python3")

            symbolicLink.createSymbolicLinkPointingTo(targetFile)

            return true
        } catch (e: Exception) {
            Timber.e(e.toString())
            return false
        }
    }


    fun pipInstallPackage(packageName: String): Boolean {
        Timber.d("Pip installing $packageName")

        try {
            if (shell?.isAlive() != true) initShell()
            val command =
                "pip3 install $packageName"
            Timber.d(command)

            val result = shell!!.run(command)
            Timber.d(shell!!.isRunning().toString())
            Timber.d(result.output())

            return result.isSuccess
        } catch (e: Exception) {
            Timber.e(e.toString())
            return false
        }
    }

    fun listPackages(): List<File> {
        Timber.d("List all packages installed")

        try {
            val binLocation = File(activity.filesDir, "build/bin").listFiles()
            val usrBinLocation = File(activity.filesDir, "build/usr/bin")
            val autosecBinLocation = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin")

            val packages = listOf(
                binLocation?.toList() ?: emptyList(),
                usrBinLocation.listFiles()?.toList() ?: emptyList(),
                autosecBinLocation.listFiles()?.toList() ?: emptyList()
            ).flatten().toSet()


            return packages.toList()
        } catch (e: Exception) {
            Timber.e(e.toString())
            throw  e
        }
    }

    fun startMCPServer(mcpExecPath: String, modulePath: String, host: String, port: String) {

        Timber.d("Starting AutoPie MCP server")

        try {

            initMCPShell(modulePath, host, port)

            val command =
                "python3.10 $mcpExecPath $modulePath & echo \$! > ${activity.filesDir.absolutePath}/uvicorn.pid"

            Timber.d(command)

            mcpShell?.addOnStdoutLineListener(object : Shell.OnLineListener {
                override fun onLine(line: String) {
                    Timber.d(line)
                }
            })

            mcpShell?.addOnCommandResultListener(object : Shell.OnCommandResultListener {
                override fun onResult(result: Shell.Command.Result) {
                    mcpShell?.interrupt()
                }
            })


            val result = mcpShell!!.run(command)

            Timber.d(result.output())

        } catch (e: Exception) {
            Timber.e(e.toString())
        }

    }

    fun stopMCPServer() {
        try {

            val newMCPShell = getNewShell()
            Timber.d("Stopping MCP server")
            val result =
                newMCPShell.run("kill -9 \$(cat ${activity.filesDir.absolutePath}/uvicorn.pid) 2>/dev/null || true")
            Timber.d(result.output())
            mcpShell?.interrupt()
        } catch (e: Exception) {
            Timber.e("Shell terminated")
        }

    }

    fun deleteFile(filePath: String) {

        CoroutineScope(dispatchers.io).launch {

            delay(20000L)

            try {

                if (shell?.isAlive() != true) initShell()

                Timber.d("Deleting file at $filePath")

                val result = shell!!.run("rm '$filePath'")

                Timber.d(result.output())
            } catch (e: Exception) {
                Timber.e(e.toString())
            }
        }

    }

    fun clearPackagesCache() {

        val folderPath = activity.filesDir.absolutePath + "/.shiv"

        Timber.d(folderPath)

        CoroutineScope(dispatchers.io).launch {
            try {

                val directory = File(folderPath)
                directory.deleteRecursively()

            } catch (e: Exception) {
                Timber.e(e.toString())
            }
        }

    }

    fun makeBinariesFolderExecutable() {

        Timber.d("Making python binary files executable")

        val shellPath = File(activity.filesDir, "sh").absolutePath

        val binLocation = File(activity.filesDir, "build/usr/bin")

        val shell = Shell(
            shellPath,
        )

        shell.run("chmod +x ${binLocation.absolutePath}/*")


    }


}