package com.autosec.pie.services

import android.app.Application
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.AutoPieConstants
import com.autosec.pie.data.AutoPieStrings
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.data.CommandExtraInput
import com.autosec.pie.data.CommandInterface
import com.autosec.pie.data.CommandModel
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.viewModels.MainViewModel
import com.jaredrummler.ktsh.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ProcessManagerService {
    companion object {

        val main: MainViewModel by inject(MainViewModel::class.java)

        private val activity: Application by inject(Context::class.java)


        private var shell : Shell? = null

        private var shells = HashMap<Int, Shell>()

        init {
            main.viewModelScope.launch {
                main.eventFlow.collect{
                    when(it){
                        is ViewModelEvent.CancelProcess -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                Timber.d("Received processId in event: ${it.processId}")
                                Timber.d("Shells List: ${shells.keys}")

                                val runningShell = shells[it.processId]

                                if(runningShell != null) {
                                    runningShell.interrupt()

                                    Timber.d("Process terminated: ${it.processId}")
                                    shells.remove(it.processId)
                                }
                                else{
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


        private fun initShell(){
            val shellPath = File(activity.filesDir, "sh").absolutePath

            shell = Shell(
                shellPath,
            )

            Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


            val setEnvResult =
                shell?.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

            Timber.d(setEnvResult?.output())

        }

        private fun getShell() : Shell {
            val shellPath = File(activity.filesDir, "sh").absolutePath

            val shell = Shell(
                shellPath,
            )

            Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


            val setEnvResult =
                shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath} ${activity.packageName}")

            return shell

        }

        fun checkShell(): Boolean {
            try{
                val shellPath = File(activity.filesDir, "sh").absolutePath

                val shell = Shell(
                    shellPath,
                )

                val result = shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath}")

                shell.shutdown()

                return result.isSuccess
            }catch (e: Exception){
                return false
            }
        }


        fun runCommand4(exec: String, command: String, cwd: String, usePython: Boolean = true) : Boolean {
            try {

                if(shell?.isAlive() != true) initShell()

                val checkEnvResult = shell?.run("cd ${cwd}")

                Timber.d(checkEnvResult?.output())

                val fullCommand = if(usePython) "python3.9 $exec $command" else "sh $exec $command"

                Timber.d(fullCommand)

                val result = shell!!.run(fullCommand)

                val output = result.output()

                Timber.d(output)


                return result.isSuccess


            } catch (e: Exception) {
                Timber.e(e.toString())
            }

            return false

        }

        fun runCommandWithEnv(commandObject: CommandInterface,exec: String, command: String, cwd: String, usePython: Boolean = true) : Boolean {
            try {

                val shell = getShell()

                for(extra in commandObject.extras ?: emptyList()){
                    Timber.d("Setting extra to defaults: ${extra.name}='${extra.default}'")
                    shell.run("export ${extra.name}='${extra.default}'")
                }

                val checkEnvResult = shell.run("cd ${cwd}")

                Timber.d(checkEnvResult.output())

                val fullCommand = if(usePython) "python3.9 $exec $command" else "sh $exec $command"

                Timber.d(fullCommand)

                val result = shell.run(fullCommand)

                val output = result.output()

                Timber.d(output)

                shell.shutdown()

                return result.isSuccess


            } catch (e: Exception) {
                Timber.e(e.toString())
            }

            return false

        }

        fun runCommandForShare(exec: String, command: String, cwd: String, usePython: Boolean = true): Boolean {

            try {

                if(shell?.isAlive() != true) initShell()

                shell!!.run("cd ${cwd}")

                val fullCommand = if(usePython) "python3.9 $exec $command" else "sh $exec $command"

                Timber.d(fullCommand)

                val result = shell!!.run(fullCommand)

                Timber.d("Exit Code ${result.exitCode}")

                val output = result.output()

                Timber.d(output)

                //shell?.shutdown()

                return result.isSuccess

            } catch (e: Exception) {
                Timber.e(e.toString())
            }

            return false

        }

        fun runCommandForShareWithEnv(commandObject: CommandModel,exec: String, command: String, cwd: String, commandExtraInputs: List<CommandExtraInput>,processId: Int, usePython: Boolean = true): Boolean {

            try {

                val shell = getShell()

                Timber.d("Received processId in Command Start: $processId")
                shells.set(processId, shell)

                shell.run("cd ${cwd}")

                //TODO: There might be some udaipp here. There are multiple extras
                if(commandExtraInputs.isEmpty()){
                    for(extra in commandObject.extras ?: emptyList()){
                        Timber.d("Setting extra to defaults: ${extra.name}=${extra.default}")
                        shell.run("export ${extra.name}='${extra.default}'")
                    }
                }else{
                    for(extra in commandExtraInputs){
                        Timber.d("Setting extra to values: ${extra.name}=${extra.value}")
                        shell.run("export ${extra.name}='${extra.value}'")
                    }
                }

                val fullCommand = if(usePython) "python3.9 $exec $command" else "sh $exec $command"

                Timber.d(fullCommand)

                val result = shell.run(fullCommand)

                Timber.d("Exit Code ${result.exitCode}")

                val output = result.output()

                Timber.d(output)

                Timber.d("Command Run: ${result.details.command}")

                shell.shutdown()
                shells.remove(processId)

                return result.isSuccess



            } catch (e: Exception) {
                Timber.e(e.toString())
            }

            return false

        }



        fun createAutoPieShell(): Shell? {

            try {
                val shellPath = File(activity.filesDir, "sh").absolutePath

                val shell = Shell(
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

                if(shell?.isAlive() != true) initShell()

                val command = "python3.9 -c \"import urllib.request; url = '${url}'; output_file = '${fullFilePath}'; urllib.request.urlretrieve(url, output_file); print(f'Downloaded {url} to {output_file}')\""

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

        fun deleteFile(filePath: String) {

            CoroutineScope(Dispatchers.IO).launch {

                delay(20000L)

                try {

                    if(shell?.isAlive() != true) initShell()

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

            CoroutineScope(Dispatchers.IO).launch {
                try {

                    val directory = File(folderPath)
                    directory.deleteRecursively()

                } catch (e: Exception) {
                    Timber.e(e.toString())
                }
            }

        }

        fun makeBinariesFolderExecutable(){

            Timber.d("Making python binary files executable")

            val shellPath = File(activity.filesDir, "sh").absolutePath

            val binLocation = File(activity.filesDir, "build/usr/bin")

            val shell = Shell(
                shellPath,
            )

            shell.run("chmod +x ${binLocation.absolutePath}/*")


        }

    }
}