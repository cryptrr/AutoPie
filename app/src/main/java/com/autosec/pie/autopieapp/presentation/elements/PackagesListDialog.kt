package com.autosec.pie.autopieapp.presentation.elements

import android.widget.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autosec.pie.autopieapp.data.InstalledPackageModel
import com.autosec.pie.autopieapp.presentation.viewModels.InstalledPackagesViewModel
import com.autosec.pie.utils.getActivity
import org.koin.androidx.compose.koinViewModel

@Composable
fun PackagesListDialog(
    showDialog: Boolean,
    title: String,
    value: MutableState<String>,
    onDismissRequest: () -> Unit
) {

    //TODO: add key of command name for viewmodel
    val installedPackagesViewModel: InstalledPackagesViewModel = koinViewModel()

    val installedPackagesState = installedPackagesViewModel.filteredPackages.collectAsState()

    if (showDialog) {
        Dialog(onDismissRequest = { onDismissRequest() }, DialogProperties()) {
            Box(modifier = Modifier.height(500.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,

                ) {

                    LazyColumn(
                        Modifier
                        .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {

                        item{
                            Spacer(Modifier.height(10.dp))
                        }

                        item {
                            SearchBar(searchQuery = installedPackagesViewModel.searchQuery, "Search your packages") {
                                installedPackagesViewModel.search(installedPackagesViewModel.searchQuery.value)
                            }
                        }
                        items(installedPackagesState.value, key = { it.path }) { item ->
                            PackageCardSmall(item = item,value, onDismissRequest)
                        }

                        item{
                            Spacer(Modifier.height(10.dp))
                        }
                    }


                }
            }
        }
    }
}

@Composable
fun PackageCardSmall(
    item: InstalledPackageModel,
    value: MutableState<String>,
    onDismissRequest: () -> Unit
    ) {

    val activity = LocalContext.current.getActivity()
    var isLoading by remember {
        mutableStateOf(false)
    }

    ElevatedCard(onClick = {

        value.value = item.name
        onDismissRequest()

    },

        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(67.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))) {

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(15.dp), verticalArrangement = Arrangement.Center){
                Text(text = item.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(7.dp))
            }
        }
    }
}