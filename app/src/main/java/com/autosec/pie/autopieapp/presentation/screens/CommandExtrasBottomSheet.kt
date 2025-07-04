package com.autosec.pie.autopieapp.presentation.screens

import android.app.Activity
import android.content.Intent
import android.widget.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.autosec.pie.utils.getActivity
import com.autosec.pie.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandExtrasBottomSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    parentSheetState: SheetState? = null,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val viewModel: ShareReceiverViewModel = koinViewModel()

    val scope = rememberCoroutineScope()

    val activity = LocalContext.current.getActivity()

    LaunchedEffect(key1 = state.targetValue) {
        if (state.targetValue == SheetValue.Expanded) {
            parentSheetState?.hide()
        } else {
            parentSheetState?.show()
        }
    }

    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
            //.height(700.dp)
            //.fillMaxHeight(0.40F)
            ,
            contentAlignment = Alignment.TopStart

        )
        {


            Column(
                Modifier
                    //.fillMaxSize()
                    .padding(horizontal = 15.dp)
            ) {

                viewModel.currentExtrasDetails.value?.let {
                    CommandExtraInputs(it.second, parentSheetState, open, state)
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
                //activity?.finish()
                viewModel.currentExtrasDetails.value = null
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CommandExtraInputs(command: CommandModel, parentSheetState: SheetState? = null, openState: MutableState<Boolean>, sheetState: SheetState) {

    val context = LocalContext.current

    val activity = LocalContext.current.getActivity()


    val viewModel: ShareReceiverViewModel = koinViewModel()

    val fileUris by remember {
        mutableStateOf(viewModel.currentExtrasDetails.value?.third?.fileUris)
    }
    val currentLink by remember {
        mutableStateOf(viewModel.currentExtrasDetails.value?.third?.currentLink)
    }

    val extraInput = remember {
        mutableStateOf("")
    }

    val extraInputList = remember {
        derivedStateOf { extraInput.value.split(",") }
    }


    var isLoading by remember {
        mutableStateOf(false)
    }

    val commandExtraInputs = remember {
        mutableStateOf<List<CommandExtraInput>>(emptyList())
    }

    fun addToExtraInputs(commandExtraInput: CommandExtraInput) {
        if (commandExtraInputs.value.any { it.name == commandExtraInput.name }) {
            commandExtraInputs.value = commandExtraInputs.value.toMutableList().also {
                val index = it.indexOfFirst { it.name == commandExtraInput.name }

                it.set(index, commandExtraInput)
            }
        } else {
            commandExtraInputs.value =
                commandExtraInputs.value.toMutableList().also { it.add(0, commandExtraInput) }
        }
        Timber.d("Extra commands list: $commandExtraInputs")
    }

    Text(
        text = command.name,
        lineHeight = 32.sp,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
    
    Spacer(modifier = Modifier.height(20.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

//        Timber.d("FileUris: $fileUris")
//        Timber.d("currentLink: $currentLink")

        if(fileUris == null && currentLink == null && command.command.contains("INPUT_FILE")){

            GenericTextAndSelectorFormField(text = extraInput, title = "INPUT", subtitle = "Put file or url here to set as \${INPUT_FILE} for the command.")
        }else{
            Spacer(modifier = Modifier.height(7.dp))
        }

        for (extra in command.extras ?: emptyList()) {
            Column(Modifier.fillMaxWidth(if(extra.description.isNotEmpty()) 1F else 0.47F)) {
                when (extra.type) {
                    "STRING" -> {
                        val textValue = remember {
                            mutableStateOf(extra.default)
                        }

                        LaunchedEffect(key1 = textValue.value) {
                            addToExtraInputs(
                                CommandExtraInput(
                                    extra.name,
                                    extra.default,
                                    textValue.value,
                                    extra.type,
                                    extra.defaultBoolean,
                                    extra.id,
                                    extra.description
                                )
                            )
                        }

                        GenericTextFormField(text = textValue, title = extra.name, subtitle = extra.description)
                    }

                    "BOOLEAN" -> {
                        val booleanExpanded = remember { mutableStateOf(false) }
                        val selectedOptionForBoolean =
                            rememberSaveable {
                                mutableStateOf(extra.defaultBoolean.toString().uppercase())
                            }
                        val booleanOptions = listOf("TRUE", "FALSE")

                        LaunchedEffect(key1 = selectedOptionForBoolean.value) {
                            addToExtraInputs(
                                CommandExtraInput(
                                    extra.name,
                                    extra.default,
                                    selectedOptionForBoolean.value,
                                    extra.type,
                                    extra.defaultBoolean,
                                    extra.id,
                                    extra.description
                                )
                            )
                        }

                        Text(text = extra.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if(extra.description.isNotEmpty()){
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(text = extra.description, fontSize = 14.sp, fontWeight = FontWeight.Normal)
                        }
                        Spacer(modifier = Modifier.height(10.dp))


                        OptionSelector(
                            options = booleanOptions,
                            selectedOption = selectedOptionForBoolean,
                            expanded = booleanExpanded
                        )
                    }

                    "SELECTABLE" -> {
                        val expanded = remember { mutableStateOf(false) }
                        val selectedOption =
                            rememberSaveable {
                                mutableStateOf(extra.default)
                            }
                        val options = extra.selectableOptions

                        LaunchedEffect(key1 = selectedOption.value) {
                            addToExtraInputs(
                                CommandExtraInput(
                                    extra.name,
                                    extra.default,
                                    selectedOption.value,
                                    extra.type,
                                    extra.defaultBoolean,
                                    extra.id,
                                    extra.description
                                )
                            )
                        }

                        Column {
                            Text(
                                text = extra.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if(extra.description.isNotEmpty()){
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(text = extra.description, fontSize = 14.sp, fontWeight = FontWeight.Normal)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OptionSelector(
                                options = options,
                                selectedOption = selectedOption,
                                expanded = expanded
                            )
                        }
                    }
                }


            }
        }

        Row {
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

                            val gson = Gson()
                            val commandJson = gson.toJson(command)
                            val fileUrisJson = gson.toJson(fileUris ?: extraInputList.value)

                            val commandExtraInputsJson = gson.toJson(commandExtraInputs.value)

                            val intent = Intent(context, ForegroundService::class.java).apply {
                                putExtra("command", commandJson)
                                putExtra("currentLink", currentLink ?: extraInput.value)
                                putExtra("fileUris", fileUrisJson)
                                putExtra("commandExtraInputs", commandExtraInputsJson)
                            }


                            startForegroundService(context, intent)

                            if(parentSheetState != null){
                                delay(900)
                                activity?.finish()
                                viewModel.currentExtrasDetails.value = null
                            }else{
                                delay(1500)
                                openState.value = false
                                viewModel.currentExtrasDetails.value = null
                            }
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
                                text = "RUN",
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