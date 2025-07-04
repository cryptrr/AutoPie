package com.autosec.pie.autopieapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandExtra
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.CommandExtraElement
import com.autosec.pie.autopieapp.presentation.elements.GenericFormSwitch
import com.autosec.pie.autopieapp.presentation.elements.GenericTextFormField
import com.autosec.pie.autopieapp.presentation.elements.YesNoDialog
import com.autosec.pie.autopieapp.data.services.AutoPieCoreService
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.ui.theme.GreenGrey60
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.utils.Utils
import com.autosec.pie.autopieapp.presentation.viewModels.EditCommandViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCommandBottomSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    key: String,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val viewModel: EditCommandViewModel = koinViewModel()

    LaunchedEffect(key1 = key) {
        viewModel.getCommandDetails(key)
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(0.75F),
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {

            if (viewModel.isLoading.value) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp)
                ) {

                    EditCommandScreen(key, open)
                }
            }

        }


    }


    ModalBottomSheet(
        sheetState = state,
        content = { bottomSheetContent() },
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onDismissRequest = {
            scope.launch {
                open.value = false
            }
        }
    )
}


@Composable
fun EditCommandScreen(commandKey: String, open: MutableState<Boolean>) {

    val viewModel: EditCommandViewModel = koinViewModel()

    //val extrasElements = remember{ mutableStateOf<List<String>>(emptyList()) }
    val extrasElements = viewModel.commandExtras

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }


    fun addExtra() {
        extrasElements.value += CommandExtra(id = Utils.getRandomNumericalId(), type = "STRING")
    }



    Column {

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .weight(1F, true)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Command",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Box(
                    Modifier
                        .clip(
                            RoundedCornerShape(10.dp)
                        )
                        .background(
                            when (viewModel.type.value) {
                                "SHARE" -> PastelPurple
                                "FILE_OBSERVER" -> Purple10
                                "CRON" -> GreenGrey60
                                else -> Purple10
                            }
                        )
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    when (viewModel.type.value) {
                        "SHARE" -> {
                            Text(
                                text = "SHARE",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }

                        "FILE_OBSERVER" -> {
                            Text(
                                text = "FILE OBSERVER",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                        "CRON" -> {
                            Text(
                                text = "CRON",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(25.dp))


            GenericTextFormField(text = viewModel.commandName, "NAME", placeholder = "name")

            Spacer(modifier = Modifier.height(20.dp))
            GenericTextFormField(text = viewModel.execFile, "PROGRAM", placeholder = "exec file")

            Spacer(modifier = Modifier.height(20.dp))

            GenericTextFormField(
                text = viewModel.command,
                "Command".uppercase(),
                placeholder = "command",
                singleLine = false,
                modifier = Modifier
                //.height(100.dp)
                //.wrapContentHeight()
            )

            if (viewModel.selectedCommandType == "FILE_OBSERVER") {

                Spacer(modifier = Modifier.height(20.dp))

                GenericTextFormField(
                    text = viewModel.selectors,
                    "Selectors".uppercase(),
                    subtitle = "Selector is a regex pattern to filter for this command.\nFor Example, to select only PNG files, use \"^.*\\\\.png$\""
                )
            }

            if (viewModel.selectedCommandType == "CRON") {

                Spacer(modifier = Modifier.height(20.dp))

                GenericTextFormField(
                    text = viewModel.cronInterval,
                    "Cron Interval*".uppercase(),
                    subtitle = "The interval in which this needs to run once.\nUse values like 15m, 30m, 1h etc.\nAndroid Limits periodic jobs to minimum of 15m."
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val annotatedString = buildAnnotatedString {
                append("This is the directory in which the command will be run. \n" +
                        "Type ")
                withStyle(style = SpanStyle(background = MaterialTheme.colorScheme.surfaceColorAtElevation(30.dp), fontSize = 14.sp)) {
                    append(" Download ")
                }
                append(" to use your download folder.")
            }

            GenericTextFormField(
                text = viewModel.directory,
                "DIRECTORY",
                subtitle = annotatedString
            )

            Spacer(modifier = Modifier.height(20.dp))


            if (extrasElements.value.isNotEmpty()) {
                CommandExtraElement(extrasElements = extrasElements, {
                    viewModel.addCommandExtra(it)
                }){
                    viewModel.removeCommandExtra(it)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))


            GenericFormSwitch(
                text = "Delete source file after completion",
                switchState = viewModel.deleteSource,
                onChange = { viewModel.deleteSource.value = it }
            )


        }


        Row {
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .width(63.dp),
                shape = RoundedCornerShape(20),
                contentPadding = PaddingValues(vertical = 10.dp),
                onClick = {
                    showDeleteDialog = true
                },

                ) {
                Icon(
                    modifier = Modifier
                        .size(27.dp),
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                )

            }

            Spacer(modifier = Modifier.width(11.dp))
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .width(63.dp),
                shape = RoundedCornerShape(20),
                contentPadding = PaddingValues(vertical = 10.dp),
                onClick = {
                    addExtra()
                },

                ) {
                Icon(
                    modifier = Modifier

                        .size(27.dp),
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Extras",
                )

            }

            Spacer(modifier = Modifier.width(11.dp))
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20),
                //contentPadding = PaddingValues(vertical = 20.dp),
                enabled = viewModel.isValidCommand,
                onClick = {
                    viewModel.viewModelScope.launch {
                        viewModel.changeCommandDetails(key = commandKey)
                        delay(500L)
                        viewModel.main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                        when(viewModel.type.value){
                            "SHARE" -> viewModel.main.dispatchEvent(ViewModelEvent.SharesConfigChanged)
                            "FILE_OBSERVER" -> viewModel.main.dispatchEvent(ViewModelEvent.ObserversConfigChanged)
                            "CRON" -> viewModel.main.dispatchEvent(ViewModelEvent.CronConfigChanged)
                        }
                        open.value = false
                    }
                },

                ) {
                Text(
                    text = "SAVE",
                    //modifier = Modifier.align(Alignment.Center),
                    letterSpacing = 1.11.sp,
                    fontWeight = FontWeight.SemiBold
                )

            }
        }

        YesNoDialog(
            showDialog = showDeleteDialog,
            title = "Are you sure you want to delete this command",
            subtitle = "This operation is not reversible.",
            onYesClicked = {
                viewModel.viewModelScope.launch {
                    viewModel.deleteCommand(key = commandKey)
                    delay(500L)
                    viewModel.main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                    viewModel.main.showNotification(AppNotification.CommandDeleted)
                    open.value = false
                }
            },
            onNoClicked = {
                showDeleteDialog = false
            },
            onDismissRequest = {
                showDeleteDialog = false
            }
        )

    }
}