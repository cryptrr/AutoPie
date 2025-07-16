package com.autosec.pie.autopieapp.data.services

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import com.autosec.pie.autopieapp.data.AutoPieConstants
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.YesNoDialog
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.core.DispatcherProvider
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

class AutoPieCoreService {


    companion object {
        val application: Application by inject(Context::class.java)
        private val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
        val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
        private val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)



        fun initAutosec() {

            CoroutineScope(dispatchers.io).launch{
                val autosecFolderExists = checkForAutoSecFolder()
                val binFolderExists = checkForBinFolder()

                if (mainViewModel.storageManagerPermissionGranted && !autosecFolderExists) {
                    Timber.d("Autosec folder does not exist. Creating and copying files")
                    createAutoSecFolder()
                    createLogsFolder()

                } else {
                    Timber.d("Autosec folder exists. Doing nothing.")
                }

                if (mainViewModel.storageManagerPermissionGranted && !binFolderExists) {
                    Timber.d("Starting fetching init files")
                    //downloadAndExtractAutoSecInitArchive()

                    mainViewModel.installInitPackagesPrompt = true

                } else {
                    Timber.d("Bin folder exists. Doing nothing.")
                }
            }


        }


        fun extractAndExecuteBinary(context: Application) {


            val binaries = listOf("busybox", "ssl_helper", "env.sh")


            CoroutineScope(dispatchers.io).launch {

                if (processManagerService.checkShell()) {
                    Timber.d("Shellcheck: Shell is installed correctly")
                    return@launch
                }

                Timber.d("Shellcheck: Shell is not installed")


                for (binary in binaries) {
                    try {
                        Timber.d("Trying to copy: $binary")
                        val inputStream: InputStream = context.assets.open(binary)
                        val binDirectory = File(context.filesDir, "/")

                        if (binDirectory.exists() && binDirectory.isDirectory) {
                            Timber.d("Bin directory exists: ${binDirectory.absolutePath}")
                            Timber.d("Doing Nothing")

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


        fun extractTarXzFromAssets(context: Application) {

            val distFolder = File(context.filesDir, "build")

            if (distFolder.exists() && distFolder.isDirectory) {
                Timber.d("Dist folder exists")
                return
            }

            CoroutineScope(dispatchers.io).launch {

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

                    CoroutineScope(dispatchers.main).launch {
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

                    //Make sure the binary folder files are executable. Found it necessary for some busybox binaries.

                    processManagerService.makeBinariesFolderExecutable()

                    CoroutineScope(dispatchers.main).launch {
                        mainViewModel.dispatchEvent(ViewModelEvent.InstalledPythonSuccessfully)
                        mainViewModel.showNotification(AppNotification.InstallingPythonPackagesSuccess)
                        //Toast.makeText(activity.applicationContext, "Python installation complete", Toast.LENGTH_LONG).show()
                    }

                    installOtherPackages()


                } catch (e: IOException) {
                    Timber.e(e)

                    CoroutineScope(dispatchers.main).launch {
                        Toast.makeText(
                            application.applicationContext,
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

        fun checkForAutoSecFolder(): Boolean {
            val autoSecFolder =
                File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")

            return autoSecFolder.exists()
        }

        fun checkForBinFolder(): Boolean {
            val binFolder =
                File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin")

            return binFolder.exists()
        }

        fun createAutoSecFolder() {
            Timber.d("Creating AutoSec Folder")
            val autoSecFolder =
                File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")

            autoSecFolder.mkdir()

        }

        fun createLogsFolder() {
            Timber.d("Creating Logs Folder")

            val logsFolder =
                File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/logs")

            logsFolder.mkdir()

        }



        fun downloadFile(url: String, directory: File, filename: String) {

            val destinationFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                filename
            )

            val request = DownloadManager.Request(Uri.parse(url))

            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)

            request.setTitle("Downloading Init File")
            //request.setDescription("Downloading file.zip")

            //request.setDestinationUri(Uri.fromFile(destinationFile))

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

            if (destinationFile.exists()) {
                Timber.d("File already downloaded")
                return
            }


            try {
                val downloadManager =
                    getSystemService(application, DownloadManager::class.java) as DownloadManager

                val downloadId = downloadManager.enqueue(request)

                Timber.d("")

            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun downloadAndExtractAutoSecInitArchive() {

            Timber.d("Downloading Init Archive")


            CoroutineScope(dispatchers.io).launch {

                val autoSecFolder =
                    File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")

                val appDataFolder =
                    File(application.filesDir.absolutePath)

                if (autoSecFolder.exists()) {
                    mainViewModel.showNotification(AppNotification.DownloadingInitPackages)

                    //ProcessManagerService.runWget(AutoPieConstants.AUTOPIE_INIT_ARCHIVE_URL, Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz")
                    val isDownloaded = processManagerService.downloadFileWithPython(
                        AutoPieConstants.AUTOPIE_INIT_ARCHIVE_URL,
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz"
                    )

                    if (isDownloaded) {
                        mainViewModel.showNotification(AppNotification.DownloadedInitPackages)
                        extractAutoSecFiles()
                    }
                }
            }
        }

        fun downloadAndExtractAutoSecEmptyInit() {

            Timber.d("Downloading Empty Init")


            CoroutineScope(dispatchers.io).launch {

                val autoSecFolder =
                    File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")


                if (autoSecFolder.exists()) {

                    val isDownloaded = processManagerService.downloadFileWithPython(
                        AutoPieConstants.AUTOPIE_EMPTY_INIT_ARCHIVE_URL,
                        Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz"
                    )

                    if (isDownloaded) {
                        extractAutoSecFiles()
                    }
                }
            }
        }

        fun installOtherPackages() {

            CoroutineScope(dispatchers.io).launch {

                val distFolder = File(application.filesDir, "build")

                if (distFolder.exists()) {

                    processManagerService.linkPython3()

                    processManagerService.installPip()

                    //Installing the forked version of httpx that supports --cookie-file
                    //Both these calls are necessary
                    processManagerService.pipInstallPackage("https://github.com/cryptrr/httpx/raw/refs/heads/master/dist/httpx-0.28.1-py3-none-any.whl")
                    processManagerService.pipInstallPackage("httpx[cli]")
                }
            }
        }

        fun createEmptyCookieFile() {

            if(!mainViewModel.storageManagerPermissionGranted){
                Timber.d("Storage permission not granted")

                return
            }

            CoroutineScope(dispatchers.io).launch {
                val cookieFile =
                    File(Environment.getExternalStorageDirectory().absolutePath + "/cookies.txt")

                if(!cookieFile.exists()){
                    cookieFile.createNewFile()
                    val textToWrite = """
                        # Netscape HTTP Cookie File
                        # Generated by Cyotek WebCopy v1.9.1.0 on 2023-03-13T10:13:33
                        # Edit at your own risk.
                        
                        demo.cyotek.com	FALSE	/features/	FALSE	1678706015	CrawlDemo_Path	gamma
                        demo.cyotek.com	FALSE	/	FALSE	1678706015	CrawlDemo_Domain	delta
                    """.trimIndent()

                    cookieFile.writeText(textToWrite)
                }
            }
        }

        private fun isFileCompletelyDownloaded(
            filePath: String,
            timeoutMillis: Long = 25000
        ): Boolean {
            val file = File(filePath)

            // Check if the file exists
            if (!file.exists()) {
                Timber.d("File does not exist $filePath")
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

            Timber.d("extractAutoSecFiles()")

            val autoSecFolder =
                File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec")
            val initArchiveFile =
                File(Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz")



            if (!autoSecFolder.exists() && !autoSecFolder.isDirectory) {
                Timber.d("AutoSec folder does not exist")
                return
            }

            if (!initArchiveFile.exists()) {
                Timber.d("AutoSec init file not downloaded")
                return
            }


            CoroutineScope(dispatchers.io).launch {

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

                    CoroutineScope(dispatchers.main).launch {
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

                    CoroutineScope(dispatchers.main).launch {
                        mainViewModel.dispatchEvent(ViewModelEvent.RefreshCommandsList)

                        initArchiveFile.delete()

                    }


                } catch (e: IOException) {
                    Timber.e(e)

                    CoroutineScope(dispatchers.main).launch {

                        mainViewModel.showNotification(AppNotification.FailedDownloadingArchive)
                    }

                } finally {
                    try {
                        tarInputStream?.close()

                    } catch (e: IOException) {
                        Timber.e(e)
                    }
                }
            }
        }


    }
}

