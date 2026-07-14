package com.autopi.autopieapp.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopi.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudCommandDetails(
    state: SheetState,
    open: MutableState<Boolean>,
    key: String? = null,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val viewModel: CloudCommandsViewModel = koinViewModel()
    val selectedCommandId = viewModel.selectedCommand.value?.id

    LaunchedEffect(key1 = selectedCommandId) {
        if (selectedCommandId != null) {
            viewModel.loadSelectedCommandDocumentation()
        }
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(.95F)

            ,
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {

            if (viewModel.isLoading.value) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val command = viewModel.selectedCommand.value
                Column(
                    Modifier

                        .fillMaxSize()
                        .padding(horizontal = 15.dp)

                ) {

                    Column(
                        Modifier
                            .weight(1F, true)
                            .verticalScroll(rememberScrollState())){

                        Spacer(modifier = Modifier.height(100.dp))
                        Text(
                            text = command?.name.orEmpty(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = command?.id.orEmpty(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(0.8F)
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Text(
                            text = command?.summary.orEmpty(),
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8F)
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Text(
                            text = listOfNotNull(
                                command?.namespace?.takeIf { it.isNotBlank() }?.let { "Namespace: $it" },
                                command?.status?.takeIf { it.isNotBlank() }?.let { "Status: $it" },
                                command?.version?.takeIf { it.isNotBlank() }?.let { "Version: $it" }
                            ).joinToString("\n"),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7F)
                        )
                        command?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = tags.joinToString("  ") { "#$it" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.58F)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        if (viewModel.detailsLoading.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        } else {
                            val docs = viewModel.selectedCommandDocumentation.value
                            docs?.readme?.takeIf(String::isNotBlank)?.let { readme ->
                                Text(
                                    text = "README.md",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Markdown(
                                    content = readme,
                                    imageTransformer = Coil3ImageTransformerImpl,
                                )
                            }
                            docs?.changelog?.takeIf(String::isNotBlank)?.let { changelog ->
                                Spacer(modifier = Modifier.height(28.dp))
                                Text(
                                    text = "CHANGELOG.md",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Markdown(
                                    content = changelog,
                                    imageTransformer = Coil3ImageTransformerImpl,
                                )
                            }
                        }
                    }

                    Row(){
                        Button(
                            modifier = Modifier
                                .padding(vertical = 15.dp)
                                .height(52.dp)
                                .fillMaxWidth(),
                            enabled = !viewModel.installInProgress.value,
                            shape = RoundedCornerShape(20),
                            //contentPadding = PaddingValues(vertical = 20.dp),
                            onClick = {
                                viewModel.installSelectedCommand {
                                    open.value = false
                                }
                            },

                            ) {


                            Column {
                                if (viewModel.installInProgress.value) {
                                    CircularProgressIndicator(
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = Color.Black.copy(alpha = 0.4F)
                                    )
                                } else {
                                    Text(
                                        text = "INSTALL",
                                        //modifier = Modifier.align(Alignment.Center),
                                        letterSpacing = 1.11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                        }
                    }
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
