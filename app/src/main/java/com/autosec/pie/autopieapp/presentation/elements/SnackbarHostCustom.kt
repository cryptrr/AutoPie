package com.autosec.pie.autopieapp.presentation.elements

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.autosec.pie.autopieapp.domain.Notification
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
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
            notification?.apply {
                bannerState = this
                snackbarHostState.showSnackbar(
                    "",
                    duration = if (bannerState!!.infinite) SnackbarDuration.Indefinite else SnackbarDuration.Short,
                )
            }
        }
    }



    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
        //.background(Color.Blue.copy(alpha = 0.2F))
    ) {

        Timber.d("SnackbarHostCustom")

        val (snackbarHostRef) = createRefs()

        SnackbarHost(
            modifier = Modifier
                .constrainAs(snackbarHostRef) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(parent.top, margin = 270.dp)
                },
            hostState = snackbarHostState,
            snackbar = {
                Banner(bannerState!!)
            }

        )
    }
}