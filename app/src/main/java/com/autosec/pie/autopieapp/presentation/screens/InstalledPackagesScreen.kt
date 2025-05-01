package com.autosec.pie.autopieapp.presentation.screens

import android.app.Activity
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.autopieapp.data.InstalledPackageModel
import com.autosec.pie.utils.getActivity
import com.autosec.pie.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.inject

@Composable
fun InstalledScreen(innerPadding: PaddingValues) {
    val installedPackagesViewModel: InstalledPackagesViewModel = koinViewModel()

    val installedPackagesState = installedPackagesViewModel.installedPackages.collectAsState()

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(15.dp)){
        LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp)){
            item {
                Spacer(modifier = Modifier.height(15.dp))
                Text(text = "Installed Packages", fontSize = 33.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(15.dp))
            }
            items(installedPackagesState.value, key = {it.path}){ item ->
                PackageCard(item = item)
            }
        }
    }
}

@Composable
fun PackageCard(
    item: InstalledPackageModel
) {

    val activity = LocalContext.current.getActivity()
    var isLoading by remember {
        mutableStateOf(false)
    }
    
    ElevatedCard(onClick = {



    },

        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))) {

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