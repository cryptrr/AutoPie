package com.autopi.autopieapp.data.services

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Environment
import android.system.Os
import androidx.lifecycle.viewModelScope
import com.autopi.OutputViewerActivity
import com.autopi.autopieapp.data.AutoPieError
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandInterface
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandResult
import com.autopi.autopieapp.data.InputParsedData
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.ProcessResult
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.AutoPieCoreService.Companion.application
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DispatcherProvider
import com.autopi.utils.Shell
import com.autopi.utils.Utils
import com.termux.app.RunCommandService
import com.termux.app.TermuxActivity
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createSymbolicLinkPointingTo

class ProcessManagerService(
    private val main: MainViewModel,
    private val dispatchers: DispatcherProvider,
    private val activity: Application,
    private val autoPieConfigPathProvider: AutoPieConfigPathProvider,
){

    private var shell: Shell? = null

    private var mcpShell: Shell? = null

    private var shells = ConcurrentHashMap<Int, Shell>()

    private val SHELL_PATH = "usr/bin/bash"

    var processIds : List<Int> = emptyList()
    var successProcessIds : List<Int> = emptyList()
        private set
    var failedProcessIds : List<Int> = emptyList()
        private set

    fun getLoadingActivityComponentName(): String = "${activity.packageName}/.LoadingActivity"

    fun getCookieJarPath(): String = "${activity.filesDir.absolutePath}/usr/var/lib/cookies.txt"

    private fun openOutputViewer(logFile: String, commandName: String) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClass(activity, OutputViewerActivity::class.java)
                putExtra("logFile", logFile)
                putExtra("commandName", commandName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            activity.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open output viewer")
        }
    }


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
                                main.dispatchEvent(ViewModelEvent.CommandStoppedByUser(it.processId))
                            } else {
                                Timber.d("processId not match")
                            }

                            Timber.d("Shells List After: ${shells.keys}")
                        }
                    }

                    is ViewModelEvent.CancelAllProcesses -> {
                        CoroutineScope(dispatchers.io).launch {
                            Timber.d("Shells List: ${shells.keys}")

                            for(runningShell in shells.entries){
                                runningShell.value.process.destroyForcibly()

                                Timber.d("Process terminated: ${runningShell.key}")
                                shells.remove(runningShell.key)
                                main.dispatchEvent(ViewModelEvent.CommandStoppedByUser(runningShell.key))
                            }

                            Timber.d("Shells List After: ${shells.keys}")
                        }
                    }

                    is ViewModelEvent.CommandStarted -> {
                        try {
                            //Add it to the success list
                            processIds = processIds + it.processId
                        }catch (e: Exception){
                            Timber.e(e)
                        }
                    }

                    is ViewModelEvent.CommandCompleted -> {
                        try {
                            //Add it to the success list
                            successProcessIds = successProcessIds + it.processId
                        }catch (e: Exception){
                            Timber.e(e)
                        }
                    }
                    is ViewModelEvent.CommandFailed -> {
                        try {
                            //Add it to the failed list
                            failedProcessIds = failedProcessIds + it.processId
                        }catch (e: Exception){
                            Timber.e(e)
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    fun getAutoPiePackagePath(exec: String): String {
        return File(autoPieConfigPathProvider.getBinDirectory(), exec).absolutePath
    }

    fun getCommandWorkingDirectory(path: String): String {
        return Path(Environment.getExternalStorageDirectory().absolutePath, path).absolutePathString()
    }

    fun getConfigRelativePath(path: String): String {
        return File(autoPieConfigPathProvider.getCommandBaseDirectory(), path).absolutePath
    }


    private fun initShell() {
        Timber.d("Initializing Shell")
        val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath

        shell = Shell(
            shellPath,
            getTermuxShellEnvironment(),
        )

        Timber.d("DIRECTORY: . ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        val setEnvResult =
            shell?.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        Timber.d(setEnvResult?.output())
        shell?.run(getTermuxEnvExports())

    }

    private fun initMCPShell(modulePath: String, host: String, port: String) {
        val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath

        val envMap = getTermuxShellEnvironment()
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
        mcpShell?.run(getTermuxEnvExports())

    }

    private fun getNewShell(): Shell {
        val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath

        val newShell = Shell(
            shellPath,
            getTermuxShellEnvironment(),
        )

        Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        //val setEnvResult = newShell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        return newShell

    }

    private fun getTermuxShellEnvironment(): HashMap<String, String> {
        val defaultPath = System.getenv("PATH") ?: ""
        val defaultLdLibraryPath = System.getenv("LD_LIBRARY_PATH") ?: ""
        val prefix = File(activity.filesDir, "usr").absolutePath

        return hashMapOf(
            "ANDROID_PACKAGE_NAME" to activity.packageName,
            "HOME" to activity.filesDir.absolutePath,
            "PREFIX" to prefix,
            "PATH" to "$prefix/bin:${activity.filesDir.absolutePath}/bin:$defaultPath",
            "LD_LIBRARY_PATH" to "$prefix/lib:$defaultLdLibraryPath",
            "SSL_CERT_FILE" to "$prefix/etc/ssl/cert.pem",
            "TERMINFO" to "$prefix/share/terminfo",
        )
    }

    private fun getTermuxEnvExports(): String {
        val prefix = File(activity.filesDir, "usr").absolutePath
        val appBin = File(activity.filesDir, "bin").absolutePath

        return """
            export ANDROID_PACKAGE_NAME=${activity.packageName.shellQuote()}
            export HOME=${activity.filesDir.absolutePath.shellQuote()}
            export PREFIX=${prefix.shellQuote()}
            export PATH=${"$prefix/bin".shellQuote()}:${appBin.shellQuote()}:${'$'}PATH
            export LD_LIBRARY_PATH=${"$prefix/lib".shellQuote()}:${'$'}LD_LIBRARY_PATH
            export SSL_CERT_FILE=${"$prefix/etc/ssl/cert.pem".shellQuote()}
            export TERMINFO=${"$prefix/share/terminfo".shellQuote()}
            unset PYTHONHOME
            unset PYTHONPATH
        """.trimIndent()
    }

    private fun getEnvsFromCommand(inputParsedData:  List<InputParsedData>, commandExtraInputs: List<CommandExtraInput>,commandObject: CommandInterface, ): HashMap<String, String> {
        val envMap = HashMap<String, String>()

//        envMap["HOME"] = activity.filesDir.absolutePath
//        envMap["PREFIX"] = "${activity.filesDir.absolutePath}/usr"
//        envMap["PATH"] = "${activity.filesDir.absolutePath}/usr/bin:${activity.filesDir.absolutePath}/bin:$defaultPath"
//        envMap["LD_LIBRARY_PATH"] = "${activity.filesDir.absolutePath}/usr/lib:$defaultLdLibraryPath"
//        envMap["ANDROID_PACKAGE_NAME"] = activity.packageName
//        envMap["COOKIE_JAR"] = System.getenv("COOKIE_JAR") ?: "${activity.filesDir.absolutePath}/usr/var/lib/cookies.txt"


        for (inputData in inputParsedData) {
            envMap[inputData.name] = inputData.value
        }

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
                        val fullPath = getConfigRelativePath(extra.default)
                        envMap[extra.name] = fullPath
                    }
                }
                else if(extra.name.endsWith("FILES")){
                    envMap[extra.name] = extra.default.split(",").map {
                        if(Path(extra.default).isAbsolute){
                            it
                        }else{
                            getConfigRelativePath(it)
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
                        val fullPath = getConfigRelativePath(extra.value)
                        envMap[extra.name] = fullPath
                    }
                }
                else if(extra.name.endsWith("FILES")){
                    envMap[extra.name] = extra.value.split(",").map {
                        if(Path(extra.value).isAbsolute){
                            it
                        }else{
                            getConfigRelativePath(it)
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

        return envMap
    }

    private fun getShell(
        inputParsedData: List<InputParsedData>,
        commandObject: CommandInterface,
        commandExtraInputs: List<CommandExtraInput> = emptyList(),
        logWriter: BufferedWriter
    ): Shell {
        val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath
        val defaultPath = System.getenv("PATH") ?: ""
        val defaultLdLibraryPath = System.getenv("LD_LIBRARY_PATH") ?: ""

        val envMap = HashMap<String, String>()

        envMap["HOME"] = activity.filesDir.absolutePath
        envMap["PREFIX"] = "${activity.filesDir.absolutePath}/usr"
        envMap["PATH"] = "${activity.filesDir.absolutePath}/usr/bin:${activity.filesDir.absolutePath}/bin:$defaultPath"
        envMap["LD_LIBRARY_PATH"] = "${activity.filesDir.absolutePath}/usr/lib:$defaultLdLibraryPath"
        envMap["ANDROID_PACKAGE_NAME"] = activity.packageName


        for (inputData in inputParsedData) {
            envMap[inputData.name] = inputData.value
        }

        Timber.d("INPUT_FILE DATA: ${envMap["INPUT_FILE"]}")

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
                        val fullPath = getConfigRelativePath(extra.default)
                        envMap[extra.name] = fullPath
                    }
                }
                else if(extra.name.endsWith("FILES")){
                    envMap[extra.name] = extra.default.split(",").map {
                        if(Path(extra.default).isAbsolute){
                            it
                        }else{
                            getConfigRelativePath(it)
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
                        val fullPath = getConfigRelativePath(extra.value)
                        envMap[extra.name] = fullPath
                    }
                }
                else if(extra.name.endsWith("FILES")){
                    envMap[extra.name] = extra.value.split(",").map {
                        if(Path(extra.value).isAbsolute){
                            it
                        }else{
                            getConfigRelativePath(it)
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

        shells.entries.first().value

        shell.addOnStderrLineListener(object : Shell.OnLineListener {
            override fun onLine(line: String) {
                writeLogLine(logWriter, line)
            }
        }).addOnStdoutLineListener(object : Shell.OnLineListener {
            override fun onLine(line: String) {
                writeLogLine(logWriter, line)
            }
        })


        return shell

    }


    fun checkShell(): Boolean {
        try {
            val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath

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



    fun runCommandForShareWithEnv2(
        commandObject: CommandInterface,
        exec: String,
        command: String,
        cwd: String,
        inputParsedData: List<InputParsedData> = emptyList(),
        commandExtraInputs: List<CommandExtraInput>,
        rawInput: String,
        processId: Int,
        jobType: JobType,
        usePython: Boolean = true,
        isShellScript: Boolean = false
    ): ProcessResult {

        try {

            val logFile = File(activity.cacheDir, "${processId}.log")
            logFile.createNewFile()

            val logWriter = BufferedWriter(FileWriter(logFile, true))
            Timber.d("Logs written to ${logFile.absolutePath}")

            val fullCommand = when {
                usePython && Utils.isPythonScript(commandObject.command) -> {
                    Timber.d("Running Python Script")
                    val pythonScriptFile = File(activity.cacheDir, "${processId}.py")
                    pythonScriptFile.writeText(Utils.stripScriptHeaders(commandObject.command))
                    "python ${pythonScriptFile.absolutePath.shellQuote()}"
                }
                usePython -> {
                    Timber.d("Running Python Package")
                    "python $exec $command"
                }
                //TODO: shellScript Marked for deletion
                isShellScript -> "bash $command"
                //This is the default branch for modern AutoPie
                else -> "$command"
            }

            val scriptFile = File(activity.cacheDir, "${processId}.sh")
            scriptFile.writeText("set -x\n")
            //TODO: Do this on condition.
            scriptFile.appendText("readarray -t INPUT_FILES_ARR <<< \"\$INPUT_FILES\"\n")
            scriptFile.appendText(fullCommand)

            Timber.d("Script file written ${scriptFile.absolutePath}}")


            main.dispatchEvent(ViewModelEvent.CommandStarted(processId,commandObject as CommandModel, logFile.absolutePath, rawInput, jobType))

            if (Utils.isOpenLogsCommand(commandObject.command)) {
                openOutputViewer(logFile.absolutePath, commandObject.name)
            }

            //checkForUnsafeCommands(commandObject, command)

            val shell = getShell(inputParsedData, commandObject, commandExtraInputs, logWriter)

            Timber.d("Received processId in Command Start: $processId")
            shells.set(processId, shell)

            val cwdSuccess = shell.run("cd ${cwd}")

            if(!cwdSuccess.isSuccess){
                Timber.e("CWD unsuccessful ${cwdSuccess.output}")
            }else{
                Timber.d("current working directory is $cwd")
            }


            Timber.d("FULL COMMAND: $fullCommand")

            //Timber.d("Env dump: ${shell.environment}")


            val result = shell.run("bash ${scriptFile.absolutePath.shellQuote()} < /dev/null")

            Timber.d("Exit Code ${result.exitCode}")


            val output = result.output()

            Timber.d(output)

            Timber.d("Command Run: ${result.details.command}")


            shell.shutdown()

            closeLog(logWriter)

            shells.remove(processId)

            return ProcessResult(commandObject.name, processId ,result.isSuccess, output)


        }
        catch (e: Exception) {
            Timber.e(e.toString())
            throw e
        }
    }

    fun runCommandInTermuxShell(
        commandObject: CommandInterface,
        exec: String,
        command: String,
        cwd: String,
        inputParsedData: List<InputParsedData> = emptyList(),
        commandExtraInputs: List<CommandExtraInput>,
        rawInput: String,
        processId: Int,
        jobType: JobType,
        usePython: Boolean = true,
        isShellScript: Boolean = false
    ): ProcessResult {

        Timber.d("runCommandInTermuxShell for $command")

        try {
            val envs = getEnvsFromCommand(inputParsedData, commandExtraInputs, commandObject)
            val scriptFile = File(activity.cacheDir, "${processId}.sh")
            scriptFile.writeText("set -x\n")
            envs.forEach { (key, value) ->
                scriptFile.appendText(
                    "export $key=${value.shellExportValue()}\n"
                )
            }
            //TODO: Do this on condition.
            scriptFile.appendText("readarray -t INPUT_FILES_ARR <<< \"\$INPUT_FILES\"\n")

            if (usePython && Utils.isPythonScript(commandObject.command)) {
                val pythonScriptFile = File(activity.cacheDir, "${processId}.py")
                pythonScriptFile.writeText(Utils.stripScriptHeaders(commandObject.command))
                scriptFile.appendText("python ${pythonScriptFile.absolutePath.shellQuote()}")
            } else {
                scriptFile.appendText(command)
            }

            scriptFile.appendText(
                """

                status=${'$'}?
                printf '\nExit code: %s\n' "${'$'}status"
                exec "${'$'}{SHELL:-bash}" -i
                """.trimIndent()
            )


            val intent = Intent(activity, RunCommandService::class.java).apply {

                action = TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND

                putExtra(
                    TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH,
                    "${activity.filesDir}/usr/bin/bash"
                )

                putExtra(
                    TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS,
                    arrayOf("-i", scriptFile.absolutePath)
                )

                putExtra(
                    TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_WORKDIR,
                    cwd
                )

                putExtra(
                    TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_BACKGROUND,
                    false
                )

                putExtra(
                    TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION,
                    TermuxConstants.TERMUX_APP.TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY.toString()
                )
            }

            Timber.d(intent.toString())

            activity.startService(intent).also {
                Timber.d("Starting Termux Activity: $it")
            }

            return ProcessResult(commandObject.name, processId , true, "Command Opened in Termux Shell")


        }catch (e: Exception){
            Timber.e(e)
            throw e
        }

    }


    fun createTerminalShell(): com.jaredrummler.ktsh.Shell? {

        Timber.d("Creating shell for terminal")
        try {
            val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath

            val defaultPath = System.getenv("PATH") ?: ""
            val defaultLdLibraryPath = System.getenv("LD_LIBRARY_PATH") ?: ""

            val envMap = HashMap<String, String>()
            envMap["HOME"] = activity.filesDir.absolutePath
            envMap["PREFIX"] = "${activity.filesDir.absolutePath}/usr"
            envMap["PATH"] = "${activity.filesDir.absolutePath}/usr/bin:${activity.filesDir.absolutePath}/bin:$defaultPath"
            envMap["LD_LIBRARY_PATH"] = "${activity.filesDir.absolutePath}/usr/lib:$defaultLdLibraryPath"
            envMap["ANDROID_PACKAGE_NAME"] = activity.packageName

            val shell = com.jaredrummler.ktsh.Shell(
                shellPath,
                envMap
            )

            Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)

            //shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

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
                "python -c \"import urllib.request; url = '${url}'; output_file = '${fullFilePath}'; urllib.request.urlretrieve(url, output_file); print(f'Downloaded {url} to {output_file}')\""

            Timber.d(command)

            val result = shell!!.run(command)

            Timber.d(result.output())

            return result.isSuccess

        } catch (e: Exception) {
            Timber.e(e.toString())
            return false
        }
    }

    fun downloadFileWithWCurl(url: String, fullFilePath: String): Boolean {
        Timber.d("Downloading file with wcurl")
        try {
            if (shell?.isAlive() != true) initShell()

            val command =
                "wcurl $url -o $fullFilePath"

            Timber.d(command)

            val result = shell!!.run(command)

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
                "python -m ensurepip"
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

    fun commandExists(commandName: String): Boolean {
        Timber.d("Checking if command exists: $commandName")

        return try {
            if (shell?.isAlive() != true) initShell()

            val result = shell!!.run("command -v ${commandName.shellQuote()} >/dev/null 2>&1")
            result.isSuccess
        } catch (e: Exception) {
            Timber.e(e.toString())
            false
        }
    }

    fun linkBusyboxAr(): Boolean {
        Timber.d("Linking busybox ar to usr/bin")

        val toLink = listOf("ar", "which")

        try {

            for(pkg in toLink){
                val symlinkPath = File(activity.filesDir, "busybox").absolutePath
                val symlinkPathTo = File(activity.filesDir, "usr/bin/${pkg}").absolutePath

                Os.symlink(symlinkPath, symlinkPathTo)
            }

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
                "pip install $packageName"
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
            val binLocation = File(activity.filesDir, "bin").listFiles()
            val usrBinLocation = File(activity.filesDir, "usr/bin")
            val autosecBinLocation = autoPieConfigPathProvider.getBinDirectory()

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
                "python $mcpExecPath $modulePath & echo \$! > ${activity.filesDir.absolutePath}/uvicorn.pid"

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


    fun makeBinariesExecutableInFolder(folder: File) {

        Timber.d("Making the files in ${folder.absolutePath} exec")

        if (!folder.exists() || !folder.isDirectory) {
            println("Invalid folder path: ${folder.absolutePath}")
            return
        }

        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                val success = file.setExecutable(true)
                if (!success) {
                    println("Failed to make executable: ${file.absolutePath}")
                }
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

    fun writeLogLine(writer: BufferedWriter, line: String) {
        writer.appendLine(line)
        writer.flush() // flush immediately for streaming
    }

    fun closeLog(writer: BufferedWriter) {
        writer.close()
    }


}

private fun String.shellQuote(): String {
    return "'${replace("'", "'\"'\"'")}'"
}

private fun String.shellExportValue(): String {
    val trimmed = trim()
    val alreadyQuoted = (trimmed.startsWith("'") && trimmed.endsWith("'")) ||
            (trimmed.startsWith("\"") && trimmed.endsWith("\""))

    return if (alreadyQuoted) this else shellQuote()
}
