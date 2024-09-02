package com.autosec.pie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.ShareItemModel
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.elements.AutoPieLogo
import com.autosec.pie.elements.SearchBar
import com.autosec.pie.services.ForegroundService
import com.autosec.pie.ui.theme.AutoPieTheme
import com.autosec.pie.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber


class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        Timber.d(this.intent.toString())


        val data = intent?.getStringExtra(Intent.EXTRA_TEXT)


        val files = mutableListOf<String>()

        when {
            Intent.ACTION_SEND_MULTIPLE == intent?.action -> {

                val sharedPaths = intent.getParcelableArrayListExtra<Uri>("extra_file_uris")

                Timber.d(sharedPaths.toString())

                sharedPaths?.map {
                    it.path.let{
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

                sharedPath?.path.let{
                    if (it != null) {
                        files.add(it)
                    }
                }
                
            }

        }

        Timber.d(files.toString())
        Timber.d(data.toString())




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



    LaunchedEffect(key1 = currentLink, fileUris) {
        shareReceiverViewModel.main.eventFlow.collect{
            when(it){
                is ViewModelEvent.CloseShareReceiverSheet -> activity?.finish()
                else -> {}
            }
        }
    }

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
        it != SheetValue.Hidden
    })

    val scope = rememberCoroutineScope()




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
                   ){

                LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp), contentPadding = PaddingValues(10.dp)){
                    item {
                        AutoPieLogo()
                        Spacer(modifier = Modifier.height(15.dp))
                    }
                    item {
                        SearchBar(searchQuery = shareReceiverViewModel.searchQuery) {
                            shareReceiverViewModel.search(shareReceiverViewModel.searchQuery.value)
                        }
                    }
                    items(shareReceiverViewModel.filteredShareItemsResult, key = {it.name}){ item ->
                        ShareCard(card = item, currentLink, fileUris)
                    }
                }

            }
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

@Composable
fun ShareCard(
    card: ShareItemModel,
    currentLink: String?,
    fileUris: List<String>
) {

    val activity = (LocalContext.current as? Activity)
    val context = LocalContext.current
    var isLoading by remember {
        mutableStateOf(false)
    }

    val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)

    ElevatedCard(onClick = {
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

        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Black.copy(alpha = 0.08F))) {

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            if(isLoading){
                CircularProgressIndicator(strokeWidth = 2.dp)
            }else{
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(15.dp), verticalArrangement = Arrangement.Center){
                    Text(text = card.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "${card.exec.split("/").last()} ${card.command}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

