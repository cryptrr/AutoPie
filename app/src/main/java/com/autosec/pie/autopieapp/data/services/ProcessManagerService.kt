package com.autopi.autopieapp.data.services

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Environment
import android.system.Os
import androidx.lifecycle.viewModelScope
import com.autopi.LoadingActivity
import com.autopi.OutputViewerActivity
import com.autopi.SmallLoadingActivity
import com.autopi.autopieapp.data.AutoPieError
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandFlags
import com.autopi.autopieapp.data.CommandInterface
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandResult
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.InputParsedData
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.ProcessResult
import com.autopi.autopieapp.data.hasFlag
import com.autopi.autopieapp.data.isSecretExtra
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.secretKey
import com.autopi.autopieapp.data.services.AutoPieCoreService.Companion.application
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.autopieapp.widget.updateCommandWidgets
import com.autopi.core.DispatcherProvider
import com.autopi.utils.Shell
import com.autopi.utils.Utils
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.termux.app.RunCommandService
import com.termux.app.TermuxActivity
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.math.roundToInt

class ProcessManagerService(
    private val main: MainViewModel,
    private val dispatchers: DispatcherProvider,
    private val activity: Application,
    private val autoPieConfigPathProvider: AutoPieConfigPathProvider,
    private val shellTimeout: Shell.Timeout? = null,
    private val secretsService: SecretsService = SecretsService(activity),
    private val autoPieNotification: AutoPieNotification,
    private val internalConfigService: InternalConfigService,
){

    private val environmentVariableName = Regex("[A-Za-z_][A-Za-z0-9_]*")

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
                                runningShell.interrupt()
                                shells.remove(it.processId)
                                Timber.d("Process terminated: ${it.processId}")
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
                                runningShell.value.interrupt()
                                shells.remove(runningShell.key)
                                Timber.d("Process terminated: ${runningShell.key}")
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
                        LoadingActivity.dismiss(it.processId)
                        SmallLoadingActivity.dismiss(it.processId)
                        try {
                            //Add it to the success list
                            successProcessIds = successProcessIds + it.processId
                        }catch (e: Exception){
                            Timber.e(e)
                        }
                    }
                    is ViewModelEvent.CommandFailed -> {
                        LoadingActivity.dismiss(it.processId)
                        SmallLoadingActivity.dismiss(it.processId)
                        try {
                            //Add it to the failed list
                            failedProcessIds = failedProcessIds + it.processId
                        }catch (e: Exception){
                            Timber.e(e)
                        }
                    }
                    is ViewModelEvent.CommandStoppedByUser -> {
                        LoadingActivity.dismiss(it.processId)
                        SmallLoadingActivity.dismiss(it.processId)
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

    suspend fun getShellEnvironmentVariable(processId: Int, variableName: String): String? =
        withContext(dispatchers.io) {
            if (!environmentVariableName.matches(variableName)) {
                Timber.w("Invalid environment variable name requested: $variableName")
                return@withContext null
            }

            val runningShell = shells[processId]
            if (runningShell == null) {
                Timber.w("No shell found for processId $processId while resolving $variableName")
                return@withContext null
            }
            if (!runningShell.isAlive()) {
                shells.remove(processId, runningShell)
                Timber.w("Shell for processId $processId is not alive while resolving $variableName")
                return@withContext null
            }

            try {
                val result = runningShell.run(
                    "if [ \"\${$variableName+x}\" = x ]; then printf '%s\\n' \"\${$variableName}\"; else false; fi",
                    silentShellConfig()
                )
                if (result.isSuccess) {
                    Timber.d("Resolved $variableName from shell for processId $processId")
                    result.stdout()
                } else {
                    Timber.w("Variable $variableName is not set in shell for processId $processId")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read $variableName from shell for processId $processId")
                null
            }
        }

    suspend fun setShellEnvironmentVariable(
        processId: Int,
        variableName: String,
        value: String
    ): Boolean = setShellEnvironmentVariables(
        processId = processId,
        variables = mapOf(variableName to value)
    )

    suspend fun setShellEnvironmentVariables(
        processId: Int,
        variables: Map<String, String>
    ): Boolean = withContext(dispatchers.io) {
        val validVariables = variables.filterKeys { variableName ->
            val isValid = environmentVariableName.matches(variableName)
            if (!isValid) {
                Timber.w("Invalid environment variable name requested: $variableName")
            }
            isValid
        }
        if (validVariables.isEmpty()) return@withContext variables.isEmpty()

        try {
            val runningShell = getOrCreateShell(processId)
            val result = runningShell.run(
                validVariables.toShellExportCommands(),
                silentShellConfig()
            )
            if (!result.isSuccess) {
                Timber.e("Failed to set shell environment for processId $processId")
            }
            result.isSuccess
        } catch (e: Exception) {
            Timber.e(e, "Failed to set shell environment for processId $processId")
            false
        }
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

    private fun getNewShell(isolateProcessGroup: Boolean = false): Shell {
        val shellPath = File(activity.filesDir, SHELL_PATH).absolutePath

        val newShell = Shell(
            shellPath,
            getTermuxShellEnvironment(),
            isolateProcessGroup
        )

        Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


        //val setEnvResult = newShell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

        return newShell

    }

    private fun getOrCreateShell(processId: Int): Shell =
        shells.computeIfAbsent(processId) { getNewShell(isolateProcessGroup = true) }

    private fun silentShellConfig(): Shell.Command.Config =
        Shell.Command.Config.Builder().apply {
            notify = false
            timeout = shellTimeout
        }.create()

    fun createShell(processId: Int) {
        getOrCreateShell(processId)
        Timber.d("Shell created for processId $processId")
    }

    fun stopShell(processId: Int) {
        val runningShell = shells.remove(processId) ?: return
        if (runningShell.isAlive()) {
            runningShell.shutdown()
        }
        Timber.d("Shell stopped and removed for processId $processId")
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
            //"LD_LIBRARY_PATH" to "$prefix/lib:$defaultLdLibraryPath",
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
            export TERMINFO=${"$prefix/share/terminfo".shellQuote()}
        """.trimIndent()
    }

    private fun getEnvsFromCommand(inputParsedData:  List<InputParsedData>, commandExtraInputs: List<CommandExtraInput>,commandObject: CommandInterface, ): HashMap<String, String> {
        val envMap = HashMap<String, String>()
        val externalStorageRoot = Environment.getExternalStorageDirectory()

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
                val extraValue = if (extra.isSecretExtra()) {
                    secretsService.get(extra.secretKey(commandObject.secretCommandId())) ?: extra.default
                } else if (extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG)) {
                    internalConfigService.get(commandObject.id, extra.id) ?: extra.default
                } else {
                    extra.default
                }

                envMap[extra.name] = resolveExtraPathValue(
                    name = extra.name,
                    type = extra.type,
                    flags = extra.flags,
                    value = extraValue,
                    externalStorageRoot = externalStorageRoot
                )
            }
        }
        //This is when the command extra inputs are passed. That is when the CommandExtrasBottomSheet is opened, all the extras including the defaults are passed as commandExtraInputs
        else {
            for (extra in commandExtraInputs) {
                val commandExtra = commandObject.extras
                    ?.firstOrNull { it.id == extra.id || it.name == extra.name }
                val extraValue = if (commandExtra?.isSecretExtra() == true) {
                    secretsService.get(commandExtra.secretKey(commandObject.secretCommandId())) ?: extra.value
                } else {
                    extra.value
                }

                envMap[extra.name] = resolveExtraPathValue(
                    name = extra.name,
                    type = extra.type,
                    flags = commandExtra?.flags.orEmpty(),
                    value = extraValue,
                    externalStorageRoot = externalStorageRoot
                )
            }
        }

        //Adding the command at last to get the env included result command
        envMap["resultCommand"] = commandObject.command

        return envMap
    }

    private fun CommandInterface.secretCommandId(): String = id.ifBlank { name }

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



    suspend fun runCommandForShareWithEnv2(
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

        val exportedOutputFile = File(activity.cacheDir, "${processId}.output")
        val commandModel = commandObject as CommandModel
        val logFile = File(activity.cacheDir, "${processId}.log")

        try {
            //checkForUnsafeCommands(commandObject, command)

            val commandEnvironment = getEnvsFromCommand(
                inputParsedData,
                commandExtraInputs,
                commandObject
            )

            logFile.createNewFile()

            val logWriter = BufferedWriter(FileWriter(logFile, true))
            Timber.d("Logs written to ${logFile.absolutePath}")

            val scriptPlan = buildCommandScript(
                commandObject = commandObject,
                exec = exec,
                command = command,
                processId = processId,
                cacheDir = activity.cacheDir,
                usePython = usePython,
                isShellScript = isShellScript,
                hasInputFiles = !commandEnvironment["INPUT_FILES"].isNullOrBlank()
            )
            val fullCommand = scriptPlan.fullCommand

            val scriptFile = File(activity.cacheDir, "${processId}.sh")
            scriptPlan.pythonScript?.let { pythonScript ->
                File(activity.cacheDir, "${processId}.py").writeText(pythonScript)
            }
            scriptFile.writeText(scriptPlan.shellScript)

            Timber.d("Script file written ${scriptFile.absolutePath}}")


            dispatchCommandLifecycleEvent(
                ViewModelEvent.CommandStarted(
                    processId,
                    commandModel,
                    logFile.absolutePath,
                    rawInput,
                    jobType
                )
            )

            if (Utils.isOpenLogsCommand(commandObject.command)) {
                openOutputViewer(logFile.absolutePath, commandObject.name)
            }

            when {
                commandObject.flags.hasFlag(CommandFlags.SHOW_LOADING_SCREEN) -> {
                    LoadingActivity.start(activity, processId)
                }
                commandObject.flags.hasFlag(CommandFlags.SHOW_LOADING_SCREEN_SMALL) -> {
                    SmallLoadingActivity.start(activity, processId)
                }
            }

            val shell = getOrCreateShell(processId)

            Timber.d("Received processId in Command Start: $processId")

            val exportResult = shell.run(
                commandEnvironment.toShellExportCommands(),
                silentShellConfig()
            )
            if (!exportResult.isSuccess) {
                Timber.e("Failed to export command environment: ${exportResult.stderr()}")
            }

            val cwdSuccess = shell.run(
                "cd ${cwd.shellQuote()}",
                silentShellConfig()
            )

            if(!cwdSuccess.isSuccess){
                Timber.e("CWD unsuccessful ${cwdSuccess.output}")
            }else{
                Timber.d("current working directory is $cwd")
            }


            Timber.d("FULL COMMAND: $fullCommand")

            //Timber.d("Env dump: ${shell.environment}")


            val executionCommand = buildExecutionCommand(scriptFile, commandObject.multiStage == true)
            val latestStructuredOutput = AtomicReference<String?>(null)
            val pendingWidgetUpdates = mutableListOf<Job>()

            val result = shell.run(executionCommand) {
                timeout = shellTimeout
                notify = false
                onStdOut = { line ->
                    writeLogLine(logWriter, line)
                    when (val event = parseAutoPieStructuredEvent(line)) {
                        is AutoPieStructuredEvent.Output -> {
                            latestStructuredOutput.set(event.rawValue)
                            pendingWidgetUpdates += main.viewModelScope.launch(dispatchers.io) {
                                publishWidgetState(
                                    commandObject = commandObject,
                                    rawOutput = event.rawValue,
                                    status = "running",
                                    replaceOutput = true
                                )
                            }
                        }
                        is AutoPieStructuredEvent.Notification -> {
                            try {
                                autoPieNotification.sendNotification(
                                    contentTitle = event.title,
                                    contentText = event.body,
                                    command = commandModel,
                                    logFile = logFile.absolutePath,
                                    processId = processId,
                                    silent = false,
                                    autoCancel = false
                                )
                            } catch (error: Throwable) {
                                Timber.e(
                                    error,
                                    "Unable to send structured notification for ${commandObject.name}"
                                )
                            }
                        }
                        is AutoPieStructuredEvent.Progress -> {
                            if (jobType != JobType.CRON) {
                                try {
                                    autoPieNotification.sendBroadcastNotification(
                                        contentTitle = commandObject.name,
                                        contentText = rawInput,
                                        command = commandModel,
                                        processId = processId,
                                        logFile = logFile.absolutePath,
                                        totalProgress = 100,
                                        currentProgress = event.value
                                    )
                                } catch (error: Throwable) {
                                    Timber.e(
                                        error,
                                        "Unable to update progress notification for ${commandObject.name}"
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                onStdErr = { line -> writeLogLine(logWriter, line) }
            }

            Timber.d("Exit Code ${result.exitCode}")


            val output = result.output()
            pendingWidgetUpdates.forEach { it.join() }
            val exportedOutput = exportedOutputFile
                .takeIf(File::isFile)
                ?.readText()
                ?: latestStructuredOutput.get()

            Timber.d(output)

            Timber.d("Command Run: ${result.details.command}")


            closeLog(logWriter)

            val partial = result.isSuccess &&
                commandObject.multiStage == true &&
                commandObject.steps.size > 1

            if(commandObject.multiStage != true){
                Timber.d("Removing shell for non multistage commannd")
                shell.shutdown()
                shells.remove(processId)
            }

            if (result.isSuccess) {
                dispatchCommandLifecycleEvent(
                    ViewModelEvent.CommandCompleted(
                        processId = processId,
                        command = commandModel,
                        logFile = logFile.absolutePath,
                        jobType = jobType,
                        partial = partial,
                        exportedOutput = exportedOutput
                    )
                )
            } else {
                dispatchCommandLifecycleEvent(
                    ViewModelEvent.CommandFailed(
                        processId = processId,
                        command = commandModel,
                        logFile = logFile.absolutePath,
                        jobType = jobType
                    )
                )
            }

            return ProcessResult(
                commandObject.name,
                processId,
                result.isSuccess,
                output,
                partial = partial,
                exportedOutput = exportedOutput
            )

        }
        catch (e: Exception) {
            Timber.e(e.toString())
            dispatchCommandLifecycleEvent(
                ViewModelEvent.CommandFailed(
                    processId = processId,
                    command = commandModel,
                    logFile = logFile.absolutePath,
                    jobType = jobType
                )
            )
            throw e
        }
    }

    private suspend fun publishWidgetState(
        commandObject: CommandInterface,
        rawOutput: String?,
        status: String,
        replaceOutput: Boolean
    ) {
        try {
            updateCommandWidgets(
                context = activity,
                commandId = commandObject.id.ifBlank { commandObject.name },
                commandName = commandObject.name,
                rawOutput = rawOutput,
                status = status,
                replaceOutput = replaceOutput
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "Unable to update command widgets for ${commandObject.name}")
        }
    }

    /**
     * Applies lifecycle side effects before broadcasting the event. The event flow is deliberately
     * fire-and-forget, so correctness-critical widget state must not depend on a collector running.
     */
    private suspend fun dispatchCommandLifecycleEvent(event: ViewModelEvent) {
        when (event) {
            is ViewModelEvent.CommandStarted -> publishWidgetState(
                commandObject = event.command,
                rawOutput = null,
                status = "running",
                replaceOutput = false
            )
            is ViewModelEvent.CommandCompleted -> if (!event.partial) {
                publishWidgetState(
                    commandObject = event.command,
                    rawOutput = event.exportedOutput,
                    status = "success",
                    replaceOutput = shouldReplaceWidgetOutput(
                        jobType = event.jobType,
                        exportedOutput = event.exportedOutput
                    )
                )
            }
            is ViewModelEvent.CommandFailed -> publishWidgetState(
                commandObject = event.command,
                rawOutput = null,
                status = "failed",
                replaceOutput = false
            )
            else -> error("Unsupported command lifecycle event: $event")
        }
        main.dispatchEvent(event)
    }

    suspend fun runCommandInTermuxShell(
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

        val commandModel = commandObject as CommandModel
        val logFile = File(activity.cacheDir, "${processId}.log")

        try {
            logFile.createNewFile()
            dispatchCommandLifecycleEvent(
                ViewModelEvent.CommandStarted(
                    processId,
                    commandModel,
                    logFile.absolutePath,
                    rawInput,
                    jobType
                )
            )
            val envs = getEnvsFromCommand(inputParsedData, commandExtraInputs, commandObject)
            val scriptFile = File(activity.cacheDir, "${processId}.sh")
            scriptFile.writeText("set -x\n")
            envs.forEach { (key, value) ->
                scriptFile.appendText(
                    "export $key=${value.shellExportValue()}\n"
                )
            }
            if (!envs["INPUT_FILES"].isNullOrBlank()) {
                scriptFile.appendText("readarray -t INPUT_FILES_ARR <<< \"\$INPUT_FILES\"\n")
            }

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

            val partial = commandObject.multiStage == true && commandObject.steps.size > 1
            dispatchCommandLifecycleEvent(
                ViewModelEvent.CommandCompleted(
                    processId = processId,
                    command = commandModel,
                    logFile = logFile.absolutePath,
                    jobType = jobType,
                    partial = partial
                )
            )

            return ProcessResult(
                commandObject.name,
                processId,
                true,
                "Command Opened in Termux Shell",
                partial = partial
            )


        }catch (e: Exception){
            Timber.e(e)
            dispatchCommandLifecycleEvent(
                ViewModelEvent.CommandFailed(
                    processId = processId,
                    command = commandModel,
                    logFile = logFile.absolutePath,
                    jobType = jobType
                )
            )
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
            //envMap["LD_LIBRARY_PATH"] = "${activity.filesDir.absolutePath}/usr/lib:$defaultLdLibraryPath"
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

internal fun shouldReplaceWidgetOutput(
    jobType: JobType,
    exportedOutput: String?
): Boolean = jobType != JobType.CRON || !exportedOutput.isNullOrBlank()

private const val AUTOPIE_EVENT_PREFIX = "#@AUTOPIE"

internal sealed interface AutoPieStructuredEvent {
    data class Output(val rawValue: String) : AutoPieStructuredEvent
    data class Notification(val title: String, val body: String) : AutoPieStructuredEvent
    data class Progress(val value: Int) : AutoPieStructuredEvent
    data class Unsupported(val type: String) : AutoPieStructuredEvent
}

internal fun parseAutoPieStructuredEvent(line: String): AutoPieStructuredEvent? {
    if (!line.startsWith(AUTOPIE_EVENT_PREFIX)) return null
    val payload = line.removePrefix(AUTOPIE_EVENT_PREFIX).trimStart()
    if (payload.isBlank()) return null

    return runCatching {
        val event = JsonParser.parseString(payload).asJsonObject
        val type = event.get("type")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return null

        when (type) {
            "output" -> AutoPieStructuredEvent.Output(
                rawValue = event.get("value")?.toWidgetRawValue() ?: return null
            )
            "notification" -> AutoPieStructuredEvent.Notification(
                title = event.stringOrNull("title") ?: return null,
                body = event.stringOrNull("body") ?: return null
            )
            "progress" -> {
                val value = event.get("value")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.asDouble
                    ?.roundToInt()
                    ?: return null
                AutoPieStructuredEvent.Progress(value.coerceIn(0, 100))
            }
            else -> AutoPieStructuredEvent.Unsupported(type)
        }
    }.getOrNull()
}

private fun JsonElement.toWidgetRawValue(): String {
    if (!isJsonPrimitive || !asJsonPrimitive.isString) return toString()

    val nestedJson = runCatching { JsonParser.parseString(asString) }.getOrNull()
    return if (nestedJson?.isJsonObject == true || nestedJson?.isJsonArray == true) {
        nestedJson.toString()
    } else {
        toString()
    }
}

private fun com.google.gson.JsonObject.stringOrNull(key: String): String? =
    get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString

private fun String.shellQuote(): String {
    return "'${replace("'", "'\"'\"'")}'"
}

private fun String.shellExportValue(): String {
    val trimmed = trim()
    val alreadyQuoted = (trimmed.startsWith("'") && trimmed.endsWith("'")) ||
            (trimmed.startsWith("\"") && trimmed.endsWith("\""))

    return if (alreadyQuoted) this else shellQuote()
}

internal data class CommandScriptPlan(
    val fullCommand: String,
    val shellScript: String,
    val pythonScript: String? = null
)

internal fun buildCommandScript(
    commandObject: CommandInterface,
    exec: String,
    command: String,
    processId: Int,
    cacheDir: File,
    usePython: Boolean,
    isShellScript: Boolean,
    hasInputFiles: Boolean
): CommandScriptPlan {
    val pythonScriptFile = File(cacheDir, "${processId}.py")
    val exportedOutputFile = File(cacheDir, "${processId}.output")
    val pythonScript = if (usePython && Utils.isPythonScript(commandObject.command)) {
        Utils.stripScriptHeaders(commandObject.command)
    } else {
        null
    }
    val fullCommand = when {
        pythonScript != null -> {
            Timber.d("Running Python Script")
            "python ${pythonScriptFile.absolutePath.shellQuote()}"
        }
        usePython -> {
            Timber.d("Running Python Package")
            "python $exec $command"
        }
        isShellScript -> "bash $command"
        else -> command
    }

    return CommandScriptPlan(
        fullCommand = fullCommand,
        shellScript = buildString {
            append("rm -f ${exportedOutputFile.absolutePath.shellQuote()}\n")
            append(commandScriptPreamble(commandObject.multiStage == true))
            if (hasInputFiles) {
                append("readarray -t INPUT_FILES_ARR <<< \"\$INPUT_FILES\"\n")
            }
            append(fullCommand)
            if (commandObject.multiStage == true) {
                append("\nstep_status=\$?\nset +x\n")
                append(commandOutputCapture(exportedOutputFile))
                append("return \"\$step_status\"\n")
            } else {
                append("\ncommand_status=\$?\nset +x\n")
                append(commandOutputCapture(exportedOutputFile))
                append("exit \"\$command_status\"\n")
            }
        },
        pythonScript = pythonScript
    )
}

internal fun commandOutputCapture(outputFile: File): String = buildString {
    append("if [ \"\${OUTPUT+x}\" = x ]; then\n")
    append("    umask 077\n")
    append("    printf '%s' \"\$OUTPUT\" > ${outputFile.absolutePath.shellQuote()}\n")
    append("fi\n")
}

internal fun Map<String, String>.toShellExportCommands(): String =
    entries.joinToString("\n") { (key, value) ->
        "export $key=${value.shellExportValue()}"
    }

internal fun commandScriptPreamble(multiStage: Boolean): String = buildString {
    if (multiStage) {
        append("if [ \"\${OUTPUT+x}\" = x ]; then\n")
        append("    export INPUT=\"\$OUTPUT\"\n")
        append("    unset OUTPUT\n")
        append("fi\n")
    }
    append("set -x\n")
}

internal fun buildExecutionCommand(scriptFile: File, multiStage: Boolean): String =
    if (multiStage) {
        ". ${scriptFile.absolutePath.shellQuote()} < /dev/null"
    } else {
        "bash ${scriptFile.absolutePath.shellQuote()} < /dev/null"
    }

internal fun resolveExtraPathValue(
    name: String,
    type: String,
    flags: List<String>?,
    value: String,
    externalStorageRoot: File
): String {
    if (type != "STRING" || value.isBlank()) return value

    fun resolve(path: String): String {
        val trimmedPath = path.trim()
        if (trimmedPath.isBlank() || File(trimmedPath).isAbsolute) return trimmedPath
        return File(externalStorageRoot, trimmedPath).absolutePath
    }

    val isMultiplePaths = name.endsWith("FILES") ||
        flags.hasFlag(ExtraFlags.MULTI_FILE_PICKER)
    if (isMultiplePaths) {
        return value.split(',').joinToString(",", transform = ::resolve)
    }

    val isSinglePath = name.endsWith("FILE") || name.endsWith("FOLDER") ||
        flags.hasFlag(ExtraFlags.FILE_PICKER) || flags.hasFlag(ExtraFlags.FOLDER_PICKER)
    return if (isSinglePath) resolve(value) else value
}
