package com.autopi.autopieapp.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopi.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.domain.model.CloudCommandModel
import com.autopi.autopieapp.presentation.elements.SearchBar
import com.autopi.autopieapp.presentation.viewModels.isCloudCommandUpdateAvailable
import com.autopi.ui.theme.GreenGrey60
import com.autopi.ui.theme.PastelGreen
import com.autopi.ui.theme.PastelPurple
import com.autopi.ui.theme.Purple10
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Composable
fun CloudCommandsPage(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.TopStart
    ) {
        CloudCommandsScreen()
    }
}


@Composable
fun CloudCommandsScreen() {


    val viewModel: CloudCommandsViewModel = koinViewModel()

    val state = viewModel.filteredListOfCommands.collectAsState()


    Box(modifier = Modifier.fillMaxSize()) {
        CloudCommandsList(state.value, viewModel)
    }
}

@Composable
fun CloudCommandsList(cloudCommands: List<CloudCommandModel>, viewModel: CloudCommandsViewModel) {

    val state = rememberLazyListState()
    val installedCommandVersions = viewModel.installedCommandVersions.collectAsState()

    val isAtBottom = !state.canScrollForward



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
                        text = "Install New",
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            item {
                SearchBar(viewModel.searchCommandQuery,"Search command catalog"){
                    viewModel.searchInCommands(viewModel.searchCommandQuery.value)
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
                    val installedVersion = installedCommandVersions.value[it.id]
                    CloudCommandCard(
                        card = it,
                        isInstalled = installedVersion != null,
                        updateAvailable = installedVersion?.let { version ->
                            isCloudCommandUpdateAvailable(it.version, version)
                        } == true
                    )
                }
            }


        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudCommandCard(
    card: CloudCommandModel,
    isInstalled: Boolean,
    updateAvailable: Boolean
) {

    var isLoading by remember {
        mutableStateOf(false)
    }


    val viewModel: CloudCommandsViewModel = koinViewModel()


    ElevatedCard(
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .combinedClickable(
                onClick = {
                    Timber.d("CLICK DETECTED")
                    viewModel.selectedCommand.value = card
                    viewModel.main.dispatchEvent(ViewModelEvent.OpenCloudCommandDetails)

                }
            ).border(BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(.35F)), shape= RoundedCornerShape(15.dp))
        ,

        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.elevatedCardColors(containerColor =  Color.Black.copy(0.13F))
    ) {

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                val status = when {
                    updateAvailable -> "update"
                    isInstalled -> "installed"
                    else -> card.status.ifBlank { "catalog" }
                }
                val badgeColor = when (status.lowercase()) {
                    "update" -> Color(0xFFFFD166)
                    "installed" -> PastelGreen
                    "stable" -> GreenGrey60
                    "beta" -> PastelPurple
                    "experimental" -> Purple10
                    else -> MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .clip(
                            RoundedCornerShape(10.dp)
                        )
                        .background(badgeColor)
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = status.uppercase(),
                        fontSize = 13.3.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(15.dp), verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = card.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(.72F)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.id,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(0.8F),
                        modifier = Modifier.fillMaxWidth(.85F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.summary.ifBlank { "Catalog entry" },
                        maxLines = 2,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7F),
                        modifier = Modifier
                            .fillMaxWidth()
                        //.basicMarquee()
                    )
                    val tagsText = card.tags.take(3).joinToString("  ") { "#$it" }
                    if (tagsText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tagsText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.55F),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
