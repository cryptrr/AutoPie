package com.autosec.pie.autopieapp.presentation.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.InstallDesktop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.ui.theme.Purple10

@Composable
fun AppBottomBar(selectedItem: MutableIntState){
    NavigationBar(tonalElevation = 0.dp) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .padding(horizontal = 25.dp)
            .align(Alignment.CenterVertically)
        ) {

            OutlinedButton(
                modifier = Modifier
                    .height(70.dp)
                    .width(95.dp)

                    .clickable { }
                    .align(Alignment.CenterVertically)
                ,
                contentPadding = PaddingValues(10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if(selectedItem.intValue == 0) Purple10.copy(0.15F) else Color.Transparent),
                border = null,
                onClick = {
                    selectedItem.intValue = 0
                }

            ){

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Commands",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(3.dp))

                    Text("Commands", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }

            }

            OutlinedButton(
                modifier = Modifier
                    .height(70.dp)
                    .width(95.dp)
                    .align(Alignment.CenterVertically)
                ,
                contentPadding = PaddingValues(5.dp),
                border = null,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if(selectedItem.intValue == 1) Purple10.copy(0.15F) else Color.Transparent),
                onClick = {
                    selectedItem.intValue = 1
                }

            ){

                Box(modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))){
                    Box(modifier = Modifier.align(Alignment.Center)){
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.InstallDesktop,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text("Installed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

            }


            OutlinedButton(
                modifier = Modifier
                    .height(70.dp)
                    .width(95.dp)

                    .align(Alignment.CenterVertically)
                ,
                contentPadding = PaddingValues(10.dp),
                shape = RoundedCornerShape(20.dp),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if(selectedItem.intValue == 2) Purple10.copy(0.15F) else Color.Transparent),
                onClick = {
                    selectedItem.intValue = 2
                }

            ){

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(3.dp))

                    Text("Settings", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }

            }

        }
    }
}