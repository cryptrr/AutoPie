package com.autosec.pie.use_case

import android.os.Environment
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.InputParsedData
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.containsValidHttpUrl
import com.autosec.pie.utils.containsValidUrl
import com.autosec.pie.utils.extractAllUrls
import com.autosec.pie.utils.extractFirstUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.net.URL

class RunShareCommandForText(private val processManagerService: ProcessManagerService){
    operator fun invoke(item: CommandModel, text: String, fileUris: List<String>, commandExtraInputs: List<CommandExtraInput> = emptyList(), processId: Int) : Flow<Pair<Boolean, String>> {

        return flow {
            Timber.d("RunShareCommandForText")

            //TODO: Make this more robust
            val inputParsedData = mutableListOf<InputParsedData>().also {
                it.add(InputParsedData(name = "INPUT_TEXT", value = "\"$text\""))
                it.add(InputParsedData(name = "INPUT_FILE", value = "\"${if(text.containsValidUrl()) text.extractFirstUrl() else ""}\""))
                it.add(InputParsedData(name = "INPUT_FILES", value = "\"${if(text.containsValidUrl()) text.extractAllUrls() else ""}\""))
                it.add(InputParsedData(name = "INPUT_URL", value = "\"${if(text.containsValidHttpUrl()) text.extractFirstUrl() else ""}\""))
                it.add(InputParsedData(name = "INPUT_URLS", value = "\"${if(text.containsValidHttpUrl()) text.extractAllUrls() else ""}\""))
                it.add(InputParsedData(name = "RAND", value = (1000..9999).random().toString()))
            }

            Timber.d(if(item.exec.contains("ssh")) "SSH does not allow quoting in host strings. But autopie needs quoting for all env vars.\nThis is a hacky fix to turn off quoting for ssh commands" else "")
            val quotedCommandExtraInputs = if(!item.exec.contains("ssh")) commandExtraInputs.map{ it.copy(value = "\"${it.value}\"") } else commandExtraInputs

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


            val success = processManagerService.runCommandForShareWithEnv(item, fullExecPath, resultString, item.path,inputParsedData,quotedCommandExtraInputs,processId, usePython, isShellScript)

            emit(Pair(success, text))
        }
    }

}