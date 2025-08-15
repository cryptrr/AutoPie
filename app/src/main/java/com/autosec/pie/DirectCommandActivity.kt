package com.autosec.pie

import android.app.Activity
import android.app.ComponentCaller
import com.autosec.pie.autopieapp.domain.ViewModelError

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.AutoPieLogo
import com.autosec.pie.autopieapp.presentation.elements.SearchBar
import com.autosec.pie.autopieapp.presentation.screens.CommandExtrasBottomSheet
import com.autosec.pie.ui.theme.AutoPieTheme
import com.autosec.pie.utils.Utils.Companion.getPathsFromClipData
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import com.autosec.pie.utils.getActivity
import org.koin.androidx.compose.koinViewModel
import java.io.File


class DirectCommandActivity : ComponentActivity() {


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        Timber.d(this.intent.toString())
        Timber.d(this.intent.extras.toString())

        val commandId = intent.getStringExtra("commandId")
        val input = intent.getStringExtra("input")
        val isAsync = intent.getBooleanExtra("async", true)
        val callerPackage = callingPackage
            ?: referrer?.authority ?: ""

        val callerType = when{
            callerPackage.contains("launcher") -> "DIRECT_ICON"
            callerPackage == "com.autosec.pie" -> "DIRECT_ICON"
            else -> "EXTERNAL_APP"
        }

        Timber.d("Calling package: $callerPackage")




        setContent {



            //val activity = LocalContext.current.getActivity()

            val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()

            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                shareReceiverViewModel.main.eventFlow.collect{
                    when(it){
                        //For asynchronous requests
                        is ViewModelEvent.CommandStarted -> {

                            try {
                                val uri = FileProvider.getUriForFile(this@DirectCommandActivity, "${this@DirectCommandActivity.packageName}.fileprovider",
                                    File(it.logFile)
                                )

                                if(callerType == "EXTERNAL_APP" && isAsync){
                                    delay(900)
                                    val result = Intent().apply {
                                        putExtra("status", "running")
                                        putExtra("processId", it.processId)
                                        putExtra("logFile", uri.toString())
                                    }
                                    setResult(RESULT_OK, result)
                                    finish()
                                    shareReceiverViewModel.currentExtrasDetails.value = null
                                }
                            }catch (e: Exception){
                                Timber.e(e)
                            }
                        }

                        //For synchronous requests
                        is ViewModelEvent.CommandCompleted -> {
                            try {
                                val uri = FileProvider.getUriForFile(this@DirectCommandActivity, "${this@DirectCommandActivity.packageName}.fileprovider",
                                    File(it.logFile)
                                )

                                val result = Intent().apply {
                                    putExtra("status", "ok")
                                    putExtra("processId", it.processId)
                                    putExtra("logFile", uri.toString())
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            }catch (e: Exception){
                                Timber.e(e)
                            }
                        }

                        is ViewModelEvent.CommandFailed -> {
                            try {
                                val uri = FileProvider.getUriForFile(this@DirectCommandActivity, "${this@DirectCommandActivity.packageName}.fileprovider",
                                    File(it.logFile)
                                )

                                val result = Intent().apply {
                                    putExtra("status", "failed")
                                    putExtra("processId", it.processId)
                                    putExtra("logFile", uri.toString())
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            }catch (e: Exception){
                                Timber.e(e)
                            }
                        }

                        else -> {}
                    }
                }
            }

            DisposableEffect(commandId) {
                //Get the command from the list

                scope.launch {
                    delay(100L)
                    if(commandId != null){
                        Timber.d("Setting command to $commandId")
                        val success = shareReceiverViewModel.selectCommandFromDirectActivity(commandId,input,callerType, this@DirectCommandActivity)
                    }
                }

                onDispose {
                    Timber.d("Unsetting current command $commandId")
                    shareReceiverViewModel.currentExtrasDetails.value = null
                }
            }


            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
                it != SheetValue.Hidden
            })

            val extrasBottomSheetState = rememberModalBottomSheetState(true)
            val extrasBottomSheetStateOpen = remember {
                mutableStateOf(false)
            }

            LaunchedEffect(shareReceiverViewModel.currentExtrasDetails.value) {
                extrasBottomSheetStateOpen.value = shareReceiverViewModel.currentExtrasDetails.value != null
            }


            AutoPieTheme {

                    CommandExtrasBottomSheet(
                        state = extrasBottomSheetState,
                        open = extrasBottomSheetStateOpen,
                        state,
                        callerName = callerType,
                        isAsync = isAsync
                    )

            }
        }
    }

}






