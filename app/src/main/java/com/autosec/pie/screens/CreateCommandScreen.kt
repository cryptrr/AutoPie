package com.autosec.pie.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.elements.GenericFormSwitch
import com.autosec.pie.elements.GenericTextFormField
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.viewModels.CreateCommandViewModel
import com.autosec.pie.viewModels.EditCommandViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommandScreen(open: MutableState<Boolean>) {

    val viewModel: CreateCommandViewModel by KoinJavaComponent.inject(CreateCommandViewModel::class.java)


    Column {

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .weight(1F, true)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New Command",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }



            Spacer(modifier = Modifier.height(25.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                viewModel.commandTypeOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = viewModel.commandTypeOptions.size,
                            baseShape = RoundedCornerShape(10.dp)
                        ),
                        onClick = {
                            viewModel.selectedICommandTypeIndex = index

                            when (label) {
                                "Share" -> viewModel.selectedCommandType = "SHARE"
                                "Observer" -> viewModel.selectedCommandType = "FILE_OBSERVER"
                            }
                        },
                        colors = SegmentedButtonDefaults.colors().copy(
                            inactiveContainerColor = Color.Transparent,
                            activeContentColor = Purple10
                        ),
                        selected = index == viewModel.selectedICommandTypeIndex
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(15.dp))



            GenericTextFormField(text = viewModel.commandName, "NAME*")

            Spacer(modifier = Modifier.height(20.dp))
            GenericTextFormField(text = viewModel.execFile, "PROGRAM*")

            Spacer(modifier = Modifier.height(20.dp))

            GenericTextFormField(
                text = viewModel.command,
                "Command".uppercase(),
                maxLines = 4,
                singleLine = false,
                modifier = Modifier
                    .height(100.dp)
                //.wrapContentHeight()
            )

            if(viewModel.selectedCommandType == "FILE_OBSERVER"){
                Spacer(modifier = Modifier.height(20.dp))

                GenericTextFormField(
                    text = viewModel.selectors,
                    "Selectors".uppercase(),
                    subtitle = "Selector is a regex pattern to filter for this command.\nFor Example, to select only PNG files, use \"^.*\\\\.png$\""
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GenericTextFormField(
                text = viewModel.directory,
                subtitle = "Provide a folder in your storage",
                title = "DIRECTORY",
            )

            Spacer(modifier = Modifier.height(20.dp))


            GenericFormSwitch(
                text = "Delete source file after completion",
                switchState = viewModel.deleteSource,
                onChange = { viewModel.deleteSource.value = it }
            )
        }


        Row {

            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20),
                //contentPadding = PaddingValues(vertical = 20.dp),
                enabled = viewModel.isValidCommand,
                onClick = {
                    viewModel.viewModelScope.launch {
                        viewModel.createNewCommand()

                        delay(500L)
                        viewModel.main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                        open.value = false
                    }
                },

                ) {
                Text(
                    text = "CREATE",
                    //modifier = Modifier.align(Alignment.Center),
                    letterSpacing = 1.11.sp,
                    fontWeight = FontWeight.SemiBold
                )

            }
        }

    }
}