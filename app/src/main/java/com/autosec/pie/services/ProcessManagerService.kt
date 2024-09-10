package com.autosec.pie.services

import android.app.Application
import android.content.Context
import com.autosec.pie.data.AutoPieConstants
import com.autosec.pie.data.AutoPieStrings
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

        private val activity: Application by inject(Context::class.java)


        private var shell : Shell? = null


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


        fun runCommand4(exec: String, command: String, cwd: String) : Boolean {
            try {

                if(shell?.isAlive() != true) initShell()

                val checkEnvResult = shell?.run("cd ${cwd}")

                //Log.d("running", shell.isRunning().toString())
                Timber.d(checkEnvResult?.output())

                Timber.d("python3.9 $exec $command")


                //val result = shell.run("python3.9 ${Environment.getExternalStorageDirectory().absolutePath + "/puta.py"}")
                val result = shell!!.run("python3.9 ${exec} $command")

                val output = result.output()

                Timber.d(output)

                val regex = Regex(AutoPieConstants.AUTOPIE_SHELL_RESULT_REGEX)

                val AUTOPIE_OUTPUT = regex.find(output)?.groups?.get(1)?.value

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

                Timber.d("Exit Code ${result.exitCode}")

                val output = result.output()

                Timber.d(output)

                val regex = Regex(AutoPieConstants.AUTOPIE_SHELL_RESULT_REGEX)

                val AUTOPIE_OUTPUT = regex.find(output)?.groups?.get(1)?.value

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