package com.autosec.pie.autopieapp.presentation.elements

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilePresent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.utils.Utils
import timber.log.Timber



@Composable
fun GenericTextFormField(text: MutableState<String>,title: String,subtitle: String? = null, placeholder: String? = null, maxLines: Int? = null, singleLine: Boolean = true,isError: Boolean = false,onValueChange: (String) -> Unit = {}, modifier: Modifier = Modifier, trailingIcon: (@Composable (() -> Unit))? = null){
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
            trailingIcon = trailingIcon,
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
fun GenericTextAndSelectorFormField(text: MutableState<String>,title: String,subtitle: String? = null, placeholder: String? = null, maxLines: Int? = null, singleLine: Boolean = true,isError: Boolean = false,onValueChange: (String) -> Unit = {}, modifier: Modifier = Modifier){


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
            trailingIcon = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MultiFilePicker{
                        text.value = it.joinToString(",")
                    }
                }
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

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun MultiFilePicker(
    onFilesPicked: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val paths = uris.mapNotNull { uri ->
            Timber.d("SELECTED FILE: $uri")
            Utils.getAbsolutePathFromUri2(context ,uri)
        }
        onFilesPicked(paths)
    }

    // Call this when you want to launch the picker, for example on button click
    remember { launcher }

    Button(onClick = { launcher.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
        Icon(
            imageVector = Icons.Rounded.FilePresent,
            contentDescription = "File Picker",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(25.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun SingleFilePicker(
    onFilesPicked: (String) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let{
            val path =  Utils.getAbsolutePathFromUri2(context,uri)

            if (path != null) {
                onFilesPicked(path)
            }
        }
    }

    remember { launcher }

    Button(onClick = { launcher.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
        Icon(
            imageVector = Icons.Rounded.FilePresent,
            contentDescription = "File Picker",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(25.dp)
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