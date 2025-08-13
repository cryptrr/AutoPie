package com.autosec.pie.autopieapp.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.EmptyItemsBadge
import com.autosec.pie.autopieapp.presentation.elements.LoadingBadge
import com.autosec.pie.autopieapp.presentation.elements.SearchBar
import com.autosec.pie.autopieapp.presentation.elements.YesNoDialog
import com.autosec.pie.ui.theme.GreenGrey60
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.utils.getActivity
import com.autosec.pie.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.autosec.pie.ui.theme.PastelGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber


@Composable
fun HomeScreen(innerPadding: PaddingValues) {

    val commandsListScreenViewModel: CommandsListScreenViewModel = koinViewModel()

    //Timber.d("ViewModel Check Parent: CommandsListScreenViewModel : ${commandsListScreenViewModel}")

//    LaunchedEffect(key1 = Unit) {
//        commandsListScreenViewModel.getCommandsList()
//    }

    val filteredListOfCommands = commandsListScreenViewModel.filteredListOfCommands.collectAsState()
    val mostUsedPackages = commandsListScreenViewModel.mostUsedPackages.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf("") }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(15.dp)
        ) {
            item {
                Text(
                    text = "Commands",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

//                SingleChoiceSegmentedButtonRow {
//                    commandsListScreenViewModel.commandTypeOptions.forEachIndexed { index, label ->
//                        SegmentedButton(
//                            shape = SegmentedButtonDefaults.itemShape(
//                                index = index,
//                                count = commandsListScreenViewModel.commandTypeOptions.size,
//                                baseShape = RoundedCornerShape(10.dp)
//                            ),
//                            onClick = {
//                                commandsListScreenViewModel.selectedICommandTypeIndex = index
//                                when(label){
//                                    "Share" -> commandsListScreenViewModel.filterOnlyShareCommands()
//                                    "Observers" -> commandsListScreenViewModel.filterOnlyObserverCommands()
//                                    "All" -> commandsListScreenViewModel.noFilter()
//                                }
//                            },
//                            selected = index == commandsListScreenViewModel.selectedICommandTypeIndex
//                        ) {
//                            Text(label)
//                        }
//                    }
//                }
            }


            item {
                SearchBar(searchQuery = commandsListScreenViewModel.searchCommandQuery, "Search your commands") {
                    commandsListScreenViewModel.searchInCommands(commandsListScreenViewModel.searchCommandQuery.value)
                }
            }

            item{
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)){
                    val inputChipInteractionSource = remember { MutableInteractionSource() }

                    mostUsedPackages.value.map{
                        Box{
                            AssistChip(
                                label = {Text(it)},
                                onClick = {},
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2F)),

                            )
                            Box(modifier = Modifier
                                .matchParentSize()
                                .combinedClickable (
                                    onClick = {
                                        //Timber.d("CLICK DETECTED")
                                        commandsListScreenViewModel.searchCommandQuery.value = it;
                                        commandsListScreenViewModel.searchInCommands(it)
                                    },
                                    onLongClick = {
                                        Timber.d("LONG PRESS DETECTED")
                                        showDeleteDialog = true
                                        tagToDelete = it
                                    },
                                    interactionSource = inputChipInteractionSource,
                                    indication = null,
                                ))
                        }

                    }


                    if(commandsListScreenViewModel.searchCommandQuery.value.isNotBlank() && !mostUsedPackages.value.contains(commandsListScreenViewModel.searchCommandQuery.value)){
                        AssistChip(onClick = {commandsListScreenViewModel.addUserTag(commandsListScreenViewModel.searchCommandQuery.value)}, label = { Text("Add") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)), border = BorderStroke(1.dp,
                            PastelGreen
                        ))
                    }

                }
            }


            when{
                commandsListScreenViewModel.isLoading.value -> {
                    item{
                        LoadingBadge()
                    }
                }

                filteredListOfCommands.value.isNotEmpty() -> {
                    items(filteredListOfCommands.value, key = {it.name}) { item ->
                        CommandCard(card = item)
                    }
                }

                else -> {
                    item {
                        EmptyItemsBadge(
                            icon = Icons.Outlined.Share,
                            text = "Your commands list is empty"
                        )
                    }
                }

            }



        }

        YesNoDialog(
            showDialog = showDeleteDialog,
            title = "Are you sure you want to delete the tag -- $tagToDelete",
            subtitle = "This operation is not reversible.",
            onYesClicked = {
                commandsListScreenViewModel.viewModelScope.launch {
                    commandsListScreenViewModel.deleteUserTag(tagToDelete)
                    showDeleteDialog = false
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommandCard(
    card: CommandModel
) {

    val activity = LocalContext.current.getActivity()
    var isLoading by remember {
        mutableStateOf(false)
    }

    val commandsListScreenViewModel: CommandsListScreenViewModel = koinViewModel()

    //Timber.d("ViewModel Check Child: CommandsListScreenViewModel : ${commandsListScreenViewModel}")


    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()


    Card(
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                onClick = {
//                    Timber.d("CLICK DETECTED")

                    commandsListScreenViewModel.main.dispatchEvent(ViewModelEvent.OpenCommandDetails(card))

                },
                onLongClick = {
                    Timber.d("LONG PRESS DETECTED")

                    shareReceiverViewModel.currentExtrasDetails.value =
                        Triple(true, card, ShareInputs())
                }
            ),

        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1F))

    ) {

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .clip(
                            RoundedCornerShape(10.dp)
                        )
                        .background(
                            when (card.type) {
                                CommandType.SHARE -> PastelPurple
                                CommandType.FILE_OBSERVER -> Purple10
                                CommandType.CRON -> GreenGrey60
                                null -> PastelPurple
                            }
                        )
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    when (card.type) {
                        CommandType.SHARE -> {
                            Text(
                                text = "SHARE",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }

                        CommandType.FILE_OBSERVER -> {
                            Text(
                                text = "FILE OBSERVER",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                        CommandType.CRON -> {
                            Text(
                                text = "CRON",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                        null -> {}
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(15.dp), verticalArrangement = Arrangement.Center
                ) {
                    Text(text = card.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${card.exec.split("/").last()} ${card.command}",
                        maxLines = 2,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7F),
                        modifier = Modifier
                            .fillMaxWidth()
                            //.basicMarquee()
                    )
                }
            }
        }
    }
}

