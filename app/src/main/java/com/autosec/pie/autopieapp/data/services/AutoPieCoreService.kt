package com.autopi.autopieapp.data.services

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.UserManager
import android.system.Os
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import com.autopi.autopieapp.data.AutoPieConstants
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.domain.AppNotification
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DispatcherProvider
import com.termux.app.TermuxActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AutoPieCoreService {


    companion object {
        val application: Application by inject(Context::class.java)
        private val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
        val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
        private val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)
        private val autoPieConfigPathProvider: AutoPieConfigPathProvider by inject(AutoPieConfigPathProvider::class.java)
        private var termuxBootstrapTriggered = false

        fun ensureTermuxBootstrapTriggered(context: Context) {
            if (isTermuxBootstrapInstalled(context) || termuxBootstrapTriggered) {
                return
            }

            termuxBootstrapTriggered = true

            try {
                Timber.d("Termux bootstrap missing. Launching TermuxActivity to trigger setup.")
                val intent = Intent(context, TermuxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                termuxBootstrapTriggered = false
                Timber.e(e, "Failed to launch TermuxActivity for bootstrap setup")
            }
        }

        fun isTermuxBootstrapInstalled(context: Context): Boolean {
            return File(context.filesDir, "usr/bin/bash").exists()
        }


        fun initAutosec() {

            try {
                CoroutineScope(dispatchers.io).launch{
                    val autosecFolderExists = checkForAutoSecFolder()
                    val binFolderExists = checkForBinFolder()
                    val canAccessConfigPath =
                        !autoPieConfigPathProvider.usesExternalStorage() || mainViewModel.storageManagerPermissionGranted

                    if (canAccessConfigPath && !autosecFolderExists) {
                        Timber.d("Autosec folder does not exist. Creating and copying files")
                        createAutoSecFolder()
                        createLogsFolder()

                    } else {
                        Timber.d("Autosec folder exists. Doing nothing.")
                    }

                    if (canAccessConfigPath && !binFolderExists) {
                        Timber.d("Starting fetching init files")
                        downloadAndExtractAutoSecInitArchive()
                        //mainViewModel.installInitPackagesPrompt = true

                    } else {
                        Timber.d("Bin folder exists. Doing nothing.")
                    }
                }
            }catch (e: Exception){
                Timber.e(e)
            }


        }


        fun extractRequiredFilesAndMakeExec(context: Application) {


            val binaries = if(isPrimaryUser(context)) listOf("env.sh", "env-shell.sh") else listOf("env.sh","env-shell.sh")

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
                        val homeFolder = File(context.filesDir, "home")

                        if(!homeFolder.exists()){
                            homeFolder.mkdir()
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting and executing binary")
                        //Toast.makeText(this@MainActivity, "Error extracting and executing binary", Toast.LENGTH_SHORT).show()
                    }

                }

            }
        }


        fun extractBootstrapArchive(context: Application) {

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
            try {
                val autoSecFolder = autoPieConfigPathProvider.getAutoSecDirectory()

                return autoSecFolder.exists()
            }catch (e: Exception){
                Timber.e(e)
                return false
            }
        }

        fun checkForBinFolder(): Boolean {
            try {
                val binFolder = autoPieConfigPathProvider.getBinDirectory()

                return binFolder.exists()
            }catch (e: Exception){
                Timber.e(e)
                return false
            }
        }

        fun createAutoSecFolder() {
            try {
                Timber.d("Creating AutoSec Folder")
                val autoSecFolder = autoPieConfigPathProvider.getAutoSecDirectory()

                autoSecFolder.mkdirs()
            }catch (e: Exception){
                Timber.e(e)
            }

        }

        fun createLogsFolder() {
            Timber.d("Creating Logs Folder")

            try {
                val logsFolder = autoPieConfigPathProvider.getLogsDirectory()

                logsFolder.mkdirs()
            }catch (e: Exception){
                Timber.e(e)
            }

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

        private fun downloadFileNatively(url: String, fullFilePath: String): Boolean {
            Timber.d("Downloading file natively")

            return try {
                val destinationFile = File(fullFilePath)
                val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.download")

                destinationFile.parentFile?.mkdirs()

                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }

                try {
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        Timber.e("Download failed with HTTP $responseCode for $url")
                        return false
                    }

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (destinationFile.exists() && !destinationFile.delete()) {
                        Timber.e("Unable to replace existing file ${destinationFile.absolutePath}")
                        tempFile.delete()
                        return false
                    }

                    if (!tempFile.renameTo(destinationFile)) {
                        Timber.e("Unable to move downloaded file to ${destinationFile.absolutePath}")
                        tempFile.delete()
                        return false
                    }

                    true
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e)
                false
            }
        }

        fun downloadAndExtractAutoSecInitArchive() {

            Timber.d("Downloading Init Archive")

            CoroutineScope(dispatchers.io).launch {

               try {
                   val autoSecFolder = autoPieConfigPathProvider.getAutoSecDirectory()

                   val versionFile = File(autoSecFolder, "version.txt")

                   val versionText = AutoPieConstants.AUTOPIE_INIT_ARCHIVE_URL.split("/").takeLast(2).joinToString("/")

                   if (autoSecFolder.exists()) {
                       mainViewModel.showNotification(AppNotification.DownloadingInitPackages)

                       //ProcessManagerService.runWget(AutoPieConstants.AUTOPIE_INIT_ARCHIVE_URL, Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/autosec.tar.xz")
                       val isDownloaded = downloadFileNatively(
                           AutoPieConstants.AUTOPIE_INIT_ARCHIVE_URL,
                           autoPieConfigPathProvider.getInitArchiveFile().absolutePath
                       )

                       if (isDownloaded) {
                           mainViewModel.showNotification(AppNotification.DownloadedInitPackages)

                           versionFile.writeText(versionText)

                           extractAutoSecFiles()
                       }else{
                           mainViewModel.showError(ViewModelError.ErrorDownloadingInitPackages)
                       }
                   }
               }catch (e: Exception){
                   Timber.e(e)
               }
            }
        }

        fun downloadAndExtractAutoSecEmptyInit() {

            Timber.d("Downloading Empty Init")


            CoroutineScope(dispatchers.io).launch {

                val autoSecFolder = autoPieConfigPathProvider.getAutoSecDirectory()


                if (autoSecFolder.exists()) {

                    val isDownloaded = processManagerService.downloadFileWithWCurl(
                        AutoPieConstants.AUTOPIE_EMPTY_INIT_ARCHIVE_URL,
                        autoPieConfigPathProvider.getInitArchiveFile().absolutePath
                    )

                    if (isDownloaded) {
                        extractAutoSecFiles()
                    }
                }
            }
        }

        //TODO: Newer version is in previous commit.
        fun installOtherPackages() {

            CoroutineScope(dispatchers.io).launch {

                try {
                    val distFolder = File(application.filesDir, "usr")

                    if (distFolder.exists()) {

                        // processManagerService.installPip()

                        //Installing the forked version of httpx that supports --cookie-file
                        //Both these calls are necessary
                        processManagerService.pipInstallPackage("https://github.com/cryptrr/httpx/raw/refs/heads/master/dist/httpx-0.28.1-py3-none-any.whl")
                        processManagerService.pipInstallPackage("httpx[cli]")
                    }
                }catch (e: Exception){
                    Timber.e(e,"installOtherPackages() failed")
                }
            }
        }

        fun createEmptyCookieFile() {

            if(!mainViewModel.storageManagerPermissionGranted){
                Timber.d("Storage permission not granted")

                return
            }

            CoroutineScope(dispatchers.io).launch {
                try {
                    val cookieFile =
                        File(application.filesDir.absolutePath + "/usr/var/lib/cookies.txt")

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
                }catch (e: Exception){
                    Timber.e(e)
                }
            }
        }

        fun fetchLatestRepositoryJson(){
            Timber.d("Fetching latest commands repository")
            CoroutineScope(dispatchers.io).launch {
                try {
                    val repositoryJsonFile = File(application.filesDir, "repolist.json")
                    val repositoryJsonNewFile = File(application.filesDir, "repolist.json.new")
                    val repositoryJsonBackupFile = File(application.filesDir, "repolist.json.bak")

                    if (repositoryJsonNewFile.exists() && !repositoryJsonNewFile.delete()) {
                        Timber.e("Unable to delete existing new repository file ${repositoryJsonNewFile.absolutePath}")
                        return@launch
                    }

                    val isDownloaded = downloadFileNatively(
                        AutoPieConstants.AUTOPIE_FULL_COMMANDS_REPO_URL,
                        repositoryJsonNewFile.absolutePath
                    )

                    if (!isDownloaded) {
                        Timber.e("Failed to fetch latest commands repository")
                        repositoryJsonNewFile.delete()
                        return@launch
                    }

                    if (repositoryJsonBackupFile.exists() && !repositoryJsonBackupFile.delete()) {
                        Timber.e("Unable to delete existing repository backup ${repositoryJsonBackupFile.absolutePath}")
                        repositoryJsonNewFile.delete()
                        return@launch
                    }

                    if (repositoryJsonFile.exists() && !repositoryJsonFile.renameTo(repositoryJsonBackupFile)) {
                        Timber.e("Unable to backup existing repository file ${repositoryJsonFile.absolutePath}")
                        repositoryJsonNewFile.delete()
                        return@launch
                    }

                    if (!repositoryJsonNewFile.renameTo(repositoryJsonFile)) {
                        Timber.e("Unable to move new repository file to ${repositoryJsonFile.absolutePath}")
                        if (!repositoryJsonFile.exists() && repositoryJsonBackupFile.exists()) {
                            repositoryJsonBackupFile.renameTo(repositoryJsonFile)
                        }
                        return@launch
                    }

                    Timber.d("Latest repository fetched successfully")
                }catch (e: Exception){
                    Timber.e(e)
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

        fun setAutoPieGraphics(){

            try {
                val bashFile =
                    File(application.filesDir.absolutePath + "/home/.bashrc")

                if(bashFile.exists()){
                    return
                }

                val content = """
                echo "     

      █████╗ ██╗   ██╗████████╗ ██████╗
     ██╔══██╗██║   ██║╚══██╔══╝██╔═══██╗
     ███████║██║   ██║   ██║   ██║   ██║
     ██╔══██║██║   ██║   ██║   ██║   ██║
     ██║  ██║╚██████╔╝   ██║   ╚██████╔╝
     ╚═╝  ╚═╝ ╚═════╝    ╚═╝    ╚═════╝


██████╗ ██╗███████╗
██╔══██╗██║██╔════╝
██████╔╝██║█████╗
██╔═══╝ ██║██╔══╝
██║     ██║███████╗
╚═╝     ╚═╝╚══════╝
            Now powered by Termux" 
            """.trimIndent()


                bashFile.createNewFile()
                bashFile.writeText(content)
            }catch (e: Exception){
                Timber.e(e)
            }
        }

        fun extractAutoSecFiles() {

            Timber.d("extractAutoSecFiles()")

            val autoSecFolder = autoPieConfigPathProvider.getAutoSecDirectory()
            val initArchiveFile = autoPieConfigPathProvider.getInitArchiveFile()



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

        fun isPrimaryUser(context: Context): Boolean {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            Timber.d("Is Primary User: ${userManager.isSystemUser}")
            return  userManager.isSystemUser
        }

    }
}
