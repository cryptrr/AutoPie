package com.autopi.autopieapp.presentation.elements

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopi.autopieapp.data.AutoPieStrings
import com.autopi.autopieapp.data.CommandExtra


@Composable
fun CommandExtraElement(
    extrasElements: MutableState<List<CommandExtra>>,
    onAddCommandExtra: (CommandExtra) -> Unit,
    onRemoveCommandExtra: (String) -> Unit,
) {

    val rowState = rememberLazyListState()

    val derivedCommandName = remember {
        derivedStateOf { extrasElements.value.firstOrNull()?.name }
    }

    val modifiedExtraDescription = remember {
        derivedStateOf {
            if (derivedCommandName.value?.isNotBlank() == true) AutoPieStrings.EXTRAS_DESCRIPTION.replace(
                AutoPieStrings.EXTRAS_DESCRIPTION_TO_REPLACE,
                derivedCommandName.value!!
            ) else AutoPieStrings.EXTRAS_DESCRIPTION
        }
    }

    val scrollState = rememberScrollState()


    Column {
        Text(text = "EXTRAS", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = modifiedExtraDescription.value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(10.dp))

//        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
//            items(items = extrasElements.value, key = { it.id }) {
//                CommandExtraInputElement(it, extrasElements, onAddCommandExtra, onRemoveCommandExtra)
//            }
//        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(scrollState)
        ) {
            for (item in extrasElements.value.reversed()) {
                key(item.id){
                    CommandExtraInputElement(item,extrasElements, onAddCommandExtra, onRemoveCommandExtra)
                }
            }
        }
    }

}


@Composable
fun CommandExtraInputElement(
    command: CommandExtra,
    extrasElements: MutableState<List<CommandExtra>>,
    onAddCommandExtra: (CommandExtra) -> Unit,
    onRemoveCommandExtra: (String) -> Unit
) {

    val name = rememberSaveable {
        mutableStateOf(command.name)
    }.also {
        it.value = it.value.uppercase()
    }

    val default = rememberSaveable {
        mutableStateOf(command.default)
    }

    val selectableOptions = rememberSaveable {
        mutableStateOf(
            command.selectableOptions.entries.joinToString(",") { (label, value) ->
                if (label == value) label else "$label=$value"
            }
        )
    }

    val description = rememberSaveable {
        mutableStateOf(command.description)
    }

    val isRequired = rememberSaveable {
        mutableStateOf(command.required)
    }

    var expanded = remember { mutableStateOf(false) }
    var selectedCommandType =
        rememberSaveable { mutableStateOf(command.type.split(",").firstOrNull() ?: "") }
    val options = listOf("STRING", "SELECTABLE","FLAG", "BOOLEAN","SLIDER")

    //Boolean extra options
    var booleanExpanded = remember { mutableStateOf(false) }
    var selectedOptionForBoolean =
        rememberSaveable { mutableStateOf(command.defaultBoolean.toString()) }
    val booleanOptions = listOf("TRUE", "FALSE")

    val sliderOptions = rememberSaveable {
        mutableStateOf(command.default)
    }


    LaunchedEffect(
        listOf(
            name.value,
            default.value,
            selectableOptions.value,
            description.value,
            selectedCommandType.value,
            selectedOptionForBoolean.value,
            isRequired.value
        )
    ) {

        val parsedSelectableOptions = selectableOptions.value
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .associateTo(linkedMapOf()) { option ->
                val label = option.substringBefore("=").trim()
                val value = option.substringAfter("=", option).trim()
                label to value
            }

        val commandExtra = CommandExtra(
            id = command.id,
            name = name.value,
            type = selectedCommandType.value,
            default = when{
                command.type == "SELECTABLE" -> parsedSelectableOptions.values.firstOrNull() ?: ""
                else -> default.value
            },
            description = description.value,
            defaultBoolean = selectedOptionForBoolean.value.toBoolean(),
            selectableOptions = parsedSelectableOptions,
            required = isRequired.value
        )

        onAddCommandExtra(commandExtra)

    }


    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(30.dp))
            .width(300.dp)
            //.height(90.dp)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.fillMaxWidth(0.7F)) {
                OptionSelector(options, selectedCommandType, expanded)
            }

            Box(
                Modifier
                    .padding(2.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .height(55.dp)
                    .aspectRatio(1F, true)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(90.dp))
                    .clickable {

                        onRemoveCommandExtra(command.id)

                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(25.dp)
                )
            }
        }


        when (selectedCommandType.value) {
            "STRING" -> {
                GenericTextFormField(
                    text = name,
                    "",
                    placeholder = "NAME",
                    isError = name.value.isBlank(),


                    )
                GenericTextFormField(
                    text = default,
                    "",
                    placeholder = "DEFAULT",
                    isError = default.value.isBlank(),
                    trailingIcon = if(name.value.endsWith("FILE")){
                        {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                SingleFilePicker(useRelativePaths = true){
                                    default.value = it
                                }
                            }
                        }
                    }else if(name.value.endsWith("FILES")) {
                        {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MultiFilePicker(useRelativePaths = true){
                                    default.value = it.joinToString(",")
                                }
                            }
                        }
                    }else {
                        null
                    }
                )
                GenericTextFormField(
                    text = description,
                    "",
                    placeholder = "DESCRIPTION",
                    singleLine = false,
                )
                if(default.value.isEmpty()){
                    GenericFormSwitch(
                        text = "Required",
                        switchState = isRequired,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        onChange = { isRequired.value = it }
                    )
                }
            }

            "BOOLEAN" -> {
                GenericTextFormField(
                    text = name,
                    "",
                    placeholder = "NAME",
                    isError = name.value.isBlank()
                )
                OptionSelector(
                    options = booleanOptions,
                    selectedOption = selectedOptionForBoolean,
                    expanded = booleanExpanded
                )
                GenericTextFormField(
                    text = description,
                    "",
                    placeholder = "DESCRIPTION",
                    singleLine = false,
                )
            }

            "SELECTABLE" -> {
                GenericTextFormField(
                    text = name,
                    "",
                    placeholder = "NAME",
                )
                GenericTextFormField(
                    text = selectableOptions,
                    "",
                    placeholder = "OPTIONS",
                    subtitle = "Separate options with commas. Use Label=raw value for a user-friendly label. The first value is the default.",
                    isError = selectableOptions.value.isBlank()
                )
                GenericTextFormField(
                    text = description,
                    "",
                    placeholder = "DESCRIPTION",
                    singleLine = false,
                )
            }
            "SLIDER" -> {
                GenericTextFormField(
                    text = name,
                    "",
                    placeholder = "NAME",
                )
                GenericTextFormField(
                    text = default,
                    "",
                    placeholder = "OPTIONS",
                    subtitle = "Options for this field. Separate options with commas (FROM,DEFAULT,TO)",
                    isError = default.value.isBlank()
                )
                GenericTextFormField(
                    text = description,
                    "",
                    placeholder = "DESCRIPTION",
                    singleLine = false,
                )
            }
            "FLAG" -> {
                GenericTextFormField(
                    text = name,
                    "",
                    placeholder = "NAME",
                )
                GenericTextFormField(
                    text = default,
                    "",
                    placeholder = "FLAG",
                    subtitle = "Flag to set. Eg --flag. Checkbox will be shown to select/deselect.",
                    isError = default.value.isBlank()
                )
                GenericTextFormField(
                    text = description,
                    "",
                    placeholder = "DESCRIPTION",
                    singleLine = false,
                )
            }

        }

    }
}

@Composable
fun OptionSelectorBoolean(
    options: List<String>,
    selectedOption: MutableState<String>,
    expanded: MutableState<Boolean>
) {


    Column(
        modifier = Modifier
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(15.dp)
            )
            .height(57.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black.copy(alpha = 0.15F))
            .clickable { expanded.value = true }
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween){
            Text(
                text = selectedOption.value.uppercase(),
                modifier = Modifier
                    //.clip(RoundedCornerShape(15.dp))
                    .fillMaxWidth(0.7F)
                    .padding(16.dp)
            )
            Box(Modifier
                .fillMaxHeight()
                .aspectRatio(1F), contentAlignment = Alignment.Center){
                Icon(
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Show options",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        selectedOption.value = option.toBoolean().toString()
                        expanded.value = false
                    },
                    text = { Text(option) }
                )
            }
        }
    }
}

@Composable
fun OptionSelector(
    options: Map<String, String>,
    selectedOption: MutableState<String>,
    expanded: MutableState<Boolean>
) {
    val selectedLabel = options.entries
        .firstOrNull { it.value == selectedOption.value }
        ?.key
        ?: selectedOption.value

    Column(
        modifier = Modifier
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(15.dp)
            )
            .height(57.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black.copy(alpha = 0.15F))
            .clickable { expanded.value = true }
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(0.9F)) {
            Text(
                text = selectedLabel,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.85F)
                    .padding(top = 16.dp, bottom = 16.dp, start = 16.dp)
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1F),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Show options",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    onClick = {
                        selectedOption.value = value
                        expanded.value = false
                    },
                    text = { Text(label) }
                )
            }
        }
    }
}

@Composable
fun OptionSelector(
    options: List<String>,
    selectedOption: MutableState<String>,
    expanded: MutableState<Boolean>
) {


    Column(
        modifier = Modifier
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(15.dp)
            )
            .height(57.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black.copy(alpha = 0.15F))
            .clickable { expanded.value = true }
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(0.9F)){
            Text(
                text = selectedOption.value,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.85F)
                    .padding(top = 16.dp, bottom = 16.dp, start = 16.dp)
            )
            Box(Modifier
                .fillMaxHeight()
                .aspectRatio(1F), contentAlignment = Alignment.Center){
                Icon(
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Show options",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        selectedOption.value = option
                        expanded.value = false
                    },
                    text = { Text(option) }
                )
            }
        }
    }
}
