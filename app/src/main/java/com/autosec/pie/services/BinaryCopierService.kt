package com.autosec.pie.services

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.autosec.pie.domain.AppNotification
import com.autosec.pie.domain.ViewModelError
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.viewModels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class BinaryCopierService {


    companion object {
        val activity: Application by inject(Context::class.java)
        private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)


        fun extractAndExecuteBinary(context: Application) {

            val binaries = listOf("busybox", "env.sh")

            for (binary in binaries) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Timber.d("Trying to copy: $binary")
                        val inputStream: InputStream = context.assets.open(binary)
                        val binDirectory = File(context.filesDir, "/")

                        if (binDirectory.exists() && binDirectory.isDirectory) {
                            Timber.d("Bin directory exists: " + binDirectory.absolutePath)
                        } else {
                            binDirectory.mkdir()
                        }

                        val outputFile = File(context.filesDir, binary)
                        val outputStream = FileOutputStream(outputFile)

                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Make the file executable
                        if (outputFile.setExecutable(true)) {
                            Timber.d("File is set to executable: " + outputFile.absolutePath)
                        } else {
                            Timber.e("Failed to set the file to executable.")
                        }

                        if (binary == "busybox") {
                            val symlinkPath = File(context.filesDir, "sh").absolutePath

                            val command = "$outputFile ln -sf $outputFile $symlinkPath"

                            val process = Runtime.getRuntime().exec(command)
                            process.waitFor()
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting and executing binary")
                        //Toast.makeText(this@MainActivity, "Error extracting and executing binary", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        fun extractAndExecuteLibraries(context: Activity) {

            val binaries = listOf("libandroid-support.so", "libpython3.11.so.1.0")

            for (binary in binaries) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Timber.d("Trying to copy: $binary")
                        val inputStream: InputStream = context.assets.open(binary)
                        val binDirectory = File(context.filesDir, "lib")

                        if (binDirectory.exists() && binDirectory.isDirectory) {
                            Timber.d("Lib directory exists: " + binDirectory.absolutePath)
                        } else {
                            binDirectory.mkdir()
                        }

                        val outputFile = File(context.filesDir, "lib/$binary")
                        val outputStream = FileOutputStream(outputFile)

                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        //Make the file executable
                        if (outputFile.setReadable(true)) {
                            Timber.d("File is set to readable: " + outputFile.absolutePath)
                        } else {
                            Timber.e("Failed to set the file to readable.")
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting and executing binary")
                        //Toast.makeText(this@MainActivity, "Error extracting and executing binary", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fun extractAndExecuteLibraries2(context: Activity) {

            val binaries = context.assets.list("lib")?.toList()

            for (binary in binaries!!) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Timber.d("Trying to copy: " + binary)
                        val inputStream: InputStream = context.assets.open("lib/$binary")
                        val binDirectory = File(context.filesDir, "usr/lib")

                        if (binDirectory.exists() && binDirectory.isDirectory) {
                            Timber.d("Lib directory exists: " + binDirectory.absolutePath)
                        } else {
                            binDirectory.mkdir()
                        }

                        val outputFile = File(context.filesDir, "usr/lib/$binary")
                        val outputStream = FileOutputStream(outputFile)

                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        //Make the file executable
                        if (outputFile.setReadable(true)) {
                            Timber.d("File is set to readable: " + outputFile.absolutePath)
                        } else {
                            Timber.e("Failed to set the file to readable.")
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting and executing binary")
                        //Toast.makeText(this@MainActivity, "Error extracting and executing binary", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fun extractAndSetUpTLS(context: Activity) {

            val binaries = context.assets.list("etc/tls")?.toList()

            for (binary in binaries!!) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Timber.d("Trying to copy: $binary")
                        val inputStream: InputStream = context.assets.open("etc/tls/$binary")
                        val binDirectory = File(context.filesDir, "usr/etc")
                        val tlsDir = File(context.filesDir, "usr/etc/tls")


                        if (binDirectory.exists() && binDirectory.isDirectory) {
                            Timber.d("Etc directory exists: " + binDirectory.absolutePath)
                        } else {
                            binDirectory.mkdir()
                            tlsDir.mkdir()
                        }

                        val outputFile = File(context.filesDir, "usr/etc/tls/$binary")
                        val outputStream = FileOutputStream(outputFile)

                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        //Make the file executable
                        if (outputFile.setReadable(true)) {
                            Timber.d("File is set to readable: " + outputFile.absolutePath)
                        } else {
                            Timber.e("Failed to set the file to readable.")
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting and executing binary")
                        //Toast.makeText(this@MainActivity, "Error extracting and executing binary", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        fun extractTarXzFromAssets(context: Application) {

            val distFolder = File(context.filesDir, "build")

            if (distFolder.exists() && distFolder.isDirectory) {
                Timber.d("Dist folder exists")
                return
            }

            CoroutineScope(Dispatchers.IO).launch {

                Timber.d("Starting extracting filesystem")


                val assetFilename = "build-aarch64-api27.tar.xz"


                val destinationPath = context.filesDir

                var tarInputStream: TarArchiveInputStream? = null

                try {
                    // Open the asset file as an InputStream
                    val inputStream: InputStream = context.assets.open(assetFilename)
                    val xzInputStream = XZCompressorInputStream(inputStream)
                    tarInputStream = TarArchiveInputStream(xzInputStream)

                    // Extract the files
                    var entry: TarArchiveEntry? = tarInputStream.nextTarEntry

                    CoroutineScope(Dispatchers.Main).launch {
                        mainViewModel.dispatchEvent(ViewModelEvent.InstallingPython)
                        mainViewModel.showNotification(AppNotification.InstallingPythonPackages)
                        //Toast.makeText(activity.applicationContext, "Please wait for python to be installed...", Toast.LENGTH_LONG).show()
                    }

                    while (entry != null) {
                        val file = File(destinationPath, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            val outputStream = FileOutputStream(file)
                            tarInputStream.copyTo(outputStream)
                            outputStream.close()
                        }
                        entry = tarInputStream.nextTarEntry
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        mainViewModel.dispatchEvent(ViewModelEvent.InstalledPythonSuccessfully)
                        mainViewModel.showNotification(AppNotification.InstallingPythonPackagesSuccess)
                        //Toast.makeText(activity.applicationContext, "Python installation complete", Toast.LENGTH_LONG).show()
                    }


                } catch (e: IOException) {
                    e.printStackTrace()

                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            activity.applicationContext,
                            "Error installing python. Please Reinstall this app.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } finally {
                    try {
                        tarInputStream?.close()

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

        fun checkForAutoSecFolder() : Boolean {
            val autoSecFolder = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")

            return autoSecFolder.exists()
        }

        fun createAutoSecFolder() {
            CoroutineScope(Dispatchers.IO).launch {
                val autoSecFolder =
                    File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")

                autoSecFolder.mkdir()
            }
        }

        fun createLogsFolder() {
            CoroutineScope(Dispatchers.IO).launch {
                val logsFolder =
                    File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/logs")

                logsFolder.mkdir()
            }
        }

        fun downloadAutoSecInitArchive() {

            CoroutineScope(Dispatchers.IO).launch {

                val autoSecFolder =
                    File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")

                if(autoSecFolder.exists()){
                    ProcessManagerService.runWget("", Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz")
                }
            }
        }

        private fun isFileCompletelyDownloaded(filePath: String, timeoutMillis: Long = 25000): Boolean {
            val file = File(filePath)

            // Check if the file exists
            if (!file.exists()) {
                return false
            }

            var previousSize = file.length()
            var currentSize: Long

            // Keep checking if the file size changes
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                Thread.sleep(1000) // Wait 1 second before checking the file size again

                currentSize = file.length()

                // If the size hasn't changed after waiting, we assume the file is fully downloaded
                if (currentSize == previousSize) {
                    return true
                }

                previousSize = currentSize
            }

            // If the timeout is reached and the file size is still changing, return false
            return false
        }

        fun extractAutoSecFiles() {


            val autoSecFolder = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")
            val initArchiveFile = File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz")


            if (!autoSecFolder.exists() && !autoSecFolder.isDirectory) {
                Timber.d("AutoSec folder does not exist")
                return
            }

//            if (!initArchiveFile.exists()) {
//                Timber.d("AutoSec init file not downloaded")
//                return
//            }



            CoroutineScope(Dispatchers.IO).launch {

                //Waits until the file is downloaded
                val isFileDownloaded = isFileCompletelyDownloaded(initArchiveFile.absolutePath)


                if(!isFileDownloaded){
                    mainViewModel.showError(ViewModelError.Unknown)
                    return@launch
                }

                Timber.d("Starting extracting Init Archive")


                val destinationPath = autoSecFolder

                var tarInputStream: TarArchiveInputStream? = null

                try {
                    // Open the asset file as an InputStream
                    val inputStream: InputStream = initArchiveFile.inputStream()
                    val xzInputStream = XZCompressorInputStream(inputStream)
                    tarInputStream = TarArchiveInputStream(xzInputStream)

                    // Extract the files
                    var entry: TarArchiveEntry? = tarInputStream.nextTarEntry

                    CoroutineScope(Dispatchers.Main).launch {
                        //mainViewModel.dispatchEvent(ViewModelEvent.InstallingInitArchive)
                    }

                    while (entry != null) {
                        val file = File(destinationPath, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            val outputStream = FileOutputStream(file)
                            tarInputStream.copyTo(outputStream)
                            outputStream.close()
                        }
                        entry = tarInputStream.nextTarEntry
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        //mainViewModel.dispatchEvent(ViewModelEvent.InstalledInitArchive)
                    }


                } catch (e: IOException) {
                    e.printStackTrace()

                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            activity.applicationContext,
                            "Error installing init archive. Please Reinstall this app.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } finally {
                    try {
                        tarInputStream?.close()

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}

