package com.autosec.pie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.ShareInputs
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.elements.AutoPieLogo
import com.autosec.pie.elements.SearchBar
import com.autosec.pie.screens.CommandExtrasBottomSheet
import com.autosec.pie.services.ForegroundService
import com.autosec.pie.ui.theme.AutoPieTheme
import com.autosec.pie.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber


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

                Timber.d(sharedPaths.toString())

                sharedPaths?.map {
                    it.path.let {
                        files.add(it!!)
                    }
                }

//                if (intent.type!!.startsWith("image/")) {
//                    val clipData: ClipData? = intent.clipData
//                    if (clipData != null) {
//                        val itemCount: Int = clipData.itemCount
//                        for (i in 0 until itemCount) {
//                            val item: ClipData.Item = clipData.getItemAt(i)
//                            val imageUri: Uri? = item.uri
//                            if (imageUri != null) {
//                                files.add(imageUri)
//                            }
//                        }
//                    }
//                }

            }

            Intent.ACTION_SEND == intent?.action -> {

                val sharedPath = intent.getParcelableExtra<Uri>("extra_file_uris")

                Timber.d(sharedPath.toString())

                sharedPath?.path.let {
                    if (it != null) {
                        files.add(it)
                    }
                }
            }

            Intent.ACTION_VIEW == intent?.action -> {

                val sharedPath = intent.getParcelableExtra<Uri>("extra_file_uris")

                Timber.d(sharedPath.toString())

                sharedPath?.path.let {
                    if (it != null) {
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
    val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)

    val activity = (LocalContext.current as? Activity)

//    LaunchedEffect(currentLink, fileUris) {
//        try {
//            shareReceiverViewModel.getSharesConfig()
//        }catch (e:Exception){
//            Timber.e(e)
//        }
//    }


    LaunchedEffect(key1 = currentLink, fileUris) {
        shareReceiverViewModel.main.eventFlow.collect {
            when (it) {
                is ViewModelEvent.CloseShareReceiverSheet -> activity?.finish()
                else -> {}
            }
        }
    }

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
        it != SheetValue.Hidden
    })

    val scope = rememberCoroutineScope()


    val extrasBottomSheetState = rememberModalBottomSheetState(true)
    val extrasBottomSheetStateOpen = remember {
        derivedStateOf { shareReceiverViewModel.currentExtrasDetails.value != null }
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
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    item {
                        AutoPieLogo()
                        Spacer(modifier = Modifier.height(15.dp))
                    }
                    item {
                        SearchBar(searchQuery = shareReceiverViewModel.searchQuery) {
                            shareReceiverViewModel.search(shareReceiverViewModel.searchQuery.value)
                        }
                    }
                    items(
                        shareReceiverViewModel.filteredShareItemsResult,
                        key = { it.name }) { item ->
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
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
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

    val activity = (LocalContext.current as? Activity)
    val context = LocalContext.current
    var isLoading by remember {
        mutableStateOf(false)
    }

    val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)

    ElevatedCard(
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                onClick = {
                    Timber.d("CLICK DETECTED")
                    shareReceiverViewModel.viewModelScope.launch {

                        val commandJson = Gson().toJson(card)
                        val fileUrisJson = Gson().toJson(fileUris)

                        val intent = Intent(context, ForegroundService::class.java).apply {
                            putExtra("command", commandJson)
                            putExtra("currentLink", currentLink)
                            putExtra("fileUris", fileUrisJson)
                        }

                        startForegroundService(context, intent)

                        //shareReceiverViewModel.runShareCommand(card, currentLink, fileUris)
                        isLoading = true
                        delay(900)
                        activity?.finish()
                    }
                },
                onLongClick = {
                    Timber.d("LONG PRESS DETECTED")

                    if (card.extras?.isNotEmpty()!!) {
                        shareReceiverViewModel.currentExtrasDetails.value =
                            Triple(true, card, ShareInputs(currentLink, fileUris))
                    }
                }
            ),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Black.copy(alpha = 0.1F))
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
        if (card.extras?.isNotEmpty()!!) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.Black.copy(0.2F))
                    .clickable {
                        onExpandButtonClick()
                    }
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Show more options",
                    modifier = Modifier.size(25.dp)
                )
            }
        }
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = card.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(if (card.extras?.isNotEmpty()!!) 0.9F else 1F)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${card.exec} ${card.command}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}



