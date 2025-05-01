package com.autosec.pie.autopieapp.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.List
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.utils.getActivity
import com.autosec.pie.autopieapp.presentation.viewModels.CloudCommandsViewModel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import com.autosec.pie.core.*
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.domain.model.CloudCommandModel
import com.autosec.pie.autopieapp.presentation.elements.SearchBar
import com.autosec.pie.ui.theme.GreenGrey60
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsSearchBottomSheet(
    state: SheetState,
    open : MutableState<Boolean>,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val activity = LocalContext.current.getActivity()

    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(0.75F)
            ,
            contentAlignment = Alignment.TopStart

        )
        {


            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 15.dp)){


                CloudCommandsScreen()


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
fun SearchCommands(

) {

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        Column(horizontalAlignment = Alignment.CenterHorizontally){
            Text("Search from Commands Repository to Add")
            Spacer(modifier = Modifier.height(7.dp))
            Text("Work In Progress")
        }
    }
}

@Composable
fun CloudCommandsScreen() {


    val viewModel: CloudCommandsViewModel = koinViewModel()

    val state by viewModel.stateFlow.collectAsState(initial = Result.None)

    LaunchedEffect(key1 = Unit) {
        if (state !is Result.Success) {
            viewModel.getCloudCommands()
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        CloudCommandsList(viewModel.cloudCommandsList, viewModel)
    }
}

@Composable
fun CloudCommandsList(cloudCommands: List<CloudCommandModel>, viewModel: CloudCommandsViewModel) {

    val state = rememberLazyListState()

    val isAtBottom = !state.canScrollForward

    LaunchedEffect(key1 = isAtBottom) {
        if (isAtBottom) {
            Timber.d ( "Reached bottom of BlockedUsersList" )

            viewModel.getMoreCloudCommands()

        }
    }


    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top),
            state = state
        ) {
            item {
                Row(
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                        .fillMaxWidth()
                        .padding(vertical = 0.dp)
                ) {
                    Text(
                        text = "Explore Commands",
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            item {
                SearchBar(viewModel.searchQuery){
                    viewModel.searchCloudCommands()
                }
            }

            if (cloudCommands.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(550.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.List,
                                contentDescription = "No results",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7F),
                                modifier = Modifier.size(80.dp)
                            )

                        }
                    }
                }
            } else {

                items(cloudCommands) {
                    CloudCommandCard(card = it)
                }
            }


        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudCommandCard(
    card: CloudCommandModel
) {

    val activity = LocalContext.current.getActivity()
    var isLoading by remember {
        mutableStateOf(false)
    }


    val viewModel: CloudCommandsViewModel = koinViewModel()


    ElevatedCard(
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                onClick = {
                    Timber.d("CLICK DETECTED")
                    viewModel.selectedCommand.value = card
                    viewModel.main.dispatchEvent(ViewModelEvent.OpenCloudCommandDetails)

                }
            ),

        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.elevatedCardColors(containerColor =  Color.Black.copy(0.13F))
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
                        text = "${card.packageUniqueName} ${card.command}",
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



