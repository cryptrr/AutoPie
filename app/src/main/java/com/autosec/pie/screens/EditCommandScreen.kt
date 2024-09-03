package com.autosec.pie.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.autosec.pie.data.CommandExtra
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.elements.CommandExtraInputElement
import com.autosec.pie.elements.GenericFormSwitch
import com.autosec.pie.elements.GenericTextFormField
import com.autosec.pie.ui.theme.PastelPurple
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.viewModels.EditCommandViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCommandBottomSheet(
    state: SheetState,
    open: MutableState<Boolean>,
    key: String,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val viewModel: EditCommandViewModel by KoinJavaComponent.inject(EditCommandViewModel::class.java)

    LaunchedEffect(key1 = key) {
        viewModel.getCommandDetails(key)
    }


    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(0.75F),
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {

            if(viewModel.isLoading.value){
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                    CircularProgressIndicator()
                }
            }else{
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp)
                ) {

                    EditCommandScreen(key, open)
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
                open.value = false
            }
        }
    )
}


@Composable
fun EditCommandScreen(commandKey: String, open: MutableState<Boolean>) {

    val viewModel: EditCommandViewModel by KoinJavaComponent.inject(EditCommandViewModel::class.java)

    val extrasElements = remember {
        mutableStateListOf<Int>()
    }

    var counter = remember { mutableStateOf(0) }


    fun addExtra(){
        extrasElements.add(counter.value)
        counter.value++
    }




    Column {

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .weight(1F, true)) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically){
                Text(
                    text = "Edit Command",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Box(
                    Modifier
                        .clip(
                            RoundedCornerShape(10.dp)
                        )
                        .background(
                            when (viewModel.type.value) {
                                "SHARE" -> PastelPurple
                                "FILE_OBSERVER" -> Purple10
                                else -> Purple10
                            }
                        )
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    when (viewModel.type.value) {
                        "SHARE" -> {
                            Text(
                                text = "SHARE",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }

                        "FILE_OBSERVER" -> {
                            Text(
                                text = "FILE OBSERVER",
                                fontSize = 13.3.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(25.dp))


            GenericTextFormField(text = viewModel.commandName, "NAME", placeholder = "name")

            Spacer(modifier = Modifier.height(20.dp))
            GenericTextFormField(text = viewModel.execFile, "PROGRAM", placeholder = "exec file")

            Spacer(modifier = Modifier.height(20.dp))

            GenericTextFormField(
                text = viewModel.command,
                "Command".uppercase(),
                placeholder = "command",
                singleLine = false,
                modifier = Modifier
                    //.height(100.dp)
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
                "DIRECTORY",
                placeholder = "directory"
            )

            Spacer(modifier = Modifier.height(20.dp))


            Column {
               if( extrasElements.isNotEmpty()) {
                    Text(text = "EXTRAS", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                   Text(text = extrasElements.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
               }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(
                    rememberScrollState())){
                    for(item in extrasElements){
                        CommandExtraInputElement(viewModel, item, extrasElements, counter)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))


            GenericFormSwitch(
                text = "Delete source file after completion",
                switchState = viewModel.deleteSource,
                onChange = { viewModel.deleteSource.value = it}
            )



        }


        Row {
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .width(63.dp),
                shape = RoundedCornerShape(20),
                contentPadding = PaddingValues(vertical = 10.dp),
                onClick = {
                    viewModel.viewModelScope.launch {
                        viewModel.deleteCommand(key = commandKey)
                        delay(500L)
                        viewModel.main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                        open.value = false
                    }
                },

                ) {
                Icon(
                    modifier = Modifier
                        .size(27.dp)

                    ,
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                )

            }
            
            Spacer(modifier = Modifier.width(11.dp))
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .width(63.dp),
                shape = RoundedCornerShape(20),
                contentPadding = PaddingValues(vertical = 10.dp),
                onClick = {
                    addExtra()
                },

                ) {
                Icon(
                    modifier = Modifier

                        .size(27.dp),
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Extras",
                )

            }

            Spacer(modifier = Modifier.width(11.dp))
            Button(
                modifier = Modifier
                    .padding(vertical = 15.dp)
                    .height(52.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20),
                //contentPadding = PaddingValues(vertical = 20.dp),
                onClick = {
                    viewModel.viewModelScope.launch {
                        viewModel.changeCommandDetails(key = commandKey)
                        delay(500L)
                        viewModel.main.dispatchEvent(ViewModelEvent.RefreshCommandsList)
                        open.value = false
                    }
                },

                ) {
                Text(
                    text = "SAVE",
                    //modifier = Modifier.align(Alignment.Center),
                    letterSpacing = 1.11.sp,
                    fontWeight = FontWeight.SemiBold
                )

            }
        }

    }
}