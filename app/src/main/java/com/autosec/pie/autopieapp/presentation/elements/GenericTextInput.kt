package com.autosec.pie.autopieapp.presentation.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GenericTextFormField(text: MutableState<String>,title: String,subtitle: String? = null, placeholder: String? = null, maxLines: Int? = null, singleLine: Boolean = true,isError: Boolean = false,onValueChange: (String) -> Unit = {}, modifier: Modifier = Modifier){
    Column {
        if(title.isNotBlank()){
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
        }

        if(subtitle?.isNotBlank() == true){
            Text(text = subtitle, fontSize = 14.sp, fontWeight = FontWeight.Normal)
        }

        Spacer(modifier = Modifier.height(10.dp))


        OutlinedTextField(
            shape = RoundedCornerShape(15.dp),
            value = text.value,

            onValueChange = {
                text.value = it
                onValueChange(it)
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            minLines = 2,
            maxLines = Int.MAX_VALUE,
            colors = OutlinedTextFieldDefaults.colors().copy(unfocusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(.75F)),
            //label = { Text("Search") },
            placeholder = { placeholder?.let{Text(it)} },
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)


        )
    }
}

@Composable
fun GenericTextFormField(text: MutableState<String>,title: String,subtitle: AnnotatedString, placeholder: String? = null, maxLines: Int? = null, singleLine: Boolean = true,isError: Boolean = false,onValueChange: (String) -> Unit = {}, modifier: Modifier = Modifier){
    Column {
        if(title.isNotBlank()){
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
        }

        if(subtitle?.isNotBlank() == true){
            Text(text = subtitle, fontSize = 14.sp, fontWeight = FontWeight.Normal)
        }

        Spacer(modifier = Modifier.height(10.dp))


        OutlinedTextField(
            shape = RoundedCornerShape(15.dp),
            value = text.value,

            onValueChange = {
                text.value = it
                onValueChange(it)
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            minLines = 2,
            maxLines = Int.MAX_VALUE,
            colors = OutlinedTextFieldDefaults.colors().copy(unfocusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(.75F)),
            //label = { Text("Search") },
            placeholder = { placeholder?.let{Text(it)} },
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)


        )
    }
}

@Composable
fun GenericFormSwitch(text: String, switchState: MutableState<Boolean>, onChange: (Boolean) -> Unit){
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
    ) {
        Text(text, fontSize = 16.4.sp, fontWeight = FontWeight.SemiBold)
        Switch(checked = switchState.value, onCheckedChange = {
                onChange(it)
        })
    }
}