package com.autosec.pie.autopieapp.presentation.screens

import android.app.Activity
import android.app.Activity.RESULT_OK
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistRemove
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import com.autosec.pie.autopieapp.presentation.elements.EmptyItemsBadge
import com.autosec.pie.autopieapp.presentation.elements.GenericTextAndSelectorFormField
import com.autosec.pie.autopieapp.presentation.elements.MultiFilePicker
import com.autosec.pie.autopieapp.presentation.elements.OptionSelectorBoolean
import com.autosec.pie.autopieapp.presentation.elements.PasswordFormField
import com.autosec.pie.autopieapp.presentation.elements.SingleFilePicker
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
    callerName: String = "SHARE",
    isAsync: Boolean,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {},
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


                if(viewModel.commandNotFound.value == true){
                    Box(Modifier.fillMaxWidth()){
                        EmptyItemsBadge(Icons.Default.PlaylistRemove, "Command does not exist.")
                    }
                }

                viewModel.currentExtrasDetails.value?.let {
                    CommandExtraInputs(it.second, parentSheetState, open, state, callerName, isAsync
                    )
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
                if(callerName == "DIRECT_ICON" || callerName == "EXTERNAL_APP"){
                    activity?.finish()
                }
                viewModel.currentExtrasDetails.value = null
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CommandExtraInputs(command: CommandModel, parentSheetState: SheetState? = null, openState: MutableState<Boolean>,sheetState: SheetState, callerName: String,isAsync: Boolean,) {

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
        derivedStateOf { if(extraInput.value.isNotEmpty()) extraInput.value.split(",") else emptyList() }
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

    val scrollState = rememberScrollState()

    Text(
        text = command.name,
        lineHeight = 32.sp,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
    
    Spacer(modifier = Modifier.height(20.dp))

    Box(){
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(bottom = 90.dp)
        ) {

            if(fileUris == null && currentLink == null && listOf("INPUT_FILE", "INPUT_URL", "INPUT_URLS", "INPUT_FILES").any{command.command.contains(it)}){
                GenericTextAndSelectorFormField(text = extraInput, title = "INPUT", subtitle = "Put file, url or text here to set as INPUT for the command.", useRelativePaths = false)
            }


            for(extra in command.extras ?: emptyList()) {

                Column(Modifier.fillMaxWidth(if(extra.description.isNotEmpty()) 1F else 0.47F)) {
                    when (extra.type) {
                        "STRING" -> {

                            val isPasswordField = remember{extra.name.endsWith("PASSWORD") || extra.name.endsWith("PASSWD")}

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


                            if(isPasswordField){
                                PasswordFormField(text = textValue , title = extra.name, subtitle = extra.description)
                            }
                            else{
                                GenericTextFormField(text = textValue , title = extra.name, subtitle = extra.description){
                                    if(extra.name.endsWith("FILES")){
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            MultiFilePicker(useRelativePaths = false){
                                                textValue.value = it.joinToString(",")
                                            }
                                        }
                                    }
                                    else if(extra.name.endsWith("FILE")){
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            SingleFilePicker(useRelativePaths = false){
                                                textValue.value = it
                                            }
                                        }
                                    }
                                }
                            }



                        }

                        "BOOLEAN" -> {
                            val booleanExpanded = remember { mutableStateOf(false) }
                            val selectedOptionForBoolean =
                                rememberSaveable {
                                    mutableStateOf(extra.defaultBoolean.toString())
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


                            OptionSelectorBoolean(
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

        }


        Row(Modifier.align(Alignment.BottomCenter)){
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20),
                //contentPadding = PaddingValues(vertical = 20.dp),
                onClick = {
                    viewModel.main.viewModelScope.launch {

                        if(isLoading){
                            return@launch
                        }

                        isLoading = true
                        val processId = (100000..999999).random()
                        viewModel.onCommandClickWithExtras(command, currentLink ?: extraInput.value, fileUris ?: extraInputList.value, commandExtraInputs.value, processId)

                        if(callerName == "EXTERNAL_APP" && !isAsync){
                            delay(900)
                            return@launch
                        }else if(callerName == "EXTERNAL_APP"){
                            //IS EXTERNAL_APP and async
                            delay(900)
                            val result = Intent().apply {
                                putExtra("status", "running")
                                putExtra("processId", processId)
                            }
                            activity?.setResult(RESULT_OK, result)
                            activity?.finish()
                            viewModel.currentExtrasDetails.value = null
                        }

                        if(parentSheetState != null){
                            //When this activity is opened from share receiver
                            delay(900)
                            activity?.finish()
                            viewModel.currentExtrasDetails.value = null
                        }else{
                            //when this activity is opened from the app itself.
                            delay(1500)
                            openState.value = false
                            viewModel.currentExtrasDetails.value = null
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