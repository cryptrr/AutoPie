package com.autosec.pie.autopieapp.presentation.elements

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.autopieapp.data.AutoPieStrings
import com.autosec.pie.autopieapp.data.CommandExtra


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
            for (item in extrasElements.value) {
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
        mutableStateOf(command.selectableOptions.joinToString(","))
    }

    val description = rememberSaveable {
        mutableStateOf(command.description)
    }

    var expanded = remember { mutableStateOf(false) }
    var selectedCommandType =
        rememberSaveable { mutableStateOf(command.type.split(",").firstOrNull() ?: "") }
    val options = listOf("STRING", "SELECTABLE", "BOOLEAN")

    //Boolean extra options
    var booleanExpanded = remember { mutableStateOf(false) }
    var selectedOptionForBoolean =
        rememberSaveable { mutableStateOf(command.defaultBoolean.toString()) }
    val booleanOptions = listOf("TRUE", "FALSE")


    LaunchedEffect(
        listOf(
            name.value,
            default.value,
            selectableOptions.value,
            description.value,
            selectedCommandType.value,
            selectedOptionForBoolean.value
        )
    ) {

        val commandExtra = CommandExtra(
            id = command.id,
            name = name.value,
            type = selectedCommandType.value,
            default = if(command.type == "SELECTABLE") (selectableOptions.value.split(",").firstOrNull() ?: "") else default.value,
            description = description.value,
            defaultBoolean = selectedOptionForBoolean.value.toBoolean(),
            selectableOptions = selectableOptions.value.split(",")
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
                    isError = default.value.isBlank()
                )
                GenericTextFormField(
                    text = description,
                    "",
                    placeholder = "DESCRIPTION",
                    singleLine = false,
                )
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
                    subtitle = "Options for this field. Separate options with commas. First Option will be considered default.",
                    isError = selectableOptions.value.isBlank()
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
fun OptionSelector(
    options: List<String>,
    selectedOption: MutableState<String>,
    expanded: MutableState<Boolean>
) {


    Column(
        modifier = Modifier.border(
            2.dp,
            MaterialTheme.colorScheme.primary,
            RoundedCornerShape(15.dp)
        ).height(57.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black.copy(alpha = 0.15F))
            .clickable { expanded.value = true }
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween){
            Text(
                text = selectedOption.value,
                modifier = Modifier
                    //.clip(RoundedCornerShape(15.dp))
                    .fillMaxWidth(0.7F)
                    .padding(16.dp)
            )
            Box(Modifier.fillMaxHeight().aspectRatio(1F), contentAlignment = Alignment.Center){
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