package com.autosec.pie

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


class DirectCommandActivity : ComponentActivity() {

    override fun onDestroy() {
        Timber.d("DirectCommandActivity Destroyed")
        super.onDestroy()
    }

    override fun onPause() {
        Timber.d("DirectCommandActivity Paused")
        super.onPause()
    }

    override fun onStop() {
        Timber.d("DirectCommandActivity Stopped")

        super.onStop()
    }



    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        Timber.d(this.intent.toString())
        Timber.d(this.intent.extras.toString())

        val commandId = intent.getStringExtra("commandId")

        setContent {

            val activity = LocalContext.current.getActivity()

            val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()

            val scope = rememberCoroutineScope()

            DisposableEffect(commandId) {
                //Get the command from the list

                scope.launch {
                    delay(250L)
                    if(commandId != null){
                        Timber.d("Setting command to $commandId")
                        val success = shareReceiverViewModel.selectCommandFromDirectActivity(commandId)
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

                if (extrasBottomSheetStateOpen.value) {
                    CommandExtrasBottomSheet(
                        state = extrasBottomSheetState,
                        open = extrasBottomSheetStateOpen,
                        state,
                        callerName = "DIRECT_ICON"
                    )
                }

            }
        }
    }


}






