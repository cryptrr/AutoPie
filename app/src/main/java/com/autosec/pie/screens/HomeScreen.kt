package com.autosec.pie.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CommandType
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.elements.EmptyItemsBadge
import com.autosec.pie.elements.LoadingBadge
import com.autosec.pie.elements.SearchBar
import com.autosec.pie.ui.theme.GreenGrey60
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.ui.theme.Purple60
import com.autosec.pie.viewModels.CommandsListScreenViewModel
import org.koin.java.KoinJavaComponent.inject

@Composable
fun HomeScreen(innerPadding: PaddingValues) {

    val commandsListScreenViewModel: CommandsListScreenViewModel by inject(
        CommandsListScreenViewModel::class.java
    )

//    LaunchedEffect(key1 = Unit) {
//        commandsListScreenViewModel.getCommandsList()
//    }

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
                SearchBar(searchQuery = commandsListScreenViewModel.searchCommandQuery) {
                    commandsListScreenViewModel.searchInCommands(commandsListScreenViewModel.searchCommandQuery.value)
                }
            }

            when{
                commandsListScreenViewModel.isLoading.value -> {
                    item{
                        LoadingBadge()
                    }
                }

                commandsListScreenViewModel.filteredListOfCommands.isNotEmpty() -> {
                    items(commandsListScreenViewModel.filteredListOfCommands, key = {it.name}) { item ->
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
    }
}

@Composable
fun CommandCard(
    card: CommandModel
) {

    val activity = (LocalContext.current as? Activity)
    var isLoading by remember {
        mutableStateOf(false)
    }

    val commandsListScreenViewModel: CommandsListScreenViewModel by inject(
        CommandsListScreenViewModel::class.java
    )

    ElevatedCard(
        onClick = {
            commandsListScreenViewModel.main.currentCommandKey.value = card.name
            commandsListScreenViewModel.main.dispatchEvent(ViewModelEvent.OpenEditCommandSheet)
        },
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
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