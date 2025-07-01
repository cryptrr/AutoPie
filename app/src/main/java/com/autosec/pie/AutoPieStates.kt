package com.autosec.pie

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
data class AutoPieStates(
    val addShareBottomSheetState: SheetState,
    val addShareBottomSheetStateOpen: MutableState<Boolean>,

    val commandsSearchBottomSheetState: SheetState,
    val commandsSearchBottomSheetStateOpen: MutableState<Boolean>,

    val installNewPackageBottomSheet: SheetState,
    val installNewPackageBottomSheetOpen: MutableState<Boolean>,

    val runCommandBottomSheetState: SheetState,
    val runCommandBottomSheetStateOpen: State<Boolean>,

    val cloudCommandDetailsBottomSheet: SheetState,
    val cloudCommandDetailsBottomSheetOpen: MutableState<Boolean>,

    val cloudPackageDetailsBottomSheet: SheetState,
    val cloudPackageDetailsBottomSheetOpen: MutableState<Boolean>,

    val editCommandBottomSheet: SheetState,
    val editCommandBottomSheetOpen: MutableState<Boolean>,

    val commandDetailsBottomSheet: SheetState,
    val commandDetailsBottomSheetOpen: MutableState<Boolean>,

    val currentCommandModel: MutableState<CommandModel?>


)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAutoPieStates(): AutoPieStates{

    val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()

    val addShareBottomSheetState = rememberModalBottomSheetState(true,confirmValueChange = {
        it != SheetValue.Hidden
    })
    val addShareBottomSheetStateOpen = rememberSaveable { mutableStateOf(false) }

    val commandsSearchBottomSheetState = rememberModalBottomSheetState(true)
    val commandsSearchBottomSheetStateOpen = rememberSaveable { mutableStateOf(false) }

    val installNewPackageBottomSheet = rememberModalBottomSheetState(true)
    val installNewPackageBottomSheetOpen = rememberSaveable { mutableStateOf(false) }

    val runCommandBottomSheetState = rememberModalBottomSheetState(true)
    val runCommandBottomSheetStateOpen = remember {
        derivedStateOf { shareReceiverViewModel.currentExtrasDetails.value != null }
    }

    val cloudCommandDetailsBottomSheet = rememberModalBottomSheetState(true,confirmValueChange = {
        it != SheetValue.Hidden
    })
    val cloudCommandDetailsBottomSheetOpen = rememberSaveable { mutableStateOf(false) }

    val cloudPackageDetailsBottomSheet = rememberModalBottomSheetState(true,confirmValueChange = {
        it != SheetValue.Hidden
    })
    val cloudPackageDetailsBottomSheetOpen = rememberSaveable { mutableStateOf(false) }


    val composableScope = rememberCoroutineScope()



    val editCommandBottomSheet = rememberModalBottomSheetState(true,confirmValueChange = { state ->
        state != SheetValue.Hidden
    })


    val editCommandBottomSheetOpen = rememberSaveable { mutableStateOf(false) }

    val commandDetailsBottomSheet = rememberModalBottomSheetState(true)

    val commandDetailsBottomSheetOpen = rememberSaveable { mutableStateOf(false) }

    val currentCommandModel = rememberSaveable { mutableStateOf<CommandModel?>(null) }

    //STUPID BOTTOMSHEETSTATE NEVER GOES TO HIDDEN AFTER OPENED
    //HAHA GET REKT, JETPACK COMPOSE BOTTOMSHEETSTATE!
    LaunchedEffect(key1 = editCommandBottomSheetOpen, editCommandBottomSheet.targetValue) {
        var showInfoJob : Job? = null

        if(editCommandBottomSheet.targetValue == SheetValue.Hidden) {
            composableScope.launch {
                delay(500L)
                if(!editCommandBottomSheetOpen.value){
                    showInfoJob?.cancel().also {
                        Timber.d("showInfoJob canceled")
                    }
                }
            }
            showInfoJob = composableScope.launch {
                delay(600L)
                mainViewModel.showNotification(AppNotification.ShowCloseSheetInfo)
            }
        }
    }

    LaunchedEffect(key1 = addShareBottomSheetStateOpen, addShareBottomSheetState.targetValue) {
        var showInfoJob : Job? = null

        if(addShareBottomSheetState.targetValue == SheetValue.Hidden) {
            composableScope.launch {
                delay(500L)
                if(!addShareBottomSheetStateOpen.value){
                    showInfoJob?.cancel().also {
                        Timber.d("showInfoJob canceled")
                    }
                }
            }
            showInfoJob = composableScope.launch {
                delay(600L)
                mainViewModel.showNotification(AppNotification.ShowCloseSheetInfo)
            }
        }
    }



    LaunchedEffect(key1 = Unit) {

        mainViewModel.eventFlow.collect{
            when(it){
                is ViewModelEvent.OpenEditCommandSheet -> {
                    editCommandBottomSheetOpen.value = true
                }
                is ViewModelEvent.OpenCommandDetails -> {
                    commandDetailsBottomSheetOpen.value = true
                    currentCommandModel.value = it.card
                }
                is ViewModelEvent.OpenCloudCommandDetails -> {
                    cloudCommandDetailsBottomSheetOpen.value = true
                }
                is ViewModelEvent.OpenCloudPackageDetails -> {
                    cloudPackageDetailsBottomSheetOpen.value = true
                }
                else -> {}
            }
        }
    }

    return AutoPieStates(
        editCommandBottomSheet = editCommandBottomSheet,
        commandsSearchBottomSheetState = commandsSearchBottomSheetState,
        runCommandBottomSheetStateOpen = runCommandBottomSheetStateOpen,
        runCommandBottomSheetState = runCommandBottomSheetState,
        cloudCommandDetailsBottomSheet = cloudCommandDetailsBottomSheet,
        cloudCommandDetailsBottomSheetOpen = cloudCommandDetailsBottomSheetOpen,
        addShareBottomSheetState = addShareBottomSheetState,
        addShareBottomSheetStateOpen = addShareBottomSheetStateOpen,
        commandsSearchBottomSheetStateOpen = commandsSearchBottomSheetStateOpen,
        editCommandBottomSheetOpen = editCommandBottomSheetOpen,
        installNewPackageBottomSheetOpen = installNewPackageBottomSheetOpen,
        installNewPackageBottomSheet = installNewPackageBottomSheet,
        cloudPackageDetailsBottomSheet = cloudPackageDetailsBottomSheet,
        cloudPackageDetailsBottomSheetOpen = cloudPackageDetailsBottomSheetOpen,
        commandDetailsBottomSheet = commandDetailsBottomSheet,
        commandDetailsBottomSheetOpen = commandDetailsBottomSheetOpen,
        currentCommandModel = currentCommandModel
    )
}