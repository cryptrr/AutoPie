package com.autopi

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autopi.autopieapp.domain.AppNotification
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.elements.AppBottomBar
import com.autopi.autopieapp.presentation.elements.AutoPieLogo
import com.autopi.autopieapp.presentation.elements.SnackbarHostCustom
import com.autopi.autopieapp.presentation.elements.YesNoMultiCheckboxDialog
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.presentation.screens.AddShareCommandBottomSheet
import com.autopi.autopieapp.presentation.screens.CloudCommandDetails
import com.autopi.autopieapp.presentation.screens.CloudPackageDetails
import com.autopi.autopieapp.presentation.screens.CommandExtrasBottomSheet
import com.autopi.autopieapp.presentation.screens.EditCommandBottomSheet
import com.autopi.autopieapp.presentation.screens.HomeScreen
import com.autopi.autopieapp.presentation.screens.InstallNewPackageBottomSheet
import com.autopi.autopieapp.presentation.screens.InstalledScreen
import com.autopi.autopieapp.presentation.screens.SettingsScreen
import com.autopi.autopieapp.data.services.AutoPieCoreService
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.presentation.screens.CommandDetailsSheet
import com.autopi.autopieapp.presentation.screens.CommandHistorySheet
import com.autopi.ui.theme.AutoPieTheme
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.autopieapp.presentation.viewModels.ShareReceiverViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val autoPieNotification: AutoPieNotification by KoinJavaComponent.inject(
        AutoPieNotification::class.java)
    private val processManagerService: ProcessManagerService by KoinJavaComponent.inject(
        ProcessManagerService::class.java)


    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        autoPieNotification.requestNotificationPermission(this)
        autoPieNotification.createNotificationChannel()

        setContent {

            val mainViewModel: MainViewModel by inject(MainViewModel::class.java)

            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

            val autoPieStates = rememberAutoPieStates()
            val context = LocalContext.current


            AutoPieTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),

                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        val selectedItem = remember { mutableIntStateOf(0) }
                        val fabVisible = remember { mutableStateOf(true) }
                        val fabScrollConnection = remember {
                            object : NestedScrollConnection {
                                override fun onPreScroll(
                                    available: Offset,
                                    source: NestedScrollSource
                                ): Offset {
                                    if (selectedItem.intValue == 0) {
                                        when {
                                            available.y < -1f -> fabVisible.value = false
                                            available.y > 1f -> fabVisible.value = true
                                        }
                                    }
                                    return Offset.Zero
                                }
                            }
                        }

                        LaunchedEffect(selectedItem.intValue) {
                            if (selectedItem.intValue == 0) {
                                fabVisible.value = true
                            }
                        }

                        Scaffold(
                            modifier = Modifier
                                .nestedScroll(fabScrollConnection)
                                .nestedScroll(scrollBehavior.nestedScrollConnection),

                            snackbarHost = {
                                SnackbarHostCustom()
                            },
                            topBar = {
                                LargeTopAppBar(
                                    title = {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AutoPieLogo()
//                                            IconButton(
//                                                onClick = { /*TODO*/ }, modifier = Modifier
//                                                    .clip(
//                                                        RoundedCornerShape(10.dp)
//                                                    )
//                                                    .padding(end = 17.dp)
//                                            ) {
//                                                Icon(
//                                                    imageVector = Icons.Outlined.Settings,
//                                                    contentDescription = "Settings",
//                                                    tint = MaterialTheme.colorScheme.onSurface,
//                                                    modifier = Modifier.size(28.dp)
//                                                )
//                                            }
                                        }
                                    },
                                    //colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                                    navigationIcon = {
//                                        IconButton(
//                                            modifier = Modifier
//                                                .padding(4.dp),
//                                            onClick = {}
//                                        ) {
//                                            Icon(
//                                                modifier = Modifier
//
//                                                    .size(27.dp),
//                                                imageVector = Icons.Default.Menu,
//                                                contentDescription = "Search",
//                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
//                                            )
//                                        }
                                    },

                                    scrollBehavior = scrollBehavior
                                )
                            },
                            bottomBar = {
                                AppBottomBar(selectedItem)
                            },
                            floatingActionButtonPosition = FabPosition.End,
                            floatingActionButton = {
                                when (selectedItem.intValue) {
                                    0 -> {
                                        AnimatedVisibility(
                                            visible = fabVisible.value,
                                            enter = fadeIn() + scaleIn(),
                                            exit = fadeOut() + scaleOut()
                                        ) {
                                            ExtendedFloatingActionButton(
                                                onClick = {
                                                    mainViewModel.viewModelScope.launch {
                                                        autoPieStates.addShareBottomSheetStateOpen.value = true
                                                    }
                                                },
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = null
                                                    )
                                                },
                                                text = { Text("Create", fontSize = 15.7.sp) }
                                            )
                                        }
                                    }

                                    else -> {}
                                }

                            },
                            content = { innerPadding ->
                                when {
                                    !mainViewModel.storageManagerPermissionGranted -> {
                                        RequestManageStoragePermission(
                                            context = this@MainActivity,
                                            innerPadding
                                        )
                                    }

                                    selectedItem.intValue == 0 -> HomeScreen(
                                        innerPadding = innerPadding,
                                        onInstallNewClick = {
                                            selectedItem.intValue = 1
                                        }
                                    )
                                    selectedItem.intValue == 1 -> InstalledScreen(innerPadding)
                                    selectedItem.intValue == 2 -> SettingsScreen(innerPadding)
                                    else -> {}
                                }
                            }
                        )


                    }

                    YesNoMultiCheckboxDialog(
                        showDialog = mainViewModel.installInitPackagesPrompt &&
                            mainViewModel.storageManagerPermissionGranted,
                        title = "Do you want to install init packages and its commands?",
                        subtitle = "",
                        options = mainViewModel.initPackageCommandKeywords,
                        onYesClicked = { selectedKeywords ->
                            mainViewModel.installInitPackagesPrompt = false
                            mainViewModel.installCloudCommandsForKeywords(selectedKeywords)
                        },
                        onNoClicked = {
                            mainViewModel.installInitPackagesPrompt = false
                            mainViewModel.markInitPackageCommandsPromptHandled()
                        },
                        onDismissRequest = {
                            mainViewModel.installInitPackagesPrompt = false
                            mainViewModel.markInitPackageCommandsPromptHandled()
                        }
                    )


                    if (autoPieStates.addShareBottomSheetStateOpen.value) {
                        AddShareCommandBottomSheet(
                            state = autoPieStates.addShareBottomSheetState,
                            open = autoPieStates.addShareBottomSheetStateOpen
                        )
                    }
                    if (autoPieStates.installNewPackageBottomSheetOpen.value) {
                        InstallNewPackageBottomSheet(
                            state = autoPieStates.installNewPackageBottomSheet,
                            open = autoPieStates.installNewPackageBottomSheetOpen
                        )
                    }
                    if (autoPieStates.editCommandBottomSheetOpen.value) {
                        EditCommandBottomSheet(
                            state = autoPieStates.editCommandBottomSheet,
                            open = autoPieStates.editCommandBottomSheetOpen,
                            key = mainViewModel.currentCommandKey.value
                        )
                    }
                    if (autoPieStates.runCommandBottomSheetStateOpen.value) {
                        CommandExtrasBottomSheet(
                            state = autoPieStates.runCommandBottomSheetState,
                            open = autoPieStates.runCommandBottomSheetStateOpen,
                            isAsync = true
                        )
                    }
                    if (autoPieStates.cloudCommandDetailsBottomSheetOpen.value) {
                        CloudCommandDetails(
                            state = autoPieStates.cloudCommandDetailsBottomSheet,
                            open = autoPieStates.cloudCommandDetailsBottomSheetOpen,
                        )
                    }
                    if (autoPieStates.cloudPackageDetailsBottomSheetOpen.value) {
                        CloudPackageDetails(
                            state = autoPieStates.cloudPackageDetailsBottomSheet,
                            open = autoPieStates.cloudPackageDetailsBottomSheetOpen,
                        )
                    }

                    if (autoPieStates.commandDetailsBottomSheetOpen.value) {
                        CommandDetailsSheet(
                            state = autoPieStates.commandDetailsBottomSheet,
                            open = autoPieStates.commandDetailsBottomSheetOpen,
                        )
                    }

                    if (autoPieStates.commandHistoryBottomSheetOpen.value) {
                        CommandHistorySheet(
                            state = autoPieStates.commandHistoryBottomSheet,
                            open = autoPieStates.commandHistoryBottomSheetOpen,
                        )
                    }

                }
            }
        }
    }

}
