package com.autosec.pie.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.autosec.pie.viewModels.CloudCommandsViewModel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import com.autosec.pie.core.*
import com.autosec.pie.domain.model.CloudCommandModel
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


    val viewModel: CloudCommandsViewModel by inject(CloudCommandsViewModel::class.java)

    val state by viewModel.stateFlow.collectAsState(initial = Result.None)

    LaunchedEffect(key1 = Unit) {
        if (state !is Result.Success) {
            viewModel.getCloudCommands()
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is Result.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(27.dp)
                    )
                }
            }

            is Result.Success -> {
                CloudCommandsList(viewModel.cloudCommandsList, viewModel)
            }

            else -> {}
        }
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
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 15.dp)
                        .fillMaxWidth()
                        .padding(vertical = 0.dp)
                ) {
                    Text(
                        "Blocked Users",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }
            }

            if (cloudCommands.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(550.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.List,
                                contentDescription = "Blocklist is empty",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7F),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(15.dp))
                            Text(
                                "Blocklist is empty",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 15.5.sp
                            )
                        }
                    }
                }
            } else {
                items(cloudCommands) {
                    CommandItem(it)
                }
            }


        }
    }

}

@Composable
fun CommandItem(user: CloudCommandModel) {


    Row(
        Modifier.height(75.dp).padding(vertical = 3.dp, horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable {

            }
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(1F), contentAlignment = Alignment.Center) {
//            Box(
//                modifier = Modifier.clip(RoundedCornerShape(50)).size(35.dp)
//            ) {
//                Image(
//                    painter = painter,
//                    "User Avatar",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight().weight(1F, fill = true)
        ) {
            Text(user.name, fontSize = 15.sp)
        }

//        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp).fillMaxHeight()) {
//            FollowButton(Result.None)
//        }

    }
}

