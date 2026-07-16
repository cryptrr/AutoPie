package com.autopi.autopieapp.presentation.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun YesNoDialog(
    showDialog: Boolean,
    title: String,
    subtitle: String,
    onYesClicked: () -> Unit,
    onNoClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (showDialog) {
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = title, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = subtitle, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(12.5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {

                        Button(shape = RoundedCornerShape(10.dp),onClick = { onNoClicked() }) {
                            Text("No")
                        }

                        Spacer(modifier = Modifier.width(15.dp))


                        Button(shape = RoundedCornerShape(10.dp),onClick = { onYesClicked() }) {
                            Text("Yes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YesNoMultiCheckboxDialog(
    showDialog: Boolean,
    title: String,
    subtitle: String,
    options: List<String>,
    onYesClicked: (List<String>) -> Unit,
    onNoClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (showDialog) {
        var checkedOptions by rememberSaveable(options) {
            mutableStateOf(options.associateWith { true })
        }

        Dialog(onDismissRequest = { onDismissRequest() }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(text = title, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = subtitle, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(12.dp))

                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checkedOptions[option] == true,
                                onCheckedChange = { checked ->
                                    checkedOptions = checkedOptions.toMutableMap().apply {
                                        this[option] = checked
                                    }
                                }
                            )
                            Text(text = option, fontSize = 15.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(shape = RoundedCornerShape(10.dp), onClick = { onNoClicked() }) {
                            Text("No")
                        }

                        Spacer(modifier = Modifier.width(15.dp))

                        Button(
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                onYesClicked(
                                    options.filter { checkedOptions[it] == true }
                                )
                            }
                        ) {
                            Text("Yes")
                        }
                    }
                }
            }
        }
    }
}
