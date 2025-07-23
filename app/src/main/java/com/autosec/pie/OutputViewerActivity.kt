package com.autosec.pie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.autosec.pie.autopieapp.presentation.screens.CommandExtrasBottomSheet
import com.autosec.pie.autopieapp.presentation.screens.OutputViewerBottomSheet
import com.autosec.pie.autopieapp.presentation.viewModels.OutputViewerViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.autosec.pie.ui.theme.AutoPieTheme
import com.autosec.pie.utils.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

class OutputViewerActivity : ComponentActivity() {


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        Timber.d(this.intent.toString())

        val outputPath = intent.getStringExtra("logFile")
        val commandName = intent.getStringExtra("commandName")

        Timber.d("Received data - $commandName ,  $outputPath")

        setContent {

            val activity = LocalContext.current.getActivity()

            val outputViewerViewModel: OutputViewerViewModel = koinViewModel()

            val scope = rememberCoroutineScope()

            DisposableEffect(outputPath) {

                scope.launch {
                    delay(100L)
                    if(outputPath != null){
                        Timber.d("Fetching output from $outputPath")
                        outputViewerViewModel.currentLogPath.value = outputPath
                        outputViewerViewModel.currentCommandName.value = commandName ?: ""
                        outputViewerViewModel.getOutputFromFile(outputPath)
                    }
                }

                onDispose {
                    Timber.d("Unsetting current command $outputPath")
                    outputViewerViewModel.currentLogPath.value = null
                    outputViewerViewModel.currentCommandName.value = ""
                }
            }


            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
                it != SheetValue.Hidden
            })

            val extrasBottomSheetState = rememberModalBottomSheetState(true, confirmValueChange = { it != SheetValue.Hidden })
            val extrasBottomSheetStateOpen = remember {
                mutableStateOf(false)
            }

            LaunchedEffect(outputViewerViewModel.currentLogPath.value) {
                extrasBottomSheetStateOpen.value = outputViewerViewModel.currentLogPath.value != null
            }


            AutoPieTheme {
                if (extrasBottomSheetStateOpen.value) {
                    OutputViewerBottomSheet(
                        state = extrasBottomSheetState,
                        open = extrasBottomSheetStateOpen,
                        state,
                        callerName = "LOG_VIEWER"
                    )
                }

            }
        }
    }

}