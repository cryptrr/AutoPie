package com.autosec.pie.autopieapp.presentation.screens

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.BuildConfig
import com.autosec.pie.R
import com.autosec.pie.autopieapp.data.CommandHistoryEntity
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.ShareInputs
import com.autosec.pie.autopieapp.data.toInputs
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.presentation.elements.AutoPieOutlinedButton
import com.autosec.pie.autopieapp.presentation.elements.AutoPiePrimaryButton
import com.autosec.pie.autopieapp.presentation.elements.EmptyItemsBadge
import com.autosec.pie.autopieapp.presentation.elements.OptionItem
import com.autosec.pie.autopieapp.presentation.elements.OptionLayout
import com.autosec.pie.autopieapp.presentation.elements.OutlinedButtonMedium
import com.autosec.pie.autopieapp.presentation.viewModels.CommandHistoryViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CreateCommandViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.autosec.pie.ui.theme.GreenGrey60
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.PastelRed
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.utils.Utils
import com.autosec.pie.utils.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandHistorySheet(
    state: SheetState,
    open: MutableState<Boolean>,
    key: String? = null,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {


    val scope = rememberCoroutineScope()

    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()

    val card = shareReceiverViewModel.main.currentSelectedCommand.value ?: return

    val commandHistoryViewModel: CommandHistoryViewModel = koinViewModel(key = card.name)

    val historyState = commandHistoryViewModel.commandsHistoryResult.collectAsState()
    val commandModel = commandHistoryViewModel.commandDetails.collectAsState()

    LaunchedEffect(card.name) {
        commandHistoryViewModel.getCommandHistory(card.name)
        commandHistoryViewModel.getCommand(card.name)
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .heightIn(max = 700.dp, min = 100.dp)
                .wrapContentHeight(),
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {

            Column(Modifier.padding(15.dp)){

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = card.name,
                    lineHeight = 32.sp,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(17.dp))

                Text(
                    text = "History",
                    lineHeight = 32.sp,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7F)
                )



                Spacer(modifier = Modifier.height(20.dp))


                if(historyState.value.isNotEmpty()){
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)){
                        items(historyState.value, key = {it.id}){
                            CommandHistoryCardWrapper(it, commandModel.value)
                        }
                    }
                }else{
                    EmptyItemsBadge(
                        icon = Icons.Default.ClearAll,
                        text = "There is no history here yet."
                    )
                }

            }

        }


    }


    ModalBottomSheet(
        sheetState = state,
        content = { bottomSheetContent() },
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onDismissRequest = {
            scope.launch {
                open.value = false
            }
        }
    )
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CommandHistoryCardWrapper(
    commandHistory: CommandHistoryEntity,
    command: CommandModel?
) {


    var isLoading by remember {
        mutableStateOf(false)
    }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            //.height(120.dp)
            .combinedClickable(
                onClick = {

                },
                onLongClick = {

                }
            ),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(0.55F)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1F))
    ) {

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                CommandHistoryCard(commandHistory = commandHistory, command)
            }
        }
    }
}

@Composable
fun CommandHistoryCard(commandHistory: CommandHistoryEntity, command: CommandModel?) {

    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()


    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 15.dp, vertical = 20.dp),
        //verticalArrangement = Arrangement.Center
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .align(Alignment.TopEnd)
            ,

        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                tint = if(commandHistory.success) GreenGrey60 else PastelRed,
                contentDescription = "Command Success or Failure",
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = Utils.timeAgo(commandHistory.id),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(if (commandHistory.commandExtraInputs.isNotEmpty()) 0.9F else 1F)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = commandHistory.currentLink ?: commandHistory.fileUris?.toString() ?: "",
                maxLines = 4,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(17.dp))

            OutlinedButtonMedium("RETRY") {
                command?.let{
                    shareReceiverViewModel.onCommandClickWithExtras(command, commandHistory.currentLink, commandHistory.fileUris ?: emptyList(), commandHistory.commandExtraInputs.map{it.toInputs()})
                }
            }

        }
    }
}



