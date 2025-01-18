package com.autosec.pie.autopieapp.presentation.screens

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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.presentation.viewModels.CloudCommandsViewModel
import com.autosec.pie.autopieapp.presentation.viewModels.CloudPackagesViewModel
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudPackageDetails(
    state: SheetState,
    open: MutableState<Boolean>,
    key: String? = null,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val viewModel: CloudPackagesViewModel by KoinJavaComponent.inject(CloudPackagesViewModel::class.java)

    LaunchedEffect(key1 = key) {
        //viewModel.getCommandDetails(key)
        viewModel.isLoading.value = false
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(.95F),
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {

            if (viewModel.isLoading.value) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp)
                ) {

                    Column(
                        Modifier
                            .weight(1F, true)
                            .verticalScroll(rememberScrollState())){
//                        Row(
//                            modifier = Modifier
//                                .padding(vertical = 20.dp)
//                                .fillMaxWidth()
//                                .padding(vertical = 0.dp)
//                        ) {
//                            Text(
//                                text = viewModel.selectedPackage.value?.name ?: "",
//                                fontSize = 33.sp,
//                                fontWeight = FontWeight.Bold,
//                                color = MaterialTheme.colorScheme.onPrimaryContainer
//                            )
//                        }

                        Spacer(modifier = Modifier.height(100.dp))

                        Markdown(
                            content = viewModel.selectedPackage.value?.description ?: "",
                            imageTransformer = Coil3ImageTransformerImpl,
                        )
                    }

                    var isLoading by remember {
                        mutableStateOf(false)
                    }

                    Row(){
                        Button(
                            modifier = Modifier
                                .padding(vertical = 15.dp)
                                .height(52.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(20),
                            //contentPadding = PaddingValues(vertical = 20.dp),
                            onClick = {
                                viewModel.main.viewModelScope.launch {

                                    try {
                                        isLoading = true
                                        delay(1000L)
                                        open.value = false
                                    }catch (e: Exception){
                                        Timber.e(e)
                                    }
                                }
                            },

                            ) {


                            Column {
                                when (isLoading) {
                                    true -> {
                                        CircularProgressIndicator(
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(24.dp),
                                            color = Color.Black.copy(alpha = 0.4F)
                                        )
                                    }

                                    false -> {
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