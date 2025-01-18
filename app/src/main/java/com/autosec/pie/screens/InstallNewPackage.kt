package com.autosec.pie.screens

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.core.Result
import com.autosec.pie.data.InstalledPackageModel
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.domain.model.CloudPackageModel
import com.autosec.pie.elements.SearchBar
import com.autosec.pie.utils.getActivity
import com.autosec.pie.viewModels.CloudCommandsViewModel
import com.autosec.pie.viewModels.CloudPackagesViewModel
import com.autosec.pie.viewModels.ShareReceiverViewModel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallNewPackageBottomSheet(
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

                InstallNewPackageScreen()


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
fun InstallNewPackageScreen(

) {

    val viewModel: CloudPackagesViewModel by inject(CloudPackagesViewModel::class.java)

    val state by viewModel.stateFlow.collectAsState(initial = Result.None)

    LaunchedEffect(key1 = Unit) {
        if (state !is Result.Success) {
            viewModel.getPackages()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart){
        LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp)){
            item {
                Spacer(modifier = Modifier.height(15.dp))
                Text(text = "Explore Packages", fontSize = 33.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(15.dp))
            }
            item{
                SearchBar(viewModel.searchQuery){
                    viewModel.getPackages()
                }
            }
            items(viewModel.cloudPackagesList, key = {it.id}){ item ->
                CloudPackageCard(item = item)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudPackageCard(
    item: CloudPackageModel
) {

    val activity = LocalContext.current.getActivity()
    var isLoading by remember {
        mutableStateOf(false)
    }

    val viewModel: CloudPackagesViewModel by inject(CloudPackagesViewModel::class.java)

    ElevatedCard(onClick = {
        Timber.d("CLICK DETECTED")
        viewModel.selectedPackage.value = item
        viewModel.main.dispatchEvent(ViewModelEvent.OpenCloudPackageDetails)


    },

        elevation = CardDefaults.cardElevation(0.dp),

        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)

        , shape = RoundedCornerShape(15.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.Black.copy(0.13F))) {

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            if(isLoading){
                CircularProgressIndicator(strokeWidth = 2.dp)
            }else{
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(15.dp), verticalArrangement = Arrangement.Center){
                    Text(text = item.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
//                    Text(text = "version ${item.version}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7F),
//                        modifier = Modifier.fillMaxWidth().basicMarquee()
//                    )
//                    Text(text = item.path, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(0.7F),
//                        modifier = Modifier.fillMaxWidth().basicMarquee()
//                    )
                }
            }
        }
    }
}