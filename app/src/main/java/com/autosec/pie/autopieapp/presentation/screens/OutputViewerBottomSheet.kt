package com.autosec.pie.autopieapp.presentation.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.CommandExtraInput
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.presentation.elements.GenericTextFormField
import com.autosec.pie.autopieapp.presentation.elements.OptionSelector
import com.autosec.pie.autopieapp.data.services.ForegroundService
import com.autosec.pie.autopieapp.presentation.elements.GenericTextAndSelectorFormField
import com.autosec.pie.autopieapp.presentation.elements.MultiFilePicker
import com.autosec.pie.autopieapp.presentation.elements.OptionSelectorBoolean
import com.autosec.pie.autopieapp.presentation.elements.PasswordFormField
import com.autosec.pie.autopieapp.presentation.elements.SingleFilePicker
import com.autosec.pie.autopieapp.presentation.viewModels.OutputViewerViewModel
import com.autosec.pie.utils.getActivity
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputViewerBottomSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    parentSheetState: SheetState? = null,
    callerName: String = "SHARE",
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {},
) {

    val viewModel: OutputViewerViewModel = koinViewModel()

    val scope = rememberCoroutineScope()

    val activity = LocalContext.current.getActivity()

    LaunchedEffect(key1 = state.targetValue) {
        if (state.targetValue == SheetValue.Expanded) {
            parentSheetState?.hide()
        } else {
            parentSheetState?.show()
        }
    }

    val outputState = viewModel.outputContent.collectAsState()

    val scroll = rememberScrollState()


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.wrapContentHeight()
                .fillMaxHeight(0.80F)
            ,
            contentAlignment = Alignment.TopStart

        )
        {
            Column(
                Modifier
                    //.fillMaxSize()
                    .padding(15.dp)

            ) {

                Text(
                    text = viewModel.currentLogPath.value?.split("/")?.last() ?: "",
                    lineHeight = 32.sp,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(Modifier.height(20.dp))

                viewModel.currentLogPath.value?.let {
                    Column(Modifier.clip(
                        RoundedCornerShape(15.dp)
                    ).fillMaxHeight().background(Color.Black.copy(alpha = 0.2F)).padding(horizontal = 15.dp).verticalScroll(scroll)){
                        SelectionContainer {
                            Text(outputState.value)
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
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onDismissRequest = {
            scope.launch {
                activity?.finish()
                viewModel.currentLogPath.value = null
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, device = "spec:width=1280px,height=2856px,dpi=480,cutout=corner")
@Composable
fun OutputBottomSheetPreview() {
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            //.wrapContentHeight()
            .fillMaxHeight(0.80F)
        ,
        contentAlignment = Alignment.TopStart
    )
    {
        Column(
            Modifier
                //.fillMaxSize()
                .padding(15.dp)
        ) {

            Text(
                text = "Shit is whack",
                lineHeight = 32.sp,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(20.dp))

            Column(
                Modifier.clip(
                    RoundedCornerShape(15.dp)
                ).fillMaxHeight().background(Color.Black.copy(alpha = 0.2F)).padding(15.dp)
                    .verticalScroll(scroll)
            ) {
                SelectionContainer {
                    Text("RAAAAAA")
                }
            }
        }
    }
}
