package com.autosec.pie


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
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


class ShareReceiverActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        Timber.d(this.intent.toString())
        Timber.d(this.intent.extras.toString())


        val data = intent?.getStringExtra(Intent.EXTRA_TEXT)

        val viewData = intent?.data

        Timber.d("View data: $viewData")


        val files = mutableListOf<String>()


        when {
            Intent.ACTION_SEND_MULTIPLE == intent?.action -> {

                val sharedPaths = intent.getParcelableArrayListExtra<Uri>("extra_file_uris")

                Timber.d("ACTION_SEND_MULTIPLE PATH: $sharedPaths")

                sharedPaths?.map { sharedPath ->
                    sharedPath.path.let {
                        val fragment = sharedPath?.fragment
                        val fullPath = if (fragment != null) "$it#$fragment" else it
                        files.add(fullPath!!)
                    }
                }

                if(sharedPaths == null && data == null){
                    val clipData = getPathsFromClipData(this.applicationContext, intent)

                    Timber.d("ACTION_SEND_MULTIPLE CLIP: $clipData")

                    clipData.forEach {
                        files.add(it)
                    }
                }

            }

            Intent.ACTION_SEND == intent?.action -> {

                val sharedPath = intent.getParcelableExtra<Uri>("extra_file_uris")



                Timber.d("ACTION_SEND PATH: $sharedPath")


                sharedPath?.path.let {
                    if (it != null) {
                        val fragment = sharedPath?.fragment
                        val fullPath = if (fragment != null) "$it#$fragment" else it
                        files.add(fullPath)
                    }
                }

                if(sharedPath == null && data == null) {
                    val clipData = getPathsFromClipData(this.applicationContext, intent)

                    Timber.d("ACTION_SEND CLIP: $clipData")

                    clipData.forEach {
                        files.add(it)
                    }
                }

            }

            Intent.ACTION_VIEW == intent?.action -> {

                val sharedPath = intent.getParcelableExtra<Uri>("extra_file_uris")

                Timber.d("ACTION_VIEW PATH: $sharedPath")

                sharedPath?.path.let {
                    if (it != null) {
                        val fragment = sharedPath?.fragment
                        val fullPath = if (fragment != null) "$it#$fragment" else it
                        files.add(fullPath)
                    }
                }

                if(sharedPath == null && data == null){
                    val clipData = getPathsFromClipData(this.applicationContext, intent)

                    Timber.d("ACTION_VIEW CLIP: $clipData")

                    clipData.forEach {
                        files.add(it)
                    }
                }
            }

        }

        Timber.d("Intent EXTRA_TEXT: ${data.toString()}")
        Timber.d("Intent FILES: : ${files}")




        setContent {

            AutoPieTheme {

                ShareContextMenuBottomSheet(currentLink = data, fileUris = files)

            }
        }
    }


}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContextMenuBottomSheet(
    currentLink: String?,
    fileUris: List<String>,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {
    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()

    val shareItemsResult = shareReceiverViewModel.shareItemsResult.collectAsState()
    val filteredShareItemsResult = shareReceiverViewModel.filteredShareItemsResult.collectAsState()
    val mostUsedPackages = shareReceiverViewModel.mostUsedPackages.collectAsState()


    val activity = LocalContext.current.getActivity()


    LaunchedEffect(key1 = currentLink, fileUris) {
        shareReceiverViewModel.main.eventFlow.collect {
            when (it) {
                is ViewModelEvent.CloseShareReceiverSheet -> activity?.finish()
                else -> {}
            }
        }
    }

    SideEffect {
        if(shareItemsResult.value.isEmpty()){
            shareReceiverViewModel.getSharesConfig()
        }
    }

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
        it != SheetValue.Hidden
    })

    val scope = rememberCoroutineScope()


    val extrasBottomSheetState = rememberModalBottomSheetState(true)
    val extrasBottomSheetStateOpen = remember {
        mutableStateOf(false)
    }

    LaunchedEffect(shareReceiverViewModel.currentExtrasDetails.value) {
        extrasBottomSheetStateOpen.value = shareReceiverViewModel.currentExtrasDetails.value != null
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(0.75F),
            contentAlignment = Alignment.TopStart

        )
        {

            Column(
                Modifier
                    .fillMaxSize()
            ) {

                LazyColumn(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    item {

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoPieLogo()
                            Spacer(modifier = Modifier.height(15.dp))
                            Button(
                                modifier = Modifier,
                                contentPadding = PaddingValues(10.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                onClick = {
                                val intent = Intent(activity, MainActivity::class.java)
                                activity?.startActivity(intent)
                            }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Open the main app.",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                    }
                    item {
                        SearchBar(searchQuery = shareReceiverViewModel.main.shareReceiverSearchQuery) {
                            shareReceiverViewModel.search(shareReceiverViewModel.main.shareReceiverSearchQuery.value)
                        }
                    }
                    item{
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)){
                            mostUsedPackages.value.map{
                                AssistChip(onClick = {shareReceiverViewModel.main.shareReceiverSearchQuery.value = it;shareReceiverViewModel.search(it)}, label = { Text(it) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2F)))
                            }
                        }
                    }
                    items(
                        filteredShareItemsResult.value,
                        key = { it.name ?: it }) { item ->
                        ShareCard(card = item, currentLink, fileUris, state)
                    }
                }

            }
        }

        if (extrasBottomSheetStateOpen.value) {
            CommandExtrasBottomSheet(
                state = extrasBottomSheetState,
                open = extrasBottomSheetStateOpen,
                state
            )
        }

    }


    ModalBottomSheet(

        sheetState = state,

        content = { bottomSheetContent() },
        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp),
        //containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        properties = ModalBottomSheetDefaults.properties(),
        onDismissRequest = {
            scope.launch {
                state.hide()
                activity?.finish()
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShareCard(
    card: CommandModel,
    currentLink: String?,
    fileUris: List<String>,
    sheetState: SheetState,
) {


    val activity = LocalContext.current.getActivity()
    var isLoading by remember {
        mutableStateOf(false)
    }

    val shareReceiverViewModel: ShareReceiverViewModel = koinViewModel()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                onClick = {
                    Timber.d("CLICK DETECTED")

                    if(isLoading){
                        return@combinedClickable
                    }

                    if (card.extras?.any { it.type == "STRING" && it.default.isEmpty() && it.required } == true) {
                        shareReceiverViewModel.currentExtrasDetails.value =
                            Triple(true, card, ShareInputs(currentLink, fileUris))
                    } else {
                        shareReceiverViewModel.onCommandClick(card, fileUris, currentLink) {
                            shareReceiverViewModel.viewModelScope.launch {
                                isLoading = true
                                delay(900)
                                Timber.d("CLOSING THE AUTOPIE COMMANDS SHEET.")
                                activity?.finish()
                            }
                        }
                    }
                },
                onLongClick = {
                    Timber.d("LONG PRESS DETECTED")

                    if (card.extras?.isNotEmpty() == true) {
                        shareReceiverViewModel.currentExtrasDetails.value =
                            Triple(true, card, ShareInputs(currentLink, fileUris))
                    }
                }
            ),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
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
                CommandCard(card = card) {
                    shareReceiverViewModel.currentExtrasDetails.value =
                        Triple(true, card, ShareInputs(currentLink, fileUris))
                }
            }
        }
    }
}

@Composable
fun CommandCard(card: CommandModel, onExpandButtonClick: () -> Unit) {


    Box(
        Modifier
            .fillMaxSize()
            .padding(15.dp),
        //verticalArrangement = Arrangement.Center
        contentAlignment = Alignment.Center
    ) {
        if (card.extras?.isNotEmpty() == true) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(15.dp))
                    //.background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp))
                    .clickable {
                        onExpandButtonClick()
                    }
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Show more options",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = card.name ?: "",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(if (card.extras?.isNotEmpty() == true) 0.9F else 1F)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${card.exec} ${card.command}",
                maxLines = 2,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}



