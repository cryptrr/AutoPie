package com.autopi.autopieapp.presentation.screens

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Build
import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.viewModelScope
import com.autopi.autopieapp.data.CommandExtraInput
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.ExtraFlags
import com.autopi.autopieapp.data.firstStepOrSelf
import com.autopi.autopieapp.data.flagValue
import com.autopi.autopieapp.data.hasFlag
import com.autopi.autopieapp.data.hasNextStep
import com.autopi.autopieapp.data.matchesExtraValues
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.presentation.elements.GenericTextFormField
import com.autopi.autopieapp.presentation.elements.OptionSelector
import com.autopi.autopieapp.data.services.ForegroundService
import com.autopi.autopieapp.presentation.elements.EmptyItemsBadge
import com.autopi.autopieapp.presentation.elements.FlagSelector
import com.autopi.autopieapp.presentation.elements.GenericTextAndSelectorFormField
import com.autopi.autopieapp.presentation.elements.MultiFilePicker
import com.autopi.autopieapp.presentation.elements.OptionSelectorBoolean
import com.autopi.autopieapp.presentation.elements.PasswordFormField
import com.autopi.autopieapp.presentation.elements.SingleFilePicker
import com.autopi.autopieapp.presentation.elements.SliderSelector
import com.autopi.utils.getActivity
import com.autopi.autopieapp.presentation.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import kotlin.math.roundToInt

private val ENVIRONMENT_DEFAULT = Regex("\\$\\$([A-Za-z_][A-Za-z0-9_]*)")

private fun String.toShellBooleanOrNull(): String? = when (trim().lowercase()) {
    "true", "1", "yes", "on" -> "true"
    "false", "0", "no", "off" -> "false"
    else -> null
}

private fun String.toSelectableOptions(): Map<String, String> =
    split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .associateTo(linkedMapOf()) { option ->
            val label = option.substringBefore("=").trim()
            val value = option.substringAfter("=", option).trim()
            label to value
        }

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
                    val activeCommand = it.second.firstStepOrSelf()
                    key(activeCommand.steps.size, activeCommand.path, activeCommand.command) {
                        CommandExtraInputs(activeCommand, parentSheetState, open, state, callerName, isAsync)
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
                viewModel.currentExtrasDetails.value?.let { (_, command, inputs) ->
                    if (command.multiStage == true) {
                        inputs.processId?.let { processId ->
                            viewModel.main.dispatchEvent(ViewModelEvent.StopShell(processId))
                        }
                    }
                }
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


    var isLoading by remember(command) {
        mutableStateOf(false)
    }

    val requestedProcessId = viewModel.currentExtrasDetails.value?.third?.processId
    val processId = remember(command, requestedProcessId) {
        requestedProcessId ?: (100000..999999).random()
    }

    LaunchedEffect(command, processId) {
        if (command.multiStage == true) {
            val currentDetails = viewModel.currentExtrasDetails.value
            if (currentDetails != null && currentDetails.third.processId == null) {
                viewModel.currentExtrasDetails.value = Triple(
                    currentDetails.first,
                    currentDetails.second,
                    currentDetails.third.copy(processId = processId)
                )
            }
            viewModel.main.dispatchEvent(ViewModelEvent.CreateShell(processId))
        }
    }


    val commandExtraInputs = remember(command.extras) {
        mutableStateOf(
            command.extras.orEmpty()
                .map { extra ->
                    CommandExtraInput(
                        extra.name,
                        extra.default,
                        //TODO: Check if this is necessary
                        when {
                            extra.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) -> extra.default
                            extra.type == "BOOLEAN" -> extra.defaultBoolean.toString()
                            extra.type == "SLIDER" ->
                                extra.default.split(",").getOrNull(1) ?: extra.default
                            extra.type == "FLAG" -> ""
                            else -> extra.default
                        },
                        extra.type,
                        extra.defaultBoolean,
                        extra.id,
                        extra.description
                    )
                }
        )
    }

    val extraValuesById = commandExtraInputs.value.associate { it.id to it.value }
    val visibleExtras = command.extras.orEmpty()
        .filterNot { it.flags.hasFlag(ExtraFlags.INTERNAL_CONFIG) }
        .filter { extra ->
            extra.visibleWhen?.matchesExtraValues(extraValuesById) != false
        }

    fun addToExtraInputs(commandExtraInput: CommandExtraInput) {
        if (commandExtraInputs.value.any { it.id == commandExtraInput.id }) {
            commandExtraInputs.value = commandExtraInputs.value.toMutableList().also {
                val index = it.indexOfFirst { it.id == commandExtraInput.id }

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


            for(extra in visibleExtras.filter { it.type != "FLAG" }) {
                key(extra.id) {
                    Column(Modifier.fillMaxWidth(if(extra.description.isNotEmpty()) 1F else 0.47F)) {
                    when (extra.type) {
                        "STRING" -> {

                            val isPasswordField = remember(extra.name, extra.flags) {
                                extra.flags.hasFlag(ExtraFlags.PASSWORD) ||
                                    extra.name.endsWith("PASSWORD") ||
                                    extra.name.endsWith("PASSWD") ||
                                    extra.name.endsWith("SECRET")
                            }
                            val useMultiFilePicker = remember(extra.name, extra.flags) {
                                extra.flags.hasFlag(ExtraFlags.MULTI_FILE_PICKER) || extra.name.endsWith("FILES")
                            }
                            val useSingleFilePicker = remember(extra.name, extra.flags) {
                                extra.flags.hasFlag(ExtraFlags.FILE_PICKER) || extra.name.endsWith("FILE")
                            }
                            val pickerMimeType = extra.flags.flagValue(ExtraFlags.MIME_TYPE) ?: "*/*"

                            val textValue = remember(extra.id, extra.default) {
                                mutableStateOf(extra.default)
                            }

                            val shellEnvironmentVariable = remember(extra.default) {
                                ENVIRONMENT_DEFAULT.matchEntire(extra.default)?.groupValues?.get(1)
                            }

                            LaunchedEffect(processId, extra.id, shellEnvironmentVariable) {
                                shellEnvironmentVariable?.let { variableName ->
                                    viewModel.processManagerService
                                        .getShellEnvironmentVariable(processId, variableName)
                                        ?.let { textValue.value = it }
                                }
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
                                GenericTextFormField(
                                    text = textValue,
                                    title = extra.name,
                                    subtitle = extra.description,
                                    trailingIcon = if(useMultiFilePicker){
                                        {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                MultiFilePicker(
                                                    useRelativePaths = true,
                                                    mimeType = pickerMimeType
                                                ) {
                                                    textValue.value = it.joinToString(",")
                                                }
                                            }
                                        }
                                    }
                                    else if(useSingleFilePicker){
                                        {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                SingleFilePicker(
                                                    useRelativePaths = true,
                                                    mimeType = pickerMimeType
                                                ) {
                                                    textValue.value = it
                                                }
                                            }
                                        }
                                    }
                                    else {
                                        null
                                    }
                                )
                            }



                        }

                        "BOOLEAN" -> {
                            val booleanExpanded = remember { mutableStateOf(false) }
                            val shellEnvironmentVariable = remember(extra.default) {
                                ENVIRONMENT_DEFAULT.matchEntire(extra.default)?.groupValues?.get(1)
                            }
                            val selectedOptionForBoolean =
                                rememberSaveable(extra.id, extra.default, extra.defaultBoolean) {
                                    mutableStateOf(extra.defaultBoolean.toString())
                                }
                            val booleanOptions = listOf("TRUE", "FALSE")

                            LaunchedEffect(processId, extra.id, shellEnvironmentVariable) {
                                shellEnvironmentVariable?.let { variableName ->
                                    viewModel.processManagerService
                                        .getShellEnvironmentVariable(processId, variableName)
                                        ?.toShellBooleanOrNull()
                                        ?.let { selectedOptionForBoolean.value = it }
                                }
                            }

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
                            val configuredOptions = extra.selectableOptions
                            val shellEnvironmentVariable = remember(configuredOptions) {
                                configuredOptions.values.singleOrNull()
                                    ?.let { ENVIRONMENT_DEFAULT.matchEntire(it) }
                                    ?.groupValues
                                    ?.get(1)
                            }
                            var options by remember(extra.id, configuredOptions) {
                                mutableStateOf(configuredOptions)
                            }
                            val selectedOption =
                                rememberSaveable(extra.id, extra.default, configuredOptions) {
                                    mutableStateOf(
                                        configuredOptions[extra.default]
                                            ?: extra.default.ifEmpty {
                                                configuredOptions.values.firstOrNull().orEmpty()
                                            }
                                    )
                                }

                            LaunchedEffect(processId, extra.id, shellEnvironmentVariable) {
                                shellEnvironmentVariable?.let { variableName ->
                                    viewModel.processManagerService
                                        .getShellEnvironmentVariable(processId, variableName)
                                        ?.toSelectableOptions()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let { resolvedOptions ->
                                            options = resolvedOptions
                                            selectedOption.value = resolvedOptions[extra.default]
                                                ?: extra.default.ifEmpty {
                                                    resolvedOptions.values.firstOrNull().orEmpty()
                                                }
                                        }
                                }
                            }

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
                        "SLIDER" -> {
                            val shellEnvironmentVariable = remember(extra.default) {
                                ENVIRONMENT_DEFAULT.matchEntire(extra.default)?.groupValues?.get(1)
                            }
                            var sliderConfiguration by remember(extra.id, extra.default) {
                                mutableStateOf(extra.default)
                            }

                            LaunchedEffect(processId, extra.id, shellEnvironmentVariable) {
                                shellEnvironmentVariable?.let { variableName ->
                                    viewModel.processManagerService
                                        .getShellEnvironmentVariable(processId, variableName)
                                        ?.let { sliderConfiguration = it }
                                }
                            }

                            val sliderValues = sliderConfiguration.split(",")
                            val startValue = sliderValues.elementAtOrNull(0)?.trim()?.toFloatOrNull() ?: 0F
                            val endValue = sliderValues.elementAtOrNull(2)?.trim()?.toFloatOrNull() ?: 100F
                            val defaultValue = sliderValues.elementAtOrNull(1)
                                ?.trim()
                                ?.toFloatOrNull()
                                ?.coerceIn(startValue, endValue)
                                ?: 57F.coerceIn(startValue, endValue)

                            //Timber.d("RawDef: ${extra.default} DEFAULT: ${extra.default.split(",")} Start value: $startValue, End value: $endValue, Default Value: $defaultValue")


                            val sliderState = remember(sliderConfiguration) {
                                SliderState(
                                    value = defaultValue
                                    ,
                                    valueRange = startValue..endValue,
                                    steps = (endValue - startValue).toInt() - 1
                                )
                            }

                            LaunchedEffect(key1 = sliderState.value) {
                                addToExtraInputs(
                                    CommandExtraInput(
                                        extra.name,
                                        extra.default,
                                        sliderState.value.toString(),
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
                                Text(text = "VALUE: ${sliderState.value}", fontSize = 14.sp, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic)
                                Spacer(modifier = Modifier.height(10.dp))

                                SliderSelector(sliderState)

                            }
                        }
                    }
                    }
                }
            }

            val horizontalScrollState = rememberScrollState()


            if(visibleExtras.any { it.type == "FLAG" }){
                Column {
                    Text(
                        text = "FLAGS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(text = "Extra flags to enable/disable.", fontSize = 14.sp, fontWeight = FontWeight.Normal)
                }
            }



            Row(Modifier.horizontalScroll(horizontalScrollState), horizontalArrangement = Arrangement.spacedBy(5.dp)){
                for(flag in visibleExtras.filter { it.type == "FLAG" }){
                    key(flag.id) {
                    val isChecked = remember { mutableStateOf(false) }

                    //Timber.d("RawDef: ${extra.default} DEFAULT: ${extra.default.split(",")} Start value: $startValue, End value: $endValue, Default Value: $defaultValue")

                    LaunchedEffect(key1 = isChecked.value) {
                        addToExtraInputs(
                            CommandExtraInput(
                                flag.name,
                                flag.default,
                                if (isChecked.value) flag.default else "",
                                flag.type,
                                flag.defaultBoolean,
                                flag.id,
                                flag.description
                            )
                        )
                    }

                    Column(Modifier.widthIn(min = 100.dp, max = 250.dp)){

                        FlagSelector(flag, isChecked.value) { isChecked.value = it }

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

                        viewModel.onCommandClickWithExtras(command, currentLink ?: extraInput.value, fileUris ?: extraInputList.value, commandExtraInputs.value, processId)

                        if (command.hasNextStep()) {
                            return@launch
                        }

                        //Don't close the activity if intent is started with async false.
                        //If intent is started with async true, the closing logic is in the event listener inside DirectCommandActivity
                        if(callerName == "EXTERNAL_APP"){
                            return@launch
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
