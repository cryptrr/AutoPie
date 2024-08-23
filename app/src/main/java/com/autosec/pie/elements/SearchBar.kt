package com.autosec.pie.elements

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchBar(searchQuery: MutableState<String>, doSearch: () -> Unit){

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        shape = RoundedCornerShape(15.dp),
        value = searchQuery.value,
        onValueChange = {
            searchQuery.value = it
            doSearch()
        },
        colors = OutlinedTextFieldDefaults.colors().copy(unfocusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(.75F)),
        //label = { Text("Search") },
        placeholder = { Text("Search your commands") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                doSearch()
            }
        ),
        trailingIcon = {
            Button(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp),
                onClick = {
                    if(searchQuery.value.isNotBlank()){
                        searchQuery.value = ""
                        doSearch()
                        focusManager.clearFocus()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                if(searchQuery.value.isNotBlank()){
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(25.dp)
                    )
                }
                else{
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()


    )
}