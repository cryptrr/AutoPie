package com.autosec.pie

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.elements.AppBottomBar
import com.autosec.pie.elements.AutoPieLogo
import com.autosec.pie.elements.SnackbarHostCustom
import com.autosec.pie.screens.AddShareCommandBottomSheet
import com.autosec.pie.screens.CommandsSearchBottomSheet
import com.autosec.pie.screens.HomeScreen
import com.autosec.pie.screens.InstallNewPackageBottomSheet
import com.autosec.pie.screens.InstalledScreen
import com.autosec.pie.screens.SettingsScreen
import com.autosec.pie.services.BinaryCopierService
import com.autosec.pie.services.ProcessManagerService
import com.autosec.pie.ui.theme.AutoPieTheme
import com.autosec.pie.ui.theme.PastelGreen
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.viewModels.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        setContent {

            val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

            val addShareBottomSheetState = rememberModalBottomSheetState(true)
            val openBottomSheet = rememberSaveable { mutableStateOf(false) }

            val commandsSearchBottomSheetState = rememberModalBottomSheetState(true)
            val commandsSearchBottomSheetStateOpen = rememberSaveable { mutableStateOf(false) }

            val installNewPackageBottomSheet = rememberModalBottomSheetState(true)
            val installNewPackageBottomSheetOpen = rememberSaveable { mutableStateOf(false) }

            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()



            AutoPieTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),

                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        val selectedItem = remember { mutableIntStateOf(0) }

                        Scaffold(
                            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

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
                                            IconButton(
                                                onClick = { /*TODO*/ }, modifier = Modifier
                                                    .clip(
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(end = 17.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Settings,
                                                    contentDescription = "Settings",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    },
                                    //colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                                    navigationIcon = {
                                        IconButton(
                                            modifier = Modifier
                                                .padding(4.dp),
                                            onClick = {}
                                        ) {
                                            Icon(
                                                modifier = Modifier

                                                    .size(27.dp),
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Search",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
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
                                        FloatingActionButton(
                                            modifier = Modifier.height(65.dp),
                                            onClick = { /*TODO*/ },
                                            containerColor = MaterialTheme.colorScheme.primary

                                        ) {
                                            Row(
                                                Modifier.padding(
                                                    vertical = 10.dp,
                                                    horizontal = 15.dp
                                                ), verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            mainViewModel.viewModelScope.launch {
                                                                openBottomSheet.value = true
                                                                //addShareBottomSheetState.show()
                                                            }
                                                        }
                                                )
                                                {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            tint = Color.Black.copy(0.7F),
                                                            imageVector = Icons.Outlined.AddBox,
                                                            contentDescription = "Create command",
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(
                                                            "Add",
                                                            fontSize = 15.7.sp,
                                                            color = Color.Black.copy(0.7F),
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                    }
                                                }

                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxHeight(0.8f)
                                                        .width(10.dp)
                                                )
                                                Box(
                                                    Modifier
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(100))
                                                        .width(3.dp)
                                                        .background(Color.Black.copy(0.06f))
                                                )
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxHeight(0.8f)
                                                        .width(10.dp)
                                                )


                                                Box(
                                                    modifier = Modifier
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            commandsSearchBottomSheetStateOpen.value =
                                                                true
                                                        }
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Search,
                                                            tint = Color.Black.copy(0.7F),
                                                            contentDescription = "Search",
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(
                                                            "Search",
                                                            color = Color.Black.copy(0.7F),
                                                            fontSize = 15.7.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )

                                                    }
                                                }
                                            }

                                        }
                                    }

                                    1 -> {
                                        FloatingActionButton(
                                            onClick = {
                                                installNewPackageBottomSheetOpen.value = true
                                            },
                                            containerColor = MaterialTheme.colorScheme.primary

                                        ) {
                                            Box(
                                                Modifier.padding(
                                                    vertical = 10.dp,
                                                    horizontal = 15.dp
                                                )
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Download,
                                                        tint = Color.Black.copy(0.7F),
                                                        contentDescription = "Install package",
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(

                                                        "Install New",
                                                        fontSize = 15.7.sp,
                                                        color = Color.Black.copy(0.7F),
                                                        fontWeight = FontWeight.SemiBold
                                                    )

                                                }
                                            }
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

                                    selectedItem.intValue == 0 -> HomeScreen(innerPadding)
                                    selectedItem.intValue == 1 -> InstalledScreen(innerPadding)
                                    selectedItem.intValue == 2 -> SettingsScreen(innerPadding)
                                    else -> {}
                                }
                            }
                        )


                    }


                    if (openBottomSheet.value) {
                        AddShareCommandBottomSheet(
                            state = addShareBottomSheetState,
                            open = openBottomSheet
                        )
                    }
                    if (commandsSearchBottomSheetStateOpen.value) {
                        CommandsSearchBottomSheet(
                            state = commandsSearchBottomSheetState,
                            open = commandsSearchBottomSheetStateOpen
                        )
                    }
                    if (installNewPackageBottomSheetOpen.value) {
                        InstallNewPackageBottomSheet(
                            state = installNewPackageBottomSheet,
                            open = installNewPackageBottomSheetOpen
                        )
                    }

                }
            }
        }
    }

}
