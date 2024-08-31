package com.autosec.pie.services

import android.app.Application
import android.content.Context
import com.autosec.pie.notifications.AutoPieNotification
import com.jaredrummler.ktsh.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ProcessManagerService {
    companion object {

        private val activity: Application by inject(Context::class.java)


        private var shell : Shell? = null

        private fun initShell(){
            val shellPath = File(activity.filesDir, "sh").absolutePath

            shell = Shell(
                shellPath,
            )

            Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)


            val setEnvResult =
                shell?.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath}")

        }


        fun runCommand4(exec: String, command: String, cwd: String) : Boolean {
            try {

                if(shell?.isAlive() != true) initShell()

                val checkEnvResult = shell?.run("cd ${cwd}")

                //Log.d("running", shell.isRunning().toString())
                Timber.d(checkEnvResult?.output())

                Timber.d("python3.9 $exec $command")


                //val result = shell.run("python3.9 ${Environment.getExternalStorageDirectory().absolutePath + "/puta.py"}")
                val result = shell!!.run("python3.9 ${exec} $command")

                Timber.d(result.output())

                return result.isSuccess


            } catch (e: Exception) {
                Timber.e(e.toString())
            }

            return false

        }

        fun runCommandForShare(exec: String, command: String, cwd: String): Boolean {

            try {

                if(shell?.isAlive() != true) initShell()

                shell!!.run("cd ${cwd}")

                Timber.d("python3.9 $exec $command")

                val result = shell!!.run("python3.9 ${exec} $command")

                Timber.d(result.output())

                Timber.d("Exit Code ${result.exitCode}")

                //shell?.shutdown()

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

                shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath}")

                shell.run("cd ${activity.filesDir.absolutePath}")

                return shell
            } catch (e: Exception) {
                Timber.e(e.toString())

                return null
            }
        }

        fun runWget(url: String, fullFilePath: String) {

            CoroutineScope(Dispatchers.IO).launch {
                try {

                    val shellPath = File(activity.filesDir, "sh").absolutePath

                    val shell = Shell(
                        shellPath,
                    )

                    Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)

                    shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath}")

                    Timber.d("wget")

                    val result = shell.run("wget -O $fullFilePath $url")


                    Timber.d(shell.isRunning().toString())
                    Timber.d(result.output())
                } catch (e: Exception) {
                    Timber.e(e.toString())
                }
            }

        }

        fun deleteFile(filePath: String) {

            CoroutineScope(Dispatchers.IO).launch {

                delay(20000L)

                try {

                    val shellPath = File(activity.filesDir, "sh").absolutePath

                    val shell = Shell(
                        shellPath,
                    )

                    Timber.d(". ." + activity.filesDir.absolutePath + "/env.sh " + activity.filesDir.absolutePath)

                    shell.run(". .${activity.filesDir.absolutePath}/env.sh ${activity.filesDir.absolutePath}")

                    Timber.d("Deleting file at $filePath")

                    val result = shell.run("rm '$filePath'")


                    Timber.d(shell.isRunning().toString())
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

        fun runBusyBoxShell(): String {
            val busyBoxPath = File(activity.filesDir, "busybox").absolutePath

            val command = listOf(busyBoxPath, "sh", "-c", "python --help")

            val processBuilder = ProcessBuilder(command)

            // Setting up environment variables if needed
            val environment = processBuilder.environment()
            environment["LD_LIBRARY_PATH"] =
                "${activity.filesDir.absolutePath}/aarch64-linux-android/lib"
            environment["PATH"] =
                "${activity.filesDir.absolutePath}/aarch64-linux-android/bin:$busyBoxPath"
            environment["HOME"] = "${activity.filesDir.absolutePath}:$busyBoxPath"

            // Start the process
            val process = processBuilder.start()

            // Capture the output
            val output =
                BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }

            Timber.d(output)


            return if (error.isEmpty()) output else error
        }


        fun runCommand2(binary: File, arguments: List<String>, cwd: File): String {


            val command = arrayOf("ls", activity.filesDir.absolutePath + "/bin")
            val process = Runtime.getRuntime().exec(command)
            val result = process.inputStream.bufferedReader().readText()

            process.waitFor()

            Timber.d(result)

            return result
        }


    }
}