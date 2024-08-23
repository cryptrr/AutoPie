package com.autosec.pie.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.elements.GenericFormSwitch
import com.autosec.pie.elements.GenericTextFormField
import com.autosec.pie.viewModels.CreateCommandViewModel
import org.koin.java.KoinJavaComponent


@Composable
fun CreateCommandScreen(){

    val viewModel: CreateCommandViewModel by KoinJavaComponent.inject(CreateCommandViewModel::class.java)
    

    Column {

        Text(
            text = "New Command",
            fontSize = 33.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Spacer(modifier = Modifier.height(25.dp))


        GenericTextFormField(text = viewModel.newCommandName,"Name", placeholder = "name")

        Spacer(modifier = Modifier.height(20.dp))
        GenericTextFormField(text = viewModel.execFile,"Exec File", placeholder = "exec file")

        Spacer(modifier = Modifier.height(20.dp))

        GenericTextFormField(text = viewModel.command,"Command To Run", placeholder = "command")

        Spacer(modifier = Modifier.height(20.dp))

        GenericTextFormField(text = viewModel.directory,"Directory To Store", placeholder = "directory")

        Spacer(modifier = Modifier.height(20.dp))

        
        GenericFormSwitch(text = "Delete source file after completion", switchState = viewModel.deleteSource) {
            
        }

    }
}