package com.autosec.pie.use_case

import android.os.Environment
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.isValidUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.URL

class RunShareCommandForUrl(private val processManagerService: ProcessManagerService){
    suspend operator fun invoke(item: CommandModel, currentLink: String, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<Pair<Boolean, String>> {

        return flow {
            Timber.d("runShareCommandForUrl")


            val inputUrl = URL(currentLink)

            val host = inputUrl.host

            val filename = inputUrl.file

            val inputParsedData = mutableListOf<InputParsedData>().also {
                it.add(InputParsedData(name = "INPUT_FILE", value = "\"$currentLink\""))
                it.add(InputParsedData(name = "INPUT_URL", value = "\"$currentLink\""))
                it.add(InputParsedData(name = "HOST", value = "\"$host\""))
                it.add(InputParsedData(name = "FILENAME", value = "\"$filename\""))
                it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
            }


            //val resultString = "\"${item.command.replace("{INPUT_FILE}", "'$currentLink'")}\""
            val resultString = "\"${item.command}\""


            val execFilePath =
                Environment.getExternalStorageDirectory().absolutePath + "/AutoSec/bin/" + item.exec

            val fullExecPath = when{
                File(item.exec).isAbsolute -> {
                    item.exec
                }
                File(execFilePath).exists() -> {
                    //For packages installed inside autosec/bin
                    execFilePath
                }
                else -> {
                    //Base case fallback to terminal installed packages such as busybox packages.
                    item.exec
                }
            }

            val isShellScript = Utils.isShellScript(File(fullExecPath))
            val usePython = Utils.isZipFile(File(fullExecPath))


            Timber.d("Command to run: ${item.exec} $resultString")


            val success = processManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,inputParsedData,commandExtraInputs,processId, usePython, isShellScript)

            emit(Pair(success, currentLink))
        }
    }

}
