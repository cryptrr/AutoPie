package com.autopi.autopieapp.presentation.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.dp
import com.autopi.autopieapp.domain.Notification
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import org.koin.java.KoinJavaComponent
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun SnackbarHostCustom() {


    val viewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)

    val snackbarHostState = remember { SnackbarHostState() }

    var bannerState : Notification? by remember{ mutableStateOf(null) }


    LaunchedEffect(key1 = Unit) {
        flowOf(viewModel.viewModelError, viewModel.appNotification).flattenMerge(2).collectLatest { notification ->
            if (notification == null) {
                snackbarHostState.currentSnackbarData?.dismiss()
                bannerState = null
                return@collectLatest
            }

            bannerState = notification
            snackbarHostState.showSnackbar(
                "",
                duration = if (notification.infinite) SnackbarDuration.Indefinite else SnackbarDuration.Short,
            )
            bannerState = null
        }
    }



    if (bannerState != null && snackbarHostState.currentSnackbarData != null) {
        Timber.d("SnackbarHostCustom")

        Popup(alignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = {
                        Banner(bannerState!!)
                    }
                )
            }
        }
    }
}
