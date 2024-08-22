package com.autosec.pie.screens

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShareCommandBottomSheet(
    state: SheetState,
    open : MutableState<Boolean>,
    onHide: () -> Unit = {},
    onExpand: () -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    val activity = (LocalContext.current as? Activity)

    @Composable
    fun bottomSheetContent() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp)
                .fillMaxHeight(0.75F)
            ,
            contentAlignment = Alignment.TopStart
            //.windowInsetsPadding(WindowInsets.navigationBars)

        )
        {


            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 15.dp)){

                NewCommand()


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
fun NewCommand(

) {

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        Column(horizontalAlignment = Alignment.CenterHorizontally){
            Text("Add new command")
            Text("Work In Progress")
        }
    }
}